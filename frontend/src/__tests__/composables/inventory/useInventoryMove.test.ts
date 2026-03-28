import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { withSetup, mockAxiosResponse, createMockFormRef } from '../../helpers'
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

  it('fetchFromInventory がAPI失敗時にエラーメッセージを表示する', async () => {
    vi.mocked(apiClient.get).mockReset()
    vi.mocked(apiClient.get).mockRejectedValue(new Error('Network error'))

    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryMove(formRef)
    })

    result.form.fromLocationCode = 'A-01'
    await result.fetchFromInventory()

    expect(ElMessage.error).toHaveBeenCalledWith('inventory.fetchError')
    expect(result.fromInventoryOptions.value).toHaveLength(0)
  })

  it('fetchFromInventory が空コードでスキップする', async () => {
    vi.mocked(apiClient.get).mockReset()

    const formRef = createMockFormRef()
    const { result } = withSetup(() => useInventoryMove(formRef))

    result.form.fromLocationCode = ''
    await result.fetchFromInventory()

    expect(apiClient.get).not.toHaveBeenCalled()
  })

  it('fetchFromInventory でロケーション未検出時に警告する', async () => {
    vi.mocked(apiClient.get).mockReset()
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse({ content: [], totalElements: 0 }))

    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryMove(formRef)
    })

    result.form.fromLocationCode = 'NOTEXIST'
    await result.fetchFromInventory()

    expect(ElMessage.warning).toHaveBeenCalledWith('inventory.locationNotFound')
  })

  it('fetchToLocationInfo がAPI失敗時にリセットする', async () => {
    vi.mocked(apiClient.get).mockReset()
    vi.mocked(apiClient.get).mockRejectedValue(new Error('Network error'))

    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryMove(formRef)
    })

    result.form.toLocationCode = 'B-01'
    await result.fetchToLocationInfo()

    expect(result.toLocationId.value).toBeNull()
    expect(result.toCurrentQty.value).toBeNull()
    expect(result.toMaxQty.value).toBeNull()
  })

  it('fetchToLocationInfo でロケーション未検出時に警告する', async () => {
    vi.mocked(apiClient.get).mockReset()
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse({ content: [], totalElements: 0 }))

    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryMove(formRef)
    })

    result.form.toLocationCode = 'NOTEXIST'
    await result.fetchToLocationInfo()

    expect(ElMessage.warning).toHaveBeenCalledWith('inventory.locationNotFound')
    expect(result.toLocationId.value).toBeNull()
  })

  it('submitMove がAPI 500エラー時に汎用エラーメッセージを表示する', async () => {
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

    expect(ElMessage.error).toHaveBeenCalledWith('inventory.moveError')
  })

  it('initFromRoute がクエリパラメータから初期値を設定する', async () => {
    const { mockRoute } = await import('../../setup')
    mockRoute.query = { locationCode: 'C-01', productId: '1', unitType: 'CASE' }

    const formRef = createMockFormRef()
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useInventoryMove(formRef)
    })

    result.initFromRoute()
    expect(result.form.fromLocationCode).toBe('C-01')

    // クリーンアップ
    mockRoute.query = {}
  })
})
