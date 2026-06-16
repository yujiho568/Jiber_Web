<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { propertyApi } from '@/api/property'
import type { PropertyMapItem, PropertyType, TransactionType } from '@/api/types'
import KakaoMapPanel from '@/components/KakaoMapPanel.vue'
import EmptyState from '@/components/EmptyState.vue'
import { SEOUL_SEED_VIEWPORT, type MapViewport } from '@/map/kakaoMap'
import { hasKakaoMapKey } from '@/map/kakaoLoader'
import { formatKrw, propertyTypeLabel, transactionTypeLabel } from '@/utils/format'

const propertyTypeOptions: PropertyType[] = ['APARTMENT', 'OFFICETEL', 'VILLA', 'HOUSE']
const transactionTypeOptions: TransactionType[] = ['SALE', 'JEONSE', 'MONTHLY_RENT']

const selectedPropertyTypes = ref<PropertyType[]>(['APARTMENT'])
const selectedTransactionTypes = ref<TransactionType[]>(['SALE'])
const zoomLevel = ref(SEOUL_SEED_VIEWPORT.zoomLevel)
const loading = ref(false)
const errorMessage = ref('')
const items = ref<PropertyMapItem[]>([])
const currentViewport = ref<MapViewport>({ ...SEOUL_SEED_VIEWPORT })
const selectedPropertyId = ref<number | null>(null)
const hasMapKey = hasKakaoMapKey()
let searchTimer: number | null = null

const resultDescription = computed(() => {
  if (loading.value) {
    return '현재 지도 범위의 실거래 정보를 불러오고 있습니다.'
  }

  if (items.value.length > 0) {
    return `${items.value.length.toLocaleString('ko-KR')}건의 실거래 위치를 찾았습니다.`
  }

  return '현재 조건에 맞는 검색 결과가 없습니다.'
})

async function searchVisibleArea(viewport: MapViewport = currentViewport.value) {
  loading.value = true
  errorMessage.value = ''
  currentViewport.value = viewport
  zoomLevel.value = viewport.zoomLevel

  try {
    const response = await propertyApi.getMapProperties({
      swLat: viewport.swLat,
      swLng: viewport.swLng,
      neLat: viewport.neLat,
      neLng: viewport.neLng,
      zoomLevel: viewport.zoomLevel,
      propertyTypes: selectedPropertyTypes.value,
      transactionTypes: selectedTransactionTypes.value
    })
    items.value = response.items
    if (!response.items.some((item) => item.propertyId === selectedPropertyId.value)) {
      selectedPropertyId.value = null
    }
  } catch {
    items.value = []
    errorMessage.value = '현재 지도 범위의 실거래 정보를 불러오지 못했습니다. 백엔드 서버 상태를 확인해 주세요.'
  } finally {
    loading.value = false
  }
}

function scheduleSearch(viewport: MapViewport) {
  if (searchTimer) {
    window.clearTimeout(searchTimer)
  }

  searchTimer = window.setTimeout(() => {
    void searchVisibleArea(viewport)
  }, 250)
}

function handleMapReady(viewport: MapViewport) {
  currentViewport.value = viewport
  zoomLevel.value = viewport.zoomLevel
  void searchVisibleArea(viewport)
}

function handleBoundsChanged(viewport: MapViewport) {
  currentViewport.value = viewport
  zoomLevel.value = viewport.zoomLevel
  scheduleSearch(viewport)
}

function handleMapLoadError() {
  void searchVisibleArea(SEOUL_SEED_VIEWPORT)
}

function selectProperty(propertyId: number) {
  selectedPropertyId.value = propertyId
  window.requestAnimationFrame(() => {
    document.getElementById(`property-result-${propertyId}`)?.scrollIntoView({
      behavior: 'smooth',
      block: 'nearest'
    })
  })
}

onMounted(() => {
  if (!hasMapKey) {
    void searchVisibleArea(SEOUL_SEED_VIEWPORT)
  }
})

onBeforeUnmount(() => {
  if (searchTimer) {
    window.clearTimeout(searchTimer)
  }
})
</script>

<template>
  <section class="page-heading">
    <p class="eyebrow">지도 검색</p>
    <h1>지역과 거래 조건을 선택해 실거래 위치를 확인하세요.</h1>
  </section>

  <section class="map-layout">
    <aside class="filter-panel" aria-label="지도 검색 조건">
      <h2>검색 조건</h2>
      <fieldset>
        <legend>부동산 유형</legend>
        <label v-for="type in propertyTypeOptions" :key="type" class="check-row">
          <input v-model="selectedPropertyTypes" :value="type" type="checkbox" />
          <span>{{ propertyTypeLabel(type) }}</span>
        </label>
      </fieldset>

      <fieldset>
        <legend>거래 유형</legend>
        <label v-for="type in transactionTypeOptions" :key="type" class="check-row">
          <input v-model="selectedTransactionTypes" :value="type" type="checkbox" />
          <span>{{ transactionTypeLabel(type) }}</span>
        </label>
      </fieldset>

      <label class="field-label" for="zoom-level">지도 확대 단계</label>
      <input id="zoom-level" v-model.number="zoomLevel" min="1" max="14" type="range" />
      <span class="range-value">{{ zoomLevel }}단계</span>

      <button class="primary-button full-width" type="button" :disabled="loading" @click="searchVisibleArea()">
        {{ loading ? '검색 중입니다' : '현재 지도 범위로 검색' }}
      </button>

      <p v-if="errorMessage" class="inline-error">{{ errorMessage }}</p>
      <p class="helper-text">
        {{ hasMapKey ? '지도를 이동하면 현재 화면 범위로 다시 검색합니다.' : '지도 키가 없으면 기본 강남권 범위로 검색합니다.' }}
      </p>
    </aside>

    <div class="map-workspace">
      <KakaoMapPanel
        :items="items"
        :selected-property-id="selectedPropertyId"
        @ready="handleMapReady"
        @bounds-changed="handleBoundsChanged"
        @property-selected="selectProperty"
        @load-error="handleMapLoadError"
      />

      <section class="result-panel" aria-label="검색 결과">
        <div class="section-title">
          <h2>검색 결과</h2>
          <span>{{ items.length }}건</span>
        </div>
        <p class="helper-text">{{ resultDescription }}</p>

        <ul v-if="items.length" class="result-list">
          <li
            v-for="item in items"
            :id="`property-result-${item.propertyId}`"
            :key="item.propertyId"
            :class="{ 'is-selected': selectedPropertyId === item.propertyId }"
            @mouseenter="selectedPropertyId = item.propertyId"
            @focusin="selectedPropertyId = item.propertyId"
          >
            <RouterLink :to="`/properties/${item.propertyId}`" @click="selectedPropertyId = item.propertyId">
              <strong>{{ item.name }}</strong>
              <span>{{ propertyTypeLabel(item.propertyType) }} · {{ item.address }}</span>
              <small v-if="item.latestTransaction">
                {{ transactionTypeLabel(item.latestTransaction.transactionType) }}
                {{ formatKrw(item.latestTransaction.dealAmount) }}
              </small>
            </RouterLink>
          </li>
        </ul>

        <EmptyState
          v-else
          title="검색 결과가 없습니다."
          description="지도를 이동하거나 부동산 유형과 거래 유형 조건을 조정해 보세요."
        />
      </section>
    </div>
  </section>
</template>
