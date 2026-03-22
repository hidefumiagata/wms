import { ref, reactive, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import { useWarehouseStore } from '@/stores/warehouse'
import type { LocationListItem } from '@/api/generated/models/location-list-item'
import type { LocationFullDetail } from '@/api/generated/models/location-full-detail'
import type { AreaListItem } from '@/api/generated/models/area-list-item'

interface LocationPageResponse {
  content: LocationListItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

interface AreaPageResponse {
  content: AreaListItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export function useLocationList() {
  const { t } = useI18n()
  const warehouseStore = useWarehouseStore()

  const items = ref<LocationListItem[]>([])
  const areas = ref<AreaListItem[]>([])
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)

  const searchForm = reactive({
    codePrefix: '',
    areaId: null as number | null,
    isActive: null as boolean | null,
  })

  let abortController: AbortController | null = null

  onUnmounted(() => {
    abortController?.abort()
  })

  async function fetchAreas() {
    if (!warehouseStore.selectedWarehouseId) return
    try {
      const res = await apiClient.get<AreaPageResponse>('/master/areas', {
        params: { warehouseId: warehouseStore.selectedWarehouseId, isActive: true, size: 100 },
      })
      areas.value = res.data.content
    } catch {
      areas.value = []
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
        sort: 'locationCode,asc',
      }
      if (searchForm.codePrefix) params.codePrefix = searchForm.codePrefix
      if (searchForm.areaId) params.areaId = searchForm.areaId
      if (searchForm.isActive !== null) params.isActive = searchForm.isActive

      const res = await apiClient.get<LocationPageResponse>('/master/locations', {
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
        ElMessage.error(t('master.location.fetchError'))
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
    searchForm.codePrefix = ''
    searchForm.areaId = null
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

  async function handleToggleActive(row: LocationListItem) {
    const isDeactivating = row.isActive
    const confirmMsg = isDeactivating
      ? t('master.location.confirmDeactivate')
      : t('master.location.confirmActivate')

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
      const detail = await apiClient.get<LocationFullDetail>(`/master/locations/${row.id}`)
      await apiClient.patch(`/master/locations/${row.id}/toggle-active`, {
        isActive: !row.isActive,
        version: detail.data.version,
      })
      ElMessage.success(
        isDeactivating
          ? t('master.location.deactivateSuccess')
          : t('master.location.activateSuccess'),
      )
      await fetchList()
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 422) {
        const errorCode = error.response.data?.errorCode
        if (errorCode === 'CANNOT_DEACTIVATE_STOCKTAKE_IN_PROGRESS') {
          ElMessage.error(t('master.location.cannotDeactivateStocktakeInProgress'))
        } else {
          ElMessage.error(t('master.location.cannotDeactivateHasInventory'))
        }
      } else if (error.response.status === 409) {
        ElMessage.error(t('error.optimisticLock'))
      }
    } finally {
      loading.value = false
    }
  }

  return {
    items,
    areas,
    loading,
    total,
    page,
    pageSize,
    searchForm,
    fetchList,
    fetchAreas,
    handleSearch,
    handleReset,
    handlePageChange,
    handleSizeChange,
    handleToggleActive,
  }
}
