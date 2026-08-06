const API_ORIGIN = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
const API_PREFIX = '/api/v1'
const SESSION_KEY = 'linkcute.demo.session'

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
    return value ? JSON.parse(value) : null
  } catch {
    localStorage.removeItem(SESSION_KEY)
    return null
  }
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
    refreshToken: authData.refreshToken,
    expiresAt: Date.now() + Number(authData.expiresIn || 0) * 1000,
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
  if (!session?.refreshToken) throw new ApiError('Phiên đăng nhập đã hết hạn.', 401)

  if (!refreshPromise) {
    refreshPromise = fetch(`${API_ORIGIN}${API_PREFIX}/auth/refresh-token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: session.refreshToken }),
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

async function request(path, options = {}, retry = true) {
  const { auth = false, body, headers, ...fetchOptions } = options
  const session = getStoredSession()
  const response = await fetch(`${API_ORIGIN}${API_PREFIX}${path}`, {
    ...fetchOptions,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(auth && session?.accessToken ? { Authorization: `Bearer ${session.accessToken}` } : {}),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (response.status === 401 && auth && retry && session?.refreshToken) {
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
    return request('/auth/login', { method: 'POST', body }).then((response) => {
      saveSession(response.data)
      return response
    })
  },
  verifyRegistration(body) {
    return request('/auth/verify-otp', { method: 'POST', body }).then((response) => {
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
      await request('/auth/logout', { method: 'POST', auth: true })
    } finally {
      clearSession()
    }
  },
}
