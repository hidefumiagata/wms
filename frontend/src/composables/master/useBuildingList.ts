import { ref, reactive, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import { useWarehouseStore } from '@/stores/warehouse'
import type { BuildingListItem } from '@/api/generated/models/building-list-item'
import type { BuildingDetail } from '@/api/generated/models/building-detail'
import type { PageResponse } from '@/api/types'

export function useBuildingList() {
  const { t } = useI18n()
  const warehouseStore = useWarehouseStore()

  const items = ref<BuildingListItem[]>([])
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)

  const searchForm = reactive({
    isActive: null as boolean | null,
  })

  let abortController: AbortController | null = null

  onUnmounted(() => {
    abortController?.abort()
  })

  async function fetchList() {
    if (!warehouseStore.selectedWarehouseId) {
      items.value = []
      total.value = 0
      return
    }

    abortController?.abort()
    abortController = new AbortController()
    const signal = abortController.signal

    loading.value = true
    try {
      const params: Record<string, unknown> = {
        warehouseId: warehouseStore.selectedWarehouseId,
        page: page.value - 1,
        size: pageSize.value,
      }
      if (searchForm.isActive !== null) params.isActive = searchForm.isActive

      const res = await apiClient.get<PageResponse<BuildingListItem>>('/master/buildings', {
        params,
        signal,
      })
      items.value = res.data.content
      total.value = res.data.totalElements
    } catch (err: unknown) {
      if (axios.isCancel(err)) return
      items.value = []
      total.value = 0
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else {
        ElMessage.error(t('master.building.fetchError'))
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
    searchForm.isActive = null
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

  async function handleToggleActive(row: BuildingListItem) {
    const isDeactivating = row.isActive
    const confirmMsg = isDeactivating
      ? t('master.building.confirmDeactivate')
      : t('master.building.confirmActivate')

    try {
      await ElMessageBox.confirm(confirmMsg, t('common.confirm'), {
        type: 'warning',
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
      })
    } catch {
      return
    }

    loading.value = true
    try {
      // BuildingListItem doesn't have version — fetch detail first
      const detail = await apiClient.get<BuildingDetail>(`/master/buildings/${row.id}`)
      await apiClient.patch(`/master/buildings/${row.id}/toggle-active`, {
        isActive: !row.isActive,
        version: detail.data.version,
      })
      ElMessage.success(
        isDeactivating
          ? t('master.building.deactivateSuccess')
          : t('master.building.activateSuccess'),
      )
      await fetchList()
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 422) {
        ElMessage.error(t('master.building.cannotDeactivateHasChildren'))
      } else if (error.response.status === 409) {
        ElMessage.error(t('error.optimisticLock'))
      }
    } finally {
      loading.value = false
    }
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
    handleToggleActive,
  }
}
