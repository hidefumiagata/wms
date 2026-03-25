import { describe, it, expect, vi } from 'vitest'
import { ref } from 'vue'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, createAxiosError, flushPromises } from '../../helpers'
import { useResetRequest } from '@/composables/auth/useResetRequest'
import type { FormInstance } from 'element-plus'

// vue-router モック
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useRoute: () => ({ params: {}, query: {}, name: 'test' }),
  createRouter: vi.fn(),
  createWebHistory: vi.fn(),
}))

/** validate が常に成功する formRef を作成 */
function createMockFormRef() {
  return ref({
    validate: vi.fn().mockResolvedValue(true),
  } as unknown as FormInstance)
}

describe('useResetRequest', () => {
  it('初期状態が正しい', () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => useResetRequest(formRef))
    expect(result.form.identifier).toBe('')
    expect(result.loading.value).toBe(false)
    expect(result.sent.value).toBe(false)
    expect(result.errorMessage.value).toBe('')
  })

  it('rules が identifier を含む', () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => useResetRequest(formRef))
    expect(result.rules.identifier).toBeDefined()
  })

  describe('handleSubmit', () => {
    it('送信成功時に sent が true になる', async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce(mockAxiosResponse({}))

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useResetRequest(formRef))
      result.form.identifier = 'USER001'

      await result.handleSubmit()
      await flushPromises()

      expect(apiClient.post).toHaveBeenCalledWith('/auth/password-reset/request', {
        identifier: 'USER001',
      })
      expect(result.sent.value).toBe(true)
      expect(result.loading.value).toBe(false)
    })

    it('429 エラーで rateLimitExceeded メッセージ', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(429, { errorCode: 'RATE_LIMIT_EXCEEDED' }),
      )

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useResetRequest(formRef))
      result.form.identifier = 'USER001'

      await result.handleSubmit()
      await flushPromises()

      expect(result.errorMessage.value).toBe('auth.messages.rateLimitExceeded')
      expect(result.sent.value).toBe(false)
      expect(result.loading.value).toBe(false)
    })

    it('その他のエラーで resetRequestFailed メッセージ', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(500, { errorCode: 'INTERNAL_ERROR' }),
      )

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useResetRequest(formRef))
      result.form.identifier = 'USER001'

      await result.handleSubmit()
      await flushPromises()

      expect(result.errorMessage.value).toBe('auth.messages.resetRequestFailed')
      expect(result.sent.value).toBe(false)
      expect(result.loading.value).toBe(false)
    })
  })
})
