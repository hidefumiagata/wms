import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
// RouteMeta 型拡張は types/router.d.ts で定義

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
        // 403 Forbidden（認証済みでも権限なし）—— DefaultLayout 下でナビゲーションを維持
        {
          path: 'forbidden',
          name: 'forbidden',
          component: () => import('@/pages/ForbiddenPage.vue'),
        },
        // 商品マスタ — SCR-05: SYSTEM_ADMIN, WAREHOUSE_MANAGER
        {
          path: 'master/products',
          name: 'product-list',
          component: () => import('@/pages/master/ProductListPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER'] },
        },
        {
          path: 'master/products/new',
          name: 'product-new',
          component: () => import('@/pages/master/ProductFormPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER'] },
        },
        {
          path: 'master/products/:id/edit',
          name: 'product-edit',
          component: () => import('@/pages/master/ProductFormPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER'] },
        },
        // 取引先マスタ — SCR-03: SYSTEM_ADMIN
        {
          path: 'master/partners',
          name: 'partner-list',
          component: () => import('@/pages/master/PartnerListPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN'] },
        },
        {
          path: 'master/partners/new',
          name: 'partner-new',
          component: () => import('@/pages/master/PartnerFormPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN'] },
        },
        {
          path: 'master/partners/:id/edit',
          name: 'partner-edit',
          component: () => import('@/pages/master/PartnerFormPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN'] },
        },
        // 倉庫マスタ — SCR-04: SYSTEM_ADMIN, WAREHOUSE_MANAGER
        {
          path: 'master/warehouses',
          name: 'warehouse-list',
          component: () => import('@/pages/master/WarehouseListPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER'] },
        },
        {
          path: 'master/warehouses/new',
          name: 'warehouse-new',
          component: () => import('@/pages/master/WarehouseFormPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER'] },
        },
        {
          path: 'master/warehouses/:id',
          name: 'warehouse-edit',
          component: () => import('@/pages/master/WarehouseFormPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER'] },
        },
        // 棟マスタ — SCR-02 (MST-031/032/033): SYSTEM_ADMIN, WAREHOUSE_MANAGER
        {
          path: 'master/buildings',
          name: 'building-list',
          component: () => import('@/pages/master/BuildingListPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER'] },
        },
        {
          path: 'master/buildings/new',
          name: 'building-new',
          component: () => import('@/pages/master/BuildingFormPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER'] },
        },
        {
          path: 'master/buildings/:id/edit',
          name: 'building-edit',
          component: () => import('@/pages/master/BuildingFormPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER'] },
        },
        // エリアマスタ — SCR-02 (MST-041/042/043): SYSTEM_ADMIN, WAREHOUSE_MANAGER
        {
          path: 'master/areas',
          name: 'area-list',
          component: () => import('@/pages/master/AreaListPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER'] },
        },
        {
          path: 'master/areas/new',
          name: 'area-new',
          component: () => import('@/pages/master/AreaFormPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER'] },
        },
        {
          path: 'master/areas/:id/edit',
          name: 'area-edit',
          component: () => import('@/pages/master/AreaFormPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER'] },
        },
        // ロケーションマスタ — SCR-02 (MST-051/052/053): SYSTEM_ADMIN, WAREHOUSE_MANAGER
        {
          path: 'master/locations',
          name: 'location-list',
          component: () => import('@/pages/master/LocationListPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER'] },
        },
        {
          path: 'master/locations/new',
          name: 'location-new',
          component: () => import('@/pages/master/LocationFormPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER'] },
        },
        {
          path: 'master/locations/:id/edit',
          name: 'location-edit',
          component: () => import('@/pages/master/LocationFormPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER'] },
        },
        // ユーザーマスタ — SCR-06 (MST-061/062/063): SYSTEM_ADMIN のみ
        {
          path: 'master/users',
          name: 'user-list',
          component: () => import('@/pages/master/UserListPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN'] },
        },
        {
          path: 'master/users/new',
          name: 'user-new',
          component: () => import('@/pages/master/UserFormPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN'] },
        },
        {
          path: 'master/users/:id/edit',
          name: 'user-edit',
          component: () => import('@/pages/master/UserFormPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN'] },
        },
        // 入荷予定一覧 — INB-001: WAREHOUSE_MANAGER, WAREHOUSE_STAFF, VIEWER
        {
          path: 'inbound/slips',
          name: 'inbound-slip-list',
          component: () => import('@/pages/inbound/InboundSlipListPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'VIEWER'] },
        },
        // 入荷予定登録 — INB-002: WAREHOUSE_MANAGER, WAREHOUSE_STAFF
        {
          path: 'inbound/slips/new',
          name: 'inbound-slip-new',
          component: () => import('@/pages/inbound/InboundSlipNewPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF'] },
        },
        // 入荷予定詳細 — INB-003: WAREHOUSE_MANAGER, WAREHOUSE_STAFF, VIEWER
        {
          path: 'inbound/slips/:id',
          name: 'inbound-slip-detail',
          component: () => import('@/pages/inbound/InboundSlipDetailPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'VIEWER'] },
        },
        // 入荷検品 — INB-004: WAREHOUSE_MANAGER, WAREHOUSE_STAFF
        {
          path: 'inbound/slips/:id/inspect',
          name: 'inbound-slip-inspect',
          component: () => import('@/pages/inbound/InboundSlipInspectPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF'] },
        },
        // 入庫指示・確定 — INB-005: WAREHOUSE_MANAGER, WAREHOUSE_STAFF
        {
          path: 'inbound/slips/:id/store',
          name: 'inbound-slip-store',
          component: () => import('@/pages/inbound/InboundSlipStorePage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF'] },
        },
        // 入荷実績照会 — INB-006: WAREHOUSE_MANAGER, WAREHOUSE_STAFF, VIEWER
        {
          path: 'inbound/results',
          name: 'inbound-results',
          component: () => import('@/pages/inbound/InboundResultsPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'VIEWER'] },
        },
        // 在庫一覧照会 — INV-001: 全ロール
        {
          path: 'inventory',
          name: 'inventory-list',
          component: () => import('@/pages/inventory/InventoryListPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'VIEWER'] },
        },
        // 在庫移動登録 — INV-002: WAREHOUSE_MANAGER, WAREHOUSE_STAFF
        {
          path: 'inventory/move',
          name: 'inventory-move',
          component: () => import('@/pages/inventory/InventoryMovePage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF'] },
        },
        // ばらし登録 — INV-003: WAREHOUSE_MANAGER, WAREHOUSE_STAFF
        {
          path: 'inventory/breakdown',
          name: 'inventory-breakdown',
          component: () => import('@/pages/inventory/InventoryBreakdownPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF'] },
        },
        // 在庫訂正登録 — INV-004: WAREHOUSE_MANAGER
        {
          path: 'inventory/correction',
          name: 'inventory-correction',
          component: () => import('@/pages/inventory/InventoryCorrectionPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER'] },
        },
        // 受注一覧 — OUT-001: 全ロール
        {
          path: 'outbound/slips',
          name: 'outbound-slip-list',
          component: () => import('@/pages/outbound/OutboundSlipListPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'VIEWER'] },
        },
        // 受注登録 — OUT-002: MANAGER, STAFF
        {
          path: 'outbound/slips/new',
          name: 'outbound-slip-new',
          component: () => import('@/pages/outbound/OutboundSlipNewPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF'] },
        },
        // 受注詳細 — OUT-003: 全ロール
        {
          path: 'outbound/slips/:id',
          name: 'outbound-slip-detail',
          component: () => import('@/pages/outbound/OutboundSlipDetailPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'VIEWER'] },
        },
        // 出荷検品 — OUT-021: MANAGER, STAFF
        {
          path: 'outbound/slips/:id/inspect',
          name: 'outbound-slip-inspect',
          component: () => import('@/pages/outbound/OutboundSlipInspectPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF'] },
        },
        // 出荷完了 — OUT-022: MANAGER, STAFF
        {
          path: 'outbound/slips/:id/ship',
          name: 'outbound-slip-ship',
          component: () => import('@/pages/outbound/OutboundSlipShipPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF'] },
        },
        // ピッキング指示一覧 — OUT-011: 全ロール
        {
          path: 'outbound/picking',
          name: 'picking-list',
          component: () => import('@/pages/outbound/PickingListPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'VIEWER'] },
        },
        // ピッキング指示作成 — OUT-012: MANAGER, STAFF
        {
          path: 'outbound/picking/new',
          name: 'picking-new',
          component: () => import('@/pages/outbound/PickingNewPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF'] },
        },
        // ピッキング完了入力 — OUT-013: MANAGER, STAFF
        {
          path: 'outbound/picking/:id',
          name: 'picking-detail',
          component: () => import('@/pages/outbound/PickingDetailPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF'] },
        },
        // システムパラメータ管理 — SCR-12 (SYS-001): SYSTEM_ADMIN のみ
        {
          path: 'system/parameters',
          name: 'system-parameters',
          component: () => import('@/pages/system/SystemParametersPage.vue'),
          meta: { roles: ['SYSTEM_ADMIN'] },
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

  // ロールベースアクセス制御：meta.roles が指定されている場合のみチェック
  const requiredRoles = to.meta.roles
  if (requiredRoles && requiredRoles.length > 0) {
    const userRole = auth.user?.role
    if (!userRole || !requiredRoles.includes(userRole)) {
      return { name: 'forbidden' }
    }
  }

  return true
})

export default router
