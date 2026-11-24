import { useEffect, useRef, useState } from 'react'
import { ArrowLeft, ImagePlus, LogOut, Plus, Send, Settings, UsersRound } from 'lucide-react'
import { availableGroupFriends, groupErrorMessage } from '../chat/groups.js'
import { avatarFileError } from '../profile/model.js'
import ProfileAvatar from './ProfileAvatar.jsx'

function GroupAvatar({ group }) {
  return <ProfileAvatar user={{ fullName: group.name, avatarUrl: group.avatarUrl }} className="chat-avatar" alt="" />
}

function GroupSettings({ group, state, friends, currentUserId, run }) {
  const [name, setName] = useState(group.name)
  const [friendId, setFriendId] = useState('')
  const [confirmLeave, setConfirmLeave] = useState(false)
  const owner = group.ownerId === currentUserId
  const available = availableGroupFriends(friends, group.participants)
  useEffect(() => { setName(group.name) }, [group.name])
  return (
    <div className="group-settings">
      {owner && <>
        <form onSubmit={(event) => { event.preventDefault(); void run(() => state.perform('renameChatGroup', group.id, name.trim())) }}>
          <label>Tên nhóm<input value={name} onChange={(event) => setName(event.target.value)} maxLength={100} required /></label>
          <button type="submit" disabled={state.busy || !name.trim()}>Lưu tên</button>
        </form>
        <label className="group-image-input"><ImagePlus size={18} /> Đổi ảnh nhóm
          <input type="file" accept="image/jpeg,image/png,image/webp" disabled={state.busy}
            onChange={(event) => {
              const file = event.target.files?.[0]
              event.target.value = ''
              if (!file) return
              void run(async () => {
                const error = avatarFileError(file)
                if (error) throw new Error(error)
                return state.perform('uploadChatGroupAvatar', group.id, file)
              })
            }} />
        </label>
        <small>JPEG, PNG hoặc WebP, tối đa 5 MiB.</small>
      </>}
      <form onSubmit={(event) => {
        event.preventDefault()
        void run(async () => {
          const outcome = await state.perform('inviteChatGroupFriend', group.id, friendId)
          if (outcome) setFriendId('')
          return outcome
        })
      }}>
        <label>Mời bạn bè
          <select value={friendId} onChange={(event) => setFriendId(event.target.value)}>
            <option value="">{available.length ? 'Chọn một người bạn' : 'Không còn bạn bè để mời'}</option>
            {available.map((friend) => <option key={friend.user.id} value={friend.user.id}>{friend.user.fullName}</option>)}
          </select>
        </label>
        <button type="submit" disabled={state.busy || !available.some((friend) => friend.user.id === friendId)}>Gửi lời mời</button>
      </form>
      <p>Người được mời cần chấp nhận trước khi tham gia trò chuyện.</p>
      <ul className="group-members" aria-label="Thành viên và lời mời nhóm">
        {group.participants.map((entry) => <li key={entry.user.id}>
          <ProfileAvatar user={entry.user} className="chat-avatar" alt="" />
          <span>{entry.user.fullName}<small>{entry.status === 'INVITED' ? 'Đang chờ chấp nhận' : entry.user.id === group.ownerId ? 'Chủ nhóm' : 'Thành viên'}</small></span>
        </li>)}
      </ul>
      {confirmLeave ? <div className="group-leave-confirm" role="alert">
        <p>{owner
          ? 'Rời nhóm? Quyền quản lý sẽ chuyển cho thành viên tham gia sớm nhất. Nhóm sẽ bị xóa nếu không còn thành viên.'
          : 'Rời nhóm? Bạn sẽ không thể đọc hoặc gửi tin nhắn cho đến khi được mời và tham gia lại.'}</p>
        <button type="button" disabled={state.busy} onClick={() => { void run(() => state.perform('leaveChatGroup', group.id)) }}>Xác nhận rời nhóm</button>
        <button type="button" disabled={state.busy} onClick={() => setConfirmLeave(false)}>Ở lại</button>
      </div> : <button type="button" className="group-leave" onClick={() => setConfirmLeave(true)}><LogOut size={16} /> Rời nhóm</button>}
    </div>
  )
}

