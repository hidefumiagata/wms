<template>
  <div class="wms-page">
    <el-card v-loading="loading">
      <!-- サマリーバー -->
      <template #header>
        <div class="card-header">
          <div>
            <span class="title">{{ t('inventory.stocktakeConfirmTitle') }}</span>
            <span v-if="header" class="header-id">{{ header.stocktakeNumber }}</span>
          </div>
        </div>
        <div v-if="header" class="summary-bar">
          <span>{{ header.targetDescription }}</span>
          <span style="margin-left: 16px">
            {{ t('inventory.stocktakeDate') }}: {{ formatDate(header.startedAt) }}
          </span>
          <el-tag
            :type="header.status === 'CONFIRMED' ? 'success' : 'warning'"
            size="small"
            style="margin-left: 8px"
          >
            {{
              header.status === 'CONFIRMED'
                ? t('inventory.stocktakeStatusConfirmed')
                : t('inventory.stocktakeStatusStarted')
            }}
          </el-tag>
        </div>
      </template>

      <!-- 差異サマリー -->
      <div class="diff-summary">
        <el-card shadow="never" class="summary-card summary-ok">
          <div class="summary-label">{{ t('inventory.stocktakeDiffNone') }}</div>
          <div class="summary-value">{{ noDiffCount }}</div>
        </el-card>
        <el-card shadow="never" class="summary-card summary-ng">
          <div class="summary-label">{{ t('inventory.stocktakeDiffExists') }}</div>
          <div class="summary-value">{{ diffCount }}</div>
        </el-card>
        <el-card shadow="never" class="summary-card summary-total">
          <div class="summary-label">{{ t('inventory.stocktakeTotalLines') }}</div>
          <div class="summary-value">{{ totalCount }}</div>
        </el-card>
      </div>

      <!-- フィルタ -->
      <div class="filter-bar">
        <!-- TODO: RPT-011 棚卸結果レポート出力ボタン -->
        <el-checkbox v-model="showDiffOnly">
          {{ t('inventory.stocktakeDiffOnlyFilter') }}
        </el-checkbox>
      </div>

      <!-- 差異テーブル -->
      <el-table
        :data="filteredLines"
        stripe
        border
        style="width: 100%"
        :row-class-name="rowClassName"
      >
        <el-table-column prop="locationCode" :label="t('inventory.locationCode')" width="180" />
        <el-table-column prop="productName" :label="t('inventory.productName')" min-width="140" />
        <el-table-column :label="t('inventory.unitType')" width="80" align="center">
          <template #default="{ row }">
            {{ unitTypeLabelFn(row.unitType) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('inventory.stocktakeBeforeQty')" width="100" align="right">
          <template #default="{ row }">
            {{ row.quantityBefore }}
          </template>
        </el-table-column>
        <el-table-column :label="t('inventory.stocktakeActualQty')" width="100" align="right">
          <template #default="{ row }">
            {{ row.quantityCounted ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="t('inventory.stocktakeDiffQty')" width="100" align="right">
          <template #default="{ row }">
            <span :class="diffClass(row.quantityDiff)">
              {{ formatDiff(row.quantityDiff) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="t('inventory.stocktakeDiffRate')" width="100" align="right">
          <template #default="{ row }">
            <span :class="diffClass(row.quantityDiff)">
              {{ diffRate(row) }}
            </span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="t('common.noData')" />
        </template>
      </el-table>

      <!-- ボタン -->
      <div class="form-actions">
        <el-button @click="goBackToInput">
          {{ t('inventory.stocktakeBackToInput') }}
        </el-button>
        <el-button
          v-if="header?.status === 'STARTED'"
          type="primary"
          :loading="confirming"
          @click="confirmStocktake"
        >
          {{ t('inventory.stocktakeConfirmAction') }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useStocktakeConfirm } from '@/composables/inventory/useStocktakeConfirm'
import { unitTypeLabel, formatDate } from '@/utils/inventoryFormatters'

const { t } = useI18n()

const {
  loading,
  confirming,
  header,
  showDiffOnly,
  diffCount,
  noDiffCount,
  totalCount,
  filteredLines,
  fetchDetail,
  diffRate,
  formatDiff,
  confirmStocktake,
  goBackToInput,
} = useStocktakeConfirm()

function unitTypeLabelFn(unitType: string): string {
  return unitTypeLabel(unitType, t)
}

function diffClass(diff: number | null | undefined): string {
  if (diff == null || diff === 0) return ''
  return diff > 0 ? 'diff-positive' : 'diff-negative'
}

function rowClassName({ row }: { row: { quantityDiff?: number | null } }): string {
  if (row.quantityDiff != null && row.quantityDiff !== 0) return 'diff-row'
  return ''
}

onMounted(() => {
  fetchDetail()
})
</script>

<style scoped lang="scss">
.wms-page {
  padding: 20px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.title {
  font-size: 16px;
  font-weight: 600;
}
.header-id {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
}
.summary-bar {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.diff-summary {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.summary-card {
  flex: 1;
  text-align: center;
  padding: 8px;
}
.summary-ok {
  border-left: 3px solid var(--el-color-success);
}
.summary-ng {
  border-left: 3px solid var(--el-color-warning);
}
.summary-total {
  border-left: 3px solid var(--el-color-info);
}
.summary-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.summary-value {
  font-size: 24px;
  font-weight: 700;
}
.filter-bar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}
.diff-positive {
  color: var(--el-color-primary);
  font-weight: 600;
}
.diff-negative {
  color: var(--el-color-danger);
  font-weight: 600;
}
:deep(.diff-row) {
  background-color: #fff3e0 !important;
}
</style>
