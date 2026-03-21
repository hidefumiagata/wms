<template>
  <div class="password-reset-page">
    <h2 class="password-reset-page__title">{{ t('auth.resetPassword') }}</h2>

    <el-alert
      v-if="sent"
      :title="t('auth.passwordResetSent')"
      type="success"
      show-icon
      :closable="false"
      style="margin-bottom: 24px"
    />

    <template v-else>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item :label="t('auth.email')" prop="email">
          <el-input v-model="form.email" type="email" size="large" autocomplete="email" />
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          :loading="loading"
          native-type="submit"
          style="width: 100%; margin-top: 8px"
        >
          {{ t('auth.sendResetEmail') }}
        </el-button>
      </el-form>
    </template>

    <div v-if="errorMessage" style="margin-top: 16px">
      <el-alert :title="errorMessage" type="error" show-icon :closable="false" />
    </div>

    <div style="margin-top: 16px; text-align: center">
      <RouterLink to="/login">{{ t('auth.backToLogin') }}</RouterLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'
import apiClient from '@/api/client'

const { t } = useI18n()

const formRef = ref<FormInstance>()
const loading = ref(false)
const sent = ref(false)
const errorMessage = ref('')

const form = reactive({ email: '' })
const rules: FormRules = {
  email: [{ required: true, type: 'email', message: t('validation.emailInvalid'), trigger: 'blur' }],
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  errorMessage.value = ''
  try {
    await apiClient.post('/auth/password-reset/request', { email: form.email })
    sent.value = true
  } catch {
    errorMessage.value = t('error.server')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.password-reset-page {
  &__title {
    font-size: 24px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 32px;
  }
}
</style>
