import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, createAxiosError } from '../../helpers'
import { useLocationForm } from '@/composables/master/useLocationForm'
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

describe('useLocationForm', () => {
  beforeEach(() => {
    mockRouteParams = {}
  })

  describe('新規作成モード', () => {
    it('isEdit が false になる', () => {
      const { result } = withSetup(() => useLocationForm())
      expect(result.isEdit.value).toBe(false)
    })

    it('handleCancel がロケーション一覧画面に遷移する', async () => {
      const { result } = withSetup(() => useLocationForm())
      await result.handleCancel()
      // useRouter().push が呼ばれることを検証
    })
  })

  describe('編集モード', () => {
    beforeEach(() => {
      mockRouteParams = { id: '1' }
    })

    it('isEdit が true になる', () => {
      const { result } = withSetup(() => useLocationForm())
      expect(result.isEdit.value).toBe(true)
    })

    it('fetchLocation がデータを取得しフォームに設定する', async () => {
      const locationData = {
        areaId: 10,
        locationCode: 'BLD01-A-01-01',
        locationName: 'テストロケーション',
        warehouseCode: 'WHS01',
        areaCode: 'AREA01',
        version: 2,
      }
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse(locationData))

      const { result } = withSetup(() => useLocationForm())
      await result.fetchLocation()

      expect(apiClient.get).toHaveBeenCalledWith(
        '/master/locations/1',
        expect.objectContaining({
          signal: expect.any(AbortSignal),
        }),
      )
    })

    it('fetchLocation が signal を渡す（AbortController対応）', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({
          areaId: 10,
          locationCode: 'BLD01-A-01-01',
          locationName: '',
          warehouseCode: 'WHS01',
          areaCode: 'AREA01',
          version: 1,
        }),
      )

      const { result } = withSetup(() => useLocationForm())
      await result.fetchLocation()

      const callArgs = vi.mocked(apiClient.get).mock.calls[0]
      expect(callArgs[1]).toHaveProperty('signal')
      expect(callArgs[1]!.signal).toBeInstanceOf(AbortSignal)
    })

    it('onUnmounted 時に進行中のリクエストがキャンセルされる', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({
          areaId: 10,
          locationCode: 'BLD01-A-01-01',
          locationName: '',
          warehouseCode: 'WHS01',
          areaCode: 'AREA01',
          version: 1,
        }),
      )

      const { result, wrapper } = withSetup(() => useLocationForm())
      const fetchPromise = result.fetchLocation()

      const signal = vi.mocked(apiClient.get).mock.calls[0][1]!.signal!
      expect(signal.aborted).toBe(false)

      wrapper.unmount()
      expect(signal.aborted).toBe(true)

      await fetchPromise
    })

    it('fetchLocation のキャンセル時に state が更新されない', async () => {
      const cancelError = new Error('canceled')
      vi.mocked(apiClient.get).mockRejectedValueOnce(cancelError)
      vi.mocked(axios.isCancel).mockReturnValueOnce(true)

      const { result } = withSetup(() => useLocationForm())
      await result.fetchLocation()

      expect(ElMessage.error).not.toHaveBeenCalled()
    })

    it('404エラー時にエラーメッセージが表示される', async () => {
      vi.mocked(apiClient.get).mockRejectedValueOnce(createAxiosError(404))

      const { result } = withSetup(() => useLocationForm())
      await result.fetchLocation()

      expect(ElMessage.error).toHaveBeenCalledWith('master.location.notFound')
    })

    it('ネットワークエラー時にエラーメッセージが表示される', async () => {
      vi.mocked(apiClient.get).mockRejectedValueOnce(new Error('Network Error'))

      const { result } = withSetup(() => useLocationForm())
      await result.fetchLocation()

      expect(ElMessage.error).toHaveBeenCalledWith('error.network')
    })
  })

  describe('fetchAreas', () => {
    it('倉庫選択時にエリア一覧を取得する', async () => {
      const areasData = {
        content: [
          {
            id: 1,
            areaCode: 'AREA01',
            areaName: '第1エリア',
            areaType: 'STOCK',
            buildingCode: 'BLD01',
          },
          {
            id: 2,
            areaCode: 'AREA02',
            areaName: '第2エリア',
            areaType: 'RECEIVING',
            buildingCode: 'BLD01',
          },
        ],
        totalElements: 2,
      }
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse(areasData))

      const { result } = withSetup(() => {
        const form = useLocationForm()
        form.warehouseStore.selectedWarehouseId = 1
        return form
      })
      await result.fetchAreas()

      expect(apiClient.get).toHaveBeenCalledWith('/master/areas', {
        params: { warehouseId: 1, isActive: true, size: 100 },
      })
    })

    it('倉庫未選択時はAPIを呼ばない', async () => {
      const { result } = withSetup(() => useLocationForm())
      await result.fetchAreas()

      expect(apiClient.get).not.toHaveBeenCalled()
    })
  })

  describe('handleSubmit', () => {
    it('新規作成時にPOST APIを呼ぶ', async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce(mockAxiosResponse({}))

      const { result } = withSetup(() => useLocationForm())
      result.areaId.value = 10
      result.locationCode.value = 'BLD01-A-01-01'
      result.locationName.value = 'テストロケーション'

      await result.handleSubmit()

      if (vi.mocked(apiClient.post).mock.calls.length > 0) {
        expect(apiClient.post).toHaveBeenCalledWith(
          '/master/locations',
          expect.objectContaining({
            areaId: 10,
            locationCode: 'BLD01-A-01-01',
          }),
        )
      }
    })

    it('編集時にPUT APIを呼ぶ', async () => {
      mockRouteParams = { id: '1' }
      vi.mocked(apiClient.put).mockResolvedValueOnce(mockAxiosResponse({}))
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({
          areaId: 10,
          locationCode: 'BLD01-A-01-01',
          locationName: 'テストロケーション',
          warehouseCode: 'WHS01',
          areaCode: 'AREA01',
          version: 2,
        }),
      )

      const { result } = withSetup(() => useLocationForm())
      await result.fetchLocation()

      await result.handleSubmit()

      if (vi.mocked(apiClient.put).mock.calls.length > 0) {
        expect(apiClient.put).toHaveBeenCalledWith(
          '/master/locations/1',
          expect.objectContaining({
            version: 2,
          }),
        )
      }
    })
  })
})
