import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import apiClient from '@/api/client'
import { ElMessageBox } from 'element-plus'
import { mockAxiosResponse, flushPromises } from '../helpers'
import { mockRouter } from '../setup'

// --- vi.hoisted で BroadcastChannel / crypto をモジュール読み込み前にセットアップ ---
// vi.stubGlobal は hoisted されないため、vi.hoisted 内で globalThis に直接設定する。
// session-timer.ts のモジュールレベル BroadcastChannel 生成が vi.stubGlobal より先に
// 実行されるため、vi.hoisted で事前にグローバルに設定する必要がある。
const { mockBcInstance } = vi.hoisted(() => {
  const instance = {
    postMessage: vi.fn(),
    close: vi.fn(),
    onmessage: null as ((event: MessageEvent) => void) | null,
  }
  // vi.fn(() => instance) は new で呼べないため、function コンストラクタを使う
  globalThis.BroadcastChannel = function BroadcastChannel() {
    return instance
  } as unknown as typeof BroadcastChannel
  // crypto は getter-only なので Object.defineProperty で上書き
  Object.defineProperty(globalThis, 'crypto', {
    value: { randomUUID: vi.fn(() => 'test-nonce-123') },
    writable: true,
    configurable: true,
  })
  return { mockBcInstance: instance }
})

// --- localStorage モック ---
const storageMap = new Map<string, string>()
vi.stubGlobal('localStorage', {
  getItem: vi.fn((key: string) => storageMap.get(key) ?? null),
  setItem: vi.fn((key: string, value: string) => storageMap.set(key, value)),
  removeItem: vi.fn((key: string) => storageMap.delete(key)),
  clear: vi.fn(() => storageMap.clear()),
})

// --- apiClient.interceptors モック ---
// setup.ts のモックには interceptors が含まれないため追加
let interceptorCallback: ((response: unknown) => unknown) | null = null
const mockInterceptors = {
  response: {
    use: vi.fn((onFulfilled: (response: unknown) => unknown) => {
      interceptorCallback = onFulfilled
      return 1 // interceptor ID
    }),
    eject: vi.fn(),
  },
}
;(apiClient as unknown as Record<string, unknown>).interceptors = mockInterceptors

// --- ElMessageBox.close モック ---
;(ElMessageBox as unknown as Record<string, unknown>).close = vi.fn()

// --- auth store モック ---
const mockLogout = vi.fn().mockResolvedValue(undefined)
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    logout: mockLogout,
  }),
}))

// --- テスト本体 ---
import { startSessionTimer, stopSessionTimer, resetSessionTimer } from '@/utils/session-timer'

/**
 * デフォルト値（60分/55分）で startSessionTimer を呼び出す。
 * 前のテストで変更された module-level な TIMEOUT_MS/WARNING_THRESHOLD_MS を
 * 確実にリセットするため、毎回デフォルト設定の API レスポンスを返す。
 */
async function startWithDefaults() {
  vi.mocked(apiClient.get).mockResolvedValue(
    mockAxiosResponse({ timeoutMinutes: 60, warningMinutes: 55 }),
  )
  await startSessionTimer()
}

