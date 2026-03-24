<template>
  <div class="wms-page" v-loading="loading">
    <template v-if="slip">
      <el-card>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item :label="t('outbound.slip.slipNumber')">{{ slip.slipNumber }}</el-descriptions-item>
          <el-descriptions-item :label="t('outbound.slip.partner')">{{ slip.partnerName ?? '—' }}</el-descriptions-item>
          <el-descriptions-item :label="t('outbound.slip.plannedDate')">{{ slip.plannedDate }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 出荷内容確認 -->
      <el-card>
        <template #header><span>{{ t('outbound.ship.title') }}</span></template>
        <el-table :data="slip.lines" stripe border style="width: 100%">
          <el-table-column prop="lineNo" :label="t('outbound.slip.lineNo')" width="60" align="center" />
          <el-table-column prop="productCode" :label="t('outbound.slip.productCode')" width="140" />
          <el-table-column prop="productName" :label="t('outbound.slip.productName')" min-width="160" />
          <el-table-column prop="unitType" :label="t('outbound.slip.unitType')" width="80" align="center" />
          <el-table-column :label="t('outbound.inspect.inspectedQty')" width="100" align="right">
            <template #default="{ row }">{{ row.inspectedQty ?? row.orderedQty }}</template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 配送情報入力 -->
      <el-card>
        <el-form label-width="120px" style="max-width: 500px">
          <el-form-item :label="t('outbound.ship.carrier')" required :error="errors.carrier">
            <el-select v-model="shipForm.carrier" :placeholder="t('outbound.ship.carrier')" style="width: 100%">
              <el-option :label="t('outbound.ship.carrierYamato')" value="ヤマト運輸" />
              <el-option :label="t('outbound.ship.carrierSagawa')" value="佐川急便" />
              <el-option :label="t('outbound.ship.carrierJP')" value="日本郵便" />
              <el-option :label="t('outbound.ship.carrierOther')" value="その他" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('outbound.ship.trackingNumber')">
            <el-input v-model="shipForm.trackingNumber" :maxlength="50" />
          </el-form-item>
          <el-form-item :label="t('outbound.ship.shipDate')" required :error="errors.shippedDate">
            <el-date-picker v-model="shipForm.shippedDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item :label="t('outbound.ship.shipNote')">
            <el-input v-model="shipForm.note" type="textarea" :rows="3" :maxlength="500" show-word-limit />
          </el-form-item>
        </el-form>
      </el-card>

      <div class="action-bar">
        <el-button @click="goBack">{{ t('outbound.ship.backToDetail') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ t('outbound.ship.confirmShip') }}</el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useOutboundSlipShip } from '@/composables/outbound/useOutboundSlipShip'

const { t } = useI18n()
const { slip, loading, submitting, errors, shipForm, fetchBusinessDate, fetchDetail, handleSubmit, goBack } = useOutboundSlipShip()

onMounted(() => {
  fetchBusinessDate()
  fetchDetail()
})
</script>

<style scoped lang="scss">
.wms-page { padding: 20px; display: flex; flex-direction: column; gap: 16px; }
.action-bar { display: flex; justify-content: space-between; }
</style>
