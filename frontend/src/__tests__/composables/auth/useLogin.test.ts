import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { withSetup, createAxiosError, flushPromises } from '../../helpers'
import { useLogin } from '@/composables/auth/useLogin'
import type { FormInstance } from 'element-plus'
import type { AuthUser } from '@/stores/auth'

// sanitizeRedirect モック
vi.mock('@/utils/redirect', () => ({
  sanitizeRedirect: vi.fn((r) => r || '/'),
}))

// vue-router モック（route.query を制御）
let mockRouteQuery: Record<string, string> = {}
const mockPush = vi.fn()
const mockReplace = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace }),
  useRoute: () => ({ params: {}, query: mockRouteQuery, name: 'test' }),
  createRouter: vi.fn(),
  createWebHistory: vi.fn(),
}))

// auth store のモック
const mockLogin = vi.fn()
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    login: mockLogin,
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

describe('useLogin', () => {
  beforeEach(() => {
    mockRouteQuery = {}
    mockPush.mockClear()
    mockReplace.mockClear()
    mockLogin.mockClear()
  })

  it('初期状態のフォームが空文字列を持つ', () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => useLogin(formRef))
    expect(result.form.userCode).toBe('')
    expect(result.form.password).toBe('')
    expect(result.loading.value).toBe(false)
    expect(result.errorMessage.value).toBe('')
  })

  it('rules が userCode と password を含む', () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => useLogin(formRef))
    expect(result.rules.userCode).toBeDefined()
    expect(result.rules.password).toBeDefined()
  })

  describe('handleLogin', () => {
    it('ログイン成功時に router.push が呼ばれる', async () => {
      const mockUser: AuthUser = {
        userId: 1,
        userCode: 'USER001',
        fullName: 'Test User',
        role: 'WAREHOUSE_STAFF',
        passwordChangeRequired: false,
      }
      mockLogin.mockResolvedValueOnce(mockUser)

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useLogin(formRef))
      result.form.userCode = 'USER001'
      result.form.password = 'password123'

      await result.handleLogin()
      await flushPromises()

      expect(mockLogin).toHaveBeenCalledWith('USER001', 'password123')
      expect(mockPush).toHaveBeenCalledWith('/')
    })

    it('passwordChangeRequired の場合 change-password に遷移', async () => {
      const mockUser: AuthUser = {
        userId: 1,
        userCode: 'USER001',
        fullName: 'Test User',
        role: 'WAREHOUSE_STAFF',
        passwordChangeRequired: true,
      }
      mockLogin.mockResolvedValueOnce(mockUser)

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useLogin(formRef))
      result.form.userCode = 'USER001'
      result.form.password = 'password123'

      await result.handleLogin()
      await flushPromises()

      expect(mockPush).toHaveBeenCalledWith({ name: 'change-password' })
    })

    it('redirect パラメータがある場合そのパスに遷移', async () => {
      mockRouteQuery = { redirect: '/inventory' }
      const mockUser: AuthUser = {
        userId: 1,
        userCode: 'USER001',
        fullName: 'Test User',
        role: 'WAREHOUSE_STAFF',
        passwordChangeRequired: false,
      }
      mockLogin.mockResolvedValueOnce(mockUser)

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useLogin(formRef))
      result.form.userCode = 'USER001'
      result.form.password = 'password123'

      await result.handleLogin()
      await flushPromises()

      expect(mockPush).toHaveBeenCalledWith('/inventory')
    })

    it('ACCOUNT_LOCKED エラーでアカウントロックメッセージを表示', async () => {
      mockLogin.mockRejectedValueOnce(
        createAxiosError(401, { errorCode: 'ACCOUNT_LOCKED', message: 'Account locked' }),
      )

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useLogin(formRef))
      result.form.userCode = 'USER001'
      result.form.password = 'password123'

      await result.handleLogin()
      await flushPromises()

      expect(result.errorMessage.value).toBe('auth.messages.accountLocked')
      expect(result.loading.value).toBe(false)
    })

    it('ネットワークエラー（レスポンスなし）で loginFailed500 メッセージを表示', async () => {
      const networkError = new Error('Network Error')
      mockLogin.mockRejectedValueOnce(networkError)

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useLogin(formRef))
      result.form.userCode = 'USER001'
      result.form.password = 'password123'

      await result.handleLogin()
      await flushPromises()

      expect(result.errorMessage.value).toBe('auth.messages.loginFailed500')
      expect(result.loading.value).toBe(false)
    })

    it('認証失敗エラーで invalidCredentials メッセージを表示', async () => {
      mockLogin.mockRejectedValueOnce(
        createAxiosError(401, { errorCode: 'INVALID_CREDENTIALS', message: 'Invalid credentials' }),
      )

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useLogin(formRef))
      result.form.userCode = 'USER001'
      result.form.password = 'wrongpassword'

      await result.handleLogin()
      await flushPromises()

      expect(result.errorMessage.value).toBe('auth.messages.invalidCredentials')
      expect(result.loading.value).toBe(false)
    })

    it('formRef が null の場合 validate スキップで早期リターン', async () => {
      const formRef = ref<FormInstance | null>(null) as ReturnType<typeof ref<FormInstance>>

      const { result } = withSetup(() => useLogin(formRef))
      result.form.userCode = 'USER001'
      result.form.password = 'password123'

      await result.handleLogin()
      await flushPromises()

      expect(mockLogin).not.toHaveBeenCalled()
    })
  })

  describe('onMounted - session expired', () => {
    it('route.query.reason が session_expired の場合 sessionExpiredMessage がセットされる', () => {
      mockRouteQuery = { reason: 'session_expired' }

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useLogin(formRef))

      expect(result.sessionExpiredMessage.value).toBe('auth.messages.sessionExpired')
      expect(mockReplace).toHaveBeenCalledWith({
        name: 'login',
        query: { redirect: undefined },
      })
    })

    it('route.query.reason がない場合 sessionExpiredMessage は空', () => {
      mockRouteQuery = {}

      const formRef = createMockFormRef()
      const { result } = withSetup(() => useLogin(formRef))

      expect(result.sessionExpiredMessage.value).toBe('')
    })

    it('session_expired 時に redirect パラメータが保持される', () => {
      mockRouteQuery = { reason: 'session_expired', redirect: '/dashboard' }

      const formRef = createMockFormRef()
      withSetup(() => useLogin(formRef))

      expect(mockReplace).toHaveBeenCalledWith({
        name: 'login',
        query: { redirect: '/dashboard' },
      })
    })
  })
})
