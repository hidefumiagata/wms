import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'

export interface WarehouseListItem {
  id: number
  warehouseCode: string
  warehouseName: string
  warehouseNameKana: string
  address: string | null
  isActive: boolean
  version: number
}

interface WarehouseListResponse {
  content: WarehouseListItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export function useWarehouseList() {
  const { t } = useI18n()

  // --- 状態 ---
  const items = ref<WarehouseListItem[]>([])
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1) // Element Plus は 1-based
  const pageSize = ref(20)

  const searchForm = reactive({
    warehouseCode: '',
    warehouseName: '',
    isActive: null as boolean | null,
  })

  // --- API呼び出し ---
  async function fetchList() {
    loading.value = true
    try {
      const params: Record<string, unknown> = {
        page: page.value - 1,
        size: pageSize.value,
        sort: 'warehouseCode,asc',
      }
      if (searchForm.warehouseCode) params.warehouseCode = searchForm.warehouseCode
      if (searchForm.warehouseName) params.warehouseName = searchForm.warehouseName
      if (searchForm.isActive !== null) params.isActive = searchForm.isActive

      const res = await apiClient.get<WarehouseListResponse>('/master/warehouses', { params })
      items.value = res.data.content
      total.value = res.data.totalElements
    } catch {
      ElMessage.error(t('master.warehouse.fetchError'))
    } finally {
      loading.value = false
    }
  }

  // --- 操作 ---
  function handleSearch() {
    page.value = 1
    fetchList()
  }

  function handleReset() {
    searchForm.warehouseCode = ''
    searchForm.warehouseName = ''
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

  async function handleToggleActive(row: WarehouseListItem) {
    const isDeactivating = row.isActive
    const confirmMsg = isDeactivating
      ? t('master.warehouse.confirmDeactivate')
      : t('master.warehouse.confirmActivate')

    try {
      await ElMessageBox.confirm(confirmMsg, t('common.confirm'), {
        type: 'warning',
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
      })
    } catch {
      return // キャンセル
    }

    loading.value = true
    try {
      await apiClient.patch(`/master/warehouses/${row.id}/deactivate`, {
        isActive: !row.isActive,
        version: row.version,
      })
      ElMessage.success(
        isDeactivating
          ? t('master.warehouse.deactivateSuccess')
          : t('master.warehouse.activateSuccess')
      )
      fetchList()
    } catch (err: unknown) {
      const error = toApiError(err)
      if (error.response?.status === 422) {
        ElMessage.error(t('master.warehouse.cannotDeactivateHasInventory'))
      } else if (error.response?.status === 409) {
        ElMessage.error(t('error.optimisticLock'))
      } else {
        ElMessage.error(t('error.server'))
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
