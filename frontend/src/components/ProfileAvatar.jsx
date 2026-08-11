import { useEffect, useState } from 'react'
import { profileInitials } from '../friends/model.js'

export default function ProfileAvatar({ user, className = 'profile-avatar', src, alt }) {
  const imageUrl = src === undefined ? user?.avatarUrl : src
  const [imageFailed, setImageFailed] = useState(false)

  useEffect(() => setImageFailed(false), [imageUrl])

  return (
    <span className={className}>
      {imageUrl && !imageFailed ? (
        <img
          src={imageUrl}
          alt={alt ?? `Ảnh đại diện của ${user?.fullName || 'thành viên LinkCute'}`}
          onError={() => setImageFailed(true)}
        />
      ) : profileInitials(user?.fullName || user?.email)}
    </span>
  )
}
