import { ref, computed, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import type { OutboundSlipDetail } from '@/api/generated/models/outbound-slip-detail'

interface InspectLine {
  id: number
  lineNo: number
  productCode: string
  productName: string
  unitType: string
  orderedQty: number
  inspectedQty: number
  diffQty: number
}

export function useOutboundSlipInspect() {
  const { t } = useI18n()
  const route = useRoute()
  const router = useRouter()

  const slip = ref<OutboundSlipDetail | null>(null)
  const lines = ref<InspectLine[]>([])
  const loading = ref(false)
  const saving = ref(false)
  const isDirty = ref(false)
  const saved = ref(false)

  const slipId = computed(() => {
    const id = route.params.id
    if (typeof id !== 'string') return null
    const num = Number(id)
    return Number.isFinite(num) && num > 0 ? num : null
  })

  async function fetchDetail() {
    if (!slipId.value) { router.push({ name: 'outbound-slip-list' }); return }
    loading.value = true
    try {
      const res = await apiClient.get<OutboundSlipDetail>(`/outbound/slips/${slipId.value}`)
      slip.value = res.data
      lines.value = (res.data.lines ?? []).map(l => {
        const inspectedQty = l.inspectedQty ?? l.orderedQty!
        return {
          id: l.id!,
          lineNo: l.lineNo!,
          productCode: l.productCode!,
          productName: l.productName!,
          unitType: l.unitType!,
          orderedQty: l.orderedQty!,
          inspectedQty,
          diffQty: inspectedQty - l.orderedQty!,
        }
      })
      isDirty.value = false
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) ElMessage.error(t('error.network'))
      else if (error.response.status === 404) {
        ElMessage.error(t('outbound.slip.notFound'))
        router.push({ name: 'outbound-slip-list' })
      }
    } finally { loading.value = false }
  }

  function updateDiff(line: InspectLine) {
    line.diffQty = (line.inspectedQty ?? 0) - line.orderedQty
    isDirty.value = true
  }

  async function handleSave() {
    for (const line of lines.value) {
      if (line.inspectedQty == null || line.inspectedQty < 0) {
        ElMessage.error(t('outbound.inspect.qtyInvalid'))
        return
      }
    }

    saving.value = true
    try {
      const reqLines = lines.value.map(l => ({ lineId: l.id, inspectedQty: l.inspectedQty }))
      await apiClient.post(`/outbound/slips/${slipId.value}/inspect`, { lines: reqLines })
      ElMessage.success(t('outbound.inspect.saveSuccess'))
      saved.value = true
      isDirty.value = false
      router.push({ name: 'outbound-slip-ship', params: { id: slipId.value } })
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) ElMessage.error(t('error.network'))
      else if (error.response.status === 409) ElMessage.error(t('error.optimisticLock'))
      else if (error.response.status === 422) ElMessage.error(error.response.data?.message ?? t('error.server'))
    } finally { saving.value = false }
  }

  function goBack() { router.push({ name: 'outbound-slip-detail', params: { id: slipId.value } }) }

  // 未保存変更ガード
  onBeforeRouteLeave(async () => {
    if (isDirty.value && !saved.value) {
      try {
        await ElMessageBox.confirm(
          t('outbound.inspect.unsavedWarning'),
          t('common.confirm'),
          { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
        )
        return true
      } catch {
        return false
      }
    }
    return true
  })

  return { slip, lines, loading, saving, fetchDetail, updateDiff, handleSave, goBack }
}
