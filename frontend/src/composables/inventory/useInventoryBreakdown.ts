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

interface InventoryOption {
  productId: number
  productCode: string
  productName: string
  unitType: string
  quantity: number
  allocatedQty: number
  availableQty: number
}

interface ProductInfo {
  id: number
  productCode: string
  productName: string
  caseQuantity: number
  ballQuantity: number
}

export function useInventoryBreakdown() {
  const { t } = useI18n()
  const router = useRouter()
  const warehouseStore = useWarehouseStore()

  const loading = ref(false)
  const submitting = ref(false)

  // ばらし元
  const fromLocationCode = ref('')
  const fromLocationId = ref<number | null>(null)
  const fromInventoryOptions = ref<InventoryOption[]>([])
  const selectedProductId = ref<number | null>(null)
  const fromUnitType = ref<string | null>(null)
  const breakdownQty = ref(1)

  // ばらし先
  const toUnitType = ref<string | null>(null)
  const toLocationCode = ref('')
  const toLocationId = ref<number | null>(null)
  const toCurrentQty = ref<number | null>(null)

  // 商品マスタ情報（変換レート用）
  const productInfo = ref<ProductInfo | null>(null)

  // --- AbortController ---
  let abortController: AbortController | null = null
  onUnmounted(() => {
    abortController?.abort()
  })

  // 選択中の在庫
  const fromInventory = computed<InventoryOption | null>(() => {
    if (!selectedProductId.value || !fromUnitType.value) return null
    return (
      fromInventoryOptions.value.find(
        (i) => i.productId === selectedProductId.value && i.unitType === fromUnitType.value,
      ) ?? null
    )
  })

  // 商品選択肢
  const productOptions = computed(() => {
    const seen = new Set<number>()
    return fromInventoryOptions.value.filter((i) => {
      if (seen.has(i.productId)) return false
      seen.add(i.productId)
      return true
    })
  })

  // ばらし元荷姿選択肢（PIECEは不可）
  const fromUnitTypeOptions = computed(() => {
    if (!selectedProductId.value) return []
    return fromInventoryOptions.value.filter(
      (i) => i.productId === selectedProductId.value && i.unitType !== 'PIECE',
    )
  })

  // ばらし先荷姿選択肢（ばらし元より小さい荷姿のみ）
  const toUnitTypeOptions = computed(() => {
    if (!fromUnitType.value) return []
    if (fromUnitType.value === 'CASE') return ['BALL', 'PIECE']
    if (fromUnitType.value === 'BALL') return ['PIECE']
    return []
  })

  // 変換レート
  const conversionRate = computed(() => {
    if (!productInfo.value || !fromUnitType.value || !toUnitType.value) return null
    const p = productInfo.value
    if (fromUnitType.value === 'CASE' && toUnitType.value === 'BALL') return p.caseQuantity
    if (fromUnitType.value === 'CASE' && toUnitType.value === 'PIECE')
      return p.caseQuantity * p.ballQuantity
    if (fromUnitType.value === 'BALL' && toUnitType.value === 'PIECE') return p.ballQuantity
    return null
  })

  // 変換後数量
  const convertedQty = computed(() => {
    if (!conversionRate.value) return null
    return breakdownQty.value * conversionRate.value
  })

  // --- ロケーション検索 ---
  async function fetchFromInventory() {
    if (!fromLocationCode.value.trim()) return
    abortController?.abort()
    abortController = new AbortController()
    loading.value = true
    try {
      // ロケーションID取得
      const locRes = await apiClient.get('/master/locations', {
        params: {
          warehouseId: warehouseStore.selectedWarehouseId,
          locationCode: fromLocationCode.value.trim(),
          page: 0,
          size: 1,
        },
        signal: abortController.signal,
      })
      const locs = locRes.data.content ?? []
      if (locs.length === 0) {
        ElMessage.warning(t('inventory.locationNotFound'))
        fromInventoryOptions.value = []
        fromLocationId.value = null
        return
      }
      fromLocationId.value = locs[0].id

      // 在庫取得
      const res = await apiClient.get('/inventory', {
        params: {
          warehouseId: warehouseStore.selectedWarehouseId,
          locationCodePrefix: fromLocationCode.value.trim(),
          viewType: 'LOCATION',
          page: 0,
          size: 100,
        },
        signal: abortController.signal,
      })
      const items: InventoryLocationItem[] = res.data.content ?? []
      fromInventoryOptions.value = items.map((i) => ({
        productId: i.productId,
        productCode: i.productCode,
        productName: i.productName,
        unitType: i.unitType,
        quantity: i.quantity,
        allocatedQty: i.allocatedQty,
        availableQty: i.availableQty,
      }))
      selectedProductId.value = null
      fromUnitType.value = null
      toUnitType.value = null
    } catch (err) {
      if (axios.isCancel(err)) return
      fromInventoryOptions.value = []
      ElMessage.error(t('inventory.fetchError'))
    } finally {
      loading.value = false
    }
  }

  // 商品選択時: 商品マスタから変換レート取得
  async function onProductChange() {
    fromUnitType.value = null
    toUnitType.value = null
    productInfo.value = null
    if (!selectedProductId.value) return
    try {
      const res = await apiClient.get(`/master/products/${selectedProductId.value}`)
      const p = res.data
      productInfo.value = {
        id: p.id,
        productCode: p.productCode,
        productName: p.productName,
        caseQuantity: p.caseQuantity ?? 0,
        ballQuantity: p.ballQuantity ?? 0,
      }
    } catch {
      ElMessage.warning(t('inventory.conversionRateNotSet'))
    }
  }

  function onFromUnitTypeChange() {
    toUnitType.value = null
  }

  // --- ばらし先情報取得 ---
  async function fetchToLocationInfo() {
    if (!toLocationCode.value.trim()) {
      toLocationId.value = fromLocationId.value
      toCurrentQty.value = null
      return
    }
    try {
      const locRes = await apiClient.get('/master/locations', {
        params: {
          warehouseId: warehouseStore.selectedWarehouseId,
          locationCode: toLocationCode.value.trim(),
          page: 0,
          size: 1,
        },
      })
      const locs = locRes.data.content ?? []
      if (locs.length === 0) {
        toLocationId.value = null
        toCurrentQty.value = null
        ElMessage.warning(t('inventory.locationNotFound'))
        return
      }
      toLocationId.value = locs[0].id

      if (selectedProductId.value && toUnitType.value) {
        const invRes = await apiClient.get('/inventory', {
          params: {
            warehouseId: warehouseStore.selectedWarehouseId,
            locationCodePrefix: toLocationCode.value.trim(),
            productId: selectedProductId.value,
            unitType: toUnitType.value,
            viewType: 'LOCATION',
            page: 0,
            size: 1,
          },
        })
        const items: InventoryLocationItem[] = invRes.data.content ?? []
        toCurrentQty.value = items.length > 0 ? items[0].quantity : 0
      }
    } catch {
      toLocationId.value = null
      toCurrentQty.value = null
    }
  }

  // --- 登録 ---
  async function submitBreakdown() {
    if (!fromInventory.value || !fromLocationId.value || !toUnitType.value) return

    if (breakdownQty.value < 1 || breakdownQty.value > fromInventory.value.availableQty) {
      ElMessage.error(t('inventory.breakdownQtyExceedsAvailable'))
      return
    }
    if (!conversionRate.value || conversionRate.value <= 0) {
      ElMessage.error(t('inventory.conversionRateNotSet'))
      return
    }

    const effectiveToLocationId = toLocationId.value ?? fromLocationId.value
    const fromUtLabel = unitTypeLabel(fromUnitType.value!, t)
    const toUtLabel = unitTypeLabel(toUnitType.value, t)

    try {
      await ElMessageBox.confirm(
        t('inventory.breakdownConfirmMessage', {
          fromUnitType: fromUtLabel,
          qty: breakdownQty.value,
          toUnitType: toUtLabel,
          result: convertedQty.value,
        }),
        t('common.confirm'),
        { type: 'warning' },
      )
    } catch {
      return
    }

    submitting.value = true
    try {
      await apiClient.post('/inventory/breakdown', {
        fromLocationId: fromLocationId.value,
        productId: fromInventory.value.productId,
        fromUnitType: fromUnitType.value,
        breakdownQty: breakdownQty.value,
        toUnitType: toUnitType.value,
        toLocationId: effectiveToLocationId,
      })
      ElMessage.success(t('inventory.breakdownSuccess'))
      router.push({ name: 'inventory-list' })
    } catch (err) {
      const error = toApiError(err)
      if (error.response?.status === 422 || error.response?.status === 409) {
        ElMessage.error(error.response.data?.message ?? t('inventory.breakdownError'))
      } else {
        ElMessage.error(t('inventory.breakdownError'))
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
    fromLocationCode,
    fromLocationId,
    fromInventoryOptions,
    selectedProductId,
    fromUnitType,
    breakdownQty,
    toUnitType,
    toLocationCode,
    toLocationId,
    toCurrentQty,
    fromInventory,
    productOptions,
    fromUnitTypeOptions,
    toUnitTypeOptions,
    productInfo,
    conversionRate,
    convertedQty,
    onProductChange,
    onFromUnitTypeChange,
    fetchFromInventory,
    fetchToLocationInfo,
    submitBreakdown,
    goBack,
  }
}
