import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import i18n from '@/i18n'

const apiClient = axios.create({
  baseURL: '/api/v1',
  withCredentials: true, // httpOnly Cookie を送受信（SameSite=Lax による CSRF 対策）
  headers: {
    'Content-Type': 'application/json',
  },
})

// レスポンスインターセプター: 共通エラー処理
// 400/409/422 は処理せず各 Composable の try/catch に委ねる（設計書: architecture-blueprint/03-frontend-architecture.md）
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const { t } = i18n.global

    if (status === 401) {
      // 認証切れ → ログイン画面へリダイレクト
      const currentPath = router.currentRoute.value.fullPath
      if (router.currentRoute.value.name !== 'login') {
        router.push({ name: 'login', query: { redirect: currentPath, reason: 'session_expired' } })
      }
    } else if (status === 403) {
      ElMessage.error(t('error.forbidden'))
    } else if (status >= 500) {
      ElMessage.error(t('error.server'))
    }

    return Promise.reject(error)
  },
)

export default apiClient
