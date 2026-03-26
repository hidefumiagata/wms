<template>
  <div class="wms-page">
    <!-- 検索フォーム -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline @submit.prevent="handleSearch">
        <el-form-item :label="t('master.warehouse.warehouseCode')">
          <el-input
            v-model="searchForm.warehouseCode"
            :placeholder="t('master.warehouse.warehouseCode')"
            clearable
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item :label="t('master.warehouse.warehouseName')">
          <el-input
            v-model="searchForm.warehouseName"
            :placeholder="t('master.warehouse.warehouseName')"
            clearable
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item :label="t('master.warehouse.status')">
          <el-select
            v-model="searchForm.isActive"
            style="width: 130px"
            clearable
            :placeholder="t('master.warehouse.statusAll')"
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
          <span>{{ t('master.warehouse.title') }}</span>
          <el-button type="primary" :icon="Plus" @click="router.push({ name: 'warehouse-new' })">
            {{ t('master.warehouse.newRegistration') }}
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="items" stripe border style="width: 100%">
        <el-table-column
          prop="warehouseCode"
          :label="t('master.warehouse.warehouseCode')"
          width="120"
        >
          <template #default="{ row }">
            <el-link
              type="primary"
              @click="router.push({ name: 'warehouse-edit', params: { id: row.id } })"
            >
              {{ row.warehouseCode }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column
          prop="warehouseName"
          :label="t('master.warehouse.warehouseName')"
          min-width="160"
        />
        <el-table-column
          prop="warehouseNameKana"
          :label="t('master.warehouse.warehouseNameKana')"
          min-width="160"
        />
        <el-table-column
          prop="address"
          :label="t('master.warehouse.address')"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column :label="t('master.warehouse.status')" width="90" align="center">
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
              @click="router.push({ name: 'warehouse-edit', params: { id: row.id } })"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              size="small"
              :type="row.isActive ? 'danger' : 'success'"
              plain
              @click="handleToggleActive(row)"
            >
              {{ row.isActive ? t('master.warehouse.deactivate') : t('master.warehouse.activate') }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="t('common.noData')" />
        </template>
      </el-table>

      <!-- ページネーション -->
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
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { Search, Refresh, Plus, Edit } from '@element-plus/icons-vue'
import { useWarehouseList } from '@/composables/master/useWarehouseList'

const { t } = useI18n()
const router = useRouter()

const {
  items,
  loading,
  total,
  page,
  pageSize,
  searchForm,
  fetchList,
  handleSearch,
  handleReset,
  handlePageChange,
  handleSizeChange,
  handleToggleActive,
} = useWarehouseList()

onMounted(() => fetchList())
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
