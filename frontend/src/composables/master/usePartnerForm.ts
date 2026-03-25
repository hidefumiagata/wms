import { ref, computed, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { z } from 'zod'
import axios from 'axios'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import type { PartnerDetail } from '@/api/generated/models/partner-detail'
import { PartnerType } from '@/api/generated/models/partner-type'
import type { CreatePartnerRequest } from '@/api/generated/models/create-partner-request'
import type { UpdatePartnerRequest } from '@/api/generated/models/update-partner-request'

// 全角カタカナ（長音・スペース含む）
const KATAKANA_REGEX = /^[ァ-ヶー　 ]*$/
const PARTNER_CODE_REGEX = /^[A-Za-z0-9\-]+$/
const PHONE_REGEX = /^[\d\-()]*$/

export function usePartnerForm() {
  const { t } = useI18n()
  const router = useRouter()
  const route = useRoute()

  const partnerId = computed(() => {
    const id = route.params.id
    if (!id) return null
    const num = Number(id)
    return Number.isInteger(num) && num > 0 ? num : null
  })
  const isEdit = computed(() => partnerId.value !== null)

  // --- Zod スキーマ（ロケール変更時に自動再生成） ---
  const validationSchema = computed(() =>
    toTypedSchema(
      z.object({
        partnerCode: z
          .string()
          .min(1, t('master.partner.validation.codeRequired'))
          .max(50, t('master.partner.validation.codeMaxLength'))
          .regex(PARTNER_CODE_REGEX, t('master.partner.validation.codeFormat')),
        partnerName: z
          .string()
          .min(1, t('master.partner.validation.nameRequired'))
          .max(200, t('master.partner.validation.nameMaxLength')),
        partnerNameKana: z
          .string()
          .min(1, t('master.partner.validation.kanaRequired'))
          .max(200, t('master.partner.validation.kanaMaxLength'))
          .regex(KATAKANA_REGEX, t('master.partner.validation.kanaFormat')),
        partnerType: z.enum([PartnerType.Supplier, PartnerType.Customer, PartnerType.Both]),
        address: z
          .string()
          .max(200, t('master.partner.validation.addressMaxLength'))
          .optional()
          .or(z.literal('')),
        phone: z
          .string()
          .max(20, t('master.partner.validation.phoneMaxLength'))
          .regex(PHONE_REGEX, t('master.partner.validation.phoneFormat'))
          .optional()
          .or(z.literal('')),
        contactPerson: z
          .string()
          .max(50, t('master.partner.validation.contactPersonMaxLength'))
          .optional()
          .or(z.literal('')),
        email: z
          .string()
          .max(254, t('master.partner.validation.emailMaxLength'))
          .email(t('master.partner.validation.emailFormat'))
          .optional()
          .or(z.literal('')),
      }),
    ),
  )

  // --- VeeValidate ---
  const {
    errors,
    handleSubmit: createSubmitHandler,
    setFieldError,
    setValues,
    defineField,
  } = useForm({
    validationSchema,
    initialValues: {
      partnerCode: '',
      partnerName: '',
      partnerNameKana: '',
      partnerType: PartnerType.Supplier as PartnerType,
      address: '',
      phone: '',
      contactPerson: '',
      email: '',
    },
  })

  const fieldOpts = { validateOnModelUpdate: false, validateOnBlur: true }

  const [partnerCode, partnerCodeAttrs] = defineField('partnerCode', fieldOpts)
  const [partnerName, partnerNameAttrs] = defineField('partnerName', fieldOpts)
  const [partnerNameKana, partnerNameKanaAttrs] = defineField('partnerNameKana', fieldOpts)
  const [partnerType, partnerTypeAttrs] = defineField('partnerType', fieldOpts)
  const [address, addressAttrs] = defineField('address', fieldOpts)
  const [phone, phoneAttrs] = defineField('phone', fieldOpts)
  const [contactPerson, contactPersonAttrs] = defineField('contactPerson', fieldOpts)
  const [email, emailAttrs] = defineField('email', fieldOpts)

  // --- 状態 ---
  const loading = ref(false)
  const initialLoading = ref(false)
  const version = ref(0)

  // --- 並行リクエスト制御 ---
  let abortController: AbortController | null = null
  onUnmounted(() => { abortController?.abort() })

  // --- API呼び出し ---
  async function checkCodeExists() {
    if (isEdit.value) return
    const code = partnerCode.value
    if (!code || !PARTNER_CODE_REGEX.test(code)) return

    try {
      const res = await apiClient.get<{ exists: boolean }>('/master/partners/exists', {
        params: { partnerCode: code },
      })
      if (res.data.exists) {
        setFieldError('partnerCode', t('master.partner.validation.codeDuplicate'))
      }
    } catch {
      // 確認失敗時はサーバー側バリデーションに委ねる
    }
  }

  async function fetchPartner() {
    if (!partnerId.value) {
      router.push({ name: 'partner-list' })
      return
    }
    abortController?.abort()
    abortController = new AbortController()
    const signal = abortController.signal

    initialLoading.value = true
    try {
      const res = await apiClient.get<PartnerDetail>(`/master/partners/${partnerId.value}`, { signal })
      setValues({
        partnerCode: res.data.partnerCode,
        partnerName: res.data.partnerName,
        partnerNameKana: res.data.partnerNameKana ?? '',
        partnerType: res.data.partnerType,
        address: res.data.address ?? '',
        phone: res.data.phone ?? '',
        contactPerson: res.data.contactPerson ?? '',
        email: res.data.email ?? '',
      })
      version.value = res.data.version
    } catch (err: unknown) {
      if (axios.isCancel(err)) return
      const error = toApiError(err)
      if (error.response?.status === 404) {
        ElMessage.error(t('master.partner.notFound'))
        router.push({ name: 'partner-list' })
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
        const body: UpdatePartnerRequest = {
          partnerName: values.partnerName,
          partnerNameKana: values.partnerNameKana,
          partnerType: values.partnerType as PartnerType,
          address: values.address || undefined,
          phone: values.phone || undefined,
          contactPerson: values.contactPerson || undefined,
          email: values.email || undefined,
          version: version.value,
        }
        await apiClient.put(`/master/partners/${partnerId.value}`, body)
        ElMessage.success(t('master.partner.updateSuccess'))
      } else {
        const body: CreatePartnerRequest = {
          partnerCode: values.partnerCode,
          partnerName: values.partnerName,
          partnerNameKana: values.partnerNameKana,
          partnerType: values.partnerType as PartnerType,
          address: values.address || undefined,
          phone: values.phone || undefined,
          contactPerson: values.contactPerson || undefined,
          email: values.email || undefined,
        }
        await apiClient.post('/master/partners', body)
        ElMessage.success(t('master.partner.createSuccess'))
      }
      router.push({ name: 'partner-list' })
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 409) {
        if (error.response.data?.errorCode === 'DUPLICATE_CODE') {
          setFieldError('partnerCode', t('master.partner.validation.codeDuplicate'))
        } else {
          ElMessage.error(t('error.optimisticLock'))
        }
      }
    } finally {
      loading.value = false
    }
  })

  function handleCancel() {
    router.push({ name: 'partner-list' })
  }

  return {
    partnerCode, partnerCodeAttrs,
    partnerName, partnerNameAttrs,
    partnerNameKana, partnerNameKanaAttrs,
    partnerType, partnerTypeAttrs,
    address, addressAttrs,
    phone, phoneAttrs,
    contactPerson, contactPersonAttrs,
    email, emailAttrs,
    errors,
    loading,
    initialLoading,
    isEdit,
    fetchPartner,
    handleSubmit,
    handleCancel,
    checkCodeExists,
  }
}
