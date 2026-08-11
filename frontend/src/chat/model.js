import {
  LOCATION_MESSAGE_TYPE,
  TEXT_MESSAGE_TYPE,
  chatMessageType,
  requireLocation,
} from '../location/model.js'

export const MAX_CHAT_MESSAGE_LENGTH = 2000

const CHAT_ERROR_MESSAGES = {
  CHAT_REQUIRES_FRIENDSHIP: 'Bạn chỉ có thể nhắn tin với người đang là bạn bè.',
  CHAT_RECIPIENT_NOT_FOUND: 'Không tìm thấy người nhận tin nhắn.',
  CHAT_SELF_NOT_ALLOWED: 'Bạn không thể tự nhắn tin cho chính mình.',
  INVALID_CHAT_MESSAGE: `Tin nhắn phải có từ 1 đến ${MAX_CHAT_MESSAGE_LENGTH} ký tự.`,
  INVALID_CHAT_LOCATION: 'Tọa độ hoặc độ chính xác của vị trí không hợp lệ.',
  UNAUTHENTICATED: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
}

export function normalizeChatContent(value) {
  const content = String(value ?? '').trim()
  if (!content || content.length > MAX_CHAT_MESSAGE_LENGTH) {
    throw new Error(CHAT_ERROR_MESSAGES.INVALID_CHAT_MESSAGE)
  }
  return content
}

export function chatPeerId(message, currentUserId) {
  if (!message || !currentUserId) return null
  if (message.senderId === currentUserId) return message.recipientId || null
  if (message.recipientId === currentUserId) return message.senderId || null
  return null
}

function messageKey(message) {
  if (message?.clientMessageId) return `client:${message.senderId || 'unknown'}:${message.clientMessageId}`
  if (message?.id) return `server:${message.id}`
  return null
}

function messageTime(message) {
  const value = new Date(message?.createdAt || 0).getTime()
  return Number.isNaN(value) ? 0 : value
}

export function mergeChatMessages(...groups) {
  const merged = new Map()

  groups.flat().filter(Boolean).forEach((message) => {
    const key = messageKey(message)
    if (!key) return
    const current = merged.get(key)
    const currentIsServer = Boolean(current?.id)
    const candidateIsServer = Boolean(message.id)
    const serverAcknowledged = currentIsServer || candidateIsServer
    const next = currentIsServer && !candidateIsServer
      ? { ...message, ...current }
      : { ...(current || {}), ...message }
    merged.set(key, {
      ...next,
      status: serverAcknowledged ? 'SENT' : (next.status || 'SENDING'),
      error: serverAcknowledged ? null : (next.error ?? null),
    })
  })

  return [...merged.values()].sort((left, right) => {
    const timeDifference = messageTime(left) - messageTime(right)
    if (timeDifference) return timeDifference
    return messageKey(left).localeCompare(messageKey(right))
  })
}

export function optimisticChatMessage({ clientMessageId, senderId, recipientId, content, createdAt }) {
  return {
    id: null,
    clientMessageId,
    senderId,
    recipientId,
    messageType: TEXT_MESSAGE_TYPE,
    content: normalizeChatContent(content),
    createdAt: createdAt || new Date().toISOString(),
    status: 'SENDING',
    error: null,
  }
}

export function optimisticLocationMessage({
  clientMessageId,
  senderId,
  recipientId,
  location,
  createdAt,
}) {
  const normalized = requireLocation(location)
  return {
    id: null,
    clientMessageId,
    senderId,
    recipientId,
    messageType: LOCATION_MESSAGE_TYPE,
    content: null,
    latitude: normalized.latitude,
    longitude: normalized.longitude,
    accuracyMeters: normalized.accuracyMeters,
    createdAt: createdAt || new Date().toISOString(),
    status: 'SENDING',
    error: null,
  }
}

export function chatMessagePayload(message) {
  const payload = {
    recipientId: message?.recipientId,
    clientMessageId: message?.clientMessageId,
    messageType: chatMessageType(message),
  }

  if (payload.messageType === LOCATION_MESSAGE_TYPE) {
    const location = requireLocation(message)
    return {
      ...payload,
      content: null,
      latitude: location.latitude,
      longitude: location.longitude,
      accuracyMeters: location.accuracyMeters,
    }
  }

  return {
    ...payload,
    messageType: TEXT_MESSAGE_TYPE,
    content: normalizeChatContent(message?.content),
  }
}

export function updateChatMessage(messages, clientMessageId, changes, senderId = null) {
  return messages.map((message) => (
    message.clientMessageId === clientMessageId && (!senderId || message.senderId === senderId)
  )
    ? { ...message, ...changes }
    : message)
}

export function chatErrorMessage(error, fallback = 'Không thể gửi tin nhắn. Vui lòng thử lại.') {
  if (error?.errorCode && CHAT_ERROR_MESSAGES[error.errorCode]) {
    return CHAT_ERROR_MESSAGES[error.errorCode]
  }
  return error?.message || fallback
}
