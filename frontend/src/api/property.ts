import { apiClient, compactParams, toListQueryValue } from './client'
import type {
  MapSearchParams,
  PagedResponse,
  PropertyDetail,
  PropertyMapResponse,
  PropertySearchItem,
  PropertySearchParams,
  ShapRequest,
  ShapResponse,
  ValuationRequest,
  ValuationResponse
} from './types'

function mapSearchParams(params: MapSearchParams) {
  return compactParams({
    ...params,
    propertyTypes: toListQueryValue(params.propertyTypes),
    transactionTypes: toListQueryValue(params.transactionTypes)
  })
}

function propertySearchParams(params: PropertySearchParams) {
  return compactParams({
    ...params,
    propertyTypes: toListQueryValue(params.propertyTypes),
    transactionTypes: toListQueryValue(params.transactionTypes)
  })
}

export const propertyApi = {
  async getMapProperties(params: MapSearchParams): Promise<PropertyMapResponse> {
    const { data } = await apiClient.get<PropertyMapResponse>('/properties/map', {
      params: mapSearchParams(params)
    })
    return data
  },

  async searchProperties(params: PropertySearchParams): Promise<PagedResponse<PropertySearchItem>> {
    const { data } = await apiClient.get<PagedResponse<PropertySearchItem>>('/properties/search', {
      params: propertySearchParams(params)
    })
    return data
  },

  async getProperty(propertyId: string | number): Promise<PropertyDetail> {
    const { data } = await apiClient.get<PropertyDetail>(`/properties/${propertyId}`)
    return data
  },

  async requestValuation(propertyId: string | number, payload: ValuationRequest): Promise<ValuationResponse> {
    const { data } = await apiClient.post<ValuationResponse>(`/properties/${propertyId}/valuation`, payload)
    return data
  },

  async requestShap(propertyId: string | number, payload: ShapRequest): Promise<ShapResponse> {
    const { data } = await apiClient.post<ShapResponse>(`/properties/${propertyId}/shap`, payload)
    return data
  }
}
