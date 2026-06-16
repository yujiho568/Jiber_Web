import { describe, expect, it } from 'vitest'

import { canAccessRoute } from '@/router/guards'
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
})
