import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import apiClient from '@/api/client'
import { calcPasswordStrength, type PasswordStrength } from './useChangePassword'

export function useResetConfirm(formRef: ReturnType<typeof ref<FormInstance>>) {
  const { t } = useI18n()
  const router = useRouter()
  const route = useRoute()

  const loading = ref(false)
  const errorMessage = ref('')
  const tokenMissing = ref(false)
  const token = ref('')

  const form = reactive({
    newPassword: '',
    confirmPassword: '',
  })

  const passwordStrength = computed<PasswordStrength>(() => calcPasswordStrength(form.newPassword))

  const rules: FormRules = {
    newPassword: [
      { required: true, message: t('auth.validation.newPasswordRequired'), trigger: 'blur' },
      {
        pattern: /^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9]).{8,128}$/,
        message: t('auth.validation.passwordPolicy'),
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

  onMounted(() => {
    const tokenParam = route.query.token as string | undefined
    if (!tokenParam) {
      tokenMissing.value = true
    } else {
      token.value = tokenParam
    }
  })

  async function handleSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return

    loading.value = true
    errorMessage.value = ''
    try {
      await apiClient.post('/auth/password-reset/confirm', {
        token: token.value,
        newPassword: form.newPassword,
      })
      ElMessage.success(t('auth.messages.passwordResetSuccess'))
      router.push({ name: 'login' })
    } catch (err: unknown) {
      const error = err as { response?: { status?: number } }
      if (error.response?.status === 401) {
        errorMessage.value = t('auth.messages.resetTokenInvalid')
      } else if (error.response?.status === 400) {
        errorMessage.value = t('auth.validation.passwordPolicy')
      } else {
        errorMessage.value = t('auth.messages.resetConfirmFailed')
      }
    } finally {
      loading.value = false
    }
  }

  return { form, rules, loading, errorMessage, tokenMissing, passwordStrength, handleSubmit }
}
