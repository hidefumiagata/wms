<template>
  <el-dialog
    :model-value="visible"
    :title="dialogTitle"
    width="800px"
    :close-on-click-modal="false"
    @update:model-value="$emit('close')"
  >
    <template v-if="result">
      <!-- L1 ファイルレベルエラー -->
      <template v-if="result.fileError">
        <el-alert type="error" :closable="false" show-icon>
          <template #title>
            {{ t('validationResult.fileError') }}
          </template>
          <p>{{ result.fileError.errorCode }}: {{ result.fileError.message }}</p>
        </el-alert>
      </template>

      <!-- 通常のバリデーション結果 -->
      <template v-else>
        <!-- サマリー -->
        <el-card shadow="never" class="summary-card">
          <div class="summary">
            <span class="summary-label">{{ t('validationResult.summary') }}</span>
            <span class="summary-item">
              {{ t('validationResult.totalRows') }}:
              <strong>{{ result.totalRows }}</strong>
              {{ t('validationResult.rows') }}
            </span>
            <el-divider direction="vertical" />
            <span class="summary-item success">
              {{ t('validationResult.successCount') }}:
              <strong>{{ result.successCount }}</strong>
              {{ t('validationResult.rows') }}
            </span>
            <el-divider v-if="result.errorCount > 0" direction="vertical" />
            <span v-if="result.errorCount > 0" class="summary-item error">
              {{ t('validationResult.errorCount') }}:
              <strong>{{ result.errorCount }}</strong>
              {{ t('validationResult.rows') }}
            </span>
          </div>
        </el-card>

        <!-- 全件成功メッセージ -->
        <el-alert
          v-if="result.errorCount === 0"
          type="success"
          :closable="false"
          show-icon
          style="margin-top: 16px"
        >
          {{ t('validationResult.allSuccess', { total: result.totalRows }) }}
        </el-alert>

        <!-- エラー詳細テーブル -->
        <div v-if="result.rows && result.rows.length > 0" class="error-detail">
          <div class="error-detail-header">
            {{ t('validationResult.errorDetail') }}
            <el-tag type="danger" size="small">
              {{ t('validationResult.errorCount') }}: {{ result.errorCount }}
            </el-tag>
          </div>
          <el-table :data="flatErrors" stripe border style="width: 100%" max-height="300">
            <el-table-column prop="rowNumber" :label="t('validationResult.rowNumber')" width="80" />
            <el-table-column prop="column" :label="t('validationResult.column')" width="160" />
            <el-table-column
              prop="errorCode"
              :label="t('validationResult.errorCode')"
              width="160"
            />
            <el-table-column
              prop="message"
              :label="t('validationResult.message')"
              min-width="200"
            />
          </el-table>
        </div>
      </template>
    </template>

    <template #footer>
      <span class="dialog-footer">
        <el-button
          v-if="!result?.fileError && (result?.successCount ?? 0) > 0"
          type="primary"
          :loading="importing"
          :disabled="importing"
          @click="$emit('import-success-only')"
        >
          {{ t('validationResult.importSuccessOnly') }}
        </el-button>
        <el-button
          v-if="!result?.fileError"
          type="warning"
          :loading="importing"
          :disabled="importing"
          @click="$emit('import-discard')"
        >
          {{ t('validationResult.importDiscard') }}
        </el-button>
        <el-button :disabled="importing" @click="$emit('close')">
          {{ t('common.close') }}
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InterfaceValidationResult } from '@/api/generated/models/interface-validation-result'

const { t } = useI18n()

const props = defineProps<{
  visible: boolean
  result: InterfaceValidationResult | null
  importing: boolean
}>()

defineEmits<{
  'update:visible': [value: boolean]
  'import-success-only': []
  'import-discard': []
  close: []
}>()

const dialogTitle = computed(() => {
  if (!props.result) return ''
  return t('validationResult.title', { fileName: props.result.fileName })
})

interface FlatError {
  rowNumber: number
  column: string
  errorCode: string
  message: string
}

const flatErrors = computed<FlatError[]>(() => {
  if (!props.result?.rows) return []
  const errors: FlatError[] = []
  for (const row of props.result.rows) {
    for (const err of row.errors) {
      errors.push({
        rowNumber: row.rowNumber,
        column: err.column,
        errorCode: err.errorCode,
        message: err.message,
      })
    }
  }
  return errors
})
</script>

<style scoped lang="scss">
.summary-card {
  margin-bottom: 16px;

  .summary {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .summary-label {
    font-weight: 600;
    margin-right: 8px;
  }

  .summary-item {
    &.success strong {
      color: var(--el-color-success);
    }

    &.error strong {
      color: var(--el-color-danger);
    }
  }
}

.error-detail {
  margin-top: 16px;

  .error-detail-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
    font-weight: 600;
  }
}
</style>
