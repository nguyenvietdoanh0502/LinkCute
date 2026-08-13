import assert from 'node:assert/strict'
import test from 'node:test'
import { createServer } from 'vite'

const CALL_ID = '00000000-0000-4000-8000-000000000001'
const FRIEND_ID = '00000000-0000-4000-8000-000000000002'

let vite
let model
let socketModule
let clientModule
let originalLocalStorage
let storageValues

test.before(async () => {
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
  model = await vite.ssrLoadModule('/src/call/model.js')
  socketModule = await vite.ssrLoadModule('/src/realtime/callSocket.js')
  clientModule = await vite.ssrLoadModule('/src/api/client.js')
})

test.beforeEach(() => {
  storageValues.clear()
  clientModule.saveSession({
    user: { id: CALL_ID },
    accessToken: 'call-access-token',
    expiresIn: 3600,
  })
})

test.after(async () => {
  if (originalLocalStorage === undefined) delete globalThis.localStorage
  else globalThis.localStorage = originalLocalStorage
  await vite?.close()
})

test('call model normalizes friendship peers and safely parses ICE server configuration', () => {
  assert.deepEqual(model.normalizeCallPeer({
    friendshipId: 'friendship-1',
    user: { id: FRIEND_ID, fullName: 'Minh Anh' },
  }), { id: FRIEND_ID, userId: FRIEND_ID, fullName: 'Minh Anh' })
  assert.deepEqual(model.parseIceServers('[{"urls":"  stun:stun.example.com:3478  "}]'), [
    { urls: 'stun:stun.example.com:3478' },
  ])
  assert.deepEqual(model.parseIceServers('invalid-json'), [
    { urls: 'stun:stun.l.google.com:19302' },
  ])
  assert.match(model.createCallId(), /^[0-9a-f-]{36}$/i)
})

test('call model builds only type-correct signaling payloads', () => {
  assert.deepEqual(model.callSignalPayload({
    callId: CALL_ID,
    recipientUserId: FRIEND_ID,
    type: model.CALL_SIGNAL.OFFER,
    sdp: 'v=0',
  }), {
    callId: CALL_ID,
    recipientUserId: FRIEND_ID,
    type: 'OFFER',
    sdp: 'v=0',
  })
  assert.deepEqual(model.callSignalPayload({
    callId: CALL_ID,
    recipientUserId: FRIEND_ID,
    type: model.CALL_SIGNAL.ICE_CANDIDATE,
    candidate: 'candidate:1',
    sdpMid: 'audio',
    sdpMLineIndex: 0,
  }), {
    callId: CALL_ID,
    recipientUserId: FRIEND_ID,
    type: 'ICE_CANDIDATE',
    candidate: 'candidate:1',
    sdpMid: 'audio',
    sdpMLineIndex: 0,
  })
  assert.throws(() => model.callSignalPayload({
    callId: CALL_ID,
    recipientUserId: FRIEND_ID,
    type: model.CALL_SIGNAL.OFFER,
  }), /kết nối/i)
  assert.throws(() => model.callSignalPayload({
    callId: CALL_ID,
    recipientUserId: FRIEND_ID,
    type: model.CALL_SIGNAL.HANGUP,
    candidate: 'not-allowed',
  }), /kết thúc/i)
})

test('call socket authenticates, uses private queues, and publishes the backend contract', async () => {
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
    activate() { this.active = true }
    subscribe(destination, handler) {
      this.subscriptions.push({ destination, handler })
      return { unsubscribe() {} }
    }
    publish(frame) { this.published.push(frame) }
    deactivate() { this.active = false; return Promise.resolve() }
  }

  const statuses = []
  const signals = []
  const errors = []
  const socket = socketModule.createCallSocket({
    brokerURL: 'wss://linkcute.example/ws',
    ClientClass: FakeClient,
    onStatus: (status) => statuses.push(status),
    onSignal: (signal) => signals.push(signal),
    onError: (error) => errors.push(error),
  })
  const stomp = FakeClient.instance

  assert.throws(() => socket.send({}), /chưa sẵn sàng/)
  await stomp.options.beforeConnect()
  assert.deepEqual(stomp.connectHeaders, { Authorization: 'Bearer call-access-token' })
  stomp.connected = true
  stomp.options.onConnect()
  assert.deepEqual(stomp.subscriptions.map(({ destination }) => destination), [
    '/user/queue/call-signals',
    '/user/queue/call-errors',
  ])

  const offer = {
    callId: CALL_ID,
    recipientUserId: FRIEND_ID,
    type: model.CALL_SIGNAL.OFFER,
    sdp: 'v=0',
  }
  socket.send(offer)
  assert.deepEqual(stomp.published, [{
    destination: '/app/call.signal',
    body: JSON.stringify(offer),
  }])

  stomp.subscriptions[0].handler({ body: JSON.stringify({ ...offer, senderUserId: FRIEND_ID }) })
  stomp.subscriptions[1].handler({ body: JSON.stringify({ callId: CALL_ID, errorCode: 'RATE_LIMIT_EXCEEDED' }) })
  assert.equal(signals[0].senderUserId, FRIEND_ID)
  assert.equal(errors[0].errorCode, 'RATE_LIMIT_EXCEEDED')
  assert.deepEqual(statuses, ['connecting', 'connected'])
  await socket.deactivate()
})
