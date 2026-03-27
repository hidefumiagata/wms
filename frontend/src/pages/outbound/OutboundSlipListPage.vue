<template>
  <div class="wms-page">
    <!-- 検索フォーム -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline @submit.prevent="handleSearch">
        <el-form-item :label="t('outbound.slip.slipNumber')">
          <el-input
            v-model="searchForm.slipNumber"
            :placeholder="t('outbound.slip.slipNumber')"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item :label="t('outbound.slip.plannedDateFrom')">
          <el-date-picker
            v-model="searchForm.plannedDateFrom"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 160px"
            clearable
          />
        </el-form-item>
        <el-form-item :label="t('outbound.slip.plannedDateTo')">
          <el-date-picker
            v-model="searchForm.plannedDateTo"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 160px"
            clearable
          />
        </el-form-item>
        <el-form-item :label="t('outbound.slip.partner')">
          <el-select
            v-model="searchForm.partnerId"
            style="width: 200px"
            clearable
            filterable
            :placeholder="t('outbound.slip.partnerAll')"
          >
            <el-option
              v-for="p in partnerOptions"
              :key="p.id"
              :label="p.partnerName"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('outbound.slip.status')">
          <el-select
            v-model="searchForm.status"
            style="width: 140px"
            clearable
            :placeholder="t('outbound.slip.statusAll')"
          >
            <el-option :label="t('outbound.slip.statusOrdered')" value="ORDERED" />
            <el-option
              :label="t('outbound.slip.statusPartialAllocated')"
              value="PARTIAL_ALLOCATED"
            />
            <el-option :label="t('outbound.slip.statusAllocated')" value="ALLOCATED" />
            <el-option
              :label="t('outbound.slip.statusPickingCompleted')"
              value="PICKING_COMPLETED"
            />
            <el-option :label="t('outbound.slip.statusInspecting')" value="INSPECTING" />
            <el-option :label="t('outbound.slip.statusShipped')" value="SHIPPED" />
            <el-option :label="t('outbound.slip.statusCancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :icon="Search">
            {{ t('common.search') }}
          </el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 一覧テーブル -->
    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <span>{{ t('outbound.slip.title') }}</span>
          <div class="table-header__right">
            <el-button
              v-if="!isViewer"
              type="warning"
              :disabled="selectedIds.length === 0"
              :loading="loading"
              @click="handleBulkAllocate"
            >
              {{ t('outbound.slip.bulkAllocate') }}
            </el-button>
            <el-button
              v-if="!isViewer"
              type="primary"
              :icon="Plus"
              @click="router.push({ name: 'outbound-slip-new' })"
            >
              {{ t('outbound.slip.newRegistration') }}
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="items"
        stripe
        border
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column v-if="!isViewer" type="selection" width="40" :selectable="isSelectable" />
        <el-table-column prop="slipNumber" :label="t('outbound.slip.slipNumber')" width="180">
          <template #default="{ row }">
            <el-link
              type="primary"
              @click="router.push({ name: 'outbound-slip-detail', params: { id: row.id } })"
            >
              {{ row.slipNumber }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column :label="t('outbound.slip.slipType')" width="120" align="center">
          <template #default="{ row }">
            {{
              row.slipType === 'NORMAL'
                ? t('outbound.slip.typeNormal')
                : t('outbound.slip.typeTransfer')
            }}
          </template>
        </el-table-column>
        <el-table-column prop="plannedDate" :label="t('outbound.slip.plannedDate')" width="120" />
        <el-table-column prop="partnerName" :label="t('outbound.slip.partner')" min-width="140" />
        <el-table-column
          prop="lineCount"
          :label="t('outbound.slip.lineCount')"
          width="80"
          align="center"
        />
        <el-table-column :label="t('outbound.slip.status')" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="outboundStatusTagType(row.status)" size="small">
              {{ outboundStatusLabel(row.status, t) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('outbound.slip.createdAt')" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column
          v-if="!isViewer"
          :label="t('common.actions')"
          width="140"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'ORDERED' || row.status === 'PARTIAL_ALLOCATED'"
              size="small"
              type="warning"
              plain
              @click="handleAllocateSingle(row.id)"
            >
              {{ t('outbound.slip.allocate') }}
            </el-button>
            <el-button
              v-if="row.status === 'ALLOCATED'"
              size="small"
              type="primary"
              plain
              @click="router.push({ name: 'picking-new' })"
            >
              {{ t('outbound.slip.goPickingNew') }}
            </el-button>
            <el-button
              v-if="row.status === 'PICKING_COMPLETED'"
              size="small"
              type="warning"
              plain
              @click="router.push({ name: 'outbound-slip-inspect', params: { id: row.id } })"
            >
              {{ t('outbound.slip.goInspect') }}
            </el-button>
            <el-button
              v-if="row.status === 'INSPECTING'"
              size="small"
              type="success"
              plain
              @click="router.push({ name: 'outbound-slip-ship', params: { id: row.id } })"
            >
              {{ t('outbound.slip.goShip') }}
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
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { useOutboundSlipList } from '@/composables/outbound/useOutboundSlipList'
import {
  outboundStatusLabel,
  outboundStatusTagType,
  formatDateTime,
} from '@/utils/outboundFormatters'
import { OutboundSlipStatus } from '@/api/generated/models/outbound-slip-status'

const { t } = useI18n()
const router = useRouter()

const {
  items,
  loading,
  total,
  page,
  pageSize,
  searchForm,
  partnerOptions,
  selectedIds,
  isViewer,
  fetchList,
  fetchPartnerOptions,
  handleSearch,
  handleReset,
  handlePageChange,
  handleSizeChange,
  handleSelectionChange,
  handleBulkAllocate,
  handleAllocateSingle,
} = useOutboundSlipList()

function isSelectable(row: { status: string }) {
  return (
    row.status === OutboundSlipStatus.Ordered ||
    row.status === OutboundSlipStatus.PartialAllocated ||
    row.status === OutboundSlipStatus.Allocated
  )
}

onMounted(() => {
  fetchPartnerOptions()
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
.table-header__right {
  display: flex;
  gap: 8px;
}
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
