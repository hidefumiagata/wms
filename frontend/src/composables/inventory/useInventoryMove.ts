import { ref, computed, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import { useWarehouseStore } from '@/stores/warehouse'
import type { InventoryLocationItem } from '@/api/generated/models/inventory-location-item'
import { unitTypeLabel } from '@/utils/inventoryFormatters'

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

export function useInventoryMove() {
  const { t } = useI18n()
  const router = useRouter()
  const route = useRoute()
  const warehouseStore = useWarehouseStore()

  const loading = ref(false)
  const submitting = ref(false)

  // 移動元
  const fromLocationCode = ref('')
  const fromLocationId = ref<number | null>(null)
  const fromInventoryOptions = ref<InventoryOption[]>([])
  const selectedProductId = ref<number | null>(null)
  const selectedUnitType = ref<string | null>(null)
  const fromInventory = computed<InventoryOption | null>(() => {
    if (!selectedProductId.value || !selectedUnitType.value) return null
    return fromInventoryOptions.value.find(
      i => i.productId === selectedProductId.value && i.unitType === selectedUnitType.value
    ) ?? null
  })

  // 移動先
  const toLocationCode = ref('')
  const toLocationId = ref<number | null>(null)
  const toCurrentQty = ref<number | null>(null)

  // 移動数量
  const moveQty = ref(1)

  // --- AbortController ---
  let abortController: AbortController | null = null
  onUnmounted(() => { abortController?.abort() })

  // --- 移動元ロケーション検索 ---
  async function fetchFromInventory() {
    if (!fromLocationCode.value.trim()) return
    abortController?.abort()
    abortController = new AbortController()

    loading.value = true
    try {
      // ロケーションID取得
      const locRes = await apiClient.get('/master/locations', {
        params: {
          warehouseId: warehouseStore.currentWarehouseId,
          locationCode: fromLocationCode.value.trim(),
          page: 0, size: 1,
        },
        signal: abortController.signal,
      })
      const locs = locRes.data.content ?? []
      if (locs.length === 0) {
        ElMessage.warning(t('inventory.locationNotFound'))
        fromInventoryOptions.value = []
        fromLocationId.value = null
        loading.value = false
        return
      }
      fromLocationId.value = locs[0].id

      const res = await apiClient.get('/inventory', {
        params: {
          warehouseId: warehouseStore.currentWarehouseId,
          locationCodePrefix: fromLocationCode.value.trim(),
          viewType: 'LOCATION',
          page: 0,
          size: 100,
        },
        signal: abortController.signal,
      })
      const items: InventoryLocationItem[] = res.data.content ?? []
      fromInventoryOptions.value = items.map(i => ({
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
    } catch (err) {
      if (axios.isCancel(err)) return
      fromInventoryOptions.value = []
      ElMessage.error(t('inventory.fetchError'))
    } finally {
      loading.value = false
    }
  }

  // 商品選択肢（重複除去）
  const productOptions = computed(() => {
    const seen = new Set<number>()
    return fromInventoryOptions.value.filter(i => {
      if (seen.has(i.productId)) return false
      seen.add(i.productId)
      return true
    })
  })

  // 荷姿選択肢（選択された商品でフィルタ）
  const unitTypeOptions = computed(() => {
    if (!selectedProductId.value) return []
    return fromInventoryOptions.value.filter(i => i.productId === selectedProductId.value)
  })

  function onProductChange() {
    selectedUnitType.value = null
  }

  // --- 移動先ロケーション検索 ---
  async function fetchToLocationInfo() {
    if (!toLocationCode.value.trim()) {
      toLocationId.value = null
      toCurrentQty.value = null
      return
    }
    try {
      // ロケーション検索（コード完全一致を期待）
      const locRes = await apiClient.get('/master/locations', {
        params: {
          warehouseId: warehouseStore.currentWarehouseId,
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

      // 移動先在庫数取得
      if (selectedProductId.value && selectedUnitType.value) {
        const invRes = await apiClient.get('/inventory', {
          params: {
            warehouseId: warehouseStore.currentWarehouseId,
            locationCodePrefix: toLocationCode.value.trim(),
            productId: selectedProductId.value,
            unitType: selectedUnitType.value,
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
  async function submitMove() {
    if (!fromInventory.value || !toLocationId.value || !fromLocationId.value) return

    // バリデーション
    if (fromLocationCode.value.trim() === toLocationCode.value.trim()) {
      ElMessage.error(t('inventory.sameLocationError'))
      return
    }
    if (moveQty.value < 1 || moveQty.value > fromInventory.value.availableQty) {
      ElMessage.error(t('inventory.moveQtyExceedsAvailable'))
      return
    }

    const utLabel = unitTypeLabel(fromInventory.value.unitType, t)
    try {
      await ElMessageBox.confirm(
        t('inventory.moveConfirmMessage', {
          from: fromLocationCode.value,
          to: toLocationCode.value,
          unitType: utLabel,
          qty: moveQty.value,
        }),
        t('common.confirm'),
        { type: 'warning' }
      )
    } catch {
      return // cancelled
    }

    submitting.value = true
    try {
      await apiClient.post('/inventory/move', {
        fromLocationId: fromLocationId.value,
        productId: fromInventory.value.productId,
        unitType: fromInventory.value.unitType,
        lotNumber: fromInventory.value.lotNumber ?? null,
        expiryDate: fromInventory.value.expiryDate ?? null,
        toLocationId: toLocationId.value,
        moveQty: moveQty.value,
      })
      ElMessage.success(t('inventory.moveSuccess'))
      router.push({ name: 'inventory-list' })
    } catch (err) {
      const error = toApiError(err)
      if (error.response?.status === 422 || error.response?.status === 409) {
        ElMessage.error(error.response.data?.message ?? t('inventory.moveError'))
      } else {
        ElMessage.error(t('inventory.moveError'))
      }
    } finally {
      submitting.value = false
    }
  }

  // --- 初期化（INV-001から遷移時のquery params引き継ぎ）---
  function initFromRoute() {
    const q = route.query
    if (q.locationCode) {
      fromLocationCode.value = String(q.locationCode)
      fetchFromInventory().then(() => {
        if (q.productId) selectedProductId.value = Number(q.productId)
        if (q.unitType) selectedUnitType.value = String(q.unitType)
      })
    }
  }

  function goBack() {
    router.push({ name: 'inventory-list' })
  }

  return {
    loading,
    submitting,
    fromLocationCode,
    fromInventoryOptions,
    selectedProductId,
    selectedUnitType,
    fromInventory,
    toLocationCode,
    toLocationId,
    toCurrentQty,
    moveQty,
    productOptions,
    unitTypeOptions,
    onProductChange,
    fetchFromInventory,
    fetchToLocationInfo,
    submitMove,
    initFromRoute,
    goBack,
  }
}
