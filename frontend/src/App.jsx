import {
  CalendarDays,
  MessageCircle,
  Moon,
  Sun,
  UserRound,
  UsersRound,
  X,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api, getStoredSession, subscribeSession } from './api/client.js'
import AppFooter from './components/AppFooter.jsx'
import AuthModal from './components/AuthModal.jsx'
import CallOverlay from './components/CallOverlay.jsx'
import CategoryStrip from './components/CategoryStrip.jsx'
import ChatPanel from './components/ChatPanel.jsx'
import DiscoverSection from './components/DiscoverSection.jsx'
import FriendsPanel from './components/FriendsPanel.jsx'
import Hero from './components/Hero.jsx'
import ItineraryPanel from './components/ItineraryPanel.jsx'
import PlaceDetailModal from './components/PlaceDetailModal.jsx'
import ProfileAvatar from './components/ProfileAvatar.jsx'
import ShareLocationDialog from './components/ShareLocationDialog.jsx'
import SiteHeader from './components/SiteHeader.jsx'
import Toast from './components/Toast.jsx'
import { useDebouncedValue } from './hooks/useDebouncedValue.js'
import { useChat } from './hooks/useChat.js'
import { useCall } from './hooks/useCall.js'
import { useCurrentLocation } from './hooks/useCurrentLocation.js'
import { useFriendships } from './hooks/useFriendships.js'
import { useItineraryPlans } from './hooks/useItineraryPlans.js'
import { usePlanSharing } from './hooks/usePlanSharing.js'
import { useTheme } from './hooks/useTheme.js'
import { locationFromMessage } from './location/model.js'

