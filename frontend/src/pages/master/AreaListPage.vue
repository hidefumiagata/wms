<template>
  <div class="wms-page">
    <!-- 検索フォーム -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline @submit.prevent="handleSearch">
        <el-form-item :label="t('master.area.building')">
          <el-select
            v-model="searchForm.buildingId"
            style="width: 160px"
            clearable
            :placeholder="t('master.area.buildingAll')"
          >
            <el-option
              v-for="b in buildings"
              :key="b.id"
              :label="`${b.buildingName} (${b.buildingCode})`"
              :value="b.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('master.area.storageCondition')">
          <el-select
            v-model="searchForm.storageCondition"
            style="width: 130px"
            clearable
            :placeholder="t('master.area.storageConditionAll')"
          >
            <el-option :label="t('master.area.storageAmbient')" value="AMBIENT" />
            <el-option :label="t('master.area.storageRefrigerated')" value="REFRIGERATED" />
            <el-option :label="t('master.area.storageFrozen')" value="FROZEN" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('master.area.areaType')">
          <el-select
            v-model="searchForm.areaType"
            style="width: 140px"
            clearable
            :placeholder="t('master.area.areaTypeAll')"
          >
            <el-option :label="t('master.area.typeStock')" value="STOCK" />
            <el-option :label="t('master.area.typeInbound')" value="INBOUND" />
            <el-option :label="t('master.area.typeOutbound')" value="OUTBOUND" />
            <el-option :label="t('master.area.typeReturn')" value="RETURN" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('master.area.status')">
          <el-select
            v-model="searchForm.isActive"
            style="width: 130px"
            clearable
            :placeholder="t('master.area.statusAll')"
          >
            <el-option :label="t('common.active')" :value="true" />
            <el-option :label="t('common.inactive')" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :icon="Search">
            {{ t('common.search') }}
          </el-button>
          <el-button :icon="Refresh" @click="handleReset">
            {{ t('common.reset') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 一覧テーブル -->
    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <span>{{ t('master.area.title') }}</span>
          <el-button type="primary" :icon="Plus" @click="router.push({ name: 'area-new' })">
            {{ t('master.area.newRegistration') }}
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="items" stripe border style="width: 100%">
        <el-table-column prop="areaCode" :label="t('master.area.areaCode')" width="120">
          <template #default="{ row }">
            <el-link
              type="primary"
              @click="router.push({ name: 'area-edit', params: { id: row.id } })"
            >
              {{ row.areaCode }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="areaName" :label="t('master.area.areaName')" min-width="180" />
        <el-table-column
          prop="buildingCode"
          :label="t('master.area.buildingCode')"
          width="100"
        />
        <el-table-column :label="t('master.area.storageCondition')" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              :type="storageConditionTagType(row.storageCondition)"
              size="small"
            >
              {{ storageConditionLabel(row.storageCondition) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('master.area.areaType')" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ areaTypeLabel(row.areaType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('master.area.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'info'" size="small">
              {{ row.isActive ? t('common.active') : t('common.inactive') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              :icon="Edit"
              @click="router.push({ name: 'area-edit', params: { id: row.id } })"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              size="small"
              :type="row.isActive ? 'danger' : 'success'"
              plain
              @click="handleToggleActive(row)"
            >
              {{ row.isActive ? t('master.area.deactivate') : t('master.area.activate') }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="t('common.noData')" />
        </template>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { Search, Refresh, Plus, Edit } from '@element-plus/icons-vue'
import { useAreaList } from '@/composables/master/useAreaList'
import { useWarehouseStore } from '@/stores/warehouse'

const { t } = useI18n()
const router = useRouter()
const warehouseStore = useWarehouseStore()

const {
  items,
  buildings,
  loading,
  total,
  page,
  pageSize,
  searchForm,
  fetchList,
  fetchBuildings,
  handleSearch,
  handleReset,
  handlePageChange,
  handleSizeChange,
  handleToggleActive,
} = useAreaList()

function storageConditionLabel(val: string) {
  const map: Record<string, string> = {
    AMBIENT: t('master.area.storageAmbient'),
    REFRIGERATED: t('master.area.storageRefrigerated'),
    FROZEN: t('master.area.storageFrozen'),
  }
  return map[val] ?? val
}

function storageConditionTagType(val: string) {
  const map: Record<string, string> = {
    AMBIENT: '',
    REFRIGERATED: 'info',
    FROZEN: 'warning',
  }
  return map[val] ?? ''
}

function areaTypeLabel(val: string) {
  const map: Record<string, string> = {
    STOCK: t('master.area.typeStock'),
    INBOUND: t('master.area.typeInbound'),
    OUTBOUND: t('master.area.typeOutbound'),
    RETURN: t('master.area.typeReturn'),
  }
  return map[val] ?? val
}

onMounted(() => {
  fetchBuildings()
  fetchList()
})

watch(() => warehouseStore.selectedWarehouseId, () => {
  page.value = 1
  fetchBuildings()
  fetchList()
})
</script>

<style scoped lang="scss">
.wms-page {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
