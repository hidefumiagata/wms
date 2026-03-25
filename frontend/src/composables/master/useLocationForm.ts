import { ref, computed, watch, onUnmounted } from 'vue'
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
import type { LocationFullDetail } from '@/api/generated/models/location-full-detail'
import type { AreaListItem } from '@/api/generated/models/area-list-item'
import type { PageResponse } from '@/api/types'

export function useLocationForm() {
  const { t } = useI18n()
  const router = useRouter()
  const route = useRoute()
  const warehouseStore = useWarehouseStore()

  const locationId = computed(() => {
    const id = route.params.id
    if (!id) return null
    const num = Number(id)
    return Number.isInteger(num) && num > 0 ? num : null
  })
  const isEdit = computed(() => locationId.value !== null)

  const areas = ref<AreaListItem[]>([])

  const locationSchema = z.object({
    areaId: z.number({ required_error: t('master.location.validation.areaRequired') }),
    locationCode: z
      .string()
      .min(1, t('master.location.validation.codeRequired'))
      .max(30, t('master.location.validation.codeMaxLength')),
    locationName: z.string().max(200, t('master.location.validation.nameMaxLength')).optional(),
  })

  const {
    errors,
    handleSubmit: createSubmitHandler,
    setFieldError,
    setValues,
    defineField,
    meta,
  } = useForm({
    validationSchema: toTypedSchema(locationSchema),
    initialValues: {
      areaId: undefined as number | undefined,
      locationCode: '',
      locationName: '',
    },
  })

  const [areaId, areaIdAttrs] = defineField('areaId', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })
  const [locationCode, locationCodeAttrs] = defineField('locationCode', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })
  const [locationName, locationNameAttrs] = defineField('locationName', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })

  const loading = ref(false)
  const initialLoading = ref(false)
  const version = ref(0)
  const warehouseCode = ref('')
  const areaCode = ref('')

  // --- 並行リクエスト制御 ---
  let abortController: AbortController | null = null
  onUnmounted(() => { abortController?.abort() })

  // 選択中のエリア情報
  const selectedArea = computed(() =>
    areas.value.find((a) => a.id === areaId.value) ?? null,
  )

  // EVT-MST052-002: 在庫エリア選択時にロケーションコードの棟部分を自動補完
  watch(areaId, (newId) => {
    if (isEdit.value || !newId) return
    const area = areas.value.find((a) => a.id === newId)
    if (area?.areaType === 'STOCK') {
      // 棟コードをプレフィックスとして自動入力（ユーザーが残りを入力）
      const prefix = area.buildingCode + '-'
      if (!locationCode.value || locationCode.value === '') {
        locationCode.value = prefix
      }
    }
  })

  async function fetchAreas() {
    if (!warehouseStore.selectedWarehouseId) return
    try {
      const res = await apiClient.get<PageResponse<AreaListItem>>('/master/areas', {
        params: { warehouseId: warehouseStore.selectedWarehouseId, isActive: true, size: 100 },
      })
      areas.value = res.data.content
    } catch {
      areas.value = []
    }
  }

  async function fetchLocation() {
    if (!locationId.value) {
      router.push({ name: 'location-list' })
      return
    }
    abortController?.abort()
    abortController = new AbortController()
    const signal = abortController.signal

    initialLoading.value = true
    try {
      const res = await apiClient.get<LocationFullDetail>(`/master/locations/${locationId.value}`, { signal })
      setValues({
        areaId: res.data.areaId,
        locationCode: res.data.locationCode,
        locationName: res.data.locationName ?? '',
      })
      version.value = res.data.version
      warehouseCode.value = res.data.warehouseCode
      areaCode.value = res.data.areaCode
    } catch (err: unknown) {
      if (axios.isCancel(err)) return
      const error = toApiError(err)
      if (error.response?.status === 404) {
        ElMessage.error(t('master.location.notFound'))
        router.push({ name: 'location-list' })
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
        await apiClient.put(`/master/locations/${locationId.value}`, {
          locationName: values.locationName?.trim() || null,
          version: version.value,
        })
        ElMessage.success(t('master.location.updateSuccess'))
      } else {
        await apiClient.post('/master/locations', {
          areaId: values.areaId,
          locationCode: values.locationCode,
          locationName: values.locationName?.trim() || null,
        })
        ElMessage.success(t('master.location.createSuccess'))
      }
      router.push({ name: 'location-list' })
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 409) {
        if (error.response.data?.errorCode === 'DUPLICATE_CODE') {
          setFieldError('locationCode', t('master.location.validation.codeDuplicate'))
        } else {
          ElMessage.error(t('error.optimisticLock'))
        }
      } else if (error.response.status === 422) {
        const errorCode = error.response.data?.errorCode
        if (errorCode === 'AREA_LOCATION_LIMIT_EXCEEDED') {
          ElMessage.error(t('master.location.areaLocationLimitExceeded'))
        }
      } else if (error.response.status === 400) {
        const errorCode = error.response.data?.errorCode
        if (errorCode === 'INVALID_LOCATION_CODE_FORMAT') {
          setFieldError('locationCode', t('master.location.validation.codeFormatStock'))
        }
      }
    } finally {
      loading.value = false
    }
  })

  async function handleCancel() {
    if (meta.value.dirty) {
      try {
        await ElMessageBox.confirm(t('master.location.confirmCancel'), t('common.confirm'), {
          type: 'warning',
          confirmButtonText: t('common.confirm'),
          cancelButtonText: t('common.cancel'),
        })
      } catch {
        return
      }
    }
    router.push({ name: 'location-list' })
  }

  return {
    areaId,
    areaIdAttrs,
    locationCode,
    locationCodeAttrs,
    locationName,
    locationNameAttrs,
    areas,
    selectedArea,
    errors,
    loading,
    initialLoading,
    isEdit,
    warehouseCode,
    areaCode,
    warehouseStore,
    fetchAreas,
    fetchLocation,
    handleSubmit,
    handleCancel,
  }
}
