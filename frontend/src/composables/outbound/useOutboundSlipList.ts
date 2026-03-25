import { ref, reactive, computed, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import { useWarehouseStore } from '@/stores/warehouse'
import { useAuthStore } from '@/stores/auth'
import type { OutboundSlipSummary } from '@/api/generated/models/outbound-slip-summary'
import type { OutboundSlipPageResponse } from '@/api/generated/models/outbound-slip-page-response'
import type { OutboundSlipStatus } from '@/api/generated/models/outbound-slip-status'

function formatDate(d: Date): string {
  return d.toISOString().slice(0, 10)
}

export function useOutboundSlipList() {
  const { t } = useI18n()
  const warehouseStore = useWarehouseStore()
  const auth = useAuthStore()

  const isViewer = computed(() => auth.user?.role === 'VIEWER')

  // --- 状態 ---
  const items = ref<OutboundSlipSummary[]>([])
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)
  const selectedIds = ref<number[]>([])

  // SCR-10 OUT-001: 初期値は営業日-7日〜+30日
  const today = new Date()
  const defaultFrom = new Date(today)
  defaultFrom.setDate(defaultFrom.getDate() - 7)
  const defaultTo = new Date(today)
  defaultTo.setDate(defaultTo.getDate() + 30)

  const searchForm = reactive({
    slipNumber: '',
    plannedDateFrom: formatDate(defaultFrom) as string | null,
    plannedDateTo: formatDate(defaultTo) as string | null,
    partnerId: null as number | null,
    status: null as OutboundSlipStatus | null,
  })

  // --- 出荷先プルダウン ---
  const partnerOptions = ref<{ id: number; partnerName: string }[]>([])

  async function fetchPartnerOptions() {
    try {
      const [resCustomer, resBoth] = await Promise.all([
        apiClient.get('/master/partners', {
          params: { page: 0, size: 1000, isActive: true, partnerType: 'CUSTOMER', sort: 'partnerName,asc' },
        }),
        apiClient.get('/master/partners', {
          params: { page: 0, size: 1000, isActive: true, partnerType: 'BOTH', sort: 'partnerName,asc' },
        }),
      ])
      const toOption = (p: { id: number; partnerName: string }) => ({ id: p.id, partnerName: p.partnerName })
      partnerOptions.value = [
        ...resCustomer.data.content.map(toOption),
        ...resBoth.data.content.map(toOption),
      ]
    } catch {
      // エラーは無視
    }
  }

  // --- 並行リクエスト制御 ---
  let abortController: AbortController | null = null
  onUnmounted(() => { abortController?.abort() })

  async function fetchList() {
    abortController?.abort()
    abortController = new AbortController()
    const signal = abortController.signal

    loading.value = true
    selectedIds.value = []
    try {
      const params: Record<string, unknown> = {
        warehouseId: warehouseStore.selectedWarehouseId,
        page: page.value - 1,
        size: pageSize.value,
        sort: 'plannedDate,desc',
      }
      if (searchForm.slipNumber?.trim()) params.slipNumber = searchForm.slipNumber.trim()
      if (searchForm.plannedDateFrom) params.plannedDateFrom = searchForm.plannedDateFrom
      if (searchForm.plannedDateTo) params.plannedDateTo = searchForm.plannedDateTo
      if (searchForm.partnerId) params.partnerId = searchForm.partnerId
      if (searchForm.status) params.status = searchForm.status

      const res = await apiClient.get<OutboundSlipPageResponse>('/outbound/slips', {
        params,
        signal,
      })
      items.value = res.data.content ?? []
      total.value = res.data.totalElements ?? 0
    } catch (err: unknown) {
      if (axios.isCancel(err)) return
      items.value = []
      total.value = 0
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else {
        ElMessage.error(t('outbound.slip.fetchError'))
      }
    } finally {
      if (!signal.aborted) {
        loading.value = false
      }
    }
  }

  // --- 操作 ---
  function handleSearch() {
    page.value = 1
    fetchList()
  }

  function handleReset() {
    const now = new Date()
    const from = new Date(now)
    from.setDate(from.getDate() - 7)
    const to = new Date(now)
    to.setDate(to.getDate() + 30)
    searchForm.slipNumber = ''
    searchForm.plannedDateFrom = formatDate(from)
    searchForm.plannedDateTo = formatDate(to)
    searchForm.partnerId = null
    searchForm.status = null
    page.value = 1
    fetchList()
  }

  function handlePageChange(p: number) {
    page.value = p
    fetchList()
  }

  function handleSizeChange(s: number) {
    pageSize.value = s
    page.value = 1
    fetchList()
  }

  function handleSelectionChange(rows: OutboundSlipSummary[]) {
    selectedIds.value = rows.map(r => r.id)
  }

  // --- 引当実行（共通） ---
  async function executeAllocation(ids: number[]) {
    loading.value = true
    try {
      await apiClient.post('/allocation/execute', { outboundSlipIds: ids })
      ElMessage.success(t('outbound.slip.allocateSuccess'))
      await fetchList()
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 422) {
        ElMessage.warning(t('outbound.slip.allocatePartialError'))
        await fetchList()
      } else if (error.response.status === 409) {
        ElMessage.error(t('error.optimisticLock'))
      }
    } finally {
      loading.value = false
    }
  }

  // --- 一括引当 ---
  async function handleBulkAllocate() {
    if (selectedIds.value.length === 0) {
      ElMessage.warning(t('outbound.slip.selectRequired'))
      return
    }
    try {
      await ElMessageBox.confirm(
        t('outbound.slip.allocateMessage', { count: selectedIds.value.length }),
        t('common.confirm'),
        { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
      )
    } catch { return }
    await executeAllocation(selectedIds.value)
  }

  // --- 行単位引当 ---
  async function handleAllocateSingle(id: number) {
    try {
      await ElMessageBox.confirm(
        t('outbound.slip.allocateSingleMessage'),
        t('common.confirm'),
        { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
      )
    } catch { return }
    await executeAllocation([id])
  }

  return {
    items,
    loading,
    total,
    page,
    pageSize,
    searchForm,
    partnerOptions,
    selectedIds,
    isViewer,
    fetchList,
    fetchPartnerOptions,
    handleSearch,
    handleReset,
    handlePageChange,
    handleSizeChange,
    handleSelectionChange,
    handleBulkAllocate,
    handleAllocateSingle,
  }
}
