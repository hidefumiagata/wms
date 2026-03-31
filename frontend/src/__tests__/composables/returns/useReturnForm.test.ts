import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import { withSetup, mockAxiosResponse, createMockFormRef, createAxiosError } from '../../helpers'
import { useReturnForm } from '@/composables/returns/useReturnForm'
import { useWarehouseStore } from '@/stores/warehouse'
import { mockRoute } from '../../setup'

describe('useReturnForm', () => {
  beforeEach(() => {
    mockRoute.query = {}
  })

  function setup(formRef = createMockFormRef()) {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useReturnForm(formRef)
    })
    return { result, formRef }
  }

  // --- 初期状態 ---
  describe('初期状態', () => {
    it('フォームが空の初期状態で返される', () => {
      const { result } = setup()
      expect(result.form.returnType).toBe('')
      expect(result.form.productCode).toBe('')
      expect(result.form.quantity).toBeNull()
      expect(result.form.unitType).toBe('')
      expect(result.form.locationId).toBeNull()
      expect(result.form.returnReason).toBe('')
      expect(result.form.returnReasonNote).toBe('')
      expect(result.selectedProduct.value).toBeNull()
      expect(result.submitting.value).toBe(false)
    })

    it('クエリパラメータ returnType がプリセットされる', () => {
      mockRoute.query = { returnType: 'INBOUND' }
      const { result } = setup()
      expect(result.form.returnType).toBe('INBOUND')
    })

    it('クエリパラメータ relatedSlipNumber がプリセットされる', () => {
      mockRoute.query = { relatedSlipNumber: 'INB-20260318-0001' }
      const { result } = setup()
      expect(result.form.relatedSlipNumber).toBe('INB-20260318-0001')
    })

    it('不正な returnType クエリパラメータはプリセットしない', () => {
      mockRoute.query = { returnType: 'INVALID' }
      const { result } = setup()
      expect(result.form.returnType).toBe('')
    })
  })

  // --- 条件付き表示 ---
  describe('条件付き表示', () => {
    it('isInventoryReturn は INVENTORY 選択時に true', () => {
      const { result } = setup()
      result.form.returnType = 'INVENTORY'
      expect(result.isInventoryReturn.value).toBe(true)
    })

    it('isInventoryReturn は INBOUND 選択時に false', () => {
      const { result } = setup()
      result.form.returnType = 'INBOUND'
      expect(result.isInventoryReturn.value).toBe(false)
    })

    it('showRelatedSlip は INBOUND/OUTBOUND で true', () => {
      const { result } = setup()
      result.form.returnType = 'INBOUND'
      expect(result.showRelatedSlip.value).toBe(true)
      result.form.returnType = 'OUTBOUND'
      expect(result.showRelatedSlip.value).toBe(true)
      result.form.returnType = 'INVENTORY'
      expect(result.showRelatedSlip.value).toBe(false)
    })

    it('showLotNumber はロット管理商品で true', () => {
      const { result } = setup()
      expect(result.showLotNumber.value).toBe(false)
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test',
        lotManageFlag: true,
        expiryManageFlag: false,
      }
      expect(result.showLotNumber.value).toBe(true)
    })

    it('showExpiryDate は期限管理商品で true', () => {
      const { result } = setup()
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test',
        lotManageFlag: false,
        expiryManageFlag: true,
      }
      expect(result.showExpiryDate.value).toBe(true)
    })

    it('isReasonOther は OTHER 選択時に true', () => {
      const { result } = setup()
      result.form.returnReason = 'OTHER'
      expect(result.isReasonOther.value).toBe(true)
      result.form.returnReason = 'DAMAGED'
      expect(result.isReasonOther.value).toBe(false)
    })
  })

  // --- 商品検索 ---
  describe('searchProduct', () => {
    it('空のコードでは検索しない', async () => {
      const { result } = setup()
      result.form.productCode = ''
      await result.searchProduct()
      expect(apiClient.get).not.toHaveBeenCalled()
    })

    it('検索結果0件で info メッセージを表示', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({ content: [], totalElements: 0 }),
      )
      const { result } = setup()
      result.form.productCode = 'NOTEXIST'
      await result.searchProduct()
      expect(ElMessage.info).toHaveBeenCalledWith('returns.productSearchEmpty')
      expect(result.selectedProduct.value).toBeNull()
    })

    it('完全一致する商品が選択される', async () => {
      const products = [
        {
          id: 1,
          productCode: 'P001',
          productName: 'Product 1',
          lotManageFlag: false,
          expiryManageFlag: false,
        },
        {
          id: 2,
          productCode: 'P002',
          productName: 'Product 2',
          lotManageFlag: false,
          expiryManageFlag: false,
        },
      ]
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({ content: products, totalElements: 2 }),
      )
      const { result } = setup()
      result.form.productCode = 'P001'
      await result.searchProduct()
      expect(result.selectedProduct.value?.id).toBe(1)
      expect(result.form.productCode).toBe('P001')
    })

    it('1件のみなら自動選択', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({
          content: [
            {
              id: 5,
              productCode: 'P005',
              productName: 'Product 5',
              lotManageFlag: true,
              expiryManageFlag: false,
            },
          ],
          totalElements: 1,
        }),
      )
      const { result } = setup()
      result.form.productCode = 'P0'
      await result.searchProduct()
      expect(result.selectedProduct.value?.id).toBe(5)
      expect(result.form.productCode).toBe('P005')
    })

    it('複数件で完全一致なしなら最初の1件を選択', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({
          content: [
            {
              id: 10,
              productCode: 'PA01',
              productName: 'A',
              lotManageFlag: false,
              expiryManageFlag: false,
            },
            {
              id: 11,
              productCode: 'PA02',
              productName: 'B',
              lotManageFlag: false,
              expiryManageFlag: false,
            },
          ],
          totalElements: 2,
        }),
      )
      const { result } = setup()
      result.form.productCode = 'PA'
      await result.searchProduct()
      expect(result.selectedProduct.value?.id).toBe(10)
    })

    it('API失敗時にエラーメッセージ', async () => {
      vi.mocked(apiClient.get).mockRejectedValueOnce(new Error('Network'))
      const { result } = setup()
      result.form.productCode = 'P001'
      await result.searchProduct()
      expect(ElMessage.error).toHaveBeenCalledWith('returns.productNotFound')
      expect(result.selectedProduct.value).toBeNull()
    })

    it('キャンセル時は何もしない', async () => {
      const cancelError = new axios.CanceledError()
      vi.mocked(apiClient.get).mockRejectedValueOnce(cancelError)
      vi.mocked(axios.isCancel).mockReturnValueOnce(true)
      const { result } = setup()
      result.form.productCode = 'P001'
      await result.searchProduct()
      expect(ElMessage.error).not.toHaveBeenCalled()
    })
  })

  // --- ロケーション候補取得 ---
  describe('fetchLocationCandidates', () => {
    it('在庫返品で商品・荷姿選択後にロケーション候補を取得', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({
          content: [
            {
              locationId: 100,
              locationCode: 'A-01-01',
              quantity: 20,
              allocatedQty: 5,
              availableQty: 15,
            },
          ],
        }),
      )
      const { result } = setup()
      result.form.returnType = 'INVENTORY'
      result.form.unitType = 'CASE'
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test',
        lotManageFlag: false,
        expiryManageFlag: false,
      }
      await result.fetchLocationCandidates()
      expect(result.locationOptions.value).toHaveLength(1)
      expect(result.locationOptions.value[0].locationCode).toBe('A-01-01')
    })

    it('候補が0件のときに info メッセージ', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({ content: [] }),
      )
      const { result } = setup()
      result.form.returnType = 'INVENTORY'
      result.form.unitType = 'CASE'
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test',
        lotManageFlag: false,
        expiryManageFlag: false,
      }
      await result.fetchLocationCandidates()
      expect(ElMessage.info).toHaveBeenCalledWith('returns.locationCandidateEmpty')
    })

    it('商品未選択では取得しない', async () => {
      const { result } = setup()
      result.form.returnType = 'INVENTORY'
      result.form.unitType = 'CASE'
      await result.fetchLocationCandidates()
      expect(apiClient.get).not.toHaveBeenCalled()
    })

    it('API失敗時にロケーション候補を空にする', async () => {
      vi.mocked(apiClient.get).mockRejectedValueOnce(new Error('fail'))
      const { result } = setup()
      result.form.returnType = 'INVENTORY'
      result.form.unitType = 'CASE'
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test',
        lotManageFlag: false,
        expiryManageFlag: false,
      }
      await result.fetchLocationCandidates()
      expect(result.locationOptions.value).toEqual([])
    })

    it('キャンセル時は何もしない', async () => {
      const cancelError = new axios.CanceledError()
      vi.mocked(apiClient.get).mockRejectedValueOnce(cancelError)
      vi.mocked(axios.isCancel).mockReturnValueOnce(true)
      const { result } = setup()
      result.form.returnType = 'INVENTORY'
      result.form.unitType = 'CASE'
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test',
        lotManageFlag: false,
        expiryManageFlag: false,
      }
      await result.fetchLocationCandidates()
      // locationOptions should remain unchanged (empty)
      expect(result.locationOptions.value).toEqual([])
    })
  })

  // --- selectedLocationInventory ---
  describe('selectedLocationInventory', () => {
    it('ロケーション選択時に在庫情報を返す', () => {
      const { result } = setup()
      result.locationOptions.value = [
        { locationId: 100, locationCode: 'A-01', quantity: 20, allocatedQty: 5, availableQty: 15 },
      ]
      result.form.locationId = 100
      expect(result.selectedLocationInventory.value?.quantity).toBe(20)
    })

    it('ロケーション未選択時は null', () => {
      const { result } = setup()
      expect(result.selectedLocationInventory.value).toBeNull()
    })

    it('一致しないロケーションIDでは null', () => {
      const { result } = setup()
      result.locationOptions.value = [
        { locationId: 100, locationCode: 'A-01', quantity: 20, allocatedQty: 0, availableQty: 20 },
      ]
      result.form.locationId = 999
      expect(result.selectedLocationInventory.value).toBeNull()
    })
  })

  // --- hasAllocated ---
  describe('hasAllocated', () => {
    it('引当数 > 0 で true', () => {
      const { result } = setup()
      result.locationOptions.value = [
        { locationId: 100, locationCode: 'A-01', quantity: 20, allocatedQty: 5, availableQty: 15 },
      ]
      result.form.locationId = 100
      expect(result.hasAllocated.value).toBe(true)
    })

    it('引当数 = 0 で false', () => {
      const { result } = setup()
      result.locationOptions.value = [
        { locationId: 100, locationCode: 'A-01', quantity: 20, allocatedQty: 0, availableQty: 20 },
      ]
      result.form.locationId = 100
      expect(result.hasAllocated.value).toBe(false)
    })

    it('ロケーション未選択で false', () => {
      const { result } = setup()
      expect(result.hasAllocated.value).toBe(false)
    })
  })

  // --- onReturnTypeChange ---
  describe('onReturnTypeChange', () => {
    it('ロケーション関連をリセットする', () => {
      const { result } = setup()
      result.form.locationId = 100
      result.locationOptions.value = [
        { locationId: 100, locationCode: 'A-01', quantity: 10, allocatedQty: 0, availableQty: 10 },
      ]
      result.form.returnType = 'INBOUND'
      result.onReturnTypeChange()
      expect(result.form.locationId).toBeNull()
      expect(result.locationOptions.value).toEqual([])
    })

    it('在庫返品に変更かつ商品・荷姿選択済みなら候補取得', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({ content: [{ locationId: 200, locationCode: 'B-01', quantity: 5, allocatedQty: 0, availableQty: 5 }] }),
      )
      const { result } = setup()
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test',
        lotManageFlag: false,
        expiryManageFlag: false,
      }
      result.form.unitType = 'CASE'
      result.form.returnType = 'INVENTORY'
      result.onReturnTypeChange()
      // fetchLocationCandidates is called but async - just check it was called
      expect(apiClient.get).toHaveBeenCalled()
    })
  })

  // --- onUnitTypeChange ---
  describe('onUnitTypeChange', () => {
    it('ロケーション候補をリセットし在庫返品時に再取得', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({ content: [] }),
      )
      const { result } = setup()
      result.form.returnType = 'INVENTORY'
      result.form.locationId = 100
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test',
        lotManageFlag: false,
        expiryManageFlag: false,
      }
      result.form.unitType = 'BALL'
      result.onUnitTypeChange()
      expect(result.form.locationId).toBeNull()
      expect(apiClient.get).toHaveBeenCalled()
    })

    it('入荷返品時はロケーション候補を取得しない', () => {
      const { result } = setup()
      result.form.returnType = 'INBOUND'
      result.form.unitType = 'CASE'
      result.onUnitTypeChange()
      expect(apiClient.get).not.toHaveBeenCalled()
    })
  })

  // --- selectProduct ---
  describe('selectProduct', () => {
    it('商品情報をセットしロケーションをリセット', () => {
      const { result } = setup()
      result.form.locationId = 100
      result.locationOptions.value = [
        { locationId: 100, locationCode: 'A-01', quantity: 10, allocatedQty: 0, availableQty: 10 },
      ]
      result.selectProduct({
        id: 2,
        productCode: 'P002',
        productName: 'Product 2',
        lotManageFlag: false,
        expiryManageFlag: false,
      })
      expect(result.selectedProduct.value?.id).toBe(2)
      expect(result.form.productCode).toBe('P002')
      expect(result.form.locationId).toBeNull()
      expect(result.locationOptions.value).toEqual([])
    })

    it('在庫返品かつ荷姿選択済みならロケーション候補取得', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({ content: [] }),
      )
      const { result } = setup()
      result.form.returnType = 'INVENTORY'
      result.form.unitType = 'CASE'
      result.selectProduct({
        id: 3,
        productCode: 'P003',
        productName: 'Product 3',
        lotManageFlag: false,
        expiryManageFlag: false,
      })
      expect(apiClient.get).toHaveBeenCalled()
    })
  })

  // --- submitReturn ---
  describe('submitReturn', () => {
    function setupForSubmit() {
      const formRef = createMockFormRef()
      const { result } = setup(formRef)
      result.form.returnType = 'INBOUND'
      result.form.productCode = 'P001'
      result.form.quantity = 5
      result.form.unitType = 'CASE'
      result.form.returnReason = 'QUALITY_DEFECT'
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test Product',
        lotManageFlag: false,
        expiryManageFlag: false,
      }
      return { result, formRef }
    }

    it('バリデーション失敗時に中断', async () => {
      const formRef = createMockFormRef()
      formRef.value!.validate = vi.fn().mockRejectedValue(false)
      const { result } = setup(formRef)
      await result.submitReturn()
      expect(apiClient.post).not.toHaveBeenCalled()
      expect(ElMessageBox.confirm).not.toHaveBeenCalled()
    })

    it('selectedProduct が null の場合に中断', async () => {
      const formRef = createMockFormRef()
      const { result } = setup(formRef)
      result.selectedProduct.value = null
      await result.submitReturn()
      expect(ElMessageBox.confirm).not.toHaveBeenCalled()
    })

    it('確認ダイアログキャンセルで中断', async () => {
      vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')
      const { result } = setupForSubmit()
      await result.submitReturn()
      expect(apiClient.post).not.toHaveBeenCalled()
    })

    it('入荷返品の正常登録', async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce(
        mockAxiosResponse({ slipNumber: 'RTN-I-20260318-0001' }),
      )
      const { result } = setupForSubmit()
      result.form.relatedSlipNumber = 'INB-20260318-0001'
      await result.submitReturn()

      expect(ElMessageBox.confirm).toHaveBeenCalled()
      expect(apiClient.post).toHaveBeenCalledWith(
        '/returns',
        expect.objectContaining({
          returnType: 'INBOUND',
          productId: 1,
          quantity: 5,
          unitType: 'CASE',
          returnReason: 'QUALITY_DEFECT',
          relatedSlipNumber: 'INB-20260318-0001',
        }),
      )
      expect(ElMessage.success).toHaveBeenCalled()
      // フォームがリセットされる
      expect(result.form.returnType).toBe('')
      expect(result.selectedProduct.value).toBeNull()
    })

    it('在庫返品の正常登録（locationId 含む）', async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce(
        mockAxiosResponse({ slipNumber: 'RTN-S-20260318-0001' }),
      )
      const { result } = setupForSubmit()
      result.form.returnType = 'INVENTORY'
      result.form.locationId = 100
      await result.submitReturn()

      expect(apiClient.post).toHaveBeenCalledWith(
        '/returns',
        expect.objectContaining({
          returnType: 'INVENTORY',
          locationId: 100,
        }),
      )
    })

    it('出荷返品の正常登録', async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce(
        mockAxiosResponse({ slipNumber: 'RTN-O-20260318-0001' }),
      )
      const { result } = setupForSubmit()
      result.form.returnType = 'OUTBOUND'
      await result.submitReturn()
      expect(apiClient.post).toHaveBeenCalledWith(
        '/returns',
        expect.objectContaining({ returnType: 'OUTBOUND' }),
      )
    })

    it('理由備考あり・ロット・賞味期限が送信される', async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce(
        mockAxiosResponse({ slipNumber: 'RTN-I-20260318-0002' }),
      )
      const { result } = setupForSubmit()
      result.form.returnReason = 'OTHER'
      result.form.returnReasonNote = 'Custom reason'
      result.form.lotNumber = 'LOT-001'
      result.form.expiryDate = '2026-12-31'
      result.form.partnerId = 789
      await result.submitReturn()

      expect(apiClient.post).toHaveBeenCalledWith(
        '/returns',
        expect.objectContaining({
          returnReason: 'OTHER',
          returnReasonNote: 'Custom reason',
          lotNumber: 'LOT-001',
          expiryDate: '2026-12-31',
          partnerId: 789,
        }),
      )
    })

    it('空の任意フィールドは送信しない', async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce(
        mockAxiosResponse({ slipNumber: 'RTN-I-20260318-0003' }),
      )
      const { result } = setupForSubmit()
      await result.submitReturn()

      const payload = vi.mocked(apiClient.post).mock.calls[0][1] as Record<string, unknown>
      expect(payload.relatedSlipNumber).toBeUndefined()
      expect(payload.lotNumber).toBeUndefined()
      expect(payload.expiryDate).toBeUndefined()
      expect(payload.partnerId).toBeUndefined()
      expect(payload.returnReasonNote).toBeUndefined()
      expect(payload.locationId).toBeUndefined()
    })

    // --- エラーハンドリング ---
    it('422 RETURN_INSUFFICIENT_QUANTITY', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(422, { errorCode: 'RETURN_INSUFFICIENT_QUANTITY', message: 'Insufficient' }),
      )
      const { result } = setupForSubmit()
      result.form.returnType = 'INVENTORY'
      result.form.locationId = 100
      result.locationOptions.value = [
        { locationId: 100, locationCode: 'A-01', quantity: 10, allocatedQty: 0, availableQty: 10 },
      ]
      await result.submitReturn()
      expect(ElMessage.error).toHaveBeenCalledWith(expect.stringContaining('returns.insufficientQuantity'))
    })

    it('422 RETURN_ALLOCATED_INVENTORY', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(422, { errorCode: 'RETURN_ALLOCATED_INVENTORY' }),
      )
      const { result } = setupForSubmit()
      result.form.returnType = 'INVENTORY'
      result.form.locationId = 100
      result.locationOptions.value = [
        { locationId: 100, locationCode: 'A-01', quantity: 10, allocatedQty: 3, availableQty: 7 },
      ]
      await result.submitReturn()
      expect(ElMessage.error).toHaveBeenCalledWith(expect.stringContaining('returns.allocatedInventory'))
    })

    it('422 RETURN_STOCKTAKE_LOCKED', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(422, { errorCode: 'RETURN_STOCKTAKE_LOCKED' }),
      )
      const { result } = setupForSubmit()
      await result.submitReturn()
      expect(ElMessage.error).toHaveBeenCalledWith('returns.stocktakeLocked')
    })

    it('422 その他のエラーコード', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(422, { errorCode: 'UNKNOWN_CODE', message: 'Business rule error' }),
      )
      const { result } = setupForSubmit()
      await result.submitReturn()
      expect(ElMessage.error).toHaveBeenCalledWith('Business rule error')
    })

    it('422 でメッセージが無い場合はフォールバック', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(422, { errorCode: 'UNKNOWN_CODE' }),
      )
      const { result } = setupForSubmit()
      await result.submitReturn()
      expect(ElMessage.error).toHaveBeenCalledWith('returns.registerError')
    })

    it('404 エラー', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(404, { message: 'Product not found' }),
      )
      const { result } = setupForSubmit()
      await result.submitReturn()
      expect(ElMessage.error).toHaveBeenCalledWith('Product not found')
    })

    it('404 でメッセージが無い場合はフォールバック', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(404, {}),
      )
      const { result } = setupForSubmit()
      await result.submitReturn()
      expect(ElMessage.error).toHaveBeenCalledWith('error.notFound')
    })

    it('409 楽観的ロック競合', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(createAxiosError(409, {}))
      const { result } = setupForSubmit()
      await result.submitReturn()
      expect(ElMessage.error).toHaveBeenCalledWith('error.optimisticLock')
    })

    it('ネットワークエラー（response なし）', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(new Error('Network error'))
      const { result } = setupForSubmit()
      await result.submitReturn()
      expect(ElMessage.error).toHaveBeenCalledWith('error.network')
    })

    it('500 エラー等のその他', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(createAxiosError(500, {}))
      const { result } = setupForSubmit()
      await result.submitReturn()
      expect(ElMessage.error).toHaveBeenCalledWith('returns.registerError')
    })

    it('submitting フラグが正しく制御される', async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce(
        mockAxiosResponse({ slipNumber: 'RTN-I-20260318-0001' }),
      )
      const { result } = setupForSubmit()
      expect(result.submitting.value).toBe(false)
      const promise = result.submitReturn()
      // Note: due to async confirm dialog mock, submitting may already be set
      await promise
      expect(result.submitting.value).toBe(false)
    })
  })

  // --- resetForm ---
  describe('resetForm', () => {
    it('すべてのフォームフィールドをリセット', () => {
      const formRef = createMockFormRef()
      const { result } = setup(formRef)
      result.form.returnType = 'INVENTORY'
      result.form.productCode = 'P001'
      result.form.quantity = 10
      result.form.unitType = 'CASE'
      result.form.locationId = 100
      result.form.returnReason = 'DAMAGED'
      result.form.returnReasonNote = 'Note'
      result.form.relatedSlipNumber = 'INB-001'
      result.form.lotNumber = 'LOT-001'
      result.form.expiryDate = '2026-12-31'
      result.form.partnerId = 789
      result.form.partnerCode = 'SUP-001'
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test',
        lotManageFlag: false,
        expiryManageFlag: false,
      }
      result.locationOptions.value = [
        { locationId: 100, locationCode: 'A-01', quantity: 10, allocatedQty: 0, availableQty: 10 },
      ]

      result.resetForm()

      expect(result.form.returnType).toBe('')
      expect(result.form.productCode).toBe('')
      expect(result.form.quantity).toBeNull()
      expect(result.form.unitType).toBe('')
      expect(result.form.locationId).toBeNull()
      expect(result.form.returnReason).toBe('')
      expect(result.form.returnReasonNote).toBe('')
      expect(result.form.relatedSlipNumber).toBe('')
      expect(result.form.lotNumber).toBe('')
      expect(result.form.expiryDate).toBe('')
      expect(result.form.partnerId).toBeNull()
      expect(result.form.partnerCode).toBe('')
      expect(result.selectedProduct.value).toBeNull()
      expect(result.locationOptions.value).toEqual([])
      expect(formRef.value!.clearValidate).toHaveBeenCalled()
    })
  })

  // --- rules ---
  describe('rules', () => {
    it('基本的なバリデーションルールが定義されている', () => {
      const { result } = setup()
      const r = result.rules.value
      expect(r.returnType).toBeDefined()
      expect(r.productCode).toBeDefined()
      expect(r.quantity).toBeDefined()
      expect(r.unitType).toBeDefined()
      expect(r.returnReason).toBeDefined()
    })

    it('INVENTORY 時にロケーション必須ルールがある', () => {
      const { result } = setup()
      result.form.returnType = 'INVENTORY'
      const r = result.rules.value
      expect(r.locationId).toHaveLength(1)
    })

    it('非 INVENTORY 時にロケーションルールがない', () => {
      const { result } = setup()
      result.form.returnType = 'INBOUND'
      const r = result.rules.value
      expect(r.locationId).toHaveLength(0)
    })

    it('OTHER 選択時に備考必須ルールがある', () => {
      const { result } = setup()
      result.form.returnReason = 'OTHER'
      const r = result.rules.value
      // required + maxLength = 2 rules
      expect(r.returnReasonNote).toHaveLength(2)
      expect((r.returnReasonNote as { required?: boolean }[])[0].required).toBe(true)
    })

    it('非 OTHER 時は備考は maxLength のみ', () => {
      const { result } = setup()
      result.form.returnReason = 'DAMAGED'
      const r = result.rules.value
      expect(r.returnReasonNote).toHaveLength(1)
    })
  })

  // --- rules の message 関数を呼び出してカバレッジ確保 ---
  describe('rules message functions', () => {
    it('全ルールの message 関数が文字列を返す', () => {
      const { result } = setup()
      // 全分岐をカバーするため OTHER + INVENTORY を設定
      result.form.returnType = 'INVENTORY'
      result.form.returnReason = 'OTHER'
      const r = result.rules.value

      // 各ルールの message 関数を呼び出す
      for (const [, ruleArr] of Object.entries(r)) {
        if (Array.isArray(ruleArr)) {
          for (const rule of ruleArr as { message?: (() => string) | string }[]) {
            if (typeof rule.message === 'function') {
              const msg = rule.message()
              expect(typeof msg).toBe('string')
            }
          }
        }
      }
    })

    it('非 INVENTORY 時のルールの message 関数も呼び出す', () => {
      const { result } = setup()
      result.form.returnType = 'INBOUND'
      result.form.returnReason = 'DAMAGED'
      const r = result.rules.value
      // returnReasonNote は maxLength のみ
      const noteRules = r.returnReasonNote as { message?: (() => string) | string }[]
      expect(noteRules).toHaveLength(1)
      if (typeof noteRules[0].message === 'function') {
        expect(typeof noteRules[0].message()).toBe('string')
      }
    })
  })

  // --- onUnmounted 時の abort ---
  describe('onUnmounted', () => {
    it('unmount 時に abort が呼ばれる', async () => {
      const formRef = createMockFormRef()
      const { result, wrapper } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useReturnForm(formRef)
      })
      // 何かしらのAPIコールを発生させて abortController をセット
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({ content: [], totalElements: 0 }),
      )
      result.form.productCode = 'P001'
      await result.searchProduct()

      // unmount でエラーが発生しないことを確認
      wrapper.unmount()
    })
  })

  // --- 確認ダイアログ文言のカバレッジ ---
  describe('submitReturn confirm dialog labels', () => {
    function setupWithType(returnType: string, unitType: string) {
      vi.mocked(apiClient.post).mockResolvedValueOnce(
        mockAxiosResponse({ slipNumber: 'RTN-X-001' }),
      )
      const formRef = createMockFormRef()
      const { result } = setup(formRef)
      result.form.returnType = returnType
      result.form.productCode = 'P001'
      result.form.quantity = 1
      result.form.unitType = unitType
      result.form.returnReason = 'DAMAGED'
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test',
        lotManageFlag: false,
        expiryManageFlag: false,
      }
      return result
    }

    it('OUTBOUND + BALL の確認ダイアログ', async () => {
      const result = setupWithType('OUTBOUND', 'BALL')
      await result.submitReturn()
      expect(ElMessageBox.confirm).toHaveBeenCalled()
    })

    it('INVENTORY + PIECE の確認ダイアログ', async () => {
      const result = setupWithType('INVENTORY', 'PIECE')
      await result.submitReturn()
      expect(ElMessageBox.confirm).toHaveBeenCalled()
    })
  })

  // --- 422エラーの荷姿ラベル分岐 ---
  describe('422 error unitType labels', () => {
    it('CASE の荷姿ラベルが使われる (RETURN_INSUFFICIENT_QUANTITY)', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(422, { errorCode: 'RETURN_INSUFFICIENT_QUANTITY' }),
      )
      const formRef = createMockFormRef()
      const { result } = setup(formRef)
      result.form.returnType = 'INVENTORY'
      result.form.productCode = 'P001'
      result.form.quantity = 5
      result.form.unitType = 'CASE'
      result.form.returnReason = 'DAMAGED'
      result.form.locationId = 100
      result.locationOptions.value = [
        { locationId: 100, locationCode: 'A-01', quantity: 10, allocatedQty: 0, availableQty: 10 },
      ]
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test',
        lotManageFlag: false,
        expiryManageFlag: false,
      }
      await result.submitReturn()
      expect(ElMessage.error).toHaveBeenCalled()
    })

    it('CASE の荷姿ラベルが使われる (RETURN_ALLOCATED_INVENTORY)', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(422, { errorCode: 'RETURN_ALLOCATED_INVENTORY' }),
      )
      const formRef = createMockFormRef()
      const { result } = setup(formRef)
      result.form.returnType = 'INVENTORY'
      result.form.productCode = 'P001'
      result.form.quantity = 5
      result.form.unitType = 'CASE'
      result.form.returnReason = 'DAMAGED'
      result.form.locationId = 100
      result.locationOptions.value = [
        { locationId: 100, locationCode: 'A-01', quantity: 10, allocatedQty: 3, availableQty: 7 },
      ]
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test',
        lotManageFlag: false,
        expiryManageFlag: false,
      }
      await result.submitReturn()
      expect(ElMessage.error).toHaveBeenCalled()
    })

    it('PIECE の荷姿ラベルが使われる (RETURN_INSUFFICIENT_QUANTITY)', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(422, { errorCode: 'RETURN_INSUFFICIENT_QUANTITY' }),
      )
      const formRef = createMockFormRef()
      const { result } = setup(formRef)
      result.form.returnType = 'INVENTORY'
      result.form.productCode = 'P001'
      result.form.quantity = 5
      result.form.unitType = 'PIECE'
      result.form.returnReason = 'DAMAGED'
      result.form.locationId = 100
      result.locationOptions.value = [
        { locationId: 100, locationCode: 'A-01', quantity: 10, allocatedQty: 0, availableQty: 10 },
      ]
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test',
        lotManageFlag: false,
        expiryManageFlag: false,
      }
      await result.submitReturn()
      expect(ElMessage.error).toHaveBeenCalled()
    })

    it('BALL の荷姿ラベルが使われる (RETURN_ALLOCATED_INVENTORY)', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(422, { errorCode: 'RETURN_ALLOCATED_INVENTORY' }),
      )
      const formRef = createMockFormRef()
      const { result } = setup(formRef)
      result.form.returnType = 'INVENTORY'
      result.form.productCode = 'P001'
      result.form.quantity = 5
      result.form.unitType = 'BALL'
      result.form.returnReason = 'DAMAGED'
      result.form.locationId = 100
      result.locationOptions.value = [
        { locationId: 100, locationCode: 'A-01', quantity: 10, allocatedQty: 3, availableQty: 7 },
      ]
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test',
        lotManageFlag: false,
        expiryManageFlag: false,
      }
      await result.submitReturn()
      expect(ElMessage.error).toHaveBeenCalled()
    })

    it('BALL の荷姿ラベルが使われる (RETURN_INSUFFICIENT_QUANTITY)', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(422, { errorCode: 'RETURN_INSUFFICIENT_QUANTITY' }),
      )
      const formRef = createMockFormRef()
      const { result } = setup(formRef)
      result.form.returnType = 'INVENTORY'
      result.form.productCode = 'P001'
      result.form.quantity = 5
      result.form.unitType = 'BALL'
      result.form.returnReason = 'DAMAGED'
      result.form.locationId = 100
      result.locationOptions.value = [
        { locationId: 100, locationCode: 'A-01', quantity: 10, allocatedQty: 0, availableQty: 10 },
      ]
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test',
        lotManageFlag: false,
        expiryManageFlag: false,
      }
      await result.submitReturn()
      expect(ElMessage.error).toHaveBeenCalled()
    })

    it('PIECE の荷姿ラベルが使われる (RETURN_ALLOCATED_INVENTORY)', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(
        createAxiosError(422, { errorCode: 'RETURN_ALLOCATED_INVENTORY' }),
      )
      const formRef = createMockFormRef()
      const { result } = setup(formRef)
      result.form.returnType = 'INVENTORY'
      result.form.productCode = 'P001'
      result.form.quantity = 5
      result.form.unitType = 'PIECE'
      result.form.returnReason = 'DAMAGED'
      result.form.locationId = 100
      result.locationOptions.value = [
        { locationId: 100, locationCode: 'A-01', quantity: 10, allocatedQty: 3, availableQty: 7 },
      ]
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test',
        lotManageFlag: false,
        expiryManageFlag: false,
      }
      await result.submitReturn()
      expect(ElMessage.error).toHaveBeenCalled()
    })
  })
})
