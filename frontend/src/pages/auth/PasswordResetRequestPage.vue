<template>
  <div class="reset-request-page">
    <h2 class="reset-request-page__title">{{ t('auth.resetPassword') }}</h2>
    <p class="reset-request-page__desc">{{ t('auth.resetRequestDescription') }}</p>

    <!-- 送信成功バナー -->
    <el-alert
      v-if="sent"
      :title="t('auth.messages.resetRequestSent')"
      type="success"
      show-icon
      :closable="false"
      class="reset-request-page__banner"
    />

    <!-- エラーバナー -->
    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="reset-request-page__banner"
    />

    <template v-if="!sent">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item :label="t('auth.identifier')" prop="identifier">
          <el-input
            v-model="form.identifier"
            size="large"
            autocomplete="username"
            clearable
          />
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          :loading="loading"
          native-type="submit"
          style="width: 100%; margin-top: 8px"
        >
          {{ t('auth.sendResetLink') }}
        </el-button>
      </el-form>
    </template>

    <div class="reset-request-page__footer">
      <RouterLink :to="{ name: 'login' }">{{ t('auth.backToLogin') }}</RouterLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance } from 'element-plus'
import { useResetRequest } from '@/composables/auth/useResetRequest'

const { t } = useI18n()
const formRef = ref<FormInstance>()
const { form, rules, loading, sent, errorMessage, handleSubmit } = useResetRequest(formRef)
</script>

<style scoped lang="scss">
.reset-request-page {
  &__title {
    font-size: 24px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 8px;
  }

  &__desc {
    color: #606266;
    font-size: 14px;
    margin-bottom: 24px;
  }

  &__banner {
    margin-bottom: 20px;
  }

  &__footer {
    margin-top: 16px;
    text-align: center;
    font-size: 13px;
  }
}
</style>
