import { ref, computed, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import { useWarehouseStore } from '@/stores/warehouse'
import { unitTypeLabel } from '@/utils/inventoryFormatters'
import type { InventoryLocationItem } from '@/api/generated/models/inventory-location-item'
import type { CorrectionHistoryItem } from '@/api/generated/models/correction-history-item'

interface InventoryOption {
  productId: number
  productCode: string
  productName: string
  unitType: string
  quantity: number
  allocatedQty: number
  availableQty: number
  lotNumber?: string | null
  expiryDate?: string | null
}

export function useInventoryCorrection() {
  const { t } = useI18n()
  const router = useRouter()
  const warehouseStore = useWarehouseStore()

  const loading = ref(false)
  const submitting = ref(false)

  // 対象在庫選択
  const locationCode = ref('')
  const locationId = ref<number | null>(null)
  const inventoryOptions = ref<InventoryOption[]>([])
  const selectedProductId = ref<number | null>(null)
  const selectedUnitType = ref<string | null>(null)

  // 訂正入力
  const newQty = ref<number | null>(null)
  const reason = ref('')

  // 訂正履歴
  const correctionHistory = ref<CorrectionHistoryItem[]>([])

  // --- AbortController ---
  let abortController: AbortController | null = null
  onUnmounted(() => {
    abortController?.abort()
  })

  // 選択中の在庫
  const selectedInventory = computed<InventoryOption | null>(() => {
    if (!selectedProductId.value || !selectedUnitType.value) return null
    return (
      inventoryOptions.value.find(
        (i) => i.productId === selectedProductId.value && i.unitType === selectedUnitType.value,
      ) ?? null
    )
  })

  // 差異（自動計算）
  const diff = computed(() => {
    if (newQty.value == null || !selectedInventory.value) return null
    return newQty.value - selectedInventory.value.quantity
  })

  // 商品選択肢
  const productOptions = computed(() => {
    const seen = new Set<number>()
    return inventoryOptions.value.filter((i) => {
      if (seen.has(i.productId)) return false
      seen.add(i.productId)
      return true
    })
  })

  // 荷姿選択肢
  const unitTypeOptions = computed(() => {
    if (!selectedProductId.value) return []
    return inventoryOptions.value.filter((i) => i.productId === selectedProductId.value)
  })

  // --- ロケーション検索 ---
  async function fetchInventory() {
    if (!locationCode.value.trim()) return
    abortController?.abort()
    abortController = new AbortController()
    loading.value = true
    try {
      // ロケーションID取得
      const locRes = await apiClient.get('/master/locations', {
        params: {
          warehouseId: warehouseStore.selectedWarehouseId,
          locationCode: locationCode.value.trim(),
          page: 0,
          size: 1,
        },
        signal: abortController.signal,
      })
      const locs = locRes.data.content ?? []
      if (locs.length === 0) {
        ElMessage.warning(t('inventory.locationNotFound'))
        inventoryOptions.value = []
        locationId.value = null
        return
      }
      locationId.value = locs[0].id

      // 在庫取得
      const res = await apiClient.get('/inventory', {
        params: {
          warehouseId: warehouseStore.selectedWarehouseId,
          locationCodePrefix: locationCode.value.trim(),
          viewType: 'LOCATION',
          page: 0,
          size: 100,
        },
        signal: abortController.signal,
      })
      const items: InventoryLocationItem[] = res.data.content ?? []
      inventoryOptions.value = items.map((i) => ({
        productId: i.productId,
        productCode: i.productCode,
        productName: i.productName,
        unitType: i.unitType,
        quantity: i.quantity,
        allocatedQty: i.allocatedQty,
        availableQty: i.availableQty,
        lotNumber: i.lotNumber,
        expiryDate: i.expiryDate,
      }))
      selectedProductId.value = null
      selectedUnitType.value = null
      newQty.value = null
      reason.value = ''
    } catch (err) {
      if (axios.isCancel(err)) return
      inventoryOptions.value = []
      ElMessage.error(t('inventory.fetchError'))
    } finally {
      loading.value = false
    }
  }

  function onProductChange() {
    selectedUnitType.value = null
    newQty.value = null
    correctionHistory.value = []
  }

  async function fetchCorrectionHistory() {
    if (!locationId.value || !selectedProductId.value || !selectedUnitType.value) {
      correctionHistory.value = []
      return
    }
    try {
      const res = await apiClient.get('/inventory/correction-history', {
        params: {
          warehouseId: warehouseStore.selectedWarehouseId,
          locationId: locationId.value,
          productId: selectedProductId.value,
          unitType: selectedUnitType.value,
        },
        signal: abortController?.signal,
      })
      correctionHistory.value = res.data ?? []
    } catch (err) {
      if (axios.isCancel(err)) return
      correctionHistory.value = []
    }
  }

  async function onUnitTypeChange() {
    if (selectedInventory.value) {
      newQty.value = selectedInventory.value.quantity
    }
    await fetchCorrectionHistory()
  }

  // --- 登録 ---
  async function submitCorrection() {
    if (!selectedInventory.value || !locationId.value) return

    if (newQty.value == null || newQty.value < 0) {
      ElMessage.error(t('inventory.correctionError'))
      return
    }
    if (newQty.value < selectedInventory.value.allocatedQty) {
      ElMessage.error(
        t('inventory.correctionBelowAllocated', {
          allocated: selectedInventory.value.allocatedQty,
        }),
      )
      return
    }
    if (!reason.value.trim() || reason.value.length > 200) {
      ElMessage.error(t('inventory.reason'))
      return
    }

    const utLabel = unitTypeLabel(selectedInventory.value.unitType, t)
    const confirmMsg = t('inventory.correctionConfirmMessage', {
      loc: locationCode.value,
      product: `${selectedInventory.value.productCode} ${selectedInventory.value.productName}`,
      unitType: utLabel,
      current: selectedInventory.value.quantity,
      newQty: newQty.value,
      diff: diff.value,
    })

    const fullMsg =
      newQty.value === 0
        ? `${confirmMsg}\n\n${t('inventory.correctionConfirmZeroMessage')}`
        : confirmMsg

    try {
      await ElMessageBox.confirm(fullMsg, t('common.confirm'), { type: 'warning' })
    } catch {
      return
    }

    submitting.value = true
    try {
      await apiClient.post('/inventory/correction', {
        locationId: locationId.value,
        productId: selectedInventory.value.productId,
        unitType: selectedInventory.value.unitType,
        lotNumber: selectedInventory.value.lotNumber ?? null,
        expiryDate: selectedInventory.value.expiryDate ?? null,
        newQty: newQty.value,
        reason: reason.value.trim(),
      })
      ElMessage.success(t('inventory.correctionSuccess'))
      router.push({ name: 'inventory-list' })
    } catch (err) {
      const error = toApiError(err)
      if (error.response?.status === 422 || error.response?.status === 409) {
        ElMessage.error(error.response.data?.message ?? t('inventory.correctionError'))
      } else {
        ElMessage.error(t('inventory.correctionError'))
      }
    } finally {
      submitting.value = false
    }
  }

  function goBack() {
    router.push({ name: 'inventory-list' })
  }

  return {
    loading,
    submitting,
    locationCode,
    locationId,
    inventoryOptions,
    selectedProductId,
    selectedUnitType,
    selectedInventory,
    newQty,
    reason,
    diff,
    productOptions,
    unitTypeOptions,
    correctionHistory,
    fetchInventory,
    onProductChange,
    onUnitTypeChange,
    submitCorrection,
    goBack,
  }
}
