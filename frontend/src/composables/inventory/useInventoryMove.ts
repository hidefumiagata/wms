import { type Ref, ref, reactive, computed, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
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

export function useInventoryMove(formRef: Ref<FormInstance | undefined>) {
  const { t } = useI18n()
  const router = useRouter()
  const route = useRoute()
  const warehouseStore = useWarehouseStore()

  const loading = ref(false)
  const submitting = ref(false)

  // フォームモデル
  const form = reactive({
    fromLocationCode: '',
    selectedProductId: null as number | null,
    selectedUnitType: null as string | null,
    toLocationCode: '',
    moveQty: 1,
  })

  // バリデーションルール
  const rules: FormRules = {
    fromLocationCode: [
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
    toLocationCode: [
      {
        required: true,
        message: () => t('inventory.validation.toLocationCodeRequired'),
        trigger: 'blur',
      },
    ],
    moveQty: [
      {
        required: true,
        type: 'number',
        message: () => t('inventory.validation.moveQtyRequired'),
        trigger: 'change',
      },
    ],
  }

  // 移動元
  const fromLocationId = ref<number | null>(null)
  const fromInventoryOptions = ref<InventoryOption[]>([])
  const fromInventory = computed<InventoryOption | null>(() => {
    if (!form.selectedProductId || !form.selectedUnitType) return null
    return (
      fromInventoryOptions.value.find(
        (i) => i.productId === form.selectedProductId && i.unitType === form.selectedUnitType,
      ) ?? null
    )
  })

  // 移動先
  const toLocationId = ref<number | null>(null)
  const toCurrentQty = ref<number | null>(null)
  const toMaxQty = ref<number | null>(null)

  // --- AbortController ---
  let abortController: AbortController | null = null
  let toAbortController: AbortController | null = null
  onUnmounted(() => {
    abortController?.abort()
    toAbortController?.abort()
  })

  // --- 移動元ロケーション検索 ---
  async function fetchFromInventory() {
    if (!form.fromLocationCode.trim()) return
    abortController?.abort()
    abortController = new AbortController()

    loading.value = true
    try {
      // ロケーションID取得
      const locRes = await apiClient.get('/master/locations', {
        params: {
          warehouseId: warehouseStore.selectedWarehouseId,
          locationCode: form.fromLocationCode.trim(),
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
        loading.value = false
        return
      }
      fromLocationId.value = locs[0].id

      const res = await apiClient.get('/inventory', {
        params: {
          warehouseId: warehouseStore.selectedWarehouseId,
          locationCodePrefix: form.fromLocationCode.trim(),
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
        lotNumber: i.lotNumber,
        expiryDate: i.expiryDate,
      }))
      form.selectedProductId = null
      form.selectedUnitType = null
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
    return fromInventoryOptions.value.filter((i) => {
      if (seen.has(i.productId)) return false
      seen.add(i.productId)
      return true
    })
  })

  // 荷姿選択肢（選択された商品でフィルタ）
  const unitTypeOptions = computed(() => {
    if (!form.selectedProductId) return []
    return fromInventoryOptions.value.filter((i) => i.productId === form.selectedProductId)
  })

  function onProductChange() {
    form.selectedUnitType = null
  }

  // --- 移動先ロケーション検索 ---
  async function fetchToLocationInfo() {
    if (!form.toLocationCode.trim()) {
      toLocationId.value = null
      toCurrentQty.value = null
      toMaxQty.value = null
      return
    }
    toAbortController?.abort()
    toAbortController = new AbortController()

    try {
      // ロケーション検索（コード完全一致を期待）
      const locRes = await apiClient.get('/master/locations', {
        params: {
          warehouseId: warehouseStore.selectedWarehouseId,
          locationCode: form.toLocationCode.trim(),
          page: 0,
          size: 1,
        },
        signal: toAbortController.signal,
      })
      const locs = locRes.data.content ?? []
      if (locs.length === 0) {
        toLocationId.value = null
        toCurrentQty.value = null
        toMaxQty.value = null
        ElMessage.warning(t('inventory.locationNotFound'))
        return
      }
      toLocationId.value = locs[0].id

      // 移動先在庫数取得（同一荷姿の合計） + ロケーション上限取得
      if (form.selectedProductId && form.selectedUnitType) {
        const [invRes, capRes] = await Promise.all([
          apiClient.get('/inventory', {
            params: {
              warehouseId: warehouseStore.selectedWarehouseId,
              locationCodePrefix: form.toLocationCode.trim(),
              unitType: form.selectedUnitType,
              viewType: 'LOCATION',
              page: 0,
              size: 100,
            },
            signal: toAbortController.signal,
          }),
          apiClient.get('/inventory/location-capacity', {
            params: { unitType: form.selectedUnitType },
            signal: toAbortController.signal,
          }),
        ])
        const items: InventoryLocationItem[] = invRes.data.content ?? []
        toCurrentQty.value = items.reduce((sum, i) => sum + i.quantity, 0)
        toMaxQty.value = capRes.data.maxQuantity ?? null
      }
    } catch (err) {
      if (axios.isCancel(err)) return
      toLocationId.value = null
      toCurrentQty.value = null
      toMaxQty.value = null
      ElMessage.error(t('inventory.fetchError'))
    }
  }

  // --- 登録 ---
  async function submitMove() {
    // el-form バリデーション
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return

    if (!fromInventory.value || !toLocationId.value || !fromLocationId.value) return

    // ビジネスバリデーション
    if (form.fromLocationCode.trim() === form.toLocationCode.trim()) {
      ElMessage.error(t('inventory.sameLocationError'))
      return
    }
    if (form.moveQty < 1 || form.moveQty > fromInventory.value.availableQty) {
      ElMessage.error(t('inventory.moveQtyExceedsAvailable'))
      return
    }
    if (toMaxQty.value != null && toCurrentQty.value != null) {
      const afterQty = toCurrentQty.value + form.moveQty
      if (afterQty > toMaxQty.value) {
        const utLabel = unitTypeLabel(fromInventory.value.unitType, t)
        ElMessage.error(
          t('inventory.capacityExceeded', {
            max: toMaxQty.value,
            after: afterQty,
            pkg: utLabel,
          }),
        )
        return
      }
    }

    const utLabel = unitTypeLabel(fromInventory.value.unitType, t)
    try {
      await ElMessageBox.confirm(
        t('inventory.moveConfirmMessage', {
          from: form.fromLocationCode,
          to: form.toLocationCode,
          unitType: utLabel,
          qty: form.moveQty,
        }),
        t('common.confirm'),
        { type: 'warning' },
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
        moveQty: form.moveQty,
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
      form.fromLocationCode = String(q.locationCode)
      fetchFromInventory().then(() => {
        if (q.productId) form.selectedProductId = Number(q.productId)
        if (q.unitType) form.selectedUnitType = String(q.unitType)
      })
    }
  }

  function goBack() {
    router.push({ name: 'inventory-list' })
  }

  return {
    submitting,
    form,
    rules,
    fromInventoryOptions,
    fromInventory,
    toLocationId,
    toCurrentQty,
    toMaxQty,
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
