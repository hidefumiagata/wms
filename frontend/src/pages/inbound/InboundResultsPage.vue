<template>
  <div class="wms-page">
    <!-- 絞り込みフォーム -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline @submit.prevent="handleSearch">
        <el-form-item :label="t('inbound.results.storedDateFrom')">
          <el-date-picker
            v-model="searchForm.storedDateFrom"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 160px"
            clearable
          />
        </el-form-item>
        <el-form-item :label="t('inbound.results.storedDateTo')">
          <el-date-picker
            v-model="searchForm.storedDateTo"
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
        <el-form-item :label="t('inbound.slip.slipNumber')">
          <el-input
            v-model="searchForm.slipNumber"
            :placeholder="t('inbound.slip.slipNumber')"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item :label="t('inbound.slip.productCode')">
          <el-input
            v-model="searchForm.productCode"
            :placeholder="t('inbound.slip.productCode')"
            clearable
            style="width: 140px"
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

    <!-- 実績テーブル -->
    <el-card class="table-card">
      <template #header>
        <span>{{ t('inbound.results.title') }}</span>
      </template>

      <el-table v-loading="loading" :data="items" stripe border style="width: 100%">
        <el-table-column prop="slipNumber" :label="t('inbound.slip.slipNumber')" width="180">
          <template #default="{ row }">
            <el-link
              type="primary"
              @click="router.push({ name: 'inbound-slip-detail', params: { id: row.slipId } })"
            >
              {{ row.slipNumber }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="storedAt" :label="t('inbound.results.storedDate')" width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.storedAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="partnerName" :label="t('inbound.slip.partner')" width="140" />
        <el-table-column prop="productCode" :label="t('inbound.slip.productCode')" width="120" />
        <el-table-column prop="productName" :label="t('inbound.slip.productName')" min-width="140" />
        <el-table-column prop="plannedQty" :label="t('inbound.slip.plannedQty')" width="90" align="right" />
        <el-table-column prop="inspectedQty" :label="t('inbound.inspect.inspectedQty')" width="90" align="right" />
        <el-table-column :label="t('inbound.inspect.diffQty')" width="80" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.diffQty !== 0 }">
              {{ row.diffQty }}
            </span>
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
import { Search, Refresh } from '@element-plus/icons-vue'
import { useInboundResults } from '@/composables/inbound/useInboundResults'
import { formatDateTime } from '@/utils/inboundFormatters'

const { t } = useI18n()
const router = useRouter()

const {
  items, loading, total, page, pageSize, searchForm, partnerOptions,
  fetchList, fetchPartnerOptions, handleSearch, handleReset, handlePageChange, handleSizeChange,
} = useInboundResults()

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

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.text-danger {
  color: var(--el-color-danger);
  font-weight: bold;
}
</style>
