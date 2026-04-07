/**
 * マスタ系の再利用型定義
 */

/**
 * 商品検索ダイアログ等で共有する商品オプション
 */
export interface ProductOption {
  id: number
  productCode: string
  productName: string
  lotManageFlag: boolean
  expiryManageFlag: boolean
}

/**
 * API レスポンスの raw オブジェクト（Record<string, unknown>）から ProductOption に正規化する共通マッパー。
 *
 * 返品画面など、`/master/products` の 1件パス・複数件パス双方で同一型を流すために使う。
 * 不正な値（null/undefined）が来た場合も安全に扱えるよう、boolean 系は `=== true` でフォールバック。
 */
export function toProductOption(p: Record<string, unknown>): ProductOption {
  return {
    id: p.id as number,
    productCode: p.productCode as string,
    productName: p.productName as string,
    lotManageFlag: p.lotManageFlag === true,
    expiryManageFlag: p.expiryManageFlag === true,
  }
}
