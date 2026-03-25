import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { withSetup, mockAxiosResponse, createCancelError } from '../../helpers'
import { useProductList } from '@/composables/master/useProductList'
import { ElMessage } from 'element-plus'
import axios from 'axios'

vi.mock('axios', async (importOriginal) => {
  const actual = await importOriginal<typeof import('axios')>()
  return {
    ...actual,
    default: {
      ...actual.default,
      isCancel: vi.fn((err: unknown) => {
        return (err as { __CANCEL__?: boolean })?.__CANCEL__ === true
      }),
    },
  }
})

// generated model のモック
vi.mock('@/api/generated/models/product-detail', () => ({}))
vi.mock('@/api/generated/models/product-page-response', () => ({}))
vi.mock('@/api/generated/models/storage-condition', () => ({
  StorageCondition: {
    Ambient: 'AMBIENT',
    Refrigerated: 'REFRIGERATED',
    Frozen: 'FROZEN',
  },
}))

describe('useProductList', () => {
  const mockPageResponse = {
    content: [
      { id: 1, productCode: 'PRD001', productName: 'テスト商品', isActive: true, version: 1 },
    ],
    totalElements: 1,
    totalPages: 1,
  }

  beforeEach(() => {
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(mockPageResponse))
  })

  it('fetchList がデータを取得する', async () => {
    const { result } = withSetup(() => useProductList())

    await result.fetchList()

    expect(result.items.value).toEqual(mockPageResponse.content)
    expect(result.total.value).toBe(1)
    expect(result.loading.value).toBe(false)
  })

  it('fetchList が signal を渡す（AbortController対応）', async () => {
    const { result } = withSetup(() => useProductList())

    await result.fetchList()

    const callArgs = vi.mocked(apiClient.get).mock.calls[0]
    expect(callArgs[1]).toHaveProperty('signal')
    expect(callArgs[1]!.signal).toBeInstanceOf(AbortSignal)
  })

  it('連続呼び出しで前のリクエストがキャンセルされる', async () => {
    const { result } = withSetup(() => useProductList())

    const abortSpy = vi.spyOn(AbortController.prototype, 'abort')
    await result.fetchList()
    await result.fetchList()

    expect(abortSpy).toHaveBeenCalled()
    abortSpy.mockRestore()
  })

  it('onUnmounted 時にリクエストがキャンセルされる', async () => {
    const { result, wrapper } = withSetup(() => useProductList())

    const fetchPromise = result.fetchList()
    const abortSpy = vi.spyOn(AbortController.prototype, 'abort')

    wrapper.unmount()

    expect(abortSpy).toHaveBeenCalled()
    abortSpy.mockRestore()

    await fetchPromise
  })

  it('キャンセル時に state が更新されない', async () => {
    const { result } = withSetup(() => useProductList())

    await result.fetchList()
    expect(result.items.value).toHaveLength(1)

    vi.mocked(apiClient.get).mockRejectedValueOnce(createCancelError())
    vi.mocked(axios.isCancel).mockReturnValueOnce(true)

    await result.fetchList()

    expect(result.items.value).toHaveLength(1)
  })

  it('デフォルトの検索条件で isActive が true', () => {
    const { result } = withSetup(() => useProductList())

    expect(result.searchForm.isActive).toBe(true)
  })

  it('handleReset で isActive がデフォルト(true)に戻る', async () => {
    const { result } = withSetup(() => useProductList())

    result.searchForm.isActive = false
    await result.handleReset()

    expect(result.searchForm.isActive).toBe(true)
  })

  it('storageConditionLabel が正しいラベルを返す', () => {
    const { result } = withSetup(() => useProductList())

    // i18n はキーをそのまま返すので、翻訳キーが返ることを確認
    const label = result.storageConditionLabel('AMBIENT')
    expect(label).toBe('master.product.storageAmbient')
  })
})
