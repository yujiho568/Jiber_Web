<script setup lang="ts">
import { computed, ref } from 'vue'

import { chatApi } from '@/api/chat'
import type { ChatContext } from '@/api/types'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  contexts?: ChatContext[]
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

const canSubmit = computed(() => question.value.trim().length > 0 && !loading.value)

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
    const response = await chatApi.askRealEstate({ question: trimmed })
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
</script>

<template>
  <section class="page-heading">
    <p class="eyebrow">RAG 챗봇</p>
    <h1>부동산 문서 챗봇</h1>
    <p>전세 계약, 실거래, 통계, 가격예측과 XAI 설명에 관한 질문을 문서 기반으로 확인하세요.</p>
  </section>

  <section class="chat-layout">
    <article class="chat-panel" aria-live="polite">
      <div v-for="(message, index) in messages" :key="index" class="chat-message" :class="`is-${message.role}`">
        <p>{{ message.content }}</p>
        <details v-if="message.contexts?.length" class="chat-contexts">
          <summary>검색 근거 {{ message.contexts.length }}개</summary>
          <ol>
            <li v-for="context in message.contexts" :key="`${index}-${context.source}-${context.text.slice(0, 20)}`">
              <strong>{{ context.source }}</strong>
              <span>{{ context.text.slice(0, 220) }}</span>
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
