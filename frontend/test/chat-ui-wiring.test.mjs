import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

let vite
let ChatPanel

test.before(async () => {
  vite = await createServer({
    root: process.cwd(),
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true },
  })
  ChatPanel = (await vite.ssrLoadModule('/src/components/ChatPanel.jsx')).default
})

test.after(async () => {
  await vite?.close()
})

function chatState(overrides = {}) {
  return {
    messagesByFriend: {},
    historyByFriend: {},
    unreadByFriend: {},
    totalUnread: 0,
    connectionStatus: 'connected',
    socketError: '',
    loadHistory: async () => null,
    loadOlder: async () => null,
    setActiveConversation: () => {},
    sendMessage: () => {},
    sendLocation: () => {},
    retryMessage: () => {},
    ...overrides,
  }
}

const friend = {
  friendshipId: 'friendship-1',
  user: {
    id: 'friend-1',
    fullName: 'Minh Anh',
    avatarUrl: 'https://example.com/minh-anh.webp',
  },
}

test('closed chat panel renders nothing', () => {
  const markup = renderToStaticMarkup(React.createElement(ChatPanel, {
    open: false,
    onClose: () => {},
    friends: [friend],
    currentUserId: 'user-1',
    chatState: chatState(),
    showToast: () => {},
  }))

  assert.equal(markup, '')
})

test('open chat panel exposes a modal and only the supplied friendship contacts', () => {
  const markup = renderToStaticMarkup(React.createElement(ChatPanel, {
    open: true,
    onClose: () => {},
    friends: [friend],
    initialFriendId: friend.user.id,
    currentUserId: 'user-1',
    chatState: chatState({ unreadByFriend: { [friend.user.id]: 4 } }),
    showToast: () => {},
  }))

  assert.match(markup, /role="dialog"/)
  assert.match(markup, /aria-modal="true"/)
  assert.match(markup, /class="chat-friend-list"/)
  assert.match(markup, /Minh Anh/)
  assert.match(markup, /minh-anh\.webp/)
  assert.match(markup, />4<\/em>/)
  assert.doesNotMatch(markup, /Pending Request User/)
})

test('empty friendship list renders the no-conversation state without a composer', () => {
  const markup = renderToStaticMarkup(React.createElement(ChatPanel, {
    open: true,
    onClose: () => {},
    friends: [],
    currentUserId: 'user-1',
    chatState: chatState(),
    showToast: () => {},
  }))

  assert.match(markup, /class="chat-empty"/)
  assert.doesNotMatch(markup, /class="chat-composer"/)
  assert.doesNotMatch(markup, /<textarea/)
})

test('renders a structured location message as a map card without relying on content', () => {
  const locationMessage = {
    id: 'message-location-1',
    clientMessageId: 'client-location-1',
    senderId: friend.user.id,
    recipientId: 'user-1',
    messageType: 'LOCATION',
    content: null,
    latitude: 21.0278,
    longitude: 105.8342,
    accuracyMeters: 18,
    createdAt: '2026-08-10T12:00:00.000Z',
  }
  const markup = renderToStaticMarkup(React.createElement(ChatPanel, {
    open: true,
    onClose: () => {},
    friends: [friend],
    initialFriendId: friend.user.id,
    currentUserId: 'user-1',
    chatState: chatState({ messagesByFriend: { [friend.user.id]: [locationMessage] } }),
    showToast: () => {},
    onOpenLocation: () => {},
  }))

  assert.match(markup, /Vị trí được chia sẻ/)
  assert.match(markup, /Sai số khoảng 18 m/)
  assert.match(markup, /Mở trên bản đồ/)
  assert.doesNotMatch(markup, />null</)

  const invalidMarkup = renderToStaticMarkup(React.createElement(ChatPanel, {
    open: true,
    onClose: () => {},
    friends: [friend],
    initialFriendId: friend.user.id,
    currentUserId: 'user-1',
    chatState: chatState({
      messagesByFriend: {
        [friend.user.id]: [{ ...locationMessage, id: 'invalid-location', accuracyMeters: null }],
      },
    }),
    showToast: () => {},
    onOpenLocation: () => {},
  }))
  assert.match(invalidMarkup, /Vị trí không hợp lệ/)
  assert.doesNotMatch(invalidMarkup, /Mở trên bản đồ/)
})

test('application wiring limits chat contacts to friends and connects unread/open-chat state', async () => {
  const [appSource, friendsSource, panelSource, hookSource] = await Promise.all([
    readFile(new URL('../src/App.jsx', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/FriendsPanel.jsx', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/ChatPanel.jsx', import.meta.url), 'utf8'),
    readFile(new URL('../src/hooks/useChat.js', import.meta.url), 'utf8'),
  ])

  assert.match(appSource, /const chatState = useChat\(session\)/)
  assert.match(appSource, /className="chat-launcher"[\s\S]{0,700}visibleChatUnread/)
  assert.match(appSource, /<FriendsPanel[\s\S]{0,400}onOpenChat=\{openChat\}/)
  assert.match(appSource, /<ChatPanel[\s\S]{0,400}friends=\{friendshipState\.friends\}/)
  assert.match(friendsSource, /const handleMessage = \(friend\) => onOpenChat\?\.\(friend\)/)

  assert.match(panelSource, /chatState\.setActiveConversation\(selectedFriendId\)/)
  assert.match(panelSource, /chatState\.loadHistory\(selectedFriendId\)/)
  assert.match(panelSource, /chatState\.loadOlder\(selectedFriendId\)/)
  assert.match(panelSource, /chatState\.retryMessage\(selectedFriendId, message\.clientMessageId\)/)
  assert.match(panelSource, /maxLength=\{MAX_CHAT_MESSAGE_LENGTH\}/)
  assert.match(panelSource, /chatState\.connectionStatus !== 'connected'/)

  assert.match(hookSource, /const socket = createChatSocket\(/)
  assert.match(hookSource, /socket\.deactivate\(\)\.catch\(\(\) => \{\}\)/)
  assert.match(hookSource, /api\.getChatHistory\([\s\S]{0,160}size: 50/)
  assert.match(hookSource, /socketRef\.current\.send\(chatMessagePayload\(optimistic\)\)/)
  assert.match(hookSource, /const sendLocation = useCallback/)
  assert.match(hookSource, /socketRef\.current\.send\(chatMessagePayload\(message\)\)/)
})
