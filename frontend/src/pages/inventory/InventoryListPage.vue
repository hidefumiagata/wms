<template>
  <div class="wms-page">
    <!-- 検索フォーム -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline @submit.prevent="handleSearch">
        <el-form-item :label="t('inventory.locationCode')">
          <el-input
            v-model="searchForm.locationCodePrefix"
            :placeholder="t('inventory.locationCode')"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item :label="t('inventory.product')">
          <el-select
            v-model="searchForm.productId"
            style="width: 220px"
            clearable
            filterable
            :placeholder="t('inventory.unitTypeAll')"
          >
            <el-option
              v-for="p in productOptions"
              :key="p.id"
              :label="`${p.productCode} ${p.productName}`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('inventory.unitType')">
          <el-select
            v-model="searchForm.unitType"
            style="width: 120px"
            clearable
            :placeholder="t('inventory.unitTypeAll')"
          >
            <el-option :label="t('inventory.unitTypeCase')" value="CASE" />
            <el-option :label="t('inventory.unitTypeBall')" value="BALL" />
            <el-option :label="t('inventory.unitTypePiece')" value="PIECE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('inventory.storageCondition')">
          <el-select
            v-model="searchForm.storageCondition"
            style="width: 120px"
            clearable
            :placeholder="t('inventory.storageConditionAll')"
          >
            <el-option :label="t('inventory.storageAmbient')" value="AMBIENT" />
            <el-option :label="t('inventory.storageRefrigerated')" value="REFRIGERATED" />
            <el-option :label="t('inventory.storageFrozen')" value="FROZEN" />
          </el-select>
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

    <!-- 一覧テーブル -->
    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <div class="table-header__left">
            <span>{{ t('inventory.title') }}</span>
            <el-radio-group
              :model-value="viewType"
              size="small"
              style="margin-left: 16px"
              @change="(val: string) => handleViewTypeChange(val as ViewType)"
            >
              <el-radio-button value="LOCATION">{{ t('inventory.locationView') }}</el-radio-button>
              <el-radio-button value="PRODUCT_SUMMARY">{{ t('inventory.productSummaryView') }}</el-radio-button>
            </el-radio-group>
          </div>
          <div>
            <!-- TODO: RPT-007 在庫一覧レポート / RPT-008 在庫推移レポート（レポート機能実装後に追加） -->
          </div>
        </div>
      </template>

      <!-- ロケーション別テーブル -->
      <el-table
        v-if="viewType === 'LOCATION'"
        v-loading="loading"
        :data="locationItems"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="locationCode" :label="t('inventory.locationCode')" width="200" />
        <el-table-column prop="productCode" :label="t('inventory.productCode')" width="140" />
        <el-table-column prop="productName" :label="t('inventory.productName')" min-width="160" />
        <el-table-column :label="t('inventory.unitType')" width="100" align="center">
          <template #default="{ row }">
            {{ unitTypeLabelFn(row.unitType) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('inventory.quantity')" width="100" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.quantity) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('inventory.allocatedQty')" width="100" align="right">
          <template #default="{ row }">
            {{ row.allocatedQty > 0 ? formatNumber(row.allocatedQty) : '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="t('inventory.availableQty')" width="120" align="right">
          <template #default="{ row }">
            <span :class="{ 'has-allocated': row.allocatedQty > 0 }">
              {{ formatNumber(row.availableQty) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column v-if="!isViewer" :label="t('common.actions')" width="80" align="center">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              size="small"
              @click="handleMove(row)"
            >
              {{ t('inventory.move') }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="t('inventory.noResult')" />
        </template>
      </el-table>

      <!-- 商品合計テーブル -->
      <el-table
        v-else
        v-loading="loading"
        :data="productSummaryItems"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="productCode" :label="t('inventory.productCode')" width="140" />
        <el-table-column prop="productName" :label="t('inventory.productName')" min-width="160" />
        <el-table-column :label="t('inventory.storageCondition')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="storageConditionTagTypeFn(row.storageCondition)" size="small">
              {{ storageConditionLabelFn(row.storageCondition) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('inventory.caseQuantity')" width="120" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.caseQuantity) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('inventory.ballQuantity')" width="120" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.ballQuantity) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('inventory.pieceQuantity')" width="120" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.pieceQuantity) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('inventory.totalPieceEquivalent')" width="160" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.totalPieceEquivalent) }}
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="t('inventory.noResult')" />
        </template>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { Search, Refresh } from '@element-plus/icons-vue'
import { useInventoryList } from '@/composables/inventory/useInventoryList'
import type { ViewType } from '@/composables/inventory/useInventoryList'
import { unitTypeLabel, storageConditionLabel, storageConditionTagType, formatNumber } from '@/utils/inventoryFormatters'
import type { InventoryLocationItem } from '@/api/generated/models/inventory-location-item'

const { t } = useI18n()
const router = useRouter()

const {
  viewType,
  locationItems,
  productSummaryItems,
  loading,
  total,
  page,
  pageSize,
  searchForm,
  productOptions,
  isViewer,
  fetchList,
  fetchProductOptions,
  handleSearch,
  handleReset,
  handleViewTypeChange,
  handlePageChange,
  handleSizeChange,
} = useInventoryList()

function unitTypeLabelFn(unitType: string): string {
  return unitTypeLabel(unitType, t)
}

function storageConditionLabelFn(condition: string): string {
  return storageConditionLabel(condition, t)
}

function storageConditionTagTypeFn(condition: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  return storageConditionTagType(condition)
}

function handleMove(row: InventoryLocationItem) {
  router.push({
    name: 'inventory-move',
    query: {
      locationId: String(row.locationId),
      locationCode: row.locationCode,
      productId: String(row.productId),
      unitType: row.unitType,
    },
  })
}

onMounted(() => {
  fetchProductOptions()
  fetchList()
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

  &__left {
    display: flex;
    align-items: center;
  }
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.has-allocated {
  color: #e6a23c;
  font-weight: 600;
}
</style>
