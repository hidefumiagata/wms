import { ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import type { OutboundSlipDetail } from '@/api/generated/models/outbound-slip-detail'

function formatDate(d: Date): string {
  return d.toISOString().slice(0, 10)
}

export function useOutboundSlipShip() {
  const { t } = useI18n()
  const route = useRoute()
  const router = useRouter()

  const slip = ref<OutboundSlipDetail | null>(null)
  const loading = ref(false)
  const submitting = ref(false)
  const errors = ref<Record<string, string>>({})

  const shipForm = reactive({
    carrier: '',
    trackingNumber: '',
    shippedDate: formatDate(new Date()),
    note: '',
  })

  const slipId = computed(() => {
    const id = route.params.id
    if (typeof id !== 'string') return null
    const num = Number(id)
    return Number.isFinite(num) && num > 0 ? num : null
  })

  // 営業日
  const businessDate = ref<string>(formatDate(new Date()))

  async function fetchBusinessDate() {
    try {
      const res = await apiClient.get('/system/business-date')
      businessDate.value = res.data.businessDate ?? formatDate(new Date())
      shipForm.shippedDate = businessDate.value
    } catch {
      // フォールバック
    }
  }

  async function fetchDetail() {
    if (!slipId.value) { router.push({ name: 'outbound-slip-list' }); return }
    loading.value = true
    try {
      const res = await apiClient.get<OutboundSlipDetail>(`/outbound/slips/${slipId.value}`)
      slip.value = res.data
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) ElMessage.error(t('error.network'))
      else if (error.response.status === 404) {
        ElMessage.error(t('outbound.slip.notFound'))
        router.push({ name: 'outbound-slip-list' })
      }
    } finally { loading.value = false }
  }

  function validate(): boolean {
    const newErrors: Record<string, string> = {}
    if (!shipForm.carrier) newErrors.carrier = t('outbound.ship.carrierRequired')
    if (!shipForm.shippedDate) newErrors.shippedDate = t('outbound.ship.shipDateRequired')
    errors.value = newErrors
    return Object.keys(newErrors).length === 0
  }

  async function handleSubmit() {
    if (!validate()) return

    try {
      await ElMessageBox.confirm(
        t('outbound.ship.confirmShipMessage'),
        t('common.confirm'),
        { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
      )
    } catch { return }

    submitting.value = true
    try {
      await apiClient.post(`/outbound/slips/${slipId.value}/ship`, {
        shippedDate: shipForm.shippedDate,
        carrier: shipForm.carrier,
        trackingNumber: shipForm.trackingNumber || undefined,
        note: shipForm.note || undefined,
      })
      ElMessage.success(t('outbound.ship.shipSuccess', { slipNo: slip.value?.slipNumber }))
      router.push({ name: 'outbound-slip-detail', params: { id: slipId.value } })
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) ElMessage.error(t('error.network'))
      else if (error.response.status === 409) ElMessage.error(t('error.optimisticLock'))
      else if (error.response.status === 422) ElMessage.error(error.response.data?.message ?? t('error.server'))
    } finally { submitting.value = false }
  }

  function goBack() { router.push({ name: 'outbound-slip-detail', params: { id: slipId.value } }) }

  return {
    slip, loading, submitting, errors, shipForm, businessDate,
    fetchBusinessDate, fetchDetail, handleSubmit, goBack,
  }
}
