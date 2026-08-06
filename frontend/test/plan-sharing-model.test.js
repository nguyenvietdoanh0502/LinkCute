import assert from 'node:assert/strict'
import test from 'node:test'
import {
  isPlanVisibleForSession,
  itineraryReducer,
  mergeRemoteOwnerPlan,
} from '../src/itinerary/model.js'
import {
  buildPlanSyncRequest,
  normalizePlanInvitation,
  normalizeRemotePlan,
  planNeedsSync,
  planSharingErrorMessage,
  remoteMemberPlanToView,
  remotePlanToLocalPlan,
} from '../src/itinerary/sharing.js'

const OWNER = {
  id: 'user-owner',
  fullName: 'Nguyễn Minh Anh',
  avatarUrl: null,
  pinCode: 'RML-123456',
}

function remotePlan(overrides = {}) {
  return {
    id: 'server-plan-1',
    clientPlanId: 'plan-local-1',
    name: 'Cuối tuần Hà Nội',
    date: '2026-08-09',
    clientUpdatedAt: '2026-08-05T10:00:00.000Z',
    version: 3,
    accessRole: 'OWNER',
    owner: OWNER,
    members: [{ user: { id: 'user-2', fullName: 'Lan' }, joinedAt: '2026-08-05T09:00:00Z' }],
    items: [
      {
        id: 'server-item-2',
        clientItemId: 'item-local-2',
        position: 1,
        place: { id: 'place-2', name: 'Quán ăn', category: 'FOOD', lat: null, lng: null },
        startTime: null,
        endTime: null,
      },
      {
        id: 'server-item-1',
        clientItemId: 'item-local-1',
        position: 0,
        place: { id: 'place-1', name: 'Quán cà phê', category: 'CAFE', lat: 21.02, lng: 105.8 },
        startTime: '09:00',
        endTime: '10:00',
      },
    ],
    createdAt: '2026-08-05T08:00:00Z',
    updatedAt: '2026-08-05T10:00:01Z',
    ...overrides,
  }
}

function localPlan(overrides = {}) {
  return {
    id: 'plan-local-1',
    name: 'Cuối tuần Hà Nội',
    date: '2026-08-09',
    items: [
      {
        id: 'item-local-1',
        place: {
          id: 'place-1',
          name: 'Quán cà phê',
          address: null,
          district: null,
          category: 'CAFE',
          photoUrl: null,
          lat: null,
          lng: null,
          priceLevel: null,
          priceMin: null,
          priceMax: null,
        },
        startTime: '',
        endTime: '',
      },
    ],
    createdAt: '2026-08-05T08:00:00.000Z',
    updatedAt: '2026-08-05T10:00:00.000Z',
    ...overrides,
  }
}

test('normalizes remote plans, preserves access role and sorts itinerary items', () => {
  const ownerPlan = normalizeRemotePlan(remotePlan())
  const memberPlan = remoteMemberPlanToView(remotePlan({ accessRole: 'MEMBER' }))

  assert.equal(ownerPlan.accessRole, 'OWNER')
  assert.deepEqual(ownerPlan.items.map((item) => item.id), ['item-local-1', 'item-local-2'])
  assert.equal(ownerPlan.items[0].remoteId, 'server-item-1')
  assert.equal(ownerPlan.members[0].user.fullName, 'Lan')
  assert.equal(memberPlan.accessRole, 'MEMBER')
  assert.equal(memberPlan.readOnly, true)
  assert.equal(memberPlan.source, 'remote-member')
})

test('normalizes incoming invitation previews without exposing plan items', () => {
  const invitation = normalizePlanInvitation({
    id: 'invite-1',
    plan: { id: 'server-plan-1', name: 'Đi chơi', date: '2026-08-09', itemCount: 4, owner: OWNER },
    inviter: OWNER,
    invitee: { id: 'user-2', fullName: 'Lan' },
    status: 'PENDING',
    sentAt: '2026-08-05T10:00:00Z',
  })

  assert.equal(invitation.plan.itemCount, 4)
  assert.equal('items' in invitation.plan, false)
  assert.equal(invitation.inviter.id, OWNER.id)
})

