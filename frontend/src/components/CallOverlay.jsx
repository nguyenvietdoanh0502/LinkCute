import {
  AlertCircle,
  Mic,
  MicOff,
  Phone,
  PhoneIncoming,
  PhoneOff,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import ProfileAvatar from './ProfileAvatar.jsx'

const FOCUSABLE_SELECTOR = 'button:not([disabled]), [tabindex]:not([tabindex="-1"])'

const STATUS_COPY = {
  incoming: {
    eyebrow: 'Cuộc gọi đến',
    title: 'Có người bạn đang gọi',
    description: (name) => `${name} muốn nói chuyện với bạn.`,
  },
  outgoing: {
    eyebrow: 'Cuộc gọi đi',
    title: 'Đang gọi…',
    description: (name) => `Đang chờ ${name} trả lời.`,
  },
  connecting: {
    eyebrow: 'Đang kết nối',
    title: 'Sắp kết nối cuộc gọi',
    description: () => 'Đang thiết lập đường truyền âm thanh…',
  },
  active: {
    eyebrow: 'Đã kết nối',
    title: 'Cuộc gọi đang diễn ra',
    description: (name) => `Bạn đang trò chuyện với ${name}.`,
  },
  ended: {
    eyebrow: 'Đã kết thúc',
    title: 'Cuộc gọi đã kết thúc',
    description: (name) => `Cuộc gọi với ${name} đã dừng.`,
  },
  error: {
    eyebrow: 'Không thể kết nối',
    title: 'Cuộc gọi gặp sự cố',
    description: () => 'Vui lòng đóng thông báo và thử gọi lại.',
  },
}

function validTimestamp(value) {
  if (!value) return null
  const timestamp = value instanceof Date ? value.getTime() : new Date(value).getTime()
  return Number.isFinite(timestamp) ? timestamp : null
}

export function formatCallDuration(totalSeconds) {
  const safeSeconds = Math.max(0, Math.floor(Number(totalSeconds) || 0))
  const hours = Math.floor(safeSeconds / 3600)
  const minutes = Math.floor((safeSeconds % 3600) / 60)
  const seconds = safeSeconds % 60
  const minuteSeconds = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  return hours ? `${String(hours).padStart(2, '0')}:${minuteSeconds}` : minuteSeconds
}

function errorMessage(error) {
  if (typeof error === 'string') return error
  return error?.message || 'Không thể kết nối cuộc gọi. Vui lòng thử lại.'
}

function endedDescription(endReason, peerName) {
  const reason = String(endReason || '').toLowerCase()
  if (reason === 'declined-local') return 'Bạn đã từ chối cuộc gọi.'
  if (reason === 'peer-rejected') return `${peerName} đã từ chối cuộc gọi.`
  if (reason === 'remote-ended') return `${peerName} đã kết thúc cuộc gọi.`
  if (reason === 'missed') return `Bạn đã bỏ lỡ cuộc gọi từ ${peerName}.`
  if (reason === 'connection-timeout') return 'Không thể thiết lập đường truyền âm thanh.'
  if (reason.includes('timeout') || reason.includes('no_answer')) {
    return `${peerName} không trả lời cuộc gọi.`
  }
  if (reason.includes('reject') || reason.includes('declin')) {
    return `${peerName} đã từ chối cuộc gọi.`
  }
  if (reason.includes('busy')) return `${peerName} đang bận.`
  if (reason.includes('disconnect') || reason.includes('network')) {
    return 'Cuộc gọi đã dừng do mất kết nối.'
  }
  if (typeof endReason === 'string' && endReason.trim() && reason !== 'ended') {
    return endReason
  }
  return STATUS_COPY.ended.description(peerName)
}

function peerProfile(peer) {
  if (!peer) return {}
  if (typeof peer === 'string') return { id: peer }
  return peer.user || peer
}

export default function CallOverlay({ callState }) {
  const dialogRef = useRef(null)
  const [elapsedSeconds, setElapsedSeconds] = useState(0)
  const status = callState?.status || 'idle'
  const profile = useMemo(() => peerProfile(callState?.peer), [callState?.peer])
  const peerName = profile.fullName || profile.displayName || profile.name || 'một người bạn'
  const copy = STATUS_COPY[status]
  const dismissCall = callState?.dismissCall
    || callState?.resetCall
    || callState?.clearCall
    || callState?.endCall

  useEffect(() => {
    if (status !== 'active') {
      setElapsedSeconds(0)
      return undefined
    }

    const startedAt = validTimestamp(callState?.activeSince || callState?.startedAt) || Date.now()
    const updateElapsed = () => {
      setElapsedSeconds(Math.max(0, Math.floor((Date.now() - startedAt) / 1000)))
    }
    updateElapsed()
    const timer = window.setInterval(updateElapsed, 1000)
    return () => window.clearInterval(timer)
  }, [callState?.activeSince, callState?.callId, callState?.startedAt, status])

  useEffect(() => {
    if (!copy) return undefined

    const previousOverflow = document.body.style.overflow
    const previouslyFocused = document.activeElement
    document.body.style.overflow = 'hidden'
    const focusFrame = window.requestAnimationFrame(() => {
      const initialControl = dialogRef.current?.querySelector('[data-call-initial-focus]')
      if (initialControl) initialControl.focus()
      else dialogRef.current?.focus()
    })

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        if (status === 'incoming' && callState?.rejectCall) {
          event.preventDefault()
          callState.rejectCall()
        } else if ((status === 'ended' || status === 'error') && dismissCall) {
          event.preventDefault()
          dismissCall()
        }
        return
      }
      if (event.key !== 'Tab') return

      const focusable = [...(dialogRef.current?.querySelectorAll(FOCUSABLE_SELECTOR) || [])]
      if (!focusable.length) {
        event.preventDefault()
        dialogRef.current?.focus()
        return
      }
      const first = focusable[0]
      const last = focusable.at(-1)
      if (!dialogRef.current?.contains(document.activeElement)) {
        event.preventDefault()
        first.focus()
      } else if (event.shiftKey && document.activeElement === first) {
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
  }, [callState?.rejectCall, copy, dismissCall, status])

  if (!copy) return null

  const activeCall = status === 'active'
  const error = status === 'error' ? errorMessage(callState?.error) : ''
  const description = status === 'ended'
    ? endedDescription(callState?.endReason, peerName)
    : copy.description(peerName)

  return (
    <div className={`call-overlay call-overlay--${status}`}>
      <section
        ref={dialogRef}
        className="call-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="call-overlay-title"
        aria-describedby="call-overlay-description"
        tabIndex={-1}
      >
        <audio
          ref={callState?.remoteAudioRef}
          className="call-remote-audio"
          autoPlay
          playsInline
          aria-hidden="true"
        />

        <div className="call-dialog__status" aria-live="polite">
          <span className="call-dialog__eyebrow">
            {status === 'incoming' ? <PhoneIncoming size={14} aria-hidden="true" /> : <Phone size={14} aria-hidden="true" />}
            {copy.eyebrow}
          </span>
        </div>

        <div className="call-avatar-wrap" aria-hidden="true">
          <span className="call-avatar-pulse" />
          <ProfileAvatar user={profile} className="call-avatar" alt="" />
        </div>

        <div className="call-dialog__copy">
          <h2 id="call-overlay-title">{copy.title}</h2>
          <p id="call-overlay-description">{description}</p>
          {activeCall && (
            <time
              className="call-duration"
              dateTime={`PT${elapsedSeconds}S`}
              aria-label={`Thời lượng cuộc gọi ${formatCallDuration(elapsedSeconds)}`}
            >
              {formatCallDuration(elapsedSeconds)}
            </time>
          )}
          {error && (
            <p className="call-error" role="alert">
              <AlertCircle size={17} aria-hidden="true" />
              {error}
            </p>
          )}
        </div>

        <div className="call-actions">
          {status === 'incoming' && (
            <>
              <button
                className="call-action call-action--accept"
                type="button"
                onClick={callState?.acceptCall}
                disabled={!callState?.acceptCall}
                aria-label={`Nhận cuộc gọi từ ${peerName}`}
                data-call-initial-focus
              >
                <Phone size={22} aria-hidden="true" />
                <span>Nhận cuộc gọi</span>
              </button>
              <button
                className="call-action call-action--danger"
                type="button"
                onClick={callState?.rejectCall}
                disabled={!callState?.rejectCall}
                aria-label={`Từ chối cuộc gọi từ ${peerName}`}
              >
                <PhoneOff size={22} aria-hidden="true" />
                <span>Từ chối</span>
              </button>
            </>
          )}

          {activeCall && (
            <button
              className={`call-action call-action--secondary ${callState?.isMuted ? 'is-muted' : ''}`}
              type="button"
              onClick={callState?.toggleMute}
              disabled={!callState?.toggleMute}
              aria-label={callState?.isMuted ? 'Bật micrô' : 'Tắt micrô'}
              aria-pressed={Boolean(callState?.isMuted)}
              data-call-initial-focus
            >
              {callState?.isMuted
                ? <MicOff size={22} aria-hidden="true" />
                : <Mic size={22} aria-hidden="true" />}
              <span>{callState?.isMuted ? 'Bật micrô' : 'Tắt micrô'}</span>
            </button>
          )}

          {(status === 'outgoing' || status === 'connecting' || activeCall) && (
            <button
              className="call-action call-action--danger"
              type="button"
              onClick={callState?.endCall}
              disabled={!callState?.endCall}
              aria-label={`Kết thúc cuộc gọi với ${peerName}`}
              data-call-initial-focus={!activeCall || undefined}
            >
              <PhoneOff size={22} aria-hidden="true" />
              <span>Kết thúc</span>
            </button>
          )}

          {(status === 'ended' || status === 'error') && (
            <button
              className="call-action call-action--dismiss"
              type="button"
              onClick={dismissCall}
              disabled={!dismissCall}
              aria-label="Đóng thông báo cuộc gọi"
              data-call-initial-focus
            >
              <X size={21} aria-hidden="true" />
              <span>Đóng</span>
            </button>
          )}
        </div>
      </section>
    </div>
  )
}
