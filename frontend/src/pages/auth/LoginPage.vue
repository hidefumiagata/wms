<template>
  <div class="login-page">
    <h2 class="login-page__title">{{ t('auth.login') }}</h2>

    <!-- セッションタイムアウトメッセージ -->
    <el-alert
      v-if="sessionExpiredMessage"
      :title="sessionExpiredMessage"
      type="info"
      show-icon
      :closable="false"
      class="login-page__banner"
    />

    <!-- 認証エラーバナー -->
    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="login-page__banner"
    />

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="handleLogin"
    >
      <el-form-item :label="t('auth.userCode')" prop="userCode">
        <el-input
          v-model="form.userCode"
          placeholder="例: yamada.taro"
          size="large"
          autocomplete="username"
          clearable
        />
      </el-form-item>

      <el-form-item :label="t('auth.password')" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          :placeholder="t('auth.password')"
          size="large"
          autocomplete="current-password"
          show-password
        />
      </el-form-item>

      <el-button
        type="primary"
        size="large"
        :loading="loading"
        native-type="submit"
        style="width: 100%; margin-top: 8px"
      >
        {{ t('auth.loginButton') }}
      </el-button>
    </el-form>

    <div class="login-page__footer">
      <RouterLink :to="{ name: 'reset-request' }">{{ t('auth.forgotPassword') }}</RouterLink>
    </div>

    <div class="login-page__version">{{ t('auth.versionInfo') }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance } from 'element-plus'
import { useLogin } from '@/composables/auth/useLogin'

const { t } = useI18n()
const formRef = ref<FormInstance>()
const { form, rules, loading, errorMessage, sessionExpiredMessage, handleLogin } = useLogin(formRef)
</script>

<style scoped lang="scss">
.login-page {
  &__title {
    font-size: 24px;
    font-weight: 600;
    color: #303133;
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

  &__version {
    margin-top: 32px;
    font-size: 12px;
    color: #909399;
    text-align: center;
  }
}
</style>
