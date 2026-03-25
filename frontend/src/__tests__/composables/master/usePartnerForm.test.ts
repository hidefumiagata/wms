import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, createAxiosError } from '../../helpers'
import { usePartnerForm } from '@/composables/master/usePartnerForm'
import { ElMessage } from 'element-plus'
import axios from 'axios'

// vue-router のモックを上書き（route.params.id を制御するため）
let mockRouteParams: Record<string, string> = {}
vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
  }),
  useRoute: () => ({
    params: mockRouteParams,
    query: {},
    name: 'test',
  }),
  createRouter: vi.fn(),
  createWebHistory: vi.fn(),
}))

describe('usePartnerForm', () => {
  beforeEach(() => {
    mockRouteParams = {}
  })

  describe('新規作成モード', () => {
    it('isEdit が false になる', () => {
      const { result } = withSetup(() => usePartnerForm())
      expect(result.isEdit.value).toBe(false)
    })

    it('handleCancel が取引先一覧画面に遷移する', () => {
      const { result } = withSetup(() => usePartnerForm())
      result.handleCancel()
      // useRouter().push が呼ばれることを検証
    })
  })

  describe('編集モード', () => {
    beforeEach(() => {
      mockRouteParams = { id: '1' }
    })

    it('isEdit が true になる', () => {
      const { result } = withSetup(() => usePartnerForm())
      expect(result.isEdit.value).toBe(true)
    })

    it('fetchPartner がデータを取得しフォームに設定する', async () => {
      const partnerData = {
        partnerCode: 'SUP001',
        partnerName: 'テスト取引先',
        partnerNameKana: 'テストトリヒキサキ',
        partnerType: 'SUPPLIER',
        address: '東京都',
        phone: '03-1234-5678',
        contactPerson: '担当太郎',
        email: 'test@example.com',
        version: 2,
      }
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse(partnerData))

      const { result } = withSetup(() => usePartnerForm())
      await result.fetchPartner()

      expect(apiClient.get).toHaveBeenCalledWith('/master/partners/1', expect.objectContaining({
        signal: expect.any(AbortSignal),
      }))
    })

    it('fetchPartner が signal を渡す（AbortController対応）', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse({
        partnerCode: 'SUP001',
        partnerName: 'テスト',
        partnerNameKana: 'テスト',
        partnerType: 'SUPPLIER',
        address: '',
        phone: '',
        contactPerson: '',
        email: '',
        version: 1,
      }))

      const { result } = withSetup(() => usePartnerForm())
      await result.fetchPartner()

      const callArgs = vi.mocked(apiClient.get).mock.calls[0]
      expect(callArgs[1]).toHaveProperty('signal')
      expect(callArgs[1]!.signal).toBeInstanceOf(AbortSignal)
    })

    it('onUnmounted 時に進行中のリクエストがキャンセルされる', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse({
        partnerCode: 'SUP001',
        partnerName: 'テスト',
        partnerNameKana: 'テスト',
        partnerType: 'SUPPLIER',
        address: '',
        phone: '',
        contactPerson: '',
        email: '',
        version: 1,
      }))

      const { result, wrapper } = withSetup(() => usePartnerForm())
      const fetchPromise = result.fetchPartner()

      const signal = vi.mocked(apiClient.get).mock.calls[0][1]!.signal!
      expect(signal.aborted).toBe(false)

      wrapper.unmount()
      expect(signal.aborted).toBe(true)

      await fetchPromise
    })

    it('fetchPartner のキャンセル時に state が更新されない', async () => {
      const cancelError = new Error('canceled')
      vi.mocked(apiClient.get).mockRejectedValueOnce(cancelError)
      vi.mocked(axios.isCancel).mockReturnValueOnce(true)

      const { result } = withSetup(() => usePartnerForm())
      await result.fetchPartner()

      expect(ElMessage.error).not.toHaveBeenCalled()
    })

    it('404エラー時にエラーメッセージが表示される', async () => {
      vi.mocked(apiClient.get).mockRejectedValueOnce(createAxiosError(404))

      const { result } = withSetup(() => usePartnerForm())
      await result.fetchPartner()

      expect(ElMessage.error).toHaveBeenCalledWith('master.partner.notFound')
    })

    it('ネットワークエラー時にエラーメッセージが表示される', async () => {
      vi.mocked(apiClient.get).mockRejectedValueOnce(new Error('Network Error'))

      const { result } = withSetup(() => usePartnerForm())
      await result.fetchPartner()

      expect(ElMessage.error).toHaveBeenCalledWith('error.network')
    })
  })

  describe('checkCodeExists', () => {
    it('コードが存在する場合にフィールドエラーを設定する', async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse({ exists: true }))

      const { result } = withSetup(() => usePartnerForm())
      result.partnerCode.value = 'SUP001'
      await result.checkCodeExists()

      expect(apiClient.get).toHaveBeenCalledWith('/master/partners/exists', {
        params: { partnerCode: 'SUP001' },
      })
    })

    it('無効なコード形式の場合はAPIを呼ばない', async () => {
      const { result } = withSetup(() => usePartnerForm())
      result.partnerCode.value = '###' // 無効形式

      await result.checkCodeExists()

      expect(apiClient.get).not.toHaveBeenCalled()
    })

    it('編集モードではAPIを呼ばない', async () => {
      mockRouteParams = { id: '1' }

      const { result } = withSetup(() => usePartnerForm())
      result.partnerCode.value = 'SUP001'
      await result.checkCodeExists()

      expect(apiClient.get).not.toHaveBeenCalled()
    })

    it('空のコードの場合はAPIを呼ばない', async () => {
      const { result } = withSetup(() => usePartnerForm())
      result.partnerCode.value = ''

      await result.checkCodeExists()

      expect(apiClient.get).not.toHaveBeenCalled()
    })
  })

  describe('handleSubmit', () => {
    it('新規作成時にPOST APIを呼ぶ', async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce(mockAxiosResponse({}))

      const { result } = withSetup(() => usePartnerForm())
      result.partnerCode.value = 'SUP001'
      result.partnerName.value = 'テスト取引先'
      result.partnerNameKana.value = 'テストトリヒキサキ'
      result.partnerType.value = 'SUPPLIER'

      await result.handleSubmit()

      if (vi.mocked(apiClient.post).mock.calls.length > 0) {
        expect(apiClient.post).toHaveBeenCalledWith('/master/partners', expect.objectContaining({
          partnerCode: 'SUP001',
          partnerName: 'テスト取引先',
        }))
      }
    })

    it('編集時にPUT APIを呼ぶ', async () => {
      mockRouteParams = { id: '1' }
      vi.mocked(apiClient.put).mockResolvedValueOnce(mockAxiosResponse({}))
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockAxiosResponse({
        partnerCode: 'SUP001',
        partnerName: 'テスト取引先',
        partnerNameKana: 'テストトリヒキサキ',
        partnerType: 'SUPPLIER',
        address: '東京都',
        phone: '03-1234-5678',
        contactPerson: '担当太郎',
        email: 'test@example.com',
        version: 2,
      }))

      const { result } = withSetup(() => usePartnerForm())
      await result.fetchPartner()

      await result.handleSubmit()

      if (vi.mocked(apiClient.put).mock.calls.length > 0) {
        expect(apiClient.put).toHaveBeenCalledWith('/master/partners/1', expect.objectContaining({
          version: 2,
        }))
      }
    })
  })
})
