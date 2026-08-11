const PLAN_ERROR_MESSAGES = {
  INVALID_PLAN_DATA: 'Dữ liệu kế hoạch chưa hợp lệ. Vui lòng kiểm tra lại.',
  MISSING_PLAN_INVITEE_ID: 'Chưa chọn người bạn cần mời.',
  PLAN_NOT_FOUND: 'Kế hoạch này không còn tồn tại.',
  PLAN_ACCESS_DENIED: 'Bạn không có quyền thao tác với kế hoạch này.',
  PLAN_FORBIDDEN: 'Bạn không có quyền thao tác với kế hoạch này.',
  PLAN_READ_ONLY: 'Chỉ chủ kế hoạch mới có thể chỉnh sửa nội dung.',
  PLAN_VERSION_CONFLICT: 'Kế hoạch vừa được cập nhật ở nơi khác. LinkCute đã tải lại bản mới nhất.',
  PLAN_STALE_VERSION: 'Kế hoạch vừa được cập nhật ở nơi khác. LinkCute đã tải lại bản mới nhất.',
  PLAN_INVITATION_NOT_FOUND: 'Lời mời này không còn tồn tại.',
  PLAN_INVITATION_ALREADY_EXISTS: 'Người bạn này đã nhận lời mời vào kế hoạch.',
  PLAN_INVITATION_ALREADY_PENDING: 'Người bạn này đã nhận lời mời vào kế hoạch.',
  PLAN_MEMBER_ALREADY_EXISTS: 'Người bạn này đã tham gia kế hoạch.',
  PLAN_INVITEE_NOT_FRIEND: 'Bạn chỉ có thể mời người đang ở trong danh sách bạn bè.',
  PLAN_CANNOT_INVITE_SELF: 'Bạn không thể tự mời chính mình.',
  CANNOT_INVITE_SELF_TO_PLAN: 'Bạn không thể tự mời chính mình.',
  PLAN_MEMBER_NOT_FOUND: 'Thành viên này không còn trong kế hoạch.',
  CONCURRENCY_CONFLICT: 'Kế hoạch vừa được cập nhật ở nơi khác. LinkCute đã tải lại bản mới nhất.',
  USER_NOT_EXISTED: 'Không tìm thấy người dùng này.',
  PLAN_OWNER_CANNOT_LEAVE: 'Chủ kế hoạch không thể rời kế hoạch của mình.',
  PLACE_NOT_FOUND: 'Một địa điểm trong kế hoạch không còn tồn tại.',
  UNAUTHENTICATED: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
}

