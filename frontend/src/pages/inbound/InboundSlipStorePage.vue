<template>
  <div class="wms-page" v-loading="loading">
    <template v-if="slip">
      <!-- 伝票情報サマリー -->
      <el-card>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item :label="t('inbound.slip.slipNumber')">{{ slip.slipNumber }}</el-descriptions-item>
          <el-descriptions-item :label="t('inbound.slip.partner')">{{ slip.partnerName ?? '—' }}</el-descriptions-item>
          <el-descriptions-item :label="t('inbound.slip.status')">
            <el-tag size="small">{{ inboundStatusLabel(slip.status, t) }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 入庫明細 -->
      <el-card>
        <template #header>
          <div class="table-header">
            <span>{{ t('inbound.store.title') }}</span>
            <el-button
              v-if="hasUnstored"
              type="success"
              size="small"
              :loading="storing"
              @click="handleStoreAll"
            >
              {{ t('inbound.store.storeAll') }}
            </el-button>
          </div>
        </template>

        <el-table :data="lines" stripe border style="width: 100%">
          <el-table-column prop="lineNo" :label="t('inbound.slip.lineNo')" width="60" align="center" />
          <el-table-column prop="productCode" :label="t('inbound.slip.productCode')" width="140" />
          <el-table-column prop="productName" :label="t('inbound.slip.productName')" min-width="140" />
          <el-table-column prop="inspectedQty" :label="t('inbound.inspect.inspectedQty')" width="90" align="right" />
          <el-table-column :label="t('inbound.store.location')" width="200">
            <template #default="{ row }">
              <el-select
                v-if="!row.isStored"
                v-model="row.locationId"
                filterable
                :placeholder="t('inbound.store.selectLocation')"
                size="small"
                style="width: 100%"
              >
                <el-option
                  v-for="loc in locationOptions"
                  :key="loc.id"
                  :label="loc.locationCode"
                  :value="loc.id"
                />
              </el-select>
              <span v-else>{{ row.locationCode }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('inbound.store.storeStatus')" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isStored ? 'success' : 'info'" size="small">
                {{ row.isStored ? t('inbound.store.stored') : t('inbound.store.unstored') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.actions')" width="90" align="center">
            <template #default="{ row }">
              <el-button
                v-if="!row.isStored"
                type="success"
                size="small"
                :loading="storing"
                @click="handleStoreSingle(row.id)"
              >
                {{ t('inbound.store.storeOne') }}
              </el-button>
              <el-tag v-else type="success" size="small">{{ t('inbound.store.done') }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- フッター -->
      <div class="action-bar">
        <el-button @click="goBack">{{ t('inbound.store.backToDetail') }}</el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useInboundSlipStore } from '@/composables/inbound/useInboundSlipStore'
import { inboundStatusLabel } from '@/utils/inboundFormatters'

const { t } = useI18n()

const {
  slip, lines, loading, storing, locationOptions, hasUnstored,
  fetchDetail, fetchLocations, handleStoreSingle, handleStoreAll, goBack,
} = useInboundSlipStore()

onMounted(() => {
  fetchDetail()
  fetchLocations()
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
  justify-content: flex-start;
}
</style>
