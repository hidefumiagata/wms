import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { withSetup, mockAxiosResponse, createAxiosError } from '../../helpers'
import { useOutboundSlipDetail } from '@/composables/outbound/useOutboundSlipDetail'
import { mockRoute, mockRouter } from '../../setup'
import axios from 'axios'

vi.mock('@/api/generated/models/outbound-slip-detail', () => ({}))
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

describe('useOutboundSlipDetail', () => {
  const mockSlip = {
    id: 1,
    slipNumber: 'OUT-001',
    status: 'ORDERED',
    lines: [],
    version: 1,
  }

  beforeEach(() => {
    mockRoute.params = { id: '1' }
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(mockSlip))
  })

  it('fetchDetail が signal 付きでGETリクエストを送信する', async () => {
    const { result } = withSetup(() => useOutboundSlipDetail())
    await result.fetchDetail()

    expect(apiClient.get).toHaveBeenCalledWith(
      '/outbound/slips/1',
      expect.objectContaining({
        signal: expect.any(AbortSignal),
      }),
    )
    expect(result.slip.value).toEqual(mockSlip)
  })

  it('fetchDetail で404の場合にリダイレクトする', async () => {
    vi.mocked(apiClient.get).mockRejectedValue(createAxiosError(404))

    const { result } = withSetup(() => useOutboundSlipDetail())
    await result.fetchDetail()

    expect(ElMessage.error).toHaveBeenCalled()
    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'outbound-slip-list' })
  })

  it('キャンセル時に state が更新されない', async () => {
    const { result } = withSetup(() => useOutboundSlipDetail())
    await result.fetchDetail()
    expect(result.slip.value).toEqual(mockSlip)

    const cancelError = new Error('canceled')
    vi.mocked(apiClient.get).mockRejectedValueOnce(cancelError)
    vi.mocked(axios.isCancel).mockReturnValueOnce(true)

    await result.fetchDetail()
    expect(result.slip.value).toEqual(mockSlip)
  })

  it('handleAllocate が POST /allocation/execute を呼ぶ', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse({}))

    const { result } = withSetup(() => useOutboundSlipDetail())
    await result.fetchDetail()
    await result.handleAllocate()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(apiClient.post).toHaveBeenCalledWith('/allocation/execute', { outboundSlipIds: [1] })
    expect(ElMessage.success).toHaveBeenCalled()
  })

  it('handleCancel が POST でキャンセルする', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse({}))

    const { result } = withSetup(() => useOutboundSlipDetail())
    await result.fetchDetail()
    await result.handleCancel()

    expect(apiClient.post).toHaveBeenCalledWith('/outbound/slips/1/cancel')
    expect(ElMessage.success).toHaveBeenCalled()
  })

  it('canAllocate が ORDERED ステータスで true', async () => {
    const { result } = withSetup(() => useOutboundSlipDetail())
    await result.fetchDetail()

    expect(result.canAllocate.value).toBe(true)
  })

  it('canAllocate が SHIPPED ステータスで false', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      mockAxiosResponse({ ...mockSlip, status: 'SHIPPED' }),
    )

    const { result } = withSetup(() => useOutboundSlipDetail())
    await result.fetchDetail()

    expect(result.canAllocate.value).toBe(false)
  })

  it('canCancel が SHIPPED ステータスで false', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      mockAxiosResponse({ ...mockSlip, status: 'SHIPPED' }),
    )

    const { result } = withSetup(() => useOutboundSlipDetail())
    await result.fetchDetail()

    expect(result.canCancel.value).toBe(false)
  })

  it('canInspect が PICKING_COMPLETED ステータスで true', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      mockAxiosResponse({ ...mockSlip, status: 'PICKING_COMPLETED' }),
    )

    const { result } = withSetup(() => useOutboundSlipDetail())
    await result.fetchDetail()

    expect(result.canInspect.value).toBe(true)
  })

  it('canShip が INSPECTING ステータスで true', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      mockAxiosResponse({ ...mockSlip, status: 'INSPECTING' }),
    )

    const { result } = withSetup(() => useOutboundSlipDetail())
    await result.fetchDetail()

    expect(result.canShip.value).toBe(true)
  })

  it('goBack が一覧画面に遷移する', () => {
    const { result } = withSetup(() => useOutboundSlipDetail())
    result.goBack()
    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'outbound-slip-list' })
  })

  it('goInspect が検品画面に遷移する', () => {
    const { result } = withSetup(() => useOutboundSlipDetail())
    result.goInspect()
    expect(mockRouter.push).toHaveBeenCalledWith({
      name: 'outbound-slip-inspect',
      params: { id: 1 },
    })
  })

  it('goShip が出荷画面に遷移する', () => {
    const { result } = withSetup(() => useOutboundSlipDetail())
    result.goShip()
    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'outbound-slip-ship', params: { id: 1 } })
  })
})
