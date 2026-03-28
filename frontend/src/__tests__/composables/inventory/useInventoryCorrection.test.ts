import { ref } from 'vue'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { withSetup, mockAxiosResponse } from '../../helpers'
import { useInventoryCorrection } from '@/composables/inventory/useInventoryCorrection'
import { useWarehouseStore } from '@/stores/warehouse'
import { mockRouter } from '../../setup'

vi.mock('@/api/generated/models/inventory-location-item', () => ({}))
vi.mock('@/api/generated/models/correction-history-item', () => ({}))
vi.mock('@/utils/inventoryFormatters', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/utils/inventoryFormatters')>()
  return { ...actual }
})

function createMockFormRef(valid = true) {
  return ref({
    validate: vi.fn().mockResolvedValue(valid),
    resetFields: vi.fn(),
    clearValidate: vi.fn(),
  } as unknown as FormInstance)
}

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
    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryCorrection(formRef)
    })

    result.form.locationCode = 'A-01'
    await result.fetchInventory()

    expect(result.inventoryOptions.value).toHaveLength(1)
    expect(result.locationId.value).toBe(100)
    expect(result.form.selectedProductId).toBeNull()
    expect(result.form.newQty).toBeNull()
    expect(result.form.reason).toBe('')
  })

  it('diff が自動計算される', async () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryCorrection(formRef)
    })

    result.form.locationCode = 'A-01'
    await result.fetchInventory()

    result.form.selectedProductId = 1
    result.form.selectedUnitType = 'CASE'
    result.form.newQty = 15

    expect(result.diff.value).toBe(5) // 15 - 10
  })

  it('submitCorrection がフォームバリデーション失敗時に中断する', async () => {
    const formRef = createMockFormRef()
    formRef.value!.validate = vi.fn().mockRejectedValue(false)

    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryCorrection(formRef)
    })

    await result.submitCorrection()

    expect(apiClient.post).not.toHaveBeenCalled()
    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
  })

  it('submitCorrection が数量<0でエラーする', async () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryCorrection(formRef)
    })

    result.form.locationCode = 'A-01'
    await result.fetchInventory()

    result.form.selectedProductId = 1
    result.form.selectedUnitType = 'CASE'
    result.locationId.value = 100
    result.form.newQty = -1
    result.form.reason = 'test reason'

    await result.submitCorrection()

    expect(ElMessage.error).toHaveBeenCalled()
    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('submitCorrection が引当数未満でエラーする', async () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryCorrection(formRef)
    })

    result.form.locationCode = 'A-01'
    await result.fetchInventory()

    result.form.selectedProductId = 1
    result.form.selectedUnitType = 'CASE'
    result.locationId.value = 100
    result.form.newQty = 2 // below allocatedQty=3
    result.form.reason = 'test reason'

    await result.submitCorrection()

    expect(ElMessage.error).toHaveBeenCalledWith(
      expect.stringContaining('inventory.correctionBelowAllocated'),
    )
    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('submitCorrection が理由未入力でもフォームバリデーションで弾かれる', async () => {
    const formRef = createMockFormRef()
    formRef.value!.validate = vi.fn().mockRejectedValue(false)

    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryCorrection(formRef)
    })

    result.form.locationCode = 'A-01'
    await result.fetchInventory()

    result.form.selectedProductId = 1
    result.form.selectedUnitType = 'CASE'
    result.locationId.value = 100
    result.form.newQty = 8
    result.form.reason = ''

    await result.submitCorrection()

    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('submitCorrection が正常にPOSTする', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse({}))

    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryCorrection(formRef)
    })

    result.form.locationCode = 'A-01'
    await result.fetchInventory()

    result.form.selectedProductId = 1
    result.form.selectedUnitType = 'CASE'
    result.locationId.value = 100
    result.form.newQty = 8
    result.form.reason = 'Counted mismatch'

    await result.submitCorrection()

    expect(formRef.value!.validate).toHaveBeenCalled()
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

  it('onProductChange が selectedUnitType と newQty と correctionHistory をリセットする', () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => useInventoryCorrection(formRef))
    result.form.selectedUnitType = 'CASE'
    result.form.newQty = 10

    result.onProductChange()
    expect(result.form.selectedUnitType).toBeNull()
    expect(result.form.newQty).toBeNull()
    expect(result.correctionHistory.value).toEqual([])
  })

  it('onUnitTypeChange が現在数量を初期値に設定し履歴を取得する', async () => {
    const mockHistory = [
      {
        correctedAt: '2026-03-20T10:00:00+09:00',
        quantityBefore: 5,
        quantityAfter: 3,
        reason: '棚卸差異',
        executedByName: '山田太郎',
      },
    ]
    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryCorrection(formRef)
    })

    result.form.locationCode = 'A-01'
    await result.fetchInventory()

    vi.mocked(apiClient.get).mockReset()
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse(mockHistory))

    result.form.selectedProductId = 1
    result.form.selectedUnitType = 'CASE'
    result.locationId.value = 100
    await result.onUnitTypeChange()

    expect(result.form.newQty).toBe(10)
    expect(result.correctionHistory.value).toHaveLength(1)
    expect(result.correctionHistory.value[0].reason).toBe('棚卸差異')
  })

  it('onUnitTypeChange で履歴取得に失敗しても空配列になる', async () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryCorrection(formRef)
    })

    result.form.locationCode = 'A-01'
    await result.fetchInventory()

    vi.mocked(apiClient.get).mockReset()
    vi.mocked(apiClient.get).mockRejectedValueOnce(new Error('Network error'))

    result.form.selectedProductId = 1
    result.form.selectedUnitType = 'CASE'
    result.locationId.value = 100
    await result.onUnitTypeChange()

    expect(result.correctionHistory.value).toEqual([])
  })

  it('goBack が在庫一覧に遷移する', () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => useInventoryCorrection(formRef))
    result.goBack()
    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'inventory-list' })
  })

  it('rules が正しく定義されている', () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => useInventoryCorrection(formRef))

    expect(result.rules.locationCode).toBeDefined()
    expect(result.rules.selectedProductId).toBeDefined()
    expect(result.rules.selectedUnitType).toBeDefined()
    expect(result.rules.newQty).toBeDefined()
    expect(result.rules.reason).toBeDefined()
    // reason has 2 rules (required + max length)
    expect(result.rules.reason).toHaveLength(2)
  })
})
