import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { mockAxiosResponse } from '../helpers'
import { useWarehouseStore, type WarehouseOption } from '@/stores/warehouse'

const warehouseA: WarehouseOption = {
  id: 1,
  warehouseCode: 'WH001',
  warehouseName: 'Warehouse A',
  isActive: true,
}

const warehouseB: WarehouseOption = {
  id: 2,
  warehouseCode: 'WH002',
  warehouseName: 'Warehouse B',
  isActive: true,
}

describe('useWarehouseStore', () => {
  let store: ReturnType<typeof useWarehouseStore>

  beforeEach(() => {
    store = useWarehouseStore()
  })

  describe('initial state', () => {
    it('warehouses is empty', () => {
      expect(store.warehouses).toEqual([])
    })

    it('selectedWarehouseId is null', () => {
      expect(store.selectedWarehouseId).toBeNull()
    })

    it('loading is false', () => {
      expect(store.loading).toBe(false)
    })

    it('selectedWarehouse is null', () => {
      expect(store.selectedWarehouse).toBeNull()
    })
  })

  describe('fetchWarehouses', () => {
    it('sets warehouses on success', async () => {
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse([warehouseA, warehouseB]))

      await store.fetchWarehouses()

      expect(apiClient.get).toHaveBeenCalledWith('/master/warehouses', {
        params: { all: true, isActive: true },
      })
      expect(store.warehouses).toEqual([warehouseA, warehouseB])
      expect(store.loading).toBe(false)
    })

    it('auto-selects when only one warehouse returned', async () => {
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse([warehouseA]))

      await store.fetchWarehouses()

      expect(store.selectedWarehouseId).toBe(1)
    })

    it('does not auto-select when multiple warehouses returned', async () => {
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse([warehouseA, warehouseB]))

      await store.fetchWarehouses()

      expect(store.selectedWarehouseId).toBeNull()
    })

    it('resets selectedWarehouseId if selected warehouse no longer in list', async () => {
      // Pre-select warehouse B
      store.selectWarehouse(2)

      // Fetch returns only warehouse A (B is gone)
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse([warehouseA]))

      await store.fetchWarehouses()

      // Should reset invalid selection, then auto-select the single remaining
      expect(store.selectedWarehouseId).toBe(1)
    })

    it('keeps selectedWarehouseId if selected warehouse still in list', async () => {
      store.selectWarehouse(2)

      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse([warehouseA, warehouseB]))

      await store.fetchWarehouses()

      expect(store.selectedWarehouseId).toBe(2)
    })

    it('clears warehouses on error', async () => {
      // Set initial data
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse([warehouseA]))
      await store.fetchWarehouses()
      vi.clearAllMocks()

      // Now trigger error
      vi.mocked(apiClient.get).mockRejectedValue(new Error('Network Error'))

      await store.fetchWarehouses()

      expect(store.warehouses).toEqual([])
      expect(store.loading).toBe(false)
    })
  })

  describe('selectWarehouse', () => {
    it('sets selectedWarehouseId', () => {
      store.selectWarehouse(5)

      expect(store.selectedWarehouseId).toBe(5)
    })
  })

  describe('selectedWarehouse computed', () => {
    it('returns matching warehouse', async () => {
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse([warehouseA, warehouseB]))
      await store.fetchWarehouses()

      store.selectWarehouse(2)

      expect(store.selectedWarehouse).toEqual(warehouseB)
    })

    it('returns null when no match', async () => {
      vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse([warehouseA]))
      await store.fetchWarehouses()

      store.selectWarehouse(999)

      expect(store.selectedWarehouse).toBeNull()
    })
  })
})
