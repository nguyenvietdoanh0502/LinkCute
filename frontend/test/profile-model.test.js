import assert from 'node:assert/strict'
import test from 'node:test'
import {
  AVATAR_MAX_BYTES,
  avatarFileError,
  buildProfilePayload,
  calculateAge,
  genderLabel,
  profileFormFromUser,
} from '../src/profile/model.js'

test('calculateAge derives an approximate age from a valid birth year', () => {
  assert.equal(calculateAge(2000, 2026), 26)
  assert.equal(calculateAge('', 2026), null)
  assert.equal(calculateAge(1899, 2026), null)
  assert.equal(calculateAge(2027, 2026), null)
})

test('profileFormFromUser fills safe defaults for optional profile fields', () => {
  assert.deepEqual(profileFormFromUser({ fullName: 'Minh Anh', gender: 'FEMALE', birthYear: 2001 }), {
    fullName: 'Minh Anh',
    gender: 'FEMALE',
    birthYear: '2001',
    address: '',
  })
  assert.equal(profileFormFromUser({ gender: 'UNKNOWN' }).gender, 'UNSPECIFIED')
})

test('buildProfilePayload trims values and converts empty optional fields to null', () => {
  assert.deepEqual(buildProfilePayload({
    fullName: '  Nguyễn Minh Anh  ',
    gender: 'OTHER',
    birthYear: ' 1998 ',
    address: '  Cầu Giấy, Hà Nội  ',
  }, 2026), {
    fullName: 'Nguyễn Minh Anh',
    gender: 'OTHER',
    birthYear: 1998,
    address: 'Cầu Giấy, Hà Nội',
  })

  assert.deepEqual(buildProfilePayload({ fullName: 'An', gender: 'INVALID', birthYear: '', address: ' ' }, 2026), {
    fullName: 'An',
    gender: 'UNSPECIFIED',
    birthYear: null,
    address: null,
  })
})

test('buildProfilePayload rejects invalid names and birth years', () => {
  assert.throws(() => buildProfilePayload({ fullName: ' ', birthYear: '' }, 2026), /họ và tên/i)
  assert.throws(() => buildProfilePayload({ fullName: 'A', birthYear: '' }, 2026), /ít nhất 2 ký tự/i)
  assert.throws(() => buildProfilePayload({ fullName: 'An', birthYear: '1899' }, 2026), /1900/)
  assert.throws(() => buildProfilePayload({ fullName: 'An', birthYear: '2027' }, 2026), /2026/)
})

test('avatarFileError accepts only JPEG, PNG, or WebP up to 5 MiB', () => {
  assert.equal(avatarFileError({ type: 'image/png', size: AVATAR_MAX_BYTES }), '')
  assert.match(avatarFileError({ type: 'image/gif', size: 100 }), /JPEG, PNG hoặc WebP/)
  assert.match(avatarFileError({ type: 'image/webp', size: AVATAR_MAX_BYTES + 1 }), /5 MiB/)
})

test('genderLabel localizes supported values and safely falls back', () => {
  assert.equal(genderLabel('MALE'), 'Nam')
  assert.equal(genderLabel('FEMALE'), 'Nữ')
  assert.equal(genderLabel('UNKNOWN'), 'Chưa muốn tiết lộ')
})
