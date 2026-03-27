import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, flushPromises } from '../../helpers'
import { useInboundSlipList } from '@/composables/inbound/useInboundSlipList'
import { useWarehouseStore } from '@/stores/warehouse'
import { useAuthStore } from '@/stores/auth'
import axios from 'axios'

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
})
