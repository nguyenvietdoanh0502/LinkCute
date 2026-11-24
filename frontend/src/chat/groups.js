const ERRORS = {
  GROUP_NOT_FOUND: 'Nhóm không còn tồn tại hoặc bạn không còn là thành viên.',
  GROUP_INVITATION_NOT_FOUND: 'Lời mời này không còn hiệu lực.',
  GROUP_REQUIRES_FRIENDSHIP: 'Hai người cần là bạn bè để gửi và chấp nhận lời mời.',
  GROUP_ALREADY_PARTICIPANT: 'Người này đã ở trong nhóm hoặc đang chờ chấp nhận lời mời.',
  INVALID_GROUP_NAME: 'Tên nhóm cần từ 1 đến 100 ký tự.',
  UNAUTHORIZED: 'Chỉ chủ nhóm được đổi tên và ảnh nhóm.',
  UNAUTHENTICATED: 'Vui lòng đăng nhập lại.',
  INVALID_AVATAR: 'Chọn ảnh JPEG, PNG hoặc WebP hợp lệ.',
  AVATAR_TOO_LARGE: 'Ảnh nhóm không được vượt quá 5 MiB.',
  AVATAR_UPLOAD_FAILED: 'Không tải được ảnh nhóm. Vui lòng thử lại.',
  AVATAR_STORAGE_NOT_CONFIGURED: 'Máy chủ chưa cấu hình lưu ảnh nhóm.',
  INVALID_CHAT_MESSAGE: 'Tin nhắn cần từ 1 đến 2000 ký tự.',
}

export function groupErrorMessage(error) {
  return ERRORS[error?.errorCode] || error?.message || 'Không thể cập nhật nhóm. Vui lòng thử lại.'
}

export function availableGroupFriends(friends = [], participants = []) {
  const existing = new Set(participants.map((entry) => entry.user?.id))
  return friends.filter((friend) => friend.user?.id && !existing.has(friend.user.id))
}

export function mergeGroupMessages(current = [], incoming = []) {
  const merged = new Map()
  for (const message of [...current, ...incoming]) {
    if (!message?.sender?.id || !message.clientMessageId) continue
    const key = `${message.sender.id}:${message.clientMessageId}`
    const previous = merged.get(key)
    if (previous?.id && !message.id) continue
    merged.set(key, message)
  }
  return [...merged.values()].sort((a, b) => {
    if (a.id && b.id) return Number(a.id) - Number(b.id)
    if (a.id) return -1
    if (b.id) return 1
    return new Date(a.createdAt) - new Date(b.createdAt)
  })
}
