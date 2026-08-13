import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  CALL_SIGNAL,
  CALL_STATUS,
  callErrorMessage,
  configuredIceServers,
  createCallId,
  isUuid,
  mediaErrorMessage,
  normalizeCallPeer,
} from '../call/model.js'
import { createCallSocket } from '../realtime/callSocket.js'

const OUTGOING_TIMEOUT_MS = 30_000
const DISCONNECT_GRACE_MS = 10_000
const MAX_PENDING_ICE_CANDIDATES = 128
const LIVE_ICE_STATUSES = new Set([
  CALL_STATUS.OUTGOING,
  CALL_STATUS.INCOMING,
  CALL_STATUS.CONNECTING,
  CALL_STATUS.ACTIVE,
])

const INITIAL_CALL = Object.freeze({
  status: CALL_STATUS.IDLE,
  callId: null,
  peer: null,
  error: '',
  endReason: '',
  isMuted: false,
  activeSince: null,
})

function browserMediaDevices() {
  return globalThis.navigator?.mediaDevices
}

function browserPeerConnection() {
  return globalThis.RTCPeerConnection
}

function validSignalEnvelope(signal, currentUserId) {
  return signal
    && isUuid(signal.callId)
    && isUuid(signal.senderUserId)
    && isUuid(signal.recipientUserId)
    && signal.recipientUserId === currentUserId
    && Object.values(CALL_SIGNAL).includes(signal.type)
}

