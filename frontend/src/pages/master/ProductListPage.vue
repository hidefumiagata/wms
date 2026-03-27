<template>
  <div class="wms-page">
    <!-- 検索フォーム -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline @submit.prevent="handleSearch">
        <el-form-item :label="t('master.product.productCode')">
          <el-input
            v-model="searchForm.productCode"
            :placeholder="t('master.product.productCode')"
            clearable
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item :label="t('master.product.productName')">
          <el-input
            v-model="searchForm.productName"
            :placeholder="t('master.product.productName')"
            clearable
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item :label="t('master.product.storageCondition')">
          <el-select
            v-model="searchForm.storageCondition"
            style="width: 130px"
            clearable
            :placeholder="t('master.product.storageConditionAll')"
          >
            <el-option
              :label="t('master.product.storageAmbient')"
              :value="StorageCondition.Ambient"
            />
            <el-option
              :label="t('master.product.storageRefrigerated')"
              :value="StorageCondition.Refrigerated"
            />
            <el-option
              :label="t('master.product.storageFrozen')"
              :value="StorageCondition.Frozen"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('master.product.status')">
          <el-select
            v-model="searchForm.isActive"
            style="width: 130px"
            clearable
            :placeholder="t('master.product.statusAll')"
          >
            <el-option :label="t('common.active')" :value="true" />
            <el-option :label="t('common.inactive')" :value="false" />
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
          <span>{{ t('master.product.title') }}</span>
          <el-button type="primary" :icon="Plus" @click="router.push({ name: 'product-new' })">
            {{ t('master.product.newRegistration') }}
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="items" stripe border style="width: 100%">
        <el-table-column prop="productCode" :label="t('master.product.productCode')" width="140">
          <template #default="{ row }">
            <el-link
              type="primary"
              @click="router.push({ name: 'product-edit', params: { id: row.id } })"
            >
              {{ row.productCode }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column
          prop="productName"
          :label="t('master.product.productName')"
          min-width="160"
        />
        <el-table-column
          prop="productNameKana"
          :label="t('master.product.productNameKana')"
          min-width="140"
        />
        <el-table-column :label="t('master.product.storageCondition')" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              :type="
                row.storageCondition === 'FROZEN'
                  ? 'primary'
                  : row.storageCondition === 'REFRIGERATED'
                    ? 'info'
                    : 'warning'
              "
              size="small"
            >
              {{ storageConditionLabel(row.storageCondition) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('master.product.isHazardous')" width="80" align="center">
          <template #default="{ row }">
            {{ row.isHazardous ? '●' : '—' }}
          </template>
        </el-table-column>
        <el-table-column :label="t('master.product.lotManageFlag')" width="80" align="center">
          <template #default="{ row }">
            {{ row.lotManageFlag ? '●' : '—' }}
          </template>
        </el-table-column>
        <el-table-column :label="t('master.product.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'info'" size="small">
              {{ row.isActive ? t('common.active') : t('common.inactive') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              :icon="Edit"
              @click="router.push({ name: 'product-edit', params: { id: row.id } })"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              size="small"
              :type="row.isActive ? 'danger' : 'success'"
              plain
              @click="handleToggleActive(row)"
            >
              {{ row.isActive ? t('master.product.deactivate') : t('master.product.activate') }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="t('common.noData')" />
        </template>
      </el-table>

      <!-- ページネーション -->
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
import { Search, Refresh, Plus, Edit } from '@element-plus/icons-vue'
import { useProductList } from '@/composables/master/useProductList'
import { StorageCondition } from '@/api/generated/models/storage-condition'

const { t } = useI18n()
const router = useRouter()

const {
  items,
  loading,
  total,
  page,
  pageSize,
  searchForm,
  fetchList,
  handleSearch,
  handleReset,
  handlePageChange,
  handleSizeChange,
  handleToggleActive,
  storageConditionLabel,
} = useProductList()

onMounted(() => fetchList())
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

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
