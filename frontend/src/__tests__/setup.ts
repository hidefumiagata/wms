import { vi } from 'vitest'
import { config } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { testI18n } from './test-i18n'

config.global.plugins = [testI18n]

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

// --- axios モック（isCancel / isAxiosError を含む） ---
vi.mock('axios', async (importOriginal) => {
  const actual = await importOriginal<typeof import('axios')>()
  return {
    ...actual,
    default: {
      ...actual.default,
      isCancel: vi.fn((err: unknown) => {
        return err instanceof actual.default.CanceledError
      }),
      isAxiosError: vi.fn((err: unknown) => {
        return (err as { isAxiosError?: boolean })?.isAxiosError === true
      }),
    },
  }
})

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
