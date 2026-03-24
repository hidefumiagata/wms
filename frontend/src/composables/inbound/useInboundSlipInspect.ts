import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import type { InboundSlipDetail } from '@/api/generated/models/inbound-slip-detail'
import { InboundLineStatus } from '@/api/generated/models/inbound-line-status'

interface InspectLine {
  id: number
  lineNo: number
  productCode: string
  productName: string
  unitType: string
  plannedQty: number
  inspectedQty: number
  diffQty: number
  lineStatus: string
  isStored: boolean
}

export function useInboundSlipInspect() {
  const { t } = useI18n()
  const route = useRoute()
  const router = useRouter()

  const slip = ref<InboundSlipDetail | null>(null)
  const lines = ref<InspectLine[]>([])
  const loading = ref(false)
  const saving = ref(false)

  const slipId = computed(() => {
    const id = route.params.id
    return typeof id === 'string' ? Number(id) : null
  })

  async function fetchDetail() {
    if (!slipId.value) return
    loading.value = true
    try {
      const res = await apiClient.get<InboundSlipDetail>(`/inbound/slips/${slipId.value}`)
      slip.value = res.data
      lines.value = res.data.lines.map(l => {
        const inspectedQty = l.inspectedQty ?? l.plannedQty
        return {
          id: l.id,
          lineNo: l.lineNo,
          productCode: l.productCode,
          productName: l.productName,
          unitType: l.unitType,
          plannedQty: l.plannedQty,
          inspectedQty,
          diffQty: inspectedQty - l.plannedQty,
          lineStatus: l.lineStatus,
          isStored: l.lineStatus === InboundLineStatus.Stored,
        }
      })
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 404) {
        ElMessage.error(t('inbound.slip.notFound'))
        router.push({ name: 'inbound-slip-list' })
      }
    } finally {
      loading.value = false
    }
  }

  function updateDiff(line: InspectLine) {
    line.diffQty = (line.inspectedQty ?? 0) - line.plannedQty
  }

  async function handleSave() {
    // バリデーション: 入荷数は0以上
    for (const line of lines.value) {
      if (line.isStored) continue
      if (line.inspectedQty == null || line.inspectedQty < 0) {
        ElMessage.error(t('inbound.inspect.qtyInvalid'))
        return
      }
    }

    // 差異確認ダイアログ
    const diffLines = lines.value.filter(l => !l.isStored && l.diffQty !== 0)
    if (diffLines.length > 0) {
      try {
        await ElMessageBox.confirm(
          t('inbound.inspect.diffWarning', { count: diffLines.length }),
          t('common.confirm'),
          { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
        )
      } catch {
        return
      }
    }

    saving.value = true
    try {
      const inspectLines = lines.value
        .filter(l => !l.isStored)
        .map(l => ({ lineId: l.id, inspectedQty: l.inspectedQty }))

      await apiClient.post(`/inbound/slips/${slipId.value}/inspect`, { lines: inspectLines })
      ElMessage.success(t('inbound.inspect.saveSuccess'))
      router.push({ name: 'inbound-slip-detail', params: { id: slipId.value } })
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 409) {
        ElMessage.error(error.response.data?.message ?? t('error.optimisticLock'))
      }
    } finally {
      saving.value = false
    }
  }

  function goBack() {
    router.push({ name: 'inbound-slip-detail', params: { id: slipId.value } })
  }

  return {
    slip,
    lines,
    loading,
    saving,
    fetchDetail,
    updateDiff,
    handleSave,
    goBack,
  }
}
