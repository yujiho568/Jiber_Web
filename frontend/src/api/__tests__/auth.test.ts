import { afterEach, describe, expect, it, vi } from 'vitest'

import { authApi, getOAuthStartUrl } from '@/api/auth'
import { apiClient } from '@/api/client'

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

afterEach(() => {
  vi.restoreAllMocks()
})

describe('authApi OAuth start URL', () => {
  it.each(['google', 'kakao', 'naver'] as const)(
    'builds the %s backend OAuth start URL without nesting under /api/v1',
    (provider) => {
      const startUrl = getOAuthStartUrl(provider)

      expect(startUrl).toMatch(new RegExp(`/oauth2/authorization/${provider}$`))
      expect(startUrl).not.toContain('/api/v1/oauth2')
    }
  )
})

describe('authApi email and social endpoints', () => {
  it('posts email login with credentials included', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({ data: sessionResponse })

    await authApi.login({ email: 'user@example.com', password: 'password-8' })

    expect(postSpy).toHaveBeenCalledWith(
      '/auth/login',
      { email: 'user@example.com', password: 'password-8' },
      { withCredentials: true }
    )
  })

  it('posts email signup with credentials included', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({ data: sessionResponse })

    await authApi.signup({ email: 'user@example.com', password: 'password-8', displayName: '사용자' })

    expect(postSpy).toHaveBeenCalledWith(
      '/auth/signup',
      { email: 'user@example.com', password: 'password-8', displayName: '사용자' },
      { withCredentials: true }
    )
  })

  it('gets pending social signup state with credentials included', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce({
      data: {
        provider: 'NAVER',
        email: 'user@example.com',
        displayName: '사용자',
        matchingEmailAccountExists: true
      }
    })

    await authApi.getPendingSocialSignup()

    expect(getSpy).toHaveBeenCalledWith('/auth/social/pending', { withCredentials: true })
  })

  it('posts social signup with credentials included', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({ data: sessionResponse })

    await authApi.completeSocialSignup({
      email: 'user@example.com',
      password: 'password-8',
      displayName: '사용자'
    })

    expect(postSpy).toHaveBeenCalledWith(
      '/auth/social/signup',
      { email: 'user@example.com', password: 'password-8', displayName: '사용자' },
      { withCredentials: true }
    )
  })

  it('posts social link with credentials included', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({ data: sessionResponse })

    await authApi.linkPendingSocialAccount({ email: 'user@example.com', password: 'password-8' })

    expect(postSpy).toHaveBeenCalledWith(
      '/auth/social/link',
      { email: 'user@example.com', password: 'password-8' },
      { withCredentials: true }
    )
  })
})
