import { apiClient } from './client'
import type { ChatRequest, ChatResponse } from './types'

export const chatApi = {
  async askRealEstate(payload: ChatRequest): Promise<ChatResponse> {
    const { data } = await apiClient.post<ChatResponse>('/chat/real-estate', payload)
    return data
  }
}
