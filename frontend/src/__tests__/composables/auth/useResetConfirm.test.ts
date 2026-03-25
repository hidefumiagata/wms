import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, createAxiosError, flushPromises } from '../../helpers'
import { useResetConfirm } from '@/composables/auth/useResetConfirm'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'

// vue-router モック（route.query を制御）
let mockRouteQuery: Record<string, string> = {}
const mockPush = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn() }),
  useRoute: () => ({ params: {}, query: mockRouteQuery, name: 'test' }),
  createRouter: vi.fn(),
  createWebHistory: vi.fn(),
}))

/** validate が常に成功する formRef を作成 */
function createMockFormRef() {
  return ref({
    validate: vi.fn().mockResolvedValue(true),
  } as unknown as FormInstance)
}

describe('useResetConfirm', () => {
  beforeEach(() => {
    mockRouteQuery = {}
    mockPush.mockClear()
  })

  it('初期状態が正しい', () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => useResetConfirm(formRef))
    expect(result.form.newPassword).toBe('')
    expect(result.form.confirmPassword).toBe('')
    expect(result.loading.value).toBe(false)
    expect(result.errorMessage.value).toBe('')
  })

  it('passwordStrength が computed で更新される', () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => useResetConfirm(formRef))
    expect(result.passwordStrength.value).toBe('weak')

    result.form.newPassword = 'Abcdefg1'
    expect(result.passwordStrength.value).toBe('medium')

    result.form.newPassword = 'Abcdefghij1!'
    expect(result.passwordStrength.value).toBe('strong')
  })

  describe('onMounted - token check', () => {
    it('クエリにトークンがない場合 tokenMissing が true になる', () => {
      mockRouteQuery = {}

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useResetConfirm(formRef))

      expect(result.tokenMissing.value).toBe(true)
    })

    it('クエリにトークンがある場合 tokenMissing は false のまま', () => {
      mockRouteQuery = { token: 'valid-token-123' }

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useResetConfirm(formRef))

      expect(result.tokenMissing.value).toBe(false)
    })
  })

  describe('handleSubmit', () => {
    it('パスワードリセット成功時に ElMessage.success と login 画面遷移', async () => {
      mockRouteQuery = { token: 'valid-token-123' }
      vi.mocked(apiClient.post).mockResolvedValueOnce(mockAxiosResponse({}))

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useResetConfirm(formRef))
      result.form.newPassword = 'NewPass1'
      result.form.confirmPassword = 'NewPass1'

      await result.handleSubmit()
      await flushPromises()

      expect(apiClient.post).toHaveBeenCalledWith('/auth/password-reset/confirm', {
        token: 'valid-token-123',
        newPassword: 'NewPass1',
      })
      expect(ElMessage.success).toHaveBeenCalled()
      expect(mockPush).toHaveBeenCalledWith({ name: 'login' })
      expect(result.loading.value).toBe(false)
    })

    it('401 エラーで resetTokenInvalid メッセージ', async () => {
      mockRouteQuery = { token: 'expired-token' }
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(401, { errorCode: 'TOKEN_EXPIRED' }),
      )

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useResetConfirm(formRef))
      result.form.newPassword = 'NewPass1'

      await result.handleSubmit()
      await flushPromises()

      expect(result.errorMessage.value).toBe('auth.messages.resetTokenInvalid')
      expect(result.loading.value).toBe(false)
    })

    it('400 エラーで passwordPolicy メッセージ', async () => {
      mockRouteQuery = { token: 'valid-token' }
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(400, { errorCode: 'VALIDATION_ERROR' }),
      )

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useResetConfirm(formRef))
      result.form.newPassword = 'weak'

      await result.handleSubmit()
      await flushPromises()

      expect(result.errorMessage.value).toBe('auth.validation.passwordPolicy')
      expect(result.loading.value).toBe(false)
    })

    it('その他のエラーで resetConfirmFailed メッセージ', async () => {
      mockRouteQuery = { token: 'valid-token' }
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(500, { errorCode: 'INTERNAL_ERROR' }),
      )

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useResetConfirm(formRef))
      result.form.newPassword = 'NewPass1'

      await result.handleSubmit()
      await flushPromises()

      expect(result.errorMessage.value).toBe('auth.messages.resetConfirmFailed')
      expect(result.loading.value).toBe(false)
    })
  })
})
