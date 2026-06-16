import type { KakaoMapsApi } from './kakaoMap'

type KakaoMapsWindow = Window & {
  kakao?: {
    maps?: KakaoMapsApi & {
      load(callback: () => void): void
    }
  }
}

let loaderPromise: Promise<void> | null = null

export function hasKakaoMapKey(appKey = import.meta.env.VITE_KAKAO_MAP_APP_KEY): boolean {
  return Boolean(appKey?.trim())
}

export function getKakaoMapFallbackMessage(): string {
  return '카카오 지도 API 키가 아직 설정되지 않았습니다. frontend/.env에 VITE_KAKAO_MAP_APP_KEY를 설정하면 실제 지도를 불러올 수 있습니다.'
}

export function getKakaoMaps(): (KakaoMapsApi & { load(callback: () => void): void }) | null {
  return ((window as KakaoMapsWindow).kakao?.maps ?? null)
}

export function loadKakaoMaps(appKey = import.meta.env.VITE_KAKAO_MAP_APP_KEY): Promise<void> {
  if (!hasKakaoMapKey(appKey)) {
    return Promise.reject(new Error(getKakaoMapFallbackMessage()))
  }

  const kakaoWindow = window as KakaoMapsWindow

  if (kakaoWindow.kakao?.maps) {
    return new Promise((resolve) => {
      kakaoWindow.kakao?.maps?.load(resolve)
    })
  }

  if (loaderPromise) {
    return loaderPromise
  }

  loaderPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(appKey ?? '')}&autoload=false&libraries=services,clusterer`
    script.async = true
    script.onload = () => {
      if (!kakaoWindow.kakao?.maps) {
        reject(new Error('카카오 지도 SDK를 확인하지 못했습니다. JavaScript 키 설정을 확인해 주세요.'))
        return
      }

      kakaoWindow.kakao.maps.load(resolve)
    }
    script.onerror = () => {
      loaderPromise = null
      reject(new Error('카카오 지도를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'))
    }
    document.head.appendChild(script)
  })

  return loaderPromise
}
