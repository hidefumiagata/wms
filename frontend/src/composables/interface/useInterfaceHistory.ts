import { ref, reactive, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import type { IfExecutionItem } from '@/api/generated/models/if-execution-item'
import type { IfExecutionPageResponse } from '@/api/generated/models/if-execution-page-response'

export function useInterfaceHistory() {
  const { t } = useI18n()

  // --- 状態 ---
  const items = ref<IfExecutionItem[]>([])
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)

  // 検索フォーム
  function formatDate(d: Date): string {
    return d.toISOString().slice(0, 10)
  }
  const today = new Date()
  const defaultFrom = new Date(today)
  defaultFrom.setMonth(defaultFrom.getMonth() - 1)

  const searchForm = reactive({
    ifType: null as string | null,
    dateFrom: formatDate(defaultFrom) as string | null,
    dateTo: formatDate(today) as string | null,
    status: null as string | null,
    fileName: '',
  })

  // --- 並行リクエスト制御 ---
  let abortController: AbortController | null = null
  onUnmounted(() => {
    abortController?.abort()
  })

  // --- 一覧取得 ---
  async function fetchList() {
    abortController?.abort()
    abortController = new AbortController()
    const signal = abortController.signal

    loading.value = true
    try {
      const params: Record<string, unknown> = {
        page: page.value - 1,
        size: pageSize.value,
        sort: 'executedAt,desc',
      }
      if (searchForm.ifType) params.ifType = searchForm.ifType
      if (searchForm.dateFrom) params.dateFrom = searchForm.dateFrom
      if (searchForm.dateTo) params.dateTo = searchForm.dateTo
      if (searchForm.status) params.status = searchForm.status
      if (searchForm.fileName) params.fileName = searchForm.fileName

      const res = await apiClient.get<IfExecutionPageResponse>('/interface/executions', {
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
        ElMessage.error(t('interfaceHistory.fetchError'))
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
    from.setMonth(from.getMonth() - 1)
    searchForm.ifType = null
    searchForm.dateFrom = formatDate(from)
    searchForm.dateTo = formatDate(now)
    searchForm.status = null
    searchForm.fileName = ''
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
    fetchList,
    handleSearch,
    handleReset,
    handlePageChange,
    handleSizeChange,
  }
}
