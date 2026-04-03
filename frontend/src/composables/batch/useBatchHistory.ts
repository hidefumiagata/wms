import { ref, reactive, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import { downloadReport } from '@/utils/reportDownload'
import { toDateString } from '@/utils/dateFormat'
import type { BatchExecutionDetail } from '@/api/generated/models/batch-execution-detail'
import type { BatchExecutionPageResponse } from '@/api/generated/models/batch-execution-page-response'
import type { BatchExecutionStatus } from '@/api/generated/models/batch-execution-status'

export function useBatchHistory() {
  const { t } = useI18n()

  // --- 状態 ---
  const items = ref<BatchExecutionDetail[]>([])
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)

  // 検索フォーム: SCR-11 BAT002-FILTER-*
  const today = new Date()
  const defaultFrom = new Date(today)
  defaultFrom.setMonth(defaultFrom.getMonth() - 1)

  const searchForm = reactive({
    executedDateFrom: toDateString(defaultFrom) as string | null,
    executedDateTo: toDateString(today) as string | null,
    targetBusinessDate: null as string | null,
    status: null as BatchExecutionStatus | null,
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
        sort: 'startedAt,desc',
      }
      if (searchForm.executedDateFrom) params.executedDateFrom = searchForm.executedDateFrom
      if (searchForm.executedDateTo) params.executedDateTo = searchForm.executedDateTo
      if (searchForm.targetBusinessDate) params.targetBusinessDate = searchForm.targetBusinessDate
      if (searchForm.status) params.status = searchForm.status

      const res = await apiClient.get<BatchExecutionPageResponse>('/batch/executions', {
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
        ElMessage.error(t('batch.history.fetchError'))
      }
    } finally {
      if (!signal.aborted) {
        loading.value = false
      }
    }
  }

  // --- 詳細ドロワー ---
  const drawerVisible = ref(false)
  const drawerLoading = ref(false)
  const selectedDetail = ref<BatchExecutionDetail | null>(null)

  async function openDetail(id: number) {
    drawerVisible.value = true
    drawerLoading.value = true
    try {
      const res = await apiClient.get<BatchExecutionDetail>(`/batch/executions/${id}`)
      selectedDetail.value = res.data
    } catch {
      ElMessage.error(t('batch.history.detailFetchError'))
      selectedDetail.value = null
    } finally {
      drawerLoading.value = false
    }
  }

  function closeDrawer() {
    drawerVisible.value = false
    selectedDetail.value = null
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
    searchForm.executedDateFrom = toDateString(from)
    searchForm.executedDateTo = toDateString(now)
    searchForm.targetBusinessDate = null
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

  // --- レポートダイアログ ---
  type ReportType = 'rpt006' | 'rpt016'
  const reportDialogVisible = ref(false)
  const activeReportType = ref<ReportType | null>(null)
  const reportBusinessDate = ref<string | null>(null)
  const processedDates = ref<string[]>([])
  const processedDatesLoading = ref(false)
  const downloadingReport = ref(false)

  async function fetchProcessedDates() {
    processedDatesLoading.value = true
    try {
      const res = await apiClient.get<BatchExecutionPageResponse>('/batch/executions', {
        params: { status: 'SUCCESS', size: 1000, sort: 'targetBusinessDate,desc' },
      })
      const dates = (res.data.content ?? [])
        .map((e) => e.targetBusinessDate)
        .filter((d): d is string => !!d)
      processedDates.value = [...new Set(dates)]
      reportBusinessDate.value = processedDates.value.length > 0 ? processedDates.value[0] : null
    } catch {
      processedDates.value = []
      reportBusinessDate.value = null
    } finally {
      processedDatesLoading.value = false
    }
  }

  function openReportDialog(type: ReportType) {
    activeReportType.value = type
    reportDialogVisible.value = true
    fetchProcessedDates()
  }

  function closeReportDialog() {
    reportDialogVisible.value = false
    activeReportType.value = null
    reportBusinessDate.value = null
    processedDates.value = []
  }

  function isDateDisabled(date: Date): boolean {
    const dateStr = toDateString(date)
    return !processedDates.value.includes(dateStr)
  }

  async function downloadConfirmedReport() {
    if (downloadingReport.value) return
    if (!reportBusinessDate.value) {
      ElMessage.warning(t('batch.history.reportBusinessDateRequired'))
      return
    }

    downloadingReport.value = true
    const isRpt006 = activeReportType.value === 'rpt006'
    try {
      const path = isRpt006 ? '/reports/unreceived-confirmed' : '/reports/unshipped-confirmed'
      const filenameBase = isRpt006
        ? `unreceived_confirmed_${reportBusinessDate.value.replace(/-/g, '')}`
        : `unshipped_confirmed_${reportBusinessDate.value.replace(/-/g, '')}`

      await downloadReport({
        path,
        params: { targetBusinessDate: reportBusinessDate.value },
        format: 'pdf',
        filenameBase,
      })
      const successKey = isRpt006
        ? 'batch.history.reportUnreceivedDownloaded'
        : 'batch.history.reportUnshippedDownloaded'
      ElMessage.success(t(successKey))
      closeReportDialog()
    } catch (err: unknown) {
      const error = toApiError(err)
      if (error.response && (error.response.status === 404 || error.response.status === 422)) {
        const noExecKey = isRpt006
          ? 'batch.history.reportUnreceivedNoExecution'
          : 'batch.history.reportUnshippedNoExecution'
        ElMessage.error(t(noExecKey, { date: reportBusinessDate.value }))
      } else {
        ElMessage.error(t('batch.history.reportDownloadError'))
      }
    } finally {
      downloadingReport.value = false
    }
  }

  return {
    items,
    loading,
    total,
    page,
    pageSize,
    searchForm,
    drawerVisible,
    drawerLoading,
    selectedDetail,
    fetchList,
    openDetail,
    closeDrawer,
    handleSearch,
    handleReset,
    handlePageChange,
    handleSizeChange,
    // レポート
    reportDialogVisible,
    activeReportType,
    reportBusinessDate,
    processedDates,
    processedDatesLoading,
    downloadingReport,
    openReportDialog,
    closeReportDialog,
    isDateDisabled,
    downloadConfirmedReport,
  }
}
