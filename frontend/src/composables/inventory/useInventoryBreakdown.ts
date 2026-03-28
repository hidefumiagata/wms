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

export function useInventoryBreakdown(formRef: ReturnType<typeof ref<FormInstance>>) {
  const { t } = useI18n()
  const router = useRouter()
  const warehouseStore = useWarehouseStore()

  const loading = ref(false)
  const submitting = ref(false)

  // フォームモデル
  const form = reactive({
    fromLocationCode: '',
    selectedProductId: null as number | null,
    fromUnitType: null as string | null,
    breakdownQty: 1,
    toUnitType: null as string | null,
    toLocationCode: '',
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
    fromUnitType: [
      {
        required: true,
        message: () => t('inventory.validation.unitTypeRequired'),
        trigger: 'change',
      },
    ],
    breakdownQty: [
      {
        required: true,
        message: () => t('inventory.validation.breakdownQtyRequired'),
        trigger: 'change',
      },
    ],
    toUnitType: [
      {
        required: true,
        message: () => t('inventory.validation.toUnitTypeRequired'),
        trigger: 'change',
      },
    ],
  }

  // ばらし元
  const fromLocationId = ref<number | null>(null)
  const fromInventoryOptions = ref<InventoryOption[]>([])

  // ばらし先
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
    if (!form.selectedProductId || !form.fromUnitType) return null
    return (
      fromInventoryOptions.value.find(
        (i) => i.productId === form.selectedProductId && i.unitType === form.fromUnitType,
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
    if (!form.selectedProductId) return []
    return fromInventoryOptions.value.filter(
      (i) => i.productId === form.selectedProductId && i.unitType !== 'PIECE',
    )
  })

  // ばらし先荷姿選択肢（ばらし元より小さい荷姿のみ）
  const toUnitTypeOptions = computed(() => {
    if (!form.fromUnitType) return []
    if (form.fromUnitType === 'CASE') return ['BALL', 'PIECE']
    if (form.fromUnitType === 'BALL') return ['PIECE']
    return []
  })

  // 変換レート
  const conversionRate = computed(() => {
    if (!productInfo.value || !form.fromUnitType || !form.toUnitType) return null
    const p = productInfo.value
    if (form.fromUnitType === 'CASE' && form.toUnitType === 'BALL') return p.caseQuantity
    if (form.fromUnitType === 'CASE' && form.toUnitType === 'PIECE')
      return p.caseQuantity * p.ballQuantity
    if (form.fromUnitType === 'BALL' && form.toUnitType === 'PIECE') return p.ballQuantity
    return null
  })

  // 変換後数量
  const convertedQty = computed(() => {
    if (!conversionRate.value) return null
    return form.breakdownQty * conversionRate.value
  })

  // --- ロケーション検索 ---
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
        return
      }
      fromLocationId.value = locs[0].id

      // 在庫取得
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
      }))
      form.selectedProductId = null
      form.fromUnitType = null
      form.toUnitType = null
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
    form.fromUnitType = null
    form.toUnitType = null
    productInfo.value = null
    if (!form.selectedProductId) return
    try {
      const res = await apiClient.get(`/master/products/${form.selectedProductId}`)
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
    form.toUnitType = null
  }

  // --- ばらし先情報取得 ---
  async function fetchToLocationInfo() {
    if (!form.toLocationCode.trim()) {
      toLocationId.value = fromLocationId.value
      toCurrentQty.value = null
      return
    }
    try {
      const locRes = await apiClient.get('/master/locations', {
        params: {
          warehouseId: warehouseStore.selectedWarehouseId,
          locationCode: form.toLocationCode.trim(),
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

      if (form.selectedProductId && form.toUnitType) {
        const invRes = await apiClient.get('/inventory', {
          params: {
            warehouseId: warehouseStore.selectedWarehouseId,
            locationCodePrefix: form.toLocationCode.trim(),
            productId: form.selectedProductId,
            unitType: form.toUnitType,
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
    // el-form バリデーション
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return

    if (!fromInventory.value || !fromLocationId.value || !form.toUnitType) return

    if (form.breakdownQty < 1 || form.breakdownQty > fromInventory.value.availableQty) {
      ElMessage.error(t('inventory.breakdownQtyExceedsAvailable'))
      return
    }
    if (!conversionRate.value || conversionRate.value <= 0) {
      ElMessage.error(t('inventory.conversionRateNotSet'))
      return
    }

    const effectiveToLocationId = toLocationId.value ?? fromLocationId.value
    const fromUtLabel = unitTypeLabel(form.fromUnitType!, t)
    const toUtLabel = unitTypeLabel(form.toUnitType, t)

    try {
      await ElMessageBox.confirm(
        t('inventory.breakdownConfirmMessage', {
          fromUnitType: fromUtLabel,
          qty: form.breakdownQty,
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
        fromUnitType: form.fromUnitType,
        breakdownQty: form.breakdownQty,
        toUnitType: form.toUnitType,
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
    form,
    rules,
    fromLocationId,
    fromInventoryOptions,
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
