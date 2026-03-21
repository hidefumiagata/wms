import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import apiClient from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { toApiError } from '@/utils/apiError'

export type PasswordStrength = 'weak' | 'medium' | 'strong'

// パスワードポリシー: 8〜128文字、英大文字・英小文字・数字を各1文字以上
export const PASSWORD_POLICY_REGEX = /^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9]).{8,128}$/

export function calcPasswordStrength(password: string): PasswordStrength {
  if (!PASSWORD_POLICY_REGEX.test(password)) return 'weak'
  if (password.length >= 12) return 'strong'
  return 'medium'
}

export function useChangePassword(formRef: ReturnType<typeof ref<FormInstance>>) {
  const { t } = useI18n()
  const router = useRouter()
  const auth = useAuthStore()

  const loading = ref(false)
  const errorMessage = ref('')

  const form = reactive({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  })

  const passwordStrength = computed<PasswordStrength>(() => calcPasswordStrength(form.newPassword))

  const rules: FormRules = {
    currentPassword: [
      { required: true, message: t('auth.validation.currentPasswordRequired'), trigger: 'blur' },
    ],
    newPassword: [
      { required: true, message: t('auth.validation.newPasswordRequired'), trigger: 'blur' },
      {
        pattern: PASSWORD_POLICY_REGEX,
        message: t('auth.validation.passwordPolicy'),
        trigger: 'blur',
      },
      {
        validator: (_rule, value, callback) => {
          if (value === form.currentPassword) {
            callback(new Error(t('auth.validation.passwordSameAsCurrent')))
          } else {
            callback()
          }
        },
        trigger: 'blur',
      },
    ],
    confirmPassword: [
      { required: true, message: t('auth.validation.confirmPasswordRequired'), trigger: 'blur' },
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
    errorMessage.value = ''
    try {
      await apiClient.post('/auth/change-password', {
        currentPassword: form.currentPassword,
        newPassword: form.newPassword,
      })
      auth.clearPasswordChangeRequired()
      ElMessage({ message: t('auth.messages.passwordChanged'), type: 'success', duration: 2000 })
      setTimeout(() => router.push('/'), 500)
    } catch (err: unknown) {
      const error = toApiError(err)
      const code = error.response?.data?.errorCode
      const status = error.response?.status
      if (status === 401 && code === 'INVALID_CREDENTIALS') {
        errorMessage.value = t('auth.messages.currentPasswordWrong')
      } else if (status === 409 && code === 'SAME_PASSWORD') {
        errorMessage.value = t('auth.validation.passwordSameAsCurrent')
      } else if (status === 400) {
        errorMessage.value = t('auth.validation.passwordPolicy')
      } else {
        errorMessage.value = t('auth.messages.passwordChangeFailed500')
      }
    } finally {
      loading.value = false
    }
  }

  async function handleLogout() {
    await auth.logout()
    router.push({ name: 'login' })
  }

  return { form, rules, loading, errorMessage, passwordStrength, handleSubmit, handleLogout }
}
