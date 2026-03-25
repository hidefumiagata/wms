import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { flushPromises } from '@vue/test-utils'
import { testI18n } from './test-i18n'
import type { AxiosResponse } from 'axios'

export { flushPromises }

/**
 * Composable をコンポーネント内で実行し、戻り値を取得するヘルパー。
 * onUnmounted 等のライフサイクルフックが正しく動作する。
 */
export function withSetup<T>(composable: () => T): {
  result: T
  wrapper: ReturnType<typeof mount>
} {
  let result!: T

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
      plugins: [createPinia(), testI18n],
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
 * mockAxiosResponse のファクトリ版（テスト間の共有による mutation リスクを回避）
 */
export function createMockResponseFactory<T>(data: T, status = 200) {
  return () => mockAxiosResponse(structuredClone(data), status)
}

/**
 * API エラーレスポンスを生成するヘルパー（axios.isAxiosError で認識される）
 */
export function createAxiosError(
  status: number,
  data?: { errorCode?: string; message?: string },
) {
  const error = new Error(`Request failed with status code ${status}`) as Error & {
    isAxiosError: boolean
    response: { status: number; data: typeof data }
  }
  error.isAxiosError = true
  error.response = { status, data }
  return error
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
