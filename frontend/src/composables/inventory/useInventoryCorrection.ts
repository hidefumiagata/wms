import { ref, reactive, computed, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
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

export function useInventoryCorrection(formRef: ReturnType<typeof ref<FormInstance>>) {
  const { t } = useI18n()
  const router = useRouter()
  const warehouseStore = useWarehouseStore()

  const loading = ref(false)
  const submitting = ref(false)

  // フォームモデル
  const form = reactive({
    locationCode: '',
    selectedProductId: null as number | null,
    selectedUnitType: null as string | null,
    newQty: null as number | null,
    reason: '',
  })

  // バリデーションルール
  const rules: FormRules = {
    locationCode: [
      {
        required: true,
        message: () => t('inventory.validation.locationCodeRequired'),
        trigger: 'blur',
      },
    ],
    selectedProductId: [
      {
        required: true,
        message: () => t('inventory.validation.productRequired'),
        trigger: 'change',
      },
    ],
    selectedUnitType: [
      {
        required: true,
        message: () => t('inventory.validation.unitTypeRequired'),
        trigger: 'change',
      },
    ],
    newQty: [
      {
        required: true,
        message: () => t('inventory.validation.newQtyRequired'),
        trigger: 'change',
      },
    ],
    reason: [
      { required: true, message: () => t('inventory.validation.reasonRequired'), trigger: 'blur' },
      { max: 200, message: () => t('inventory.validation.reasonMaxLength'), trigger: 'blur' },
    ],
  }

  // 対象在庫選択
  const locationId = ref<number | null>(null)
  const inventoryOptions = ref<InventoryOption[]>([])

  // 訂正履歴
  const correctionHistory = ref<CorrectionHistoryItem[]>([])

  // --- AbortController ---
  let abortController: AbortController | null = null
  onUnmounted(() => {
    abortController?.abort()
  })

  // 選択中の在庫
  const selectedInventory = computed<InventoryOption | null>(() => {
    if (!form.selectedProductId || !form.selectedUnitType) return null
    return (
      inventoryOptions.value.find(
        (i) => i.productId === form.selectedProductId && i.unitType === form.selectedUnitType,
      ) ?? null
    )
  })

  // 差異（自動計算）
  const diff = computed(() => {
    if (form.newQty == null || !selectedInventory.value) return null
    return form.newQty - selectedInventory.value.quantity
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
    if (!form.selectedProductId) return []
    return inventoryOptions.value.filter((i) => i.productId === form.selectedProductId)
  })

  // --- ロケーション検索 ---
  async function fetchInventory() {
    if (!form.locationCode.trim()) return
    abortController?.abort()
    abortController = new AbortController()
    loading.value = true
    try {
      // ロケーションID取得
      const locRes = await apiClient.get('/master/locations', {
        params: {
          warehouseId: warehouseStore.selectedWarehouseId,
          locationCode: form.locationCode.trim(),
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
          locationCodePrefix: form.locationCode.trim(),
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
      form.selectedProductId = null
      form.selectedUnitType = null
      form.newQty = null
      form.reason = ''
    } catch (err) {
      if (axios.isCancel(err)) return
      inventoryOptions.value = []
      ElMessage.error(t('inventory.fetchError'))
    } finally {
      loading.value = false
    }
  }

  function onProductChange() {
    form.selectedUnitType = null
    form.newQty = null
    correctionHistory.value = []
  }

  async function fetchCorrectionHistory() {
    if (!locationId.value || !form.selectedProductId || !form.selectedUnitType) {
      correctionHistory.value = []
      return
    }
    try {
      const res = await apiClient.get('/inventory/correction-history', {
        params: {
          warehouseId: warehouseStore.selectedWarehouseId,
          locationId: locationId.value,
          productId: form.selectedProductId,
          unitType: form.selectedUnitType,
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
      form.newQty = selectedInventory.value.quantity
    }
    await fetchCorrectionHistory()
  }

  // --- 登録 ---
  async function submitCorrection() {
    // el-form バリデーション
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return

    if (!selectedInventory.value || !locationId.value) return

    if (form.newQty == null || form.newQty < 0) {
      ElMessage.error(t('inventory.correctionError'))
      return
    }
    if (form.newQty < selectedInventory.value.allocatedQty) {
      ElMessage.error(
        t('inventory.correctionBelowAllocated', {
          allocated: selectedInventory.value.allocatedQty,
        }),
      )
      return
    }

    const utLabel = unitTypeLabel(selectedInventory.value.unitType, t)
    const confirmMsg = t('inventory.correctionConfirmMessage', {
      loc: form.locationCode,
      product: `${selectedInventory.value.productCode} ${selectedInventory.value.productName}`,
      unitType: utLabel,
      current: selectedInventory.value.quantity,
      newQty: form.newQty,
      diff: diff.value,
    })

    const fullMsg =
      form.newQty === 0
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
        newQty: form.newQty,
        reason: form.reason.trim(),
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
    form,
    rules,
    locationId,
    inventoryOptions,
    selectedInventory,
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
