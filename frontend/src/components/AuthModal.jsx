import {
  ArrowLeft,
  CalendarDays,
  Camera,
  Eye,
  EyeOff,
  KeyRound,
  LockKeyhole,
  LogOut,
  Mail,
  MapPin,
  Pencil,
  Save,
  ShieldCheck,
  Trash2,
  UserRound,
  UsersRound,
  X,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { api, clearSession } from '../api/client.js'
import {
  GENDER_OPTIONS,
  avatarFileError,
  buildProfilePayload,
  calculateAge,
  genderLabel,
  profileFormFromUser,
} from '../profile/model.js'
import ProfileAvatar from './ProfileAvatar.jsx'

const TITLES = {
  login: ['Chào bạn quay lại', 'Đăng nhập để tiếp tục hành trình của riêng bạn.'],
  register: ['Tạo tài khoản', 'Một phút đăng ký, thật nhiều nơi hay để khám phá.'],
  verifyRegister: ['Xác thực email', 'Nhập mã OTP đã được gửi tới email của bạn.'],
  forgot: ['Quên mật khẩu?', 'Chúng tôi sẽ gửi mã xác thực tới email của bạn.'],
  verifyReset: ['Nhập mã xác thực', 'Mã OTP giúp chúng tôi biết đây thực sự là bạn.'],
  reset: ['Đặt mật khẩu mới', 'Chọn một mật khẩu mạnh và dễ nhớ với riêng bạn.'],
  changePassword: ['Đổi mật khẩu', 'Cập nhật mật khẩu cho tài khoản hiện tại.'],
  editProfile: ['Chỉnh sửa hồ sơ', 'Cập nhật thông tin giúp bạn bè nhận ra bạn dễ dàng hơn.'],
}

function TextField({ icon: Icon, label, ...props }) {
  return (
    <label className="form-field">
      <span>{label}</span>
      <div className="form-field__control">
        <Icon size={18} aria-hidden="true" />
        <input {...props} />
      </div>
    </label>
  )
}

function PasswordField({ label, value, onChange, autoComplete = 'current-password' }) {
  const [visible, setVisible] = useState(false)
  return (
    <label className="form-field">
      <span>{label}</span>
      <div className="form-field__control">
        <LockKeyhole size={18} aria-hidden="true" />
        <input
          type={visible ? 'text' : 'password'}
          value={value}
          onChange={onChange}
          autoComplete={autoComplete}
          minLength={8}
          required
        />
        <button
          className="field-icon-button"
          type="button"
          onClick={() => setVisible((current) => !current)}
          aria-label={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
        >
          {visible ? <EyeOff size={17} /> : <Eye size={17} />}
        </button>
      </div>
    </label>
  )
}

function SelectField({ icon: Icon, label, children, ...props }) {
  return (
    <label className="form-field">
      <span>{label}</span>
      <div className="form-field__control">
        <Icon size={18} aria-hidden="true" />
        <select {...props}>{children}</select>
      </div>
    </label>
  )
}

function TextAreaField({ icon: Icon, label, ...props }) {
  return (
    <label className="form-field">
      <span>{label}</span>
      <div className="form-field__control form-field__control--textarea">
        <Icon size={18} aria-hidden="true" />
        <textarea {...props} />
      </div>
    </label>
  )
}

export default function AuthModal({ session, initialMode = 'login', onClose, onOpenFriends, showToast }) {
  const [mode, setMode] = useState(
    session && ['account', 'editProfile', 'changePassword'].includes(initialMode) ? initialMode : session ? 'account' : initialMode,
  )
  const [form, setForm] = useState({
    email: '',
    fullName: '',
    password: '',
    confirmPassword: '',
    otpCode: '',
    oldPassword: '',
    newPassword: '',
    confirmNewPassword: '',
  })
  const [resetToken, setResetToken] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [profileLoading, setProfileLoading] = useState(Boolean(session))
  const [profileForm, setProfileForm] = useState(() => profileFormFromUser(session?.user))
  const [avatarPreview, setAvatarPreview] = useState('')

  useEffect(() => {
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const closeOnEscape = (event) => event.key === 'Escape' && onClose()
    window.addEventListener('keydown', closeOnEscape)
    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', closeOnEscape)
    }
  }, [onClose])

  useEffect(() => {
    if (!session?.user?.id) {
      setProfileLoading(false)
      return undefined
    }

    const controller = new AbortController()
    let active = true
    setProfileLoading(true)
    api.getMyProfile({ signal: controller.signal })
      .catch((requestError) => {
        if (active && requestError?.name !== 'AbortError') {
          setError(requestError.message || 'Không thể tải hồ sơ mới nhất.')
        }
      })
      .finally(() => {
        if (active) setProfileLoading(false)
      })

    return () => {
      active = false
      controller.abort()
    }
  }, [session?.user?.id])

  useEffect(() => {
    if (mode !== 'editProfile') setProfileForm(profileFormFromUser(session?.user))
  }, [mode, session?.user])

  useEffect(() => () => {
    if (avatarPreview) URL.revokeObjectURL(avatarPreview)
  }, [avatarPreview])

  const update = (field) => (event) => {
    setForm((current) => ({ ...current, [field]: event.target.value }))
    setError('')
  }

  const updateProfile = (field) => (event) => {
    setProfileForm((current) => ({ ...current, [field]: event.target.value }))
    setError('')
  }

  const goTo = (nextMode) => {
    setError('')
    setMode(nextMode)
  }

  const run = async (action) => {
    setBusy(true)
    setError('')
    try {
      await action()
    } catch (requestError) {
      setError(requestError.message || 'Đã có lỗi xảy ra. Vui lòng thử lại.')
    } finally {
      setBusy(false)
    }
  }

  const handleSubmit = (event) => {
    event.preventDefault()

    if (mode === 'login') {
      run(async () => {
        await api.login({ email: form.email.trim(), password: form.password })
        showToast('Đăng nhập thành công. Chào bạn quay lại!')
        onClose()
      })
    }

    if (mode === 'register') {
      run(async () => {
        await api.register({
          email: form.email.trim(),
          fullName: form.fullName.trim(),
          password: form.password,
          confirmPassword: form.confirmPassword,
        })
        showToast('Mã OTP đã được gửi tới email của bạn.')
        goTo('verifyRegister')
      })
    }

    if (mode === 'verifyRegister') {
      run(async () => {
        await api.verifyRegistration({ email: form.email.trim(), otpCode: form.otpCode.trim() })
        showToast('Tài khoản đã được xác thực thành công!')
        onClose()
      })
    }

    if (mode === 'forgot') {
      run(async () => {
        await api.forgotPassword({ email: form.email.trim() })
        showToast('Mã khôi phục đã được gửi tới email của bạn.')
        goTo('verifyReset')
      })
    }

    if (mode === 'verifyReset') {
      run(async () => {
        const response = await api.verifyResetOtp({ email: form.email.trim(), otpCode: form.otpCode.trim() })
        setResetToken(response.data.resetToken)
        goTo('reset')
      })
    }

    if (mode === 'reset') {
      run(async () => {
        await api.resetPassword({
          email: form.email.trim(),
          resetToken,
          newPassword: form.newPassword,
          confirmNewPassword: form.confirmNewPassword,
        })
        showToast('Đổi mật khẩu thành công. Bạn có thể đăng nhập ngay.')
        setForm((current) => ({ ...current, password: '', newPassword: '', confirmNewPassword: '' }))
        goTo('login')
      })
    }

    if (mode === 'changePassword') {
      run(async () => {
        await api.changePassword({
          oldPassword: form.oldPassword,
          newPassword: form.newPassword,
          confirmNewPassword: form.confirmNewPassword,
        })
        clearSession()
        showToast('Mật khẩu đã đổi. Hãy đăng nhập lại để tiếp tục.')
        setForm((current) => ({ ...current, password: '', oldPassword: '', newPassword: '', confirmNewPassword: '' }))
        goTo('login')
      })
    }
  }

  const handleProfileSubmit = (event) => {
    event.preventDefault()
    run(async () => {
      const payload = buildProfilePayload(profileForm)
      await api.updateMyProfile(payload)
      showToast('Hồ sơ của bạn đã được cập nhật.')
      goTo('account')
    })
  }

  const handleAvatarChange = (event) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    const validationError = avatarFileError(file)
    if (validationError) {
      setError(validationError)
      return
    }

    setAvatarPreview(URL.createObjectURL(file))
    run(async () => {
      try {
        await api.uploadMyAvatar(file)
        showToast('Ảnh đại diện đã được cập nhật.')
      } finally {
        setAvatarPreview('')
      }
    })
  }

  const removeAvatar = () => {
    if (!session?.user?.avatarUrl || !window.confirm('Xóa ảnh đại diện hiện tại?')) return
    run(async () => {
      await api.deleteMyAvatar()
      showToast('Ảnh đại diện đã được xóa.')
    })
  }

  const logout = () => run(async () => {
    await api.logout()
    showToast('Bạn đã đăng xuất.')
    onClose()
  })

  const otpMode = mode === 'verifyRegister' || mode === 'verifyReset'
  const showBack = !['login', 'register', 'account'].includes(mode)
  const backMode = mode === 'verifyRegister' ? 'register' : mode === 'forgot' ? 'login' : mode === 'verifyReset' ? 'forgot' : mode === 'reset' ? 'verifyReset' : 'account'
  const profileAge = session?.user?.age ?? calculateAge(session?.user?.birthYear)
  const editingAge = calculateAge(profileForm.birthYear)

  return (
    <div className="modal-backdrop auth-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section className="auth-modal" role="dialog" aria-modal="true" aria-label={mode === 'account' ? 'Tài khoản' : TITLES[mode]?.[0]}>
        <div className="auth-modal__art" aria-hidden="true">
          <div className="auth-orbit auth-orbit--one" />
          <div className="auth-orbit auth-orbit--two" />
          <div className="auth-mark">L</div>
          <p>Find your<br /><em>next favorite</em><br />place.</p>
        </div>

        <div className="auth-modal__content">
          <button className="icon-button auth-close" type="button" onClick={onClose} aria-label="Đóng"><X size={20} /></button>
          {showBack && <button className="auth-back" type="button" onClick={() => goTo(backMode)}><ArrowLeft size={17} /> Quay lại</button>}

          {mode === 'account' ? (
            <div className="account-panel">
              <ProfileAvatar className="account-avatar" user={session?.user} />
              <span className="eyebrow">Tài khoản của bạn</span>
              <h2>{session?.user?.fullName || 'LinkCute Explorer'}</h2>
              <p>{session?.user?.email}</p>
              {profileLoading && <span className="account-loading">Đang đồng bộ hồ sơ…</span>}
              {session?.user?.pinCode && <div className="account-pin"><span>Mã kết bạn</span><strong>{session.user.pinCode}</strong></div>}
              <div className="account-details">
                <div><span>Giới tính</span><strong>{genderLabel(session?.user?.gender)}</strong></div>
                <div><span>Năm sinh</span><strong>{session?.user?.birthYear || 'Chưa cập nhật'}{profileAge !== null ? ` · ${profileAge} tuổi` : ''}</strong></div>
                <div><span>Địa chỉ</span><strong>{session?.user?.address || 'Chưa cập nhật'}</strong></div>
              </div>
              {error && <div className="form-error account-error" role="alert">{error}</div>}
              <button className="button button--primary button--wide" type="button" onClick={() => goTo('editProfile')} disabled={profileLoading}><Pencil size={17} /> Chỉnh sửa hồ sơ</button>
              {onOpenFriends && <button className="button button--added button--wide" type="button" onClick={onOpenFriends}><UsersRound size={17} /> Bạn bè của tôi</button>}
              <button className="button button--ghost button--wide" type="button" onClick={() => goTo('changePassword')}><KeyRound size={17} /> Đổi mật khẩu</button>
              <button className="button button--ghost button--wide" type="button" onClick={logout} disabled={busy}><LogOut size={17} /> {busy ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>
            </div>
          ) : mode === 'editProfile' ? (
            <>
              <div className="auth-heading profile-heading">
                <span className="eyebrow">LinkCute member</span>
                <h2>{TITLES.editProfile[0]}</h2>
                <p>{TITLES.editProfile[1]}</p>
              </div>

              <form className="auth-form profile-form" onSubmit={handleProfileSubmit}>
                <div className="profile-avatar-editor">
                  <ProfileAvatar
                    className="account-avatar profile-avatar-preview"
                    user={session?.user}
                    src={avatarPreview || session?.user?.avatarUrl}
                  />
                  <div className="profile-avatar-editor__actions">
                    <label className={`button button--ghost profile-avatar-picker${busy ? ' is-disabled' : ''}`}>
                      <Camera size={16} /> {busy && avatarPreview ? 'Đang tải ảnh…' : 'Chọn ảnh'}
                      <input
                        type="file"
                        accept="image/jpeg,image/png,image/webp"
                        onChange={handleAvatarChange}
                        disabled={busy}
                      />
                    </label>
                    {session?.user?.avatarUrl && (
                      <button className="profile-avatar-remove" type="button" onClick={removeAvatar} disabled={busy}>
                        <Trash2 size={15} /> Xóa ảnh
                      </button>
                    )}
                    <small>JPEG, PNG hoặc WebP · tối đa 5 MiB</small>
                  </div>
                </div>

                <TextField
                  icon={UserRound}
                  label="Họ và tên"
                  value={profileForm.fullName}
                  onChange={updateProfile('fullName')}
                  autoComplete="name"
                  minLength={2}
                  maxLength={100}
                  required
                />
                <SelectField icon={UserRound} label="Giới tính" value={profileForm.gender} onChange={updateProfile('gender')}>
                  {GENDER_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                </SelectField>
                <TextField
                  icon={CalendarDays}
                  label="Năm sinh"
                  type="number"
                  value={profileForm.birthYear}
                  onChange={updateProfile('birthYear')}
                  min={1900}
                  max={new Date().getFullYear()}
                  inputMode="numeric"
                  placeholder="2000"
                />
                {editingAge !== null && <div className="profile-age-hint">Tuổi ước tính: <strong>{editingAge}</strong></div>}
                <TextAreaField
                  icon={MapPin}
                  label="Địa chỉ"
                  value={profileForm.address}
                  onChange={updateProfile('address')}
                  autoComplete="street-address"
                  maxLength={255}
                  rows={3}
                  placeholder="Quận, thành phố hoặc địa chỉ của bạn"
                />

                {error && <div className="form-error" role="alert">{error}</div>}
                <button className="button button--primary button--wide auth-submit" type="submit" disabled={busy}>
                  {busy ? <><span className="button-loader" /> Đang lưu…</> : <><Save size={17} /> Lưu thay đổi</>}
                </button>
              </form>
            </>
          ) : (
            <>
              <div className="auth-heading">
                {otpMode && <span className="auth-heading__icon"><ShieldCheck size={24} /></span>}
                <span className="eyebrow">LinkCute member</span>
                <h2>{TITLES[mode]?.[0]}</h2>
                <p>{TITLES[mode]?.[1]}</p>
              </div>

              <form className="auth-form" onSubmit={handleSubmit}>
                {mode === 'register' && <TextField icon={UserRound} label="Họ và tên" value={form.fullName} onChange={update('fullName')} autoComplete="name" placeholder="Nguyễn Minh Anh" required />}

                {['login', 'register', 'forgot'].includes(mode) && (
                  <TextField icon={Mail} label="Email" type="email" value={form.email} onChange={update('email')} autoComplete="email" placeholder="ban@email.com" required />
                )}

                {otpMode && (
                  <>
                    <div className="otp-recipient">Mã được gửi tới <strong>{form.email}</strong></div>
                    <TextField icon={ShieldCheck} label="Mã OTP" value={form.otpCode} onChange={update('otpCode')} inputMode="numeric" autoComplete="one-time-code" placeholder="000000" maxLength={6} pattern="[0-9]{6}" required />
                  </>
                )}

                {['login', 'register'].includes(mode) && <PasswordField label="Mật khẩu" value={form.password} onChange={update('password')} autoComplete={mode === 'register' ? 'new-password' : 'current-password'} />}
                {mode === 'register' && <PasswordField label="Nhập lại mật khẩu" value={form.confirmPassword} onChange={update('confirmPassword')} autoComplete="new-password" />}
                {mode === 'changePassword' && <PasswordField label="Mật khẩu hiện tại" value={form.oldPassword} onChange={update('oldPassword')} />}
                {['reset', 'changePassword'].includes(mode) && (
                  <>
                    <PasswordField label="Mật khẩu mới" value={form.newPassword} onChange={update('newPassword')} autoComplete="new-password" />
                    <PasswordField label="Nhập lại mật khẩu mới" value={form.confirmNewPassword} onChange={update('confirmNewPassword')} autoComplete="new-password" />
                  </>
                )}

                {mode === 'login' && <button className="text-button auth-forgot" type="button" onClick={() => goTo('forgot')}>Quên mật khẩu?</button>}
                {error && <div className="form-error" role="alert">{error}</div>}

                <button className="button button--primary button--wide auth-submit" type="submit" disabled={busy}>
                  {busy ? <><span className="button-loader" /> Đang xử lý…</> : (
                    mode === 'login' ? 'Đăng nhập' :
                    mode === 'register' ? 'Tạo tài khoản' :
                    otpMode ? 'Xác thực mã OTP' :
                    mode === 'forgot' ? 'Gửi mã xác thực' :
                    mode === 'reset' ? 'Lưu mật khẩu mới' : 'Đổi mật khẩu'
                  )}
                </button>
              </form>

              {['login', 'register'].includes(mode) && (
                <p className="auth-switch">
                  {mode === 'login' ? 'Chưa có tài khoản?' : 'Bạn đã có tài khoản?'}{' '}
                  <button type="button" onClick={() => goTo(mode === 'login' ? 'register' : 'login')}>{mode === 'login' ? 'Đăng ký ngay' : 'Đăng nhập'}</button>
                </p>
              )}
            </>
          )}
        </div>
      </section>
    </div>
  )
}
