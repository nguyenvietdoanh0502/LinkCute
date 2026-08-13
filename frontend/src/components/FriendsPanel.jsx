import {
  Check,
  Clock3,
  Copy,
  Inbox,
  MessageCircle,
  Phone,
  RefreshCw,
  Search,
  Send,
  UserMinus,
  UserPlus,
  UsersRound,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import {
  friendshipErrorMessage,
  normalizeFriendPin,
  profileInitials,
} from '../friends/model.js'

const FOCUSABLE_SELECTOR = [
  'button:not([disabled])',
  'input:not([disabled])',
  'a[href]',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

const TAB_ORDER = ['friends', 'incoming', 'outgoing']
const DATE_FORMATTER = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
})

function formatDate(value, fallback) {
  if (!value) return fallback
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? fallback : DATE_FORMATTER.format(date)
}

function ProfileAvatar({ user, size = 'normal' }) {
  const [imageFailed, setImageFailed] = useState(false)

  useEffect(() => setImageFailed(false), [user?.avatarUrl])

  return (
    <span className={`friend-avatar friend-avatar--${size}`} aria-hidden="true">
      {user?.avatarUrl && !imageFailed ? (
        <img
          src={user.avatarUrl}
          alt=""
          referrerPolicy="no-referrer"
          onError={() => setImageFailed(true)}
        />
      ) : profileInitials(user?.fullName)}
    </span>
  )
}

function UserIdentity({ user, meta }) {
  return (
    <div className="friend-identity">
      <strong>{user?.fullName || 'Thành viên LinkCute'}</strong>
      <span>{user?.pinCode || 'Chưa có mã kết bạn'}</span>
      {meta && <small>{meta}</small>}
    </div>
  )
}

function EmptyFriendsState({ icon: Icon, title, description }) {
  return (
    <div className="friends-empty">
      <span><Icon size={25} aria-hidden="true" /></span>
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
  )
}

function FriendCard({ friend, busy, callDisabled, onMessage, onCall, onRemove }) {
  return (
    <article className="friend-card">
      <ProfileAvatar user={friend.user} />
      <UserIdentity
        user={friend.user}
        meta={`Bạn bè từ ${formatDate(friend.friendsSince, 'gần đây')}`}
      />
      <div className="friend-card__actions">
        <button
          className="friend-icon-action friend-icon-action--call"
          type="button"
          onClick={() => onCall(friend)}
          disabled={callDisabled || !onCall}
          aria-label={`Gọi thoại cho ${friend.user?.fullName || 'thành viên này'}`}
          title={callDisabled || !onCall ? 'Cuộc gọi chưa sẵn sàng' : 'Gọi thoại'}
        >
          <Phone size={17} aria-hidden="true" />
        </button>
        <button
          className="friend-icon-action friend-icon-action--message"
          type="button"
          onClick={() => onMessage(friend)}
          aria-label={`Nhắn tin cho ${friend.user?.fullName || 'thành viên này'}`}
          title="Nhắn tin"
        >
          <MessageCircle size={17} aria-hidden="true" />
        </button>
        <button
          className="friend-icon-action friend-icon-action--danger"
          type="button"
          onClick={() => onRemove(friend)}
          disabled={busy}
          aria-label={`Hủy kết bạn với ${friend.user?.fullName || 'thành viên này'}`}
          title="Hủy kết bạn"
        >
          {busy ? <span className="friend-action-loader" /> : <UserMinus size={17} aria-hidden="true" />}
        </button>
      </div>
    </article>
  )
}

function IncomingRequestCard({ request, acceptBusy, deleteBusy, onAccept, onReject }) {
  return (
    <article className="friend-card friend-card--request">
      <ProfileAvatar user={request.user} />
      <UserIdentity
        user={request.user}
        meta={`Gửi lúc ${formatDate(request.createdAt, 'gần đây')}`}
      />
      <div className="friend-card__actions">
        <button
          className="friend-action friend-action--accept"
          type="button"
          onClick={() => onAccept(request)}
          disabled={acceptBusy || deleteBusy}
        >
          {acceptBusy ? <span className="friend-action-loader" /> : <Check size={16} aria-hidden="true" />}
          Chấp nhận
        </button>
        <button
          className="friend-action friend-action--muted"
          type="button"
          onClick={() => onReject(request)}
          disabled={acceptBusy || deleteBusy}
        >
          {deleteBusy ? <span className="friend-action-loader" /> : <X size={16} aria-hidden="true" />}
          Từ chối
        </button>
      </div>
    </article>
  )
}

