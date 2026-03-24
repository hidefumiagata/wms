import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import type { OutboundSlipDetail } from '@/api/generated/models/outbound-slip-detail'
import { OutboundSlipStatus } from '@/api/generated/models/outbound-slip-status'
import { useAuthStore } from '@/stores/auth'

export function useOutboundSlipDetail() {
  const { t } = useI18n()
  const route = useRoute()
  const router = useRouter()
  const auth = useAuthStore()

  const slip = ref<OutboundSlipDetail | null>(null)
  const loading = ref(false)
  const actionLoading = ref(false)

  const slipId = computed(() => {
    const id = route.params.id
    if (typeof id !== 'string') return null
    const num = Number(id)
    return Number.isFinite(num) && num > 0 ? num : null
  })

  const isViewer = computed(() => auth.user?.role === 'VIEWER')

  // ステータス別ボタン表示
  const canAllocate = computed(() =>
    !isViewer.value && (
      slip.value?.status === OutboundSlipStatus.Ordered ||
      slip.value?.status === OutboundSlipStatus.PartialAllocated
    )
  )
  const canCancel = computed(() =>
    !isViewer.value &&
    slip.value?.status === OutboundSlipStatus.Ordered
  )
  const canPickingNew = computed(() =>
    !isViewer.value &&
    slip.value?.status === OutboundSlipStatus.Allocated
  )
  const canInspect = computed(() =>
    !isViewer.value &&
    slip.value?.status === OutboundSlipStatus.PickingCompleted
  )
  const canShip = computed(() =>
    !isViewer.value &&
    slip.value?.status === OutboundSlipStatus.Inspecting
  )

  async function fetchDetail() {
    if (!slipId.value) {
      router.push({ name: 'outbound-slip-list' })
      return
    }
    loading.value = true
    try {
      const res = await apiClient.get<OutboundSlipDetail>(`/outbound/slips/${slipId.value}`)
      slip.value = res.data
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 404) {
        ElMessage.error(t('outbound.slip.notFound'))
        router.push({ name: 'outbound-slip-list' })
      }
    } finally {
      loading.value = false
    }
  }

  async function handleAllocate() {
    try {
      await ElMessageBox.confirm(
        t('outbound.slip.allocateSingleMessage'),
        t('common.confirm'),
        { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
      )
    } catch { return }

    actionLoading.value = true
    try {
      await apiClient.post('/allocation/execute', { outboundSlipIds: [slipId.value] })
      ElMessage.success(t('outbound.slip.allocateSuccess'))
      await fetchDetail()
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 422) {
        ElMessage.warning(t('outbound.slip.allocatePartialError'))
        await fetchDetail()
      } else if (error.response.status === 409) {
        ElMessage.error(t('error.optimisticLock'))
      }
    } finally {
      actionLoading.value = false
    }
  }

  async function handleCancel() {
    try {
      await ElMessageBox.confirm(
        t('outbound.slip.cancelMessage'),
        t('common.confirm'),
        { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
      )
    } catch { return }

    actionLoading.value = true
    try {
      await apiClient.post(`/outbound/slips/${slipId.value}/cancel`)
      ElMessage.success(t('outbound.slip.cancelSuccess'))
      await fetchDetail()
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 409) {
        ElMessage.error(t('error.optimisticLock'))
      } else if (error.response.status === 422) {
        ElMessage.error(error.response.data?.message ?? t('error.server'))
      }
    } finally {
      actionLoading.value = false
    }
  }

  function goBack() { router.push({ name: 'outbound-slip-list' }) }
  function goInspect() { router.push({ name: 'outbound-slip-inspect', params: { id: slipId.value } }) }
  function goShip() { router.push({ name: 'outbound-slip-ship', params: { id: slipId.value } }) }

  return {
    slip, loading, actionLoading, slipId, isViewer,
    canAllocate, canCancel, canPickingNew, canInspect, canShip,
    fetchDetail, handleAllocate, handleCancel, goBack, goInspect, goShip,
  }
}
