import type { Bounds, PropertyMapItem } from '@/api/types'

export interface MapViewport extends Bounds {
  zoomLevel: number
}

export interface LatLngPoint {
  lat: number
  lng: number
}

export interface KakaoLatLngLike {
  getLat(): number
  getLng(): number
}

export interface KakaoBoundsLike {
  getSouthWest(): KakaoLatLngLike
  getNorthEast(): KakaoLatLngLike
}

export interface KakaoMapLike {
  getBounds(): KakaoBoundsLike
  getLevel(): number
  setCenter?(latLng: unknown): void
  setLevel?(level: number): void
  panTo?(latLng: unknown): void
}

export interface KakaoMarkerLike {
  setMap(map: KakaoMapLike | null): void
}

export interface KakaoMapsApi {
  LatLng: new (lat: number, lng: number) => unknown
  Map: new (container: HTMLElement, options: { center: unknown; level: number }) => KakaoMapLike
  Marker: new (options: {
    map: KakaoMapLike
    position: unknown
    title: string
    image?: unknown
    clickable?: boolean
  }) => KakaoMarkerLike
  MarkerImage?: new (src: string, size: unknown, options?: { offset?: unknown }) => unknown
  Size?: new (width: number, height: number) => unknown
  Point?: new (x: number, y: number) => unknown
  event: {
    addListener(target: unknown, eventName: string, handler: () => void): void
  }
}

export const DEFAULT_MAP_CENTER: LatLngPoint = {
  lat: 37.5008,
  lng: 127.0366
}

export const DEFAULT_MAP_LEVEL = 5

export const SEOUL_SEED_VIEWPORT: MapViewport = {
  swLat: 37.48,
  swLng: 127.01,
  neLat: 37.52,
  neLng: 127.06,
  zoomLevel: DEFAULT_MAP_LEVEL
}

export function boundsFromKakao(bounds: KakaoBoundsLike, zoomLevel: number): MapViewport {
  const southWest = bounds.getSouthWest()
  const northEast = bounds.getNorthEast()

  return {
    swLat: southWest.getLat(),
    swLng: southWest.getLng(),
    neLat: northEast.getLat(),
    neLng: northEast.getLng(),
    zoomLevel
  }
}

export function viewportFromMap(map: KakaoMapLike): MapViewport {
  return boundsFromKakao(map.getBounds(), map.getLevel())
}

export function clearMarkers(markers: KakaoMarkerLike[]) {
  markers.forEach((marker) => marker.setMap(null))
}

function markerSvg(fill: string, stroke: string): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="34" height="42" viewBox="0 0 34 42"><path d="M17 41s15-13.2 15-25A15 15 0 1 0 2 16c0 11.8 15 25 15 25Z" fill="${fill}" stroke="${stroke}" stroke-width="3"/><circle cx="17" cy="16" r="5" fill="#fff"/></svg>`
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`
}

function createMarkerImage(kakaoMaps: KakaoMapsApi, selected: boolean): unknown {
  if (!kakaoMaps.MarkerImage || !kakaoMaps.Size || !kakaoMaps.Point) {
    return undefined
  }

  const fill = selected ? '#b45309' : '#2563eb'
  const stroke = selected ? '#78350f' : '#1d4ed8'

  return new kakaoMaps.MarkerImage(markerSvg(fill, stroke), new kakaoMaps.Size(34, 42), {
    offset: new kakaoMaps.Point(17, 42)
  })
}

export function syncKakaoMarkers(options: {
  kakaoMaps: KakaoMapsApi
  map: KakaoMapLike
  previousMarkers: KakaoMarkerLike[]
  items: PropertyMapItem[]
  selectedPropertyId: number | null
  onClick: (propertyId: number) => void
}): KakaoMarkerLike[] {
  clearMarkers(options.previousMarkers)

  return options.items.map((item) => {
    const marker = new options.kakaoMaps.Marker({
      map: options.map,
      position: new options.kakaoMaps.LatLng(item.lat, item.lng),
      title: `property-${item.propertyId}`,
      image: createMarkerImage(options.kakaoMaps, options.selectedPropertyId === item.propertyId),
      clickable: true
    })

    options.kakaoMaps.event.addListener(marker, 'click', () => options.onClick(item.propertyId))

    return marker
  })
}
