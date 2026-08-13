const API_ORIGIN = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
const API_PREFIX = '/api/v1'
const SESSION_KEY = 'linkcute.demo.session'
const COOKIE_AUTH_HEADERS = { 'X-Requested-With': 'XMLHttpRequest' }

export class ApiError extends Error {
  constructor(message, status, errorCode) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.errorCode = errorCode
  }
}

const sessionListeners = new Set()
let refreshPromise = null

export function getStoredSession() {
  try {
    const value = localStorage.getItem(SESSION_KEY)
    if (!value) return null

    const storedSession = JSON.parse(value)
    if (!storedSession || typeof storedSession !== 'object' || Array.isArray(storedSession)) {
      localStorage.removeItem(SESSION_KEY)
      return null
    }

    if (Object.prototype.hasOwnProperty.call(storedSession, 'refreshToken')) {
      const session = { ...storedSession }
      delete session.refreshToken
      localStorage.setItem(SESSION_KEY, JSON.stringify(session))
      return session
    }

    return storedSession
  } catch {
    localStorage.removeItem(SESSION_KEY)
    return null
  }
}

export function getApiOrigin() {
  return API_ORIGIN
}

export function subscribeSession(listener) {
  sessionListeners.add(listener)
  return () => sessionListeners.delete(listener)
}

function publishSession(session) {
  sessionListeners.forEach((listener) => listener(session))
}

export function saveSession(authData) {
  const previous = getStoredSession()
  const session = {
    user: authData.user || previous?.user || null,
    accessToken: authData.accessToken,
    expiresAt: Date.now() + Number(authData.expiresIn || 0) * 1000,
  }
  localStorage.setItem(SESSION_KEY, JSON.stringify(session))
  publishSession(session)
  return session
}

export function updateSessionUser(user) {
  const current = getStoredSession()
  if (!current || !user) return current

  const session = {
    ...current,
    user: {
      ...(current.user || {}),
      ...user,
    },
  }
  localStorage.setItem(SESSION_KEY, JSON.stringify(session))
  publishSession(session)
  return session
}

export function clearSession() {
  localStorage.removeItem(SESSION_KEY)
  publishSession(null)
}

async function parseResponse(response) {
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    throw new ApiError('Máy chủ trả về dữ liệu không hợp lệ.', response.status)
  }
}

async function refreshSession() {
  const session = getStoredSession()
  if (!session?.accessToken) {
    clearSession()
    throw new ApiError('Phiên đăng nhập đã hết hạn.', 401, 'UNAUTHENTICATED')
  }

  if (!refreshPromise) {
    refreshPromise = fetch(`${API_ORIGIN}${API_PREFIX}/auth/refresh-token`, {
      method: 'POST',
      credentials: 'include',
      headers: COOKIE_AUTH_HEADERS,
    })
      .then(async (response) => {
        const payload = await parseResponse(response)
        if (!response.ok || payload?.status === 'error') {
          throw new ApiError(payload?.message || 'Không thể làm mới phiên đăng nhập.', response.status, payload?.errorCode)
        }
        return saveSession(payload.data)
      })
      .catch((error) => {
        clearSession()
        throw error
      })
      .finally(() => {
        refreshPromise = null
      })
  }

  return refreshPromise
}

export async function ensureFreshSession(minValidityMs = 30_000) {
  const session = getStoredSession()
  if (!session?.accessToken) {
    clearSession()
    throw new ApiError('Phiên đăng nhập đã hết hạn.', 401, 'UNAUTHENTICATED')
  }

  const expiresAt = Number(session.expiresAt || 0)
  if (!expiresAt || expiresAt - Date.now() <= minValidityMs) {
    return refreshSession()
  }
  return session
}

async function request(path, options = {}, retry = true) {
  const { auth = false, body, headers, credentials, ...fetchOptions } = options
  const session = getStoredSession()
  const isFormData = typeof FormData !== 'undefined' && body instanceof FormData
  const response = await fetch(`${API_ORIGIN}${API_PREFIX}${path}`, {
    ...fetchOptions,
    ...(credentials ? { credentials } : {}),
    headers: {
      ...(body !== undefined && !isFormData ? { 'Content-Type': 'application/json' } : {}),
      ...(auth && session?.accessToken ? { Authorization: `Bearer ${session.accessToken}` } : {}),
      ...headers,
    },
    body: body === undefined ? undefined : isFormData ? body : JSON.stringify(body),
  })

  if (response.status === 401 && auth && retry && session?.accessToken) {
    await refreshSession()
    return request(path, options, false)
  }

  const payload = await parseResponse(response)
  if (!response.ok || payload?.status === 'error') {
    throw new ApiError(
      payload?.message || `Yêu cầu thất bại (${response.status}).`,
      response.status,
      payload?.errorCode,
    )
  }
  return payload
}

function queryString(params) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, value)
  })
  return search.toString()
}

