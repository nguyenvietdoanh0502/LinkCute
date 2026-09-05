import {
  ArrowLeft,
  ArrowRight,
  ChevronDown,
  LayoutGrid,
  Map,
  MapPinned,
  SlidersHorizontal,
} from 'lucide-react'
import { lazy, Suspense } from 'react'
import FilterBar from './FilterBar.jsx'
import PlaceGrid from './PlaceGrid.jsx'

const AwsPlacesMap = lazy(() => import('./AwsPlacesMap.jsx'))

export default function DiscoverSection({
  viewMode,
  onViewModeChange,
  mobileFiltersOpen,
  onToggleMobileFilters,
  search,
  onSearchChange,
  category,
  onCategoryChange,
  district,
  onDistrictChange,
  districts,
  categories,
  openNow,
  onOpenNowChange,
  onClearFilters,
  placesError,
  onRetry,
  loading,
  placePage,
  pageItems,
  onPageChange,
  onSelectPlace,
  onAddToPlan,
  isInPlan,
  mapPlaces,
  mapLoading,
  mapError,
  userLocation,
  locationStatus,
  locationError,
  onLocate,
  onShareLocation,
  focusedLocation,
  onClearFocusedLocation,
  theme,
}) {
  const hasFilters = Boolean(search || category || district || openNow)
  const total = placePage?.totalElements || 0

  return (
    <section className="discover-section" id="discover">
      <div className="section-heading">
        <div>
          <span className="eyebrow">Khám phá gần đây</span>
          <h2>{hasFilters ? 'Kết quả dành cho bạn' : 'Những nơi đáng ghé'}</h2>
          <p>{(viewMode === 'map' ? mapLoading : loading) ? 'Đang tìm những lựa chọn phù hợp…' : `${total.toLocaleString('vi-VN')} địa điểm được tìm thấy`}</p>
        </div>
        <div className="discover-actions">
          <div className="view-switch" role="group" aria-label="Chế độ hiển thị">
            <button className={viewMode === 'list' ? 'active' : ''} type="button" onClick={() => onViewModeChange('list')}><LayoutGrid size={16} /> Danh sách</button>
            <button className={viewMode === 'map' ? 'active' : ''} type="button" onClick={() => onViewModeChange('map')}><MapPinned size={16} /> Bản đồ</button>
          </div>
          <button className="mobile-filter-button" type="button" onClick={onToggleMobileFilters}>
            <SlidersHorizontal size={17} /> Bộ lọc <ChevronDown size={15} />
          </button>
        </div>
      </div>

      <FilterBar
        mobileFiltersOpen={mobileFiltersOpen}
        search={search}
        onSearchChange={onSearchChange}
        district={district}
        onDistrictChange={onDistrictChange}
        districts={districts}
        openNow={openNow}
        onOpenNowChange={onOpenNowChange}
        hasFilters={hasFilters}
        onClearFilters={onClearFilters}
      />

      {viewMode === 'list' && placesError && (
        <div className="empty-state">
          <span className="empty-state__symbol">!</span>
          <h3>Chưa kết nối được với LinkCute</h3>
          <p>{placesError}</p>
          <button className="button button--secondary" type="button" onClick={onRetry}>Thử lại</button>
        </div>
      )}

      {viewMode === 'list' && !placesError && (
        <PlaceGrid
          loading={loading}
          places={placePage?.content}
          onSelect={onSelectPlace}
          onAddToPlan={onAddToPlan}
          isInPlan={isInPlan}
        />
      )}

      {viewMode === 'map' && (
        <Suspense fallback={<div className="map-setup-state"><span className="loader" /><p>Đang chuẩn bị trình hiển thị bản đồ…</p></div>}>
          <AwsPlacesMap
            places={mapPlaces}
            loading={mapLoading}
            error={mapError}
            onSelect={onSelectPlace}
            userLocation={userLocation}
            locationStatus={locationStatus}
            locationError={locationError}
            onLocate={onLocate}
            onShareLocation={onShareLocation}
            focusedLocation={focusedLocation}
            onClearFocusedLocation={onClearFocusedLocation}
            theme={theme}
            filters={{
              search,
              category,
              district,
              openNow,
              categories,
              districts,
              onSearchChange,
              onCategoryChange,
              onDistrictChange,
              onOpenNowChange,
            }}
          />
        </Suspense>
      )}

      {viewMode === 'list' && !loading && !placesError && placePage?.content?.length === 0 && (
        <div className="empty-state">
          <span className="empty-state__symbol"><Map size={29} /></span>
          <h3>Chưa tìm thấy nơi phù hợp</h3>
          <p>Thử đổi từ khóa, khu vực hoặc bỏ bớt bộ lọc nhé.</p>
          <button className="button button--secondary" type="button" onClick={onClearFilters}>Xóa bộ lọc</button>
        </div>
      )}

      {viewMode === 'list' && !loading && placePage?.totalPages > 1 && (
        <nav className="pagination" aria-label="Phân trang">
          <button type="button" disabled={placePage.first} onClick={() => onPageChange((current) => current - 1)} aria-label="Trang trước"><ArrowLeft size={17} /></button>
          {pageItems.map((item, index) => (
            <span key={item} className="pagination__item-wrap">
              {index > 0 && item - pageItems[index - 1] > 1 && <i>…</i>}
              <button className={placePage.number === item ? 'active' : ''} type="button" onClick={() => onPageChange(item)} aria-current={placePage.number === item ? 'page' : undefined}>{item + 1}</button>
            </span>
          ))}
          <button type="button" disabled={placePage.last} onClick={() => onPageChange((current) => current + 1)} aria-label="Trang sau"><ArrowRight size={17} /></button>
        </nav>
      )}
    </section>
  )
}
