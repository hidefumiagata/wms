import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { withSetup, mockAxiosResponse, createAxiosError, flushPromises } from '../../helpers'
import { useInboundSlipDetail } from '@/composables/inbound/useInboundSlipDetail'
import { useAuthStore } from '@/stores/auth'
import { mockRoute, mockRouter } from '../../setup'
import axios from 'axios'

vi.mock('@/api/generated/models/inbound-slip-detail', () => ({}))
vi.mock('@/api/generated/models/inbound-slip-status', () => ({
  InboundSlipStatus: {
    Planned: 'PLANNED',
    Confirmed: 'CONFIRMED',
    Inspecting: 'INSPECTING',
    PartialStored: 'PARTIAL_STORED',
    Stored: 'STORED',
    Cancelled: 'CANCELLED',
  },
}))

describe('useInboundSlipDetail', () => {
  const mockSlip = {
    id: 1,
    slipNumber: 'INB-001',
    status: 'PLANNED',
    lines: [],
    version: 1,
  }

  beforeEach(() => {
    mockRoute.params = { id: '1' }
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(mockSlip))
  })

  it('fetchDetail が signal 付きでGETリクエストを送信する', async () => {
    const { result } = withSetup(() => useInboundSlipDetail())
    await result.fetchDetail()

    expect(apiClient.get).toHaveBeenCalledWith(
      '/inbound/slips/1',
      expect.objectContaining({
        signal: expect.any(AbortSignal),
      }),
    )
    expect(result.slip.value).toEqual(mockSlip)
  })

  it('fetchDetail で404の場合にリダイレクトする', async () => {
    vi.mocked(apiClient.get).mockRejectedValue(createAxiosError(404))

    const { result } = withSetup(() => useInboundSlipDetail())
    await result.fetchDetail()

    expect(ElMessage.error).toHaveBeenCalled()
    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'inbound-slip-list' })
  })

  it('キャンセル時に state が更新されない', async () => {
    const { result } = withSetup(() => useInboundSlipDetail())
    await result.fetchDetail()
    expect(result.slip.value).toEqual(mockSlip)

    const cancelError = new Error('canceled')
    vi.mocked(apiClient.get).mockRejectedValueOnce(cancelError)
    vi.mocked(axios.isCancel).mockReturnValueOnce(true)

    await result.fetchDetail()
    expect(result.slip.value).toEqual(mockSlip)
  })

  it('handleConfirm が確認後にPOSTリクエストを送信する', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse({}))

    const { result } = withSetup(() => useInboundSlipDetail())
    await result.fetchDetail()
    await result.handleConfirm()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(apiClient.post).toHaveBeenCalledWith('/inbound/slips/1/confirm')
    expect(ElMessage.success).toHaveBeenCalled()
  })

  it('handleCancel が確認後にPOSTリクエストを送信する', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse({}))

    const { result } = withSetup(() => useInboundSlipDetail())
    await result.fetchDetail()
    await result.handleCancel()

    expect(apiClient.post).toHaveBeenCalledWith('/inbound/slips/1/cancel')
    expect(ElMessage.success).toHaveBeenCalled()
  })

  it('handleCancel で409 INBOUND_ALREADY_STORED エラーを処理する', async () => {
    vi.mocked(apiClient.post).mockRejectedValue(
      createAxiosError(409, { errorCode: 'INBOUND_ALREADY_STORED' }),
    )

    const { result } = withSetup(() => useInboundSlipDetail())
    await result.fetchDetail()
    await result.handleCancel()

    expect(ElMessage.error).toHaveBeenCalledWith('inbound.slip.cancelForbidden')
  })

  it('canConfirm が PLANNED ステータスで true', async () => {
    const { result } = withSetup(() => useInboundSlipDetail())
    await result.fetchDetail()

    expect(result.canConfirm.value).toBe(true)
  })

  it('canConfirm が VIEWER ロールで false', async () => {
    const { result } = withSetup(() => {
      const auth = useAuthStore()
      auth.user = {
        userId: 1,
        userCode: 'v1',
        fullName: 'Viewer',
        role: 'VIEWER',
        passwordChangeRequired: false,
      }
      return useInboundSlipDetail()
    })
    await result.fetchDetail()

    expect(result.canConfirm.value).toBe(false)
  })

  it('canCancel が STORED/CANCELLED ステータスで false', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse({ ...mockSlip, status: 'STORED' }))

    const { result } = withSetup(() => useInboundSlipDetail())
    await result.fetchDetail()

    expect(result.canCancel.value).toBe(false)
  })

  it('goBack が一覧画面に遷移する', () => {
    const { result } = withSetup(() => useInboundSlipDetail())
    result.goBack()
    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'inbound-slip-list' })
  })

  it('goInspect が検品画面に遷移する', () => {
    const { result } = withSetup(() => useInboundSlipDetail())
    result.goInspect()
    expect(mockRouter.push).toHaveBeenCalledWith({
      name: 'inbound-slip-inspect',
      params: { id: 1 },
    })
  })

  it('goStore が格納画面に遷移する', () => {
    const { result } = withSetup(() => useInboundSlipDetail())
    result.goStore()
    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'inbound-slip-store', params: { id: 1 } })
  })
})
