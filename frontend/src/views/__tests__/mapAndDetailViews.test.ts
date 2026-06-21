import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { PagedResponse, PropertyDetail, PropertyMapItem, PropertySearchItem } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import MapView from '@/views/MapView.vue'
import PropertyDetailView from '@/views/PropertyDetailView.vue'

const userSession = {
  accessToken: 'memory-only-detail-token',
  user: {
    userId: 7,
    email: 'detail@example.com',
    displayName: '상세 사용자',
    roles: ['USER' as const]
  }
}

const propertyApiMock = vi.hoisted(() => ({
  getMapProperties: vi.fn(),
  searchProperties: vi.fn(),
  getProperty: vi.fn(),
  requestValuation: vi.fn(),
  requestShap: vi.fn()
}))

const favoritesApiMock = vi.hoisted(() => ({
  addApartment: vi.fn(),
  removeApartment: vi.fn()
}))

const kakaoLoaderMock = vi.hoisted(() => ({
  hasKey: false,
  hasKakaoMapKey: vi.fn(() => kakaoLoaderMock.hasKey),
  loadKakaoMaps: vi.fn(),
  getKakaoMaps: vi.fn(),
  getKakaoMapFallbackMessage: vi.fn(
    () =>
      '카카오 지도 API 키가 아직 설정되지 않았습니다. frontend/.env에 VITE_KAKAO_MAP_APP_KEY를 설정하면 실제 지도를 불러올 수 있습니다.'
  )
}))

vi.mock('@/api/property', () => ({
  propertyApi: propertyApiMock
}))

vi.mock('@/api/favorites', () => ({
  favoritesApi: favoritesApiMock
}))

vi.mock('@/map/kakaoLoader', () => ({
  hasKakaoMapKey: kakaoLoaderMock.hasKakaoMapKey,
  loadKakaoMaps: kakaoLoaderMock.loadKakaoMaps,
  getKakaoMaps: kakaoLoaderMock.getKakaoMaps,
  getKakaoMapFallbackMessage: kakaoLoaderMock.getKakaoMapFallbackMessage
}))

function mapResponse(items: PropertyMapItem[]) {
  return {
    items,
    bounds: {
      swLat: 37.48,
      swLng: 127.01,
      neLat: 37.52,
      neLng: 127.06
    },
    filters: {
      propertyTypes: ['APARTMENT'],
      transactionTypes: ['SALE'],
      zoomLevel: 5
    }
  }
}

function searchResponse(items: PropertySearchItem[]): PagedResponse<PropertySearchItem> {
  return {
    items,
    page: {
      number: 0,
      size: 20,
      totalElements: items.length,
      totalPages: items.length ? 1 : 0
    }
  }
}

const seedMapItem: PropertyMapItem = {
  propertyId: 1001,
  propertyType: 'APARTMENT',
  name: '샘플 역삼아파트',
  address: '서울특별시 강남구 테헤란로 123',
  lat: 37.5008,
  lng: 127.0366,
  latestTransaction: {
    transactionType: 'SALE',
    dealAmount: 1250000000,
    dealDate: '2026-05-20'
  },
  dealCount: 18,
  aiAvailable: true
}

const importedSearchItem: PropertySearchItem = {
  propertyId: 1912,
  propertyType: 'APARTMENT',
  name: '경희궁롯데캐슬',
  address: '서울특별시 종로구 무악동',
  legalDong: '무악동',
  lat: 37.5738636,
  lng: 126.9594466,
  latestTransaction: {
    transactionType: 'JEONSE',
    dealAmount: 1080000000,
    dealDate: '2026-06-08'
  },
  aiAvailable: true
}

const importedDetail: PropertyDetail = {
  propertyId: 1912,
  propertyType: 'APARTMENT',
  name: '경희궁롯데캐슬',
  address: {
    sido: '서울특별시',
    sigungu: '종로구',
    legalDong: '무악동'
  },
  location: {
    lat: 37.5738636,
    lng: 126.9594466
  },
  summary: {
    builtYear: 2019,
    householdCount: null,
    latestDealAmount: 1080000000,
    latestDealDate: '2026-06-08'
  },
  transactions: [
    {
      transactionId: 5912,
      transactionType: 'JEONSE',
      dealAmount: 1080000000,
      dealDate: '2026-06-08',
      exclusiveAreaM2: 84.8792,
      floor: 10
    }
  ],
  favorite: {
    apartmentFavorited: false,
    areaFavorited: false
  },
  ai: {
    valuationAvailable: true,
    shapAvailable: true
  }
}

function createTestRouter(initialPath = '/map') {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<main />' } },
      { path: '/map', component: MapView },
      { path: '/properties/:propertyId', component: PropertyDetailView }
    ]
  })
}

