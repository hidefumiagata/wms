import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { withSetup, mockAxiosResponse, flushPromises } from '../../helpers'
import { useInventoryCorrection } from '@/composables/inventory/useInventoryCorrection'
import { useWarehouseStore } from '@/stores/warehouse'
import { mockRouter } from '../../setup'
import axios from 'axios'

vi.mock('@/api/generated/models/inventory-location-item', () => ({}))
vi.mock('@/utils/inventoryFormatters', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/utils/inventoryFormatters')>()
  return { ...actual }
})

describe('useInventoryCorrection', () => {
  const mockLocationRes = { content: [{ id: 100 }], totalElements: 1 }
  const mockInventoryRes = {
    content: [
      {
        productId: 1,
        productCode: 'P001',
        productName: 'Product 1',
        unitType: 'CASE',
        quantity: 10,
        allocatedQty: 3,
        availableQty: 7,
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

  it('fetchInventory がロケーション検索+在庫を取得する', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryCorrection()
    })

    result.locationCode.value = 'A-01'
    await result.fetchInventory()

    expect(result.inventoryOptions.value).toHaveLength(1)
    expect(result.locationId.value).toBe(100)
    expect(result.selectedProductId.value).toBeNull()
    expect(result.newQty.value).toBeNull()
    expect(result.reason.value).toBe('')
  })

  it('diff が自動計算される', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryCorrection()
    })

    result.locationCode.value = 'A-01'
    await result.fetchInventory()

    result.selectedProductId.value = 1
    result.selectedUnitType.value = 'CASE'
    result.newQty.value = 15

    expect(result.diff.value).toBe(5) // 15 - 10
  })

  it('submitCorrection が数量<0でエラーする', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryCorrection()
    })

    result.locationCode.value = 'A-01'
    await result.fetchInventory()

    result.selectedProductId.value = 1
    result.selectedUnitType.value = 'CASE'
    result.locationId.value = 100
    result.newQty.value = -1
    result.reason.value = 'test reason'

    await result.submitCorrection()

    expect(ElMessage.error).toHaveBeenCalled()
    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('submitCorrection が引当数未満でエラーする', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryCorrection()
    })

    result.locationCode.value = 'A-01'
    await result.fetchInventory()

    result.selectedProductId.value = 1
    result.selectedUnitType.value = 'CASE'
    result.locationId.value = 100
    result.newQty.value = 2 // below allocatedQty=3
    result.reason.value = 'test reason'

    await result.submitCorrection()

    expect(ElMessage.error).toHaveBeenCalledWith(
      expect.stringContaining('inventory.correctionBelowAllocated'),
    )
    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('submitCorrection が理由未入力でエラーする', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryCorrection()
    })

    result.locationCode.value = 'A-01'
    await result.fetchInventory()

    result.selectedProductId.value = 1
    result.selectedUnitType.value = 'CASE'
    result.locationId.value = 100
    result.newQty.value = 8
    result.reason.value = ''

    await result.submitCorrection()

    expect(ElMessage.error).toHaveBeenCalled()
    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('submitCorrection が正常にPOSTする', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse({}))

    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryCorrection()
    })

    result.locationCode.value = 'A-01'
    await result.fetchInventory()

    result.selectedProductId.value = 1
    result.selectedUnitType.value = 'CASE'
    result.locationId.value = 100
    result.newQty.value = 8
    result.reason.value = 'Counted mismatch'

    await result.submitCorrection()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(apiClient.post).toHaveBeenCalledWith(
      '/inventory/correction',
      expect.objectContaining({
        locationId: 100,
        productId: 1,
        unitType: 'CASE',
        newQty: 8,
        reason: 'Counted mismatch',
      }),
    )
    expect(ElMessage.success).toHaveBeenCalled()
    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'inventory-list' })
  })

  it('onProductChange が selectedUnitType と newQty をリセットする', () => {
    const { result } = withSetup(() => useInventoryCorrection())
    result.selectedUnitType.value = 'CASE'
    result.newQty.value = 10

    result.onProductChange()
    expect(result.selectedUnitType.value).toBeNull()
    expect(result.newQty.value).toBeNull()
  })

  it('onUnitTypeChange が現在数量を初期値に設定する', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryCorrection()
    })

    result.locationCode.value = 'A-01'
    await result.fetchInventory()

    result.selectedProductId.value = 1
    result.selectedUnitType.value = 'CASE'
    result.onUnitTypeChange()

    expect(result.newQty.value).toBe(10) // current quantity
  })

  it('goBack が在庫一覧に遷移する', () => {
    const { result } = withSetup(() => useInventoryCorrection())
    result.goBack()
    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'inventory-list' })
  })
})
