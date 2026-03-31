import { ref, reactive, computed, watch, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import { useWarehouseStore } from '@/stores/warehouse'
import type { AllocationOrderSummary } from '@/api/generated/models/allocation-order-summary'
import type { AllocationOrderPageResponse } from '@/api/generated/models/allocation-order-page-response'
import type { AllocationExecutionResult } from '@/api/generated/models/allocation-execution-result'
import type { AllocatedOrderSummary } from '@/api/generated/models/allocated-order-summary'
import type { AllocatedOrderPageResponse } from '@/api/generated/models/allocated-order-page-response'
import type { UnpackInstructionItem } from '@/api/generated/models/unpack-instruction-item'

type TabName = 'execute' | 'allocated'

export function useAllocation() {
  const { t } = useI18n()
  const warehouseStore = useWarehouseStore()

  // --- タブ ---
  const activeTab = ref<TabName>('execute')

  // --- 日付フォーマット ---
  function formatDate(dateStr: string | undefined): string {
    if (!dateStr) return ''
    return dateStr.replace(/-/g, '/')
  }

  // --- ステータスラベル/タグ色 ---
  function orderStatusTagType(status: string) {
    switch (status) {
      case 'ORDERED':
        return 'info'
      case 'PARTIAL_ALLOCATED':
        return 'warning'
      default:
        return 'info'
    }
  }

  function orderStatusLabel(status: string) {
    switch (status) {
      case 'ORDERED':
        return t('allocation.statusOrdered')
      case 'PARTIAL_ALLOCATED':
        return t('allocation.statusPartialAllocated')
      default:
        return status
    }
  }

  function allocatedStatusTagType(status: string) {
    switch (status) {
      case 'ALLOCATED':
        return 'success'
      case 'PARTIAL_ALLOCATED':
        return 'warning'
      default:
        return 'info'
    }
  }

  function allocatedStatusLabel(status: string) {
    switch (status) {
      case 'ALLOCATED':
        return t('allocation.statusAllocated')
      case 'PARTIAL_ALLOCATED':
        return t('allocation.statusPartialAllocated')
      default:
        return status
    }
  }

  // --- 出荷予定日Toの最小日付 ---
  const shippingDateToMin = computed(() => searchForm.shippingDateFrom)

  // === Tab1: 引当実行 ===
  const orders = ref<AllocationOrderSummary[]>([])
  const ordersLoading = ref(false)
  const ordersTotal = ref(0)
  const ordersPage = ref(1)
  const ordersPageSize = ref(20)
  const selectedIds = ref<number[]>([])

  const searchForm = reactive({
    status: ['ORDERED', 'PARTIAL_ALLOCATED'] as string[],
    shippingDateFrom: null as string | null,
    shippingDateTo: null as string | null,
    partnerName: '',
  })

  // 引当結果
  const executionResult = ref<AllocationExecutionResult | null>(null)
  const executing = ref(false)

  // === Tab2: 引当済み一覧 ===
  const allocatedOrders = ref<AllocatedOrderSummary[]>([])
  const allocatedLoading = ref(false)
  const allocatedTotal = ref(0)
  const allocatedPage = ref(1)
  const allocatedPageSize = ref(20)

  // --- AbortController ---
  let ordersAbort: AbortController | null = null
  let allocatedAbort: AbortController | null = null
  onUnmounted(() => {
    ordersAbort?.abort()
    allocatedAbort?.abort()
  })

  // --- 引当対象受注一覧取得 ---
  async function fetchOrders() {
    ordersAbort?.abort()
    ordersAbort = new AbortController()
    const signal = ordersAbort.signal

    ordersLoading.value = true
    selectedIds.value = []
    try {
      const params: Record<string, unknown> = {
        warehouseId: warehouseStore.selectedWarehouseId,
        page: ordersPage.value - 1,
        size: ordersPageSize.value,
        sort: 'plannedDate,asc',
      }
      if (searchForm.status.length > 0) params.status = searchForm.status
      if (searchForm.shippingDateFrom) params.shippingDateFrom = searchForm.shippingDateFrom
      if (searchForm.shippingDateTo) params.shippingDateTo = searchForm.shippingDateTo
      if (searchForm.partnerName?.trim()) params.partnerName = searchForm.partnerName.trim()

      const res = await apiClient.get<AllocationOrderPageResponse>('/allocation/orders', {
        params,
        signal,
      })
      orders.value = res.data.content ?? []
      ordersTotal.value = res.data.totalElements ?? 0
    } catch (err: unknown) {
      if (axios.isCancel(err)) return
      orders.value = []
      ordersTotal.value = 0
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else {
        ElMessage.error(t('allocation.fetchError'))
      }
    } finally {
      if (!signal.aborted) {
        ordersLoading.value = false
      }
    }
  }

  // --- 引当済み受注一覧取得 ---
  async function fetchAllocatedOrders() {
    allocatedAbort?.abort()
    allocatedAbort = new AbortController()
    const signal = allocatedAbort.signal

    allocatedLoading.value = true
    try {
      const res = await apiClient.get<AllocatedOrderPageResponse>('/allocation/allocated-orders', {
        params: {
          warehouseId: warehouseStore.selectedWarehouseId,
          page: allocatedPage.value - 1,
          size: allocatedPageSize.value,
          sort: 'plannedDate,asc',
        },
        signal,
      })
      allocatedOrders.value = res.data.content ?? []
      allocatedTotal.value = res.data.totalElements ?? 0
    } catch (err: unknown) {
      if (axios.isCancel(err)) return
      allocatedOrders.value = []
      allocatedTotal.value = 0
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else {
        ElMessage.error(t('allocation.allocatedFetchError'))
      }
    } finally {
      if (!signal.aborted) {
        allocatedLoading.value = false
      }
    }
  }

  // --- 倉庫切替時 ---
  watch(
    () => warehouseStore.selectedWarehouseId,
    (newId) => {
      if (newId == null) return
      ordersPage.value = 1
      allocatedPage.value = 1
      if (activeTab.value === 'execute') {
        fetchOrders()
      } else {
        fetchAllocatedOrders()
      }
    },
  )

  // --- タブ切替時 ---
  function handleTabChange(tab: string | number) {
    activeTab.value = tab as TabName
    if (tab === 'allocated') {
      allocatedPage.value = 1
      fetchAllocatedOrders()
    }
  }

  // --- 検索操作 ---
  function handleSearch() {
    ordersPage.value = 1
    fetchOrders()
  }

  function handleReset() {
    searchForm.status = ['ORDERED', 'PARTIAL_ALLOCATED']
    searchForm.shippingDateFrom = null
    searchForm.shippingDateTo = null
    searchForm.partnerName = ''
    ordersPage.value = 1
    fetchOrders()
  }

  function handleOrdersPageChange(p: number) {
    ordersPage.value = p
    fetchOrders()
  }

  function handleOrdersSizeChange(s: number) {
    ordersPageSize.value = s
    ordersPage.value = 1
    fetchOrders()
  }

  function handleSelectionChange(rows: AllocationOrderSummary[]) {
    selectedIds.value = rows.map((r) => r.id).filter((id): id is number => id != null)
  }

  // --- 引当実行 ---
  async function handleExecute() {
    if (selectedIds.value.length === 0) {
      ElMessage.warning(t('allocation.selectRequired'))
      return
    }

    executing.value = true
    executionResult.value = null
    try {
      const res = await apiClient.post<AllocationExecutionResult>('/allocation/execute', {
        outboundSlipIds: selectedIds.value,
      })
      executionResult.value = res.data
      ElMessage.success(t('allocation.executeSuccess', { count: res.data.allocatedCount ?? 0 }))
      if ((res.data.unallocatedLines ?? []).length > 0) {
        ElMessage.warning(t('allocation.unallocatedWarning'))
      }
      await fetchOrders()
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else {
        const msg = error.response.data?.message ?? ''
        ElMessage.error(t('allocation.executeError', { message: msg }))
      }
    } finally {
      executing.value = false
    }
  }

  // --- ばらし完了 ---
  async function handleUnpackComplete(instruction: UnpackInstructionItem) {
    try {
      await apiClient.put(`/allocation/unpack-instructions/${instruction.id}/complete`)
      ElMessage.success(t('allocation.unpackCompleteSuccess'))
      // ばらし指示のステータスをローカル更新
      if (executionResult.value?.unpackInstructions) {
        const target = executionResult.value.unpackInstructions.find(
          (i) => i.id === instruction.id,
        )
        if (target) {
          target.status = 'COMPLETED' as UnpackInstructionItem['status']
        }
      }
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 400) {
        ElMessage.error(t('allocation.alreadyCompleted'))
      } else {
        ElMessage.error(t('allocation.unpackCompleteError'))
      }
    }
  }

  // --- 引当解放 ---
  async function handleRelease(order: AllocatedOrderSummary) {
    try {
      await ElMessageBox.confirm(
        t('allocation.releaseConfirm', { slipNumber: order.slipNumber }),
        t('common.confirm'),
        {
          type: 'warning',
          confirmButtonText: t('common.confirm'),
          cancelButtonText: t('common.cancel'),
        },
      )
    } catch {
      return
    }

    allocatedLoading.value = true
    try {
      await apiClient.post('/allocation/release', {
        outboundSlipIds: [order.id],
      })
      ElMessage.success(t('allocation.releaseSuccess'))
      await fetchAllocatedOrders()
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 409) {
        ElMessage.error(t('allocation.releaseNotAllowed'))
      } else {
        const msg = error.response.data?.message ?? ''
        ElMessage.error(t('allocation.releaseError', { message: msg }))
      }
    } finally {
      allocatedLoading.value = false
    }
  }

  // --- 引当済み一覧ページ操作 ---
  function handleAllocatedPageChange(p: number) {
    allocatedPage.value = p
    fetchAllocatedOrders()
  }

  function handleAllocatedSizeChange(s: number) {
    allocatedPageSize.value = s
    allocatedPage.value = 1
    fetchAllocatedOrders()
  }

  return {
    // タブ
    activeTab,
    handleTabChange,
    // ヘルパー
    formatDate,
    orderStatusTagType,
    orderStatusLabel,
    allocatedStatusTagType,
    allocatedStatusLabel,
    shippingDateToMin,
    // Tab1: 引当実行
    orders,
    ordersLoading,
    ordersTotal,
    ordersPage,
    ordersPageSize,
    selectedIds,
    searchForm,
    executionResult,
    executing,
    fetchOrders,
    handleSearch,
    handleReset,
    handleOrdersPageChange,
    handleOrdersSizeChange,
    handleSelectionChange,
    handleExecute,
    handleUnpackComplete,
    // Tab2: 引当済み一覧
    allocatedOrders,
    allocatedLoading,
    allocatedTotal,
    allocatedPage,
    allocatedPageSize,
    fetchAllocatedOrders,
    handleRelease,
    handleAllocatedPageChange,
    handleAllocatedSizeChange,
  }
}
