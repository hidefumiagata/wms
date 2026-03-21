import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // 認証レイアウト
    {
      path: '/login',
      component: () => import('@/layouts/AuthLayout.vue'),
      children: [
        {
          path: '',
          name: 'login',
          component: () => import('@/pages/auth/LoginPage.vue'),
          meta: { requiresAuth: false },
        },
        {
          path: '/change-password',
          name: 'change-password',
          component: () => import('@/pages/auth/ChangePasswordPage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: '/password-reset',
          name: 'password-reset',
          component: () => import('@/pages/auth/PasswordResetPage.vue'),
          meta: { requiresAuth: false },
        },
      ],
    },
    // メインレイアウト
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
      redirect: '/',
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
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  // パスワード変更要求フラグ
  if (auth.user?.passwordChangeRequired && to.name !== 'change-password') {
    return { name: 'change-password' }
  }

  return true
})

export default router
