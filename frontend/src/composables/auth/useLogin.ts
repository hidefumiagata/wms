import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

export function useLogin(formRef: ReturnType<typeof ref<FormInstance>>) {
  const { t } = useI18n()
  const router = useRouter()
  const route = useRoute()
  const auth = useAuthStore()

  const loading = ref(false)
  const errorMessage = ref('')
  const sessionExpiredMessage = ref('')

  const form = reactive({
    userCode: '',
    password: '',
  })

  const rules: FormRules = {
    userCode: [{ required: true, message: t('auth.validation.userCodeRequired'), trigger: 'blur' }],
    password: [{ required: true, message: t('auth.validation.passwordRequired'), trigger: 'blur' }],
  }

  onMounted(() => {
    if (route.query.reason === 'session_expired') {
      sessionExpiredMessage.value = t('auth.messages.sessionExpired')
      router.replace({ name: 'login', query: { redirect: route.query.redirect } })
    }
  })

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
        const redirectParam = route.query.redirect as string | undefined
        const redirect = redirectParam && redirectParam.startsWith('/') ? redirectParam : '/'
        router.push(redirect)
      }
    } catch (err: unknown) {
      const error = err as { response?: { status?: number; data?: { errorCode?: string } } }
      const code = error.response?.data?.errorCode
      if (code === 'ACCOUNT_LOCKED') {
        errorMessage.value = t('auth.messages.accountLocked')
      } else if (!error.response) {
        errorMessage.value = t('auth.messages.loginFailed500')
      } else {
        errorMessage.value = t('auth.messages.invalidCredentials')
      }
    } finally {
      loading.value = false
    }
  }

  return { form, rules, loading, errorMessage, sessionExpiredMessage, handleLogin }
}
