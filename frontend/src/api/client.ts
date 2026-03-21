import axios from 'axios'
import router from '@/router'

const apiClient = axios.create({
  baseURL: '/api/v1',
  withCredentials: true, // httpOnly Cookie を送受信（SameSite=Lax による CSRF 対策）
  headers: {
    'Content-Type': 'application/json',
  },
})

// 401: 認証切れ → ログイン画面へリダイレクト（Vue Router 経由）
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const currentPath = router.currentRoute.value.fullPath
      if (router.currentRoute.value.name !== 'login') {
        router.push({ name: 'login', query: { redirect: currentPath, reason: 'session_expired' } })
      }
    }
    return Promise.reject(error)
  },
)

export default apiClient
