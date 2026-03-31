<template>
  <div class="wms-page">
    <!-- タブ + ヘッダー -->
    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <span>{{ t('interfaceFiles.title') }}</span>
          <router-link to="/interface/history" class="history-link">
            {{ t('interfaceFiles.historyLink') }}
          </router-link>
        </div>
      </template>

      <!-- I/F種別タブ -->
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane :label="t('interfaceFiles.tabInboundPlan')" name="IFX-001" />
        <el-tab-pane :label="t('interfaceFiles.tabOrder')" name="IFX-002" />
      </el-tabs>

      <!-- ツールバー -->
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="fetchFiles">
          {{ t('interfaceFiles.refresh') }}
        </el-button>
        <span class="file-count">{{ t('interfaceFiles.fileCount', { count: totalCount }) }}</span>
      </div>

      <!-- ファイル一覧テーブル -->
      <el-table v-loading="loading" :data="files" stripe border style="width: 100%">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="fileName" :label="t('interfaceFiles.fileName')" min-width="200" />
        <el-table-column :label="t('interfaceFiles.fileSize')" width="120" align="right">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('interfaceFiles.createdAt')" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('interfaceFiles.actions')" width="240" align="center">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              :loading="validatingFile === row.fileName"
              :disabled="!!validatingFile || !!importingFile"
              @click="handleValidate(row.fileName)"
            >
              {{ t('interfaceFiles.validate') }}
            </el-button>
            <el-button
              size="small"
              type="success"
              :loading="importingFile === row.fileName"
              :disabled="!!validatingFile || !!importingFile"
              @click="handleImportFromList(row.fileName)"
            >
              {{ t('interfaceFiles.import') }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="t('interfaceFiles.emptyMessage')">
            <template #description>
              <p>{{ t('interfaceFiles.emptyMessage') }}</p>
              <p class="empty-sub">{{ t('interfaceFiles.emptyDescription') }}</p>
            </template>
          </el-empty>
        </template>
      </el-table>
    </el-card>

    <!-- バリデーション結果ダイアログ (IF-002) -->
    <ValidationResultDialog
      v-model:visible="validationDialogVisible"
      :result="validationResult"
      :importing="validationImporting"
      @import-success-only="handleImportSuccessOnly"
      @import-discard="handleImportDiscard"
      @close="closeValidationDialog"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh } from '@element-plus/icons-vue'
import { useInterfaceFiles } from '@/composables/interface/useInterfaceFiles'
import ValidationResultDialog from './ValidationResultDialog.vue'

const { t } = useI18n()

const {
  activeTab,
  files,
  totalCount,
  loading,
  validatingFile,
  importingFile,
  validationDialogVisible,
  validationResult,
  validationImporting,
  fetchFiles,
  handleTabChange,
  handleValidate,
  handleImportFromList,
  handleImportSuccessOnly,
  handleImportDiscard,
  closeValidationDialog,
  formatFileSize,
} = useInterfaceFiles()

function formatDateTime(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleString('ja-JP', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

onMounted(() => {
  fetchFiles()
})
</script>

<style scoped lang="scss">
.wms-page {
  padding: 20px;
}

.table-card {
  .table-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 16px;
    font-weight: 600;
  }

  .history-link {
    font-size: 14px;
    font-weight: normal;
    color: var(--el-color-primary);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;

  .file-count {
    color: var(--el-text-color-secondary);
    font-size: 14px;
  }
}

.empty-sub {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-top: 4px;
}
</style>
