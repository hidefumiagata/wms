<template>
  <div class="wms-page">
    <!-- 検索フォーム -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline @submit.prevent="handleSearch">
        <el-form-item :label="t('inventory.stocktakeDateFrom')">
          <el-date-picker
            v-model="searchForm.dateFrom"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 160px"
            clearable
          />
        </el-form-item>
        <el-form-item :label="t('inventory.stocktakeDateTo')">
          <el-date-picker
            v-model="searchForm.dateTo"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 160px"
            clearable
          />
        </el-form-item>
        <el-form-item :label="t('inventory.stocktakeStatus')">
          <el-select
            v-model="searchForm.status"
            style="width: 140px"
            clearable
            :placeholder="t('inventory.stocktakeStatusAll')"
          >
            <el-option :label="t('inventory.stocktakeStatusStarted')" value="STARTED" />
            <el-option :label="t('inventory.stocktakeStatusConfirmed')" value="CONFIRMED" />
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
          <span>{{ t('inventory.stocktakeTitle') }}</span>
          <el-button
            v-if="isManager"
            type="primary"
            :icon="Plus"
            @click="router.push({ name: 'stocktake-new' })"
          >
            {{ t('inventory.stocktakeNew') }}
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="items" stripe border style="width: 100%">
        <el-table-column :label="t('inventory.stocktakeNumber')" width="180">
          <template #default="{ row }">
            <el-link type="primary" @click="goToDetail(row)">
              {{ row.stocktakeNumber }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column :label="t('inventory.stocktakeDate')" width="120">
          <template #default="{ row }">
            {{ formatStocktakeDate(row.startedAt) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="targetDescription"
          :label="t('inventory.stocktakeBuilding')"
          min-width="160"
        />
        <el-table-column :label="t('inventory.stocktakeStatus')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'CONFIRMED' ? 'success' : 'warning'" size="small">
              {{
                row.status === 'CONFIRMED'
                  ? t('inventory.stocktakeStatusConfirmed')
                  : t('inventory.stocktakeStatusStarted')
              }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('inventory.stocktakeProgress')" width="120" align="center">
          <template #default="{ row }">{{ row.countedLines }} / {{ row.totalLines }}</template>
        </el-table-column>
        <el-table-column prop="startedByName" :label="t('inventory.stocktakeStaff')" width="120" />
        <el-table-column :label="t('common.actions')" width="160" align="center">
          <template #default="{ row }">
            <template v-if="row.status === 'STARTED'">
              <el-button type="primary" link size="small" @click="goToDetail(row)">
                {{ isViewer ? t('inventory.stocktakeView') : t('inventory.stocktakeInput') }}
              </el-button>
              <el-button
                v-if="isManager"
                type="warning"
                link
                size="small"
                @click="goToConfirm(row)"
              >
                {{ t('inventory.stocktakeConfirmBtn') }}
              </el-button>
            </template>
            <template v-else>
              <el-button type="primary" link size="small" @click="goToDetail(row)">
                {{ t('inventory.stocktakeResult') }}
              </el-button>
            </template>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="t('inventory.stocktakeNoResult')" />
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
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { useStocktakeList } from '@/composables/inventory/useStocktakeList'

const { t } = useI18n()
const router = useRouter()

const {
  items,
  loading,
  total,
  page,
  pageSize,
  searchForm,
  isManager,
  isViewer,
  fetchList,
  handleSearch,
  handleReset,
  handlePageChange,
  handleSizeChange,
  formatStocktakeDate,
  goToDetail,
  goToConfirm,
} = useStocktakeList()

onMounted(() => {
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
