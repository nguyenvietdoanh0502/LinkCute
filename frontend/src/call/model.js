export const CALL_SIGNAL = Object.freeze({
  OFFER: 'OFFER',
  ANSWER: 'ANSWER',
  ICE_CANDIDATE: 'ICE_CANDIDATE',
  REJECT: 'REJECT',
  HANGUP: 'HANGUP',
})

export const CALL_STATUS = Object.freeze({
  IDLE: 'idle',
  OUTGOING: 'outgoing',
  INCOMING: 'incoming',
  CONNECTING: 'connecting',
  ACTIVE: 'active',
  ENDED: 'ended',
  ERROR: 'error',
})

export const DEFAULT_ICE_SERVERS = Object.freeze([
  Object.freeze({ urls: 'stun:stun.l.google.com:19302' }),
])

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

export function isUuid(value) {
  return typeof value === 'string' && UUID_PATTERN.test(value.trim())
}

export function createCallId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()

  const bytes = new Uint8Array(16)
  if (globalThis.crypto?.getRandomValues) {
    globalThis.crypto.getRandomValues(bytes)
  } else {
    for (let index = 0; index < bytes.length; index += 1) {
      bytes[index] = Math.floor(Math.random() * 256)
    }
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

export function normalizeCallPeer(value) {
  const candidate = typeof value === 'object' && value?.user ? value.user : value
  if (typeof candidate === 'string') {
    const id = candidate.trim()
    return id ? { id, userId: id } : null
  }
  if (!candidate || typeof candidate !== 'object') return null

  const id = String(candidate.id || candidate.userId || '').trim()
  if (!id) return null
  return { ...candidate, id, userId: id }
}

function validIceServer(server) {
  if (!server || typeof server !== 'object') return false
  const urls = Array.isArray(server.urls) ? server.urls : [server.urls]
  return urls.length > 0 && urls.every((url) => typeof url === 'string' && url.trim())
}

function cloneIceServers(servers) {
  return servers.map((server) => ({
    ...server,
    urls: Array.isArray(server.urls)
      ? server.urls.map((url) => url.trim())
      : server.urls.trim(),
  }))
}

export function parseIceServers(value, fallback = DEFAULT_ICE_SERVERS) {
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    if (!Array.isArray(parsed) || !parsed.length || !parsed.every(validIceServer)) {
      return cloneIceServers(fallback)
    }
    return cloneIceServers(parsed)
  } catch {
    return cloneIceServers(fallback)
  }
}

export function configuredIceServers(
  value = import.meta.env?.VITE_WEBRTC_ICE_SERVERS,
) {
  return parseIceServers(value)
}

export function callSignalPayload({
  callId,
  recipientUserId,
  type,
  sdp,
  candidate,
  sdpMid,
  sdpMLineIndex,
}) {
  if (!isUuid(callId) || !isUuid(recipientUserId) || !Object.values(CALL_SIGNAL).includes(type)) {
    throw new Error('Tín hiệu cuộc gọi không hợp lệ.')
  }

  const payload = { callId, recipientUserId, type }
  if (typeof sdp === 'string') payload.sdp = sdp
  if (typeof candidate === 'string') payload.candidate = candidate
  if (sdpMid !== undefined && sdpMid !== null) payload.sdpMid = String(sdpMid)
  if (Number.isInteger(sdpMLineIndex)) payload.sdpMLineIndex = sdpMLineIndex

  const hasSdp = typeof payload.sdp === 'string' && Boolean(payload.sdp.trim())
  const hasIce = Object.hasOwn(payload, 'candidate')
    || Object.hasOwn(payload, 'sdpMid')
    || Object.hasOwn(payload, 'sdpMLineIndex')
  if ([CALL_SIGNAL.OFFER, CALL_SIGNAL.ANSWER].includes(type) && (!hasSdp || hasIce)) {
    throw new Error('Mô tả kết nối cuộc gọi không hợp lệ.')
  }
  if (type === CALL_SIGNAL.ICE_CANDIDATE
    && (hasSdp || typeof payload.candidate !== 'string' || !payload.candidate.trim())) {
    throw new Error('ICE candidate không hợp lệ.')
  }
  if ([CALL_SIGNAL.REJECT, CALL_SIGNAL.HANGUP].includes(type) && (hasSdp || hasIce)) {
    throw new Error('Tín hiệu kết thúc cuộc gọi không hợp lệ.')
  }
  return payload
}

const ERROR_MESSAGES = {
  CALL_REQUIRES_FRIENDSHIP: 'Chỉ hai người đang là bạn bè mới có thể gọi cho nhau.',
  CALL_RECIPIENT_NOT_FOUND: 'Người bạn này hiện không thể nhận cuộc gọi.',
  CALL_SELF_NOT_ALLOWED: 'Bạn không thể tự gọi cho chính mình.',
  INVALID_CALL_SIGNAL: 'Dữ liệu kết nối cuộc gọi không hợp lệ.',
  RATE_LIMIT_EXCEEDED: 'Bạn đang gọi quá nhanh. Vui lòng chờ một chút rồi thử lại.',
  UNAUTHENTICATED: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
}

export function callErrorMessage(error, fallback = 'Không thể kết nối cuộc gọi.') {
  if (error?.errorCode && ERROR_MESSAGES[error.errorCode]) return ERROR_MESSAGES[error.errorCode]
  if (typeof error === 'string' && error.trim()) return error
  if (typeof error?.message === 'string' && error.message.trim()) return error.message
  return fallback
}

export function mediaErrorMessage(error) {
  const name = error?.name
  if (name === 'NotAllowedError' || name === 'SecurityError') {
    return 'Bạn cần cho phép LinkCute sử dụng micrô để gọi thoại.'
  }
  if (name === 'NotFoundError' || name === 'DevicesNotFoundError') {
    return 'Không tìm thấy micrô trên thiết bị này.'
  }
  if (name === 'NotReadableError' || name === 'TrackStartError') {
    return 'Micrô đang được ứng dụng khác sử dụng.'
  }
  return callErrorMessage(error, 'Không thể mở micrô. Vui lòng kiểm tra thiết bị và thử lại.')
}
