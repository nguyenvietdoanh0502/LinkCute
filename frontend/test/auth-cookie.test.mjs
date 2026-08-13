import assert from 'node:assert/strict'
import test from 'node:test'
import { createServer } from 'vite'

const SESSION_KEY = 'linkcute.demo.session'

let vite
let client
let originalFetch
let originalLocalStorage
let storageValues

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function saveValidSession(overrides = {}) {
  return client.saveSession({
    user: { id: 'user-1', fullName: 'Minh Anh' },
    accessToken: 'access-token',
    expiresIn: 3600,
    ...overrides,
  })
}

test.before(async () => {
  originalFetch = globalThis.fetch
  originalLocalStorage = globalThis.localStorage
  storageValues = new Map()
  globalThis.localStorage = {
    getItem: (key) => storageValues.get(key) ?? null,
    setItem: (key, value) => storageValues.set(key, String(value)),
    removeItem: (key) => storageValues.delete(key),
  }

  vite = await createServer({
    root: process.cwd(),
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true },
  })
  client = await vite.ssrLoadModule('/src/api/client.js')
})

test.beforeEach(() => {
  storageValues.clear()
  globalThis.fetch = originalFetch
})

test.after(async () => {
  globalThis.fetch = originalFetch
  if (originalLocalStorage === undefined) delete globalThis.localStorage
  else globalThis.localStorage = originalLocalStorage
  await vite?.close()
})

test('session persistence ignores refresh tokens returned to JavaScript', () => {
  const session = saveValidSession({ refreshToken: 'must-not-be-stored' })
  const stored = JSON.parse(storageValues.get(SESSION_KEY))

  assert.equal(session.accessToken, 'access-token')
  assert.equal(Object.hasOwn(session, 'refreshToken'), false)
  assert.equal(Object.hasOwn(stored, 'refreshToken'), false)
  assert.doesNotMatch(storageValues.get(SESSION_KEY), /must-not-be-stored/)
})

test('reading a legacy session removes its refresh token from localStorage', () => {
  storageValues.set(SESSION_KEY, JSON.stringify({
    user: { id: 'legacy-user' },
    accessToken: 'legacy-access',
    refreshToken: 'legacy-secret',
    expiresAt: Date.now() + 60_000,
  }))

  const migrated = client.getStoredSession()
  const stored = JSON.parse(storageValues.get(SESSION_KEY))

  assert.equal(migrated.user.id, 'legacy-user')
  assert.equal(migrated.accessToken, 'legacy-access')
  assert.equal(Object.hasOwn(migrated, 'refreshToken'), false)
  assert.equal(Object.hasOwn(stored, 'refreshToken'), false)
  assert.doesNotMatch(storageValues.get(SESSION_KEY), /legacy-secret/)
})

test('login and OTP verification accept the refresh cookie without a JSON refresh token', async () => {
  const calls = []
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options })
    const userId = url.endsWith('/auth/login') ? 'login-user' : 'verified-user'
    return jsonResponse({
      status: 'success',
      data: {
        user: { id: userId },
        accessToken: `${userId}-access`,
        expiresIn: 900,
      },
    })
  }

  await client.api.login({ email: 'minh@example.com', password: 'password123' })
  await client.api.verifyRegistration({ email: 'minh@example.com', otpCode: '123456' })

  assert.equal(calls.length, 2)
  assert.match(calls[0].url, /\/api\/v1\/auth\/login$/)
  assert.match(calls[1].url, /\/api\/v1\/auth\/verify-otp$/)
  for (const { options } of calls) {
    assert.equal(options.method, 'POST')
    assert.equal(options.credentials, 'include')
    assert.equal(options.headers['X-Requested-With'], 'XMLHttpRequest')
    assert.equal(Object.hasOwn(JSON.parse(options.body), 'refreshToken'), false)
  }
  assert.deepEqual(client.getStoredSession().user, { id: 'verified-user' })
  assert.equal(client.getStoredSession().accessToken, 'verified-user-access')
  assert.equal(Object.hasOwn(JSON.parse(storageValues.get(SESSION_KEY)), 'refreshToken'), false)
})

