import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, flushPromises } from '../../helpers'
import { useInterfaceFiles } from '@/composables/interface/useInterfaceFiles'
import { useWarehouseStore } from '@/stores/warehouse'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import type { InterfaceValidationResult } from '@/api/generated/models/interface-validation-result'

describe('useInterfaceFiles', () => {
  const createFileListResponse = () => ({
    files: [
      { fileName: 'INB-PLAN-001.csv', fileSize: 12288, createdAt: '2026-03-18T09:30:15+09:00' },
      { fileName: 'INB-PLAN-002.csv', fileSize: 8192, createdAt: '2026-03-18T10:15:22+09:00' },
    ],
    totalCount: 2,
  })

  const createValidationResult = () => ({
    fileName: 'INB-PLAN-001.csv',
    totalRows: 10,
    successCount: 9,
    errorCount: 1,
    rows: [
      {
        rowNumber: 5,
        errors: [{ column: 'partner_code', errorCode: 'WMS-E-IFX-301', message: '取引先コードが存在しません' }],
      },
    ],
  })

  const createImportResult = () => ({
    successCount: 9,
    errorCount: 1,
    mode: 'SUCCESS_ONLY',
    status: 'COMPLETED',
  })

  function setupWarehouse() {
    const warehouseStore = useWarehouseStore()
    warehouseStore.selectedWarehouseId = 1
  }

  beforeEach(() => {
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(createFileListResponse()))
    vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse(createImportResult()))
  })

  // --- fetchFiles ---
  it('fetchFiles がIFX-001でAPIを呼び出しファイル一覧を取得する', async () => {
    const { result } = withSetup(() => useInterfaceFiles())

    await result.fetchFiles()

    expect(apiClient.get).toHaveBeenCalledWith(
      '/interface/IFX-001/files',
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
    expect(result.files.value).toHaveLength(2)
    expect(result.totalCount.value).toBe(2)
  })

  it('fetchFiles でfiles/totalCountがnullの場合デフォルト値を使う', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce(
      mockAxiosResponse({ files: null, totalCount: null }),
    )
    const { result } = withSetup(() => useInterfaceFiles())
    await result.fetchFiles()
    expect(result.files.value).toEqual([])
    expect(result.totalCount.value).toBe(0)
  })

  it('fetchFiles がsignalを渡す（AbortController対応）', async () => {
    const { result } = withSetup(() => useInterfaceFiles())

    await result.fetchFiles()

    const callArgs = vi.mocked(apiClient.get).mock.calls[0]
    expect(callArgs[1]).toHaveProperty('signal')
    expect(callArgs[1]!.signal).toBeInstanceOf(AbortSignal)
  })

  it('onUnmounted時にリクエストがキャンセルされる', async () => {
    const { result, wrapper } = withSetup(() => useInterfaceFiles())

    const fetchPromise = result.fetchFiles()
    const signal = vi.mocked(apiClient.get).mock.calls[0][1]!.signal!
    expect(signal.aborted).toBe(false)

    wrapper.unmount()
    expect(signal.aborted).toBe(true)

    await fetchPromise
  })

  it('キャンセル時にstateが更新されない', async () => {
    const { result } = withSetup(() => useInterfaceFiles())

    await result.fetchFiles()
    expect(result.files.value).toHaveLength(2)

    const cancelError = new Error('canceled')
    vi.mocked(apiClient.get).mockRejectedValueOnce(cancelError)
    vi.mocked(axios.isCancel).mockReturnValueOnce(true)

    await result.fetchFiles()
    expect(result.files.value).toHaveLength(2)
  })

  it('ネットワークエラー時にerror.networkメッセージを表示する', async () => {
    vi.mocked(apiClient.get).mockReset()
    const networkError = new Error('Network Error')
    vi.mocked(apiClient.get).mockRejectedValue(networkError)

    const { result } = withSetup(() => useInterfaceFiles())
    await result.fetchFiles()

    expect(result.files.value).toEqual([])
    expect(result.totalCount.value).toBe(0)
    expect(ElMessage.error).toHaveBeenCalledWith('error.network')
  })

  it('APIエラー時にfetchErrorメッセージを表示する', async () => {
    vi.mocked(apiClient.get).mockReset()
    const apiError = Object.assign(new Error('Request failed'), {
      isAxiosError: true,
      response: { status: 500, data: {} },
    })
    vi.mocked(apiClient.get).mockRejectedValue(apiError)

    const { result } = withSetup(() => useInterfaceFiles())
    await result.fetchFiles()

    expect(result.files.value).toEqual([])
    expect(ElMessage.error).toHaveBeenCalledWith('interfaceFiles.fetchError')
  })

  // --- handleTabChange ---
  it('handleTabChange がタブを変更してfetchFilesを呼ぶ', async () => {
    const { result } = withSetup(() => useInterfaceFiles())

    result.handleTabChange('IFX-002')
    await flushPromises()

    expect(result.activeTab.value).toBe('IFX-002')
    expect(apiClient.get).toHaveBeenCalledWith(
      '/interface/IFX-002/files',
      expect.anything(),
    )
  })

  // --- handleValidate ---
  it('handleValidate がバリデーションAPIを呼び出しダイアログを表示する', async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce(
      mockAxiosResponse(createValidationResult()),
    )

    const { result } = withSetup(() => {
      setupWarehouse()
      return useInterfaceFiles()
    })
    await result.handleValidate('INB-PLAN-001.csv')

    expect(apiClient.post).toHaveBeenCalledWith(
      '/interface/IFX-001/validate',
      { fileName: 'INB-PLAN-001.csv', warehouseId: 1 },
    )
    expect(result.validationDialogVisible.value).toBe(true)
    expect(result.validationResult.value).toBeTruthy()
    expect(result.validatingFile.value).toBeNull()
  })

  it('handleValidate でwarehouseIdがnullの場合は何もしない', async () => {
    const { result } = withSetup(() => useInterfaceFiles())
    await result.handleValidate('INB-PLAN-001.csv')
    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('handleValidate でFILE_SIZE_EXCEEDEDエラー時に専用メッセージを表示する', async () => {
    const apiError = Object.assign(new Error('422'), {
      isAxiosError: true,
      response: { status: 422, data: { errorCode: 'FILE_SIZE_EXCEEDED', message: 'too large' } },
    })
    vi.mocked(apiClient.post).mockRejectedValueOnce(apiError)

    const { result } = withSetup(() => {
      setupWarehouse()
      return useInterfaceFiles()
    })
    await result.handleValidate('large.csv')

    expect(ElMessage.error).toHaveBeenCalledWith('interfaceFiles.fileSizeExceeded')
    expect(result.validatingFile.value).toBeNull()
  })

  it('handleValidate でネットワークエラー時にerror.networkメッセージを表示する', async () => {
    const networkError = new Error('Network Error')
    vi.mocked(apiClient.post).mockRejectedValueOnce(networkError)

    const { result } = withSetup(() => {
      setupWarehouse()
      return useInterfaceFiles()
    })
    await result.handleValidate('INB-PLAN-001.csv')

    expect(ElMessage.error).toHaveBeenCalledWith('error.network')
  })

  it('handleValidate でその他APIエラー時にvalidateErrorメッセージを表示する', async () => {
    const apiError = Object.assign(new Error('500'), {
      isAxiosError: true,
      response: { status: 500, data: {} },
    })
    vi.mocked(apiClient.post).mockRejectedValueOnce(apiError)

    const { result } = withSetup(() => {
      setupWarehouse()
      return useInterfaceFiles()
    })
    await result.handleValidate('INB-PLAN-001.csv')

    expect(ElMessage.error).toHaveBeenCalledWith('interfaceFiles.validateError')
  })

  // --- handleImportFromList (opens import mode dialog) ---
  // handleImportFromList のテストは importModeDialog セクションに統合

  // --- handleImportSuccessOnly ---
  it('handleImportSuccessOnly が確認ダイアログ後にSUCCESS_ONLYで取り込みを実行する', async () => {
    vi.mocked(ElMessageBox.confirm).mockResolvedValueOnce('confirm')
    vi.mocked(apiClient.post).mockResolvedValueOnce(
      mockAxiosResponse({ successCount: 9, errorCount: 1, mode: 'SUCCESS_ONLY', status: 'COMPLETED' }),
    )

    const { result } = withSetup(() => {
      setupWarehouse()
      return useInterfaceFiles()
    })
    result.validationResult.value = createValidationResult() as InterfaceValidationResult
    result.validationDialogVisible.value = true

    await result.handleImportSuccessOnly()

    expect(apiClient.post).toHaveBeenCalledWith(
      '/interface/IFX-001/import',
      { fileName: 'INB-PLAN-001.csv', warehouseId: 1, mode: 'SUCCESS_ONLY' },
    )
    expect(result.validationDialogVisible.value).toBe(false)
  })

  it('handleImportSuccessOnly でwarehouseIdがnullの場合は何もしない', async () => {
    const { result } = withSetup(() => useInterfaceFiles())
    result.validationResult.value = createValidationResult() as InterfaceValidationResult

    await result.handleImportSuccessOnly()

    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('handleImportSuccessOnly でvalidationResultがnullの場合は何もしない', async () => {
    const { result } = withSetup(() => useInterfaceFiles())
    result.validationResult.value = null

    await result.handleImportSuccessOnly()

    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('handleImportSuccessOnly でキャンセル時はダイアログを閉じない', async () => {
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')

    const { result } = withSetup(() => {
      setupWarehouse()
      return useInterfaceFiles()
    })
    result.validationResult.value = createValidationResult() as InterfaceValidationResult
    result.validationDialogVisible.value = true

    await result.handleImportSuccessOnly()

    expect(apiClient.post).not.toHaveBeenCalled()
    expect(result.validationDialogVisible.value).toBe(true)
  })

  // --- handleImportDiscard ---
  it('handleImportDiscard が確認ダイアログ後にDISCARDで取り込みを実行する', async () => {
    vi.mocked(ElMessageBox.confirm).mockResolvedValueOnce('confirm')
    vi.mocked(apiClient.post).mockResolvedValueOnce(
      mockAxiosResponse({ successCount: 0, errorCount: 10, mode: 'DISCARD', status: 'DISCARDED' }),
    )

    const { result } = withSetup(() => {
      setupWarehouse()
      return useInterfaceFiles()
    })
    result.validationResult.value = createValidationResult() as InterfaceValidationResult
    result.validationDialogVisible.value = true

    await result.handleImportDiscard()

    expect(apiClient.post).toHaveBeenCalledWith(
      '/interface/IFX-001/import',
      { fileName: 'INB-PLAN-001.csv', warehouseId: 1, mode: 'DISCARD' },
    )
    expect(ElMessage.success).toHaveBeenCalledWith('interfaceFiles.importSuccessDiscarded')
    expect(result.validationDialogVisible.value).toBe(false)
  })

  it('handleImportDiscard でwarehouseIdがnullの場合は何もしない', async () => {
    const { result } = withSetup(() => useInterfaceFiles())
    result.validationResult.value = createValidationResult() as InterfaceValidationResult

    await result.handleImportDiscard()

    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('handleImportDiscard でvalidationResultがnullの場合は何もしない', async () => {
    const { result } = withSetup(() => useInterfaceFiles())
    result.validationResult.value = null

    await result.handleImportDiscard()

    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('handleImportDiscard でキャンセル時はダイアログを閉じない', async () => {
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')

    const { result } = withSetup(() => {
      setupWarehouse()
      return useInterfaceFiles()
    })
    result.validationResult.value = createValidationResult() as InterfaceValidationResult
    result.validationDialogVisible.value = true

    await result.handleImportDiscard()

    expect(apiClient.post).not.toHaveBeenCalled()
    expect(result.validationDialogVisible.value).toBe(true)
  })

  // --- closeValidationDialog ---
  it('closeValidationDialog がダイアログ状態をリセットする', () => {
    const { result } = withSetup(() => useInterfaceFiles())

    result.validationDialogVisible.value = true
    result.validationResult.value = createValidationResult() as InterfaceValidationResult

    result.closeValidationDialog()

    expect(result.validationDialogVisible.value).toBe(false)
    expect(result.validationResult.value).toBeNull()
  })

  // --- formatFileSize ---
  it('formatFileSize がバイト・KB・MBを正しくフォーマットする', () => {
    const { result } = withSetup(() => useInterfaceFiles())

    expect(result.formatFileSize(500)).toBe('500 B')
    expect(result.formatFileSize(1024)).toBe('1.0 KB')
    expect(result.formatFileSize(12288)).toBe('12.0 KB')
    expect(result.formatFileSize(1048576)).toBe('1.0 MB')
    expect(result.formatFileSize(52428800)).toBe('50.0 MB')
  })

  // --- formatDateTime ---
  it('formatDateTime が日時文字列をフォーマットする', () => {
    const { result } = withSetup(() => useInterfaceFiles())
    const formatted = result.formatDateTime('2026-03-18T09:30:15+09:00')
    expect(formatted).toBeTruthy()
    expect(formatted).toContain('2026')
  })

  it('formatDateTime が空文字の場合空文字を返す', () => {
    const { result } = withSetup(() => useInterfaceFiles())
    expect(result.formatDateTime('')).toBe('')
  })

  // --- importModeDialog ---
  it('openImportModeDialog がダイアログを開く（warehouseIdあり）', () => {
    const { result } = withSetup(() => {
      setupWarehouse()
      return useInterfaceFiles()
    })
    result.handleImportFromList('INB-PLAN-001.csv')
    expect(result.importModeDialogVisible.value).toBe(true)
    expect(result.importModeFileName.value).toBe('INB-PLAN-001.csv')
    expect(result.importModeSelected.value).toBe('SUCCESS_ONLY')
  })

  it('openImportModeDialog でwarehouseIdがnullの場合はダイアログを開かない', () => {
    const { result } = withSetup(() => useInterfaceFiles())
    result.handleImportFromList('INB-PLAN-001.csv')
    expect(result.importModeDialogVisible.value).toBe(false)
  })

  it('confirmImportMode が選択されたモードで取り込みを実行する', async () => {
    const { result } = withSetup(() => {
      setupWarehouse()
      return useInterfaceFiles()
    })
    result.importModeFileName.value = 'INB-PLAN-001.csv'
    result.importModeSelected.value = 'DISCARD'
    result.importModeDialogVisible.value = true

    vi.mocked(apiClient.post).mockResolvedValueOnce(
      mockAxiosResponse({ successCount: 0, errorCount: 10, mode: 'DISCARD', status: 'DISCARDED' }),
    )
    await result.confirmImportMode()

    expect(result.importModeDialogVisible.value).toBe(false)
    expect(apiClient.post).toHaveBeenCalledWith(
      '/interface/IFX-001/import',
      { fileName: 'INB-PLAN-001.csv', warehouseId: 1, mode: 'DISCARD' },
    )
  })

  it('confirmImportMode でAPIエラー時にimportErrorメッセージを表示する', async () => {
    const apiError = Object.assign(new Error('500'), {
      isAxiosError: true,
      response: { status: 500, data: {} },
    })
    vi.mocked(apiClient.post).mockRejectedValueOnce(apiError)

    const { result } = withSetup(() => {
      setupWarehouse()
      return useInterfaceFiles()
    })
    result.importModeFileName.value = 'INB-PLAN-001.csv'
    result.importModeSelected.value = 'SUCCESS_ONLY'
    await result.confirmImportMode()

    expect(ElMessage.error).toHaveBeenCalledWith('interfaceFiles.importError')
  })

  it('confirmImportMode でネットワークエラー時にerror.networkメッセージを表示する', async () => {
    vi.mocked(apiClient.post).mockRejectedValueOnce(new Error('Network Error'))

    const { result } = withSetup(() => {
      setupWarehouse()
      return useInterfaceFiles()
    })
    result.importModeFileName.value = 'INB-PLAN-001.csv'
    result.importModeSelected.value = 'SUCCESS_ONLY'
    await result.confirmImportMode()

    expect(ElMessage.error).toHaveBeenCalledWith('error.network')
  })

  it('confirmImportMode でwarehouseIdがnullの場合は何もしない', async () => {
    const { result } = withSetup(() => useInterfaceFiles())
    result.importModeFileName.value = 'INB-PLAN-001.csv'
    await result.confirmImportMode()
    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('cancelImportMode がダイアログを閉じる', () => {
    const { result } = withSetup(() => useInterfaceFiles())
    result.importModeDialogVisible.value = true
    result.cancelImportMode()
    expect(result.importModeDialogVisible.value).toBe(false)
  })
})
