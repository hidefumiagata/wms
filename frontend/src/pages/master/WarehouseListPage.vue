<template>
  <div class="wms-page">
    <el-card>
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between">
          <span>{{ t('master.warehouse.title') }}</span>
          <el-button type="primary" :icon="Plus" @click="router.push('/master/warehouses/new')">
            新規登録
          </el-button>
        </div>
      </template>
      <el-empty description="倉庫マスタ画面は次のIssueで実装予定です" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'

const { t } = useI18n()
const router = useRouter()
</script>
