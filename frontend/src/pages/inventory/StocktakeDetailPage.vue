<template>
  <div class="wms-page">
    <el-card v-loading="loading">
      <!-- サマリーバー -->
      <template #header>
        <div class="card-header">
          <div>
            <span class="title">{{ t('inventory.stocktakeDetailTitle') }}</span>
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

      <!-- ツールバー -->
      <div class="toolbar">
        <!-- TODO: RPT-010 棚卸リスト出力ボタン -->
        <span class="counter">
          {{
            t('inventory.stocktakeUncountedCount', { uncounted: uncountedCount, total: totalCount })
          }}
        </span>
      </div>

      <!-- 実数入力テーブル -->
      <el-table :data="lines" stripe border style="width: 100%" :row-class-name="rowClassName">
        <el-table-column prop="locationCode" :label="t('inventory.locationCode')" width="180" />
        <el-table-column prop="productCode" :label="t('inventory.productCode')" width="120" />
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
        <el-table-column :label="t('inventory.stocktakeActualQty')" width="120" align="center">
          <template #default="{ row }">
            <el-input-number
              v-if="header?.status === 'STARTED'"
              v-model="row.editQty"
              :min="0"
              size="small"
              controls-position="right"
              style="width: 100px"
              @change="onActualQtyChange(row)"
            />
            <span v-else>{{ row.quantityCounted ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('inventory.stocktakeInputStatus')" width="60" align="center">
          <template #default="{ row }">
            <el-tag v-if="lineStatus(row) === 'uncounted'" type="info" size="small">
              {{ t('inventory.stocktakeStatusNotCounted') }}
            </el-tag>
            <el-tag v-else-if="lineStatus(row) === 'diff'" type="warning" size="small">
              {{ t('inventory.stocktakeStatusDiff') }}
            </el-tag>
            <el-tag v-else type="success" size="small">
              {{ t('inventory.stocktakeStatusMatch') }}
            </el-tag>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="t('common.noData')" />
        </template>
      </el-table>

      <!-- ボタン -->
      <div class="form-actions">
        <el-button @click="goBack">{{ t('inventory.backToList') }}</el-button>
        <el-button
          v-if="header?.status === 'STARTED'"
          type="info"
          :loading="saving"
          :disabled="!hasDirtyLines"
          @click="saveLines"
        >
          {{ t('inventory.stocktakeSave') }}
        </el-button>
        <el-button
          v-if="header?.status === 'STARTED'"
          type="primary"
          :disabled="!allCounted"
          @click="goToConfirm"
        >
          {{ t('inventory.stocktakeGoConfirm') }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useStocktakeDetail } from '@/composables/inventory/useStocktakeDetail'
import { unitTypeLabel, formatDate } from '@/utils/inventoryFormatters'

const { t } = useI18n()

const {
  loading,
  saving,
  header,
  lines,
  uncountedCount,
  totalCount,
  allCounted,
  fetchDetail,
  onActualQtyChange,
  lineStatus,
  saveLines,
  goToConfirm,
  goBack,
  hasDirtyLines,
} = useStocktakeDetail()

function unitTypeLabelFn(unitType: string): string {
  return unitTypeLabel(unitType, t)
}

function rowClassName({ row }: { row: Parameters<typeof lineStatus>[0] }): string {
  return lineStatus(row) === 'diff' ? 'diff-row' : ''
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
.toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
.counter {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}
:deep(.diff-row) {
  background-color: #fff3e0 !important;
}
</style>
