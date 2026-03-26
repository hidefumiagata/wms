import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { withSetup, mockAxiosResponse } from '../../helpers'
import { useInventoryMove } from '@/composables/inventory/useInventoryMove'
import { useWarehouseStore } from '@/stores/warehouse'
import { mockRouter } from '../../setup'

vi.mock('@/api/generated/models/inventory-location-item', () => ({}))
vi.mock('@/utils/inventoryFormatters', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/utils/inventoryFormatters')>()
  return { ...actual }
})

describe('useInventoryMove', () => {
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
        lotNumber: null,
        expiryDate: null,
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
      return useInventoryMove()
    })

    result.fromLocationCode.value = 'A-01'
    await result.fetchFromInventory()

    expect(result.fromInventoryOptions.value).toHaveLength(1)
    expect(result.selectedProductId.value).toBeNull()
    expect(result.selectedUnitType.value).toBeNull()
  })

  it('fetchToLocationInfo が移動先ロケーション情報を取得する', async () => {
    vi.mocked(apiClient.get).mockReset()
    const toLocRes = mockAxiosResponse({ content: [{ id: 200 }], totalElements: 1 })
    const toInvRes = mockAxiosResponse({ content: [{ quantity: 3 }] })
    vi.mocked(apiClient.get).mockResolvedValueOnce(toLocRes).mockResolvedValueOnce(toInvRes)

    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryMove()
    })

    result.toLocationCode.value = 'B-01'
    result.selectedProductId.value = 1
    result.selectedUnitType.value = 'CASE'
    await result.fetchToLocationInfo()

    expect(result.toLocationId.value).toBe(200)
    expect(result.toCurrentQty.value).toBe(3)
  })

  it('fetchToLocationInfo が空コードでリセットする', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryMove()
    })

    result.toLocationCode.value = ''
    await result.fetchToLocationInfo()

    expect(result.toLocationId.value).toBeNull()
    expect(result.toCurrentQty.value).toBeNull()
  })

  it('submitMove が同一ロケーションでエラーする', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryMove()
    })

    result.fromLocationCode.value = 'A-01'
    await result.fetchFromInventory()

    result.selectedProductId.value = 1
    result.selectedUnitType.value = 'CASE'
    result.toLocationCode.value = 'A-01'
    result.toLocationId.value = 200
    result.moveQty.value = 5

    await result.submitMove()

    expect(ElMessage.error).toHaveBeenCalledWith('inventory.sameLocationError')
    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('submitMove が数量超過でエラーする', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryMove()
    })

    result.fromLocationCode.value = 'A-01'
    await result.fetchFromInventory()

    result.selectedProductId.value = 1
    result.selectedUnitType.value = 'CASE'
    result.toLocationCode.value = 'B-01'
    result.toLocationId.value = 200
    result.moveQty.value = 999 // exceeds availableQty=8

    await result.submitMove()

    expect(ElMessage.error).toHaveBeenCalledWith('inventory.moveQtyExceedsAvailable')
    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('submitMove が正常にPOSTする', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse({}))

    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryMove()
    })

    result.fromLocationCode.value = 'A-01'
    await result.fetchFromInventory()

    result.selectedProductId.value = 1
    result.selectedUnitType.value = 'CASE'
    result.toLocationCode.value = 'B-01'
    result.toLocationId.value = 200
    result.moveQty.value = 3

    await result.submitMove()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(apiClient.post).toHaveBeenCalledWith(
      '/inventory/move',
      expect.objectContaining({
        fromLocationId: 100,
        productId: 1,
        unitType: 'CASE',
        toLocationId: 200,
        moveQty: 3,
      }),
    )
    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'inventory-list' })
  })

  it('onProductChange が selectedUnitType をリセットする', () => {
    const { result } = withSetup(() => useInventoryMove())
    result.selectedUnitType.value = 'CASE'

    result.onProductChange()
    expect(result.selectedUnitType.value).toBeNull()
  })

  it('goBack が在庫一覧に遷移する', () => {
    const { result } = withSetup(() => useInventoryMove())
    result.goBack()
    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'inventory-list' })
  })
})
