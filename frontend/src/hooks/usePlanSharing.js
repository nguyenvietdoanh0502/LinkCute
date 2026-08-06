import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../api/client.js'
import {
  buildPlanSyncRequest,
  findOutgoingPlanInvitation,
  isPlanMember,
  isPlanVersionConflict,
  localOwnerPlanToView,
  normalizePlanInvitation,
  normalizePlanInvitations,
  normalizeRemotePlan,
  normalizeRemotePlans,
  planNeedsSync,
  planSharingErrorMessage,
  remoteMemberPlanToView,
  remotePlanToLocalPlan,
} from '../itinerary/sharing.js'

const SYNC_DELAY_MS = 700
const NOOP = () => {}

function replaceById(values, nextValue) {
  const index = values.findIndex((value) => value.id === nextValue.id)
  if (index === -1) return [...values, nextValue]
  const next = [...values]
  next[index] = nextValue
  return next
}

export function usePlanSharing({
  session,
  localPlans = [],
  upsertOwnedRemotePlan = NOOP,
  clearPlanServerLink = NOOP,
} = {}) {
  const [remotePlans, setRemotePlans] = useState([])
  const [incomingInvitations, setIncomingInvitations] = useState([])
  const [outgoingInvitations, setOutgoingInvitations] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [syncError, setSyncError] = useState('')
  const [busyActions, setBusyActions] = useState(() => new Set())
  const sessionUserId = session?.user?.id ? String(session.user.id) : ''
  const sessionIdentity = sessionUserId || (session?.accessToken ? 'authenticated' : '')
  const sessionIdentityRef = useRef(sessionIdentity)
  sessionIdentityRef.current = sessionIdentity
  const loadSequenceRef = useRef(0)
  const remotePlansRef = useRef([])
  const localPlansRef = useRef(localPlans)
  const busyActionsRef = useRef(new Set())
  const syncTimersRef = useRef(new Map())
  const publishPromisesRef = useRef(new Map())

  useEffect(() => {
    localPlansRef.current = localPlans
  }, [localPlans])

  const clearSyncTimer = useCallback((clientPlanId) => {
    const scheduled = syncTimersRef.current.get(clientPlanId)
    if (scheduled) globalThis.clearTimeout(scheduled.timer)
    syncTimersRef.current.delete(clientPlanId)
  }, [])

  const clearAllSyncTimers = useCallback(() => {
    syncTimersRef.current.forEach(({ timer }) => globalThis.clearTimeout(timer))
    syncTimersRef.current.clear()
  }, [])

  const adoptOwnerSnapshot = useCallback((remotePlan, { forceRemote = false } = {}) => {
    if (
      remotePlan?.accessRole !== 'OWNER'
      || !sessionUserId
      || remotePlan.owner?.id !== sessionUserId
    ) return
    const localPlan = remotePlanToLocalPlan(remotePlan)
    if (localPlan) upsertOwnedRemotePlan(localPlan, { forceRemote })
  }, [sessionUserId, upsertOwnedRemotePlan])

  const storeRemotePlan = useCallback((rawPlan, options = {}) => {
    if (!sessionIdentity || sessionIdentityRef.current !== sessionIdentity) return null
    const plan = normalizeRemotePlan(rawPlan)
    if (!plan) return null
    setRemotePlans((current) => {
      if (sessionIdentityRef.current !== sessionIdentity) return current
      const next = replaceById(current, plan)
      remotePlansRef.current = next
      return next
    })
    adoptOwnerSnapshot(plan, options)
    return plan
  }, [adoptOwnerSnapshot, sessionIdentity])

  const load = useCallback(async ({ signal, silent = false } = {}) => {
    if (!sessionIdentity) return { plans: [], incoming: [], outgoing: [] }
    const sequence = ++loadSequenceRef.current
    if (!silent) setLoading(true)
    setError('')

    try {
      const [rawPlans, rawIncoming, rawOutgoing] = await Promise.all([
        api.getPlans({ signal }),
        api.getIncomingPlanInvitations({ signal }),
        api.getOutgoingPlanInvitations({ signal }),
      ])
      const plans = normalizeRemotePlans(rawPlans)
      const incoming = normalizePlanInvitations(rawIncoming)
      const outgoing = normalizePlanInvitations(rawOutgoing)
      if (sequence !== loadSequenceRef.current) return null

      remotePlansRef.current = plans
      setRemotePlans(plans)
      setIncomingInvitations(incoming)
      setOutgoingInvitations(outgoing)

      plans.forEach((plan) => adoptOwnerSnapshot(plan))
      const remoteIds = new Set(plans.map((plan) => plan.id))
      localPlansRef.current.forEach((plan) => {
        if (
          plan.serverId
          && plan.serverOwnerId === sessionUserId
          && !remoteIds.has(plan.serverId)
        ) clearPlanServerLink(plan.id)
      })
      return { plans, incoming, outgoing }
    } catch (requestError) {
      if (requestError?.name === 'AbortError') return null
      if (sequence === loadSequenceRef.current) {
        setError(planSharingErrorMessage(requestError, 'Chưa tải được kế hoạch đã chia sẻ.'))
      }
      throw requestError
    } finally {
      if (!silent && sequence === loadSequenceRef.current) setLoading(false)
    }
  }, [adoptOwnerSnapshot, clearPlanServerLink, sessionIdentity, sessionUserId])

  useEffect(() => {
    loadSequenceRef.current += 1
    clearAllSyncTimers()
    publishPromisesRef.current.clear()
    busyActionsRef.current = new Set()
    setBusyActions(new Set())
    setSyncError('')

    if (!sessionIdentity) {
      remotePlansRef.current = []
      setRemotePlans([])
      setIncomingInvitations([])
      setOutgoingInvitations([])
      setLoading(false)
      setError('')
      return undefined
    }

    const abortController = new AbortController()
    load({ signal: abortController.signal }).catch(() => {})
    return () => {
      abortController.abort()
      clearAllSyncTimers()
    }
  }, [clearAllSyncTimers, load, sessionIdentity])

  const runBusy = useCallback(async (key, action) => {
    const identity = sessionIdentityRef.current
    const actionSet = busyActionsRef.current
    if (actionSet.has(key)) return null
    actionSet.add(key)
    setBusyActions(new Set(actionSet))
    try {
      return await action()
    } finally {
      actionSet.delete(key)
      if (sessionIdentityRef.current === identity && busyActionsRef.current === actionSet) {
        setBusyActions(new Set(actionSet))
      }
    }
  }, [])

  const remoteForLocalPlan = useCallback((localPlan) => (
    remotePlansRef.current.find((plan) => (
      plan.accessRole === 'OWNER'
      && (
        plan.clientPlanId === localPlan?.id
        || plan.id === localPlan?.serverId
      )
    )) || null
  ), [])

  const reconcileConflict = useCallback(async (localPlan, requestError) => {
    const identity = sessionIdentity
    if (!identity || sessionIdentityRef.current !== identity) return true
    const remote = remoteForLocalPlan(localPlan)
    const remoteId = remote?.id || localPlan?.serverId
    if (!remoteId || !isPlanVersionConflict(requestError)) return false
    try {
      const fresh = normalizeRemotePlan(await api.getPlan(remoteId))
      if (sessionIdentityRef.current !== identity) return true
      if (fresh) {
        const adopted = remotePlanToLocalPlan(fresh)
        if (adopted) {
          const currentIndex = localPlansRef.current.findIndex((plan) => (
            plan.id === adopted.id || plan.serverId === adopted.serverId
          ))
          localPlansRef.current = currentIndex === -1
            ? [...localPlansRef.current, adopted]
            : localPlansRef.current.map((plan, index) => index === currentIndex ? adopted : plan)
        }
        storeRemotePlan(fresh, { forceRemote: true })
        setSyncError(planSharingErrorMessage(requestError))
        return true
      }
    } catch {
      // Preserve the original mutation error when reconciliation also fails.
    }
    return false
  }, [remoteForLocalPlan, sessionIdentity, storeRemotePlan])

  const syncOwnerPlan = useCallback(async (localPlan) => {
    if (!sessionIdentity || sessionIdentityRef.current !== sessionIdentity || !localPlan) return null
    const identity = sessionIdentity
    const remote = remoteForLocalPlan(localPlan)
    try {
      const synced = await api.syncPlan(buildPlanSyncRequest(
        localPlan,
        remote?.version ?? localPlan.serverVersion ?? null,
      ))
      if (sessionIdentityRef.current !== identity) return null
      const plan = storeRemotePlan(synced)
      setSyncError('')
      return plan
    } catch (requestError) {
      await reconcileConflict(localPlan, requestError)
      throw requestError
    }
  }, [reconcileConflict, remoteForLocalPlan, sessionIdentity, storeRemotePlan])

  const syncPublishedPlan = useCallback(async (clientPlanId) => {
    const inFlight = publishPromisesRef.current.get(clientPlanId)
    if (inFlight) return inFlight
    const localPlan = localPlansRef.current.find((plan) => plan.id === clientPlanId)
    const remote = remoteForLocalPlan(localPlan)
    if (!localPlan || !remote || !planNeedsSync(localPlan, remote)) return remote

    const promise = runBusy(`sync:${clientPlanId}`, () => syncOwnerPlan(localPlan))
      .catch((requestError) => {
        if (sessionIdentityRef.current === sessionIdentity) {
          setSyncError(planSharingErrorMessage(requestError))
        }
        return null
      })
      .finally(() => {
        if (publishPromisesRef.current.get(clientPlanId) === promise) {
          publishPromisesRef.current.delete(clientPlanId)
        }
      })
    publishPromisesRef.current.set(clientPlanId, promise)
    return promise
  }, [remoteForLocalPlan, runBusy, sessionIdentity, syncOwnerPlan])

  useEffect(() => {
    if (!sessionIdentity) return
    const liveIds = new Set(localPlans.map((plan) => plan.id))

    localPlans.forEach((localPlan) => {
      const remote = remotePlans.find((plan) => (
        plan.accessRole === 'OWNER'
        && (plan.clientPlanId === localPlan.id || plan.id === localPlan.serverId)
      ))
      const scheduled = syncTimersRef.current.get(localPlan.id)
      if (!remote || !planNeedsSync(localPlan, remote)) {
        if (scheduled) clearSyncTimer(localPlan.id)
        return
      }
      if (scheduled?.updatedAt === localPlan.updatedAt) return
      if (scheduled) clearSyncTimer(localPlan.id)

      const timer = globalThis.setTimeout(() => {
        syncTimersRef.current.delete(localPlan.id)
        syncPublishedPlan(localPlan.id)
      }, SYNC_DELAY_MS)
      syncTimersRef.current.set(localPlan.id, { timer, updatedAt: localPlan.updatedAt })
    })

    syncTimersRef.current.forEach((_, clientPlanId) => {
      if (!liveIds.has(clientPlanId)) clearSyncTimer(clientPlanId)
    })
  }, [clearSyncTimer, localPlans, remotePlans, sessionIdentity, syncPublishedPlan])

  const ensurePublished = useCallback(async (localPlan) => {
    if (!sessionIdentity) {
      const authError = new Error('Vui lòng đăng nhập để chia sẻ kế hoạch.')
      authError.status = 401
      authError.errorCode = 'UNAUTHENTICATED'
      throw authError
    }
    if (!localPlan) throw new Error('Không tìm thấy kế hoạch cần chia sẻ.')

    clearSyncTimer(localPlan.id)
    const currentPromise = publishPromisesRef.current.get(localPlan.id)
    if (currentPromise) {
      await currentPromise
      const latestLocal = localPlansRef.current.find((plan) => plan.id === localPlan.id) || localPlan
      const latestRemote = remoteForLocalPlan(latestLocal)
      if (latestRemote && !planNeedsSync(latestLocal, latestRemote)) return latestRemote
      return ensurePublished(latestLocal)
    }

    const promise = (async () => {
      const identity = sessionIdentity
      if (sessionIdentityRef.current !== identity) return null
      let remote = remoteForLocalPlan(localPlan)
      if (!remote && localPlan.serverId) {
        try {
          remote = storeRemotePlan(await api.getPlan(localPlan.serverId))
          if (sessionIdentityRef.current !== identity) return null
        } catch (requestError) {
          if (requestError?.status !== 404) throw requestError
          clearPlanServerLink(localPlan.id)
        }
      }
      if (sessionIdentityRef.current !== identity) return null
      if (remote && !planNeedsSync(localPlan, remote)) return remote
      return syncOwnerPlan(localPlan)
    })().finally(() => {
      if (publishPromisesRef.current.get(localPlan.id) === promise) {
        publishPromisesRef.current.delete(localPlan.id)
      }
    })

    publishPromisesRef.current.set(localPlan.id, promise)
    return promise
  }, [clearPlanServerLink, clearSyncTimer, remoteForLocalPlan, sessionIdentity, storeRemotePlan, syncOwnerPlan])

  const inviteFriend = useCallback((localPlan, userId) => runBusy(
    `invite:${localPlan?.id}:${userId}`,
    async () => {
      const identity = sessionIdentity
      if (sessionIdentityRef.current !== identity) return null
      const published = await ensurePublished(localPlan)
      if (!published || sessionIdentityRef.current !== identity) return null
      const invitation = normalizePlanInvitation(await api.inviteToPlan(published.id, userId))
      if (sessionIdentityRef.current !== identity) return null
      if (invitation) {
        setOutgoingInvitations((current) => replaceById(current, invitation))
      }
      return invitation
    },
  ), [ensurePublished, runBusy, sessionIdentity])

  const cancelInvitation = useCallback((invitation) => runBusy(
    `cancel-invite:${invitation?.id}`,
    async () => {
      const identity = sessionIdentity
      if (sessionIdentityRef.current !== identity) return null
      await api.cancelPlanInvitation(invitation.id)
      if (sessionIdentityRef.current !== identity) return null
      setOutgoingInvitations((current) => current.filter((item) => item.id !== invitation.id))
      return true
    },
  ), [runBusy, sessionIdentity])

  const refreshRemotePlan = useCallback(async (planId) => {
    const identity = sessionIdentity
    if (sessionIdentityRef.current !== identity) return null
    const plan = await api.getPlan(planId)
    if (sessionIdentityRef.current !== identity) return null
    return storeRemotePlan(plan)
  }, [sessionIdentity, storeRemotePlan])

  const removeMember = useCallback((plan, userId) => runBusy(
    `remove-member:${plan?.remoteId || plan?.serverId}:${userId}`,
    async () => {
      const identity = sessionIdentity
      if (sessionIdentityRef.current !== identity) return null
      const planId = plan?.remoteId || plan?.serverId
      await api.removePlanMember(planId, userId)
      if (sessionIdentityRef.current !== identity) return null
      return refreshRemotePlan(planId)
    },
  ), [refreshRemotePlan, runBusy, sessionIdentity])

  const acceptInvitation = useCallback((invitation) => runBusy(
    `accept-invite:${invitation?.id}`,
    async () => {
      const identity = sessionIdentity
      if (sessionIdentityRef.current !== identity) return null
      const response = await api.acceptPlanInvitation(invitation.id)
      if (sessionIdentityRef.current !== identity) return null
      if (response?.accessRole && Array.isArray(response.items)) storeRemotePlan(response)
      const snapshot = await load({ silent: true })
      return snapshot?.plans.find((plan) => plan.id === invitation.plan.id)
        || remotePlansRef.current.find((plan) => plan.id === invitation.plan.id)
        || null
    },
  ), [load, runBusy, sessionIdentity, storeRemotePlan])

  const declineInvitation = useCallback((invitation) => runBusy(
    `decline-invite:${invitation?.id}`,
    async () => {
      const identity = sessionIdentity
      if (sessionIdentityRef.current !== identity) return null
      await api.declinePlanInvitation(invitation.id)
      if (sessionIdentityRef.current !== identity) return null
      setIncomingInvitations((current) => current.filter((item) => item.id !== invitation.id))
      return true
    },
  ), [runBusy, sessionIdentity])

  const leavePlan = useCallback((plan) => runBusy(
    `leave-plan:${plan?.remoteId || plan?.id}`,
    async () => {
      const identity = sessionIdentity
      if (sessionIdentityRef.current !== identity) return null
      const planId = plan?.remoteId || plan?.id
      await api.leavePlan(planId)
      if (sessionIdentityRef.current !== identity) return null
      setRemotePlans((current) => {
        const next = current.filter((item) => item.id !== planId)
        remotePlansRef.current = next
        return next
      })
      return true
    },
  ), [runBusy, sessionIdentity])

  const deletePublishedPlan = useCallback((plan) => {
    const planId = plan?.remoteId || plan?.serverId
    if (!planId) return Promise.resolve(true)
    return runBusy(`delete-plan:${planId}`, async () => {
      const identity = sessionIdentity
      if (sessionIdentityRef.current !== identity) return null
      await api.deletePlan(planId)
      if (sessionIdentityRef.current !== identity) return null
      setRemotePlans((current) => {
        const next = current.filter((item) => item.id !== planId)
        remotePlansRef.current = next
        return next
      })
      return true
    })
  }, [runBusy, sessionIdentity])

  const ownerRemoteByClientId = useMemo(() => new Map(
    remotePlans
      .filter((plan) => plan.accessRole === 'OWNER')
      .map((plan) => [plan.clientPlanId, plan]),
  ), [remotePlans])

  const ownerPlans = useMemo(() => localPlans.map((plan) => {
    const remote = ownerRemoteByClientId.get(plan.id)
      || remotePlans.find((candidate) => candidate.id === plan.serverId)
    return localOwnerPlanToView(plan, remote, session?.user)
  }), [localPlans, ownerRemoteByClientId, remotePlans, session?.user])

  const memberPlans = useMemo(() => remotePlans
    .map(remoteMemberPlanToView)
    .filter(Boolean), [remotePlans])

  const plans = useMemo(() => [...ownerPlans, ...memberPlans], [memberPlans, ownerPlans])

  const invitationStateFor = useCallback((plan, userId) => {
    if (isPlanMember(plan, userId)) return { status: 'MEMBER', invitation: null }
    const invitation = findOutgoingPlanInvitation(
      outgoingInvitations,
      plan?.remoteId || plan?.serverId,
      userId,
    )
    return invitation
      ? { status: 'PENDING', invitation }
      : { status: 'NONE', invitation: null }
  }, [outgoingInvitations])

  const refresh = useCallback(() => load(), [load])
  const clearSyncError = useCallback(() => setSyncError(''), [])

  return useMemo(() => ({
    plans,
    ownerPlans,
    memberPlans,
    remotePlans,
    incomingInvitations,
    outgoingInvitations,
    incomingCount: incomingInvitations.length,
    loading,
    error,
    syncError,
    busyActions,
    refresh,
    clearSyncError,
    ensurePublished,
    inviteFriend,
    cancelInvitation,
    removeMember,
    acceptInvitation,
    declineInvitation,
    leavePlan,
    deletePublishedPlan,
    invitationStateFor,
  }), [
    plans,
    ownerPlans,
    memberPlans,
    remotePlans,
    incomingInvitations,
    outgoingInvitations,
    loading,
    error,
    syncError,
    busyActions,
    refresh,
    clearSyncError,
    ensurePublished,
    inviteFriend,
    cancelInvitation,
    removeMember,
    acceptInvitation,
    declineInvitation,
    leavePlan,
    deletePublishedPlan,
    invitationStateFor,
  ])
}
