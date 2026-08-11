import assert from 'node:assert/strict'
import test from 'node:test'
import {
  MAX_CHAT_MESSAGE_LENGTH,
  chatMessagePayload,
  chatErrorMessage,
  chatPeerId,
  mergeChatMessages,
  normalizeChatContent,
  optimisticChatMessage,
  optimisticLocationMessage,
  updateChatMessage,
} from '../src/chat/model.js'

const SENDER_ID = '00000000-0000-0000-0000-000000000001'
const FRIEND_ID = '00000000-0000-0000-0000-000000000002'
const CLIENT_MESSAGE_ID = '10000000-0000-0000-0000-000000000001'

test('normalizes chat content and enforces the shared message limit', () => {
  assert.equal(normalizeChatContent('  Xin chào bạn  '), 'Xin chào bạn')
  assert.equal(normalizeChatContent('x'.repeat(MAX_CHAT_MESSAGE_LENGTH)).length, MAX_CHAT_MESSAGE_LENGTH)
  assert.throws(() => normalizeChatContent('   '), /2000/)
  assert.throws(() => normalizeChatContent('x'.repeat(MAX_CHAT_MESSAGE_LENGTH + 1)), /2000/)
})

test('identifies the other participant only for messages in the current conversation', () => {
  const outgoing = { senderId: SENDER_ID, recipientId: FRIEND_ID }
  const incoming = { senderId: FRIEND_ID, recipientId: SENDER_ID }

  assert.equal(chatPeerId(outgoing, SENDER_ID), FRIEND_ID)
  assert.equal(chatPeerId(incoming, SENDER_ID), FRIEND_ID)
  assert.equal(chatPeerId(outgoing, 'unrelated-user'), null)
  assert.equal(chatPeerId(null, SENDER_ID), null)
  assert.equal(chatPeerId(outgoing, null), null)
})

test('creates a normalized optimistic message with retry metadata', () => {
  const message = optimisticChatMessage({
    clientMessageId: CLIENT_MESSAGE_ID,
    senderId: SENDER_ID,
    recipientId: FRIEND_ID,
    content: '  Hẹn bạn lúc 19:00 nhé  ',
    createdAt: '2026-08-10T12:00:00.000Z',
  })

  assert.deepEqual(message, {
    id: null,
    clientMessageId: CLIENT_MESSAGE_ID,
    senderId: SENDER_ID,
    recipientId: FRIEND_ID,
    messageType: 'TEXT',
    content: 'Hẹn bạn lúc 19:00 nhé',
    createdAt: '2026-08-10T12:00:00.000Z',
    status: 'SENDING',
    error: null,
  })
})

test('creates and serializes a structured optimistic location message', () => {
  const message = optimisticLocationMessage({
    clientMessageId: CLIENT_MESSAGE_ID,
    senderId: SENDER_ID,
    recipientId: FRIEND_ID,
    location: {
      latitude: 21.0278,
      longitude: 105.8342,
      accuracyMeters: 12.5,
      capturedAt: '2026-08-10T12:00:00.000Z',
    },
    createdAt: '2026-08-10T12:00:01.000Z',
  })

  assert.deepEqual(message, {
    id: null,
    clientMessageId: CLIENT_MESSAGE_ID,
    senderId: SENDER_ID,
    recipientId: FRIEND_ID,
    messageType: 'LOCATION',
    content: null,
    latitude: 21.0278,
    longitude: 105.8342,
    accuracyMeters: 12.5,
    createdAt: '2026-08-10T12:00:01.000Z',
    status: 'SENDING',
    error: null,
  })
  assert.deepEqual(chatMessagePayload(message), {
    recipientId: FRIEND_ID,
    clientMessageId: CLIENT_MESSAGE_ID,
    messageType: 'LOCATION',
    content: null,
    latitude: 21.0278,
    longitude: 105.8342,
    accuracyMeters: 12.5,
  })
  assert.throws(() => optimisticLocationMessage({
    clientMessageId: CLIENT_MESSAGE_ID,
    senderId: SENDER_ID,
    recipientId: FRIEND_ID,
    location: { latitude: 21, longitude: 105 },
  }), /Tọa độ/)
})

