<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { favoritesApi } from '@/api/favorites'
import { getApiError } from '@/api/client'
import type { FavoriteApartmentItem, FavoriteAreaItem } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import { formatDate, formatKrw, transactionTypeLabel } from '@/utils/format'

const apartments = ref<FavoriteApartmentItem[]>([])
const areas = ref<FavoriteAreaItem[]>([])
const loading = ref(false)
const deletingPropertyId = ref<number | null>(null)
const deletingAreaId = ref<number | null>(null)
const errorMessage = ref('')
const statusMessage = ref('')

async function fetchFavorites() {
  loading.value = true
  errorMessage.value = ''

  try {
    const [apartmentResponse, areaResponse] = await Promise.all([
      favoritesApi.listApartments(),
      favoritesApi.listAreas()
    ])
    apartments.value = apartmentResponse.items
    areas.value = areaResponse.items
  } catch {
    errorMessage.value = '즐겨찾기를 아직 불러오지 못했습니다. 로그인 상태와 백엔드 API를 확인해 주세요.'
  } finally {
    loading.value = false
  }
}

function areaMapQuery(item: FavoriteAreaItem): Record<string, string> {
  const query: Record<string, string> = {
    areaLabel: item.label
  }

  if (typeof item.centerLat === 'number' && typeof item.centerLng === 'number') {
    query.centerLat = String(item.centerLat)
    query.centerLng = String(item.centerLng)
  }

  if (typeof item.zoomLevel === 'number') {
    query.zoomLevel = String(item.zoomLevel)
  }

  return query
}

function formatAreaLocation(item: FavoriteAreaItem) {
  const regionText = [item.sido, item.sigungu, item.legalDong].filter(Boolean).join(' ')
  if (regionText) {
    return regionText
  }

  if (typeof item.centerLat === 'number' && typeof item.centerLng === 'number') {
    return `지도 중심 ${item.centerLat.toFixed(4)}, ${item.centerLng.toFixed(4)}`
  }

  return '저장된 지도 영역'
}

async function removeApartmentFavorite(propertyId: number) {
  deletingPropertyId.value = propertyId
  errorMessage.value = ''
  statusMessage.value = ''

  try {
    await favoritesApi.removeApartment(propertyId)
    statusMessage.value = '관심 아파트에서 삭제했습니다.'
    await fetchFavorites()
  } catch (error) {
    const apiError = getApiError(error)
    if (apiError?.code === 'FAVORITE_NOT_FOUND') {
      apartments.value = apartments.value.filter((item) => item.propertyId !== propertyId)
      statusMessage.value = '이미 삭제된 관심 아파트입니다.'
      return
    }

    errorMessage.value = '관심 아파트를 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.'
  } finally {
    deletingPropertyId.value = null
  }
}

async function removeAreaFavorite(favoriteAreaId: number) {
  deletingAreaId.value = favoriteAreaId
  errorMessage.value = ''
  statusMessage.value = ''

  try {
    await favoritesApi.removeArea(favoriteAreaId)
    statusMessage.value = '관심 지역에서 삭제했습니다.'
    await fetchFavorites()
  } catch (error) {
    const apiError = getApiError(error)
    if (apiError?.code === 'FAVORITE_AREA_NOT_FOUND') {
      areas.value = areas.value.filter((item) => item.favoriteAreaId !== favoriteAreaId)
      statusMessage.value = '이미 삭제된 관심 지역입니다.'
      return
    }

    errorMessage.value = '관심 지역을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.'
  } finally {
    deletingAreaId.value = null
  }
}

onMounted(fetchFavorites)
</script>

<template>
  <section class="page-heading">
    <p class="eyebrow">즐겨찾기</p>
    <h1>저장한 아파트와 관심 지역을 한곳에서 확인하세요.</h1>
    <p>즐겨찾기는 개인 기록용 기능이며 특정 거래 판단을 대신하지 않습니다.</p>
    <div class="button-row">
      <RouterLink class="secondary-button" to="/chat">챗봇에 질문하기</RouterLink>
    </div>
  </section>

  <p v-if="loading" class="loading-text">즐겨찾기를 불러오고 있습니다.</p>
  <p v-if="statusMessage" class="helper-text">{{ statusMessage }}</p>
  <p v-if="errorMessage" class="inline-error">{{ errorMessage }}</p>

  <section class="two-column">
    <article class="info-panel">
      <div class="section-title">
        <h2>관심 아파트</h2>
        <span>{{ apartments.length }}건</span>
      </div>
      <ul v-if="apartments.length" class="result-list">
        <li v-for="item in apartments" :key="item.favoriteId" class="favorite-item">
          <RouterLink :to="`/properties/${item.propertyId}`">
            <strong>{{ item.name }}</strong>
            <span>{{ item.address }}</span>
            <small>
              <template v-if="item.latestTransaction">
                {{ transactionTypeLabel(item.latestTransaction.transactionType) }}
                {{ formatKrw(item.latestTransaction.dealAmount) }} ·
              </template>
              저장일 {{ formatDate(item.createdAt) }}
            </small>
          </RouterLink>
          <div class="favorite-item-actions">
            <button
              class="text-button secondary"
              data-test="apartment-favorite-delete"
              type="button"
              :disabled="deletingPropertyId === item.propertyId"
              @click="removeApartmentFavorite(item.propertyId)"
            >
              {{ deletingPropertyId === item.propertyId ? '삭제 중' : '삭제' }}
            </button>
          </div>
        </li>
      </ul>
      <EmptyState
        v-else
        title="저장한 아파트가 없습니다."
        description="지도나 상세 화면에서 관심 아파트를 추가하면 여기에 표시됩니다."
      />
    </article>

    <article class="info-panel">
      <div class="section-title">
        <h2>관심 지역</h2>
        <span>{{ areas.length }}건</span>
      </div>
      <ul v-if="areas.length" class="result-list">
        <li v-for="item in areas" :key="item.favoriteAreaId" class="favorite-item">
          <RouterLink :to="{ path: '/map', query: areaMapQuery(item) }">
            <strong>{{ item.label }}</strong>
            <span>{{ formatAreaLocation(item) }}</span>
            <small>
              <template v-if="item.zoomLevel">지도 {{ item.zoomLevel }}단계 · </template>
              저장일 {{ formatDate(item.createdAt) }}
            </small>
          </RouterLink>
          <div class="favorite-item-actions">
            <button
              class="text-button secondary"
              data-test="area-favorite-delete"
              type="button"
              :disabled="deletingAreaId === item.favoriteAreaId"
              @click="removeAreaFavorite(item.favoriteAreaId)"
            >
              {{ deletingAreaId === item.favoriteAreaId ? '삭제 중' : '삭제' }}
            </button>
          </div>
        </li>
      </ul>
      <EmptyState
        v-else
        title="저장한 관심 지역이 없습니다."
        description="지도에서 현재 영역을 관심 지역으로 등록하면 여기에 표시됩니다."
      />
    </article>
  </section>
</template>
