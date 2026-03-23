<template>
  <div class="wms-page" v-loading="loading">
    <template v-if="slip">
      <!-- 基本情報 -->
      <el-card>
        <template #header>
          <div class="detail-header">
            <span>{{ t('inbound.slip.detail') }}</span>
            <el-tag :type="statusTagType(slip.status)" size="large">
              {{ statusLabel(slip.status) }}
            </el-tag>
          </div>
        </template>

        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('inbound.slip.slipNumber')">
            {{ slip.slipNumber }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('inbound.slip.slipType')">
            {{ slip.slipType === 'NORMAL' ? t('inbound.slip.typeNormal') : t('inbound.slip.typeBatch') }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('inbound.slip.plannedDate')">
            {{ slip.plannedDate }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('inbound.slip.partner')">
            {{ slip.partnerName ?? '—' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('inbound.slip.note')" :span="2">
            {{ slip.note ?? '—' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('inbound.slip.createdAt')">
            {{ formatDateTime(slip.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('inbound.slip.createdBy')">
            {{ slip.createdByName ?? slip.createdBy }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 入荷明細 -->
      <el-card>
        <template #header>
          <span>{{ t('inbound.slip.lines') }}</span>
        </template>

        <el-table :data="slip.lines" stripe border style="width: 100%">
          <el-table-column prop="lineNo" :label="t('inbound.slip.lineNo')" width="60" align="center" />
          <el-table-column prop="productCode" :label="t('inbound.slip.productCode')" width="140" />
          <el-table-column prop="productName" :label="t('inbound.slip.productName')" min-width="160" />
          <el-table-column prop="unitType" :label="t('inbound.slip.unitType')" width="80" align="center" />
          <el-table-column prop="lotNumber" :label="t('inbound.slip.lotNumber')" width="120">
            <template #default="{ row }">{{ row.lotNumber ?? '—' }}</template>
          </el-table-column>
          <el-table-column prop="expiryDate" :label="t('inbound.slip.expiryDate')" width="110">
            <template #default="{ row }">{{ row.expiryDate ?? '—' }}</template>
          </el-table-column>
          <el-table-column prop="plannedQty" :label="t('inbound.slip.plannedQty')" width="90" align="right" />
          <el-table-column :label="t('inbound.slip.inspectedQty')" width="90" align="right">
            <template #default="{ row }">
              {{ row.inspectedQty != null ? row.inspectedQty : '—' }}
            </template>
          </el-table-column>
          <el-table-column :label="t('inbound.slip.differenceQty')" width="80" align="right">
            <template #default="{ row }">
              <span
                v-if="row.diffQty != null"
                :class="{ 'text-danger': row.diffQty !== 0 }"
              >
                {{ row.diffQty }}
              </span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty :description="t('common.noData')" />
          </template>
        </el-table>
      </el-card>

      <!-- 操作ボタン -->
      <div class="action-bar">
        <el-button @click="goBack">
          {{ t('inbound.slip.backToList') }}
        </el-button>
        <div class="action-bar__right">
          <el-button
            v-if="canCancel"
            type="danger"
            plain
            :loading="actionLoading"
            @click="handleCancel"
          >
            {{ t('inbound.slip.cancel') }}
          </el-button>
          <el-button
            v-if="canConfirm"
            type="primary"
            :loading="actionLoading"
            @click="handleConfirm"
          >
            {{ t('inbound.slip.confirm') }}
          </el-button>
          <el-button
            v-if="canInspect"
            type="warning"
            @click="goInspect"
          >
            {{ t('inbound.slip.goInspect') }}
          </el-button>
          <el-button
            v-if="canStore"
            type="success"
            @click="goStore"
          >
            {{ t('inbound.slip.goStore') }}
          </el-button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useInboundSlipDetail } from '@/composables/inbound/useInboundSlipDetail'
import { InboundSlipStatus } from '@/api/generated/models/inbound-slip-status'

const { t } = useI18n()

const {
  slip,
  loading,
  actionLoading,
  canConfirm,
  canCancel,
  canInspect,
  canStore,
  fetchDetail,
  handleConfirm,
  handleCancel,
  goBack,
  goInspect,
  goStore,
} = useInboundSlipDetail()

function statusLabel(status: string): string {
  switch (status) {
    case InboundSlipStatus.Planned: return t('inbound.slip.statusPlanned')
    case InboundSlipStatus.Confirmed: return t('inbound.slip.statusConfirmed')
    case InboundSlipStatus.Inspecting: return t('inbound.slip.statusInspecting')
    case InboundSlipStatus.PartialStored: return t('inbound.slip.statusPartialStored')
    case InboundSlipStatus.Stored: return t('inbound.slip.statusStored')
    case InboundSlipStatus.Cancelled: return t('inbound.slip.statusCancelled')
    default: return status
  }
}

function statusTagType(status: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case InboundSlipStatus.Planned: return 'info'
    case InboundSlipStatus.Confirmed: return ''
    case InboundSlipStatus.Inspecting: return 'warning'
    case InboundSlipStatus.PartialStored: return 'warning'
    case InboundSlipStatus.Stored: return 'success'
    case InboundSlipStatus.Cancelled: return 'danger'
    default: return 'info'
  }
}

function formatDateTime(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleString('ja-JP', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

onMounted(() => fetchDetail())
</script>

<style scoped lang="scss">
.wms-page {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;

  &__right {
    display: flex;
    gap: 8px;
  }
}

.text-danger {
  color: var(--el-color-danger);
  font-weight: bold;
}
</style>
