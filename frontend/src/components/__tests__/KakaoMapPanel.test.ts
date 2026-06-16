import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { PropertyMapItem } from '@/api/types'
import KakaoMapPanel from '@/components/KakaoMapPanel.vue'

const kakaoMock = vi.hoisted(() => {
  const state = {
    hasKey: false,
    idleHandler: null as null | (() => void),
    markerClickHandlers: [] as Array<() => void>,
    mapInstance: {
      getBounds: vi.fn(() => ({
        getSouthWest: () => ({
          getLat: () => 37.48,
          getLng: () => 127.01
        }),
        getNorthEast: () => ({
          getLat: () => 37.52,
          getLng: () => 127.06
        })
      })),
      getLevel: vi.fn(() => 5)
    },
    markers: [] as Array<{ setMap: ReturnType<typeof vi.fn>; title: string }>
  }

  const maps = {
    LatLng: vi.fn((lat: number, lng: number) => ({ lat, lng })),
    Size: vi.fn((width: number, height: number) => ({ width, height })),
    Point: vi.fn((x: number, y: number) => ({ x, y })),
    MarkerImage: vi.fn((src: string) => ({ src })),
    Map: vi.fn(() => state.mapInstance),
    Marker: vi.fn((options: { title: string }) => {
      const marker = {
        setMap: vi.fn(),
        title: options.title
      }
      state.markers.push(marker)
      return marker
    }),
    event: {
      addListener: vi.fn((target: unknown, eventName: string, handler: () => void) => {
        if (target === state.mapInstance && eventName === 'idle') {
          state.idleHandler = handler
        }

        if (eventName === 'click') {
          state.markerClickHandlers.push(handler)
        }
      })
    },
    load: vi.fn((callback: () => void) => callback())
  }

  return {
    state,
    maps,
    hasKakaoMapKey: vi.fn(() => state.hasKey),
    loadKakaoMaps: vi.fn(() => Promise.resolve()),
    getKakaoMaps: vi.fn(() => maps),
    getKakaoMapFallbackMessage: vi.fn(
      () =>
        '카카오 지도 API 키가 아직 설정되지 않았습니다. frontend/.env에 VITE_KAKAO_MAP_APP_KEY를 설정하면 실제 지도를 불러올 수 있습니다.'
    )
  }
})

vi.mock('@/map/kakaoLoader', () => ({
  hasKakaoMapKey: kakaoMock.hasKakaoMapKey,
  loadKakaoMaps: kakaoMock.loadKakaoMaps,
  getKakaoMaps: kakaoMock.getKakaoMaps,
  getKakaoMapFallbackMessage: kakaoMock.getKakaoMapFallbackMessage
}))

function property(propertyId: number): PropertyMapItem {
  return {
    propertyId,
    propertyType: 'APARTMENT',
    name: `테스트 단지 ${propertyId}`,
    address: '서울특별시 강남구 테스트로',
    lat: 37.5 + propertyId / 100000,
    lng: 127.03 + propertyId / 100000,
    latestTransaction: null,
    dealCount: 1,
    aiAvailable: true
  }
}

describe('KakaoMapPanel', () => {
  beforeEach(() => {
    kakaoMock.state.hasKey = false
    kakaoMock.state.idleHandler = null
    kakaoMock.state.markerClickHandlers = []
    kakaoMock.state.markers = []
    kakaoMock.state.mapInstance.getBounds.mockClear()
    kakaoMock.state.mapInstance.getLevel.mockClear()
    kakaoMock.maps.LatLng.mockClear()
    kakaoMock.maps.Size.mockClear()
    kakaoMock.maps.Point.mockClear()
    kakaoMock.maps.MarkerImage.mockClear()
    kakaoMock.maps.Map.mockClear()
    kakaoMock.maps.Marker.mockClear()
    kakaoMock.maps.event.addListener.mockClear()
    kakaoMock.maps.load.mockClear()
    kakaoMock.hasKakaoMapKey.mockClear()
    kakaoMock.loadKakaoMaps.mockClear()
    kakaoMock.getKakaoMaps.mockClear()
    kakaoMock.getKakaoMapFallbackMessage.mockClear()
  })

  it('keeps the Korean fallback visible when the Kakao Maps key is missing', () => {
    const wrapper = mount(KakaoMapPanel, {
      props: {
        items: [],
        selectedPropertyId: null
      }
    })

    expect(wrapper.text()).toContain('지도 준비 중')
    expect(wrapper.text()).toContain('카카오 지도 API 키가 아직 설정되지 않았습니다.')
    expect(wrapper.text()).toContain('VITE_KAKAO_MAP_APP_KEY')
  })

  it('creates a Kakao map, emits lifecycle bounds, syncs markers, and forwards marker clicks when a key exists', async () => {
    kakaoMock.state.hasKey = true
    vi.useFakeTimers()

    try {
      const wrapper = mount(KakaoMapPanel, {
        props: {
          items: [property(1001)],
          selectedPropertyId: null
        }
      })

      await flushPromises()

      expect(kakaoMock.loadKakaoMaps).toHaveBeenCalledTimes(1)
      expect(kakaoMock.getKakaoMaps).toHaveBeenCalledTimes(1)
      expect(kakaoMock.maps.Map).toHaveBeenCalledTimes(1)
      expect(kakaoMock.maps.event.addListener).toHaveBeenCalledWith(
        kakaoMock.state.mapInstance,
        'idle',
        expect.any(Function)
      )
      expect(wrapper.emitted('ready')?.[0]).toEqual([
        {
          swLat: 37.48,
          swLng: 127.01,
          neLat: 37.52,
          neLng: 127.06,
          zoomLevel: 5
        }
      ])

      expect(kakaoMock.maps.Marker).toHaveBeenCalledTimes(1)
      expect(kakaoMock.state.markers[0].title).toBe('property-1001')

      kakaoMock.state.idleHandler?.()
      vi.advanceTimersByTime(180)

      expect(wrapper.emitted('boundsChanged')?.[0]).toEqual([
        {
          swLat: 37.48,
          swLng: 127.01,
          neLat: 37.52,
          neLng: 127.06,
          zoomLevel: 5
        }
      ])

      kakaoMock.state.markerClickHandlers[0]()
      expect(wrapper.emitted('propertySelected')?.[0]).toEqual([1001])

      const firstMarker = kakaoMock.state.markers[0]
      await wrapper.setProps({
        items: [property(1002)],
        selectedPropertyId: 1002
      })
      await flushPromises()

      expect(firstMarker.setMap).toHaveBeenCalledWith(null)
      expect(kakaoMock.maps.Marker).toHaveBeenCalledTimes(2)
      expect(kakaoMock.state.markers[1].title).toBe('property-1002')
    } finally {
      vi.useRealTimers()
    }
  })
})