function Conversation({ state, friends, currentUserId, run }) {
  const [settings, setSettings] = useState(false)
  const [draft, setDraft] = useState('')
  const endRef = useRef(null)
  const containerRef = useRef(null)
  const group = state.detail
  const last = state.messages.at(-1)
  useEffect(() => {
    endRef.current?.scrollIntoView?.({ block: 'end' })
  }, [last?.id, last?.clientMessageId, settings])
  if (!group) return <div className="chat-empty"><UsersRound size={28} /><p>{state.loading ? 'Đang tải nhóm…' : 'Chọn hoặc tạo một nhóm để trò chuyện.'}</p></div>
  const send = (event) => {
    event.preventDefault()
    if (!draft.trim() || state.busy) return
    const content = draft
    setDraft('')
    void state.sendMessage(content)
  }
  return <>
    <header className="chat-conversation__header">
      <button type="button" className="group-back" aria-label="Về danh sách nhóm" onClick={() => state.selectGroup(null)}><ArrowLeft size={18} /></button>
      <GroupAvatar group={group} />
      <div><strong>{group.name}</strong><span>{group.participants.filter((p) => p.status === 'MEMBER').length} thành viên</span></div>
      <button type="button" className="group-settings-toggle" aria-label={settings ? 'Về trò chuyện nhóm' : 'Thông tin nhóm'} aria-expanded={settings} onClick={() => setSettings(!settings)}><Settings size={19} /></button>
    </header>
    {settings ? <GroupSettings group={group} state={state} friends={friends} currentUserId={currentUserId} run={run} /> : <>
      <div ref={containerRef} className="chat-messages" role="log" aria-label={`Tin nhắn nhóm ${group.name}`} aria-live="polite">
        {state.hasMore && <button type="button" className="chat-load-more" onClick={async () => {
          const container = containerRef.current
          const height = container?.scrollHeight || 0
          const top = container?.scrollTop || 0
          await state.loadOlder()
          requestAnimationFrame(() => { if (container?.isConnected) container.scrollTop = container.scrollHeight - height + top })
        }}>Xem tin nhắn cũ hơn</button>}
        {!state.messages.length && !state.loading && <div className="chat-first-message"><UsersRound size={27} /><strong>Bắt đầu câu chuyện của nhóm</strong><span>Mời bạn bè và gửi lời chào đầu tiên.</span></div>}
        {state.messages.map((message) => <article key={`${message.sender.id}:${message.clientMessageId}`}
          className={`chat-message ${message.sender.id === currentUserId ? 'chat-message--own' : ''} ${message.status === 'FAILED' ? 'chat-message--failed' : ''}`}>
          <small>{message.sender.fullName || 'Thành viên'}</small>
          <div className="chat-message__bubble">{message.content}</div>
          <footer><time dateTime={message.createdAt}>{new Date(message.createdAt).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}</time>
            {message.status === 'SENDING' && <span>Đang gửi…</span>}
            {message.status === 'FAILED' && <button type="button" disabled={state.busy} onClick={() => { void state.sendMessage(message.content, message.clientMessageId) }}>Gửi lại</button>}
          </footer>
          {message.status === 'FAILED' && <small role="alert">{message.error}</small>}
        </article>)}
        <div ref={endRef} />
      </div>
      <form className="chat-composer" onSubmit={send}>
        <label><span className="sr-only">Nhập tin nhắn nhóm</span><textarea value={draft} onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => { if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) send(event) }}
          maxLength={2000} rows={1} placeholder="Nhắn cho cả nhóm…" /></label>
        <button type="submit" disabled={state.busy || !draft.trim()} aria-label="Gửi tin nhắn nhóm"><Send size={18} /></button>
      </form>
    </>}
  </>
}

