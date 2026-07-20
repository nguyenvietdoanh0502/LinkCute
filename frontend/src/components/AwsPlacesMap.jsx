import maplibregl from 'maplibre-gl'
import { Cloud, KeyRound, MapPinned } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { categoryLabel } from './PlaceCard.jsx'

const HANOI_CENTER = [105.8342, 21.0278]
const DEFAULT_REGION = 'ap-southeast-1'
const DEFAULT_STYLE = 'Standard'
const SOURCE_ID = 'linkcute-places'
const CLUSTER_LAYER_ID = 'linkcute-place-clusters'
const CLUSTER_COUNT_LAYER_ID = 'linkcute-place-cluster-count'
const PLACE_LAYER_ID = 'linkcute-place-markers'

const CATEGORY_COLORS = {
  FOOD: '#ef604b',
  CAFE: '#a96743',
  ENTERTAINMENT: '#31786f',
  CINEMA: '#315765',
  SHOPPING: '#c55f79',
  OTHER: '#776b9e',
}

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

function createMarkerImage(category, color) {
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
  context.strokeStyle = '#ffffff'
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

function addPlaceLayers(map) {
  Object.entries(CATEGORY_COLORS).forEach(([category, color]) => {
    const imageId = MARKER_IMAGE_IDS[category]
    if (!map.hasImage(imageId)) {
      map.addImage(imageId, createMarkerImage(category, color), { pixelRatio: 2 })
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
        '#31786f',
        10,
        '#d87753',
        35,
        '#b85168',
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
      'circle-stroke-color': '#ffffff',
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

export default function AwsPlacesMap({ places = [], loading = false, error = '', onSelect }) {
  const containerRef = useRef(null)
  const mapRef = useRef(null)
  const popupRef = useRef(null)
  const placesByIdRef = useRef(new Map())
  const [mapReady, setMapReady] = useState(false)
  const [mapError, setMapError] = useState('')

  const apiKey = import.meta.env.VITE_AWS_LOCATION_API_KEY?.trim()
  const region = import.meta.env.VITE_AWS_LOCATION_REGION?.trim() || DEFAULT_REGION
  const mapStyle = import.meta.env.VITE_AWS_MAP_STYLE?.trim() || DEFAULT_STYLE
  const colorScheme = import.meta.env.VITE_AWS_MAP_COLOR_SCHEME?.trim() || 'Light'

  const validPlaces = useMemo(() => places.filter(isValidCoordinate), [places])

  useEffect(() => {
    placesByIdRef.current = new Map(validPlaces.map((place) => [String(place.id), place]))
  }, [validPlaces])

  useEffect(() => {
    if (!apiKey || !containerRef.current) return undefined

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
    map.addControl(new maplibregl.NavigationControl({ visualizePitch: true }), 'top-right')
    map.addControl(new maplibregl.AttributionControl({ compact: true }), 'bottom-right')

    const onLoad = () => {
      try {
        hideAwsPoiLayers(map)
        addPlaceLayers(map)
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

  useEffect(() => {
    const map = mapRef.current
    const source = map?.getSource(SOURCE_ID)
    if (!map || !mapReady || !source) return

    source.setData(toFeatureCollection(validPlaces))
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
  }, [mapReady, validPlaces])

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
    <div className="aws-map-panel">
      <div ref={containerRef} className="aws-map-canvas" aria-label="Bản đồ Amazon Location chứa các địa điểm tìm được" />

      <div className="map-result-badge">
        <span><MapPinned size={17} /></span>
        <div><strong>{validPlaces.length} địa điểm</strong><small>Amazon Location Maps</small></div>
      </div>

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