async function mountMapView() {
  const router = createTestRouter('/map')
  await router.push('/map')
  await router.isReady()

  const wrapper = mount(MapView, {
    global: {
      plugins: [router]
    }
  })
  await flushPromises()

  return { wrapper, router }
}

function detailWithFavorite(apartmentFavorited: boolean): PropertyDetail {
  return {
    ...importedDetail,
    favorite: {
      apartmentFavorited,
      areaFavorited: false
    }
  }
}

function createApiError(code: string) {
  return {
    isAxiosError: true,
    response: {
      data: {
        code,
        message: '요청을 처리하지 못했습니다.',
        path: '/api/v1/favorites/apartments',
        timestamp: '2026-06-19T10:00:00+09:00'
      }
    }
  }
}

async function mountPropertyDetailView(options: { authenticated?: boolean; detail?: PropertyDetail } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  propertyApiMock.getProperty.mockResolvedValueOnce(options.detail ?? importedDetail)

  const router = createTestRouter('/properties/1912')
  await router.push('/properties/1912')
  await router.isReady()

  if (options.authenticated) {
    useAuthStore().setSession(userSession)
  }

  const wrapper = mount(PropertyDetailView, {
    global: {
      plugins: [pinia, router],
      stubs: {
        TransactionChart: true,
        ShapChart: true
      }
    }
  })
  await flushPromises()

  return wrapper
}

beforeEach(() => {
  kakaoLoaderMock.hasKey = false
  propertyApiMock.getMapProperties.mockReset().mockResolvedValue(mapResponse([]))
  propertyApiMock.searchProperties.mockReset().mockResolvedValue(searchResponse([]))
  propertyApiMock.getProperty.mockReset().mockResolvedValue(importedDetail)
  propertyApiMock.requestValuation.mockReset()
  propertyApiMock.requestShap.mockReset()
  favoritesApiMock.addApartment.mockReset().mockResolvedValue({
    favoriteId: 1,
    propertyId: 1912,
    createdAt: '2026-06-19T10:00:00+09:00',
    message: '저장했습니다.'
  })
  favoritesApiMock.removeApartment.mockReset().mockResolvedValue({
    propertyId: 1912,
    message: '삭제했습니다.'
  })
})

describe('MapView keyword search', () => {
  it('renders a Korean keyword search input', async () => {
    const { wrapper } = await mountMapView()

    expect(wrapper.text()).toContain('단지명 또는 지역 검색')
    expect(wrapper.get('[data-test="map-search-keyword"]').attributes('placeholder')).toContain('경희궁롯데캐슬')
  })

  it('searches imported canonical data and keeps the missing-key fallback usable', async () => {
    propertyApiMock.searchProperties.mockResolvedValueOnce(searchResponse([importedSearchItem]))
    const { wrapper } = await mountMapView()

    await wrapper.get('[data-test="map-search-keyword"]').setValue('경희궁롯데캐슬')
    await wrapper.get('[data-test="map-search-form"]').trigger('submit')
    await flushPromises()

    expect(propertyApiMock.searchProperties).toHaveBeenCalledWith(
      expect.objectContaining({
        keyword: '경희궁롯데캐슬',
        propertyTypes: ['APARTMENT'],
        transactionTypes: ['SALE', 'JEONSE', 'MONTHLY_RENT'],
        size: 20,
        sort: 'relevance,desc'
      })
    )
    expect(wrapper.text()).toContain('카카오 지도 API 키가 아직 설정되지 않았습니다.')
    expect(wrapper.text()).toContain('경희궁롯데캐슬')
    expect(wrapper.text()).toContain('전세')
    expect(wrapper.find('#property-result-1912').classes()).toContain('is-selected')
  })

  it('applies transaction filters to keyword searches and routes from a result item', async () => {
    propertyApiMock.searchProperties.mockResolvedValueOnce(searchResponse([importedSearchItem]))
    const { wrapper, router } = await mountMapView()

    await wrapper.get('input[value="SALE"]').setValue(false)
    await wrapper.get('input[value="MONTHLY_RENT"]').setValue(false)
    await wrapper.get('input[value="JEONSE"]').setValue(true)
    await wrapper.get('[data-test="map-search-keyword"]').setValue('무악동')
    await wrapper.get('[data-test="map-search-form"]').trigger('submit')
    await flushPromises()

    expect(propertyApiMock.searchProperties).toHaveBeenCalledWith(
      expect.objectContaining({
        keyword: '무악동',
        propertyTypes: ['APARTMENT'],
        transactionTypes: ['JEONSE']
      })
    )
    expect(propertyApiMock.searchProperties.mock.calls[0][0]).not.toHaveProperty('zoom')
    expect(propertyApiMock.searchProperties.mock.calls[0][0]).not.toHaveProperty('propertyType')

    await wrapper.get('a[href="/properties/1912"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/properties/1912')
  })

  it('returns to map bounds search when an empty keyword is submitted', async () => {
    propertyApiMock.getMapProperties
      .mockResolvedValueOnce(mapResponse([]))
      .mockResolvedValueOnce(mapResponse([seedMapItem]))
    propertyApiMock.searchProperties.mockResolvedValueOnce(searchResponse([importedSearchItem]))
    const { wrapper } = await mountMapView()

    await wrapper.get('[data-test="map-search-keyword"]').setValue('종로구')
    await wrapper.get('[data-test="map-search-form"]').trigger('submit')
    await flushPromises()
    await wrapper.get('[data-test="map-search-keyword"]').setValue('   ')
    await wrapper.get('[data-test="map-search-form"]').trigger('submit')
    await flushPromises()

    expect(propertyApiMock.getMapProperties).toHaveBeenCalledTimes(2)
    expect(propertyApiMock.searchProperties).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('샘플 역삼아파트')
    expect(wrapper.text()).not.toContain('"종로구" 검색 결과')
  })
})

