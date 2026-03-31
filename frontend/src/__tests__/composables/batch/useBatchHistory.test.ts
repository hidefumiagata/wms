import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, flushPromises } from '../../helpers'
import { useBatchHistory } from '@/composables/batch/useBatchHistory'
import { ElMessage } from 'element-plus'
import axios from 'axios'

describe('useBatchHistory', () => {
  const createMockResponse = () => ({
    content: [
      {
        id: 1,
        targetBusinessDate: '2026-03-14',
        status: 'SUCCESS',
        startedAt: '2026-03-14T23:00:01+09:00',
        completedAt: '2026-03-14T23:00:08+09:00',
        executedByName: '山田 太郎',
      },
    ],
    totalElements: 1,
    totalPages: 1,
    page: 0,
    size: 20,
  })

  beforeEach(() => {
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createMockResponse()))
  })

  it('fetchList がパラメータ付きでAPIを呼び出す', async () => {
    const { result } = withSetup(() => useBatchHistory())

    result.searchForm.executedDateFrom = '2026-03-01'
    result.searchForm.executedDateTo = '2026-03-31'
    result.searchForm.targetBusinessDate = '2026-03-14'
    result.searchForm.status = 'SUCCESS' as never

    await result.fetchList()

    expect(apiClient.get).toHaveBeenCalledWith(
      '/batch/executions',
      expect.objectContaining({
        params: expect.objectContaining({
          executedDateFrom: '2026-03-01',
          executedDateTo: '2026-03-31',
          targetBusinessDate: '2026-03-14',
          status: 'SUCCESS',
          sort: 'startedAt,desc',
        }),
      }),
    )
  })

  it('fetchList が検索条件なしの場合もデフォルトパラメータを送る', async () => {
    const { result } = withSetup(() => useBatchHistory())

    result.searchForm.executedDateFrom = null
    result.searchForm.executedDateTo = null
    result.searchForm.targetBusinessDate = null
    result.searchForm.status = null

    await result.fetchList()

    const callArgs = vi.mocked(apiClient.get).mock.calls[0]
    expect(callArgs[1]!.params).toEqual({
      page: 0,
      size: 20,
      sort: 'startedAt,desc',
    })
  })

  it('fetchList がitemsとtotalを更新する', async () => {
    const { result } = withSetup(() => useBatchHistory())

    await result.fetchList()

    expect(result.items.value).toHaveLength(1)
    expect(result.items.value[0].id).toBe(1)
    expect(result.total.value).toBe(1)
  })

  it('fetchList がsignalを渡す（AbortController対応）', async () => {
    const { result } = withSetup(() => useBatchHistory())

    await result.fetchList()

    const callArgs = vi.mocked(apiClient.get).mock.calls[0]
    expect(callArgs[1]).toHaveProperty('signal')
    expect(callArgs[1]!.signal).toBeInstanceOf(AbortSignal)
  })

  it('onUnmounted時にリクエストがキャンセルされる', async () => {
    const { result, wrapper } = withSetup(() => useBatchHistory())

    const fetchPromise = result.fetchList()
    const signal = vi.mocked(apiClient.get).mock.calls[0][1]!.signal!
    expect(signal.aborted).toBe(false)

    wrapper.unmount()
    expect(signal.aborted).toBe(true)

    await fetchPromise
  })

  it('キャンセル時にstateが更新されない', async () => {
    const { result } = withSetup(() => useBatchHistory())

    await result.fetchList()
    expect(result.items.value).toHaveLength(1)

    const cancelError = new Error('canceled')
    vi.mocked(apiClient.get).mockRejectedValueOnce(cancelError)
    vi.mocked(axios.isCancel).mockReturnValueOnce(true)

    await result.fetchList()
    expect(result.items.value).toHaveLength(1)
  })

  it('ネットワークエラー時にerror.networkメッセージを表示する', async () => {
    const networkError = new Error('Network Error')
    vi.mocked(apiClient.get).mockRejectedValueOnce(networkError)

    const { result } = withSetup(() => useBatchHistory())
    await result.fetchList()

    expect(result.items.value).toEqual([])
    expect(result.total.value).toBe(0)
    expect(ElMessage.error).toHaveBeenCalledWith('error.network')
  })

  it('APIエラー時にfetchErrorメッセージを表示する', async () => {
    const apiError = Object.assign(new Error('Request failed'), {
      isAxiosError: true,
      response: { status: 500, data: {} },
    })
    vi.mocked(apiClient.get).mockRejectedValueOnce(apiError)

    const { result } = withSetup(() => useBatchHistory())
    await result.fetchList()

    expect(result.items.value).toEqual([])
    expect(result.total.value).toBe(0)
    expect(ElMessage.error).toHaveBeenCalledWith('batch.history.fetchError')
  })

  it('handleSearch がページを1にリセットしてfetchListを呼ぶ', async () => {
    const { result } = withSetup(() => useBatchHistory())

    result.page.value = 5
    result.handleSearch()
    await flushPromises()

    expect(result.page.value).toBe(1)
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('handleReset が検索条件をデフォルトに戻す', async () => {
    const { result } = withSetup(() => useBatchHistory())

    result.searchForm.targetBusinessDate = '2026-03-14'
    result.searchForm.status = 'FAILED' as never
    result.page.value = 3

    result.handleReset()
    await flushPromises()

    expect(result.searchForm.targetBusinessDate).toBeNull()
    expect(result.searchForm.status).toBeNull()
    expect(result.page.value).toBe(1)
    expect(result.searchForm.executedDateFrom).toBeTruthy()
    expect(result.searchForm.executedDateTo).toBeTruthy()
  })

  it('handlePageChange がページを変更してfetchListを呼ぶ', async () => {
    const { result } = withSetup(() => useBatchHistory())

    result.handlePageChange(3)
    await flushPromises()

    expect(result.page.value).toBe(3)
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('handleSizeChange がサイズを変更しページを1にリセットしてfetchListを呼ぶ', async () => {
    const { result } = withSetup(() => useBatchHistory())

    result.page.value = 5
    result.handleSizeChange(50)
    await flushPromises()

    expect(result.pageSize.value).toBe(50)
    expect(result.page.value).toBe(1)
    expect(apiClient.get).toHaveBeenCalled()
  })

  describe('openDetail', () => {
    it('詳細をAPIから取得してドロワーを表示する', async () => {
      const detailData = {
        id: 1,
        targetBusinessDate: '2026-03-14',
        status: 'SUCCESS',
        step1Status: 'SUCCESS',
        step2Status: 'SUCCESS',
        step3Status: 'SUCCESS',
        step4Status: 'SUCCESS',
        step5Status: 'SUCCESS',
        step6Status: 'SUCCESS',
        startedAt: '2026-03-14T23:00:01+09:00',
        completedAt: '2026-03-14T23:00:08+09:00',
        executedByName: '山田 太郎',
      }
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse(detailData))

      const { result } = withSetup(() => useBatchHistory())
      await result.openDetail(1)

      expect(apiClient.get).toHaveBeenCalledWith('/batch/executions/1')
      expect(result.drawerVisible.value).toBe(true)
      expect(result.drawerLoading.value).toBe(false)
      expect(result.selectedDetail.value).toEqual(detailData)
    })

    it('詳細取得エラー時にエラーメッセージを表示する', async () => {
      vi.mocked(apiClient.get).mockRejectedValueOnce(new Error('fail'))

      const { result } = withSetup(() => useBatchHistory())
      await result.openDetail(999)

      expect(result.drawerVisible.value).toBe(true)
      expect(result.drawerLoading.value).toBe(false)
      expect(result.selectedDetail.value).toBeNull()
      expect(ElMessage.error).toHaveBeenCalledWith('batch.history.detailFetchError')
    })
  })

  describe('closeDrawer', () => {
    it('ドロワーを閉じてselectedDetailをクリアする', () => {
      const { result } = withSetup(() => useBatchHistory())

      result.drawerVisible.value = true
      result.selectedDetail.value = { id: 1 } as never

      result.closeDrawer()

      expect(result.drawerVisible.value).toBe(false)
      expect(result.selectedDetail.value).toBeNull()
    })
  })
})
