import axios from 'axios'

export interface ApiError {
  response?: {
    status?: number
    data?: {
      errorCode?: string
      message?: string
    }
  }
}

export function toApiError(err: unknown): ApiError {
  if (axios.isAxiosError(err)) {
    return {
      response: {
        status: err.response?.status,
        data: err.response?.data as NonNullable<ApiError['response']>['data'],
      },
    }
  }
  return {}
}
