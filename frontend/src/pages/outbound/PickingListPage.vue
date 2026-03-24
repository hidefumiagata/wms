<template>
  <div class="wms-page">
    <el-card class="search-card">
      <el-form :model="searchForm" inline @submit.prevent="handleSearch">
        <el-form-item :label="t('outbound.picking.instructionNumber')">
          <el-input v-model="searchForm.instructionNumber" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item :label="t('outbound.picking.status')">
          <el-select v-model="searchForm.status" style="width: 140px" clearable :placeholder="t('outbound.slip.statusAll')">
            <el-option :label="t('outbound.picking.statusCreated')" value="CREATED" />
            <el-option :label="t('outbound.picking.statusInProgress')" value="IN_PROGRESS" />
            <el-option :label="t('outbound.picking.statusCompleted')" value="COMPLETED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('outbound.picking.createdDateFrom')">
          <el-date-picker v-model="searchForm.createdDateFrom" type="date" value-format="YYYY-MM-DD" style="width: 160px" clearable />
        </el-form-item>
        <el-form-item :label="t('outbound.picking.createdDateTo')">
          <el-date-picker v-model="searchForm.createdDateTo" type="date" value-format="YYYY-MM-DD" style="width: 160px" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :icon="Search">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <span>{{ t('outbound.picking.title') }}</span>
          <el-button type="primary" :icon="Plus" @click="router.push({ name: 'picking-new' })">
            {{ t('outbound.picking.createNew') }}
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="items" stripe border style="width: 100%">
        <el-table-column prop="instructionNumber" :label="t('outbound.picking.instructionNumber')" width="200">
          <template #default="{ row }">
            <el-link type="primary" @click="router.push({ name: 'picking-detail', params: { id: row.id } })">
              {{ row.instructionNumber }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="areaName" :label="t('outbound.picking.area')" width="140">
          <template #default="{ row }">{{ row.areaName ?? '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('outbound.picking.status')" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="pickingStatusTagType(row.status)" size="small">
              {{ pickingStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lineCount" :label="t('outbound.slip.lineCount')" width="80" align="center" />
        <el-table-column prop="createdAt" :label="t('outbound.slip.createdAt')" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="createdByName" :label="t('outbound.slip.createdBy')" width="120" />
        <template #empty><el-empty :description="t('common.noData')" /></template>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total" :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next" background @current-change="handlePageChange" @size-change="handleSizeChange" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { usePickingList } from '@/composables/outbound/usePickingList'
import { formatDateTime } from '@/utils/outboundFormatters'
import { PickingInstructionStatus } from '@/api/generated/models/picking-instruction-status'

const { t } = useI18n()
const router = useRouter()

const { items, loading, total, page, pageSize, searchForm, fetchList, handleSearch, handleReset, handlePageChange, handleSizeChange } = usePickingList()

function pickingStatusLabel(status: string): string {
  switch (status) {
    case PickingInstructionStatus.Created: return t('outbound.picking.statusCreated')
    case PickingInstructionStatus.InProgress: return t('outbound.picking.statusInProgress')
    case PickingInstructionStatus.Completed: return t('outbound.picking.statusCompleted')
    default: return status
  }
}

function pickingStatusTagType(status: string): '' | 'success' | 'warning' | 'info' {
  switch (status) {
    case PickingInstructionStatus.Created: return 'info'
    case PickingInstructionStatus.InProgress: return 'warning'
    case PickingInstructionStatus.Completed: return 'success'
    default: return 'info'
  }
}

onMounted(() => fetchList())
</script>

<style scoped lang="scss">
.wms-page { padding: 20px; display: flex; flex-direction: column; gap: 16px; }
.table-header { display: flex; align-items: center; justify-content: space-between; }
.pagination-wrapper { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
