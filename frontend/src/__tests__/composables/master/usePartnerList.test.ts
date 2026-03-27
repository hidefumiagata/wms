import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, createAxiosError, flushPromises } from '../../helpers'
import { usePartnerList } from '@/composables/master/usePartnerList'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'

vi.mock('@/api/generated/models/partner-detail', () => ({}))
vi.mock('@/api/generated/models/partner-page-response', () => ({}))
vi.mock('@/api/generated/models/partner-type', () => ({}))

describe('usePartnerList', () => {
  const createMockListResponse = () => ({
    content: [
      {
        id: 1,
        partnerCode: 'PTN001',
        partnerName: 'テスト取引先',
        partnerType: 'SUPPLIER',
        isActive: true,
        version: 1,
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  })

  beforeEach(() => {
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createMockListResponse()))
  })

  // --- fetchList 基本動作 ---

  it('fetchList がデータを取得し、items と total を更新する', async () => {
    const { result } = withSetup(() => usePartnerList())

    await result.fetchList()

    expect(apiClient.get).toHaveBeenCalledWith(
      '/master/partners',
      expect.objectContaining({
        params: expect.objectContaining({ page: 0, size: 20 }),
      }),
    )
    expect(result.items.value).toEqual(createMockListResponse().content)
    expect(result.total.value).toBe(1)
    expect(result.loading.value).toBe(false)
  })

  it('fetchList が signal を axios に渡す（AbortController対応）', async () => {
    const { result } = withSetup(() => usePartnerList())

    await result.fetchList()

    const callArgs = vi.mocked(apiClient.get).mock.calls[0]
    expect(callArgs[1]).toHaveProperty('signal')
    expect(callArgs[1]!.signal).toBeInstanceOf(AbortSignal)
  })

  // --- AbortController 動作 ---

  it('fetchList を連続呼び出しすると前のリクエストのsignalがabortされる', async () => {
    const { result } = withSetup(() => usePartnerList())

    await result.fetchList()
    const firstSignal = vi.mocked(apiClient.get).mock.calls[0][1]!.signal!

    await result.fetchList()

    expect(firstSignal.aborted).toBe(true)
  })

  it('onUnmounted 時に進行中のリクエストがキャンセルされる', async () => {
    const { result, wrapper } = withSetup(() => usePartnerList())

    const fetchPromise = result.fetchList()

    const signal = vi.mocked(apiClient.get).mock.calls[0][1]!.signal!
    expect(signal.aborted).toBe(false)

    wrapper.unmount()

    expect(signal.aborted).toBe(true)

    await fetchPromise
  })

  it('キャンセル時に state（items, total）が更新されない', async () => {
    const { result } = withSetup(() => usePartnerList())

    await result.fetchList()
    expect(result.items.value).toHaveLength(1)

    const cancelError = new Error('canceled')
    vi.mocked(apiClient.get).mockRejectedValueOnce(cancelError)
    vi.mocked(axios.isCancel).mockReturnValueOnce(true)

    await result.fetchList()

    expect(result.items.value).toHaveLength(1)
  })

  it('キャンセル時に loading が false にならない（新しいリクエストが管理）', async () => {
    const { result } = withSetup(() => usePartnerList())

    let resolveFirst!: (value: unknown) => void
    vi.mocked(apiClient.get).mockImplementationOnce(() => {
      return new Promise((resolve) => {
        resolveFirst = resolve
      })
    })

    const firstFetch = result.fetchList()
    expect(result.loading.value).toBe(true)

    vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse(createMockListResponse()))
    const secondFetch = result.fetchList()

    vi.mocked(axios.isCancel).mockReturnValueOnce(true)
    resolveFirst(Promise.reject(new Error('canceled')))

    await Promise.allSettled([firstFetch, secondFetch])

    expect(result.loading.value).toBe(false)
  })

  // --- 操作メソッド ---

  it('handleSearch がページを1にリセットしてfetchListを呼ぶ', async () => {
    const { result } = withSetup(() => usePartnerList())

    result.page.value = 3
    result.handleSearch()
    await flushPromises()

    expect(result.page.value).toBe(1)
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('handleReset が検索条件をクリアしてfetchListを呼ぶ', async () => {
    const { result } = withSetup(() => usePartnerList())

    result.searchForm.partnerCode = 'PTN001'
    result.searchForm.partnerName = 'テスト'
    result.searchForm.partnerType = 'SUPPLIER' as never
    result.searchForm.isActive = true
    result.page.value = 5

    result.handleReset()
    await flushPromises()

    expect(result.searchForm.partnerCode).toBe('')
    expect(result.searchForm.partnerName).toBe('')
    expect(result.searchForm.partnerType).toBeNull()
    expect(result.searchForm.isActive).toBeNull()
    expect(result.page.value).toBe(1)
  })

  it('handlePageChange がページ番号を更新してfetchListを呼ぶ', async () => {
    const { result } = withSetup(() => usePartnerList())

    result.handlePageChange(5)
    await flushPromises()

    expect(result.page.value).toBe(5)
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('handleSizeChange がページサイズを更新し、ページを1にリセットする', async () => {
    const { result } = withSetup(() => usePartnerList())

    result.page.value = 3
    result.handleSizeChange(50)
    await flushPromises()

    expect(result.pageSize.value).toBe(50)
    expect(result.page.value).toBe(1)
  })

  it('検索条件がparamsに反映される', async () => {
    const { result } = withSetup(() => usePartnerList())

    result.searchForm.partnerCode = 'PTN001'
    result.searchForm.partnerName = 'テスト'
    result.searchForm.partnerType = 'SUPPLIER' as never
    result.searchForm.isActive = true

    await result.fetchList()

    expect(apiClient.get).toHaveBeenCalledWith(
      '/master/partners',
      expect.objectContaining({
        params: expect.objectContaining({
          partnerCode: 'PTN001',
          partnerName: 'テスト',
          partnerType: 'SUPPLIER',
          isActive: true,
        }),
      }),
    )
  })

  // --- エラーハンドリング ---

  it('APIエラー時にitemsとtotalがリセットされる', async () => {
    const { result } = withSetup(() => usePartnerList())

    await result.fetchList()
    expect(result.items.value).toHaveLength(1)

    vi.mocked(apiClient.get).mockRejectedValueOnce(createAxiosError(500))

    await result.fetchList()

    expect(result.items.value).toHaveLength(0)
    expect(result.total.value).toBe(0)
  })

  it('ネットワークエラー時にエラーメッセージが表示される', async () => {
    const { result } = withSetup(() => usePartnerList())

    vi.mocked(apiClient.get).mockRejectedValueOnce(new Error('Network Error'))

    await result.fetchList()

    expect(ElMessage.error).toHaveBeenCalled()
  })

  it('サーバーエラー時にfetchErrorメッセージが表示される', async () => {
    const { result } = withSetup(() => usePartnerList())

    vi.mocked(apiClient.get).mockRejectedValueOnce(createAxiosError(500))

    await result.fetchList()

    expect(ElMessage.error).toHaveBeenCalledWith('master.partner.fetchError')
  })

  // --- handleToggleActive ---

  it('handleToggleActive が確認後にPATCH APIを呼ぶ', async () => {
    vi.mocked(apiClient.patch).mockResolvedValueOnce(mockAxiosResponse({}))

    const { result } = withSetup(() => usePartnerList())
    const row = {
      id: 1,
      partnerCode: 'PTN001',
      partnerName: 'テスト取引先',
      partnerType: 'SUPPLIER',
      isActive: true,
      version: 1,
    }

    await result.handleToggleActive(row as never)

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(apiClient.patch).toHaveBeenCalledWith('/master/partners/1/toggle-active', {
      isActive: false,
      version: 1,
    })
    expect(ElMessage.success).toHaveBeenCalled()
  })

  it('handleToggleActive で確認キャンセル時はAPIを呼ばない', async () => {
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')

    const { result } = withSetup(() => usePartnerList())
    const row = {
      id: 1,
      partnerCode: 'PTN001',
      partnerName: 'テスト取引先',
      partnerType: 'SUPPLIER',
      isActive: true,
      version: 1,
    }

    await result.handleToggleActive(row as never)

    expect(apiClient.patch).not.toHaveBeenCalled()
  })

  it('handleToggleActive の409エラーで楽観的ロックエラーが表示される', async () => {
    vi.mocked(apiClient.patch).mockRejectedValueOnce(createAxiosError(409))

    const { result } = withSetup(() => usePartnerList())
    const row = {
      id: 1,
      partnerCode: 'PTN001',
      partnerName: 'テスト取引先',
      partnerType: 'SUPPLIER',
      isActive: true,
      version: 1,
    }

    await result.handleToggleActive(row as never)

    expect(ElMessage.error).toHaveBeenCalledWith('error.optimisticLock')
  })

  it('handleToggleActive の422エラーでエラーメッセージが表示される', async () => {
    vi.mocked(apiClient.patch).mockRejectedValueOnce(
      createAxiosError(422, { errorCode: 'CANNOT_DEACTIVATE_HAS_ACTIVE_OUTBOUND' }),
    )

    const { result } = withSetup(() => usePartnerList())
    const row = {
      id: 1,
      partnerCode: 'PTN001',
      partnerName: 'テスト取引先',
      partnerType: 'SUPPLIER',
      isActive: true,
      version: 1,
    }

    await result.handleToggleActive(row as never)

    expect(ElMessage.error).toHaveBeenCalledWith('master.partner.cannotDeactivateHasActiveOutbound')
  })

  it('handleToggleActive の422エラー（入荷）でエラーメッセージが表示される', async () => {
    vi.mocked(apiClient.patch).mockRejectedValueOnce(createAxiosError(422, { errorCode: 'OTHER' }))

    const { result } = withSetup(() => usePartnerList())
    const row = {
      id: 1,
      partnerCode: 'PTN001',
      partnerName: 'テスト取引先',
      partnerType: 'SUPPLIER',
      isActive: true,
      version: 1,
    }

    await result.handleToggleActive(row as never)

    expect(ElMessage.error).toHaveBeenCalledWith('master.partner.cannotDeactivateHasActiveInbound')
  })
})
