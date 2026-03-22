<template>
  <div class="wms-page">
    <el-card v-loading="initialLoading">
      <template #header>
        <div class="form-header">
          <el-button :icon="ArrowLeft" text @click="handleCancel">
            {{ t('common.back') }}
          </el-button>
          <span class="form-title">
            {{ isEdit ? t('master.location.edit') : t('master.location.create') }}
          </span>
        </div>
      </template>

      <el-form
        label-width="200px"
        style="max-width: 680px"
        @submit.prevent="handleSubmit"
      >
        <!-- 倉庫コード -->
        <el-form-item :label="t('master.location.warehouseCode')">
          <span class="readonly-value">
            {{ isEdit ? warehouseCode : warehouseStore.selectedWarehouse?.warehouseName }}
            {{ isEdit ? '' : `(${warehouseStore.selectedWarehouse?.warehouseCode ?? ''})` }}
          </span>
        </el-form-item>

        <!-- エリアコード -->
        <el-form-item :label="t('master.location.areaCode')" :error="errors.areaId">
          <el-select
            v-if="!isEdit"
            v-model="areaId"
            v-bind="areaIdAttrs"
            :placeholder="t('master.location.selectArea')"
            style="width: 100%"
          >
            <el-option
              v-for="a in areas"
              :key="a.id"
              :label="`${a.areaCode} (${a.areaName})`"
              :value="a.id"
            />
          </el-select>
          <span v-else class="readonly-value">{{ areaCode }}</span>
        </el-form-item>

        <!-- ロケーションコード -->
        <el-form-item :label="t('master.location.locationCode')" :error="errors.locationCode">
          <el-input
            v-if="!isEdit"
            v-model="locationCode"
            v-bind="locationCodeAttrs"
            :placeholder="t('master.location.codePlaceholder')"
            maxlength="30"
            show-word-limit
          />
          <span v-else class="readonly-value">{{ locationCode }}</span>
          <div v-if="!isEdit" class="form-hint">
            {{ t('master.location.codeHint') }}
          </div>
          <div v-if="!isEdit && selectedArea?.areaType === 'STOCK'" class="form-hint">
            {{ t('master.location.codeFormatHintStock') }}
          </div>
        </el-form-item>

        <!-- ロケーション名称 -->
        <el-form-item :label="t('master.location.locationName')" :error="errors.locationName">
          <el-input
            v-model="locationName"
            v-bind="locationNameAttrs"
            :placeholder="t('master.location.namePlaceholder')"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <!-- ボタン -->
        <el-form-item>
          <el-button @click="handleCancel">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" native-type="submit" :loading="loading">
            {{ isEdit ? t('common.save') : t('master.location.register') }}
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
import { useLocationForm } from '@/composables/master/useLocationForm'

const { t } = useI18n()

const {
  areaId,
  areaIdAttrs,
  locationCode,
  locationCodeAttrs,
  locationName,
  locationNameAttrs,
  areas,
  selectedArea,
  errors,
  loading,
  initialLoading,
  isEdit,
  warehouseCode,
  areaCode,
  warehouseStore,
  fetchAreas,
  fetchLocation,
  handleSubmit,
  handleCancel,
} = useLocationForm()

onMounted(() => {
  if (isEdit.value) {
    fetchLocation()
  } else {
    fetchAreas()
  }
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
