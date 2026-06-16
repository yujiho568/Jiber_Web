import { defineStore } from 'pinia'

import { authApi } from '@/api/auth'
import { getApiErrorMessage } from '@/api/client'
import type { AuthUser } from '@/api/types'

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

    async restoreSessionFromCookie(): Promise<boolean> {
      if (this.accessToken && this.user) {
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
