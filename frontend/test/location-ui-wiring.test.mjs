import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

let vite
let ShareLocationDialog
let isCurrentLocationRequest
let requestBrowserPosition

test.before(async () => {
  vite = await createServer({
    root: process.cwd(),
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true },
  })
  ShareLocationDialog = (await vite.ssrLoadModule('/src/components/ShareLocationDialog.jsx')).default
  const locationHook = await vite.ssrLoadModule('/src/hooks/useCurrentLocation.js')
  isCurrentLocationRequest = locationHook.isCurrentLocationRequest
  requestBrowserPosition = locationHook.requestBrowserPosition
})

test.after(async () => {
  await vite?.close()
})

const friend = {
  friendshipId: 'friendship-1',
  user: { id: 'friend-1', fullName: 'Minh Anh', pinCode: 'RML-123456' },
}

test('location share dialog requires an explicit friend action and explains snapshot privacy', () => {
  const markup = renderToStaticMarkup(React.createElement(ShareLocationDialog, {
    open: true,
    onClose: () => {},
    friends: [friend],
    location: { latitude: 21.0278, longitude: 105.8342, accuracyMeters: 12 },
    connectionStatus: 'connected',
    onShare: () => {},
    onOpenFriends: () => {},
  }))

  assert.match(markup, /role="dialog"/)
  assert.match(markup, /21\.027800, 105\.834200/)
  assert.match(markup, /không phải theo dõi trực tiếp/)
  assert.match(markup, /Minh Anh/)
  assert.match(markup, /aria-label="Gửi vị trí cho Minh Anh"/)
  assert.doesNotMatch(markup, /<button(?=[^>]*aria-label="Gửi vị trí cho Minh Anh")(?=[^>]*disabled)/)

  const disconnectedMarkup = renderToStaticMarkup(React.createElement(ShareLocationDialog, {
    open: true,
    onClose: () => {},
    friends: [friend],
    location: { latitude: 21.0278, longitude: 105.8342, accuracyMeters: 12 },
    connectionStatus: 'reconnecting',
    onShare: () => {},
    onOpenFriends: () => {},
  }))
  assert.match(disconnectedMarkup, /Đang chờ kết nối tin nhắn realtime/)
  assert.match(disconnectedMarkup, /<button(?=[^>]*aria-label="Gửi vị trí cho Minh Anh")(?=[^>]*disabled)/)
})

test('location share dialog does not send a coordinate missing required accuracy', () => {
  const markup = renderToStaticMarkup(React.createElement(ShareLocationDialog, {
    open: true,
    onClose: () => {},
    friends: [friend],
    location: { latitude: 21.0278, longitude: 105.8342 },
    connectionStatus: 'connected',
    onShare: () => {},
    onOpenFriends: () => {},
  }))

  assert.match(markup, /Tọa độ không hợp lệ/)
  assert.match(markup, /<button(?=[^>]*aria-label="Gửi vị trí cho Minh Anh")(?=[^>]*disabled)/)
})

test('browser position adapter converts a synchronous geolocation exception into a rejection', async () => {
  const synchronousFailure = Object.assign(new Error('permission bridge failed'), { code: 1 })
  await assert.rejects(
    requestBrowserPosition({
      getCurrentPosition() {
        throw synchronousFailure
      },
    }, { timeout: 1 }),
    (error) => error === synchronousFailure,
  )
})

test('location request guard rejects unmounted, superseded, and cross-identity callbacks', () => {
  const request = { identity: 'user-a', generation: 4 }
  assert.equal(isCurrentLocationRequest(request, {
    mounted: true,
    identity: 'user-a',
    generation: 4,
  }), true)
  assert.equal(isCurrentLocationRequest(request, {
    mounted: true,
    identity: 'user-b',
    generation: 4,
  }), false)
  assert.equal(isCurrentLocationRequest(request, {
    mounted: true,
    identity: 'user-a',
    generation: 5,
  }), false)
  assert.equal(isCurrentLocationRequest(request, {
    mounted: false,
    identity: 'user-a',
    generation: 4,
  }), false)
})

test('application wires one-shot geolocation, sharing, typed chat, and map focus', async () => {
  const [appSource, mapSource, hookSource, panelSource] = await Promise.all([
    readFile(new URL('../src/App.jsx', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/AwsPlacesMap.jsx', import.meta.url), 'utf8'),
    readFile(new URL('../src/hooks/useCurrentLocation.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/ChatPanel.jsx', import.meta.url), 'utf8'),
  ])

  assert.match(appSource, /const sessionIdentity = session\?\.user\?\.id[\s\S]{0,160}session\?\.user\?\.email/)
  assert.match(appSource, /session\?\.user\?\.email[\s\S]{0,80}\|\| session\?\.accessToken/)
  assert.doesNotMatch(appSource, /session\?\.refreshToken/)
  assert.match(appSource, /const locationState = useCurrentLocation\(sessionIdentity\)/)
  assert.match(appSource, /previousSessionIdentityRef\.current === sessionIdentity/)
  assert.match(appSource, /setLocationShareOpen\(false\)[\s\S]{0,100}setFocusedLocation\(null\)/)
  assert.match(appSource, /sessionIdentityRef\.current !== requestedIdentity/)
  assert.match(appSource, /onShareLocation=\{handleOpenLocationShare\}/)
  assert.match(appSource, /focusedLocation=\{focusedLocation\}/)
  assert.match(appSource, /onOpenLocation=\{handleOpenSharedLocation\}/)
  assert.match(appSource, /chatState\.sendLocation\(friendId, location\)/)
  assert.match(appSource, /setViewMode\('map'\)/)

  assert.match(hookSource, /geolocation\.getCurrentPosition\(resolve, reject, options\)/)
  assert.doesNotMatch(hookSource, /watchPosition\(/)
  assert.match(hookSource, /if \(!forceFresh && isFreshLocation/)
  assert.match(hookSource, /enableHighAccuracy: true/)
  assert.match(hookSource, /mountedRef\.current = true/)
  assert.match(hookSource, /generationRef\.current \+= 1/)
  assert.match(hookSource, /request\?\.identity === current\?\.identity/)
  assert.match(hookSource, /resetIdentityRef\.current === currentIdentity/)
  assert.match(hookSource, /if \(!requestIsCurrent\(\)\) throw staleLocationRequestError\(\)/)

  assert.match(mapSource, /return \[location\.longitude, latitude\]/)
  assert.match(mapSource, /map-location-marker--\$\{kind\}/)
  assert.match(mapSource, /locationCameraModeRef\.current !== 'places'/)
  assert.match(mapSource, /validFocusedLocation[\s\S]{0,800}map\.flyTo/)

  assert.match(panelSource, /messageType === LOCATION_MESSAGE_TYPE/)
  assert.match(panelSource, /onOpenLocation\?\.\(message, selectedFriend\)/)
})
