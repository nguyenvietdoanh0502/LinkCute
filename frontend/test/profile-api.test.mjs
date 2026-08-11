import assert from 'node:assert/strict'
import test from 'node:test'
import { createServer } from 'vite'

let vite
let client
let originalFetch
let originalLocalStorage

test.before(async () => {
  originalFetch = globalThis.fetch
  originalLocalStorage = globalThis.localStorage

  const values = new Map()
  globalThis.localStorage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key),
  }

  vite = await createServer({
    root: process.cwd(),
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true },
  })
  client = await vite.ssrLoadModule('/src/api/client.js')
})

test.after(async () => {
  globalThis.fetch = originalFetch
  if (originalLocalStorage === undefined) delete globalThis.localStorage
  else globalThis.localStorage = originalLocalStorage
  await vite?.close()
})

test('updating the session user preserves every token field', () => {
  client.saveSession({
    user: { id: 'user-1', fullName: 'Tên cũ' },
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    expiresIn: 3600,
  })
  const before = client.getStoredSession()

  client.updateSessionUser({ id: 'user-1', fullName: 'Tên mới', avatarUrl: 'https://res.cloudinary.com/avatar.jpg' })
  const after = client.getStoredSession()

  assert.equal(after.accessToken, before.accessToken)
  assert.equal(after.refreshToken, before.refreshToken)
  assert.equal(after.expiresAt, before.expiresAt)
  assert.equal(after.user.fullName, 'Tên mới')
  assert.equal(after.user.avatarUrl, 'https://res.cloudinary.com/avatar.jpg')
})

test('avatar upload sends FormData without overriding its multipart boundary', async () => {
  const calls = []
  const updatedUser = {
    id: 'user-1',
    fullName: 'Tên mới',
    avatarUrl: 'https://res.cloudinary.com/avatar.webp',
  }
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options })
    return new Response(JSON.stringify({ status: 'success', data: updatedUser }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })
  }

  await client.api.uploadMyAvatar(new Blob(['avatar'], { type: 'image/webp' }))

  assert.equal(calls.length, 1)
  assert.match(calls[0].url, /\/api\/v1\/users\/me\/avatar$/)
  assert.equal(calls[0].options.method, 'POST')
  assert.equal(calls[0].options.headers.Authorization, 'Bearer access-token')
  assert.equal(calls[0].options.headers['Content-Type'], undefined)
  assert.ok(calls[0].options.body instanceof FormData)
  assert.equal(calls[0].options.body.get('file').type, 'image/webp')
  assert.equal(client.getStoredSession().user.avatarUrl, updatedUser.avatarUrl)
})

test('profile update remains JSON and synchronizes the returned user', async () => {
  let captured
  globalThis.fetch = async (url, options) => {
    captured = { url, options }
    return new Response(JSON.stringify({
      status: 'success',
      data: { id: 'user-1', fullName: 'Nguyễn Minh Anh', gender: 'FEMALE', birthYear: 2000 },
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })
  }

  await client.api.updateMyProfile({
    fullName: 'Nguyễn Minh Anh',
    gender: 'FEMALE',
    birthYear: 2000,
    address: null,
  })

  assert.match(captured.url, /\/api\/v1\/users\/me\/profile$/)
  assert.equal(captured.options.method, 'PATCH')
  assert.equal(captured.options.headers['Content-Type'], 'application/json')
  assert.equal(JSON.parse(captured.options.body).birthYear, 2000)
  assert.equal(client.getStoredSession().user.gender, 'FEMALE')
})
