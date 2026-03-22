import { ref, reactive, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import type { UserDetail } from '@/api/generated/models/user-detail'
import type { UserPageResponse } from '@/api/generated/models/user-page-response'

export function useUserList() {
  const { t } = useI18n()

  const items = ref<UserDetail[]>([])
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)

  const searchForm = reactive({
    keyword: '',
    role: '' as string,
    status: '' as string,
  })

  let abortController: AbortController | null = null

  onUnmounted(() => {
    abortController?.abort()
  })

  async function fetchList() {
    abortController?.abort()
    abortController = new AbortController()
    const signal = abortController.signal

    loading.value = true
    try {
      const params: Record<string, unknown> = {
        page: page.value - 1,
        size: pageSize.value,
        sort: 'createdAt,desc',
      }
      if (searchForm.keyword) params.keyword = searchForm.keyword
      if (searchForm.role) params.role = searchForm.role
      if (searchForm.status) params.status = searchForm.status

      const res = await apiClient.get<UserPageResponse>('/master/users', { params, signal })
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
        ElMessage.error(t('master.user.fetchError'))
      }
    } finally {
      if (!signal.aborted) {
        loading.value = false
      }
    }
  }

  function handleSearch() {
    page.value = 1
    fetchList()
  }

  function handleReset() {
    searchForm.keyword = ''
    searchForm.role = ''
    searchForm.status = ''
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

  async function handleUnlock(row: UserDetail) {
    const confirmMsg = t('master.user.confirmUnlock', {
      name: row.fullName,
      code: row.userCode,
    })

    try {
      await ElMessageBox.confirm(confirmMsg, t('common.confirm'), {
        type: 'warning',
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
      })
    } catch {
      return
    }

    loading.value = true
    try {
      await apiClient.patch(`/master/users/${row.id}/unlock`)
      ElMessage.success(t('master.user.unlockSuccess'))
      await fetchList()
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else {
        ElMessage.error(t('master.user.unlockError'))
      }
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
    handleUnlock,
  }
}
