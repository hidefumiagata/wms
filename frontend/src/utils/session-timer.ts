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

// デフォルト値（API取得前 or 取得失敗時のフォールバック）
let WARNING_THRESHOLD_MS = 55 * 60 * 1000 // 55分
let TIMEOUT_MS = 60 * 60 * 1000 // 60分

const ACTIVITY_EVENTS = ['mousemove', 'click', 'keydown', 'touchstart'] as const

let warningTimer: ReturnType<typeof setTimeout> | null = null
let timeoutTimer: ReturnType<typeof setTimeout> | null = null
let warningShown = false
let isLoggingOut = false
let lastActiveAt = Date.now()

// --- マルチタブ同期 ---
// BroadcastChannel: 同一オリジンの複数タブ間でメッセージを送受信する Web API。
// 1タブでログアウトが発生した際、他タブにも即座に伝播させる。
// 非対応ブラウザ（IE等）では機能をスキップし、単タブ動作にフォールバックする。
// BroadcastChannel は DefaultLayout のライフサイクルではなくモジュールレベルで保持する。
// SPA のルート遷移で DefaultLayout が再マウントされてもチャンネルを維持するため。
const bc: BroadcastChannel | null =
  typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('wms_session') : null

// --- nonce 管理 ---
// localStorage を通じてタブ間で nonce を共有し、nonce を知らない外部スクリプト
// （ブラウザ拡張機能等）からの強制ログアウト注入（ポイズニング）を阻止する。
// XSS が成立した場合は localStorage も読めるため完全な防御ではないが、
// チャンネル名だけを知る単純な注入攻撃を防ぐ多層防御として有効。
const BC_NONCE_KEY = 'wms_bc_nonce'

function getSessionNonce(): string {
  let nonce = localStorage.getItem(BC_NONCE_KEY)
  if (!nonce) {
    nonce = crypto.randomUUID()
    localStorage.setItem(BC_NONCE_KEY, nonce)
  }
  return nonce
}

// メッセージの実行時型ガード
// TypeScript の型注釈はコンパイル時のみ有効なため、
// 外部から届いた payload の構造を実行時に検証する
function isLogoutMessage(data: unknown): data is { type: 'logout'; nonce: string } {
  return (
    typeof data === 'object' &&
    data !== null &&
    (data as Record<string, unknown>)['type'] === 'logout' &&
    typeof (data as Record<string, unknown>)['nonce'] === 'string'
  )
}

if (bc) {
  bc.onmessage = (event: MessageEvent<unknown>) => {
    // 型ガード + nonce 検証の両方を通過したメッセージのみ処理する
    if (isLogoutMessage(event.data) && event.data.nonce === getSessionNonce()) {
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
  // nonce を含めることで、チャンネル名だけを知る外部スクリプトの注入を防ぐ
  if (options?.broadcast !== false && bc) {
    bc.postMessage({ type: 'logout', nonce: getSessionNonce() })
  }

  try {
    await useAuthStore().logout()
  } catch {
    // logout API が失敗してもクライアント側の遷移は必ず実行する
  }
  // isLoggingOut はリセットしない。ログアウト後のページ遷移中に
  // BroadcastChannel や visibilitychange から再度 doLogout が呼ばれるのを防ぐ。
  // 次回ログイン時に startSessionTimer() 内で再初期化される。
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
  lastActiveAt = Date.now()
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
 * Page Visibility API による復帰時チェック。
 * バックグラウンドタブやPCスリープから復帰した際、setTimeout の遅延を補償する。
 * 実際の経過時間を計算し、タイムアウト閾値を超えていれば即座に警告またはログアウトする。
 * 閾値未満の場合は残り時間でタイマーを再設定する（全時間リセットではない）。
 */
function onVisibilityChange() {
  if (document.visibilityState !== 'visible') return
  if (isLoggingOut) return

  const elapsed = Date.now() - lastActiveAt
  if (elapsed >= TIMEOUT_MS) {
    doLogout()
  } else if (elapsed >= WARNING_THRESHOLD_MS && !warningShown) {
    showWarning()
  } else if (!warningShown) {
    // 閾値未満 → 残り時間でタイマーを再設定（lastActiveAt は更新しない）
    clearTimers()
    const warningRemaining = WARNING_THRESHOLD_MS - elapsed
    const timeoutRemaining = TIMEOUT_MS - elapsed
    warningTimer = setTimeout(showWarning, warningRemaining)
    timeoutTimer = setTimeout(doLogout, timeoutRemaining)
  }
}

/**
 * セッションタイマーを開始する。DefaultLayout の onMounted から呼び出す。
 * バックエンドからセッション設定を取得し、タイムアウト値を更新する。
 */
export async function startSessionTimer() {
  isLoggingOut = false
  try {
    const { data } = await apiClient.get('/system/session-config')
    const timeout = data?.timeoutMinutes
    const warning = data?.warningMinutes
    if (typeof timeout === 'number' && timeout > 0) {
      TIMEOUT_MS = timeout * 60 * 1000
    }
    if (typeof warning === 'number' && warning >= 0) {
      WARNING_THRESHOLD_MS = warning * 60 * 1000
    }
  } catch {
    // API取得失敗時はデフォルト値を維持
  }
  ACTIVITY_EVENTS.forEach((event) =>
    window.addEventListener(event, onActivity, { passive: true })
  )
  document.addEventListener('visibilitychange', onVisibilityChange)
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
  document.removeEventListener('visibilitychange', onVisibilityChange)
}

// Axiosレスポンス成功時にタイマーリセット（警告ダイアログ表示中も含む）
apiClient.interceptors.response.use((response) => {
  startTimers()
  return response
})
