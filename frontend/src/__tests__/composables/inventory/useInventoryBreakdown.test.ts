import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { withSetup, mockAxiosResponse, flushPromises } from '../../helpers'
import { useInventoryBreakdown } from '@/composables/inventory/useInventoryBreakdown'
import { useWarehouseStore } from '@/stores/warehouse'
import { mockRouter } from '../../setup'
import axios from 'axios'

vi.mock('@/api/generated/models/inventory-location-item', () => ({}))
vi.mock('@/utils/inventoryFormatters', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/utils/inventoryFormatters')>()
  return { ...actual }
})

describe('useInventoryBreakdown', () => {
  const mockLocationRes = { content: [{ id: 100 }], totalElements: 1 }
  const mockInventoryRes = {
    content: [
      {
        productId: 1,
        productCode: 'P001',
        productName: 'Product 1',
        unitType: 'CASE',
        quantity: 10,
        allocatedQty: 2,
        availableQty: 8,
      },
      {
        productId: 1,
        productCode: 'P001',
        productName: 'Product 1',
        unitType: 'BALL',
        quantity: 5,
        allocatedQty: 0,
        availableQty: 5,
      },
    ],
  }

  beforeEach(() => {
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce(mockAxiosResponse(mockLocationRes))
      .mockResolvedValueOnce(mockAxiosResponse(mockInventoryRes))
  })

  it('fetchFromInventory がロケーション検索+在庫を取得する', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryBreakdown()
    })

    result.fromLocationCode.value = 'A-01'
    await result.fetchFromInventory()

    expect(result.fromInventoryOptions.value).toHaveLength(2)
    expect(result.fromLocationId.value).toBe(100)
    expect(result.selectedProductId.value).toBeNull()
  })

  it('fetchFromInventory が空ロケーションで警告する', async () => {
    vi.mocked(apiClient.get).mockReset()
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse({ content: [], totalElements: 0 }))

    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryBreakdown()
    })

    result.fromLocationCode.value = 'NOTEXIST'
    await result.fetchFromInventory()

    expect(ElMessage.warning).toHaveBeenCalled()
    expect(result.fromInventoryOptions.value).toHaveLength(0)
  })

  it('onProductChange が商品マスタから変換レートを取得する', async () => {
    vi.mocked(apiClient.get).mockReset()
    vi.mocked(apiClient.get).mockResolvedValue(
      mockAxiosResponse({
        id: 1,
        productCode: 'P001',
        productName: 'Product 1',
        caseQuantity: 12,
        ballQuantity: 6,
      }),
    )

    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryBreakdown()
    })

    result.selectedProductId.value = 1
    await result.onProductChange()

    expect(result.productInfo.value).toBeTruthy()
    expect(result.productInfo.value!.caseQuantity).toBe(12)
    expect(result.productInfo.value!.ballQuantity).toBe(6)
  })

  it('productOptions が重複除去した商品リストを返す', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryBreakdown()
    })

    result.fromLocationCode.value = 'A-01'
    await result.fetchFromInventory()

    expect(result.productOptions.value).toHaveLength(1) // productId=1 deduplicated
  })

  it('conversionRate が正しく計算される', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryBreakdown()
    })

    result.productInfo.value = {
      id: 1,
      productCode: 'P001',
      productName: 'P1',
      caseQuantity: 12,
      ballQuantity: 6,
    }
    result.fromUnitType.value = 'CASE'
    result.toUnitType.value = 'BALL'
    expect(result.conversionRate.value).toBe(12)

    result.toUnitType.value = 'PIECE'
    expect(result.conversionRate.value).toBe(72) // 12 * 6

    result.fromUnitType.value = 'BALL'
    result.toUnitType.value = 'PIECE'
    expect(result.conversionRate.value).toBe(6)
  })

  it('convertedQty が正しく計算される', () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryBreakdown()
    })

    result.productInfo.value = {
      id: 1,
      productCode: 'P001',
      productName: 'P1',
      caseQuantity: 12,
      ballQuantity: 6,
    }
    result.fromUnitType.value = 'CASE'
    result.toUnitType.value = 'BALL'
    result.breakdownQty.value = 3

    expect(result.convertedQty.value).toBe(36) // 3 * 12
  })

  it('submitBreakdown が POST リクエストを送信する', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse({}))

    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryBreakdown()
    })

    result.fromLocationCode.value = 'A-01'
    await result.fetchFromInventory()

    result.selectedProductId.value = 1
    result.fromUnitType.value = 'CASE'
    result.toUnitType.value = 'BALL'
    result.breakdownQty.value = 2
    result.fromLocationId.value = 100
    result.productInfo.value = {
      id: 1,
      productCode: 'P001',
      productName: 'P1',
      caseQuantity: 12,
      ballQuantity: 6,
    }

    await result.submitBreakdown()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(apiClient.post).toHaveBeenCalledWith(
      '/inventory/breakdown',
      expect.objectContaining({
        fromLocationId: 100,
        productId: 1,
        fromUnitType: 'CASE',
        breakdownQty: 2,
        toUnitType: 'BALL',
      }),
    )
    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'inventory-list' })
  })

  it('goBack が在庫一覧に遷移する', () => {
    const { result } = withSetup(() => useInventoryBreakdown())
    result.goBack()
    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'inventory-list' })
  })
})
