import { Client } from '@stomp/stompjs'
import { ensureFreshSession } from '../api/client.js'
import { callSignalPayload } from '../call/model.js'
import { chatWebSocketUrl } from './chatSocket.js'

function parseFrame(frame) {
  try {
    return JSON.parse(frame.body)
  } catch {
    return { message: frame.body || 'Máy chủ trả về dữ liệu cuộc gọi không hợp lệ.' }
  }
}

export function createCallSocket({
  onSignal,
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
      try {
        const session = await ensureFreshSession()
        client.connectHeaders = { Authorization: `Bearer ${session.accessToken}` }
      } catch (error) {
        reportError(error)
        reportStatus('error')
        throw error
      }
    },
    onConnect: () => {
      reportStatus('connected')
      client.subscribe('/user/queue/call-signals', (frame) => onSignal?.(parseFrame(frame)))
      client.subscribe('/user/queue/call-errors', (frame) => reportError(parseFrame(frame)))
    },
    onStompError: (frame) => {
      reportError(parseFrame(frame))
      reportStatus('error')
    },
    onWebSocketError: () => reportStatus('reconnecting'),
    onWebSocketClose: () => reportStatus(client.active ? 'reconnecting' : 'disconnected'),
  })

  client.activate()

  return {
    send(signal) {
      if (!client.connected) throw new Error('Kết nối cuộc gọi chưa sẵn sàng.')
      client.publish({
        destination: '/app/call.signal',
        body: JSON.stringify(callSignalPayload(signal)),
      })
    },
    deactivate() {
      return client.deactivate()
    },
    isConnected() {
      return Boolean(client.connected)
    },
  }
}