test('proactive refresh uses only the HttpOnly cookie and preserves the session user', async () => {
  saveValidSession({
    refreshToken: 'legacy-response-token',
    accessToken: 'expired-access',
    expiresIn: -1,
  })
  const calls = []
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options })
    return jsonResponse({
      status: 'success',
      data: {
        accessToken: 'rotated-access',
        refreshToken: 'unexpected-server-token',
        expiresIn: 1800,
      },
    })
  }

  const session = await client.ensureFreshSession()

  assert.equal(calls.length, 1)
  assert.match(calls[0].url, /\/api\/v1\/auth\/refresh-token$/)
  assert.equal(calls[0].options.method, 'POST')
  assert.equal(calls[0].options.credentials, 'include')
  assert.equal(calls[0].options.headers['X-Requested-With'], 'XMLHttpRequest')
  assert.equal(calls[0].options.headers['Content-Type'], undefined)
  assert.equal(calls[0].options.body, undefined)
  assert.equal(session.user.id, 'user-1')
  assert.equal(session.accessToken, 'rotated-access')
  assert.equal(Object.hasOwn(session, 'refreshToken'), false)
  assert.doesNotMatch(storageValues.get(SESSION_KEY), /unexpected-server-token|legacy-response-token/)
})

test('a protected 401 refreshes once, then retries with the rotated access token', async () => {
  saveValidSession()
  const calls = []
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options })
    if (calls.length === 1) {
      return jsonResponse({ status: 'error', message: 'expired' }, 401)
    }
    if (url.endsWith('/auth/refresh-token')) {
      return jsonResponse({
        status: 'success',
        data: { accessToken: 'rotated-access', expiresIn: 1800 },
      })
    }
    return jsonResponse({
      status: 'success',
      data: { id: 'user-1', fullName: 'Minh Anh Updated' },
    })
  }

  const profile = await client.api.getMyProfile()

  assert.equal(profile.fullName, 'Minh Anh Updated')
  assert.equal(calls.length, 3)
  assert.match(calls[0].url, /\/api\/v1\/users\/me$/)
  assert.match(calls[1].url, /\/api\/v1\/auth\/refresh-token$/)
  assert.match(calls[2].url, /\/api\/v1\/users\/me$/)
  assert.equal(calls[0].options.headers.Authorization, 'Bearer access-token')
  assert.equal(calls[1].options.body, undefined)
  assert.equal(calls[2].options.headers.Authorization, 'Bearer rotated-access')
  assert.equal(calls.filter(({ url }) => url.endsWith('/auth/refresh-token')).length, 1)
})

test('a failed cookie refresh clears the local session without retrying forever', async () => {
  saveValidSession()
  const calls = []
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options })
    if (url.endsWith('/auth/refresh-token')) {
      return jsonResponse({ status: 'error', message: 'Refresh cookie expired' }, 401)
    }
    return jsonResponse({ status: 'error', message: 'Access token expired' }, 401)
  }

  await assert.rejects(client.api.getMyProfile(), /Refresh cookie expired/)

  assert.equal(calls.length, 2)
  assert.equal(calls.filter(({ url }) => url.endsWith('/auth/refresh-token')).length, 1)
  assert.equal(client.getStoredSession(), null)
  assert.equal(storageValues.has(SESSION_KEY), false)
})

test('logout sends the CSRF header and cookie credentials, then clears local state', async () => {
  saveValidSession()
  let captured
  globalThis.fetch = async (url, options) => {
    captured = { url, options }
    return jsonResponse({ status: 'success', data: null })
  }

  await client.api.logout()

  assert.match(captured.url, /\/api\/v1\/auth\/logout$/)
  assert.equal(captured.options.method, 'POST')
  assert.equal(captured.options.credentials, 'include')
  assert.equal(captured.options.headers['X-Requested-With'], 'XMLHttpRequest')
  assert.equal(captured.options.headers.Authorization, 'Bearer access-token')
  assert.equal(captured.options.body, undefined)
  assert.equal(client.getStoredSession(), null)
})

test('logout clears local state even when the server request fails', async () => {
  saveValidSession()
  globalThis.fetch = async () => {
    throw new TypeError('Network unavailable')
  }

  await assert.rejects(client.api.logout(), /Network unavailable/)

  assert.equal(client.getStoredSession(), null)
  assert.equal(storageValues.has(SESSION_KEY), false)
})

test('ordinary API requests do not send refresh-cookie credentials or the CSRF header', async () => {
  let captured
  globalThis.fetch = async (url, options) => {
    captured = { url, options }
    return jsonResponse({ status: 'success', data: [] })
  }

  await client.api.getCategories()

  assert.match(captured.url, /\/api\/v1\/categories$/)
  assert.equal(Object.hasOwn(captured.options, 'credentials'), false)
  assert.equal(captured.options.headers['X-Requested-With'], undefined)
})
