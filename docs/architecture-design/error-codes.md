# エラーコード定義 — Error Code Registry

> **SSOT**: このファイルは全エラーコード定義の単一情報源（SSOT）です。
> 例外クラス階層・GlobalExceptionHandlerマッピングについては [architecture-blueprint/08-common-infrastructure.md](../architecture-blueprint/08-common-infrastructure.md) を参照してください。

---

## 1. エラーコード体系

**命名規則**: `{RESOURCE}_{ERROR_TYPE}` または `{ERROR_TYPE}`（リソース非依存の場合）

エラーコードはServiceレイヤーの例外スロー時に文字列リテラルで指定します（定数クラスは設けません）。

---

## 2. 共通エラーコード

全モジュールまたは複数モジュールにまたがって使用されるエラーコードです。

| エラーコード | HTTPステータス | 説明 | 使用モジュール |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | 入力バリデーションエラー | 全モジュール |
| `UNAUTHORIZED` | 401 | 認証エラー | 全モジュール |
| `FORBIDDEN` | 403 | 権限不足 | 全モジュール |
| `OPTIMISTIC_LOCK_CONFLICT` | 409 | 楽観的ロック競合 | マスタ管理系 |
| `DUPLICATE_CODE` | 409 | コード重複 | マスタ管理系 |
| `INVALID_SORT_FIELD` | 400 | 不正なソートフィールド | 一覧取得API |
| `INTERNAL_SERVER_ERROR` | 500 | サーバー内部エラー | 全モジュール |

---

## 3. モジュール別エラーコード

### 3.1 認証 (auth)

| エラーコード | HTTPステータス | 説明 |
|---|---|---|
| `INVALID_CREDENTIALS` | 401 | 認証失敗（ユーザー不在またはパスワード不一致） |
| `REFRESH_TOKEN_EXPIRED` | 401 | リフレッシュトークン期限切れ |
| `TOKEN_EXPIRED` | 401 | アクセストークン期限切れ |
| `INVALID_TOKEN` | 401 | トークン不正 |
| `SAME_PASSWORD` | 422 | 新旧パスワード同一 |
| `RATE_LIMIT_EXCEEDED` | 429 | レート制限超過 |
| `ACCOUNT_INACTIVE` | 403 | アカウント無効 |

---

### 3.2 施設管理 (master-facility)

| エラーコード | HTTPステータス | 説明 |
|---|---|---|
| `WAREHOUSE_NOT_FOUND` | 404 | 倉庫が見つからない |
| `BUILDING_NOT_FOUND` | 404 | 棟が見つからない |
| `AREA_NOT_FOUND` | 404 | エリアが見つからない |
| `LOCATION_NOT_FOUND` | 404 | ロケーションが見つからない |
| `CANNOT_DEACTIVATE_HAS_CHILDREN` | 422 | 子リソースが存在するため無効化不可 |
| `CANNOT_DEACTIVATE_HAS_INVENTORY` | 422 | 在庫が存在するため無効化不可 |
| `CANNOT_DEACTIVATE_STOCKTAKE_IN_PROGRESS` | 422 | 棚卸中のため無効化不可 |
| `AREA_LOCATION_LIMIT_EXCEEDED` | 422 | 入荷/出荷/返品エリアのロケーション上限超過 |
| `INVALID_PARAMETER` | 400 | クエリパラメータ不正 |

---

### 3.3 取引先管理 (master-partner)

| エラーコード | HTTPステータス | 説明 |
|---|---|---|
| `PARTNER_NOT_FOUND` | 404 | 取引先が見つからない |
| `CANNOT_DEACTIVATE_HAS_ACTIVE_INBOUND` | 422 | アクティブな入荷があるため無効化不可 |
| `CANNOT_DEACTIVATE_HAS_ACTIVE_OUTBOUND` | 422 | アクティブな出荷があるため無効化不可 |

---

### 3.4 商品管理 (master-product)

| エラーコード | HTTPステータス | 説明 |
|---|---|---|
| `PRODUCT_NOT_FOUND` | 404 | 商品が見つからない |
| `PRODUCT_INACTIVE` | 422 | 商品が無効状態 |
| `CANNOT_CHANGE_LOT_MANAGE_FLAG` | 422 | ロット管理フラグ変更不可 |
| `CANNOT_CHANGE_EXPIRY_MANAGE_FLAG` | 422 | 賞味期限管理フラグ変更不可 |

---

### 3.5 ユーザー管理 (master-user)

| エラーコード | HTTPステータス | 説明 |
|---|---|---|
| `USER_NOT_FOUND` | 404 | ユーザーが見つからない |
| `CANNOT_CHANGE_SELF_ROLE` | 422 | 自身のロール変更不可 |
| `CANNOT_DEACTIVATE_SELF` | 422 | 自身の無効化不可 |

---

### 3.6 入荷管理 (inbound)

