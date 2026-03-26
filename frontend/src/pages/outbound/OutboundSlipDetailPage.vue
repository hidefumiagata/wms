<template>
  <div class="wms-page" v-loading="loading">
    <template v-if="slip">
      <!-- 基本情報 -->
      <el-card>
        <template #header>
          <div class="detail-header">
            <span>{{ t('outbound.slip.detail') }}</span>
            <el-tag :type="outboundStatusTagType(slip.status)" size="large">
              {{ outboundStatusLabel(slip.status, t) }}
            </el-tag>
          </div>
        </template>

        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('outbound.slip.slipNumber')">{{ slip.slipNumber }}</el-descriptions-item>
          <el-descriptions-item :label="t('outbound.slip.slipType')">
            {{ slip.slipType === 'NORMAL' ? t('outbound.slip.typeNormal') : t('outbound.slip.typeTransfer') }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('outbound.slip.plannedDate')">{{ slip.plannedDate }}</el-descriptions-item>
          <el-descriptions-item v-if="slip.transferSlipNumber" label="振替伝票番号">{{ slip.transferSlipNumber }}</el-descriptions-item>
          <el-descriptions-item :label="t('outbound.slip.partner')">
            <span v-if="slip.partnerCode">{{ slip.partnerCode }} — {{ slip.partnerName }}</span>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('outbound.slip.note')" :span="2">{{ slip.note ?? '—' }}</el-descriptions-item>
          <el-descriptions-item :label="t('outbound.slip.createdAt')">{{ formatDateTime(slip.createdAt) }}</el-descriptions-item>
          <el-descriptions-item :label="t('outbound.slip.createdBy')">{{ slip.createdBy }}</el-descriptions-item>
          <el-descriptions-item v-if="slip.shippedAt" :label="t('outbound.slip.shippedAt')">{{ formatDateTime(slip.shippedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="slip.carrier" :label="t('outbound.slip.carrier')">{{ slip.carrier }}</el-descriptions-item>
          <el-descriptions-item v-if="slip.trackingNumber" :label="t('outbound.slip.trackingNumber')">{{ slip.trackingNumber }}</el-descriptions-item>
          <el-descriptions-item v-if="slip.cancelledAt" :label="t('outbound.slip.cancelledAt')">{{ formatDateTime(slip.cancelledAt) }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 受注明細 -->
      <el-card>
        <template #header>
          <span>{{ t('outbound.slip.lines') }}</span>
        </template>

        <el-table :data="slip.lines" stripe border style="width: 100%">
          <el-table-column prop="lineNo" :label="t('outbound.slip.lineNo')" width="60" align="center" />
          <el-table-column prop="productCode" :label="t('outbound.slip.productCode')" width="140" />
          <el-table-column prop="productName" :label="t('outbound.slip.productName')" min-width="160" />
          <el-table-column prop="unitType" :label="t('outbound.slip.unitType')" width="80" align="center" />
          <el-table-column prop="orderedQty" :label="t('outbound.slip.orderedQty')" width="90" align="right" />
          <el-table-column :label="t('outbound.slip.allocatedQty')" width="90" align="right">
            <template #default="{ row }">{{ row.allocatedQty != null ? row.allocatedQty : '—' }}</template>
          </el-table-column>
          <el-table-column :label="t('outbound.slip.pickingQty')" width="110" align="right">
            <template #default="{ row }">{{ row.pickingQty != null ? row.pickingQty : '—' }}</template>
          </el-table-column>
          <el-table-column :label="t('outbound.slip.inspectedQty')" width="90" align="right">
            <template #default="{ row }">{{ row.inspectedQty != null ? row.inspectedQty : '—' }}</template>
          </el-table-column>
          <el-table-column :label="t('outbound.slip.shippedQty')" width="90" align="right">
            <template #default="{ row }">{{ row.shippedQty > 0 ? row.shippedQty : '—' }}</template>
          </el-table-column>
          <el-table-column :label="t('outbound.slip.status')" width="130" align="center">
            <template #default="{ row }">
              <el-tag size="small">{{ row.lineStatus }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 操作ボタン -->
      <div class="action-bar">
        <el-button @click="goBack">{{ t('outbound.slip.backToList') }}</el-button>
        <div class="action-bar__right">
          <el-button v-if="canCancel" type="danger" plain :loading="actionLoading" @click="handleCancel">
            {{ t('outbound.slip.cancel') }}
          </el-button>
          <el-button v-if="canAllocate" type="warning" :loading="actionLoading" @click="handleAllocate">
            {{ t('outbound.slip.allocate') }}
          </el-button>
          <el-button v-if="canPickingNew" type="primary" @click="router.push({ name: 'picking-new' })">
            {{ t('outbound.slip.goPickingNew') }}
          </el-button>
          <el-button v-if="canInspect" type="warning" @click="goInspect">
            {{ t('outbound.slip.goInspect') }}
          </el-button>
          <el-button v-if="canShip" type="success" @click="goShip">
            {{ t('outbound.slip.goShip') }}
          </el-button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useOutboundSlipDetail } from '@/composables/outbound/useOutboundSlipDetail'
import { outboundStatusLabel, outboundStatusTagType, formatDateTime } from '@/utils/outboundFormatters'

const { t } = useI18n()
const router = useRouter()

const {
  slip, loading, actionLoading,
  canAllocate, canCancel, canPickingNew, canInspect, canShip,
  fetchDetail, handleAllocate, handleCancel, goBack, goInspect, goShip,
} = useOutboundSlipDetail()

onMounted(() => fetchDetail())
</script>

<style scoped lang="scss">
.wms-page { padding: 20px; display: flex; flex-direction: column; gap: 16px; }
.detail-header { display: flex; align-items: center; justify-content: space-between; }
.action-bar { display: flex; align-items: center; justify-content: space-between; }
.action-bar__right { display: flex; gap: 8px; }
</style>
