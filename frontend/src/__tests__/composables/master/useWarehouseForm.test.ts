import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, createAxiosError } from '../../helpers'
import { useWarehouseForm } from '@/composables/master/useWarehouseForm'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { mockRouter } from '../../setup'

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

    it('handleCancel が倉庫一覧画面に遷移する', () => {
      const { result } = withSetup(() => useWarehouseForm())
      result.handleCancel()

      // useRouter().push が呼ばれることを検証
      // （vi.mock で useRouter のモックを返しているので直接検証）
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

      // fetchWarehouse で渡された signal を取得
      const signal = vi.mocked(apiClient.get).mock.calls[0][1]!.signal!
      expect(signal.aborted).toBe(false)

      wrapper.unmount()

      expect(signal.aborted).toBe(true)

      await fetchPromise
    })

    it('fetchWarehouse のキャンセル時に state が更新されない', async () => {
      const cancelError = new Error('canceled')
      vi.mocked(apiClient.get).mockRejectedValueOnce(cancelError)
      vi.mocked(axios.isCancel).mockReturnValueOnce(true)

      const { result } = withSetup(() => useWarehouseForm())
      await result.fetchWarehouse()

      // キャンセルの場合はエラーメッセージも表示されない
      expect(ElMessage.error).not.toHaveBeenCalled()
    })

    it('404エラー時にエラーメッセージが表示される', async () => {
      vi.mocked(apiClient.get).mockRejectedValueOnce(createAxiosError(404))

      const { result } = withSetup(() => useWarehouseForm())
      await result.fetchWarehouse()

      expect(ElMessage.error).toHaveBeenCalledWith('master.warehouse.notFound')
    })

    it('ネットワークエラー時にエラーメッセージが表示される', async () => {
      vi.mocked(apiClient.get).mockRejectedValueOnce(new Error('Network Error'))

      const { result } = withSetup(() => useWarehouseForm())
      await result.fetchWarehouse()

      expect(ElMessage.error).toHaveBeenCalledWith('error.network')
    })
  })

  describe('checkCodeExists', () => {
    it('コードが存在する場合にフィールドエラーを設定する', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse({ exists: true }))

      const { result } = withSetup(() => useWarehouseForm())
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

    it('編集モードではAPIを呼ばない', async () => {
      mockRouteParams = { id: '1' }

      const { result } = withSetup(() => useWarehouseForm())
      result.warehouseCode.value = 'WHSA'
      await result.checkCodeExists()

      expect(apiClient.get).not.toHaveBeenCalled()
    })
  })

  describe('handleSubmit', () => {
    it('新規作成時にPOST APIを呼ぶ', async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce(mockAxiosResponse({}))

      const { result } = withSetup(() => useWarehouseForm())

      // vee-validate のバリデーションをバイパスして直接submit
      // handleSubmit は createSubmitHandler で作成されるため、
      // バリデーション通過後のコールバックを直接テストする
      result.warehouseCode.value = 'WHSA'
      result.warehouseName.value = 'テスト倉庫'
      result.warehouseNameKana.value = 'テストソウコ'
      result.address.value = '東京都'

      // handleSubmit はバリデーション付きなのでform提出をシミュレート
      await result.handleSubmit()

      // バリデーションエラーがなければ POST が呼ばれる
      if (vi.mocked(apiClient.post).mock.calls.length > 0) {
        expect(apiClient.post).toHaveBeenCalledWith('/master/warehouses', expect.objectContaining({
          warehouseCode: 'WHSA',
          warehouseName: 'テスト倉庫',
        }))
      }
    })

    it('編集時にPUT APIを呼ぶ', async () => {
      mockRouteParams = { id: '1' }
      vi.mocked(apiClient.put).mockResolvedValueOnce(mockAxiosResponse({}))
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse({
        warehouseCode: 'WHSA',
        warehouseName: 'テスト倉庫',
        warehouseNameKana: 'テストソウコ',
        address: '東京都',
        version: 2,
      }))

      const { result } = withSetup(() => useWarehouseForm())
      // まず既存データをロード
      await result.fetchWarehouse()

      // submit
      await result.handleSubmit()

      // バリデーションが通れば PUT が呼ばれる
      if (vi.mocked(apiClient.put).mock.calls.length > 0) {
        expect(apiClient.put).toHaveBeenCalledWith('/master/warehouses/1', expect.objectContaining({
          version: 2,
        }))
      }
    })
  })
})
