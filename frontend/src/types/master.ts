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
