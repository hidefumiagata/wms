<template>
  <div class="wms-page">
    <el-card v-loading="initialLoading">
      <template #header>
        <div class="form-header">
          <el-button :icon="ArrowLeft" text @click="handleCancel">
            {{ t('common.back') }}
          </el-button>
          <span class="form-title">
            {{ isEdit ? t('master.warehouse.edit') : t('master.warehouse.create') }}
          </span>
        </div>
      </template>

      <el-form
        label-width="160px"
        style="max-width: 640px"
        @submit.prevent="handleSubmit"
      >
        <!-- 倉庫コード -->
        <el-form-item
          :label="t('master.warehouse.warehouseCode')"
          :error="errors.warehouseCode"
        >
          <el-input
            v-if="!isEdit"
            v-model="warehouseCode"
            v-bind="warehouseCodeAttrs"
            :placeholder="t('master.warehouse.codePlaceholder')"
            maxlength="4"
            show-word-limit
            @blur="checkCodeExists"
          />
          <span v-else class="readonly-value">{{ warehouseCode }}</span>
        </el-form-item>

        <!-- 倉庫名 -->
        <el-form-item
          :label="t('master.warehouse.warehouseName')"
          :error="errors.warehouseName"
        >
          <el-input
            v-model="warehouseName"
            v-bind="warehouseNameAttrs"
            :placeholder="t('master.warehouse.warehouseName')"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <!-- 倉庫名カナ -->
        <el-form-item
          :label="t('master.warehouse.warehouseNameKana')"
          :error="errors.warehouseNameKana"
        >
          <el-input
            v-model="warehouseNameKana"
            v-bind="warehouseNameKanaAttrs"
            :placeholder="t('master.warehouse.kanaPlaceholder')"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <!-- 住所 -->
        <el-form-item
          :label="t('master.warehouse.address')"
          :error="errors.address"
        >
          <el-input
            v-model="address"
            v-bind="addressAttrs"
            type="textarea"
            :rows="3"
            :placeholder="t('master.warehouse.addressPlaceholder')"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <!-- ボタン -->
        <el-form-item>
          <el-button @click="handleCancel">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" native-type="submit" :loading="loading">
            {{ isEdit ? t('common.save') : t('master.warehouse.register') }}
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
import { useWarehouseForm } from '@/composables/master/useWarehouseForm'

const { t } = useI18n()

const {
  warehouseCode,
  warehouseCodeAttrs,
  warehouseName,
  warehouseNameAttrs,
  warehouseNameKana,
  warehouseNameKanaAttrs,
  address,
  addressAttrs,
  errors,
  loading,
  initialLoading,
  isEdit,
  fetchWarehouse,
  handleSubmit,
  handleCancel,
  checkCodeExists,
} = useWarehouseForm()

onMounted(() => {
  if (isEdit.value) fetchWarehouse()
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
</style>
