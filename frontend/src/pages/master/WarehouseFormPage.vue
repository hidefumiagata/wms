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
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="160px"
        style="max-width: 640px"
        @submit.prevent="handleSubmit"
      >
        <!-- 倉庫コード -->
        <el-form-item :label="t('master.warehouse.warehouseCode')" prop="warehouseCode">
          <el-input
            v-if="!isEdit"
            v-model="form.warehouseCode"
            :placeholder="t('master.warehouse.codePlaceholder')"
            maxlength="4"
            show-word-limit
            @blur="checkCodeExists"
          />
          <span v-else class="readonly-value">{{ form.warehouseCode }}</span>
        </el-form-item>

        <!-- 倉庫名 -->
        <el-form-item :label="t('master.warehouse.warehouseName')" prop="warehouseName">
          <el-input
            v-model="form.warehouseName"
            :placeholder="t('master.warehouse.warehouseName')"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <!-- 倉庫名カナ -->
        <el-form-item :label="t('master.warehouse.warehouseNameKana')" prop="warehouseNameKana">
          <el-input
            v-model="form.warehouseNameKana"
            :placeholder="t('master.warehouse.kanaPlaceholder')"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <!-- 住所 -->
        <el-form-item :label="t('master.warehouse.address')" prop="address">
          <el-input
            v-model="form.address"
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
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useWarehouseForm } from '@/composables/master/useWarehouseForm'

const { t } = useI18n()

const formRef = ref<FormInstance>()

const {
  form,
  rules,
  loading,
  initialLoading,
  isEdit,
  fetchWarehouse,
  handleSubmit,
  handleCancel,
  checkCodeExists,
} = useWarehouseForm(formRef)

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
