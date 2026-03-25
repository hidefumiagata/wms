import { vi } from 'vitest'
import { config } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createPinia, setActivePinia } from 'pinia'

// --- vue-i18n モック ---
// テスト用の最小限i18nインスタンス（翻訳キーをそのまま返す）
const i18n = createI18n({
  legacy: false,
  locale: 'ja',
  fallbackLocale: 'ja',
  missing: (_locale, key) => key,
  messages: { ja: {} },
})

config.global.plugins = [i18n]

// --- Element Plus モック ---
vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  },
  ElMessageBox: {
    confirm: vi.fn().mockResolvedValue('confirm'),
    alert: vi.fn().mockResolvedValue('confirm'),
  },
}))

// --- vue-router モック ---
const mockRouter = {
  push: vi.fn(),
  replace: vi.fn(),
  currentRoute: { value: { fullPath: '/', name: 'test', params: {}, query: {} } },
}
const mockRoute = { params: {}, query: {}, name: 'test' }

vi.mock('vue-router', () => ({
  useRouter: () => mockRouter,
  useRoute: () => mockRoute,
  createRouter: vi.fn(),
  createWebHistory: vi.fn(),
}))

// --- apiClient モック ---
vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}))

// --- @/i18n モック ---
vi.mock('@/i18n', () => ({
  default: {
    global: { t: (key: string) => key },
  },
}))

// --- @/router モック ---
vi.mock('@/router', () => ({
  default: mockRouter,
}))

// --- 各テストの前にPiniaを初期化 ---
import { beforeEach } from 'vitest'

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

// --- エクスポート（テストファイルから利用） ---
export { mockRouter, mockRoute }
