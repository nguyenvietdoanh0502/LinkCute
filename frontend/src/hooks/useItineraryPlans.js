import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react'
import {
  ITINERARY_STORAGE_KEY,
  createEmptyItineraryState,
  defaultPlanName,
  isPlanVisibleForSession,
  itineraryReducer,
  loadItineraryState,
  parseItineraryState,
  saveItineraryState,
  snapshotPlace,
  todayLocalDate,
} from '../itinerary/model.js'

function createId(prefix) {
  const value = globalThis.crypto?.randomUUID?.()
    || `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
  return `${prefix}-${value}`
}

function loadInitialState() {
  if (typeof window === 'undefined') {
    return { state: createEmptyItineraryState(), error: '' }
  }

  try {
    return { state: loadItineraryState(window.localStorage), error: '' }
  } catch {
    return {
      state: createEmptyItineraryState(),
      error: 'Không đọc được kế hoạch đã lưu. LinkCute sẽ tiếp tục với dữ liệu tạm trên tab này.',
    }
  }
}

export function useItineraryPlans(session = null) {
  const [initial] = useState(loadInitialState)
  const [state, dispatch] = useReducer(itineraryReducer, initial.state)
  const [storageError, setStorageError] = useState(initial.error)
  const skipFirstSave = useRef(Boolean(initial.error))

  useEffect(() => {
    if (skipFirstSave.current) {
      skipFirstSave.current = false
      return
    }
    try {
      saveItineraryState(window.localStorage, state)
      setStorageError('')
    } catch {
      setStorageError('Không lưu được kế hoạch trên trình duyệt. Thay đổi vẫn còn cho tới khi bạn đóng tab.')
    }
  }, [state])

  useEffect(() => {
    const syncAcrossTabs = (event) => {
      if (event.key !== ITINERARY_STORAGE_KEY) return
      if (document.querySelector('.itinerary-drawer')) {
        setStorageError(
          'Có thay đổi từ một tab khác. LinkCute đang giữ bản bạn chỉnh sửa ở tab này để tránh ghi đè.',
        )
        return
      }
      try {
        dispatch({ type: 'REPLACE_STATE', state: parseItineraryState(event.newValue) })
        setStorageError('')
      } catch {
        setStorageError('Kế hoạch từ một tab khác không thể đồng bộ.')
      }
    }

    window.addEventListener('storage', syncAcrossTabs)
    return () => window.removeEventListener('storage', syncAcrossTabs)
  }, [])

  const sessionUserId = session?.user?.id ? String(session.user.id) : ''
  const plans = useMemo(
    () => state.plans.filter((plan) => isPlanVisibleForSession(plan, sessionUserId)),
    [sessionUserId, state.plans],
  )
  const activePlan = useMemo(
    () => plans.find((plan) => plan.id === state.activePlanId) || plans[0] || null,
    [plans, state.activePlanId],
  )

  const createPlan = useCallback(({ name, date, initialPlace = null }) => {
    const planId = createId('plan')
    const createdAt = new Date().toISOString()
    const planDate = date || todayLocalDate()
    const place = snapshotPlace(initialPlace)
    const items = place
      ? [{
        id: createId('item'),
        place,
        startTime: '',
        endTime: '',
      }]
      : []

    dispatch({
      type: 'CREATE_PLAN',
      plan: {
        id: planId,
        name: name?.trim() || defaultPlanName(planDate),
        date: planDate,
        items,
        createdAt,
        updatedAt: createdAt,
      },
    })
    return planId
  }, [])

  const setActivePlan = useCallback((planId) => {
    if (!plans.some((plan) => plan.id === planId)) return
    dispatch({ type: 'SET_ACTIVE_PLAN', planId })
  }, [plans])

  const updatePlan = useCallback((planId, { name, date }) => {
    dispatch({
      type: 'UPDATE_PLAN_META',
      planId,
      name,
      date,
      updatedAt: new Date().toISOString(),
    })
  }, [])

  const deletePlan = useCallback((planId) => {
    dispatch({ type: 'DELETE_PLAN', planId })
  }, [])

  const addPlace = useCallback((place, requestedPlanId = activePlan?.id) => {
    if (!requestedPlanId) return { ok: false, reason: 'missing-plan' }
    const plan = plans.find((candidate) => candidate.id === requestedPlanId)
    if (!plan) return { ok: false, reason: 'missing-plan' }
    if (plan.items.some((item) => item.place.id === String(place?.id))) {
      return { ok: false, reason: 'duplicate' }
    }

    const snapshot = snapshotPlace(place)
    if (!snapshot) return { ok: false, reason: 'invalid-place' }
    dispatch({
      type: 'ADD_ITEM',
      planId: requestedPlanId,
      item: {
        id: createId('item'),
        place: snapshot,
        startTime: '',
        endTime: '',
      },
      updatedAt: new Date().toISOString(),
    })
    return { ok: true }
  }, [activePlan?.id, plans])

  const removeItem = useCallback((planId, itemId) => {
    dispatch({
      type: 'REMOVE_ITEM',
      planId,
      itemId,
      updatedAt: new Date().toISOString(),
    })
  }, [])

  const moveItem = useCallback((planId, itemId, direction) => {
    dispatch({
      type: 'MOVE_ITEM',
      planId,
      itemId,
      direction,
      updatedAt: new Date().toISOString(),
    })
  }, [])

  const updateItemTime = useCallback((planId, itemId, startTime, endTime) => {
    dispatch({
      type: 'UPDATE_ITEM_TIME',
      planId,
      itemId,
      startTime,
      endTime,
      updatedAt: new Date().toISOString(),
    })
  }, [])

  const isPlaceInActivePlan = useCallback(
    (placeId) => Boolean(activePlan?.items.some((item) => item.place.id === String(placeId))),
    [activePlan],
  )

  const upsertOwnedRemotePlan = useCallback((plan, { forceRemote = false } = {}) => {
    dispatch({ type: 'UPSERT_REMOTE_OWNER_PLAN', plan, forceRemote })
  }, [])

  const clearPlanServerLink = useCallback((planId) => {
    dispatch({ type: 'CLEAR_PLAN_SERVER_LINK', planId })
  }, [])

  const publicState = useMemo(() => ({
    ...state,
    activePlanId: activePlan?.id || null,
    plans,
  }), [activePlan?.id, plans, state])

  return {
    state: publicState,
    plans,
    activePlan,
    storageError,
    createPlan,
    setActivePlan,
    updatePlan,
    deletePlan,
    addPlace,
    removeItem,
    moveItem,
    updateItemTime,
    isPlaceInActivePlan,
    upsertOwnedRemotePlan,
    clearPlanServerLink,
  }
}
