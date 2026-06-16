import { apiClient, getBackendPublicBaseUrl } from './client'
import type { AuthLogoutResponse, AuthMeResponse, AuthRefreshResponse } from './types'

export type OAuthProvider = 'google' | 'kakao' | 'naver'

export const authApi = {
  async getMe(): Promise<AuthMeResponse> {
    const { data } = await apiClient.get<AuthMeResponse>('/auth/me')
    return data
  },

  async refresh(): Promise<AuthRefreshResponse> {
    const { data } = await apiClient.post<AuthRefreshResponse>('/auth/refresh', null, {
      withCredentials: true
    })
    return data
  },

  async logout(logoutAllDevices = false): Promise<AuthLogoutResponse> {
    const { data } = await apiClient.post<AuthLogoutResponse>(
      '/auth/logout',
      { logoutAllDevices },
      { withCredentials: true }
    )
    return data
  }
}

export function getOAuthStartUrl(provider: OAuthProvider): string {
  return `${getBackendPublicBaseUrl()}/oauth2/authorization/${provider}`
}
