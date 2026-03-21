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
  return err as ApiError
}
