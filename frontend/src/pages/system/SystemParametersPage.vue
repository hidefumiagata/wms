<template>
  <div class="wms-page">
    <el-card v-loading="loading">
      <template #header>
        <span class="page-title">{{ t('system.parameters.title') }}</span>
      </template>

      <div v-if="groups.length === 0 && !loading" class="empty-state">
        <el-empty :description="t('common.noData')" />
      </div>

      <!-- カテゴリ別グルーピング -->
      <div v-for="group in groups" :key="group.category" class="category-group">
        <div class="category-header" @click="toggleCategory(group)">
          <el-icon>
            <ArrowRight v-if="group.collapsed" />
            <ArrowDown v-else />
          </el-icon>
          <span class="category-label">
            {{ categoryLabels[group.category] ?? group.category }}
          </span>
          <el-tag size="small" type="info">{{ group.items.length }}</el-tag>
        </div>

        <el-table
          v-show="!group.collapsed"
          :data="group.items"
          border
          style="width: 100%"
          class="param-table"
        >
          <el-table-column :label="t('system.parameters.paramName')" width="240">
            <template #default="{ row }">
              <el-tooltip :content="row.original.paramKey" placement="top">
                <span>{{ row.original.displayName }}</span>
              </el-tooltip>
            </template>
          </el-table-column>

          <el-table-column :label="t('system.parameters.currentValue')" width="220">
            <template #default="{ row }">
              <div>
                <el-input-number
                  v-if="row.original.valueType === SystemParameterValueType.Integer"
                  v-model="row.editValue"
                  :min="0"
                  :controls="false"
                  style="width: 100%"
                  @change="(val: number | undefined) => { row.editValue = String(val ?? 0); handleValueChange(row) }"
                />
                <el-input
                  v-else
                  v-model="row.editValue"
                  maxlength="500"
                  @input="handleValueChange(row)"
                />
                <div v-if="row.error" class="inline-error">{{ row.error }}</div>
              </div>
            </template>
          </el-table-column>

          <el-table-column
            :label="t('system.parameters.defaultValue')"
            width="120"
            align="center"
          >
            <template #default="{ row }">
              <span class="default-value">{{ row.original.defaultValue }}</span>
            </template>
          </el-table-column>

          <el-table-column :label="t('system.parameters.description')" min-width="200">
            <template #default="{ row }">
              {{ row.original.description }}
            </template>
          </el-table-column>

          <el-table-column :label="t('system.parameters.updatedAt')" width="150">
            <template #default="{ row }">
              {{ formatDateTime(row.original.updatedAt) }}
            </template>
          </el-table-column>

          <el-table-column :label="t('common.actions')" width="100" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                type="primary"
                size="small"
                :disabled="!isDirty(row)"
                :loading="row.saving"
                @click="handleSave(row)"
              >
                {{ t('common.save') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRight, ArrowDown } from '@element-plus/icons-vue'
import { useSystemParameters } from '@/composables/system/useSystemParameters'
import { SystemParameterValueType } from '@/api/generated/models/system-parameter-value-type'

const { t } = useI18n()

const {
  groups,
  loading,
  fetchParameters,
  toggleCategory,
  isDirty,
  handleValueChange,
  handleSave,
  categoryLabels,
} = useSystemParameters()

function formatDateTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => fetchParameters())
</script>

<style scoped lang="scss">
.wms-page {
  padding: 20px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
}

.category-group {
  margin-bottom: 20px;
}

.category-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  cursor: pointer;
  user-select: none;
  margin-bottom: 8px;

  &:hover {
    background: #ecf0f5;
  }
}

.category-label {
  font-weight: 600;
  font-size: 14px;
}

.default-value {
  color: var(--el-text-color-secondary);
}

.param-table {
  margin-bottom: 4px;
}

.empty-state {
  padding: 40px 0;
}

.inline-error {
  color: var(--el-color-danger);
  font-size: 12px;
  margin-top: 2px;
}
</style>
