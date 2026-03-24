<template>
  <div class="wms-page">
    <el-card>
      <el-result icon="info" title="入荷予定登録" sub-title="この画面は後続Issueで実装予定です">
        <template #extra>
          <el-button @click="router.push({ name: 'inbound-slip-list' })">一覧に戻る</el-button>
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
