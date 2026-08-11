import assert from 'node:assert/strict'
import test from 'node:test'
import {
  ITINERARY_STORAGE_KEY,
  calculatePlanBudget,
  createEmptyItineraryState,
  defaultPlanName,
  findOverlappingItemIds,
  hasValidTimeRange,
  itineraryReducer,
  loadItineraryState,
  parseItineraryState,
  saveItineraryState,
  snapshotPlace,
} from '../src/itinerary/model.js'

const NOW = '2026-07-24T10:00:00.000Z'

function place(id, overrides = {}) {
  return {
    id,
    name: `Địa điểm ${id}`,
    address: null,
    district: 'Hoàn Kiếm',
    category: 'FOOD',
    photoUrl: null,
    lat: null,
    lng: null,
    priceLevel: null,
    priceMin: null,
    priceMax: null,
    ...overrides,
  }
}

function item(id, placeValue, overrides = {}) {
  return {
    id,
    place: placeValue,
    startTime: '',
    endTime: '',
    ...overrides,
  }
}

function plan(overrides = {}) {
  return {
    id: 'plan-1',
    name: 'Cuối tuần Hà Nội',
    date: '2026-07-25',
    items: [],
    createdAt: NOW,
    updatedAt: NOW,
    ...overrides,
  }
}

test('creates a plan and makes it active', () => {
  const next = itineraryReducer(createEmptyItineraryState(), {
    type: 'CREATE_PLAN',
    plan: plan(),
  })

  assert.equal(next.activePlanId, 'plan-1')
  assert.equal(next.plans.length, 1)
  assert.equal(next.plans[0].name, 'Cuối tuần Hà Nội')
})

test('adds a place once and rejects duplicate data at reducer boundary', () => {
  const initial = {
    version: 1,
    activePlanId: 'plan-1',
    plans: [plan()],
  }
  const first = itineraryReducer(initial, {
    type: 'ADD_ITEM',
    planId: 'plan-1',
    item: item('item-1', place('place-1')),
    updatedAt: NOW,
  })
  const duplicate = itineraryReducer(first, {
    type: 'ADD_ITEM',
    planId: 'plan-1',
    item: item('item-2', place('place-1')),
    updatedAt: NOW,
  })

  assert.equal(first.plans[0].items.length, 1)
  assert.equal(duplicate.plans[0].items.length, 1)
})

test('moves, updates and removes itinerary items', () => {
  const initial = {
    version: 1,
    activePlanId: 'plan-1',
    plans: [plan({
      items: [
        item('item-a', place('place-a')),
        item('item-b', place('place-b')),
      ],
    })],
  }
  const moved = itineraryReducer(initial, {
    type: 'MOVE_ITEM',
    planId: 'plan-1',
    itemId: 'item-b',
    direction: -1,
    updatedAt: NOW,
  })
  const timed = itineraryReducer(moved, {
    type: 'UPDATE_ITEM_TIME',
    planId: 'plan-1',
    itemId: 'item-b',
    startTime: '10:00',
    endTime: '11:30',
    updatedAt: NOW,
  })
  const removed = itineraryReducer(timed, {
    type: 'REMOVE_ITEM',
    planId: 'plan-1',
    itemId: 'item-a',
    updatedAt: NOW,
  })

  assert.deepEqual(moved.plans[0].items.map((value) => value.id), ['item-b', 'item-a'])
  assert.equal(timed.plans[0].items[0].startTime, '10:00')
  assert.deepEqual(removed.plans[0].items.map((value) => value.id), ['item-b'])
})

test('deleting an active plan selects the next available plan', () => {
  const initial = {
    version: 1,
    activePlanId: 'plan-1',
    plans: [plan(), plan({ id: 'plan-2', name: 'Plan 2' })],
  }
  const next = itineraryReducer(initial, { type: 'DELETE_PLAN', planId: 'plan-1' })

  assert.equal(next.activePlanId, 'plan-2')
  assert.deepEqual(next.plans.map((value) => value.id), ['plan-2'])
})