export function useCall(session, options = {}) {
  const currentUserId = session?.user?.id || null
  const identity = currentUserId || session?.user?.email || ''
  const [call, setCall] = useState(INITIAL_CALL)
  const [connectionStatus, setConnectionStatus] = useState('disconnected')
  const hasAccessToken = Boolean(session?.accessToken)
  const socketRef = useRef(null)
  const socketGenerationRef = useRef(0)
  const callRef = useRef(INITIAL_CALL)
  const peerConnectionRef = useRef(null)
  const localStreamRef = useRef(null)
  const pendingCandidatesRef = useRef([])
  const outgoingTimerRef = useRef(null)
  const disconnectTimerRef = useRef(null)
  const remoteAudioRef = useRef(null)
  const mountedRef = useRef(true)
  const operationRef = useRef(0)
  const previousIdentityRef = useRef(identity)

  const mediaDevices = options.mediaDevices || browserMediaDevices()
  const RTCPeerConnectionClass = options.RTCPeerConnectionClass || browserPeerConnection()
  const iceServers = useMemo(
    () => options.iceServers || configuredIceServers(),
    [options.iceServers],
  )
  const socketFactory = options.socketFactory || createCallSocket
  const disconnectGraceMs = options.disconnectGraceMs || DISCONNECT_GRACE_MS

  const commitCall = useCallback((nextOrTransform) => {
    const next = typeof nextOrTransform === 'function'
      ? nextOrTransform(callRef.current)
      : nextOrTransform
    callRef.current = next
    if (mountedRef.current) setCall(next)
    return next
  }, [])

  const clearOutgoingTimer = useCallback(() => {
    if (outgoingTimerRef.current) globalThis.clearTimeout(outgoingTimerRef.current)
    outgoingTimerRef.current = null
  }, [])

  const clearDisconnectTimer = useCallback(() => {
    if (disconnectTimerRef.current) globalThis.clearTimeout(disconnectTimerRef.current)
    disconnectTimerRef.current = null
  }, [])

  const releaseMedia = useCallback(() => {
    clearOutgoingTimer()
    clearDisconnectTimer()
    operationRef.current += 1
    pendingCandidatesRef.current = []
    const connection = peerConnectionRef.current
    peerConnectionRef.current = null
    if (connection) {
      connection.onicecandidate = null
      connection.ontrack = null
      connection.onconnectionstatechange = null
      try { connection.close() } catch { /* already closed */ }
    }
    localStreamRef.current?.getTracks?.().forEach((track) => track.stop())
    localStreamRef.current = null
    if (remoteAudioRef.current) remoteAudioRef.current.srcObject = null
  }, [clearDisconnectTimer, clearOutgoingTimer])

  const isCurrentOperation = useCallback((operation, snapshot, connection = null) => (
    operationRef.current === operation
      && callRef.current.callId === snapshot.callId
      && callRef.current.peer?.id === snapshot.peer?.id
      && (!connection || peerConnectionRef.current === connection)
  ), [])

  const discardStaleResources = useCallback((connection, stream) => {
    if (connection) {
      connection.onicecandidate = null
      connection.ontrack = null
      connection.onconnectionstatechange = null
      try { connection.close() } catch { /* already closed */ }
      if (peerConnectionRef.current === connection) peerConnectionRef.current = null
    }
    stream?.getTracks?.().forEach((track) => track.stop())
    if (localStreamRef.current === stream) localStreamRef.current = null
  }, [])

  const resetCall = useCallback(() => {
    releaseMedia()
    commitCall({ ...INITIAL_CALL })
  }, [commitCall, releaseMedia])

  const sendSignal = useCallback((signal) => {
    if (!socketRef.current) throw new Error('Kết nối cuộc gọi chưa sẵn sàng.')
    socketRef.current.send(signal)
  }, [])

  const finishCall = useCallback((status, endReason = '', error = '') => {
    releaseMedia()
    commitCall((current) => ({
      ...current,
      status,
      error,
      endReason,
      isMuted: false,
      activeSince: null,
    }))
  }, [commitCall, releaseMedia])

  const sendTerminalBestEffort = useCallback((type, snapshot = callRef.current) => {
    if (!snapshot.callId || !snapshot.peer?.id) return
    try {
      sendSignal({
        callId: snapshot.callId,
        recipientUserId: snapshot.peer.id,
        type,
      })
    } catch {
      // Local media still has to be released when signaling is unavailable.
    }
  }, [sendSignal])

  const attachRemoteAudio = useCallback((element) => {
    remoteAudioRef.current = element
    if (element && peerConnectionRef.current) {
      const receivers = peerConnectionRef.current.getReceivers?.() || []
      const tracks = receivers.map((receiver) => receiver.track).filter(Boolean)
      if (tracks.length && globalThis.MediaStream) element.srcObject = new MediaStream(tracks)
    }
  }, [])

  const flushPendingCandidates = useCallback(async (connection, isCurrent = () => true) => {
    const pending = pendingCandidatesRef.current
    pendingCandidatesRef.current = []
    for (const candidate of pending) {
      if (!isCurrent()) return false
      try {
        await connection.addIceCandidate(candidate)
      } catch {
        // A single stale/malformed candidate must not abort SDP negotiation.
      }
    }
    return isCurrent()
  }, [])

  const createConnection = useCallback((snapshot, localStream) => {
    if (!RTCPeerConnectionClass) throw new Error('Trình duyệt này không hỗ trợ gọi WebRTC.')
    const connection = new RTCPeerConnectionClass({ iceServers })
    peerConnectionRef.current = connection
    localStream?.getTracks?.().forEach((track) => connection.addTrack(track, localStream))

    connection.onicecandidate = ({ candidate }) => {
      if (!candidate || typeof candidate.candidate !== 'string' || !candidate.candidate.trim()) return
      const active = callRef.current
      if (active.callId !== snapshot.callId || active.peer?.id !== snapshot.peer.id) return
      try {
        sendSignal({
          callId: snapshot.callId,
          recipientUserId: snapshot.peer.id,
          type: CALL_SIGNAL.ICE_CANDIDATE,
          candidate: candidate.candidate,
          sdpMid: candidate.sdpMid,
          sdpMLineIndex: candidate.sdpMLineIndex,
        })
      } catch (error) {
        if (peerConnectionRef.current === connection
          && callRef.current.callId === snapshot.callId
          && callRef.current.peer?.id === snapshot.peer.id) {
          finishCall(CALL_STATUS.ERROR, 'network', callErrorMessage(error))
        }
      }
    }

    connection.ontrack = ({ streams, track }) => {
      const remoteStream = streams?.[0]
        || (globalThis.MediaStream && track ? new MediaStream([track]) : null)
      if (remoteAudioRef.current && remoteStream) {
        remoteAudioRef.current.srcObject = remoteStream
        remoteAudioRef.current.play?.().catch(() => {})
      }
    }

    connection.onconnectionstatechange = () => {
      if (peerConnectionRef.current !== connection) return
      if (connection.connectionState === 'connected') {
        clearDisconnectTimer()
        clearOutgoingTimer()
        commitCall((current) => current.callId === snapshot.callId ? {
          ...current,
          status: CALL_STATUS.ACTIVE,
          activeSince: current.activeSince || Date.now(),
          error: '',
        } : current)
      } else if (connection.connectionState === 'failed') {
        clearDisconnectTimer()
        finishCall(CALL_STATUS.ERROR, 'network', 'Cuộc gọi đã mất kết nối.')
      } else if (connection.connectionState === 'disconnected') {
        clearDisconnectTimer()
        disconnectTimerRef.current = globalThis.setTimeout(() => {
          if (peerConnectionRef.current === connection
            && connection.connectionState === 'disconnected') {
            finishCall(CALL_STATUS.ERROR, 'network', 'Cuộc gọi đã mất kết nối.')
          }
        }, disconnectGraceMs)
      } else if (connection.connectionState === 'closed'
        && callRef.current.callId === snapshot.callId
        && ![CALL_STATUS.ENDED, CALL_STATUS.ERROR, CALL_STATUS.IDLE].includes(callRef.current.status)) {
        finishCall(CALL_STATUS.ENDED, 'ended')
      }
    }
    return connection
  }, [RTCPeerConnectionClass, clearDisconnectTimer, clearOutgoingTimer, commitCall,
    disconnectGraceMs, finishCall, iceServers, sendSignal])

  const acquireMicrophone = useCallback(async () => {
    if (!mediaDevices?.getUserMedia) throw new Error('Trình duyệt không hỗ trợ truy cập micrô.')
    return mediaDevices.getUserMedia({ audio: true, video: false })
  }, [mediaDevices])

  const startCall = useCallback(async (friend) => {
    const peer = normalizeCallPeer(friend)
    if (!peer || !isUuid(peer.id)) throw new Error('Vui lòng chọn một người bạn để gọi.')
    if (!currentUserId || peer.id === currentUserId) throw new Error('Không thể bắt đầu cuộc gọi này.')
    if (connectionStatus !== 'connected') throw new Error('Kết nối cuộc gọi chưa sẵn sàng.')
    if (![CALL_STATUS.IDLE, CALL_STATUS.ENDED, CALL_STATUS.ERROR].includes(callRef.current.status)) {
      throw new Error('Bạn đang có một cuộc gọi khác.')
    }

    releaseMedia()
    const operation = operationRef.current
    const snapshot = { ...INITIAL_CALL, status: CALL_STATUS.OUTGOING, callId: createCallId(), peer }
    commitCall(snapshot)
    let stream = null
    let connection = null
    try {
      stream = await acquireMicrophone()
      if (!isCurrentOperation(operation, snapshot)) {
        discardStaleResources(null, stream)
        return null
      }
      localStreamRef.current = stream
      connection = createConnection(snapshot, stream)
      const offer = await connection.createOffer({ offerToReceiveAudio: true })
      if (!isCurrentOperation(operation, snapshot, connection)) {
        discardStaleResources(connection, stream)
        return null
      }
      await connection.setLocalDescription(offer)
      if (!isCurrentOperation(operation, snapshot, connection)) {
        discardStaleResources(connection, stream)
        return null
      }
      sendSignal({
        callId: snapshot.callId,
        recipientUserId: peer.id,
        type: CALL_SIGNAL.OFFER,
        sdp: connection.localDescription?.sdp || offer.sdp,
      })
      outgoingTimerRef.current = globalThis.setTimeout(() => {
        const active = callRef.current
        if (active.callId !== snapshot.callId
          || ![CALL_STATUS.OUTGOING, CALL_STATUS.CONNECTING].includes(active.status)) return
        sendTerminalBestEffort(CALL_SIGNAL.HANGUP, active)
        finishCall(CALL_STATUS.ENDED, 'timeout')
      }, options.outgoingTimeoutMs || OUTGOING_TIMEOUT_MS)
      return snapshot.callId
    } catch (error) {
      if (!isCurrentOperation(operation, snapshot, connection)) {
        discardStaleResources(connection, stream)
        return null
      }
      finishCall(CALL_STATUS.ERROR, '', mediaErrorMessage(error))
      throw new Error(mediaErrorMessage(error))
    }
  }, [acquireMicrophone, commitCall, connectionStatus, createConnection, currentUserId,
    discardStaleResources, finishCall, isCurrentOperation, options.outgoingTimeoutMs,
    releaseMedia, sendSignal, sendTerminalBestEffort])

  const acceptCall = useCallback(async () => {
    const snapshot = callRef.current
    if (snapshot.status !== CALL_STATUS.INCOMING || !snapshot.offer?.sdp) return
    const operation = operationRef.current
    clearOutgoingTimer()
    commitCall((current) => ({ ...current, status: CALL_STATUS.CONNECTING, error: '' }))
    let stream = null
    let connection = null
    const stillCurrent = () => isCurrentOperation(operation, snapshot, connection)
    try {
      stream = await acquireMicrophone()
      if (!isCurrentOperation(operation, snapshot)) {
        discardStaleResources(null, stream)
        return
      }
      localStreamRef.current = stream
      connection = createConnection(snapshot, stream)
      await connection.setRemoteDescription({ type: 'offer', sdp: snapshot.offer.sdp })
      if (!stillCurrent()) {
        discardStaleResources(connection, stream)
        return
      }
      if (!await flushPendingCandidates(connection, stillCurrent) || !stillCurrent()) {
        discardStaleResources(connection, stream)
        return
      }
      const answer = await connection.createAnswer()
      if (!stillCurrent()) {
        discardStaleResources(connection, stream)
        return
      }
      await connection.setLocalDescription(answer)
      if (!stillCurrent()) {
        discardStaleResources(connection, stream)
        return
      }
      sendSignal({
        callId: snapshot.callId,
        recipientUserId: snapshot.peer.id,
        type: CALL_SIGNAL.ANSWER,
        sdp: connection.localDescription?.sdp || answer.sdp,
      })
      outgoingTimerRef.current = globalThis.setTimeout(() => {
        const active = callRef.current
        if (active.callId !== snapshot.callId || active.status !== CALL_STATUS.CONNECTING) return
        sendTerminalBestEffort(CALL_SIGNAL.HANGUP, active)
        finishCall(CALL_STATUS.ENDED, 'connection-timeout')
      }, options.connectionTimeoutMs || OUTGOING_TIMEOUT_MS)
    } catch (error) {
      if (!stillCurrent()) {
        discardStaleResources(connection, stream)
        return
      }
      sendTerminalBestEffort(CALL_SIGNAL.REJECT, snapshot)
      finishCall(CALL_STATUS.ERROR, '', mediaErrorMessage(error))
    }
  }, [acquireMicrophone, clearOutgoingTimer, commitCall, createConnection, finishCall,
    flushPendingCandidates, discardStaleResources, isCurrentOperation,
    options.connectionTimeoutMs, sendSignal, sendTerminalBestEffort])

  const rejectCall = useCallback(() => {
    const snapshot = callRef.current
    if (snapshot.status !== CALL_STATUS.INCOMING) return
    sendTerminalBestEffort(CALL_SIGNAL.REJECT, snapshot)
    finishCall(CALL_STATUS.ENDED, 'declined-local')
  }, [finishCall, sendTerminalBestEffort])

  const endCall = useCallback(() => {
    const snapshot = callRef.current
    if ([CALL_STATUS.IDLE, CALL_STATUS.ENDED, CALL_STATUS.ERROR].includes(snapshot.status)) {
      resetCall()
      return
    }
    sendTerminalBestEffort(CALL_SIGNAL.HANGUP, snapshot)
    finishCall(CALL_STATUS.ENDED, 'ended')
  }, [finishCall, resetCall, sendTerminalBestEffort])

  const toggleMute = useCallback(() => {
    const tracks = localStreamRef.current?.getAudioTracks?.() || []
    if (!tracks.length) return
    const muted = tracks.some((track) => track.enabled)
    tracks.forEach((track) => { track.enabled = !muted })
    commitCall((current) => ({ ...current, isMuted: muted }))
  }, [commitCall])

  const handleSignal = useCallback(async (signal) => {
    if (!validSignalEnvelope(signal, currentUserId) || signal.senderUserId === currentUserId) return
    const active = callRef.current

    if (signal.type === CALL_SIGNAL.OFFER) {
      if (typeof signal.sdp !== 'string' || !signal.sdp.trim()) return
      if (![CALL_STATUS.IDLE, CALL_STATUS.ENDED, CALL_STATUS.ERROR].includes(active.status)) {
        try {
          sendSignal({
            callId: signal.callId,
            recipientUserId: signal.senderUserId,
            type: CALL_SIGNAL.REJECT,
          })
        } catch { /* best effort busy response */ }
        return
      }
      releaseMedia()
      commitCall({
        ...INITIAL_CALL,
        status: CALL_STATUS.INCOMING,
        callId: signal.callId,
        peer: { id: signal.senderUserId, userId: signal.senderUserId },
        offer: { type: 'offer', sdp: signal.sdp },
      })
      outgoingTimerRef.current = globalThis.setTimeout(() => {
        const current = callRef.current
        if (current.callId !== signal.callId || current.status !== CALL_STATUS.INCOMING) return
        sendTerminalBestEffort(CALL_SIGNAL.REJECT, current)
        finishCall(CALL_STATUS.ENDED, 'missed')
      }, options.incomingTimeoutMs || OUTGOING_TIMEOUT_MS)
      return
    }

    if (signal.callId !== active.callId || signal.senderUserId !== active.peer?.id) return

    if (signal.type === CALL_SIGNAL.ANSWER) {
      if (active.status !== CALL_STATUS.OUTGOING || typeof signal.sdp !== 'string' || !signal.sdp.trim()) return
      const connection = peerConnectionRef.current
      if (!connection) return
      const operation = operationRef.current
      const stillCurrent = () => isCurrentOperation(operation, active, connection)
      commitCall((current) => ({ ...current, status: CALL_STATUS.CONNECTING }))
      try {
        await connection.setRemoteDescription({ type: 'answer', sdp: signal.sdp })
        if (!stillCurrent()) return
        await flushPendingCandidates(connection, stillCurrent)
      } catch (error) {
        if (stillCurrent()) finishCall(CALL_STATUS.ERROR, '', callErrorMessage(error))
      }
      return
    }

    if (signal.type === CALL_SIGNAL.ICE_CANDIDATE) {
      if (!LIVE_ICE_STATUSES.has(active.status)) return
      if (typeof signal.candidate !== 'string' || !signal.candidate.trim()) return
      const candidate = {
        candidate: signal.candidate,
        sdpMid: signal.sdpMid ?? null,
        sdpMLineIndex: signal.sdpMLineIndex ?? null,
      }
      const connection = peerConnectionRef.current
      if (!connection?.remoteDescription) {
        if (pendingCandidatesRef.current.length >= MAX_PENDING_ICE_CANDIDATES) return
        const duplicate = pendingCandidatesRef.current.some((pending) => (
          pending.candidate === candidate.candidate
          && pending.sdpMid === candidate.sdpMid
          && pending.sdpMLineIndex === candidate.sdpMLineIndex
        ))
        if (!duplicate) pendingCandidatesRef.current.push(candidate)
      } else {
        const operation = operationRef.current
        try {
          await connection.addIceCandidate(candidate)
        } catch {
          // Ignore malformed peer candidates without terminating a healthy call.
        }
        if (!isCurrentOperation(operation, active, connection)) return
      }
      return
    }

    if (signal.type === CALL_SIGNAL.REJECT) {
      if (active.status === CALL_STATUS.OUTGOING) {
        finishCall(CALL_STATUS.ENDED, 'peer-rejected')
      }
    } else if (signal.type === CALL_SIGNAL.HANGUP) {
      finishCall(CALL_STATUS.ENDED, 'remote-ended')
    }
  }, [commitCall, currentUserId, finishCall, flushPendingCandidates, isCurrentOperation,
    options.incomingTimeoutMs, releaseMedia, sendSignal, sendTerminalBestEffort])

  const handleSocketError = useCallback((error) => {
    const active = callRef.current
    if (error?.callId && error.callId !== active.callId) return
    if ([CALL_STATUS.IDLE, CALL_STATUS.ENDED, CALL_STATUS.ERROR].includes(active.status)) return
    if (!error?.callId && active.status === CALL_STATUS.ACTIVE) return
    finishCall(CALL_STATUS.ERROR, '', callErrorMessage(error))
  }, [finishCall])

  useEffect(() => {
    if (previousIdentityRef.current === identity) return
    previousIdentityRef.current = identity
    resetCall()
  }, [identity, resetCall])

  useEffect(() => {
    mountedRef.current = true
    const generation = ++socketGenerationRef.current
    const isCurrent = () => socketGenerationRef.current === generation
    if (!identity || !hasAccessToken || !isUuid(currentUserId)) {
      socketRef.current = null
      setConnectionStatus('disconnected')
      resetCall()
      return undefined
    }

    const socket = socketFactory({
      onSignal: (signal) => { if (isCurrent()) handleSignal(signal) },
      onError: (error) => { if (isCurrent()) handleSocketError(error) },
      onStatus: (status) => {
        if (!isCurrent()) return
        setConnectionStatus(status)
        if (['error', 'disconnected', 'reconnecting'].includes(status)
          && [CALL_STATUS.OUTGOING, CALL_STATUS.INCOMING, CALL_STATUS.CONNECTING]
            .includes(callRef.current.status)) {
          finishCall(CALL_STATUS.ERROR, 'network', 'Kết nối cuộc gọi đã bị gián đoạn.')
        }
      },
    })
    socketRef.current = socket

    return () => {
      if (isCurrent()) socketGenerationRef.current += 1
      if (socketRef.current === socket) socketRef.current = null
      socket.deactivate?.().catch?.(() => {})
    }
  }, [currentUserId, finishCall, handleSignal, handleSocketError, identity, releaseMedia,
    hasAccessToken, resetCall, session?.accessToken, socketFactory])

  useEffect(() => () => {
    mountedRef.current = false
    releaseMedia()
  }, [releaseMedia])

  return useMemo(() => ({
    ...call,
    connectionStatus,
    remoteAudioRef,
    attachRemoteAudio,
    startCall,
    acceptCall,
    rejectCall,
    endCall,
    toggleMute,
    resetCall,
  }), [acceptCall, attachRemoteAudio, call, connectionStatus, endCall, rejectCall, resetCall,
    startCall, toggleMute])
}
