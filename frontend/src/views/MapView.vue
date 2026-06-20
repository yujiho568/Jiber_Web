<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { propertyApi } from '@/api/property'
import type { PropertyMapItem, PropertySearchItem, PropertyType, TransactionType } from '@/api/types'
import KakaoMapPanel from '@/components/KakaoMapPanel.vue'
import EmptyState from '@/components/EmptyState.vue'
import { SEOUL_SEED_VIEWPORT, type LatLngPoint, type MapViewport } from '@/map/kakaoMap'
import { hasKakaoMapKey } from '@/map/kakaoLoader'
import { formatKrw, propertyTypeLabel, transactionTypeLabel } from '@/utils/format'

const propertyTypeOptions: PropertyType[] = ['APARTMENT', 'OFFICETEL', 'VILLA', 'HOUSE']
const transactionTypeOptions: TransactionType[] = ['SALE', 'JEONSE', 'MONTHLY_RENT']

const selectedPropertyTypes = ref<PropertyType[]>(['APARTMENT'])
const selectedTransactionTypes = ref<TransactionType[]>([...transactionTypeOptions])
const zoomLevel = ref(SEOUL_SEED_VIEWPORT.zoomLevel)
const loading = ref(false)
const errorMessage = ref('')
const items = ref<PropertyMapItem[]>([])
const currentViewport = ref<MapViewport>({ ...SEOUL_SEED_VIEWPORT })
const selectedPropertyId = ref<number | null>(null)
const searchKeyword = ref('')
const activeSearchKeyword = ref('')
const mapFocusTarget = ref<LatLngPoint | null>(null)
const hasMapKey = hasKakaoMapKey()
let searchTimer: number | null = null

const isKeywordSearch = computed(() => activeSearchKeyword.value.length > 0)

const resultDescription = computed(() => {
  if (loading.value) {
    return isKeywordSearch.value
      ? '검색어와 거래 조건에 맞는 실거래 정보를 불러오고 있습니다.'
      : '현재 지도 범위의 실거래 정보를 불러오고 있습니다.'
  }

  if (items.value.length > 0) {
    const count = items.value.length.toLocaleString('ko-KR')
    return isKeywordSearch.value
      ? `"${activeSearchKeyword.value}" 검색 결과 ${count}건을 찾았습니다.`
      : `${count}건의 실거래 위치를 찾았습니다.`
  }

  return isKeywordSearch.value
    ? `"${activeSearchKeyword.value}"에 맞는 검색 결과가 없습니다. 검색어 또는 거래 조건을 조정해 보세요.`
    : '현재 조건에 맞는 검색 결과가 없습니다.'
})

function toMapItem(item: PropertySearchItem): PropertyMapItem {
  return {
    propertyId: item.propertyId,
    propertyType: item.propertyType,
    name: item.name,
    address: item.address,
    lat: item.lat,
    lng: item.lng,
    latestTransaction: item.latestTransaction,
    dealCount: item.latestTransaction ? 1 : 0,
    aiAvailable: item.aiAvailable
  }
}

function focusFirstResult(nextItems: PropertyMapItem[]) {
  const firstItem = nextItems.find((item) => Number.isFinite(item.lat) && Number.isFinite(item.lng))
  selectedPropertyId.value = firstItem?.propertyId ?? null
  mapFocusTarget.value = firstItem ? { lat: firstItem.lat, lng: firstItem.lng } : null
}

async function searchVisibleArea(viewport: MapViewport = currentViewport.value) {
  loading.value = true
  errorMessage.value = ''
  activeSearchKeyword.value = ''
  mapFocusTarget.value = null
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

async function searchByKeyword(keyword: string) {
  loading.value = true
  errorMessage.value = ''
  activeSearchKeyword.value = keyword

  try {
    const response = await propertyApi.searchProperties({
      keyword,
      propertyTypes: selectedPropertyTypes.value,
      transactionTypes: selectedTransactionTypes.value,
      size: 20,
      sort: 'relevance,desc'
    })
    const nextItems = response.items.map(toMapItem)
    items.value = nextItems
    focusFirstResult(nextItems)
  } catch {
    items.value = []
    selectedPropertyId.value = null
    mapFocusTarget.value = null
    errorMessage.value = '검색 결과를 불러오지 못했습니다. 검색어를 확인하거나 잠시 후 다시 시도해 주세요.'
  } finally {
    loading.value = false
  }
}

function handleKeywordSubmit() {
  const keyword = searchKeyword.value.trim()

  if (!keyword) {
    searchKeyword.value = ''
    void searchVisibleArea()
    return
  }

  void searchByKeyword(keyword)
}

function resetToVisibleArea() {
  searchKeyword.value = ''
  void searchVisibleArea()
}

function scheduleSearch(viewport: MapViewport) {
  if (isKeywordSearch.value) {
    return
  }

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
  if (!isKeywordSearch.value) {
    void searchVisibleArea(viewport)
  }
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
    <div class="button-row">
      <RouterLink class="secondary-button" to="/chat">챗봇에 질문하기</RouterLink>
    </div>
  </section>

  <section class="map-layout">
    <aside class="filter-panel" aria-label="지도 검색 조건">
      <h2>검색 조건</h2>
      <form class="map-search-form" data-test="map-search-form" @submit.prevent="handleKeywordSubmit">
        <label class="field-label" for="map-search-keyword">단지명 또는 지역 검색</label>
        <div class="search-row compact">
          <input
            id="map-search-keyword"
            v-model="searchKeyword"
            data-test="map-search-keyword"
            type="search"
            autocomplete="off"
            placeholder="경희궁롯데캐슬, 무악동, 종로구"
          />
          <button class="primary-button" type="submit" :disabled="loading">
            {{ loading && searchKeyword.trim() ? '검색 중' : '검색' }}
          </button>
        </div>
        <button
          v-if="isKeywordSearch"
          class="text-button full-width"
          type="button"
          :disabled="loading"
          @click="resetToVisibleArea"
        >
          검색어 지우고 지도 범위 보기
        </button>
      </form>

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

      <button class="primary-button full-width" type="button" :disabled="loading" @click="resetToVisibleArea">
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
        :focus-target="mapFocusTarget"
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
          :description="
            isKeywordSearch
              ? '단지명이나 지역명을 다시 입력하거나 거래 유형 조건을 조정해 보세요.'
              : '지도를 이동하거나 부동산 유형과 거래 유형 조건을 조정해 보세요.'
          "
        />
      </section>
    </div>
  </section>
</template>
