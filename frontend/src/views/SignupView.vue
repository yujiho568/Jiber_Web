<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import {
  authErrorMessage,
  safeRedirectTarget,
  validateDisplayName,
  validateEmail,
  validatePassword
} from './authHelpers'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const email = ref('')
const displayName = ref('')
const password = ref('')
const formErrors = ref<string[]>([])
const errorMessage = ref('')

function validateForm(): string[] {
  return [
    validateEmail(email.value),
    validateDisplayName(displayName.value),
    validatePassword(password.value)
  ].filter((message): message is string => Boolean(message))
}

async function submitSignup() {
  errorMessage.value = ''
  formErrors.value = validateForm()
  if (formErrors.value.length) {
    return
  }

  try {
    await authStore.signupWithPassword({
      email: email.value.trim(),
      displayName: displayName.value.trim(),
      password: password.value
    })
    await router.push(safeRedirectTarget(route.query.redirect))
  } catch (error) {
    errorMessage.value = authErrorMessage(error, '회원가입을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.')
  }
}
</script>

<template>
  <section class="auth-layout">
    <div class="page-heading">
      <p class="eyebrow">회원가입</p>
      <h1>이메일로 Jiber 계정을 만드세요.</h1>
      <p>비밀번호는 8자 이상으로 입력해 주세요.</p>
    </div>

    <article class="auth-panel">
      <form class="auth-form" data-test="signup-form" @submit.prevent="submitSignup">
        <label>
          이메일
          <input
            v-model="email"
            data-test="signup-email"
            autocomplete="email"
            inputmode="email"
            type="email"
            placeholder="you@example.com"
          />
        </label>

        <label>
          이름
          <input
            v-model="displayName"
            data-test="signup-display-name"
            autocomplete="name"
            type="text"
            placeholder="서비스에서 사용할 이름"
          />
        </label>

        <label>
          비밀번호
          <input
            v-model="password"
            data-test="signup-password"
            autocomplete="new-password"
            minlength="8"
            type="password"
            placeholder="8자 이상"
          />
        </label>

        <div v-if="formErrors.length" class="inline-error">
          <p v-for="message in formErrors" :key="message">{{ message }}</p>
        </div>
        <p v-if="errorMessage" class="inline-error">{{ errorMessage }}</p>

        <button class="primary-button full-width" type="submit" :disabled="authStore.loading">
          {{ authStore.loading ? '가입 중입니다' : '회원가입' }}
        </button>
      </form>

      <p class="helper-text">
        이미 계정이 있다면 <RouterLink class="inline-link" to="/login">로그인</RouterLink>으로 이동해 주세요.
      </p>
    </article>
  </section>
</template>
