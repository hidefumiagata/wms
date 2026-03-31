<template>
  <div class="wms-page">
    <el-card>
      <template #header>
        <span>{{ t('allocation.title') }}</span>
      </template>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- Tab1: 引当実行 -->
        <el-tab-pane :label="t('allocation.tabExecute')" name="execute">
          <!-- 検索フォーム -->
          <el-card class="search-card" shadow="never">
            <el-form :model="searchForm" inline @submit.prevent="handleSearch">
              <el-form-item :label="t('allocation.status')">
                <el-select
                  v-model="searchForm.status"
                  multiple
                  clearable
                  style="width: 240px"
                >
                  <el-option
                    :label="t('allocation.statusOrdered')"
                    value="ORDERED"
                  />
                  <el-option
                    :label="t('allocation.statusPartialAllocated')"
                    value="PARTIAL_ALLOCATED"
                  />
                </el-select>
              </el-form-item>
              <el-form-item :label="t('allocation.shippingDateFrom')">
                <el-date-picker
                  v-model="searchForm.shippingDateFrom"
                  type="date"
                  value-format="YYYY-MM-DD"
                  clearable
                  style="width: 160px"
                />
              </el-form-item>
              <el-form-item :label="t('allocation.shippingDateTo')">
                <el-date-picker
                  v-model="searchForm.shippingDateTo"
                  type="date"
                  value-format="YYYY-MM-DD"
                  clearable
                  :disabled-date="(date: Date) => shippingDateToMin ? date < new Date(shippingDateToMin) : false"
                  style="width: 160px"
                />
              </el-form-item>
              <el-form-item :label="t('allocation.partnerName')">
                <el-input
                  v-model="searchForm.partnerName"
                  clearable
                  :maxlength="100"
                  style="width: 200px"
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" native-type="submit" :icon="Search">
                  {{ t('common.search') }}
                </el-button>
                <el-button :icon="Refresh" @click="handleReset">
                  {{ t('common.reset') }}
                </el-button>
              </el-form-item>
            </el-form>
          </el-card>

          <!-- 受注一覧テーブル -->
          <el-card class="table-card" shadow="never">
            <template #header>
              <div class="table-header">
                <span>{{ t('allocation.orderListTitle') }}</span>
                <el-button
                  type="primary"
                  :loading="executing"
                  :disabled="selectedIds.length === 0"
                  @click="handleExecute"
                >
                  {{ t('allocation.executeButton') }}
                </el-button>
              </div>
            </template>

            <el-table
              v-loading="ordersLoading"
              :data="orders"
              row-key="id"
              @selection-change="handleSelectionChange"
            >
              <el-table-column type="selection" width="40" />
              <el-table-column
                prop="slipNumber"
                :label="t('allocation.slipNumber')"
                width="180"
              />
              <el-table-column
                :label="t('allocation.plannedDate')"
                width="130"
                align="center"
              >
                <template #default="{ row }">
                  {{ formatDate(row.plannedDate) }}
                </template>
              </el-table-column>
              <el-table-column
                prop="partnerName"
                :label="t('allocation.partner')"
                min-width="160"
              />
              <el-table-column
                prop="lineCount"
                :label="t('allocation.lineCount')"
                width="100"
                align="center"
              />
              <el-table-column
                :label="t('allocation.status')"
                width="120"
                align="center"
              >
                <template #default="{ row }">
                  <el-tag :type="orderStatusTagType(row.status)">
                    {{ orderStatusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty :description="t('common.noData')" />
              </template>
            </el-table>

            <div class="pagination-wrapper">
              <el-pagination
                v-model:current-page="ordersPage"
                v-model:page-size="ordersPageSize"
                :total="ordersTotal"
                :page-sizes="[20, 50, 100]"
                layout="total, sizes, prev, pager, next"
                background
                @current-change="handleOrdersPageChange"
                @size-change="handleOrdersSizeChange"
              />
            </div>
          </el-card>

          <!-- 引当結果 -->
          <el-card v-if="executionResult" class="result-card" shadow="never">
            <template #header>
              <span>{{ t('allocation.resultTitle') }}</span>
            </template>

            <el-alert
              :title="t('allocation.allocatedCount', { count: executionResult.allocatedCount ?? 0 })"
              type="success"
              :closable="false"
              show-icon
              style="margin-bottom: 16px"
            />

            <!-- ばらし指示一覧 -->
            <template v-if="(executionResult.unpackInstructions ?? []).length > 0">
              <h4 class="result-card__subtitle">{{ t('allocation.unpackTitle') }}</h4>
              <el-table
                :data="executionResult.unpackInstructions"
                stripe
                border
                style="margin-bottom: 16px"
              >
                <el-table-column
                  prop="productCode"
                  :label="t('allocation.unpackProductCode')"
                  width="140"
                />
                <el-table-column
                  prop="productName"
                  :label="t('allocation.unpackProductName')"
                  min-width="160"
                />
                <el-table-column
                  prop="fromUnitType"
                  :label="t('allocation.unpackFromUnit')"
                  width="100"
                  align="center"
                />
                <el-table-column
                  prop="toUnitType"
                  :label="t('allocation.unpackToUnit')"
                  width="100"
                  align="center"
                />
                <el-table-column
                  prop="quantity"
                  :label="t('allocation.unpackQuantity')"
                  width="80"
                  align="right"
                />
                <el-table-column
                  :label="t('allocation.unpackStatus')"
                  width="100"
                  align="center"
                >
                  <template #default="{ row }">
                    <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'warning'">
                      {{ row.status === 'COMPLETED' ? t('allocation.unpackStatusCompleted') : t('allocation.unpackStatusInstructed') }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column
                  :label="t('common.actions')"
                  width="100"
                  align="center"
                  fixed="right"
                >
                  <template #default="{ row }">
                    <el-button
                      v-if="row.status !== 'COMPLETED'"
                      size="small"
                      type="success"
                      @click="handleUnpackComplete(row)"
                    >
                      {{ t('allocation.unpackComplete') }}
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </template>

            <!-- 未引当明細一覧 -->
            <template v-if="(executionResult.unallocatedLines ?? []).length > 0">
              <h4 class="result-card__subtitle">{{ t('allocation.unallocatedTitle') }}</h4>
              <el-alert
                :title="t('allocation.unallocatedWarning')"
                type="warning"
                :closable="false"
                show-icon
                style="margin-bottom: 8px"
              />
              <el-table
                :data="executionResult.unallocatedLines"
                stripe
                border
              >
                <el-table-column
                  prop="productCode"
                  :label="t('allocation.unallocatedProductCode')"
                  width="140"
                />
                <el-table-column
                  prop="productName"
                  :label="t('allocation.unallocatedProductName')"
                  min-width="160"
                />
                <el-table-column
                  prop="shortageQty"
                  :label="t('allocation.unallocatedShortageQty')"
                  width="100"
                  align="right"
                />
              </el-table>
            </template>
          </el-card>
        </el-tab-pane>

        <!-- Tab2: 引当済み一覧 -->
        <el-tab-pane :label="t('allocation.tabAllocated')" name="allocated">
          <el-card shadow="never">
            <template #header>
              <span>{{ t('allocation.allocatedListTitle') }}</span>
            </template>

            <el-table
              v-loading="allocatedLoading"
              :data="allocatedOrders"
              row-key="id"
            >
              <el-table-column
                prop="slipNumber"
                :label="t('allocation.slipNumber')"
                width="180"
              />
              <el-table-column
                :label="t('allocation.plannedDate')"
                width="130"
                align="center"
              >
                <template #default="{ row }">
                  {{ formatDate(row.plannedDate) }}
                </template>
              </el-table-column>
              <el-table-column
                prop="partnerName"
                :label="t('allocation.partner')"
                min-width="160"
              />
              <el-table-column
                prop="lineCount"
                :label="t('allocation.lineCount')"
                width="100"
                align="center"
              />
              <el-table-column
                prop="allocatedLineCount"
                :label="t('allocation.allocatedLineCount')"
                width="130"
                align="center"
              />
              <el-table-column
                :label="t('allocation.status')"
                width="120"
                align="center"
              >
                <template #default="{ row }">
                  <el-tag :type="allocatedStatusTagType(row.status)">
                    {{ allocatedStatusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column
                :label="t('common.actions')"
                width="130"
                align="center"
                fixed="right"
              >
                <template #default="{ row }">
                  <el-button
                    size="small"
                    type="danger"
                    @click="handleRelease(row)"
                  >
                    {{ t('allocation.releaseButton') }}
                  </el-button>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty :description="t('common.noData')" />
              </template>
            </el-table>

            <div class="pagination-wrapper">
              <el-pagination
                v-model:current-page="allocatedPage"
                v-model:page-size="allocatedPageSize"
                :total="allocatedTotal"
                :page-sizes="[20, 50, 100]"
                layout="total, sizes, prev, pager, next"
                background
                @current-change="handleAllocatedPageChange"
                @size-change="handleAllocatedSizeChange"
              />
            </div>
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search, Refresh } from '@element-plus/icons-vue'
import { useAllocation } from '@/composables/allocation/useAllocation'

const { t } = useI18n()

const {
  activeTab,
  handleTabChange,
  formatDate,
  orderStatusTagType,
  orderStatusLabel,
  allocatedStatusTagType,
  allocatedStatusLabel,
  shippingDateToMin,
  orders,
  ordersLoading,
  ordersTotal,
  ordersPage,
  ordersPageSize,
  selectedIds,
  searchForm,
  executionResult,
  executing,
  fetchOrders,
  handleSearch,
  handleReset,
  handleOrdersPageChange,
  handleOrdersSizeChange,
  handleSelectionChange,
  handleExecute,
  handleUnpackComplete,
  allocatedOrders,
  allocatedLoading,
  allocatedTotal,
  allocatedPage,
  allocatedPageSize,
  handleRelease,
  handleAllocatedPageChange,
  handleAllocatedSizeChange,
} = useAllocation()

onMounted(() => {
  fetchOrders()
})
</script>

<style scoped lang="scss">
.search-card {
  margin-bottom: 16px;
}

.table-card {
  margin-bottom: 16px;
}

.result-card {
  margin-top: 16px;

  &__subtitle {
    margin: 0 0 8px 0;
    font-size: 14px;
    color: #303133;
  }
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