export default function GroupChatView({ state, friends = [], currentUserId, showToast }) {
  const [creating, setCreating] = useState(false)
  const [name, setName] = useState('')
  const mounted = useRef(true)
  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false; state.selectGroup(null) }
  }, [state.selectGroup])
  const run = async (action) => {
    try { return await action() }
    catch (error) { if (mounted.current) showToast?.(groupErrorMessage(error), 'error'); return null }
  }
  const joined = state.groups.filter((g) => g.status === 'MEMBER')
  const invitations = state.groups.filter((g) => g.status === 'INVITED')
  return <div className={`group-layout ${state.selectedGroupId || creating ? 'group-layout--selected' : ''}`}>
    <aside className="group-list" aria-label="Nhóm trò chuyện">
      <button className="button button--primary group-create" type="button" onClick={() => { state.selectGroup(null); setCreating(true) }}><Plus size={17} /> Tạo nhóm</button>
      {invitations.length > 0 && <h3>Lời mời vào nhóm ({invitations.length})</h3>}
      {invitations.map((group) => <article className="group-invitation" key={group.id}>
        <GroupAvatar group={group} /><strong>{group.name}</strong><small>{group.invitedBy?.fullName || 'Một người bạn'} mời bạn</small>
        <div><button type="button" disabled={state.busy} onClick={() => { void run(async () => {
          const outcome = await state.perform('acceptChatGroupInvitation', group.id)
          if (outcome && mounted.current) { setCreating(false); state.selectGroup(group.id) }
        }) }}>Chấp nhận</button>
          <button type="button" disabled={state.busy} onClick={() => { void run(() => state.perform('declineChatGroupInvitation', group.id)) }}>Xóa lời mời</button></div>
      </article>)}
      <h3>Nhóm của bạn ({joined.length})</h3>
      {joined.map((group) => <button className={`group-list-item ${state.selectedGroupId === group.id ? 'is-active' : ''}`} type="button" key={group.id}
        aria-current={state.selectedGroupId === group.id ? 'true' : undefined}
        onClick={() => { setCreating(false); state.selectGroup(group.id) }}><GroupAvatar group={group} /><span><strong>{group.name}</strong><small>{group.ownerId === currentUserId ? 'Bạn là chủ nhóm' : 'Mở trò chuyện'}</small></span></button>)}
      {!joined.length && <p>Chưa có nhóm. Tạo nhóm mới hoặc chấp nhận lời mời của bạn bè.</p>}
    </aside>
    <main className="chat-conversation group-conversation">
      {state.error && <div className="chat-inline-error" role="alert"><span>{state.error}</span><button type="button" onClick={() => { void state.refresh() }}>Thử lại</button></div>}
      {creating ? <form className="group-create-form" onSubmit={(event) => {
        event.preventDefault()
        void run(async () => {
          const outcome = await state.perform('createChatGroup', name.trim())
          if (outcome && mounted.current) { setName(''); setCreating(false); state.selectGroup(outcome.result.id) }
        })
      }}>
        <button type="button" className="group-back" onClick={() => setCreating(false)}><ArrowLeft size={17} /> Về danh sách nhóm</button>
        <UsersRound size={32} /><h3>Tạo nhóm trò chuyện</h3><p>Đặt tên cho nhóm, sau đó thêm ảnh và mời bạn bè trong phần Thông tin nhóm.</p>
        <label>Tên nhóm<input value={name} onChange={(event) => setName(event.target.value)} maxLength={100} required placeholder="Ví dụ: Cuối tuần đi đâu?" /></label>
        <button className="button button--primary" type="submit" disabled={state.busy || !name.trim()}>{state.busy ? 'Đang tạo…' : 'Tạo nhóm'}</button>
      </form> : <Conversation key={state.selectedGroupId || 'empty'} state={state} friends={friends} currentUserId={currentUserId} run={run} />}
    </main>
  </div>
}
