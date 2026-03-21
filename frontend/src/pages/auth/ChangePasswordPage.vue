<template>
  <div class="change-password-page">
    <h2 class="change-password-page__title">{{ t('auth.changePassword') }}</h2>

    <!-- 初回ログイン注意バナー -->
    <el-alert
      :title="t('auth.passwordChangeNotice')"
      type="warning"
      show-icon
      :closable="false"
      class="change-password-page__notice"
    />

    <!-- APIエラーバナー -->
    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="change-password-page__banner"
    />

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="handleSubmit"
    >
      <el-form-item :label="t('auth.currentPassword')" prop="currentPassword">
        <el-input
          v-model="form.currentPassword"
          type="password"
          size="large"
          show-password
          autocomplete="current-password"
        />
      </el-form-item>

      <el-form-item :label="t('auth.newPassword')" prop="newPassword">
        <el-input
          v-model="form.newPassword"
          type="password"
          size="large"
          show-password
          autocomplete="new-password"
        />
        <!-- パスワード強度インジケーター -->
        <div v-if="form.newPassword" class="change-password-page__strength">
          <div
            class="change-password-page__strength-bar"
            :class="`change-password-page__strength-bar--${passwordStrength}`"
          />
          <span
            class="change-password-page__strength-label"
            :class="`change-password-page__strength-label--${passwordStrength}`"
          >
            {{ t(`auth.strength.${passwordStrength}`) }}
          </span>
        </div>
      </el-form-item>

      <el-form-item :label="t('auth.confirmPassword')" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          size="large"
          show-password
          autocomplete="new-password"
        />
      </el-form-item>

      <el-button
        type="primary"
        size="large"
        :loading="loading"
        native-type="submit"
        style="width: 100%; margin-top: 8px"
      >
        {{ t('auth.changePasswordButton') }}
      </el-button>
    </el-form>

    <div class="change-password-page__footer">
      <el-link type="info" @click="handleLogout">{{ t('auth.logoutLink') }}</el-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance } from 'element-plus'
import { useChangePassword } from '@/composables/auth/useChangePassword'

const { t } = useI18n()
const formRef = ref<FormInstance>()
const { form, rules, loading, errorMessage, passwordStrength, handleSubmit, handleLogout } =
  useChangePassword(formRef)
</script>

<style scoped lang="scss">
.change-password-page {
  &__title {
    font-size: 24px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 16px;
  }

  &__notice,
  &__banner {
    margin-bottom: 20px;
  }

  &__strength {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 6px;
  }

  &__strength-bar {
    height: 4px;
    border-radius: 2px;
    flex: 1;
    transition: background-color 0.2s;

    &--weak { background: #f56c6c; }
    &--medium { background: #e6a23c; }
    &--strong { background: #67c23a; }
  }

  &__strength-label {
    font-size: 12px;
    min-width: 24px;
    &--weak { color: #f56c6c; }
    &--medium { color: #e6a23c; }
    &--strong { color: #67c23a; }
  }

  &__footer {
    margin-top: 20px;
    text-align: center;
    font-size: 13px;
  }
}
</style>
