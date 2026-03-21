import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'

export function useResetRequest(formRef: ReturnType<typeof ref<FormInstance>>) {
  const { t } = useI18n()

  const loading = ref(false)
  const sent = ref(false)
  const errorMessage = ref('')

  const form = reactive({ identifier: '' })

  const rules: FormRules = {
    identifier: [
      { required: true, message: t('auth.validation.identifierRequired'), trigger: 'blur' },
    ],
  }

  async function handleSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return

    loading.value = true
    errorMessage.value = ''
    try {
      await apiClient.post('/auth/password-reset/request', { identifier: form.identifier })
      sent.value = true
    } catch (err: unknown) {
      const error = toApiError(err)
      if (error.response?.status === 429) {
        errorMessage.value = t('auth.messages.rateLimitExceeded')
      } else {
        errorMessage.value = t('auth.messages.resetRequestFailed')
      }
    } finally {
      loading.value = false
    }
  }

  return { form, rules, loading, sent, errorMessage, handleSubmit }
}
