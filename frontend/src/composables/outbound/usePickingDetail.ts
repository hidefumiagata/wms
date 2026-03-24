import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import type { PickingInstructionDetail } from '@/api/generated/models/picking-instruction-detail'
import { PickingInstructionStatus } from '@/api/generated/models/picking-instruction-status'

export function usePickingDetail() {
  const { t } = useI18n()
  const route = useRoute()
  const router = useRouter()

  const instruction = ref<PickingInstructionDetail | null>(null)
  const loading = ref(false)
  const completing = ref(false)

  const pickingId = computed(() => {
    const id = route.params.id
    if (typeof id !== 'string') return null
    const num = Number(id)
    return Number.isFinite(num) && num > 0 ? num : null
  })

  const canComplete = computed(() =>
    instruction.value?.status === PickingInstructionStatus.Created ||
    instruction.value?.status === PickingInstructionStatus.InProgress
  )

  async function fetchDetail() {
    if (!pickingId.value) {
      router.push({ name: 'picking-list' })
      return
    }
    loading.value = true
    try {
      const res = await apiClient.get<PickingInstructionDetail>(`/outbound/picking/${pickingId.value}`)
      instruction.value = res.data
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 404) {
        ElMessage.error(t('outbound.picking.notFound'))
        router.push({ name: 'picking-list' })
      }
    } finally {
      loading.value = false
    }
  }

  async function handleComplete(completedLines: { lineId: number; qtyPicked: number }[]) {
    completing.value = true
    try {
      await apiClient.post(`/outbound/picking/${pickingId.value}/complete`, { lines: completedLines })
      ElMessage.success(t('outbound.picking.completeSuccess'))
      await fetchDetail()
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
      completing.value = false
    }
  }

  function goBack() { router.push({ name: 'picking-list' }) }

  return {
    instruction, loading, completing, pickingId, canComplete,
    fetchDetail, handleComplete, goBack,
  }
}
