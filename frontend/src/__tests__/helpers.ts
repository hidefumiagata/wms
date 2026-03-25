import { vi } from 'vitest'
import { defineComponent, onUnmounted } from 'vue'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createPinia } from 'pinia'
import type { AxiosResponse } from 'axios'

/**
 * Composable をコンポーネント内で実行し、戻り値を取得するヘルパー。
 * onUnmounted 等のライフサイクルフックが正しく動作する。
 */
export function withSetup<T>(composable: () => T): {
  result: T
  wrapper: ReturnType<typeof mount>
} {
  let result!: T

  const i18n = createI18n({
    legacy: false,
    locale: 'ja',
    fallbackLocale: 'ja',
    missing: (_locale, key) => key,
    messages: { ja: {} },
  })

  const TestComponent = defineComponent({
    setup() {
      result = composable()
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

  return { result, wrapper }
}

/**
 * axios レスポンスのモックを生成するヘルパー
 */
export function mockAxiosResponse<T>(data: T, status = 200): AxiosResponse<T> {
  return {
    data,
    status,
    statusText: 'OK',
    headers: {},
    config: { headers: {} } as AxiosResponse['config'],
  }
}

/**
 * AbortError（axios.isCancel が true を返すエラー）を生成するヘルパー
 */
export function createCancelError(): Error & { __CANCEL__: boolean } {
  const error = new Error('canceled') as Error & { __CANCEL__: boolean }
  error.__CANCEL__ = true
  return error
}

/**
 * API エラーレスポンスを生成するヘルパー
 */
export function createAxiosError(
  status: number,
  data?: { errorCode?: string; message?: string },
): {
  isAxiosError: boolean
  response: { status: number; data: typeof data }
} {
  return {
    isAxiosError: true,
    response: { status, data },
  }
}

/**
 * Promise を手動で resolve/reject するためのヘルパー
 */
export function createDeferredPromise<T>(): {
  promise: Promise<T>
  resolve: (value: T) => void
  reject: (reason?: unknown) => void
} {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}
