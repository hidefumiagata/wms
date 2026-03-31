<template>
  <div class="wms-page">
    <!-- 検索フォーム -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline @submit.prevent="handleSearch">
        <el-form-item :label="t('interfaceHistory.filterIfType')">
          <el-select
            v-model="searchForm.ifType"
            style="width: 180px"
            clearable
            :placeholder="t('interfaceHistory.filterIfTypeAll')"
          >
            <el-option :label="t('interfaceHistory.filterIfTypeInbound')" value="IFX-001" />
            <el-option :label="t('interfaceHistory.filterIfTypeOrder')" value="IFX-002" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('interfaceHistory.filterDateFrom')">
          <el-date-picker
            v-model="searchForm.dateFrom"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 160px"
            clearable
          />
        </el-form-item>
        <el-form-item :label="t('interfaceHistory.filterDateTo')">
          <el-date-picker
            v-model="searchForm.dateTo"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 160px"
            clearable
          />
        </el-form-item>
        <el-form-item :label="t('interfaceHistory.filterResult')">
          <el-select
            v-model="searchForm.status"
            style="width: 120px"
            clearable
            :placeholder="t('interfaceHistory.filterResultAll')"
          >
            <el-option :label="t('interfaceHistory.filterResultCompleted')" value="COMPLETED" />
            <el-option :label="t('interfaceHistory.filterResultDiscarded')" value="DISCARDED" />
            <el-option :label="t('interfaceHistory.filterResultFailed')" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('interfaceHistory.filterFileName')">
          <el-input
            v-model="searchForm.fileName"
            style="width: 200px"
            clearable
            :placeholder="t('interfaceHistory.filterFileName')"
          />
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
          <span>{{ t('interfaceHistory.title') }}</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="items" stripe border style="width: 100%">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column :label="t('interfaceHistory.executedAt')" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.executedAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="fileName" :label="t('interfaceHistory.fileName')" min-width="180" />
        <el-table-column :label="t('interfaceHistory.ifType')" width="110" align="center">
          <template #default="{ row }">
            {{ ifTypeLabel(row.ifType) }}
          </template>
        </el-table-column>
        <el-table-column prop="totalCount" :label="t('interfaceHistory.totalCount')" width="90" align="right" />
        <el-table-column prop="successCount" :label="t('interfaceHistory.successCount')" width="90" align="right" />
        <el-table-column :label="t('interfaceHistory.errorCount')" width="90" align="right">
          <template #default="{ row }">
            <span :class="{ 'error-count': row.errorCount > 0 }">{{ row.errorCount }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('interfaceHistory.mode')" width="110" align="center">
          <template #default="{ row }">
            {{ modeLabel(row.mode) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('interfaceHistory.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
            <el-tooltip v-if="row.blobMoveFailed" :content="t('interfaceHistory.blobMoveWarning')" placement="top">
              <el-icon color="var(--el-color-warning)" style="margin-left: 4px"><WarningFilled /></el-icon>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="executedByName" :label="t('interfaceHistory.executedBy')" width="120" />
        <template #empty>
          <el-empty :description="t('interfaceHistory.emptyMessage')" />
        </template>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
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
import { Search, Refresh, WarningFilled } from '@element-plus/icons-vue'
import { useInterfaceHistory } from '@/composables/interface/useInterfaceHistory'

const { t } = useI18n()

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
} = useInterfaceHistory()

function formatDateTime(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleString('ja-JP', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

function ifTypeLabel(ifType: string): string {
  if (ifType === 'INBOUND_PLAN') return t('interfaceHistory.ifTypeInbound')
  if (ifType === 'ORDER') return t('interfaceHistory.ifTypeOrder')
  return ifType
}

function modeLabel(mode: string): string {
  if (mode === 'SUCCESS_ONLY') return t('interfaceHistory.modeSuccessOnly')
  if (mode === 'DISCARD') return t('interfaceHistory.modeDiscard')
  return mode
}

function statusLabel(status: string): string {
  if (status === 'COMPLETED') return t('interfaceHistory.statusCompleted')
  if (status === 'DISCARDED') return t('interfaceHistory.statusDiscarded')
  if (status === 'FAILED') return t('interfaceHistory.statusFailed')
  return status
}

function statusTagType(status: string): '' | 'success' | 'danger' | 'info' {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'DISCARDED') return 'info'
  return ''
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.wms-page {
  padding: 20px;
}

.search-card {
  margin-bottom: 16px;
}

.table-card {
  .table-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 16px;
    font-weight: 600;
  }
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.error-count {
  color: var(--el-color-danger);
  font-weight: 600;
}
</style>
