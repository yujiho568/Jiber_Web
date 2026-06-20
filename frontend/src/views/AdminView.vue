<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'

import { noticesApi } from '@/api/notices'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const title = ref('')
const content = ref('')
const pinned = ref(false)
const publishedAt = ref(new Date().toISOString().slice(0, 16))
const statusMessage = ref('')
const saving = ref(false)

async function createNotice() {
  saving.value = true
  statusMessage.value = ''

  try {
    const response = await noticesApi.create({
      title: title.value,
      content: content.value,
      pinned: pinned.value,
      publishedAt: new Date(publishedAt.value).toISOString()
    })
    statusMessage.value = response.message
    title.value = ''
    content.value = ''
    pinned.value = false
  } catch {
    statusMessage.value = '공지사항을 저장하지 못했습니다. 관리자 권한과 백엔드 API를 확인해 주세요.'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="page-heading">
    <p class="eyebrow">관리자</p>
    <h1>공지사항을 등록하고 운영 상태를 확인합니다.</h1>
    <div class="button-row">
      <RouterLink class="secondary-button" to="/chat">챗봇에 질문하기</RouterLink>
    </div>
    <p v-if="!authStore.isAdmin" class="inline-error">관리자 권한이 필요합니다.</p>
  </section>

  <form class="admin-form" @submit.prevent="createNotice">
    <label>
      제목
      <input v-model="title" required maxlength="120" type="text" placeholder="공지사항 제목" />
    </label>
    <label>
      내용
      <textarea v-model="content" required rows="8" placeholder="공지사항 내용을 입력하세요."></textarea>
    </label>
    <label>
      게시 일시
      <input v-model="publishedAt" required type="datetime-local" />
    </label>
    <label class="check-row">
      <input v-model="pinned" type="checkbox" />
      <span>상단 고정</span>
    </label>
    <button class="primary-button" type="submit" :disabled="saving">
      {{ saving ? '저장 중입니다' : '공지사항 등록' }}
    </button>
    <p v-if="statusMessage" class="helper-text">{{ statusMessage }}</p>
  </form>
</template>