| エラーコード | HTTPステータス | 説明 |
|---|---|---|
| `INBOUND_SLIP_NOT_FOUND` | 404 | 入荷伝票が見つからない |
| `INBOUND_LINE_NOT_FOUND` | 404 | 入荷明細が見つからない |
| `INBOUND_INVALID_STATUS` | 409 | 入荷ステータス不正 |
| `INBOUND_PARTNER_NOT_SUPPLIER` | 422 | 取引先が仕入先でない |
| `INBOUND_PARTNER_REQUIRED` | 422 | 仕入先IDが必要 |
| `INBOUND_LINE_ALREADY_STORED` | 422 | 入荷明細は入庫済み |
| `INBOUND_LOCATION_AREA_MISMATCH` | 422 | 入荷エリア外のロケーション |
| `INBOUND_CANCEL` | 409 | 入荷キャンセル |
| `DUPLICATE_LINE_IN_REQUEST` | 422 | リクエスト内に重複行 |
| `DUPLICATE_PRODUCT_IN_LINES` | 422 | 同一商品の重複行 |
| `EXPIRY_DATE_EXPIRED` | 422 | 賞味期限切れ |
| `EXPIRY_DATE_REQUIRED` | 422 | 賞味期限が必要 |
| `LOT_NUMBER_REQUIRED` | 422 | ロット番号が必要 |
| `PLANNED_DATE_TOO_EARLY` | 422 | 予定日が営業日より前 |
| `LOCATION_PRODUCT_MISMATCH` | 422 | ロケーションに異なる商品が存在 |
| `INVENTORY_STOCKTAKE_IN_PROGRESS` | 422 | 棚卸中ロケーション |

---

### 3.7 在庫管理 (inventory)

| エラーコード | HTTPステータス | 説明 |
|---|---|---|
| `INVENTORY_NOT_FOUND` | 404 | 在庫が見つからない |
| `INVENTORY_INSUFFICIENT` | 422 | 在庫不足 |
| `INVENTORY_CAPACITY_EXCEEDED` | 422 | ロケーション容量超過 |
| `INVENTORY_STOCKTAKE_NOT_ALL_COUNTED` | 422 | 棚卸未完了項目あり |
| `STOCKTAKE_NOT_FOUND` | 404 | 棚卸が見つからない |
| `STOCKTAKE_LINE_NOT_FOUND` | 404 | 棚卸明細が見つからない |
| `STOCKTAKE_INVALID_STATUS` | 409 | 棚卸ステータス不正 |
| `CORRECTION_BELOW_ALLOCATED` | 422 | 訂正数量が引当数量未満 |

---

### 3.8 出荷管理 (outbound)

| エラーコード | HTTPステータス | 説明 |
|---|---|---|
| `OUTBOUND_SLIP_NOT_FOUND` | 404 | 出荷伝票が見つからない |
| `OUTBOUND_INVALID_STATUS` | 409 | 出荷ステータス不正 |
| `OUTBOUND_PARTNER_NOT_CUSTOMER` | 422 | 取引先が出荷先でない |
| `OUTBOUND_PARTNER_REQUIRED` | 422 | 出荷先IDが必要 |
| `OUTBOUND_PRODUCT_SHIPMENT_STOPPED` | 422 | 商品が出荷停止 |
| `PICKING_NOT_FOUND` | 404 | ピッキング指示が見つからない |
| `PICKING_LINE_NOT_FOUND` | 422 | 指定されたlineIdが当該ピッキング指示に存在しない |
| `PICKING_QTY_EXCEEDED` | 422 | ピッキング完了数量がピッキング予定数量を超えている |
| `PICKING_NO_ALLOCATION_CANDIDATES` | 422 | ピッキング対象の引当明細が存在しない |
| `UNPACK_NOT_COMPLETED` | 422 | ばらし指示未完了 |
| `ALLOCATION_INSUFFICIENT` | 422 | 引当不足 |

---

### 3.9 帳票 (report)

| エラーコード | HTTPステータス | 説明 |
|---|---|---|
| `REPORT_PARAMETER_REQUIRED` | 422 | 必須パラメータの組み合わせが不足 |

---

### 3.10 バッチ処理 (batch)

| エラーコード | HTTPステータス | 説明 |
|---|---|---|
| `BATCH_ALREADY_RUNNING` | 409 | バッチ実行中 |
| `BATCH_EXECUTION_NOT_FOUND` | 404 | バッチ実行が見つからない |

---

### 3.11 システムパラメータ (system-parameters)

| エラーコード | HTTPステータス | 説明 |
|---|---|---|
| `PARAM_NOT_FOUND` | 404 | パラメータが見つからない |

---

### 3.12 在庫引当 (allocation)

| エラーコード | HTTPステータス | 説明 |
|---|---|---|
| `INVALID_STATUS` | 409 | 引当不可ステータス |
| `ALREADY_COMPLETED` | 422 | ばらし指示完了済み |
| `BREAKDOWN_INSTRUCTION_NOT_FOUND` | 404 | ばらし指示が見つからない |
| `RELEASE_NOT_ALLOWED` | 422 | 引当解除不可（ピッキング指示作成済み） |

---

### 3.13 返品管理 (returns)

| エラーコード | HTTPステータス | 説明 |
|---|---|---|
| `EXCESS_QUANTITY` | 422 | 返品数量超過 |
| `RETURN_ALLOCATED_INVENTORY` | 422 | 引当済み在庫は返品不可 |
| `RETURN_INSUFFICIENT_QUANTITY` | 422 | 返品数量が在庫数量超過 |
| `RETURN_STOCKTAKE_LOCKED` | 422 | 棚卸中ロケーションは返品不可 |

---

## 4. エラーレスポンス形式

エラーレスポンスのJSON形式・例外クラス階層・GlobalExceptionHandlerマッピングの詳細については、[architecture-blueprint/08-common-infrastructure.md](../architecture-blueprint/08-common-infrastructure.md) を参照してください。
