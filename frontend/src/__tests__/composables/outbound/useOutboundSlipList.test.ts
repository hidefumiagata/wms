import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { withSetup, mockAxiosResponse, flushPromises } from '../../helpers'
import { useOutboundSlipList } from '@/composables/outbound/useOutboundSlipList'
import { useWarehouseStore } from '@/stores/warehouse'
import { useAuthStore } from '@/stores/auth'
import axios from 'axios'

vi.mock('@/api/generated/models/outbound-slip-summary', () => ({}))
vi.mock('@/api/generated/models/outbound-slip-page-response', () => ({}))
vi.mock('@/api/generated/models/outbound-slip-status', () => ({
  OutboundSlipStatus: {
    Ordered: 'ORDERED',
    PartialAllocated: 'PARTIAL_ALLOCATED',
    Allocated: 'ALLOCATED',
    PickingCompleted: 'PICKING_COMPLETED',
    Inspecting: 'INSPECTING',
    Shipped: 'SHIPPED',
    Cancelled: 'CANCELLED',
  },
}))

describe('useOutboundSlipList', () => {
  const createMockResponse = () => ({
    content: [{ id: 1, slipNumber: 'OUT-001', status: 'ORDERED' }],
    totalElements: 1,
    totalPages: 1,
    page: 0,
    size: 20,
  })

  beforeEach(() => {
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createMockResponse()))
  })

  it('fetchList が warehouseId と signal を含める', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 42
      return useOutboundSlipList()
    })

    await result.fetchList()

    expect(apiClient.get).toHaveBeenCalledWith(
      '/outbound/slips',
      expect.objectContaining({
        params: expect.objectContaining({ warehouseId: 42 }),
        signal: expect.any(AbortSignal),
      }),
    )
    expect(result.items.value).toHaveLength(1)
  })

  it('onUnmounted 時にリクエストがキャンセルされる', async () => {
    const { result, wrapper } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useOutboundSlipList()
    })

    const fetchPromise = result.fetchList()
    const signal = vi.mocked(apiClient.get).mock.calls[0][1]!.signal!
    expect(signal.aborted).toBe(false)

    wrapper.unmount()
    expect(signal.aborted).toBe(true)

    await fetchPromise
  })

  it('handleSearch がページを1にリセットする', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useOutboundSlipList()
    })

    result.page.value = 5
    result.handleSearch()
    await flushPromises()

    expect(result.page.value).toBe(1)
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('handleReset が検索条件をデフォルトに戻す', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useOutboundSlipList()
    })

    result.searchForm.slipNumber = 'OUT-999'
    result.searchForm.partnerId = 5
    result.page.value = 3

    result.handleReset()
    await flushPromises()

    expect(result.searchForm.slipNumber).toBe('')
    expect(result.searchForm.partnerId).toBeNull()
    expect(result.page.value).toBe(1)
  })

  it('fetchPartnerOptions が出荷先オプションを取得する', async () => {
    const customerRes = mockAxiosResponse({ content: [{ id: 1, partnerName: '出荷先A' }] })
    const bothRes = mockAxiosResponse({ content: [{ id: 2, partnerName: '兼用B' }] })
    vi.mocked(apiClient.get).mockResolvedValueOnce(customerRes).mockResolvedValueOnce(bothRes)

    const { result } = withSetup(() => useOutboundSlipList())
    await result.fetchPartnerOptions()

    expect(result.partnerOptions.value).toHaveLength(2)
    expect(result.partnerOptions.value[0].partnerName).toBe('出荷先A')
  })

  it('handleBulkAllocate が POST /allocation/execute を呼ぶ', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse({}))

    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useOutboundSlipList()
    })

    result.selectedIds.value = [1, 2]
    await result.handleBulkAllocate()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(apiClient.post).toHaveBeenCalledWith('/allocation/execute', { outboundSlipIds: [1, 2] })
    expect(ElMessage.success).toHaveBeenCalled()
  })

  it('handleBulkAllocate が未選択時に警告する', async () => {
    const { result } = withSetup(() => useOutboundSlipList())
    result.selectedIds.value = []
    await result.handleBulkAllocate()

    expect(ElMessage.warning).toHaveBeenCalled()
    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('handleAllocateSingle が単一IDで引当実行する', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse({}))

    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useOutboundSlipList()
    })

    await result.handleAllocateSingle(99)

    expect(apiClient.post).toHaveBeenCalledWith('/allocation/execute', { outboundSlipIds: [99] })
  })

  it('handleSelectionChange が selectedIds を更新する', () => {
    const { result } = withSetup(() => useOutboundSlipList())

    result.handleSelectionChange([
      { id: 1, slipNumber: 'OUT-001' } as never,
      { id: 2, slipNumber: 'OUT-002' } as never,
    ])

    expect(result.selectedIds.value).toEqual([1, 2])
  })

  it('isViewer がロールに基づいて判定される', () => {
    const { result } = withSetup(() => {
      const auth = useAuthStore()
      auth.user = {
        userId: 1,
        userCode: 'v1',
        fullName: 'Viewer',
        role: 'VIEWER',
        passwordChangeRequired: false,
      }
      return useOutboundSlipList()
    })

    expect(result.isViewer.value).toBe(true)
  })
})
