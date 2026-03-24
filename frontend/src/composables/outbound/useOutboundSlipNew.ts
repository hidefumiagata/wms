import { ref, reactive, computed } from 'vue'
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
  orderedQty: number | null
}

function formatDate(d: Date): string {
  return d.toISOString().slice(0, 10)
}

export function useOutboundSlipNew() {
  const { t } = useI18n()
  const router = useRouter()
  const warehouseStore = useWarehouseStore()

  let lineKeySeq = 1

  function createEmptyLine(): LineItem {
    return {
      key: lineKeySeq++,
      productCode: '',
      productId: null,
      productName: '',
      unitType: 'CASE',
      orderedQty: null,
    }
  }

  // --- ヘッダー ---
  const headerForm = reactive({
    slipType: 'NORMAL' as 'NORMAL' | 'WAREHOUSE_TRANSFER',
    plannedDate: '',
    partnerId: null as number | null,
    transferWarehouseId: null as number | null,
    note: '',
  })

  const isNormal = computed(() => headerForm.slipType === 'NORMAL')

  // 営業日
  const businessDate = ref<string>(formatDate(new Date()))

  async function fetchBusinessDate() {
    try {
      const res = await apiClient.get('/system/business-date')
      businessDate.value = res.data.businessDate ?? formatDate(new Date())
    } catch {
      // フォールバック
    }
  }

  // --- 明細 ---
  const lines = ref<LineItem[]>([createEmptyLine()])

  // --- 振替先倉庫プルダウン（WAREHOUSE_TRANSFER時） ---
  const warehouseOptions = ref<{ id: number; warehouseName: string }[]>([])

  async function fetchWarehouseOptions() {
    try {
      const res = await apiClient.get('/master/warehouses', {
        params: { page: 0, size: 100, isActive: true, sort: 'warehouseName,asc' },
      })
      // 現在選択中の倉庫を除外
      warehouseOptions.value = res.data.content
        .filter((w: { id: number }) => w.id !== warehouseStore.currentWarehouseId)
        .map((w: { id: number; warehouseName: string }) => ({ id: w.id, warehouseName: w.warehouseName }))
    } catch {
      // エラーは無視
    }
  }

  // --- 出荷先プルダウン ---
  const partnerOptions = ref<{ id: number; partnerName: string }[]>([])

  async function fetchPartnerOptions() {
    try {
      const [resCustomer, resBoth] = await Promise.all([
        apiClient.get('/master/partners', {
          params: { page: 0, size: 1000, isActive: true, partnerType: 'CUSTOMER', sort: 'partnerName,asc' },
        }),
        apiClient.get('/master/partners', {
          params: { page: 0, size: 1000, isActive: true, partnerType: 'BOTH', sort: 'partnerName,asc' },
        }),
      ])
      const toOption = (p: { id: number; partnerName: string }) => ({ id: p.id, partnerName: p.partnerName })
      partnerOptions.value = [
        ...resCustomer.data.content.map(toOption),
        ...resBoth.data.content.map(toOption),
      ]
    } catch {
      // エラーは無視
    }
  }

  // --- 状態 ---
  const loading = ref(false)
  const errors = ref<Record<string, string>>({})

  // --- 商品コード自動補完 ---
  async function handleProductCodeBlur(line: LineItem) {
    if (!line.productCode) {
      line.productId = null
      line.productName = ''
      return
    }
    try {
      const res = await apiClient.get('/master/products', {
        params: { productCode: line.productCode, page: 0, size: 1, isActive: true },
      })
      const products = res.data.content
      if (products.length > 0 && products[0].productCode === line.productCode) {
        line.productId = products[0].id
        line.productName = products[0].productName
      } else {
        line.productId = null
        line.productName = ''
        ElMessage.warning(t('outbound.slip.productNotFound', { code: line.productCode }))
      }
    } catch {
      ElMessage.error(t('error.network'))
    }
  }

  // --- 行操作 ---
  function addLine() { lines.value.push(createEmptyLine()) }
  function removeLine(index: number) {
    if (lines.value.length <= 1) return
    lines.value.splice(index, 1)
  }

  // --- バリデーション ---
  function validate(): boolean {
    const newErrors: Record<string, string> = {}

    if (!headerForm.plannedDate) {
      newErrors.plannedDate = t('outbound.slip.validation.plannedDateRequired')
    } else if (headerForm.plannedDate < businessDate.value) {
      newErrors.plannedDate = t('outbound.slip.validation.plannedDateTooEarly')
    }
    if (isNormal.value && !headerForm.partnerId) {
      newErrors.partnerId = t('outbound.slip.validation.partnerRequired')
    }
    if (!isNormal.value && !headerForm.transferWarehouseId) {
      newErrors.transferWarehouseId = t('outbound.slip.validation.transferWarehouseRequired')
    }

    if (lines.value.length === 0) {
      newErrors.lines = t('outbound.slip.validation.minOneLine')
    }

    lines.value.forEach((line, i) => {
      if (!line.productId) {
        newErrors[`line_${i}_productCode`] = t('outbound.slip.validation.productRequired')
      }
      if (!line.orderedQty || line.orderedQty < 1) {
        newErrors[`line_${i}_orderedQty`] = t('outbound.slip.validation.qtyRequired')
      }
    })

    // 商品重複チェック
    const productIds = lines.value.map(l => l.productId).filter(Boolean)
    if (new Set(productIds).size !== productIds.length) {
      newErrors.lines = t('outbound.slip.validation.duplicateProduct')
    }

    errors.value = newErrors
    return Object.keys(newErrors).length === 0
  }

  // --- 登録 ---
  async function handleSubmit() {
    if (!validate()) {
      ElMessage.warning(t('outbound.slip.validation.hasErrors'))
      return
    }

    loading.value = true
    try {
      const body = {
        warehouseId: warehouseStore.currentWarehouseId,
        partnerId: isNormal.value ? headerForm.partnerId : undefined,
        plannedDate: headerForm.plannedDate,
        slipType: headerForm.slipType,
        note: headerForm.note || undefined,
        lines: lines.value.map(line => ({
          productId: line.productId!,
          unitType: line.unitType,
          orderedQty: line.orderedQty!,
        })),
      }

      const res = await apiClient.post('/outbound/slips', body)
      ElMessage.success(t('outbound.slip.createSuccess', { slipNo: res.data.slipNumber }))
      router.push({ name: 'outbound-slip-detail', params: { id: res.data.id } })
    } catch (err: unknown) {
      const error = toApiError(err)
      if (!error.response) {
        ElMessage.error(t('error.network'))
      } else if (error.response.status === 409) {
        ElMessage.error(t('error.optimisticLock'))
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
          t('outbound.slip.cancelFormMessage'),
          t('common.confirm'),
          { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
        )
      } catch { return }
    }
    router.push({ name: 'outbound-slip-list' })
  }

  return {
    headerForm, lines, partnerOptions, warehouseOptions, loading, errors, isNormal, businessDate,
    fetchBusinessDate, fetchPartnerOptions, fetchWarehouseOptions, handleProductCodeBlur,
    addLine, removeLine, handleSubmit, handleCancel,
  }
}
