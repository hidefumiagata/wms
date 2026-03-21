<template>
  <div class="change-password-page">
    <h2 class="change-password-page__title">{{ t('auth.changePassword') }}</h2>
    <p style="color: #606266; margin-bottom: 24px">{{ t('auth.passwordChangeRequired') }}</p>

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
        {{ t('auth.changePassword') }}
      </el-button>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import apiClient from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  newPassword: '',
  confirmPassword: '',
})

const rules: FormRules = {
  newPassword: [{ required: true, message: t('validation.newPasswordRequired'), trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: t('validation.confirmPasswordRequired'), trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error(t('validation.passwordMismatch')))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await apiClient.post('/auth/change-password', {
      newPassword: form.newPassword,
    })
    auth.clearPasswordChangeRequired()
    ElMessage.success(t('auth.passwordChanged'))
    router.push('/')
  } catch {
    ElMessage.error(t('auth.passwordChangeFailed'))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.change-password-page {
  &__title {
    font-size: 24px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 16px;
  }
}
</style>
