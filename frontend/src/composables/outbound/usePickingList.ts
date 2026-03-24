import { ref, reactive, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import { useWarehouseStore } from '@/stores/warehouse'
import type { PickingInstructionSummary } from '@/api/generated/models/picking-instruction-summary'
import type { PickingInstructionPageResponse } from '@/api/generated/models/picking-instruction-page-response'
import type { PickingInstructionStatus } from '@/api/generated/models/picking-instruction-status'

export function usePickingList() {
  const { t } = useI18n()
  const warehouseStore = useWarehouseStore()

  const items = ref<PickingInstructionSummary[]>([])
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)

  const searchForm = reactive({
    instructionNumber: '',
    status: null as PickingInstructionStatus | null,
    createdDateFrom: null as string | null,
    createdDateTo: null as string | null,
  })

  let abortController: AbortController | null = null
  onUnmounted(() => { abortController?.abort() })

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
        sort: 'createdAt,desc',
      }
      if (searchForm.instructionNumber?.trim()) params.instructionNumber = searchForm.instructionNumber.trim()
      if (searchForm.status) params.status = searchForm.status
      if (searchForm.createdDateFrom) params.createdDateFrom = searchForm.createdDateFrom
      if (searchForm.createdDateTo) params.createdDateTo = searchForm.createdDateTo

      const res = await apiClient.get<PickingInstructionPageResponse>('/outbound/picking', {
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
        ElMessage.error(t('outbound.picking.fetchError'))
      }
    } finally {
      if (!signal.aborted) loading.value = false
    }
  }

  function handleSearch() { page.value = 1; fetchList() }
  function handleReset() {
    searchForm.instructionNumber = ''
    searchForm.status = null
    searchForm.createdDateFrom = null
    searchForm.createdDateTo = null
    page.value = 1
    fetchList()
  }
  function handlePageChange(p: number) { page.value = p; fetchList() }
  function handleSizeChange(s: number) { pageSize.value = s; page.value = 1; fetchList() }

  return {
    items, loading, total, page, pageSize, searchForm,
    fetchList, handleSearch, handleReset, handlePageChange, handleSizeChange,
  }
}
