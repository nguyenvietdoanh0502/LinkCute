import { Client } from '@stomp/stompjs'
import { ensureFreshSession, getApiOrigin } from '../api/client.js'

export function chatWebSocketUrl(apiOrigin = getApiOrigin()) {
  const browserOrigin = typeof window !== 'undefined' ? window.location.origin : ''
  const base = apiOrigin || browserOrigin
  if (!base) throw new Error('Không xác định được địa chỉ WebSocket.')

  const url = new URL('/ws', base)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.toString()
}

function parseFrame(frame) {
  try {
    return JSON.parse(frame.body)
  } catch {
    return { message: frame.body || 'Máy chủ trả về dữ liệu WebSocket không hợp lệ.' }
  }
}

export function createChatSocket({
  onMessage,
  onError,
  onStatus,
  ClientClass = Client,
  brokerURL = chatWebSocketUrl(),
}) {
  const reportStatus = onStatus || (() => {})
  const reportError = onError || (() => {})

  const client = new ClientClass({
    brokerURL,
    reconnectDelay: 3000,
    connectionTimeout: 10_000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    debug: () => {},
    beforeConnect: async () => {
      reportStatus('connecting')
      const session = await ensureFreshSession()
      client.connectHeaders = { Authorization: `Bearer ${session.accessToken}` }
    },
    onConnect: () => {
      reportStatus('connected')
      client.subscribe('/user/queue/messages', (frame) => onMessage?.(parseFrame(frame)))
      client.subscribe('/user/queue/chat-errors', (frame) => reportError(parseFrame(frame)))
    },
    onStompError: (frame) => {
      reportError(parseFrame(frame))
      reportStatus('error')
    },
    onWebSocketError: () => {
      reportStatus('reconnecting')
    },
    onWebSocketClose: () => {
      reportStatus(client.active ? 'reconnecting' : 'disconnected')
    },
  })

  client.activate()

  return {
    send(payload) {
      if (!client.connected) throw new Error('Kết nối realtime chưa sẵn sàng.')
      client.publish({ destination: '/app/chat.send', body: JSON.stringify(payload) })
    },
    deactivate() {
      return client.deactivate()
    },
    isConnected() {
      return client.connected
    },
  }
}
