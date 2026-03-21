import { ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'

// 全角カタカナ（長音・スペース含む）
const KATAKANA_REGEX = /^[ァ-ヶー　 ]*$/

export function useWarehouseForm(formRef: ReturnType<typeof ref<FormInstance>>) {
  const { t } = useI18n()
  const router = useRouter()
  const route = useRoute()

  const warehouseId = computed(() => {
    const id = route.params.id
    return id ? Number(id) : null
  })
  const isEdit = computed(() => warehouseId.value !== null)

  // --- 状態 ---
  const loading = ref(false)
  const initialLoading = ref(false)
  const version = ref(0)
  const codeAlreadyExists = ref(false)

  const form = reactive({
    warehouseCode: '',
    warehouseName: '',
    warehouseNameKana: '',
    address: '',
  })

  // --- バリデーションルール ---
  const rules: FormRules = {
    warehouseCode: [
      { required: true, message: t('master.warehouse.validation.codeRequired'), trigger: 'blur' },
      {
        pattern: /^[A-Z]{4}$/,
        message: t('master.warehouse.validation.codeFormat'),
        trigger: 'blur',
      },
      {
        validator: (_rule, _value, callback) => {
          if (codeAlreadyExists.value) {
            callback(new Error(t('master.warehouse.validation.codeDuplicate')))
          } else {
            callback()
          }
        },
        trigger: 'blur',
      },
    ],
    warehouseName: [
      { required: true, message: t('master.warehouse.validation.nameRequired'), trigger: 'blur' },
      { max: 200, message: t('master.warehouse.validation.nameMaxLength'), trigger: 'blur' },
    ],
    warehouseNameKana: [
      { required: true, message: t('master.warehouse.validation.kanaRequired'), trigger: 'blur' },
      { max: 200, message: t('master.warehouse.validation.kanaMaxLength'), trigger: 'blur' },
      {
        pattern: KATAKANA_REGEX,
        message: t('master.warehouse.validation.kanaFormat'),
        trigger: 'blur',
      },
    ],
    address: [
      { max: 500, message: t('master.warehouse.validation.addressMaxLength'), trigger: 'blur' },
    ],
  }

  // --- API呼び出し ---
  async function checkCodeExists() {
    codeAlreadyExists.value = false
    if (!form.warehouseCode || !/^[A-Z]{4}$/.test(form.warehouseCode)) return

    try {
      const res = await apiClient.get<{ exists: boolean }>('/master/warehouses/exists', {
        params: { warehouseCode: form.warehouseCode },
      })
      codeAlreadyExists.value = res.data.exists
      if (codeAlreadyExists.value) {
        formRef.value?.validateField('warehouseCode')
      }
    } catch {
      // 確認失敗時はそのまま（サーバー側バリデーションに委ねる）
    }
  }

  async function fetchWarehouse() {
    if (!warehouseId.value) return
    initialLoading.value = true
    try {
      const res = await apiClient.get<{
        warehouseCode: string
        warehouseName: string
        warehouseNameKana: string
        address: string | null
        version: number
      }>(`/master/warehouses/${warehouseId.value}`)
      form.warehouseCode = res.data.warehouseCode
      form.warehouseName = res.data.warehouseName
      form.warehouseNameKana = res.data.warehouseNameKana ?? ''
      form.address = res.data.address ?? ''
      version.value = res.data.version
    } catch (err: unknown) {
      const error = toApiError(err)
      if (error.response?.status === 404) {
        ElMessage.error(t('master.warehouse.notFound'))
        router.push({ name: 'warehouse-list' })
      } else {
        ElMessage.error(t('error.server'))
      }
    } finally {
      initialLoading.value = false
    }
  }

  async function handleSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return

    loading.value = true
    try {
      if (isEdit.value) {
        await apiClient.put(`/master/warehouses/${warehouseId.value}`, {
          warehouseName: form.warehouseName,
          warehouseNameKana: form.warehouseNameKana,
          address: form.address,
          version: version.value,
        })
        ElMessage.success(t('master.warehouse.updateSuccess'))
      } else {
        await apiClient.post('/master/warehouses', {
          warehouseCode: form.warehouseCode,
          warehouseName: form.warehouseName,
          warehouseNameKana: form.warehouseNameKana,
          address: form.address,
        })
        ElMessage.success(t('master.warehouse.createSuccess'))
      }
      router.push({ name: 'warehouse-list' })
    } catch (err: unknown) {
      const error = toApiError(err)
      if (error.response?.status === 409) {
        if (error.response?.data?.errorCode === 'DUPLICATE_CODE') {
          // 倉庫コード重複（サーバー側で検出）
          codeAlreadyExists.value = true
          formRef.value?.validateField('warehouseCode')
        } else {
          // 楽観的ロック競合
          ElMessage.error(t('error.optimisticLock'))
        }
      } else if (error.response?.status === 400) {
        ElMessage.error(t('error.server'))
      } else {
        ElMessage.error(t('error.server'))
      }
    } finally {
      loading.value = false
    }
  }

  function handleCancel() {
    router.push({ name: 'warehouse-list' })
  }

  return {
    form,
    rules,
    loading,
    initialLoading,
    isEdit,
    fetchWarehouse,
    handleSubmit,
    handleCancel,
    checkCodeExists,
  }
}
