import { defineStore } from 'pinia'

import { authApi } from '@/api/auth'
import { getApiErrorMessage } from '@/api/client'
import type {
  AuthLoginRequest,
  AuthSessionResponse,
  AuthSignupRequest,
  AuthUser,
  SocialLinkRequest,
  SocialSignupRequest
} from '@/api/types'

interface AuthSessionPayload {
  accessToken: string
  user: AuthUser
}

interface AuthState {
  accessToken: string | null
  user: AuthUser | null
  bootstrapped: boolean
  loading: boolean
  errorMessage: string | null
}

let bootstrapSessionPromise: Promise<boolean> | null = null

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    accessToken: null,
    user: null,
    bootstrapped: false,
    loading: false,
    errorMessage: null
  }),

  getters: {
    isAuthenticated: (state) => Boolean(state.accessToken && state.user),
    isAdmin: (state) => Boolean(state.user?.roles.includes('ADMIN')),
    roles: (state) => state.user?.roles ?? []
  },

  actions: {
    setSessionFromResponse(response: AuthSessionResponse) {
      this.setSession({
        accessToken: response.accessToken,
        user: response.user
      })
      this.bootstrapped = true
    },

    setSession(payload: AuthSessionPayload) {
      this.accessToken = payload.accessToken
      this.user = payload.user
      this.errorMessage = null
    },

    clearSession() {
      this.accessToken = null
      this.user = null
      this.errorMessage = null
    },

    async completeLoginCallback() {
      this.loading = true
      this.errorMessage = null

      try {
        const refreshResponse = await authApi.refresh()
        this.setSession({
          accessToken: refreshResponse.accessToken,
          user: refreshResponse.user
        })

        const meResponse = await authApi.getMe()
        if (meResponse.authenticated && meResponse.user) {
          this.user = meResponse.user
        }
        this.bootstrapped = true
      } catch (error) {
        this.clearSession()
        this.errorMessage = getApiErrorMessage(error, '로그인 정보를 확인하지 못했습니다.')
        throw error
      } finally {
        this.loading = false
      }
    },

    async loginWithPassword(payload: AuthLoginRequest) {
      this.loading = true
      this.errorMessage = null

      try {
        const response = await authApi.login(payload)
        this.setSessionFromResponse(response)
      } catch (error) {
        this.clearSession()
        this.errorMessage = getApiErrorMessage(error, '로그인하지 못했습니다.')
        throw error
      } finally {
        this.loading = false
      }
    },

    async signupWithPassword(payload: AuthSignupRequest) {
      this.loading = true
      this.errorMessage = null

      try {
        const response = await authApi.signup(payload)
        this.setSessionFromResponse(response)
      } catch (error) {
        this.clearSession()
        this.errorMessage = getApiErrorMessage(error, '회원가입을 완료하지 못했습니다.')
        throw error
      } finally {
        this.loading = false
      }
    },

    async completeSocialSignup(payload: SocialSignupRequest) {
      this.loading = true
      this.errorMessage = null

      try {
        const response = await authApi.completeSocialSignup(payload)
        this.setSessionFromResponse(response)
      } catch (error) {
        this.clearSession()
        this.errorMessage = getApiErrorMessage(error, '소셜 회원가입을 완료하지 못했습니다.')
        throw error
      } finally {
        this.loading = false
      }
    },

    async linkPendingSocialAccount(payload: SocialLinkRequest) {
      this.loading = true
      this.errorMessage = null

      try {
        const response = await authApi.linkPendingSocialAccount(payload)
        this.setSessionFromResponse(response)
      } catch (error) {
        this.clearSession()
        this.errorMessage = getApiErrorMessage(error, '소셜 계정을 연결하지 못했습니다.')
        throw error
      } finally {
        this.loading = false
      }
    },

    async restoreSessionFromCookie(): Promise<boolean> {
      if (this.accessToken && this.user) {
        this.bootstrapped = true
        return true
      }

      this.loading = true

      try {
        const refreshResponse = await authApi.refresh()
        this.setSession({
          accessToken: refreshResponse.accessToken,
          user: refreshResponse.user
        })
        this.bootstrapped = true
        return true
      } catch {
        this.clearSession()
        this.bootstrapped = true
        return false
      } finally {
        this.loading = false
      }
    },

    async bootstrapSessionFromCookie(): Promise<boolean> {
      if (this.isAuthenticated) {
        this.bootstrapped = true
        return true
      }

      if (this.bootstrapped) {
        return false
      }

      if (!bootstrapSessionPromise) {
        bootstrapSessionPromise = this.restoreSessionFromCookie().finally(() => {
          bootstrapSessionPromise = null
        })
      }

      return bootstrapSessionPromise
    },

    async fetchMe() {
      const meResponse = await authApi.getMe()
      this.bootstrapped = true

      if (meResponse.authenticated && meResponse.user) {
        this.user = meResponse.user
      } else {
        this.clearSession()
      }
    },

    async logout() {
      this.loading = true

      try {
        await authApi.logout()
      } finally {
        this.clearSession()
        this.loading = false
        this.bootstrapped = true
      }
    }
  }
})
