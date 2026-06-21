import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { authApi } from '@/api/auth'
import { canAccessRoute } from '@/router/guards'
import { router } from '@/router'
import type { UserRole } from '@/api/types'
import { useAuthStore } from '@/stores/auth'

const sessionResponse = {
  accessToken: 'memory-only-router-token',
  tokenType: 'Bearer' as const,
  expiresIn: 900,
  user: {
    userId: 10,
    email: 'router@example.com',
    displayName: '라우터 사용자',
    roles: ['USER' as const]
  }
}

function authRequiredError() {
  return {
    isAxiosError: true,
    response: {
      data: {
        code: 'AUTH_REQUIRED',
        message: '로그인이 필요합니다.',
        path: '/api/v1/auth/refresh',
        timestamp: '2026-06-18T00:00:00+09:00'
      }
    }
  }
}

async function visit(path: string) {
  await router.push(path)
  await router.isReady()
  return useAuthStore()
}

describe('canAccessRoute', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('allows anonymous users to visit public routes', () => {
    expect(canAccessRoute({ requiresAuth: false }, null)).toEqual({
      allowed: true
    })
  })

  it('requires login for favorite routes', () => {
    expect(canAccessRoute({ requiresAuth: true, roles: ['USER'] }, null)).toEqual({
      allowed: false,
      reason: 'AUTH_REQUIRED',
      message: '로그인이 필요합니다.'
    })
  })

  it('requires ADMIN role for admin routes', () => {
    const roles: UserRole[] = ['USER']

    expect(canAccessRoute({ requiresAuth: true, roles: ['ADMIN'] }, roles)).toEqual({
      allowed: false,
      reason: 'ACCESS_DENIED',
      message: '관리자 권한이 필요합니다.'
    })
  })

  it('does not pass propertyId as a prop to the detail view', () => {
    const propertyDetailRoute = router.getRoutes().find((route) => route.name === 'property-detail')

    expect(propertyDetailRoute?.props.default).toBe(false)
  })

  it('registers email and social auth routes', () => {
    expect(router.hasRoute('login')).toBe(true)
    expect(router.hasRoute('signup')).toBe(true)
    expect(router.hasRoute('social-signup')).toBe(true)
  })

  it('marks login and signup routes as guest-only', () => {
    expect(router.getRoutes().find((route) => route.name === 'login')?.meta.guestOnly).toBe(true)
    expect(router.getRoutes().find((route) => route.name === 'signup')?.meta.guestOnly).toBe(true)
    expect(router.getRoutes().find((route) => route.name === 'social-signup')?.meta.guestOnly).toBe(true)
  })

  it('redirects authenticated users away from guest-only auth routes', () => {
    expect(canAccessRoute({ requiresAuth: false, guestOnly: true }, ['USER'], true)).toEqual({
      allowed: false,
      reason: 'ALREADY_AUTHENTICATED',
      message: '이미 로그인되어 있습니다.'
    })
  })

  it('restores auth state on initial public map navigation when refresh succeeds', async () => {
    const refreshSpy = vi.spyOn(authApi, 'refresh').mockResolvedValueOnce(sessionResponse)

    const store = await visit('/map?case=public-refresh-success')

    expect(refreshSpy).toHaveBeenCalledTimes(1)
    expect(store.isAuthenticated).toBe(true)
    expect(store.user?.displayName).toBe('라우터 사용자')
  })

  it('keeps public map navigation anonymous and quiet when refresh fails', async () => {
    const refreshSpy = vi.spyOn(authApi, 'refresh').mockRejectedValueOnce(authRequiredError())
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const consoleWarnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})

    const store = await visit('/map?case=public-refresh-failure')

    expect(refreshSpy).toHaveBeenCalledTimes(1)
    expect(store.isAuthenticated).toBe(false)
    expect(store.errorMessage).toBeNull()
    expect(router.currentRoute.value.name).toBe('map')
    expect(consoleErrorSpy).not.toHaveBeenCalled()
    expect(consoleWarnSpy).not.toHaveBeenCalled()
  })

  it('redirects guest-only login route to map after refresh cookie restore', async () => {
    const refreshSpy = vi.spyOn(authApi, 'refresh').mockResolvedValueOnce(sessionResponse)

    const store = await visit('/login?case=guest-refresh-success')

    expect(refreshSpy).toHaveBeenCalledTimes(1)
    expect(store.isAuthenticated).toBe(true)
    expect(router.currentRoute.value.name).toBe('map')
  })

  it('keeps protected favorites guard behavior after refresh failure', async () => {
    vi.spyOn(authApi, 'refresh').mockRejectedValueOnce(authRequiredError())

    const store = await visit('/favorites?case=protected-refresh-failure')

    expect(store.isAuthenticated).toBe(false)
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.auth).toBe('AUTH_REQUIRED')
    expect(router.currentRoute.value.query.redirect).toBe('/favorites?case=protected-refresh-failure')
  })

  it('allows authenticated users to visit favorites after refresh restore', async () => {
    const refreshSpy = vi.spyOn(authApi, 'refresh').mockResolvedValueOnce(sessionResponse)

    const store = await visit('/favorites?case=protected-refresh-success')

    expect(refreshSpy).toHaveBeenCalledTimes(1)
    expect(store.isAuthenticated).toBe(true)
    expect(router.currentRoute.value.name).toBe('favorites')
  })

  it('does not bootstrap refresh on login callback route', async () => {
    const refreshSpy = vi.spyOn(authApi, 'refresh').mockResolvedValueOnce(sessionResponse)

    await visit('/login/callback?case=callback-skip-bootstrap')

    expect(refreshSpy).not.toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('login-callback')
  })
})
