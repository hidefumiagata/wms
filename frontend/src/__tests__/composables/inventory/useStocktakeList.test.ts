import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { ElMessage } from 'element-plus'
import { withSetup, mockAxiosResponse, flushPromises } from '../../helpers'
import { useStocktakeList } from '@/composables/inventory/useStocktakeList'
import { useWarehouseStore } from '@/stores/warehouse'
import { useAuthStore } from '@/stores/auth'
import { mockRouter } from '../../setup'

vi.mock('@/api/generated/models/stocktake-summary', () => ({}))
vi.mock('@/api/generated/models/stocktake-summary-page-response', () => ({}))

describe('useStocktakeList', () => {
  const createMockResponse = () => ({
    content: [{ id: 1, stocktakeNumber: 'ST-001', status: 'IN_PROGRESS' }],
    totalElements: 1,
    totalPages: 1,
  })

  beforeEach(() => {
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createMockResponse()))
  })

  it('fetchList が日付範囲付きでリクエストする', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useStocktakeList()
    })

    await result.fetchList()

    expect(apiClient.get).toHaveBeenCalledWith(
      '/inventory/stocktakes',
      expect.objectContaining({
        params: expect.objectContaining({
          warehouseId: 1,
          dateFrom: expect.any(String),
          dateTo: expect.any(String),
        }),
        signal: expect.any(AbortSignal),
      }),
    )
    expect(result.items.value).toHaveLength(1)
  })

  it('fetchList が日付範囲不正時にエラーを表示する', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useStocktakeList()
    })

    result.searchForm.dateFrom = '2025-12-31'
    result.searchForm.dateTo = '2025-01-01'

    await result.fetchList()

    expect(ElMessage.error).toHaveBeenCalled()
    expect(apiClient.get).not.toHaveBeenCalled()
  })

  it('isManager がロールに基づいて判定される', () => {
    const { result } = withSetup(() => {
      const auth = useAuthStore()
      auth.user = {
        userId: 1,
        userCode: 'wm1',
        fullName: 'Manager',
        role: 'WAREHOUSE_MANAGER',
        passwordChangeRequired: false,
      }
      return useStocktakeList()
    })

    expect(result.isManager.value).toBe(true)
  })

  it('isViewer がロールに基づいて判定される', () => {
    const { result } = withSetup(() => {
      const auth = useAuthStore()
      auth.user = {
        userId: 1,
        userCode: 'v1',
        fullName: 'Viewer',
        role: 'VIEWER',
        passwordChangeRequired: false,
      }
      return useStocktakeList()
    })

    expect(result.isViewer.value).toBe(true)
  })

  it('倉庫切替時にページを1にリセットしてfetchListを呼ぶ', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useStocktakeList()
    })

    result.page.value = 3
    vi.mocked(apiClient.get).mockClear()

    const ws = useWarehouseStore()
    ws.selectedWarehouseId = 2
    await flushPromises()

    expect(result.page.value).toBe(1)
    expect(apiClient.get).toHaveBeenCalledWith(
      '/inventory/stocktakes',
      expect.objectContaining({
        params: expect.objectContaining({ warehouseId: 2 }),
      }),
    )
  })

  it('倉庫がnullになった場合はfetchListを呼ばない', async () => {
    withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useStocktakeList()
    })

    vi.mocked(apiClient.get).mockClear()

    const ws = useWarehouseStore()
    ws.selectedWarehouseId = null
    await flushPromises()

    expect(apiClient.get).not.toHaveBeenCalled()
  })

  it('handleSearch がページを1にリセットする', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useStocktakeList()
    })

    result.page.value = 5
    result.handleSearch()
    await flushPromises()

    expect(result.page.value).toBe(1)
  })

  it('handleReset が検索条件をデフォルトに戻す', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useStocktakeList()
    })

    result.searchForm.status = 'COMPLETED'
    result.page.value = 3

    result.handleReset()
    await flushPromises()

    expect(result.searchForm.status).toBeNull()
    expect(result.page.value).toBe(1)
    expect(result.searchForm.dateFrom).toBeTruthy()
    expect(result.searchForm.dateTo).toBeTruthy()
  })

  it('handlePageChange がページを変更する', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useStocktakeList()
    })

    result.handlePageChange(3)
    await flushPromises()

    expect(result.page.value).toBe(3)
  })

  it('formatStocktakeDate が日付をフォーマットする', () => {
    const { result } = withSetup(() => useStocktakeList())

    const formatted = result.formatStocktakeDate('2025-03-25')
    expect(formatted).toBeTruthy()

    expect(result.formatStocktakeDate('')).toBe('')
  })

  it('goToDetail が詳細画面に遷移する', () => {
    const { result } = withSetup(() => useStocktakeList())
    result.goToDetail({ id: 5 } as never)

    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'stocktake-detail', params: { id: 5 } })
  })

  it('goToConfirm が確認画面に遷移する', () => {
    const { result } = withSetup(() => useStocktakeList())
    result.goToConfirm({ id: 5 } as never)

    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'stocktake-confirm', params: { id: 5 } })
  })
})
