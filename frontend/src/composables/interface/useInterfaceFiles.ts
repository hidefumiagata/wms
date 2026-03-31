import { ref, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import { useWarehouseStore } from '@/stores/warehouse'
import type { InterfaceFileInfo } from '@/api/generated/models/interface-file-info'
import type { InterfaceFileListResponse } from '@/api/generated/models/interface-file-list-response'
import type { InterfaceValidationResult } from '@/api/generated/models/interface-validation-result'
import type { InterfaceImportResult } from '@/api/generated/models/interface-import-result'

export type TabType = 'IFX-001' | 'IFX-002'

export function useInterfaceFiles() {
  const { t } = useI18n()
  const warehouseStore = useWarehouseStore()

  // --- 状態 ---
  const activeTab = ref<TabType>('IFX-001')
  const files = ref<InterfaceFileInfo[]>([])
  const totalCount = ref(0)
  const loading = ref(false)
  const validatingFile = ref<string | null>(null)
  const importingFile = ref<string | null>(null)

  // バリデーション結果ダイアログ
  const validationDialogVisible = ref(false)
  const validationResult = ref<InterfaceValidationResult | null>(null)
  const validationImporting = ref(false)

  // --- 並行リクエスト制御 ---
  let abortController: AbortController | null = null
  onUnmounted(() => {
    abortController?.abort()
  })

  // --- ファイル一覧取得 ---
  async function fetchFiles() {
    abortController?.abort()
    abortController = new AbortController()
    const signal = abortController.signal

    loading.value = true
    try {
      const res = await apiClient.get<InterfaceFileListResponse>(
        `/interface/${activeTab.value}/files`,
        { signal },
      )
      files.value = res.data.files ?? []
      totalCount.value = res.data.totalCount ?? 0
    } catch (err: unknown) {
      if (axios.isCancel(err)) return
      files.value = []
      totalCount.value = 0
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else {
        ElMessage.error(t('interfaceFiles.fetchError'))
      }
    } finally {
      if (!signal.aborted) {
        loading.value = false
      }
    }
  }

  // --- タブ切替 ---
  function handleTabChange(tab: TabType) {
    activeTab.value = tab
    fetchFiles()
  }

  // --- バリデーション実行 ---
  async function handleValidate(fileName: string) {
    const warehouseId = warehouseStore.selectedWarehouseId
    if (!warehouseId) return

    validatingFile.value = fileName
    try {
      const res = await apiClient.post<InterfaceValidationResult>(
        `/interface/${activeTab.value}/validate`,
        { fileName, warehouseId },
      )
      validationResult.value = res.data
      validationDialogVisible.value = true
    } catch (err: unknown) {
      const error = toApiError(err)
      if (error.response?.status === 422 && error.response.data?.errorCode === 'FILE_SIZE_EXCEEDED') {
        ElMessage.error(t('interfaceFiles.fileSizeExceeded'))
      } else if (!error.response) {
        ElMessage.error(t('error.network'))
      } else {
        ElMessage.error(t('interfaceFiles.validateError'))
      }
    } finally {
      validatingFile.value = null
    }
  }

  // --- IF-001 直接取り込み（モード選択ダイアログ付き） ---
  const importModeDialogVisible = ref(false)
  const importModeFileName = ref('')
  const importModeSelected = ref<string>('SUCCESS_ONLY')

  function openImportModeDialog(fileName: string) {
    const warehouseId = warehouseStore.selectedWarehouseId
    if (!warehouseId) return
    importModeFileName.value = fileName
    importModeSelected.value = 'SUCCESS_ONLY'
    importModeDialogVisible.value = true
  }

  async function confirmImportMode() {
    const warehouseId = warehouseStore.selectedWarehouseId
    if (!warehouseId) return
    importModeDialogVisible.value = false
    await executeImport(importModeFileName.value, warehouseId, importModeSelected.value)
  }

  function cancelImportMode() {
    importModeDialogVisible.value = false
  }

  async function handleImportFromList(fileName: string) {
    openImportModeDialog(fileName)
  }

  // --- IF-002 バリデーション結果ダイアログからの取り込み ---
  async function handleImportSuccessOnly() {
    if (!validationResult.value) return
    const warehouseId = warehouseStore.selectedWarehouseId
    if (!warehouseId) return
    const fileName = validationResult.value.fileName

    try {
      await ElMessageBox.confirm(
        t('validationResult.confirmSuccessOnly', {
          fileName,
          success: validationResult.value.successCount,
          error: validationResult.value.errorCount,
        }),
        t('common.confirm'),
        { confirmButtonText: t('interfaceFiles.execute'), cancelButtonText: t('common.cancel'), type: 'warning' },
      )
      validationImporting.value = true
      await executeImport(fileName, warehouseId, 'SUCCESS_ONLY')
      closeValidationDialog()
    } catch {
      // cancelled
    } finally {
      validationImporting.value = false
    }
  }

  async function handleImportDiscard() {
    if (!validationResult.value) return
    const warehouseId = warehouseStore.selectedWarehouseId
    if (!warehouseId) return
    const fileName = validationResult.value.fileName

    try {
      await ElMessageBox.confirm(
        t('validationResult.confirmDiscard', { fileName }),
        t('common.confirm'),
        { confirmButtonText: t('interfaceFiles.execute'), cancelButtonText: t('common.cancel'), type: 'warning' },
      )
      validationImporting.value = true
      await executeImport(fileName, warehouseId, 'DISCARD')
      closeValidationDialog()
    } catch {
      // cancelled
    } finally {
      validationImporting.value = false
    }
  }

  // --- 取り込み実行 ---
  async function executeImport(fileName: string, warehouseId: number, mode: string) {
    importingFile.value = fileName
    try {
      const res = await apiClient.post<InterfaceImportResult>(
        `/interface/${activeTab.value}/import`,
        { fileName, warehouseId, mode },
      )
      if (mode === 'DISCARD') {
        ElMessage.success(t('interfaceFiles.importSuccessDiscarded', { fileName }))
      } else {
        ElMessage.success(
          t('interfaceFiles.importSuccessCompleted', {
            fileName,
            success: res.data.successCount,
            error: res.data.errorCount,
          }),
        )
      }
      fetchFiles()
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else {
        ElMessage.error(t('interfaceFiles.importError'))
      }
    } finally {
      importingFile.value = null
    }
  }

  function closeValidationDialog() {
    validationDialogVisible.value = false
    validationResult.value = null
  }

  // --- フォーマットヘルパー ---
  function formatDateTime(dateStr: string): string {
    if (!dateStr) return ''
    const d = new Date(dateStr)
    return d.toLocaleString('ja-JP', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })
  }

  function formatFileSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  }

  return {
    activeTab,
    files,
    totalCount,
    loading,
    validatingFile,
    importingFile,
    validationDialogVisible,
    validationResult,
    validationImporting,
    fetchFiles,
    handleTabChange,
    handleValidate,
    handleImportFromList,
    importModeDialogVisible,
    importModeFileName,
    importModeSelected,
    confirmImportMode,
    cancelImportMode,
    handleImportSuccessOnly,
    handleImportDiscard,
    closeValidationDialog,
    formatDateTime,
    formatFileSize,
  }
}
