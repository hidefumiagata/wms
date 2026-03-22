<template>
  <div class="forbidden-page">
    <el-result
      icon="warning"
      :title="t('error.forbidden')"
      :sub-title="t('error.forbiddenDescription')"
    >
      <template #extra>
        <el-button type="primary" @click="router.push({ path: '/' })">
          {{ t('common.back') }}
        </el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const { t } = useI18n()
const router = useRouter()
</script>

<style scoped lang="scss">
.forbidden-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}
</style>
