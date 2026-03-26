<template>
  <div class="wms-page">
    <el-card>
      <el-result icon="info" :title="t('stub.title')" :sub-title="t('stub.subtitle')">
        <template #extra>
          <el-button @click="router.back()">{{ t('common.back') }}</el-button>
        </template>
      </el-result>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
const { t } = useI18n({
  messages: {
    ja: { stub: { title: 'ピッキング指示作成', subtitle: '後続Issueで実装予定' } },
    en: { stub: { title: 'ピッキング指示作成', subtitle: 'Coming soon' } },
  },
})
const router = useRouter()
</script>

<style scoped lang="scss">
.wms-page {
  padding: 20px;
}
</style>
