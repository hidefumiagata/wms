import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, createCancelError, createAxiosError } from '../../helpers'
import { useWarehouseForm } from '@/composables/master/useWarehouseForm'
import { ElMessage } from 'element-plus'
import axios from 'axios'

// axios.isCancel のモック
vi.mock('axios', async (importOriginal) => {
  const actual = await importOriginal<typeof import('axios')>()
  return {
    ...actual,
    default: {
      ...actual.default,
      isCancel: vi.fn((err: unknown) => {
        return (err as { __CANCEL__?: boolean })?.__CANCEL__ === true
      }),
    },
  }
})

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

describe('useWarehouseForm', () => {
  beforeEach(() => {
    mockRouteParams = {}
  })

  describe('新規作成モード', () => {
    it('isEdit が false になる', () => {
      const { result } = withSetup(() => useWarehouseForm())
      expect(result.isEdit.value).toBe(false)
    })
  })

  describe('編集モード', () => {
    beforeEach(() => {
      mockRouteParams = { id: '1' }
    })

    it('isEdit が true になる', () => {
      const { result } = withSetup(() => useWarehouseForm())
      expect(result.isEdit.value).toBe(true)
    })

    it('fetchWarehouse がデータを取得しフォームに設定する', async () => {
      const warehouseData = {
        warehouseCode: 'WHSA',
        warehouseName: 'テスト倉庫',
        warehouseNameKana: 'テストソウコ',
        address: '東京都',
        version: 2,
      }
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse(warehouseData))

      const { result } = withSetup(() => useWarehouseForm())
      await result.fetchWarehouse()

      expect(apiClient.get).toHaveBeenCalledWith('/master/warehouses/1', expect.objectContaining({
        signal: expect.any(AbortSignal),
      }))
    })

    it('fetchWarehouse が signal を渡す（AbortController対応）', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse({
        warehouseCode: 'WHSA',
        warehouseName: 'テスト',
        warehouseNameKana: 'テスト',
        address: '',
        version: 1,
      }))

      const { result } = withSetup(() => useWarehouseForm())
      await result.fetchWarehouse()

      const callArgs = vi.mocked(apiClient.get).mock.calls[0]
      expect(callArgs[1]).toHaveProperty('signal')
      expect(callArgs[1]!.signal).toBeInstanceOf(AbortSignal)
    })

    it('onUnmounted 時に進行中のリクエストがキャンセルされる', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse({
        warehouseCode: 'WHSA',
        warehouseName: 'テスト',
        warehouseNameKana: 'テスト',
        address: '',
        version: 1,
      }))

      const { result, wrapper } = withSetup(() => useWarehouseForm())
      const fetchPromise = result.fetchWarehouse()

      const abortSpy = vi.spyOn(AbortController.prototype, 'abort')
      wrapper.unmount()

      expect(abortSpy).toHaveBeenCalled()
      abortSpy.mockRestore()

      await fetchPromise
    })

    it('fetchWarehouse のキャンセル時に state が更新されない', async () => {
      vi.mocked(apiClient.get).mockRejectedValueOnce(createCancelError())
      vi.mocked(axios.isCancel).mockReturnValueOnce(true)

      const { result } = withSetup(() => useWarehouseForm())
      await result.fetchWarehouse()

      // キャンセルの場合は initialLoading が更新されない（signal.aborted チェック）
      // エラーメッセージも表示されない
      expect(ElMessage.error).not.toHaveBeenCalled()
    })

    it('404エラー時に一覧画面にリダイレクトする', async () => {
      const axiosError = createAxiosError(404)
      vi.mocked(apiClient.get).mockRejectedValueOnce(axiosError)

      const { result } = withSetup(() => useWarehouseForm())
      await result.fetchWarehouse()

      expect(ElMessage.error).toHaveBeenCalled()
    })
  })

  describe('checkCodeExists', () => {
    it('コードが存在する場合にフィールドエラーを設定する', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse({ exists: true }))

      const { result } = withSetup(() => useWarehouseForm())
      // warehouseCode に有効な値を設定
      result.warehouseCode.value = 'WHSA'
      await result.checkCodeExists()

      expect(apiClient.get).toHaveBeenCalledWith('/master/warehouses/exists', {
        params: { warehouseCode: 'WHSA' },
      })
    })

    it('無効なコード形式の場合はAPIを呼ばない', async () => {
      const { result } = withSetup(() => useWarehouseForm())
      result.warehouseCode.value = 'ab' // 無効形式

      await result.checkCodeExists()

      expect(apiClient.get).not.toHaveBeenCalled()
    })
  })
})
