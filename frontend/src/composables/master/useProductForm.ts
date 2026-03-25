import { ref, computed, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { z } from 'zod'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import type { ProductDetail } from '@/api/generated/models/product-detail'
import { StorageCondition } from '@/api/generated/models/storage-condition'
import type { CreateProductRequest } from '@/api/generated/models/create-product-request'
import type { UpdateProductRequest } from '@/api/generated/models/update-product-request'

// 全角カタカナ（長音・スペース含む）
const KATAKANA_REGEX = /^[ァ-ヶー　 ]*$/
const PRODUCT_CODE_REGEX = /^[A-Za-z0-9\-]+$/
const BARCODE_REGEX = /^[0-9]*$/

export function useProductForm() {
  const { t } = useI18n()
  const router = useRouter()
  const route = useRoute()

  const productId = computed(() => {
    const id = route.params.id
    if (!id) return null
    const num = Number(id)
    return Number.isInteger(num) && num > 0 ? num : null
  })
  const isEdit = computed(() => productId.value !== null)

  // --- Zod スキーマ（ロケール変更時に自動再生成） ---
  const validationSchema = computed(() =>
    toTypedSchema(
      z.object({
        productCode: z
          .string()
          .min(1, t('master.product.validation.codeRequired'))
          .max(20, t('master.product.validation.codeMaxLength'))
          .regex(PRODUCT_CODE_REGEX, t('master.product.validation.codeFormat')),
        productName: z
          .string()
          .min(1, t('master.product.validation.nameRequired'))
          .max(200, t('master.product.validation.nameMaxLength')),
        productNameKana: z
          .string()
          .min(1, t('master.product.validation.kanaRequired'))
          .max(200, t('master.product.validation.kanaMaxLength'))
          .regex(KATAKANA_REGEX, t('master.product.validation.kanaFormat')),
        barcode: z
          .string()
          .max(20, t('master.product.validation.barcodeMaxLength'))
          .regex(BARCODE_REGEX, t('master.product.validation.barcodeFormat'))
          .optional()
          .or(z.literal('')),
        storageCondition: z.enum([StorageCondition.Ambient, StorageCondition.Refrigerated, StorageCondition.Frozen]),
        caseQuantity: z
          .number({ invalid_type_error: t('master.product.validation.caseQuantityRange') })
          .int(t('master.product.validation.caseQuantityRange'))
          .min(1, t('master.product.validation.caseQuantityRange'))
          .max(9999, t('master.product.validation.caseQuantityRange'))
          .optional()
          .or(z.nan()),
        ballQuantity: z
          .number({ invalid_type_error: t('master.product.validation.ballQuantityRange') })
          .int(t('master.product.validation.ballQuantityRange'))
          .min(1, t('master.product.validation.ballQuantityRange'))
          .max(9999, t('master.product.validation.ballQuantityRange'))
          .optional()
          .or(z.nan()),
        isHazardous: z.boolean(),
        lotManageFlag: z.boolean(),
        expiryManageFlag: z.boolean(),
        shipmentStopFlag: z.boolean(),
        isActive: z.boolean(),
      }),
    ),
  )

  // --- VeeValidate ---
  const {
    errors,
    handleSubmit: createSubmitHandler,
    setFieldError,
    setValues,
    defineField,
  } = useForm({
    validationSchema,
    initialValues: {
      productCode: '',
      productName: '',
      productNameKana: '',
      barcode: '',
      storageCondition: StorageCondition.Ambient as StorageCondition,
      caseQuantity: undefined as number | undefined,
      ballQuantity: undefined as number | undefined,
      isHazardous: false,
      lotManageFlag: false,
      expiryManageFlag: false,
      shipmentStopFlag: false,
      isActive: true,
    },
  })

  const fieldOpts = { validateOnModelUpdate: false, validateOnBlur: true }

  const [productCode, productCodeAttrs] = defineField('productCode', fieldOpts)
  const [productName, productNameAttrs] = defineField('productName', fieldOpts)
  const [productNameKana, productNameKanaAttrs] = defineField('productNameKana', fieldOpts)
  const [barcode, barcodeAttrs] = defineField('barcode', fieldOpts)
  const [storageCondition, storageConditionAttrs] = defineField('storageCondition', fieldOpts)
  const [caseQuantity, caseQuantityAttrs] = defineField('caseQuantity', fieldOpts)
  const [ballQuantity, ballQuantityAttrs] = defineField('ballQuantity', fieldOpts)
  const [isHazardous, isHazardousAttrs] = defineField('isHazardous', fieldOpts)
  const [lotManageFlag, lotManageFlagAttrs] = defineField('lotManageFlag', fieldOpts)
  const [expiryManageFlag, expiryManageFlagAttrs] = defineField('expiryManageFlag', fieldOpts)
  const [shipmentStopFlag, shipmentStopFlagAttrs] = defineField('shipmentStopFlag', fieldOpts)
  const [isActive, isActiveAttrs] = defineField('isActive', fieldOpts)

  // --- 状態 ---
  const loading = ref(false)
  const initialLoading = ref(false)
  const version = ref(0)
  const hasInventory = ref(false)

  // --- 並行リクエスト制御 ---
  let abortController: AbortController | null = null
  onUnmounted(() => { abortController?.abort() })

  // --- API呼び出し ---
  async function checkCodeExists() {
    if (isEdit.value) return
    const code = productCode.value
    if (!code || !PRODUCT_CODE_REGEX.test(code)) return

    try {
      const res = await apiClient.get<{ exists: boolean }>('/master/products/exists', {
        params: { productCode: code },
      })
      if (res.data.exists) {
        setFieldError('productCode', t('master.product.validation.codeDuplicate'))
      }
    } catch {
      // 確認失敗時はサーバー側バリデーションに委ねる
    }
  }

  async function fetchProduct() {
    if (!productId.value) {
      router.push({ name: 'product-list' })
      return
    }
    abortController?.abort()
    abortController = new AbortController()
    const signal = abortController.signal

    initialLoading.value = true
    try {
      const res = await apiClient.get<ProductDetail>(`/master/products/${productId.value}`, { signal })
      setValues({
        productCode: res.data.productCode,
        productName: res.data.productName,
        productNameKana: res.data.productNameKana ?? '',
        barcode: res.data.barcode ?? '',
        storageCondition: res.data.storageCondition,
        caseQuantity: res.data.caseQuantity > 0 ? res.data.caseQuantity : undefined,
        ballQuantity: res.data.ballQuantity > 0 ? res.data.ballQuantity : undefined,
        isHazardous: res.data.isHazardous,
        lotManageFlag: res.data.lotManageFlag,
        expiryManageFlag: res.data.expiryManageFlag,
        shipmentStopFlag: res.data.shipmentStopFlag,
        isActive: res.data.isActive,
      })
      version.value = res.data.version
      hasInventory.value = res.data.hasInventory ?? false
    } catch (err: unknown) {
      if (axios.isCancel(err)) return
      const error = toApiError(err)
      if (error.response?.status === 404) {
        ElMessage.error(t('master.product.notFound'))
        router.push({ name: 'product-list' })
      } else if (!error.response) {
        ElMessage.error(t('error.network'))
      }
      // 403/500 はインターセプターが処理済み
    } finally {
      if (!signal.aborted) {
        initialLoading.value = false
      }
    }
  }

  const handleSubmit = createSubmitHandler(async (values) => {
    loading.value = true
    try {
      if (isEdit.value) {
        const body: UpdateProductRequest = {
          productName: values.productName,
          productNameKana: values.productNameKana || undefined,
          caseQuantity: values.caseQuantity ?? 0,
          ballQuantity: values.ballQuantity ?? 0,
          barcode: values.barcode || undefined,
          storageCondition: values.storageCondition as StorageCondition,
          isHazardous: values.isHazardous,
          lotManageFlag: values.lotManageFlag,
          expiryManageFlag: values.expiryManageFlag,
          shipmentStopFlag: values.shipmentStopFlag,
          isActive: values.isActive,
          version: version.value,
        }
        await apiClient.put(`/master/products/${productId.value}`, body)
        ElMessage.success(t('master.product.updateSuccess'))
      } else {
        const body: CreateProductRequest = {
          productCode: values.productCode,
          productName: values.productName,
          productNameKana: values.productNameKana || undefined,
          caseQuantity: values.caseQuantity ?? 0,
          ballQuantity: values.ballQuantity ?? 0,
          barcode: values.barcode || undefined,
          storageCondition: values.storageCondition as StorageCondition,
          isHazardous: values.isHazardous,
          lotManageFlag: values.lotManageFlag,
          expiryManageFlag: values.expiryManageFlag,
          shipmentStopFlag: values.shipmentStopFlag,
          isActive: values.isActive,
        }
        await apiClient.post('/master/products', body)
        ElMessage.success(t('master.product.createSuccess'))
      }
      router.push({ name: 'product-list' })
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 409) {
        if (error.response.data?.errorCode === 'DUPLICATE_CODE') {
          setFieldError('productCode', t('master.product.validation.codeDuplicate'))
        } else {
          ElMessage.error(t('error.optimisticLock'))
        }
      } else if (error.response.status === 422) {
        const errorCode = error.response.data?.errorCode
        if (errorCode === 'CANNOT_CHANGE_LOT_MANAGE_FLAG') {
          ElMessage.error(t('master.product.cannotChangeLotFlag'))
        } else if (errorCode === 'CANNOT_CHANGE_EXPIRY_MANAGE_FLAG') {
          ElMessage.error(t('master.product.cannotChangeExpiryFlag'))
        }
      }
      // 403/500 はインターセプターが処理済み
    } finally {
      loading.value = false
    }
  })

  async function handleCancel() {
    try {
      await ElMessageBox.confirm(
        t('master.product.confirmCancel'),
        t('common.confirm'),
        {
          type: 'warning',
          confirmButtonText: t('common.confirm'),
          cancelButtonText: t('common.cancel'),
        }
      )
    } catch {
      return // ダイアログキャンセル
    }
    router.push({ name: 'product-list' })
  }

  return {
    productCode, productCodeAttrs,
    productName, productNameAttrs,
    productNameKana, productNameKanaAttrs,
    barcode, barcodeAttrs,
    storageCondition, storageConditionAttrs,
    caseQuantity, caseQuantityAttrs,
    ballQuantity, ballQuantityAttrs,
    isHazardous, isHazardousAttrs,
    lotManageFlag, lotManageFlagAttrs,
    expiryManageFlag, expiryManageFlagAttrs,
    shipmentStopFlag, shipmentStopFlagAttrs,
    isActive, isActiveAttrs,
    errors,
    loading,
    initialLoading,
    isEdit,
    hasInventory,
    fetchProduct,
    handleSubmit,
    handleCancel,
    checkCodeExists,
  }
}
