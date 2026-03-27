<template>
  <div class="wms-page">
    <!-- 検索フォーム -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline @submit.prevent="handleSearch">
        <el-form-item :label="t('inbound.slip.slipNumber')">
          <el-input
            v-model="searchForm.slipNumber"
            :placeholder="t('inbound.slip.slipNumber')"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item :label="t('inbound.slip.plannedDateFrom')">
          <el-date-picker
            v-model="searchForm.plannedDateFrom"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 160px"
            clearable
          />
        </el-form-item>
        <el-form-item :label="t('inbound.slip.plannedDateTo')">
          <el-date-picker
            v-model="searchForm.plannedDateTo"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 160px"
            clearable
          />
        </el-form-item>
        <el-form-item :label="t('inbound.slip.partner')">
          <el-select
            v-model="searchForm.partnerId"
            style="width: 200px"
            clearable
            filterable
            :placeholder="t('inbound.slip.partnerAll')"
          >
            <el-option
              v-for="p in partnerOptions"
              :key="p.id"
              :label="p.partnerName"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('inbound.slip.status')">
          <el-select
            v-model="searchForm.status"
            style="width: 140px"
            clearable
            :placeholder="t('inbound.slip.statusAll')"
          >
            <el-option :label="t('inbound.slip.statusPlanned')" value="PLANNED" />
            <el-option :label="t('inbound.slip.statusConfirmed')" value="CONFIRMED" />
            <el-option :label="t('inbound.slip.statusInspecting')" value="INSPECTING" />
            <el-option :label="t('inbound.slip.statusPartialStored')" value="PARTIAL_STORED" />
            <el-option :label="t('inbound.slip.statusStored')" value="STORED" />
            <el-option :label="t('inbound.slip.statusCancelled')" value="CANCELLED" />
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
          <span>{{ t('inbound.slip.title') }}</span>
          <el-button
            v-if="!isViewer"
            type="primary"
            :icon="Plus"
            @click="router.push({ name: 'inbound-slip-new' })"
          >
            {{ t('inbound.slip.newRegistration') }}
          </el-button>
        </div>
      </template>

      <!-- TODO: RPT-03 入荷予定レポートボタン（レポート機能実装後に追加） -->

      <el-table v-loading="loading" :data="items" stripe border style="width: 100%">
        <el-table-column prop="slipNumber" :label="t('inbound.slip.slipNumber')" width="180">
          <template #default="{ row }">
            <el-link
              type="primary"
              @click="router.push({ name: 'inbound-slip-detail', params: { id: row.id } })"
            >
              {{ row.slipNumber }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column :label="t('inbound.slip.slipType')" width="100" align="center">
          <template #default="{ row }">
            {{
              row.slipType === 'NORMAL' ? t('inbound.slip.typeNormal') : t('inbound.slip.typeBatch')
            }}
          </template>
        </el-table-column>
        <el-table-column prop="plannedDate" :label="t('inbound.slip.plannedDate')" width="120" />
        <el-table-column prop="partnerName" :label="t('inbound.slip.partner')" min-width="160" />
        <el-table-column
          prop="lineCount"
          :label="t('inbound.slip.lineCount')"
          width="80"
          align="center"
        />
        <el-table-column :label="t('inbound.slip.status')" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('inbound.slip.createdAt')" width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
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
import { useInboundSlipList } from '@/composables/inbound/useInboundSlipList'
import { inboundStatusLabel, inboundStatusTagType, formatDateTime } from '@/utils/inboundFormatters'

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
  isViewer,
  fetchList,
  fetchPartnerOptions,
  handleSearch,
  handleReset,
  handlePageChange,
  handleSizeChange,
} = useInboundSlipList()

function statusLabel(status: string): string {
  return inboundStatusLabel(status, t)
}

function statusTagType(status: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  return inboundStatusTagType(status)
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

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
