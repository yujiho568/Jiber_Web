<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { favoritesApi } from '@/api/favorites'
import { propertyApi } from '@/api/property'
import { getApiError } from '@/api/client'
import type { PropertyDetail, ShapValue, ValuationResponse } from '@/api/types'
import ShapChart from '@/charts/ShapChart.vue'
import TransactionChart from '@/charts/TransactionChart.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useAuthStore } from '@/stores/auth'
import { formatDate, formatKrw, propertyTypeLabel, transactionTypeLabel } from '@/utils/format'

const route = useRoute()
const authStore = useAuthStore()

const property = ref<PropertyDetail | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const aiMessage = ref('')
const favoriteMessage = ref('')
const favoriteErrorMessage = ref('')
const favoriteUpdating = ref(false)
const valuation = ref<ValuationResponse | null>(null)
const shapValues = ref<ShapValue[]>([])

const propertyId = computed(() => String(route.params.propertyId))
const aiUnavailableMessage = '아파트 단지에 한해 제공되는 기능입니다.'
const latestTransaction = computed(() => {
  if (!property.value?.transactions.length) {
    return null
  }

  return (
    property.value.transactions.find((transaction) => transaction.dealDate === property.value?.summary.latestDealDate) ??
    property.value.transactions[0]
  )
})
const recentTransactionCount = computed(() => property.value?.transactions.length ?? 0)
const isApartmentFavorite = computed(() => Boolean(property.value?.favorite?.apartmentFavorited))
const canRequestAi = computed(() => {
  return Boolean(
    authStore.isAuthenticated &&
      property.value?.propertyType === 'APARTMENT' &&
      property.value.ai.valuationAvailable &&
      property.value.ai.shapAvailable
  )
})

function setApartmentFavorite(nextValue: boolean) {
  if (!property.value) {
    return
  }

  property.value.favorite = {
    apartmentFavorited: nextValue,
    areaFavorited: property.value.favorite?.areaFavorited ?? false
  }
}

function setFavoriteFailure(error: unknown, fallbackMessage: string) {
  const apiError = getApiError(error)

  if (apiError?.code === 'FAVORITE_ALREADY_EXISTS') {
    setApartmentFavorite(true)
    favoriteMessage.value = '이미 관심 아파트에 저장되어 있습니다.'
    return
  }

  if (apiError?.code === 'FAVORITE_NOT_FOUND') {
    setApartmentFavorite(false)
    favoriteMessage.value = '이미 삭제된 관심 아파트입니다.'
    return
  }

  if (apiError?.code === 'PROPERTY_NOT_FOUND') {
    favoriteErrorMessage.value = '부동산 정보를 찾을 수 없습니다.'
    return
  }

  if (apiError?.code === 'AUTH_REQUIRED') {
    favoriteErrorMessage.value = '로그인이 필요한 기능입니다.'
    return
  }

  favoriteErrorMessage.value = fallbackMessage
}

async function fetchProperty() {
  loading.value = true
  errorMessage.value = ''

  try {
    property.value = await propertyApi.getProperty(propertyId.value)
  } catch {
    errorMessage.value = '부동산 상세 정보를 아직 불러오지 못했습니다. 백엔드 API 연결을 확인해 주세요.'
  } finally {
    loading.value = false
  }
}

async function requestAiExplanation() {
  aiMessage.value = ''
  valuation.value = null
  shapValues.value = []

  if (!authStore.isAuthenticated) {
    aiMessage.value = '로그인이 필요한 기능입니다.'
    return
  }

  if (!canRequestAi.value) {
    aiMessage.value = aiUnavailableMessage
    return
  }

  try {
    const payload = {
      exclusiveAreaM2: 84.95,
      floor: 15,
      asOfDate: new Date().toISOString().slice(0, 10)
    }
    valuation.value = await propertyApi.requestValuation(propertyId.value, payload)
    const shap = await propertyApi.requestShap(propertyId.value, payload)
    shapValues.value = shap.values
    aiMessage.value = valuation.value.message || shap.message
  } catch {
    aiMessage.value = '추정가와 SHAP 요인을 아직 불러오지 못했습니다. 로그인 상태와 백엔드 API를 확인해 주세요.'
  }
}

