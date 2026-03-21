<template>
  <div class="login-page">
    <h2 class="login-page__title">{{ t('auth.login') }}</h2>

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
          :placeholder="t('auth.userCode')"
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

    <div v-if="errorMessage" class="login-page__error">
      <el-alert :title="errorMessage" type="error" show-icon :closable="false" />
    </div>

    <div class="login-page__footer">
      <RouterLink to="/password-reset">{{ t('auth.forgotPassword') }}</RouterLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const errorMessage = ref('')

const form = reactive({
  userCode: '',
  password: '',
})

const rules: FormRules = {
  userCode: [{ required: true, message: t('validation.required', { field: t('auth.userCode') }), trigger: 'blur' }],
  password: [{ required: true, message: t('validation.passwordRequired'), trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  errorMessage.value = ''

  try {
    const user = await auth.login(form.userCode, form.password)
    if (user.passwordChangeRequired) {
      router.push({ name: 'change-password' })
    } else {
      // オープンリダイレクト対策: 相対パス（/始まり）のみ許可
      const redirectParam = route.query.redirect as string | undefined
      const redirect =
        redirectParam && redirectParam.startsWith('/') ? redirectParam : '/'
      router.push(redirect)
    }
  } catch (err: unknown) {
    const error = err as { response?: { data?: { errorCode?: string } } }
    const code = error.response?.data?.errorCode
    if (code === 'ACCOUNT_LOCKED') {
      errorMessage.value = t('auth.errors.accountLocked')
    } else if (code === 'ACCOUNT_INACTIVE') {
      errorMessage.value = t('auth.errors.accountInactive')
    } else {
      errorMessage.value = t('auth.errors.invalidCredentials')
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  &__title {
    font-size: 24px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 32px;
  }

  &__error {
    margin-top: 16px;
  }

  &__footer {
    margin-top: 16px;
    text-align: center;
  }
}
</style>
