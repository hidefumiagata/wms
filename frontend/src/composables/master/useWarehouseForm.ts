import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { z } from 'zod'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'

// 全角カタカナ（長音・スペース含む）
const KATAKANA_REGEX = /^[ァ-ヶー　 ]*$/
const WAREHOUSE_CODE_REGEX = /^[A-Z]{4}$/

export function useWarehouseForm() {
  const { t } = useI18n()
  const router = useRouter()
  const route = useRoute()

  const warehouseId = computed(() => {
    const id = route.params.id
    if (!id) return null
    const num = Number(id)
    return Number.isInteger(num) && num > 0 ? num : null
  })
  const isEdit = computed(() => warehouseId.value !== null)

  // --- Zod スキーマ ---
  // t() は setup() コンテキスト内で実行されるため、初期化時に正しい翻訳が取得できる
  const warehouseSchema = z.object({
    warehouseCode: z
      .string()
      .min(1, t('master.warehouse.validation.codeRequired'))
      .regex(WAREHOUSE_CODE_REGEX, t('master.warehouse.validation.codeFormat')),
    warehouseName: z
      .string()
      .min(1, t('master.warehouse.validation.nameRequired'))
      .max(200, t('master.warehouse.validation.nameMaxLength')),
    warehouseNameKana: z
      .string()
      .min(1, t('master.warehouse.validation.kanaRequired'))
      .max(200, t('master.warehouse.validation.kanaMaxLength'))
      .regex(KATAKANA_REGEX, t('master.warehouse.validation.kanaFormat')),
    address: z
      .string()
      .max(500, t('master.warehouse.validation.addressMaxLength')),
  })

  // --- VeeValidate ---
  const {
    errors,
    handleSubmit: createSubmitHandler,
    setFieldError,
    setValues,
    defineField,
  } = useForm({
    validationSchema: toTypedSchema(warehouseSchema),
    initialValues: { warehouseCode: '', warehouseName: '', warehouseNameKana: '', address: '' },
  })

  // validateOnModelUpdate: false → 入力中（v-model 更新時）はバリデーションしない
  // validateOnBlur: true     → フォーカスを外したタイミングでバリデーションを実行
  const [warehouseCode, warehouseCodeAttrs] = defineField('warehouseCode', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })
  const [warehouseName, warehouseNameAttrs] = defineField('warehouseName', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })
  const [warehouseNameKana, warehouseNameKanaAttrs] = defineField('warehouseNameKana', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })
  const [address, addressAttrs] = defineField('address', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })

  // --- 状態 ---
  const loading = ref(false)
  const initialLoading = ref(false)
  const version = ref(0)

  // --- API呼び出し ---
  async function checkCodeExists() {
    if (isEdit.value) return // 編集モードではコードチェック不要（変更不可）
    const code = warehouseCode.value
    if (!code || !WAREHOUSE_CODE_REGEX.test(code)) return

    try {
      const res = await apiClient.get<{ exists: boolean }>('/master/warehouses/exists', {
        params: { warehouseCode: code },
      })
      if (res.data.exists) {
        setFieldError('warehouseCode', t('master.warehouse.validation.codeDuplicate'))
      }
    } catch {
      // 確認失敗時はサーバー側バリデーションに委ねる
    }
  }

  async function fetchWarehouse() {
    if (!warehouseId.value) {
      router.push({ name: 'warehouse-list' })
      return
    }
    initialLoading.value = true
    try {
      const res = await apiClient.get<{
        warehouseCode: string
        warehouseName: string
        warehouseNameKana: string
        address: string | null
        version: number
      }>(`/master/warehouses/${warehouseId.value}`)
      setValues({
        warehouseCode: res.data.warehouseCode,
        warehouseName: res.data.warehouseName,
        warehouseNameKana: res.data.warehouseNameKana ?? '',
        address: res.data.address ?? '',
      })
      version.value = res.data.version
    } catch (err: unknown) {
      const error = toApiError(err)
      if (error.response?.status === 404) {
        ElMessage.error(t('master.warehouse.notFound'))
        router.push({ name: 'warehouse-list' })
      } else if (!error.response) {
        ElMessage.error(t('error.network'))
      }
      // 403/500 はインターセプターが処理済み
    } finally {
      initialLoading.value = false
    }
  }

  const handleSubmit = createSubmitHandler(async (values) => {
    loading.value = true
    try {
      if (isEdit.value) {
        await apiClient.put(`/master/warehouses/${warehouseId.value}`, {
          warehouseName: values.warehouseName,
          warehouseNameKana: values.warehouseNameKana,
          address: values.address,
          version: version.value,
        })
        ElMessage.success(t('master.warehouse.updateSuccess'))
      } else {
        await apiClient.post('/master/warehouses', {
          warehouseCode: values.warehouseCode,
          warehouseName: values.warehouseName,
          warehouseNameKana: values.warehouseNameKana,
          address: values.address,
        })
        ElMessage.success(t('master.warehouse.createSuccess'))
      }
      router.push({ name: 'warehouse-list' })
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 409) {
        if (error.response.data?.errorCode === 'DUPLICATE_CODE') {
          // 倉庫コード重複（サーバー側で検出）
          setFieldError('warehouseCode', t('master.warehouse.validation.codeDuplicate'))
        } else {
          // 楽観的ロック競合
          ElMessage.error(t('error.optimisticLock'))
        }
      }
      // 403/500 はインターセプターが処理済み
    } finally {
      loading.value = false
    }
  })

  function handleCancel() {
    router.push({ name: 'warehouse-list' })
  }

  return {
    // フィールド（v-model バインディング用）
    warehouseCode,
    warehouseCodeAttrs,
    warehouseName,
    warehouseNameAttrs,
    warehouseNameKana,
    warehouseNameKanaAttrs,
    address,
    addressAttrs,
    // バリデーションエラー
    errors,
    // 状態
    loading,
    initialLoading,
    isEdit,
    // 操作
    fetchWarehouse,
    handleSubmit,
    handleCancel,
    checkCodeExists,
  }
}
