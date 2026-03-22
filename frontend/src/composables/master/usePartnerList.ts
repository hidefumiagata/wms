import { ref, reactive, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import type { PartnerDetail } from '@/api/generated/models/partner-detail'
import type { PartnerPageResponse } from '@/api/generated/models/partner-page-response'
import type { PartnerType } from '@/api/generated/models/partner-type'

export function usePartnerList() {
  const { t } = useI18n()

  // --- 状態 ---
  const items = ref<PartnerDetail[]>([])
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1) // Element Plus は 1-based
  const pageSize = ref(20)

  const searchForm = reactive({
    partnerCode: '',
    partnerName: '',
    partnerType: null as PartnerType | null,
    isActive: null as boolean | null,
  })

  // --- 並行リクエスト制御 ---
  // 新しいリクエストが来たら前のリクエストをキャンセルし、
  // 古いレスポンスで画面が上書きされる Race Condition を防ぐ
  let abortController: AbortController | null = null

  // コンポーネントのアンマウント時に進行中のリクエストをキャンセルし、
  // アンマウント後のステート更新（メモリリーク）を防ぐ
  onUnmounted(() => {
    abortController?.abort()
  })

  // --- API呼び出し ---
  async function fetchList() {
    abortController?.abort()
    abortController = new AbortController()
    const signal = abortController.signal

    loading.value = true
    try {
      const params: Record<string, unknown> = {
        page: page.value - 1,
        size: pageSize.value,
        sort: 'partnerCode,asc',
      }
      if (searchForm.partnerCode) params.partnerCode = searchForm.partnerCode
      if (searchForm.partnerName) params.partnerName = searchForm.partnerName
      if (searchForm.partnerType) params.partnerType = searchForm.partnerType
      if (searchForm.isActive !== null) params.isActive = searchForm.isActive

      const res = await apiClient.get<PartnerPageResponse>('/master/partners', {
        params,
        signal,
      })
      items.value = res.data.content
      total.value = res.data.totalElements
    } catch (err: unknown) {
      if (axios.isCancel(err)) return

      items.value = []
      total.value = 0
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else {
        ElMessage.error(t('master.partner.fetchError'))
      }
    } finally {
      if (!signal.aborted) {
        loading.value = false
      }
    }
  }

  // --- 操作 ---
  function handleSearch() {
    page.value = 1
    fetchList()
  }

  function handleReset() {
    searchForm.partnerCode = ''
    searchForm.partnerName = ''
    searchForm.partnerType = null
    searchForm.isActive = null
    page.value = 1
    fetchList()
  }

  function handlePageChange(p: number) {
    page.value = p
    fetchList()
  }

  function handleSizeChange(s: number) {
    pageSize.value = s
    page.value = 1
    fetchList()
  }

  async function handleToggleActive(row: PartnerDetail) {
    const isDeactivating = row.isActive
    const confirmMsg = isDeactivating
      ? t('master.partner.confirmDeactivate')
      : t('master.partner.confirmActivate')

    try {
      await ElMessageBox.confirm(confirmMsg, t('common.confirm'), {
        type: 'warning',
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
      })
    } catch {
      return // キャンセル
    }

    loading.value = true
    try {
      await apiClient.patch(`/master/partners/${row.id}/toggle-active`, {
        isActive: !row.isActive,
        version: row.version,
      })
      ElMessage.success(
        isDeactivating
          ? t('master.partner.deactivateSuccess')
          : t('master.partner.activateSuccess')
      )
      await fetchList()
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 422) {
        const errorCode = error.response.data?.errorCode
        if (errorCode === 'CANNOT_DEACTIVATE_HAS_ACTIVE_OUTBOUND') {
          ElMessage.error(t('master.partner.cannotDeactivateHasActiveOutbound'))
        } else {
          ElMessage.error(t('master.partner.cannotDeactivateHasActiveInbound'))
        }
      } else if (error.response.status === 409) {
        ElMessage.error(t('error.optimisticLock'))
      }
      // 403/500 はインターセプターが処理済み
    } finally {
      loading.value = false
    }
  }

  return {
    items,
    loading,
    total,
    page,
    pageSize,
    searchForm,
    fetchList,
    handleSearch,
    handleReset,
    handlePageChange,
    handleSizeChange,
    handleToggleActive,
  }
}
