import type { AdministrativeCluster, Bounds, PropertyMapItem } from '@/api/types'
import { formatKrw } from '@/utils/format'

export interface MapViewport extends Bounds {
  zoomLevel: number
}

export interface LatLngPoint {
  lat: number
  lng: number
}

export interface MapMarkerRenderMode {
  showIndividualMarkers: boolean
  showTransactionClusterer: boolean
  showAdministrativeClusters: boolean
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

export interface KakaoOverlayLike {
  setMap(map: KakaoMapLike | null): void
}

export interface KakaoTransactionMarkerLike extends KakaoMarkerLike {
  recentTransactionCount?: number
}

export interface KakaoClusterLike {
  getMarkers(): KakaoTransactionMarkerLike[]
  getClusterMarker(): {
    setContent(content: string): void
  }
}

export interface KakaoMarkerClustererLike {
  addMarkers(markers: KakaoMarkerLike[]): void
  clear(): void
  setMap?(map: KakaoMapLike | null): void
}

export interface KakaoMapsApi {
  LatLng: new (lat: number, lng: number) => unknown
  Map: new (container: HTMLElement, options: { center: unknown; level: number }) => KakaoMapLike
  Marker: new (options: {
    map?: KakaoMapLike
    position: unknown
    title: string
    image?: unknown
    clickable?: boolean
  }) => KakaoMarkerLike
  MarkerClusterer?: new (options: {
    map: KakaoMapLike
    averageCenter: boolean
    minLevel: number
    gridSize: number
  }) => KakaoMarkerClustererLike
  CustomOverlay?: new (options: {
    map: KakaoMapLike
    position: unknown
    content: string
    yAnchor?: number
  }) => KakaoOverlayLike
  MarkerImage?: new (src: string, size: unknown, options?: { offset?: unknown }) => unknown
  Size?: new (width: number, height: number) => unknown
  Point?: new (x: number, y: number) => unknown
  event: {
    addListener(target: unknown, eventName: string, handler: (...args: unknown[]) => void): void
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

export function clearOverlayMarkers(overlays: KakaoOverlayLike[]) {
  overlays.forEach((overlay) => overlay.setMap(null))
}

export function clearKakaoTransactionClusterer(clusterer: KakaoMarkerClustererLike | null) {
  clusterer?.clear()
  clusterer?.setMap?.(null)
}

export function mapMarkerRenderMode(zoomLevel: number): MapMarkerRenderMode {
  if (zoomLevel <= 3) {
    return {
      showIndividualMarkers: true,
      showTransactionClusterer: false,
      showAdministrativeClusters: false
    }
  }

  return {
    showIndividualMarkers: false,
    showTransactionClusterer: true,
    showAdministrativeClusters: zoomLevel >= 5
  }
}

export function sumRecentTransactionCount(items: PropertyMapItem[]): number {
  return items.reduce((total, item) => total + (item.recentTransactionCount ?? 0), 0)
}

export function formatAdministrativeClusterLabel(cluster: AdministrativeCluster): string {
  const averageLabel =
    typeof cluster.averageDealAmount === 'number'
      ? `평균 ${formatKrw(cluster.averageDealAmount)}`
      : '평균 정보 없음'

  return `${cluster.label}\n${averageLabel}\n거래 ${cluster.transactionCount.toLocaleString('ko-KR')}건`
}

function transactionClusterBadgeContent(count: number): string {
  return `<div class="map-transaction-cluster">거래 ${count.toLocaleString('ko-KR')}건</div>`
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => {
    switch (character) {
      case '&':
        return '&amp;'
      case '<':
        return '&lt;'
      case '>':
        return '&gt;'
      case '"':
        return '&quot;'
      case "'":
        return '&#39;'
      default:
        return character
    }
  })
}

function administrativeClusterContent(cluster: AdministrativeCluster): string {
  const [areaLabel, averageLabel, countLabel] = formatAdministrativeClusterLabel(cluster).split('\n')

  return [
    `<div class="map-admin-cluster" data-cluster-id="${escapeHtml(cluster.clusterId)}">`,
    `<strong>${escapeHtml(areaLabel)}</strong>`,
    `<span>${escapeHtml(averageLabel)}</span>`,
    `<span>${escapeHtml(countLabel)}</span>`,
    '</div>'
  ].join('')
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

export function syncKakaoTransactionClusters(options: {
  kakaoMaps: KakaoMapsApi
  map: KakaoMapLike
  previousClusterer: KakaoMarkerClustererLike | null
  items: PropertyMapItem[]
}): KakaoMarkerClustererLike | null {
  clearKakaoTransactionClusterer(options.previousClusterer)

  if (!options.kakaoMaps.MarkerClusterer) {
    return null
  }

  const markers = options.items.map((item) => {
    const marker = new options.kakaoMaps.Marker({
      position: new options.kakaoMaps.LatLng(item.lat, item.lng),
      title: `property-cluster-${item.propertyId}`
    }) as KakaoTransactionMarkerLike

    marker.recentTransactionCount = item.recentTransactionCount ?? 0

    return marker
  })

  const clusterer = new options.kakaoMaps.MarkerClusterer({
    map: options.map,
    averageCenter: true,
    minLevel: 4,
    gridSize: 80
  })

  clusterer.addMarkers(markers)

  options.kakaoMaps.event.addListener(clusterer, 'clustered', (clusters: unknown) => {
    if (!Array.isArray(clusters)) {
      return
    }

    clusters.forEach((cluster) => {
      const kakaoCluster = cluster as KakaoClusterLike
      const transactionCount = kakaoCluster
        .getMarkers()
        .reduce((total, marker) => total + (marker.recentTransactionCount ?? 0), 0)

      kakaoCluster.getClusterMarker().setContent(transactionClusterBadgeContent(transactionCount))
    })
  })

  return clusterer
}

export function syncAdministrativeClusterOverlays(options: {
  kakaoMaps: KakaoMapsApi
  map: KakaoMapLike
  previousOverlays: KakaoOverlayLike[]
  clusters: AdministrativeCluster[]
}): KakaoOverlayLike[] {
  clearOverlayMarkers(options.previousOverlays)

  if (!options.kakaoMaps.CustomOverlay) {
    return []
  }

  const CustomOverlay = options.kakaoMaps.CustomOverlay

  return options.clusters.map(
    (cluster) =>
      new CustomOverlay({
        map: options.map,
        position: new options.kakaoMaps.LatLng(cluster.centerLat, cluster.centerLng),
        content: administrativeClusterContent(cluster),
        yAnchor: 0.5
      })
  )
}
