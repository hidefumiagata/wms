import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { withSetup, mockAxiosResponse, createAxiosError, flushPromises } from '../../helpers'
import { useStocktakeForm } from '@/composables/inventory/useStocktakeForm'
import { useWarehouseStore } from '@/stores/warehouse'
import { mockRouter } from '../../setup'

describe('useStocktakeForm', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse({
      content: [],
      totalElements: 0,
    }))
  })

  it('fetchBuildings が棟マスタを取得する', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse({
      content: [{ id: 1, buildingName: 'A棟' }, { id: 2, buildingName: 'B棟' }],
    }))

    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useStocktakeForm()
    })

    await result.fetchBuildings()

    expect(result.buildingOptions.value).toHaveLength(2)
    expect(result.buildingOptions.value[0].buildingName).toBe('A棟')
  })

  it('onBuildingChange がエリアを取得しロケーション数を更新する', async () => {
    const areaRes = mockAxiosResponse({ content: [{ id: 10, areaName: 'エリア1' }] })
    const locCountRes = mockAxiosResponse({ totalElements: 15 })
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce(areaRes)
      .mockResolvedValueOnce(locCountRes)

    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useStocktakeForm()
    })

    result.selectedBuildingId.value = 1
    await result.onBuildingChange()

    expect(result.areaOptions.value).toHaveLength(1)
    expect(result.targetLocationCount.value).toBe(15)
    expect(result.selectedAreaId.value).toBeNull()
  })

  it('onBuildingChange が未選択時にリセットする', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useStocktakeForm()
    })

    result.selectedBuildingId.value = null
    await result.onBuildingChange()

    expect(result.areaOptions.value).toHaveLength(0)
    expect(result.targetLocationCount.value).toBeNull()
  })

  it('onAreaChange がロケーション数を再取得する', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse({ totalElements: 5 }))

    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useStocktakeForm()
    })

    result.selectedBuildingId.value = 1
    result.selectedAreaId.value = 10
    await result.onAreaChange()

    expect(apiClient.get).toHaveBeenCalledWith('/master/locations', expect.objectContaining({
      params: expect.objectContaining({ buildingId: 1, areaId: 10 }),
    }))
  })

  it('submitStart がバリデーションエラーを出す（棟未選択）', async () => {
    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useStocktakeForm()
    })

    result.selectedBuildingId.value = null
    await result.submitStart()

    expect(ElMessage.error).toHaveBeenCalled()
    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('submitStart が確認後にPOSTリクエストを送信する', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse({ id: 99, stocktakeNumber: 'ST-001' }))

    const { result } = withSetup(() => {
      const ws = useWarehouseStore()
      ws.selectedWarehouseId = 1
      return useStocktakeForm()
    })

    result.selectedBuildingId.value = 1
    // Set date to today to pass validation
    result.stocktakeDate.value = new Date().toISOString().slice(0, 10)
    result.targetLocationCount.value = 5

    await result.submitStart()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(apiClient.post).toHaveBeenCalledWith('/inventory/stocktakes', expect.objectContaining({
      warehouseId: 1,
      buildingId: 1,
    }))
    expect(ElMessage.success).toHaveBeenCalled()
    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'stocktake-detail', params: { id: 99 } })
  })

  it('goBack が一覧画面に遷移する', () => {
    const { result } = withSetup(() => useStocktakeForm())
    result.goBack()
    expect(mockRouter.push).toHaveBeenCalledWith({ name: 'stocktake-list' })
  })
})
