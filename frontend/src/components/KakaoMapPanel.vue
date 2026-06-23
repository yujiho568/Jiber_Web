<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

import type { PropertyMapItem } from '@/api/types'
import {
  DEFAULT_MAP_CENTER,
  DEFAULT_MAP_LEVEL,
  clearMarkers,
  syncKakaoMarkers,
  viewportFromMap,
  type LatLngPoint,
  type KakaoMapLike,
  type KakaoMapsApi,
  type KakaoMarkerLike,
  type MapViewport
} from '@/map/kakaoMap'
import { getKakaoMapFallbackMessage, getKakaoMaps, hasKakaoMapKey, loadKakaoMaps } from '@/map/kakaoLoader'

const props = withDefaults(
  defineProps<{
    items: PropertyMapItem[]
    selectedPropertyId?: number | null
    focusTarget?: LatLngPoint | null
    focusZoomLevel?: number | null
  }>(),
  {
    selectedPropertyId: null,
    focusTarget: null,
    focusZoomLevel: null
  }
)

const emit = defineEmits<{
  ready: [viewport: MapViewport]
  boundsChanged: [viewport: MapViewport]
  propertySelected: [propertyId: number]
  loadError: [message: string]
}>()

const loading = ref(false)
const ready = ref(false)
const message = ref(getKakaoMapFallbackMessage())
const mapElement = ref<HTMLDivElement | null>(null)

let kakaoMaps: KakaoMapsApi | null = null
let map: KakaoMapLike | null = null
let markers: KakaoMarkerLike[] = []
let idleTimer: number | null = null

function emitViewport(eventName: 'ready' | 'boundsChanged') {
  if (!map) {
    return
  }

  const viewport = viewportFromMap(map)

  if (eventName === 'ready') {
    emit('ready', viewport)
    return
  }

  emit('boundsChanged', viewport)
}

function scheduleBoundsChanged() {
  if (idleTimer) {
    window.clearTimeout(idleTimer)
  }

  idleTimer = window.setTimeout(() => emitViewport('boundsChanged'), 180)
}

function renderMarkers() {
  if (!kakaoMaps || !map) {
    return
  }

  markers = syncKakaoMarkers({
    kakaoMaps,
    map,
    previousMarkers: markers,
    items: props.items,
    selectedPropertyId: props.selectedPropertyId,
    onClick: (propertyId) => emit('propertySelected', propertyId)
  })
}

function focusMap(target: LatLngPoint | null) {
  if (!target || !kakaoMaps || !map) {
    return
  }

  const latLng = new kakaoMaps.LatLng(target.lat, target.lng)
  if (props.focusZoomLevel) {
    map.setLevel?.(props.focusZoomLevel)
  }

  if (map.panTo) {
    map.panTo(latLng)
    return
  }

  map.setCenter?.(latLng)
}

onMounted(async () => {
  if (!hasKakaoMapKey()) {
    return
  }

  loading.value = true
  message.value = '카카오 지도를 불러오고 있습니다.'

  try {
    await loadKakaoMaps()
    const maps = getKakaoMaps()
    if (!maps || !mapElement.value) {
      throw new Error('카카오 지도 SDK를 확인하지 못했습니다. JavaScript 키 설정을 확인해 주세요.')
    }

    kakaoMaps = maps
    map = new maps.Map(mapElement.value, {
      center: new maps.LatLng(DEFAULT_MAP_CENTER.lat, DEFAULT_MAP_CENTER.lng),
      level: DEFAULT_MAP_LEVEL
    })
    maps.event.addListener(map, 'idle', scheduleBoundsChanged)

    ready.value = true
    message.value = '지도를 움직이면 현재 화면 범위로 검색합니다.'
    emitViewport('ready')
    renderMarkers()
    focusMap(props.focusTarget)
  } catch (error) {
    message.value = error instanceof Error ? error.message : '카카오 지도를 불러오지 못했습니다.'
    emit('loadError', message.value)
  } finally {
    loading.value = false
  }
})

watch(() => [props.items, props.selectedPropertyId], renderMarkers, { deep: true })
watch(() => [props.focusTarget, props.focusZoomLevel], () => focusMap(props.focusTarget), { deep: true })

onBeforeUnmount(() => {
  if (idleTimer) {
    window.clearTimeout(idleTimer)
  }
  clearMarkers(markers)
  markers = []
})
</script>

<template>
  <section class="map-panel" :class="{ 'is-ready': ready }" aria-label="지도 영역">
    <div ref="mapElement" class="map-canvas" aria-hidden="true"></div>
    <div v-if="!ready" class="map-grid" aria-hidden="true">
      <span class="map-pin pin-one"></span>
      <span class="map-pin pin-two"></span>
      <span class="map-pin pin-three"></span>
    </div>
    <div v-if="!ready" class="map-message">
      <strong>{{ ready ? '카카오 지도 준비 완료' : '지도 준비 중' }}</strong>
      <p>{{ loading ? '지도 리소스를 확인하고 있습니다.' : message }}</p>
    </div>
    <p v-else class="map-status">{{ message }}</p>
  </section>
</template>
