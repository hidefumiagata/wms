import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import apiClient from '@/api/client'

export interface WarehouseOption {
  id: number
  warehouseCode: string
  warehouseName: string
  isActive: boolean
}

export const useWarehouseStore = defineStore('warehouse', () => {
  const warehouses = ref<WarehouseOption[]>([])
  const selectedWarehouseId = ref<number | null>(null)
  const loading = ref(false)

  const selectedWarehouse = computed(() =>
    warehouses.value.find((w) => w.id === selectedWarehouseId.value) ?? null,
  )

  async function fetchWarehouses() {
    loading.value = true
    try {
      const res = await apiClient.get<WarehouseOption[]>('/master/warehouses', {
        params: { all: true, isActive: true },
      })
      warehouses.value = res.data
      // 選択中の倉庫が無効化された場合はリセット
      if (selectedWarehouseId.value && !res.data.find((w) => w.id === selectedWarehouseId.value)) {
        selectedWarehouseId.value = null
      }
      // 未選択かつ倉庫が1件のみの場合は自動選択
      if (!selectedWarehouseId.value && res.data.length === 1) {
        selectedWarehouseId.value = res.data[0].id
      }
    } finally {
      loading.value = false
    }
  }

  function selectWarehouse(id: number) {
    selectedWarehouseId.value = id
  }

  return {
    warehouses,
    selectedWarehouseId,
    selectedWarehouse,
    loading,
    fetchWarehouses,
    selectWarehouse,
  }
})
