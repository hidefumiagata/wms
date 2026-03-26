import { ref, computed, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import { useWarehouseStore } from '@/stores/warehouse'

interface BuildingOption {
  id: number
  buildingName: string
}

interface AreaOption {
  id: number
  areaName: string
}

export function useStocktakeForm() {
  const { t } = useI18n()
  const router = useRouter()
  const warehouseStore = useWarehouseStore()

  const submitting = ref(false)

  // --- AbortController ---
  let abortController: AbortController | null = null
  onUnmounted(() => {
    abortController?.abort()
  })

  // フォーム
  const selectedBuildingId = ref<number | null>(null)
  const selectedAreaId = ref<number | null>(null)
  const stocktakeDate = ref(new Date().toISOString().slice(0, 10))
  const note = ref('')

  // マスタオプション
  const buildingOptions = ref<BuildingOption[]>([])
  const areaOptions = ref<AreaOption[]>([])

  // プレビュー
  const targetLocationCount = ref<number | null>(null)

  // --- 初期化: 棟マスタ取得 ---
  async function fetchBuildings() {
    try {
      const res = await apiClient.get('/master/buildings', {
        params: {
          warehouseId: warehouseStore.selectedWarehouseId,
          page: 0,
          size: 1000,
          isActive: true,
          sort: 'buildingName,asc',
        },
      })
      buildingOptions.value = (res.data.content ?? []).map(
        (b: { id: number; buildingName: string }) => ({
          id: b.id,
          buildingName: b.buildingName,
        }),
      )
    } catch {
      buildingOptions.value = []
    }
  }

  // --- 棟変更時: エリア取得 + ロケーション数プレビュー ---
  async function onBuildingChange() {
    selectedAreaId.value = null
    areaOptions.value = []
    targetLocationCount.value = null

    if (!selectedBuildingId.value) return

    try {
      const res = await apiClient.get('/master/areas', {
        params: {
          buildingId: selectedBuildingId.value,
          page: 0,
          size: 1000,
          isActive: true,
          sort: 'areaName,asc',
        },
      })
      areaOptions.value = (res.data.content ?? []).map((a: { id: number; areaName: string }) => ({
        id: a.id,
        areaName: a.areaName,
      }))
    } catch {
      areaOptions.value = []
    }

    await fetchLocationCount()
  }

  // --- エリア変更時: ロケーション数プレビュー ---
  async function onAreaChange() {
    await fetchLocationCount()
  }

  // --- ロケーション数取得 ---
  async function fetchLocationCount() {
    if (!selectedBuildingId.value) {
      targetLocationCount.value = null
      return
    }
    abortController?.abort()
    abortController = new AbortController()
    try {
      const params: Record<string, unknown> = {
        warehouseId: warehouseStore.selectedWarehouseId,
        buildingId: selectedBuildingId.value,
        isActive: true,
        page: 0,
        size: 1,
      }
      if (selectedAreaId.value) params.areaId = selectedAreaId.value
      const res = await apiClient.get('/master/locations', {
        params,
        signal: abortController.signal,
      })
      targetLocationCount.value = res.data.totalElements ?? 0
    } catch {
      targetLocationCount.value = null
    }
  }

  // --- 棚卸開始 ---
  async function submitStart() {
    // バリデーション
    if (!selectedBuildingId.value) {
      ElMessage.error(t('inventory.stocktakeBuildingRequired'))
      return
    }
    if (!stocktakeDate.value) {
      ElMessage.error(t('inventory.stocktakeDateRequired'))
      return
    }
    if (note.value.length > 200) {
      ElMessage.error(t('inventory.stocktakeNotePlaceholder'))
      return
    }
    // 実施日は当日以降（フロントエンドバリデーション）
    const today = new Date().toISOString().slice(0, 10)
    if (stocktakeDate.value < today) {
      ElMessage.error(t('inventory.stocktakeDateRequired'))
      return
    }

    try {
      await ElMessageBox.confirm(
        t('inventory.stocktakeStartConfirm', { count: targetLocationCount.value ?? 0 }),
        t('common.confirm'),
        { type: 'warning' },
      )
    } catch {
      return
    }

    submitting.value = true
    try {
      const res = await apiClient.post('/inventory/stocktakes', {
        warehouseId: warehouseStore.selectedWarehouseId,
        buildingId: selectedBuildingId.value,
        areaId: selectedAreaId.value ?? null,
        stocktakeDate: stocktakeDate.value,
        note: note.value.trim() || null,
      })
      ElMessage.success(t('inventory.stocktakeStartSuccess', { number: res.data.stocktakeNumber }))
      router.push({ name: 'stocktake-detail', params: { id: res.data.id } })
    } catch (err) {
      const error = toApiError(err)
      if (error.response?.status === 409) {
        ElMessage.error(t('inventory.stocktakeConflictError'))
      } else if (error.response?.status === 422) {
        ElMessage.error(error.response.data?.message ?? t('inventory.stocktakeStartError'))
      } else {
        ElMessage.error(t('inventory.stocktakeStartError'))
      }
    } finally {
      submitting.value = false
    }
  }

  function goBack() {
    router.push({ name: 'stocktake-list' })
  }

  return {
    submitting,
    selectedBuildingId,
    selectedAreaId,
    stocktakeDate,
    note,
    buildingOptions,
    areaOptions,
    targetLocationCount,
    fetchBuildings,
    onBuildingChange,
    onAreaChange,
    submitStart,
    goBack,
  }
}
