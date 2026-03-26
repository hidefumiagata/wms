<template>
  <div class="reset-confirm-page">
    <h2 class="reset-confirm-page__title">{{ t('auth.resetConfirm') }}</h2>

    <!-- トークンなしエラー -->
    <el-alert
      v-if="tokenMissing"
      :title="t('auth.messages.tokenMissing')"
      type="error"
      show-icon
      :closable="false"
      class="reset-confirm-page__banner"
    />

    <template v-if="!tokenMissing">
      <!-- パスワードポリシー情報バナー -->
      <el-alert
        :title="t('auth.passwordPolicyInfo')"
        type="info"
        show-icon
        :closable="false"
        class="reset-confirm-page__banner"
      />

      <!-- APIエラーバナー -->
      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        :closable="false"
        class="reset-confirm-page__banner"
      />

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item :label="t('auth.newPassword')" prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            size="large"
            show-password
            autocomplete="new-password"
          />
          <!-- パスワード強度インジケーター -->
          <div v-if="form.newPassword" class="reset-confirm-page__strength">
            <div
              class="reset-confirm-page__strength-bar"
              :class="`reset-confirm-page__strength-bar--${passwordStrength}`"
            />
            <span
              class="reset-confirm-page__strength-label"
              :class="`reset-confirm-page__strength-label--${passwordStrength}`"
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
          {{ t('auth.resetPasswordButton') }}
        </el-button>
      </el-form>
    </template>

    <div class="reset-confirm-page__footer">
      <RouterLink :to="{ name: 'login' }">{{ t('auth.backToLogin') }}</RouterLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance } from 'element-plus'
import { useResetConfirm } from '@/composables/auth/useResetConfirm'

const { t } = useI18n()
const formRef = ref<FormInstance>()
const { form, rules, loading, errorMessage, tokenMissing, passwordStrength, handleSubmit } =
  useResetConfirm(formRef)
</script>

<style scoped lang="scss">
.reset-confirm-page {
  &__title {
    font-size: 24px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 16px;
  }

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

    &--weak {
      background: #f56c6c;
    }
    &--medium {
      background: #e6a23c;
    }
    &--strong {
      background: #67c23a;
    }
  }

  &__strength-label {
    font-size: 12px;
    min-width: 24px;
    &--weak {
      color: #f56c6c;
    }
    &--medium {
      color: #e6a23c;
    }
    &--strong {
      color: #67c23a;
    }
  }

  &__footer {
    margin-top: 16px;
    text-align: center;
    font-size: 13px;
  }
}
</style>
