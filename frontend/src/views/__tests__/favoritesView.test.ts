import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { FavoriteApartmentItem, FavoriteAreaItem } from '@/api/types'
import FavoritesView from '@/views/FavoritesView.vue'

const favoritesApiMock = vi.hoisted(() => ({
  listApartments: vi.fn(),
  removeApartment: vi.fn(),
  listAreas: vi.fn(),
  removeArea: vi.fn()
}))

vi.mock('@/api/favorites', () => ({
  favoritesApi: favoritesApiMock
}))

const favoriteItem: FavoriteApartmentItem = {
  favoriteId: 11,
  propertyId: 1912,
  propertyType: 'APARTMENT',
  name: '경희궁롯데캐슬',
  address: '서울특별시 종로구 무악동 89',
  lat: 37.5738636,
  lng: 126.9594466,
  latestTransaction: {
    transactionType: 'JEONSE',
    dealAmount: 1080000000,
    dealDate: '2026-06-08'
  },
  createdAt: '2026-06-19T10:00:00+09:00'
}

const favoriteAreaItem: FavoriteAreaItem = {
  favoriteAreaId: 801,
  label: '검색: 무악동',
  centerLat: 37.5738636,
  centerLng: 126.9594466,
  zoomLevel: 6,
  createdAt: '2026-06-20T10:00:00+09:00'
}

function createApiError(code: string) {
  return {
    isAxiosError: true,
    response: {
      data: {
        code,
        message: '요청을 처리하지 못했습니다.',
        path: '/api/v1/favorites/areas',
        timestamp: '2026-06-20T10:00:00+09:00'
      }
    }
  }
}

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/favorites', component: FavoritesView },
      { path: '/map', component: { template: '<main />' } },
      { path: '/chat', component: { template: '<main />' } },
      { path: '/properties/:propertyId', component: { template: '<main />' } }
    ]
  })
}

async function mountFavoritesView() {
  const router = createTestRouter()
  await router.push('/favorites')
  await router.isReady()

  const wrapper = mount(FavoritesView, {
    global: {
      plugins: [router]
    }
  })
  await flushPromises()

  return { wrapper, router }
}

beforeEach(() => {
  favoritesApiMock.listApartments.mockReset().mockResolvedValue({ items: [] })
  favoritesApiMock.removeApartment.mockReset().mockResolvedValue({
    propertyId: 1912,
    message: '삭제했습니다.'
  })
  favoritesApiMock.listAreas.mockReset().mockResolvedValue({ items: [] })
  favoritesApiMock.removeArea.mockReset().mockResolvedValue({
    favoriteAreaId: 801,
    message: '삭제했습니다.'
  })
})

describe('FavoritesView', () => {
  it('renders apartment and area favorite items', async () => {
    favoritesApiMock.listApartments.mockResolvedValueOnce({ items: [favoriteItem] })
    favoritesApiMock.listAreas.mockResolvedValueOnce({ items: [favoriteAreaItem] })

    const { wrapper } = await mountFavoritesView()

    expect(favoritesApiMock.listApartments).toHaveBeenCalledTimes(1)
    expect(favoritesApiMock.listAreas).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('경희궁롯데캐슬')
    expect(wrapper.text()).toContain('전세')
    expect(wrapper.text()).toContain('10.8억 원')
    expect(wrapper.text()).toContain('검색: 무악동')
    expect(wrapper.text()).toContain('지도 중심 37.5739, 126.9594')
    expect(wrapper.text()).toContain('지도 6단계')
  })

  it('shows empty states when there are no favorites', async () => {
    const { wrapper } = await mountFavoritesView()

    expect(wrapper.text()).toContain('저장한 아파트가 없습니다.')
    expect(wrapper.text()).toContain('지도나 상세 화면에서 관심 아파트를 추가하면 여기에 표시됩니다.')
    expect(wrapper.text()).toContain('저장한 관심 지역이 없습니다.')
    expect(wrapper.text()).toContain('지도에서 현재 영역을 관심 지역으로 등록하면 여기에 표시됩니다.')
  })

  it('links apartment favorites to the property detail page', async () => {
    favoritesApiMock.listApartments.mockResolvedValueOnce({ items: [favoriteItem] })

    const { wrapper, router } = await mountFavoritesView()

    await wrapper.get('a[href="/properties/1912"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/properties/1912')
  })

  it('links area favorites back to the map with restore query parameters', async () => {
    favoritesApiMock.listAreas.mockResolvedValueOnce({ items: [favoriteAreaItem] })

    const { wrapper, router } = await mountFavoritesView()

    await wrapper.get('a[href^="/map"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/map')
    expect(router.currentRoute.value.query).toMatchObject({
      areaLabel: '검색: 무악동',
      centerLat: '37.5738636',
      centerLng: '126.9594466',
      zoomLevel: '6'
    })
  })

  it('deletes an apartment favorite and refreshes the list', async () => {
    favoritesApiMock.listApartments.mockResolvedValueOnce({ items: [favoriteItem] }).mockResolvedValueOnce({ items: [] })

    const { wrapper } = await mountFavoritesView()

    await wrapper.get('[data-test="apartment-favorite-delete"]').trigger('click')
    await flushPromises()

    expect(favoritesApiMock.removeApartment).toHaveBeenCalledWith(1912)
    expect(favoritesApiMock.listApartments).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('관심 아파트에서 삭제했습니다.')
    expect(wrapper.text()).toContain('저장한 아파트가 없습니다.')
  })

  it('deletes an area favorite and refreshes the list', async () => {
    favoritesApiMock.listAreas.mockResolvedValueOnce({ items: [favoriteAreaItem] }).mockResolvedValueOnce({ items: [] })

    const { wrapper } = await mountFavoritesView()

    await wrapper.get('[data-test="area-favorite-delete"]').trigger('click')
    await flushPromises()

    expect(favoritesApiMock.removeArea).toHaveBeenCalledWith(801)
    expect(favoritesApiMock.listAreas).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('관심 지역에서 삭제했습니다.')
    expect(wrapper.text()).toContain('저장한 관심 지역이 없습니다.')
  })

  it('handles already deleted area favorites safely', async () => {
    favoritesApiMock.listAreas.mockResolvedValueOnce({ items: [favoriteAreaItem] })
    favoritesApiMock.removeArea.mockRejectedValueOnce(createApiError('FAVORITE_AREA_NOT_FOUND'))

    const { wrapper } = await mountFavoritesView()

    await wrapper.get('[data-test="area-favorite-delete"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('이미 삭제된 관심 지역입니다.')
    expect(wrapper.text()).toContain('저장한 관심 지역이 없습니다.')
  })
})
