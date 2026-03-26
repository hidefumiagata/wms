<template>
  <div class="wms-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ t('inventory.moveTitle') }}</span>
        </div>
      </template>

      <!-- 移動元 -->
      <el-divider content-position="left">{{ t('inventory.fromLocation') }}</el-divider>
      <el-form label-width="160px">
        <el-form-item :label="t('inventory.fromLocationCode')" required>
          <el-input
            v-model="fromLocationCode"
            style="width: 280px"
            @keyup.enter="fetchFromInventory"
            @blur="fetchFromInventory"
          />
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
          >
            <el-option
              v-for="u in unitTypeOptions"
              :key="u.unitType"
              :label="unitTypeLabelFn(u.unitType)"
              :value="u.unitType"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="fromInventory" :label="t('inventory.currentQty')">
          <span>{{ formatNumber(fromInventory.quantity) }}</span>
        </el-form-item>
        <el-form-item v-if="fromInventory" :label="t('inventory.availableQty')">
          <span :class="{ 'has-allocated': fromInventory.allocatedQty > 0 }">
            {{ formatNumber(fromInventory.availableQty) }}
          </span>
          <span v-if="fromInventory.allocatedQty > 0" class="alloc-hint">
            （{{ t('inventory.allocatedQty') }}: {{ fromInventory.allocatedQty }}）
          </span>
        </el-form-item>
      </el-form>

      <!-- 移動先 -->
      <el-divider content-position="left">{{ t('inventory.toLocation') }}</el-divider>
      <el-form label-width="160px">
        <el-form-item :label="t('inventory.toLocationCode')" required>
          <el-input
            v-model="toLocationCode"
            style="width: 280px"
            @keyup.enter="fetchToLocationInfo"
            @blur="fetchToLocationInfo"
          />
        </el-form-item>
        <el-form-item v-if="toCurrentQty != null" :label="t('inventory.currentQty')">
          <span>{{ formatNumber(toCurrentQty) }}</span>
        </el-form-item>
      </el-form>

      <!-- 移動数量 -->
      <el-divider content-position="left">{{ t('inventory.moveQty') }}</el-divider>
      <el-form label-width="160px">
        <el-form-item :label="t('inventory.moveQty')" required>
          <el-input-number
            v-model="moveQty"
            :min="1"
            :max="fromInventory?.availableQty ?? 1"
            style="width: 160px"
          />
        </el-form-item>
      </el-form>

      <!-- ボタン -->
      <div class="form-actions">
        <el-button @click="goBack">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="!fromInventory || !toLocationId || moveQty < 1"
          @click="submitMove"
        >
          {{ t('common.save') }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useInventoryMove } from '@/composables/inventory/useInventoryMove'
import { unitTypeLabel, formatNumber } from '@/utils/inventoryFormatters'

const { t } = useI18n()

const {
  fromLocationCode,
  selectedProductId,
  selectedUnitType,
  fromInventory,
  toLocationCode,
  toLocationId,
  toCurrentQty,
  moveQty,
  submitting,
  productOptions,
  unitTypeOptions,
  onProductChange,
  fetchFromInventory,
  fetchToLocationInfo,
  submitMove,
  initFromRoute,
  goBack,
} = useInventoryMove()

function unitTypeLabelFn(unitType: string): string {
  return unitTypeLabel(unitType, t)
}

onMounted(() => {
  initFromRoute()
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
.alloc-hint {
  color: var(--el-color-warning);
  margin-left: 8px;
  font-size: 12px;
}
</style>