function OutgoingRequestCard({ request, busy, onCancel }) {
  return (
    <article className="friend-card friend-card--request">
      <ProfileAvatar user={request.user} />
      <UserIdentity
        user={request.user}
        meta={`Đã gửi ${formatDate(request.createdAt, 'gần đây')}`}
      />
      <div className="friend-card__actions">
        <span className="friend-pending-label"><Clock3 size={14} aria-hidden="true" /> Đang chờ</span>
        <button
          className="friend-action friend-action--muted"
          type="button"
          onClick={() => onCancel(request)}
          disabled={busy}
        >
          {busy ? <span className="friend-action-loader" /> : <X size={16} aria-hidden="true" />}
          Thu hồi
        </button>
      </div>
    </article>
  )
}

function SearchResultCard({
  result,
  incomingRequest,
  outgoingRequest,
  friend,
  busyActions,
  onSend,
  onAccept,
  onReject,
  onCancel,
  onMessage,
  onCall,
  onRemove,
  callDisabled,
}) {
  const status = result.relationshipStatus || 'NONE'
  const acceptBusy = incomingRequest && busyActions.has(`accept:${incomingRequest.id}`)
  const incomingDeleteBusy = incomingRequest && busyActions.has(`delete-request:${incomingRequest.id}`)
  const outgoingDeleteBusy = outgoingRequest && busyActions.has(`delete-request:${outgoingRequest.id}`)
  const friendshipId = result.friendshipId || friend?.friendshipId
  const removeBusy = friendshipId && busyActions.has(`remove:${friendshipId}`)

  return (
    <article className="friend-search-result" aria-live="polite">
      <ProfileAvatar user={result} size="large" />
      <UserIdentity user={result} />

      <div className="friend-search-result__action">
        {status === 'NONE' && (
          <button
            className="button button--primary"
            type="button"
            onClick={() => onSend(result)}
            disabled={busyActions.has(`send:${result.id}`)}
          >
            {busyActions.has(`send:${result.id}`)
              ? <span className="button-loader" />
              : <UserPlus size={17} aria-hidden="true" />}
            Gửi lời mời
          </button>
        )}

        {status === 'INCOMING_PENDING' && incomingRequest && (
          <>
            <button
              className="button button--primary"
              type="button"
              onClick={() => onAccept(incomingRequest)}
              disabled={acceptBusy || incomingDeleteBusy}
            >
              {acceptBusy ? <span className="button-loader" /> : <Check size={17} aria-hidden="true" />}
              Chấp nhận
            </button>
            <button
              className="button button--secondary"
              type="button"
              onClick={() => onReject(incomingRequest)}
              disabled={acceptBusy || incomingDeleteBusy}
            >
              Từ chối
            </button>
          </>
        )}

        {status === 'INCOMING_PENDING' && !incomingRequest && (
          <span className="friend-status friend-status--incoming"><Inbox size={15} /> Bạn đã nhận lời mời</span>
        )}

        {status === 'OUTGOING_PENDING' && (
          <>
            <span className="friend-status"><Clock3 size={15} /> Đang chờ phản hồi</span>
            {outgoingRequest && (
              <button
                className="button button--secondary"
                type="button"
                onClick={() => onCancel(outgoingRequest)}
                disabled={outgoingDeleteBusy}
              >
                {outgoingDeleteBusy ? <span className="friend-action-loader" /> : <X size={16} />}
                Thu hồi
              </button>
            )}
          </>
        )}

        {status === 'FRIENDS' && (
          <>
            <span className="friend-status friend-status--friends"><Check size={15} /> Đã là bạn bè</span>
            <button
              className="button button--secondary"
              type="button"
              onClick={() => onCall({ friendshipId, user: result })}
              disabled={callDisabled || !onCall}
            >
              <Phone size={16} /> Gọi thoại
            </button>
            <button
              className="button button--primary"
              type="button"
              onClick={() => onMessage({ friendshipId, user: result })}
            >
              <MessageCircle size={16} /> Nhắn tin
            </button>
            {friendshipId && (
              <button
                className="button button--secondary"
                type="button"
                onClick={() => onRemove({ friendshipId, user: result })}
                disabled={removeBusy}
              >
                {removeBusy ? <span className="friend-action-loader" /> : <UserMinus size={16} />}
                Hủy kết bạn
              </button>
            )}
          </>
        )}
      </div>
    </article>
  )
}

