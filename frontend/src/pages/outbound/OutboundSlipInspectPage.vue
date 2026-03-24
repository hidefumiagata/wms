<template>
  <div class="wms-page" v-loading="loading">
    <template v-if="slip">
      <el-card>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item :label="t('outbound.slip.slipNumber')">{{ slip.slipNumber }}</el-descriptions-item>
          <el-descriptions-item :label="t('outbound.slip.partner')">{{ slip.partnerName ?? '—' }}</el-descriptions-item>
          <el-descriptions-item :label="t('outbound.slip.status')">
            <el-tag size="small">{{ outboundStatusLabel(slip.status!, t) }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card>
        <template #header><span>{{ t('outbound.inspect.title') }}</span></template>
        <el-table :data="lines" stripe border style="width: 100%" :row-class-name="diffRowClass">
          <el-table-column prop="lineNo" :label="t('outbound.slip.lineNo')" width="60" align="center" />
          <el-table-column prop="productCode" :label="t('outbound.slip.productCode')" width="140" />
          <el-table-column prop="productName" :label="t('outbound.slip.productName')" min-width="160" />
          <el-table-column prop="unitType" :label="t('outbound.slip.unitType')" width="80" align="center" />
          <el-table-column prop="orderedQty" :label="t('outbound.slip.orderedQty')" width="90" align="right" />
          <el-table-column :label="t('outbound.inspect.inspectedQty')" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.inspectedQty" :min="0" :max="999999" :precision="0" size="small" controls-position="right" style="width: 100%" @change="updateDiff(row)" />
            </template>
          </el-table-column>
          <el-table-column :label="t('outbound.inspect.diffQty')" width="80" align="right">
            <template #default="{ row }">
              <span :class="{ 'text-danger': row.diffQty !== 0 }">{{ row.diffQty }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <div class="action-bar">
        <el-button @click="goBack">{{ t('outbound.inspect.backToDetail') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ t('outbound.inspect.save') }}</el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useOutboundSlipInspect } from '@/composables/outbound/useOutboundSlipInspect'
import { outboundStatusLabel } from '@/utils/outboundFormatters'

const { t } = useI18n()
const { slip, lines, loading, saving, fetchDetail, updateDiff, handleSave, goBack } = useOutboundSlipInspect()

function diffRowClass({ row }: { row: { diffQty: number } }) {
  return row.diffQty !== 0 ? 'diff-highlight-row' : ''
}

onMounted(() => fetchDetail())
</script>

<style scoped lang="scss">
.wms-page { padding: 20px; display: flex; flex-direction: column; gap: 16px; }
.action-bar { display: flex; justify-content: space-between; }
.text-danger { color: var(--el-color-danger); font-weight: bold; }
:deep(.diff-highlight-row) { background-color: #fdf6ec !important; }
</style>
