<template>
  <div class="wms-page">
    <el-card v-loading="initialLoading">
      <template #header>
        <div class="form-header">
          <el-button :icon="ArrowLeft" text @click="handleCancel">
            {{ t('common.back') }}
          </el-button>
          <span class="form-title">
            {{ isEdit ? t('master.area.edit') : t('master.area.create') }}
          </span>
        </div>
      </template>

      <el-form
        label-width="160px"
        style="max-width: 640px"
        @submit.prevent="handleSubmit"
      >
        <!-- 倉庫コード -->
        <el-form-item :label="t('master.area.warehouseCode')">
          <span class="readonly-value">
            {{ isEdit ? warehouseCode : warehouseStore.selectedWarehouse?.warehouseName }}
            {{ isEdit ? '' : `(${warehouseStore.selectedWarehouse?.warehouseCode ?? ''})` }}
          </span>
        </el-form-item>

        <!-- 棟コード -->
        <el-form-item :label="t('master.area.buildingCode')" :error="errors.buildingId">
          <el-select
            v-if="!isEdit"
            v-model="buildingId"
            v-bind="buildingIdAttrs"
            :placeholder="t('master.area.selectBuilding')"
            style="width: 100%"
          >
            <el-option
              v-for="b in buildings"
              :key="b.id"
              :label="`${b.buildingName} (${b.buildingCode})`"
              :value="b.id"
            />
          </el-select>
          <span v-else class="readonly-value">{{ buildingCode }}</span>
        </el-form-item>

        <!-- エリアコード -->
        <el-form-item :label="t('master.area.areaCode')" :error="errors.areaCode">
          <el-input
            v-if="!isEdit"
            v-model="areaCode"
            v-bind="areaCodeAttrs"
            :placeholder="t('master.area.codePlaceholder')"
            maxlength="10"
            show-word-limit
          />
          <span v-else class="readonly-value">{{ areaCode }}</span>
          <div v-if="!isEdit" class="form-hint">
            {{ t('master.area.codeHint') }}
          </div>
        </el-form-item>

        <!-- エリア名称 -->
        <el-form-item :label="t('master.area.areaName')" :error="errors.areaName">
          <el-input
            v-model="areaName"
            v-bind="areaNameAttrs"
            :placeholder="t('master.area.namePlaceholder')"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <!-- 保管条件 -->
        <el-form-item :label="t('master.area.storageCondition')" :error="errors.storageCondition">
          <el-radio-group v-model="storageCondition" v-bind="storageConditionAttrs">
            <el-radio value="AMBIENT">{{ t('master.area.storageAmbient') }}</el-radio>
            <el-radio value="REFRIGERATED">{{ t('master.area.storageRefrigerated') }}</el-radio>
            <el-radio value="FROZEN">{{ t('master.area.storageFrozen') }}</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- エリア種別 -->
        <el-form-item :label="t('master.area.areaType')" :error="errors.areaType">
          <el-select
            v-if="!isEdit"
            v-model="areaType"
            v-bind="areaTypeAttrs"
            style="width: 200px"
          >
            <el-option :label="t('master.area.typeStock')" value="STOCK" />
            <el-option :label="t('master.area.typeInbound')" value="INBOUND" />
            <el-option :label="t('master.area.typeOutbound')" value="OUTBOUND" />
            <el-option :label="t('master.area.typeReturn')" value="RETURN" />
          </el-select>
          <span v-else class="readonly-value">{{ areaTypeLabel(areaType) }}</span>
        </el-form-item>

        <!-- ボタン -->
        <el-form-item>
          <el-button @click="handleCancel">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" native-type="submit" :loading="loading">
            {{ isEdit ? t('common.save') : t('master.area.register') }}
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
import { useAreaForm } from '@/composables/master/useAreaForm'

const { t } = useI18n()

const {
  buildingId,
  buildingIdAttrs,
  areaCode,
  areaCodeAttrs,
  areaName,
  areaNameAttrs,
  storageCondition,
  storageConditionAttrs,
  areaType,
  areaTypeAttrs,
  buildings,
  errors,
  loading,
  initialLoading,
  isEdit,
  warehouseCode,
  buildingCode,
  warehouseStore,
  fetchBuildings,
  fetchArea,
  handleSubmit,
  handleCancel,
} = useAreaForm()

function areaTypeLabel(val: string) {
  const map: Record<string, string> = {
    STOCK: t('master.area.typeStock'),
    INBOUND: t('master.area.typeInbound'),
    OUTBOUND: t('master.area.typeOutbound'),
    RETURN: t('master.area.typeReturn'),
  }
  return map[val] ?? val
}

onMounted(() => {
  if (isEdit.value) {
    fetchArea()
  } else {
    fetchBuildings()
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
