import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import type { InboundSlipDetail } from '@/api/generated/models/inbound-slip-detail'
import { InboundLineStatus } from '@/api/generated/models/inbound-line-status'

interface StoreLine {
  id: number
  lineNo: number
  productCode: string
  productName: string
  inspectedQty: number
  locationId: number | null
  locationCode: string
  isStored: boolean
}

export function useInboundSlipStore() {
  const { t } = useI18n()
  const route = useRoute()
  const router = useRouter()

  const slip = ref<InboundSlipDetail | null>(null)
  const lines = ref<StoreLine[]>([])
  const loading = ref(false)
  const storing = ref(false)

  // ロケーション選択肢
  const locationOptions = ref<{ id: number; locationCode: string }[]>([])

  const slipId = computed(() => {
    const id = route.params.id
    if (typeof id !== 'string') return null
    const num = Number(id)
    return Number.isFinite(num) && num > 0 ? num : null
  })

  const hasUnstored = computed(() => lines.value.some(l => !l.isStored))

  async function fetchDetail() {
    if (!slipId.value) {
      router.push({ name: 'inbound-slip-list' })
      return
    }
    loading.value = true
    try {
      const res = await apiClient.get<InboundSlipDetail>(`/inbound/slips/${slipId.value}`)
      slip.value = res.data
      lines.value = res.data.lines
        .filter(l => l.inspectedQty != null) // 検品済みのみ
        .map(l => ({
          id: l.id,
          lineNo: l.lineNo,
          productCode: l.productCode,
          productName: l.productName,
          inspectedQty: l.inspectedQty!,
          locationId: l.putawayLocationId ?? null,
          locationCode: l.putawayLocationCode ?? '',
          isStored: l.lineStatus === InboundLineStatus.Stored,
        }))
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

  async function fetchLocations() {
    try {
      const res = await apiClient.get('/master/locations', {
        params: { page: 0, size: 1000, isActive: true, sort: 'locationCode,asc' },
      })
      locationOptions.value = res.data.content.map((l: { id: number; locationCode: string }) => ({
        id: l.id,
        locationCode: l.locationCode,
      }))
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      }
    }
  }

  async function handleStoreLines(lineIds: number[]) {
    // バリデーション: ロケーション選択必須
    const targetLines = lines.value.filter(l => lineIds.includes(l.id) && !l.isStored)
    for (const line of targetLines) {
      if (!line.locationId) {
        ElMessage.error(t('inbound.store.locationRequired'))
        return
      }
    }

    storing.value = true
    try {
      const storeLines = targetLines.map(l => ({
        lineId: l.id,
        locationId: l.locationId!,
      }))

      await apiClient.post(`/inbound/slips/${slipId.value}/store`, { lines: storeLines })

      // 成功: 各行の状態を更新
      for (const line of targetLines) {
        line.isStored = true
      }

      const allStored = lines.value.every(l => l.isStored)
      if (allStored) {
        ElMessage.success(t('inbound.store.allStoredSuccess'))
        router.push({ name: 'inbound-slip-detail', params: { id: slipId.value } })
      } else if (targetLines.length === 1) {
        // 個別確定: 商品名とロケーション表示（D-MAJ-03）
        const line = targetLines[0]
        const loc = locationOptions.value.find(l => l.id === line.locationId)
        ElMessage.success(t('inbound.store.partialSuccess', {
          productName: line.productName,
          location: loc?.locationCode ?? '',
        }))
      } else {
        ElMessage.success(t('inbound.store.partialSuccess', { productName: '', location: '' }))
      }
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 409) {
        ElMessage.error(error.response.data?.message ?? t('error.optimisticLock'))
      } else if (error.response.status === 422) {
        ElMessage.error(error.response.data?.message ?? t('error.server'))
      }
      // 403/500 はインターセプターが処理済み
    } finally {
      storing.value = false
    }
  }

  async function handleStoreSingle(lineId: number) {
    await handleStoreLines([lineId])
  }

  async function handleStoreAll() {
    const unstoredIds = lines.value.filter(l => !l.isStored).map(l => l.id)
    if (unstoredIds.length === 0) return

    try {
      await ElMessageBox.confirm(
        t('inbound.store.confirmAll'),
        t('common.confirm'),
        { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
      )
    } catch {
      return
    }

    await handleStoreLines(unstoredIds)
  }

  function goBack() {
    router.push({ name: 'inbound-slip-detail', params: { id: slipId.value } })
  }

  return {
    slip,
    lines,
    loading,
    storing,
    locationOptions,
    hasUnstored,
    fetchDetail,
    fetchLocations,
    handleStoreSingle,
    handleStoreAll,
    goBack,
  }
}
