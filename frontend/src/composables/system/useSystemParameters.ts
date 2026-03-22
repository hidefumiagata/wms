import { ref, computed, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import type { SystemParameterDetail } from '@/api/generated/models/system-parameter-detail'
import { SystemParameterValueType } from '@/api/generated/models/system-parameter-value-type'
import { SystemParameterCategory } from '@/api/generated/models/system-parameter-category'

/** カテゴリごとにグルーピングされたパラメータ */
export interface ParameterGroup {
  category: string
  collapsed: boolean
  items: ParameterRow[]
}

/** 各パラメータ行（編集状態を含む） */
export interface ParameterRow {
  original: SystemParameterDetail
  editValue: string
  saving: boolean
}

export function useSystemParameters() {
  const { t } = useI18n()

  const groups = ref<ParameterGroup[]>([])
  const loading = ref(false)

  let abortController: AbortController | null = null

  onUnmounted(() => {
    abortController?.abort()
  })

  async function fetchParameters() {
    abortController?.abort()
    abortController = new AbortController()
    const signal = abortController.signal

    loading.value = true
    try {
      const res = await apiClient.get<SystemParameterDetail[]>('/system/parameters', { signal })
      const data = res.data

      // カテゴリ別にグルーピング（APIレスポンスはcategory→display_order順）
      const groupMap = new Map<string, ParameterRow[]>()
      for (const param of data) {
        const cat = param.category ?? 'OTHER'
        if (!groupMap.has(cat)) {
          groupMap.set(cat, [])
        }
        groupMap.get(cat)!.push({
          original: param,
          editValue: param.paramValue ?? '',
          saving: false,
        })
      }

      groups.value = Array.from(groupMap.entries()).map(([category, items]) => ({
        category,
        collapsed: false,
        items,
      }))
    } catch (err: unknown) {
      if (axios.isCancel(err)) return
      groups.value = []
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else {
        ElMessage.error(t('system.parameters.fetchError'))
      }
    } finally {
      if (!signal.aborted) {
        loading.value = false
      }
    }
  }

  function toggleCategory(group: ParameterGroup) {
    group.collapsed = !group.collapsed
  }

  const isDirty = (row: ParameterRow) => row.editValue !== (row.original.paramValue ?? '')

  function validateValue(row: ParameterRow): string | null {
    const { valueType } = row.original
    const val = row.editValue.trim()

    if (!val) return t('system.parameters.validation.required')

    if (valueType === SystemParameterValueType.Integer) {
      if (!/^(0|[1-9][0-9]*)$/.test(val)) {
        return t('system.parameters.validation.integerFormat')
      }
    } else if (valueType === SystemParameterValueType.String) {
      if (val.length > 500) {
        return t('system.parameters.validation.stringMaxLength')
      }
    }
    return null
  }

  async function handleSave(row: ParameterRow) {
    const validationError = validateValue(row)
    if (validationError) {
      ElMessage.error(validationError)
      return
    }

    const confirmMsg = t('system.parameters.confirmSave', {
      name: row.original.displayName,
      value: row.editValue,
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

    row.saving = true
    try {
      const res = await apiClient.put<SystemParameterDetail>(
        `/system/parameters/${row.original.paramKey}`,
        {
          paramValue: row.editValue.trim(),
          version: row.original.version,
        },
      )
      // 更新後のデータで行を更新
      row.original = res.data
      row.editValue = res.data.paramValue ?? ''
      ElMessage.success(t('system.parameters.saveSuccess'))
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 400) {
        ElMessage.error(t('system.parameters.validationError'))
      } else if (error.response.status === 409) {
        ElMessage.error(t('error.optimisticLock'))
      } else {
        ElMessage.error(t('system.parameters.saveError'))
      }
    } finally {
      row.saving = false
    }
  }

  /** カテゴリラベル */
  const categoryLabels = computed(() => {
    const map: Record<string, string> = {
      [SystemParameterCategory.Inventory]: t('system.parameters.categoryInventory'),
      [SystemParameterCategory.Outbound]: t('system.parameters.categoryOutbound'),
      [SystemParameterCategory.Inbound]: t('system.parameters.categoryInbound'),
      [SystemParameterCategory.System]: t('system.parameters.categorySystem'),
      [SystemParameterCategory.Security]: t('system.parameters.categorySecurity'),
    }
    return map
  })

  return {
    groups,
    loading,
    fetchParameters,
    toggleCategory,
    isDirty,
    validateValue,
    handleSave,
    categoryLabels,
  }
}
