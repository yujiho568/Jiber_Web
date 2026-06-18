import { describe, expect, it } from 'vitest'

import { canAccessRoute } from '@/router/guards'
import { router } from '@/router'
import type { UserRole } from '@/api/types'

describe('canAccessRoute', () => {
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
})
