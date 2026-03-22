<template>
  <div class="wms-page">
    <!-- 検索フォーム -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline @submit.prevent="handleSearch">
        <el-form-item :label="t('master.location.codePrefix')">
          <el-input
            v-model="searchForm.codePrefix"
            :placeholder="t('master.location.codePrefixPlaceholder')"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item :label="t('master.location.area')">
          <el-select
            v-model="searchForm.areaId"
            style="width: 200px"
            clearable
            :placeholder="t('master.location.areaAll')"
          >
            <el-option
              v-for="a in areas"
              :key="a.id"
              :label="`${a.areaCode} (${a.areaName})`"
              :value="a.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('master.location.status')">
          <el-select
            v-model="searchForm.isActive"
            style="width: 130px"
            clearable
            :placeholder="t('master.location.statusAll')"
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
          <span>{{ t('master.location.title') }}</span>
          <el-button type="primary" :icon="Plus" @click="router.push({ name: 'location-new' })">
            {{ t('master.location.newRegistration') }}
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="items" stripe border style="width: 100%">
        <el-table-column
          prop="locationCode"
          :label="t('master.location.locationCode')"
          width="180"
        >
          <template #default="{ row }">
            <el-link
              type="primary"
              @click="router.push({ name: 'location-edit', params: { id: row.id } })"
            >
              {{ row.locationCode }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column
          prop="locationName"
          :label="t('master.location.locationName')"
          min-width="180"
        />
        <el-table-column prop="areaCode" :label="t('master.location.areaCode')" width="110" />
        <el-table-column :label="t('master.location.status')" width="90" align="center">
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
              @click="router.push({ name: 'location-edit', params: { id: row.id } })"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              size="small"
              :type="row.isActive ? 'danger' : 'success'"
              plain
              @click="handleToggleActive(row)"
            >
              {{ row.isActive ? t('master.location.deactivate') : t('master.location.activate') }}
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
import { useLocationList } from '@/composables/master/useLocationList'
import { useWarehouseStore } from '@/stores/warehouse'

const { t } = useI18n()
const router = useRouter()
const warehouseStore = useWarehouseStore()

const {
  items,
  areas,
  loading,
  total,
  page,
  pageSize,
  searchForm,
  fetchList,
  fetchAreas,
  handleSearch,
  handleReset,
  handlePageChange,
  handleSizeChange,
  handleToggleActive,
} = useLocationList()

onMounted(() => {
  fetchAreas()
  fetchList()
})

watch(() => warehouseStore.selectedWarehouseId, () => {
  page.value = 1
  fetchAreas()
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
