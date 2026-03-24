import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import type { InboundSlipDetail } from '@/api/generated/models/inbound-slip-detail'
import { InboundSlipStatus } from '@/api/generated/models/inbound-slip-status'
import { useAuthStore } from '@/stores/auth'

export function useInboundSlipDetail() {
  const { t } = useI18n()
  const route = useRoute()
  const router = useRouter()
  const auth = useAuthStore()

  // --- 状態 ---
  const slip = ref<InboundSlipDetail | null>(null)
  const loading = ref(false)
  const actionLoading = ref(false)

  const slipId = computed(() => {
    const id = route.params.id
    return typeof id === 'string' ? Number(id) : null
  })

  const isViewer = computed(() => auth.user?.role === 'VIEWER')

  // ステータス別ボタン表示制御
  const canConfirm = computed(() =>
    !isViewer.value && slip.value?.status === InboundSlipStatus.Planned
  )
  const canCancel = computed(() =>
    !isViewer.value &&
    slip.value?.status !== InboundSlipStatus.Stored &&
    slip.value?.status !== InboundSlipStatus.Cancelled
  )
  const canInspect = computed(() =>
    !isViewer.value && (
      slip.value?.status === InboundSlipStatus.Confirmed ||
      slip.value?.status === InboundSlipStatus.Inspecting ||
      slip.value?.status === InboundSlipStatus.PartialStored
    )
  )
  const canStore = computed(() =>
    !isViewer.value && (
      slip.value?.status === InboundSlipStatus.Inspecting ||
      slip.value?.status === InboundSlipStatus.PartialStored
    )
  )

  // --- API呼び出し ---
  async function fetchDetail() {
    if (!slipId.value) return
    loading.value = true
    try {
      const res = await apiClient.get<InboundSlipDetail>(`/inbound/slips/${slipId.value}`)
      slip.value = res.data
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 404) {
        ElMessage.error(t('inbound.slip.notFound'))
        router.push({ name: 'inbound-slip-list' })
      }
      // 403/500 はインターセプターが処理済み
    } finally {
      loading.value = false
    }
  }

  async function handleConfirm() {
    try {
      await ElMessageBox.confirm(t('inbound.slip.confirmMessage'), t('common.confirm'), {
        type: 'warning',
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
      })
    } catch {
      return
    }

    actionLoading.value = true
    try {
      await apiClient.post(`/inbound/slips/${slipId.value}/confirm`)
      ElMessage.success(t('inbound.slip.confirmSuccess'))
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
      // 403/500 はインターセプターが処理済み
    } finally {
      actionLoading.value = false
    }
  }

  async function handleCancel() {
    const isPartialStored = slip.value?.status === InboundSlipStatus.PartialStored
    const message = isPartialStored
      ? t('inbound.slip.cancelPartialStoredMessage')
      : t('inbound.slip.cancelMessage')

    try {
      await ElMessageBox.confirm(message, t('common.confirm'), {
        type: 'warning',
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
      })
    } catch {
      return
    }

    actionLoading.value = true
    try {
      await apiClient.post(`/inbound/slips/${slipId.value}/cancel`)
      ElMessage.success(t('inbound.slip.cancelSuccess'))
      await fetchDetail()
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 409) {
        const errorCode = error.response.data?.errorCode
        if (errorCode === 'INBOUND_ALREADY_STORED') {
          ElMessage.error(t('inbound.slip.cancelForbidden'))
        } else {
          ElMessage.error(t('error.optimisticLock'))
        }
      }
    } finally {
      actionLoading.value = false
    }
  }

  function goBack() {
    router.push({ name: 'inbound-slip-list' })
  }

  function goInspect() {
    router.push({ name: 'inbound-slip-inspect', params: { id: slipId.value } })
  }

  function goStore() {
    router.push({ name: 'inbound-slip-store', params: { id: slipId.value } })
  }

  return {
    slip,
    loading,
    actionLoading,
    slipId,
    canConfirm,
    canCancel,
    canInspect,
    canStore,
    fetchDetail,
    handleConfirm,
    handleCancel,
    goBack,
    goInspect,
    goStore,
  }
}
