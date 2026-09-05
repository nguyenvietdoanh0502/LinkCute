import { MapPin, RotateCcw, Search } from 'lucide-react'
import SelectDropdown from './SelectDropdown.jsx'

export default function FilterBar({
  mobileFiltersOpen,
  search,
  onSearchChange,
  district,
  onDistrictChange,
  districts,
  openNow,
  onOpenNowChange,
  hasFilters,
  onClearFilters,
}) {
  return (
    <div className={`filter-bar ${mobileFiltersOpen ? 'filter-bar--open' : ''}`}>
      <label className="filter-search">
        <Search size={18} />
        <input value={search} onChange={(event) => onSearchChange(event.target.value)} placeholder="Tên hoặc địa chỉ…" aria-label="Tìm theo tên hoặc địa chỉ" />
      </label>
      <SelectDropdown
        className="filter-dropdown"
        value={district}
        onChange={onDistrictChange}
        icon={<MapPin size={17} />}
        ariaLabel="Chọn quận huyện"
        searchable
        searchPlaceholder="Tìm quận, huyện…"
        options={[
          { value: '', label: 'Mọi khu vực' },
          ...districts.map((item) => ({
            value: item.district,
            label: item.district,
            count: item.count,
          })),
        ]}
      />
      <label className="toggle-filter">
        <input type="checkbox" checked={openNow} onChange={(event) => onOpenNowChange(event.target.checked)} />
        <span className="toggle-filter__track"><span /></span>
        Đang mở cửa
      </label>
      {hasFilters && <button className="clear-filters" type="button" onClick={onClearFilters}><RotateCcw size={15} /> Đặt lại</button>}
    </div>
  )
}
