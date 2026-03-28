import { ref } from 'vue'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { withSetup, mockAxiosResponse } from '../../helpers'
import { useInventoryMove } from '@/composables/inventory/useInventoryMove'
import { useWarehouseStore } from '@/stores/warehouse'
import { mockRouter } from '../../setup'

vi.mock('@/api/generated/models/inventory-location-item', () => ({}))
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
    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryMove(formRef)
    })

    result.form.fromLocationCode = 'A-01'
    await result.fetchFromInventory()

    expect(result.fromInventoryOptions.value).toHaveLength(1)
    expect(result.form.selectedProductId).toBeNull()
    expect(result.form.selectedUnitType).toBeNull()
  })

  it('fetchToLocationInfo が移動先ロケーション情報と上限を取得する', async () => {
    vi.mocked(apiClient.get).mockReset()
    const toLocRes = mockAxiosResponse({ content: [{ id: 200 }], totalElements: 1 })
    const toInvRes = mockAxiosResponse({ content: [{ quantity: 3 }] })
    const toCapRes = mockAxiosResponse({ unitType: 'CASE', maxQuantity: 10 })
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce(toLocRes)
      .mockResolvedValueOnce(toInvRes)
      .mockResolvedValueOnce(toCapRes)

    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryMove(formRef)
    })

    result.form.toLocationCode = 'B-01'
    result.form.selectedProductId = 1
    result.form.selectedUnitType = 'CASE'
    await result.fetchToLocationInfo()

    expect(result.toLocationId.value).toBe(200)
    expect(result.toCurrentQty.value).toBe(3)
    expect(result.toMaxQty.value).toBe(10)
  })

  it('fetchToLocationInfo が空コードでリセットする', async () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryMove(formRef)
    })

    result.form.toLocationCode = ''
    await result.fetchToLocationInfo()

    expect(result.toLocationId.value).toBeNull()
    expect(result.toCurrentQty.value).toBeNull()
    expect(result.toMaxQty.value).toBeNull()
  })

  it('submitMove がフォームバリデーション失敗時に中断する', async () => {
    const formRef = createMockFormRef()
    formRef.value!.validate = vi.fn().mockRejectedValue(false)

    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryMove(formRef)
    })

    await result.submitMove()

    expect(apiClient.post).not.toHaveBeenCalled()
    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
  })

  it('submitMove が同一ロケーションでエラーする', async () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryMove(formRef)
    })

    result.form.fromLocationCode = 'A-01'
    await result.fetchFromInventory()

    result.form.selectedProductId = 1
    result.form.selectedUnitType = 'CASE'
    result.form.toLocationCode = 'A-01'
    result.toLocationId.value = 200
    result.form.moveQty = 5

    await result.submitMove()

    expect(ElMessage.error).toHaveBeenCalledWith('inventory.sameLocationError')
    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('submitMove が数量超過でエラーする', async () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryMove(formRef)
    })

    result.form.fromLocationCode = 'A-01'
    await result.fetchFromInventory()

    result.form.selectedProductId = 1
    result.form.selectedUnitType = 'CASE'
    result.form.toLocationCode = 'B-01'
    result.toLocationId.value = 200
    result.form.moveQty = 999 // exceeds availableQty=8

    await result.submitMove()

    expect(ElMessage.error).toHaveBeenCalledWith('inventory.moveQtyExceedsAvailable')
    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('submitMove が収容上限超過でエラーする', async () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryMove(formRef)
    })

    result.form.fromLocationCode = 'A-01'
    await result.fetchFromInventory()

    result.form.selectedProductId = 1
    result.form.selectedUnitType = 'CASE'
    result.form.toLocationCode = 'B-01'
    result.toLocationId.value = 200
    result.toCurrentQty.value = 8
    result.toMaxQty.value = 10
    result.form.moveQty = 5 // 8 + 5 = 13 > 10

    await result.submitMove()

    expect(ElMessage.error).toHaveBeenCalled()
    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('submitMove が正常にPOSTする', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse({}))

    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryMove(formRef)
    })

    result.form.fromLocationCode = 'A-01'
    await result.fetchFromInventory()

    result.form.selectedProductId = 1
    result.form.selectedUnitType = 'CASE'
    result.form.toLocationCode = 'B-01'
    result.toLocationId.value = 200
    result.form.moveQty = 3

    await result.submitMove()

    expect(formRef.value!.validate).toHaveBeenCalled()
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
    const formRef = createMockFormRef()
    const { result } = withSetup(() => useInventoryMove(formRef))
    result.form.selectedUnitType = 'CASE'

    result.onProductChange()
    expect(result.form.selectedUnitType).toBeNull()
  })

  it('goBack が在庫一覧に遷移する', () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => useInventoryMove(formRef))
    result.goBack()
    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'inventory-list' })
  })

  it('rules が正しく定義されている', () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => useInventoryMove(formRef))

    expect(result.rules.fromLocationCode).toBeDefined()
    expect(result.rules.selectedProductId).toBeDefined()
    expect(result.rules.selectedUnitType).toBeDefined()
    expect(result.rules.toLocationCode).toBeDefined()
    expect(result.rules.moveQty).toBeDefined()
  })
})
