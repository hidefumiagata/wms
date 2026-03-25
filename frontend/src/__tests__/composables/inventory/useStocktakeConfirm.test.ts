import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { withSetup, mockAxiosResponse, createAxiosError, flushPromises } from '../../helpers'
import { useStocktakeConfirm } from '@/composables/inventory/useStocktakeConfirm'
import { mockRoute, mockRouter } from '../../setup'
import axios from 'axios'

vi.mock('@/api/generated/models/stocktake-detail', () => ({}))
vi.mock('@/api/generated/models/stocktake-line-item', () => ({}))
vi.mock('@/utils/inventoryFormatters', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/utils/inventoryFormatters')>()
  return { ...actual }
})

describe('useStocktakeConfirm', () => {
  const mockDetail = {
    id: 1,
    stocktakeNumber: 'ST-001',
    status: 'IN_PROGRESS',
    lines: {
      content: [
        { lineId: 1, quantityBefore: 10, quantityCounted: 8, quantityDiff: -2, isCounted: true },
        { lineId: 2, quantityBefore: 5, quantityCounted: 5, quantityDiff: 0, isCounted: true },
        { lineId: 3, quantityBefore: 20, quantityCounted: 25, quantityDiff: 5, isCounted: true },
      ],
    },
  }

  beforeEach(() => {
    mockRoute.params = { id: '1' }
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(mockDetail))
  })

  it('fetchDetail が明細を取得する', async () => {
    const { result } = withSetup(() => useStocktakeConfirm())
    await result.fetchDetail()

    expect(result.header.value).toBeTruthy()
    expect(result.lines.value).toHaveLength(3)
    expect(result.totalCount.value).toBe(3)
  })

  it('diffCount と noDiffCount が正しく計算される', async () => {
    const { result } = withSetup(() => useStocktakeConfirm())
    await result.fetchDetail()

    expect(result.diffCount.value).toBe(2) // -2 and +5
    expect(result.noDiffCount.value).toBe(1) // 0
  })

  it('diffRate が差異率を返す', async () => {
    const { result } = withSetup(() => useStocktakeConfirm())
    await result.fetchDetail()

    expect(result.diffRate(result.lines.value[0])).toBe('-20.0%')
    expect(result.diffRate(result.lines.value[1])).toContain('0.0%')
    expect(result.diffRate(result.lines.value[2])).toBe('+25.0%')
  })

  it('diffRate が quantityBefore=0 で "—" を返す', () => {
    const { result } = withSetup(() => useStocktakeConfirm())
    const line = { quantityBefore: 0, quantityDiff: 5 } as never
    expect(result.diffRate(line)).toBe('—')
  })

  it('formatDiff が差異数をフォーマットする', () => {
    const { result } = withSetup(() => useStocktakeConfirm())

    expect(result.formatDiff(-2)).toBe('-2')
    expect(result.formatDiff(5)).toBe('+5')
    expect(result.formatDiff(0)).toBe('+0')
    expect(result.formatDiff(null)).toBe('—')
    expect(result.formatDiff(undefined)).toBe('—')
  })

  it('showDiffOnly が差異ありのみフィルタする', async () => {
    const { result } = withSetup(() => useStocktakeConfirm())
    await result.fetchDetail()

    expect(result.filteredLines.value).toHaveLength(3)

    result.showDiffOnly.value = true
    expect(result.filteredLines.value).toHaveLength(2)
  })

  it('confirmStocktake が確認後にPOSTする', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse({}))

    const { result } = withSetup(() => useStocktakeConfirm())
    await result.fetchDetail()
    await result.confirmStocktake()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(apiClient.post).toHaveBeenCalledWith('/inventory/stocktakes/1/confirm')
    expect(ElMessage.success).toHaveBeenCalled()
    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'stocktake-list' })
  })

  it('confirmStocktake が409エラーを処理する', async () => {
    vi.mocked(apiClient.post).mockRejectedValue(createAxiosError(409, { message: 'conflict' }))

    const { result } = withSetup(() => useStocktakeConfirm())
    await result.fetchDetail()
    await result.confirmStocktake()

    expect(ElMessage.error).toHaveBeenCalled()
  })

  it('goBackToInput が入力画面に遷移する', () => {
    const { result } = withSetup(() => useStocktakeConfirm())
    result.goBackToInput()

    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'stocktake-detail', params: { id: 1 } })
  })
})
