<template>
  <div class="wms-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ t('inventory.correctionTitle') }}</span>
          <!-- TODO: RPT-009 訂正一覧レポートボタン -->
        </div>
      </template>

      <!-- 対象在庫の選択 -->
      <el-divider content-position="left">{{ t('inventory.product') }}</el-divider>
      <el-form label-width="160px">
        <el-form-item :label="t('inventory.locationCode')" required>
          <el-input
            v-model="locationCode"
            style="width: 280px"
            @keyup.enter="fetchInventory"
          />
          <el-button style="margin-left: 8px" @click="fetchInventory">
            {{ t('common.search') }}
          </el-button>
        </el-form-item>
        <el-form-item :label="t('inventory.product')" required>
          <el-select
            v-model="selectedProductId"
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
        <el-form-item :label="t('inventory.unitType')" required>
          <el-select
            v-model="selectedUnitType"
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
      </el-form>

      <!-- 現在在庫・訂正情報 -->
      <template v-if="selectedInventory">
        <el-divider content-position="left">{{ t('inventory.correctionTitle') }}</el-divider>
        <el-form label-width="160px">
          <el-form-item :label="t('inventory.currentQty')">
            <span>{{ formatNumber(selectedInventory.quantity) }}</span>
          </el-form-item>
          <el-form-item :label="t('inventory.allocatedQty')">
            <span :class="{ 'has-allocated': selectedInventory.allocatedQty > 0 }">
              {{ selectedInventory.allocatedQty > 0 ? formatNumber(selectedInventory.allocatedQty) : '-' }}
            </span>
          </el-form-item>
          <el-form-item :label="t('inventory.newQty')" required>
            <el-input-number
              v-model="newQty"
              :min="0"
              style="width: 160px"
            />
          </el-form-item>
          <el-form-item :label="t('inventory.diff')">
            <span :class="diffClass">
              {{ diff != null ? (diff > 0 ? '+' : '') + formatNumber(diff) : '-' }}
            </span>
          </el-form-item>
          <el-form-item :label="t('inventory.reason')" required>
            <el-input
              v-model="reason"
              type="textarea"
              :rows="3"
              :maxlength="200"
              show-word-limit
              :placeholder="t('inventory.reasonPlaceholder')"
              style="width: 400px"
            />
          </el-form-item>
        </el-form>
      </template>

      <!-- ボタン -->
      <div class="form-actions">
        <el-button @click="goBack">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="!selectedInventory || newQty == null || !reason.trim()"
          @click="submitCorrection"
        >
          {{ t('common.save') }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useInventoryCorrection } from '@/composables/inventory/useInventoryCorrection'
import { unitTypeLabel, formatNumber } from '@/utils/inventoryFormatters'

const { t } = useI18n()

const {
  locationCode,
  selectedProductId,
  selectedUnitType,
  selectedInventory,
  newQty,
  reason,
  diff,
  submitting,
  productOptions,
  unitTypeOptions,
  fetchInventory,
  onProductChange,
  onUnitTypeChange,
  submitCorrection,
  goBack,
} = useInventoryCorrection()

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
</style>
