import assert from 'node:assert/strict'
import test from 'node:test'
import React, { act } from 'react'
import TestRenderer from 'react-test-renderer'
import { createServer } from 'vite'

globalThis.IS_REACT_ACT_ENVIRONMENT = true

const USER_ID = '00000000-0000-4000-8000-000000000001'
const FRIEND_ID = '00000000-0000-4000-8000-000000000002'
const CALL_ID = '00000000-0000-4000-8000-000000000003'

let vite
let useCall
let CALL_SIGNAL
let originalMediaStream

test.before(async () => {
  originalMediaStream = globalThis.MediaStream
  globalThis.MediaStream = class FakeMediaStream {
    constructor(tracks = []) { this.tracks = tracks }
    getTracks() { return this.tracks }
  }
  vite = await createServer({
    root: process.cwd(),
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true },
  })
  const hookModule = await vite.ssrLoadModule('/src/hooks/useCall.js')
  const model = await vite.ssrLoadModule('/src/call/model.js')
  useCall = hookModule.useCall
  CALL_SIGNAL = model.CALL_SIGNAL
})

test.after(async () => {
  if (originalMediaStream === undefined) delete globalThis.MediaStream
  else globalThis.MediaStream = originalMediaStream
  await vite?.close()
})

function deferred() {
  let resolve
  let reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

function fakeStream() {
  const track = { enabled: true, stopped: false, stop() { this.stopped = true } }
  return {
    track,
    stream: {
      getTracks: () => [track],
      getAudioTracks: () => [track],
    },
  }
}

class FakePeerConnection {
  static instances = []
  constructor(configuration) {
    this.configuration = configuration
    this.connectionState = 'new'
    this.remoteDescription = null
    this.localDescription = null
    this.addedCandidates = []
    this.closed = false
    FakePeerConnection.instances.push(this)
  }
  addTrack() {}
  async createOffer() { return { type: 'offer', sdp: 'offer-sdp' } }
  async createAnswer() { return { type: 'answer', sdp: 'answer-sdp' } }
  async setLocalDescription(description) { this.localDescription = description }
  async setRemoteDescription(description) { this.remoteDescription = description }
  async addIceCandidate(candidate) { this.addedCandidates.push(candidate) }
  getReceivers() { return [] }
  close() { this.closed = true; this.connectionState = 'closed' }
}

function createHarness({ mediaDevices, PeerClass = FakePeerConnection } = {}) {
  let latest
  let callbacks
  const sent = []
  const socket = {
    send(signal) { sent.push(signal) },
    deactivate() { return Promise.resolve() },
  }
  const socketFactory = (nextCallbacks) => {
    callbacks = nextCallbacks
    return socket
  }
  function Harness({ session }) {
    latest = useCall(session, {
      socketFactory,
      mediaDevices,
      RTCPeerConnectionClass: PeerClass,
      iceServers: [{ urls: 'stun:test.example' }],
      outgoingTimeoutMs: 60_000,
      incomingTimeoutMs: 60_000,
      connectionTimeoutMs: 60_000,
      disconnectGraceMs: 60_000,
    })
    return null
  }
  const session = {
    user: { id: USER_ID, email: 'user@example.com' },
    accessToken: 'token-one',
  }
  let renderer
  act(() => { renderer = TestRenderer.create(React.createElement(Harness, { session })) })
  act(() => { callbacks.onStatus('connected') })
  return {
    latest: () => latest,
    callbacks: () => callbacks,
    sent,
    renderer,
    updateSession(nextSession) {
      act(() => renderer.update(React.createElement(Harness, { session: nextSession })))
    },
    unmount() { act(() => renderer.unmount()) },
    session,
  }
}

test.beforeEach(() => { FakePeerConnection.instances = [] })

test('outgoing call sends an offer, toggles mic, activates, and releases resources', async () => {
  const local = fakeStream()
  const harness = createHarness({
    mediaDevices: { getUserMedia: async () => local.stream },
  })

  await act(async () => {
    await harness.latest().startCall({ user: { id: FRIEND_ID, fullName: 'Minh Anh' } })
  })
  assert.equal(harness.latest().status, 'outgoing')
  assert.equal(harness.sent[0].type, CALL_SIGNAL.OFFER)
  assert.equal(harness.sent[0].recipientUserId, FRIEND_ID)

  const connection = FakePeerConnection.instances[0]
  act(() => connection.onicecandidate({ candidate: { candidate: '', sdpMid: null, sdpMLineIndex: null } }))
  assert.equal(harness.latest().status, 'outgoing')
  assert.equal(harness.sent.length, 1)

  act(() => harness.latest().toggleMute())
  assert.equal(local.track.enabled, false)
  assert.equal(harness.latest().isMuted, true)

  await act(async () => {
    await harness.callbacks().onSignal({
      callId: harness.latest().callId,
      senderUserId: FRIEND_ID,
      recipientUserId: USER_ID,
      type: CALL_SIGNAL.ANSWER,
      sdp: 'answer-sdp',
    })
  })
  act(() => {
    connection.connectionState = 'connected'
    connection.onconnectionstatechange()
  })
  assert.equal(harness.latest().status, 'active')

  act(() => harness.latest().endCall())
  assert.equal(harness.latest().status, 'ended')
  assert.equal(harness.sent.at(-1).type, CALL_SIGNAL.HANGUP)
  assert.equal(local.track.stopped, true)
  assert.equal(connection.closed, true)
  harness.unmount()
})

test('hangup while microphone permission is pending prevents a stale offer and stops the late stream', async () => {
  const permission = deferred()
  const local = fakeStream()
  const harness = createHarness({
    mediaDevices: { getUserMedia: () => permission.promise },
  })

  let startPromise
  await act(async () => {
    startPromise = harness.latest().startCall(FRIEND_ID)
    await Promise.resolve()
  })
  act(() => harness.latest().endCall())
  await act(async () => {
    permission.resolve(local.stream)
    await startPromise
  })

  assert.equal(harness.latest().status, 'ended')
  assert.equal(harness.sent.some(({ type }) => type === CALL_SIGNAL.OFFER), false)
  assert.equal(local.track.stopped, true)
  harness.unmount()
})

test('incoming call queues bounded unique ICE, ignores terminal ICE, and accepts with an answer', async () => {
  const local = fakeStream()
  const harness = createHarness({
    mediaDevices: { getUserMedia: async () => local.stream },
  })
  await act(async () => {
    await harness.callbacks().onSignal({
      callId: CALL_ID,
      senderUserId: FRIEND_ID,
      recipientUserId: USER_ID,
      type: CALL_SIGNAL.OFFER,
      sdp: 'offer-sdp',
    })
    for (let index = 0; index < 140; index += 1) {
      await harness.callbacks().onSignal({
        callId: CALL_ID,
        senderUserId: FRIEND_ID,
        recipientUserId: USER_ID,
        type: CALL_SIGNAL.ICE_CANDIDATE,
        candidate: `candidate:${index}`,
        sdpMid: 'audio',
        sdpMLineIndex: 0,
      })
    }
    await harness.latest().acceptCall()
  })

  const connection = FakePeerConnection.instances[0]
  assert.equal(connection.addedCandidates.length, 128)
  assert.equal(harness.sent.at(-1).type, CALL_SIGNAL.ANSWER)
  assert.equal(harness.latest().status, 'connecting')

  act(() => harness.latest().endCall())
  await act(async () => {
    await harness.callbacks().onSignal({
      callId: CALL_ID,
      senderUserId: FRIEND_ID,
      recipientUserId: USER_ID,
      type: CALL_SIGNAL.ICE_CANDIDATE,
      candidate: 'candidate:late',
    })
  })
  assert.equal(connection.addedCandidates.length, 128)
  harness.unmount()
})

test('access token rotation reconnects signaling without closing an active peer connection', async () => {
  const local = fakeStream()
  const harness = createHarness({
    mediaDevices: { getUserMedia: async () => local.stream },
  })
  await act(async () => {
    await harness.latest().startCall(FRIEND_ID)
    await harness.callbacks().onSignal({
      callId: harness.latest().callId,
      senderUserId: FRIEND_ID,
      recipientUserId: USER_ID,
      type: CALL_SIGNAL.ANSWER,
      sdp: 'answer-sdp',
    })
  })
  const connection = FakePeerConnection.instances[0]
  act(() => {
    connection.connectionState = 'connected'
    connection.onconnectionstatechange()
  })

  harness.updateSession({ ...harness.session, accessToken: 'token-two' })
  assert.equal(harness.latest().status, 'active')
  assert.equal(connection.closed, false)
  assert.equal(local.track.stopped, false)
  harness.unmount()
  assert.equal(connection.closed, true)
  assert.equal(local.track.stopped, true)
})

test('rejects a second offer while busy and ignores stale or malformed peer signals', async () => {
  const local = fakeStream()
  const harness = createHarness({
    mediaDevices: { getUserMedia: async () => local.stream },
  })
  await act(async () => {
    await harness.latest().startCall(FRIEND_ID)
    await harness.callbacks().onSignal({
      callId: CALL_ID,
      senderUserId: '00000000-0000-4000-8000-000000000004',
      recipientUserId: USER_ID,
      type: CALL_SIGNAL.OFFER,
      sdp: 'second-offer',
    })
    await harness.callbacks().onSignal({
      callId: CALL_ID,
      senderUserId: FRIEND_ID,
      recipientUserId: USER_ID,
      type: CALL_SIGNAL.HANGUP,
    })
    await harness.callbacks().onSignal({
      callId: 'not-a-uuid',
      senderUserId: FRIEND_ID,
      recipientUserId: USER_ID,
      type: CALL_SIGNAL.ANSWER,
      sdp: 'malformed',
    })
  })

  assert.equal(harness.latest().status, 'outgoing')
  assert.equal(harness.sent.filter(({ type }) => type === CALL_SIGNAL.REJECT).length, 1)
  assert.equal(harness.sent.at(-1).recipientUserId, '00000000-0000-4000-8000-000000000004')
  harness.unmount()
})

test('ignores a sibling rejection once the selected peer connection is active', async () => {
  const local = fakeStream()
  const harness = createHarness({
    mediaDevices: { getUserMedia: async () => local.stream },
  })
  await act(async () => {
    await harness.latest().startCall(FRIEND_ID)
    await harness.callbacks().onSignal({
      callId: harness.latest().callId,
      senderUserId: FRIEND_ID,
      recipientUserId: USER_ID,
      type: CALL_SIGNAL.ANSWER,
      sdp: 'answer-sdp',
    })
  })
  const connection = FakePeerConnection.instances[0]
  act(() => {
    connection.connectionState = 'connected'
    connection.onconnectionstatechange()
  })
  await act(async () => {
    await harness.callbacks().onSignal({
      callId: harness.latest().callId,
      senderUserId: FRIEND_ID,
      recipientUserId: USER_ID,
      type: CALL_SIGNAL.REJECT,
    })
  })
  assert.equal(harness.latest().status, 'active')
  assert.equal(connection.closed, false)
  harness.unmount()
})
