import {
  Clock3,
  ExternalLink,
  Globe2,
  MapPin,
  Phone,
  Star,
  X,
} from 'lucide-react'
import { useEffect } from 'react'
import { categoryLabel } from './PlaceCard.jsx'

const DAY_NAMES = ['Thứ hai', 'Thứ ba', 'Thứ tư', 'Thứ năm', 'Thứ sáu', 'Thứ bảy', 'Chủ nhật']

function safeUrl(value) {
  if (!value) return null
  try {
    const url = new URL(value.startsWith('http') ? value : `https://${value}`)
    return ['http:', 'https:'].includes(url.protocol) ? url.href : null
  } catch {
    return null
  }
}

function timeLabel(value) {
  return value ? value.slice(0, 5) : '—'
}

export default function PlaceDetailModal({ detail, loading, error, onClose }) {
  useEffect(() => {
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const closeOnEscape = (event) => event.key === 'Escape' && onClose()
    window.addEventListener('keydown', closeOnEscape)
    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', closeOnEscape)
    }
  }, [onClose])

  const website = safeUrl(detail?.website)
  const mapUrl = detail?.lat != null && detail?.lng != null
    ? `https://www.google.com/maps/search/?api=1&query=${detail.lat},${detail.lng}`
    : null
  const photos = detail?.photos
    ?.map((photo) => ({ ...photo, safeUrl: safeUrl(photo.url) }))
    .filter((photo) => photo.safeUrl)
    .slice(0, 4) || []

  return (
    <div className="modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section className="detail-modal" role="dialog" aria-modal="true" aria-label="Chi tiết địa điểm">
        <button className="icon-button modal-close" type="button" onClick={onClose} aria-label="Đóng">
          <X size={20} />
        </button>

        {loading && (
          <div className="detail-state">
            <span className="loader" />
            <p>Đang mở cánh cửa đến địa điểm này…</p>
          </div>
        )}

        {error && !loading && (
          <div className="detail-state detail-state--error">
            <span>!</span>
            <h2>Chưa thể tải địa điểm</h2>
            <p>{error}</p>
          </div>
        )}

        {detail && !loading && (
          <>
            <div className={`detail-hero category-${detail.category?.toLowerCase() || 'other'}`}>
              {photos[0] ? <img src={photos[0].safeUrl} alt={detail.name} /> : <div className="detail-hero__pattern" />}
              <div className="detail-hero__overlay" />
              <div className="detail-hero__content">
                <span className="eyebrow eyebrow--light">{categoryLabel(detail.category)}</span>
                <h2>{detail.name}</h2>
                <p><MapPin size={16} /> {detail.address || detail.district || 'Hà Nội'}</p>
              </div>
            </div>

            <div className="detail-content">
              <div className="detail-facts">
                <div>
                  <span className="detail-facts__label">Đánh giá</span>
                  <strong><Star size={17} fill="currentColor" /> {detail.rating?.toFixed(1) || 'Mới'}</strong>
                  <small>{detail.userRatingsTotal?.toLocaleString('vi-VN') || 0} lượt</small>
                </div>
                <div>
                  <span className="detail-facts__label">Khu vực</span>
                  <strong>{detail.district || 'Hà Nội'}</strong>
                  <small>{detail.dataSource?.replaceAll('_', ' ') || 'Local data'}</small>
                </div>
                <div>
                  <span className="detail-facts__label">Trạng thái</span>
                  <strong>{detail.operatingStatus === 'OPERATIONAL' ? 'Đang hoạt động' : detail.operatingStatus || 'Đang cập nhật'}</strong>
                  <small>{detail.enriched ? 'Đã xác minh dữ liệu' : 'Dữ liệu cộng đồng'}</small>
                </div>
              </div>

              <div className="detail-actions">
                {mapUrl && <a className="button button--primary" href={mapUrl} target="_blank" rel="noreferrer"><MapPin size={17} /> Chỉ đường</a>}
                {website && <a className="button button--secondary" href={website} target="_blank" rel="noreferrer"><Globe2 size={17} /> Website</a>}
                {detail.phone && <a className="button button--ghost" href={`tel:${detail.phone}`}><Phone size={17} /> {detail.phone}</a>}
              </div>

              {(detail.openingHours?.length > 0 || detail.openingHoursRaw) && (
                <section className="detail-section">
                  <div className="section-heading section-heading--compact">
                    <div><span className="eyebrow">Thời gian</span><h3>Giờ mở cửa</h3></div>
                    <Clock3 size={21} />
                  </div>
                  {detail.openingHours?.length > 0 ? (
                    <div className="opening-list">
                      {detail.openingHours.map((hour, index) => (
                        <div key={`${hour.dayOfWeek}-${index}`}>
                          <span>{DAY_NAMES[(hour.dayOfWeek || 1) - 1] || `Ngày ${hour.dayOfWeek}`}</span>
                          <strong>{timeLabel(hour.openTime)} – {timeLabel(hour.closeTime)}{hour.crossesMidnight ? ' (+1)' : ''}</strong>
                        </div>
                      ))}
                    </div>
                  ) : <p className="muted-copy">{detail.openingHoursRaw}</p>}
                </section>
              )}

              {photos.length > 1 && (
                <section className="detail-section">
                  <div className="section-heading section-heading--compact"><div><span className="eyebrow">Khoảnh khắc</span><h3>Hình ảnh</h3></div></div>
                  <div className="photo-grid">
                    {photos.map((photo, index) => <img key={`${photo.safeUrl}-${index}`} src={photo.safeUrl} alt={`${detail.name} ${index + 1}`} loading="lazy" />)}
                  </div>
                </section>
              )}

              {detail.reviews?.length > 0 && (
                <section className="detail-section">
                  <div className="section-heading section-heading--compact"><div><span className="eyebrow">Người đã ghé</span><h3>Đánh giá gần đây</h3></div></div>
                  <div className="review-list">
                    {detail.reviews.slice(0, 4).map((review, index) => (
                      <blockquote key={`${review.authorName}-${index}`}>
                        <div><strong>{review.authorName || 'Khách ghé thăm'}</strong><span><Star size={13} fill="currentColor" /> {review.rating || '—'}</span></div>
                        <p>{review.text || review.relativeTimeDescription || 'Một trải nghiệm đáng nhớ.'}</p>
                      </blockquote>
                    ))}
                  </div>
                </section>
              )}

              {(website || mapUrl) && (
                <div className="detail-footer-note">
                  Dữ liệu được tổng hợp để bạn tham khảo trước chuyến đi.
                  {website && <a href={website} target="_blank" rel="noreferrer"> Xem nguồn <ExternalLink size={13} /></a>}
                </div>
              )}
            </div>
          </>
        )}
      </section>
    </div>
  )
}
