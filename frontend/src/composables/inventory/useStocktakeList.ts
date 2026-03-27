import { ref, reactive, computed, watch, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import { useWarehouseStore } from '@/stores/warehouse'
import { useAuthStore } from '@/stores/auth'
import type { StocktakeSummary } from '@/api/generated/models/stocktake-summary'
import type { StocktakeSummaryPageResponse } from '@/api/generated/models/stocktake-summary-page-response'

export function useStocktakeList() {
  const { t } = useI18n()
  const router = useRouter()
  const warehouseStore = useWarehouseStore()
  const auth = useAuthStore()

  const isManager = computed(
    () => auth.user?.role === 'WAREHOUSE_MANAGER' || auth.user?.role === 'SYSTEM_ADMIN',
  )
  const isViewer = computed(() => auth.user?.role === 'VIEWER')

  const items = ref<StocktakeSummary[]>([])
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)

  // 検索条件: 初期値は当月
  const now = new Date()
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1)

  function formatDate(d: Date): string {
    return d.toISOString().slice(0, 10)
  }

  const searchForm = reactive({
    dateFrom: formatDate(monthStart) as string | null,
    dateTo: formatDate(now) as string | null,
    status: null as string | null,
  })

  // --- AbortController ---
  let abortController: AbortController | null = null
  onUnmounted(() => {
    abortController?.abort()
  })

  async function fetchList() {
    abortController?.abort()
    abortController = new AbortController()
    const signal = abortController.signal

    // 日付範囲バリデーション
    if (searchForm.dateFrom && searchForm.dateTo && searchForm.dateFrom > searchForm.dateTo) {
      ElMessage.error(t('inventory.stocktakeDateRangeError'))
      return
    }

    loading.value = true
    try {
      const params: Record<string, unknown> = {
        warehouseId: warehouseStore.selectedWarehouseId,
        page: page.value - 1,
        size: pageSize.value,
        sort: 'startedAt,desc',
      }
      if (searchForm.dateFrom) params.dateFrom = searchForm.dateFrom
      if (searchForm.dateTo) params.dateTo = searchForm.dateTo
      if (searchForm.status) params.status = searchForm.status

      const res = await apiClient.get<StocktakeSummaryPageResponse>('/inventory/stocktakes', {
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
        ElMessage.error(t('inventory.stocktakeFetchError'))
      }
    } finally {
      if (!signal.aborted) {
        loading.value = false
      }
    }
  }

  // --- 倉庫切替時の自動リフェッチ ---
  watch(
    () => warehouseStore.selectedWarehouseId,
    (newId) => {
      if (newId == null) return
      page.value = 1
      fetchList()
    },
  )

  function handleSearch() {
    page.value = 1
    fetchList()
  }

  function handleReset() {
    const resetNow = new Date()
    const resetMonthStart = new Date(resetNow.getFullYear(), resetNow.getMonth(), 1)
    searchForm.dateFrom = formatDate(resetMonthStart)
    searchForm.dateTo = formatDate(resetNow)
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

  function formatStocktakeDate(dateStr: string): string {
    if (!dateStr) return ''
    const d = dateStr.includes('T') ? new Date(dateStr) : new Date(dateStr + 'T00:00:00')
    return d.toLocaleDateString('ja-JP', { year: 'numeric', month: '2-digit', day: '2-digit' })
  }

  function goToDetail(row: StocktakeSummary) {
    router.push({ name: 'stocktake-detail', params: { id: row.id } })
  }

  function goToConfirm(row: StocktakeSummary) {
    router.push({ name: 'stocktake-confirm', params: { id: row.id } })
  }

  return {
    items,
    loading,
    total,
    page,
    pageSize,
    searchForm,
    isManager,
    isViewer,
    fetchList,
    handleSearch,
    handleReset,
    handlePageChange,
    handleSizeChange,
    formatStocktakeDate,
    goToDetail,
    goToConfirm,
  }
}
