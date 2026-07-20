import { ArrowUpRight, MapPin, Star } from 'lucide-react'
import { useState } from 'react'

const CATEGORY_LABELS = {
  FOOD: 'Ẩm thực',
  CAFE: 'Cà phê',
  ENTERTAINMENT: 'Giải trí',
  CINEMA: 'Rạp phim',
  SHOPPING: 'Mua sắm',
  OTHER: 'Khám phá',
}

const CATEGORY_SYMBOLS = {
  FOOD: '✦',
  CAFE: '☕',
  ENTERTAINMENT: '♪',
  CINEMA: '◉',
  SHOPPING: '◇',
  OTHER: '⌖',
}

export function categoryLabel(category) {
  return CATEGORY_LABELS[category] || category || 'Khám phá'
}

export default function PlaceCard({ place, onSelect }) {
  const [imageFailed, setImageFailed] = useState(false)
  const hasImage = place.photoUrl && !imageFailed

  const open = () => onSelect(place.id)

  return (
    <article
      className="place-card"
      tabIndex="0"
      role="button"
      onClick={open}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') open()
      }}
      aria-label={`Xem chi tiết ${place.name}`}
    >
      <div className={`place-card__visual category-${place.category?.toLowerCase() || 'other'}`}>
        {hasImage ? (
          <img src={place.photoUrl} alt="" loading="lazy" onError={() => setImageFailed(true)} />
        ) : (
          <div className="place-card__fallback" aria-hidden="true">
            <span>{CATEGORY_SYMBOLS[place.category] || '⌖'}</span>
            <small>linkcute pick</small>
          </div>
        )}

        <span className="place-card__category">{categoryLabel(place.category)}</span>
        <span className="place-card__open-icon"><ArrowUpRight size={17} /></span>
      </div>

      <div className="place-card__body">
        <div className="place-card__title-row">
          <h3>{place.name}</h3>
          {place.rating != null && (
            <span className="rating"><Star size={14} fill="currentColor" /> {place.rating.toFixed(1)}</span>
          )}
        </div>

        <p className="place-card__address">
          <MapPin size={15} />
          <span>{place.address || place.district || 'Hà Nội'}</span>
        </p>

        <div className="place-card__meta">
          <span>{place.district || 'Hà Nội'}</span>
          <span className="dot" />
          <span>{place.userRatingsTotal ? `${place.userRatingsTotal.toLocaleString('vi-VN')} đánh giá` : 'Điểm đến mới'}</span>
        </div>
      </div>
    </article>
  )
}
