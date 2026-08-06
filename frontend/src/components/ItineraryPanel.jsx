import {
  AlertTriangle,
  ArrowDown,
  ArrowUp,
  Bell,
  CalendarDays,
  Clock3,
  List,
  LogOut,
  MapPin,
  Plus,
  Trash2,
  WalletCards,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import {
  calculatePlanBudget,
  defaultPlanName,
  findOverlappingItemIds,
  hasValidTimeRange,
  todayLocalDate,
} from '../itinerary/model.js'
import { categoryLabel } from './PlaceCard.jsx'
import PlanPeopleView, {
  IncomingPlanInvitationsView,
  PlanParticipantsSummary,
} from './PlanPeopleView.jsx'

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

const currencyFormatter = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0,
})

function formatBudgetValue(value) {
  return currencyFormatter.format(value)
}

function formatPlanDate(date) {
  if (!date) return ''
  return new Intl.DateTimeFormat('vi-VN', {
    weekday: 'short',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(`${date}T00:00:00`))
}

function timeInMinutes(value) {
  if (!value) return null
  const [hour, minute] = value.split(':').map(Number)
  return Number.isFinite(hour) && Number.isFinite(minute) ? hour * 60 + minute : null
}

function formatTimelineHour(minutes) {
  const hour = Math.floor(minutes / 60)
  const minute = minutes % 60
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
}

function TimeEditor({ item, hasOverlap, onCommit }) {
  const [startTime, setStartTime] = useState(item.startTime || '')
  const [endTime, setEndTime] = useState(item.endTime || '')
  const [error, setError] = useState('')
  const errorId = `itinerary-time-error-${item.id}`

  useEffect(() => {
    setStartTime(item.startTime || '')
    setEndTime(item.endTime || '')
    setError('')
  }, [item.id, item.startTime, item.endTime])

  const commit = () => {
    if (!hasValidTimeRange(startTime, endTime)) {
      setError('Giờ kết thúc phải sau giờ bắt đầu.')
      return
    }

    setError('')
    if (startTime !== item.startTime || endTime !== item.endTime) {
      onCommit(startTime, endTime)
    }
  }

  return (
    <div className="itinerary-time">
      <div className="itinerary-time__fields">
        <label>
          <span>Bắt đầu</span>
          <input
            type="time"
            value={startTime}
            onChange={(event) => {
              const nextStart = event.target.value
              setStartTime(nextStart)
              if (hasValidTimeRange(nextStart, endTime)) setError('')
            }}
            onBlur={commit}
            aria-label={`Giờ bắt đầu tại ${item.place.name}`}
            aria-invalid={Boolean(error)}
            aria-describedby={error ? errorId : undefined}
          />
        </label>
        <span className="itinerary-time__separator" aria-hidden="true">–</span>
        <label>
          <span>Kết thúc</span>
          <input
            type="time"
            value={endTime}
            onChange={(event) => {
              const nextEnd = event.target.value
              setEndTime(nextEnd)
              if (hasValidTimeRange(startTime, nextEnd)) setError('')
            }}
            onBlur={commit}
            aria-label={`Giờ kết thúc tại ${item.place.name}`}
            aria-invalid={Boolean(error)}
            aria-describedby={error ? errorId : undefined}
          />
        </label>
      </div>

      {error && <p className="itinerary-time__error" id={errorId} role="alert">{error}</p>}
      {!error && hasOverlap && (
        <p className="itinerary-time__warning">
          <AlertTriangle size={14} aria-hidden="true" />
          Trùng giờ với một địa điểm khác
        </p>
      )}
    </div>
  )
}

function PlanItem({
  item,
  index,
  itemCount,
  hasOverlap,
  onRemove,
  onMove,
  onUpdateTime,
  readOnly = false,
}) {
  return (
    <li className={`itinerary-item${readOnly ? ' itinerary-item--readonly' : ''}`}>
      <div className="itinerary-item__index" aria-hidden="true">{index + 1}</div>
      <div className="itinerary-item__content">
        <span className="itinerary-item__category">{categoryLabel(item.place.category)}</span>
        <h4>{item.place.name}</h4>
        {(item.place.address || item.place.district) && (
          <p className="itinerary-item__address">
            <MapPin size={14} aria-hidden="true" />
            <span>{item.place.address || item.place.district}</span>
          </p>
        )}

        {readOnly ? (
          <div className="itinerary-time itinerary-time--readonly">
            <Clock3 size={15} aria-hidden="true" />
            <span>
              {item.startTime || item.endTime
                ? `${item.startTime || 'Chưa rõ'} – ${item.endTime || 'Chưa rõ'}`
                : 'Chưa xếp giờ'}
            </span>
            {hasOverlap && <small>Trùng giờ với địa điểm khác</small>}
          </div>
        ) : (
          <TimeEditor
            item={item}
            hasOverlap={hasOverlap}
            onCommit={onUpdateTime}
          />
        )}
      </div>

      {!readOnly && <div className="itinerary-item__actions" aria-label={`Sắp xếp ${item.place.name}`}>
        <button
          type="button"
          className="itinerary-icon-button"
          onClick={() => onMove(-1)}
          disabled={index === 0}
          aria-label={`Đưa ${item.place.name} lên trên`}
          title="Đưa lên"
        >
          <ArrowUp size={17} aria-hidden="true" />
        </button>
        <button
          type="button"
          className="itinerary-icon-button"
          onClick={() => onMove(1)}
          disabled={index === itemCount - 1}
          aria-label={`Đưa ${item.place.name} xuống dưới`}
          title="Đưa xuống"
        >
          <ArrowDown size={17} aria-hidden="true" />
        </button>
        <button
          type="button"
          className="itinerary-icon-button itinerary-icon-button--danger"
          onClick={onRemove}
          aria-label={`Xóa ${item.place.name} khỏi kế hoạch`}
          title="Xóa khỏi kế hoạch"
        >
          <Trash2 size={17} aria-hidden="true" />
        </button>
      </div>}
    </li>
  )
}

function BudgetSummary({ plan }) {
  const budget = calculatePlanBudget(plan)
  const canEstimate = budget.pricedCount >= 2

  return (
    <section className="itinerary-budget" aria-labelledby="itinerary-budget-title">
      <div className="itinerary-budget__icon" aria-hidden="true">
        <WalletCards size={20} />
      </div>
      <div>
        <p id="itinerary-budget-title">Ước tính ngân sách mỗi người</p>
        {canEstimate ? (
          <strong>
            {budget.min === budget.max
              ? `${formatBudgetValue(budget.min)} / người`
              : `${formatBudgetValue(budget.min)} – ${formatBudgetValue(budget.max)} / người`}
          </strong>
        ) : (
          <strong>Chưa đủ dữ liệu giá</strong>
        )}
        {canEstimate ? (
          <small>Tổng của {budget.pricedCount} địa điểm có khoảng giá.</small>
        ) : (
          <small>Cần ít nhất 2 địa điểm có khoảng giá để tính tổng.</small>
        )}
        {budget.missingCount > 0 && (
          <small>
            Chưa gồm {budget.missingCount} địa điểm thiếu dữ liệu giá.
          </small>
        )}
        <small>Chỉ cộng khoảng giá cụ thể; mức giá tương đối chưa được quy đổi.</small>
      </div>
    </section>
  )
}

function TimelineView({ plan, overlappingItemIds }) {
  const {
    scheduled,
    unscheduled,
    timelineStart,
    timelineEnd,
  } = useMemo(() => {
    const scheduledItems = []
    const unscheduledItems = []

    plan.items.forEach((item, index) => {
      const entry = { item, index }
      if (
        item.startTime
        && item.endTime
        && hasValidTimeRange(item.startTime, item.endTime)
      ) {
        scheduledItems.push({
          ...entry,
          start: timeInMinutes(item.startTime),
          end: timeInMinutes(item.endTime),
        })
      } else {
        unscheduledItems.push(entry)
      }
    })

    scheduledItems.sort((left, right) => (
      left.item.startTime.localeCompare(right.item.startTime)
      || left.index - right.index
    ))

    const laidOutItems = []
    let currentGroup = []
    let currentGroupEnd = -1

    const finishGroup = () => {
      if (currentGroup.length === 0) return
      const laneEnds = []
      const entriesWithLanes = currentGroup.map((entry) => {
        let lane = laneEnds.findIndex((end) => end <= entry.start)
        if (lane === -1) lane = laneEnds.length
        laneEnds[lane] = entry.end
        return { ...entry, lane }
      })
      const laneCount = laneEnds.length
      laidOutItems.push(...entriesWithLanes.map((entry) => ({ ...entry, laneCount })))
    }

    scheduledItems.forEach((entry) => {
      if (currentGroup.length > 0 && entry.start >= currentGroupEnd) {
        finishGroup()
        currentGroup = []
        currentGroupEnd = -1
      }
      currentGroup.push(entry)
      currentGroupEnd = Math.max(currentGroupEnd, entry.end)
    })
    finishGroup()

    const firstMinute = scheduledItems[0]?.start
    const lastMinute = scheduledItems.reduce(
      (latest, entry) => Math.max(latest, entry.end),
      firstMinute ?? 0,
    )

    return {
      scheduled: laidOutItems,
      unscheduled: unscheduledItems,
      timelineStart: firstMinute == null ? 0 : Math.floor(firstMinute / 60) * 60,
      timelineEnd: firstMinute == null ? 0 : Math.ceil(lastMinute / 60) * 60,
    }
  }, [plan.items])

  const pixelsPerMinute = 1.6
  const timelineHeight = Math.max((timelineEnd - timelineStart) * pixelsPerMinute, 150)
  const hourMarks = []
  for (let minute = timelineStart; minute <= timelineEnd; minute += 60) {
    hourMarks.push(minute)
  }

  return (
    <div className="itinerary-timeline">
      {scheduled.length > 0 ? (
        <div className="itinerary-timeline__canvas" style={{ height: `${timelineHeight}px` }}>
          <div className="itinerary-timeline__hours" aria-hidden="true">
            {hourMarks.map((minute) => (
              <span
                key={minute}
                style={{ top: `${(minute - timelineStart) * pixelsPerMinute}px` }}
              >
                <time>{formatTimelineHour(minute)}</time>
                <i />
              </span>
            ))}
          </div>
          <ol className="itinerary-timeline__scheduled">
            {scheduled.map(({ item, start, end, lane, laneCount }) => {
              const duration = end - start
              const laneFraction = lane / laneCount
              const leftPercent = laneFraction * 100
              const leftPixels = 84 * (1 - laneFraction)
              const widthPercent = 100 / laneCount
              const widthPixels = 84 / laneCount + 8
              return (
                <li
                  key={item.id}
                  className={[
                    'itinerary-timeline__entry',
                    duration < 45 ? 'itinerary-timeline__entry--compact' : '',
                    duration < 25 ? 'itinerary-timeline__entry--tiny' : '',
                    overlappingItemIds.has(item.id) ? 'itinerary-timeline__entry--warning' : '',
                  ].filter(Boolean).join(' ')}
                  title={`${item.startTime}–${item.endTime} · ${item.place.name}`}
                  aria-label={[
                    `${item.startTime} đến ${item.endTime}`,
                    item.place.name,
                    overlappingItemIds.has(item.id) ? 'trùng giờ với địa điểm khác' : '',
                  ].filter(Boolean).join(', ')}
                  style={{
                    top: `${(start - timelineStart) * pixelsPerMinute}px`,
                    left: `calc(${leftPercent}% + ${leftPixels}px)`,
                    width: `calc(${widthPercent}% - ${widthPixels}px)`,
                    height: `${duration * pixelsPerMinute}px`,
                    zIndex: lane + 1,
                  }}
                >
                  <time>{item.startTime}–{item.endTime}</time>
                  <span>{categoryLabel(item.place.category)}</span>
                  <h4>{item.place.name}</h4>
                  {overlappingItemIds.has(item.id) && (
                    <p>
                      <AlertTriangle size={14} aria-hidden="true" />
                      Trùng giờ
                    </p>
                  )}
                </li>
              )
            })}
          </ol>
        </div>
      ) : (
        <div className="itinerary-empty itinerary-empty--compact">
          <Clock3 size={22} aria-hidden="true" />
          <p>Chưa có địa điểm nào được xếp giờ.</p>
        </div>
      )}

      {unscheduled.length > 0 && (
        <section className="itinerary-unscheduled" aria-labelledby="itinerary-unscheduled-title">
          <h4 id="itinerary-unscheduled-title">Chưa xếp giờ ({unscheduled.length})</h4>
          <ul>
            {unscheduled.map(({ item }) => (
              <li key={item.id}>
                <span>{categoryLabel(item.place.category)}</span>
                <strong>{item.place.name}</strong>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}

function CreatePlanForm({ pendingPlace, hasPlans, onCreate, onCancel }) {
  const [date, setDate] = useState(todayLocalDate)
  const [name, setName] = useState(() => defaultPlanName(todayLocalDate()))
  const pastDate = Boolean(date && date < todayLocalDate())
  const dateWarningId = 'itinerary-create-date-warning'

  const handleDateChange = (event) => {
    const nextDate = event.target.value
    const previousDefault = defaultPlanName(date)
    setDate(nextDate)
    if (!name.trim() || name === previousDefault) {
      setName(defaultPlanName(nextDate))
    }
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    const planDate = date || todayLocalDate()
    onCreate({
      name: name.trim() || defaultPlanName(planDate),
      date: planDate,
      initialPlace: pendingPlace || null,
    })
  }

  return (
    <form className="itinerary-create" onSubmit={handleSubmit}>
      <div className="itinerary-create__intro">
        <span className="itinerary-create__icon" aria-hidden="true">
          <CalendarDays size={24} />
        </span>
        <div>
          <h3>Tạo kế hoạch mới</h3>
          <p>Chọn ngày và đặt tên để bắt đầu sắp xếp hành trình.</p>
        </div>
      </div>

      {pendingPlace && (
        <div className="itinerary-pending-place">
          <span>Sẽ tự động thêm</span>
          <strong>{pendingPlace.name}</strong>
        </div>
      )}

      <label className="itinerary-field">
        <span>Tên kế hoạch</span>
        <input
          type="text"
          value={name}
          onChange={(event) => setName(event.target.value)}
          maxLength={80}
          autoComplete="off"
          data-itinerary-initial-focus
        />
      </label>

      <label className="itinerary-field">
        <span>Ngày đi</span>
        <input
          type="date"
          value={date}
          onChange={handleDateChange}
          required
          aria-describedby={pastDate ? dateWarningId : undefined}
        />
        {pastDate && (
          <small className="itinerary-date-warning" id={dateWarningId} aria-live="polite">
            <AlertTriangle size={13} aria-hidden="true" />
            Ngày này đã qua, nhưng bạn vẫn có thể tạo kế hoạch.
          </small>
        )}
      </label>

      <div className="itinerary-create__actions">
        <button type="button" className="button button--secondary" onClick={onCancel}>
          {hasPlans ? 'Hủy' : 'Để sau'}
        </button>
        <button type="submit" className="button button--primary">
          Tạo kế hoạch
        </button>
      </div>
    </form>
  )
}

function PlanMetaEditor({ plan, onUpdate }) {
  const [name, setName] = useState(plan.name)
  const [date, setDate] = useState(plan.date)

  useEffect(() => {
    setName(plan.name)
    setDate(plan.date)
  }, [plan.id, plan.name, plan.date])

  const commit = () => {
    const nextDate = date || plan.date || todayLocalDate()
    const nextName = name.trim() || defaultPlanName(nextDate)
    setName(nextName)
    setDate(nextDate)

    if (nextName !== plan.name || nextDate !== plan.date) {
      onUpdate({ name: nextName, date: nextDate })
    }
  }

  return (
    <div className="itinerary-meta">
      <label className="itinerary-field">
        <span>Tên kế hoạch</span>
        <input
          type="text"
          value={name}
          onChange={(event) => setName(event.target.value)}
          onBlur={commit}
          maxLength={80}
          autoComplete="off"
        />
      </label>
      <label className="itinerary-field">
        <span>Ngày đi</span>
        <input
          type="date"
          value={date}
          onChange={(event) => setDate(event.target.value)}
          onBlur={commit}
        />
      </label>
    </div>
  )
}

function ReadonlyPlanMeta({ plan }) {
  return (
    <div className="itinerary-meta itinerary-meta--readonly">
      <div className="itinerary-readonly-field">
        <span>Tên kế hoạch</span>
        <strong>{plan.name}</strong>
      </div>
      <div className="itinerary-readonly-field">
        <span>Ngày đi</span>
        <strong>{formatPlanDate(plan.date)}</strong>
      </div>
      <p className="itinerary-readonly-note">
        Bạn đang xem kế hoạch do {plan.owner?.fullName || 'một người bạn'} chia sẻ.
      </p>
    </div>
  )
}

export default function ItineraryPanel({
  open,
  onClose,
  plans = [],
  activePlan = null,
  pendingPlace = null,
  storageError = '',
  session = null,
  friendshipState = null,
  planSharingState = null,
  initialSubview = 'plan',
  showToast,
  onRequireLogin,
  onOpenFriends,
  onCreatePlan,
  onSelectPlan,
  onSelectAcceptedPlan,
  onUpdatePlan,
  onDeletePlan,
  onRemoveItem,
  onMoveItem,
  onUpdateItemTime,
}) {
  const drawerRef = useRef(null)
  const onCloseRef = useRef(onClose)
  const subviewRef = useRef('plan')
  const [view, setView] = useState('list')
  const [creating, setCreating] = useState(!activePlan)
  const [subview, setSubview] = useState('plan')

  const sharing = planSharingState || {}
  const friendships = friendshipState || {}
  const isReadOnly = activePlan?.accessRole === 'MEMBER' || activePlan?.readOnly

  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  useEffect(() => {
    subviewRef.current = subview
  }, [subview])

  useEffect(() => {
    if (!open) return
    setCreating(!activePlan || Boolean(pendingPlace))
    setSubview(initialSubview === 'people' && activePlan ? 'people' : 'plan')
    setView('list')
    // This initialization intentionally happens only when the drawer opens.
    // Edits to the active plan must not reset the current tab or steal focus.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  useEffect(() => {
    if (!open) return undefined

    const previouslyFocused = document.activeElement
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    const focusInitialControl = () => {
      const drawer = drawerRef.current
      const initialControl = drawer?.querySelector('[data-itinerary-initial-focus]')
        || drawer?.querySelector(FOCUSABLE_SELECTOR)
      initialControl?.focus()
    }
    const frame = window.requestAnimationFrame(focusInitialControl)

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        if (subviewRef.current !== 'plan') {
          setSubview('plan')
          return
        }
        onCloseRef.current()
        return
      }
      if (event.key !== 'Tab') return

      const focusable = Array.from(
        drawerRef.current?.querySelectorAll(FOCUSABLE_SELECTOR) || [],
      ).filter((element) => !element.hasAttribute('disabled') && element.offsetParent !== null)
      if (focusable.length === 0) {
        event.preventDefault()
        drawerRef.current?.focus()
        return
      }

      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (!drawerRef.current?.contains(document.activeElement)) {
        event.preventDefault()
        const fallbackFocus = event.shiftKey ? last : first
        fallbackFocus.focus()
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
      window.cancelAnimationFrame(frame)
      document.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = previousOverflow
      if (previouslyFocused instanceof HTMLElement && previouslyFocused.isConnected) {
        previouslyFocused.focus()
      } else {
        document.querySelector('.itinerary-launcher')?.focus()
      }
    }
  }, [open])

  useEffect(() => {
    if (!open) return undefined
    const frame = window.requestAnimationFrame(() => {
      const drawer = drawerRef.current
      if (!drawer?.contains(document.activeElement)) {
        const nextFocus = drawer?.querySelector('[data-itinerary-initial-focus]')
          || drawer?.querySelector(FOCUSABLE_SELECTOR)
        nextFocus?.focus()
      }
    })
    return () => window.cancelAnimationFrame(frame)
  }, [activePlan?.id, creating, open, subview, view])

  const overlappingItemIds = useMemo(
    () => findOverlappingItemIds(activePlan),
    [activePlan],
  )

  if (!open) return null

  const handleCreate = (values) => {
    onCreatePlan(values)
    setCreating(false)
    setView('list')
  }

  const handleDelete = async () => {
    if (!activePlan) return
    const confirmed = window.confirm(`Xóa “${activePlan.name}”? Kế hoạch này sẽ không thể khôi phục.`)
    if (!confirmed) return
    try {
      await onDeletePlan(activePlan.id)
    } catch (error) {
      showToast?.(error?.message || 'Chưa thể xóa kế hoạch.', 'error')
    }
  }

  const handleOpenPeople = () => {
    if (!session) {
      onRequireLogin?.()
      return
    }
    setCreating(false)
    setSubview('people')
  }

  const handleLeave = async () => {
    if (!activePlan) return
    const confirmed = window.confirm(`Rời khỏi “${activePlan.name}”?`)
    if (!confirmed) return
    try {
      const result = await sharing.leavePlan?.(activePlan)
      if (result === null || result === undefined) return
      showToast?.(`Bạn đã rời “${activePlan.name}”.`)
      setSubview('plan')
    } catch (error) {
      showToast?.(error?.message || 'Chưa thể rời kế hoạch.', 'error')
    }
  }

  const handleViewKeyDown = (event, currentView) => {
    const views = ['list', 'timeline']
    const currentIndex = views.indexOf(currentView)
    let nextView = null

    if (event.key === 'ArrowRight') nextView = views[(currentIndex + 1) % views.length]
    if (event.key === 'ArrowLeft') nextView = views[(currentIndex - 1 + views.length) % views.length]
    if (event.key === 'Home') nextView = views[0]
    if (event.key === 'End') nextView = views.at(-1)
    if (!nextView) return

    event.preventDefault()
    setView(nextView)
    window.requestAnimationFrame(() => {
      document.getElementById(`itinerary-${nextView}-tab`)?.focus()
    })
  }

  return (
    <div
      className="itinerary-drawer-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <section
        ref={drawerRef}
        className="itinerary-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="itinerary-panel-title"
        tabIndex={-1}
      >
        <header className="itinerary-drawer__header">
          <div>
            <span className="eyebrow">Hành trình của bạn</span>
            <h2 id="itinerary-panel-title">Kế hoạch đi chơi</h2>
          </div>
          <div className="itinerary-header-actions">
            {session && (
              <button
                type="button"
                className="itinerary-invitations-button"
                onClick={() => {
                  setCreating(false)
                  setSubview('invitations')
                }}
                aria-label={`Mở lời mời kế hoạch${sharing.incomingCount ? `, ${sharing.incomingCount} lời mời mới` : ''}`}
                title="Lời mời kế hoạch"
              >
                <Bell size={19} aria-hidden="true" />
                {sharing.incomingCount > 0 && <span>{sharing.incomingCount}</span>}
              </button>
            )}
            <button
              type="button"
              className="itinerary-close"
              onClick={onClose}
              aria-label="Đóng kế hoạch"
              title="Đóng"
            >
              <X size={21} aria-hidden="true" />
            </button>
          </div>
        </header>

        {storageError && (
          <div className="itinerary-storage-error" role="alert">
            <AlertTriangle size={17} aria-hidden="true" />
            <span>{storageError}</span>
          </div>
        )}

        {sharing.syncError && (
          <div className="itinerary-storage-error" role="alert">
            <AlertTriangle size={17} aria-hidden="true" />
            <span>{sharing.syncError}</span>
          </div>
        )}

        {subview === 'invitations' ? (
          <IncomingPlanInvitationsView
            invitations={sharing.incomingInvitations || []}
            loading={sharing.loading}
            error={sharing.error}
            busyActions={sharing.busyActions}
            onRefresh={() => sharing.refresh?.().catch(() => {})}
            onAccept={sharing.acceptInvitation}
            onDecline={sharing.declineInvitation}
            onAccepted={(plan) => {
              if (plan?.id) onSelectAcceptedPlan?.(plan.id)
              setSubview('plan')
              setCreating(false)
            }}
            onBack={() => setSubview('plan')}
            showToast={showToast}
          />
        ) : subview === 'people' && activePlan ? (
          <PlanPeopleView
            plan={activePlan}
            session={session}
            friends={friendships.friends || []}
            friendsLoading={friendships.loading}
            friendsError={friendships.error}
            busyActions={sharing.busyActions}
            invitationStateFor={sharing.invitationStateFor}
            onInvite={(plan, user) => sharing.inviteFriend(plan, user.id)}
            onCancelInvitation={sharing.cancelInvitation}
            onRemoveMember={(plan, user) => sharing.removeMember(plan, user.id)}
            onRefreshFriends={() => friendships.refresh?.().catch(() => {})}
            onOpenFriends={onOpenFriends}
            onBack={() => setSubview('plan')}
            showToast={showToast}
          />
        ) : creating ? (
          <CreatePlanForm
            key={`${plans.length}-${pendingPlace?.id || 'empty'}`}
            pendingPlace={pendingPlace}
            hasPlans={plans.length > 0}
            onCreate={handleCreate}
            onCancel={() => {
              if (activePlan) setCreating(false)
              else onClose()
            }}
          />
        ) : activePlan ? (
          <>
            <div className="itinerary-plan-toolbar">
              <label className="itinerary-plan-picker">
                <span>Kế hoạch đang xem</span>
                <select
                  value={activePlan.id}
                  onChange={(event) => onSelectPlan(event.target.value)}
                  data-itinerary-initial-focus
                >
                  {plans.map((plan) => (
                    <option key={plan.id} value={plan.id}>
                      {plan.name} · {formatPlanDate(plan.date)}
                      {plan.accessRole === 'MEMBER' ? ` · của ${plan.owner?.fullName || 'bạn bè'}` : ''}
                    </option>
                  ))}
                </select>
              </label>
              <button
                type="button"
                className="itinerary-new-plan"
                onClick={() => {
                  setSubview('plan')
                  setCreating(true)
                }}
              >
                <Plus size={17} aria-hidden="true" />
                Kế hoạch mới
              </button>
            </div>

            {isReadOnly ? (
              <ReadonlyPlanMeta plan={activePlan} />
            ) : (
              <PlanMetaEditor
                plan={activePlan}
                onUpdate={(meta) => onUpdatePlan(activePlan.id, meta)}
              />
            )}

            <div className="itinerary-plan-summary">
              <span>
                <CalendarDays size={16} aria-hidden="true" />
                {formatPlanDate(activePlan.date)}
              </span>
              <span>{activePlan.items.length} địa điểm</span>
            </div>

            <div className="itinerary-sharing-summary">
              <PlanParticipantsSummary plan={activePlan} onOpen={handleOpenPeople} />
              {!session && (
                <small>Đăng nhập khi bạn muốn mời bạn bè vào kế hoạch này.</small>
              )}
            </div>

            <div className="itinerary-tabs" role="tablist" aria-label="Cách xem kế hoạch">
              <button
                type="button"
                role="tab"
                id="itinerary-list-tab"
                aria-controls="itinerary-plan-view"
                aria-selected={view === 'list'}
                tabIndex={view === 'list' ? 0 : -1}
                className={view === 'list' ? 'is-active' : ''}
                onClick={() => setView('list')}
                onKeyDown={(event) => handleViewKeyDown(event, 'list')}
              >
                <List size={17} aria-hidden="true" />
                Danh sách
              </button>
              <button
                type="button"
                role="tab"
                id="itinerary-timeline-tab"
                aria-controls="itinerary-plan-view"
                aria-selected={view === 'timeline'}
                tabIndex={view === 'timeline' ? 0 : -1}
                className={view === 'timeline' ? 'is-active' : ''}
                onClick={() => setView('timeline')}
                onKeyDown={(event) => handleViewKeyDown(event, 'timeline')}
              >
                <Clock3 size={17} aria-hidden="true" />
                Dòng thời gian
              </button>
            </div>

            <div
              className="itinerary-drawer__body"
              id="itinerary-plan-view"
              role="tabpanel"
              aria-labelledby={view === 'list' ? 'itinerary-list-tab' : 'itinerary-timeline-tab'}
            >
              {activePlan.items.length === 0 ? (
                <div className="itinerary-empty">
                  <MapPin size={26} aria-hidden="true" />
                  <h3>Kế hoạch đang trống</h3>
                  <p>
                    {isReadOnly
                      ? 'Chủ kế hoạch chưa thêm địa điểm nào.'
                      : 'Mở chi tiết một địa điểm và chọn “Thêm vào kế hoạch”.'}
                  </p>
                </div>
              ) : view === 'list' ? (
                <ol className="itinerary-list">
                  {activePlan.items.map((item, index) => (
                    <PlanItem
                      key={item.id}
                      item={item}
                      index={index}
                      itemCount={activePlan.items.length}
                      hasOverlap={overlappingItemIds.has(item.id)}
                      readOnly={isReadOnly}
                      onRemove={() => onRemoveItem(activePlan.id, item.id)}
                      onMove={(direction) => onMoveItem(activePlan.id, item.id, direction)}
                      onUpdateTime={(startTime, endTime) => (
                        onUpdateItemTime(activePlan.id, item.id, startTime, endTime)
                      )}
                    />
                  ))}
                </ol>
              ) : (
                <TimelineView
                  plan={activePlan}
                  overlappingItemIds={overlappingItemIds}
                />
              )}

              <BudgetSummary plan={activePlan} />
            </div>

            <footer className="itinerary-drawer__footer">
              <span>
                {isReadOnly
                  ? `Được ${activePlan.owner?.fullName || 'bạn bè'} chia sẻ · Chỉ đọc`
                  : activePlan.published
                    ? sharing.busyActions?.has(`sync:${activePlan.id}`)
                      ? 'Đang đồng bộ thay đổi…'
                      : 'Đã chia sẻ · Tự động đồng bộ'
                    : storageError
                  ? storageError.includes('tab khác')
                    ? 'Đang giữ bản chỉnh sửa của tab này'
                    : 'Đang dùng dữ liệu tạm trong tab này'
                  : 'Tự động lưu trên thiết bị này'}
              </span>
              {isReadOnly ? (
                <button
                  type="button"
                  className="itinerary-leave-plan"
                  onClick={handleLeave}
                  disabled={sharing.busyActions?.has(`leave-plan:${activePlan.remoteId || activePlan.id}`)}
                >
                  <LogOut size={16} aria-hidden="true" />
                  Rời kế hoạch
                </button>
              ) : (
                <button
                  type="button"
                  className="itinerary-delete-plan"
                  onClick={handleDelete}
                  disabled={sharing.busyActions?.has(`delete-plan:${activePlan.remoteId || activePlan.serverId}`)}
                >
                  <Trash2 size={16} aria-hidden="true" />
                  Xóa kế hoạch
                </button>
              )}
            </footer>
          </>
        ) : (
          <div className="itinerary-empty">
            <CalendarDays size={28} aria-hidden="true" />
            <h3>Chưa có kế hoạch</h3>
            <button
              type="button"
              className="button button--primary"
              onClick={() => setCreating(true)}
            >
              Tạo kế hoạch đầu tiên
            </button>
          </div>
        )}
      </section>
    </div>
  )
}
