export const LOCATION_MESSAGE_TYPE = 'LOCATION'
export const TEXT_MESSAGE_TYPE = 'TEXT'
export const DEFAULT_LOCATION_MAX_AGE_MS = 30_000

export const MAX_LOCATION_ACCURACY_METERS = 1_000_000
export const MAX_MERCATOR_LATITUDE = 85.051129

function finiteNumber(value) {
  if (value === null || value === undefined || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function capturedAtValue(value) {
  if (value === null || value === undefined || value === '') return null
  const timestamp = typeof value === 'number' ? value : new Date(value).getTime()
  return Number.isFinite(timestamp) ? new Date(timestamp).toISOString() : null
}

export function normalizeLocation(value) {
  const latitude = finiteNumber(value?.latitude ?? value?.lat)
  const longitude = finiteNumber(value?.longitude ?? value?.lng)
  if (latitude === null || longitude === null) return null
  if (latitude < -90 || latitude > 90) return null
  if (longitude < -180 || longitude > 180) return null

  const rawAccuracy = finiteNumber(value?.accuracyMeters ?? value?.accuracy)
  if (rawAccuracy === null || rawAccuracy < 0 || rawAccuracy > MAX_LOCATION_ACCURACY_METERS) return null

  return {
    latitude,
    longitude,
    accuracyMeters: rawAccuracy,
    capturedAt: capturedAtValue(value?.capturedAt ?? value?.timestamp),
  }
}

export function requireLocation(value) {
  const location = normalizeLocation(value)
  if (!location) {
    throw new Error('Tọa độ vị trí không hợp lệ. Vui lòng lấy lại vị trí và thử lại.')
  }
  return location
}

export function isValidLocation(value) {
  return Boolean(normalizeLocation(value))
}

export function chatMessageType(message) {
  const type = String(message?.messageType || TEXT_MESSAGE_TYPE).trim().toUpperCase()
  return type || TEXT_MESSAGE_TYPE
}

export function locationFromMessage(message) {
  if (chatMessageType(message) !== LOCATION_MESSAGE_TYPE) return null
  return normalizeLocation(message)
}

export function isFreshLocation(
  value,
  maxAgeMs = DEFAULT_LOCATION_MAX_AGE_MS,
  now = Date.now(),
) {
  const location = normalizeLocation(value)
  if (!location?.capturedAt) return false
  const capturedAt = new Date(location.capturedAt).getTime()
  return Number.isFinite(capturedAt)
    && capturedAt <= now + 1_000
    && now - capturedAt <= Math.max(0, Number(maxAgeMs) || 0)
}

export function geolocationErrorMessage(error) {
  if (error?.code === 1 || error?.name === 'NotAllowedError') {
    return 'Bạn đã từ chối quyền vị trí. Hãy cho phép vị trí trong cài đặt trình duyệt rồi thử lại.'
  }
  if (error?.code === 2 || error?.name === 'PositionUnavailableError') {
    return 'Thiết bị chưa xác định được vị trí. Hãy bật GPS hoặc kiểm tra kết nối rồi thử lại.'
  }
  if (error?.code === 3 || error?.name === 'TimeoutError') {
    return 'Quá thời gian chờ vị trí. Hãy di chuyển tới nơi thoáng hơn rồi thử lại.'
  }
  if (error?.code === 'GEOLOCATION_INSECURE') {
    return 'Trình duyệt chỉ cho phép lấy vị trí trên HTTPS hoặc localhost.'
  }
  if (error?.code === 'GEOLOCATION_UNSUPPORTED') {
    return 'Trình duyệt này không hỗ trợ lấy vị trí.'
  }
  return error?.message || 'Chưa thể lấy vị trí của bạn. Vui lòng thử lại.'
}

export function locationAccuracyLabel(value) {
  const location = normalizeLocation(value)
  if (!location) return 'Không có thông tin độ chính xác'
  if (location.accuracyMeters < 1_000) {
    return `Sai số khoảng ${Math.round(location.accuracyMeters)} m`
  }
  const kilometers = location.accuracyMeters / 1_000
  return `Sai số khoảng ${kilometers < 10 ? kilometers.toFixed(1) : Math.round(kilometers)} km`
}
