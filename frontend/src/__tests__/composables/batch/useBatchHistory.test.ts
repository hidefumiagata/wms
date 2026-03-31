import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, flushPromises } from '../../helpers'
import { useBatchHistory } from '@/composables/batch/useBatchHistory'
import { downloadReport } from '@/utils/reportDownload'
import { ElMessage } from 'element-plus'
import axios from 'axios'

vi.mock('@/utils/reportDownload', () => ({
  downloadReport: vi.fn().mockResolvedValue(undefined),
}))

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

  it('fetchList でcontent/totalElementsがnullの場合デフォルト値を使う', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce(
      mockAxiosResponse({ content: null, totalElements: null, totalPages: 0, page: 0, size: 20 }),
    )
    const { result } = withSetup(() => useBatchHistory())
    await result.fetchList()
    expect(result.items.value).toEqual([])
    expect(result.total.value).toBe(0)
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

  describe('レポートダイアログ', () => {
    const processedDatesResponse = () => ({
      content: [
        { id: 1, targetBusinessDate: '2026-03-14', status: 'SUCCESS' },
        { id: 2, targetBusinessDate: '2026-03-13', status: 'SUCCESS' },
        { id: 3, targetBusinessDate: '2026-03-13', status: 'SUCCESS' },
      ],
      totalElements: 3,
      totalPages: 1,
      page: 0,
      size: 1000,
    })

    it('openReportDialog がダイアログを開き処理済み営業日を取得する', async () => {
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(processedDatesResponse()))

      const { result } = withSetup(() => useBatchHistory())
      result.openReportDialog('rpt006')
      await flushPromises()

      expect(result.reportDialogVisible.value).toBe(true)
      expect(result.activeReportType.value).toBe('rpt006')
      expect(result.processedDates.value).toEqual(['2026-03-14', '2026-03-13'])
      expect(result.reportBusinessDate.value).toBe('2026-03-14')
    })

    it('openReportDialog でrpt016が設定される', async () => {
      const { result } = withSetup(() => useBatchHistory())
      result.openReportDialog('rpt016')
      await flushPromises()

      expect(result.activeReportType.value).toBe('rpt016')
    })

    it('fetchProcessedDates エラー時にprocessedDatesが空になる', async () => {
      vi.mocked(apiClient.get).mockRejectedValue(new Error('fail'))

      const { result } = withSetup(() => useBatchHistory())
      result.openReportDialog('rpt006')
      await flushPromises()

      expect(result.processedDates.value).toEqual([])
      expect(result.reportBusinessDate.value).toBeNull()
    })

    it('closeReportDialog がダイアログ状態をリセットする', () => {
      const { result } = withSetup(() => useBatchHistory())

      result.reportDialogVisible.value = true
      result.activeReportType.value = 'rpt006'
      result.reportBusinessDate.value = '2026-03-14'
      result.processedDates.value = ['2026-03-14']

      result.closeReportDialog()

      expect(result.reportDialogVisible.value).toBe(false)
      expect(result.activeReportType.value).toBeNull()
      expect(result.reportBusinessDate.value).toBeNull()
      expect(result.processedDates.value).toEqual([])
    })

    it('isDateDisabled が処理済み日付はfalse、未処理日付はtrueを返す', () => {
      const { result } = withSetup(() => useBatchHistory())
      result.processedDates.value = ['2026-03-14', '2026-03-13']

      expect(result.isDateDisabled(new Date('2026-03-14'))).toBe(false)
      expect(result.isDateDisabled(new Date('2026-03-12'))).toBe(true)
    })

    it('downloadConfirmedReport(rpt006) がunreceived-confirmedをダウンロードする', async () => {
      const { result } = withSetup(() => useBatchHistory())
      result.activeReportType.value = 'rpt006'
      result.reportBusinessDate.value = '2026-03-14'
      result.reportDialogVisible.value = true

      await result.downloadConfirmedReport()

      expect(downloadReport).toHaveBeenCalledWith({
        path: '/reports/unreceived-confirmed',
        params: { targetBusinessDate: '2026-03-14' },
        format: 'pdf',
        filenameBase: 'unreceived_confirmed_20260314',
      })
      expect(ElMessage.success).toHaveBeenCalledWith('batch.history.reportDownloading')
      expect(result.reportDialogVisible.value).toBe(false)
    })

    it('downloadConfirmedReport(rpt016) がunshipped-confirmedをダウンロードする', async () => {
      const { result } = withSetup(() => useBatchHistory())
      result.activeReportType.value = 'rpt016'
      result.reportBusinessDate.value = '2026-03-13'
      result.reportDialogVisible.value = true

      await result.downloadConfirmedReport()

      expect(downloadReport).toHaveBeenCalledWith({
        path: '/reports/unshipped-confirmed',
        params: { targetBusinessDate: '2026-03-13' },
        format: 'pdf',
        filenameBase: 'unshipped_confirmed_20260313',
      })
      expect(ElMessage.success).toHaveBeenCalledWith('batch.history.reportDownloading')
    })

    it('downloadConfirmedReport が営業日未選択時に警告を表示する', async () => {
      const { result } = withSetup(() => useBatchHistory())
      result.activeReportType.value = 'rpt006'
      result.reportBusinessDate.value = null

      await result.downloadConfirmedReport()

      expect(downloadReport).not.toHaveBeenCalled()
      expect(ElMessage.warning).toHaveBeenCalledWith('batch.history.reportBusinessDateRequired')
    })

    it('downloadConfirmedReport がダウンロード中の二重実行を防止する', async () => {
      const { result } = withSetup(() => useBatchHistory())
      result.activeReportType.value = 'rpt006'
      result.reportBusinessDate.value = '2026-03-14'
      result.downloadingReport.value = true

      await result.downloadConfirmedReport()

      expect(downloadReport).not.toHaveBeenCalled()
    })

    it('fetchProcessedDates でcontentがnullの場合は空配列になる', async () => {
      vi.mocked(apiClient.get).mockResolvedValue(
        mockAxiosResponse({ content: null, totalElements: 0, totalPages: 0, page: 0, size: 1000 }),
      )

      const { result } = withSetup(() => useBatchHistory())
      result.openReportDialog('rpt006')
      await flushPromises()

      expect(result.processedDates.value).toEqual([])
      expect(result.reportBusinessDate.value).toBeNull()
    })

    it('fetchProcessedDates でtargetBusinessDateがnullのレコードはフィルタされる', async () => {
      vi.mocked(apiClient.get).mockResolvedValue(
        mockAxiosResponse({
          content: [
            { id: 1, targetBusinessDate: '2026-03-14', status: 'SUCCESS' },
            { id: 2, targetBusinessDate: null, status: 'SUCCESS' },
          ],
          totalElements: 2,
          totalPages: 1,
          page: 0,
          size: 1000,
        }),
      )

      const { result } = withSetup(() => useBatchHistory())
      result.openReportDialog('rpt006')
      await flushPromises()

      expect(result.processedDates.value).toEqual(['2026-03-14'])
    })

    it('downloadConfirmedReport エラー時にエラーメッセージを表示する', async () => {
      vi.mocked(downloadReport).mockRejectedValueOnce(new Error('fail'))

      const { result } = withSetup(() => useBatchHistory())
      result.activeReportType.value = 'rpt006'
      result.reportBusinessDate.value = '2026-03-14'

      await result.downloadConfirmedReport()

      expect(ElMessage.error).toHaveBeenCalledWith('batch.history.reportDownloadError')
      expect(result.downloadingReport.value).toBe(false)
    })
  })
})
