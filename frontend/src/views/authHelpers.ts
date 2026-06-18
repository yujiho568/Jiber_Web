import { getApiError } from '@/api/client'
import type { OAuthProviderCode } from '@/api/types'

export function providerLabel(provider: OAuthProviderCode): string {
  const labels: Record<OAuthProviderCode, string> = {
    GOOGLE: '구글',
    KAKAO: '카카오',
    NAVER: '네이버'
  }

  return labels[provider]
}

export function safeRedirectTarget(redirect: unknown, fallback = '/map'): string {
  if (typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')) {
    return redirect
  }

  return fallback
}

export function validateEmail(email: string): string | null {
  const normalizedEmail = email.trim()
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail)) {
    return '올바른 이메일 주소를 입력해 주세요.'
  }

  return null
}

export function validateDisplayName(displayName: string): string | null {
  if (!displayName.trim()) {
    return '이름을 입력해 주세요.'
  }

  return null
}

export function validatePassword(password: string): string | null {
  if (password.length < 8) {
    return '비밀번호는 8자 이상 입력해 주세요.'
  }

  return null
}

export function authErrorMessage(error: unknown, fallbackMessage: string): string {
  const apiError = getApiError(error)

  if (!apiError) {
    return fallbackMessage
  }

  if (apiError.code === 'INVALID_CREDENTIALS') {
    return '이메일 또는 비밀번호를 확인해 주세요.'
  }

  if (apiError.code === 'EMAIL_ALREADY_EXISTS') {
    return '이미 가입된 이메일입니다.'
  }

  if (apiError.code === 'SOCIAL_PENDING_NOT_FOUND') {
    return '소셜 가입 정보가 만료되었거나 찾을 수 없습니다.'
  }

  if (apiError.code === 'SOCIAL_ACCOUNT_ALREADY_LINKED') {
    return '이미 다른 계정에 연결된 소셜 계정입니다.'
  }

  if (apiError.code === 'VALIDATION_FAILED') {
    const messages = apiError.details
      ?.map((detail) => {
        if (detail.field === 'email') return '올바른 이메일 주소를 입력해 주세요.'
        if (detail.field === 'password') return '비밀번호는 8자 이상 입력해 주세요.'
        if (detail.field === 'displayName') return '이름을 입력해 주세요.'
        return detail.reason
      })
      .filter(Boolean)

    if (messages?.length) {
      return Array.from(new Set(messages)).join(' ')
    }

    return '입력값을 다시 확인해 주세요.'
  }

  return apiError.message || fallbackMessage
}
