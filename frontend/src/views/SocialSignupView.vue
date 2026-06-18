<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { authApi } from '@/api/auth'
import type { PendingSocialSignupResponse } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import {
  authErrorMessage,
  providerLabel,
  validateDisplayName,
  validateEmail,
  validatePassword
} from './authHelpers'

const router = useRouter()
const authStore = useAuthStore()

const pending = ref<PendingSocialSignupResponse | null>(null)
const loadingPending = ref(true)
const pendingErrorMessage = ref('')
const signupEmail = ref('')
const signupDisplayName = ref('')
const signupPassword = ref('')
const signupErrors = ref<string[]>([])
const signupMessage = ref('')
const linkEmail = ref('')
const linkPassword = ref('')
const linkMessage = ref('')
const showLinkGuidance = ref(false)

const pendingProviderLabel = computed(() => (pending.value ? providerLabel(pending.value.provider) : '소셜'))
const shouldPrioritizeLink = computed(() => Boolean(pending.value?.matchingEmailAccountExists || showLinkGuidance.value))

function syncPendingForm(response: PendingSocialSignupResponse) {
  signupEmail.value = response.email ?? ''
  signupDisplayName.value = response.displayName ?? ''
  linkEmail.value = response.email ?? ''
  showLinkGuidance.value = response.matchingEmailAccountExists
}

async function fetchPendingSocialSignup() {
  loadingPending.value = true
  pendingErrorMessage.value = ''

  try {
    const response = await authApi.getPendingSocialSignup()
    pending.value = response
    syncPendingForm(response)
  } catch (error) {
    pending.value = null
    pendingErrorMessage.value = authErrorMessage(
      error,
      '소셜 가입 정보가 만료되었거나 찾을 수 없습니다.'
    )
  } finally {
    loadingPending.value = false
  }
}

function validateSignupForm(): string[] {
  return [
    validateEmail(signupEmail.value),
    validateDisplayName(signupDisplayName.value),
    validatePassword(signupPassword.value)
  ].filter((message): message is string => Boolean(message))
}

async function submitSocialSignup() {
  signupMessage.value = ''
  signupErrors.value = validateSignupForm()
  if (signupErrors.value.length) {
    return
  }

  try {
    await authStore.completeSocialSignup({
      email: signupEmail.value.trim(),
      displayName: signupDisplayName.value.trim(),
      password: signupPassword.value
    })
    await router.push('/map')
  } catch (error) {
    const message = authErrorMessage(error, '소셜 회원가입을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.')
    signupMessage.value =
      message === '이미 가입된 이메일입니다.' ? `${message} 기존 계정 비밀번호로 연결해 주세요.` : message
    if (message === '이미 가입된 이메일입니다.') {
      showLinkGuidance.value = true
      linkEmail.value = signupEmail.value
    }
  }
}

async function submitSocialLink() {
  linkMessage.value = ''

  const emailError = validateEmail(linkEmail.value)
  if (emailError || !linkPassword.value) {
    linkMessage.value = emailError ?? '기존 계정 비밀번호를 입력해 주세요.'
    return
  }

  try {
    await authStore.linkPendingSocialAccount({
      email: linkEmail.value.trim(),
      password: linkPassword.value
    })
    await router.push('/map')
  } catch (error) {
    linkMessage.value = authErrorMessage(error, '소셜 계정을 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.')
  }
}

onMounted(fetchPendingSocialSignup)
</script>

