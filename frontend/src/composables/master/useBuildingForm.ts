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
import { useWarehouseStore } from '@/stores/warehouse'
import type { BuildingDetail } from '@/api/generated/models/building-detail'

const BUILDING_CODE_REGEX = /^[A-Za-z0-9]+$/

export function useBuildingForm() {
  const { t } = useI18n()
  const router = useRouter()
  const route = useRoute()
  const warehouseStore = useWarehouseStore()

  const buildingId = computed(() => {
    const id = route.params.id
    if (!id) return null
    const num = Number(id)
    return Number.isInteger(num) && num > 0 ? num : null
  })
  const isEdit = computed(() => buildingId.value !== null)

  // --- Zod スキーマ（ロケール変更時に自動再生成） ---
  const validationSchema = computed(() =>
    toTypedSchema(
      z.object({
        buildingCode: z
          .string()
          .min(1, t('master.building.validation.codeRequired'))
          .max(10, t('master.building.validation.codeMaxLength'))
          .regex(BUILDING_CODE_REGEX, t('master.building.validation.codeFormat')),
        buildingName: z
          .string()
          .min(1, t('master.building.validation.nameRequired'))
          .max(200, t('master.building.validation.nameMaxLength')),
      }),
    ),
  )

  const {
    errors,
    handleSubmit: createSubmitHandler,
    setFieldError,
    setValues,
    defineField,
    meta,
  } = useForm({
    validationSchema,
    initialValues: { buildingCode: '', buildingName: '' },
  })

  const [buildingCode, buildingCodeAttrs] = defineField('buildingCode', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })
  const [buildingName, buildingNameAttrs] = defineField('buildingName', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })

  const loading = ref(false)
  const initialLoading = ref(false)
  const version = ref(0)
  const warehouseCode = ref('')

  // --- 並行リクエスト制御 ---
  let abortController: AbortController | null = null
  onUnmounted(() => { abortController?.abort() })

  async function fetchBuilding() {
    if (!buildingId.value) {
      router.push({ name: 'building-list' })
      return
    }
    abortController?.abort()
    abortController = new AbortController()
    const signal = abortController.signal

    initialLoading.value = true
    try {
      const res = await apiClient.get<BuildingDetail>(`/master/buildings/${buildingId.value}`, { signal })
      setValues({
        buildingCode: res.data.buildingCode,
        buildingName: res.data.buildingName,
      })
      version.value = res.data.version
      warehouseCode.value = res.data.warehouseCode
    } catch (err: unknown) {
      if (axios.isCancel(err)) return
      const error = toApiError(err)
      if (error.response?.status === 404) {
        ElMessage.error(t('master.building.notFound'))
        router.push({ name: 'building-list' })
      } else if (!error.response) {
        ElMessage.error(t('error.network'))
      }
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
        await apiClient.put(`/master/buildings/${buildingId.value}`, {
          buildingName: values.buildingName,
          version: version.value,
        })
        ElMessage.success(t('master.building.updateSuccess'))
      } else {
        await apiClient.post('/master/buildings', {
          warehouseId: warehouseStore.selectedWarehouseId,
          buildingCode: values.buildingCode,
          buildingName: values.buildingName,
        })
        ElMessage.success(t('master.building.createSuccess'))
      }
      router.push({ name: 'building-list' })
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 409) {
        if (error.response.data?.errorCode === 'DUPLICATE_CODE') {
          setFieldError('buildingCode', t('master.building.validation.codeDuplicate'))
        } else {
          ElMessage.error(t('error.optimisticLock'))
        }
      }
    } finally {
      loading.value = false
    }
  })

  async function handleCancel() {
    if (meta.value.dirty) {
      try {
        await ElMessageBox.confirm(t('master.building.confirmCancel'), t('common.confirm'), {
          type: 'warning',
          confirmButtonText: t('common.confirm'),
          cancelButtonText: t('common.cancel'),
        })
      } catch {
        return
      }
    }
    router.push({ name: 'building-list' })
  }

  return {
    buildingCode,
    buildingCodeAttrs,
    buildingName,
    buildingNameAttrs,
    errors,
    loading,
    initialLoading,
    isEdit,
    warehouseCode,
    warehouseStore,
    fetchBuilding,
    handleSubmit,
    handleCancel,
  }
}
