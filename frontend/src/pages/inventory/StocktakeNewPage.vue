<template>
  <div class="wms-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ t('inventory.stocktakeNewTitle') }}</span>
        </div>
      </template>

      <!-- 警告バナー -->
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 20px">
        {{ t('inventory.stocktakeLockWarning') }}
      </el-alert>

      <el-form label-width="180px">
        <!-- 棟 -->
        <el-form-item :label="t('inventory.stocktakeBuilding')" required>
          <el-select
            v-model="selectedBuildingId"
            style="width: 280px"
            filterable
            :placeholder="t('inventory.stocktakeSelectBuilding')"
            @change="onBuildingChange"
          >
            <el-option
              v-for="b in buildingOptions"
              :key="b.id"
              :label="b.buildingName"
              :value="b.id"
            />
          </el-select>
        </el-form-item>

        <!-- エリア -->
        <el-form-item :label="t('inventory.stocktakeArea')">
          <el-select
            v-model="selectedAreaId"
            style="width: 280px"
            clearable
            :disabled="!selectedBuildingId"
            :placeholder="t('inventory.stocktakeSelectArea')"
            @change="onAreaChange"
          >
            <el-option v-for="a in areaOptions" :key="a.id" :label="a.areaName" :value="a.id" />
          </el-select>
        </el-form-item>

        <!-- プレビュー -->
        <el-form-item
          v-if="targetLocationCount != null"
          :label="t('inventory.stocktakeTargetLocations')"
        >
          <el-card shadow="never" class="preview-card">
            <div class="preview-count">
              {{ targetLocationCount }} {{ t('inventory.stocktakeTargetLocations') }}
            </div>
          </el-card>
        </el-form-item>

        <!-- 実施日 -->
        <el-form-item :label="t('inventory.stocktakeDate')" required>
          <el-date-picker
            v-model="stocktakeDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 200px"
          />
        </el-form-item>

        <!-- 備考 -->
        <el-form-item :label="t('inventory.stocktakeNote')">
          <el-input
            v-model="note"
            type="textarea"
            :rows="3"
            :maxlength="200"
            show-word-limit
            :placeholder="t('inventory.stocktakeNotePlaceholder')"
            style="width: 400px"
          />
        </el-form-item>
      </el-form>

      <!-- ボタン -->
      <div class="form-actions">
        <!-- TODO: RPT-010 棚卸リスト出力ボタン -->
        <el-button @click="goBack">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="!selectedBuildingId || !stocktakeDate"
          @click="submitStart"
        >
          {{ t('inventory.stocktakeNew') }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useStocktakeForm } from '@/composables/inventory/useStocktakeForm'

const { t } = useI18n()

const {
  submitting,
  selectedBuildingId,
  selectedAreaId,
  stocktakeDate,
  note,
  buildingOptions,
  areaOptions,
  targetLocationCount,
  fetchBuildings,
  onBuildingChange,
  onAreaChange,
  submitStart,
  goBack,
} = useStocktakeForm()

onMounted(() => {
  fetchBuildings()
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
.preview-card {
  background: var(--el-fill-color-light);
}
.preview-count {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-color-primary);
}
</style>
