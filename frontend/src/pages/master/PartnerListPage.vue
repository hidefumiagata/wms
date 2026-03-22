<template>
  <div class="wms-page">
    <!-- 検索フォーム -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline @submit.prevent="handleSearch">
        <el-form-item :label="t('master.partner.partnerCode')">
          <el-input
            v-model="searchForm.partnerCode"
            :placeholder="t('master.partner.partnerCode')"
            clearable
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item :label="t('master.partner.partnerName')">
          <el-input
            v-model="searchForm.partnerName"
            :placeholder="t('master.partner.partnerName')"
            clearable
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item :label="t('master.partner.partnerType')">
          <el-select
            v-model="searchForm.partnerType"
            style="width: 130px"
            clearable
            :placeholder="t('master.partner.typeAll')"
          >
            <el-option :label="t('master.partner.typeSupplier')" value="SUPPLIER" />
            <el-option :label="t('master.partner.typeCustomer')" value="CUSTOMER" />
            <el-option :label="t('master.partner.typeBoth')" value="BOTH" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('master.partner.status')">
          <el-select
            v-model="searchForm.isActive"
            style="width: 130px"
            clearable
            :placeholder="t('master.partner.statusAll')"
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
          <span>{{ t('master.partner.title') }}</span>
          <el-button type="primary" :icon="Plus" @click="router.push({ name: 'partner-new' })">
            {{ t('master.partner.newRegistration') }}
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="items" stripe border style="width: 100%">
        <el-table-column
          prop="partnerCode"
          :label="t('master.partner.partnerCode')"
          width="140"
        >
          <template #default="{ row }">
            <el-link
              type="primary"
              @click="router.push({ name: 'partner-edit', params: { id: row.id } })"
            >
              {{ row.partnerCode }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column
          prop="partnerName"
          :label="t('master.partner.partnerName')"
          min-width="160"
        />
        <el-table-column
          prop="partnerNameKana"
          :label="t('master.partner.partnerNameKana')"
          min-width="160"
        />
        <el-table-column :label="t('master.partner.partnerType')" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.partnerType === 'BOTH' ? 'warning' : row.partnerType === 'SUPPLIER' ? 'primary' : 'success'"
              size="small"
            >
              {{ partnerTypeLabel(row.partnerType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('master.partner.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'info'" size="small">
              {{ row.isActive ? t('common.active') : t('common.inactive') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('common.actions')"
          width="180"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              size="small"
              :icon="Edit"
              @click="router.push({ name: 'partner-edit', params: { id: row.id } })"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              size="small"
              :type="row.isActive ? 'danger' : 'success'"
              plain
              @click="handleToggleActive(row)"
            >
              {{ row.isActive ? t('master.partner.deactivate') : t('master.partner.activate') }}
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
import { usePartnerList } from '@/composables/master/usePartnerList'
import { PartnerType } from '@/api/generated/models/partner-type'

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
} = usePartnerList()

function partnerTypeLabel(type: string): string {
  switch (type) {
    case PartnerType.Supplier: return t('master.partner.typeSupplier')
    case PartnerType.Customer: return t('master.partner.typeCustomer')
    case PartnerType.Both: return t('master.partner.typeBoth')
    default: return type
  }
}

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
