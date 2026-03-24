<template>
  <div class="wms-page" v-loading="loading">
    <template v-if="instruction">
      <el-card>
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item :label="t('outbound.picking.instructionNumber')">{{ instruction.instructionNumber }}</el-descriptions-item>
          <el-descriptions-item :label="t('outbound.picking.area')">{{ instruction.areaName ?? '—' }}</el-descriptions-item>
          <el-descriptions-item :label="t('outbound.picking.status')">
            <el-tag size="small">{{ instruction.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('outbound.slip.createdAt')">{{ formatDateTime(instruction.createdAt ?? '') }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card>
        <template #header><span>{{ t('outbound.picking.pickingLines') }}</span></template>

        <el-table :data="instruction.lines" stripe border style="width: 100%">
          <el-table-column prop="lineNo" :label="t('outbound.slip.lineNo')" width="60" align="center" />
          <el-table-column prop="locationCode" :label="t('outbound.picking.location')" width="120" />
          <el-table-column prop="productCode" :label="t('outbound.slip.productCode')" width="140" />
          <el-table-column prop="productName" :label="t('outbound.slip.productName')" min-width="140" />
          <el-table-column prop="unitType" :label="t('outbound.slip.unitType')" width="80" align="center" />
          <el-table-column prop="qtyToPick" :label="t('outbound.picking.qtyToPick')" width="90" align="right" />
          <el-table-column :label="t('outbound.picking.qtyPicked')" width="120">
            <template #default="{ row }">
              <el-input-number
                v-if="canComplete && row.lineStatus !== 'COMPLETED'"
                v-model="row.qtyPicked"
                :min="0"
                :max="row.qtyToPick"
                :precision="0"
                size="small"
                controls-position="right"
                style="width: 100%"
              />
              <span v-else>{{ row.qtyPicked ?? '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('outbound.picking.lineStatus')" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.lineStatus === 'COMPLETED' ? 'success' : 'info'" size="small">
                {{ row.lineStatus === 'COMPLETED' ? t('outbound.picking.lineCompleted') : t('outbound.picking.linePending') }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <div class="action-bar">
        <el-button @click="goBack">{{ t('outbound.picking.backToList') }}</el-button>
        <el-button v-if="canComplete" type="primary" :loading="completing" @click="submitComplete">
          {{ t('outbound.picking.completeButton') }}
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePickingDetail } from '@/composables/outbound/usePickingDetail'
import { formatDateTime } from '@/utils/outboundFormatters'

const { t } = useI18n()

const { instruction, loading, completing, canComplete, fetchDetail, handleComplete, goBack } = usePickingDetail()

function submitComplete() {
  if (!instruction.value) return
  const completedLines = instruction.value.lines
    ?.filter(l => l.lineStatus !== 'COMPLETED' && l.qtyPicked != null)
    .map(l => ({ lineId: l.id!, qtyPicked: l.qtyPicked! })) ?? []
  if (completedLines.length === 0) return
  handleComplete(completedLines)
}

onMounted(() => fetchDetail())
</script>

<style scoped lang="scss">
.wms-page { padding: 20px; display: flex; flex-direction: column; gap: 16px; }
.action-bar { display: flex; justify-content: space-between; }
</style>