describe('session-timer', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    storageMap.clear()
    interceptorCallback = null
    vi.mocked(apiClient.get).mockResolvedValue(
      mockAxiosResponse({ timeoutMinutes: 60, warningMinutes: 55 }),
    )
    vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse({}))
    // デフォルトで confirm ダイアログは保留状態（resolve しない）にする
    // これによりテスト間のログアウト連鎖を防ぐ
    vi.mocked(ElMessageBox.confirm).mockReturnValue(new Promise(() => {}))
  })

  afterEach(() => {
    stopSessionTimer()
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  describe('startSessionTimer', () => {
    it('GET /system/session-config を呼び出す', async () => {
      await startSessionTimer()

      expect(apiClient.get).toHaveBeenCalledWith('/system/session-config')
    })

    it('イベントリスナーを登録する', async () => {
      const addSpy = vi.spyOn(window, 'addEventListener')

      await startSessionTimer()

      const registeredEvents = addSpy.mock.calls.map((c) => c[0])
      expect(registeredEvents).toContain('mousemove')
      expect(registeredEvents).toContain('click')
      expect(registeredEvents).toContain('keydown')
      expect(registeredEvents).toContain('touchstart')

      addSpy.mockRestore()
    })

    it('visibilitychange リスナーを登録する', async () => {
      const addSpy = vi.spyOn(document, 'addEventListener')

      await startSessionTimer()

      const registeredEvents = addSpy.mock.calls.map((c) => c[0])
      expect(registeredEvents).toContain('visibilitychange')

      addSpy.mockRestore()
    })

    it('Axios レスポンスインターセプターを登録する', async () => {
      await startSessionTimer()

      expect(mockInterceptors.response.use).toHaveBeenCalled()
    })

    it('session-config 取得失敗時もエラーにならない', async () => {
      vi.mocked(apiClient.get).mockRejectedValue(new Error('network error'))

      await expect(startSessionTimer()).resolves.not.toThrow()
    })

    it('nonce をローテーションする', async () => {
      await startSessionTimer()

      expect(localStorage.setItem).toHaveBeenCalledWith('wms_bc_nonce', 'test-nonce-123')
    })
  })

  describe('stopSessionTimer', () => {
    it('イベントリスナーを解除する', async () => {
      await startSessionTimer()
      const removeSpy = vi.spyOn(window, 'removeEventListener')

      stopSessionTimer()

      const removedEvents = removeSpy.mock.calls.map((c) => c[0])
      expect(removedEvents).toContain('mousemove')
      expect(removedEvents).toContain('click')
      expect(removedEvents).toContain('keydown')
      expect(removedEvents).toContain('touchstart')

      removeSpy.mockRestore()
    })

    it('visibilitychange リスナーを解除する', async () => {
      await startSessionTimer()
      const removeSpy = vi.spyOn(document, 'removeEventListener')

      stopSessionTimer()

      const removedEvents = removeSpy.mock.calls.map((c) => c[0])
      expect(removedEvents).toContain('visibilitychange')

      removeSpy.mockRestore()
    })

    it('Axios インターセプターを eject する', async () => {
      await startSessionTimer()

      stopSessionTimer()

      expect(mockInterceptors.response.eject).toHaveBeenCalled()
    })
  })

  describe('resetSessionTimer', () => {
    it('警告表示前ならタイマーをリセットする', async () => {
      await startWithDefaults()

      // 50分経過
      vi.advanceTimersByTime(50 * 60 * 1000)

      // リセットすると警告タイマーも再開される
      resetSessionTimer()

      // さらに50分進めても（リセット後50分 < 55分）警告は出ない
      vi.advanceTimersByTime(50 * 60 * 1000)
      expect(ElMessageBox.confirm).not.toHaveBeenCalled()
    })
  })

  describe('警告ダイアログ', () => {
    it('55分後に警告ダイアログを表示する', async () => {
      await startWithDefaults()

      vi.advanceTimersByTime(55 * 60 * 1000)

      expect(ElMessageBox.confirm).toHaveBeenCalledWith(
        'auth.sessionWarning',
        'auth.sessionWarningTitle',
        expect.objectContaining({
          type: 'warning',
          closeOnClickModal: false,
          closeOnPressEscape: false,
        }),
      )
    })

    it('警告で「継続」を選択するとリフレッシュAPIを呼ぶ', async () => {
      vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm')

      await startWithDefaults()
      vi.advanceTimersByTime(55 * 60 * 1000)

      // confirm のPromise解決を待つ
      await vi.runAllTimersAsync()

      expect(apiClient.post).toHaveBeenCalledWith('/auth/refresh')
    })

    it('警告で「ログアウト」を選択するとログアウトする', async () => {
      vi.mocked(ElMessageBox.confirm).mockRejectedValue('cancel')

      await startWithDefaults()
      vi.advanceTimersByTime(55 * 60 * 1000)

      await vi.runAllTimersAsync()

      expect(mockLogout).toHaveBeenCalled()
      expect(mockRouter.push).toHaveBeenCalledWith({
        name: 'login',
        query: { reason: 'session_expired' },
      })
    })
  })

  describe('タイムアウト（強制ログアウト）', () => {
    it('60分後に強制ログアウトする', async () => {
      await startWithDefaults()
      vi.advanceTimersByTime(60 * 60 * 1000)

      await vi.runAllTimersAsync()

      expect(mockLogout).toHaveBeenCalled()
      expect(mockRouter.push).toHaveBeenCalledWith({
        name: 'login',
        query: { reason: 'session_expired' },
      })
    })
  })

  describe('アクティビティスロットリング', () => {
    it('30秒以内の mousemove ではタイマーリセットしない', async () => {
      await startWithDefaults()

      // 10秒後に mousemove を発火（スロットリング30秒以内なのでリセットされない）
      vi.advanceTimersByTime(10_000)
      window.dispatchEvent(new Event('mousemove'))

      // 開始から55分経過すると警告が出る（リセットされていないため）
      vi.advanceTimersByTime(55 * 60 * 1000 - 10_000)
      expect(ElMessageBox.confirm).toHaveBeenCalled()
    })

    it('30秒経過後の mousemove ではタイマーがリセットされる', async () => {
      await startWithDefaults()

      // 31秒後に mousemove（スロットリング超過なのでリセットされる）
      vi.advanceTimersByTime(31_000)
      window.dispatchEvent(new Event('mousemove'))

      // リセットされたので、ここから55分後まで警告は出ない
      vi.advanceTimersByTime(54 * 60 * 1000)
      expect(ElMessageBox.confirm).not.toHaveBeenCalled()

      // さらに1分進めると（リセット後55分）→ 警告
      vi.advanceTimersByTime(1 * 60 * 1000)
      expect(ElMessageBox.confirm).toHaveBeenCalled()
    })
  })

  describe('applySessionConfig（バリデーション）', () => {
    it('サーバーから取得した値を適用する', async () => {
      vi.mocked(apiClient.get).mockResolvedValue(
        mockAxiosResponse({ timeoutMinutes: 30, warningMinutes: 25 }),
      )

      await startSessionTimer()

      // 25分で警告が出る
      vi.advanceTimersByTime(25 * 60 * 1000)
      expect(ElMessageBox.confirm).toHaveBeenCalled()
    })

    it('warning >= timeout の場合、timeout - 5分にフォールバックする', async () => {
      vi.mocked(apiClient.get).mockResolvedValue(
        mockAxiosResponse({ timeoutMinutes: 10, warningMinutes: 10 }),
      )

      await startSessionTimer()

      // warning = 10分 >= timeout = 10分 → warning = 10 - 5 = 5分
      vi.advanceTimersByTime(5 * 60 * 1000)
      expect(ElMessageBox.confirm).toHaveBeenCalled()
    })

    it('上限 8時間を超える値はクランプされる', async () => {
      vi.mocked(apiClient.get).mockResolvedValue(
        mockAxiosResponse({ timeoutMinutes: 600, warningMinutes: 590 }),
      )

      await startSessionTimer()

      // timeout は 480分（8時間）にクランプ、warning は 480分にクランプ
      // warning >= timeout なので warning = 480 - 5 = 475分
      // 475分で警告が出ることを確認
      vi.advanceTimersByTime(475 * 60 * 1000)
      expect(ElMessageBox.confirm).toHaveBeenCalled()
    })

    it('不正な値（非数値）の場合、前回の値を維持する', async () => {
      // まずデフォルト値で初期化
      await startWithDefaults()
      stopSessionTimer()

      // 不正な値で再スタート
      vi.mocked(apiClient.get).mockResolvedValue(
        mockAxiosResponse({ timeoutMinutes: 'invalid', warningMinutes: null }),
      )

      await startSessionTimer()

      // 前回のデフォルト値（55分）で警告
      vi.advanceTimersByTime(55 * 60 * 1000)
      expect(ElMessageBox.confirm).toHaveBeenCalled()
    })
  })

  describe('BroadcastChannel', () => {
    it('ログアウト時に他タブへ通知する', async () => {
      // confirm ダイアログは保留状態にして、60分のタイムアウトで doLogout が呼ばれるのを待つ
      await startWithDefaults()
      vi.advanceTimersByTime(60 * 60 * 1000)

      await flushPromises()

      // doLogout が呼ばれたことを確認
      expect(mockLogout).toHaveBeenCalled()
      // BroadcastChannel で他タブに通知
      expect(mockBcInstance.postMessage).toHaveBeenCalledWith({
        type: 'logout',
        nonce: expect.any(String),
      })
    })

    it('他タブからのログアウト通知を受信して自タブもログアウトする', async () => {
      await startWithDefaults()

      // onmessage ハンドラに nonce 付きメッセージを送信
      const nonce = storageMap.get('wms_bc_nonce') ?? 'test-nonce-123'
      if (mockBcInstance.onmessage) {
        mockBcInstance.onmessage(
          new MessageEvent('message', {
            data: { type: 'logout', nonce },
          }),
        )
      }

      await vi.runAllTimersAsync()

      expect(mockLogout).toHaveBeenCalled()
    })

    it('nonce が一致しないメッセージは無視する', async () => {
      await startWithDefaults()
      // mockLogout をクリアして、startSessionTimer 由来の呼び出しと区別
      mockLogout.mockClear()

      if (mockBcInstance.onmessage) {
        mockBcInstance.onmessage(
          new MessageEvent('message', {
            data: { type: 'logout', nonce: 'wrong-nonce' },
          }),
        )
      }

      // onmessage 後すぐに確認（タイマーは進めない）
      expect(mockLogout).not.toHaveBeenCalled()
    })

    it('不正な型のメッセージは無視する', async () => {
      await startWithDefaults()
      mockLogout.mockClear()

      if (mockBcInstance.onmessage) {
        mockBcInstance.onmessage(
          new MessageEvent('message', {
            data: { type: 'something_else' },
          }),
        )
      }

      expect(mockLogout).not.toHaveBeenCalled()
    })
  })

  describe('Page Visibility API', () => {
    it('バックグラウンドから復帰時、タイムアウト超過で即ログアウト', async () => {
      await startWithDefaults()

      // 61分経過をシミュレート（タイマーも進むが doLogout が先に呼ばれるのを防ぐため
      // confirm を保留にしている）
      vi.advanceTimersByTime(61 * 60 * 1000)

      // visibilityState を 'visible' にしてイベント発火
      Object.defineProperty(document, 'visibilityState', {
        value: 'visible',
        writable: true,
        configurable: true,
      })
      document.dispatchEvent(new Event('visibilitychange'))

      await vi.runAllTimersAsync()

      expect(mockLogout).toHaveBeenCalled()
    })

    it('バックグラウンドから復帰時、警告閾値超過で警告表示', async () => {
      await startWithDefaults()

      // 56分経過（警告閾値55分超過だがタイムアウト60分未満）
      vi.advanceTimersByTime(56 * 60 * 1000)

      Object.defineProperty(document, 'visibilityState', {
        value: 'visible',
        writable: true,
        configurable: true,
      })
      document.dispatchEvent(new Event('visibilitychange'))

      // visibilitychange ハンドラ内で showWarning が呼ばれる
      expect(ElMessageBox.confirm).toHaveBeenCalled()
    })

    it('バックグラウンドから復帰時、閾値未満なら残り時間でタイマー再設定', async () => {
      await startWithDefaults()

      // 30分経過（閾値未満）
      vi.advanceTimersByTime(30 * 60 * 1000)

      Object.defineProperty(document, 'visibilityState', {
        value: 'visible',
        writable: true,
        configurable: true,
      })
      document.dispatchEvent(new Event('visibilitychange'))

      // まだ警告は出ない
      expect(ElMessageBox.confirm).not.toHaveBeenCalled()

      // 残り25分で警告が出る（開始から55分）
      vi.advanceTimersByTime(25 * 60 * 1000)
      expect(ElMessageBox.confirm).toHaveBeenCalled()
    })

    it('hidden 状態では何もしない', async () => {
      await startWithDefaults()

      Object.defineProperty(document, 'visibilityState', {
        value: 'hidden',
        writable: true,
        configurable: true,
      })
      document.dispatchEvent(new Event('visibilitychange'))

      expect(ElMessageBox.confirm).not.toHaveBeenCalled()
      expect(mockLogout).not.toHaveBeenCalled()
    })
  })

  describe('Axios インターセプターによるリセット', () => {
    it('レスポンス成功時にタイマーをリセットする', async () => {
      await startWithDefaults()

      // 50分経過
      vi.advanceTimersByTime(50 * 60 * 1000)

      // インターセプターのコールバックを直接呼び出し（APIレスポンス成功をシミュレート）
      if (interceptorCallback) {
        interceptorCallback({ status: 200 })
      }

      // リセットされたので、さらに50分経過しても警告は出ない
      vi.advanceTimersByTime(50 * 60 * 1000)
      expect(ElMessageBox.confirm).not.toHaveBeenCalled()

      // さらに5分で警告
      vi.advanceTimersByTime(5 * 60 * 1000)
      expect(ElMessageBox.confirm).toHaveBeenCalled()
    })

    it('stopSessionTimer 後はインターセプターがタイマーリセットしない', async () => {
      await startWithDefaults()

      // interceptorCallback をキャプチャ
      const savedCallback = interceptorCallback

      stopSessionTimer()

      // stop後にインターセプターを呼んでもタイマーは設定されない
      if (savedCallback) {
        savedCallback({ status: 200 })
      }

      // 55分進めても警告は出ない（タイマーが設定されていないため）
      vi.advanceTimersByTime(55 * 60 * 1000)
      expect(ElMessageBox.confirm).not.toHaveBeenCalled()
    })
  })

  describe('doLogout', () => {
    it('ログアウト時に ElMessageBox.close を呼ぶ', async () => {
      vi.mocked(ElMessageBox.confirm).mockRejectedValue('cancel')

      await startWithDefaults()
      vi.advanceTimersByTime(55 * 60 * 1000)

      await vi.runAllTimersAsync()

      expect(ElMessageBox.close).toHaveBeenCalled()
    })

    it('ログアウト時に nonce を削除する', async () => {
      vi.mocked(ElMessageBox.confirm).mockRejectedValue('cancel')

      await startWithDefaults()
      vi.advanceTimersByTime(55 * 60 * 1000)

      await vi.runAllTimersAsync()

      expect(localStorage.removeItem).toHaveBeenCalledWith('wms_bc_nonce')
    })

    it('ログアウト時にログイン画面へ遷移する', async () => {
      vi.mocked(ElMessageBox.confirm).mockRejectedValue('cancel')

      await startWithDefaults()
      vi.advanceTimersByTime(55 * 60 * 1000)

      await vi.runAllTimersAsync()

      expect(mockRouter.push).toHaveBeenCalledWith({
        name: 'login',
        query: { reason: 'session_expired' },
      })
    })

    it('logout API が失敗してもログイン画面への遷移は実行される', async () => {
      vi.mocked(ElMessageBox.confirm).mockRejectedValue('cancel')
      mockLogout.mockRejectedValue(new Error('logout failed'))

      await startWithDefaults()
      vi.advanceTimersByTime(55 * 60 * 1000)

      await vi.runAllTimersAsync()

      expect(mockRouter.push).toHaveBeenCalledWith({
        name: 'login',
        query: { reason: 'session_expired' },
      })
    })
  })

  describe('セッション延長失敗', () => {
    it('リフレッシュAPI失敗時にログアウトする', async () => {
      vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm')
      vi.mocked(apiClient.post).mockRejectedValue(new Error('refresh failed'))

      await startWithDefaults()
      vi.advanceTimersByTime(55 * 60 * 1000)

      await vi.runAllTimersAsync()

      expect(mockLogout).toHaveBeenCalled()
    })
  })
})
