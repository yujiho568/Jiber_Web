import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { FavoriteApartmentItem } from '@/api/types'
import FavoritesView from '@/views/FavoritesView.vue'

const favoritesApiMock = vi.hoisted(() => ({
  listApartments: vi.fn(),
  removeApartment: vi.fn()
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

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/favorites', component: FavoritesView },
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
})

describe('FavoritesView', () => {
  it('renders apartment favorite items and the cautious area favorite state', async () => {
    favoritesApiMock.listApartments.mockResolvedValueOnce({ items: [favoriteItem] })

    const { wrapper } = await mountFavoritesView()

    expect(favoritesApiMock.listApartments).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('경희궁롯데캐슬')
    expect(wrapper.text()).toContain('전세')
    expect(wrapper.text()).toContain('10.8억 원')
    expect(wrapper.text()).toContain('관심 지역 저장은 준비 중입니다.')
  })

  it('shows an empty state when there are no apartment favorites', async () => {
    const { wrapper } = await mountFavoritesView()

    expect(wrapper.text()).toContain('저장한 아파트가 없습니다.')
    expect(wrapper.text()).toContain('지도나 상세 화면에서 관심 아파트를 추가하면 여기에 표시됩니다.')
  })

  it('links apartment favorites to the property detail page', async () => {
    favoritesApiMock.listApartments.mockResolvedValueOnce({ items: [favoriteItem] })

    const { wrapper, router } = await mountFavoritesView()

    await wrapper.get('a[href="/properties/1912"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/properties/1912')
  })

  it('deletes an apartment favorite and refreshes the list', async () => {
    favoritesApiMock.listApartments.mockResolvedValueOnce({ items: [favoriteItem] }).mockResolvedValueOnce({ items: [] })

    const { wrapper } = await mountFavoritesView()

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(favoritesApiMock.removeApartment).toHaveBeenCalledWith(1912)
    expect(favoritesApiMock.listApartments).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('관심 아파트에서 삭제했습니다.')
    expect(wrapper.text()).toContain('저장한 아파트가 없습니다.')
  })
})
