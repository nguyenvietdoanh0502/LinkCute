import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api, ensureFreshSession } from '../api/client.js'
import {
  chatMessagePayload,
  chatErrorMessage,
  chatPeerId,
  mergeChatMessages,
  optimisticChatMessage,
  optimisticLocationMessage,
  updateChatMessage,
} from '../chat/model.js'
import { createChatSocket } from '../realtime/chatSocket.js'

const EMPTY_HISTORY = { loading: false, error: '', hasMore: false, nextBefore: null, nextBeforeId: null }
const ACK_TIMEOUT_MS = 12_000

function createClientMessageId() {
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

export function useChat(session) {
  const currentUserId = session?.user?.id || null
  const identity = currentUserId || session?.user?.email || ''
  const [messagesByFriend, setMessagesByFriend] = useState({})
  const [historyByFriend, setHistoryByFriend] = useState({})
  const [unreadByFriend, setUnreadByFriend] = useState({})
  const [connectionStatus, setConnectionStatus] = useState('disconnected')
  const [socketError, setSocketError] = useState('')
  const socketRef = useRef(null)
  const socketGenerationRef = useRef(0)
  const activeFriendRef = useRef(null)
  const messagesRef = useRef(messagesByFriend)
  const historyRef = useRef(historyByFriend)
  const historyRequestsRef = useRef(new Map())
  const acknowledgementTimersRef = useRef(new Map())

  useEffect(() => { messagesRef.current = messagesByFriend }, [messagesByFriend])
  useEffect(() => { historyRef.current = historyByFriend }, [historyByFriend])

  useEffect(() => {
    historyRequestsRef.current.forEach((controller) => controller.abort())
    historyRequestsRef.current.clear()
    activeFriendRef.current = null
    messagesRef.current = {}
    setMessagesByFriend({})
    setHistoryByFriend({})
    setUnreadByFriend({})
    setSocketError('')
    return () => {
      historyRequestsRef.current.forEach((controller) => controller.abort())
      historyRequestsRef.current.clear()
      acknowledgementTimersRef.current.forEach((timer) => window.clearTimeout(timer))
      acknowledgementTimersRef.current.clear()
    }
  }, [identity])

  const commitMessages = useCallback((transform) => {
    const next = transform(messagesRef.current)
    messagesRef.current = next
    setMessagesByFriend(next)
    return next
  }, [])

  const clearAcknowledgementTimer = useCallback((clientMessageId) => {
    if (!clientMessageId) return
    const timer = acknowledgementTimersRef.current.get(clientMessageId)
    if (timer) window.clearTimeout(timer)
    acknowledgementTimersRef.current.delete(clientMessageId)
  }, [])

  const scheduleAcknowledgementTimeout = useCallback((friendId, clientMessageId) => {
    clearAcknowledgementTimer(clientMessageId)
    const timer = window.setTimeout(() => {
      acknowledgementTimersRef.current.delete(clientMessageId)
      commitMessages((current) => ({
        ...current,
        [friendId]: updateChatMessage(current[friendId] || [], clientMessageId, {
          status: 'FAILED',
          error: 'Chưa nhận được xác nhận từ máy chủ. Bạn có thể gửi lại an toàn.',
        }, currentUserId),
      }))
    }, ACK_TIMEOUT_MS)
    acknowledgementTimersRef.current.set(clientMessageId, timer)
  }, [clearAcknowledgementTimer, commitMessages, currentUserId])

  const handleMessage = useCallback((message) => {
    const friendId = chatPeerId(message, currentUserId)
    if (!friendId) return
    if (message.senderId === currentUserId) {
      clearAcknowledgementTimer(message.clientMessageId)
    }

    const currentMessages = messagesRef.current[friendId] || []
    const alreadyKnown = currentMessages.some((candidate) => (
      (message.clientMessageId
        && candidate.clientMessageId === message.clientMessageId
        && candidate.senderId === message.senderId)
      || (message.id && candidate.id === message.id)
    ))
    const nextMessages = mergeChatMessages(currentMessages, message)
    const nextByFriend = { ...messagesRef.current, [friendId]: nextMessages }
    messagesRef.current = nextByFriend
    setMessagesByFriend(nextByFriend)

    if (!alreadyKnown && message.senderId !== currentUserId && activeFriendRef.current !== friendId) {
      setUnreadByFriend((current) => ({
        ...current,
        [friendId]: (current[friendId] || 0) + 1,
      }))
    }
  }, [clearAcknowledgementTimer, currentUserId])

  const handleSocketError = useCallback((error) => {
    const message = chatErrorMessage(error)
    if (!error?.clientMessageId) {
      setSocketError(message)
      return
    }

    clearAcknowledgementTimer(error.clientMessageId)

    commitMessages((current) => Object.fromEntries(
      Object.entries(current).map(([friendId, messages]) => [
        friendId,
        updateChatMessage(messages, error.clientMessageId, { status: 'FAILED', error: message }, currentUserId),
      ]),
    ))
  }, [clearAcknowledgementTimer, commitMessages, currentUserId])

  useEffect(() => {
    const generation = ++socketGenerationRef.current
    const isCurrentGeneration = () => socketGenerationRef.current === generation
    if (!identity || !session?.accessToken) {
      socketRef.current = null
      setConnectionStatus('disconnected')
      return undefined
    }

    setSocketError('')
    const socket = createChatSocket({
      onMessage: (message) => {
        if (isCurrentGeneration()) handleMessage(message)
      },
      onError: (error) => {
        if (isCurrentGeneration()) handleSocketError(error)
      },
      onStatus: (status) => {
        if (isCurrentGeneration()) setConnectionStatus(status)
      },
    })
    socketRef.current = socket

    return () => {
      if (isCurrentGeneration()) socketGenerationRef.current += 1
      if (socketRef.current === socket) socketRef.current = null
      socket.deactivate().catch(() => {})
    }
  }, [handleMessage, handleSocketError, identity, session?.accessToken])

  useEffect(() => {
    const expiresAt = Number(session?.expiresAt || 0)
    if (!identity || !expiresAt) return undefined

    const delay = Math.max(0, expiresAt - Date.now() - 60_000)
    const timer = window.setTimeout(() => {
      ensureFreshSession(60_000).catch(() => {})
    }, delay)
    return () => window.clearTimeout(timer)
  }, [identity, session?.expiresAt])

  const loadHistory = useCallback(async (friendId, { before = null, beforeId = null } = {}) => {
    if (!identity || !friendId) return null

    historyRequestsRef.current.get(friendId)?.abort()
    const controller = new AbortController()
    historyRequestsRef.current.set(friendId, controller)
    setHistoryByFriend((current) => ({
      ...current,
      [friendId]: { ...(current[friendId] || EMPTY_HISTORY), loading: true, error: '' },
    }))

    try {
      const history = await api.getChatHistory(
        friendId,
        { before, beforeId, size: 50 },
        { signal: controller.signal },
      )
      ;(history?.messages || [])
        .filter((message) => message.senderId === currentUserId)
        .forEach((message) => clearAcknowledgementTimer(message.clientMessageId))
      commitMessages((current) => ({
          ...current,
          [friendId]: mergeChatMessages(history?.messages || [], current[friendId] || []),
      }))
      setHistoryByFriend((current) => ({
        ...current,
        [friendId]: {
          loading: false,
          error: '',
          hasMore: Boolean(history?.hasMore),
          nextBefore: history?.nextBefore || null,
          nextBeforeId: history?.nextBeforeId || null,
        },
      }))
      return history
    } catch (error) {
      if (error.name === 'AbortError') return null
      setHistoryByFriend((current) => ({
        ...current,
        [friendId]: {
          ...(current[friendId] || EMPTY_HISTORY),
          loading: false,
          error: chatErrorMessage(error, 'Không thể tải lịch sử trò chuyện.'),
        },
      }))
      throw error
    } finally {
      if (historyRequestsRef.current.get(friendId) === controller) {
        historyRequestsRef.current.delete(friendId)
      }
    }
  }, [clearAcknowledgementTimer, commitMessages, currentUserId, identity])

  const loadOlder = useCallback((friendId) => {
    const history = historyRef.current[friendId]
    if (!history?.hasMore || !history.nextBefore || history.loading) return Promise.resolve(null)
    return loadHistory(friendId, { before: history.nextBefore, beforeId: history.nextBeforeId })
  }, [loadHistory])

  const setActiveConversation = useCallback((friendId) => {
    activeFriendRef.current = friendId || null
    if (friendId) {
      setUnreadByFriend((current) => ({ ...current, [friendId]: 0 }))
    }
  }, [])

  const sendOptimisticMessage = useCallback((friendId, optimistic) => {
    const nextByFriend = {
      ...messagesRef.current,
      [friendId]: mergeChatMessages(messagesRef.current[friendId] || [], optimistic),
    }
    messagesRef.current = nextByFriend
    setMessagesByFriend(nextByFriend)

    try {
      if (!socketRef.current) throw new Error('Kết nối realtime chưa sẵn sàng.')
      socketRef.current.send(chatMessagePayload(optimistic))
      scheduleAcknowledgementTimeout(friendId, optimistic.clientMessageId)
      return optimistic.clientMessageId
    } catch (error) {
      const message = chatErrorMessage(error)
      commitMessages((current) => ({
        ...current,
        [friendId]: updateChatMessage(current[friendId] || [], optimistic.clientMessageId, {
          status: 'FAILED',
          error: message,
        }, currentUserId),
      }))
      throw new Error(message)
    }
  }, [commitMessages, currentUserId, scheduleAcknowledgementTimeout])

  const sendMessage = useCallback((friendId, content) => {
    if (!currentUserId || !friendId) throw new Error('Vui lòng chọn một người bạn để nhắn tin.')
    const optimistic = optimisticChatMessage({
      clientMessageId: createClientMessageId(),
      senderId: currentUserId,
      recipientId: friendId,
      content,
    })
    return sendOptimisticMessage(friendId, optimistic)
  }, [currentUserId, sendOptimisticMessage])

  const sendLocation = useCallback((friendId, location) => {
    if (!currentUserId || !friendId) throw new Error('Vui lòng chọn một người bạn để chia sẻ vị trí.')
    const optimistic = optimisticLocationMessage({
      clientMessageId: createClientMessageId(),
      senderId: currentUserId,
      recipientId: friendId,
      location,
    })
    return sendOptimisticMessage(friendId, optimistic)
  }, [currentUserId, sendOptimisticMessage])

  const retryMessage = useCallback((friendId, clientMessageId) => {
    const message = (messagesRef.current[friendId] || [])
      .find((candidate) => candidate.clientMessageId === clientMessageId
        && candidate.senderId === currentUserId)
    if (!message) return

    commitMessages((current) => ({
      ...current,
      [friendId]: updateChatMessage(current[friendId] || [], clientMessageId, {
        status: 'SENDING',
        error: null,
      }, currentUserId),
    }))
    try {
      if (!socketRef.current) throw new Error('Kết nối realtime chưa sẵn sàng.')
      socketRef.current.send(chatMessagePayload(message))
      scheduleAcknowledgementTimeout(friendId, clientMessageId)
    } catch (error) {
      commitMessages((current) => ({
        ...current,
        [friendId]: updateChatMessage(current[friendId] || [], clientMessageId, {
          status: 'FAILED',
          error: chatErrorMessage(error),
        }, currentUserId),
      }))
    }
  }, [commitMessages, currentUserId, scheduleAcknowledgementTimeout])

  const totalUnread = useMemo(() => Object.values(unreadByFriend)
    .reduce((total, count) => total + Number(count || 0), 0), [unreadByFriend])

  return useMemo(() => ({
    messagesByFriend,
    historyByFriend,
    unreadByFriend,
    totalUnread,
    connectionStatus,
    socketError,
    loadHistory,
    loadOlder,
    setActiveConversation,
    sendMessage,
    sendLocation,
    retryMessage,
  }), [
    messagesByFriend,
    historyByFriend,
    unreadByFriend,
    totalUnread,
    connectionStatus,
    socketError,
    loadHistory,
    loadOlder,
    setActiveConversation,
    sendMessage,
    sendLocation,
    retryMessage,
  ])
}
