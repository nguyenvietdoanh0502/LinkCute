import maplibregl from 'maplibre-gl'
import {
  Clock3,
  Cloud,
  KeyRound,
  LocateFixed,
  MapPin,
  MapPinned,
  Search,
  Share2,
  Tags,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import {
  MAX_MERCATOR_LATITUDE,
  locationAccuracyLabel,
  normalizeLocation,
} from '../location/model.js'
import { categoryLabel } from './PlaceCard.jsx'
import SelectDropdown from './SelectDropdown.jsx'

const HANOI_CENTER = [105.8342, 21.0278]
const DEFAULT_REGION = 'ap-southeast-1'
const DEFAULT_STYLE = 'Standard'
const SOURCE_ID = 'linkcute-places'
const CLUSTER_LAYER_ID = 'linkcute-place-clusters'
const CLUSTER_COUNT_LAYER_ID = 'linkcute-place-cluster-count'
const PLACE_LAYER_ID = 'linkcute-place-markers'

// Brand-driven palette: coral CTA hue for food, warm tan cafes, teal-adjacent
// entertainment, steel-blue cinema, dusty rose shopping, muted violet other.
const CATEGORY_COLORS = {
  FOOD: '#ef6b55',
  CAFE: '#c98a4b',
  ENTERTAINMENT: '#2e7d72',
  CINEMA: '#3d5a80',
  SHOPPING: '#b45c7e',
  OTHER: '#7a6f9e',
}

const CLUSTER_COLOR_RAMP = ['#2e7d72', 10, '#ef6b55', 35, '#c33f2b']

const MARKER_IMAGE_IDS = Object.fromEntries(
  Object.keys(CATEGORY_COLORS).map((category) => [category, `linkcute-marker-${category.toLowerCase()}`]),
)

function isValidCoordinate(place) {
  const latitude = Number(place.lat)
  const longitude = Number(place.lng)
  return Number.isFinite(latitude)
    && Number.isFinite(longitude)
    && latitude >= -85
    && latitude <= 85
    && longitude >= -180
    && longitude <= 180
}

function createPopupContent(place, onSelect) {
  const content = document.createElement('div')
  content.className = 'map-popup'

  const category = document.createElement('span')
  category.className = 'map-popup__category'
  category.textContent = categoryLabel(place.category)

  const name = document.createElement('strong')
  name.textContent = place.name || 'Địa điểm LinkCute'

  const address = document.createElement('p')
  address.textContent = place.address || place.district || 'Việt Nam'

  const action = document.createElement('button')
  action.type = 'button'
  action.textContent = 'Xem chi tiết →'
  action.addEventListener('click', () => onSelect(place.id))

  content.append(category, name, address, action)
  return content
}

function mapCoordinates(location) {
  const latitude = Math.max(
    -MAX_MERCATOR_LATITUDE,
    Math.min(MAX_MERCATOR_LATITUDE, location.latitude),
  )
  return [location.longitude, latitude]
}

function createLocationMarkerElement(kind) {
  const marker = document.createElement('div')
  marker.className = `map-location-marker map-location-marker--${kind}`
  const pulse = document.createElement('span')
  const dot = document.createElement('i')
  marker.append(pulse, dot)
  return marker
}

function createLocationPopupContent(location, { label, kind, sharedAt }) {
  const content = document.createElement('div')
  content.className = 'map-popup map-location-popup'

  const category = document.createElement('span')
  category.className = 'map-popup__category'
  category.textContent = kind === 'self' ? 'Vị trí của tôi' : 'Vị trí được chia sẻ'

  const title = document.createElement('strong')
  title.textContent = label || (kind === 'self' ? 'Bạn đang ở đây' : 'Vị trí bạn bè')

  const description = document.createElement('p')
  const sharedDate = sharedAt ? new Date(sharedAt) : null
  const timeLabel = sharedDate && !Number.isNaN(sharedDate.getTime())
    ? ` · Gửi lúc ${sharedDate.toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' })}`
    : ''
  description.textContent = `${location.latitude.toFixed(6)}, ${location.longitude.toFixed(6)} · ${locationAccuracyLabel(location)}${timeLabel}`

  content.append(category, title, description)
  return content
}

function drawCategoryIcon(context, category) {
  context.save()
  context.strokeStyle = '#ffffff'
  context.fillStyle = '#ffffff'
  context.lineWidth = 3.6
  context.lineCap = 'round'
  context.lineJoin = 'round'

  if (category === 'FOOD') {
    context.beginPath()
    context.moveTo(21, 18)
    context.lineTo(21, 29)
    context.moveTo(27, 18)
    context.lineTo(27, 29)
    context.moveTo(21, 25)
    context.lineTo(27, 25)
    context.moveTo(24, 29)
    context.lineTo(24, 42)
    context.stroke()
    context.beginPath()
    context.ellipse(40, 23, 5, 7, 0, 0, Math.PI * 2)
    context.moveTo(40, 30)
    context.lineTo(40, 42)
    context.stroke()
  } else if (category === 'CAFE') {
    context.beginPath()
    context.moveTo(19, 25)
    context.lineTo(42, 25)
    context.lineTo(40, 38)
    context.quadraticCurveTo(31, 43, 22, 38)
    context.closePath()
    context.stroke()
    context.beginPath()
    context.arc(43, 31, 6, -Math.PI / 2, Math.PI / 2)
    context.moveTo(26, 20)
    context.quadraticCurveTo(23, 16, 27, 13)
    context.moveTo(35, 20)
    context.quadraticCurveTo(32, 16, 36, 13)
    context.stroke()
  } else if (category === 'ENTERTAINMENT') {
    context.beginPath()
    context.moveTo(29, 20)
    context.lineTo(29, 38)
    context.moveTo(29, 20)
    context.lineTo(43, 17)
    context.lineTo(43, 34)
    context.stroke()
    context.beginPath()
    context.ellipse(24, 39, 6, 4.5, -0.2, 0, Math.PI * 2)
    context.ellipse(38, 35, 6, 4.5, -0.2, 0, Math.PI * 2)
    context.fill()
  } else if (category === 'CINEMA') {
    context.beginPath()
    context.roundRect(17, 18, 30, 25, 5)
    context.stroke()
    context.beginPath()
    context.moveTo(29, 24)
    context.lineTo(40, 30.5)
    context.lineTo(29, 37)
    context.closePath()
    context.fill()
  } else if (category === 'SHOPPING') {
    context.beginPath()
    context.moveTo(19, 25)
    context.lineTo(45, 25)
    context.lineTo(43, 43)
    context.lineTo(21, 43)
    context.closePath()
    context.moveTo(26, 25)
    context.arc(32, 25, 6, Math.PI, 0)
    context.stroke()
  } else {
    context.beginPath()
    context.arc(32, 29, 11, 0, Math.PI * 2)
    context.moveTo(32, 15)
    context.lineTo(32, 20)
    context.moveTo(32, 38)
    context.lineTo(32, 43)
    context.moveTo(18, 29)
    context.lineTo(23, 29)
    context.moveTo(41, 29)
    context.lineTo(46, 29)
    context.stroke()
    context.beginPath()
    context.arc(32, 29, 3.5, 0, Math.PI * 2)
    context.fill()
  }

  context.restore()
}

function createMarkerImage(category, color, strokeColor = '#ffffff') {
  const canvas = document.createElement('canvas')
  canvas.width = 64
  canvas.height = 80
  const context = canvas.getContext('2d')

  context.save()
  context.shadowColor = 'rgba(14, 39, 43, 0.28)'
  context.shadowBlur = 7
  context.shadowOffsetY = 4
  context.beginPath()
  context.moveTo(32, 79)
  context.bezierCurveTo(27, 67, 7, 48, 7, 29)
  context.bezierCurveTo(7, 13, 17, 3, 32, 3)
  context.bezierCurveTo(47, 3, 57, 13, 57, 29)
  context.bezierCurveTo(57, 48, 37, 67, 32, 79)
  context.closePath()
  context.fillStyle = color
  context.fill()
  context.shadowColor = 'transparent'
  context.lineWidth = 4
  context.strokeStyle = strokeColor
  context.stroke()
  context.restore()

  drawCategoryIcon(context, category)
  return context.getImageData(0, 0, canvas.width, canvas.height)
}

function toFeatureCollection(places) {
  return {
    type: 'FeatureCollection',
    features: places.map((place) => ({
      type: 'Feature',
      geometry: {
        type: 'Point',
        coordinates: [Number(place.lng), Number(place.lat)],
      },
      properties: {
        id: String(place.id),
        category: CATEGORY_COLORS[place.category] ? place.category : 'OTHER',
      },
    })),
  }
}

function hideAwsPoiLayers(map) {
  const layers = map.getStyle().layers || []

  layers.forEach((layer) => {
    const layerId = layer.id.toLowerCase()
    const sourceLayer = String(layer['source-layer'] || '').toLowerCase()
    const isPoiLayer = layerId.startsWith('poi_')
      || sourceLayer === 'poi'
      || sourceLayer === 'aerodrome_label'
      || (layer.type === 'symbol' && sourceLayer === 'landuse')

    if (isPoiLayer) {
      map.setLayoutProperty(layer.id, 'visibility', 'none')
    }
  })
}

function addPlaceLayers(map, strokeColor = '#ffffff') {
  Object.entries(CATEGORY_COLORS).forEach(([category, color]) => {
    const imageId = MARKER_IMAGE_IDS[category]
    if (!map.hasImage(imageId)) {
      map.addImage(imageId, createMarkerImage(category, color, strokeColor), { pixelRatio: 2 })
    }
  })

  map.addSource(SOURCE_ID, {
    type: 'geojson',
    data: toFeatureCollection([]),
    cluster: true,
    clusterMaxZoom: 14,
    clusterRadius: 54,
  })

  map.addLayer({
    id: CLUSTER_LAYER_ID,
    type: 'circle',
    source: SOURCE_ID,
    filter: ['has', 'point_count'],
    paint: {
      'circle-color': [
        'step',
        ['get', 'point_count'],
        ...CLUSTER_COLOR_RAMP,
      ],
      'circle-radius': [
        'step',
        ['get', 'point_count'],
        20,
        10,
        24,
        35,
        29,
      ],
      'circle-stroke-width': 3,
      'circle-stroke-color': strokeColor,
      'circle-opacity': 0.94,
      'circle-stroke-opacity': 0.95,
    },
  })

  map.addLayer({
    id: CLUSTER_COUNT_LAYER_ID,
    type: 'symbol',
    source: SOURCE_ID,
    filter: ['has', 'point_count'],
    layout: {
      'text-field': ['get', 'point_count_abbreviated'],
      'text-font': ['Amazon Ember Regular'],
      'text-size': 12,
      'text-allow-overlap': true,
    },
    paint: {
      'text-color': '#ffffff',
      'text-halo-color': 'rgba(20, 50, 53, 0.18)',
      'text-halo-width': 1,
    },
  })

  map.addLayer({
    id: PLACE_LAYER_ID,
    type: 'symbol',
    source: SOURCE_ID,
    filter: ['!', ['has', 'point_count']],
    layout: {
      'icon-image': [
        'match',
        ['get', 'category'],
        'FOOD', MARKER_IMAGE_IDS.FOOD,
        'CAFE', MARKER_IMAGE_IDS.CAFE,
        'ENTERTAINMENT', MARKER_IMAGE_IDS.ENTERTAINMENT,
        'CINEMA', MARKER_IMAGE_IDS.CINEMA,
        'SHOPPING', MARKER_IMAGE_IDS.SHOPPING,
        MARKER_IMAGE_IDS.OTHER,
      ],
      'icon-anchor': 'bottom',
      'icon-size': [
        'interpolate',
        ['linear'],
        ['zoom'],
        5, 0.72,
        12, 0.86,
        16, 1,
      ],
      'icon-allow-overlap': false,
      'icon-padding': 4,
    },
  })
}

function FullscreenMapFilters({ filters }) {
  if (!filters) return null

  return (
    <div className="map-fullscreen-filters" aria-label="Bộ lọc bản đồ toàn màn hình">
      <label className="map-overlay-field map-overlay-search">
        <Search size={17} />
        <input
          value={filters.search}
          onChange={(event) => filters.onSearchChange(event.target.value)}
          placeholder="Tên hoặc địa chỉ…"
          aria-label="Tìm kiếm trên bản đồ"
        />
        {filters.search && (
          <button type="button" onClick={() => filters.onSearchChange('')} aria-label="Xóa tìm kiếm">
            <X size={15} />
          </button>
        )}
      </label>

      <SelectDropdown
        className="map-overlay-dropdown"
        value={filters.district}
        onChange={filters.onDistrictChange}
        icon={<MapPin size={17} />}
        ariaLabel="Chọn khu vực trên bản đồ"
        searchable
        searchPlaceholder="Tìm khu vực…"
        options={[
          { value: '', label: 'Mọi khu vực' },
          ...filters.districts.map((item) => ({
            value: item.district,
            label: item.district,
            count: item.count,
          })),
        ]}
      />

      <SelectDropdown
        className="map-overlay-dropdown"
        value={filters.category}
        onChange={filters.onCategoryChange}
        icon={<Tags size={17} />}
        ariaLabel="Chọn danh mục trên bản đồ"
        options={[
          { value: '', label: 'Mọi category' },
          ...filters.categories.map((item) => ({
            value: item.category,
            label: item.name || categoryLabel(item.category),
            count: item.count,
          })),
        ]}
      />

      <label className="map-overlay-toggle">
        <input
          type="checkbox"
          checked={filters.openNow}
          onChange={(event) => filters.onOpenNowChange(event.target.checked)}
        />
        <span className="toggle-filter__track"><span /></span>
        <Clock3 size={16} />
        <span>Đang mở cửa</span>
      </label>
    </div>
  )
}

export default function AwsPlacesMap({
  places = [],
  loading = false,
  error = '',
  onSelect,
  filters = null,
  userLocation = null,
  locationStatus = 'idle',
  locationError = '',
  onLocate,
  onShareLocation,
  focusedLocation = null,
  onClearFocusedLocation,
  theme = 'light',
}) {
  const panelRef = useRef(null)
  const containerRef = useRef(null)
  const mapRef = useRef(null)
  const popupRef = useRef(null)
  const userMarkerRef = useRef(null)
  const focusedMarkerRef = useRef(null)
  const locationCameraModeRef = useRef('places')
  const lastFilterSignatureRef = useRef('')
  const placesByIdRef = useRef(new Map())
  const themeAppliedRef = useRef(false)
  const [mapReady, setMapReady] = useState(false)
  const [mapError, setMapError] = useState('')

  const apiKey = import.meta.env.VITE_AWS_LOCATION_API_KEY?.trim()
  const region = import.meta.env.VITE_AWS_LOCATION_REGION?.trim() || DEFAULT_REGION
  const mapStyle = import.meta.env.VITE_AWS_MAP_STYLE?.trim() || DEFAULT_STYLE
  // 'Light'/'Dark' match the AWS style descriptor's color-scheme values.
  const colorScheme = theme === 'dark' ? 'Dark' : 'Light'

  const validPlaces = useMemo(() => places.filter(isValidCoordinate), [places])
  const validUserLocation = useMemo(() => normalizeLocation(userLocation), [userLocation])
  const validFocusedLocation = useMemo(() => normalizeLocation(focusedLocation), [focusedLocation])
  const filterSignature = `${filters?.search || ''}|${filters?.category || ''}|${filters?.district || ''}|${Boolean(filters?.openNow)}`

  useEffect(() => {
    if (!lastFilterSignatureRef.current) {
      lastFilterSignatureRef.current = filterSignature
      return
    }
    if (lastFilterSignatureRef.current === filterSignature) return
    lastFilterSignatureRef.current = filterSignature
    if (locationCameraModeRef.current === 'self') locationCameraModeRef.current = 'places'
  }, [filterSignature])

  useEffect(() => {
    placesByIdRef.current = new Map(validPlaces.map((place) => [String(place.id), place]))
  }, [validPlaces])

  useEffect(() => {
    if (!apiKey || !panelRef.current || !containerRef.current) return undefined

    setMapError('')
    setMapReady(false)

    const styleUrl = new URL(`https://maps.geo.${region}.amazonaws.com/v2/styles/${encodeURIComponent(mapStyle)}/descriptor`)
    styleUrl.searchParams.set('key', apiKey)
    styleUrl.searchParams.set('color-scheme', colorScheme)

    const map = new maplibregl.Map({
      container: containerRef.current,
      style: styleUrl.toString(),
      center: HANOI_CENTER,
      zoom: 11,
      minZoom: 5,
      maxZoom: 19,
      attributionControl: false,
    })

    mapRef.current = map
    themeAppliedRef.current = false
    map.addControl(new maplibregl.NavigationControl({ visualizePitch: true }), 'top-right')
    map.addControl(new maplibregl.AttributionControl({ compact: true }), 'bottom-right')

    const onLoad = () => {
      try {
        hideAwsPoiLayers(map)
        addPlaceLayers(map, getComputedStyle(map.getCanvas()).getPropertyValue('--map-marker-stroke').trim() || '#ffffff')
        setMapReady(true)
        setMapError('')
        map.resize()
      } catch {
        setMapError('Không thể khởi tạo marker trên bản đồ. Hãy tải lại trang và thử lại.')
      }
    }

    const onError = (event) => {
      const status = event?.error?.status
      if (status === 403) {
        setMapError('AWS từ chối API key. Hãy kiểm tra region, quyền GetTile và allowed referrers.')
      } else if (status === 429) {
        setMapError('Amazon Location đã vượt quota hoặc đang giới hạn tốc độ request.')
      }
    }

    const onClusterClick = async (event) => {
      const feature = event.features?.[0]
      const source = map.getSource(SOURCE_ID)
      if (!feature || !source) return

      const zoom = await source.getClusterExpansionZoom(Number(feature.properties.cluster_id))
      if (mapRef.current === map) {
        map.easeTo({ center: feature.geometry.coordinates, zoom, duration: 500 })
      }
    }

    const onPlaceClick = (event) => {
      const feature = event.features?.[0]
      const place = placesByIdRef.current.get(String(feature?.properties?.id))
      if (!feature || !place) return

      popupRef.current?.remove()
      popupRef.current = new maplibregl.Popup({ offset: 30, closeButton: true, maxWidth: '300px' })
        .setLngLat(feature.geometry.coordinates)
        .setDOMContent(createPopupContent(place, onSelect))
        .addTo(map)
    }

    const showPointer = () => { map.getCanvas().style.cursor = 'pointer' }
    const resetPointer = () => { map.getCanvas().style.cursor = '' }

    map.on('load', onLoad)
    map.on('error', onError)
    map.on('click', CLUSTER_LAYER_ID, onClusterClick)
    map.on('click', PLACE_LAYER_ID, onPlaceClick)
    map.on('mouseenter', CLUSTER_LAYER_ID, showPointer)
    map.on('mouseleave', CLUSTER_LAYER_ID, resetPointer)
    map.on('mouseenter', PLACE_LAYER_ID, showPointer)
    map.on('mouseleave', PLACE_LAYER_ID, resetPointer)

    const loadTimeout = window.setTimeout(() => {
      if (!map.loaded()) {
        setMapError('Amazon Location Maps tải quá lâu. Hãy kiểm tra API key, region và kết nối mạng.')
      }
    }, 15000)

    const resizeObserver = new ResizeObserver(() => map.resize())
    resizeObserver.observe(containerRef.current)

    return () => {
      resizeObserver.disconnect()
      window.clearTimeout(loadTimeout)
      popupRef.current?.remove()
      popupRef.current = null
      userMarkerRef.current?.remove()
      userMarkerRef.current = null
      focusedMarkerRef.current?.remove()
      focusedMarkerRef.current = null
      locationCameraModeRef.current = 'places'
      themeAppliedRef.current = false
      map.off('load', onLoad)
      map.off('error', onError)
      map.off('click', CLUSTER_LAYER_ID, onClusterClick)
      map.off('click', PLACE_LAYER_ID, onPlaceClick)
      map.off('mouseenter', CLUSTER_LAYER_ID, showPointer)
      map.off('mouseleave', CLUSTER_LAYER_ID, resetPointer)
      map.off('mouseenter', PLACE_LAYER_ID, showPointer)
      map.off('mouseleave', PLACE_LAYER_ID, resetPointer)
      map.remove()
      mapRef.current = null
    }
  }, [apiKey, colorScheme, mapStyle, onSelect, region])

  // Theme sync: on the first run the init effect just created the map with
  // this exact color-scheme, so only the canvas strokes need aligning with
  // the theme. On later theme changes, swap the AWS style descriptor's
  // color-scheme and rebuild the source/layers (setStyle resets the style).
  useEffect(() => {
    const map = mapRef.current
    if (!map) return

    const applyScheme = () => {
      if (mapRef.current !== map) return
      if (!map.isStyleLoaded()) return // init onLoad seeds layers with the current stroke
      try {
        hideAwsPoiLayers(map)
        const strokeColor = getComputedStyle(map.getCanvas())
          .getPropertyValue('--map-marker-stroke').trim() || '#ffffff'
        Object.values(MARKER_IMAGE_IDS).forEach((imageId) => {
          if (map.hasImage(imageId)) map.removeImage(imageId)
        })
        Object.entries(CATEGORY_COLORS).forEach(([category, color]) => {
          map.addImage(MARKER_IMAGE_IDS[category], createMarkerImage(category, color, strokeColor), { pixelRatio: 2 })
        })
        if (map.getSource(SOURCE_ID)) {
          // Style was re-seeded by the init handler — just restyle the stroke.
          map.setPaintProperty(CLUSTER_LAYER_ID, 'circle-stroke-color', strokeColor)
        } else {
          // setStyle reset the style — rebuild source, layers, and data.
          addPlaceLayers(map, strokeColor)
        }
        map.getSource(SOURCE_ID)?.setData(toFeatureCollection(Array.from(placesByIdRef.current.values())))
      } catch {
        // Style reload failed — the map keeps its previous theme until next load.
      }
    }

    if (!themeAppliedRef.current) {
      // Fresh map (or StrictMode remount) — strokes only, no style reload.
      themeAppliedRef.current = true
      applyScheme()
      return undefined
    }

    const styleUrl = new URL(`https://maps.geo.${region}.amazonaws.com/v2/styles/${encodeURIComponent(mapStyle)}/descriptor`)
    styleUrl.searchParams.set('key', apiKey)
    styleUrl.searchParams.set('color-scheme', theme === 'dark' ? 'Dark' : 'Light')

    map.on('load', applyScheme)
    map.setStyle(styleUrl.toString())

    return () => {
      map.off('load', applyScheme)
    }
  }, [apiKey, mapStyle, region, theme])

  useEffect(() => {
    const map = mapRef.current
    if (!map || !mapReady) return

    userMarkerRef.current?.remove()
    userMarkerRef.current = null
    if (!validUserLocation) return

    userMarkerRef.current = new maplibregl.Marker({
      element: createLocationMarkerElement('self'),
      anchor: 'center',
    })
      .setLngLat(mapCoordinates(validUserLocation))
      .setPopup(new maplibregl.Popup({ offset: 20, closeButton: true, maxWidth: '310px' })
        .setDOMContent(createLocationPopupContent(validUserLocation, {
          kind: 'self',
          label: 'Bạn đang ở đây',
        })))
      .addTo(map)
  }, [mapReady, validUserLocation])

  useEffect(() => {
    const map = mapRef.current
    if (!map || !mapReady) return

    focusedMarkerRef.current?.remove()
    focusedMarkerRef.current = null
    if (!validFocusedLocation) {
      if (locationCameraModeRef.current === 'shared') locationCameraModeRef.current = 'places'
      return
    }

    locationCameraModeRef.current = 'shared'
    const popup = new maplibregl.Popup({ offset: 28, closeButton: true, maxWidth: '330px' })
      .setDOMContent(createLocationPopupContent(validFocusedLocation, {
        kind: 'shared',
        label: focusedLocation?.label,
        sharedAt: focusedLocation?.sharedAt,
      }))
    focusedMarkerRef.current = new maplibregl.Marker({
      element: createLocationMarkerElement('shared'),
      anchor: 'bottom',
    })
      .setLngLat(mapCoordinates(validFocusedLocation))
      .setPopup(popup)
      .addTo(map)
    map.flyTo({ center: mapCoordinates(validFocusedLocation), zoom: 16, duration: 750 })
    popup.setLngLat(mapCoordinates(validFocusedLocation)).addTo(map)
  }, [focusedLocation, mapReady, validFocusedLocation])

  useEffect(() => {
    const map = mapRef.current
    const source = map?.getSource(SOURCE_ID)
    if (!map || !mapReady || !source) return

    source.setData(toFeatureCollection(validPlaces))
    if (locationCameraModeRef.current !== 'places' || validFocusedLocation) return
    if (validPlaces.length === 0) return

    const bounds = new maplibregl.LngLatBounds()
    validPlaces.forEach((place) => bounds.extend([Number(place.lng), Number(place.lat)]))

    if (validPlaces.length === 1) {
      map.flyTo({ center: bounds.getCenter(), zoom: 15, duration: 700 })
    } else {
      map.fitBounds(bounds, {
        padding: { top: 90, right: 70, bottom: 70, left: 70 },
        maxZoom: 14,
        duration: 700,
      })
    }
  }, [mapReady, validFocusedLocation, validPlaces])

  const handleLocate = async () => {
    try {
      const location = normalizeLocation(await onLocate?.())
      const map = mapRef.current
      if (!location || !map || !mapReady) return
      onClearFocusedLocation?.()
      locationCameraModeRef.current = 'self'
      map.flyTo({ center: mapCoordinates(location), zoom: 16, duration: 750 })
    } catch {
      // The location hook exposes a localized error in the map overlay.
    }
  }

  if (!apiKey) {
    return (
      <div className="map-setup-state">
        <span><KeyRound size={27} /></span>
        <h3>Chưa có Amazon Location API key</h3>
        <p>Thêm <code>VITE_AWS_LOCATION_API_KEY</code> và <code>VITE_AWS_LOCATION_REGION</code> vào file <code>frontend/.env</code>, sau đó khởi động lại Vite.</p>
        <a className="button button--secondary" href="https://console.aws.amazon.com/location/home#/api-keys" target="_blank" rel="noreferrer"><Cloud size={16} /> Mở AWS Location</a>
      </div>
    )
  }

  return (
    <div ref={panelRef} className="aws-map-panel">
      <div ref={containerRef} className="aws-map-canvas" aria-label="Bản đồ Amazon Location chứa các địa điểm tìm được" />
      <FullscreenMapFilters filters={filters} />

      <div className="map-result-badge">
        <span><MapPinned size={17} /></span>
        <div><strong>{validPlaces.length} địa điểm</strong><small>Amazon Location Maps</small></div>
      </div>

      <div className="map-location-actions" aria-label="Vị trí của bạn trên bản đồ">
        <button
          type="button"
          onClick={handleLocate}
          disabled={locationStatus === 'requesting' || !mapReady}
          aria-label="Hiển thị vị trí của tôi"
          title="Vị trí của tôi"
        >
          {locationStatus === 'requesting'
            ? <span className="map-location-spinner" />
            : <LocateFixed size={17} />}
          <span>{locationStatus === 'requesting' ? 'Đang định vị…' : 'Vị trí của tôi'}</span>
        </button>
        <button
          type="button"
          onClick={onShareLocation}
          disabled={locationStatus === 'requesting'}
          aria-label="Chia sẻ vị trí của tôi với bạn bè"
          title="Chia sẻ vị trí"
        >
          <Share2 size={17} />
          <span>Chia sẻ</span>
        </button>
      </div>

      {locationError && (
        <div className="map-location-error" role="alert">{locationError}</div>
      )}

      {validFocusedLocation && (
        <div className="map-focused-location" role="status">
          <MapPin size={17} aria-hidden="true" />
          <span>{focusedLocation?.label || 'Vị trí được chia sẻ'}</span>
          <button type="button" onClick={onClearFocusedLocation} aria-label="Đóng vị trí được chia sẻ">
            <X size={15} />
          </button>
        </div>
      )}

      {(loading || (!mapReady && !mapError)) && (
        <div className="map-loading"><span className="loader" /><p>Đang tải bản đồ AWS và các điểm đến…</p></div>
      )}

      {(error || mapError) && (
        <div className="map-error" role="alert">
          <strong>Chưa thể tải bản đồ</strong>
          <span>{error || mapError}</span>
        </div>
      )}
    </div>
  )
}