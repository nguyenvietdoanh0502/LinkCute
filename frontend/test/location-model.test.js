import assert from 'node:assert/strict'
import test from 'node:test'
import {
  chatMessageType,
  geolocationErrorMessage,
  isFreshLocation,
  locationAccuracyLabel,
  locationFromMessage,
  normalizeLocation,
} from '../src/location/model.js'

test('normalizes browser and message coordinate shapes against the backend contract', () => {
  assert.deepEqual(normalizeLocation({
    lat: '21.0278',
    lng: '105.8342',
    accuracy: '15.5',
    timestamp: Date.UTC(2026, 7, 10, 12),
  }), {
    latitude: 21.0278,
    longitude: 105.8342,
    accuracyMeters: 15.5,
    capturedAt: '2026-08-10T12:00:00.000Z',
  })

  assert.equal(normalizeLocation({ latitude: 90, longitude: 180, accuracyMeters: 1 })?.latitude, 90)
  assert.equal(normalizeLocation({ latitude: -90, longitude: -180, accuracyMeters: 1 })?.longitude, -180)
  assert.equal(normalizeLocation({ latitude: 90.01, longitude: 105, accuracyMeters: 1 }), null)
  assert.equal(normalizeLocation({ latitude: 21, longitude: 180.01, accuracyMeters: 1 }), null)
  assert.equal(normalizeLocation({ latitude: 21, longitude: 105 }), null)
  assert.equal(normalizeLocation({ latitude: 21, longitude: 105, accuracyMeters: -1 }), null)
  assert.equal(normalizeLocation({ latitude: 21, longitude: 105, accuracyMeters: 1_000_001 }), null)
  assert.equal(normalizeLocation({ latitude: Number.NaN, longitude: 105, accuracyMeters: 1 }), null)
})

test('parses only valid typed location messages and treats legacy messages as text', () => {
  const message = {
    messageType: 'LOCATION',
    content: null,
    latitude: 21.0278,
    longitude: 105.8342,
    accuracyMeters: 8,
  }
  assert.deepEqual(locationFromMessage(message), {
    latitude: 21.0278,
    longitude: 105.8342,
    accuracyMeters: 8,
    capturedAt: null,
  })
  assert.equal(locationFromMessage({ ...message, accuracyMeters: null }), null)
  assert.equal(locationFromMessage({ content: 'Tin nhắn cũ' }), null)
  assert.equal(chatMessageType({ content: 'Tin nhắn cũ' }), 'TEXT')
  assert.equal(chatMessageType({ messageType: 'location' }), 'LOCATION')
})

test('checks cached location age without accepting future or missing timestamps', () => {
  const now = Date.UTC(2026, 7, 10, 12, 0, 30)
  const location = {
    latitude: 21,
    longitude: 105,
    accuracyMeters: 10,
    capturedAt: new Date(now - 20_000).toISOString(),
  }
  assert.equal(isFreshLocation(location, 30_000, now), true)
  assert.equal(isFreshLocation(location, 10_000, now), false)
  assert.equal(isFreshLocation({ ...location, capturedAt: null }, 30_000, now), false)
  assert.equal(isFreshLocation({ ...location, capturedAt: new Date(now + 2_000).toISOString() }, 30_000, now), false)
})

test('localizes geolocation failures and formats accuracy', () => {
  assert.match(geolocationErrorMessage({ code: 1 }), /từ chối quyền/)
  assert.match(geolocationErrorMessage({ code: 2 }), /bật GPS/)
  assert.match(geolocationErrorMessage({ code: 3 }), /Quá thời gian/)
  assert.match(geolocationErrorMessage({ code: 'GEOLOCATION_INSECURE' }), /HTTPS/)
  assert.match(geolocationErrorMessage({ code: 'GEOLOCATION_UNSUPPORTED' }), /không hỗ trợ/)
  assert.equal(locationAccuracyLabel({ latitude: 21, longitude: 105, accuracyMeters: 0 }), 'Sai số khoảng 0 m')
  assert.equal(locationAccuracyLabel({ latitude: 21, longitude: 105, accuracyMeters: 14.6 }), 'Sai số khoảng 15 m')
  assert.equal(locationAccuracyLabel({ latitude: 21, longitude: 105, accuracyMeters: 1_250 }), 'Sai số khoảng 1.3 km')
})
