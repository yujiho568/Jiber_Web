import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import type { PropertyDetail, ShapValue, ValuationResponse } from '@/api/types'
import { useChatContextStore } from '@/stores/chatContext'

const property: PropertyDetail = {
  propertyId: 1912,
  propertyType: 'APARTMENT',
  name: '경희궁롯데캐슬',
  address: {
    sido: '서울특별시',
    sigungu: '종로구',
    legalDong: '무악동',
    roadAddress: '통일로 230'
  },
  location: {
    lat: 37.5738636,
    lng: 126.9594466
  },
  summary: {
    builtYear: 2019,
    householdCount: 195,
    latestDealAmount: 1080000000,
    latestDealDate: '2026-06-08'
  },
  transactions: [],
  favorite: {
    apartmentFavorited: false,
    areaFavorited: false
  },
  ai: {
    valuationAvailable: true,
    shapAvailable: true
  }
}

const valuation: ValuationResponse = {
  propertyId: 1912,
  supported: true,
  estimatedPrice: 1100000000,
  currency: 'KRW',
  message: 'skeleton valuation'
}

const shapValues: ShapValue[] = [
  {
    feature: 'exclusiveAreaM2',
    labelKo: '전용면적',
    value: 84.95,
    shapValue: 254850000,
    direction: 'UP'
  }
]

describe('useChatContextStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    sessionStorage.clear()
  })

  it('keeps runtime analysis context in Pinia memory only', () => {
    const store = useChatContextStore()

    store.setPropertyAnalysisContext(property, valuation, shapValues)

    expect(store.runtimeContext?.property.name).toBe('경희궁롯데캐슬')
    expect(store.runtimeContext?.valuation?.estimatedPrice).toBe(1100000000)
    expect(localStorage.getItem('jiber.chat.runtimeContext')).toBeNull()
    expect(sessionStorage.getItem('jiber.chat.runtimeContext')).toBeNull()
  })
})
