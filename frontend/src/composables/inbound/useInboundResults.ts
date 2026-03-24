import { ref, reactive, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import { useWarehouseStore } from '@/stores/warehouse'
import type { InboundResultItem } from '@/api/generated/models/inbound-result-item'
import type { InboundResultPageResponse } from '@/api/generated/models/inbound-result-page-response'

function formatDate(d: Date): string {
  return d.toISOString().slice(0, 10)
}

export function useInboundResults() {
  const { t } = useI18n()
  const warehouseStore = useWarehouseStore()

  // SCR-07 INB006: デフォルト日付 = 月初〜当日
  const today = new Date()
  const monthStart = new Date(today.getFullYear(), today.getMonth(), 1)

  const items = ref<InboundResultItem[]>([])
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)

  const searchForm = reactive({
    storedDateFrom: formatDate(monthStart) as string | null,
    storedDateTo: formatDate(today) as string | null,
    partnerId: null as number | null,
    slipNumber: '',
    productCode: '',
  })

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
      // エラーは無視
    }
  }

  let abortController: AbortController | null = null
  onUnmounted(() => {
    abortController?.abort()
  })

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
      }
      if (searchForm.storedDateFrom) params.storedDateFrom = searchForm.storedDateFrom
      if (searchForm.storedDateTo) params.storedDateTo = searchForm.storedDateTo
      if (searchForm.partnerId) params.partnerId = searchForm.partnerId
      if (searchForm.slipNumber) params.slipNumber = searchForm.slipNumber
      if (searchForm.productCode) params.productCode = searchForm.productCode

      const res = await apiClient.get<InboundResultPageResponse>('/inbound/results', {
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
        ElMessage.error(t('inbound.results.fetchError'))
      }
    } finally {
      if (!signal.aborted) {
        loading.value = false
      }
    }
  }

  function handleSearch() {
    page.value = 1
    fetchList()
  }

  function handleReset() {
    const now = new Date()
    const ms = new Date(now.getFullYear(), now.getMonth(), 1)
    searchForm.storedDateFrom = formatDate(ms)
    searchForm.storedDateTo = formatDate(now)
    searchForm.partnerId = null
    searchForm.slipNumber = ''
    searchForm.productCode = ''
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
