<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { chatApi } from '@/api/chat'
import type { ChatContext } from '@/api/types'
import { useChatContextStore } from '@/stores/chatContext'
import { formatKrw } from '@/utils/format'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  contexts?: ChatContext[]
}

interface SourceSummary {
  source: string
  label: string
}

const messages = ref<ChatMessage[]>([
  {
    role: 'assistant',
    content: '부동산 계약, 실거래, 통계, 가격예측/XAI 문서에 근거해서 답변합니다.'
  }
])
const question = ref('')
const loading = ref(false)
const errorMessage = ref('')
const route = useRoute()
const chatContextStore = useChatContextStore()

const canSubmit = computed(() => question.value.trim().length > 0 && !loading.value)
const activeContext = computed(() => chatContextStore.runtimeContext)
const estimatedPriceText = computed(() => formatKrw(activeContext.value?.valuation?.estimatedPrice))

const SOURCE_LABELS: Record<string, string> = {
  'r_one_2026_05_housing_price_trend_report.pdf': '2026년 5월 전국주택가격동향조사 보고서',
  'r_one_2026_05_housing_price_trend_report.md': '2026년 5월 전국주택가격동향조사 보고서',
  'r_one_2026_04_apartment_actual_transaction_price_index_report.pdf': '2026년 4월 공동주택 실거래가격지수 보고서',
  'r_one_2026_04_apartment_actual_transaction_price_index_report.md': '2026년 4월 공동주택 실거래가격지수 보고서',
  'r_one_2026_05_officetel_price_trend_report.pdf': '2026년 5월 오피스텔가격동향조사 보고서',
  'r_one_2026_05_officetel_price_trend_report.md': '2026년 5월 오피스텔가격동향조사 보고서',
  'r_one_2026_06_15_weekly_apartment_price_trend_table.md': '2026년 6월 15일 기준 주간아파트가격 동향 통계표',
  'safe_jeonse_contract_checklist.pdf': '안심 전세계약 체크리스트',
  'safe_jeonse_contract_checklist.md': '안심 전세계약 체크리스트',
  'rtms_main.txt': '국토교통부 실거래가 공개시스템 안내',
  'rtms_main.md': '국토교통부 실거래가 공개시스템 안내',
  'rtech_faq.txt': '부동산테크 FAQ',
  'rtech_faq.md': '부동산테크 FAQ',
  'rtech_library.txt': '부동산테크 자료실',
  'rtech_library.md': '부동산테크 자료실',
  'r_one_statistics_dictionary.txt': '한국부동산원 R-ONE 통계용어 사전',
  'r_one_statistics_dictionary.md': '한국부동산원 R-ONE 통계용어 사전',
  'r_one_stat_meta.txt': '한국부동산원 R-ONE 통계 메타 설명',
  'r_one_stat_meta.md': '한국부동산원 R-ONE 통계 메타 설명',
  'r_one_reports.txt': '한국부동산원 R-ONE 공표보고서 목록',
  'r_one_reports.md': '한국부동산원 R-ONE 공표보고서 목록',
  'r_one_main.md': '한국부동산원 R-ONE 서비스 안내',
  'rtech_main.md': '부동산테크 서비스 안내',
  'law_real_estate_transaction_reporting.md': '부동산 거래신고 등에 관한 법률 안내',
  'law_real_estate_transaction_reporting_detail.txt': '부동산 거래신고 등에 관한 법률',
  'law_real_estate_transaction_reporting_detail.md': '부동산 거래신고 등에 관한 법률',
  'law_housing_lease_protection_detail.txt': '주택임대차보호법',
  'law_housing_lease_protection_detail.md': '주택임대차보호법',
  'law_licensed_real_estate_agent_detail.txt': '공인중개사법',
  'law_licensed_real_estate_agent_detail.md': '공인중개사법',
  'law_apartment_management_detail.txt': '공동주택관리법',
  'law_apartment_management_detail.md': '공동주택관리법'
}

