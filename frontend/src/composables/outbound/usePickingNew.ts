import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import type { AllocationOrderSummary } from '@/api/generated/models/allocation-order-summary'
import type { AllocationOrderPageResponse } from '@/api/generated/models/allocation-order-page-response'

export function usePickingNew() {
  const { t } = useI18n()
  const router = useRouter()

  const allocatedOrders = ref<AllocationOrderSummary[]>([])
  const selectedSlipIds = ref<number[]>([])
  const areaId = ref<number | null>(null)
  const areaOptions = ref<{ id: number; areaName: string }[]>([])
  const loading = ref(false)
  const submitting = ref(false)

  async function fetchAllocatedOrders() {
    loading.value = true
    try {
      const res = await apiClient.get<AllocationOrderPageResponse>('/allocation/allocated-orders', {
        params: { page: 0, size: 100, sort: 'plannedDate,asc' },
      })
      allocatedOrders.value = res.data.content ?? []
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) ElMessage.error(t('error.network'))
    } finally {
      loading.value = false
    }
  }

  async function fetchAreaOptions() {
    try {
      const res = await apiClient.get('/master/areas', {
        params: { page: 0, size: 100, isActive: true, sort: 'areaName,asc' },
      })
      areaOptions.value = res.data.content.map((a: { id: number; areaName: string }) => ({
        id: a.id,
        areaName: a.areaName,
      }))
    } catch {
      // エラーは無視
    }
  }

  function handleSelectionChange(rows: AllocationOrderSummary[]) {
    selectedSlipIds.value = rows.map(r => r.id!).filter(Boolean)
  }

  const canSubmit = computed(() => selectedSlipIds.value.length > 0)

  async function handleSubmit() {
    if (selectedSlipIds.value.length === 0) {
      ElMessage.warning(t('outbound.picking.selectRequired'))
      return
    }

    submitting.value = true
    try {
      const body = {
        slipIds: selectedSlipIds.value,
        areaId: areaId.value || undefined,
      }
      const res = await apiClient.post('/outbound/picking', body)
      ElMessage.success(t('outbound.picking.createSuccess', { no: res.data.instructionNumber }))
      router.push({ name: 'picking-detail', params: { id: res.data.id } })
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 409) {
        ElMessage.error(error.response.data?.message ?? t('error.optimisticLock'))
      } else if (error.response.status === 422) {
        ElMessage.error(error.response.data?.message ?? t('error.server'))
      }
    } finally {
      submitting.value = false
    }
  }

  function goBack() { router.push({ name: 'picking-list' }) }

  return {
    allocatedOrders, selectedSlipIds, areaId, areaOptions, loading, submitting, canSubmit,
    fetchAllocatedOrders, fetchAreaOptions, handleSelectionChange, handleSubmit, goBack,
  }
}
