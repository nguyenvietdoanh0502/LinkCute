import {
  ArrowLeft,
  ArrowRight,
  CalendarDays,
  ChevronDown,
  Compass,
  LayoutGrid,
  Map,
  MapPinned,
  MapPin,
  RotateCcw,
  Search,
  SlidersHorizontal,
  Sparkles,
  UserRound,
  UsersRound,
  X,
} from 'lucide-react'
import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from 'react'
import { api, getStoredSession, subscribeSession } from './api/client.js'
import AuthModal from './components/AuthModal.jsx'
import FriendsPanel from './components/FriendsPanel.jsx'
import ItineraryPanel from './components/ItineraryPanel.jsx'
import PlaceCard, { categoryLabel } from './components/PlaceCard.jsx'
import PlaceDetailModal from './components/PlaceDetailModal.jsx'
import SelectDropdown from './components/SelectDropdown.jsx'
import Toast from './components/Toast.jsx'
import { useDebouncedValue } from './hooks/useDebouncedValue.js'
import { useFriendships } from './hooks/useFriendships.js'
import { useItineraryPlans } from './hooks/useItineraryPlans.js'
import { usePlanSharing } from './hooks/usePlanSharing.js'

const CATEGORY_ICONS = {
  FOOD: '✦',
  CAFE: '☕',
  ENTERTAINMENT: '♫',
  CINEMA: '◉',
  SHOPPING: '◇',
  OTHER: '⌖',
}

const PAGE_SIZE = 12
const AwsPlacesMap = lazy(() => import('./components/AwsPlacesMap.jsx'))

function SkeletonCard() {
  return (
    <div className="place-card skeleton-card" aria-hidden="true">
      <div className="skeleton skeleton--visual" />
      <div className="place-card__body">
        <div className="skeleton skeleton--title" />
        <div className="skeleton skeleton--line" />
        <div className="skeleton skeleton--short" />
      </div>
    </div>
  )
}

function paginationItems(current, total) {
  if (total <= 5) return Array.from({ length: total }, (_, index) => index)
  const items = new Set([0, total - 1, current - 1, current, current + 1])
  return [...items].filter((item) => item >= 0 && item < total).sort((a, b) => a - b)
}

