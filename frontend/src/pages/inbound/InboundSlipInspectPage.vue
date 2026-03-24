<template>
  <div class="wms-page">
    <el-card>
      <el-result icon="info" title="入荷検品" sub-title="この画面は後続Issueで実装予定です">
        <template #extra>
          <el-button @click="router.back()">戻る</el-button>
        </template>
      </el-result>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<style scoped lang="scss">
.wms-page { padding: 20px; }
</style>
