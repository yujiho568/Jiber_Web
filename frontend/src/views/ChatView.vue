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
    content: '부동산 챗봇은 현재 계약 skeleton 단계입니다. 실제 RAG와 모델은 추후 연결됩니다.'
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
  return fileName.replace(/\.[^.]+$/, '')
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
    errorMessage.value = '챗봇 답변을 불러오지 못했습니다. 백엔드 연결을 확인해 주세요.'
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
    <p class="eyebrow">AI 챗봇</p>
    <h1>부동산 챗봇</h1>
    <p>전세 계약, 실거래, 가격예측과 XAI 설명에 관한 질문을 남길 수 있습니다.</p>
  </section>

  <section class="chat-layout">
    <article class="chat-notice">
      챗봇 답변은 참고용 정보입니다. 실제 계약, 매수·매도, 법률·세무 판단은 전문가와 공식 자료를 함께 확인하세요.
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
      <p v-if="loading" class="loading-text">답변을 요청하고 있습니다.</p>
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
