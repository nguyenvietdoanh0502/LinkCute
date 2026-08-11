import {
  AlertCircle,
  ChevronUp,
  MapPin,
  MessageCircle,
  RefreshCw,
  Send,
  UsersRound,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { MAX_CHAT_MESSAGE_LENGTH } from '../chat/model.js'
import {
  LOCATION_MESSAGE_TYPE,
  chatMessageType,
  locationAccuracyLabel,
  locationFromMessage,
} from '../location/model.js'
import ProfileAvatar from './ProfileAvatar.jsx'

const FOCUSABLE_SELECTOR = [
  'button:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

const TIME_FORMATTER = new Intl.DateTimeFormat('vi-VN', {
  hour: '2-digit',
  minute: '2-digit',
})

const DAY_FORMATTER = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
})

function formatMessageTime(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const today = new Date()
  const sameDay = date.getFullYear() === today.getFullYear()
    && date.getMonth() === today.getMonth()
    && date.getDate() === today.getDate()
  return sameDay
    ? TIME_FORMATTER.format(date)
    : `${DAY_FORMATTER.format(date)} · ${TIME_FORMATTER.format(date)}`
}

function connectionCopy(status) {
  if (status === 'connected') return ['Đã kết nối realtime', 'is-online']
  if (status === 'connecting') return ['Đang kết nối…', 'is-connecting']
  if (status === 'reconnecting') return ['Đang kết nối lại…', 'is-connecting']
  return ['Mất kết nối realtime', 'is-offline']
}

function EmptyConversation({ hasFriends }) {
  return (
    <div className="chat-empty">
      <span>{hasFriends ? <MessageCircle size={28} /> : <UsersRound size={28} />}</span>
      <h3>{hasFriends ? 'Chọn một người bạn' : 'Chưa có bạn bè để nhắn tin'}</h3>
      <p>{hasFriends
        ? 'Chọn một người trong danh sách để mở cuộc trò chuyện.'
        : 'Hai người cần kết bạn trước khi có thể bắt đầu trò chuyện.'}</p>
    </div>
  )
}

function LocationMessage({ message, onOpen }) {
  const location = locationFromMessage(message)
  return (
    <div className={`chat-location-message ${location ? '' : 'chat-location-message--invalid'}`}>
      <span><MapPin size={20} aria-hidden="true" /></span>
      <div>
        <strong>{location ? 'Vị trí được chia sẻ' : 'Vị trí không hợp lệ'}</strong>
        <small>{location
          ? locationAccuracyLabel(location)
          : 'Tin nhắn này không chứa tọa độ có thể mở trên bản đồ.'}</small>
      </div>
      {location && (
        <button type="button" onClick={onOpen}>
          Mở trên bản đồ
        </button>
      )}
    </div>
  )
}

