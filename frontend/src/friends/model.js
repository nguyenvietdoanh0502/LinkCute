export const FRIEND_PIN_PATTERN = /^RML-\d{6}$/

const FRIENDSHIP_ERROR_MESSAGES = {
  CANNOT_FRIEND_SELF: 'Bạn không thể gửi lời mời kết bạn cho chính mình.',
  FRIEND_REQUEST_SELF: 'Bạn không thể gửi lời mời kết bạn cho chính mình.',
  FRIENDSHIP_SELF_REQUEST: 'Bạn không thể gửi lời mời kết bạn cho chính mình.',
  ALREADY_FRIENDS: 'Hai bạn đã là bạn bè.',
  FRIENDSHIP_ALREADY_EXISTS: 'Hai bạn đã là bạn bè.',
  FRIEND_REQUEST_ALREADY_EXISTS: 'Lời mời kết bạn này đã tồn tại.',
  FRIEND_REQUEST_NOT_FOUND: 'Lời mời này không còn tồn tại.',
  FRIENDSHIP_NOT_FOUND: 'Quan hệ bạn bè này không còn tồn tại.',
  USER_NOT_EXISTED: 'Không tìm thấy thành viên với mã này.',
  FRIEND_USER_NOT_FOUND: 'Không tìm thấy thành viên với mã này.',
  CONCURRENCY_CONFLICT: 'Dữ liệu vừa được thay đổi ở nơi khác. Vui lòng tải lại.',
  UNAUTHENTICATED: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
}

export function normalizeFriendPin(value) {
  const normalized = String(value || '')
    .trim()
    .toUpperCase()
    .replace(/\s+/g, '')

  if (/^\d{6}$/.test(normalized)) return `RML-${normalized}`
  return normalized.replace(/^RML(?=\d{6}$)/, 'RML-')
}

export function isValidFriendPin(value) {
  return FRIEND_PIN_PATTERN.test(normalizeFriendPin(value))
}

export function profileInitials(fullName) {
  const words = String(fullName || '')
    .trim()
    .split(/\s+/)
    .filter(Boolean)

  if (words.length === 0) return 'L'
  return words.slice(-2).map((word) => word.charAt(0)).join('').toUpperCase()
}

export function syncSearchRelationship(searchResult, overview) {
  if (!searchResult) return searchResult

  const userId = searchResult.id
  const friend = overview?.friends?.find((item) => item.user?.id === userId)
  if (friend) {
    return {
      ...searchResult,
      relationshipStatus: 'FRIENDS',
      friendshipId: friend.friendshipId,
    }
  }

  const incomingRequest = overview?.incomingRequests?.find((item) => item.user?.id === userId)
  if (incomingRequest) {
    return {
      ...searchResult,
      relationshipStatus: 'INCOMING_PENDING',
      friendshipId: incomingRequest.id,
    }
  }

  const outgoingRequest = overview?.outgoingRequests?.find((item) => item.user?.id === userId)
  if (outgoingRequest) {
    return {
      ...searchResult,
      relationshipStatus: 'OUTGOING_PENDING',
      friendshipId: outgoingRequest.id,
    }
  }

  return {
    ...searchResult,
    relationshipStatus: 'NONE',
    friendshipId: null,
  }
}

export function friendshipErrorMessage(error, fallback = 'Đã có lỗi xảy ra. Vui lòng thử lại.') {
  if (error?.errorCode && FRIENDSHIP_ERROR_MESSAGES[error.errorCode]) {
    return FRIENDSHIP_ERROR_MESSAGES[error.errorCode]
  }
  if (error?.status === 404) return 'Không tìm thấy dữ liệu bạn bè phù hợp.'
  if (error?.status === 409) return 'Dữ liệu vừa thay đổi. Vui lòng tải lại và thử lần nữa.'
  return error?.message || fallback
}
