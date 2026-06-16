import type { RouteMeta } from 'vue-router'

import type { UserRole } from '@/api/types'

export type RouteAccessReason = 'AUTH_REQUIRED' | 'ACCESS_DENIED'

export interface RouteAccessResult {
  allowed: boolean
  reason?: RouteAccessReason
  message?: string
}

export interface ProtectedRouteMeta extends RouteMeta {
  requiresAuth?: boolean
  roles?: UserRole[]
}

export function canAccessRoute(meta: ProtectedRouteMeta, roles: UserRole[] | null): RouteAccessResult {
  if (!meta.requiresAuth) {
    return { allowed: true }
  }

  if (!roles?.length) {
    return {
      allowed: false,
      reason: 'AUTH_REQUIRED',
      message: '로그인이 필요합니다.'
    }
  }

  if (meta.roles?.length && !meta.roles.some((role) => roles.includes(role))) {
    return {
      allowed: false,
      reason: 'ACCESS_DENIED',
      message: '관리자 권한이 필요합니다.'
    }
  }

  return { allowed: true }
}
