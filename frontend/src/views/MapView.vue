<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'

import { propertyApi } from '@/api/property'
import type { PropertyMapItem, PropertyType, TransactionType } from '@/api/types'
import KakaoMapPanel from '@/components/KakaoMapPanel.vue'
import EmptyState from '@/components/EmptyState.vue'
import { formatKrw, propertyTypeLabel, transactionTypeLabel } from '@/utils/format'

const propertyTypeOptions: PropertyType[] = ['APARTMENT', 'OFFICETEL', 'VILLA', 'HOUSE']
const transactionTypeOptions: TransactionType[] = ['SALE', 'JEONSE', 'MONTHLY_RENT']

const selectedPropertyTypes = ref<PropertyType[]>(['APARTMENT'])
const selectedTransactionTypes = ref<TransactionType[]>(['SALE'])
const zoomLevel = ref(5)
const loading = ref(false)
const errorMessage = ref('')
const items = ref<PropertyMapItem[]>([])

async function searchVisibleArea() {
  loading.value = true
  errorMessage.value = ''

  try {
    const response = await propertyApi.getMapProperties({
      swLat: 37.48,
      swLng: 127.01,
      neLat: 37.52,
      neLng: 127.06,
      zoomLevel: zoomLevel.value,
      propertyTypes: selectedPropertyTypes.value,
      transactionTypes: selectedTransactionTypes.value
    })
    items.value = response.items
  } catch {
    items.value = []
    errorMessage.value = '지도 검색 API를 아직 불러오지 못했습니다. 백엔드 서버 상태를 확인해 주세요.'
  } finally {
    loading.value = false
  }
}
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

      <button class="primary-button full-width" type="button" :disabled="loading" @click="searchVisibleArea">
        {{ loading ? '검색 중입니다' : '이 조건으로 검색' }}
      </button>

      <p v-if="errorMessage" class="inline-error">{{ errorMessage }}</p>
    </aside>

    <div class="map-workspace">
      <KakaoMapPanel />

      <section class="result-panel" aria-label="검색 결과">
        <div class="section-title">
          <h2>검색 결과</h2>
          <span>{{ items.length }}건</span>
        </div>

        <ul v-if="items.length" class="result-list">
          <li v-for="item in items" :key="item.propertyId">
            <RouterLink :to="`/properties/${item.propertyId}`">
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
          title="아직 검색 결과가 없습니다."
          description="지도가 연결되면 현재 화면 범위의 실거래 정보를 불러옵니다."
        />
      </section>
    </div>
  </section>
</template>
