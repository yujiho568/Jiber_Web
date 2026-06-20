<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { favoritesApi } from '@/api/favorites'
import type { FavoriteApartmentItem, FavoriteAreaItem } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import { formatDate, formatKrw } from '@/utils/format'

const apartments = ref<FavoriteApartmentItem[]>([])
const areas = ref<FavoriteAreaItem[]>([])
const loading = ref(false)
const errorMessage = ref('')

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
  <p v-if="errorMessage" class="inline-error">{{ errorMessage }}</p>

  <section class="two-column">
    <article class="info-panel">
      <div class="section-title">
        <h2>관심 아파트</h2>
        <span>{{ apartments.length }}건</span>
      </div>
      <ul v-if="apartments.length" class="result-list">
        <li v-for="item in apartments" :key="item.favoriteId">
          <RouterLink :to="`/properties/${item.propertyId}`">
            <strong>{{ item.name }}</strong>
            <span>{{ item.address }}</span>
            <small>
              {{ formatKrw(item.latestTransaction?.dealAmount) }} · 저장일 {{ formatDate(item.createdAt) }}
            </small>
          </RouterLink>
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
        <li v-for="item in areas" :key="item.favoriteAreaId">
          <strong>{{ item.label }}</strong>
          <span>{{ item.sido }} {{ item.sigungu }} {{ item.legalDong }}</span>
          <small>저장일 {{ formatDate(item.createdAt) }}</small>
        </li>
      </ul>
      <EmptyState
        v-else
        title="저장한 관심 지역이 없습니다."
        description="지도 검색에서 자주 보는 지역을 저장하면 빠르게 다시 찾을 수 있습니다."
      />
    </article>
  </section>
</template>