function nullableText(value) {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function nullableNumber(value) {
  if (value === '' || value === null || value === undefined) return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function normalizeUser(raw) {
  if (!raw || !nullableText(raw.id)) return null
  return {
    id: String(raw.id),
    fullName: nullableText(raw.fullName) || 'Thành viên LinkCute',
    avatarUrl: nullableText(raw.avatarUrl),
    pinCode: nullableText(raw.pinCode),
  }
}

function normalizePlace(raw) {
  if (!raw || !nullableText(raw.id) || !nullableText(raw.name)) return null
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

function normalizeRemoteItem(raw, fallbackPosition) {
  const place = normalizePlace(raw?.place)
  const remoteId = nullableText(raw?.id)
  const clientItemId = nullableText(raw?.clientItemId) || remoteId
  if (!place || !remoteId || !clientItemId) return null

  return {
    id: clientItemId,
    remoteId,
    clientItemId,
    position: Number.isInteger(raw.position) ? raw.position : fallbackPosition,
    place,
    startTime: nullableText(raw.startTime) || '',
    endTime: nullableText(raw.endTime) || '',
  }
}

export function normalizeRemotePlan(raw) {
  const id = nullableText(raw?.id)
  if (!id) return null

  const items = Array.isArray(raw.items)
    ? raw.items
      .map(normalizeRemoteItem)
      .filter(Boolean)
      .sort((left, right) => left.position - right.position)
    : []

  return {
    id,
    clientPlanId: nullableText(raw.clientPlanId),
    name: nullableText(raw.name) || 'Kế hoạch chưa đặt tên',
    date: nullableText(raw.date) || '',
    clientUpdatedAt: nullableText(raw.clientUpdatedAt),
    version: Number.isInteger(raw.version) ? raw.version : nullableNumber(raw.version),
    accessRole: raw.accessRole === 'MEMBER' ? 'MEMBER' : 'OWNER',
    owner: normalizeUser(raw.owner),
    members: Array.isArray(raw.members)
      ? raw.members
        .map((member) => ({
          user: normalizeUser(member?.user),
          joinedAt: nullableText(member?.joinedAt),
        }))
        .filter((member) => member.user)
      : [],
    items,
    createdAt: nullableText(raw.createdAt),
    updatedAt: nullableText(raw.updatedAt),
  }
}

export function normalizeRemotePlans(raw) {
  return Array.isArray(raw) ? raw.map(normalizeRemotePlan).filter(Boolean) : []
}

export function normalizePlanInvitation(raw) {
  const id = nullableText(raw?.id)
  const planId = nullableText(raw?.plan?.id)
  if (!id || !planId) return null

  return {
    id,
    plan: {
      id: planId,
      name: nullableText(raw.plan.name) || 'Kế hoạch chưa đặt tên',
      date: nullableText(raw.plan.date) || '',
      itemCount: Math.max(0, Number(raw.plan.itemCount) || 0),
      owner: normalizeUser(raw.plan.owner),
    },
    inviter: normalizeUser(raw.inviter),
    invitee: normalizeUser(raw.invitee),
    status: nullableText(raw.status) || 'PENDING',
    sentAt: nullableText(raw.sentAt),
    respondedAt: nullableText(raw.respondedAt),
  }
}

export function normalizePlanInvitations(raw) {
  return Array.isArray(raw) ? raw.map(normalizePlanInvitation).filter(Boolean) : []
}

function syncPlace(place) {
  const normalized = normalizePlace(place)
  if (!normalized) return null
  return normalized
}

export function buildPlanSyncRequest(plan, expectedVersion = null) {
  if (!plan?.id || !plan?.name || !plan?.date) {
    throw new Error('Kế hoạch chưa đủ thông tin để chia sẻ.')
  }

  const items = (plan.items || []).map((item, position) => {
    const place = syncPlace(item.place)
    if (!place || !item?.id) {
      throw new Error('Một địa điểm trong kế hoạch chưa đủ thông tin để đồng bộ.')
    }
    return {
      clientItemId: String(item.clientItemId || item.id),
      position,
      place,
      startTime: nullableText(item.startTime),
      endTime: nullableText(item.endTime),
    }
  })

  return {
    clientPlanId: String(plan.clientPlanId || plan.id),
    name: String(plan.name).trim(),
    date: plan.date,
    clientUpdatedAt: plan.updatedAt || new Date().toISOString(),
    expectedVersion: Number.isInteger(expectedVersion) ? expectedVersion : null,
    items,
  }
}

export function remotePlanToLocalPlan(remotePlan) {
  const plan = normalizeRemotePlan(remotePlan)
  if (!plan || plan.accessRole !== 'OWNER' || !plan.owner?.id) return null

  return {
    id: plan.clientPlanId || `plan-${plan.id}`,
    name: plan.name,
    date: plan.date,
    items: plan.items.map((item) => ({
      id: item.clientItemId,
      place: item.place,
      startTime: item.startTime,
      endTime: item.endTime,
    })),
    createdAt: plan.createdAt || plan.updatedAt || new Date().toISOString(),
    updatedAt: plan.clientUpdatedAt || plan.updatedAt || new Date().toISOString(),
    serverId: plan.id,
    serverOwnerId: plan.owner.id,
    serverVersion: plan.version,
  }
}

export function remoteMemberPlanToView(remotePlan) {
  const plan = normalizeRemotePlan(remotePlan)
  if (!plan || plan.accessRole !== 'MEMBER') return null
  return {
    ...plan,
    remoteId: plan.id,
    published: true,
    readOnly: true,
    source: 'remote-member',
  }
}

export function localOwnerPlanToView(localPlan, remotePlan, sessionUser) {
  const remote = normalizeRemotePlan(remotePlan)
  const isPublished = Boolean(remote && remote.accessRole === 'OWNER')
  return {
    ...localPlan,
    clientPlanId: localPlan.id,
    remoteId: isPublished ? remote.id : localPlan.serverId || null,
    serverId: isPublished ? remote.id : localPlan.serverId || null,
    serverOwnerId: isPublished ? remote.owner?.id : localPlan.serverOwnerId || null,
    serverVersion: isPublished ? remote.version : localPlan.serverVersion ?? null,
    version: isPublished ? remote.version : localPlan.serverVersion ?? null,
    accessRole: 'OWNER',
    owner: remote?.owner || (sessionUser ? normalizeUser(sessionUser) : null),
    members: remote?.members || [],
    published: isPublished || Boolean(localPlan.serverId),
    readOnly: false,
    source: 'local-owner',
  }
}

export function findOutgoingPlanInvitation(outgoingInvitations, planId, userId) {
  return (outgoingInvitations || []).find((invitation) => (
    invitation.plan?.id === planId && invitation.invitee?.id === userId
  )) || null
}

export function isPlanMember(plan, userId) {
  return Boolean(plan?.members?.some((member) => member.user?.id === userId))
}

export function planNeedsSync(localPlan, remotePlan) {
  if (!localPlan || !remotePlan || remotePlan.accessRole !== 'OWNER') return false
  const localTime = Date.parse(localPlan.updatedAt || '')
  const remoteTime = Date.parse(remotePlan.clientUpdatedAt || '')
  if (Number.isFinite(localTime) && Number.isFinite(remoteTime)) return localTime > remoteTime
  return String(localPlan.updatedAt || '') !== String(remotePlan.clientUpdatedAt || '')
}

export function planSharingErrorMessage(error, fallback = 'Chưa thể cập nhật kế hoạch. Vui lòng thử lại.') {
  if (error?.errorCode && PLAN_ERROR_MESSAGES[error.errorCode]) {
    return PLAN_ERROR_MESSAGES[error.errorCode]
  }
  if (error?.status === 401) return PLAN_ERROR_MESSAGES.UNAUTHENTICATED
  if (error?.status === 403) return PLAN_ERROR_MESSAGES.PLAN_ACCESS_DENIED
  if (error?.status === 404) return 'Kế hoạch hoặc lời mời này không còn tồn tại.'
  if (error?.status === 409) return PLAN_ERROR_MESSAGES.PLAN_VERSION_CONFLICT
  return error?.message || fallback
}

export function isPlanVersionConflict(error) {
  return (error?.status === 409 && (!error?.errorCode || error.errorCode === 'CONCURRENCY_CONFLICT'))
    || error?.errorCode === 'CONCURRENCY_CONFLICT'
    || error?.errorCode === 'PLAN_VERSION_CONFLICT'
    || error?.errorCode === 'PLAN_STALE_VERSION'
}
