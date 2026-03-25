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
import type { AreaDetail } from '@/api/generated/models/area-detail'
import type { BuildingListItem } from '@/api/generated/models/building-list-item'
import type { PageResponse } from '@/api/types'

const AREA_CODE_REGEX = /^[A-Za-z0-9\-]+$/

export function useAreaForm() {
  const { t } = useI18n()
  const router = useRouter()
  const route = useRoute()
  const warehouseStore = useWarehouseStore()

  const areaId = computed(() => {
    const id = route.params.id
    if (!id) return null
    const num = Number(id)
    return Number.isInteger(num) && num > 0 ? num : null
  })
  const isEdit = computed(() => areaId.value !== null)

  const buildings = ref<BuildingListItem[]>([])

  // --- Zod スキーマ（ロケール変更時に自動再生成） ---
  const validationSchema = computed(() =>
    toTypedSchema(
      z.object({
        buildingId: z.number({ required_error: t('master.area.validation.buildingRequired') }),
        areaCode: z
          .string()
          .min(1, t('master.area.validation.codeRequired'))
          .max(20, t('master.area.validation.codeMaxLength'))
          .regex(AREA_CODE_REGEX, t('master.area.validation.codeFormat')),
        areaName: z
          .string()
          .min(1, t('master.area.validation.nameRequired'))
          .max(200, t('master.area.validation.nameMaxLength')),
        storageCondition: z.string().min(1, t('master.area.validation.storageConditionRequired')),
        areaType: z.string().min(1, t('master.area.validation.areaTypeRequired')),
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
    initialValues: {
      buildingId: undefined as number | undefined,
      areaCode: '',
      areaName: '',
      storageCondition: 'AMBIENT',
      areaType: 'STOCK',
    },
  })

  const [buildingId, buildingIdAttrs] = defineField('buildingId', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })
  const [areaCode, areaCodeAttrs] = defineField('areaCode', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })
  const [areaName, areaNameAttrs] = defineField('areaName', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })
  const [storageCondition, storageConditionAttrs] = defineField('storageCondition', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })
  const [areaType, areaTypeAttrs] = defineField('areaType', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })

  const loading = ref(false)
  const initialLoading = ref(false)
  const version = ref(0)
  const warehouseCode = ref('')
  const buildingCode = ref('')

  // --- 並行リクエスト制御 ---
  let abortController: AbortController | null = null
  onUnmounted(() => { abortController?.abort() })

  async function fetchBuildings() {
    if (!warehouseStore.selectedWarehouseId) return
    try {
      const res = await apiClient.get<PageResponse<BuildingListItem>>('/master/buildings', {
        params: { warehouseId: warehouseStore.selectedWarehouseId, isActive: true, size: 100 },
      })
      buildings.value = res.data.content
    } catch {
      buildings.value = []
    }
  }

  async function fetchArea() {
    if (!areaId.value) {
      router.push({ name: 'area-list' })
      return
    }
    abortController?.abort()
    abortController = new AbortController()
    const signal = abortController.signal

    initialLoading.value = true
    try {
      const res = await apiClient.get<AreaDetail>(`/master/areas/${areaId.value}`, { signal })
      setValues({
        buildingId: res.data.buildingId,
        areaCode: res.data.areaCode,
        areaName: res.data.areaName,
        storageCondition: res.data.storageCondition,
        areaType: res.data.areaType,
      })
      version.value = res.data.version
      warehouseCode.value = res.data.warehouseCode
      buildingCode.value = res.data.buildingCode
    } catch (err: unknown) {
      if (axios.isCancel(err)) return
      const error = toApiError(err)
      if (error.response?.status === 404) {
        ElMessage.error(t('master.area.notFound'))
        router.push({ name: 'area-list' })
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
        await apiClient.put(`/master/areas/${areaId.value}`, {
          areaName: values.areaName,
          storageCondition: values.storageCondition,
          version: version.value,
        })
        ElMessage.success(t('master.area.updateSuccess'))
      } else {
        await apiClient.post('/master/areas', {
          buildingId: values.buildingId,
          areaCode: values.areaCode,
          areaName: values.areaName,
          storageCondition: values.storageCondition,
          areaType: values.areaType,
        })
        ElMessage.success(t('master.area.createSuccess'))
      }
      router.push({ name: 'area-list' })
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 409) {
        if (error.response.data?.errorCode === 'DUPLICATE_CODE') {
          setFieldError('areaCode', t('master.area.validation.codeDuplicate'))
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
        await ElMessageBox.confirm(t('master.area.confirmCancel'), t('common.confirm'), {
          type: 'warning',
          confirmButtonText: t('common.confirm'),
          cancelButtonText: t('common.cancel'),
        })
      } catch {
        return
      }
    }
    router.push({ name: 'area-list' })
  }

  return {
    buildingId,
    buildingIdAttrs,
    areaCode,
    areaCodeAttrs,
    areaName,
    areaNameAttrs,
    storageCondition,
    storageConditionAttrs,
    areaType,
    areaTypeAttrs,
    buildings,
    errors,
    loading,
    initialLoading,
    isEdit,
    warehouseCode,
    buildingCode,
    warehouseStore,
    fetchBuildings,
    fetchArea,
    handleSubmit,
    handleCancel,
  }
}
