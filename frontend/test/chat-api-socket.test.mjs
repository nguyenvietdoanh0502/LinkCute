import assert from 'node:assert/strict'
import test from 'node:test'
import { createServer } from 'vite'

let vite
let clientModule
let socketModule
let originalFetch
let originalLocalStorage
let storageValues

test.before(async () => {
  originalFetch = globalThis.fetch
  originalLocalStorage = globalThis.localStorage
  storageValues = new Map()
  globalThis.localStorage = {
    getItem: (key) => storageValues.get(key) ?? null,
    setItem: (key, value) => storageValues.set(key, String(value)),
    removeItem: (key) => storageValues.delete(key),
  }

  vite = await createServer({
    root: process.cwd(),
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true },
  })
  clientModule = await vite.ssrLoadModule('/src/api/client.js')
  socketModule = await vite.ssrLoadModule('/src/realtime/chatSocket.js')
})

test.beforeEach(() => {
  storageValues.clear()
  clientModule.saveSession({
    user: { id: 'user-1', fullName: 'Minh Anh' },
    accessToken: 'chat-access-token',
    refreshToken: 'chat-refresh-token',
    expiresIn: 3600,
  })
})

test.after(async () => {
  globalThis.fetch = originalFetch
  if (originalLocalStorage === undefined) delete globalThis.localStorage
  else globalThis.localStorage = originalLocalStorage
  await vite?.close()
})

test('chat history API encodes the friend cursor and forwards auth plus cancellation', async () => {
  const calls = []
  const controller = new AbortController()
  const history = {
    messages: [{ id: 'message-1', content: 'Xin chào' }],
    hasMore: true,
    nextBefore: '2026-08-10T10:00:00Z',
  }
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options })
    return new Response(JSON.stringify({ status: 'success', data: history }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })
  }

  const result = await clientModule.api.getChatHistory(
    'friend/with spaces',
    { before: '2026-08-10T11:00:00.000Z', size: 50 },
    { signal: controller.signal },
  )

  assert.deepEqual(result, history)
  assert.equal(calls.length, 1)
  const requestUrl = new URL(calls[0].url, 'http://linkcute.test')
  assert.equal(requestUrl.pathname, '/api/v1/chat/friends/friend%2Fwith%20spaces/messages')
  const query = requestUrl.searchParams
  assert.equal(query.get('before'), '2026-08-10T11:00:00.000Z')
  assert.equal(query.get('size'), '50')
  assert.equal(calls[0].options.headers.Authorization, 'Bearer chat-access-token')
  assert.strictEqual(calls[0].options.signal, controller.signal)
  assert.equal(calls[0].options.body, undefined)
})

test('builds native WebSocket URLs for HTTP and HTTPS API origins', () => {
  assert.equal(socketModule.chatWebSocketUrl('http://localhost:8080/api'), 'ws://localhost:8080/ws')
  assert.equal(socketModule.chatWebSocketUrl('https://linkcute.example/api/'), 'wss://linkcute.example/ws')
  assert.throws(() => socketModule.chatWebSocketUrl(''), /WebSocket/)
})

test('STOMP socket authenticates, subscribes to private queues, publishes, and deactivates', async () => {
  class FakeClient {
    static instance

    constructor(options) {
      this.options = options
      this.active = false
      this.connected = false
      this.connectHeaders = {}
      this.subscriptions = []
      this.published = []
      FakeClient.instance = this
    }

    activate() {
      this.active = true
    }

    subscribe(destination, handler) {
      this.subscriptions.push({ destination, handler })
      return { unsubscribe() {} }
    }

    publish(frame) {
      this.published.push(frame)
    }

    async deactivate() {
      this.active = false
      this.connected = false
    }
  }

  const statuses = []
  const messages = []
  const errors = []
  const socket = socketModule.createChatSocket({
    brokerURL: 'wss://linkcute.example/ws',
    ClientClass: FakeClient,
    onStatus: (status) => statuses.push(status),
    onMessage: (message) => messages.push(message),
    onError: (error) => errors.push(error),
  })
  const stomp = FakeClient.instance

  assert.equal(stomp.active, true)
  assert.equal(stomp.options.brokerURL, 'wss://linkcute.example/ws')
  assert.equal(stomp.options.reconnectDelay, 3000)
  assert.throws(() => socket.send({ recipientId: 'friend-1', content: 'hello' }), /realtime/)

  await stomp.options.beforeConnect()
  assert.deepEqual(statuses, ['connecting'])
  assert.deepEqual(stomp.connectHeaders, { Authorization: 'Bearer chat-access-token' })

  stomp.connected = true
  stomp.options.onConnect()
  assert.deepEqual(statuses, ['connecting', 'connected'])
  assert.deepEqual(stomp.subscriptions.map((subscription) => subscription.destination), [
    '/user/queue/messages',
    '/user/queue/chat-errors',
  ])

  stomp.subscriptions[0].handler({ body: JSON.stringify({ id: 'message-1', content: 'hello' }) })
  stomp.subscriptions[1].handler({ body: JSON.stringify({ errorCode: 'CHAT_REQUIRES_FRIENDSHIP' }) })
  assert.deepEqual(messages, [{ id: 'message-1', content: 'hello' }])
  assert.deepEqual(errors, [{ errorCode: 'CHAT_REQUIRES_FRIENDSHIP' }])

  const payload = {
    recipientId: 'friend-1',
    clientMessageId: 'client-message-1',
    messageType: 'TEXT',
    content: 'hello',
  }
  const locationPayload = {
    recipientId: 'friend-1',
    clientMessageId: 'client-location-1',
    messageType: 'LOCATION',
    content: null,
    latitude: 21.0278,
    longitude: 105.8342,
    accuracyMeters: 12,
  }
  socket.send(payload)
  socket.send(locationPayload)
  assert.deepEqual(stomp.published, [payload, locationPayload].map((body) => ({
    destination: '/app/chat.send',
    body: JSON.stringify(body),
  })))
  assert.equal(socket.isConnected(), true)

  await socket.deactivate()
  assert.equal(stomp.active, false)
  assert.equal(socket.isConnected(), false)
})

test('STOMP lifecycle reports protocol, transport, reconnect, and malformed-frame failures', () => {
  class FakeClient {
    static instance

    constructor(options) {
      this.options = options
      this.active = true
      this.connected = false
      FakeClient.instance = this
    }

    activate() {}
    subscribe() {}
    deactivate() { this.active = false; return Promise.resolve() }
  }

  const statuses = []
  const errors = []
  socketModule.createChatSocket({
    brokerURL: 'ws://localhost/ws',
    ClientClass: FakeClient,
    onStatus: (status) => statuses.push(status),
    onError: (error) => errors.push(error),
  })
  const stomp = FakeClient.instance

  stomp.options.onStompError({ body: JSON.stringify({ message: 'Broker rejected CONNECT' }) })
  stomp.options.onWebSocketError(new Error('offline'))
  stomp.active = true
  stomp.options.onWebSocketClose()
  stomp.active = false
  stomp.options.onWebSocketClose()
  stomp.options.onStompError({ body: 'not-json' })

  assert.deepEqual(statuses, ['error', 'reconnecting', 'reconnecting', 'disconnected', 'error'])
  assert.deepEqual(errors[0], { message: 'Broker rejected CONNECT' })
  assert.equal(errors[1].message, 'not-json')
})
