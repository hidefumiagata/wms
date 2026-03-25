<template>
  <div class="dashboard-page">
    <el-card shadow="never" class="dashboard-page__welcome">
      <div class="dashboard-page__greeting">
        <el-icon :size="48" color="var(--el-color-primary)"><HomeFilled /></el-icon>
        <div class="dashboard-page__text">
          <h2>{{ t('dashboard.welcome', { name: auth.user?.fullName ?? '' }) }}</h2>
          <p class="dashboard-page__role">{{ t(`dashboard.role.${auth.user?.role ?? 'VIEWER'}`) }}</p>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { HomeFilled } from '@element-plus/icons-vue'

const { t } = useI18n()
const auth = useAuthStore()
</script>

<style scoped lang="scss">
.dashboard-page {
  padding: 24px;

  &__welcome {
    max-width: 600px;
  }

  &__greeting {
    display: flex;
    align-items: center;
    gap: 20px;
  }

  &__text h2 {
    margin: 0 0 4px;
    font-size: 20px;
    font-weight: 600;
  }

  &__role {
    margin: 0;
    color: var(--el-text-color-secondary);
    font-size: 14px;
  }
}
</style>
