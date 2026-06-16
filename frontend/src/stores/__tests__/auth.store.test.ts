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
