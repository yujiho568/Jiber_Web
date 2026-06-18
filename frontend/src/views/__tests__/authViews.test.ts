import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { authApi } from '@/api/auth'
import LoginView from '@/views/LoginView.vue'
import SignupView from '@/views/SignupView.vue'
import SocialSignupView from '@/views/SocialSignupView.vue'
import { useAuthStore } from '@/stores/auth'

const sessionResponse = {
  accessToken: 'memory-only-token',
  tokenType: 'Bearer' as const,
  expiresIn: 900,
  user: {
    userId: 1,
    email: 'user@example.com',
    displayName: '사용자',
    roles: ['USER' as const]
  }
}

function createApiError(code: string) {
  return {
    isAxiosError: true,
    response: {
      data: {
        code,
        message: '요청을 처리하지 못했습니다.',
        path: '/api/v1/auth',
        timestamp: '2026-06-18T00:00:00+09:00'
      }
    }
  }
}

function createTestRouter(initialPath: string) {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<main />' } },
      { path: '/map', component: { template: '<main />' } },
      { path: '/favorites', component: { template: '<main />' } },
      { path: '/login', component: LoginView },
      { path: '/signup', component: SignupView },
      { path: '/signup/social', component: SocialSignupView }
    ]
  })
}

async function mountAuthView(component: typeof LoginView, initialPath: string) {
  const pinia = createPinia()
  setActivePinia(pinia)

  const router = createTestRouter(initialPath)
  await router.push(initialPath)
  await router.isReady()

  const wrapper = mount(component, {
    global: {
      plugins: [pinia, router]
    }
  })

  return { wrapper, router, authStore: useAuthStore() }
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('LoginView', () => {
  it('logs in with email and follows the redirect query', async () => {
    const loginSpy = vi.spyOn(authApi, 'login').mockResolvedValueOnce(sessionResponse)
    const { wrapper, router, authStore } = await mountAuthView(LoginView, '/login?redirect=/favorites')

    await wrapper.get('[data-test="login-email"]').setValue('user@example.com')
    await wrapper.get('[data-test="login-password"]').setValue('password-8')
    await wrapper.get('[data-test="login-form"]').trigger('submit')
    await flushPromises()

    expect(loginSpy).toHaveBeenCalledWith({ email: 'user@example.com', password: 'password-8' })
    expect(authStore.accessToken).toBe('memory-only-token')
    expect(router.currentRoute.value.fullPath).toBe('/favorites')
  })

  it('shows a safe Korean message for invalid credentials', async () => {
    vi.spyOn(authApi, 'login').mockRejectedValueOnce(createApiError('INVALID_CREDENTIALS'))
    const { wrapper } = await mountAuthView(LoginView, '/login')

    await wrapper.get('[data-test="login-email"]').setValue('user@example.com')
    await wrapper.get('[data-test="login-password"]').setValue('wrong-pass')
    await wrapper.get('[data-test="login-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('이메일 또는 비밀번호를 확인해 주세요.')
  })
})

describe('SignupView', () => {
  it('signs up with email and starts a memory-only session', async () => {
    const signupSpy = vi.spyOn(authApi, 'signup').mockResolvedValueOnce(sessionResponse)
    const { wrapper, router, authStore } = await mountAuthView(SignupView, '/signup')

    await wrapper.get('[data-test="signup-email"]').setValue('user@example.com')
    await wrapper.get('[data-test="signup-display-name"]').setValue('사용자')
    await wrapper.get('[data-test="signup-password"]').setValue('password-8')
    await wrapper.get('[data-test="signup-form"]').trigger('submit')
    await flushPromises()

    expect(signupSpy).toHaveBeenCalledWith({
      email: 'user@example.com',
      displayName: '사용자',
      password: 'password-8'
    })
    expect(authStore.accessToken).toBe('memory-only-token')
    expect(router.currentRoute.value.fullPath).toBe('/map')
  })

  it('shows duplicate email errors in Korean', async () => {
    vi.spyOn(authApi, 'signup').mockRejectedValueOnce(createApiError('EMAIL_ALREADY_EXISTS'))
    const { wrapper } = await mountAuthView(SignupView, '/signup')

    await wrapper.get('[data-test="signup-email"]').setValue('user@example.com')
    await wrapper.get('[data-test="signup-display-name"]').setValue('사용자')
    await wrapper.get('[data-test="signup-password"]').setValue('password-8')
    await wrapper.get('[data-test="signup-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('이미 가입된 이메일입니다.')
  })

  it('validates email, display name, and password before signup', async () => {
    const signupSpy = vi.spyOn(authApi, 'signup')
    const { wrapper } = await mountAuthView(SignupView, '/signup')

    await wrapper.get('[data-test="signup-email"]').setValue('not-email')
    await wrapper.get('[data-test="signup-display-name"]').setValue('')
    await wrapper.get('[data-test="signup-password"]').setValue('short')
    await wrapper.get('[data-test="signup-form"]').trigger('submit')
    await flushPromises()

    expect(signupSpy).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('올바른 이메일 주소를 입력해 주세요.')
    expect(wrapper.text()).toContain('이름을 입력해 주세요.')
    expect(wrapper.text()).toContain('비밀번호는 8자 이상 입력해 주세요.')
  })
})

describe('SocialSignupView', () => {
  it('shows pending social preview and prioritizes existing account linking when email matches', async () => {
    const linkSpy = vi.spyOn(authApi, 'linkPendingSocialAccount')
    vi.spyOn(authApi, 'getPendingSocialSignup').mockResolvedValueOnce({
      provider: 'NAVER',
      email: 'user@example.com',
      displayName: '네이버 사용자',
      matchingEmailAccountExists: true
    })

    const { wrapper } = await mountAuthView(SocialSignupView, '/signup/social')
    await flushPromises()

    expect(wrapper.text()).toContain('네이버')
    expect(wrapper.text()).toContain('user@example.com')
    expect(wrapper.text()).toContain('기존 계정에 연결')
    expect(wrapper.text()).toContain('비밀번호를 입력해야 연결할 수 있습니다.')
    expect(linkSpy).not.toHaveBeenCalled()
  })

  it('shows a safe empty state when pending social signup is missing', async () => {
    vi.spyOn(authApi, 'getPendingSocialSignup').mockRejectedValueOnce(createApiError('SOCIAL_PENDING_NOT_FOUND'))

    const { wrapper } = await mountAuthView(SocialSignupView, '/signup/social')
    await flushPromises()

    expect(wrapper.text()).toContain('소셜 가입 정보가 만료되었거나 찾을 수 없습니다.')
    expect(wrapper.text()).toContain('로그인으로 돌아가기')
    expect(wrapper.text()).toContain('회원가입으로 돌아가기')
  })

  it('completes social signup and starts a memory-only session', async () => {
    const socialSignupSpy = vi.spyOn(authApi, 'completeSocialSignup').mockResolvedValueOnce(sessionResponse)
    vi.spyOn(authApi, 'getPendingSocialSignup').mockResolvedValueOnce({
      provider: 'KAKAO',
      email: 'user@example.com',
      displayName: '카카오 사용자',
      matchingEmailAccountExists: false
    })
    const { wrapper, router, authStore } = await mountAuthView(SocialSignupView, '/signup/social')
    await flushPromises()

    await wrapper.get('[data-test="social-signup-display-name"]').setValue('카카오 사용자')
    await wrapper.get('[data-test="social-signup-password"]').setValue('password-8')
    await wrapper.get('[data-test="social-signup-form"]').trigger('submit')
    await flushPromises()

    expect(socialSignupSpy).toHaveBeenCalledWith({
      email: 'user@example.com',
      displayName: '카카오 사용자',
      password: 'password-8'
    })
    expect(authStore.accessToken).toBe('memory-only-token')
    expect(router.currentRoute.value.fullPath).toBe('/map')
  })

  it('switches to existing account guidance when social signup email already exists', async () => {
    vi.spyOn(authApi, 'completeSocialSignup').mockRejectedValueOnce(createApiError('EMAIL_ALREADY_EXISTS'))
    vi.spyOn(authApi, 'getPendingSocialSignup').mockResolvedValueOnce({
      provider: 'GOOGLE',
      email: 'user@example.com',
      displayName: '구글 사용자',
      matchingEmailAccountExists: false
    })
    const { wrapper } = await mountAuthView(SocialSignupView, '/signup/social')
    await flushPromises()

    await wrapper.get('[data-test="social-signup-display-name"]').setValue('구글 사용자')
    await wrapper.get('[data-test="social-signup-password"]').setValue('password-8')
    await wrapper.get('[data-test="social-signup-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('이미 가입된 이메일입니다.')
    expect(wrapper.text()).toContain('기존 계정 비밀번호로 연결해 주세요.')
  })

  it('links a pending social account after existing account password verification', async () => {
    const linkSpy = vi.spyOn(authApi, 'linkPendingSocialAccount').mockResolvedValueOnce(sessionResponse)
    vi.spyOn(authApi, 'getPendingSocialSignup').mockResolvedValueOnce({
      provider: 'NAVER',
      email: 'user@example.com',
      displayName: '네이버 사용자',
      matchingEmailAccountExists: true
    })
    const { wrapper, router, authStore } = await mountAuthView(SocialSignupView, '/signup/social')
    await flushPromises()

    await wrapper.get('[data-test="social-link-email"]').setValue('user@example.com')
    await wrapper.get('[data-test="social-link-password"]').setValue('password-8')
    await wrapper.get('[data-test="social-link-form"]').trigger('submit')
    await flushPromises()

    expect(linkSpy).toHaveBeenCalledWith({ email: 'user@example.com', password: 'password-8' })
    expect(authStore.accessToken).toBe('memory-only-token')
    expect(router.currentRoute.value.fullPath).toBe('/map')
  })

  it('shows safe Korean messages for social link failures', async () => {
    vi.spyOn(authApi, 'linkPendingSocialAccount').mockRejectedValueOnce(createApiError('SOCIAL_ACCOUNT_ALREADY_LINKED'))
    vi.spyOn(authApi, 'getPendingSocialSignup').mockResolvedValueOnce({
      provider: 'NAVER',
      email: 'user@example.com',
      displayName: '네이버 사용자',
      matchingEmailAccountExists: true
    })
    const { wrapper } = await mountAuthView(SocialSignupView, '/signup/social')
    await flushPromises()

    await wrapper.get('[data-test="social-link-email"]').setValue('user@example.com')
    await wrapper.get('[data-test="social-link-password"]').setValue('password-8')
    await wrapper.get('[data-test="social-link-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('이미 다른 계정에 연결된 소셜 계정입니다.')
  })
})