test('server acknowledgement wins over an optimistic copy regardless of merge order', () => {
  const optimistic = {
    id: null,
    clientMessageId: CLIENT_MESSAGE_ID,
    senderId: SENDER_ID,
    recipientId: FRIEND_ID,
    content: 'Xin chào',
    createdAt: '2026-08-10T12:00:00.000Z',
    status: 'FAILED',
    error: 'Temporary socket error',
  }
  const acknowledged = {
    id: '20000000-0000-0000-0000-000000000001',
    clientMessageId: CLIENT_MESSAGE_ID,
    senderId: SENDER_ID,
    recipientId: FRIEND_ID,
    content: 'Xin chào',
    createdAt: '2026-08-10T12:00:01.000Z',
  }

  for (const messages of [
    mergeChatMessages(optimistic, acknowledged),
    mergeChatMessages(acknowledged, optimistic),
  ]) {
    assert.equal(messages.length, 1)
    assert.equal(messages[0].id, acknowledged.id)
    assert.equal(messages[0].createdAt, acknowledged.createdAt)
    assert.equal(messages[0].status, 'SENT')
    assert.equal(messages[0].error, null)
  }
})

test('deduplicates server messages, ignores unkeyed payloads, and sorts chronologically', () => {
  const later = {
    id: 'message-2',
    senderId: FRIEND_ID,
    recipientId: SENDER_ID,
    content: 'Tin sau',
    createdAt: '2026-08-10T12:02:00.000Z',
  }
  const earlier = {
    id: 'message-1',
    senderId: SENDER_ID,
    recipientId: FRIEND_ID,
    content: 'Tin trước',
    createdAt: '2026-08-10T12:01:00.000Z',
  }

  const messages = mergeChatMessages(later, { content: 'invalid' }, earlier, { ...later })

  assert.deepEqual(messages.map((message) => message.id), ['message-1', 'message-2'])
  assert.ok(messages.every((message) => message.status === 'SENT'))
})

test('keeps equal client ids from opposite senders isolated', () => {
  const outgoing = {
    id: 'message-outgoing',
    clientMessageId: CLIENT_MESSAGE_ID,
    senderId: SENDER_ID,
    recipientId: FRIEND_ID,
    content: 'Tin của tôi',
    createdAt: '2026-08-10T12:01:00.000Z',
  }
  const incoming = {
    id: 'message-incoming',
    clientMessageId: CLIENT_MESSAGE_ID,
    senderId: FRIEND_ID,
    recipientId: SENDER_ID,
    content: 'Tin của bạn',
    createdAt: '2026-08-10T12:02:00.000Z',
  }

  const messages = mergeChatMessages(outgoing, incoming)
  assert.equal(messages.length, 2)
  const updated = updateChatMessage(messages, CLIENT_MESSAGE_ID, { status: 'FAILED' }, SENDER_ID)
  assert.equal(updated.find((message) => message.senderId === SENDER_ID).status, 'FAILED')
  assert.equal(updated.find((message) => message.senderId === FRIEND_ID).status, 'SENT')
})

test('updates only the requested optimistic message and localizes socket errors', () => {
  const messages = [
    { clientMessageId: 'client-1', status: 'SENDING' },
    { clientMessageId: 'client-2', status: 'SENDING' },
  ]
  const updated = updateChatMessage(messages, 'client-2', { status: 'FAILED', error: 'offline' })

  assert.strictEqual(updated[0], messages[0])
  assert.deepEqual(updated[1], { clientMessageId: 'client-2', status: 'FAILED', error: 'offline' })
  assert.notEqual(chatErrorMessage({ errorCode: 'CHAT_REQUIRES_FRIENDSHIP' }, 'fallback'), 'fallback')
  assert.match(chatErrorMessage({ errorCode: 'INVALID_CHAT_LOCATION' }), /Tọa độ/)
  assert.equal(chatErrorMessage({ message: 'Socket unavailable' }), 'Socket unavailable')
  assert.equal(chatErrorMessage({}, 'History unavailable'), 'History unavailable')
})
