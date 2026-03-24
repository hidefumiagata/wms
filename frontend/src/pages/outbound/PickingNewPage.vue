<template>
  <div class="wms-page">
    <el-card>
      <template #header><span>{{ t('outbound.picking.createNew') }}</span></template>
      <el-form label-width="120px" style="max-width: 400px">
        <el-form-item :label="t('outbound.picking.area')">
          <el-select v-model="areaId" clearable filterable :placeholder="t('outbound.picking.areaAll')" style="width: 100%">
            <el-option v-for="a in areaOptions" :key="a.id" :label="a.areaName" :value="a.id" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <template #header>
        <div class="table-header">
          <span>{{ t('outbound.picking.allocatedOrders') }}</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="allocatedOrders" stripe border style="width: 100%" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="40" />
        <el-table-column prop="slipNumber" :label="t('outbound.slip.slipNumber')" width="180" />
        <el-table-column prop="plannedDate" :label="t('outbound.slip.plannedDate')" width="120" />
        <el-table-column prop="partnerName" :label="t('outbound.slip.partner')" min-width="140" />
        <el-table-column prop="lineCount" :label="t('outbound.slip.lineCount')" width="80" align="center" />
        <template #empty><el-empty :description="t('common.noData')" /></template>
      </el-table>
    </el-card>

    <div class="action-bar">
      <el-button @click="goBack">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="handleSubmit">
        {{ t('outbound.picking.createNew') }}
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePickingNew } from '@/composables/outbound/usePickingNew'

const { t } = useI18n()

const {
  allocatedOrders, areaId, areaOptions, loading, submitting, canSubmit,
  fetchAllocatedOrders, fetchAreaOptions, handleSelectionChange, handleSubmit, goBack,
} = usePickingNew()

onMounted(() => {
  fetchAllocatedOrders()
  fetchAreaOptions()
})
</script>

<style scoped lang="scss">
.wms-page { padding: 20px; display: flex; flex-direction: column; gap: 16px; }
.table-header { display: flex; align-items: center; justify-content: space-between; }
.action-bar { display: flex; justify-content: flex-end; gap: 8px; }
</style>
