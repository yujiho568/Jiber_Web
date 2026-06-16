import axios, { AxiosError } from 'axios'

import type { ApiErrorResponse } from './types'

const fallbackApiBaseUrl = 'http://localhost:8080/api/v1'

export const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || fallbackApiBaseUrl

export const apiClient = axios.create({
  baseURL: apiBaseUrl,
  headers: {
    'Content-Type': 'application/json'
  }
})

apiClient.interceptors.request.use(async (config) => {
  const { useAuthStore } = await import('@/stores/auth')
  const authStore = useAuthStore()

  if (authStore.accessToken) {
    config.headers.Authorization = `Bearer ${authStore.accessToken}`
  }

  return config
})

export function getBackendPublicBaseUrl(): string {
  return apiBaseUrl.replace(/\/api\/v1\/?$/, '')
}

export function toListQueryValue<T extends string>(values?: T[]): string | undefined {
  if (!values?.length) {
    return undefined
  }

  return values.join(',')
}

export function compactParams<T extends object>(params: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== '')
  ) as Partial<T>
}

export function getApiError(error: unknown): ApiErrorResponse | null {
  if (!axios.isAxiosError(error)) {
    return null
  }

  const axiosError = error as AxiosError<ApiErrorResponse>
  return axiosError.response?.data ?? null
}

export function getApiErrorMessage(error: unknown, fallbackMessage = '요청을 처리하지 못했습니다.'): string {
  return getApiError(error)?.message ?? fallbackMessage
}