function renderMessage(content: string): string {
  return content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
    .replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
}

function sourceLabel(source: string): string {
  const normalizedSource = source.split('#')[0]
  const fileName = normalizedSource.split('/').pop() ?? normalizedSource
  return SOURCE_LABELS[fileName] ?? fileName.replace(/\.[^.]+$/, '')
}

function sourceKey(source: string): string {
  return source.split('#')[0]
}

function sourceSummaries(contexts?: ChatContext[]): SourceSummary[] {
  if (!contexts?.length) {
    return []
  }

  const seen = new Set<string>()
  return contexts
    .filter((context) => {
      const key = sourceKey(context.source)
      if (seen.has(key)) {
        return false
      }
      seen.add(key)
      return true
    })
    .map((context) => ({
      source: sourceKey(context.source),
      label: sourceLabel(context.source)
    }))
}

async function submitQuestion() {
  const trimmed = question.value.trim()
  if (!trimmed || loading.value) {
    return
  }

  messages.value.push({ role: 'user', content: trimmed })
  question.value = ''
  loading.value = true
  errorMessage.value = ''

  try {
    const response = await chatApi.askRealEstate({
      question: trimmed,
      runtimeContext: activeContext.value ?? undefined
    })
    messages.value.push({
      role: 'assistant',
      content: response.answer,
      contexts: response.contexts
    })
  } catch {
    errorMessage.value = '챗봇 답변을 불러오지 못했습니다. model-server와 백엔드 연결을 확인해 주세요.'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  const queryQuestion = typeof route.query.q === 'string' ? route.query.q.trim() : ''
  if (queryQuestion) {
    question.value = queryQuestion
    void submitQuestion()
  }
})
</script>

<template>
  <section class="page-heading">
    <p class="eyebrow">RAG 챗봇</p>
    <h1>부동산 문서 챗봇</h1>
    <p>전세 계약, 실거래, 통계, 가격예측과 XAI 설명에 관한 질문을 문서 기반으로 확인하세요.</p>
  </section>

  <section class="chat-layout">
    <article class="chat-notice">
      챗봇 답변은 문서와 입력된 분석 컨텍스트를 바탕으로 한 참고용 정보입니다. 실제 계약, 매수·매도, 법적 판단은 전문가와 공식 자료를 함께 확인하세요.
    </article>

    <article v-if="activeContext" class="chat-runtime-context">
      <div>
        <p class="eyebrow">현재 분석 컨텍스트</p>
        <h2>{{ activeContext.property.name }}</h2>
        <p>{{ activeContext.property.address }}</p>
        <p>추정가 {{ estimatedPriceText }}</p>
      </div>
      <button class="secondary-button" type="button" @click="chatContextStore.clearRuntimeContext()">컨텍스트 해제</button>
    </article>

    <article class="chat-panel" aria-live="polite">
      <div v-for="(message, index) in messages" :key="index" class="chat-message" :class="`is-${message.role}`">
        <p v-html="renderMessage(message.content)"></p>
        <details v-if="message.contexts?.length" class="chat-contexts">
          <summary>검색 근거 문서 {{ sourceSummaries(message.contexts).length }}개</summary>
          <ol>
            <li v-for="source in sourceSummaries(message.contexts)" :key="`${index}-${source.source}`">
              <strong>{{ source.label }}</strong>
            </li>
          </ol>
        </details>
      </div>
      <p v-if="loading" class="loading-text">문서를 검색하고 답변을 생성하고 있습니다.</p>
      <p v-if="errorMessage" class="inline-error">{{ errorMessage }}</p>
    </article>

    <form class="chat-form" @submit.prevent="submitQuestion">
      <label for="chat-question">질문</label>
      <textarea
        id="chat-question"
        v-model="question"
        rows="4"
        placeholder="예: 전세 계약 전에 확인해야 할 핵심 사항을 알려줘"
      ></textarea>
      <button class="primary-button" type="submit" :disabled="!canSubmit">질문하기</button>
    </form>
  </section>
</template>
