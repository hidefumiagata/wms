import 'vue-router'
import type { UserRole } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    /** 認証必須フラグ（デフォルト: 親ルートから継承） */
    requiresAuth?: boolean
    /**
     * アクセス許可ロール。
     * 指定された場合、ユーザーのロールが含まれていなければ /forbidden へリダイレクト。
     * 未指定の場合はロールチェックをスキップ（認証のみで到達可能）。
     */
    roles?: UserRole[]
    /** パンくずリストのi18nキー配列 */
    breadcrumb?: string[]
  }
}