<template>
  <section class="auth-layout">
    <div class="page-heading">
      <p class="eyebrow">소셜 회원가입</p>
      <h1>소셜 계정을 Jiber 계정과 연결하세요.</h1>
      <p>기존 계정이 있다면 이메일과 비밀번호 확인 후에만 연결할 수 있습니다.</p>
    </div>

    <p v-if="loadingPending" class="loading-text">소셜 가입 정보를 확인하고 있습니다.</p>

    <article v-else-if="pending" class="auth-panel social-auth-panel">
      <section class="social-preview" aria-label="소셜 가입 정보">
        <span class="tag">{{ pendingProviderLabel }}</span>
        <strong>{{ pending.email ?? '이메일 정보 없음' }}</strong>
        <p>{{ pending.displayName ?? '이름 정보 없음' }}</p>
        <p v-if="pending.matchingEmailAccountExists" class="helper-text">
          이 이메일로 가입된 계정이 있습니다. 자동으로 연결하지 않으며, 기존 계정 비밀번호를 입력해야 연결할 수
          있습니다.
        </p>
      </section>

      <section v-if="shouldPrioritizeLink" class="auth-section">
        <h2>기존 계정에 연결</h2>
        <p class="helper-text">Jiber 계정의 이메일과 비밀번호를 입력하면 이 소셜 계정을 연결합니다.</p>
        <form class="auth-form" data-test="social-link-form" @submit.prevent="submitSocialLink">
          <label>
            이메일
            <input
              v-model="linkEmail"
              data-test="social-link-email"
              autocomplete="email"
              inputmode="email"
              type="email"
              placeholder="you@example.com"
            />
          </label>
          <label>
            기존 계정 비밀번호
            <input
              v-model="linkPassword"
              data-test="social-link-password"
              autocomplete="current-password"
              type="password"
              placeholder="비밀번호"
            />
          </label>
          <p v-if="linkMessage" class="inline-error">{{ linkMessage }}</p>
          <button class="primary-button full-width" type="submit" :disabled="authStore.loading">
            {{ authStore.loading ? '연결 중입니다' : '기존 계정에 연결' }}
          </button>
        </form>
      </section>

      <section class="auth-section">
        <h2>소셜 계정으로 새 회원가입</h2>
        <p v-if="shouldPrioritizeLink" class="helper-text">
          새 계정으로 가입하려면 기존 계정과 다른 이메일을 사용해 주세요.
        </p>
        <form class="auth-form" data-test="social-signup-form" @submit.prevent="submitSocialSignup">
          <label>
            이메일
            <input
              v-model="signupEmail"
              data-test="social-signup-email"
              autocomplete="email"
              inputmode="email"
              type="email"
              placeholder="you@example.com"
            />
          </label>
          <label>
            이름
            <input
              v-model="signupDisplayName"
              data-test="social-signup-display-name"
              autocomplete="name"
              type="text"
              placeholder="서비스에서 사용할 이름"
            />
          </label>
          <label>
            비밀번호
            <input
              v-model="signupPassword"
              data-test="social-signup-password"
              autocomplete="new-password"
              minlength="8"
              type="password"
              placeholder="8자 이상"
            />
          </label>
          <div v-if="signupErrors.length" class="inline-error">
            <p v-for="message in signupErrors" :key="message">{{ message }}</p>
          </div>
          <p v-if="signupMessage" class="inline-error">{{ signupMessage }}</p>
          <button class="secondary-button full-width" type="submit" :disabled="authStore.loading">
            {{ authStore.loading ? '가입 중입니다' : '새 계정으로 가입' }}
          </button>
        </form>
      </section>

      <section v-if="!shouldPrioritizeLink" class="auth-section">
        <h2>기존 계정에 연결</h2>
        <p class="helper-text">이미 Jiber 계정이 있다면 이메일과 비밀번호 확인 후 연결할 수 있습니다.</p>
        <form class="auth-form" data-test="social-link-form" @submit.prevent="submitSocialLink">
          <label>
            이메일
            <input
              v-model="linkEmail"
              data-test="social-link-email"
              autocomplete="email"
              inputmode="email"
              type="email"
              placeholder="you@example.com"
            />
          </label>
          <label>
            기존 계정 비밀번호
            <input
              v-model="linkPassword"
              data-test="social-link-password"
              autocomplete="current-password"
              type="password"
              placeholder="비밀번호"
            />
          </label>
          <p v-if="linkMessage" class="inline-error">{{ linkMessage }}</p>
          <button class="primary-button full-width" type="submit" :disabled="authStore.loading">
            {{ authStore.loading ? '연결 중입니다' : '기존 계정에 연결' }}
          </button>
        </form>
      </section>
    </article>

    <article v-else class="auth-panel">
      <p class="inline-error">{{ pendingErrorMessage }}</p>
      <p class="helper-text">브라우저에 저장된 소셜 가입 세션을 확인하지 못했습니다. 다시 시작해 주세요.</p>
      <div class="button-row">
        <RouterLink class="primary-button" to="/login">로그인으로 돌아가기</RouterLink>
        <RouterLink class="secondary-button" to="/signup">회원가입으로 돌아가기</RouterLink>
      </div>
    </article>
  </section>
</template>
