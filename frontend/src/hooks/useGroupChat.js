import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api/client.js'
import { groupErrorMessage, mergeGroupMessages } from '../chat/groups.js'

const EMPTY = { groups: [], detail: null, messages: [], hasMore: false, nextBefore: null, loading: false, error: '' }

export function useGroupChat(session, { apiClient = api, pollIntervalMs = 15000 } = {}) {
  const identity = session?.user?.id || ''
  const identityRef = useRef(identity)
  identityRef.current = identity
  const [state, setState] = useState({ ...EMPTY, identity })
  const [selectedGroupId, setSelectedGroupId] = useState(null)
  const [busy, setBusy] = useState(false)
  const groupRef = useRef(null)
  const stateRef = useRef(state)
  const requests = useRef(new Set())
  const refreshRequest = useRef(null)
  const mounted = useRef(false)
  const generation = useRef(0)
  const mutationPending = useRef(false)
  const olderPending = useRef(false)
  const historyCursor = useRef(null)

  const commit = useCallback((update) => {
    const next = typeof update === 'function' ? update(stateRef.current) : update
    stateRef.current = next
    setState(next)
  }, [])

  const refresh = useCallback(async () => {
    if (!identity || identityRef.current !== identity || !mounted.current) return
    refreshRequest.current?.abort()
    const controller = new AbortController()
    refreshRequest.current = controller
    requests.current.add(controller)
    const epoch = generation.current
    const id = groupRef.current
    const current = () => mounted.current && !controller.signal.aborted && identityRef.current === identity
      && generation.current === epoch && groupRef.current === id
    try {
      const groups = await apiClient.getChatGroups({ signal: controller.signal })
      if (!current()) return
      commit((s) => ({ ...s, groups, error: '' }))
      if (!id) return
      if (!groups.some((g) => g.id === id && g.status === 'MEMBER')) {
        groupRef.current = null
        setSelectedGroupId(null)
        commit((s) => ({ ...EMPTY, identity, groups: s.groups }))
        return
      }
      const detail = await apiClient.getChatGroup(id, { signal: controller.signal })
      if (!current()) return
      // Advance only from history responses, never from a send acknowledgement;
      // another member may have sent messages before that acknowledgement.
      let after = historyCursor.current
      let initial = true
      do {
        const history = await apiClient.getChatGroupMessages(id, after == null ? {} : { after }, { signal: controller.signal })
        if (!current()) return
        historyCursor.current = history.messages.at(-1)?.id ?? after ?? 0
        commit((s) => ({ ...s, detail, messages: mergeGroupMessages(s.messages, history.messages), loading: false,
          ...(after == null && initial ? { hasMore: history.hasMore, nextBefore: history.nextBefore } : {}) }))
        if (after == null || !history.hasMore || !history.messages.length) break
        after = history.messages.at(-1).id
        initial = false
      } while (current())
    } catch (error) {
      if (!current()) return
      const denied = error?.status === 403 || error?.status === 404
      commit((s) => ({ ...s, ...(denied ? { detail: null, messages: [], hasMore: false } : {}), error: groupErrorMessage(error), loading: false }))
    } finally {
      requests.current.delete(controller)
    }
  }, [apiClient, commit, identity])

  useEffect(() => {
    mounted.current = true
    generation.current += 1
    groupRef.current = null
    historyCursor.current = null
    setSelectedGroupId(null)
    mutationPending.current = false
    olderPending.current = false
    setBusy(false)
    commit({ ...EMPTY, identity })
    void refresh()
    const timer = identity ? setInterval(() => { void refresh() }, pollIntervalMs) : null
    return () => {
      mounted.current = false
      generation.current += 1
      if (timer) clearInterval(timer)
      requests.current.forEach((controller) => controller.abort())
      requests.current.clear()
    }
  }, [commit, identity, pollIntervalMs, refresh])

  const selectGroup = useCallback((id) => {
    refreshRequest.current?.abort()
    groupRef.current = id
    historyCursor.current = null
    setSelectedGroupId(id)
    commit((s) => ({ ...EMPTY, identity, groups: s.groups, loading: Boolean(id) }))
    void refresh()
  }, [commit, identity, refresh])

  const perform = useCallback(async (method, ...args) => {
    if (!identity || identityRef.current !== identity || mutationPending.current) return null
    mutationPending.current = true
    setBusy(true)
    const epoch = generation.current
    const controller = new AbortController()
    requests.current.add(controller)
    const current = () => mounted.current && !controller.signal.aborted && identityRef.current === identity && generation.current === epoch
    try {
      const result = await apiClient[method](...args, { signal: controller.signal })
      if (!current()) return null
      await refresh()
      return current() ? { result } : null
    } catch (error) {
      if (!current()) return null
      throw error
    } finally {
      requests.current.delete(controller)
      if (current()) { mutationPending.current = false; setBusy(false) }
    }
  }, [apiClient, identity, refresh])

  const sendMessage = useCallback(async (content, clientMessageId = crypto.randomUUID()) => {
    const id = groupRef.current
    if (!id || !content.trim() || content.trim().length > 2000 || mutationPending.current) return false
    const optimistic = { groupId: id, clientMessageId, sender: session.user, content: content.trim(), createdAt: new Date().toISOString(), status: 'SENDING' }
    commit((s) => ({ ...s, messages: mergeGroupMessages(s.messages, [optimistic]) }))
    try {
      const outcome = await perform('sendChatGroupMessage', id, { content: optimistic.content, clientMessageId })
      if (!outcome) return false
      if (groupRef.current === id) commit((s) => ({ ...s, messages: mergeGroupMessages(s.messages, [outcome.result]) }))
      return true
    } catch (error) {
      if (groupRef.current === id) commit((s) => ({ ...s, messages: mergeGroupMessages(s.messages, [{ ...optimistic, status: 'FAILED', error: groupErrorMessage(error) }]) }))
      return false
    }
  }, [commit, perform, session])

  const loadOlder = useCallback(async () => {
    const id = groupRef.current
    const cursor = stateRef.current.nextBefore
    if (!id || !cursor || olderPending.current) return
    olderPending.current = true
    const epoch = generation.current
    const controller = new AbortController()
    requests.current.add(controller)
    const current = () => mounted.current && !controller.signal.aborted && identityRef.current === identity
      && generation.current === epoch && groupRef.current === id
    try {
      const history = await apiClient.getChatGroupMessages(id, { before: cursor }, { signal: controller.signal })
      if (current()) commit((s) => ({ ...s, messages: mergeGroupMessages(s.messages, history.messages), hasMore: history.hasMore, nextBefore: history.nextBefore, error: '' }))
    } catch (error) {
      if (current()) commit((s) => ({ ...s, error: groupErrorMessage(error) }))
    } finally {
      requests.current.delete(controller)
      if (generation.current === epoch) olderPending.current = false
    }
  }, [apiClient, commit, identity])

  const visible = state.identity === identity ? state : EMPTY
  return { ...visible, selectedGroupId: state.identity === identity ? selectedGroupId : null,
    busy: state.identity === identity && busy, selectGroup, refresh, perform, sendMessage, loadOlder }
}