export default function ChatPanel({
  open,
  onClose,
  friends,
  initialFriendId,
  currentUserId,
  chatState,
  showToast,
  onOpenLocation,
}) {
  const panelRef = useRef(null)
  const composerRef = useRef(null)
  const messagesContainerRef = useRef(null)
  const messagesEndRef = useRef(null)
  const preserveScrollRef = useRef(null)
  const [selectedFriendId, setSelectedFriendId] = useState(() => initialFriendId || null)
  const [draft, setDraft] = useState('')
  const friendIds = useMemo(() => (friends || []).map((friend) => friend.user?.id).filter(Boolean), [friends])
  const selectedFriend = (friends || []).find((friend) => friend.user?.id === selectedFriendId) || null
  const messages = selectedFriendId ? (chatState.messagesByFriend[selectedFriendId] || []) : []
  const history = selectedFriendId
    ? (chatState.historyByFriend[selectedFriendId] || { loading: false, error: '', hasMore: false })
    : { loading: false, error: '', hasMore: false }
  const [connectionLabel, connectionClass] = connectionCopy(chatState.connectionStatus)

  useEffect(() => {
    if (!open) return
    setSelectedFriendId((current) => {
      if (initialFriendId && friendIds.includes(initialFriendId)) return initialFriendId
      if (current && friendIds.includes(current)) return current
      return friendIds[0] || null
    })
  }, [friendIds, initialFriendId, open])

  useEffect(() => {
    setDraft('')
  }, [open, selectedFriendId])

  useEffect(() => {
    if (!open || !selectedFriendId) {
      chatState.setActiveConversation(null)
      return undefined
    }
    chatState.setActiveConversation(selectedFriendId)
    chatState.loadHistory(selectedFriendId).catch(() => {})
    return () => chatState.setActiveConversation(null)
  }, [chatState.loadHistory, chatState.setActiveConversation, open, selectedFriendId])

  useEffect(() => {
    if (!open) return undefined
    const previousOverflow = document.body.style.overflow
    const previouslyFocused = document.activeElement
    document.body.style.overflow = 'hidden'
    const focusFrame = window.requestAnimationFrame(() => {
      if (selectedFriendId) composerRef.current?.focus()
      else panelRef.current?.focus()
    })

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose()
        return
      }
      if (event.key !== 'Tab') return
      const focusable = [...(panelRef.current?.querySelectorAll(FOCUSABLE_SELECTOR) || [])]
      if (!focusable.length) return
      const first = focusable[0]
      const last = focusable.at(-1)
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => {
      window.cancelAnimationFrame(focusFrame)
      document.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = previousOverflow
      if (previouslyFocused instanceof HTMLElement && previouslyFocused.isConnected) {
        previouslyFocused.focus()
      }
    }
  }, [onClose, open, selectedFriendId])

  useEffect(() => {
    if (!open || !selectedFriendId) {
      preserveScrollRef.current = null
      return undefined
    }
    const preservation = preserveScrollRef.current
    if (preservation) {
      if (preservation.friendId !== selectedFriendId) {
        preserveScrollRef.current = null
        messagesEndRef.current?.scrollIntoView({ block: 'end' })
        return undefined
      }
      if (history.loading) return undefined
      preserveScrollRef.current = null
      const frame = window.requestAnimationFrame(() => {
        const container = messagesContainerRef.current
        if (!container) return
        container.scrollTop = container.scrollHeight - preservation.scrollHeight + preservation.scrollTop
      })
      return () => window.cancelAnimationFrame(frame)
    }
    messagesEndRef.current?.scrollIntoView({ block: 'end' })
    return undefined
  }, [history.loading, messages.length, open, selectedFriendId])

  if (!open) return null

  const submitMessage = (event) => {
    event?.preventDefault()
    if (!selectedFriendId || !draft.trim()) return
    try {
      chatState.sendMessage(selectedFriendId, draft)
      setDraft('')
    } catch (error) {
      showToast(error.message, 'error')
    }
  }

  const handleComposerKeyDown = (event) => {
    if (event.key === 'Enter' && !event.shiftKey) submitMessage(event)
  }

  const handleLoadOlder = () => {
    const container = messagesContainerRef.current
    if (container) {
      preserveScrollRef.current = {
        friendId: selectedFriendId,
        scrollHeight: container.scrollHeight,
        scrollTop: container.scrollTop,
      }
    }
    chatState.loadOlder(selectedFriendId).catch(() => {})
  }

  return (
    <div className="chat-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section
        ref={panelRef}
        className="chat-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="chat-panel-title"
        tabIndex={-1}
      >
        <header className="chat-panel__header">
          <div>
            <span className="eyebrow">Trò chuyện riêng tư</span>
            <h2 id="chat-panel-title">Tin nhắn</h2>
          </div>
          <button type="button" onClick={onClose} aria-label="Đóng tin nhắn"><X size={21} /></button>
        </header>

        <div className="chat-layout">
          <aside className="chat-friend-list" aria-label="Bạn bè có thể nhắn tin">
            {(friends || []).map((friend) => {
              const friendId = friend.user?.id
              const unread = chatState.unreadByFriend[friendId] || 0
              return (
                <button
                  key={friend.friendshipId}
                  type="button"
                  className={selectedFriendId === friendId ? 'is-active' : ''}
                  onClick={() => setSelectedFriendId(friendId)}
                  aria-current={selectedFriendId === friendId ? 'true' : undefined}
                >
                  <ProfileAvatar user={friend.user} className="chat-avatar" alt="" />
                  <span>
                    <strong>{friend.user?.fullName || 'Thành viên LinkCute'}</strong>
                    <small>{unread ? `${unread} tin nhắn mới` : 'Mở trò chuyện'}</small>
                  </span>
                  {unread > 0 && <em>{unread > 99 ? '99+' : unread}</em>}
                </button>
              )
            })}
          </aside>

          <main className="chat-conversation">
            {!selectedFriend ? (
              <EmptyConversation hasFriends={friendIds.length > 0} />
            ) : (
              <>
                <header className="chat-conversation__header">
                  <ProfileAvatar user={selectedFriend.user} className="chat-avatar chat-avatar--large" />
                  <div>
                    <strong>{selectedFriend.user?.fullName || 'Thành viên LinkCute'}</strong>
                    <span className={connectionClass}><i />{connectionLabel}</span>
                  </div>
                </header>

                <div ref={messagesContainerRef} className="chat-messages" aria-live="polite" aria-label={`Tin nhắn với ${selectedFriend.user?.fullName || 'bạn bè'}`}>
                  {history.hasMore && (
                    <button
                      className="chat-load-more"
                      type="button"
                      onClick={handleLoadOlder}
                      disabled={history.loading}
                    >
                      {history.loading ? <RefreshCw className="is-spinning" size={15} /> : <ChevronUp size={15} />}
                      Xem tin nhắn cũ hơn
                    </button>
                  )}

                  {history.loading && !messages.length && (
                    <div className="chat-loading"><span className="loader" /> Đang tải cuộc trò chuyện…</div>
                  )}
                  {history.error && (
                    <div className="chat-inline-error" role="alert">
                      <AlertCircle size={16} />
                      <span>{history.error}</span>
                      <button type="button" onClick={() => chatState.loadHistory(selectedFriendId).catch(() => {})}>Thử lại</button>
                    </div>
                  )}
                  {!history.loading && !history.error && !messages.length && (
                    <div className="chat-first-message">
                      <MessageCircle size={27} />
                      <strong>Bắt đầu câu chuyện</strong>
                      <span>Hãy gửi một lời chào tới {selectedFriend.user?.fullName || 'người bạn này'}.</span>
                    </div>
                  )}

                  {messages.map((message) => {
                    const own = message.senderId === currentUserId
                    const messageType = chatMessageType(message)
                    return (
                      <article
                        key={message.id || `${message.senderId}:${message.clientMessageId}`}
                        className={`chat-message ${own ? 'chat-message--own' : ''} ${message.status === 'FAILED' ? 'chat-message--failed' : ''}`}
                      >
                        <div className="chat-message__bubble">
                          {messageType === LOCATION_MESSAGE_TYPE ? (
                            <LocationMessage
                              message={message}
                              onOpen={() => onOpenLocation?.(message, selectedFriend)}
                            />
                          ) : (message.content || 'Tin nhắn này không thể hiển thị.')}
                        </div>
                        <footer>
                          <time dateTime={message.createdAt}>{formatMessageTime(message.createdAt)}</time>
                          {own && message.status === 'SENDING' && <span>Đang gửi…</span>}
                          {own && message.status === 'FAILED' && (
                            <button type="button" onClick={() => chatState.retryMessage(selectedFriendId, message.clientMessageId)}>
                              Gửi lại
                            </button>
                          )}
                        </footer>
                        {message.status === 'FAILED' && message.error && <small role="alert">{message.error}</small>}
                      </article>
                    )
                  })}
                  <div ref={messagesEndRef} />
                </div>

                {chatState.socketError && <div className="chat-socket-error" role="alert">{chatState.socketError}</div>}
                <form className="chat-composer" onSubmit={submitMessage}>
                  <label>
                    <span className="sr-only">Nhập tin nhắn</span>
                    <textarea
                      ref={composerRef}
                      value={draft}
                      onChange={(event) => setDraft(event.target.value)}
                      onKeyDown={handleComposerKeyDown}
                      maxLength={MAX_CHAT_MESSAGE_LENGTH}
                      rows={1}
                      placeholder={`Nhắn cho ${selectedFriend.user?.fullName || 'bạn bè'}…`}
                    />
                  </label>
                  <button
                    type="submit"
                    disabled={!draft.trim() || chatState.connectionStatus !== 'connected'}
                    aria-label="Gửi tin nhắn"
                    title={chatState.connectionStatus === 'connected' ? 'Gửi tin nhắn' : 'Đang chờ kết nối realtime'}
                  >
                    <Send size={18} />
                  </button>
                </form>
              </>
            )}
          </main>
        </div>
      </section>
    </div>
  )
}
