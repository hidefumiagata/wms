import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import apiClient from '@/api/client'
import { useAuthStore } from '@/stores/auth'

export type PasswordStrength = 'weak' | 'medium' | 'strong'

export function calcPasswordStrength(password: string): PasswordStrength {
  if (password.length < 8) return 'weak'
  const hasUpper = /[A-Z]/.test(password)
  const hasLower = /[a-z]/.test(password)
  const hasDigit = /[0-9]/.test(password)
  if (!hasUpper || !hasLower || !hasDigit) return 'weak'
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
      { min: 8, message: t('auth.validation.passwordMinLength'), trigger: 'blur' },
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
      ElMessage.success(t('auth.messages.passwordChanged'))
      router.push('/')
    } catch (err: unknown) {
      const error = err as { response?: { status?: number } }
      if (error.response?.status === 400) {
        errorMessage.value = t('auth.messages.currentPasswordWrong')
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
