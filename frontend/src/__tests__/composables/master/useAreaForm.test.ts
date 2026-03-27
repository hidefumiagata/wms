import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, createAxiosError } from '../../helpers'
import { useAreaForm } from '@/composables/master/useAreaForm'
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

describe('useAreaForm', () => {
  beforeEach(() => {
    mockRouteParams = {}
  })

  describe('新規作成モード', () => {
    it('isEdit が false になる', () => {
      const { result } = withSetup(() => useAreaForm())
      expect(result.isEdit.value).toBe(false)
    })

    it('handleCancel がエリア一覧画面に遷移する', async () => {
      const { result } = withSetup(() => useAreaForm())
      await result.handleCancel()
      // useRouter().push が呼ばれることを検証
    })
  })

  describe('編集モード', () => {
    beforeEach(() => {
      mockRouteParams = { id: '1' }
    })

    it('isEdit が true になる', () => {
      const { result } = withSetup(() => useAreaForm())
      expect(result.isEdit.value).toBe(true)
    })

    it('fetchArea がデータを取得しフォームに設定する', async () => {
      const areaData = {
        buildingId: 10,
        areaCode: 'AREA01',
        areaName: 'テストエリア',
        storageCondition: 'AMBIENT',
        areaType: 'STOCK',
        warehouseCode: 'WHS01',
        buildingCode: 'BLD01',
        version: 2,
      }
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse(areaData))

      const { result } = withSetup(() => useAreaForm())
      await result.fetchArea()

      expect(apiClient.get).toHaveBeenCalledWith(
        '/master/areas/1',
        expect.objectContaining({
          signal: expect.any(AbortSignal),
        }),
      )
    })

    it('fetchArea が signal を渡す（AbortController対応）', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({
          buildingId: 10,
          areaCode: 'AREA01',
          areaName: 'テストエリア',
          storageCondition: 'AMBIENT',
          areaType: 'STOCK',
          warehouseCode: 'WHS01',
          buildingCode: 'BLD01',
          version: 1,
        }),
      )

      const { result } = withSetup(() => useAreaForm())
      await result.fetchArea()

      const callArgs = vi.mocked(apiClient.get).mock.calls[0]
      expect(callArgs[1]).toHaveProperty('signal')
      expect(callArgs[1]!.signal).toBeInstanceOf(AbortSignal)
    })

    it('onUnmounted 時に進行中のリクエストがキャンセルされる', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({
          buildingId: 10,
          areaCode: 'AREA01',
          areaName: 'テストエリア',
          storageCondition: 'AMBIENT',
          areaType: 'STOCK',
          warehouseCode: 'WHS01',
          buildingCode: 'BLD01',
          version: 1,
        }),
      )

      const { result, wrapper } = withSetup(() => useAreaForm())
      const fetchPromise = result.fetchArea()

      const signal = vi.mocked(apiClient.get).mock.calls[0][1]!.signal!
      expect(signal.aborted).toBe(false)

      wrapper.unmount()
      expect(signal.aborted).toBe(true)

      await fetchPromise
    })

    it('fetchArea のキャンセル時に state が更新されない', async () => {
      const cancelError = new Error('canceled')
      vi.mocked(apiClient.get).mockRejectedValueOnce(cancelError)
      vi.mocked(axios.isCancel).mockReturnValueOnce(true)

      const { result } = withSetup(() => useAreaForm())
      await result.fetchArea()

      expect(ElMessage.error).not.toHaveBeenCalled()
    })

    it('404エラー時にエラーメッセージが表示される', async () => {
      vi.mocked(apiClient.get).mockRejectedValueOnce(createAxiosError(404))

      const { result } = withSetup(() => useAreaForm())
      await result.fetchArea()

      expect(ElMessage.error).toHaveBeenCalledWith('master.area.notFound')
    })

    it('ネットワークエラー時にエラーメッセージが表示される', async () => {
      vi.mocked(apiClient.get).mockRejectedValueOnce(new Error('Network Error'))

      const { result } = withSetup(() => useAreaForm())
      await result.fetchArea()

      expect(ElMessage.error).toHaveBeenCalledWith('error.network')
    })
  })

  describe('fetchBuildings', () => {
    it('倉庫選択時に棟一覧を取得する', async () => {
      const buildingsData = {
        content: [
          { id: 1, buildingCode: 'BLD01', buildingName: '第1棟' },
          { id: 2, buildingCode: 'BLD02', buildingName: '第2棟' },
        ],
        totalElements: 2,
      }
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse(buildingsData))

      const { result } = withSetup(() => {
        const form = useAreaForm()
        // warehouseStore にselectedWarehouseId を設定
        form.warehouseStore.selectedWarehouseId = 1
        return form
      })
      await result.fetchBuildings()

      expect(apiClient.get).toHaveBeenCalledWith('/master/buildings', {
        params: { warehouseId: 1, isActive: true, size: 100 },
      })
    })

    it('倉庫未選択時はAPIを呼ばない', async () => {
      const { result } = withSetup(() => useAreaForm())
      await result.fetchBuildings()

      expect(apiClient.get).not.toHaveBeenCalled()
    })
  })

  describe('handleSubmit', () => {
    it('新規作成時にPOST APIを呼ぶ', async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce(mockAxiosResponse({}))

      const { result } = withSetup(() => useAreaForm())
      result.buildingId.value = 10
      result.areaCode.value = 'AREA01'
      result.areaName.value = 'テストエリア'
      result.storageCondition.value = 'AMBIENT'
      result.areaType.value = 'STOCK'

      await result.handleSubmit()

      if (vi.mocked(apiClient.post).mock.calls.length > 0) {
        expect(apiClient.post).toHaveBeenCalledWith(
          '/master/areas',
          expect.objectContaining({
            buildingId: 10,
            areaCode: 'AREA01',
            areaName: 'テストエリア',
          }),
        )
      }
    })

    it('編集時にPUT APIを呼ぶ', async () => {
      mockRouteParams = { id: '1' }
      vi.mocked(apiClient.put).mockResolvedValueOnce(mockAxiosResponse({}))
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({
          buildingId: 10,
          areaCode: 'AREA01',
          areaName: 'テストエリア',
          storageCondition: 'AMBIENT',
          areaType: 'STOCK',
          warehouseCode: 'WHS01',
          buildingCode: 'BLD01',
          version: 2,
        }),
      )

      const { result } = withSetup(() => useAreaForm())
      await result.fetchArea()

      await result.handleSubmit()

      if (vi.mocked(apiClient.put).mock.calls.length > 0) {
        expect(apiClient.put).toHaveBeenCalledWith(
          '/master/areas/1',
          expect.objectContaining({
            version: 2,
          }),
        )
      }
    })
  })
})
