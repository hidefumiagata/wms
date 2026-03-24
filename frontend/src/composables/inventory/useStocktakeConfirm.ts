import { ref, computed, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import type { StocktakeDetail } from '@/api/generated/models/stocktake-detail'
import type { StocktakeLineItem } from '@/api/generated/models/stocktake-line-item'

export function useStocktakeConfirm() {
  const { t } = useI18n()
  const router = useRouter()
  const route = useRoute()

  const loading = ref(false)
  const confirming = ref(false)
  const header = ref<StocktakeDetail | null>(null)
  const lines = ref<StocktakeLineItem[]>([])
  const showDiffOnly = ref(false)

  // --- AbortController ---
  let abortController: AbortController | null = null
  onUnmounted(() => { abortController?.abort() })

  const stocktakeId = computed(() => Number(route.params.id))

  // 差異サマリー
  const diffCount = computed(() => lines.value.filter(l => l.quantityDiff != null && l.quantityDiff !== 0).length)
  const noDiffCount = computed(() => lines.value.filter(l => l.quantityDiff != null && l.quantityDiff === 0).length)
  const totalCount = computed(() => lines.value.length)

  // フィルタ済みリスト
  const filteredLines = computed(() => {
    if (!showDiffOnly.value) return lines.value
    return lines.value.filter(l => l.quantityDiff != null && l.quantityDiff !== 0)
  })

  // --- 明細取得 ---
  async function fetchDetail() {
    abortController?.abort()
    abortController = new AbortController()
    loading.value = true
    try {
      const res = await apiClient.get<StocktakeDetail>(
        `/inventory/stocktakes/${stocktakeId.value}`,
        { signal: abortController.signal }
      )
      header.value = res.data
      lines.value = res.data.lines?.content ?? []
    } catch (err) {
      if (axios.isCancel(err)) return
      header.value = null
      lines.value = []
      ElMessage.error(t('inventory.stocktakeDetailFetchError'))
    } finally {
      loading.value = false
    }
  }

  // 差異率計算
  function diffRate(line: StocktakeLineItem): string {
    if (line.quantityBefore === 0) return '—'
    if (line.quantityDiff == null) return '—'
    const rate = (line.quantityDiff / line.quantityBefore) * 100
    return `${rate >= 0 ? '+' : ''}${rate.toFixed(1)}%`
  }

  // 差異数フォーマット
  function formatDiff(diff: number | null | undefined): string {
    if (diff == null) return '—'
    return `${diff >= 0 ? '+' : ''}${diff}`
  }

  // --- 棚卸確定 ---
  async function confirmStocktake() {
    try {
      await ElMessageBox.confirm(
        t('inventory.stocktakeConfirmMessage'),
        t('common.confirm'),
        { type: 'warning' }
      )
    } catch {
      return
    }

    confirming.value = true
    try {
      const res = await apiClient.post(`/inventory/stocktakes/${stocktakeId.value}/confirm`)
      ElMessage.success(t('inventory.stocktakeConfirmSuccess', {
        number: header.value?.stocktakeNumber ?? ''
      }))
      router.push({ name: 'stocktake-list' })
    } catch (err) {
      const error = toApiError(err)
      if (error.response?.status === 409) {
        ElMessage.error(error.response.data?.message ?? t('inventory.stocktakeConfirmNotAllCounted'))
      } else if (error.response?.status === 422) {
        ElMessage.error(error.response.data?.message ?? t('inventory.stocktakeConfirmAlreadyDone'))
      } else {
        ElMessage.error(t('inventory.stocktakeConfirmError'))
      }
    } finally {
      confirming.value = false
    }
  }

  // --- 遷移 ---
  function goBackToInput() {
    router.push({ name: 'stocktake-detail', params: { id: stocktakeId.value } })
  }

  function formatDate(dateStr: string): string {
    if (!dateStr) return ''
    const d = dateStr.includes('T') ? new Date(dateStr) : new Date(dateStr + 'T00:00:00')
    return d.toLocaleDateString('ja-JP', { year: 'numeric', month: '2-digit', day: '2-digit' })
  }

  return {
    loading,
    confirming,
    header,
    lines,
    showDiffOnly,
    stocktakeId,
    diffCount,
    noDiffCount,
    totalCount,
    filteredLines,
    fetchDetail,
    diffRate,
    formatDiff,
    confirmStocktake,
    goBackToInput,
    formatDate,
  }
}
