import { type Ref, ref, reactive, computed, watch, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import { useWarehouseStore } from '@/stores/warehouse'
import { ReturnType } from '@/api/generated/models/return-type'
import { ReturnReason } from '@/api/generated/models/return-reason'
import { toProductOption, type ProductOption } from '@/types/master'

interface LocationInventory {
  locationId: number
  locationCode: string
  quantity: number
  allocatedQty: number
  availableQty: number
}

interface PartnerOption {
  id: number
  partnerCode: string
  partnerName: string
}


export function useReturnForm(formRef: Ref<FormInstance | undefined>) {
  const { t } = useI18n()
  const route = useRoute()
  const warehouseStore = useWarehouseStore()

  // --- 状態 ---
  const submitting = ref(false)
  const productSearchLoading = ref(false)
  const locationLoading = ref(false)
  const supplierSearchLoading = ref(false)

  // 商品選択結果
  const selectedProduct = ref<ProductOption | null>(null)
  // 商品検索ダイアログ
  const productDialogVisible = ref(false)
  const productSearchResults = ref<ProductOption[]>([])
  const productSearchTotal = ref(0)
  // 仕入先選択結果
  const selectedSupplier = ref<PartnerOption | null>(null)
  // 仕入先検索ダイアログ
  const supplierDialogVisible = ref(false)
  const supplierSearchResults = ref<PartnerOption[]>([])
  const supplierSearchTotal = ref(0)
  // ロケーション候補（在庫返品時）
  const locationOptions = ref<LocationInventory[]>([])
  // 選択中ロケーションの在庫情報
  const selectedLocationInventory = computed<LocationInventory | null>(() => {
    if (!form.locationId) return null
    return locationOptions.value.find((l) => l.locationId === form.locationId) ?? null
  })

  // --- フォーム ---
  const form = reactive({
    returnType: '' as string,
    productCode: '',
    quantity: null as number | null,
    unitType: '' as string,
    locationId: null as number | null,
    partnerId: null as number | null,
    partnerCode: '',
    returnReason: '' as string,
    returnReasonNote: '',
    relatedSlipNumber: '',
    lotNumber: '',
    expiryDate: '',
  })

  // クエリパラメータからプリセット
  const qReturnType = route.query.returnType as string | undefined
  if (qReturnType && Object.values(ReturnType).includes(qReturnType as ReturnType)) {
    form.returnType = qReturnType
  }
  const qRelatedSlip = route.query.relatedSlipNumber as string | undefined
  if (qRelatedSlip) {
    form.relatedSlipNumber = qRelatedSlip
  }

  // --- 条件付き表示 ---
  const isInventoryReturn = computed(() => form.returnType === ReturnType.Inventory)
  const showRelatedSlip = computed(
    () => form.returnType === ReturnType.Inbound || form.returnType === ReturnType.Outbound,
  )
  const showLotNumber = computed(() => selectedProduct.value?.lotManageFlag === true)
  const showExpiryDate = computed(() => selectedProduct.value?.expiryManageFlag === true)
  const isReasonOther = computed(() => form.returnReason === ReturnReason.Other)

  // --- バリデーション ---
  const rules = computed<FormRules>(() => ({
    returnType: [
      {
        required: true,
        message: () => t('returns.validation.returnTypeRequired'),
        trigger: 'change',
      },
    ],
    productCode: [
      {
        required: true,
        message: () => t('returns.validation.productRequired'),
        trigger: 'blur',
      },
    ],
    quantity: [
      {
        required: true,
        type: 'number',
        min: 1,
        message: () => t('returns.validation.quantityRequired'),
        trigger: 'change',
      },
    ],
    unitType: [
      {
        required: true,
        message: () => t('returns.validation.unitTypeRequired'),
        trigger: 'change',
      },
    ],
    locationId: isInventoryReturn.value
      ? [
          {
            required: true,
            message: () => t('returns.validation.locationRequired'),
            trigger: 'change',
          },
        ]
      : [],
    returnReason: [
      {
        required: true,
        message: () => t('returns.validation.returnReasonRequired'),
        trigger: 'change',
      },
    ],
    returnReasonNote: isReasonOther.value
      ? [
          {
            required: true,
            message: () => t('returns.validation.returnReasonNoteRequired'),
            trigger: 'blur',
          },
          {
            max: 500,
            message: () => t('returns.validation.returnReasonNoteMaxLength'),
            trigger: 'blur',
          },
        ]
      : [
          {
            max: 500,
            message: () => t('returns.validation.returnReasonNoteMaxLength'),
            trigger: 'blur',
          },
        ],
  }))

  // --- AbortController（操作ごとに分離して相互キャンセルを防止） ---
  let productAbortController: AbortController | null = null
  let supplierAbortController: AbortController | null = null
  let locationAbortController: AbortController | null = null
  onUnmounted(() => {
    productAbortController?.abort()
    supplierAbortController?.abort()
    locationAbortController?.abort()
  })

  // --- 返品種別変更時 ---
  function onReturnTypeChange() {
    // ロケーション関連をリセット
    form.locationId = null
    locationOptions.value = []
    // 在庫返品→他に変更した場合はロケーション候補クリア
    // 在庫返品に変更かつ商品・荷姿選択済みならロケーション候補を再取得
    if (isInventoryReturn.value && selectedProduct.value && form.unitType) {
      fetchLocationCandidates()
    }
  }

  // --- 商品検索 ---
  async function searchProduct() {
    if (!form.productCode.trim()) return
    productAbortController?.abort()
    productAbortController = new AbortController()
    productSearchLoading.value = true
    try {
      const res = await apiClient.get('/master/products', {
        params: {
          keyword: form.productCode.trim(),
          isActive: true,
          page: 0,
          size: 10,
        },
        signal: productAbortController.signal,
      })
      const content = res.data.content
      const rawProducts: Record<string, unknown>[] = Array.isArray(content) ? content : []
      const total =
        typeof res.data.totalElements === 'number'
          ? res.data.totalElements
          : (console.warn(
              '[searchProduct] API contract violation: totalElements missing',
            ),
            rawProducts.length)
      if (rawProducts.length === 0) {
        ElMessage.info(t('returns.productSearchEmpty'))
        selectedProduct.value = null
        return
      }
      // 全経路で同一の ProductOption 型を流すため、ここで正規化
      const normalized = rawProducts.map(toProductOption)
      // 完全一致 or 1件の場合は直接選択
      const exact = normalized.find((p) => p.productCode === form.productCode.trim())
      if (exact) {
        selectProduct(exact)
        return
      }
      if (normalized.length === 1) {
        selectProduct(normalized[0])
        return
      }
      // 複数件で完全一致なし → ダイアログ表示
      productSearchResults.value = normalized
      productSearchTotal.value = total
      selectedProduct.value = null
      productDialogVisible.value = true
    } catch (err) {
      if (axios.isCancel(err)) return
      ElMessage.error(t('returns.productNotFound'))
      selectedProduct.value = null
    } finally {
      productSearchLoading.value = false
    }
  }

  function selectProduct(product: ProductOption) {
    // 同一商品の再選択時はロット・有効期限を保持する（C-3-m5）
    const isSameProduct = selectedProduct.value?.id === product.id
    selectedProduct.value = product
    form.productCode = product.productCode
    // ロケーション候補リセット
    form.locationId = null
    locationOptions.value = []
    // 前商品の条件付き入力値（ロット・有効期限）を必ずクリアする。
    // showLotNumber=false で非表示になっても、クリアしないと submit ペイロードに
    // 前商品の値が残るため明示的にリセットする。
    // ただし同一商品を再選択した場合は入力済みの値を保持する。
    if (!isSameProduct) {
      form.lotNumber = ''
      form.expiryDate = ''
    }
    // 在庫返品時かつ荷姿選択済みならロケーション候補取得
    if (isInventoryReturn.value && form.unitType) {
      fetchLocationCandidates()
    }
    // ダイアログを閉じ、検索結果の残骸もクリアする
    productDialogVisible.value = false
    productSearchResults.value = []
    productSearchTotal.value = 0
  }

  // --- 荷姿変更時 ---
  function onUnitTypeChange() {
    form.locationId = null
    locationOptions.value = []
    if (isInventoryReturn.value && selectedProduct.value) {
      fetchLocationCandidates()
    }
  }

  // --- 仕入先検索 ---
  async function searchSupplier() {
    supplierAbortController?.abort()
    supplierAbortController = new AbortController()
    supplierSearchLoading.value = true
    try {
      const params: Record<string, unknown> = {
        partnerType: 'SUPPLIER',
        isActive: true,
        page: 0,
        size: 20,
      }
      const keyword = form.partnerCode.trim()
      if (keyword) {
        // partnerCode is prefix match, partnerName is partial match on backend
        // Use partnerName for general search (covers both name and kana)
        params.partnerName = keyword
      }
      const res = await apiClient.get('/master/partners', {
        params,
        signal: supplierAbortController.signal,
      })
      const partners = res.data.content ?? []
      const total = res.data.totalElements ?? 0
      if (partners.length === 0) {
        ElMessage.info(t('returns.supplierSearchEmpty'))
        return
      }
      // 完全一致 or 1件 → 直接選択
      if (keyword) {
        const exact = partners.find(
          (p: PartnerOption) => p.partnerCode === keyword,
        )
        if (exact) {
          selectSupplier(exact)
          return
        }
      }
      if (partners.length === 1) {
        selectSupplier(partners[0])
        return
      }
      // 複数件 → ダイアログ表示
      supplierSearchResults.value = partners.map((p: Record<string, unknown>) => ({
        id: p.id as number,
        partnerCode: p.partnerCode as string,
        partnerName: p.partnerName as string,
      }))
      supplierSearchTotal.value = total
      supplierDialogVisible.value = true
    } catch (err) {
      if (axios.isCancel(err)) return
      ElMessage.error(t('returns.supplierSearchError'))
    } finally {
      supplierSearchLoading.value = false
    }
  }

  function selectSupplier(partner: PartnerOption) {
    selectedSupplier.value = partner
    form.partnerId = partner.id
    form.partnerCode = partner.partnerCode
    supplierDialogVisible.value = false
  }

  function clearSupplier() {
    selectedSupplier.value = null
    form.partnerId = null
    form.partnerCode = ''
  }

  // --- ロケーション候補取得（在庫返品時） ---
  async function fetchLocationCandidates() {
    if (!selectedProduct.value || !form.unitType || !warehouseStore.selectedWarehouseId) return
    locationAbortController?.abort()
    locationAbortController = new AbortController()
    locationLoading.value = true
    try {
      const res = await apiClient.get('/inventory', {
        params: {
          warehouseId: warehouseStore.selectedWarehouseId,
          productId: selectedProduct.value.id,
          unitType: form.unitType,
          viewType: 'LOCATION',
          page: 0,
          size: 100,
        },
        signal: locationAbortController.signal,
      })
      const items = res.data.content ?? []
      locationOptions.value = items.map((i: Record<string, unknown>) => ({
        locationId: i.locationId,
        locationCode: i.locationCode,
        quantity: i.quantity as number,
        allocatedQty: i.allocatedQty as number,
        availableQty: i.availableQty as number,
      }))
      if (locationOptions.value.length === 0) {
        ElMessage.info(t('returns.locationCandidateEmpty'))
      }
    } catch (err) {
      if (axios.isCancel(err)) return
      locationOptions.value = []
    } finally {
      locationLoading.value = false
    }
  }

  // --- 在庫数の警告表示 ---
  const hasAllocated = computed(() => (selectedLocationInventory.value?.allocatedQty ?? 0) > 0)

  // --- 登録 ---
  async function submitReturn() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return

    if (!selectedProduct.value) return

    // 返品種別ラベル
    const returnTypeLabel =
      form.returnType === ReturnType.Inbound
        ? t('returns.returnTypeInbound')
        : form.returnType === ReturnType.Inventory
          ? t('returns.returnTypeInventory')
          : t('returns.returnTypeOutbound')

    // 荷姿ラベル
    const unitTypeLabel =
      form.unitType === 'CASE'
        ? t('returns.unitTypeCase')
        : form.unitType === 'BALL'
          ? t('returns.unitTypeBall')
          : t('returns.unitTypePiece')

    // 確認ダイアログ
    const confirmMsg = t('returns.confirmMessage', {
      productName: selectedProduct.value.productName,
      quantity: form.quantity,
      unitType: unitTypeLabel,
      returnType: returnTypeLabel,
    })

    try {
      await ElMessageBox.confirm(confirmMsg, t('common.confirm'), { type: 'warning' })
    } catch {
      return
    }

    submitting.value = true
    try {
      const payload: Record<string, unknown> = {
        returnType: form.returnType,
        productId: selectedProduct.value.id,
        quantity: form.quantity,
        unitType: form.unitType,
        returnReason: form.returnReason,
      }
      if (form.locationId) payload.locationId = form.locationId
      if (form.partnerId) payload.partnerId = form.partnerId
      if (form.returnReasonNote.trim()) payload.returnReasonNote = form.returnReasonNote.trim()
      if (form.relatedSlipNumber.trim()) payload.relatedSlipNumber = form.relatedSlipNumber.trim()
      if (form.lotNumber.trim()) payload.lotNumber = form.lotNumber.trim()
      if (form.expiryDate) payload.expiryDate = form.expiryDate

      const res = await apiClient.post('/returns', payload)
      ElMessage.success(t('returns.registerSuccess', { slipNumber: res.data.slipNumber }))
      resetForm()
    } catch (err) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 422) {
        const code = error.response.data?.errorCode
        if (code === 'RETURN_INSUFFICIENT_QUANTITY') {
          const inv = selectedLocationInventory.value
          const unitLabel =
            form.unitType === 'CASE'
              ? t('returns.unitTypeCase')
              : form.unitType === 'BALL'
                ? t('returns.unitTypeBall')
                : t('returns.unitTypePiece')
          ElMessage.error(
            t('returns.insufficientQuantity', {
              available: inv?.availableQty ?? 0,
              unitType: unitLabel,
            }),
          )
        } else if (code === 'RETURN_ALLOCATED_INVENTORY') {
          const inv = selectedLocationInventory.value
          const unitLabel =
            form.unitType === 'CASE'
              ? t('returns.unitTypeCase')
              : form.unitType === 'BALL'
                ? t('returns.unitTypeBall')
                : t('returns.unitTypePiece')
          ElMessage.error(
            t('returns.allocatedInventory', {
              allocated: inv?.allocatedQty ?? 0,
              unitType: unitLabel,
            }),
          )
        } else if (code === 'RETURN_STOCKTAKE_LOCKED') {
          ElMessage.error(t('returns.stocktakeLocked'))
        } else {
          ElMessage.error(error.response.data?.message ?? t('returns.registerError'))
        }
      } else if (error.response.status === 404) {
        ElMessage.error(error.response.data?.message ?? t('error.notFound'))
      } else if (error.response.status === 409) {
        ElMessage.error(t('error.optimisticLock'))
      } else {
        ElMessage.error(t('returns.registerError'))
      }
    } finally {
      submitting.value = false
    }
  }

  function resetForm() {
    selectedProduct.value = null
    selectedSupplier.value = null
    locationOptions.value = []
    productSearchResults.value = []
    productSearchTotal.value = 0
    productDialogVisible.value = false
    supplierSearchResults.value = []
    supplierSearchTotal.value = 0
    supplierDialogVisible.value = false
    form.returnType = ''
    form.productCode = ''
    form.quantity = null
    form.unitType = ''
    form.locationId = null
    form.partnerId = null
    form.partnerCode = ''
    form.returnReason = ''
    form.returnReasonNote = ''
    form.relatedSlipNumber = ''
    form.lotNumber = ''
    form.expiryDate = ''
    formRef.value?.clearValidate()
  }

  // returnReason が OTHER から他に変更された場合の備考クリア用watch
  watch(
    () => form.returnReason,
    (newVal, oldVal) => {
      if (oldVal === ReturnReason.Other && newVal !== ReturnReason.Other) {
        formRef.value?.clearValidate('returnReasonNote')
      }
    },
  )

  return {
    form,
    rules,
    submitting,
    productSearchLoading,
    locationLoading,
    supplierSearchLoading,
    selectedProduct,
    productDialogVisible,
    productSearchResults,
    productSearchTotal,
    selectedSupplier,
    locationOptions,
    selectedLocationInventory,
    supplierDialogVisible,
    supplierSearchResults,
    supplierSearchTotal,
    isInventoryReturn,
    showRelatedSlip,
    showLotNumber,
    showExpiryDate,
    hasAllocated,
    onReturnTypeChange,
    searchProduct,
    selectProduct,
    searchSupplier,
    selectSupplier,
    clearSupplier,
    onUnitTypeChange,
    submitReturn,
    resetForm,
  }
}
