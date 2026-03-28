import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { withSetup, mockAxiosResponse, createMockFormRef } from '../../helpers'
import { useInventoryBreakdown } from '@/composables/inventory/useInventoryBreakdown'
import { useWarehouseStore } from '@/stores/warehouse'
import { mockRouter } from '../../setup'

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
    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryBreakdown(formRef)
    })

    result.form.fromLocationCode = 'A-01'
    await result.fetchFromInventory()

    expect(result.fromInventoryOptions.value).toHaveLength(2)
    expect(result.fromLocationId.value).toBe(100)
    expect(result.form.selectedProductId).toBeNull()
  })

  it('fetchFromInventory が空ロケーションで警告する', async () => {
    vi.mocked(apiClient.get).mockReset()
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse({ content: [], totalElements: 0 }))

    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryBreakdown(formRef)
    })

    result.form.fromLocationCode = 'NOTEXIST'
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

    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryBreakdown(formRef)
    })

    result.form.selectedProductId = 1
    await result.onProductChange()

    expect(result.productInfo.value).toBeTruthy()
    expect(result.productInfo.value!.caseQuantity).toBe(12)
    expect(result.productInfo.value!.ballQuantity).toBe(6)
  })

  it('productOptions が重複除去した商品リストを返す', async () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryBreakdown(formRef)
    })

    result.form.fromLocationCode = 'A-01'
    await result.fetchFromInventory()

    expect(result.productOptions.value).toHaveLength(1) // productId=1 deduplicated
  })

  it('conversionRate が正しく計算される', async () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryBreakdown(formRef)
    })

    result.productInfo.value = {
      id: 1,
      productCode: 'P001',
      productName: 'P1',
      caseQuantity: 12,
      ballQuantity: 6,
    }
    result.form.fromUnitType = 'CASE'
    result.form.toUnitType = 'BALL'
    expect(result.conversionRate.value).toBe(12)

    result.form.toUnitType = 'PIECE'
    expect(result.conversionRate.value).toBe(72) // 12 * 6

    result.form.fromUnitType = 'BALL'
    result.form.toUnitType = 'PIECE'
    expect(result.conversionRate.value).toBe(6)
  })

  it('convertedQty が正しく計算される', () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryBreakdown(formRef)
    })

    result.productInfo.value = {
      id: 1,
      productCode: 'P001',
      productName: 'P1',
      caseQuantity: 12,
      ballQuantity: 6,
    }
    result.form.fromUnitType = 'CASE'
    result.form.toUnitType = 'BALL'
    result.form.breakdownQty = 3

    expect(result.convertedQty.value).toBe(36) // 3 * 12
  })

  it('submitBreakdown がフォームバリデーション失敗時に中断する', async () => {
    const formRef = createMockFormRef()
    formRef.value!.validate = vi.fn().mockRejectedValue(false)

    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryBreakdown(formRef)
    })

    await result.submitBreakdown()

    expect(apiClient.post).not.toHaveBeenCalled()
    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
  })

  it('submitBreakdown が POST リクエストを送信する', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse({}))

    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryBreakdown(formRef)
    })

    result.form.fromLocationCode = 'A-01'
    await result.fetchFromInventory()

    result.form.selectedProductId = 1
    result.form.fromUnitType = 'CASE'
    result.form.toUnitType = 'BALL'
    result.form.breakdownQty = 2
    result.fromLocationId.value = 100
    result.productInfo.value = {
      id: 1,
      productCode: 'P001',
      productName: 'P1',
      caseQuantity: 12,
      ballQuantity: 6,
    }

    await result.submitBreakdown()

    expect(formRef.value!.validate).toHaveBeenCalled()
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
    const formRef = createMockFormRef()
    const { result } = withSetup(() => useInventoryBreakdown(formRef))
    result.goBack()
    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'inventory-list' })
  })

  it('rules が正しく定義されている', () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => useInventoryBreakdown(formRef))

    expect(result.rules.fromLocationCode).toBeDefined()
    expect(result.rules.selectedProductId).toBeDefined()
    expect(result.rules.fromUnitType).toBeDefined()
    expect(result.rules.breakdownQty).toBeDefined()
    expect(result.rules.toUnitType).toBeDefined()
  })

  it('submitBreakdown が数量超過でエラーする', async () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryBreakdown(formRef)
    })

    result.form.fromLocationCode = 'A-01'
    await result.fetchFromInventory()

    result.form.selectedProductId = 1
    result.form.fromUnitType = 'CASE'
    result.form.toUnitType = 'BALL'
    result.form.breakdownQty = 999 // exceeds availableQty=8
    result.fromLocationId.value = 100
    result.productInfo.value = {
      id: 1,
      productCode: 'P001',
      productName: 'P1',
      caseQuantity: 12,
      ballQuantity: 6,
    }

    await result.submitBreakdown()

    expect(ElMessage.error).toHaveBeenCalledWith('inventory.breakdownQtyExceedsAvailable')
    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('submitBreakdown が変換レート0以下でエラーする', async () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryBreakdown(formRef)
    })

    result.form.fromLocationCode = 'A-01'
    await result.fetchFromInventory()

    result.form.selectedProductId = 1
    result.form.fromUnitType = 'CASE'
    result.form.toUnitType = 'BALL'
    result.form.breakdownQty = 2
    result.fromLocationId.value = 100
    result.productInfo.value = {
      id: 1,
      productCode: 'P001',
      productName: 'P1',
      caseQuantity: 0, // rate = 0
      ballQuantity: 6,
    }

    await result.submitBreakdown()

    expect(ElMessage.error).toHaveBeenCalledWith('inventory.conversionRateNotSet')
    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('fetchFromInventory がAPI失敗時にエラーメッセージを表示する', async () => {
    vi.mocked(apiClient.get).mockReset()
    vi.mocked(apiClient.get).mockRejectedValue(new Error('Network error'))

    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryBreakdown(formRef)
    })

    result.form.fromLocationCode = 'A-01'
    await result.fetchFromInventory()

    expect(ElMessage.error).toHaveBeenCalledWith('inventory.fetchError')
  })

  it('onFromUnitTypeChange が toUnitType をリセットする', () => {
    const formRef = createMockFormRef()
    const { result } = withSetup(() => useInventoryBreakdown(formRef))
    result.form.toUnitType = 'PIECE'

    result.onFromUnitTypeChange()
    expect(result.form.toUnitType).toBeNull()
  })

  it('submitBreakdown がAPI 500エラー時に汎用エラーメッセージを表示する', async () => {
    const error500 = new Error('Server error') as Error & {
      isAxiosError: boolean
      response: { status: number; data: undefined }
    }
    error500.isAxiosError = true
    error500.response = { status: 500, data: undefined }
    vi.mocked(apiClient.post).mockRejectedValue(error500)

    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryBreakdown(formRef)
    })

    result.form.fromLocationCode = 'A-01'
    await result.fetchFromInventory()

    result.form.selectedProductId = 1
    result.form.fromUnitType = 'CASE'
    result.form.toUnitType = 'BALL'
    result.form.breakdownQty = 2
    result.fromLocationId.value = 100
    result.productInfo.value = {
      id: 1,
      productCode: 'P001',
      productName: 'P1',
      caseQuantity: 12,
      ballQuantity: 6,
    }

    await result.submitBreakdown()

    expect(ElMessage.error).toHaveBeenCalledWith('inventory.breakdownError')
  })
})