export default function App() {
  const [session, setSession] = useState(() => getStoredSession())
  const [authMode, setAuthMode] = useState(null)
  const [categories, setCategories] = useState([])
  const [districts, setDistricts] = useState([])
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState('')
  const [district, setDistrict] = useState('')
  const [openNow, setOpenNow] = useState(false)
  const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false)
  const [page, setPage] = useState(0)
  const [placePage, setPlacePage] = useState(null)
  const [viewMode, setViewMode] = useState('list')
  const [mapPlaces, setMapPlaces] = useState([])
  const [mapLoading, setMapLoading] = useState(false)
  const [mapPlacesError, setMapPlacesError] = useState('')
  const [requestVersion, setRequestVersion] = useState(0)
  const [loading, setLoading] = useState(true)
  const [placesError, setPlacesError] = useState('')
  const [selectedPlace, setSelectedPlace] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')
  const [toast, setToast] = useState(null)
  const [itineraryOpen, setItineraryOpen] = useState(false)
  const [friendsOpen, setFriendsOpen] = useState(false)
  const [pendingPlace, setPendingPlace] = useState(null)
  const [sharedActivePlanId, setSharedActivePlanId] = useState(null)
  const [resumeAfterAuth, setResumeAfterAuth] = useState(null)
  const [itineraryInitialSubview, setItineraryInitialSubview] = useState('plan')
  const debouncedSearch = useDebouncedValue(search, 450)
  const {
    plans: localPlans,
    activePlan: localActivePlan,
    storageError,
    createPlan: createLocalPlan,
    setActivePlan: setLocalActivePlan,
    updatePlan: updateLocalPlan,
    deletePlan: deleteLocalPlan,
    addPlace: addLocalPlace,
    removeItem: removeLocalItem,
    moveItem: moveLocalItem,
    updateItemTime: updateLocalItemTime,
    upsertOwnedRemotePlan,
    clearPlanServerLink,
  } = useItineraryPlans(session)
  const friendshipState = useFriendships(session)
  const planSharingState = usePlanSharing({
    session,
    localPlans,
    upsertOwnedRemotePlan,
    clearPlanServerLink,
  })
  const plans = planSharingState.plans
  const activePlan = useMemo(() => (
    (sharedActivePlanId
      ? planSharingState.memberPlans.find((plan) => plan.id === sharedActivePlanId)
      : null)
    || planSharingState.ownerPlans.find((plan) => plan.id === localActivePlan?.id)
    || planSharingState.ownerPlans[0]
    || planSharingState.memberPlans[0]
    || null
  ), [
    localActivePlan?.id,
    planSharingState.memberPlans,
    planSharingState.ownerPlans,
    sharedActivePlanId,
  ])
  const localAddTargetPlan = useMemo(() => (
    planSharingState.ownerPlans.find((plan) => plan.id === localActivePlan?.id)
    || planSharingState.ownerPlans[0]
    || null
  ), [localActivePlan?.id, planSharingState.ownerPlans])

  const showToast = useCallback((message, type = 'success') => {
    setToast({ message, type, id: `${Date.now()}-${Math.random()}` })
  }, [])
  const closeFriends = useCallback(() => setFriendsOpen(false), [])

  useEffect(() => subscribeSession(setSession), [])

  useEffect(() => {
    if (!session) {
      closeFriends()
      setSharedActivePlanId(null)
    }
  }, [closeFriends, session])

  useEffect(() => {
    if (!session || resumeAfterAuth !== 'plan-people') return
    setAuthMode(null)
    setResumeAfterAuth(null)
    setItineraryInitialSubview('people')
    setFriendsOpen(false)
    setItineraryOpen(true)
  }, [resumeAfterAuth, session])

  useEffect(() => {
    if (!sharedActivePlanId || planSharingState.loading) return
    if (planSharingState.memberPlans.some((plan) => plan.id === sharedActivePlanId)) return
    const timer = window.setTimeout(() => setSharedActivePlanId(null), 0)
    return () => window.clearTimeout(timer)
  }, [planSharingState.loading, planSharingState.memberPlans, sharedActivePlanId])

  useEffect(() => {
    if (storageError) showToast(storageError, 'error')
  }, [showToast, storageError])

  useEffect(() => {
    if (!planSharingState.syncError) return
    showToast(planSharingState.syncError, 'error')
    planSharingState.clearSyncError()
  }, [planSharingState, showToast])

  useEffect(() => {
    let active = true
    Promise.all([api.getCategories(), api.getDistricts()])
      .then(([categoryData, districtData]) => {
        if (!active) return
        setCategories(categoryData || [])
        setDistricts(districtData || [])
      })
      .catch((error) => {
        if (active) showToast(`Chưa tải được bộ lọc: ${error.message}`)
      })
    return () => { active = false }
  }, [showToast])

  useEffect(() => {
    let active = true
    setLoading(true)
    setPlacesError('')
    api.getPlaces({
      q: debouncedSearch.trim(),
      category,
      district,
      openNow: openNow || undefined,
      page,
      size: PAGE_SIZE,
    })
      .then((data) => {
        if (active) setPlacePage(data)
      })
      .catch((error) => {
        if (active) setPlacesError(error.message)
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [debouncedSearch, category, district, openNow, page, requestVersion])

  useEffect(() => setPage(0), [debouncedSearch, category, district, openNow])

  useEffect(() => {
    if (viewMode !== 'map') return undefined

    let active = true
    const abortController = new AbortController()
    setMapLoading(true)
    setMapPlacesError('')
    api.getMapPlaces({
      q: debouncedSearch.trim(),
      category,
      district,
      openNow: openNow || undefined,
    }, { signal: abortController.signal })
      .then((data) => {
        if (active) setMapPlaces(data || [])
      })
      .catch((error) => {
        if (active && error.name !== 'AbortError') setMapPlacesError(error.message)
      })
      .finally(() => {
        if (active) setMapLoading(false)
      })

    return () => {
      active = false
      abortController.abort()
    }
  }, [viewMode, debouncedSearch, category, district, openNow])

  const selectPlace = useCallback((id) => {
    setSelectedPlace(null)
    setDetailError('')
    setDetailLoading(true)
    api.getPlace(id)
      .then(setSelectedPlace)
      .catch((error) => setDetailError(error.message))
      .finally(() => setDetailLoading(false))
  }, [])

  const closeDetail = useCallback(() => {
    setSelectedPlace(null)
    setDetailError('')
    setDetailLoading(false)
  }, [])

  const closeItinerary = useCallback(() => {
    if (document.activeElement instanceof HTMLElement) {
      document.activeElement.blur()
    }
    setItineraryOpen(false)
    setPendingPlace(null)
    setSharedActivePlanId(null)
    setItineraryInitialSubview('plan')
  }, [])

  const handleSelectPlan = useCallback((planId) => {
    if (planSharingState.memberPlans.some((plan) => plan.id === planId)) {
      setSharedActivePlanId(planId)
      return
    }
    setSharedActivePlanId(null)
    setLocalActivePlan(planId)
  }, [planSharingState.memberPlans, setLocalActivePlan])

  const handleAddToPlan = useCallback((place) => {
    const result = addLocalPlace(place, localAddTargetPlan?.id)
    if (result.ok) {
      showToast(`Đã thêm ${place.name} vào ${localAddTargetPlan.name}.`)
      return
    }
    if (result.reason === 'duplicate') {
      showToast(`${place.name} đã có trong kế hoạch này.`, 'error')
      return
    }
    if (result.reason === 'invalid-place') {
      showToast('Địa điểm này chưa đủ thông tin để thêm vào kế hoạch.', 'error')
      return
    }

    setPendingPlace(place)
    closeDetail()
    closeFriends()
    setItineraryOpen(true)
    showToast('Tạo kế hoạch đầu tiên, LinkCute sẽ tự thêm địa điểm này.')
  }, [addLocalPlace, closeDetail, closeFriends, localAddTargetPlan, showToast])

  const handleCreatePlan = useCallback(({ name, date, initialPlace }) => {
    const placeToAdd = initialPlace || pendingPlace
    const planId = createLocalPlan({ name, date, initialPlace: placeToAdd })
    setSharedActivePlanId(null)
    setPendingPlace(null)
    showToast(placeToAdd
      ? `Đã tạo kế hoạch và thêm ${placeToAdd.name}.`
      : 'Đã tạo kế hoạch mới.')
    return planId
  }, [createLocalPlan, pendingPlace, showToast])

  const handleUpdatePlan = useCallback((planId, meta) => {
    if (activePlan?.accessRole === 'MEMBER') return
    updateLocalPlan(planId, meta)
  }, [activePlan?.accessRole, updateLocalPlan])

  const handleDeletePlan = useCallback(async (planId) => {
    const plan = plans.find((candidate) => candidate.id === planId)
    if (!plan || plan.accessRole === 'MEMBER') return
    const deleted = await planSharingState.deletePublishedPlan(plan)
    if (deleted === null || deleted === undefined) return false
    deleteLocalPlan(planId)
    setSharedActivePlanId(null)
    showToast(`Đã xóa “${plan.name}”.`)
    return true
  }, [deleteLocalPlan, planSharingState, plans, showToast])

  const handleRemoveItem = useCallback((planId, itemId) => {
    if (activePlan?.accessRole === 'MEMBER') return
    removeLocalItem(planId, itemId)
  }, [activePlan?.accessRole, removeLocalItem])

  const handleMoveItem = useCallback((planId, itemId, direction) => {
    if (activePlan?.accessRole === 'MEMBER') return
    moveLocalItem(planId, itemId, direction)
  }, [activePlan?.accessRole, moveLocalItem])

  const handleUpdateItemTime = useCallback((planId, itemId, startTime, endTime) => {
    if (activePlan?.accessRole === 'MEMBER') return
    updateLocalItemTime(planId, itemId, startTime, endTime)
  }, [activePlan?.accessRole, updateLocalItemTime])

  const isPlaceInSelectedPlan = useCallback(
    (placeId) => Boolean(localAddTargetPlan?.items?.some((item) => item.place.id === String(placeId))),
    [localAddTargetPlan],
  )

  const requireLoginForSharing = useCallback(() => {
    setResumeAfterAuth('plan-people')
    setItineraryOpen(false)
    setAuthMode('login')
    showToast('Đăng nhập để mời bạn bè vào kế hoạch.')
  }, [showToast])

  const openFriendsFromPlan = useCallback(() => {
    setItineraryOpen(false)
    setItineraryInitialSubview('plan')
    setFriendsOpen(true)
  }, [])

  const clearFilters = () => {
    setSearch('')
    setCategory('')
    setDistrict('')
    setOpenNow(false)
  }

  const hasFilters = Boolean(search || category || district || openNow)
  const total = placePage?.totalElements || 0
  const pageItems = useMemo(() => paginationItems(placePage?.number || 0, placePage?.totalPages || 0), [placePage])

  return (
    <div className="app-shell">
      <header className="site-header">
        <a className="brand" href="#top" aria-label="LinkCute - Trang chủ">
          <span className="brand__mark">L</span>
          <span>link<span>cute</span></span>
        </a>

        <nav className="desktop-nav" aria-label="Điều hướng chính">
          <a href="#discover">Khám phá</a>
          <a href="#categories">Danh mục</a>
          <a href="#about">Về LinkCute</a>
        </nav>

        <div className="header-actions">
          <button
            className="itinerary-launcher"
            type="button"
            onClick={() => {
              setPendingPlace(null)
              setFriendsOpen(false)
              setItineraryInitialSubview('plan')
              setItineraryOpen(true)
            }}
            aria-haspopup="dialog"
            aria-label={`Mở kế hoạch${activePlan ? ` ${activePlan.name}, ${activePlan.items.length} địa điểm` : ''}${planSharingState.incomingCount ? `, ${planSharingState.incomingCount} lời mời mới` : ''}`}
          >
            <CalendarDays size={17} />
            <span className="itinerary-launcher__label">Kế hoạch</span>
            {activePlan?.items.length > 0 && <span className="itinerary-launcher__badge">{activePlan.items.length}</span>}
            {planSharingState.incomingCount > 0 && (
              <span className="itinerary-launcher__invite-badge">{planSharingState.incomingCount}</span>
            )}
          </button>
          {session && (
            <button
              className="friends-launcher"
              type="button"
              onClick={() => {
                setPendingPlace(null)
                setItineraryOpen(false)
                setFriendsOpen(true)
              }}
              aria-haspopup="dialog"
              aria-label={`Mở bạn bè${friendshipState.incomingRequests.length
                ? `, ${friendshipState.incomingRequests.length} lời mời đang chờ`
                : ''}`}
            >
              <UsersRound size={17} aria-hidden="true" />
              <span className="friends-launcher__label">Bạn bè</span>
              {friendshipState.incomingRequests.length > 0 && (
                <span className="friends-launcher__badge">{friendshipState.incomingRequests.length}</span>
              )}
            </button>
          )}
          <button className="account-button" type="button" onClick={() => setAuthMode(session ? 'account' : 'login')}>
            {session ? (
              <>
                <span className="account-button__avatar">{session.user?.fullName?.charAt(0)?.toUpperCase() || 'L'}</span>
                <span>{session.user?.fullName?.split(' ').slice(-1)[0] || 'Tài khoản'}</span>
              </>
            ) : <><UserRound size={17} /><span>Đăng nhập</span></>}
          </button>
        </div>
      </header>

      <main id="top">
        <section className="hero">
          <div className="hero__grain" />
          <div className="hero__copy">
            <span className="eyebrow eyebrow--hero"><Sparkles size={14} /> Hanoi, curated with care</span>
            <h1>Một Hà Nội<br />rất <em>riêng</em> đang chờ.</h1>
            <p>Từ một quán cà phê nép trong ngõ nhỏ đến bữa tối đáng nhớ — tìm địa điểm hợp đúng tâm trạng của bạn.</p>

            <form className="hero-search" onSubmit={(event) => { event.preventDefault(); document.querySelector('#discover')?.scrollIntoView({ behavior: 'smooth' }) }}>
              <Search size={21} />
              <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Bạn muốn đi đâu, ăn gì?" aria-label="Tìm kiếm địa điểm" />
              {search && <button className="search-clear" type="button" onClick={() => setSearch('')} aria-label="Xóa tìm kiếm"><X size={17} /></button>}
              <button className="hero-search__submit" type="submit">Khám phá <ArrowRight size={17} /></button>
            </form>

            <div className="hero__quick-links">
              <span>Thử ngay:</span>
              {['CAFE', 'FOOD', 'ENTERTAINMENT'].map((item) => (
                <button key={item} type="button" onClick={() => { setCategory(item); document.querySelector('#discover')?.scrollIntoView({ behavior: 'smooth' }) }}>
                  {categoryLabel(item)}
                </button>
              ))}
            </div>
          </div>

          <div className="hero__visual" aria-hidden="true">
            <div className="hero-postcard hero-postcard--back">
              <span>36°</span>
              <strong>HÀ NỘI</strong>
            </div>
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
            <div><strong>{districts.length || '12+'}</strong><span>quận huyện</span></div>
            <div><strong>∞</strong><span>câu chuyện</span></div>
          </div>
        </section>

        <section className="category-strip" id="categories">
          <div className="category-strip__intro">
            <span className="eyebrow">Chọn một cảm hứng</span>
            <h2>Hôm nay mình đi đâu?</h2>
          </div>
          <div className="category-pills">
            <button className={!category ? 'active' : ''} type="button" onClick={() => setCategory('')}><span>⌁</span>Tất cả</button>
            {categories.map((item) => (
              <button className={category === item.category ? 'active' : ''} key={item.category} type="button" onClick={() => setCategory(item.category)}>
                <span>{CATEGORY_ICONS[item.category] || '⌖'}</span>{item.name || categoryLabel(item.category)}
                {item.count != null && <small>{item.count.toLocaleString('vi-VN')}</small>}
              </button>
            ))}
          </div>
        </section>

        <section className="discover-section" id="discover">
          <div className="section-heading">
            <div>
              <span className="eyebrow">Khám phá gần đây</span>
              <h2>{hasFilters ? 'Kết quả dành cho bạn' : 'Những nơi đáng ghé'}</h2>
              <p>{(viewMode === 'map' ? mapLoading : loading) ? 'Đang tìm những lựa chọn phù hợp…' : `${total.toLocaleString('vi-VN')} địa điểm được tìm thấy`}</p>
            </div>
            <div className="discover-actions">
              <div className="view-switch" role="group" aria-label="Chế độ hiển thị">
                <button className={viewMode === 'list' ? 'active' : ''} type="button" onClick={() => setViewMode('list')}><LayoutGrid size={16} /> Danh sách</button>
                <button className={viewMode === 'map' ? 'active' : ''} type="button" onClick={() => setViewMode('map')}><MapPinned size={16} /> Bản đồ</button>
              </div>
              <button className="mobile-filter-button" type="button" onClick={() => setMobileFiltersOpen((value) => !value)}>
                <SlidersHorizontal size={17} /> Bộ lọc <ChevronDown size={15} />
              </button>
            </div>
          </div>

          <div className={`filter-bar ${mobileFiltersOpen ? 'filter-bar--open' : ''}`}>
            <label className="filter-search">
              <Search size={18} />
              <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tên hoặc địa chỉ…" aria-label="Tìm theo tên hoặc địa chỉ" />
            </label>
            <SelectDropdown
              className="filter-dropdown"
              value={district}
              onChange={setDistrict}
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
              <input type="checkbox" checked={openNow} onChange={(event) => setOpenNow(event.target.checked)} />
              <span className="toggle-filter__track"><span /></span>
              Đang mở cửa
            </label>
            {hasFilters && <button className="clear-filters" type="button" onClick={clearFilters}><RotateCcw size={15} /> Đặt lại</button>}
          </div>

          {viewMode === 'list' && placesError && (
            <div className="empty-state">
              <span className="empty-state__symbol">!</span>
              <h3>Chưa kết nối được với LinkCute</h3>
              <p>{placesError}</p>
              <button className="button button--secondary" type="button" onClick={() => setRequestVersion((current) => current + 1)}>Thử lại</button>
            </div>
          )}

          {viewMode === 'list' && !placesError && (
            <div className="place-grid">
              {loading
                ? Array.from({ length: 8 }, (_, index) => <SkeletonCard key={index} />)
                : placePage?.content?.map((place) => (
                  <PlaceCard
                    key={place.id}
                    place={place}
                    onSelect={selectPlace}
                    onAddToPlan={handleAddToPlan}
                    isInPlan={isPlaceInSelectedPlan(place.id)}
                  />
                ))}
            </div>
          )}

          {viewMode === 'map' && (
            <Suspense fallback={<div className="map-setup-state"><span className="loader" /><p>Đang chuẩn bị trình hiển thị bản đồ…</p></div>}>
              <AwsPlacesMap
                places={mapPlaces}
                loading={mapLoading}
                error={mapPlacesError}
                onSelect={selectPlace}
                filters={{
                  search,
                  category,
                  district,
                  openNow,
                  categories,
                  districts,
                  onSearchChange: setSearch,
                  onCategoryChange: setCategory,
                  onDistrictChange: setDistrict,
                  onOpenNowChange: setOpenNow,
                }}
              />
            </Suspense>
          )}

          {viewMode === 'list' && !loading && !placesError && placePage?.content?.length === 0 && (
            <div className="empty-state">
              <span className="empty-state__symbol"><Map size={29} /></span>
              <h3>Chưa tìm thấy nơi phù hợp</h3>
              <p>Thử đổi từ khóa, khu vực hoặc bỏ bớt bộ lọc nhé.</p>
              <button className="button button--secondary" type="button" onClick={clearFilters}>Xóa bộ lọc</button>
            </div>
          )}

          {viewMode === 'list' && !loading && placePage?.totalPages > 1 && (
            <nav className="pagination" aria-label="Phân trang">
              <button type="button" disabled={placePage.first} onClick={() => setPage((current) => current - 1)} aria-label="Trang trước"><ArrowLeft size={17} /></button>
              {pageItems.map((item, index) => (
                <span key={item} className="pagination__item-wrap">
                  {index > 0 && item - pageItems[index - 1] > 1 && <i>…</i>}
                  <button className={placePage.number === item ? 'active' : ''} type="button" onClick={() => setPage(item)} aria-current={placePage.number === item ? 'page' : undefined}>{item + 1}</button>
                </span>
              ))}
              <button type="button" disabled={placePage.last} onClick={() => setPage((current) => current + 1)} aria-label="Trang sau"><ArrowRight size={17} /></button>
            </nav>
          )}
        </section>

        <section className="about-band" id="about">
          <div className="about-band__mark">LC</div>
          <div><span className="eyebrow eyebrow--light">Made for curious souls</span><h2>Không chỉ tìm một nơi.<br />Hãy tìm một <em>cảm giác.</em></h2></div>
          <p>LinkCute kết nối dữ liệu địa điểm từ backend với một trải nghiệm khám phá nhẹ nhàng, nhanh chóng và gần gũi.</p>
        </section>
      </main>

      <footer className="site-footer">
        <a className="brand brand--footer" href="#top"><span className="brand__mark">L</span><span>link<span>cute</span></span></a>
        <p>Demo ReactJS sử dụng LinkCute Backend API.</p>
        <a href="https://linkcute.duckdns.org" target="_blank" rel="noreferrer">API production <span className="online-dot" /> Online</a>
      </footer>

      <ItineraryPanel
        open={itineraryOpen}
        onClose={closeItinerary}
        plans={plans}
        activePlan={activePlan}
        pendingPlace={pendingPlace}
        storageError={storageError}
        session={session}
        friendshipState={friendshipState}
        planSharingState={planSharingState}
        initialSubview={itineraryInitialSubview}
        showToast={showToast}
        onRequireLogin={requireLoginForSharing}
        onOpenFriends={openFriendsFromPlan}
        onCreatePlan={handleCreatePlan}
        onSelectPlan={handleSelectPlan}
        onSelectAcceptedPlan={setSharedActivePlanId}
        onUpdatePlan={handleUpdatePlan}
        onDeletePlan={handleDeletePlan}
        onRemoveItem={handleRemoveItem}
        onMoveItem={handleMoveItem}
        onUpdateItemTime={handleUpdateItemTime}
      />
      <FriendsPanel
        open={friendsOpen}
        onClose={closeFriends}
        session={session}
        friendshipState={friendshipState}
        showToast={showToast}
      />
      {(selectedPlace || detailLoading || detailError) && (
        <PlaceDetailModal
          detail={selectedPlace}
          loading={detailLoading}
          error={detailError}
          onClose={closeDetail}
          onAddToPlan={handleAddToPlan}
          isInPlan={isPlaceInSelectedPlan(selectedPlace?.id)}
          activePlanName={localAddTargetPlan?.name}
        />
      )}
      {authMode && (
        <AuthModal
          session={session}
          initialMode={authMode}
          onClose={() => {
            setAuthMode(null)
            if (!getStoredSession()) setResumeAfterAuth(null)
          }}
          onOpenFriends={() => {
            setAuthMode(null)
            setItineraryOpen(false)
            setFriendsOpen(true)
          }}
          showToast={showToast}
        />
      )}
      {toast && <Toast key={toast.id} toast={toast} onClose={() => setToast(null)} />}
    </div>
  )
}
