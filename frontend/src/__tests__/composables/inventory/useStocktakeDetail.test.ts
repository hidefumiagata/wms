import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { withSetup, mockAxiosResponse, flushPromises } from '../../helpers'
import { useStocktakeDetail } from '@/composables/inventory/useStocktakeDetail'
import { mockRoute, mockRouter } from '../../setup'
import axios from 'axios'

vi.mock('@/api/generated/models/stocktake-detail', () => ({}))
vi.mock('@/api/generated/models/stocktake-line-item', () => ({}))
vi.mock('@/utils/inventoryFormatters', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/utils/inventoryFormatters')>()
  return { ...actual }
})

describe('useStocktakeDetail', () => {
  const mockDetail = {
    id: 1,
    stocktakeNumber: 'ST-001',
    status: 'IN_PROGRESS',
    lines: {
      content: [
        { lineId: 1, locationCode: 'A-01', productCode: 'P001', quantityBefore: 10, quantityCounted: null, isCounted: false, quantityDiff: null },
        { lineId: 2, locationCode: 'A-02', productCode: 'P002', quantityBefore: 5, quantityCounted: 5, isCounted: true, quantityDiff: 0 },
      ],
    },
  }

  beforeEach(() => {
    mockRoute.params = { id: '1' }
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(mockDetail))
  })

  it('fetchDetail が明細を取得して editQty を設定する', async () => {
    const { result } = withSetup(() => useStocktakeDetail())
    await result.fetchDetail()

    expect(result.header.value).toBeTruthy()
    expect(result.lines.value).toHaveLength(2)
    expect(result.lines.value[0].editQty).toBeNull() // uncounted
    expect(result.lines.value[1].editQty).toBe(5) // counted
  })

  it('uncountedCount が未入力件数を返す', async () => {
    const { result } = withSetup(() => useStocktakeDetail())
    await result.fetchDetail()

    expect(result.uncountedCount.value).toBe(1)
    expect(result.allCounted.value).toBe(false)
  })

  it('allCounted が全件入力済みで true', async () => {
    const allCountedDetail = {
      ...mockDetail,
      lines: {
        content: [
          { lineId: 1, locationCode: 'A-01', productCode: 'P001', quantityBefore: 10, quantityCounted: 10, isCounted: true, quantityDiff: 0 },
        ],
      },
    }
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(allCountedDetail))

    const { result } = withSetup(() => useStocktakeDetail())
    await result.fetchDetail()

    expect(result.allCounted.value).toBe(true)
  })

  it('onActualQtyChange が負の数を拒否する', async () => {
    const { result } = withSetup(() => useStocktakeDetail())
    await result.fetchDetail()

    const line = result.lines.value[0]
    line.editQty = -1
    result.onActualQtyChange(line)

    expect(line.editQty).toBeNull()
  })

  it('onActualQtyChange が isDirty を true にする', async () => {
    const { result } = withSetup(() => useStocktakeDetail())
    await result.fetchDetail()

    const line = result.lines.value[0]
    line.editQty = 5
    result.onActualQtyChange(line)

    expect(line.isDirty).toBe(true)
  })

  it('lineStatus が正しいステータスを返す', async () => {
    const { result } = withSetup(() => useStocktakeDetail())
    await result.fetchDetail()

    // uncounted line (editQty=null, isCounted=false)
    expect(result.lineStatus(result.lines.value[0])).toBe('uncounted')

    // matched line (quantityCounted=5, quantityBefore=5)
    expect(result.lineStatus(result.lines.value[1])).toBe('match')
  })

  it('saveLines が PUT リクエストを送信する', async () => {
    vi.mocked(apiClient.put).mockResolvedValue(mockAxiosResponse({}))

    const { result } = withSetup(() => useStocktakeDetail())
    await result.fetchDetail()

    // Set editQty on uncounted line
    result.lines.value[0].editQty = 8
    result.lines.value[0].isDirty = true

    await result.saveLines()

    expect(apiClient.put).toHaveBeenCalledWith('/inventory/stocktakes/1/lines', {
      lines: [{ lineId: 1, actualQty: 8 }],
    })
    expect(ElMessage.success).toHaveBeenCalled()
  })

  it('saveLines が変更なし時にメッセージのみ表示する', async () => {
    const { result } = withSetup(() => useStocktakeDetail())
    await result.fetchDetail()

    await result.saveLines()

    expect(apiClient.put).not.toHaveBeenCalled()
    expect(ElMessage.info).toHaveBeenCalled()
  })

  it('dirty な行があると goBack で確認ダイアログが出ることで hasDirtyLines を間接確認する', async () => {
    const { result } = withSetup(() => useStocktakeDetail())
    await result.fetchDetail()

    // No dirty lines -> no confirm dialog
    await result.goBack()
    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
  })

  it('goToConfirm が未完了時に警告する', async () => {
    const { result } = withSetup(() => useStocktakeDetail())
    await result.fetchDetail()

    result.goToConfirm()

    expect(ElMessage.warning).toHaveBeenCalled()
    expect(mockRouter.push).not.toHaveBeenCalled()
  })

  it('goBack が dirty 時に確認ダイアログを表示する', async () => {
    const { result } = withSetup(() => useStocktakeDetail())
    await result.fetchDetail()

    result.lines.value[0].isDirty = true
    await result.goBack()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'stocktake-list' })
  })

  it('goBack が dirty なし時に直接遷移する', async () => {
    const { result } = withSetup(() => useStocktakeDetail())
    await result.fetchDetail()

    await result.goBack()

    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'stocktake-list' })
  })
})
