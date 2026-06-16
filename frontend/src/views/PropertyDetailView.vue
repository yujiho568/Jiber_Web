<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { propertyApi } from '@/api/property'
import type { PropertyDetail, ShapValue, ValuationResponse } from '@/api/types'
import ShapChart from '@/charts/ShapChart.vue'
import TransactionChart from '@/charts/TransactionChart.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useAuthStore } from '@/stores/auth'
import { formatDate, formatKrw, propertyTypeLabel } from '@/utils/format'

const route = useRoute()
const authStore = useAuthStore()

const property = ref<PropertyDetail | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const aiMessage = ref('')
const valuation = ref<ValuationResponse | null>(null)
const shapValues = ref<ShapValue[]>([])

const propertyId = computed(() => String(route.params.propertyId))
const aiUnavailableMessage = '아파트 단지에 한해 제공되는 기능입니다.'
const canRequestAi = computed(() => {
  return Boolean(
    authStore.isAuthenticated &&
      property.value?.propertyType === 'APARTMENT' &&
      property.value.ai.valuationAvailable &&
      property.value.ai.shapAvailable
  )
})

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
          <dt>최근 거래일</dt>
          <dd>{{ formatDate(property.summary.latestDealDate) }}</dd>
        </div>
      </dl>
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
