<template>
  <div class="wms-page">
    <!-- 検索フォーム -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline @submit.prevent="handleSearch">
        <el-form-item :label="t('master.user.keyword')">
          <el-input
            v-model="searchForm.keyword"
            :placeholder="t('master.user.keywordPlaceholder')"
            clearable
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item :label="t('master.user.role')">
          <el-select
            v-model="searchForm.role"
            style="width: 170px"
            clearable
            :placeholder="t('master.user.roleAll')"
          >
            <el-option v-for="r in roleOptions" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('master.user.status')">
          <el-select
            v-model="searchForm.status"
            style="width: 130px"
            clearable
            :placeholder="t('master.user.statusAll')"
          >
            <el-option :label="t('master.user.statusActive')" value="ACTIVE" />
            <el-option :label="t('master.user.statusInactive')" value="INACTIVE" />
            <el-option :label="t('master.user.statusLocked')" value="LOCKED" />
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
          <span>{{ t('master.user.title') }}</span>
          <el-button type="primary" :icon="Plus" @click="router.push({ name: 'user-new' })">
            {{ t('master.user.newRegistration') }}
          </el-button>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="items"
        stripe
        border
        style="width: 100%"
        :row-class-name="rowClassName"
      >
        <el-table-column prop="userCode" :label="t('master.user.userCode')" width="140">
          <template #default="{ row }">
            <el-link
              type="primary"
              @click="router.push({ name: 'user-edit', params: { id: row.id } })"
            >
              {{ row.userCode }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="fullName" :label="t('master.user.fullName')" min-width="140" />
        <el-table-column
          prop="email"
          :label="t('master.user.email')"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column :label="t('master.user.role')" width="170" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('master.user.createdAt')" width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('master.user.status')" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.locked" type="danger" size="small">
              {{ t('master.user.statusLocked') }}
            </el-tag>
            <el-tag v-else-if="row.isActive" type="success" size="small">
              {{ t('master.user.statusActive') }}
            </el-tag>
            <el-tag v-else type="info" size="small">
              {{ t('master.user.statusInactive') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              :icon="Edit"
              @click="router.push({ name: 'user-edit', params: { id: row.id } })"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              v-if="row.locked"
              size="small"
              type="warning"
              plain
              @click="handleUnlock(row)"
            >
              {{ t('master.user.unlock') }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="t('common.noData')" />
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
import { Search, Refresh, Plus, Edit } from '@element-plus/icons-vue'
import { useUserList } from '@/composables/master/useUserList'
import { UserRole } from '@/api/generated/models/user-role'
import type { UserDetail } from '@/api/generated/models/user-detail'

const roleOptions = Object.values(UserRole)

const { t } = useI18n()
const router = useRouter()

function formatDateTime(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

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
  handleUnlock,
} = useUserList()

function rowClassName({ row }: { row: UserDetail }) {
  if (row.locked) return 'row-locked'
  if (!row.isActive) return 'row-inactive'
  return ''
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

:deep(.row-locked) {
  background-color: #fef0f0 !important;
}

:deep(.row-inactive) {
  color: #a8abb2;
}
</style>
