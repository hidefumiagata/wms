import { ref, computed, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { z } from 'zod'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import { useAuthStore } from '@/stores/auth'
import type { UserDetail } from '@/api/generated/models/user-detail'

const USER_CODE_REGEX = /^[a-zA-Z0-9_]+$/

export function useUserForm() {
  const { t } = useI18n()
  const router = useRouter()
  const route = useRoute()
  const auth = useAuthStore()

  const userId = computed(() => {
    const id = route.params.id
    if (!id) return null
    const num = Number(id)
    return Number.isInteger(num) && num > 0 ? num : null
  })
  const isEdit = computed(() => userId.value !== null)
  const isSelf = computed(() => isEdit.value && userId.value === auth.user?.userId)

  // --- Zod スキーマ（ロケール変更時に自動再生成） ---
  // スキーマは isEdit と locale に応じて動的に決定される。
  // DefaultLayout の RouterView が :key="route.fullPath" を使用しているため、
  // /users/new → /users/:id/edit 間の遷移ではコンポーネントが再マウントされ正しいスキーマが選択される。
  const validationSchema = computed(() => {
    if (isEdit.value) {
      return toTypedSchema(
        z.object({
          userCode: z.string(),
          fullName: z
            .string()
            .min(1, t('master.user.validation.nameRequired'))
            .max(200, t('master.user.validation.nameMaxLength')),
          email: z
            .string()
            .min(1, t('master.user.validation.emailRequired'))
            .max(200, t('master.user.validation.emailMaxLength'))
            .email(t('master.user.validation.emailFormat')),
          role: z.string().min(1, t('master.user.validation.roleRequired')),
          isActive: z.boolean(),
        }),
      )
    }
    return toTypedSchema(
      z
        .object({
          userCode: z
            .string()
            .min(1, t('master.user.validation.codeRequired'))
            .max(50, t('master.user.validation.codeMaxLength'))
            .regex(USER_CODE_REGEX, t('master.user.validation.codeFormat')),
          fullName: z
            .string()
            .min(1, t('master.user.validation.nameRequired'))
            .max(200, t('master.user.validation.nameMaxLength')),
          email: z
            .string()
            .min(1, t('master.user.validation.emailRequired'))
            .max(200, t('master.user.validation.emailMaxLength'))
            .email(t('master.user.validation.emailFormat')),
          role: z.string().min(1, t('master.user.validation.roleRequired')),
          initialPassword: z
            .string()
            .min(8, t('master.user.validation.passwordMinLength'))
            .max(128, t('master.user.validation.passwordMaxLength')),
          confirmPassword: z.string().min(1, t('master.user.validation.confirmPasswordRequired')),
        })
        .refine((data) => data.initialPassword === data.confirmPassword, {
          message: t('master.user.validation.passwordMismatch'),
          path: ['confirmPassword'],
        }),
    )
  })

  const {
    errors,
    handleSubmit: createSubmitHandler,
    setFieldError,
    setValues,
    defineField,
    meta,
  } = useForm({
    validationSchema,
    initialValues: {
      userCode: '',
      fullName: '',
      email: '',
      role: '',
      isActive: true,
      initialPassword: '',
      confirmPassword: '',
    },
  })

  const [userCode, userCodeAttrs] = defineField('userCode', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })
  const [fullName, fullNameAttrs] = defineField('fullName', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })
  const [email, emailAttrs] = defineField('email', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })
  const [role, roleAttrs] = defineField('role', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })
  const [isActive, isActiveAttrs] = defineField('isActive', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })
  const [initialPassword, initialPasswordAttrs] = defineField('initialPassword', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })
  const [confirmPassword, confirmPasswordAttrs] = defineField('confirmPassword', {
    validateOnModelUpdate: false,
    validateOnBlur: true,
  })

  const loading = ref(false)
  const initialLoading = ref(false)
  const version = ref(0)
  const locked = ref(false)
  const passwordChangeRequired = ref(false)
  const createdAt = ref('')
  const updatedAt = ref('')
  const showPassword = ref(false)

  // --- 並行リクエスト制御 ---
  let abortController: AbortController | null = null
  onUnmounted(() => { abortController?.abort() })

  async function checkCodeExists() {
    if (isEdit.value) return
    const code = userCode.value
    if (!code || !USER_CODE_REGEX.test(code)) return

    try {
      const res = await apiClient.get<{ exists: boolean }>('/master/users/exists', {
        params: { code },
      })
      if (res.data.exists) {
        setFieldError('userCode', t('master.user.validation.codeDuplicate', { code: userCode.value }))
      }
    } catch {
      // サーバー側バリデーションに委ねる
    }
  }

  async function fetchUser() {
    if (!userId.value) {
      router.push({ name: 'user-list' })
      return
    }
    abortController?.abort()
    abortController = new AbortController()
    const signal = abortController.signal

    initialLoading.value = true
    try {
      const res = await apiClient.get<UserDetail>(`/master/users/${userId.value}`, { signal })
      const data = res.data
      setValues({
        userCode: data.userCode,
        fullName: data.fullName,
        email: data.email,
        role: data.role,
        isActive: data.isActive,
      })
      version.value = data.version
      locked.value = data.locked
      passwordChangeRequired.value = data.passwordChangeRequired
      createdAt.value = data.createdAt
      updatedAt.value = data.updatedAt
    } catch (err: unknown) {
      if (axios.isCancel(err)) return
      const error = toApiError(err)
      if (error.response?.status === 404) {
        ElMessage.error(t('master.user.notFound'))
        router.push({ name: 'user-list' })
      } else if (!error.response) {
        ElMessage.error(t('error.network'))
      }
    } finally {
      if (!signal.aborted) {
        initialLoading.value = false
      }
    }
  }

  const handleSubmit = createSubmitHandler(async (values) => {
    loading.value = true
    try {
      if (isEdit.value) {
        await apiClient.put(`/master/users/${userId.value}`, {
          fullName: values.fullName,
          email: values.email,
          role: values.role,
          isActive: values.isActive,
          version: version.value,
        })
        ElMessage.success(t('master.user.updateSuccess'))
      } else {
        await apiClient.post('/master/users', {
          userCode: values.userCode,
          fullName: values.fullName,
          email: values.email,
          role: values.role,
          initialPassword: values.initialPassword,
        })
        ElMessage.success(t('master.user.createSuccess'))
      }
      router.push({ name: 'user-list' })
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 409) {
        if (error.response.data?.errorCode === 'DUPLICATE_CODE') {
          setFieldError('userCode', t('master.user.validation.codeDuplicate', { code: userCode.value }))
        } else {
          ElMessage.error(t('error.optimisticLock'))
        }
      } else if (error.response.status === 422) {
        const errorCode = error.response.data?.errorCode
        if (errorCode === 'CANNOT_CHANGE_SELF_ROLE') {
          ElMessage.error(t('master.user.cannotChangeSelfRole'))
        } else if (errorCode === 'CANNOT_DEACTIVATE_SELF') {
          ElMessage.error(t('master.user.cannotDeactivateSelf'))
        }
      }
    } finally {
      loading.value = false
    }
  })

  async function handleUnlock() {
    const confirmMsg = t('master.user.confirmUnlock', {
      name: fullName.value,
      code: userCode.value,
    })

    try {
      await ElMessageBox.confirm(confirmMsg, t('common.confirm'), {
        type: 'warning',
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
      })
    } catch {
      return
    }

    loading.value = true
    try {
      await apiClient.patch(`/master/users/${userId.value}/unlock`)
      ElMessage.success(t('master.user.unlockSuccess'))
      await fetchUser()
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else {
        ElMessage.error(t('master.user.unlockError'))
      }
    } finally {
      loading.value = false
    }
  }

  async function handleCancel() {
    if (meta.value.dirty) {
      try {
        await ElMessageBox.confirm(t('master.user.confirmCancel'), t('common.confirm'), {
          type: 'warning',
          confirmButtonText: t('common.confirm'),
          cancelButtonText: t('common.cancel'),
        })
      } catch {
        return
      }
    }
    router.push({ name: 'user-list' })
  }

  return {
    userCode,
    userCodeAttrs,
    fullName,
    fullNameAttrs,
    email,
    emailAttrs,
    role,
    roleAttrs,
    isActive,
    isActiveAttrs,
    initialPassword,
    initialPasswordAttrs,
    confirmPassword,
    confirmPasswordAttrs,
    errors,
    loading,
    initialLoading,
    isEdit,
    isSelf,
    version,
    locked,
    passwordChangeRequired,
    createdAt,
    updatedAt,
    showPassword,
    fetchUser,
    handleSubmit,
    handleCancel,
    handleUnlock,
    checkCodeExists,
  }
}
