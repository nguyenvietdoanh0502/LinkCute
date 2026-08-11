import { MapPin, Search, Send, ShieldCheck, UserPlus, X } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { locationAccuracyLabel, normalizeLocation } from '../location/model.js'
import ProfileAvatar from './ProfileAvatar.jsx'

const FOCUSABLE_SELECTOR = [
  'button:not([disabled])',
  'input:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

export default function ShareLocationDialog({
  open,
  onClose,
  friends = [],
  friendsLoading = false,
  friendsError = '',
  location,
  connectionStatus,
  onShare,
  onOpenFriends,
  onRefreshFriends,
}) {
  const dialogRef = useRef(null)
  const searchRef = useRef(null)
  const [query, setQuery] = useState('')
  const [busyFriendId, setBusyFriendId] = useState(null)
  const [actionError, setActionError] = useState('')
  const normalizedLocation = normalizeLocation(location)
  const normalizedQuery = query.trim().toLocaleLowerCase('vi-VN')
  const filteredFriends = useMemo(() => friends.filter(({ user }) => (
    !normalizedQuery
    || `${user?.fullName || ''} ${user?.pinCode || ''}`
      .toLocaleLowerCase('vi-VN')
      .includes(normalizedQuery)
  )), [friends, normalizedQuery])
  const connected = connectionStatus === 'connected'
  const showFriendSearch = friends.length > 0 && !friendsLoading && !friendsError

  useEffect(() => {
    if (!open) return
    setQuery('')
    setBusyFriendId(null)
    setActionError('')
  }, [open])

  useEffect(() => {
    if (!open) return undefined
    const previousOverflow = document.body.style.overflow
    const previouslyFocused = document.activeElement
    document.body.style.overflow = 'hidden'
    const focusFrame = window.requestAnimationFrame(() => (
      showFriendSearch ? searchRef.current?.focus() : dialogRef.current?.focus()
    ))

    const handleKeyDown = (event) => {
      if (event.key === 'Escape' && !busyFriendId) {
        event.preventDefault()
        onClose()
        return
      }
      if (event.key !== 'Tab') return
      const focusable = [...(dialogRef.current?.querySelectorAll(FOCUSABLE_SELECTOR) || [])]
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
  }, [busyFriendId, onClose, open, showFriendSearch])

  if (!open) return null

  const shareWith = async (friend) => {
    const friendId = friend.user?.id
    if (!friendId || !normalizedLocation || !connected || busyFriendId) return
    setBusyFriendId(friendId)
    setActionError('')
    try {
      await onShare(friend, normalizedLocation)
      onClose()
    } catch (shareError) {
      setActionError(shareError.message || 'Chưa thể chia sẻ vị trí. Vui lòng thử lại.')
    } finally {
      setBusyFriendId(null)
    }
  }

  return (
    <div className="location-share-backdrop" onMouseDown={(event) => event.target === event.currentTarget && !busyFriendId && onClose()}>
      <section
        ref={dialogRef}
        className="location-share-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="location-share-title"
        tabIndex={-1}
      >
        <header>
          <span><MapPin size={22} aria-hidden="true" /></span>
          <div>
            <span className="eyebrow">Vị trí hiện tại</span>
            <h2 id="location-share-title">Chia sẻ với bạn bè</h2>
          </div>
          <button type="button" onClick={onClose} disabled={Boolean(busyFriendId)} aria-label="Đóng chia sẻ vị trí">
            <X size={20} />
          </button>
        </header>

        <div className="location-share-summary">
          <div>
            <strong>{normalizedLocation
              ? `${normalizedLocation.latitude.toFixed(6)}, ${normalizedLocation.longitude.toFixed(6)}`
              : 'Tọa độ không hợp lệ'}</strong>
            <span>{locationAccuracyLabel(normalizedLocation)}</span>
          </div>
          <p><ShieldCheck size={15} /> Đây là vị trí tại thời điểm gửi, không phải theo dõi trực tiếp.</p>
        </div>

        {showFriendSearch && (
          <label className="location-share-search">
            <Search size={17} aria-hidden="true" />
            <span className="sr-only">Tìm bạn để chia sẻ vị trí</span>
            <input
              ref={searchRef}
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Tìm theo tên hoặc mã kết bạn…"
            />
          </label>
        )}

        {!connected && (
          <div className="location-share-notice" role="status">
            Đang chờ kết nối tin nhắn realtime. Vui lòng thử lại sau giây lát.
          </div>
        )}
        {actionError && <div className="location-share-error" role="alert">{actionError}</div>}

        <div className="location-share-friends">
          {friendsLoading ? (
            <div className="location-share-loading" role="status">
              <span className="loader" />
              <p>Đang tải danh sách bạn bè…</p>
            </div>
          ) : friendsError ? (
            <div className="location-share-empty location-share-empty--compact" role="alert">
              <strong>Chưa tải được danh sách bạn bè</strong>
              <p>{friendsError}</p>
              <button className="button button--secondary" type="button" onClick={onRefreshFriends}>
                Thử lại
              </button>
            </div>
          ) : !friends.length ? (
            <div className="location-share-empty">
              <UserPlus size={25} aria-hidden="true" />
              <strong>Chưa có bạn bè để chia sẻ</strong>
              <p>Hãy kết bạn trước, sau đó quay lại gửi vị trí của bạn.</p>
              <button className="button button--secondary" type="button" onClick={onOpenFriends}>
                <UserPlus size={16} /> Kết bạn ngay
              </button>
            </div>
          ) : filteredFriends.length ? filteredFriends.map((friend) => {
            const friendId = friend.user?.id
            const busy = busyFriendId === friendId
            return (
              <article key={friend.friendshipId || friendId}>
                <ProfileAvatar user={friend.user} className="location-share-avatar" alt="" />
                <span>
                  <strong>{friend.user?.fullName || 'Thành viên LinkCute'}</strong>
                  <small>{friend.user?.pinCode || 'Bạn bè'}</small>
                </span>
                <button
                  type="button"
                  onClick={() => shareWith(friend)}
                  disabled={!connected || !normalizedLocation || Boolean(busyFriendId)}
                  aria-label={`Gửi vị trí cho ${friend.user?.fullName || 'thành viên này'}`}
                >
                  {busy ? <span className="friend-action-loader" /> : <Send size={16} />}
                  Gửi
                </button>
              </article>
            )
          }) : (
            <div className="location-share-empty location-share-empty--compact">
              <Search size={23} />
              <strong>Không tìm thấy người bạn này</strong>
              <p>Thử nhập tên hoặc mã kết bạn khác.</p>
            </div>
          )}
        </div>
      </section>
    </div>
  )
}
