<template>
  <div class="wms-page">
    <!-- 検索フォーム -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline @submit.prevent="handleSearch">
        <el-form-item :label="t('batch.history.executedDateFrom')">
          <el-date-picker
            v-model="searchForm.executedDateFrom"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 160px"
            clearable
          />
        </el-form-item>
        <el-form-item :label="t('batch.history.executedDateTo')">
          <el-date-picker
            v-model="searchForm.executedDateTo"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 160px"
            clearable
          />
        </el-form-item>
        <el-form-item :label="t('batch.history.result')">
          <el-select
            v-model="searchForm.status"
            style="width: 120px"
            clearable
            :placeholder="t('batch.history.resultAll')"
          >
            <el-option :label="t('batch.history.resultSuccess')" value="SUCCESS" />
            <el-option :label="t('batch.history.resultFailed')" value="FAILED" />
            <el-option :label="t('batch.history.resultRunning')" value="RUNNING" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('batch.history.targetBusinessDate')">
          <el-date-picker
            v-model="searchForm.targetBusinessDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 160px"
            clearable
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
          <span>{{ t('batch.history.title') }}</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="items" stripe border style="width: 100%">
        <el-table-column prop="id" :label="t('batch.history.execId')" width="100" />
        <el-table-column :label="t('batch.history.executedAt')" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.startedAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="targetBusinessDate" :label="t('batch.history.targetBusinessDate')" width="130" />
        <el-table-column prop="executedByName" :label="t('batch.history.executedBy')" min-width="120" />
        <el-table-column :label="t('batch.history.result')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('batch.history.detail')" width="80" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">
              {{ t('batch.history.detail') }}
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

    <!-- 詳細ドロワー -->
    <el-drawer
      v-model="drawerVisible"
      :title="t('batch.history.drawerTitle')"
      size="480px"
      @close="closeDrawer"
    >
      <div v-loading="drawerLoading">
        <template v-if="selectedDetail">
          <el-descriptions :column="1" border>
            <el-descriptions-item :label="t('batch.history.execId')">
              {{ selectedDetail.id }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('batch.history.targetBusinessDate')">
              {{ selectedDetail.targetBusinessDate }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('batch.history.executedAt')">
              {{ formatDateTime(selectedDetail.startedAt) }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('batch.history.executedBy')">
              {{ selectedDetail.executedByName }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('batch.history.result')">
              <el-tag :type="statusTagType(selectedDetail.status)" size="small">
                {{ statusLabel(selectedDetail.status) }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>

          <h4 style="margin: 16px 0 8px">{{ t('batch.history.stepResults') }}</h4>
          <el-table :data="stepRows" border size="small" style="width: 100%">
            <el-table-column prop="label" label="Step" min-width="180" />
            <el-table-column label="" width="140" align="center">
              <template #default="{ row }">
                <el-tag :type="stepTagType(row.status)" size="small">
                  {{ stepLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>

          <template v-if="selectedDetail.errorMessage">
            <h4 style="margin: 16px 0 8px">{{ t('batch.history.errorMessage') }}</h4>
            <el-alert
              :title="selectedDetail.errorMessage"
              type="error"
              :closable="false"
              show-icon
            />
          </template>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search, Refresh } from '@element-plus/icons-vue'
import { useBatchHistory } from '@/composables/batch/useBatchHistory'

const { t } = useI18n()

const {
  items,
  loading,
  total,
  page,
  pageSize,
  searchForm,
  drawerVisible,
  drawerLoading,
  selectedDetail,
  fetchList,
  openDetail,
  closeDrawer,
  handleSearch,
  handleReset,
  handlePageChange,
  handleSizeChange,
} = useBatchHistory()

function formatDateTime(dt: string | undefined | null): string {
  if (!dt) return ''
  return new Date(dt).toLocaleString('ja-JP', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

function statusLabel(status: string | undefined): string {
  switch (status) {
    case 'SUCCESS': return t('batch.history.resultSuccess')
    case 'FAILED': return t('batch.history.resultFailed')
    case 'RUNNING': return t('batch.history.resultRunning')
    default: return status ?? ''
  }
}

function statusTagType(status: string | undefined): '' | 'success' | 'danger' | 'warning' {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'danger'
    case 'RUNNING': return 'warning'
    default: return ''
  }
}

function stepLabel(status: string | undefined): string {
  switch (status) {
    case 'SUCCESS': return t('batch.history.stepSuccess')
    case 'FAILED': return t('batch.history.stepFailed')
    case 'SKIPPED': return t('batch.history.stepSkipped')
    default: return status ?? ''
  }
}

function stepTagType(status: string | undefined): '' | 'success' | 'danger' | 'info' {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'danger'
    case 'SKIPPED': return 'info'
    default: return ''
  }
}

const stepRows = computed(() => {
  if (!selectedDetail.value) return []
  const d = selectedDetail.value
  return [
    { label: t('batch.history.step1'), status: d.step1Status },
    { label: t('batch.history.step2'), status: d.step2Status },
    { label: t('batch.history.step3'), status: d.step3Status },
    { label: t('batch.history.step4'), status: d.step4Status },
    { label: t('batch.history.step5'), status: d.step5Status },
    { label: t('batch.history.step6'), status: d.step6Status },
  ]
})

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
