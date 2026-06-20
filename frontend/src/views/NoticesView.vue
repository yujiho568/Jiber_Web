<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { noticesApi } from '@/api/notices'
import type { NoticeSummary } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import { formatDate } from '@/utils/format'

const keyword = ref('')
const notices = ref<NoticeSummary[]>([])
const loading = ref(false)
const errorMessage = ref('')

async function fetchNotices() {
  loading.value = true
  errorMessage.value = ''

  try {
    const response = await noticesApi.list({
      keyword: keyword.value,
      sort: 'publishedAt,desc',
      page: 0,
      size: 20
    })
    notices.value = response.items
  } catch {
    notices.value = []
    errorMessage.value = '공지사항을 아직 불러오지 못했습니다. 백엔드 API 연결을 확인해 주세요.'
  } finally {
    loading.value = false
  }
}

onMounted(fetchNotices)
</script>

<template>
  <section class="page-heading">
    <p class="eyebrow">공지사항</p>
    <h1>서비스 운영 안내를 확인하세요.</h1>
    <div class="button-row">
      <RouterLink class="secondary-button" to="/chat">챗봇에 질문하기</RouterLink>
    </div>
  </section>

  <form class="search-row" @submit.prevent="fetchNotices">
    <label class="visually-hidden" for="notice-keyword">공지사항 검색어</label>
    <input id="notice-keyword" v-model="keyword" type="search" placeholder="공지사항 검색어" />
    <button class="primary-button" type="submit">{{ loading ? '검색 중입니다' : '검색' }}</button>
  </form>

  <p v-if="errorMessage" class="inline-error">{{ errorMessage }}</p>

  <ul v-if="notices.length" class="notice-list">
    <li v-for="notice in notices" :key="notice.noticeId">
      <span v-if="notice.pinned" class="tag">고정</span>
      <strong>{{ notice.title }}</strong>
      <p>{{ notice.summary }}</p>
      <small>{{ formatDate(notice.publishedAt) }}</small>
    </li>
  </ul>

  <EmptyState
    v-else-if="!loading"
    title="등록된 공지사항이 없습니다."
    description="서비스 안내가 등록되면 이 화면에 표시됩니다."
  />

  <p v-if="loading" class="loading-text">공지사항을 불러오고 있습니다.</p>
</template>
