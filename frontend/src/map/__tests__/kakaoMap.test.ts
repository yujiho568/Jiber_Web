import { describe, expect, it, vi } from 'vitest'

import { boundsFromKakao, syncKakaoMarkers } from '@/map/kakaoMap'
import type { PropertyMapItem } from '@/api/types'

function property(propertyId: number, lat: number, lng: number): PropertyMapItem {
  return {
    propertyId,
    propertyType: 'APARTMENT',
    name: `테스트 단지 ${propertyId}`,
    address: '서울특별시 강남구 테스트로',
    lat,
    lng,
    latestTransaction: null,
    dealCount: 1,
    aiAvailable: true
  }
}

describe('kakaoMap utilities', () => {
  it('converts Kakao bounds to the property map search contract', () => {
    const bounds = {
      getSouthWest: () => ({
        getLat: () => 37.48,
        getLng: () => 127.01
      }),
      getNorthEast: () => ({
        getLat: () => 37.52,
        getLng: () => 127.06
      })
    }

    expect(boundsFromKakao(bounds, 5)).toEqual({
      swLat: 37.48,
      swLng: 127.01,
      neLat: 37.52,
      neLng: 127.06,
      zoomLevel: 5
    })
  })

  it('clears old markers, renders new markers, and wires marker clicks to property selection', () => {
    const oldMarker = { setMap: vi.fn() }
    const clickHandlers: Array<() => void> = []
    const createdMarkers: Array<{ setMap: ReturnType<typeof vi.fn>; propertyId: number }> = []
    const map = { id: 'map' }
    const kakaoMaps = {
      LatLng: vi.fn((lat: number, lng: number) => ({ lat, lng })),
      Size: vi.fn(),
      Point: vi.fn(),
      MarkerImage: vi.fn(),
      Marker: vi.fn((options: { title: string }) => {
        const marker = {
          propertyId: Number(options.title.replace('property-', '')),
          setMap: vi.fn()
        }
        createdMarkers.push(marker)
        return marker
      }),
      event: {
        addListener: vi.fn((_target: unknown, eventName: string, handler: () => void) => {
          if (eventName === 'click') {
            clickHandlers.push(handler)
          }
        })
      }
    }
    const onClick = vi.fn()

    const markers = syncKakaoMarkers({
      kakaoMaps,
      map,
      previousMarkers: [oldMarker],
      items: [property(1001, 37.5, 127.03), property(1002, 37.51, 127.04)],
      selectedPropertyId: 1002,
      onClick
    })

    expect(oldMarker.setMap).toHaveBeenCalledWith(null)
    expect(markers).toHaveLength(2)
    expect(kakaoMaps.Marker).toHaveBeenCalledTimes(2)
    expect(kakaoMaps.event.addListener).toHaveBeenCalledTimes(2)

    clickHandlers[1]()
    expect(onClick).toHaveBeenCalledWith(1002)
    expect(createdMarkers[0].setMap).not.toHaveBeenCalledWith(null)
  })
})
