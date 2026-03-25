import { ref, reactive, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import type { WarehouseListItem } from '@/api/generated/models/warehouse-list-item'
import type { PageResponse } from '@/api/types'

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

  // --- 並行リクエスト制御 ---
  // 新しいリクエストが来たら前のリクエストをキャンセルし、
  // 古いレスポンスで画面が上書きされる Race Condition を防ぐ
  let abortController: AbortController | null = null

  // コンポーネントのアンマウント時に進行中のリクエストをキャンセルし、
  // アンマウント後のステート更新（メモリリーク）を防ぐ
  onUnmounted(() => {
    abortController?.abort()
  })

  // --- API呼び出し ---
  async function fetchList() {
    // 前のリクエストをキャンセル
    abortController?.abort()
    abortController = new AbortController()
    const signal = abortController.signal

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

      const res = await apiClient.get<PageResponse<WarehouseListItem>>('/master/warehouses', {
        params,
        signal,
      })
      items.value = res.data.content
      total.value = res.data.totalElements
    } catch (err: unknown) {
      if (axios.isCancel(err)) return // キャンセル済み：新しいリクエストが loading を管理する

      // エラー時はページネーション状態と一致させるため空にリセット
      items.value = []
      total.value = 0
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else {
        ElMessage.error(t('master.warehouse.fetchError'))
      }
    } finally {
      // このリクエストがキャンセルされていなければ loading を解除
      // （キャンセル時は新しいリクエストが loading を引き継ぐ）
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
      await apiClient.patch(`/master/warehouses/${row.id}/toggle-active`, {
        isActive: !row.isActive,
        version: row.version,
      })
      ElMessage.success(
        isDeactivating
          ? t('master.warehouse.deactivateSuccess')
          : t('master.warehouse.activateSuccess')
      )
      await fetchList()
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 422) {
        ElMessage.error(t('master.warehouse.cannotDeactivateHasInventory'))
      } else if (error.response.status === 409) {
        ElMessage.error(t('error.optimisticLock'))
      }
      // 403/500 はインターセプターが処理済み
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
