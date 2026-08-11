import assert from 'node:assert/strict'
import test from 'node:test'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

let vite
let AuthModal

test.before(async () => {
  vite = await createServer({
    root: process.cwd(),
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true },
  })
  AuthModal = (await vite.ssrLoadModule('/src/components/AuthModal.jsx')).default
})

test.after(async () => {
  await vite?.close()
})

const user = {
  id: 'user-1',
  email: 'minhanh@example.com',
  fullName: 'Nguyễn Minh Anh',
  pinCode: 'RML-123456',
  avatarUrl: 'https://res.cloudinary.com/demo/image/upload/avatar.webp',
  gender: 'FEMALE',
  birthYear: 2000,
  age: 26,
  address: 'Cầu Giấy, Hà Nội',
}

const commonProps = {
  session: { user, accessToken: 'token' },
  onClose: () => {},
  onOpenFriends: () => {},
  showToast: () => {},
}

test('account view renders the Cloudinary avatar and complete profile summary', () => {
  const markup = renderToStaticMarkup(React.createElement(AuthModal, commonProps))

  assert.match(markup, /res\.cloudinary\.com\/demo\/image\/upload\/avatar\.webp/)
  assert.match(markup, /Nguyễn Minh Anh/)
  assert.match(markup, /Nữ/)
  assert.match(markup, /2000 · 26 tuổi/)
  assert.match(markup, /Cầu Giấy, Hà Nội/)
  assert.match(markup, /Chỉnh sửa hồ sơ/)
})

test('edit profile view exposes all fields and constrained avatar input', () => {
  const markup = renderToStaticMarkup(React.createElement(AuthModal, {
    ...commonProps,
    initialMode: 'editProfile',
  }))

  assert.match(markup, /Họ và tên/)
  assert.match(markup, /value="Nguyễn Minh Anh"/)
  assert.match(markup, /Giới tính/)
  assert.match(markup, /value="FEMALE" selected=""/)
  assert.match(markup, /Năm sinh/)
  assert.match(markup, /value="2000"/)
  assert.match(markup, /Tuổi ước tính:/)
  assert.match(markup, /Cầu Giấy, Hà Nội/)
  assert.match(markup, /accept="image\/jpeg,image\/png,image\/webp"/)
  assert.match(markup, /tối đa 5 MiB/)
  assert.match(markup, /Lưu thay đổi/)
})
