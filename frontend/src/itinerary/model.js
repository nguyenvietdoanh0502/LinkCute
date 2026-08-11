export const ITINERARY_STORAGE_KEY = 'linkcute.itineraries.v1'
export const ITINERARY_VERSION = 1

const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/
const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/

export function createEmptyItineraryState() {
  return {
    version: ITINERARY_VERSION,
    activePlanId: null,
    plans: [],
  }
}

function nullableNumber(value) {
  if (value === '' || value === null || value === undefined) return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function nullableText(value) {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function nullableInteger(value) {
  if (value === '' || value === null || value === undefined) return null
  const number = Number(value)
  return Number.isInteger(number) && number >= 0 ? number : null
}

function normalizeTime(value) {
  return typeof value === 'string' && TIME_PATTERN.test(value) ? value : ''
}

function normalizeDate(value) {
  const match = DATE_PATTERN.exec(value || '')
  if (!match) return null
  const [, yearText, monthText, dayText] = match
  const year = Number(yearText)
  const month = Number(monthText)
  const day = Number(dayText)
  const date = new Date(year, month - 1, day)
  return date.getFullYear() === year
    && date.getMonth() === month - 1
    && date.getDate() === day
    ? value
    : null
}

function normalizePlace(raw) {
  if (!raw || typeof raw !== 'object' || !nullableText(raw.id) || !nullableText(raw.name)) {
    return null
  }

  return {
    id: String(raw.id),
    name: String(raw.name).trim(),
    address: nullableText(raw.address),
    district: nullableText(raw.district),
    category: nullableText(raw.category) || 'OTHER',
    photoUrl: nullableText(raw.photoUrl),
    lat: nullableNumber(raw.lat),
    lng: nullableNumber(raw.lng),
    priceLevel: nullableNumber(raw.priceLevel),
    priceMin: nullableNumber(raw.priceMin),
    priceMax: nullableNumber(raw.priceMax),
  }
}

function normalizeItem(raw) {
  const place = normalizePlace(raw?.place)
  if (!raw || typeof raw !== 'object' || !nullableText(raw.id) || !place) return null

  return {
    id: String(raw.id),
    place,
    startTime: normalizeTime(raw.startTime),
    endTime: normalizeTime(raw.endTime),
  }
}

function normalizePlan(raw) {
  if (!raw || typeof raw !== 'object' || !nullableText(raw.id)) return null

  const items = Array.isArray(raw.items)
    ? raw.items.map(normalizeItem).filter(Boolean)
    : []
  const uniqueItems = items.filter(
    (item, index) => items.findIndex((candidate) => candidate.place.id === item.place.id) === index,
  )

  const plan = {
    id: String(raw.id),
    name: nullableText(raw.name) || 'Kế hoạch chưa đặt tên',
    date: normalizeDate(raw.date) || todayLocalDate(),
    items: uniqueItems,
    createdAt: nullableText(raw.createdAt) || new Date().toISOString(),
    updatedAt: nullableText(raw.updatedAt) || new Date().toISOString(),
  }

  const serverId = nullableText(raw.serverId)
  const serverOwnerId = nullableText(raw.serverOwnerId)
  if (serverOwnerId) {
    plan.serverOwnerId = serverOwnerId
    if (serverId) plan.serverId = serverId
    plan.serverVersion = nullableInteger(raw.serverVersion)
  }
  return plan
}

function timestamp(value) {
  const parsed = Date.parse(value || '')
  return Number.isFinite(parsed) ? parsed : 0
}

export function mergeRemoteOwnerPlan(localPlan, remotePlan) {
  if (!localPlan) return remotePlan
  if (!remotePlan) return localPlan

  const serverMetadata = {
    serverId: remotePlan.serverId,
    serverOwnerId: remotePlan.serverOwnerId,
    serverVersion: remotePlan.serverVersion,
  }

  if (timestamp(remotePlan.updatedAt) > timestamp(localPlan.updatedAt)) {
    return {
      ...remotePlan,
      createdAt: localPlan.createdAt || remotePlan.createdAt,
      ...serverMetadata,
    }
  }

  return {
    ...localPlan,
    ...serverMetadata,
  }
}

export function isPlanVisibleForSession(plan, sessionUserId) {
  if (!plan?.serverOwnerId) return true
  return Boolean(sessionUserId && plan.serverOwnerId === String(sessionUserId))
}

export function parseItineraryState(raw) {
  if (!raw) return createEmptyItineraryState()

  const parsed = JSON.parse(raw)
  if (!parsed || typeof parsed !== 'object' || parsed.version !== ITINERARY_VERSION) {
    throw new Error('Dữ liệu kế hoạch không đúng phiên bản.')
  }

  const plans = Array.isArray(parsed.plans)
    ? parsed.plans.map(normalizePlan).filter(Boolean)
    : []
  const requestedActiveId = nullableText(parsed.activePlanId)
  const activePlanId = plans.some((plan) => plan.id === requestedActiveId)
    ? requestedActiveId
    : plans[0]?.id || null

  return {
    version: ITINERARY_VERSION,
    activePlanId,
    plans,
  }
}

export function loadItineraryState(storage) {
  return parseItineraryState(storage.getItem(ITINERARY_STORAGE_KEY))
}

export function saveItineraryState(storage, state) {
  storage.setItem(ITINERARY_STORAGE_KEY, JSON.stringify(state))
}

export function todayLocalDate(now = new Date()) {
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function defaultPlanName(date) {
  const [year, month, day] = String(date || '').split('-')
  return year && month && day ? `Kế hoạch ${day}/${month}/${year}` : 'Kế hoạch mới'
}

export function snapshotPlace(place) {
  const photoUrl = place?.photoUrl || place?.photos?.find((photo) => photo?.url)?.url
  return normalizePlace({
    id: place?.id,
    name: place?.name,
    address: place?.address,
    district: place?.district,
    category: place?.category,
    photoUrl,
    lat: place?.lat,
    lng: place?.lng,
    priceLevel: place?.priceLevel,
    priceMin: place?.priceMin,
    priceMax: place?.priceMax,
  })
}

function touchPlan(plan, updatedAt) {
  const previousTime = Date.parse(plan?.updatedAt || '')
  const requestedTime = Date.parse(updatedAt || '')
  const nextTime = Math.max(
    Number.isFinite(requestedTime) ? requestedTime : Date.now(),
    Number.isFinite(previousTime) ? previousTime + 1 : 0,
  )
  return { ...plan, updatedAt: new Date(nextTime).toISOString() }
}

export function itineraryReducer(state, action) {
  switch (action.type) {
    case 'REPLACE_STATE':
      return action.state

    case 'CREATE_PLAN':
      return {
        ...state,
        activePlanId: action.plan.id,
        plans: [...state.plans, action.plan],
      }

    case 'SET_ACTIVE_PLAN':
      return state.plans.some((plan) => plan.id === action.planId)
        ? { ...state, activePlanId: action.planId }
        : state

    case 'UPSERT_REMOTE_OWNER_PLAN': {
      const incoming = normalizePlan(action.plan)
      if (!incoming) return state
      const index = state.plans.findIndex((plan) => (
        plan.id === incoming.id || (plan.serverId && plan.serverId === incoming.serverId)
      ))
      if (index === -1) {
        return {
          ...state,
          activePlanId: state.activePlanId || incoming.id,
          plans: [...state.plans, incoming],
        }
      }

      const current = state.plans[index]
      if (
        current.serverOwnerId
        && incoming.serverOwnerId
        && current.serverOwnerId !== incoming.serverOwnerId
      ) {
        const conflictSafePlan = { ...incoming, id: `plan-${incoming.serverId}` }
        return state.plans.some((plan) => plan.id === conflictSafePlan.id)
          ? state
          : { ...state, plans: [...state.plans, conflictSafePlan] }
      }

      const plans = [...state.plans]
      plans[index] = action.forceRemote
        ? { ...incoming, createdAt: current.createdAt || incoming.createdAt }
        : mergeRemoteOwnerPlan(current, incoming)
      return { ...state, plans }
    }

    case 'CLEAR_PLAN_SERVER_LINK':
      return {
        ...state,
        plans: state.plans.map((plan) => plan.id === action.planId
          ? {
            ...plan,
            serverId: null,
            serverOwnerId: plan.serverOwnerId || action.serverOwnerId || null,
            serverVersion: null,
          }
          : plan),
      }

    case 'UPDATE_PLAN_META': {
      const nextDate = normalizeDate(action.date)
      return {
        ...state,
        plans: state.plans.map((plan) => plan.id === action.planId
          ? touchPlan({
            ...plan,
            name: nullableText(action.name) || defaultPlanName(nextDate || plan.date),
            date: nextDate || plan.date,
          }, action.updatedAt)
          : plan),
      }
    }

    case 'DELETE_PLAN': {
      const plans = state.plans.filter((plan) => plan.id !== action.planId)
      return {
        ...state,
        plans,
        activePlanId: state.activePlanId === action.planId
          ? plans[0]?.id || null
          : state.activePlanId,
      }
    }

    case 'ADD_ITEM':
      return {
        ...state,
        plans: state.plans.map((plan) => {
          if (plan.id !== action.planId) return plan
          if (plan.items.some((item) => item.place.id === action.item.place.id)) return plan
          return touchPlan({ ...plan, items: [...plan.items, action.item] }, action.updatedAt)
        }),
      }

    case 'REMOVE_ITEM':
      return {
        ...state,
        plans: state.plans.map((plan) => plan.id === action.planId
          ? touchPlan({
            ...plan,
            items: plan.items.filter((item) => item.id !== action.itemId),
          }, action.updatedAt)
          : plan),
      }

    case 'MOVE_ITEM':
      return {
        ...state,
        plans: state.plans.map((plan) => {
          if (plan.id !== action.planId) return plan
          const currentIndex = plan.items.findIndex((item) => item.id === action.itemId)
          const targetIndex = currentIndex + action.direction
          if (currentIndex < 0 || targetIndex < 0 || targetIndex >= plan.items.length) return plan
          const items = [...plan.items]
          const [item] = items.splice(currentIndex, 1)
          items.splice(targetIndex, 0, item)
          return touchPlan({ ...plan, items }, action.updatedAt)
        }),
      }

    case 'UPDATE_ITEM_TIME': {
      const startTime = normalizeTime(action.startTime)
      const endTime = normalizeTime(action.endTime)
      if (!hasValidTimeRange(startTime, endTime)) return state
      return {
        ...state,
        plans: state.plans.map((plan) => plan.id === action.planId
          ? touchPlan({
            ...plan,
            items: plan.items.map((item) => item.id === action.itemId
              ? {
                ...item,
                startTime,
                endTime,
              }
              : item),
          }, action.updatedAt)
          : plan),
      }
    }

    default:
      return state
  }
}

export function calculatePlanBudget(plan) {
  const items = plan?.items || []
  let min = 0
  let max = 0
  let pricedCount = 0

  items.forEach(({ place }) => {
    if (
      Number.isFinite(place.priceMin)
      && Number.isFinite(place.priceMax)
      && place.priceMin >= 0
      && place.priceMax >= place.priceMin
    ) {
      min += place.priceMin
      max += place.priceMax
      pricedCount += 1
    }
  })

  return {
    min,
    max,
    pricedCount,
    missingCount: items.length - pricedCount,
  }
}

function timeToMinutes(value) {
  if (!TIME_PATTERN.test(value || '')) return null
  const [hour, minute] = value.split(':').map(Number)
  return hour * 60 + minute
}

export function hasValidTimeRange(startTime, endTime) {
  if (!startTime || !endTime) return true
  const start = timeToMinutes(startTime)
  const end = timeToMinutes(endTime)
  return start !== null && end !== null && end > start
}

export function findOverlappingItemIds(plan) {
  const scheduled = (plan?.items || [])
    .map((item) => ({
      id: item.id,
      start: timeToMinutes(item.startTime),
      end: timeToMinutes(item.endTime),
    }))
    .filter((item) => item.start !== null && item.end !== null && item.end > item.start)
    .sort((left, right) => left.start - right.start)
  const overlaps = new Set()

  for (let leftIndex = 0; leftIndex < scheduled.length; leftIndex += 1) {
    for (let rightIndex = leftIndex + 1; rightIndex < scheduled.length; rightIndex += 1) {
      const left = scheduled[leftIndex]
      const right = scheduled[rightIndex]
      if (right.start >= left.end) break
      overlaps.add(left.id)
      overlaps.add(right.id)
    }
  }

  return overlaps
}
