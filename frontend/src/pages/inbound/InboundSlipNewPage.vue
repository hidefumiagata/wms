<template>
  <div class="wms-page">
    <!-- ヘッダー情報 -->
    <el-card>
      <template #header>
        <span>{{ t('inbound.slip.newRegistration') }}</span>
      </template>

      <el-form label-width="120px" style="max-width: 640px">
        <el-form-item
          :label="t('inbound.slip.plannedDate')"
          required
          :error="errors.plannedDate"
        >
          <el-date-picker
            v-model="headerForm.plannedDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item :label="t('inbound.slip.slipType')">
          <span>{{ t('inbound.slip.typeNormal') }}</span>
        </el-form-item>

        <el-form-item
          :label="t('inbound.slip.partner')"
          required
          :error="errors.partnerId"
        >
          <el-select
            v-model="headerForm.partnerId"
            filterable
            :placeholder="t('inbound.slip.partnerAll')"
            style="width: 100%"
          >
            <el-option
              v-for="p in partnerOptions"
              :key="p.id"
              :label="p.partnerName"
              :value="p.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('inbound.slip.note')">
          <el-input
            v-model="headerForm.note"
            type="textarea"
            :rows="3"
            :maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 入荷明細 -->
    <el-card>
      <template #header>
        <div class="table-header">
          <span>{{ t('inbound.slip.lines') }}</span>
          <el-button type="primary" :icon="Plus" size="small" @click="addLine">
            {{ t('common.add') }}
          </el-button>
        </div>
      </template>

      <div v-if="errors.lines" class="line-error">
        <el-alert :title="errors.lines" type="error" show-icon :closable="false" />
      </div>

      <el-table :data="lines" stripe border style="width: 100%">
        <el-table-column :label="t('inbound.slip.lineNo')" width="60" align="center">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column :label="t('inbound.slip.productCode')" width="160">
          <template #default="{ row, $index }">
            <el-input
              v-model="row.productCode"
              size="small"
              :maxlength="20"
              @blur="handleProductCodeBlur(row)"
              @keyup.enter="handleProductCodeBlur(row)"
              :class="{ 'is-error': errors[`line_${$index}_productCode`] }"
            />
          </template>
        </el-table-column>
        <el-table-column :label="t('inbound.slip.productName')" min-width="160">
          <template #default="{ row }">
            <span>{{ row.productName || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('inbound.slip.unitType')" width="110">
          <template #default="{ row }">
            <el-select v-model="row.unitType" size="small">
              <el-option label="CASE" value="CASE" />
              <el-option label="BALL" value="BALL" />
              <el-option label="PIECE" value="PIECE" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column :label="t('inbound.slip.lotNumber')" width="140">
          <template #default="{ row, $index }">
            <el-input
              v-model="row.lotNumber"
              size="small"
              :maxlength="50"
              :disabled="!row.lotManageFlag"
              :placeholder="row.lotManageFlag ? '' : '—'"
              :class="{ 'is-error': errors[`line_${$index}_lotNumber`] }"
            />
          </template>
        </el-table-column>
        <el-table-column :label="t('inbound.slip.expiryDate')" width="150">
          <template #default="{ row, $index }">
            <el-date-picker
              v-model="row.expiryDate"
              type="date"
              value-format="YYYY-MM-DD"
              size="small"
              :disabled="!row.expiryManageFlag"
              :placeholder="row.expiryManageFlag ? '' : '—'"
              style="width: 100%"
              :class="{ 'is-error': errors[`line_${$index}_expiryDate`] }"
            />
          </template>
        </el-table-column>
        <el-table-column :label="t('inbound.slip.plannedQty')" width="100">
          <template #default="{ row, $index }">
            <el-input-number
              v-model="row.plannedQty"
              :min="1"
              :max="999999"
              size="small"
              controls-position="right"
              style="width: 100%"
              :class="{ 'is-error': errors[`line_${$index}_plannedQty`] }"
            />
          </template>
        </el-table-column>
        <el-table-column width="60" align="center">
          <template #default="{ $index }">
            <el-button
              type="danger"
              :icon="Delete"
              size="small"
              circle
              :disabled="lines.length <= 1"
              @click="removeLine($index)"
            />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- フッターボタン -->
    <div class="action-bar">
      <el-button @click="handleCancel">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        {{ t('common.save') }}
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Delete } from '@element-plus/icons-vue'
import { useInboundSlipNew } from '@/composables/inbound/useInboundSlipNew'

const { t } = useI18n()

const {
  headerForm,
  lines,
  partnerOptions,
  loading,
  errors,
  fetchBusinessDate,
  fetchPartnerOptions,
  handleProductCodeBlur,
  addLine,
  removeLine,
  handleSubmit,
  handleCancel,
} = useInboundSlipNew()

onMounted(() => {
  fetchBusinessDate()
  fetchPartnerOptions()
})
</script>

<style scoped lang="scss">
.wms-page {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.line-error {
  margin-bottom: 12px;
}

:deep(.is-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}
</style>
