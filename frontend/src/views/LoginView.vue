<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { getOAuthStartUrl, type OAuthProvider } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { authErrorMessage, safeRedirectTarget, validateEmail } from './authHelpers'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loginProviders: { provider: OAuthProvider; label: string }[] = [
  { provider: 'kakao', label: '카카오로 계속하기' },
  { provider: 'google', label: '구글로 계속하기' },
  { provider: 'naver', label: '네이버로 계속하기' }
]

const email = ref('')
const password = ref('')
const errorMessage = ref('')

function startSocialLogin(provider: OAuthProvider) {
  window.location.href = getOAuthStartUrl(provider)
}

async function submitLogin() {
  errorMessage.value = ''

  const emailError = validateEmail(email.value)
  if (emailError || !password.value) {
    errorMessage.value = emailError ?? '비밀번호를 입력해 주세요.'
    return
  }

  try {
    await authStore.loginWithPassword({
      email: email.value.trim(),
      password: password.value
    })
    await router.push(safeRedirectTarget(route.query.redirect))
  } catch (error) {
    errorMessage.value = authErrorMessage(error, '로그인하지 못했습니다. 잠시 후 다시 시도해 주세요.')
  }
}
</script>

<template>
  <section class="auth-layout">
    <div class="page-heading">
      <p class="eyebrow">로그인</p>
      <h1>Jiber 계정으로 로그인하세요.</h1>
      <p v-if="route.query.auth === 'AUTH_REQUIRED'" class="muted">로그인이 필요한 화면입니다.</p>
    </div>

    <article class="auth-panel">
      <form class="auth-form" data-test="login-form" @submit.prevent="submitLogin">
        <label>
          이메일
          <input
            v-model="email"
            data-test="login-email"
            autocomplete="email"
            inputmode="email"
            type="email"
            placeholder="you@example.com"
          />
        </label>

        <label>
          비밀번호
          <input
            v-model="password"
            data-test="login-password"
            autocomplete="current-password"
            type="password"
            placeholder="비밀번호"
          />
        </label>

        <p v-if="errorMessage" class="inline-error">{{ errorMessage }}</p>

        <button class="primary-button full-width" type="submit" :disabled="authStore.loading">
          {{ authStore.loading ? '로그인 중입니다' : '로그인' }}
        </button>
      </form>

      <div class="auth-divider" aria-hidden="true"><span>또는</span></div>

      <div class="provider-list" aria-label="소셜 로그인">
        <button
          v-for="loginProvider in loginProviders"
          :key="loginProvider.provider"
          class="secondary-button full-width"
          type="button"
          @click="startSocialLogin(loginProvider.provider)"
        >
          {{ loginProvider.label }}
        </button>
      </div>

      <p class="helper-text">
        아직 계정이 없다면 <RouterLink class="inline-link" to="/signup">회원가입</RouterLink>을 진행해 주세요.
      </p>
    </article>
  </section>
</template>
