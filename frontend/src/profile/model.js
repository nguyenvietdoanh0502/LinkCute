export const AVATAR_MAX_BYTES = 5 * 1024 * 1024
export const AVATAR_ALLOWED_TYPES = Object.freeze([
  'image/jpeg',
  'image/png',
  'image/webp',
])

export const GENDER_OPTIONS = Object.freeze([
  { value: 'UNSPECIFIED', label: 'Chưa muốn tiết lộ' },
  { value: 'MALE', label: 'Nam' },
  { value: 'FEMALE', label: 'Nữ' },
  { value: 'OTHER', label: 'Khác' },
])

const GENDER_LABELS = Object.fromEntries(GENDER_OPTIONS.map(({ value, label }) => [value, label]))
const MIN_BIRTH_YEAR = 1900

export function genderLabel(gender) {
  return GENDER_LABELS[gender] || GENDER_LABELS.UNSPECIFIED
}

export function calculateAge(birthYear, currentYear = new Date().getFullYear()) {
  if (birthYear === null || birthYear === undefined || birthYear === '') return null
  const parsedYear = Number(birthYear)
  if (!Number.isInteger(parsedYear) || parsedYear < MIN_BIRTH_YEAR || parsedYear > currentYear) return null
  return currentYear - parsedYear
}

export function profileFormFromUser(user) {
  return {
    fullName: user?.fullName || '',
    gender: GENDER_LABELS[user?.gender] ? user.gender : 'UNSPECIFIED',
    birthYear: user?.birthYear === null || user?.birthYear === undefined ? '' : String(user.birthYear),
    address: user?.address || '',
  }
}

export function buildProfilePayload(form, currentYear = new Date().getFullYear()) {
  const fullName = String(form?.fullName || '').trim()
  const address = String(form?.address || '').trim()
  const gender = GENDER_LABELS[form?.gender] ? form.gender : 'UNSPECIFIED'
  const rawBirthYear = String(form?.birthYear ?? '').trim()

  if (!fullName) throw new Error('Vui lòng nhập họ và tên.')
  if (fullName.length < 2) throw new Error('Họ và tên phải có ít nhất 2 ký tự.')
  if (fullName.length > 100) throw new Error('Họ và tên không được vượt quá 100 ký tự.')
  if (address.length > 255) throw new Error('Địa chỉ không được vượt quá 255 ký tự.')

  let birthYear = null
  if (rawBirthYear) {
    birthYear = Number(rawBirthYear)
    if (!Number.isInteger(birthYear) || birthYear < MIN_BIRTH_YEAR || birthYear > currentYear) {
      throw new Error(`Năm sinh phải nằm trong khoảng ${MIN_BIRTH_YEAR}–${currentYear}.`)
    }
  }

  return {
    fullName,
    gender,
    birthYear,
    address: address || null,
  }
}

export function avatarFileError(file) {
  if (!file) return 'Vui lòng chọn một ảnh đại diện.'
  if (!AVATAR_ALLOWED_TYPES.includes(file.type)) return 'Ảnh đại diện chỉ hỗ trợ định dạng JPEG, PNG hoặc WebP.'
  if (file.size > AVATAR_MAX_BYTES) return 'Ảnh đại diện không được lớn hơn 5 MiB.'
  return ''
}
