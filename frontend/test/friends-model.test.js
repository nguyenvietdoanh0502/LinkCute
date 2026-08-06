import assert from 'node:assert/strict'
import test from 'node:test'
import {
  friendshipErrorMessage,
  isValidFriendPin,
  normalizeFriendPin,
  profileInitials,
  syncSearchRelationship,
} from '../src/friends/model.js'

test('normalizeFriendPin accepts common ways of entering a member code', () => {
  assert.equal(normalizeFriendPin(' rml-123456 '), 'RML-123456')
  assert.equal(normalizeFriendPin('rml123456'), 'RML-123456')
  assert.equal(normalizeFriendPin('123456'), 'RML-123456')
  assert.equal(normalizeFriendPin('RML-12 34 56'), 'RML-123456')
})

test('isValidFriendPin only accepts six digits after the RML prefix', () => {
  assert.equal(isValidFriendPin('RML-123456'), true)
  assert.equal(isValidFriendPin('123456'), true)
  assert.equal(isValidFriendPin('RML-12345'), false)
  assert.equal(isValidFriendPin('ABC-123456'), false)
  assert.equal(isValidFriendPin('RML-12345A'), false)
})

test('profileInitials uses the last two name parts and has a safe fallback', () => {
  assert.equal(profileInitials('Nguyễn Minh Anh'), 'MA')
  assert.equal(profileInitials('Lan'), 'L')
  assert.equal(profileInitials(''), 'L')
})

test('friendshipErrorMessage localizes known API errors and preserves useful fallbacks', () => {
  assert.equal(
    friendshipErrorMessage({ errorCode: 'FRIEND_REQUEST_ALREADY_EXISTS' }),
    'Lời mời kết bạn này đã tồn tại.',
  )
  assert.equal(
    friendshipErrorMessage({ status: 409 }),
    'Dữ liệu vừa thay đổi. Vui lòng tải lại và thử lần nữa.',
  )
  assert.equal(friendshipErrorMessage({ message: 'Custom error' }), 'Custom error')
})

test('syncSearchRelationship follows authoritative overview state and IDs', () => {
  const searchResult = {
    id: 'user-2',
    fullName: 'Minh Anh',
    relationshipStatus: 'NONE',
    friendshipId: null,
  }

  assert.deepEqual(
    syncSearchRelationship(searchResult, {
      friends: [],
      incomingRequests: [{ id: 'request-in', user: { id: 'user-2' } }],
      outgoingRequests: [],
    }),
    { ...searchResult, relationshipStatus: 'INCOMING_PENDING', friendshipId: 'request-in' },
  )
  assert.deepEqual(
    syncSearchRelationship(searchResult, {
      friends: [{ friendshipId: 'friendship-1', user: { id: 'user-2' } }],
      incomingRequests: [{ id: 'stale-request', user: { id: 'user-2' } }],
      outgoingRequests: [],
    }),
    { ...searchResult, relationshipStatus: 'FRIENDS', friendshipId: 'friendship-1' },
  )
  assert.deepEqual(
    syncSearchRelationship(
      { ...searchResult, relationshipStatus: 'OUTGOING_PENDING', friendshipId: 'old-request' },
      { friends: [], incomingRequests: [], outgoingRequests: [] },
    ),
    searchResult,
  )
})