async function toggleApartmentFavorite() {
  favoriteMessage.value = ''
  favoriteErrorMessage.value = ''

  if (!property.value) {
    return
  }

  if (!authStore.isAuthenticated) {
    favoriteMessage.value = '로그인 후 관심 아파트를 저장할 수 있습니다.'
    return
  }

  if (property.value.propertyType !== 'APARTMENT') {
    favoriteMessage.value = '아파트 단지만 관심 아파트로 저장할 수 있습니다.'
    return
  }

  favoriteUpdating.value = true

  try {
    if (isApartmentFavorite.value) {
      await favoritesApi.removeApartment(property.value.propertyId)
      setApartmentFavorite(false)
      favoriteMessage.value = '관심 아파트에서 삭제했습니다.'
      return
    }

    await favoritesApi.addApartment(property.value.propertyId)
    setApartmentFavorite(true)
    favoriteMessage.value = '관심 아파트에 추가했습니다.'
  } catch (error) {
    setFavoriteFailure(error, '관심 아파트 상태를 변경하지 못했습니다. 잠시 후 다시 시도해 주세요.')
  } finally {
    favoriteUpdating.value = false
  }
}

onMounted(fetchProperty)
</script>

<template>
  <section class="page-heading">
    <p class="eyebrow">부동산 상세</p>
    <h1>{{ property?.name ?? `부동산 #${propertyId}` }}</h1>
    <p v-if="property">
      {{ propertyTypeLabel(property.propertyType) }} · {{ property.address.sido }} {{ property.address.sigungu }}
      {{ property.address.legalDong }}
    </p>
  </section>

  <p v-if="loading" class="loading-text">상세 정보를 불러오고 있습니다.</p>
  <p v-if="errorMessage" class="inline-error">{{ errorMessage }}</p>

  <section v-if="property" class="detail-grid">
    <article class="info-panel">
      <h2>단지 요약</h2>
      <dl class="summary-list">
        <div>
          <dt>준공연도</dt>
          <dd>{{ property.summary.builtYear ?? '정보 없음' }}</dd>
        </div>
        <div>
          <dt>세대수</dt>
          <dd>{{ property.summary.householdCount?.toLocaleString('ko-KR') ?? '정보 없음' }}</dd>
        </div>
        <div>
          <dt>최근 거래금액</dt>
          <dd>{{ formatKrw(property.summary.latestDealAmount) }}</dd>
        </div>
        <div>
          <dt>최근 거래유형</dt>
          <dd>{{ latestTransaction ? transactionTypeLabel(latestTransaction.transactionType) : '정보 없음' }}</dd>
        </div>
        <div>
          <dt>최근 거래일</dt>
          <dd>{{ formatDate(property.summary.latestDealDate) }}</dd>
        </div>
        <div>
          <dt>거래 건수</dt>
          <dd>최근 거래 {{ recentTransactionCount.toLocaleString('ko-KR') }}건</dd>
        </div>
      </dl>
      <div class="favorite-actions">
        <button
          class="secondary-button"
          data-test="apartment-favorite-button"
          type="button"
          :disabled="favoriteUpdating"
          @click="toggleApartmentFavorite"
        >
          {{
            favoriteUpdating
              ? '처리 중입니다'
              : isApartmentFavorite
                ? '관심 아파트 삭제'
                : authStore.isAuthenticated
                  ? '관심 아파트 추가'
                  : '로그인 후 즐겨찾기'
          }}
        </button>
        <p v-if="favoriteMessage" class="helper-text">{{ favoriteMessage }}</p>
        <p v-if="favoriteErrorMessage" class="inline-error">{{ favoriteErrorMessage }}</p>
      </div>
    </article>

    <article class="info-panel">
      <h2>AI 분석</h2>
      <p v-if="!authStore.isAuthenticated" class="muted">추정가와 SHAP 요인은 로그인 후 확인할 수 있습니다.</p>
      <p v-else-if="!canRequestAi" class="muted">{{ aiUnavailableMessage }}</p>
      <p v-else class="muted">아파트 실거래 데이터를 바탕으로 계산한 추정과 주요 요인 설명을 요청합니다.</p>
      <button class="primary-button" type="button" @click="requestAiExplanation">추정가와 요인 보기</button>
      <p v-if="aiMessage" class="helper-text">{{ aiMessage }}</p>
      <p v-if="valuation?.estimatedPrice" class="estimate-text">
        추정가 {{ formatKrw(valuation.estimatedPrice) }}
      </p>
    </article>
  </section>

  <section class="chart-grid">
    <article class="info-panel">
      <h2>거래 차트</h2>
      <TransactionChart :transactions="property?.transactions ?? []" />
    </article>
    <article class="info-panel">
      <h2>SHAP 요인 차트</h2>
      <ShapChart :values="shapValues" />
    </article>
  </section>

  <EmptyState
    v-if="!loading && !property && !errorMessage"
    title="상세 정보를 기다리고 있습니다."
    description="부동산 상세 API가 연결되면 기본 정보와 거래 내역을 표시합니다."
  />
</template>
