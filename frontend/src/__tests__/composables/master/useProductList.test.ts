import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, createAxiosError, flushPromises } from '../../helpers'
import { useProductList } from '@/composables/master/useProductList'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'

// generated model のモック
vi.mock('@/api/generated/models/product-detail', () => ({}))
vi.mock('@/api/generated/models/product-page-response', () => ({}))
vi.mock('@/api/generated/models/storage-condition', () => ({
  StorageCondition: {
    Ambient: 'AMBIENT',
    Refrigerated: 'REFRIGERATED',
    Frozen: 'FROZEN',
  },
}))

describe('useProductList', () => {
  const createMockResponse = () => ({
    content: [
      { id: 1, productCode: 'PRD001', productName: 'テスト商品', isActive: true, version: 1 },
    ],
    totalElements: 1,
    totalPages: 1,
  })

  beforeEach(() => {
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createMockResponse()))
  })

  // --- fetchList ---

  it('fetchList がデータを取得する', async () => {
    const { result } = withSetup(() => useProductList())

    await result.fetchList()

    expect(result.items.value).toEqual(createMockResponse().content)
    expect(result.total.value).toBe(1)
    expect(result.loading.value).toBe(false)
  })

  it('fetchList が signal を渡す（AbortController対応）', async () => {
    const { result } = withSetup(() => useProductList())

    await result.fetchList()

    const callArgs = vi.mocked(apiClient.get).mock.calls[0]
    expect(callArgs[1]).toHaveProperty('signal')
    expect(callArgs[1]!.signal).toBeInstanceOf(AbortSignal)
  })

  it('連続呼び出しで前のリクエストのsignalがabortされる', async () => {
    const { result } = withSetup(() => useProductList())

    await result.fetchList()
    const firstSignal = vi.mocked(apiClient.get).mock.calls[0][1]!.signal!

    await result.fetchList()

    expect(firstSignal.aborted).toBe(true)
  })

  it('onUnmounted 時にリクエストがキャンセルされる', async () => {
    const { result, wrapper } = withSetup(() => useProductList())

    const fetchPromise = result.fetchList()
    const signal = vi.mocked(apiClient.get).mock.calls[0][1]!.signal!
    expect(signal.aborted).toBe(false)

    wrapper.unmount()

    expect(signal.aborted).toBe(true)

    await fetchPromise
  })

  it('キャンセル時に state が更新されない', async () => {
    const { result } = withSetup(() => useProductList())

    await result.fetchList()
    expect(result.items.value).toHaveLength(1)

    const cancelError = new Error('canceled')
    vi.mocked(apiClient.get).mockRejectedValueOnce(cancelError)
    vi.mocked(axios.isCancel).mockReturnValueOnce(true)

    await result.fetchList()

    expect(result.items.value).toHaveLength(1)
  })

  // --- 操作メソッド ---

  it('デフォルトの検索条件で isActive が true', () => {
    const { result } = withSetup(() => useProductList())
    expect(result.searchForm.isActive).toBe(true)
  })

  it('handleReset で isActive がデフォルト(true)に戻る', async () => {
    const { result } = withSetup(() => useProductList())

    result.searchForm.isActive = false
    result.handleReset()
    await flushPromises()

    expect(result.searchForm.isActive).toBe(true)
  })

  it('handleSearch がページを1にリセットする', async () => {
    const { result } = withSetup(() => useProductList())

    result.page.value = 3
    result.handleSearch()
    await flushPromises()

    expect(result.page.value).toBe(1)
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('storageConditionLabel が正しいラベルを返す', () => {
    const { result } = withSetup(() => useProductList())

    expect(result.storageConditionLabel('AMBIENT')).toBe('master.product.storageAmbient')
    expect(result.storageConditionLabel('REFRIGERATED')).toBe('master.product.storageRefrigerated')
    expect(result.storageConditionLabel('FROZEN')).toBe('master.product.storageFrozen')
    expect(result.storageConditionLabel('UNKNOWN')).toBe('UNKNOWN')
  })

  // --- handleToggleActive ---

  it('handleToggleActive が確認後にPATCH APIを呼ぶ', async () => {
    vi.mocked(apiClient.patch).mockResolvedValueOnce(mockAxiosResponse({}))

    const { result } = withSetup(() => useProductList())
    const row = { id: 1, productCode: 'PRD001', productName: 'テスト', isActive: true, version: 1 } as never

    await result.handleToggleActive(row)

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(apiClient.patch).toHaveBeenCalledWith('/master/products/1/toggle-active', {
      isActive: false,
      version: 1,
    })
    expect(ElMessage.success).toHaveBeenCalled()
  })

  it('handleToggleActive で確認キャンセル時はAPIを呼ばない', async () => {
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')

    const { result } = withSetup(() => useProductList())
    const row = { id: 1, productCode: 'PRD001', productName: 'テスト', isActive: true, version: 1 } as never

    await result.handleToggleActive(row)

    expect(apiClient.patch).not.toHaveBeenCalled()
  })

  it('handleToggleActive の409エラーで楽観的ロックエラーが表示される', async () => {
    vi.mocked(apiClient.patch).mockRejectedValueOnce(createAxiosError(409))

    const { result } = withSetup(() => useProductList())
    const row = { id: 1, productCode: 'PRD001', productName: 'テスト', isActive: true, version: 1 } as never

    await result.handleToggleActive(row)

    expect(ElMessage.error).toHaveBeenCalledWith('error.optimisticLock')
  })

  it('handleToggleActive の422エラー（在庫あり）でエラーが表示される', async () => {
    vi.mocked(apiClient.patch).mockRejectedValueOnce(
      createAxiosError(422, { errorCode: 'CANNOT_DEACTIVATE_HAS_INVENTORY' })
    )

    const { result } = withSetup(() => useProductList())
    const row = { id: 1, productCode: 'PRD001', productName: 'テスト', isActive: true, version: 1 } as never

    await result.handleToggleActive(row)

    expect(ElMessage.error).toHaveBeenCalledWith('master.product.cannotDeactivateHasInventory')
  })

  // --- エラーハンドリング ---

  it('サーバーエラー時にfetchErrorメッセージが表示される', async () => {
    const { result } = withSetup(() => useProductList())

    vi.mocked(apiClient.get).mockRejectedValueOnce(createAxiosError(500))

    await result.fetchList()

    expect(ElMessage.error).toHaveBeenCalledWith('master.product.fetchError')
  })

  it('ネットワークエラー時にnetworkエラーメッセージが表示される', async () => {
    const { result } = withSetup(() => useProductList())

    vi.mocked(apiClient.get).mockRejectedValueOnce(new Error('Network Error'))

    await result.fetchList()

    expect(ElMessage.error).toHaveBeenCalledWith('error.network')
  })
})
