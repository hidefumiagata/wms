import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, flushPromises } from '../../helpers'
import { useInterfaceHistory } from '@/composables/interface/useInterfaceHistory'
import { ElMessage } from 'element-plus'
import axios from 'axios'

describe('useInterfaceHistory', () => {
  const createMockResponse = () => ({
    content: [
      {
        id: 1,
        ifType: 'INBOUND_PLAN',
        fileName: 'INB-PLAN-001.csv',
        totalCount: 100,
        successCount: 97,
        errorCount: 3,
        mode: 'SUCCESS_ONLY',
        status: 'COMPLETED',
        blobMoveFailed: false,
        warehouseId: 1,
        executedAt: '2026-03-18T14:30:05+09:00',
        executedBy: 1,
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
    const { result } = withSetup(() => useInterfaceHistory())

    result.searchForm.ifType = 'IFX-001'
    result.searchForm.dateFrom = '2026-03-01'
    result.searchForm.dateTo = '2026-03-31'
    result.searchForm.status = 'COMPLETED'
    result.searchForm.fileName = 'INB'

    await result.fetchList()

    expect(apiClient.get).toHaveBeenCalledWith(
      '/interface/executions',
      expect.objectContaining({
        params: expect.objectContaining({
          ifType: 'IFX-001',
          dateFrom: '2026-03-01',
          dateTo: '2026-03-31',
          status: 'COMPLETED',
          fileName: 'INB',
          sort: 'executedAt,desc',
        }),
      }),
    )
  })

  it('fetchList が検索条件なしの場合もデフォルトパラメータを送る', async () => {
    const { result } = withSetup(() => useInterfaceHistory())

    result.searchForm.ifType = null
    result.searchForm.dateFrom = null
    result.searchForm.dateTo = null
    result.searchForm.status = null
    result.searchForm.fileName = ''

    await result.fetchList()

    const callArgs = vi.mocked(apiClient.get).mock.calls[0]
    expect(callArgs[1]!.params).toEqual({
      page: 0,
      size: 20,
      sort: 'executedAt,desc',
    })
  })

  it('fetchList がitemsとtotalを更新する', async () => {
    const { result } = withSetup(() => useInterfaceHistory())

    await result.fetchList()

    expect(result.items.value).toHaveLength(1)
    expect(result.items.value[0].id).toBe(1)
    expect(result.total.value).toBe(1)
  })

  it('fetchList でcontent/totalElementsがnullの場合デフォルト値を使う', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce(
      mockAxiosResponse({ content: null, totalElements: null, totalPages: 0, page: 0, size: 20 }),
    )
    const { result } = withSetup(() => useInterfaceHistory())
    await result.fetchList()
    expect(result.items.value).toEqual([])
    expect(result.total.value).toBe(0)
  })

  it('fetchList がsignalを渡す（AbortController対応）', async () => {
    const { result } = withSetup(() => useInterfaceHistory())

    await result.fetchList()

    const callArgs = vi.mocked(apiClient.get).mock.calls[0]
    expect(callArgs[1]).toHaveProperty('signal')
    expect(callArgs[1]!.signal).toBeInstanceOf(AbortSignal)
  })

  it('onUnmounted時にリクエストがキャンセルされる', async () => {
    const { result, wrapper } = withSetup(() => useInterfaceHistory())

    const fetchPromise = result.fetchList()
    const signal = vi.mocked(apiClient.get).mock.calls[0][1]!.signal!
    expect(signal.aborted).toBe(false)

    wrapper.unmount()
    expect(signal.aborted).toBe(true)

    await fetchPromise
  })

  it('キャンセル時にstateが更新されない', async () => {
    const { result } = withSetup(() => useInterfaceHistory())

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

    const { result } = withSetup(() => useInterfaceHistory())
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

    const { result } = withSetup(() => useInterfaceHistory())
    await result.fetchList()

    expect(result.items.value).toEqual([])
    expect(result.total.value).toBe(0)
    expect(ElMessage.error).toHaveBeenCalledWith('interfaceHistory.fetchError')
  })

  it('handleSearch がページを1にリセットしてfetchListを呼ぶ', async () => {
    const { result } = withSetup(() => useInterfaceHistory())

    result.page.value = 5
    result.handleSearch()
    await flushPromises()

    expect(result.page.value).toBe(1)
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('handleReset が検索条件をデフォルトに戻す', async () => {
    const { result } = withSetup(() => useInterfaceHistory())

    result.searchForm.ifType = 'IFX-001'
    result.searchForm.status = 'FAILED'
    result.searchForm.fileName = 'test'
    result.page.value = 3

    result.handleReset()
    await flushPromises()

    expect(result.searchForm.ifType).toBeNull()
    expect(result.searchForm.status).toBeNull()
    expect(result.searchForm.fileName).toBe('')
    expect(result.page.value).toBe(1)
    expect(result.searchForm.dateFrom).toBeTruthy()
    expect(result.searchForm.dateTo).toBeTruthy()
  })

  it('handlePageChange がページを変更してfetchListを呼ぶ', async () => {
    const { result } = withSetup(() => useInterfaceHistory())

    result.handlePageChange(3)
    await flushPromises()

    expect(result.page.value).toBe(3)
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('handleSizeChange がサイズを変更しページを1にリセットしてfetchListを呼ぶ', async () => {
    const { result } = withSetup(() => useInterfaceHistory())

    result.page.value = 5
    result.handleSizeChange(50)
    await flushPromises()

    expect(result.pageSize.value).toBe(50)
    expect(result.page.value).toBe(1)
    expect(apiClient.get).toHaveBeenCalled()
  })
})
