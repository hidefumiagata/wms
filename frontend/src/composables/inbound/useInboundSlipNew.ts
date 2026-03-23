import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import apiClient from '@/api/client'
import { toApiError } from '@/utils/apiError'
import { useWarehouseStore } from '@/stores/warehouse'

interface LineItem {
  key: number
  productCode: string
  productId: number | null
  productName: string
  unitType: string
  lotNumber: string
  expiryDate: string | null
  plannedQty: number | null
  lotManageFlag: boolean
  expiryManageFlag: boolean
}

function formatDate(d: Date): string {
  return d.toISOString().slice(0, 10)
}

export function useInboundSlipNew() {
  const { t } = useI18n()
  const router = useRouter()
  const warehouseStore = useWarehouseStore()

  // lineKeySeq をcomposable内に閉じ込める（E-MAJ-01対応）
  let lineKeySeq = 1

  function createEmptyLine(): LineItem {
    return {
      key: lineKeySeq++,
      productCode: '',
      productId: null,
      productName: '',
      unitType: 'CASE',
      lotNumber: '',
      expiryDate: null,
      plannedQty: null,
      lotManageFlag: false,
      expiryManageFlag: false,
    }
  }

  // --- ヘッダー ---
  const headerForm = reactive({
    plannedDate: formatDate(new Date()), // 初期値はブラウザ日付（fetchBusinessDateで上書き）
    partnerId: null as number | null,
    note: '',
  })

  // 営業日（D-MAJ-01対応: 営業日APIから取得）
  const businessDate = ref<string>(formatDate(new Date()))

  async function fetchBusinessDate() {
    try {
      const res = await apiClient.get('/system/business-date')
      businessDate.value = res.data.businessDate ?? formatDate(new Date())
      headerForm.plannedDate = businessDate.value
    } catch {
      // 取得失敗時はブラウザ日付をフォールバック
    }
  }

  // --- 明細 ---
  const lines = ref<LineItem[]>([createEmptyLine()])

  // --- 仕入先プルダウン ---
  const partnerOptions = ref<{ id: number; partnerName: string }[]>([])

  async function fetchPartnerOptions() {
    try {
      const [resSupplier, resBoth] = await Promise.all([
        apiClient.get('/master/partners', {
          params: { page: 0, size: 1000, isActive: true, partnerType: 'SUPPLIER', sort: 'partnerName,asc' },
        }),
        apiClient.get('/master/partners', {
          params: { page: 0, size: 1000, isActive: true, partnerType: 'BOTH', sort: 'partnerName,asc' },
        }),
      ])
      const toOption = (p: { id: number; partnerName: string }) => ({ id: p.id, partnerName: p.partnerName })
      partnerOptions.value = [
        ...resSupplier.data.content.map(toOption),
        ...resBoth.data.content.map(toOption),
      ]
    } catch {
      // エラーは無視
    }
  }

  // --- 状態 ---
  const loading = ref(false)
  const errors = reactive<Record<string, string>>({})

  // --- 商品コード自動補完 ---
  async function handleProductCodeBlur(line: LineItem) {
    if (!line.productCode) {
      line.productId = null
      line.productName = ''
      line.lotManageFlag = false
      line.expiryManageFlag = false
      return
    }

    try {
      const res = await apiClient.get('/master/products', {
        params: { productCode: line.productCode, page: 0, size: 1, isActive: true },
      })
      const products = res.data.content
      if (products.length > 0 && products[0].productCode === line.productCode) {
        const product = products[0]
        line.productId = product.id
        line.productName = product.productName
        line.lotManageFlag = product.lotManageFlag ?? false
        line.expiryManageFlag = product.expiryManageFlag ?? false
        if (!line.lotManageFlag) line.lotNumber = ''
        if (!line.expiryManageFlag) line.expiryDate = null
      } else {
        line.productId = null
        line.productName = ''
        line.lotManageFlag = false
        line.expiryManageFlag = false
        ElMessage.warning(t('inbound.slip.productNotFound', { code: line.productCode }))
      }
    } catch {
      ElMessage.error(t('error.network'))
    }
  }

  // --- 行操作 ---
  function addLine() {
    lines.value.push(createEmptyLine())
  }

  function removeLine(index: number) {
    if (lines.value.length <= 1) return
    lines.value.splice(index, 1)
  }

  // --- バリデーション ---
  function validate(): boolean {
    const newErrors: Record<string, string> = {}

    // ヘッダー
    if (!headerForm.plannedDate) {
      newErrors.plannedDate = t('inbound.slip.validation.plannedDateRequired')
    } else if (headerForm.plannedDate < businessDate.value) {
      // D-CRT-01: 入荷予定日 >= 営業日
      newErrors.plannedDate = t('inbound.slip.validation.plannedDateTooEarly')
    }
    if (!headerForm.partnerId) {
      newErrors.partnerId = t('inbound.slip.validation.partnerRequired')
    }

    // D-CRT-03: 明細1行以上
    if (lines.value.length === 0) {
      newErrors.lines = t('inbound.slip.validation.minOneLine')
    }

    lines.value.forEach((line, i) => {
      if (!line.productId) {
        newErrors[`line_${i}_productCode`] = t('inbound.slip.validation.productRequired')
      }
      if (!line.plannedQty || line.plannedQty < 1) {
        newErrors[`line_${i}_plannedQty`] = t('inbound.slip.validation.qtyRequired')
      }
      if (line.lotManageFlag && !line.lotNumber) {
        newErrors[`line_${i}_lotNumber`] = t('inbound.slip.validation.lotRequired')
      }
      if (line.expiryManageFlag && !line.expiryDate) {
        newErrors[`line_${i}_expiryDate`] = t('inbound.slip.validation.expiryRequired')
      }
      // D-CRT-02: 賞味期限 >= 営業日
      if (line.expiryManageFlag && line.expiryDate && line.expiryDate < businessDate.value) {
        newErrors[`line_${i}_expiryDate`] = t('inbound.slip.validation.expiryDateTooEarly')
      }
    })

    // 商品コード重複チェック
    const productIds = lines.value.map(l => l.productId).filter(Boolean)
    if (new Set(productIds).size !== productIds.length) {
      newErrors.lines = t('inbound.slip.validation.duplicateProduct')
    }

    Object.keys(errors).forEach(key => delete errors[key])
    Object.assign(errors, newErrors)
    return Object.keys(newErrors).length === 0
  }

  // --- 登録 ---
  async function handleSubmit() {
    if (!validate()) {
      ElMessage.warning(t('inbound.slip.validation.hasErrors'))
      return
    }

    loading.value = true
    try {
      const body = {
        warehouseId: warehouseStore.currentWarehouseId,
        partnerId: headerForm.partnerId,
        plannedDate: headerForm.plannedDate,
        slipType: 'NORMAL',
        note: headerForm.note || undefined,
        lines: lines.value.map(line => ({
          productId: line.productId!,
          unitType: line.unitType,
          plannedQty: line.plannedQty!,
          lotNumber: line.lotNumber || undefined,
          expiryDate: line.expiryDate || undefined,
        })),
      }

      const res = await apiClient.post('/inbound/slips', body)
      ElMessage.success(t('inbound.slip.createSuccess', { slipNo: res.data.slipNumber }))
      router.push({ name: 'inbound-slip-detail', params: { id: res.data.id } })
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 422) {
        ElMessage.error(error.response.data?.message ?? t('error.server'))
      }
    } finally {
      loading.value = false
    }
  }

  // --- キャンセル ---
  async function handleCancel() {
    const hasData = headerForm.partnerId || lines.value.some(l => l.productCode)
    if (hasData) {
      try {
        await ElMessageBox.confirm(
          t('inbound.slip.cancelFormMessage'),
          t('common.confirm'),
          { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
        )
      } catch {
        return
      }
    }
    router.push({ name: 'inbound-slip-list' })
  }

  return {
    headerForm,
    lines,
    partnerOptions,
    loading,
    errors,
    fetchBusinessDate,
    fetchPartnerOptions,
    handleProductCodeBlur,
    addLine,
    removeLine,
    handleSubmit,
    handleCancel,
  }
}
