import { ref, reactive, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import type { ProductDetail } from '@/api/generated/models/product-detail'
import type { ProductPageResponse } from '@/api/generated/models/product-page-response'
import { StorageCondition } from '@/api/generated/models/storage-condition'

export function useProductList() {
  const { t } = useI18n()

  // --- 状態 ---
  const items = ref<ProductDetail[]>([])
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)

  const searchForm = reactive({
    productCode: '',
    productName: '',
    storageCondition: null as StorageCondition | null,
    isActive: true as boolean | null, // デフォルト: 有効のみ（設計書 MST001-F05）
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
    abortController?.abort()
    abortController = new AbortController()
    const signal = abortController.signal

    loading.value = true
    try {
      const params: Record<string, unknown> = {
        page: page.value - 1,
        size: pageSize.value,
        sort: 'productCode,asc',
      }
      if (searchForm.productCode) params.productCode = searchForm.productCode
      if (searchForm.productName) params.productName = searchForm.productName
      if (searchForm.storageCondition) params.storageCondition = searchForm.storageCondition
      if (searchForm.isActive !== null) params.isActive = searchForm.isActive

      const res = await apiClient.get<ProductPageResponse>('/master/products', {
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
        ElMessage.error(t('master.product.fetchError'))
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
    searchForm.productCode = ''
    searchForm.productName = ''
    searchForm.storageCondition = null
    searchForm.isActive = true // デフォルト: 有効のみに戻す（設計書 EVT-MST001-002）
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

  async function handleToggleActive(row: ProductDetail) {
    const isDeactivating = row.isActive
    const confirmMsg = isDeactivating
      ? t('master.product.confirmDeactivate')
      : t('master.product.confirmActivate')

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
      await apiClient.patch(`/master/products/${row.id}/toggle-active`, {
        isActive: !row.isActive,
        version: row.version,
      })
      ElMessage.success(
        isDeactivating
          ? t('master.product.deactivateSuccess')
          : t('master.product.activateSuccess')
      )
      await fetchList()
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 422) {
        ElMessage.error(t('master.product.cannotDeactivateHasInventory'))
      } else if (error.response.status === 409) {
        ElMessage.error(t('error.optimisticLock'))
      }
      // 403/500 はインターセプターが処理済み
    } finally {
      loading.value = false
    }
  }

  function storageConditionLabel(condition: string): string {
    switch (condition) {
      case StorageCondition.Ambient: return t('master.product.storageAmbient')
      case StorageCondition.Refrigerated: return t('master.product.storageRefrigerated')
      case StorageCondition.Frozen: return t('master.product.storageFrozen')
      default: return condition
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
    storageConditionLabel,
  }
}
