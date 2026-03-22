import { ref, reactive, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import { useWarehouseStore } from '@/stores/warehouse'
import type { AreaListItem } from '@/api/generated/models/area-list-item'
import type { AreaDetail } from '@/api/generated/models/area-detail'
import type { BuildingListItem } from '@/api/generated/models/building-list-item'

interface AreaPageResponse {
  content: AreaListItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

interface BuildingPageResponse {
  content: BuildingListItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export function useAreaList() {
  const { t } = useI18n()
  const warehouseStore = useWarehouseStore()

  const items = ref<AreaListItem[]>([])
  const buildings = ref<BuildingListItem[]>([])
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)

  const searchForm = reactive({
    buildingId: null as number | null,
    storageCondition: '' as string,
    areaType: '' as string,
    isActive: null as boolean | null,
  })

  let abortController: AbortController | null = null

  onUnmounted(() => {
    abortController?.abort()
  })

  async function fetchBuildings() {
    if (!warehouseStore.selectedWarehouseId) return
    try {
      const res = await apiClient.get<BuildingPageResponse>('/master/buildings', {
        params: { warehouseId: warehouseStore.selectedWarehouseId, isActive: true, size: 100 },
      })
      buildings.value = res.data.content
    } catch {
      buildings.value = []
    }
  }

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
      if (searchForm.buildingId) params.buildingId = searchForm.buildingId
      if (searchForm.storageCondition) params.storageCondition = searchForm.storageCondition
      if (searchForm.areaType) params.areaType = searchForm.areaType
      if (searchForm.isActive !== null) params.isActive = searchForm.isActive

      const res = await apiClient.get<AreaPageResponse>('/master/areas', { params, signal })
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
        ElMessage.error(t('master.area.fetchError'))
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
    searchForm.buildingId = null
    searchForm.storageCondition = ''
    searchForm.areaType = ''
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

  async function handleToggleActive(row: AreaListItem) {
    const isDeactivating = row.isActive
    const confirmMsg = isDeactivating
      ? t('master.area.confirmDeactivate')
      : t('master.area.confirmActivate')

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
      const detail = await apiClient.get<AreaDetail>(`/master/areas/${row.id}`)
      await apiClient.patch(`/master/areas/${row.id}/toggle-active`, {
        isActive: !row.isActive,
        version: detail.data.version,
      })
      ElMessage.success(
        isDeactivating ? t('master.area.deactivateSuccess') : t('master.area.activateSuccess'),
      )
      await fetchList()
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 422) {
        ElMessage.error(t('master.area.cannotDeactivateHasChildren'))
      } else if (error.response.status === 409) {
        ElMessage.error(t('error.optimisticLock'))
      }
    } finally {
      loading.value = false
    }
  }

  return {
    items,
    buildings,
    loading,
    total,
    page,
    pageSize,
    searchForm,
    fetchList,
    fetchBuildings,
    handleSearch,
    handleReset,
    handlePageChange,
    handleSizeChange,
    handleToggleActive,
  }
}
