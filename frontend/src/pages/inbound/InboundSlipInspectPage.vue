<template>
  <div class="wms-page" v-loading="loading">
    <template v-if="slip">
      <!-- 伝票情報サマリー -->
      <el-card>
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item :label="t('inbound.slip.slipNumber')">{{ slip.slipNumber }}</el-descriptions-item>
          <el-descriptions-item :label="t('inbound.slip.partner')">{{ slip.partnerName ?? '—' }}</el-descriptions-item>
          <el-descriptions-item :label="t('inbound.slip.plannedDate')">{{ slip.plannedDate }}</el-descriptions-item>
          <el-descriptions-item :label="t('inbound.slip.status')">
            <el-tag :type="slip.status === 'INSPECTING' ? 'warning' : 'info'" size="small">
              {{ inboundStatusLabel(slip.status, t) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 検品明細 -->
      <el-card>
        <template #header>
          <span>{{ t('inbound.inspect.title') }}</span>
        </template>

        <el-table :data="lines" stripe border style="width: 100%">
          <el-table-column prop="lineNo" :label="t('inbound.slip.lineNo')" width="60" align="center" />
          <el-table-column prop="productCode" :label="t('inbound.slip.productCode')" width="140" />
          <el-table-column prop="productName" :label="t('inbound.slip.productName')" min-width="160" />
          <el-table-column prop="plannedQty" :label="t('inbound.slip.plannedQty')" width="90" align="right" />
          <el-table-column :label="t('inbound.inspect.inspectedQty')" width="120">
            <template #default="{ row }">
              <el-input-number
                v-if="!row.isStored"
                v-model="row.inspectedQty"
                :min="0"
                :max="999999"
                size="small"
                controls-position="right"
                style="width: 100%"
                @change="updateDiff(row)"
              />
              <span v-else>{{ row.inspectedQty }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('inbound.inspect.diffQty')" width="80" align="right">
            <template #default="{ row }">
              <span :class="{ 'text-danger': row.diffQty !== 0, 'text-highlight': row.diffQty !== 0 }">
                {{ row.diffQty }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- フッターボタン -->
      <div class="action-bar">
        <el-button @click="goBack">{{ t('inbound.inspect.backToDetail') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ t('inbound.inspect.save') }}
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useInboundSlipInspect } from '@/composables/inbound/useInboundSlipInspect'
import { inboundStatusLabel } from '@/utils/inboundFormatters'

const { t } = useI18n()

const { slip, lines, loading, saving, fetchDetail, updateDiff, handleSave, goBack } = useInboundSlipInspect()

onMounted(() => fetchDetail())
</script>

<style scoped lang="scss">
.wms-page {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.action-bar {
  display: flex;
  justify-content: space-between;
}

.text-danger { color: var(--el-color-danger); }
.text-highlight { font-weight: bold; }
</style>
