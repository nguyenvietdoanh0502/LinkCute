import { ArrowRight, Compass, Search, Sparkles, X } from 'lucide-react'
import { categoryLabel } from './PlaceCard.jsx'

export default function Hero({ search, onSearchChange, onSelectCategory, total, districtCount }) {
  return (
    <section className="hero">
      <div className="hero__copy">
        <span className="eyebrow eyebrow--hero"><Sparkles size={14} /> Hanoi, curated with care</span>
        <h1>Một Hà Nội<br />rất <em>riêng</em> đang chờ.</h1>
        <p>Từ một quán cà phê nép trong ngõ nhỏ đến bữa tối đáng nhớ — tìm địa điểm hợp đúng tâm trạng của bạn.</p>

        <form className="hero-search" onSubmit={(event) => { event.preventDefault(); document.querySelector('#discover')?.scrollIntoView({ behavior: 'smooth' }) }}>
          <Search size={21} />
          <input value={search} onChange={(event) => onSearchChange(event.target.value)} placeholder="Bạn muốn đi đâu, ăn gì?" aria-label="Tìm kiếm địa điểm" />
          {search && <button className="search-clear" type="button" onClick={() => onSearchChange('')} aria-label="Xóa tìm kiếm"><X size={17} /></button>}
          <button className="hero-search__submit" type="submit">Khám phá <ArrowRight size={17} /></button>
        </form>

        <div className="hero__quick-links">
          <span>Thử ngay:</span>
          {['CAFE', 'FOOD', 'ENTERTAINMENT'].map((item) => (
            <button key={item} type="button" onClick={() => { onSelectCategory(item); document.querySelector('#discover')?.scrollIntoView({ behavior: 'smooth' }) }}>
              {categoryLabel(item)}
            </button>
          ))}
        </div>
      </div>

      <div className="hero__visual" aria-hidden="true">
        <div className="hero-postcard hero-postcard--front">
          <div className="hero-sun" />
          <div className="hero-skyline"><i /><i /><i /><i /><i /></div>
          <span className="hero-postcard__number">01</span>
          <p>small streets<br />big stories</p>
        </div>
        <div className="hero-stamp"><Compass size={31} /><span>local<br />picks</span></div>
      </div>

      <div className="hero__stats">
        <div><strong>{total ? total.toLocaleString('vi-VN') : '65K+'}</strong><span>địa điểm</span></div>
        <div><strong>{districtCount || '12+'}</strong><span>quận huyện</span></div>
        <div><strong>∞</strong><span>câu chuyện</span></div>
      </div>
    </section>
  )
}
