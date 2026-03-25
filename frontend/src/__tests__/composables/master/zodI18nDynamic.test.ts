/**
 * Zodスキーマのi18n動的評価テスト
 *
 * ロケール切替時にバリデーションメッセージが自動的に更新されることを検証する。
 * 代表として useWarehouseForm を使用し、computed スキーマの動作を確認する。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { useWarehouseForm } from '@/composables/master/useWarehouseForm'

// vue-router モック
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useRoute: () => ({ params: {}, query: {}, name: 'test' }),
  createRouter: vi.fn(),
  createWebHistory: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') },
}))

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}))

vi.mock('@/i18n', () => ({
  default: { global: { t: (key: string) => key } },
}))

vi.mock('@/router', () => ({
  default: { push: vi.fn(), replace: vi.fn() },
}))

// 日英2言語の翻訳メッセージ（テストに必要な最小限）
const messages = {
  ja: {
    master: {
      warehouse: {
        validation: {
          codeRequired: '倉庫コードは必須です',
          codeFormat: '倉庫コードは英大文字4桁で入力してください',
          nameRequired: '倉庫名は必須です',
          nameMaxLength: '倉庫名は200文字以内で入力してください',
          kanaRequired: '倉庫名カナは必須です',
          kanaMaxLength: '倉庫名カナは200文字以内で入力してください',
          kanaFormat: 'カタカナで入力してください',
          addressMaxLength: '住所は500文字以内で入力してください',
        },
      },
    },
  },
  en: {
    master: {
      warehouse: {
        validation: {
          codeRequired: 'Warehouse code is required',
          codeFormat: 'Warehouse code must be 4 uppercase letters',
          nameRequired: 'Warehouse name is required',
          nameMaxLength: 'Warehouse name must be 200 characters or less',
          kanaRequired: 'Warehouse name kana is required',
          kanaMaxLength: 'Warehouse name kana must be 200 characters or less',
          kanaFormat: 'Please enter in katakana',
          addressMaxLength: 'Address must be 500 characters or less',
        },
      },
    },
  },
}

function createTestI18n(locale: 'ja' | 'en' = 'ja') {
  return createI18n({
    legacy: false,
    locale,
    fallbackLocale: 'ja',
    messages,
  })
}

/**
 * Composable を実際の i18n インスタンスとともにマウントし、
 * locale を動的に切り替えられるようにする。
 */
function mountWithI18n() {
  const i18n = createTestI18n('ja')
  let result!: ReturnType<typeof useWarehouseForm>

  const TestComponent = defineComponent({
    setup() {
      result = useWarehouseForm()
      return {}
    },
    render() {
      return null
    },
  })

  const wrapper = mount(TestComponent, {
    global: {
      plugins: [createPinia(), i18n],
    },
  })

  return { result, wrapper, i18n }
}

describe('Zodスキーマ i18n動的評価', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('日本語ロケールでバリデーションメッセージが日本語になる', async () => {
    const { result } = mountWithI18n()

    // 空の状態で submit してバリデーションエラーを発生させる
    await result.handleSubmit()
    await nextTick()

    // errors にはフィールド名をキーとしたバリデーションメッセージが入る
    expect(result.errors.value.warehouseCode).toContain('倉庫コードは必須です')
    expect(result.errors.value.warehouseName).toContain('倉庫名は必須です')
  })

  it('ロケール切替後にバリデーションメッセージが英語に更新される', async () => {
    const { result, i18n } = mountWithI18n()

    // まず日本語でバリデーション
    await result.handleSubmit()
    await nextTick()
    expect(result.errors.value.warehouseCode).toContain('倉庫コードは必須です')

    // ロケールを英語に切替
    i18n.global.locale.value = 'en'
    await nextTick()

    // 再度バリデーション実行
    await result.handleSubmit()
    await nextTick()

    expect(result.errors.value.warehouseCode).toContain('Warehouse code is required')
    expect(result.errors.value.warehouseName).toContain('Warehouse name is required')
  })

  it('英語→日本語に戻してもメッセージが正しく切り替わる', async () => {
    const { result, i18n } = mountWithI18n()

    // 英語に切替
    i18n.global.locale.value = 'en'
    await nextTick()

    await result.handleSubmit()
    await nextTick()
    expect(result.errors.value.warehouseCode).toContain('Warehouse code is required')

    // 日本語に戻す
    i18n.global.locale.value = 'ja'
    await nextTick()

    await result.handleSubmit()
    await nextTick()
    expect(result.errors.value.warehouseCode).toContain('倉庫コードは必須です')
  })
})
