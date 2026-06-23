import { afterEach, describe, expect, it, vi } from 'vitest'

import { apiClient } from '@/api/client'
import { favoritesApi } from '@/api/favorites'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('favoritesApi apartment endpoints', () => {
  it('lists apartment favorites through the Spring API client', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce({ data: { items: [] } })

    await favoritesApi.listApartments()

    expect(getSpy).toHaveBeenCalledWith('/favorites/apartments')
  })

  it('adds an apartment favorite with the property id payload', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({
      data: {
        favoriteId: 10,
        propertyId: 1912,
        createdAt: '2026-06-19T10:00:00+09:00',
        message: '저장했습니다.'
      }
    })

    await favoritesApi.addApartment(1912)

    expect(postSpy).toHaveBeenCalledWith('/favorites/apartments', { propertyId: 1912 })
  })

  it('deletes an apartment favorite by property id', async () => {
    const deleteSpy = vi.spyOn(apiClient, 'delete').mockResolvedValueOnce({
      data: {
        propertyId: 1912,
        message: '삭제했습니다.'
      }
    })

    await favoritesApi.removeApartment(1912)

    expect(deleteSpy).toHaveBeenCalledWith('/favorites/apartments/1912')
  })
})

describe('favoritesApi area endpoints', () => {
  it('lists area favorites through the Spring API client', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce({ data: { items: [] } })

    await favoritesApi.listAreas()

    expect(getSpy).toHaveBeenCalledWith('/favorites/areas')
  })

  it('adds an area favorite with the contract payload', async () => {
    const payload = {
      label: '검색: 무악동',
      centerLat: 37.5738636,
      centerLng: 126.9594466,
      zoomLevel: 5
    }
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({
      data: {
        favoriteAreaId: 801,
        label: '검색: 무악동',
        createdAt: '2026-06-19T10:00:00+09:00',
        message: '저장했습니다.'
      }
    })

    await favoritesApi.addArea(payload)

    expect(postSpy).toHaveBeenCalledWith('/favorites/areas', payload)
  })

  it('deletes an area favorite by favorite area id', async () => {
    const deleteSpy = vi.spyOn(apiClient, 'delete').mockResolvedValueOnce({
      data: {
        favoriteAreaId: 801,
        message: '삭제했습니다.'
      }
    })

    await favoritesApi.removeArea(801)

    expect(deleteSpy).toHaveBeenCalledWith('/favorites/areas/801')
  })

  it('propagates duplicate and not-found area errors for view-level handling', async () => {
    const duplicateError = new Error('FAVORITE_AREA_ALREADY_EXISTS')
    const notFoundError = new Error('FAVORITE_AREA_NOT_FOUND')
    vi.spyOn(apiClient, 'post').mockRejectedValueOnce(duplicateError)
    vi.spyOn(apiClient, 'delete').mockRejectedValueOnce(notFoundError)

    await expect(favoritesApi.addArea({ label: '현재 지도 영역', centerLat: 37.5, centerLng: 127.03 })).rejects.toBe(
      duplicateError
    )
    await expect(favoritesApi.removeArea(801)).rejects.toBe(notFoundError)
  })
})
