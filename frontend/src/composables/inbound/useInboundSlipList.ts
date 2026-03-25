import { ref, reactive, computed, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import { useWarehouseStore } from '@/stores/warehouse'
import { useAuthStore } from '@/stores/auth'
import type { InboundSlipSummary } from '@/api/generated/models/inbound-slip-summary'
import type { InboundSlipSummaryPageResponse } from '@/api/generated/models/inbound-slip-summary-page-response'
import type { InboundSlipStatus } from '@/api/generated/models/inbound-slip-status'

export function useInboundSlipList() {
  const { t } = useI18n()
  const warehouseStore = useWarehouseStore()
  const auth = useAuthStore()

  const isViewer = computed(() => auth.user?.role === 'VIEWER')

  // --- 状態 ---
  const items = ref<InboundSlipSummary[]>([])
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)

  // SCR-07 INB001-F02/F03: 初期値は営業日-7日〜+30日
  function formatDate(d: Date): string {
    return d.toISOString().slice(0, 10)
  }
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
    status: null as InboundSlipStatus | null,
  })

  // --- 仕入先プルダウン ---
  const partnerOptions = ref<{ id: number; partnerName: string }[]>([])

  async function fetchPartnerOptions() {
    try {
      const [resSupplier, resBoth] = await Promise.all([
        apiClient.get('/master/partners', {
          params: { page: 0, size: 1000, isActive: true, partnerType: 'SUPPLIER', sort: 'partnerName,asc' },
        }),
        apiClient.get('/master/partners', {
          params: { page: 0, size: 1000, isActive: true, partnerType: 'BOTH', sort: 'partnerName,asc' },
        }),
      ])
      const toOption = (p: { id: number; partnerName: string }) => ({ id: p.id, partnerName: p.partnerName })
      partnerOptions.value = [
        ...resSupplier.data.content.map(toOption),
        ...resBoth.data.content.map(toOption),
      ]
    } catch {
      // エラーは無視（プルダウンが空になるだけ）
    }
  }

  // --- 並行リクエスト制御 ---
  let abortController: AbortController | null = null
  onUnmounted(() => {
    abortController?.abort()
  })

  // --- API呼び出し ---
  async function fetchList() {
    abortController?.abort()
    abortController = new AbortController()
    const signal = abortController.signal

    loading.value = true
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

      const res = await apiClient.get<InboundSlipSummaryPageResponse>('/inbound/slips', {
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
        ElMessage.error(t('inbound.slip.fetchError'))
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

  return {
    items,
    loading,
    total,
    page,
    pageSize,
    searchForm,
    partnerOptions,
    isViewer,
    fetchList,
    fetchPartnerOptions,
    handleSearch,
    handleReset,
    handlePageChange,
    handleSizeChange,
  }
}
