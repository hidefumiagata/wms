import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, flushPromises } from '../../helpers'
import { useInventoryList } from '@/composables/inventory/useInventoryList'
import { useWarehouseStore } from '@/stores/warehouse'
import { useAuthStore } from '@/stores/auth'
import axios from 'axios'

vi.mock('@/api/generated/models/inventory-location-item', () => ({}))
vi.mock('@/api/generated/models/inventory-location-page-response', () => ({}))
vi.mock('@/api/generated/models/inventory-product-summary-item', () => ({}))
vi.mock('@/api/generated/models/inventory-product-summary-page-response', () => ({}))

describe('useInventoryList', () => {
  const createLocationResponse = () => ({
    content: [
      {
        id: 1,
        locationCode: 'A-01',
        productId: 1,
        productCode: 'P001',
        productName: 'Product 1',
        unitType: 'CASE',
        quantity: 10,
        allocatedQty: 2,
        availableQty: 8,
      },
    ],
    totalElements: 1,
    totalPages: 1,
  })

  const createProductSummaryResponse = () => ({
    content: [{ productId: 1, productCode: 'P001', productName: 'Product 1', totalQuantity: 100 }],
    totalElements: 1,
    totalPages: 1,
  })

  beforeEach(() => {
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createLocationResponse()))
  })

  it('fetchList が LOCATION viewType で在庫を取得する', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryList()
    })

    await result.fetchList()

    expect(apiClient.get).toHaveBeenCalledWith(
      '/inventory',
      expect.objectContaining({
        params: expect.objectContaining({ warehouseId: 1, viewType: 'LOCATION' }),
        signal: expect.any(AbortSignal),
      }),
    )
    expect(result.locationItems.value).toHaveLength(1)
    expect(result.productSummaryItems.value).toHaveLength(0)
  })

  it('fetchList が PRODUCT_SUMMARY viewType で商品サマリを取得する', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createProductSummaryResponse()))

    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryList()
    })

    result.viewType.value = 'PRODUCT_SUMMARY'
    await result.fetchList()

    expect(apiClient.get).toHaveBeenCalledWith(
      '/inventory',
      expect.objectContaining({
        params: expect.objectContaining({ viewType: 'PRODUCT_SUMMARY', sort: 'productCode,asc' }),
      }),
    )
    expect(result.productSummaryItems.value).toHaveLength(1)
    expect(result.locationItems.value).toHaveLength(0)
  })

  it('handleViewTypeChange がビュータイプを変更してページを1にリセットする', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryList()
    })

    result.page.value = 3
    result.handleViewTypeChange('PRODUCT_SUMMARY')
    await flushPromises()

    expect(result.viewType.value).toBe('PRODUCT_SUMMARY')
    expect(result.page.value).toBe(1)
  })

  it('fetchProductOptions が商品オプションを取得する', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      mockAxiosResponse({
        content: [{ id: 1, productCode: 'P001', productName: 'Product 1' }],
      }),
    )

    const { result } = withSetup(() => useInventoryList())
    await result.fetchProductOptions()

    expect(result.productOptions.value).toHaveLength(1)
    expect(result.productOptions.value[0].productCode).toBe('P001')
  })

  it('handleSearch がページを1にリセットする', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryList()
    })

    result.page.value = 5
    result.handleSearch()
    await flushPromises()

    expect(result.page.value).toBe(1)
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('handleReset が検索条件をクリアする', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryList()
    })

    result.searchForm.locationCodePrefix = 'A-01'
    result.searchForm.productId = 5
    result.page.value = 3

    result.handleReset()
    await flushPromises()

    expect(result.searchForm.locationCodePrefix).toBe('')
    expect(result.searchForm.productId).toBeNull()
    expect(result.page.value).toBe(1)
  })

  it('onUnmounted 時にリクエストがキャンセルされる', async () => {
    const { result, wrapper } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryList()
    })

    const fetchPromise = result.fetchList()
    const signal = vi.mocked(apiClient.get).mock.calls[0][1]!.signal!
    expect(signal.aborted).toBe(false)

    wrapper.unmount()
    expect(signal.aborted).toBe(true)

    await fetchPromise
  })

  it('キャンセル時に state が更新されない', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryList()
    })

    await result.fetchList()
    expect(result.locationItems.value).toHaveLength(1)

    const cancelError = new Error('canceled')
    vi.mocked(apiClient.get).mockRejectedValueOnce(cancelError)
    vi.mocked(axios.isCancel).mockReturnValueOnce(true)

    await result.fetchList()
    expect(result.locationItems.value).toHaveLength(1)
  })
})
