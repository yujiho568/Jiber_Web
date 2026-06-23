import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type {
  AdministrativeCluster,
  PagedResponse,
  PropertyDetail,
  PropertyMapItem,
  PropertyMapResponse,
  PropertySearchItem
} from '@/api/types'
import KakaoMapPanel from '@/components/KakaoMapPanel.vue'
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
  removeApartment: vi.fn(),
  addArea: vi.fn()
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

function mapResponse(items: PropertyMapItem[], administrativeClusters: AdministrativeCluster[] = []): PropertyMapResponse {
  return {
    items,
    administrativeClusters,
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
  recentTransactionCount: 4,
  aiAvailable: true
}

const seedAdministrativeCluster: AdministrativeCluster = {
  clusterId: 'legal-dong-1168010100',
  level: 'LEGAL_DONG',
  sido: 'Seoul',
  sigungu: 'Gangnam-gu',
  legalDong: 'Yeoksam-dong',
  label: 'Yeoksam-dong',
  centerLat: 37.5008,
  centerLng: 127.0366,
  propertyCount: 12,
  transactionCount: 34,
  averageDealAmount: 1230000000
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
      { path: '/chat', component: { template: '<main />' } },
      { path: '/properties/:propertyId', component: PropertyDetailView }
    ]
  })
}

async function mountMapView(options: { authenticated?: boolean; path?: string } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createTestRouter('/map')
  await router.push(options.path ?? '/map')
  await router.isReady()

  if (options.authenticated) {
    useAuthStore().setSession(userSession)
  }

  const wrapper = mount(MapView, {
    global: {
      plugins: [pinia, router]
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
  favoritesApiMock.addArea.mockReset().mockResolvedValue({
    favoriteAreaId: 801,
    label: '현재 지도 영역',
    createdAt: '2026-06-19T10:00:00+09:00',
    message: '저장했습니다.'
  })
})

describe('MapView keyword search', () => {
  it('passes administrative clusters from map search to KakaoMapPanel and clears them for keyword search', async () => {
    propertyApiMock.getMapProperties.mockResolvedValueOnce(mapResponse([seedMapItem], [seedAdministrativeCluster]))
    propertyApiMock.searchProperties.mockResolvedValueOnce(searchResponse([importedSearchItem]))
    const { wrapper } = await mountMapView()

    expect(wrapper.findComponent(KakaoMapPanel).props('administrativeClusters')).toEqual([seedAdministrativeCluster])

    await wrapper.get('[data-test="map-search-keyword"]').setValue('keyword')
    await wrapper.get('[data-test="map-search-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.findComponent(KakaoMapPanel).props('administrativeClusters')).toEqual([])
  })

  it('clears administrative clusters when map bounds search fails', async () => {
    propertyApiMock.getMapProperties
      .mockResolvedValueOnce(mapResponse([seedMapItem], [seedAdministrativeCluster]))
      .mockRejectedValueOnce(new Error('map failed'))
    const { wrapper } = await mountMapView()

    expect(wrapper.findComponent(KakaoMapPanel).props('administrativeClusters')).toEqual([seedAdministrativeCluster])

    wrapper.findComponent(KakaoMapPanel).vm.$emit('loadError')
    await flushPromises()

    expect(wrapper.findComponent(KakaoMapPanel).props('administrativeClusters')).toEqual([])
  })

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

  it('adds the current map area as a favorite for authenticated users', async () => {
    const { wrapper } = await mountMapView({ authenticated: true })

    await wrapper.get('[data-test="area-favorite-button"]').trigger('click')
    await flushPromises()

    expect(favoritesApiMock.addArea).toHaveBeenCalledWith(
      expect.objectContaining({
        label: '현재 지도 영역',
        centerLat: 37.5,
        centerLng: 127.035,
        zoomLevel: 5
      })
    )
    expect(wrapper.text()).toContain('관심 지역에 추가했습니다.')
  })

  it('does not call the area favorite API for anonymous users', async () => {
    const { wrapper } = await mountMapView()

    await wrapper.get('[data-test="area-favorite-button"]').trigger('click')
    await flushPromises()

    expect(favoritesApiMock.addArea).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('로그인 후 관심 지역을 저장할 수 있습니다.')
  })

  it('handles duplicate and validation area favorite errors in Korean', async () => {
    favoritesApiMock.addArea.mockRejectedValueOnce(createApiError('FAVORITE_AREA_ALREADY_EXISTS'))
    const duplicateWrapper = await mountMapView({ authenticated: true })

    await duplicateWrapper.wrapper.get('[data-test="area-favorite-button"]').trigger('click')
    await flushPromises()

    expect(duplicateWrapper.wrapper.text()).toContain('이미 관심 지역에 저장되어 있습니다.')

    favoritesApiMock.addArea.mockRejectedValueOnce(createApiError('VALIDATION_FAILED'))
    const validationWrapper = await mountMapView({ authenticated: true })

    await validationWrapper.wrapper.get('[data-test="area-favorite-button"]').trigger('click')
    await flushPromises()

    expect(validationWrapper.wrapper.text()).toContain('관심 지역 정보를 저장할 수 없습니다.')
  })

  it('uses the active keyword as the area favorite label and selected result as center', async () => {
    propertyApiMock.searchProperties.mockResolvedValueOnce(searchResponse([importedSearchItem]))
    const { wrapper } = await mountMapView({ authenticated: true })

    await wrapper.get('[data-test="map-search-keyword"]').setValue('무악동')
    await wrapper.get('[data-test="map-search-form"]').trigger('submit')
    await flushPromises()
    await wrapper.get('[data-test="area-favorite-button"]').trigger('click')
    await flushPromises()

    expect(favoritesApiMock.addArea).toHaveBeenCalledWith(
      expect.objectContaining({
        label: '검색: 무악동',
        centerLat: importedSearchItem.lat,
        centerLng: importedSearchItem.lng,
        zoomLevel: 5
      })
    )
  })

  it('restores a favorite area from map query parameters without a Kakao key', async () => {
    const { wrapper } = await mountMapView({
      path: '/map?areaLabel=%EA%B2%80%EC%83%89%3A%20%EB%AC%B4%EC%95%85%EB%8F%99&centerLat=37.5738636&centerLng=126.9594466&zoomLevel=6'
    })

    expect(propertyApiMock.getMapProperties).toHaveBeenCalledWith(
      expect.objectContaining({
        swLat: 37.5538636,
        swLng: 126.9344466,
        neLat: 37.5938636,
        neLng: 126.9844466,
        zoomLevel: 6
      })
    )
    expect(wrapper.text()).toContain('검색: 무악동 관심 지역을 지도로 불러왔습니다.')
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
