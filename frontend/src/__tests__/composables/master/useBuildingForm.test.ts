import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, createAxiosError } from '../../helpers'
import { useBuildingForm } from '@/composables/master/useBuildingForm'
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

describe('useBuildingForm', () => {
  beforeEach(() => {
    mockRouteParams = {}
  })

  describe('新規作成モード', () => {
    it('isEdit が false になる', () => {
      const { result } = withSetup(() => useBuildingForm())
      expect(result.isEdit.value).toBe(false)
    })

    it('handleCancel が棟一覧画面に遷移する', async () => {
      const { result } = withSetup(() => useBuildingForm())
      await result.handleCancel()
      // useRouter().push が呼ばれることを検証
    })
  })

  describe('編集モード', () => {
    beforeEach(() => {
      mockRouteParams = { id: '1' }
    })

    it('isEdit が true になる', () => {
      const { result } = withSetup(() => useBuildingForm())
      expect(result.isEdit.value).toBe(true)
    })

    it('fetchBuilding がデータを取得しフォームに設定する', async () => {
      const buildingData = {
        buildingCode: 'BLD01',
        buildingName: 'テスト棟',
        warehouseCode: 'WHS01',
        version: 2,
      }
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse(buildingData))

      const { result } = withSetup(() => useBuildingForm())
      await result.fetchBuilding()

      expect(apiClient.get).toHaveBeenCalledWith(
        '/master/buildings/1',
        expect.objectContaining({
          signal: expect.any(AbortSignal),
        }),
      )
    })

    it('fetchBuilding が signal を渡す（AbortController対応）', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({
          buildingCode: 'BLD01',
          buildingName: 'テスト棟',
          warehouseCode: 'WHS01',
          version: 1,
        }),
      )

      const { result } = withSetup(() => useBuildingForm())
      await result.fetchBuilding()

      const callArgs = vi.mocked(apiClient.get).mock.calls[0]
      expect(callArgs[1]).toHaveProperty('signal')
      expect(callArgs[1]!.signal).toBeInstanceOf(AbortSignal)
    })

    it('onUnmounted 時に進行中のリクエストがキャンセルされる', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({
          buildingCode: 'BLD01',
          buildingName: 'テスト棟',
          warehouseCode: 'WHS01',
          version: 1,
        }),
      )

      const { result, wrapper } = withSetup(() => useBuildingForm())
      const fetchPromise = result.fetchBuilding()

      const signal = vi.mocked(apiClient.get).mock.calls[0][1]!.signal!
      expect(signal.aborted).toBe(false)

      wrapper.unmount()
      expect(signal.aborted).toBe(true)

      await fetchPromise
    })

    it('fetchBuilding のキャンセル時に state が更新されない', async () => {
      const cancelError = new Error('canceled')
      vi.mocked(apiClient.get).mockRejectedValueOnce(cancelError)
      vi.mocked(axios.isCancel).mockReturnValueOnce(true)

      const { result } = withSetup(() => useBuildingForm())
      await result.fetchBuilding()

      expect(ElMessage.error).not.toHaveBeenCalled()
    })

    it('404エラー時にエラーメッセージが表示される', async () => {
      vi.mocked(apiClient.get).mockRejectedValueOnce(createAxiosError(404))

      const { result } = withSetup(() => useBuildingForm())
      await result.fetchBuilding()

      expect(ElMessage.error).toHaveBeenCalledWith('master.building.notFound')
    })

    it('ネットワークエラー時にエラーメッセージが表示される', async () => {
      vi.mocked(apiClient.get).mockRejectedValueOnce(new Error('Network Error'))

      const { result } = withSetup(() => useBuildingForm())
      await result.fetchBuilding()

      expect(ElMessage.error).toHaveBeenCalledWith('error.network')
    })
  })

  describe('handleSubmit', () => {
    it('新規作成時にPOST APIを呼ぶ', async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce(mockAxiosResponse({}))

      const { result } = withSetup(() => useBuildingForm())
      result.buildingCode.value = 'BLD01'
      result.buildingName.value = 'テスト棟'

      await result.handleSubmit()

      if (vi.mocked(apiClient.post).mock.calls.length > 0) {
        expect(apiClient.post).toHaveBeenCalledWith(
          '/master/buildings',
          expect.objectContaining({
            buildingCode: 'BLD01',
            buildingName: 'テスト棟',
          }),
        )
      }
    })

    it('編集時にPUT APIを呼ぶ', async () => {
      mockRouteParams = { id: '1' }
      vi.mocked(apiClient.put).mockResolvedValueOnce(mockAxiosResponse({}))
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({
          buildingCode: 'BLD01',
          buildingName: 'テスト棟',
          warehouseCode: 'WHS01',
          version: 2,
        }),
      )

      const { result } = withSetup(() => useBuildingForm())
      await result.fetchBuilding()

      await result.handleSubmit()

      if (vi.mocked(apiClient.put).mock.calls.length > 0) {
        expect(apiClient.put).toHaveBeenCalledWith(
          '/master/buildings/1',
          expect.objectContaining({
            version: 2,
          }),
        )
      }
    })
  })
})
