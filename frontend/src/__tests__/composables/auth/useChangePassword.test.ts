import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref } from 'vue'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, createAxiosError, flushPromises } from '../../helpers'
import {
  useChangePassword,
  calcPasswordStrength,
  PASSWORD_POLICY_REGEX,
} from '@/composables/auth/useChangePassword'
import type { FormInstance } from 'element-plus'

// element-plus モック上書き（ElMessage を関数としても呼べるようにする）
const { mockElMessage } = vi.hoisted(() => {
  const mockElMessage = Object.assign(vi.fn(), {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  })
  return { mockElMessage }
})
vi.mock('element-plus', () => ({
  ElMessage: mockElMessage,
  ElMessageBox: {
    confirm: vi.fn().mockResolvedValue('confirm'),
    alert: vi.fn().mockResolvedValue('confirm'),
  },
}))

// vue-router モック
const mockPush = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn() }),
  useRoute: () => ({ params: {}, query: {}, name: 'test' }),
  createRouter: vi.fn(),
  createWebHistory: vi.fn(),
}))

// auth store のモック
const mockLogout = vi.fn().mockResolvedValue(undefined)
const mockClearPasswordChangeRequired = vi.fn()
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    logout: mockLogout,
    clearPasswordChangeRequired: mockClearPasswordChangeRequired,
    user: null,
    isAuthenticated: false,
  }),
}))

/** validate が常に成功する formRef を作成 */
function createMockFormRef() {
  return ref({
    validate: vi.fn().mockResolvedValue(true),
  } as unknown as FormInstance)
}

describe('calcPasswordStrength', () => {
  it('ポリシー不適合のパスワードは weak', () => {
    expect(calcPasswordStrength('abc')).toBe('weak')
    expect(calcPasswordStrength('12345678')).toBe('weak')
    expect(calcPasswordStrength('abcdefgh')).toBe('weak')
    expect(calcPasswordStrength('ABCDEFGH')).toBe('weak')
    expect(calcPasswordStrength('')).toBe('weak')
  })

  it('ポリシー適合で12文字未満は medium', () => {
    expect(calcPasswordStrength('Abcdefg1')).toBe('medium')
    expect(calcPasswordStrength('Abcdefgh1')).toBe('medium')
    expect(calcPasswordStrength('Abcdefghi1!')).toBe('medium')
  })

  it('ポリシー適合で12文字以上は strong', () => {
    expect(calcPasswordStrength('Abcdefghij1!')).toBe('strong')
    expect(calcPasswordStrength('LongPassword1')).toBe('strong')
    expect(calcPasswordStrength('VeryLongPassword123!')).toBe('strong')
  })
})

describe('PASSWORD_POLICY_REGEX', () => {
  it('大文字・小文字・数字を含む8文字以上にマッチ', () => {
    expect(PASSWORD_POLICY_REGEX.test('Abcdefg1')).toBe(true)
    expect(PASSWORD_POLICY_REGEX.test('Password123')).toBe(true)
  })

  it('条件不足にはマッチしない', () => {
    expect(PASSWORD_POLICY_REGEX.test('abcdefg1')).toBe(false) // 大文字なし
    expect(PASSWORD_POLICY_REGEX.test('ABCDEFG1')).toBe(false) // 小文字なし
    expect(PASSWORD_POLICY_REGEX.test('Abcdefgh')).toBe(false) // 数字なし
    expect(PASSWORD_POLICY_REGEX.test('Ab1')).toBe(false)      // 8文字未満
  })
})

describe('useChangePassword', () => {
  beforeEach(() => {
    mockPush.mockClear()
    mockLogout.mockClear()
    mockClearPasswordChangeRequired.mockClear()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('初期状態のフォームが空文字列を持つ', () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => useChangePassword(formRef))
    expect(result.form.currentPassword).toBe('')
    expect(result.form.newPassword).toBe('')
    expect(result.form.confirmPassword).toBe('')
    expect(result.loading.value).toBe(false)
    expect(result.errorMessage.value).toBe('')
  })

  it('passwordStrength が computed で更新される', () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => useChangePassword(formRef))
    expect(result.passwordStrength.value).toBe('weak')

    result.form.newPassword = 'Abcdefg1'
    expect(result.passwordStrength.value).toBe('medium')

    result.form.newPassword = 'Abcdefghij1!'
    expect(result.passwordStrength.value).toBe('strong')
  })

  describe('handleSubmit', () => {
    it('パスワード変更成功時に clearPasswordChangeRequired と ElMessage と router.push が呼ばれる', async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce(mockAxiosResponse({}))

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useChangePassword(formRef))
      result.form.currentPassword = 'OldPass1'
      result.form.newPassword = 'NewPass1'
      result.form.confirmPassword = 'NewPass1'

      await result.handleSubmit()
      await flushPromises()

      expect(apiClient.post).toHaveBeenCalledWith('/auth/change-password', {
        currentPassword: 'OldPass1',
        newPassword: 'NewPass1',
      })
      expect(mockClearPasswordChangeRequired).toHaveBeenCalled()
      expect(mockElMessage).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'success' }),
      )

      // setTimeout で router.push が呼ばれる
      vi.advanceTimersByTime(500)
      expect(mockPush).toHaveBeenCalledWith('/')
    })

    it('401 INVALID_CREDENTIALS エラーで currentPasswordWrong メッセージ', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(401, { errorCode: 'INVALID_CREDENTIALS' }),
      )

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useChangePassword(formRef))
      result.form.currentPassword = 'WrongPass1'
      result.form.newPassword = 'NewPass1'

      await result.handleSubmit()
      await flushPromises()

      expect(result.errorMessage.value).toBe('auth.messages.currentPasswordWrong')
      expect(result.loading.value).toBe(false)
    })

    it('409 SAME_PASSWORD エラーで passwordSameAsCurrent メッセージ', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(409, { errorCode: 'SAME_PASSWORD' }),
      )

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useChangePassword(formRef))
      result.form.currentPassword = 'SamePass1'
      result.form.newPassword = 'SamePass1'

      await result.handleSubmit()
      await flushPromises()

      expect(result.errorMessage.value).toBe('auth.validation.passwordSameAsCurrent')
      expect(result.loading.value).toBe(false)
    })

    it('400 エラーで passwordPolicy メッセージ', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(400, { errorCode: 'VALIDATION_ERROR' }),
      )

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useChangePassword(formRef))
      await result.handleSubmit()
      await flushPromises()

      expect(result.errorMessage.value).toBe('auth.validation.passwordPolicy')
    })

    it('その他のエラーで passwordChangeFailed500 メッセージ', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(500, { errorCode: 'INTERNAL_ERROR' }),
      )

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useChangePassword(formRef))
      await result.handleSubmit()
      await flushPromises()

      expect(result.errorMessage.value).toBe('auth.messages.passwordChangeFailed500')
    })
  })

  describe('handleLogout', () => {
    it('ログアウトして login 画面に遷移', async () => {
      const formRef = createMockFormRef()
      const { result } = withSetup(() => useChangePassword(formRef))

      await result.handleLogout()
      await flushPromises()

      expect(mockLogout).toHaveBeenCalled()
      expect(mockPush).toHaveBeenCalledWith({ name: 'login' })
    })
  })
})
