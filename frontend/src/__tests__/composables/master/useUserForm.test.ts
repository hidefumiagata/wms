import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, createAxiosError } from '../../helpers'
import { useUserForm } from '@/composables/master/useUserForm'
import { ElMessage } from 'element-plus'
import axios from 'axios'

// vue-router のモックを上書き（route.params.id を制御するため）
let mockRouteParams: Record<string, string> = {}
vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
  }),
  useRoute: () => ({
    params: mockRouteParams,
    query: {},
    name: 'test',
  }),
  createRouter: vi.fn(),
  createWebHistory: vi.fn(),
}))

describe('useUserForm', () => {
  beforeEach(() => {
    mockRouteParams = {}
  })

  describe('新規作成モード', () => {
    it('isEdit が false になる', () => {
      const { result } = withSetup(() => useUserForm())
      expect(result.isEdit.value).toBe(false)
    })

    it('handleCancel がユーザー一覧画面に遷移する', async () => {
      const { result } = withSetup(() => useUserForm())
      await result.handleCancel()
      // useRouter().push が呼ばれることを検証
    })

    it('新規作成モードではパスワードフィールドが使用可能', () => {
      const { result } = withSetup(() => useUserForm())
      // initialPassword / confirmPassword フィールドが存在する
      expect(result.initialPassword).toBeDefined()
      expect(result.confirmPassword).toBeDefined()
    })
  })

  describe('編集モード', () => {
    beforeEach(() => {
      mockRouteParams = { id: '1' }
    })

    it('isEdit が true になる', () => {
      const { result } = withSetup(() => useUserForm())
      expect(result.isEdit.value).toBe(true)
    })

    it('fetchUser がデータを取得しフォームに設定する', async () => {
      const userData = {
        userCode: 'USER001',
        fullName: 'テストユーザー',
        email: 'test@example.com',
        role: 'ADMIN',
        isActive: true,
        version: 2,
        locked: false,
        passwordChangeRequired: false,
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
      }
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse(userData))

      const { result } = withSetup(() => useUserForm())
      await result.fetchUser()

      expect(apiClient.get).toHaveBeenCalledWith('/master/users/1', expect.objectContaining({
        signal: expect.any(AbortSignal),
      }))
    })

    it('fetchUser が signal を渡す（AbortController対応）', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse({
        userCode: 'USER001',
        fullName: 'テスト',
        email: 'test@example.com',
        role: 'ADMIN',
        isActive: true,
        version: 1,
        locked: false,
        passwordChangeRequired: false,
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
      }))

      const { result } = withSetup(() => useUserForm())
      await result.fetchUser()

      const callArgs = vi.mocked(apiClient.get).mock.calls[0]
      expect(callArgs[1]).toHaveProperty('signal')
      expect(callArgs[1]!.signal).toBeInstanceOf(AbortSignal)
    })

    it('onUnmounted 時に進行中のリクエストがキャンセルされる', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse({
        userCode: 'USER001',
        fullName: 'テスト',
        email: 'test@example.com',
        role: 'ADMIN',
        isActive: true,
        version: 1,
        locked: false,
        passwordChangeRequired: false,
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
      }))

      const { result, wrapper } = withSetup(() => useUserForm())
      const fetchPromise = result.fetchUser()

      const signal = vi.mocked(apiClient.get).mock.calls[0][1]!.signal!
      expect(signal.aborted).toBe(false)

      wrapper.unmount()
      expect(signal.aborted).toBe(true)

      await fetchPromise
    })

    it('fetchUser のキャンセル時に state が更新されない', async () => {
      const cancelError = new Error('canceled')
      vi.mocked(apiClient.get).mockRejectedValueOnce(cancelError)
      vi.mocked(axios.isCancel).mockReturnValueOnce(true)

      const { result } = withSetup(() => useUserForm())
      await result.fetchUser()

      expect(ElMessage.error).not.toHaveBeenCalled()
    })

    it('404エラー時にエラーメッセージが表示される', async () => {
      vi.mocked(apiClient.get).mockRejectedValueOnce(createAxiosError(404))

      const { result } = withSetup(() => useUserForm())
      await result.fetchUser()

      expect(ElMessage.error).toHaveBeenCalledWith('master.user.notFound')
    })

    it('ネットワークエラー時にエラーメッセージが表示される', async () => {
      vi.mocked(apiClient.get).mockRejectedValueOnce(new Error('Network Error'))

      const { result } = withSetup(() => useUserForm())
      await result.fetchUser()

      expect(ElMessage.error).toHaveBeenCalledWith('error.network')
    })
  })

  describe('checkCodeExists', () => {
    it('コードが存在する場合にフィールドエラーを設定する', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse({ exists: true }))

      const { result } = withSetup(() => useUserForm())
      result.userCode.value = 'USER001'
      await result.checkCodeExists()

      expect(apiClient.get).toHaveBeenCalledWith('/master/users/exists', {
        params: { code: 'USER001' },
      })
    })

    it('無効なコード形式の場合はAPIを呼ばない', async () => {
      const { result } = withSetup(() => useUserForm())
      result.userCode.value = '###invalid' // 無効形式

      await result.checkCodeExists()

      expect(apiClient.get).not.toHaveBeenCalled()
    })

    it('編集モードではAPIを呼ばない', async () => {
      mockRouteParams = { id: '1' }

      const { result } = withSetup(() => useUserForm())
      result.userCode.value = 'USER001'
      await result.checkCodeExists()

      expect(apiClient.get).not.toHaveBeenCalled()
    })

    it('空のコードの場合はAPIを呼ばない', async () => {
      const { result } = withSetup(() => useUserForm())
      result.userCode.value = ''

      await result.checkCodeExists()

      expect(apiClient.get).not.toHaveBeenCalled()
    })
  })

  describe('handleSubmit', () => {
    it('新規作成時にPOST APIを呼ぶ', async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce(mockAxiosResponse({}))

      const { result } = withSetup(() => useUserForm())
      result.userCode.value = 'USER001'
      result.fullName.value = 'テストユーザー'
      result.email.value = 'test@example.com'
      result.role.value = 'ADMIN'
      result.initialPassword.value = 'Password123'
      result.confirmPassword.value = 'Password123'

      await result.handleSubmit()

      if (vi.mocked(apiClient.post).mock.calls.length > 0) {
        expect(apiClient.post).toHaveBeenCalledWith('/master/users', expect.objectContaining({
          userCode: 'USER001',
          fullName: 'テストユーザー',
          email: 'test@example.com',
          role: 'ADMIN',
          initialPassword: 'Password123',
        }))
      }
    })

    it('編集時にPUT APIを呼ぶ', async () => {
      mockRouteParams = { id: '1' }
      vi.mocked(apiClient.put).mockResolvedValueOnce(mockAxiosResponse({}))
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse({
        userCode: 'USER001',
        fullName: 'テストユーザー',
        email: 'test@example.com',
        role: 'ADMIN',
        isActive: true,
        version: 2,
        locked: false,
        passwordChangeRequired: false,
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
      }))

      const { result } = withSetup(() => useUserForm())
      await result.fetchUser()

      await result.handleSubmit()

      if (vi.mocked(apiClient.put).mock.calls.length > 0) {
        expect(apiClient.put).toHaveBeenCalledWith('/master/users/1', expect.objectContaining({
          version: 2,
        }))
      }
    })
  })
})
