<template>
  <div class="app-header">
    <div class="app-header__left">
      <el-button
        :icon="sidebarCollapsed ? Expand : Fold"
        text
        size="large"
        @click="$emit('toggleSidebar')"
      />
    </div>

    <div class="app-header__right">
      <!-- 倉庫切替 -->
      <el-select
        v-if="warehouseStore.warehouses.length > 0"
        :model-value="warehouseStore.selectedWarehouseId"
        :placeholder="t('nav.selectWarehouse')"
        size="small"
        style="width: 200px"
        @update:model-value="warehouseStore.selectWarehouse($event as number)"
      >
        <el-option
          v-for="wh in warehouseStore.warehouses"
          :key="wh.id"
          :label="`${wh.warehouseName} (${wh.warehouseCode})`"
          :value="wh.id"
        />
      </el-select>

      <!-- 言語切替 -->
      <el-select v-model="locale" style="width: 80px" size="small">
        <el-option label="日本語" value="ja" />
        <el-option label="English" value="en" />
      </el-select>

      <!-- ユーザーメニュー -->
      <el-dropdown @command="handleCommand">
        <span class="app-header__user">
          <el-avatar :size="32" icon="UserFilled" />
          <span class="app-header__username">{{ auth.user?.fullName }}</span>
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="change-password">
              {{ t('auth.changePassword') }}
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>{{ t('auth.logout') }}</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { Expand, Fold, ArrowDown } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useWarehouseStore } from '@/stores/warehouse'

defineProps<{
  sidebarCollapsed: boolean
}>()
defineEmits<{
  toggleSidebar: []
}>()

const { locale, t } = useI18n()
const router = useRouter()
const auth = useAuthStore()
const warehouseStore = useWarehouseStore()

async function handleCommand(command: string) {
  if (command === 'logout') {
    await ElMessageBox.confirm(t('auth.logoutConfirm'), t('common.confirm'), { type: 'warning' })
    await auth.logout()
    router.push('/login')
  } else if (command === 'change-password') {
    router.push('/change-password')
  }
}
</script>

<style scoped lang="scss">
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  padding: 0 16px;

  &__right {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  &__user {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    color: #303133;
  }

  &__username {
    font-size: 14px;
  }
}
</style>
