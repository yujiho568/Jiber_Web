import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

describe('useAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    sessionStorage.clear()
  })

  it('keeps the access token in Pinia memory only after callback refresh', async () => {
    vi.spyOn(authApi, 'refresh').mockResolvedValueOnce({
      accessToken: 'memory-only-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: {
        userId: 1,
        email: 'user@example.com',
        displayName: '사용자',
        roles: ['USER']
      }
    })
    vi.spyOn(authApi, 'getMe').mockResolvedValueOnce({
      authenticated: true,
      user: {
        userId: 1,
        email: 'user@example.com',
        displayName: '사용자',
        roles: ['USER']
      }
    })

    const store = useAuthStore()
    await store.completeLoginCallback()

    expect(store.accessToken).toBe('memory-only-token')
    expect(store.user?.roles).toContain('USER')
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(sessionStorage.getItem('accessToken')).toBeNull()
  })

  it('keeps the access token in Pinia memory only after email login', async () => {
    vi.spyOn(authApi, 'login').mockResolvedValueOnce({
      accessToken: 'memory-only-login-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: {
        userId: 2,
        email: 'login@example.com',
        displayName: '로그인 사용자',
        roles: ['USER']
      }
    })

    const store = useAuthStore()
    await store.loginWithPassword({ email: 'login@example.com', password: 'password-8' })

    expect(store.accessToken).toBe('memory-only-login-token')
    expect(store.user?.displayName).toBe('로그인 사용자')
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(sessionStorage.getItem('accessToken')).toBeNull()
  })

  it('keeps the access token in Pinia memory only after social link', async () => {
    vi.spyOn(authApi, 'linkPendingSocialAccount').mockResolvedValueOnce({
      accessToken: 'memory-only-social-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: {
        userId: 3,
        email: 'linked@example.com',
        displayName: '연결 사용자',
        roles: ['USER']
      }
    })

    const store = useAuthStore()
    await store.linkPendingSocialAccount({ email: 'linked@example.com', password: 'password-8' })

    expect(store.accessToken).toBe('memory-only-social-token')
    expect(store.user?.email).toBe('linked@example.com')
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(sessionStorage.getItem('accessToken')).toBeNull()
  })

  it('clears memory auth state after logout', async () => {
    vi.spyOn(authApi, 'logout').mockResolvedValueOnce({
      message: '로그아웃되었습니다.'
    })

    const store = useAuthStore()
    store.setSession({
      accessToken: 'memory-only-token',
      user: {
        userId: 1,
        email: 'user@example.com',
        displayName: '사용자',
        roles: ['ADMIN']
      }
    })

    await store.logout()

    expect(store.accessToken).toBeNull()
    expect(store.user).toBeNull()
  })
})