describe('PropertyDetailView transaction summary', () => {
  it('shows the latest transaction type and recent transaction count in Korean', async () => {
    const wrapper = await mountPropertyDetailView()

    expect(propertyApiMock.getProperty).toHaveBeenCalledWith('1912')
    expect(wrapper.text()).toContain('경희궁롯데캐슬')
    expect(wrapper.text()).toContain('최근 거래유형')
    expect(wrapper.text()).toContain('전세')
    expect(wrapper.text()).toContain('최근 거래 1건')
  })

  it('reflects initial apartment favorite states', async () => {
    const unfavorited = await mountPropertyDetailView({ authenticated: true, detail: detailWithFavorite(false) })
    expect(unfavorited.text()).toContain('관심 아파트 추가')

    const favorited = await mountPropertyDetailView({ authenticated: true, detail: detailWithFavorite(true) })
    expect(favorited.text()).toContain('관심 아파트 삭제')
  })

  it('adds an apartment favorite and updates the button state', async () => {
    const wrapper = await mountPropertyDetailView({ authenticated: true, detail: detailWithFavorite(false) })

    await wrapper.get('[data-test="apartment-favorite-button"]').trigger('click')
    await flushPromises()

    expect(favoritesApiMock.addApartment).toHaveBeenCalledWith(1912)
    expect(wrapper.text()).toContain('관심 아파트에 추가했습니다.')
    expect(wrapper.text()).toContain('관심 아파트 삭제')
  })

  it('removes an apartment favorite and updates the button state', async () => {
    const wrapper = await mountPropertyDetailView({ authenticated: true, detail: detailWithFavorite(true) })

    await wrapper.get('[data-test="apartment-favorite-button"]').trigger('click')
    await flushPromises()

    expect(favoritesApiMock.removeApartment).toHaveBeenCalledWith(1912)
    expect(wrapper.text()).toContain('관심 아파트에서 삭제했습니다.')
    expect(wrapper.text()).toContain('관심 아파트 추가')
  })

  it('guides anonymous users to log in before saving favorites', async () => {
    const wrapper = await mountPropertyDetailView({ detail: detailWithFavorite(false) })

    await wrapper.get('[data-test="apartment-favorite-button"]').trigger('click')
    await flushPromises()

    expect(favoritesApiMock.addApartment).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('로그인 후 관심 아파트를 저장할 수 있습니다.')
  })

  it('handles duplicate favorite creation safely', async () => {
    favoritesApiMock.addApartment.mockRejectedValueOnce(createApiError('FAVORITE_ALREADY_EXISTS'))
    const wrapper = await mountPropertyDetailView({ authenticated: true, detail: detailWithFavorite(false) })

    await wrapper.get('[data-test="apartment-favorite-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('이미 관심 아파트에 저장되어 있습니다.')
    expect(wrapper.text()).toContain('관심 아파트 삭제')
  })

  it('handles missing favorite deletion safely', async () => {
    favoritesApiMock.removeApartment.mockRejectedValueOnce(createApiError('FAVORITE_NOT_FOUND'))
    const wrapper = await mountPropertyDetailView({ authenticated: true, detail: detailWithFavorite(true) })

    await wrapper.get('[data-test="apartment-favorite-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('이미 삭제된 관심 아파트입니다.')
    expect(wrapper.text()).toContain('관심 아파트 추가')
  })
})
