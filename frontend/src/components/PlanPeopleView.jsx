import {
  ArrowLeft,
  CalendarDays,
  Check,
  LogIn,
  RefreshCw,
  Search,
  UserMinus,
  UserPlus,
  UsersRound,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { profileInitials } from '../friends/model.js'
import { planSharingErrorMessage } from '../itinerary/sharing.js'

const DATE_FORMATTER = new Intl.DateTimeFormat('vi-VN', {
  weekday: 'short',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
})

function formatDate(value) {
  if (!value) return 'Chưa chọn ngày'
  const date = new Date(`${value}T00:00:00`)
  return Number.isNaN(date.getTime()) ? value : DATE_FORMATTER.format(date)
}

function PersonAvatar({ user }) {
  const [imageFailed, setImageFailed] = useState(false)
  useEffect(() => setImageFailed(false), [user?.avatarUrl])

  return (
    <span className="plan-person-avatar" aria-hidden="true">
      {user?.avatarUrl && !imageFailed ? (
        <img src={user.avatarUrl} alt="" onError={() => setImageFailed(true)} />
      ) : profileInitials(user?.fullName)}
    </span>
  )
}

function PersonIdentity({ user, meta }) {
  return (
    <span className="plan-person-identity">
      <strong>{user?.fullName || 'Thành viên LinkCute'}</strong>
      <small>{meta || user?.pinCode || ''}</small>
    </span>
  )
}

function SubviewHeading({ eyebrow, title, description, onBack, actions }) {
  return (
    <header className="plan-subview-heading">
      <button
        className="plan-subview-back"
        type="button"
        onClick={onBack}
        aria-label="Quay lại kế hoạch"
        data-itinerary-initial-focus
      >
        <ArrowLeft size={19} aria-hidden="true" />
      </button>
      <div>
        <span>{eyebrow}</span>
        <h3>{title}</h3>
        {description && <p>{description}</p>}
      </div>
      {actions && <div className="plan-subview-heading__actions">{actions}</div>}
    </header>
  )
}

export default function PlanPeopleView({
  plan,
  session,
  friends = [],
  friendsLoading = false,
  friendsError = '',
  busyActions = new Set(),
  invitationStateFor,
  onInvite,
  onCancelInvitation,
  onRemoveMember,
  onRefreshFriends,
  onOpenFriends,
  onBack,
  showToast,
}) {
  const [query, setQuery] = useState('')
  const [actionError, setActionError] = useState('')
  const isOwner = plan?.accessRole !== 'MEMBER'
  const normalizedQuery = query.trim().toLocaleLowerCase('vi-VN')
  const filteredFriends = useMemo(() => friends.filter(({ user }) => {
    if (!normalizedQuery) return true
    return `${user?.fullName || ''} ${user?.pinCode || ''}`
      .toLocaleLowerCase('vi-VN')
      .includes(normalizedQuery)
  }), [friends, normalizedQuery])

  const runAction = async (action, successMessage) => {
    setActionError('')
    try {
      const result = await action()
      if (result === null || result === undefined) return result
      if (successMessage) showToast?.(successMessage)
      return result
    } catch (error) {
      const message = planSharingErrorMessage(error)
      setActionError(message)
      showToast?.(message, 'error')
    }
  }

  return (
    <div className="plan-people-view">
      <SubviewHeading
        eyebrow={isOwner ? 'Chia sẻ hành trình' : 'Đi cùng nhau'}
        title="Người tham gia"
        description={plan ? `${plan.name} · ${formatDate(plan.date)}` : ''}
        onBack={onBack}
      />

      {!session ? (
        <div className="plan-people-empty">
          <LogIn size={27} aria-hidden="true" />
          <h4>Đăng nhập để chia sẻ</h4>
          <p>Kế hoạch local vẫn được giữ nguyên trên thiết bị này.</p>
        </div>
      ) : (
        <div className="plan-people-view__body">
          <section className="plan-participants" aria-labelledby="plan-participants-title">
            <div className="plan-people-section-heading">
              <div>
                <span>Đang tham gia</span>
                <h4 id="plan-participants-title">
                  {1 + (plan?.members?.length || 0)} người
                </h4>
              </div>
            </div>

            <div className="plan-people-list">
              <article className="plan-person-row">
                <PersonAvatar user={plan?.owner || session.user} />
                <PersonIdentity user={plan?.owner || session.user} meta="Chủ kế hoạch" />
                <span className="plan-person-status plan-person-status--owner">Chủ kế hoạch</span>
              </article>

              {(plan?.members || []).map((member) => {
                const memberId = member.user?.id
                const removeKey = `remove-member:${plan.remoteId || plan.serverId}:${memberId}`
                const isCurrentUser = memberId === session.user?.id
                return (
                  <article className="plan-person-row" key={memberId}>
                    <PersonAvatar user={member.user} />
                    <PersonIdentity
                      user={member.user}
                      meta={isCurrentUser ? 'Bạn' : member.user?.pinCode}
                    />
                    {isOwner && !isCurrentUser ? (
                      <button
                        className="plan-person-icon-action"
                        type="button"
                        disabled={busyActions.has(removeKey)}
                        onClick={() => runAction(
                          () => onRemoveMember(plan, member.user),
                          `Đã xóa ${member.user?.fullName || 'thành viên'} khỏi kế hoạch.`,
                        )}
                        aria-label={`Xóa ${member.user?.fullName || 'thành viên'} khỏi kế hoạch`}
                        title="Xóa khỏi kế hoạch"
                      >
                        {busyActions.has(removeKey)
                          ? <span className="friend-action-loader" />
                          : <UserMinus size={17} aria-hidden="true" />}
                      </button>
                    ) : (
                      <span className="plan-person-status">Thành viên</span>
                    )}
                  </article>
                )
              })}
            </div>
          </section>

          {isOwner && (
            <section className="plan-invite-friends" aria-labelledby="plan-invite-friends-title">
              <div className="plan-people-section-heading">
                <div>
                  <span>Danh sách bạn bè</span>
                  <h4 id="plan-invite-friends-title">Mời thêm bạn</h4>
                </div>
                <button
                  className="plan-people-refresh"
                  type="button"
                  onClick={onRefreshFriends}
                  disabled={friendsLoading}
                  aria-label="Tải lại danh sách bạn bè"
                >
                  <RefreshCw size={16} aria-hidden="true" />
                </button>
              </div>

              {friends.length > 0 && (
                <label className="plan-friend-filter">
                  <span className="sr-only">Lọc danh sách bạn bè</span>
                  <Search size={17} aria-hidden="true" />
                  <input
                    type="search"
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Tìm theo tên hoặc mã kết bạn…"
                  />
                </label>
              )}

              {friendsLoading && (
                <div className="plan-people-loading" role="status">
                  <span className="loader" />
                  <p>Đang tải danh sách bạn bè…</p>
                </div>
              )}

              {!friendsLoading && friendsError && (
                <div className="plan-people-error" role="alert">
                  <p>{friendsError}</p>
                  <button className="button button--secondary" type="button" onClick={onRefreshFriends}>
                    Thử lại
                  </button>
                </div>
              )}

              {!friendsLoading && !friendsError && friends.length === 0 && (
                <div className="plan-people-empty plan-people-empty--compact">
                  <UsersRound size={25} aria-hidden="true" />
                  <h4>Chưa có bạn bè để mời</h4>
                  <p>Hãy kết bạn trước, sau đó quay lại chọn người đồng hành.</p>
                  <button className="button button--secondary" type="button" onClick={onOpenFriends}>
                    <UserPlus size={17} aria-hidden="true" />
                    Kết bạn ngay
                  </button>
                </div>
              )}

              {!friendsLoading && !friendsError && friends.length > 0 && (
                filteredFriends.length > 0 ? (
                  <div className="plan-people-list plan-people-list--friends">
                    {filteredFriends.map((friend) => {
                      const user = friend.user
                      const state = invitationStateFor?.(plan, user?.id)
                        || { status: 'NONE', invitation: null }
                      const inviteBusy = busyActions.has(`invite:${plan.id}:${user?.id}`)
                      const cancelBusy = state.invitation
                        && busyActions.has(`cancel-invite:${state.invitation.id}`)

                      return (
                        <article className="plan-person-row plan-person-row--friend" key={user?.id}>
                          <PersonAvatar user={user} />
                          <PersonIdentity user={user} />
                          {state.status === 'MEMBER' ? (
                            <span className="plan-person-status plan-person-status--joined">
                              <Check size={14} aria-hidden="true" /> Đã tham gia
                            </span>
                          ) : state.status === 'PENDING' ? (
                            <button
                              className="plan-person-action plan-person-action--pending"
                              type="button"
                              disabled={cancelBusy}
                              onClick={() => runAction(
                                () => onCancelInvitation(state.invitation),
                                `Đã thu hồi lời mời tới ${user?.fullName || 'người bạn này'}.`,
                              )}
                            >
                              {cancelBusy
                                ? <span className="friend-action-loader" />
                                : <X size={15} aria-hidden="true" />}
                              Thu hồi
                            </button>
                          ) : (
                            <button
                              className="plan-person-action"
                              type="button"
                              disabled={inviteBusy}
                              onClick={() => runAction(
                                () => onInvite(plan, user),
                                `Đã mời ${user?.fullName || 'người bạn này'} vào kế hoạch.`,
                              )}
                            >
                              {inviteBusy
                                ? <span className="friend-action-loader" />
                                : <UserPlus size={16} aria-hidden="true" />}
                              Mời
                            </button>
                          )}
                        </article>
                      )
                    })}
                  </div>
                ) : (
                  <p className="plan-friend-filter-empty">Không tìm thấy người bạn phù hợp.</p>
                )
              )}
            </section>
          )}

          {actionError && <p className="plan-people-action-error" role="alert">{actionError}</p>}
        </div>
      )}
    </div>
  )
}

export function IncomingPlanInvitationsView({
  invitations = [],
  loading = false,
  error = '',
  busyActions = new Set(),
  onRefresh,
  onAccept,
  onDecline,
  onAccepted,
  onBack,
  showToast,
}) {
  const [actionError, setActionError] = useState('')

  const accept = async (invitation) => {
    setActionError('')
    try {
      const plan = await onAccept(invitation)
      if (!plan) return
      showToast?.(`Bạn đã tham gia “${invitation.plan.name}”.`)
      onAccepted?.(plan || { id: invitation.plan.id })
    } catch (requestError) {
      const message = planSharingErrorMessage(requestError)
      setActionError(message)
      showToast?.(message, 'error')
    }
  }

  const decline = async (invitation) => {
    setActionError('')
    try {
      const result = await onDecline(invitation)
      if (result === null || result === undefined) return
      showToast?.(`Đã từ chối lời mời vào “${invitation.plan.name}”.`)
    } catch (requestError) {
      const message = planSharingErrorMessage(requestError)
      setActionError(message)
      showToast?.(message, 'error')
    }
  }

  return (
    <div className="plan-invitations-view">
      <SubviewHeading
        eyebrow="Lời mời đi chơi"
        title="Kế hoạch được chia sẻ"
        description="Chấp nhận để xem hành trình và danh sách người tham gia."
        onBack={onBack}
        actions={(
          <button
            className="plan-people-refresh"
            type="button"
            onClick={onRefresh}
            disabled={loading}
            aria-label="Tải lại lời mời kế hoạch"
          >
            <RefreshCw size={17} aria-hidden="true" />
          </button>
        )}
      />

      <div className="plan-invitations-view__body">
        {loading && (
          <div className="plan-people-loading" role="status">
            <span className="loader" />
            <p>Đang tải lời mời…</p>
          </div>
        )}

        {!loading && error && (
          <div className="plan-people-error" role="alert">
            <p>{error}</p>
            <button className="button button--secondary" type="button" onClick={onRefresh}>Thử lại</button>
          </div>
        )}

        {!loading && !error && invitations.length === 0 && (
          <div className="plan-people-empty">
            <CalendarDays size={28} aria-hidden="true" />
            <h4>Chưa có lời mời mới</h4>
            <p>Khi bạn bè mời đi chơi, kế hoạch sẽ xuất hiện ở đây.</p>
          </div>
        )}

        {!loading && !error && invitations.length > 0 && (
          <div className="plan-invitation-list">
            {invitations.map((invitation) => {
              const acceptBusy = busyActions.has(`accept-invite:${invitation.id}`)
              const declineBusy = busyActions.has(`decline-invite:${invitation.id}`)
              const owner = invitation.plan.owner || invitation.inviter
              return (
                <article className="plan-invitation-card" key={invitation.id}>
                  <div className="plan-invitation-card__owner">
                    <PersonAvatar user={owner} />
                    <PersonIdentity user={owner} meta="Đã mời bạn đi cùng" />
                  </div>
                  <div className="plan-invitation-card__plan">
                    <span><CalendarDays size={15} aria-hidden="true" /> {formatDate(invitation.plan.date)}</span>
                    <h4>{invitation.plan.name}</h4>
                    <p>{invitation.plan.itemCount} địa điểm</p>
                  </div>
                  <div className="plan-invitation-card__actions">
                    <button
                      className="button button--primary"
                      type="button"
                      disabled={acceptBusy || declineBusy}
                      onClick={() => accept(invitation)}
                    >
                      {acceptBusy
                        ? <span className="button-loader" />
                        : <Check size={17} aria-hidden="true" />}
                      Tham gia
                    </button>
                    <button
                      className="button button--secondary"
                      type="button"
                      disabled={acceptBusy || declineBusy}
                      onClick={() => decline(invitation)}
                    >
                      {declineBusy
                        ? <span className="friend-action-loader" />
                        : <X size={17} aria-hidden="true" />}
                      Từ chối
                    </button>
                  </div>
                </article>
              )
            })}
          </div>
        )}

        {actionError && <p className="plan-people-action-error" role="alert">{actionError}</p>}
      </div>
    </div>
  )
}

export function PlanParticipantsSummary({ plan, onOpen }) {
  const people = [plan?.owner, ...(plan?.members || []).map((member) => member.user)]
    .filter(Boolean)
    .slice(0, 4)
  const count = 1 + (plan?.members?.length || 0)

  return (
    <button
      className="plan-participants-summary"
      type="button"
      onClick={onOpen}
      aria-label={`Xem người tham gia, ${count} người`}
    >
      <span className="plan-participants-summary__avatars" aria-hidden="true">
        {people.length ? people.map((user) => (
          <PersonAvatar key={user.id} user={user} />
        )) : <span className="plan-person-avatar"><UsersRound size={17} /></span>}
      </span>
      <span>
        <strong>{count} người</strong>
        <small>{plan?.accessRole === 'MEMBER' ? 'Xem người đi cùng' : 'Mời bạn đi cùng'}</small>
      </span>
      {plan?.accessRole === 'OWNER' && <UserPlus size={18} aria-hidden="true" />}
    </button>
  )
}
