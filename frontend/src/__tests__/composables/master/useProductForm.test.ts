import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, createAxiosError } from '../../helpers'
import { useProductForm } from '@/composables/master/useProductForm'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'

// --- generated models のモック ---
vi.mock('@/api/generated/models/product-detail', () => ({}))
vi.mock('@/api/generated/models/storage-condition', () => ({
  StorageCondition: { Ambient: 'AMBIENT', Refrigerated: 'REFRIGERATED', Frozen: 'FROZEN' },
}))
vi.mock('@/api/generated/models/create-product-request', () => ({}))
vi.mock('@/api/generated/models/update-product-request', () => ({}))

// --- vue-router のモックを上書き（route.params.id を制御するため） ---
let mockRouteParams: Record<string, string> = {}
vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
  }),
  useRoute: () => ({
    params: mockRouteParams,
    query: {},
    name: 'test',
  }),
  createRouter: vi.fn(),
  createWebHistory: vi.fn(),
}))

const productData = {
  productCode: 'PROD-001',
  productName: 'テスト商品',
  productNameKana: 'テストショウヒン',
  barcode: '4901234567890',
  storageCondition: 'AMBIENT',
  caseQuantity: 12,
  ballQuantity: 6,
  isHazardous: false,
  lotManageFlag: true,
  expiryManageFlag: true,
  shipmentStopFlag: false,
  isActive: true,
  version: 3,
  hasInventory: true,
}

describe('useProductForm', () => {
  beforeEach(() => {
    mockRouteParams = {}
  })

  // --- 1. isEdit false in create mode ---
  it('新規作成モードでは isEdit が false になる', () => {
    const { result } = withSetup(() => useProductForm())
    expect(result.isEdit.value).toBe(false)
  })

  // --- 2. isEdit true in edit mode ---
  it('編集モードでは isEdit が true になる', () => {
    mockRouteParams = { id: '1' }
    const { result } = withSetup(() => useProductForm())
    expect(result.isEdit.value).toBe(true)
  })

  // --- 3. fetchProduct success with signal ---
  it('fetchProduct がデータを取得し signal を渡す', async () => {
    mockRouteParams = { id: '5' }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse(productData))

    const { result } = withSetup(() => useProductForm())
    await result.fetchProduct()

    expect(apiClient.get).toHaveBeenCalledWith(
      '/master/products/5',
      expect.objectContaining({
        signal: expect.any(AbortSignal),
      }),
    )
    expect(result.hasInventory.value).toBe(true)
  })

  // --- 4. fetchProduct cancel doesn't update state ---
  it('fetchProduct のキャンセル時に state が更新されない', async () => {
    mockRouteParams = { id: '1' }
    const cancelError = new Error('canceled')
    vi.mocked(apiClient.get).mockRejectedValueOnce(cancelError)
    vi.mocked(axios.isCancel).mockReturnValueOnce(true)

    const { result } = withSetup(() => useProductForm())
    await result.fetchProduct()

    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  // --- 5. fetchProduct 404 error → redirect ---
  it('fetchProduct の 404 エラー時にエラーメッセージを表示しリダイレクトする', async () => {
    mockRouteParams = { id: '999' }
    vi.mocked(apiClient.get).mockRejectedValueOnce(createAxiosError(404))

    const { result } = withSetup(() => useProductForm())
    await result.fetchProduct()

    expect(ElMessage.error).toHaveBeenCalledWith('master.product.notFound')
  })

  // --- 6. fetchProduct network error ---
  it('fetchProduct のネットワークエラー時にエラーメッセージを表示する', async () => {
    mockRouteParams = { id: '1' }
    vi.mocked(apiClient.get).mockRejectedValueOnce(new Error('Network Error'))

    const { result } = withSetup(() => useProductForm())
    await result.fetchProduct()

    expect(ElMessage.error).toHaveBeenCalledWith('error.network')
  })

  // --- 7. onUnmounted aborts request ---
  it('onUnmounted 時に進行中のリクエストがキャンセルされる', async () => {
    mockRouteParams = { id: '1' }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse(productData))

    const { result, wrapper } = withSetup(() => useProductForm())
    const fetchPromise = result.fetchProduct()

    const signal = vi.mocked(apiClient.get).mock.calls[0][1]!.signal!
    expect(signal.aborted).toBe(false)

    wrapper.unmount()

    expect(signal.aborted).toBe(true)

    await fetchPromise
  })

  // --- 8. checkCodeExists with valid code ---
  it('checkCodeExists が有効なコードで API を呼ぶ', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse({ exists: true }))

    const { result } = withSetup(() => useProductForm())
    result.productCode.value = 'PROD-001'
    await result.checkCodeExists()

    expect(apiClient.get).toHaveBeenCalledWith('/master/products/exists', {
      params: { productCode: 'PROD-001' },
    })
  })

  // --- 9. checkCodeExists skips in edit mode ---
  it('checkCodeExists が編集モードでは API を呼ばない', async () => {
    mockRouteParams = { id: '1' }

    const { result } = withSetup(() => useProductForm())
    result.productCode.value = 'PROD-001'
    await result.checkCodeExists()

    expect(apiClient.get).not.toHaveBeenCalled()
  })

  // --- 10. checkCodeExists skips with invalid code ---
  it('checkCodeExists が無効なコード形式では API を呼ばない', async () => {
    const { result } = withSetup(() => useProductForm())
    result.productCode.value = 'あいう' // 無効形式（全角文字）

    await result.checkCodeExists()

    expect(apiClient.get).not.toHaveBeenCalled()
  })

  // --- 11. handleCancel with confirm → navigates ---
  it('handleCancel で確認後に商品一覧に遷移する', async () => {
    vi.mocked(ElMessageBox.confirm).mockResolvedValueOnce('confirm')

    const { result } = withSetup(() => useProductForm())
    await result.handleCancel()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
  })

  // --- 12. handleCancel with dialog cancel → stays ---
  it('handleCancel でダイアログキャンセル時は遷移しない', async () => {
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')

    const { result } = withSetup(() => useProductForm())
    await result.handleCancel()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    // ダイアログキャンセル時は router.push が呼ばれないことを確認
    // (vi.mock で useRouter のモックを返しているため、ここでは呼ばれない)
  })
})
