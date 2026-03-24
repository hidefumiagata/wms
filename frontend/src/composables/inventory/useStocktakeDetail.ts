import { ref, computed, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import { formatDate } from '@/utils/inventoryFormatters'
import type { StocktakeDetail } from '@/api/generated/models/stocktake-detail'
import type { StocktakeLineItem } from '@/api/generated/models/stocktake-line-item'

interface EditableLine extends StocktakeLineItem {
  editQty: number | null
  isDirty: boolean
}

export function useStocktakeDetail() {
  const { t } = useI18n()
  const router = useRouter()
  const route = useRoute()

  const loading = ref(false)
  const saving = ref(false)
  const header = ref<StocktakeDetail | null>(null)
  const lines = ref<EditableLine[]>([])

  // --- AbortController ---
  let abortController: AbortController | null = null
  onUnmounted(() => { abortController?.abort() })

  const stocktakeId = computed(() => Number(route.params.id))

  // 未入力件数
  const uncountedCount = computed(() =>
    lines.value.filter(l => !l.isCounted && l.editQty == null).length
  )
  const totalCount = computed(() => lines.value.length)
  const allCounted = computed(() => uncountedCount.value === 0 && totalCount.value > 0)

  // 変更があるか（キャンセル時の確認用）
  const hasDirtyLines = computed(() => lines.value.some(l => l.isDirty))

  // --- 明細取得 ---
  async function fetchDetail() {
    abortController?.abort()
    abortController = new AbortController()
    const signal = abortController.signal
    loading.value = true
    try {
      const res = await apiClient.get<StocktakeDetail>(
        `/inventory/stocktakes/${stocktakeId.value}`,
        { signal }
      )
      header.value = res.data
      const rawLines: StocktakeLineItem[] = res.data.lines?.content ?? []
      lines.value = rawLines.map(l => ({
        ...l,
        editQty: l.isCounted ? (l.quantityCounted ?? null) : null,
        isDirty: false,
      }))
    } catch (err) {
      if (axios.isCancel(err)) return
      header.value = null
      lines.value = []
      ElMessage.error(t('inventory.stocktakeDetailFetchError'))
    } finally {
      if (!signal.aborted) {
        loading.value = false
      }
    }
  }

  // --- 実数入力変更 ---
  function onActualQtyChange(line: EditableLine) {
    if (line.editQty != null && line.editQty < 0) {
      line.editQty = null
      return
    }
    line.isDirty = true
  }

  // 行の入力状態
  function lineStatus(line: EditableLine): 'uncounted' | 'diff' | 'match' {
    const qty = line.editQty ?? (line.isCounted ? line.quantityCounted : null)
    if (qty == null) return 'uncounted'
    if (qty !== line.quantityBefore) return 'diff'
    return 'match'
  }

  // --- 一時保存 ---
  async function saveLines() {
    const dirtyLines = lines.value.filter(l => l.editQty != null && (l.isDirty || !l.isCounted))
    if (dirtyLines.length === 0) {
      ElMessage.info(t('inventory.stocktakeSaveSuccess'))
      return
    }

    // バリデーション
    for (const l of dirtyLines) {
      if (l.editQty != null && (l.editQty < 0 || !Number.isInteger(l.editQty))) {
        ElMessage.error(t('inventory.stocktakeActualQtyInvalid'))
        return
      }
    }

    saving.value = true
    try {
      await apiClient.put(`/inventory/stocktakes/${stocktakeId.value}/lines`, {
        lines: dirtyLines.map(l => ({
          lineId: l.lineId,
          actualQty: l.editQty,
        })),
      })
      ElMessage.success(t('inventory.stocktakeSaveSuccess'))
      // 保存後にリフレッシュして最新状態を反映
      await fetchDetail()
    } catch (err) {
      const error = toApiError(err)
      if (error.response?.status === 409 || error.response?.status === 422) {
        ElMessage.error(error.response.data?.message ?? t('inventory.stocktakeSaveError'))
      } else {
        ElMessage.error(t('inventory.stocktakeSaveError'))
      }
    } finally {
      saving.value = false
    }
  }

  // --- 確定へ遷移 ---
  function goToConfirm() {
    if (!allCounted.value) {
      ElMessage.warning(t('inventory.stocktakeNotAllCounted'))
      return
    }
    router.push({ name: 'stocktake-confirm', params: { id: stocktakeId.value } })
  }

  // --- キャンセル（一覧へ） ---
  async function goBack() {
    if (hasDirtyLines.value) {
      try {
        await ElMessageBox.confirm(
          t('inventory.stocktakeCancelConfirm'),
          t('common.confirm'),
          { type: 'warning' }
        )
      } catch {
        return
      }
    }
    router.push({ name: 'stocktake-list' })
  }

  return {
    loading,
    saving,
    header,
    lines,
    stocktakeId,
    uncountedCount,
    totalCount,
    allCounted,
    fetchDetail,
    onActualQtyChange,
    lineStatus,
    saveLines,
    goToConfirm,
    goBack,
    formatDate,
  }
}
