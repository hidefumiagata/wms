<template>
  <el-menu
    :collapse="collapsed"
    :default-active="route.path"
    router
    class="app-sidebar"
    background-color="#001529"
    text-color="#ffffffa6"
    active-text-color="#ffffff"
  >
    <div class="app-sidebar__logo">
      <span v-if="!collapsed" class="app-sidebar__logo-text">WMS</span>
      <span v-else class="app-sidebar__logo-mini">W</span>
    </div>

    <!-- マスタ管理 -->
    <el-sub-menu index="master">
      <template #title>
        <el-icon><Grid /></el-icon>
        <span>{{ t('nav.master') }}</span>
      </template>
      <el-menu-item index="/master/products">
        <el-icon><Box /></el-icon>
        <span>{{ t('nav.product') }}</span>
      </el-menu-item>
      <el-menu-item index="/master/partners">
        <el-icon><Coordinate /></el-icon>
        <span>{{ t('nav.partner') }}</span>
      </el-menu-item>
      <el-menu-item index="/master/warehouses">
        <el-icon><OfficeBuilding /></el-icon>
        <span>{{ t('nav.warehouse') }}</span>
      </el-menu-item>
      <el-menu-item index="/master/buildings">
        <el-icon><School /></el-icon>
        <span>{{ t('nav.building') }}</span>
      </el-menu-item>
      <el-menu-item index="/master/areas">
        <el-icon><Place /></el-icon>
        <span>{{ t('nav.area') }}</span>
      </el-menu-item>
      <el-menu-item index="/master/locations">
        <el-icon><Location /></el-icon>
        <span>{{ t('nav.location') }}</span>
      </el-menu-item>
      <el-menu-item index="/master/users">
        <el-icon><User /></el-icon>
        <span>{{ t('nav.user') }}</span>
      </el-menu-item>
    </el-sub-menu>

    <!-- システム管理 -->
    <el-sub-menu index="system">
      <template #title>
        <el-icon><Setting /></el-icon>
        <span>{{ t('nav.system') }}</span>
      </template>
      <el-menu-item index="/system/parameters">
        <el-icon><Operation /></el-icon>
        <span>{{ t('nav.systemParameters') }}</span>
      </el-menu-item>
    </el-sub-menu>
  </el-menu>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Grid, OfficeBuilding, Coordinate, Box, School, Place, Location, User, Setting, Operation } from '@element-plus/icons-vue'

defineProps<{
  collapsed: boolean
}>()

const route = useRoute()
const { t } = useI18n()
</script>

<style scoped lang="scss">
.app-sidebar {
  height: 100%;
  border-right: none;

  &__logo {
    height: 56px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-bottom: 1px solid #ffffff1a;
  }

  &__logo-text {
    font-size: 20px;
    font-weight: 700;
    color: #fff;
    letter-spacing: 3px;
  }

  &__logo-mini {
    font-size: 20px;
    font-weight: 700;
    color: #fff;
  }
}

:deep(.el-sub-menu__title:hover),
:deep(.el-menu-item:hover) {
  background-color: #ffffff1a !important;
}
</style>
