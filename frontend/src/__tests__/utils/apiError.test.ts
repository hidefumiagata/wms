import { describe, it, expect } from 'vitest'
import { AxiosError } from 'axios'
import { toApiError } from '@/utils/apiError'

describe('toApiError', () => {
  it('converts an AxiosError into a structured ApiError', () => {
    const axiosError = new AxiosError('Request failed', '500', undefined, undefined, {
      status: 422,
      data: { errorCode: 'VALIDATION_ERROR', message: 'Invalid input' },
      statusText: 'Unprocessable Entity',
      headers: {},
      config: {} as unknown,
    })

    const result = toApiError(axiosError)

    expect(result).toEqual({
      response: {
        status: 422,
        data: { errorCode: 'VALIDATION_ERROR', message: 'Invalid input' },
      },
    })
  })

  it('returns structured error with undefined data when AxiosError has no response', () => {
    const axiosError = new AxiosError('Network Error', 'ERR_NETWORK')

    const result = toApiError(axiosError)

    expect(result).toEqual({
      response: {
        status: undefined,
        data: undefined,
      },
    })
  })

  it('returns empty object for a non-Axios Error', () => {
    const error = new Error('Something went wrong')

    const result = toApiError(error)

    expect(result).toEqual({})
  })

  it('returns empty object for a plain string error', () => {
    const result = toApiError('some string error')

    expect(result).toEqual({})
  })

  it('returns empty object for null', () => {
    const result = toApiError(null)

    expect(result).toEqual({})
  })

  it('returns empty object for undefined', () => {
    const result = toApiError(undefined)

    expect(result).toEqual({})
  })

  it('returns empty object for a plain object', () => {
    const result = toApiError({ foo: 'bar' })

    expect(result).toEqual({})
  })
})