export const api = {
  getPlaces(params) {
    return request(`/places?${queryString(params)}`).then((response) => response.data)
  },
  getMapPlaces(params, options = {}) {
    return request(`/places/map?${queryString(params)}`, options).then((response) => response.data)
  },
  getPlace(id) {
    return request(`/places/${id}`).then((response) => response.data)
  },
  getCategories() {
    return request('/categories').then((response) => response.data)
  },
  getDistricts() {
    return request('/districts').then((response) => response.data)
  },
  register(body) {
    return request('/auth/register', { method: 'POST', body })
  },
  login(body) {
    return request('/auth/login', {
      method: 'POST',
      body,
      credentials: 'include',
      headers: COOKIE_AUTH_HEADERS,
    }).then((response) => {
      saveSession(response.data)
      return response
    })
  },
  verifyRegistration(body) {
    return request('/auth/verify-otp', {
      method: 'POST',
      body,
      credentials: 'include',
      headers: COOKIE_AUTH_HEADERS,
    }).then((response) => {
      saveSession(response.data)
      return response
    })
  },
  forgotPassword(body) {
    return request('/auth/forgot-password', { method: 'POST', body })
  },
  verifyResetOtp(body) {
    return request('/auth/verify-otp-forgot-password', { method: 'POST', body })
  },
  resetPassword(body) {
    return request('/auth/reset-password', { method: 'POST', body })
  },
  changePassword(body) {
    return request('/auth/change-password', { method: 'POST', body, auth: true })
  },
  getMyProfile(options = {}) {
    return request('/users/me', { ...options, auth: true }).then((response) => {
      updateSessionUser(response.data)
      return response.data
    })
  },
  updateMyProfile(body) {
    return request('/users/me/profile', { method: 'PATCH', body, auth: true }).then((response) => {
      updateSessionUser(response.data)
      return response.data
    })
  },
  uploadMyAvatar(file) {
    const body = new FormData()
    body.append('file', file)
    return request('/users/me/avatar', { method: 'POST', body, auth: true }).then((response) => {
      updateSessionUser(response.data)
      return response.data
    })
  },
  deleteMyAvatar() {
    return request('/users/me/avatar', { method: 'DELETE', auth: true }).then((response) => {
      updateSessionUser(response.data)
      return response.data
    })
  },
  searchFriend(pinCode, options = {}) {
    return request(`/friends/search?${queryString({ pinCode })}`, { ...options, auth: true })
      .then((response) => response.data)
  },
  getFriends(options = {}) {
    return request('/friends', { ...options, auth: true }).then((response) => response.data)
  },
  getIncomingFriendRequests(options = {}) {
    return request('/friends/requests/incoming', { ...options, auth: true })
      .then((response) => response.data)
  },
  getOutgoingFriendRequests(options = {}) {
    return request('/friends/requests/outgoing', { ...options, auth: true })
      .then((response) => response.data)
  },
  sendFriendRequest(addresseeId) {
    return request('/friends/requests', {
      method: 'POST',
      body: { addresseeId },
      auth: true,
    }).then((response) => response?.data ?? null)
  },
  acceptFriendRequest(requestId) {
    return request(`/friends/requests/${encodeURIComponent(requestId)}/accept`, {
      method: 'POST',
      auth: true,
    }).then((response) => response?.data ?? null)
  },
  deleteFriendRequest(requestId) {
    return request(`/friends/requests/${encodeURIComponent(requestId)}`, {
      method: 'DELETE',
      auth: true,
    }).then((response) => response?.data ?? null)
  },
  removeFriend(friendshipId) {
    return request(`/friends/${encodeURIComponent(friendshipId)}`, {
      method: 'DELETE',
      auth: true,
    }).then((response) => response?.data ?? null)
  },
  getChatHistory(friendId, params = {}, options = {}) {
    const query = queryString({ before: params.before, beforeId: params.beforeId, size: params.size })
    return request(`/chat/friends/${encodeURIComponent(friendId)}/messages${query ? `?${query}` : ''}`, {
      ...options,
      auth: true,
    }).then((response) => response.data)
  },
  getPlans(options = {}) {
    return request('/plans', { ...options, auth: true }).then((response) => response.data)
  },
  syncPlan(body) {
    return request('/plans/sync', {
      method: 'POST',
      body,
      auth: true,
    }).then((response) => response?.data ?? null)
  },
  getPlan(planId, options = {}) {
    return request(`/plans/${encodeURIComponent(planId)}`, { ...options, auth: true })
      .then((response) => response.data)
  },
  deletePlan(planId) {
    return request(`/plans/${encodeURIComponent(planId)}`, {
      method: 'DELETE',
      auth: true,
    }).then((response) => response?.data ?? null)
  },
  getIncomingPlanInvitations(options = {}) {
    return request('/plan-invitations/incoming', { ...options, auth: true })
      .then((response) => response.data)
  },
  getOutgoingPlanInvitations(options = {}) {
    return request('/plan-invitations/outgoing', { ...options, auth: true })
      .then((response) => response.data)
  },
  inviteToPlan(planId, inviteeId) {
    return request(`/plans/${encodeURIComponent(planId)}/invitations`, {
      method: 'POST',
      body: { inviteeId },
      auth: true,
    }).then((response) => response?.data ?? null)
  },
  acceptPlanInvitation(invitationId) {
    return request(`/plan-invitations/${encodeURIComponent(invitationId)}/accept`, {
      method: 'POST',
      auth: true,
    }).then((response) => response?.data ?? null)
  },
  declinePlanInvitation(invitationId) {
    return request(`/plan-invitations/${encodeURIComponent(invitationId)}/decline`, {
      method: 'POST',
      auth: true,
    }).then((response) => response?.data ?? null)
  },
  cancelPlanInvitation(invitationId) {
    return request(`/plan-invitations/${encodeURIComponent(invitationId)}`, {
      method: 'DELETE',
      auth: true,
    }).then((response) => response?.data ?? null)
  },
  removePlanMember(planId, userId) {
    return request(`/plans/${encodeURIComponent(planId)}/members/${encodeURIComponent(userId)}`, {
      method: 'DELETE',
      auth: true,
    }).then((response) => response?.data ?? null)
  },
  leavePlan(planId) {
    return request(`/plans/${encodeURIComponent(planId)}/membership`, {
      method: 'DELETE',
      auth: true,
    }).then((response) => response?.data ?? null)
  },
  async logout() {
    try {
      await request('/auth/logout', {
        method: 'POST',
        auth: true,
        credentials: 'include',
        headers: COOKIE_AUTH_HEADERS,
      })
    } finally {
      clearSession()
    }
  },
}
