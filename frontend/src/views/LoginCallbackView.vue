<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const message = ref('소셜 로그인 정보를 확인하고 있습니다.')
const failed = ref(false)

onMounted(async () => {
  try {
    await authStore.completeLoginCallback()
    message.value = '로그인이 완료되었습니다. 화면을 이동합니다.'

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/map'
    await router.replace(redirect)
  } catch {
    failed.value = true
    message.value = '로그인을 완료하지 못했습니다. 다시 로그인해 주세요.'
  }
})
</script>

<template>
  <section class="callback-panel">
    <p class="eyebrow">로그인</p>
    <h1>{{ failed ? '로그인 확인 실패' : '로그인 처리 중' }}</h1>
    <p>{{ message }}</p>
    <p class="muted">이 화면은 URL에서 토큰을 읽지 않고 refresh cookie 기반으로 세션을 확인합니다.</p>
  </section>
</template>
