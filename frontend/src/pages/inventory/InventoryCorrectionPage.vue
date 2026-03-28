<template>
  <div class="wms-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ t('inventory.correctionTitle') }}</span>
          <!-- TODO: RPT-009 訂正一覧レポートボタン -->
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="160px">
        <!-- 対象在庫の選択 -->
        <el-divider content-position="left">{{ t('inventory.product') }}</el-divider>
        <el-form-item :label="t('inventory.locationCode')" prop="locationCode">
          <el-input
            v-model="form.locationCode"
            style="width: 280px"
            @keyup.enter="fetchInventory"
          />
          <el-button style="margin-left: 8px" @click="fetchInventory">
            {{ t('common.search') }}
          </el-button>
        </el-form-item>
        <el-form-item :label="t('inventory.product')" prop="selectedProductId">
          <el-select
            v-model="form.selectedProductId"
            style="width: 320px"
            filterable
            :placeholder="t('inventory.product')"
            @change="onProductChange"
          >
            <el-option
              v-for="p in productOptions"
              :key="p.productId"
              :label="`${p.productCode} ${p.productName}`"
              :value="p.productId"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('inventory.unitType')" prop="selectedUnitType">
          <el-select
            v-model="form.selectedUnitType"
            style="width: 160px"
            :placeholder="t('inventory.unitType')"
            @change="onUnitTypeChange"
          >
            <el-option
              v-for="u in unitTypeOptions"
              :key="u.unitType"
              :label="unitTypeLabelFn(u.unitType)"
              :value="u.unitType"
            />
          </el-select>
        </el-form-item>

        <!-- 現在在庫・訂正情報 -->
        <template v-if="selectedInventory">
          <el-divider content-position="left">{{ t('inventory.correctionTitle') }}</el-divider>
          <el-form-item :label="t('inventory.currentQty')">
            <span>{{ formatNumber(selectedInventory.quantity) }}</span>
          </el-form-item>
          <el-form-item :label="t('inventory.allocatedQty')">
            <span :class="{ 'has-allocated': selectedInventory.allocatedQty > 0 }">
              {{
                selectedInventory.allocatedQty > 0
                  ? formatNumber(selectedInventory.allocatedQty)
                  : '-'
              }}
            </span>
          </el-form-item>
          <el-form-item :label="t('inventory.newQty')" prop="newQty">
            <el-input-number v-model="form.newQty" :min="0" style="width: 160px" />
          </el-form-item>
          <el-form-item :label="t('inventory.diff')">
            <span :class="diffClass">
              {{ diff != null ? (diff > 0 ? '+' : '') + formatNumber(diff) : '-' }}
            </span>
          </el-form-item>
          <el-form-item :label="t('inventory.reason')" prop="reason">
            <el-input
              v-model="form.reason"
              type="textarea"
              :rows="3"
              :maxlength="200"
              show-word-limit
              :placeholder="t('inventory.reasonPlaceholder')"
              style="width: 400px"
            />
          </el-form-item>
        </template>
      </el-form>

      <!-- 訂正履歴（直近5件） -->
      <template v-if="selectedInventory">
        <el-divider content-position="left">{{ t('inventory.correctionHistory') }}</el-divider>
        <el-table
          v-if="correctionHistory.length > 0"
          :data="correctionHistory"
          size="small"
          stripe
          style="max-width: 700px"
        >
          <el-table-column
            :label="t('inventory.correctionHistoryDate')"
            prop="correctedAt"
            width="170"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.correctedAt) }}
            </template>
          </el-table-column>
          <el-table-column
            :label="t('inventory.correctionHistoryBefore')"
            prop="quantityBefore"
            width="90"
            align="right"
          >
            <template #default="{ row }">{{ formatNumber(row.quantityBefore) }}</template>
          </el-table-column>
          <el-table-column
            :label="t('inventory.correctionHistoryAfter')"
            prop="quantityAfter"
            width="90"
            align="right"
          >
            <template #default="{ row }">{{ formatNumber(row.quantityAfter) }}</template>
          </el-table-column>
          <el-table-column
            :label="t('inventory.correctionHistoryReason')"
            prop="reason"
            min-width="150"
          />
          <el-table-column
            :label="t('inventory.correctionHistoryExecutor')"
            prop="executedByName"
            width="120"
          />
        </el-table>
        <p v-else class="history-empty">{{ t('inventory.correctionHistoryEmpty') }}</p>
      </template>

      <!-- ボタン -->
      <div class="form-actions">
        <el-button @click="goBack">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="!selectedInventory || form.newQty == null || !form.reason.trim()"
          @click="submitCorrection"
        >
          {{ t('common.save') }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance } from 'element-plus'
import { useInventoryCorrection } from '@/composables/inventory/useInventoryCorrection'
import { unitTypeLabel, formatNumber } from '@/utils/inventoryFormatters'

const { t } = useI18n()
const formRef = ref<FormInstance>()

const {
  form,
  rules,
  selectedInventory,
  diff,
  submitting,
  productOptions,
  unitTypeOptions,
  correctionHistory,
  fetchInventory,
  onProductChange,
  onUnitTypeChange,
  submitCorrection,
  goBack,
} = useInventoryCorrection(formRef)

function formatDateTime(dateStr: string): string {
  const d = new Date(dateStr)
  return d.toLocaleString('ja-JP', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function unitTypeLabelFn(unitType: string): string {
  return unitTypeLabel(unitType, t)
}

const diffClass = computed(() => {
  if (diff.value == null) return 'diff-neutral'
  if (diff.value > 0) return 'diff-increase'
  if (diff.value < 0) return 'diff-decrease'
  return 'diff-neutral'
})
</script>

<style scoped lang="scss">
.wms-page {
  padding: 20px;
  max-width: 800px;
}
.card-header {
  font-size: 16px;
  font-weight: 600;
}
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 24px;
}
.has-allocated {
  color: var(--el-color-warning);
  font-weight: 600;
}
.diff-increase {
  color: var(--el-color-primary);
  font-weight: 600;
}
.diff-decrease {
  color: var(--el-color-danger);
  font-weight: 600;
}
.diff-neutral {
  color: var(--el-text-color-secondary);
}
.history-empty {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin: 8px 0;
}
</style>
