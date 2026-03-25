import { ref, reactive, computed, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import { useWarehouseStore } from '@/stores/warehouse'
import { useAuthStore } from '@/stores/auth'
import type { InventoryLocationItem } from '@/api/generated/models/inventory-location-item'
import type { InventoryLocationPageResponse } from '@/api/generated/models/inventory-location-page-response'
import type { InventoryProductSummaryItem } from '@/api/generated/models/inventory-product-summary-item'
import type { InventoryProductSummaryPageResponse } from '@/api/generated/models/inventory-product-summary-page-response'

export type ViewType = 'LOCATION' | 'PRODUCT_SUMMARY'

export function useInventoryList() {
  const { t } = useI18n()
  const warehouseStore = useWarehouseStore()
  const auth = useAuthStore()

  const isViewer = computed(() => auth.user?.role === 'VIEWER')

  // --- 状態 ---
  const viewType = ref<ViewType>('LOCATION')
  const locationItems = ref<InventoryLocationItem[]>([])
  const productSummaryItems = ref<InventoryProductSummaryItem[]>([])
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)

  const searchForm = reactive({
    locationCodePrefix: '',
    productId: null as number | null,
    unitType: null as string | null,
    storageCondition: null as string | null,
  })

  // --- 商品プルダウン ---
  const productOptions = ref<{ id: number; productCode: string; productName: string }[]>([])

  async function fetchProductOptions() {
    try {
      const res = await apiClient.get('/master/products', {
        params: { page: 0, size: 1000, isActive: true, sort: 'productCode,asc' },
      })
      productOptions.value = res.data.content.map((p: { id: number; productCode: string; productName: string }) => ({
        id: p.id,
        productCode: p.productCode,
        productName: p.productName,
      }))
    } catch {
      // エラーは無視（プルダウンが空になるだけ）
    }
  }

  // --- 並行リクエスト制御 ---
  let abortController: AbortController | null = null
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
        warehouseId: warehouseStore.selectedWarehouseId,
        viewType: viewType.value,
        page: page.value - 1,
        size: pageSize.value,
      }
      if (searchForm.locationCodePrefix?.trim()) params.locationCodePrefix = searchForm.locationCodePrefix.trim()
      if (searchForm.productId) params.productId = searchForm.productId
      if (searchForm.unitType) params.unitType = searchForm.unitType
      if (searchForm.storageCondition) params.storageCondition = searchForm.storageCondition

      if (viewType.value === 'LOCATION') {
        params.sort = 'locationCode,asc'
        const res = await apiClient.get<InventoryLocationPageResponse>('/inventory', {
          params,
          signal,
        })
        locationItems.value = res.data.content ?? []
        productSummaryItems.value = []
        total.value = res.data.totalElements ?? 0
      } else {
        params.sort = 'productCode,asc'
        const res = await apiClient.get<InventoryProductSummaryPageResponse>('/inventory', {
          params,
          signal,
        })
        productSummaryItems.value = res.data.content ?? []
        locationItems.value = []
        total.value = res.data.totalElements ?? 0
      }
    } catch (err: unknown) {
      if (axios.isCancel(err)) return

      locationItems.value = []
      productSummaryItems.value = []
      total.value = 0
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else {
        ElMessage.error(t('inventory.fetchError'))
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
    searchForm.locationCodePrefix = ''
    searchForm.productId = null
    searchForm.unitType = null
    searchForm.storageCondition = null
    page.value = 1
    fetchList()
  }

  function handleViewTypeChange(type: ViewType) {
    viewType.value = type
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
    viewType,
    locationItems,
    productSummaryItems,
    loading,
    total,
    page,
    pageSize,
    searchForm,
    productOptions,
    isViewer,
    fetchList,
    fetchProductOptions,
    handleSearch,
    handleReset,
    handleViewTypeChange,
    handlePageChange,
    handleSizeChange,
  }
}
