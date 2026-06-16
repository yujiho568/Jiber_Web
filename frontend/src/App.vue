<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'

import AppHeader from '@/components/AppHeader.vue'

const route = useRoute()

const guardMessage = computed(() => {
  if (route.query.auth === 'AUTH_REQUIRED') {
    return '로그인이 필요한 화면입니다. 소셜 로그인 후 다시 이용해 주세요.'
  }

  if (route.query.auth === 'ACCESS_DENIED') {
    return '관리자 권한이 필요한 화면입니다.'
  }

  return ''
})
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main class="app-main" aria-live="polite">
      <p v-if="guardMessage" class="app-alert">{{ guardMessage }}</p>
      <RouterView />
    </main>
  </div>
</template>
