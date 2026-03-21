import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // 認証専用レイアウト（アプリシェルなし）
    // requiresAuth: false = 未認証ユーザー向け（login, reset-request, reset-password）
    // requiresAuth: true  = 認証済みかつ passwordChangeRequired=true のユーザー専用（change-password）
    {
      path: '/',
      component: () => import('@/layouts/AuthLayout.vue'),
      children: [
        {
          path: 'login',
          name: 'login',
          component: () => import('@/pages/auth/LoginPage.vue'),
          meta: { requiresAuth: false },
        },
        {
          path: 'change-password',
          name: 'change-password',
          component: () => import('@/pages/auth/ChangePasswordPage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'auth/reset-request',
          name: 'reset-request',
          component: () => import('@/pages/auth/PasswordResetRequestPage.vue'),
          meta: { requiresAuth: false },
        },
        {
          path: 'auth/reset-password',
          name: 'reset-password',
          component: () => import('@/pages/auth/PasswordResetConfirmPage.vue'),
          meta: { requiresAuth: false },
        },
      ],
    },
    // メインレイアウト（認証済みページ）
    {
      path: '/',
      component: () => import('@/layouts/DefaultLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          redirect: '/master/warehouses',
        },
        // 倉庫マスタ
        {
          path: 'master/warehouses',
          name: 'warehouse-list',
          component: () => import('@/pages/master/WarehouseListPage.vue'),
        },
        {
          path: 'master/warehouses/new',
          name: 'warehouse-new',
          component: () => import('@/pages/master/WarehouseFormPage.vue'),
        },
        {
          path: 'master/warehouses/:id',
          name: 'warehouse-edit',
          component: () => import('@/pages/master/WarehouseFormPage.vue'),
        },
      ],
    },
    // 404
    {
      path: '/:pathMatch(.*)*',
      redirect: '/login',
    },
  ],
})

// ルートガード
router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.requiresAuth === false) {
    // 認証不要ページ：ログイン済みなら "/" へ
    if (auth.isAuthenticated) {
      return { path: '/' }
    }
    return true
  }

  // 認証必要ページ：未認証ならリフレッシュ試行
  if (!auth.isAuthenticated) {
    const ok = await auth.refresh()
    if (!ok) {
      return { name: 'login', query: { redirect: to.fullPath, reason: 'session_expired' } }
    }
  }

  // パスワード変更要求フラグ：change-password 以外へのアクセスはブロック
  if (auth.user?.passwordChangeRequired && to.name !== 'change-password') {
    return { name: 'change-password' }
  }

  return true
})

export default router
