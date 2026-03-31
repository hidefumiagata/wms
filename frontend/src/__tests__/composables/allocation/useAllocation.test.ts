import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { withSetup, mockAxiosResponse, createAxiosError, flushPromises } from '../../helpers'
import { useAllocation } from '@/composables/allocation/useAllocation'
import { useWarehouseStore } from '@/stores/warehouse'

describe('useAllocation', () => {
  const createOrdersResponse = () => ({
    content: [
      { id: 1, slipNumber: 'OUT-001', plannedDate: '2026-03-20', partnerName: '商事A', lineCount: 3, status: 'ORDERED' },
      { id: 2, slipNumber: 'OUT-002', plannedDate: '2026-03-21', partnerName: '物流B', lineCount: 5, status: 'PARTIAL_ALLOCATED' },
    ],
    totalElements: 2,
    totalPages: 1,
    page: 0,
    size: 20,
  })

  const createAllocatedResponse = () => ({
    content: [
      { id: 4, slipNumber: 'OUT-004', plannedDate: '2026-03-18', partnerName: '商事C', lineCount: 2, allocatedLineCount: 2, status: 'ALLOCATED' },
    ],
    totalElements: 1,
    totalPages: 1,
    page: 0,
    size: 20,
  })

  const createExecutionResult = () => ({
    allocatedCount: 2,
    allocatedSlips: [
      { outboundSlipId: 1, slipNumber: 'OUT-001', status: 'ALLOCATED', allocatedLines: [] },
    ],
    unpackInstructions: [
      { id: 101, productCode: 'PRD-002', productName: '商品B', fromUnitType: 'CASE', toUnitType: 'PIECE', quantity: 24, status: 'INSTRUCTED' },
    ],
    unallocatedLines: [
      { outboundSlipId: 3, slipNumber: 'OUT-003', lineNo: 2, productCode: 'PRD-005', productName: '商品E', shortageQty: 10 },
    ],
  })

  beforeEach(() => {
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createOrdersResponse()))
  })

  // ============================================================
  // ヘルパー関数
  // ============================================================

  describe('formatDate', () => {
    it('yyyy-MM-dd を yyyy/MM/dd に変換する', () => {
      const { result } = withSetup(() => useAllocation())
      expect(result.formatDate('2026-03-20')).toBe('2026/03/20')
    })

    it('undefined の場合は空文字を返す', () => {
      const { result } = withSetup(() => useAllocation())
      expect(result.formatDate(undefined)).toBe('')
    })

    it('空文字の場合は空文字を返す', () => {
      const { result } = withSetup(() => useAllocation())
      expect(result.formatDate('')).toBe('')
    })
  })

  describe('orderStatusTagType / orderStatusLabel', () => {
    it('ORDERED は info タグ', () => {
      const { result } = withSetup(() => useAllocation())
      expect(result.orderStatusTagType('ORDERED')).toBe('info')
    })

    it('PARTIAL_ALLOCATED は warning タグ', () => {
      const { result } = withSetup(() => useAllocation())
      expect(result.orderStatusTagType('PARTIAL_ALLOCATED')).toBe('warning')
    })

    it('不明なステータスは info タグ', () => {
      const { result } = withSetup(() => useAllocation())
      expect(result.orderStatusTagType('UNKNOWN')).toBe('info')
    })

    it('ORDERED のラベルを返す', () => {
      const { result } = withSetup(() => useAllocation())
      expect(result.orderStatusLabel('ORDERED')).toBeTruthy()
    })

    it('PARTIAL_ALLOCATED のラベルを返す', () => {
      const { result } = withSetup(() => useAllocation())
      expect(result.orderStatusLabel('PARTIAL_ALLOCATED')).toBeTruthy()
    })

    it('不明なステータスはそのまま返す', () => {
      const { result } = withSetup(() => useAllocation())
      expect(result.orderStatusLabel('UNKNOWN')).toBe('UNKNOWN')
    })
  })

  describe('allocatedStatusTagType / allocatedStatusLabel', () => {
    it('ALLOCATED は success タグ', () => {
      const { result } = withSetup(() => useAllocation())
      expect(result.allocatedStatusTagType('ALLOCATED')).toBe('success')
    })

    it('PARTIAL_ALLOCATED は warning タグ', () => {
      const { result } = withSetup(() => useAllocation())
      expect(result.allocatedStatusTagType('PARTIAL_ALLOCATED')).toBe('warning')
    })

    it('不明なステータスは info タグ', () => {
      const { result } = withSetup(() => useAllocation())
      expect(result.allocatedStatusTagType('UNKNOWN')).toBe('info')
    })

    it('ALLOCATED のラベルを返す', () => {
      const { result } = withSetup(() => useAllocation())
      expect(result.allocatedStatusLabel('ALLOCATED')).toBeTruthy()
    })

    it('PARTIAL_ALLOCATED のラベルを返す', () => {
      const { result } = withSetup(() => useAllocation())
      expect(result.allocatedStatusLabel('PARTIAL_ALLOCATED')).toBeTruthy()
    })

    it('不明なステータスはそのまま返す', () => {
      const { result } = withSetup(() => useAllocation())
      expect(result.allocatedStatusLabel('UNKNOWN')).toBe('UNKNOWN')
    })
  })

  describe('shippingDateToMin', () => {
    it('shippingDateFrom が設定されている場合はその値を返す', () => {
      const { result } = withSetup(() => useAllocation())
      result.searchForm.shippingDateFrom = '2026-03-01'
      expect(result.shippingDateToMin.value).toBe('2026-03-01')
    })

    it('shippingDateFrom が null の場合は null を返す', () => {
      const { result } = withSetup(() => useAllocation())
      expect(result.shippingDateToMin.value).toBeNull()
    })
  })

  // ============================================================
  // Tab1: 引当対象受注一覧
  // ============================================================

  describe('fetchOrders', () => {
    it('warehouseId と signal を含めて GET /allocation/orders を呼ぶ', async () => {
      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 42
        return useAllocation()
      })

      await result.fetchOrders()

      expect(apiClient.get).toHaveBeenCalledWith(
        '/allocation/orders',
        expect.objectContaining({
          params: expect.objectContaining({
            warehouseId: 42,
            page: 0,
            size: 20,
            sort: 'plannedDate,asc',
            status: ['ORDERED', 'PARTIAL_ALLOCATED'],
          }),
          signal: expect.any(AbortSignal),
        }),
      )
      expect(result.orders.value).toHaveLength(2)
      expect(result.ordersTotal.value).toBe(2)
    })

    it('検索条件がパラメータに含まれる', async () => {
      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      result.searchForm.shippingDateFrom = '2026-03-01'
      result.searchForm.shippingDateTo = '2026-03-31'
      result.searchForm.partnerName = 'テスト'

      await result.fetchOrders()

      expect(apiClient.get).toHaveBeenCalledWith(
        '/allocation/orders',
        expect.objectContaining({
          params: expect.objectContaining({
            shippingDateFrom: '2026-03-01',
            shippingDateTo: '2026-03-31',
            partnerName: 'テスト',
          }),
        }),
      )
    })

    it('ステータス空の場合は status パラメータを含めない', async () => {
      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      result.searchForm.status = []
      await result.fetchOrders()

      const params = vi.mocked(apiClient.get).mock.calls[0][1]!.params as Record<string, unknown>
      expect(params.status).toBeUndefined()
    })

    it('ネットワークエラー時にエラーメッセージを表示する', async () => {
      vi.mocked(apiClient.get).mockRejectedValue(new Error('Network Error'))

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      await result.fetchOrders()

      expect(ElMessage.error).toHaveBeenCalled()
      expect(result.orders.value).toEqual([])
      expect(result.ordersTotal.value).toBe(0)
    })

    it('APIエラー時にfetchErrorメッセージを表示する', async () => {
      vi.mocked(apiClient.get).mockRejectedValue(createAxiosError(400))

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      await result.fetchOrders()

      expect(ElMessage.error).toHaveBeenCalled()
      expect(result.orders.value).toEqual([])
    })

    it('キャンセル時はエラー表示しない', async () => {
      const cancelError = new axios.CanceledError()
      vi.mocked(apiClient.get).mockRejectedValue(cancelError)
      vi.mocked(axios.isCancel).mockReturnValueOnce(true)

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      await result.fetchOrders()

      expect(ElMessage.error).not.toHaveBeenCalled()
    })

    it('selectedIds がフェッチ時にクリアされる', async () => {
      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      result.selectedIds.value = [1, 2, 3]
      await result.fetchOrders()

      expect(result.selectedIds.value).toEqual([])
    })
  })

  describe('onUnmounted', () => {
    it('アンマウント時にリクエストがキャンセルされる', async () => {
      const { result, wrapper } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      const fetchPromise = result.fetchOrders()
      const signal = vi.mocked(apiClient.get).mock.calls[0][1]!.signal!
      expect(signal.aborted).toBe(false)

      wrapper.unmount()
      expect(signal.aborted).toBe(true)

      await fetchPromise
    })
  })

  describe('handleSearch', () => {
    it('ページを1にリセットしてfetchOrdersを呼ぶ', async () => {
      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      result.ordersPage.value = 5
      result.handleSearch()
      await flushPromises()

      expect(result.ordersPage.value).toBe(1)
      expect(apiClient.get).toHaveBeenCalled()
    })
  })

  describe('handleReset', () => {
    it('検索条件をデフォルトに戻す', async () => {
      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      result.searchForm.status = ['ORDERED']
      result.searchForm.shippingDateFrom = '2026-01-01'
      result.searchForm.shippingDateTo = '2026-12-31'
      result.searchForm.partnerName = '検索テスト'
      result.ordersPage.value = 3

      result.handleReset()
      await flushPromises()

      expect(result.searchForm.status).toEqual(['ORDERED', 'PARTIAL_ALLOCATED'])
      expect(result.searchForm.shippingDateFrom).toBeNull()
      expect(result.searchForm.shippingDateTo).toBeNull()
      expect(result.searchForm.partnerName).toBe('')
      expect(result.ordersPage.value).toBe(1)
    })
  })

  describe('handleOrdersPageChange / handleOrdersSizeChange', () => {
    it('ページ変更でfetchOrdersが呼ばれる', async () => {
      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      vi.mocked(apiClient.get).mockClear()
      result.handleOrdersPageChange(3)
      await flushPromises()

      expect(result.ordersPage.value).toBe(3)
      expect(apiClient.get).toHaveBeenCalled()
    })

    it('サイズ変更でページが1にリセットされる', async () => {
      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      result.ordersPage.value = 3
      vi.mocked(apiClient.get).mockClear()
      result.handleOrdersSizeChange(50)
      await flushPromises()

      expect(result.ordersPageSize.value).toBe(50)
      expect(result.ordersPage.value).toBe(1)
      expect(apiClient.get).toHaveBeenCalled()
    })
  })

  describe('handleSelectionChange', () => {
    it('選択行からIDリストを更新する', () => {
      const { result } = withSetup(() => useAllocation())

      result.handleSelectionChange([
        { id: 1, slipNumber: 'OUT-001' } as never,
        { id: 2, slipNumber: 'OUT-002' } as never,
      ])

      expect(result.selectedIds.value).toEqual([1, 2])
    })

    it('id が undefined の行はフィルタされる', () => {
      const { result } = withSetup(() => useAllocation())

      result.handleSelectionChange([
        { id: 1, slipNumber: 'OUT-001' } as never,
        { slipNumber: 'OUT-002' } as never,
      ])

      expect(result.selectedIds.value).toEqual([1])
    })
  })

  // ============================================================
  // 倉庫切替
  // ============================================================

  describe('倉庫切替', () => {
    it('execute タブ時に fetchOrders が呼ばれる', async () => {
      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      vi.mocked(apiClient.get).mockClear()
      result.activeTab.value = 'execute'

      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 2
      await flushPromises()

      expect(result.ordersPage.value).toBe(1)
      expect(apiClient.get).toHaveBeenCalledWith(
        '/allocation/orders',
        expect.objectContaining({
          params: expect.objectContaining({ warehouseId: 2 }),
        }),
      )
    })

    it('allocated タブ時に fetchAllocatedOrders が呼ばれる', async () => {
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createAllocatedResponse()))

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      result.activeTab.value = 'allocated'
      vi.mocked(apiClient.get).mockClear()
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createAllocatedResponse()))

      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 2
      await flushPromises()

      expect(apiClient.get).toHaveBeenCalledWith(
        '/allocation/allocated-orders',
        expect.objectContaining({
          params: expect.objectContaining({ page: 0 }),
        }),
      )
    })

    it('倉庫がnullの場合はfetchしない', async () => {
      withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      vi.mocked(apiClient.get).mockClear()

      const ws = useWarehouseStore()
      ws.selectedWarehouseId = null
      await flushPromises()

      expect(apiClient.get).not.toHaveBeenCalled()
    })
  })

  // ============================================================
  // タブ切替
  // ============================================================

  describe('handleTabChange', () => {
    it('allocated タブに切り替えると fetchAllocatedOrders が呼ばれる', async () => {
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createAllocatedResponse()))

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      vi.mocked(apiClient.get).mockClear()
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createAllocatedResponse()))

      result.handleTabChange('allocated')
      await flushPromises()

      expect(result.activeTab.value).toBe('allocated')
      expect(apiClient.get).toHaveBeenCalledWith(
        '/allocation/allocated-orders',
        expect.anything(),
      )
    })

    it('execute タブに切り替えても fetchAllocatedOrders は呼ばれない', async () => {
      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      vi.mocked(apiClient.get).mockClear()
      result.handleTabChange('execute')
      await flushPromises()

      expect(result.activeTab.value).toBe('execute')
      expect(apiClient.get).not.toHaveBeenCalled()
    })
  })

  // ============================================================
  // 引当実行
  // ============================================================

  describe('handleExecute', () => {
    it('選択された受注IDで POST /allocation/execute を呼ぶ', async () => {
      const execResult = createExecutionResult()
      vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse(execResult))

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      result.selectedIds.value = [1, 2]
      await result.handleExecute()

      expect(apiClient.post).toHaveBeenCalledWith('/allocation/execute', {
        outboundSlipIds: [1, 2],
      })
      expect(result.executionResult.value).toEqual(execResult)
      expect(ElMessage.success).toHaveBeenCalled()
    })

    it('未引当明細がある場合に警告メッセージを表示する', async () => {
      const execResult = createExecutionResult()
      vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse(execResult))

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      result.selectedIds.value = [1]
      await result.handleExecute()

      expect(ElMessage.warning).toHaveBeenCalled()
    })

    it('未引当明細がない場合は警告メッセージを表示しない', async () => {
      const execResult = { ...createExecutionResult(), unallocatedLines: [] }
      vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse(execResult))

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      result.selectedIds.value = [1]
      await result.handleExecute()

      expect(ElMessage.warning).not.toHaveBeenCalled()
    })

    it('未選択時に警告メッセージを表示する', async () => {
      const { result } = withSetup(() => useAllocation())
      result.selectedIds.value = []
      await result.handleExecute()

      expect(ElMessage.warning).toHaveBeenCalled()
      expect(apiClient.post).not.toHaveBeenCalled()
    })

    it('ネットワークエラー時にエラーメッセージを表示する', async () => {
      vi.mocked(apiClient.post).mockRejectedValue(new Error('Network Error'))

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      result.selectedIds.value = [1]
      await result.handleExecute()

      expect(ElMessage.error).toHaveBeenCalled()
      expect(result.executing.value).toBe(false)
    })

    it('APIエラー時にエラーメッセージを表示する', async () => {
      vi.mocked(apiClient.post).mockRejectedValue(createAxiosError(400, { message: 'Invalid' }))

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      result.selectedIds.value = [1]
      await result.handleExecute()

      expect(ElMessage.error).toHaveBeenCalled()
      expect(result.executing.value).toBe(false)
    })

    it('APIエラーにメッセージがある場合はそれを表示する', async () => {
      vi.mocked(apiClient.post).mockRejectedValue(createAxiosError(400, { message: 'ステータスが不正です' }))

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      result.selectedIds.value = [1]
      await result.handleExecute()

      expect(ElMessage.error).toHaveBeenCalled()
    })

    it('allocatedCount/unallocatedLines が undefined の場合はデフォルト値を使う', async () => {
      const execResult = { allocatedSlips: [], unpackInstructions: [] }
      vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse(execResult))

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      result.selectedIds.value = [1]
      await result.handleExecute()

      expect(ElMessage.success).toHaveBeenCalled()
      expect(ElMessage.warning).not.toHaveBeenCalled()
    })

    it('APIエラーにメッセージがない場合は空文字で表示する', async () => {
      vi.mocked(apiClient.post).mockRejectedValue(createAxiosError(400))

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      result.selectedIds.value = [1]
      await result.handleExecute()

      expect(ElMessage.error).toHaveBeenCalled()
    })

    it('実行後に fetchOrders を再呼び出しする', async () => {
      vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse(createExecutionResult()))

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      result.selectedIds.value = [1]
      vi.mocked(apiClient.get).mockClear()
      await result.handleExecute()

      expect(apiClient.get).toHaveBeenCalledWith(
        '/allocation/orders',
        expect.anything(),
      )
    })
  })

  // ============================================================
  // ばらし完了
  // ============================================================

  describe('handleUnpackComplete', () => {
    it('PUT で完了APIを呼びステータスをローカル更新する', async () => {
      vi.mocked(apiClient.put).mockResolvedValue(mockAxiosResponse({ id: 101, status: 'COMPLETED' }))
      vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse(createExecutionResult()))

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      // 引当実行して結果をセット
      result.selectedIds.value = [1]
      await result.handleExecute()

      vi.mocked(apiClient.put).mockClear()
      vi.mocked(apiClient.put).mockResolvedValue(mockAxiosResponse({ id: 101, status: 'COMPLETED' }))

      const instruction = result.executionResult.value!.unpackInstructions![0]
      await result.handleUnpackComplete(instruction)

      expect(apiClient.put).toHaveBeenCalledWith('/allocation/unpack-instructions/101/complete')
      expect(ElMessage.success).toHaveBeenCalled()
      expect(instruction.status).toBe('COMPLETED')
    })

    it('400エラー時に「既に完了済み」メッセージを表示する', async () => {
      vi.mocked(apiClient.put).mockRejectedValue(createAxiosError(400))

      const { result } = withSetup(() => useAllocation())

      await result.handleUnpackComplete({ id: 101, status: 'INSTRUCTED' } as never)

      expect(ElMessage.error).toHaveBeenCalled()
    })

    it('ネットワークエラー時にエラーメッセージを表示する', async () => {
      vi.mocked(apiClient.put).mockRejectedValue(new Error('Network Error'))

      const { result } = withSetup(() => useAllocation())

      await result.handleUnpackComplete({ id: 101, status: 'INSTRUCTED' } as never)

      expect(ElMessage.error).toHaveBeenCalled()
    })

    it('その他のAPIエラー時にunpackCompleteErrorを表示する', async () => {
      vi.mocked(apiClient.put).mockRejectedValue(createAxiosError(500))

      const { result } = withSetup(() => useAllocation())

      await result.handleUnpackComplete({ id: 101, status: 'INSTRUCTED' } as never)

      expect(ElMessage.error).toHaveBeenCalled()
    })

    it('executionResult が null の場合でも完了APIは呼ばれる', async () => {
      vi.mocked(apiClient.put).mockResolvedValue(mockAxiosResponse({ id: 101, status: 'COMPLETED' }))

      const { result } = withSetup(() => useAllocation())

      await result.handleUnpackComplete({ id: 101, status: 'INSTRUCTED' } as never)

      expect(apiClient.put).toHaveBeenCalledWith('/allocation/unpack-instructions/101/complete')
      expect(ElMessage.success).toHaveBeenCalled()
    })

    it('ローカル更新で対象が見つからない場合でもエラーにならない', async () => {
      vi.mocked(apiClient.put).mockResolvedValue(mockAxiosResponse({ id: 999, status: 'COMPLETED' }))
      vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse(createExecutionResult()))

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      result.selectedIds.value = [1]
      await result.handleExecute()

      // 存在しないIDで完了を呼ぶ
      await result.handleUnpackComplete({ id: 999, status: 'INSTRUCTED' } as never)

      expect(ElMessage.success).toHaveBeenCalled()
    })
  })

  // ============================================================
  // Tab2: 引当済み一覧
  // ============================================================

  describe('fetchAllocatedOrders', () => {
    it('warehouseId を含めて GET /allocation/allocated-orders を呼ぶ', async () => {
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createAllocatedResponse()))

      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 42
        return useAllocation()
      })

      await result.fetchAllocatedOrders()

      expect(apiClient.get).toHaveBeenCalledWith(
        '/allocation/allocated-orders',
        expect.objectContaining({
          params: expect.objectContaining({
            warehouseId: 42,
            page: 0,
            size: 20,
            sort: 'plannedDate,asc',
          }),
          signal: expect.any(AbortSignal),
        }),
      )
      expect(result.allocatedOrders.value).toHaveLength(1)
      expect(result.allocatedTotal.value).toBe(1)
    })

    it('ネットワークエラー時にエラーメッセージを表示する', async () => {
      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      vi.mocked(apiClient.get).mockImplementation(() => Promise.reject(new Error('Network Error')))
      await result.fetchAllocatedOrders()

      expect(ElMessage.error).toHaveBeenCalled()
      expect(result.allocatedOrders.value).toEqual([])
      expect(result.allocatedTotal.value).toBe(0)
    })

    it('APIエラー時にallocatedFetchErrorを表示する', async () => {
      const { result } = withSetup(() => {
        const ws = useWarehouseStore()
        ws.selectedWarehouseId = 1
        return useAllocation()
      })

      vi.mocked(apiClient.get).mockImplementation(() => Promise.reject(createAxiosError(400)))
      await result.fetchAllocatedOrders()

      expect(ElMessage.error).toHaveBeenCalled()
    })

    it('キャンセル時はエラー表示しない', async () => {
      const cancelError = new axios.CanceledError()
      vi.mocked(apiClient.get).mockRejectedValue(cancelError)
      vi.mocked(axios.isCancel).mockReturnValueOnce(true)

      const { result } = withSetup(() => useAllocation())

      await result.fetchAllocatedOrders()

      expect(ElMessage.error).not.toHaveBeenCalled()
    })

    it('content/totalElements が undefined の場合はデフォルト値を使う', async () => {
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse({}))

      const { result } = withSetup(() => useAllocation())

      await result.fetchAllocatedOrders()

      expect(result.allocatedOrders.value).toEqual([])
      expect(result.allocatedTotal.value).toBe(0)
    })

    it('2回目の呼び出しで前回のリクエストがキャンセルされる', async () => {
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createAllocatedResponse()))

      const { result } = withSetup(() => useAllocation())

      // 1回目
      const firstPromise = result.fetchAllocatedOrders()
      const firstSignal = vi.mocked(apiClient.get).mock.calls[0][1]!.signal!

      // 2回目（1回目をキャンセル）
      const secondPromise = result.fetchAllocatedOrders()

      expect(firstSignal.aborted).toBe(true)

      await firstPromise
      await secondPromise
    })
  })

  describe('handleAllocatedPageChange / handleAllocatedSizeChange', () => {
    it('ページ変更で fetchAllocatedOrders が呼ばれる', async () => {
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createAllocatedResponse()))

      const { result } = withSetup(() => useAllocation())

      vi.mocked(apiClient.get).mockClear()
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createAllocatedResponse()))

      result.handleAllocatedPageChange(2)
      await flushPromises()

      expect(result.allocatedPage.value).toBe(2)
      expect(apiClient.get).toHaveBeenCalledWith(
        '/allocation/allocated-orders',
        expect.anything(),
      )
    })

    it('サイズ変更でページが1にリセットされる', async () => {
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createAllocatedResponse()))

      const { result } = withSetup(() => useAllocation())

      result.allocatedPage.value = 3
      vi.mocked(apiClient.get).mockClear()
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createAllocatedResponse()))

      result.handleAllocatedSizeChange(50)
      await flushPromises()

      expect(result.allocatedPageSize.value).toBe(50)
      expect(result.allocatedPage.value).toBe(1)
    })
  })

  // ============================================================
  // 引当解放
  // ============================================================

  describe('handleRelease', () => {
    const order = {
      id: 4,
      slipNumber: 'OUT-004',
      plannedDate: '2026-03-18',
      partnerName: '商事C',
      lineCount: 2,
      allocatedLineCount: 2,
      status: 'ALLOCATED' as const,
    }

    it('確認ダイアログ → POST /allocation/release → 成功メッセージ', async () => {
      vi.mocked(apiClient.post).mockResolvedValue(
        mockAxiosResponse({ releasedCount: 1, releasedSlips: [{ outboundSlipId: 4 }] }),
      )
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createAllocatedResponse()))

      const { result } = withSetup(() => useAllocation())

      await result.handleRelease(order)

      expect(ElMessageBox.confirm).toHaveBeenCalled()
      expect(apiClient.post).toHaveBeenCalledWith('/allocation/release', {
        outboundSlipIds: [4],
      })
      expect(ElMessage.success).toHaveBeenCalled()
    })

    it('確認ダイアログでキャンセルした場合はAPIを呼ばない', async () => {
      vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')

      const { result } = withSetup(() => useAllocation())

      await result.handleRelease(order)

      expect(apiClient.post).not.toHaveBeenCalled()
    })

    it('409エラー時にreleaseNotAllowedメッセージを表示する', async () => {
      vi.mocked(apiClient.post).mockRejectedValue(createAxiosError(409))

      const { result } = withSetup(() => useAllocation())

      await result.handleRelease(order)

      expect(ElMessage.error).toHaveBeenCalled()
      expect(result.allocatedLoading.value).toBe(false)
    })

    it('ネットワークエラー時にエラーメッセージを表示する', async () => {
      vi.mocked(apiClient.post).mockRejectedValue(new Error('Network Error'))

      const { result } = withSetup(() => useAllocation())

      await result.handleRelease(order)

      expect(ElMessage.error).toHaveBeenCalled()
    })

    it('その他のAPIエラーにメッセージがある場合はそれを表示する', async () => {
      vi.mocked(apiClient.post).mockRejectedValue(createAxiosError(400, { message: 'Bad Request' }))

      const { result } = withSetup(() => useAllocation())

      await result.handleRelease(order)

      expect(ElMessage.error).toHaveBeenCalled()
    })

    it('その他のAPIエラーにメッセージがない場合は空文字で表示する', async () => {
      vi.mocked(apiClient.post).mockRejectedValue(createAxiosError(400))

      const { result } = withSetup(() => useAllocation())

      await result.handleRelease(order)

      expect(ElMessage.error).toHaveBeenCalled()
    })

    it('成功後に fetchAllocatedOrders を再呼び出しする', async () => {
      vi.mocked(apiClient.post).mockResolvedValue(
        mockAxiosResponse({ releasedCount: 1, releasedSlips: [] }),
      )
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createAllocatedResponse()))

      const { result } = withSetup(() => useAllocation())

      vi.mocked(apiClient.get).mockClear()
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createAllocatedResponse()))

      await result.handleRelease(order)

      expect(apiClient.get).toHaveBeenCalledWith(
        '/allocation/allocated-orders',
        expect.anything(),
      )
    })
  })
})
