import { categoryLabel } from './PlaceCard.jsx'

const CATEGORY_ICONS = {
  FOOD: '✦',
  CAFE: '☕',
  ENTERTAINMENT: '♫',
  CINEMA: '◉',
  SHOPPING: '◇',
  OTHER: '⌖',
}

export default function CategoryStrip({ categories, category, onSelectCategory }) {
  return (
    <section className="category-strip" id="categories">
      <div className="category-strip__intro">
        <span className="eyebrow">Chọn một cảm hứng</span>
        <h2>Hôm nay mình đi đâu?</h2>
      </div>
      <div className="category-pills">
        <button className={!category ? 'active' : ''} type="button" onClick={() => onSelectCategory('')}><span>⌁</span>Tất cả</button>
        {categories.map((item) => (
          <button className={category === item.category ? 'active' : ''} key={item.category} type="button" onClick={() => onSelectCategory(item.category)}>
            <span>{CATEGORY_ICONS[item.category] || '⌖'}</span>{item.name || categoryLabel(item.category)}
            {item.count != null && <small>{item.count.toLocaleString('vi-VN')}</small>}
          </button>
        ))}
      </div>
    </section>
  )
}
