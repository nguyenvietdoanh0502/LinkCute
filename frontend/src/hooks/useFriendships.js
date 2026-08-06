import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../api/client.js'
import {
  friendshipErrorMessage,
  isValidFriendPin,
  normalizeFriendPin,
  syncSearchRelationship,
} from '../friends/model.js'

const EMPTY_OVERVIEW = {
  friends: [],
  incomingRequests: [],
  outgoingRequests: [],
}

export function useFriendships(session) {
  const [overview, setOverview] = useState(EMPTY_OVERVIEW)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [searchResult, setSearchResult] = useState(null)
  const [searchLoading, setSearchLoading] = useState(false)
  const [searchError, setSearchError] = useState('')
  const [busyActions, setBusyActions] = useState(() => new Set())
  const loadSequenceRef = useRef(0)
  const searchSequenceRef = useRef(0)
  const busyActionsRef = useRef(new Set())
  const searchAbortRef = useRef(null)
  const sessionIdentity = session?.user?.id || session?.user?.email || (session?.accessToken ? 'authenticated' : '')

  const reset = useCallback(() => {
    loadSequenceRef.current += 1
    searchSequenceRef.current += 1
    searchAbortRef.current?.abort()
    setOverview(EMPTY_OVERVIEW)
    setLoading(false)
    setError('')
    setSearchResult(null)
    setSearchLoading(false)
    setSearchError('')
    busyActionsRef.current = new Set()
    setBusyActions(new Set())
  }, [])

  const load = useCallback(async ({ signal, silent = false } = {}) => {
    if (!sessionIdentity) return EMPTY_OVERVIEW

    const sequence = ++loadSequenceRef.current
    if (!silent) setLoading(true)
    setError('')

    try {
      const [friends, incomingRequests, outgoingRequests] = await Promise.all([
        api.getFriends({ signal }),
        api.getIncomingFriendRequests({ signal }),
        api.getOutgoingFriendRequests({ signal }),
      ])
      const nextOverview = {
        friends: friends || [],
        incomingRequests: incomingRequests || [],
        outgoingRequests: outgoingRequests || [],
      }
      if (sequence === loadSequenceRef.current) setOverview(nextOverview)
      return nextOverview
    } catch (requestError) {
      if (requestError.name === 'AbortError') return null
      if (sequence === loadSequenceRef.current) {
        setError(friendshipErrorMessage(requestError, 'Chưa tải được danh sách bạn bè.'))
      }
      throw requestError
    } finally {
      if (!silent && sequence === loadSequenceRef.current) setLoading(false)
    }
  }, [sessionIdentity])

  useEffect(() => {
    if (!sessionIdentity) {
      reset()
      return undefined
    }

    const abortController = new AbortController()
    load({ signal: abortController.signal }).catch(() => {})
    return () => abortController.abort()
  }, [load, reset, sessionIdentity])

  const refresh = useCallback(() => load(), [load])

  const clearSearch = useCallback(() => {
    searchSequenceRef.current += 1
    searchAbortRef.current?.abort()
    setSearchResult(null)
    setSearchError('')
    setSearchLoading(false)
  }, [])

  const searchByPin = useCallback(async (value) => {
    const pinCode = normalizeFriendPin(value)
    if (!isValidFriendPin(pinCode)) {
      const validationError = new Error('Mã kết bạn phải có dạng RML-123456.')
      setSearchResult(null)
      setSearchError(validationError.message)
      throw validationError
    }

    searchAbortRef.current?.abort()
    const abortController = new AbortController()
    searchAbortRef.current = abortController
    const sequence = ++searchSequenceRef.current
    setSearchLoading(true)
    setSearchError('')
    setSearchResult(null)

    try {
      const result = await api.searchFriend(pinCode, { signal: abortController.signal })
      if (sequence === searchSequenceRef.current) setSearchResult(result)
      return result
    } catch (requestError) {
      if (requestError.name === 'AbortError') return null
      if (sequence === searchSequenceRef.current) {
        setSearchError(friendshipErrorMessage(requestError, 'Không tìm thấy thành viên này.'))
      }
      throw requestError
    } finally {
      if (sequence === searchSequenceRef.current) setSearchLoading(false)
    }
  }, [])

  const runMutation = useCallback(async (key, action, updateSearchResult) => {
    if (busyActionsRef.current.has(key)) return null

    busyActionsRef.current.add(key)
    setBusyActions(new Set(busyActionsRef.current))
    try {
      const result = await action()
      const nextOverview = await load({ silent: true }).catch(() => null)
      if (updateSearchResult) {
        setSearchResult((current) => updateSearchResult(current, nextOverview, result))
      }
      return result
    } catch (requestError) {
      if (requestError?.status === 404 || requestError?.status === 409) {
        const nextOverview = await load({ silent: true }).catch(() => null)
        if (nextOverview) {
          setSearchResult((current) => syncSearchRelationship(current, nextOverview))
        }
      }
      throw requestError
    } finally {
      busyActionsRef.current.delete(key)
      setBusyActions(new Set(busyActionsRef.current))
    }
  }, [load])

  const sendRequest = useCallback((userId) => runMutation(
    `send:${userId}`,
    () => api.sendFriendRequest(userId),
    (current, nextOverview, result) => {
      if (current?.id !== userId) return current
      if (nextOverview) {
        const synced = syncSearchRelationship(current, nextOverview)
        if (synced.relationshipStatus !== 'NONE') return synced
      }
      const outgoingRequest = nextOverview?.outgoingRequests?.find((item) => item.user?.id === userId)
      return {
        ...current,
        relationshipStatus: 'OUTGOING_PENDING',
        friendshipId: result?.id || outgoingRequest?.id || null,
      }
    },
  ), [runMutation])

  const acceptRequest = useCallback((requestId, userId) => runMutation(
    `accept:${requestId}`,
    () => api.acceptFriendRequest(requestId),
    (current, nextOverview, result) => {
      if (current?.id !== userId) return current
      if (nextOverview) {
        const synced = syncSearchRelationship(current, nextOverview)
        if (synced.relationshipStatus === 'FRIENDS') return synced
      }
      return {
        ...current,
        relationshipStatus: 'FRIENDS',
        friendshipId: result?.friendshipId || null,
      }
    },
  ), [runMutation])

  const deleteRequest = useCallback((requestId, userId) => runMutation(
    `delete-request:${requestId}`,
    () => api.deleteFriendRequest(requestId),
    (current, nextOverview) => current?.id === userId
      ? syncSearchRelationship(current, nextOverview)
      : current,
  ), [runMutation])

  const removeFriend = useCallback((friendshipId, userId) => runMutation(
    `remove:${friendshipId}`,
    () => api.removeFriend(friendshipId),
    (current, nextOverview) => current?.id === userId
      ? syncSearchRelationship(current, nextOverview)
      : current,
  ), [runMutation])

  return useMemo(() => ({
    ...overview,
    loading,
    error,
    searchResult,
    searchLoading,
    searchError,
    busyActions,
    refresh,
    clearSearch,
    searchByPin,
    sendRequest,
    acceptRequest,
    deleteRequest,
    removeFriend,
  }), [
    overview,
    loading,
    error,
    searchResult,
    searchLoading,
    searchError,
    busyActions,
    refresh,
    clearSearch,
    searchByPin,
    sendRequest,
    acceptRequest,
    deleteRequest,
    removeFriend,
  ])
}
