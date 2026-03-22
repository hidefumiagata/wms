/**
 * セッションタイムアウト管理モジュール
 *
 * 設計仕様: docs/architecture-design/03-frontend-architecture.md § 7.3
 * - 55分無操作で警告ダイアログ表示
 * - 60分無操作で強制ログアウト
 * - Axiosレスポンス成功時・DOM操作時にタイマーリセット
 * - BroadcastChannel によるマルチタブ間ログアウト同期
 */
import { ElMessageBox } from 'element-plus'
import apiClient from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'
import i18n from '@/i18n'

const WARNING_THRESHOLD_MS = 55 * 60 * 1000 // 55分
const TIMEOUT_MS = 60 * 60 * 1000 // 60分

const ACTIVITY_EVENTS = ['mousemove', 'click', 'keydown', 'touchstart'] as const

let warningTimer: ReturnType<typeof setTimeout> | null = null
let timeoutTimer: ReturnType<typeof setTimeout> | null = null
let warningShown = false
let isLoggingOut = false

// --- マルチタブ同期 ---
// BroadcastChannel: 同一オリジンの複数タブ間でメッセージを送受信する Web API。
// 1タブでログアウトが発生した際、他タブにも即座に伝播させる。
// 非対応ブラウザ（IE等）では機能をスキップし、単タブ動作にフォールバックする。
const bc: BroadcastChannel | null =
  typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('wms_session') : null

if (bc) {
  bc.onmessage = (event: MessageEvent<{ type: string }>) => {
    if (event.data?.type === 'logout') {
      // 他タブからのログアウト通知を受信 → このタブもログアウト
      // broadcast: false で再ブロードキャストを防ぎ無限ループを回避
      doLogout({ broadcast: false })
    }
  }
}

// --- タイマー管理 ---
function clearTimers() {
  if (warningTimer !== null) { clearTimeout(warningTimer); warningTimer = null }
  if (timeoutTimer !== null) { clearTimeout(timeoutTimer); timeoutTimer = null }
}

async function doLogout(options?: { broadcast?: boolean }) {
  if (isLoggingOut) return
  isLoggingOut = true
  clearTimers()
  ElMessageBox.close()

  // 他タブにログアウトを通知（受信側は再ブロードキャストしない）
  if (options?.broadcast !== false && bc) {
    bc.postMessage({ type: 'logout' })
  }

  try {
    await useAuthStore().logout()
  } catch {
    // logout API が失敗してもクライアント側の遷移は必ず実行する
  }
  isLoggingOut = false
  router.push({ name: 'login', query: { reason: 'session_expired' } })
}

async function showWarning() {
  warningShown = true
  const { t } = i18n.global

  let extended = false
  try {
    await ElMessageBox.confirm(
      t('auth.sessionWarning'),
      t('auth.sessionWarningTitle'),
      {
        confirmButtonText: t('auth.continueSession'),
        cancelButtonText: t('auth.logout'),
        type: 'warning',
        closeOnClickModal: false,
        closeOnPressEscape: false,
      }
    )
    extended = true
  } catch {
    // 「ログアウト」クリック or ダイアログを無視してタイムアウト
  }

  if (extended) {
    try {
      // リフレッシュAPIを呼び出してバックエンドのセッションも延長する
      // ※成功時にインターセプターが resetSessionTimer() を呼び出す
      await apiClient.post('/auth/refresh')
    } catch {
      // リフレッシュ失敗 → ログアウト
      doLogout()
    }
  } else {
    doLogout()
  }
}

function startTimers() {
  clearTimers()
  warningShown = false
  warningTimer = setTimeout(showWarning, WARNING_THRESHOLD_MS)
  timeoutTimer = setTimeout(doLogout, TIMEOUT_MS)
}

/**
 * タイマーをリセットする（警告ダイアログ表示中は更新しない）。
 * DOM 操作イベントから呼び出される。
 */
export function resetSessionTimer() {
  if (!warningShown) {
    startTimers()
  }
}

function onActivity() {
  resetSessionTimer()
}

/**
 * セッションタイマーを開始する。DefaultLayout の onMounted から呼び出す。
 */
export function startSessionTimer() {
  ACTIVITY_EVENTS.forEach((event) =>
    window.addEventListener(event, onActivity, { passive: true })
  )
  startTimers()
}

/**
 * セッションタイマーを停止する。DefaultLayout の onUnmounted から呼び出す。
 */
export function stopSessionTimer() {
  clearTimers()
  ACTIVITY_EVENTS.forEach((event) =>
    window.removeEventListener(event, onActivity)
  )
}

// Axiosレスポンス成功時にタイマーリセット（警告ダイアログ表示中も含む）
apiClient.interceptors.response.use((response) => {
  startTimers()
  return response
})
