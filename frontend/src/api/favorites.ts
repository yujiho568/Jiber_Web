import { apiClient } from './client'
import type {
  FavoriteApartmentCreateResponse,
  FavoriteApartmentDeleteResponse,
  FavoriteApartmentItem,
  FavoriteAreaCreateRequest,
  FavoriteAreaCreateResponse,
  FavoriteAreaDeleteResponse,
  FavoriteAreaItem
} from './types'

export const favoritesApi = {
  async listApartments(): Promise<{ items: FavoriteApartmentItem[] }> {
    const { data } = await apiClient.get<{ items: FavoriteApartmentItem[] }>('/favorites/apartments')
    return data
  },

  async addApartment(propertyId: number): Promise<FavoriteApartmentCreateResponse> {
    const { data } = await apiClient.post<FavoriteApartmentCreateResponse>('/favorites/apartments', {
      propertyId
    })
    return data
  },

  async removeApartment(propertyId: number): Promise<FavoriteApartmentDeleteResponse> {
    const { data } = await apiClient.delete<FavoriteApartmentDeleteResponse>(`/favorites/apartments/${propertyId}`)
    return data
  },

  async listAreas(): Promise<{ items: FavoriteAreaItem[] }> {
    const { data } = await apiClient.get<{ items: FavoriteAreaItem[] }>('/favorites/areas')
    return data
  },

  async addArea(payload: FavoriteAreaCreateRequest): Promise<FavoriteAreaCreateResponse> {
    const { data } = await apiClient.post<FavoriteAreaCreateResponse>('/favorites/areas', payload)
    return data
  },

  async removeArea(favoriteAreaId: number): Promise<FavoriteAreaDeleteResponse> {
    const { data } = await apiClient.delete<FavoriteAreaDeleteResponse>(`/favorites/areas/${favoriteAreaId}`)
    return data
  }
}
