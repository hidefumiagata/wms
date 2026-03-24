import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
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
}

export function useOutboundSlipInspect() {
  const { t } = useI18n()
  const route = useRoute()
  const router = useRouter()

  const slip = ref<OutboundSlipDetail | null>(null)
  const lines = ref<InspectLine[]>([])
  const loading = ref(false)
  const saving = ref(false)

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
      lines.value = (res.data.lines ?? []).map(l => ({
        id: l.id!,
        lineNo: l.lineNo!,
        productCode: l.productCode!,
        productName: l.productName!,
        unitType: l.unitType!,
        orderedQty: l.orderedQty!,
        inspectedQty: l.inspectedQty ?? l.orderedQty!,
      }))
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) ElMessage.error(t('error.network'))
      else if (error.response.status === 404) {
        ElMessage.error(t('outbound.slip.notFound'))
        router.push({ name: 'outbound-slip-list' })
      }
    } finally { loading.value = false }
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
      router.push({ name: 'outbound-slip-detail', params: { id: slipId.value } })
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) ElMessage.error(t('error.network'))
      else if (error.response.status === 409) ElMessage.error(t('error.optimisticLock'))
      else if (error.response.status === 422) ElMessage.error(error.response.data?.message ?? t('error.server'))
    } finally { saving.value = false }
  }

  function goBack() { router.push({ name: 'outbound-slip-detail', params: { id: slipId.value } }) }

  return { slip, lines, loading, saving, fetchDetail, handleSave, goBack }
}
