<template>
  <div class="wms-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ t('inventory.breakdownTitle') }}</span>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="180px">
        <!-- ばらし元 -->
        <el-divider content-position="left">{{ t('inventory.fromLocation') }}</el-divider>
        <el-form-item :label="t('inventory.fromLocationCode')" prop="fromLocationCode">
          <el-input
            v-model="form.fromLocationCode"
            style="width: 280px"
            @keyup.enter="fetchFromInventory"
            @blur="fetchFromInventory"
          />
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
        <el-form-item :label="t('inventory.fromUnitType')" prop="fromUnitType">
          <el-select
            v-model="form.fromUnitType"
            style="width: 160px"
            :placeholder="t('inventory.unitType')"
            @change="onFromUnitTypeChange"
          >
            <el-option
              v-for="u in fromUnitTypeOptions"
              :key="u.unitType"
              :label="unitTypeLabelFn(u.unitType)"
              :value="u.unitType"
            />
          </el-select>
          <span v-if="fromInventory" class="inline-info">
            {{ t('inventory.currentQty') }}: {{ formatNumber(fromInventory.quantity) }} /
            {{ t('inventory.availableQty') }}: {{ formatNumber(fromInventory.availableQty) }}
          </span>
        </el-form-item>
        <el-form-item :label="t('inventory.breakdownQty')" prop="breakdownQty">
          <el-input-number
            v-model="form.breakdownQty"
            :min="1"
            :max="fromInventory?.availableQty ?? 1"
            style="width: 160px"
          />
        </el-form-item>

        <!-- 変換プレビュー -->
        <el-divider content-position="left">{{ t('inventory.conversionPreview') }}</el-divider>
        <el-form-item :label="t('inventory.toUnitType')" prop="toUnitType">
          <el-select
            v-model="form.toUnitType"
            style="width: 160px"
            :placeholder="t('inventory.unitType')"
          >
            <el-option
              v-for="ut in toUnitTypeOptions"
              :key="ut"
              :label="unitTypeLabelFn(ut)"
              :value="ut"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="convertedQty != null" :label="t('inventory.convertedQty')">
          <span class="preview-result">
            {{ form.breakdownQty }}
            {{ form.fromUnitType ? unitTypeLabelFn(form.fromUnitType) : '' }}
            →
            {{ formatNumber(convertedQty) }}
            {{ form.toUnitType ? unitTypeLabelFn(form.toUnitType) : '' }}
          </span>
        </el-form-item>
        <el-form-item v-if="conversionRate != null" :label="t('inventory.conversionRate')">
          <span class="rate-info">{{ conversionRate }}</span>
        </el-form-item>
        <el-form-item v-if="conversionRate != null && conversionRate <= 0">
          <el-alert type="error" :closable="false" show-icon>
            {{ t('inventory.conversionRateNotSet') }}
          </el-alert>
        </el-form-item>

        <!-- ばらし先ロケーション -->
        <el-divider content-position="left">{{ t('inventory.toLocationOptional') }}</el-divider>
        <el-form-item :label="t('inventory.toLocationOptional')">
          <el-input
            v-model="form.toLocationCode"
            style="width: 280px"
            :placeholder="t('inventory.toLocationSameHint')"
            @blur="fetchToLocationInfo"
          />
        </el-form-item>
        <el-form-item v-if="toCurrentQty != null" :label="t('inventory.toCurrentQty')">
          <span>{{ formatNumber(toCurrentQty) }}</span>
        </el-form-item>
      </el-form>

      <!-- ボタン -->
      <div class="form-actions">
        <el-button @click="goBack">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="!fromInventory || !form.toUnitType || !convertedQty"
          @click="submitBreakdown"
        >
          {{ t('common.save') }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance } from 'element-plus'
import { useInventoryBreakdown } from '@/composables/inventory/useInventoryBreakdown'
import { unitTypeLabel, formatNumber } from '@/utils/inventoryFormatters'

const { t } = useI18n()
const formRef = ref<FormInstance>()

const {
  form,
  rules,
  fromInventory,
  toCurrentQty,
  submitting,
  productOptions,
  fromUnitTypeOptions,
  toUnitTypeOptions,
  conversionRate,
  convertedQty,
  onProductChange,
  onFromUnitTypeChange,
  fetchFromInventory,
  fetchToLocationInfo,
  submitBreakdown,
  goBack,
} = useInventoryBreakdown(formRef)

function unitTypeLabelFn(unitType: string): string {
  return unitTypeLabel(unitType, t)
}
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
.inline-info {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.preview-result {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-color-primary);
}
.rate-info {
  color: var(--el-text-color-secondary);
}
</style>