test('calculates budget only from places with an explicit range', () => {
  const result = calculatePlanBudget(plan({
    items: [
      item('a', place('a', { priceMin: 50_000, priceMax: 100_000 })),
      item('b', place('b', { priceMin: 80_000, priceMax: 120_000 })),
      item('c', place('c')),
    ],
  }))

  assert.deepEqual(result, {
    min: 130_000,
    max: 220_000,
    pricedCount: 2,
    missingCount: 1,
  })
})

test('ignores invalid price ranges and derives a local-date plan name', () => {
  const result = calculatePlanBudget(plan({
    items: [
      item('a', place('a', { priceMin: 100_000, priceMax: 50_000 })),
      item('b', place('b', { priceMin: -1, priceMax: 20_000 })),
    ],
  }))

  assert.deepEqual(result, {
    min: 0,
    max: 0,
    pricedCount: 0,
    missingCount: 2,
  })
  assert.equal(defaultPlanName('2026-07-25'), 'Kế hoạch 25/07/2026')
})

test('keeps the minimal offline place snapshot including coordinates', () => {
  const snapshot = snapshotPlace({
    id: 'place-1',
    name: 'Hồ Gươm',
    address: 'Hoàn Kiếm',
    district: 'Hoàn Kiếm',
    category: 'OTHER',
    lat: 21.028511,
    lng: 105.804817,
    photos: [{ url: 'https://example.com/photo.jpg' }],
    reviews: [{ text: 'Không được lưu' }],
  })

  assert.deepEqual(snapshot, {
    id: 'place-1',
    name: 'Hồ Gươm',
    address: 'Hoàn Kiếm',
    district: 'Hoàn Kiếm',
    category: 'OTHER',
    photoUrl: 'https://example.com/photo.jpg',
    lat: 21.028511,
    lng: 105.804817,
    priceLevel: null,
    priceMin: null,
    priceMax: null,
  })
  assert.equal('reviews' in snapshot, false)
})

test('validates time ranges and identifies overlapping items', () => {
  const value = plan({
    items: [
      item('a', place('a'), { startTime: '10:00', endTime: '11:30' }),
      item('b', place('b'), { startTime: '11:00', endTime: '12:00' }),
      item('c', place('c'), { startTime: '12:00', endTime: '13:00' }),
    ],
  })

  assert.equal(hasValidTimeRange('10:00', '11:30'), true)
  assert.equal(hasValidTimeRange('11:30', '11:30'), false)
  assert.equal(hasValidTimeRange('12:00', '11:30'), false)
  assert.deepEqual([...findOverlappingItemIds(value)].sort(), ['a', 'b'])
})

test('does not persist an invalid time range', () => {
  const initial = {
    version: 1,
    activePlanId: 'plan-1',
    plans: [plan({
      items: [item('a', place('a'), { startTime: '10:00', endTime: '11:00' })],
    })],
  }
  const next = itineraryReducer(initial, {
    type: 'UPDATE_ITEM_TIME',
    planId: 'plan-1',
    itemId: 'a',
    startTime: '12:00',
    endTime: '11:30',
    updatedAt: NOW,
  })

  assert.strictEqual(next, initial)
})

test('round-trips state through storage and rejects corrupt JSON', () => {
  const values = new Map()
  const storage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
  }
  const state = {
    version: 1,
    activePlanId: 'plan-1',
    plans: [plan({ items: [item('item-1', place('place-1'))] })],
  }

  saveItineraryState(storage, state)
  assert.equal(values.has(ITINERARY_STORAGE_KEY), true)
  assert.deepEqual(loadItineraryState(storage), state)
  assert.throws(() => parseItineraryState('{not-json'))

  const invalidDateState = parseItineraryState(JSON.stringify({
    version: 1,
    activePlanId: 'plan-1',
    plans: [plan({ date: '2026-99-99' })],
  }))
  assert.notEqual(invalidDateState.plans[0].date, '2026-99-99')
})
