import { ref, reactive, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import { useWarehouseStore } from '@/stores/warehouse'
import type { InboundSlipSummary } from '@/api/generated/models/inbound-slip-summary'
import type { InboundSlipSummaryPageResponse } from '@/api/generated/models/inbound-slip-summary-page-response'
import type { InboundSlipStatus } from '@/api/generated/models/inbound-slip-status'

export function useInboundSlipList() {
  const { t } = useI18n()
  const warehouseStore = useWarehouseStore()

  // --- 状態 ---
  const items = ref<InboundSlipSummary[]>([])
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)

  const searchForm = reactive({
    slipNumber: '',
    plannedDateFrom: null as string | null,
    plannedDateTo: null as string | null,
    partnerId: null as number | null,
    status: null as InboundSlipStatus | null,
  })

  // --- 仕入先プルダウン ---
  const partnerOptions = ref<{ id: number; partnerName: string }[]>([])

  async function fetchPartnerOptions() {
    try {
      const res = await apiClient.get('/master/partners', {
        params: { page: 0, size: 1000, isActive: true, partnerType: 'SUPPLIER', sort: 'partnerName,asc' },
      })
      partnerOptions.value = res.data.content.map((p: { id: number; partnerName: string; partnerType: string }) => ({
        id: p.id,
        partnerName: p.partnerName,
      }))
      // BOTH タイプも追加
      const resBoth = await apiClient.get('/master/partners', {
        params: { page: 0, size: 1000, isActive: true, partnerType: 'BOTH', sort: 'partnerName,asc' },
      })
      const bothPartners = resBoth.data.content.map((p: { id: number; partnerName: string }) => ({
        id: p.id,
        partnerName: p.partnerName,
      }))
      partnerOptions.value = [...partnerOptions.value, ...bothPartners]
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
        warehouseId: warehouseStore.currentWarehouseId,
        page: page.value - 1,
        size: pageSize.value,
        sort: 'plannedDate,desc',
      }
      if (searchForm.slipNumber) params.slipNumber = searchForm.slipNumber
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
    searchForm.slipNumber = ''
    searchForm.plannedDateFrom = null
    searchForm.plannedDateTo = null
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
    fetchList,
    fetchPartnerOptions,
    handleSearch,
    handleReset,
    handlePageChange,
    handleSizeChange,
  }
}
