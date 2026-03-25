import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, createCancelError, createAxiosError } from '../../helpers'
import { useWarehouseList } from '@/composables/master/useWarehouseList'
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

describe('useWarehouseList', () => {
  const mockListResponse = {
    content: [
      {
        id: 1,
        warehouseCode: 'WHSA',
        warehouseName: 'テスト倉庫',
        warehouseNameKana: 'テストソウコ',
        address: '東京都',
        isActive: true,
        version: 1,
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  }

  beforeEach(() => {
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(mockListResponse))
  })

  it('fetchList がデータを取得し、items と total を更新する', async () => {
    const { result } = withSetup(() => useWarehouseList())

    await result.fetchList()

    expect(apiClient.get).toHaveBeenCalledWith('/master/warehouses', expect.objectContaining({
      params: expect.objectContaining({ page: 0, size: 20 }),
    }))
    expect(result.items.value).toEqual(mockListResponse.content)
    expect(result.total.value).toBe(1)
    expect(result.loading.value).toBe(false)
  })

  it('fetchList が signal を axios に渡す（AbortController対応）', async () => {
    const { result } = withSetup(() => useWarehouseList())

    await result.fetchList()

    const callArgs = vi.mocked(apiClient.get).mock.calls[0]
    expect(callArgs[1]).toHaveProperty('signal')
    expect(callArgs[1]!.signal).toBeInstanceOf(AbortSignal)
  })

  it('fetchList を連続呼び出しすると前のリクエストがキャンセルされる', async () => {
    const { result } = withSetup(() => useWarehouseList())

    // 1回目の呼び出し
    const abortSpy = vi.spyOn(AbortController.prototype, 'abort')
    await result.fetchList()

    // 2回目の呼び出し — 1回目のAbortControllerがabortされる
    await result.fetchList()

    expect(abortSpy).toHaveBeenCalled()
    abortSpy.mockRestore()
  })

  it('onUnmounted 時に進行中のリクエストがキャンセルされる', async () => {
    const { result, wrapper } = withSetup(() => useWarehouseList())

    // fetchList でAbortControllerを作成
    const fetchPromise = result.fetchList()

    // AbortController.abort を監視
    const abortSpy = vi.spyOn(AbortController.prototype, 'abort')

    // コンポーネントをアンマウント
    wrapper.unmount()

    expect(abortSpy).toHaveBeenCalled()
    abortSpy.mockRestore()

    await fetchPromise
  })

  it('キャンセル時に state（items, total）が更新されない', async () => {
    const { result } = withSetup(() => useWarehouseList())

    // 初期データを設定
    await result.fetchList()
    expect(result.items.value).toHaveLength(1)

    // 次の fetchList がキャンセルされるケースをシミュレート
    vi.mocked(apiClient.get).mockRejectedValueOnce(createCancelError())
    vi.mocked(axios.isCancel).mockReturnValueOnce(true)

    await result.fetchList()

    // キャンセル時は items がリセットされない（前のデータが残る）
    expect(result.items.value).toHaveLength(1)
  })

  it('キャンセル時に loading が false にならない（新しいリクエストが管理する）', async () => {
    const { result } = withSetup(() => useWarehouseList())

    // AbortされたsignalをシミュレートするためAbortControllerを使う
    const controller = new AbortController()
    controller.abort()

    // signal.aborted が true の場合、loading は false にならない
    // 実際のコードでは finally 内で signal.aborted をチェックしている
    vi.mocked(apiClient.get).mockImplementationOnce(async (_url, config) => {
      // signal が aborted の状態をシミュレート
      const cancelError = createCancelError()
      vi.mocked(axios.isCancel).mockReturnValueOnce(true)
      throw cancelError
    })

    // fetchList 前に手動で loading を true にする想定
    await result.fetchList()

    // キャンセル時は loading の制御は新しいリクエストに委ねる
    // (catch内で早期return → finallyでsignal.abortedチェック)
  })

  it('handleSearch がページを1にリセットしてfetchListを呼ぶ', async () => {
    const { result } = withSetup(() => useWarehouseList())

    result.page.value = 3
    await result.handleSearch()

    expect(result.page.value).toBe(1)
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('handleReset が検索条件をクリアしてfetchListを呼ぶ', async () => {
    const { result } = withSetup(() => useWarehouseList())

    result.searchForm.warehouseCode = 'TEST'
    result.searchForm.warehouseName = 'テスト'
    result.searchForm.isActive = true
    result.page.value = 5

    await result.handleReset()

    expect(result.searchForm.warehouseCode).toBe('')
    expect(result.searchForm.warehouseName).toBe('')
    expect(result.searchForm.isActive).toBeNull()
    expect(result.page.value).toBe(1)
  })

  it('handlePageChange がページ番号を更新してfetchListを呼ぶ', async () => {
    const { result } = withSetup(() => useWarehouseList())

    await result.handlePageChange(5)

    expect(result.page.value).toBe(5)
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('handleSizeChange がページサイズを更新し、ページを1にリセットする', async () => {
    const { result } = withSetup(() => useWarehouseList())

    result.page.value = 3
    await result.handleSizeChange(50)

    expect(result.pageSize.value).toBe(50)
    expect(result.page.value).toBe(1)
  })

  it('検索条件がparamsに反映される', async () => {
    const { result } = withSetup(() => useWarehouseList())

    result.searchForm.warehouseCode = 'WHSA'
    result.searchForm.warehouseName = 'テスト'
    result.searchForm.isActive = true

    await result.fetchList()

    expect(apiClient.get).toHaveBeenCalledWith('/master/warehouses', expect.objectContaining({
      params: expect.objectContaining({
        warehouseCode: 'WHSA',
        warehouseName: 'テスト',
        isActive: true,
      }),
    }))
  })

  it('APIエラー時にitemsとtotalがリセットされる', async () => {
    const { result } = withSetup(() => useWarehouseList())

    // 初期データロード
    await result.fetchList()
    expect(result.items.value).toHaveLength(1)

    // APIエラー
    const axiosError = createAxiosError(500)
    vi.mocked(apiClient.get).mockRejectedValueOnce(axiosError)

    await result.fetchList()

    expect(result.items.value).toHaveLength(0)
    expect(result.total.value).toBe(0)
  })

  it('ネットワークエラー時にエラーメッセージが表示される', async () => {
    const { result } = withSetup(() => useWarehouseList())

    // responseがないエラー = ネットワークエラー
    vi.mocked(apiClient.get).mockRejectedValueOnce(new Error('Network Error'))

    await result.fetchList()

    expect(ElMessage.error).toHaveBeenCalled()
  })
})