export default function FriendsPanel({
  open,
  onClose,
  session,
  friendshipState,
  showToast,
  onOpenChat,
  onStartCall,
  callDisabled = false,
}) {
  const drawerRef = useRef(null)
  const [activeTab, setActiveTab] = useState('friends')
  const [pinCode, setPinCode] = useState('')
  const {
    friends,
    incomingRequests,
    outgoingRequests,
    loading,
    error,
    searchResult,
    searchLoading,
    searchError,
    busyActions,
    refresh,
    clearSearch,
    searchByPin,
    sendRequest,
    acceptRequest,
    deleteRequest,
    removeFriend,
  } = friendshipState

  const matchingIncoming = useMemo(() => (
    incomingRequests.find((request) => request.user?.id === searchResult?.id)
      || (searchResult?.relationshipStatus === 'INCOMING_PENDING' && searchResult.friendshipId
        ? { id: searchResult.friendshipId, user: searchResult }
        : null)
  ), [incomingRequests, searchResult])
  const matchingOutgoing = useMemo(() => (
    outgoingRequests.find((request) => request.user?.id === searchResult?.id)
      || (searchResult?.relationshipStatus === 'OUTGOING_PENDING' && searchResult.friendshipId
        ? { id: searchResult.friendshipId, user: searchResult }
        : null)
  ), [outgoingRequests, searchResult])
  const matchingFriend = useMemo(() => (
    friends.find((friend) => friend.user?.id === searchResult?.id)
  ), [friends, searchResult?.id])

  useEffect(() => {
    if (!open) return undefined

    const previousOverflow = document.body.style.overflow
    const previouslyFocused = document.activeElement
    document.body.style.overflow = 'hidden'
    const focusFrame = window.requestAnimationFrame(() => {
      const initialControl = drawerRef.current?.querySelector('[data-friends-initial-focus]')
        || drawerRef.current?.querySelector(FOCUSABLE_SELECTOR)
      initialControl?.focus()
    })

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose()
        return
      }
      if (event.key !== 'Tab') return

      const focusable = Array.from(
        drawerRef.current?.querySelectorAll(FOCUSABLE_SELECTOR) || [],
      ).filter((element) => element.getClientRects().length > 0)
      if (focusable.length === 0) {
        event.preventDefault()
        drawerRef.current?.focus()
        return
      }

      const first = focusable[0]
      const last = focusable.at(-1)
      const currentFocus = document.activeElement
      if (!drawerRef.current?.contains(currentFocus)) {
        event.preventDefault()
        first.focus()
      } else if (event.shiftKey && currentFocus === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && currentFocus === last) {
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
  }, [onClose, open])

  if (!open) return null

  const runAction = async (action, successMessage) => {
    try {
      await action()
      showToast(successMessage)
    } catch (requestError) {
      showToast(friendshipErrorMessage(requestError), 'error')
    }
  }

  const handleSearch = async (event) => {
    event.preventDefault()
    const normalizedPin = normalizeFriendPin(pinCode)
    setPinCode(normalizedPin)
    try {
      await searchByPin(normalizedPin)
    } catch {
      // The hook exposes a field-level error below the search form.
    }
  }

  const updatePin = (event) => {
    setPinCode(event.target.value.toUpperCase().slice(0, 10))
    if (searchResult || searchError) clearSearch()
  }

  const handleCopyPin = async () => {
    const ownPin = session?.user?.pinCode
    if (!ownPin) return
    try {
      await navigator.clipboard.writeText(ownPin)
      showToast('Đã sao chép mã kết bạn của bạn.')
    } catch {
      showToast(`Mã kết bạn của bạn là ${ownPin}.`, 'error')
    }
  }

  const handleSend = (user) => runAction(
    () => sendRequest(user.id),
    `Đã gửi lời mời kết bạn tới ${user.fullName}.`,
  )
  const handleAccept = (request) => runAction(
    () => acceptRequest(request.id, request.user?.id),
    `Bạn và ${request.user?.fullName || 'thành viên này'} đã trở thành bạn bè.`,
  )
  const handleReject = (request) => runAction(
    () => deleteRequest(request.id, request.user?.id),
    'Đã từ chối lời mời kết bạn.',
  )
  const handleCancel = (request) => runAction(
    () => deleteRequest(request.id, request.user?.id),
    'Đã thu hồi lời mời kết bạn.',
  )
  const handleRemove = (friend) => {
    const fullName = friend.user?.fullName || 'thành viên này'
    if (!window.confirm(`Hủy kết bạn với ${fullName}?`)) return
    runAction(
      () => removeFriend(friend.friendshipId, friend.user?.id),
      `Đã hủy kết bạn với ${fullName}.`,
    )
  }
  const handleMessage = (friend) => onOpenChat?.(friend)
  const handleCall = (friend) => onStartCall?.(friend)

  const handleTabKeyDown = (event, currentTab) => {
    const currentIndex = TAB_ORDER.indexOf(currentTab)
    let nextTab = null
    if (event.key === 'ArrowRight') nextTab = TAB_ORDER[(currentIndex + 1) % TAB_ORDER.length]
    if (event.key === 'ArrowLeft') nextTab = TAB_ORDER[(currentIndex - 1 + TAB_ORDER.length) % TAB_ORDER.length]
    if (event.key === 'Home') nextTab = TAB_ORDER[0]
    if (event.key === 'End') nextTab = TAB_ORDER.at(-1)
    if (!nextTab) return

    event.preventDefault()
    setActiveTab(nextTab)
    window.requestAnimationFrame(() => document.getElementById(`friends-${nextTab}-tab`)?.focus())
  }

  const tabs = [
    { id: 'friends', label: 'Bạn bè', count: friends.length },
    { id: 'incoming', label: 'Lời mời', count: incomingRequests.length },
    { id: 'outgoing', label: 'Đã gửi', count: outgoingRequests.length },
  ]

  return (
    <div
      className="friends-drawer-backdrop"
      onMouseDown={(event) => event.target === event.currentTarget && onClose()}
    >
      <section
        ref={drawerRef}
        className="friends-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="friends-panel-title"
        tabIndex={-1}
      >
        <header className="friends-drawer__header">
          <div>
            <span className="eyebrow">Kết nối cùng nhau</span>
            <h2 id="friends-panel-title">Bạn bè LinkCute</h2>
          </div>
          <div className="friends-header-actions">
            <button
              className={`friends-refresh ${loading ? 'is-loading' : ''}`}
              type="button"
              onClick={() => refresh().catch((requestError) => {
                showToast(friendshipErrorMessage(requestError), 'error')
              })}
              disabled={loading}
              aria-label="Tải lại danh sách bạn bè"
              title="Tải lại"
            >
              <RefreshCw size={18} aria-hidden="true" />
            </button>
            <button className="friends-close" type="button" onClick={onClose} aria-label="Đóng bạn bè">
              <X size={21} aria-hidden="true" />
            </button>
          </div>
        </header>

        <div className="friends-drawer__body">
          <section className="friend-code-card" aria-labelledby="friend-code-title">
            <div className="friend-code-card__heading">
              <span className="friend-code-card__icon"><UserPlus size={20} aria-hidden="true" /></span>
              <div>
                <h3 id="friend-code-title">Thêm một người bạn</h3>
                <p>Nhập đúng mã riêng của thành viên bạn muốn kết nối.</p>
              </div>
            </div>

            <form className="friend-search-form" onSubmit={handleSearch}>
              <label>
                <span className="sr-only">Mã kết bạn</span>
                <Search size={18} aria-hidden="true" />
                <input
                  data-friends-initial-focus
                  value={pinCode}
                  onChange={updatePin}
                  placeholder="RML-123456"
                  autoComplete="off"
                  spellCheck="false"
                  aria-describedby={searchError ? 'friend-search-error' : undefined}
                  aria-invalid={Boolean(searchError)}
                />
              </label>
              <button className="button button--primary" type="submit" disabled={searchLoading || !pinCode.trim()}>
                {searchLoading ? <span className="button-loader" /> : <Search size={16} aria-hidden="true" />}
                Tìm bạn
              </button>
            </form>
            {searchError && <div className="friend-search-error" id="friend-search-error" role="alert">{searchError}</div>}

            {searchResult && (
              <SearchResultCard
                result={searchResult}
                incomingRequest={matchingIncoming}
                outgoingRequest={matchingOutgoing}
                friend={matchingFriend}
                busyActions={busyActions}
                onSend={handleSend}
                onAccept={handleAccept}
                onReject={handleReject}
                onCancel={handleCancel}
                onMessage={handleMessage}
                onCall={handleCall}
                onRemove={handleRemove}
                callDisabled={callDisabled}
              />
            )}

            {session?.user?.pinCode && (
              <div className="own-friend-code">
                <span>Mã của bạn</span>
                <strong>{session.user.pinCode}</strong>
                <button type="button" onClick={handleCopyPin} aria-label="Sao chép mã kết bạn">
                  <Copy size={15} aria-hidden="true" /> Sao chép
                </button>
              </div>
            )}
          </section>

          <div className="friends-tabs" role="tablist" aria-label="Danh sách kết nối">
            {tabs.map((tab) => (
              <button
                key={tab.id}
                id={`friends-${tab.id}-tab`}
                type="button"
                role="tab"
                aria-selected={activeTab === tab.id}
                aria-controls="friends-tab-panel"
                tabIndex={activeTab === tab.id ? 0 : -1}
                className={activeTab === tab.id ? 'is-active' : ''}
                onClick={() => setActiveTab(tab.id)}
                onKeyDown={(event) => handleTabKeyDown(event, tab.id)}
              >
                {tab.label}
                <span>{tab.count}</span>
              </button>
            ))}
          </div>

          <div
            className="friends-tab-panel"
            id="friends-tab-panel"
            role="tabpanel"
            aria-labelledby={`friends-${activeTab}-tab`}
            tabIndex={0}
          >
            {loading && (
              <div className="friends-loading" role="status">
                <span className="loader" />
                <p>Đang tải những kết nối của bạn…</p>
              </div>
            )}

            {!loading && error && (
              <div className="friends-load-error" role="alert">
                <strong>Chưa tải được danh sách</strong>
                <p>{error}</p>
                <button className="button button--secondary" type="button" onClick={() => refresh().catch(() => {})}>Thử lại</button>
              </div>
            )}

            {!loading && !error && activeTab === 'friends' && (
              friends.length ? (
                <div className="friends-list">
                  {friends.map((friend) => (
                    <FriendCard
                      key={friend.friendshipId}
                      friend={friend}
                      busy={busyActions.has(`remove:${friend.friendshipId}`)}
                      callDisabled={callDisabled}
                      onMessage={handleMessage}
                      onCall={handleCall}
                      onRemove={handleRemove}
                    />
                  ))}
                </div>
              ) : (
                <EmptyFriendsState
                  icon={UsersRound}
                  title="Chưa có bạn bè"
                  description="Chia sẻ mã của bạn hoặc tìm một thành viên ở phía trên để bắt đầu kết nối."
                />
              )
            )}

            {!loading && !error && activeTab === 'incoming' && (
              incomingRequests.length ? (
                <div className="friends-list">
                  {incomingRequests.map((request) => (
                    <IncomingRequestCard
                      key={request.id}
                      request={request}
                      acceptBusy={busyActions.has(`accept:${request.id}`)}
                      deleteBusy={busyActions.has(`delete-request:${request.id}`)}
                      onAccept={handleAccept}
                      onReject={handleReject}
                    />
                  ))}
                </div>
              ) : (
                <EmptyFriendsState
                  icon={Inbox}
                  title="Không có lời mời mới"
                  description="Khi ai đó gửi lời mời kết bạn, bạn sẽ thấy họ tại đây."
                />
              )
            )}

            {!loading && !error && activeTab === 'outgoing' && (
              outgoingRequests.length ? (
                <div className="friends-list">
                  {outgoingRequests.map((request) => (
                    <OutgoingRequestCard
                      key={request.id}
                      request={request}
                      busy={busyActions.has(`delete-request:${request.id}`)}
                      onCancel={handleCancel}
                    />
                  ))}
                </div>
              ) : (
                <EmptyFriendsState
                  icon={Send}
                  title="Chưa gửi lời mời nào"
                  description="Những lời mời đang chờ phản hồi sẽ được lưu ở đây."
                />
              )
            )}
          </div>
        </div>
      </section>
    </div>
  )
}
