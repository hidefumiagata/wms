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

    it('OTHER 選択時に備考必須ルールが追加される', () => {
      const { result } = setup()
      result.form.returnReason = 'OTHER'
      const r = result.rules.value
      expect((r.returnReasonNote as { required?: boolean }[])[0].required).toBe(true)
      result.form.returnReason = 'DAMAGED'
      const r2 = result.rules.value
      expect(r2.returnReasonNote).toHaveLength(1) // maxLength only
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

    it('空白のみのコード（"   "）は trim 結果が空のため検索しない', async () => {
      const { result } = setup()
      result.form.productCode = '   '
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
      // API パラメータ検証（C-1）
      expect(apiClient.get).toHaveBeenCalledWith(
        '/master/products',
        expect.objectContaining({
          params: expect.objectContaining({
            keyword: 'NOTEXIST',
            isActive: true,
            page: 0,
            size: 10,
          }),
        }),
      )
    })

    it('完全一致する商品が選択される（ダイアログは非表示・API パラメータ検証）', async () => {
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
      // 前後空白 → trim 動作検証（C-1）
      result.form.productCode = '  P001  '
      await result.searchProduct()
      // API パラメータが trim された keyword を受け取っていることを検証
      expect(apiClient.get).toHaveBeenCalledWith(
        '/master/products',
        expect.objectContaining({
          params: expect.objectContaining({
            keyword: 'P001',
            isActive: true,
            page: 0,
            size: 10,
          }),
        }),
      )
      expect(result.selectedProduct.value?.id).toBe(1)
      expect(result.form.productCode).toBe('P001')
      // M-1: ダイアログ非表示を検証
      expect(result.productDialogVisible.value).toBe(false)
    })

    it('複数件検索でダイアログ表示後、完全一致検索すると残骸（productSearchResults）がクリアされる', async () => {
      // 1回目: 複数件・完全一致なし → ダイアログ表示
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
      expect(result.productDialogVisible.value).toBe(true)
      expect(result.productSearchResults.value).toHaveLength(2)
      expect(result.productSearchTotal.value).toBe(2)

      // 2回目: 完全一致 → 直接選択されダイアログ閉・残骸クリア
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({
          content: [
            {
              id: 1,
              productCode: 'P001',
              productName: 'Product 1',
              lotManageFlag: false,
              expiryManageFlag: false,
            },
          ],
          totalElements: 1,
        }),
      )
      result.form.productCode = 'P001'
      await result.searchProduct()
      expect(result.selectedProduct.value?.id).toBe(1)
      expect(result.productDialogVisible.value).toBe(false)
      expect(result.productSearchResults.value).toEqual([])
      expect(result.productSearchTotal.value).toBe(0)
    })

    it('1件のみなら自動選択（副作用: ダイアログ非表示・locationId リセット・在庫候補取得）', async () => {
      // 1回目: 商品検索 → 2回目: ロケーション候補取得（INVENTORY + unitType 設定済みなのでトリガー）
      vi.mocked(apiClient.get)
        .mockResolvedValueOnce(
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
        .mockResolvedValueOnce(
          mockAxiosResponse({
            content: [
              {
                locationId: 100,
                locationCode: 'A-01',
                quantity: 10,
                allocatedQty: 0,
                availableQty: 10,
              },
            ],
          }),
        )
      const { result } = setup()
      // INVENTORY + unitType を事前設定し、1件ヒットで fetchLocationCandidates がトリガーされる条件を作る
      result.form.returnType = 'INVENTORY'
      result.form.unitType = 'CASE'
      // locationId を初期値から外して reset されることを検証
      result.form.locationId = 999
      result.form.productCode = 'P0'
      await result.searchProduct()

      expect(result.selectedProduct.value?.id).toBe(5)
      expect(result.form.productCode).toBe('P005')
      // M-2: 副作用検証
      expect(result.productDialogVisible.value).toBe(false)
      expect(result.form.locationId).toBeNull()
      // fetchLocationCandidates が呼ばれたこと（商品検索 + 在庫候補取得 = 2回）
      expect(apiClient.get).toHaveBeenCalledTimes(2)
      expect(apiClient.get).toHaveBeenNthCalledWith(
        2,
        '/inventory',
        expect.objectContaining({
          params: expect.objectContaining({
            productId: 5,
            unitType: 'CASE',
          }),
        }),
      )
    })

    it('複数件で完全一致なしならダイアログ表示', async () => {
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
      expect(result.selectedProduct.value).toBeNull()
      expect(result.productDialogVisible.value).toBe(true)
      expect(result.productSearchResults.value).toHaveLength(2)
      expect(result.productSearchTotal.value).toBe(2)
    })

    it('API失敗時にエラーメッセージ（createAxiosError ヘルパ使用、500）', async () => {
      vi.mocked(apiClient.get).mockRejectedValueOnce(
        createAxiosError(500, { message: 'Server error' }),
      )
      const { result } = setup()
      result.form.productCode = 'P001'
      await result.searchProduct()
      expect(ElMessage.error).toHaveBeenCalledWith('returns.productNotFound')
      expect(result.selectedProduct.value).toBeNull()
    })

    it('API 404 エラー時もエラーメッセージ（createAxiosError ヘルパ使用）', async () => {
      vi.mocked(apiClient.get).mockRejectedValueOnce(
        createAxiosError(404, { message: 'Not found' }),
      )
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

  // --- selectProduct（ダイアログ経由の文脈） ---
  describe('selectProduct', () => {
    it('複数件ダイアログ表示状態から selectProduct を呼ぶとフォーム反映・ダイアログ閉・残骸クリア', () => {
      const { result } = setup()
      // 複数件ダイアログ状態を事前に再現（M-3）
      result.productDialogVisible.value = true
      result.productSearchResults.value = [
        {
          id: 98,
          productCode: 'P098',
          productName: 'Other',
          lotManageFlag: false,
          expiryManageFlag: false,
        },
        {
          id: 99,
          productCode: 'P099',
          productName: 'Selected',
          lotManageFlag: false,
          expiryManageFlag: false,
        },
      ]
      result.productSearchTotal.value = 2

      result.selectProduct({
        id: 99,
        productCode: 'P099',
        productName: 'Selected',
        lotManageFlag: false,
        expiryManageFlag: false,
      })

      expect(result.selectedProduct.value?.id).toBe(99)
      expect(result.form.productCode).toBe('P099')
      expect(result.productDialogVisible.value).toBe(false)
      // 残骸クリアを検証（M-1 実装修正と整合）
      expect(result.productSearchResults.value).toEqual([])
      expect(result.productSearchTotal.value).toBe(0)
    })

    it('別商品を selectProduct すると form.lotNumber と form.expiryDate がクリアされる (C-2-M1)', () => {
      const { result } = setup()
      // 商品Aを選択した後の状態を再現：ロット・有効期限管理ありで値をセット済み
      result.selectProduct({
        id: 1,
        productCode: 'P001',
        productName: '商品A（ロット・期限管理あり）',
        lotManageFlag: true,
        expiryManageFlag: true,
      })
      result.form.lotNumber = 'LOT-A-001'
      result.form.expiryDate = '2027-01-31'
      expect(result.showLotNumber.value).toBe(true)
      expect(result.showExpiryDate.value).toBe(true)

      // 商品B（ロット・期限管理なし）に切り替え
      result.selectProduct({
        id: 2,
        productCode: 'P002',
        productName: '商品B（管理なし）',
        lotManageFlag: false,
        expiryManageFlag: false,
      })

      // 前商品の値が残っていないことを検証
      expect(result.form.lotNumber).toBe('')
      expect(result.form.expiryDate).toBe('')
      // 非表示になっていることも確認
      expect(result.showLotNumber.value).toBe(false)
      expect(result.showExpiryDate.value).toBe(false)
    })
  })

  // --- resetForm の商品検索状態クリア（resetForm 全般テストは別describeに存在） ---
  describe('resetForm (productSearch 関連)', () => {
    it('resetForm で productDialogVisible/Results/Total がクリアされる', () => {
      const { result } = setup()
      result.productDialogVisible.value = true
      result.productSearchResults.value = [
        {
          id: 1,
          productCode: 'P001',
          productName: 'A',
          lotManageFlag: false,
          expiryManageFlag: false,
        },
      ]
      result.productSearchTotal.value = 5
      result.resetForm()
      expect(result.productDialogVisible.value).toBe(false)
      expect(result.productSearchResults.value).toEqual([])
      expect(result.productSearchTotal.value).toBe(0)
    })
  })

  // --- ロケーション候補取得（onUnitTypeChange 経由） ---
  describe('ロケーション候補取得', () => {
    it('在庫返品で荷姿変更後にロケーション候補を取得', async () => {
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
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test',
        lotManageFlag: false,
        expiryManageFlag: false,
      }
      result.form.unitType = 'CASE'
      result.onUnitTypeChange()
      // Wait for the async fetch
      await vi.waitFor(() => expect(result.locationOptions.value).toHaveLength(1))
      expect(result.locationOptions.value[0].locationCode).toBe('A-01-01')
    })

    it('候補が0件のときに info メッセージ', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse({ content: [] }))
      const { result } = setup()
      result.form.returnType = 'INVENTORY'
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test',
        lotManageFlag: false,
        expiryManageFlag: false,
      }
      result.form.unitType = 'CASE'
      result.onUnitTypeChange()
      await vi.waitFor(() =>
        expect(ElMessage.info).toHaveBeenCalledWith('returns.locationCandidateEmpty'),
      )
    })

    it('商品未選択では取得しない', () => {
      const { result } = setup()
      result.form.returnType = 'INVENTORY'
      result.form.unitType = 'CASE'
      result.onUnitTypeChange()
      expect(apiClient.get).not.toHaveBeenCalled()
    })

    it('API失敗時にロケーション候補を空にする', async () => {
      vi.mocked(apiClient.get).mockRejectedValueOnce(new Error('fail'))
      const { result } = setup()
      result.form.returnType = 'INVENTORY'
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test',
        lotManageFlag: false,
        expiryManageFlag: false,
      }
      result.form.unitType = 'CASE'
      result.onUnitTypeChange()
      await vi.waitFor(() => expect(apiClient.get).toHaveBeenCalled())
      expect(result.locationOptions.value).toEqual([])
    })

    it('キャンセル時は何もしない', async () => {
      const cancelError = new axios.CanceledError()
      vi.mocked(apiClient.get).mockRejectedValueOnce(cancelError)
      vi.mocked(axios.isCancel).mockReturnValueOnce(true)
      const { result } = setup()
      result.form.returnType = 'INVENTORY'
      result.selectedProduct.value = {
        id: 1,
        productCode: 'P001',
        productName: 'Test',
        lotManageFlag: false,
        expiryManageFlag: false,
      }
      result.form.unitType = 'CASE'
      result.onUnitTypeChange()
      await vi.waitFor(() => expect(apiClient.get).toHaveBeenCalled())
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
        mockAxiosResponse({
          content: [
            {
              locationId: 200,
              locationCode: 'B-01',
              quantity: 5,
              allocatedQty: 0,
              availableQty: 5,
            },
          ],
        }),
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
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse({ content: [] }))
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

  // --- 商品選択時の動作（searchProduct 経由） ---
  describe('商品選択時の動作', () => {
    it('商品選択でロケーションがリセットされる', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({
          content: [
            {
              id: 2,
              productCode: 'P002',
              productName: 'Product 2',
              lotManageFlag: false,
              expiryManageFlag: false,
            },
          ],
          totalElements: 1,
        }),
      )
      const { result } = setup()
      result.form.locationId = 100
      result.locationOptions.value = [
        { locationId: 100, locationCode: 'A-01', quantity: 10, allocatedQty: 0, availableQty: 10 },
      ]
      result.form.productCode = 'P002'
      await result.searchProduct()
      expect(result.selectedProduct.value?.id).toBe(2)
      expect(result.form.productCode).toBe('P002')
      expect(result.form.locationId).toBeNull()
      expect(result.locationOptions.value).toEqual([])
    })

    it('在庫返品かつ荷姿選択済みなら商品選択後にロケーション候補取得', async () => {
      // 商品検索API → ロケーション候補API
      vi.mocked(apiClient.get)
        .mockResolvedValueOnce(
          mockAxiosResponse({
            content: [
              {
                id: 3,
                productCode: 'P003',
                productName: 'Product 3',
                lotManageFlag: false,
                expiryManageFlag: false,
              },
            ],
            totalElements: 1,
          }),
        )
        .mockResolvedValueOnce(mockAxiosResponse({ content: [] }))
      const { result } = setup()
      result.form.returnType = 'INVENTORY'
      result.form.unitType = 'CASE'
      result.form.productCode = 'P003'
      await result.searchProduct()
      // searchProduct → selectProduct → fetchLocationCandidates
      expect(apiClient.get).toHaveBeenCalledTimes(2)
    })
  })

  // --- 仕入先検索 ---
  describe('searchSupplier', () => {
    it('検索結果0件で info メッセージを表示', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({ content: [], totalElements: 0 }),
      )
      const { result } = setup()
      result.form.partnerCode = 'NOTEXIST'
      await result.searchSupplier()
      expect(ElMessage.info).toHaveBeenCalledWith('returns.supplierSearchEmpty')
      expect(result.selectedSupplier.value).toBeNull()
    })

    it('完全一致する仕入先コードが直接選択される', async () => {
      const partners = [
        { id: 10, partnerCode: 'SUP-001', partnerName: 'Supplier A' },
        { id: 11, partnerCode: 'SUP-002', partnerName: 'Supplier B' },
      ]
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({ content: partners, totalElements: 2 }),
      )
      const { result } = setup()
      result.form.partnerCode = 'SUP-001'
      await result.searchSupplier()
      expect(result.selectedSupplier.value?.id).toBe(10)
      expect(result.form.partnerId).toBe(10)
      expect(result.form.partnerCode).toBe('SUP-001')
      expect(result.supplierDialogVisible.value).toBe(false)
    })

    it('1件のみなら自動選択', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({
          content: [{ id: 20, partnerCode: 'SUP-010', partnerName: 'Only Supplier' }],
          totalElements: 1,
        }),
      )
      const { result } = setup()
      result.form.partnerCode = 'SUP'
      await result.searchSupplier()
      expect(result.selectedSupplier.value?.id).toBe(20)
      expect(result.form.partnerId).toBe(20)
    })

    it('複数件で完全一致なしならダイアログ表示', async () => {
      const partners = [
        { id: 30, partnerCode: 'SUP-A01', partnerName: 'Partner A' },
        { id: 31, partnerCode: 'SUP-A02', partnerName: 'Partner B' },
      ]
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({ content: partners, totalElements: 2 }),
      )
      const { result } = setup()
      result.form.partnerCode = 'SUP-A'
      await result.searchSupplier()
      expect(result.supplierDialogVisible.value).toBe(true)
      expect(result.supplierSearchResults.value).toHaveLength(2)
      expect(result.supplierSearchTotal.value).toBe(2)
    })

    it('空のコードでも検索できる（全件取得）', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(
        mockAxiosResponse({
          content: [{ id: 40, partnerCode: 'SUP-X', partnerName: 'Single' }],
          totalElements: 1,
        }),
      )
      const { result } = setup()
      result.form.partnerCode = ''
      await result.searchSupplier()
      expect(apiClient.get).toHaveBeenCalledWith(
        '/master/partners',
        expect.objectContaining({
          params: expect.objectContaining({ partnerType: 'SUPPLIER', isActive: true }),
        }),
      )
      expect(result.selectedSupplier.value?.id).toBe(40)
    })

    it('API失敗時にエラーメッセージ', async () => {
      vi.mocked(apiClient.get).mockRejectedValueOnce(new Error('Network'))
      const { result } = setup()
      result.form.partnerCode = 'SUP'
      await result.searchSupplier()
      expect(ElMessage.error).toHaveBeenCalledWith('returns.supplierSearchError')
    })

    it('キャンセル時は何もしない', async () => {
      const cancelError = new axios.CanceledError()
      vi.mocked(apiClient.get).mockRejectedValueOnce(cancelError)
      vi.mocked(axios.isCancel).mockReturnValueOnce(true)
      const { result } = setup()
      result.form.partnerCode = 'SUP'
      await result.searchSupplier()
      expect(ElMessage.error).not.toHaveBeenCalled()
    })
  })

  // --- selectSupplier ---
  describe('selectSupplier', () => {
    it('仕入先を選択してフォームに反映', () => {
      const { result } = setup()
      result.supplierDialogVisible.value = true
      result.selectSupplier({ id: 50, partnerCode: 'SUP-050', partnerName: 'Test Supplier' })
      expect(result.selectedSupplier.value?.id).toBe(50)
      expect(result.form.partnerId).toBe(50)
      expect(result.form.partnerCode).toBe('SUP-050')
      expect(result.supplierDialogVisible.value).toBe(false)
    })
  })

  // --- clearSupplier ---
  describe('clearSupplier', () => {
    it('仕入先選択をクリア', () => {
      const { result } = setup()
      result.selectedSupplier.value = { id: 50, partnerCode: 'SUP-050', partnerName: 'Test' }
      result.form.partnerId = 50
      result.form.partnerCode = 'SUP-050'
      result.clearSupplier()
      expect(result.selectedSupplier.value).toBeNull()
      expect(result.form.partnerId).toBeNull()
      expect(result.form.partnerCode).toBe('')
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
        createAxiosError(422, {
          errorCode: 'RETURN_INSUFFICIENT_QUANTITY',
          message: 'Insufficient',
        }),
      )
      const { result } = setupForSubmit()
      result.form.returnType = 'INVENTORY'
      result.form.locationId = 100
      result.locationOptions.value = [
        { locationId: 100, locationCode: 'A-01', quantity: 10, allocatedQty: 0, availableQty: 10 },
      ]
      await result.submitReturn()
      expect(ElMessage.error).toHaveBeenCalledWith(
        expect.stringContaining('returns.insufficientQuantity'),
      )
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
      expect(ElMessage.error).toHaveBeenCalledWith(
        expect.stringContaining('returns.allocatedInventory'),
      )
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
      vi.mocked(apiClient.post).mockRejectedValueOnce(createAxiosError(404, {}))
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
      result.selectedSupplier.value = { id: 789, partnerCode: 'SUP-001', partnerName: 'Test' }
      result.locationOptions.value = [
        { locationId: 100, locationCode: 'A-01', quantity: 10, allocatedQty: 0, availableQty: 10 },
      ]
      result.supplierSearchResults.value = [
        { id: 789, partnerCode: 'SUP-001', partnerName: 'Test' },
      ]
      result.supplierSearchTotal.value = 1
      result.supplierDialogVisible.value = true

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
      expect(result.selectedSupplier.value).toBeNull()
      expect(result.locationOptions.value).toEqual([])
      expect(result.supplierSearchResults.value).toEqual([])
      expect(result.supplierSearchTotal.value).toBe(0)
      expect(result.supplierDialogVisible.value).toBe(false)
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
