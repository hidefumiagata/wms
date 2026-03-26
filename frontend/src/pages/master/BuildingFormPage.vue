<template>
  <div class="wms-page">
    <el-card v-loading="initialLoading">
      <template #header>
        <div class="form-header">
          <el-button :icon="ArrowLeft" text @click="handleCancel">
            {{ t('common.back') }}
          </el-button>
          <span class="form-title">
            {{ isEdit ? t('master.building.edit') : t('master.building.create') }}
          </span>
        </div>
      </template>

      <el-form label-width="160px" style="max-width: 640px" @submit.prevent="handleSubmit">
        <!-- 倉庫コード -->
        <el-form-item :label="t('master.building.warehouseCode')">
          <span class="readonly-value">
            {{ isEdit ? warehouseCode : warehouseStore.selectedWarehouse?.warehouseName }}
            {{ isEdit ? '' : `(${warehouseStore.selectedWarehouse?.warehouseCode ?? ''})` }}
          </span>
        </el-form-item>

        <!-- 棟コード -->
        <el-form-item :label="t('master.building.buildingCode')" :error="errors.buildingCode">
          <el-input
            v-if="!isEdit"
            v-model="buildingCode"
            v-bind="buildingCodeAttrs"
            :placeholder="t('master.building.codePlaceholder')"
            maxlength="10"
            show-word-limit
          />
          <span v-else class="readonly-value">{{ buildingCode }}</span>
          <div v-if="!isEdit" class="form-hint">
            {{ t('master.building.codeHint') }}
          </div>
        </el-form-item>

        <!-- 棟名称 -->
        <el-form-item :label="t('master.building.buildingName')" :error="errors.buildingName">
          <el-input
            v-model="buildingName"
            v-bind="buildingNameAttrs"
            :placeholder="t('master.building.namePlaceholder')"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <!-- ボタン -->
        <el-form-item>
          <el-button @click="handleCancel">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" native-type="submit" :loading="loading">
            {{ isEdit ? t('common.save') : t('master.building.register') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useBuildingForm } from '@/composables/master/useBuildingForm'

const { t } = useI18n()

const {
  buildingCode,
  buildingCodeAttrs,
  buildingName,
  buildingNameAttrs,
  errors,
  loading,
  initialLoading,
  isEdit,
  warehouseCode,
  warehouseStore,
  fetchBuilding,
  handleSubmit,
  handleCancel,
} = useBuildingForm()

onMounted(() => {
  if (isEdit.value) fetchBuilding()
})
</script>

<style scoped lang="scss">
.wms-page {
  padding: 20px;
}

.form-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.form-title {
  font-size: 16px;
  font-weight: 600;
}

.readonly-value {
  display: inline-block;
  line-height: 32px;
  color: var(--el-text-color-regular);
  font-weight: 600;
}

.form-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-top: 4px;
}
</style>
