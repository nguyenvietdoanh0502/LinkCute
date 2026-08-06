import assert from 'node:assert/strict'
import test from 'node:test'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

let vite
let PlanPeopleView
let IncomingPlanInvitationsView
let ItineraryPanel

test.before(async () => {
  vite = await createServer({
    root: process.cwd(),
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true },
  })

  const peopleModule = await vite.ssrLoadModule('/src/components/PlanPeopleView.jsx')
  const itineraryModule = await vite.ssrLoadModule('/src/components/ItineraryPanel.jsx')
  PlanPeopleView = peopleModule.default
  IncomingPlanInvitationsView = peopleModule.IncomingPlanInvitationsView
  ItineraryPanel = itineraryModule.default
})

test.after(async () => {
  await vite?.close()
})

const owner = {
  id: '00000000-0000-0000-0000-000000000001',
  fullName: 'Nguyễn Chủ Kế Hoạch',
  pinCode: 'RML-100001',
}

const friend = {
  id: '00000000-0000-0000-0000-000000000002',
  fullName: 'Trần Bạn Bè',
  pinCode: 'RML-100002',
}

const member = {
  id: '00000000-0000-0000-0000-000000000003',
  fullName: 'Lê Thành Viên',
  pinCode: 'RML-100003',
}

const ownerPlan = {
  id: 'plan-local-1',
  remoteId: '10000000-0000-0000-0000-000000000001',
  serverId: '10000000-0000-0000-0000-000000000001',
  name: 'Cuối tuần Hà Nội',
  date: '2026-08-08',
  accessRole: 'OWNER',
  owner,
  members: [{ user: member, joinedAt: '2026-08-05T08:15:30Z' }],
  items: [],
}

test('owner people view renders members and invitation controls', () => {
  const markup = renderToStaticMarkup(React.createElement(PlanPeopleView, {
    plan: ownerPlan,
    session: { user: owner },
    friends: [{ user: friend }],
    busyActions: new Set(),
    invitationStateFor: () => ({ status: 'NONE', invitation: null }),
    onInvite: () => {},
    onCancelInvitation: () => {},
    onRemoveMember: () => {},
    onRefreshFriends: () => {},
    onOpenFriends: () => {},
    onBack: () => {},
    showToast: () => {},
  }))

  assert.match(markup, /Người tham gia/)
  assert.match(markup, /Nguyễn Chủ Kế Hoạch/)
  assert.match(markup, /Lê Thành Viên/)
  assert.match(markup, /Mời thêm bạn/)
  assert.match(markup, /Trần Bạn Bè/)
  assert.match(markup, />Mời</)
  assert.match(markup, /Xóa Lê Thành Viên khỏi kế hoạch/)
})

test('member people view stays read-only and does not render invitation controls', () => {
  const memberPlan = { ...ownerPlan, id: ownerPlan.remoteId, accessRole: 'MEMBER', readOnly: true }
  const markup = renderToStaticMarkup(React.createElement(PlanPeopleView, {
    plan: memberPlan,
    session: { user: member },
    friends: [{ user: friend }],
    busyActions: new Set(),
    onBack: () => {},
  }))

  assert.match(markup, /Đi cùng nhau/)
  assert.match(markup, /Thành viên/)
  assert.doesNotMatch(markup, /Mời thêm bạn/)
  assert.doesNotMatch(markup, /Xóa Lê Thành Viên khỏi kế hoạch/)
})

test('incoming invitation view exposes summary actions without plan items', () => {
  const markup = renderToStaticMarkup(React.createElement(IncomingPlanInvitationsView, {
    invitations: [{
      id: '20000000-0000-0000-0000-000000000001',
      plan: {
        id: ownerPlan.remoteId,
        name: ownerPlan.name,
        date: ownerPlan.date,
        itemCount: 3,
        owner,
      },
      inviter: owner,
      status: 'PENDING',
    }],
    busyActions: new Set(),
    onRefresh: () => {},
    onAccept: () => {},
    onDecline: () => {},
    onBack: () => {},
  }))

  assert.match(markup, /Kế hoạch được chia sẻ/)
  assert.match(markup, /Cuối tuần Hà Nội/)
  assert.match(markup, /3 địa điểm/)
  assert.match(markup, /Tham gia/)
  assert.match(markup, /Từ chối/)
  assert.doesNotMatch(markup, /itinerary-item/)
})

test('itinerary drawer renders member plan as read-only with leave action', () => {
  const memberPlan = {
    ...ownerPlan,
    id: ownerPlan.remoteId,
    accessRole: 'MEMBER',
    readOnly: true,
    items: [],
  }
  const markup = renderToStaticMarkup(React.createElement(ItineraryPanel, {
    open: true,
    plans: [memberPlan],
    activePlan: memberPlan,
    session: { user: member },
    friendshipState: { friends: [] },
    planSharingState: { busyActions: new Set(), incomingCount: 0 },
    onClose: () => {},
    onCreatePlan: () => {},
    onSelectPlan: () => {},
    onUpdatePlan: () => {},
    onDeletePlan: () => {},
    onRemoveItem: () => {},
    onMoveItem: () => {},
    onUpdateItemTime: () => {},
  }))

  assert.match(markup, /Chỉ đọc/)
  assert.match(markup, /Chủ kế hoạch chưa thêm địa điểm nào/)
  assert.match(markup, /Rời kế hoạch/)
  assert.doesNotMatch(markup, /Tên kế hoạch<\/span><input/)
  assert.doesNotMatch(markup, /Xóa kế hoạch/)
})