const PAGE_SIZE = 12

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
  const [viewMode, setViewMode] = useState('map')
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
  const [chatOpen, setChatOpen] = useState(false)
  const [chatInitialFriendId, setChatInitialFriendId] = useState(null)
  const [locationShareOpen, setLocationShareOpen] = useState(false)
  const [focusedLocation, setFocusedLocation] = useState(null)
  const [pendingPlace, setPendingPlace] = useState(null)
  const [sharedActivePlanId, setSharedActivePlanId] = useState(null)
  const [resumeAfterAuth, setResumeAfterAuth] = useState(null)
  const [itineraryInitialSubview, setItineraryInitialSubview] = useState('plan')
  const debouncedSearch = useDebouncedValue(search, 450)
  const sessionIdentity = session?.user?.id
    || session?.user?.email
    || session?.accessToken
    || ''
  const sessionIdentityRef = useRef(sessionIdentity)
  const previousSessionIdentityRef = useRef(sessionIdentity)
  sessionIdentityRef.current = sessionIdentity
  const locationState = useCurrentLocation(sessionIdentity)
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
  const chatState = useChat(session)
  const callState = useCall(session)
  const { theme, toggle: toggleTheme } = useTheme()
  const callPeer = useMemo(() => {
    const peerId = callState.peer?.id || callState.peer?.userId
    if (!peerId) return callState.peer
    return friendshipState.friends.find((friend) => friend.user?.id === peerId)?.user
      || callState.peer
  }, [callState.peer, friendshipState.friends])
  const visibleCallState = useMemo(() => (
    callPeer === callState.peer ? callState : { ...callState, peer: callPeer }
  ), [callPeer, callState])
  const canStartCall = callState.status === 'idle'
    && callState.connectionStatus === 'connected'
  const visibleChatUnread = useMemo(() => friendshipState.friends.reduce(
    (total, friend) => total + Number(chatState.unreadByFriend[friend.user?.id] || 0),
    0,
  ), [chatState.unreadByFriend, friendshipState.friends])
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
  const handleStartCall = useCallback(async (friend) => {
    setFriendsOpen(false)
    setChatOpen(false)
    setItineraryOpen(false)
    setLocationShareOpen(false)
    setSelectedPlace(null)
    setDetailError('')
    setDetailLoading(false)
    try {
      await callState.startCall(friend)
    } catch (error) {
      showToast(error?.message || 'Không thể bắt đầu cuộc gọi.', 'error')
    }
  }, [callState.startCall, showToast])
  const closeFriends = useCallback(() => setFriendsOpen(false), [])
  const closeChat = useCallback(() => setChatOpen(false), [])
  const closeLocationShare = useCallback(() => setLocationShareOpen(false), [])
  const clearFocusedLocation = useCallback(() => setFocusedLocation(null), [])
  const openChat = useCallback((friend) => {
    const friendId = friend?.user?.id || friend?.id || null
    setChatInitialFriendId(friendId)
    setPendingPlace(null)
    setItineraryOpen(false)
    setFriendsOpen(false)
    setLocationShareOpen(false)
    setChatOpen(true)
  }, [])

  useEffect(() => subscribeSession(setSession), [])

  useEffect(() => {
    if (previousSessionIdentityRef.current === sessionIdentity) return
    previousSessionIdentityRef.current = sessionIdentity
    closeFriends()
    closeChat()
    setChatInitialFriendId(null)
    setLocationShareOpen(false)
    setFocusedLocation(null)
    setSharedActivePlanId(null)
  }, [closeChat, closeFriends, sessionIdentity])

  useEffect(() => {
    if (!session || !resumeAfterAuth) return
    if (resumeAfterAuth === 'plan-people') {
      setAuthMode(null)
      setResumeAfterAuth(null)
      setItineraryInitialSubview('people')
      setFriendsOpen(false)
      setChatOpen(false)
      setItineraryOpen(true)
      return
    }
    if (resumeAfterAuth === 'share-location') {
      const requestedIdentity = sessionIdentity
      setAuthMode(null)
      setResumeAfterAuth(null)
      locationState.requestLocation()
        .then((location) => {
          if (!location || !requestedIdentity || sessionIdentityRef.current !== requestedIdentity) return
          setLocationShareOpen(true)
        })
        .catch((locationFailure) => {
          if (locationFailure?.name === 'AbortError') return
          if (sessionIdentityRef.current !== requestedIdentity) return
          showToast(locationFailure.message, 'error')
        })
    }
  }, [locationState.requestLocation, resumeAfterAuth, session, sessionIdentity, showToast])

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

  const handleLocateSelf = useCallback(() => {
    locationState.clearError()
    return locationState.requestLocation()
      .catch((locationFailure) => {
        if (locationFailure?.name !== 'AbortError') {
          showToast(locationFailure.message, 'error')
        }
        throw locationFailure
      })
  }, [locationState.clearError, locationState.requestLocation, showToast])

  const handleOpenLocationShare = useCallback(async () => {
    if (!session) {
      setResumeAfterAuth('share-location')
      setAuthMode('login')
      showToast('Đăng nhập để chia sẻ vị trí với bạn bè.')
      return null
    }

    const requestedIdentity = sessionIdentity
    try {
      const location = await locationState.requestLocation()
      if (!requestedIdentity || sessionIdentityRef.current !== requestedIdentity) return null
      setLocationShareOpen(true)
      return location
    } catch (locationFailure) {
      if (locationFailure?.name === 'AbortError') return null
      if (sessionIdentityRef.current !== requestedIdentity) return null
      showToast(locationFailure.message, 'error')
      return null
    }
  }, [locationState.requestLocation, session, sessionIdentity, showToast])

  const handleShareLocation = useCallback((friend, location) => {
    const friendId = friend?.user?.id
    const clientMessageId = chatState.sendLocation(friendId, location)
    showToast(`Vị trí đang được gửi cho ${friend?.user?.fullName || 'bạn bè'}.`)
    return clientMessageId
  }, [chatState, showToast])

  const handleOpenSharedLocation = useCallback((message, friend) => {
    const location = locationFromMessage(message)
    if (!location) {
      showToast('Tin nhắn này không chứa tọa độ hợp lệ.', 'error')
      return
    }

    const ownMessage = message.senderId === session?.user?.id
    const friendName = friend?.user?.fullName || 'bạn bè'
    setFocusedLocation({
      ...location,
      focusKey: message.id || `${message.senderId}:${message.clientMessageId}`,
      label: ownMessage ? 'Vị trí bạn đã chia sẻ' : `Vị trí ${friendName} chia sẻ`,
      sharedAt: message.createdAt,
    })
    setLocationShareOpen(false)
    setItineraryOpen(false)
    setFriendsOpen(false)
    closeChat()
    closeDetail()
    setViewMode('map')
    window.requestAnimationFrame(() => {
      document.querySelector('#discover')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  }, [closeChat, closeDetail, session?.user?.id, showToast])

  const openFriendsFromLocationShare = useCallback(() => {
    setLocationShareOpen(false)
    setItineraryOpen(false)
    setChatOpen(false)
    setFriendsOpen(true)
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

  useEffect(() => {
    if (callState.status !== 'incoming') return
    setAuthMode(null)
    setResumeAfterAuth(null)
    setLocationShareOpen(false)
    closeFriends()
    closeChat()
    closeItinerary()
    closeDetail()
  }, [callState.status, closeChat, closeDetail, closeFriends, closeItinerary])

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
    setChatOpen(false)
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
  const homeTab = viewMode === 'map' ? 'map' : 'list'

  return (
    <div className="app-shell">
      <SiteHeader
        activeTab={homeTab}
        onNavigate={(tab) => {
          if (tab === 'map') {
            setViewMode('map')
            window.requestAnimationFrame(() => {
              document.querySelector('#top')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
            })
            return
          }
          if (viewMode !== 'list') setViewMode('list')
          const target = tab === 'categories' ? '#categories' : tab === 'about' ? '#about' : '#discover'
          window.requestAnimationFrame(() => {
            window.requestAnimationFrame(() => {
              document.querySelector(target)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
            })
          })
        }}
        actions={
        <>
          <button
            className="itinerary-launcher"
            type="button"
            onClick={() => {
              setPendingPlace(null)
              setFriendsOpen(false)
              setChatOpen(false)
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
              className="chat-launcher"
              type="button"
              onClick={() => openChat(null)}
              aria-haspopup="dialog"
              aria-label={`Mở tin nhắn${visibleChatUnread ? `, ${visibleChatUnread} tin nhắn mới` : ''}`}
            >
              <MessageCircle size={17} aria-hidden="true" />
              <span className="chat-launcher__label">Tin nhắn</span>
              {visibleChatUnread > 0 && (
                <span className="chat-launcher__badge">{visibleChatUnread > 99 ? '99+' : visibleChatUnread}</span>
              )}
            </button>
          )}
          {session && (
            <button
              className="friends-launcher"
              type="button"
              onClick={() => {
                setPendingPlace(null)
                setItineraryOpen(false)
                setChatOpen(false)
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
          <button
            className="theme-toggle"
            type="button"
            onClick={toggleTheme}
            aria-label={theme === 'dark' ? 'Chuyển sang giao diện sáng' : 'Chuyển sang giao diện tối'}
            title={theme === 'dark' ? 'Giao diện tối' : 'Giao diện sáng'}
          >
            {theme === 'dark' ? <Sun size={17} /> : <Moon size={17} />}
          </button>
          <button className="account-button" type="button" onClick={() => setAuthMode(session ? 'account' : 'login')}>
            {session ? (
              <>
                <ProfileAvatar className="account-button__avatar" user={session.user} alt="" />
                <span>{session.user?.fullName?.split(' ').slice(-1)[0] || 'Tài khoản'}</span>
              </>
            ) : <><UserRound size={17} /><span>Đăng nhập</span></>}
          </button>
        </>
      } />

      <main id="top" className={`home-tab home-tab--${homeTab}`}>
        <div className="discover-content">
          <Hero
            search={search}
            onSearchChange={setSearch}
            onSelectCategory={setCategory}
            total={total}
            districtCount={districts.length}
          />

          <CategoryStrip
            categories={categories}
            category={category}
            onSelectCategory={setCategory}
          />
        </div>

        <DiscoverSection
          viewMode={viewMode}
          onViewModeChange={setViewMode}
          mobileFiltersOpen={mobileFiltersOpen}
          onToggleMobileFilters={() => setMobileFiltersOpen((value) => !value)}
          search={search}
          onSearchChange={setSearch}
          category={category}
          onCategoryChange={setCategory}
          district={district}
          onDistrictChange={setDistrict}
          districts={districts}
          categories={categories}
          openNow={openNow}
          onOpenNowChange={setOpenNow}
          onClearFilters={clearFilters}
          placesError={placesError}
          onRetry={() => setRequestVersion((current) => current + 1)}
          loading={loading}
          placePage={placePage}
          pageItems={pageItems}
          onPageChange={setPage}
          onSelectPlace={selectPlace}
          onAddToPlan={handleAddToPlan}
          isInPlan={isPlaceInSelectedPlan}
          mapPlaces={mapPlaces}
          mapLoading={mapLoading}
          mapError={mapPlacesError}
          userLocation={locationState.position}
          locationStatus={locationState.status}
          locationError={locationState.error}
          onLocate={handleLocateSelf}
          onShareLocation={handleOpenLocationShare}
          focusedLocation={focusedLocation}
          onClearFocusedLocation={clearFocusedLocation}
          theme={theme}
        />

        <AppFooter />
      </main>

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
        onOpenChat={openChat}
        onStartCall={handleStartCall}
        callDisabled={!canStartCall}
      />
      <ChatPanel
        open={chatOpen}
        onClose={closeChat}
        friends={friendshipState.friends}
        initialFriendId={chatInitialFriendId}
        currentUserId={session?.user?.id}
        chatState={chatState}
        callStatus={callState.status}
        callConnectionStatus={callState.connectionStatus}
        showToast={showToast}
        onOpenLocation={handleOpenSharedLocation}
        onStartCall={handleStartCall}
      />
      <CallOverlay callState={visibleCallState} />
      <ShareLocationDialog
        open={locationShareOpen}
        onClose={closeLocationShare}
        friends={friendshipState.friends}
        friendsLoading={friendshipState.loading}
        friendsError={friendshipState.error}
        location={locationState.position}
        connectionStatus={chatState.connectionStatus}
        onShare={handleShareLocation}
        onOpenFriends={openFriendsFromLocationShare}
        onRefreshFriends={() => friendshipState.refresh().catch(() => {})}
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
            setChatOpen(false)
            setFriendsOpen(true)
          }}
          showToast={showToast}
        />
      )}
      {toast && <Toast key={toast.id} toast={toast} onClose={() => setToast(null)} />}
    </div>
  )
}
