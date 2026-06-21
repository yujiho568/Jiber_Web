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
