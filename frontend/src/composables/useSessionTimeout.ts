import { onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

const WARNING_MS = 55 * 60 * 1000 // 55分
const TIMEOUT_MS = 60 * 60 * 1000 // 60分

const ACTIVITY_EVENTS = ['mousemove', 'click', 'keydown', 'touchstart'] as const

export function useSessionTimeout() {
  const { t } = useI18n()
  const auth = useAuthStore()
  const router = useRouter()

  let warningTimer: ReturnType<typeof setTimeout> | null = null
  let logoutTimer: ReturnType<typeof setTimeout> | null = null
  let warningShown = false

  function clearTimers() {
    if (warningTimer !== null) { clearTimeout(warningTimer); warningTimer = null }
    if (logoutTimer !== null) { clearTimeout(logoutTimer); logoutTimer = null }
  }

  async function doLogout() {
    clearTimers()
    removeListeners()
    ElMessageBox.close()
    await auth.logout()
    router.push({ name: 'login', query: { reason: 'session_expired' } })
  }

  function startTimers() {
    clearTimers()
    warningShown = false

    warningTimer = setTimeout(() => {
      warningShown = true
      ElMessageBox.confirm(
        t('session.warningMessage'),
        t('session.warningTitle'),
        {
          confirmButtonText: t('session.extend'),
          cancelButtonText: t('session.logout'),
          type: 'warning',
          closeOnClickModal: false,
          closeOnPressEscape: false,
        }
      )
        .then(() => {
          // ユーザーが「延長」を選択 → タイマーをリセット
          startTimers()
        })
        .catch(() => {
          // ユーザーが「ログアウト」を選択、またはタイムアウトで自動ログアウト
          doLogout()
        })
    }, WARNING_MS)

    logoutTimer = setTimeout(() => {
      doLogout()
    }, TIMEOUT_MS)
  }

  function onActivity() {
    // 警告ダイアログ表示中はユーザー操作でのリセットを行わない
    // （ダイアログのボタン操作でのみリセット）
    if (!warningShown) {
      startTimers()
    }
  }

  function removeListeners() {
    ACTIVITY_EVENTS.forEach((event) =>
      window.removeEventListener(event, onActivity)
    )
  }

  onMounted(() => {
    ACTIVITY_EVENTS.forEach((event) =>
      window.addEventListener(event, onActivity, { passive: true })
    )
    startTimers()
  })

  onUnmounted(() => {
    clearTimers()
    removeListeners()
  })
}
