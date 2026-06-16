import { describe, expect, it } from 'vitest'

import { getKakaoMapFallbackMessage, hasKakaoMapKey } from '@/map/kakaoLoader'

describe('kakaoLoader', () => {
  it('reports a Korean fallback message when the Kakao Maps key is missing', () => {
    expect(hasKakaoMapKey('')).toBe(false)
    expect(getKakaoMapFallbackMessage()).toContain('카카오 지도')
    expect(getKakaoMapFallbackMessage()).toContain('VITE_KAKAO_MAP_APP_KEY')
  })

  it('accepts a non-empty Kakao Maps key', () => {
    expect(hasKakaoMapKey('fake-local-key')).toBe(true)
  })
})
