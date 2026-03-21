<template>
  <el-container class="default-layout">
    <!-- サイドバー -->
    <el-aside :width="sidebarCollapsed ? '64px' : '220px'" class="default-layout__aside">
      <AppSidebar :collapsed="sidebarCollapsed" />
    </el-aside>

    <el-container>
      <!-- ヘッダー -->
      <el-header class="default-layout__header">
        <AppHeader
          :sidebar-collapsed="sidebarCollapsed"
          @toggle-sidebar="sidebarCollapsed = !sidebarCollapsed"
        />
      </el-header>

      <!-- メインコンテンツ -->
      <el-main class="default-layout__main">
        <RouterView v-slot="{ Component, route }">
          <Transition name="fade" mode="out-in">
            <component :is="Component" :key="route.fullPath" />
          </Transition>
        </RouterView>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import AppHeader from '@/components/layout/AppHeader.vue'
import { startSessionTimer, stopSessionTimer } from '@/utils/session-timer'

const sidebarCollapsed = ref(false)

onMounted(() => startSessionTimer())
onUnmounted(() => stopSessionTimer())
</script>

<style scoped lang="scss">
.default-layout {
  height: 100vh;

  &__aside {
    background: #001529;
    transition: width 0.2s;
    overflow: hidden;
  }

  &__header {
    background: #fff;
    border-bottom: 1px solid #e4e7ed;
    padding: 0;
    height: 56px;
    line-height: 56px;
  }

  &__main {
    background: #f4f6f9;
    padding: 0;
    overflow-y: auto;
  }
}
</style>
