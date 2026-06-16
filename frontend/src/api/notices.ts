import { apiClient, compactParams } from './client'
import type {
  NoticeDetail,
  NoticeListParams,
  NoticeMutationResponse,
  NoticeSummary,
  NoticeUpsertRequest,
  PagedResponse
} from './types'

export const noticesApi = {
  async list(params: NoticeListParams = {}): Promise<PagedResponse<NoticeSummary>> {
    const { data } = await apiClient.get<PagedResponse<NoticeSummary>>('/notices', {
      params: compactParams(params)
    })
    return data
  },

  async get(noticeId: string | number): Promise<NoticeDetail> {
    const { data } = await apiClient.get<NoticeDetail>(`/notices/${noticeId}`)
    return data
  },

  async create(payload: NoticeUpsertRequest): Promise<NoticeMutationResponse> {
    const { data } = await apiClient.post<NoticeMutationResponse>('/admin/notices', payload)
    return data
  },

  async update(noticeId: string | number, payload: NoticeUpsertRequest): Promise<NoticeMutationResponse> {
    const { data } = await apiClient.put<NoticeMutationResponse>(`/admin/notices/${noticeId}`, payload)
    return data
  },

  async remove(noticeId: string | number): Promise<NoticeMutationResponse> {
    const { data } = await apiClient.delete<NoticeMutationResponse>(`/admin/notices/${noticeId}`)
    return data
  }
}
