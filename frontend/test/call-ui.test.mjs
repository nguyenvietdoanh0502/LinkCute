import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

let vite
let CallOverlay
let formatCallDuration

test.before(async () => {
  vite = await createServer({
    root: process.cwd(),
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true },
  })
  const callUi = await vite.ssrLoadModule('/src/components/CallOverlay.jsx')
  CallOverlay = callUi.default
  formatCallDuration = callUi.formatCallDuration
})

test.after(async () => {
  await vite?.close()
})

const friend = {
  friendshipId: 'friendship-1',
  user: {
    id: 'friend-1',
    fullName: 'Minh Anh',
    avatarUrl: 'https://example.com/minh-anh.webp',
  },
}

function callState(overrides = {}) {
  return {
    status: 'idle',
    peer: null,
    error: '',
    isMuted: false,
    acceptCall: () => {},
    rejectCall: () => {},
    endCall: () => {},
    resetCall: () => {},
    toggleMute: () => {},
    remoteAudioRef: { current: null },
    ...overrides,
  }
}

test('idle call state renders no overlay', () => {
  const markup = renderToStaticMarkup(React.createElement(CallOverlay, {
    callState: callState(),
  }))

  assert.equal(markup, '')
})

test('incoming call is an accessible dialog with accept and reject controls', () => {
  const markup = renderToStaticMarkup(React.createElement(CallOverlay, {
    callState: callState({ status: 'incoming', peer: friend }),
  }))

  assert.match(markup, /role="dialog"/)
  assert.match(markup, /aria-modal="true"/)
  assert.match(markup, /Cuộc gọi đến/)
  assert.match(markup, /Minh Anh muốn nói chuyện với bạn/)
  assert.match(markup, /minh-anh\.webp/)
  assert.match(markup, /aria-label="Nhận cuộc gọi từ Minh Anh"/)
  assert.match(markup, /aria-label="Từ chối cuộc gọi từ Minh Anh"/)
  assert.match(markup, /<audio[^>]*autoplay=""[^>]*playsinline=""/i)
})

test('outgoing and active states expose only valid call controls', () => {
  const outgoingMarkup = renderToStaticMarkup(React.createElement(CallOverlay, {
    callState: callState({ status: 'outgoing', peer: friend.user }),
  }))
  assert.match(outgoingMarkup, /Đang chờ Minh Anh trả lời/)
  assert.match(outgoingMarkup, /aria-label="Kết thúc cuộc gọi với Minh Anh"/)
  assert.doesNotMatch(outgoingMarkup, /Nhận cuộc gọi/)
  assert.doesNotMatch(outgoingMarkup, /Tắt micrô/)

  const activeMarkup = renderToStaticMarkup(React.createElement(CallOverlay, {
    callState: callState({ status: 'active', peer: friend.user, isMuted: true }),
  }))
  assert.match(activeMarkup, /Cuộc gọi đang diễn ra/)
  assert.match(activeMarkup, /aria-label="Bật micrô"/)
  assert.match(activeMarkup, /aria-pressed="true"/)
  assert.match(activeMarkup, /aria-label="Thời lượng cuộc gọi 00:00"/)
  assert.match(activeMarkup, />00:00<\/time>/)
})

test('terminal states explain the outcome and provide a dismiss action', () => {
  const endedMarkup = renderToStaticMarkup(React.createElement(CallOverlay, {
    callState: callState({ status: 'ended', peer: friend.user, endReason: 'timeout' }),
  }))
  assert.match(endedMarkup, /Minh Anh không trả lời cuộc gọi/)
  assert.match(endedMarkup, /aria-label="Đóng thông báo cuộc gọi"/)

  const errorMarkup = renderToStaticMarkup(React.createElement(CallOverlay, {
    callState: callState({ status: 'error', peer: friend.user, error: new Error('Không có quyền dùng micrô') }),
  }))
  assert.match(errorMarkup, /role="alert"/)
  assert.match(errorMarkup, /Không có quyền dùng micrô/)
})

test('duration formatter remains stable beyond one hour', () => {
  assert.equal(formatCallDuration(0), '00:00')
  assert.equal(formatCallDuration(65), '01:05')
  assert.equal(formatCallDuration(3_661), '01:01:01')
})

test('call overlay source wires remote audio and every useCall action', async () => {
  const source = await readFile(new URL('../src/components/CallOverlay.jsx', import.meta.url), 'utf8')

  assert.match(source, /ref=\{callState\?\.remoteAudioRef\}/)
  assert.match(source, /autoPlay[\s\S]{0,40}playsInline/)
  assert.match(source, /onClick=\{callState\?\.acceptCall\}/)
  assert.match(source, /onClick=\{callState\?\.rejectCall\}/)
  assert.match(source, /onClick=\{callState\?\.endCall\}/)
  assert.match(source, /onClick=\{callState\?\.toggleMute\}/)
  assert.match(source, /callState\?\.resetCall/)
})

test('outgoing call keeps overflow usable without showing a scrollbar', async () => {
  const styles = await readFile(new URL('../src/styles.css', import.meta.url), 'utf8')

  assert.match(styles, /\.call-overlay--outgoing \.call-dialog\s*\{[^}]*scrollbar-width:\s*none/)
  assert.match(styles, /\.call-overlay--outgoing \.call-dialog::\-webkit-scrollbar\s*\{[^}]*display:\s*none/)
})