test('builds the exact sync payload with stable order and nullable times and coordinates', () => {
  const plan = localPlan({
    items: [
      localPlan().items[0],
      {
        id: 'item-local-2',
        place: { id: 'place-2', name: 'Công viên', category: 'OTHER', lat: 21.1, lng: 105.9 },
        startTime: '15:00',
        endTime: '16:30',
      },
    ],
  })
  const payload = buildPlanSyncRequest(plan, 7)

  assert.deepEqual(Object.keys(payload), [
    'clientPlanId',
    'name',
    'date',
    'clientUpdatedAt',
    'expectedVersion',
    'items',
  ])
  assert.deepEqual(payload.items.map((item) => item.position), [0, 1])
  assert.equal(payload.items[0].startTime, null)
  assert.equal(payload.items[0].endTime, null)
  assert.equal(payload.items[0].place.lat, null)
  assert.equal(payload.items[0].place.lng, null)
  assert.equal(payload.items[1].startTime, '15:00')
  assert.equal(payload.expectedVersion, 7)
})

test('reconciles timestamps: server newer adopts, local newer remains and needs sync', () => {
  const remoteLocal = remotePlanToLocalPlan(remotePlan())
  const olderLocal = localPlan({ updatedAt: '2026-08-05T09:59:59.000Z', name: 'Bản cũ' })
  const newerLocal = localPlan({ updatedAt: '2026-08-05T10:00:01.000Z', name: 'Bản mới' })

  assert.equal(mergeRemoteOwnerPlan(olderLocal, remoteLocal).name, 'Cuối tuần Hà Nội')
  assert.equal(mergeRemoteOwnerPlan(newerLocal, remoteLocal).name, 'Bản mới')
  assert.equal(planNeedsSync(newerLocal, normalizeRemotePlan(remotePlan())), true)
  assert.equal(planNeedsSync(olderLocal, normalizeRemotePlan(remotePlan())), false)
})

test('published owner cache is scoped to its account even after clearing a missing server link', () => {
  const cached = {
    ...localPlan(),
    serverId: 'server-plan-1',
    serverOwnerId: OWNER.id,
    serverVersion: 2,
  }
  const state = { version: 1, activePlanId: cached.id, plans: [cached] }
  const cleared = itineraryReducer(state, { type: 'CLEAR_PLAN_SERVER_LINK', planId: cached.id })

  assert.equal(cleared.plans[0].serverId, null)
  assert.equal(cleared.plans[0].serverOwnerId, OWNER.id)
  assert.equal(isPlanVisibleForSession(cleared.plans[0], OWNER.id), true)
  assert.equal(isPlanVisibleForSession(cleared.plans[0], 'another-user'), false)
  assert.equal(isPlanVisibleForSession(cleared.plans[0], ''), false)
})

test('local edit timestamps always advance beyond an adopted future timestamp', () => {
  const initial = {
    version: 1,
    activePlanId: 'plan-local-1',
    plans: [localPlan({ updatedAt: '2030-01-01T00:00:00.000Z' })],
  }
  const next = itineraryReducer(initial, {
    type: 'UPDATE_PLAN_META',
    planId: 'plan-local-1',
    name: 'Tên mới',
    date: '2026-08-10',
    updatedAt: '2026-08-05T10:00:00.000Z',
  })

  assert.equal(next.plans[0].updatedAt, '2030-01-01T00:00:00.001Z')
})

test('maps exact backend sharing errors to Vietnamese messages', () => {
  assert.equal(
    planSharingErrorMessage({ errorCode: 'PLAN_INVITATION_ALREADY_PENDING' }),
    'Người bạn này đã nhận lời mời vào kế hoạch.',
  )
  assert.equal(
    planSharingErrorMessage({ errorCode: 'CANNOT_INVITE_SELF_TO_PLAN' }),
    'Bạn không thể tự mời chính mình.',
  )
  assert.match(planSharingErrorMessage({ errorCode: 'CONCURRENCY_CONFLICT' }), /cập nhật ở nơi khác/)
})
