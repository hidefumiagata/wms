import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, flushPromises } from '../../helpers'
import { useInboundSlipList } from '@/composables/inbound/useInboundSlipList'
import { useWarehouseStore } from '@/stores/warehouse'
import { useAuthStore } from '@/stores/auth'
import { downloadReport } from '@/utils/reportDownload'
import { ElMessage } from 'element-plus'
import axios from 'axios'

vi.mock('@/utils/reportDownload', () => ({
  downloadReport: vi.fn().mockResolvedValue(undefined),
}))

describe('useInboundSlipList', () => {
  const createMockResponse = () => ({
    content: [{ id: 1, slipNumber: 'INB-001' }],
    totalElements: 1,
    totalPages: 1,
    page: 0,
    size: 20,
  })

  beforeEach(() => {
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createMockResponse()))
  })

  it('fetchList が warehouseId をパラメータに含める', async () => {
    const { result } = withSetup(() => {
      const warehouseStore = useWarehouseStore()
      warehouseStore.selectedWarehouseId = 42
      return useInboundSlipList()
    })

    await result.fetchList()

    expect(apiClient.get).toHaveBeenCalledWith(
      '/inbound/slips',
      expect.objectContaining({
        params: expect.objectContaining({ warehouseId: 42 }),
        signal: expect.any(AbortSignal),
      }),
    )
  })

  it('fetchList が検索条件をパラメータに含める', async () => {
    const { result } = withSetup(() => {
      const warehouseStore = useWarehouseStore()
      warehouseStore.selectedWarehouseId = 1
      return useInboundSlipList()
    })

    result.searchForm.slipNumber = 'INB-001'
    result.searchForm.plannedDateFrom = '2026-03-01'
    result.searchForm.plannedDateTo = '2026-03-31'
    result.searchForm.partnerId = 5
    result.searchForm.status = 'PLANNED' as never

    await result.fetchList()

    expect(apiClient.get).toHaveBeenCalledWith(
      '/inbound/slips',
      expect.objectContaining({
        params: expect.objectContaining({
          slipNumber: 'INB-001',
          plannedDateFrom: '2026-03-01',
          plannedDateTo: '2026-03-31',
          partnerId: 5,
          status: 'PLANNED',
        }),
      }),
    )
  })

  it('fetchList が signal を渡す（AbortController対応）', async () => {
    const { result } = withSetup(() => {
      const warehouseStore = useWarehouseStore()
      warehouseStore.selectedWarehouseId = 1
      return useInboundSlipList()
    })

    await result.fetchList()

    const callArgs = vi.mocked(apiClient.get).mock.calls[0]
    expect(callArgs[1]).toHaveProperty('signal')
    expect(callArgs[1]!.signal).toBeInstanceOf(AbortSignal)
  })

  it('onUnmounted 時にリクエストがキャンセルされる', async () => {
    const { result, wrapper } = withSetup(() => {
      const warehouseStore = useWarehouseStore()
      warehouseStore.selectedWarehouseId = 1
      return useInboundSlipList()
    })

    const fetchPromise = result.fetchList()

    // fetchList 呼び出し時に渡された signal を取得
    const signal = vi.mocked(apiClient.get).mock.calls[0][1]!.signal!
    expect(signal.aborted).toBe(false)

    wrapper.unmount()

    expect(signal.aborted).toBe(true)

    await fetchPromise
  })

  it('キャンセル時に state が更新されない', async () => {
    const { result } = withSetup(() => {
      const warehouseStore = useWarehouseStore()
      warehouseStore.selectedWarehouseId = 1
      return useInboundSlipList()
    })

    // 初期データロード
    await result.fetchList()
    expect(result.items.value).toHaveLength(1)

    // キャンセルエラー
    const cancelError = new Error('canceled')
    vi.mocked(apiClient.get).mockRejectedValueOnce(cancelError)
    vi.mocked(axios.isCancel).mockReturnValueOnce(true)

    await result.fetchList()

    // キャンセル時は items がリセットされない
    expect(result.items.value).toHaveLength(1)
  })

  it('倉庫切替時にページを1にリセットしてfetchListを呼ぶ', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInboundSlipList()
    })

    result.page.value = 3
    vi.mocked(apiClient.get).mockClear()

    const ws = useWarehouseStore()
    ws.selectedWarehouseId = 2
    await flushPromises()

    expect(result.page.value).toBe(1)
    expect(apiClient.get).toHaveBeenCalledWith(
      '/inbound/slips',
      expect.objectContaining({
        params: expect.objectContaining({ warehouseId: 2 }),
      }),
    )
  })

  it('倉庫がnullになった場合はfetchListを呼ばない', async () => {
    withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInboundSlipList()
    })

    vi.mocked(apiClient.get).mockClear()

    const ws = useWarehouseStore()
    ws.selectedWarehouseId = null
    await flushPromises()

    expect(apiClient.get).not.toHaveBeenCalled()
  })

  it('isViewer がロールに基づいて判定される', () => {
    const { result } = withSetup(() => {
      const auth = useAuthStore()
      auth.user = {
        userId: 1,
        userCode: 'user1',
        fullName: 'Test User',
        role: 'VIEWER',
        passwordChangeRequired: false,
      }
      return useInboundSlipList()
    })

    expect(result.isViewer.value).toBe(true)
  })

  it('handleSearch がページを1にリセットしてfetchListを呼ぶ', async () => {
    const { result } = withSetup(() => {
      const warehouseStore = useWarehouseStore()
      warehouseStore.selectedWarehouseId = 1
      return useInboundSlipList()
    })

    result.page.value = 5
    result.handleSearch()
    await flushPromises()

    expect(result.page.value).toBe(1)
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('handleReset が検索条件をデフォルトに戻す', async () => {
    const { result } = withSetup(() => {
      const warehouseStore = useWarehouseStore()
      warehouseStore.selectedWarehouseId = 1
      return useInboundSlipList()
    })

    result.searchForm.slipNumber = 'INB-999'
    result.searchForm.partnerId = 5
    result.page.value = 3

    result.handleReset()
    await flushPromises()

    expect(result.searchForm.slipNumber).toBe('')
    expect(result.searchForm.partnerId).toBeNull()
    expect(result.page.value).toBe(1)
    expect(result.searchForm.plannedDateFrom).toBeTruthy()
    expect(result.searchForm.plannedDateTo).toBeTruthy()
  })

  it('handlePageChange がページを変更してfetchListを呼ぶ', async () => {
    const { result } = withSetup(() => {
      const warehouseStore = useWarehouseStore()
      warehouseStore.selectedWarehouseId = 1
      return useInboundSlipList()
    })

    result.handlePageChange(3)
    await flushPromises()

    expect(result.page.value).toBe(3)
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('handleSizeChange がサイズを変更しページを1にリセットしてfetchListを呼ぶ', async () => {
    const { result } = withSetup(() => {
      const warehouseStore = useWarehouseStore()
      warehouseStore.selectedWarehouseId = 1
      return useInboundSlipList()
    })

    result.page.value = 5
    result.handleSizeChange(50)
    await flushPromises()

    expect(result.pageSize.value).toBe(50)
    expect(result.page.value).toBe(1)
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('fetchList のネットワークエラー時にerror.networkメッセージを表示する', async () => {
    const networkError = new Error('Network Error')
    vi.mocked(apiClient.get).mockRejectedValueOnce(networkError)

    const { result } = withSetup(() => {
      const warehouseStore = useWarehouseStore()
      warehouseStore.selectedWarehouseId = 1
      return useInboundSlipList()
    })

    await result.fetchList()

    expect(result.items.value).toEqual([])
    expect(result.total.value).toBe(0)
    expect(ElMessage.error).toHaveBeenCalledWith('error.network')
  })

  it('fetchList のAPIエラー時にfetchErrorメッセージを表示する', async () => {
    const apiError = Object.assign(new Error('Request failed'), {
      isAxiosError: true,
      response: { status: 500, data: {} },
    })
    vi.mocked(apiClient.get).mockRejectedValueOnce(apiError)

    const { result } = withSetup(() => {
      const warehouseStore = useWarehouseStore()
      warehouseStore.selectedWarehouseId = 1
      return useInboundSlipList()
    })

    await result.fetchList()

    expect(result.items.value).toEqual([])
    expect(result.total.value).toBe(0)
    expect(ElMessage.error).toHaveBeenCalledWith('inbound.slip.fetchError')
  })

  it('fetchPartnerOptions のエラーは無視される', async () => {
    vi.mocked(apiClient.get).mockRejectedValueOnce(new Error('fail'))

    const { result } = withSetup(() => useInboundSlipList())
    await result.fetchPartnerOptions()

    expect(result.partnerOptions.value).toEqual([])
  })

  it('fetchPartnerOptions が仕入先オプションを取得する', async () => {
    const supplierRes = mockAxiosResponse({ content: [{ id: 1, partnerName: '仕入先A' }] })
    const bothRes = mockAxiosResponse({ content: [{ id: 2, partnerName: '兼用B' }] })
    vi.mocked(apiClient.get).mockResolvedValueOnce(supplierRes).mockResolvedValueOnce(bothRes)

    const { result } = withSetup(() => useInboundSlipList())
    await result.fetchPartnerOptions()

    expect(result.partnerOptions.value).toHaveLength(2)
    expect(result.partnerOptions.value[0].partnerName).toBe('仕入先A')
    expect(result.partnerOptions.value[1].partnerName).toBe('兼用B')
  })

  describe('downloadInboundPlanReport', () => {
    it('ダウンロード中の二重クリックを防止する', async () => {
      let resolveDeferred: () => void
      vi.mocked(downloadReport).mockImplementationOnce(
        () => new Promise<void>((r) => { resolveDeferred = r }),
      )

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useInboundSlipList()
      })

      const firstCall = result.downloadInboundPlanReport()
      expect(result.downloadingInboundPlan.value).toBe(true)

      // 二重クリック: downloadReport は再度呼ばれない
      await result.downloadInboundPlanReport()
      expect(vi.mocked(downloadReport)).toHaveBeenCalledTimes(1)

      resolveDeferred!()
      await firstCall
    })

    it('現在の検索条件でRPT-03をPDFダウンロードする', async () => {
      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 10
        return useInboundSlipList()
      })

      result.searchForm.plannedDateFrom = '2026-03-01'
      result.searchForm.plannedDateTo = '2026-03-31'
      result.searchForm.partnerId = 5
      result.searchForm.status = 'PLANNED' as never

      await result.downloadInboundPlanReport()

      expect(downloadReport).toHaveBeenCalledWith(
        expect.objectContaining({
          path: '/reports/inbound-plan',
          params: expect.objectContaining({
            warehouseId: 10,
            plannedDateFrom: '2026-03-01',
            plannedDateTo: '2026-03-31',
            partnerId: 5,
            status: 'PLANNED',
          }),
          format: 'pdf',
        }),
      )
    })

    it('検索条件が空の場合はwarehouseIdのみ送る', async () => {
      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 10
        return useInboundSlipList()
      })

      result.searchForm.plannedDateFrom = null
      result.searchForm.plannedDateTo = null
      result.searchForm.partnerId = null
      result.searchForm.status = null

      await result.downloadInboundPlanReport()

      const callArgs = vi.mocked(downloadReport).mock.calls[0][0]
      expect(callArgs.params).toEqual({ warehouseId: 10 })
    })

    it('downloadingInboundPlan がtrue→falseに遷移する', async () => {
      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useInboundSlipList()
      })

      expect(result.downloadingInboundPlan.value).toBe(false)
      const promise = result.downloadInboundPlanReport()
      expect(result.downloadingInboundPlan.value).toBe(true)
      await promise
      expect(result.downloadingInboundPlan.value).toBe(false)
    })

    it('エラー時にElMessage.errorを表示する', async () => {
      vi.mocked(downloadReport).mockRejectedValueOnce(new Error('fail'))

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useInboundSlipList()
      })

      await result.downloadInboundPlanReport()

      expect(ElMessage.error).toHaveBeenCalledWith('inbound.slip.reportDownloadError')
      expect(result.downloadingInboundPlan.value).toBe(false)
    })

    it('ダウンロード開始時にElMessage.infoを表示する', async () => {
      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useInboundSlipList()
      })

      await result.downloadInboundPlanReport()

      expect(ElMessage.info).toHaveBeenCalledWith('inbound.slip.reportDownloading')
    })
  })

  describe('downloadUnreceivedRealtimeReport', () => {
    it('ダウンロード中の二重クリックを防止する', async () => {
      let resolveDeferred: () => void
      vi.mocked(downloadReport).mockImplementationOnce(
        () => new Promise<void>((r) => { resolveDeferred = r }),
      )

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useInboundSlipList()
      })

      const firstCall = result.downloadUnreceivedRealtimeReport()
      expect(result.downloadingUnreceivedRt.value).toBe(true)

      await result.downloadUnreceivedRealtimeReport()
      expect(vi.mocked(downloadReport)).toHaveBeenCalledTimes(1)

      resolveDeferred!()
      await firstCall
    })

    it('RPT-05をPDFダウンロードする（warehouseIdのみ）', async () => {
      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 20
        return useInboundSlipList()
      })

      await result.downloadUnreceivedRealtimeReport()

      expect(downloadReport).toHaveBeenCalledWith(
        expect.objectContaining({
          path: '/reports/unreceived-realtime',
          params: { warehouseId: 20 },
          format: 'pdf',
        }),
      )
    })

    it('downloadingUnreceivedRt がtrue→falseに遷移する', async () => {
      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useInboundSlipList()
      })

      expect(result.downloadingUnreceivedRt.value).toBe(false)
      const promise = result.downloadUnreceivedRealtimeReport()
      expect(result.downloadingUnreceivedRt.value).toBe(true)
      await promise
      expect(result.downloadingUnreceivedRt.value).toBe(false)
    })

    it('エラー時にElMessage.errorを表示する', async () => {
      vi.mocked(downloadReport).mockRejectedValueOnce(new Error('fail'))

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useInboundSlipList()
      })

      await result.downloadUnreceivedRealtimeReport()

      expect(ElMessage.error).toHaveBeenCalledWith('inbound.slip.reportDownloadError')
      expect(result.downloadingUnreceivedRt.value).toBe(false)
    })

    it('ダウンロード開始時にElMessage.infoを表示する', async () => {
      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useInboundSlipList()
      })

      await result.downloadUnreceivedRealtimeReport()

      expect(ElMessage.info).toHaveBeenCalledWith('inbound.slip.reportDownloading')
    })
  })
})
