# 機能設計書 — API設計 出荷管理（API-OUT-001〜022）

> **関連ファイル**: [08-api-overview.md](08-api-overview.md)（共通仕様・エラーコード一覧）

---

## 目次

1. [テーブル定義](#テーブル定義)
2. [ステータス遷移](#ステータス遷移)
3. [API-OUT-001: 受注一覧取得](#api-out-001-受注一覧取得)
4. [API-OUT-002: 受注登録](#api-out-002-受注登録)
5. [API-OUT-003: 受注詳細取得](#api-out-003-受注詳細取得)
6. [API-OUT-004: 受注削除](#api-out-004-受注削除)
7. [補助API: 個別在庫引当](#補助api-個別在庫引当)
8. [補助API: 一括在庫引当](#補助api-一括在庫引当)
9. [補助API: 受注キャンセル](#補助api-受注キャンセル)
10. [API-OUT-011: ピッキング指示一覧取得](#api-out-011-ピッキング指示一覧取得)
11. [API-OUT-012: ピッキング指示作成](#api-out-012-ピッキング指示作成)
12. [API-OUT-013: ピッキング指示詳細取得](#api-out-013-ピッキング指示詳細取得)
13. [API-OUT-014: ピッキング完了登録](#api-out-014-ピッキング完了登録)
14. [API-OUT-021: 出荷検品登録](#api-out-021-出荷検品登録)
15. [API-OUT-022: 出荷完了登録](#api-out-022-出荷完了登録)
16. [エラーコード一覧（出荷管理）](#エラーコード一覧出荷管理)

---

## テーブル定義

### outbound_slips（出荷ヘッダ／受注）

| カラム名 | 型 | NULL | 説明 |
|---------|-----|:----:|------|
| `id` | BIGSERIAL | ✗ | サロゲートキー（PK） |
| `slip_number` | varchar(50) | ✗ | 伝票番号（一意・自動採番、例: `OUT-20260313-001`） |
| `slip_type` | VARCHAR(20) | ✗ | 伝票種別: `NORMAL`（通常出荷） / `WAREHOUSE_TRANSFER`（倉庫間振替） |
| `transfer_slip_number` | varchar(50) | ○ | 振替伝票番号（`WAREHOUSE_TRANSFER` 時にセット） |
| `warehouse_id` | BIGINT | ✗ | 出荷元倉庫ID（FK: warehouses） |
| `warehouse_code` | VARCHAR(20) | ✗ | 倉庫コード（登録時コピー） |
| `warehouse_name` | VARCHAR(100) | ✗ | 倉庫名（登録時コピー） |
| `partner_id` | BIGINT | ○ | 出荷先取引先ID（FK: partners）※ `NORMAL` 時必須 |
| `partner_code` | VARCHAR(20) | ○ | 取引先コード（登録時コピー） |
| `partner_name` | VARCHAR(100) | ○ | 取引先名（登録時コピー） |
| `planned_date` | DATE | ✗ | 出荷予定日 |
| `carrier` | VARCHAR(100) | ○ | 配送業者名 |
| `tracking_number` | VARCHAR(100) | ○ | 送り状番号 |
| `status` | VARCHAR(30) | ✗ | ステータス（下記参照） |
| `note` | TEXT | ○ | 備考 |
| `shipped_at` | TIMESTAMPTZ | ○ | 出荷完了日時 |
| `shipped_by` | BIGINT | ○ | 出荷完了実施者ID（FK: users） |
| `cancelled_at` | TIMESTAMPTZ | ○ | キャンセル日時 |
| `cancelled_by` | BIGINT | ○ | キャンセル実施者ID（FK: users） |
| `created_at` | TIMESTAMPTZ | ✗ | 作成日時 |
| `updated_at` | TIMESTAMPTZ | ✗ | 更新日時 |
| `created_by` | BIGINT | ✗ | 作成者ID（FK: users） |
| `updated_by` | BIGINT | ✗ | 更新者ID（FK: users） |

**status 値**

| 値 | 意味 |
|----|------|
| `ORDERED` | 受注済み（初期ステータス） |
| `PICKING_INSTRUCTED` | ピッキング指示済み（在庫引当完了） |
| `PICKING_COMPLETED` | ピッキング完了 |
| `INSPECTING` | 出荷検品中 |
| `SHIPPED` | 出荷完了 |
| `CANCELLED` | キャンセル済み |

---

### outbound_slip_lines（出荷明細）

| カラム名 | 型 | NULL | 説明 |
|---------|-----|:----:|------|
| `id` | BIGSERIAL | ✗ | サロゲートキー（PK） |
| `outbound_slip_id` | BIGINT | ✗ | FK: outbound_slips |
| `line_no` | INTEGER | ✗ | 明細行番号（1始まり） |
| `product_id` | BIGINT | ✗ | 商品ID（FK: products） |
| `product_code` | VARCHAR(30) | ✗ | 商品コード（登録時コピー） |
| `product_name` | VARCHAR(200) | ✗ | 商品名（登録時コピー） |
| `unit_type` | VARCHAR(10) | ✗ | 数量単位: `CASE` / `BALL` / `PIECE` |
| `ordered_qty` | INTEGER | ✗ | 受注数量 |
| `shipped_qty` | INTEGER | ○ | 出荷完了数量（出荷完了時にセット） |
| `line_status` | VARCHAR(30) | ✗ | 明細ステータス（下記参照） |

**line_status 値**

| 値 | 意味 |
|----|------|
| `ORDERED` | 受注済み |
| `PICKING_INSTRUCTED` | ピッキング指示済み（在庫引当済み） |
| `PICKING_COMPLETED` | ピッキング完了 |
| `SHIPPED` | 出荷完了 |

---

### picking_instructions（ピッキング指示ヘッダ）

| カラム名 | 型 | NULL | 説明 |
|---------|-----|:----:|------|
| `id` | BIGSERIAL | ✗ | サロゲートキー（PK） |
| `instruction_number` | VARCHAR(30) | ✗ | 指示番号（一意・自動採番、例: `PIC-20260313-001`） |
| `warehouse_id` | BIGINT | ✗ | 倉庫ID（FK: warehouses） |
| `area_id` | BIGINT | ○ | 対象エリアID（FK: areas、絞り込み省略時はNULL） |
| `status` | VARCHAR(20) | ✗ | ステータス: `CREATED` / `IN_PROGRESS` / `COMPLETED` |
| `created_at` | TIMESTAMPTZ | ✗ | 作成日時 |
| `created_by` | BIGINT | ✗ | 作成者ID（FK: users） |
| `completed_at` | TIMESTAMPTZ | ○ | 完了日時 |
| `completed_by` | BIGINT | ○ | 完了者ID（FK: users） |

---

### picking_instruction_lines（ピッキング指示明細）

| カラム名 | 型 | NULL | 説明 |
|---------|-----|:----:|------|
| `id` | BIGSERIAL | ✗ | サロゲートキー（PK） |
| `picking_instruction_id` | BIGINT | ✗ | FK: picking_instructions |
| `line_no` | INTEGER | ✗ | 明細行番号（1始まり） |
| `outbound_slip_line_id` | BIGINT | ✗ | FK: outbound_slip_lines（引当対象の出荷明細） |
| `location_id` | BIGINT | ✗ | ピッキング元ロケーションID（FK: locations） |
| `location_code` | VARCHAR(30) | ✗ | ロケーションコード（在庫引当時コピー） |
| `product_id` | BIGINT | ✗ | 商品ID（FK: products） |
| `product_code` | VARCHAR(30) | ✗ | 商品コード（在庫引当時コピー） |
| `product_name` | VARCHAR(200) | ✗ | 商品名（在庫引当時コピー） |
| `unit_type` | VARCHAR(10) | ✗ | 数量単位: `CASE` / `BALL` / `PIECE` |
| `lot_number` | VARCHAR(50) | ○ | ロット番号（在庫から引当時にセット） |
| `expiry_date` | DATE | ○ | 賞味期限（在庫から引当時にセット） |
| `qty_to_pick` | INTEGER | ✗ | ピッキング指示数量 |
| `qty_picked` | INTEGER | ○ | ピッキング完了数量 |
| `line_status` | VARCHAR(20) | ✗ | 明細ステータス: `PENDING` / `COMPLETED` |

---

## ステータス遷移

### 受注ステータス遷移

```mermaid
stateDiagram-v2
    [*] --> ORDERED : 受注登録（API-OUT-002）
    ORDERED --> PICKING_INSTRUCTED : 在庫引当（allot）
    PICKING_INSTRUCTED --> PICKING_COMPLETED : ピッキング完了（API-OUT-014）
    PICKING_COMPLETED --> INSPECTING : 出荷検品開始（API-OUT-021）
    INSPECTING --> SHIPPED : 出荷完了（API-OUT-022）
    ORDERED --> CANCELLED : キャンセル（cancel）
    PICKING_INSTRUCTED --> CANCELLED : キャンセル（cancel）
    SHIPPED --> [*]
    CANCELLED --> [*]
```

| 遷移前 | 操作 | 遷移後 | トリガーAPI |
|--------|------|--------|------------|
| `ORDERED` | 在庫引当 | `PICKING_INSTRUCTED` | `POST /allot` |
| `PICKING_INSTRUCTED` | ピッキング完了登録 | `PICKING_COMPLETED` | `API-OUT-014` |
| `PICKING_COMPLETED` | 出荷検品登録 | `INSPECTING` | `API-OUT-021` |
| `INSPECTING` | 出荷完了登録 | `SHIPPED` | `API-OUT-022` |
| `ORDERED` | キャンセル | `CANCELLED` | `POST /cancel` |
| `PICKING_INSTRUCTED` | キャンセル | `CANCELLED` | `POST /cancel` |

### ピッキング指示ステータス遷移

```mermaid
stateDiagram-v2
    [*] --> CREATED : ピッキング指示作成（API-OUT-012）
    CREATED --> IN_PROGRESS : 最初の明細チェック（API-OUT-014）
    IN_PROGRESS --> COMPLETED : 全明細チェック完了（API-OUT-014）
    COMPLETED --> [*]
```

---

## API-OUT-001: 受注一覧取得

### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-OUT-001` |
| **API名** | 受注一覧取得 |
| **メソッド** | `GET` |
| **パス** | `/api/v1/outbound/slips` |
| **認証** | 要 |
| **対象ロール** | 全ロール |
| **概要** | 出荷伝票（受注）の一覧をページング形式で取得する。倉庫ID必須。各種条件で絞り込み可能。 |
| **関連画面** | `OUT-001`（受注一覧） |

---

### 2. リクエスト仕様

#### クエリパラメータ

| パラメータ名 | 型 | 必須 | デフォルト | 説明 |
|------------|-----|:----:|----------|------|
| `warehouseId` | Long | ○ | — | 倉庫ID（必須） |
| `slipNumber` | String | — | — | 伝票番号（前方一致） |
| `plannedDateFrom` | String | — | — | 出荷予定日の開始日（`yyyy-MM-dd`） |
| `plannedDateTo` | String | — | — | 出荷予定日の終了日（`yyyy-MM-dd`） |
| `partnerId` | Long | — | — | 出荷先取引先ID |
| `status` | String[] | — | — | ステータス（複数指定可）。例: `status=ORDERED&status=PICKING_INSTRUCTED` |
| `page` | Integer | — | `0` | ページ番号（0始まり） |
| `size` | Integer | — | `20` | 1ページあたりの件数（上限: 100） |
| `sort` | String | — | `plannedDate,asc` | ソート指定（例: `plannedDate,desc`） |

---

### 3. レスポンス仕様

#### 成功レスポンス（200 OK）

```json
{
  "content": [
    {
      "id": 1,
      "slipNumber": "OUT-20260313-001",
      "slipType": "NORMAL",
      "partnerName": "株式会社テスト商事",
      "plannedDate": "2026-03-20",
      "status": "ORDERED",
      "lineCount": 3,
      "createdAt": "2026-03-13T09:00:00+09:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

**content 要素のフィールド**

| フィールド名 | 型 | 説明 |
|------------|-----|------|
| `id` | Long | 出荷伝票ID |
| `slipNumber` | String | 伝票番号 |
| `slipType` | String | 伝票種別（`NORMAL` / `WAREHOUSE_TRANSFER`） |
| `partnerName` | String | 出荷先取引先名（振替の場合はnull） |
| `plannedDate` | String | 出荷予定日（`yyyy-MM-dd`） |
| `status` | String | 受注ステータス |
| `lineCount` | Integer | 明細件数 |
| `createdAt` | String | 作成日時（ISO 8601） |

#### エラーレスポンス

| HTTPステータス | エラーコード | 説明 |
|-------------|------------|------|
| `400` | `VALIDATION_ERROR` | `warehouseId` が未指定・不正 |
| `401` | `UNAUTHORIZED` | 未認証 |
| `404` | `WAREHOUSE_NOT_FOUND` | 指定倉庫が存在しない |

---

### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[warehouseId 必須チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| CHECK_WH[倉庫存在チェック]
    CHECK_WH -->|存在しない| ERR_WH[404 WAREHOUSE_NOT_FOUND]
    CHECK_WH -->|OK| QUERY[outbound_slips を条件検索\nページング・ソート適用]
    QUERY --> COUNT[LINE COUNT を集計\nSELECT COUNT FROM outbound_slip_lines]
    COUNT --> RESP[200 OK + ページングレスポンス]
    RESP --> END([終了])
```

**ビジネスルール**

| # | ルール |
|---|--------|
| 1 | `warehouseId` は必須パラメータ。未指定の場合 400 を返す |
| 2 | `plannedDateFrom` と `plannedDateTo` はどちらか一方のみの指定も可（片方省略時は上限・下限なし） |
| 3 | `status` は複数指定可能（IN句で絞り込み） |
| 4 | デフォルトソートは `planned_date ASC, slip_number ASC` |

---

### 5. 補足事項

- `lineCount` はサブクエリまたはJOINで集計する。N+1問題を避けること。

---

---

## API-OUT-002: 受注登録

### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-OUT-002` |
| **API名** | 受注登録 |
| **メソッド** | `POST` |
| **パス** | `/api/v1/outbound/slips` |
| **認証** | 要 |
| **対象ロール** | `SYSTEM_ADMIN`, `WAREHOUSE_MANAGER`, `WAREHOUSE_STAFF` |
| **概要** | 新規の出荷受注（受注伝票）を登録する。登録時のステータスは `ORDERED` 固定。伝票番号は自動採番される。 |
| **関連画面** | `OUT-002`（受注登録） |

---

### 2. リクエスト仕様

#### リクエストボディ

```json
{
  "warehouseId": 1,
  "slipType": "NORMAL",
  "partnerId": 10,
  "plannedDate": "2026-03-20",
  "note": "緊急注文",
  "lines": [
    {
      "productId": 101,
      "unitType": "CASE",
      "orderedQty": 5
    },
    {
      "productId": 102,
      "unitType": "PIECE",
      "orderedQty": 20
    }
  ]
}
```

| フィールド名 | 型 | 必須 | バリデーション | 説明 |
|------------|-----|:----:|-------------|------|
| `warehouseId` | Long | ○ | 存在する倉庫ID | 出荷元倉庫 |
| `slipType` | String | ○ | `NORMAL` / `WAREHOUSE_TRANSFER` | 伝票種別 |
| `partnerId` | Long | △ | `NORMAL` 時は必須 | 出荷先取引先ID |
| `plannedDate` | String | ○ | `yyyy-MM-dd`、現在営業日以降 | 出荷予定日 |
| `note` | String | — | 最大500文字 | 備考 |
| `lines` | Array | ○ | 1件以上 | 出荷明細 |
| `lines[].productId` | Long | ○ | 存在する商品ID | 商品 |
| `lines[].unitType` | String | ○ | `CASE` / `BALL` / `PIECE` | 数量単位 |
| `lines[].orderedQty` | Integer | ○ | 1以上999,999以下の整数 | 受注数量 |

---

### 3. レスポンス仕様

#### 成功レスポンス（201 Created）

```json
{
  "id": 1,
  "slipNumber": "OUT-20260313-001",
  "slipType": "NORMAL",
  "warehouseId": 1,
  "warehouseCode": "WH-001",
  "warehouseName": "東京DC",
  "partnerId": 10,
  "partnerCode": "C-001",
  "partnerName": "株式会社テスト商事",
  "plannedDate": "2026-03-20",
  "status": "ORDERED",
  "note": "緊急注文",
  "lines": [
    {
      "id": 1,
      "lineNo": 1,
      "productId": 101,
      "productCode": "P-001",
      "productName": "テスト商品A",
      "unitType": "CASE",
      "orderedQty": 5,
      "shippedQty": null,
      "lineStatus": "ORDERED"
    }
  ],
  "createdAt": "2026-03-13T09:00:00+09:00",
  "updatedAt": "2026-03-13T09:00:00+09:00",
  "createdBy": 1,
  "updatedBy": 1
}
```

#### エラーレスポンス

| HTTPステータス | エラーコード | 説明 |
|-------------|------------|------|
| `400` | `VALIDATION_ERROR` | 入力値バリデーションエラー |
| `404` | `WAREHOUSE_NOT_FOUND` | 倉庫が存在しない |
| `404` | `PARTNER_NOT_FOUND` | 取引先が存在しない |
| `404` | `PRODUCT_NOT_FOUND` | 商品が存在しない |
| `422` | `OUTBOUND_PARTNER_NOT_CUSTOMER` | 取引先種別が出荷先（`CUSTOMER` / `BOTH`）でない |
| `422` | `OUTBOUND_PRODUCT_SHIPMENT_STOPPED` | 出荷禁止フラグが立っている商品を選択した |

---

### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[入力バリデーション\nwarehouseId必須, plannedDate形式, lines1件以上\norderedQty範囲チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| CHECK_WH[倉庫存在チェック]
    CHECK_WH -->|存在しない| ERR_WH[404 WAREHOUSE_NOT_FOUND]
    CHECK_WH -->|OK| CHECK_TYPE{slipType?}
    CHECK_TYPE -->|NORMAL| CHECK_PARTNER[取引先存在チェック\npartnerIdが必須]
    CHECK_TYPE -->|WAREHOUSE_TRANSFER| CHECK_DATE[plannedDate 営業日チェック]
    CHECK_PARTNER -->|存在しない| ERR_PAR[404 PARTNER_NOT_FOUND]
    CHECK_PARTNER -->|OK| CHECK_PARTNER_TYPE[partner_type チェック\nCUSTOMER or BOTH]
    CHECK_PARTNER_TYPE -->|NG| ERR_PAR_TYPE[422 OUTBOUND_PARTNER_NOT_CUSTOMER]
    CHECK_PARTNER_TYPE -->|OK| CHECK_DATE
    CHECK_DATE -->|営業日前| ERR_DATE[400 VALIDATION_ERROR\nplannedDate]
    CHECK_DATE -->|OK| CHECK_PRODUCTS[各明細の商品存在チェック\n重複チェック]
    CHECK_PRODUCTS -->|存在しない| ERR_PRD[404 PRODUCT_NOT_FOUND]
    CHECK_PRODUCTS -->|重複あり| ERR_DUP[400 VALIDATION_ERROR\n同一商品重複不可]
    CHECK_PRODUCTS -->|OK| CHECK_SHIPMENT_STOP[shipment_stop_flag チェック]
    CHECK_SHIPMENT_STOP -->|trueあり| ERR_STOP[422 OUTBOUND_PRODUCT_SHIPMENT_STOPPED]
    CHECK_SHIPMENT_STOP -->|OK| GEN_SLIP_NUM[伝票番号自動採番\nOUT-yyyyMMdd-連番]
    GEN_SLIP_NUM --> INSERT_SLIP[outbound_slips INSERT\nstatus=ORDERED\nwarehouse/partner/product情報をコピー]
    INSERT_SLIP --> INSERT_LINES[outbound_slip_lines INSERT\nline_status=ORDERED]
    INSERT_LINES --> RESP[201 Created + 伝票オブジェクト（明細含む）]
    RESP --> END([終了])
```

**ビジネスルール**

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `slipType=NORMAL` の場合 `partnerId` は必須 | `VALIDATION_ERROR` |
| 2 | `partnerId` が指定された場合、`partner_type` が `CUSTOMER` または `BOTH` でなければならない | `OUTBOUND_PARTNER_NOT_CUSTOMER` |
| 3 | `shipment_stop_flag=true` の商品は明細に含められない | `OUTBOUND_PRODUCT_SHIPMENT_STOPPED` |
| 4 | `plannedDate` は現在営業日（`system_business_date` テーブル参照）以降でなければならない | `VALIDATION_ERROR` |
| 5 | `lines` に同一 `productId` の重複は不可 | `VALIDATION_ERROR` |
| 6 | 伝票番号は `OUT-yyyyMMdd-NNN`（3桁連番）形式で自動採番。採番はシーケンスまたはROWロックで重複排除する | — |
| 7 | `warehouse_code`, `warehouse_name`, `partner_code`, `partner_name`, `product_code`, `product_name` は登録時点の値をコピーして保持する（マスタ変更の影響を受けない） | — |

---

### 5. 補足事項

- 伝票番号採番とINSERTはひとつのトランザクションで行い、採番の重複を防ぐ。
- `plannedDate` の営業日チェックは `GET /api/v1/system/business-date` と同じ日付参照テーブルを使用する。

---

---

## API-OUT-003: 受注詳細取得

### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-OUT-003` |
| **API名** | 受注詳細取得 |
| **メソッド** | `GET` |
| **パス** | `/api/v1/outbound/slips/{id}` |
| **認証** | 要 |
| **対象ロール** | 全ロール |
| **概要** | 出荷伝票IDを指定して受注ヘッダ・全明細を取得する。 |
| **関連画面** | `OUT-003`（受注詳細） |

---

### 2. リクエスト仕様

#### パスパラメータ

| パラメータ名 | 型 | 必須 | 説明 |
|------------|-----|:----:|------|
| `id` | Long | ○ | 出荷伝票ID |

---

### 3. レスポンス仕様

#### 成功レスポンス（200 OK）

```json
{
  "id": 1,
  "slipNumber": "OUT-20260313-001",
  "slipType": "NORMAL",
  "transferSlipNumber": null,
  "warehouseId": 1,
  "warehouseCode": "WH-001",
  "warehouseName": "東京DC",
  "partnerId": 10,
  "partnerCode": "C-001",
  "partnerName": "株式会社テスト商事",
  "plannedDate": "2026-03-20",
  "carrier": null,
  "trackingNumber": null,
  "status": "ORDERED",
  "note": "緊急注文",
  "shippedAt": null,
  "shippedBy": null,
  "cancelledAt": null,
  "cancelledBy": null,
  "lines": [
    {
      "id": 1,
      "lineNo": 1,
      "productId": 101,
      "productCode": "P-001",
      "productName": "テスト商品A",
      "unitType": "CASE",
      "orderedQty": 5,
      "shippedQty": null,
      "lineStatus": "ORDERED"
    }
  ],
  "createdAt": "2026-03-13T09:00:00+09:00",
  "updatedAt": "2026-03-13T09:00:00+09:00",
  "createdBy": 1,
  "updatedBy": 1
}
```

#### エラーレスポンス

| HTTPステータス | エラーコード | 説明 |
|-------------|------------|------|
| `404` | `OUTBOUND_SLIP_NOT_FOUND` | 指定IDの出荷伝票が存在しない |

---

### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> FIND[outbound_slips を id で検索]
    FIND -->|存在しない| ERR[404 OUTBOUND_SLIP_NOT_FOUND]
    FIND -->|OK| FETCH_LINES[outbound_slip_lines を取得\nline_no 昇順]
    FETCH_LINES --> RESP[200 OK + 詳細オブジェクト]
    RESP --> END([終了])
```

---

### 5. 補足事項

- 明細は `line_no ASC` でソートして返す。

---

---

## API-OUT-004: 受注削除

### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-OUT-004` |
| **API名** | 受注削除 |
| **メソッド** | `DELETE` |
| **パス** | `/api/v1/outbound/slips/{id}` |
| **認証** | 要 |
| **対象ロール** | `SYSTEM_ADMIN`, `WAREHOUSE_MANAGER`, `WAREHOUSE_STAFF` |
| **概要** | `ORDERED` 状態の受注伝票を物理削除する。在庫引当済み（`PICKING_INSTRUCTED` 以降）は削除不可。 |
| **関連画面** | `OUT-003`（受注詳細・削除ボタン） |

---

### 2. リクエスト仕様

#### パスパラメータ

| パラメータ名 | 型 | 必須 | 説明 |
|------------|-----|:----:|------|
| `id` | Long | ○ | 出荷伝票ID |

---

### 3. レスポンス仕様

#### 成功レスポンス

| HTTPステータス | 説明 |
|-------------|------|
| `204 No Content` | 削除成功（レスポンスボディなし） |

#### エラーレスポンス

| HTTPステータス | エラーコード | 説明 |
|-------------|------------|------|
| `404` | `OUTBOUND_SLIP_NOT_FOUND` | 指定IDの出荷伝票が存在しない |
| `409` | `OUTBOUND_INVALID_STATUS` | `ORDERED` 以外のステータスは削除不可 |

---

### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> FIND[outbound_slips を id で検索\nSELECT FOR UPDATE]
    FIND -->|存在しない| ERR_NF[404 OUTBOUND_SLIP_NOT_FOUND]
    FIND -->|OK| CHECK_STATUS{status == ORDERED?}
    CHECK_STATUS -->|No| ERR_STATUS[409 OUTBOUND_INVALID_STATUS]
    CHECK_STATUS -->|Yes| DELETE_LINES[outbound_slip_lines DELETE]
    DELETE_LINES --> DELETE_SLIP[outbound_slips DELETE]
    DELETE_SLIP --> RESP[204 No Content]
    RESP --> END([終了])
```

**ビジネスルール**

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | 削除可能なステータスは `ORDERED` のみ。`PICKING_INSTRUCTED` 以降はキャンセルAPIを使用すること | `OUTBOUND_INVALID_STATUS` |
| 2 | 削除は物理削除（`DELETE FROM`）。明細（`outbound_slip_lines`）も連鎖削除する | — |

---

---

## 補助API: 個別在庫引当

### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | —（補助API） |
| **API名** | 個別在庫引当 |
| **メソッド** | `POST` |
| **パス** | `/api/v1/outbound/slips/{id}/allot` |
| **認証** | 要 |
| **対象ロール** | `SYSTEM_ADMIN`, `WAREHOUSE_MANAGER` |
| **概要** | 指定した出荷伝票（`ORDERED` 状態）に対して在庫引当を行い、ピッキング指示明細を生成する。在庫はFIFO（または期限順）で引当てる。 |
| **関連画面** | `OUT-003`（受注詳細・引当ボタン） |

---

### 2. リクエスト仕様

#### パスパラメータ

| パラメータ名 | 型 | 必須 | 説明 |
|------------|-----|:----:|------|
| `id` | Long | ○ | 出荷伝票ID |

リクエストボディなし。

---

### 3. レスポンス仕様

#### 成功レスポンス（200 OK）

```json
{
  "id": 1,
  "slipNumber": "OUT-20260313-001",
  "status": "PICKING_INSTRUCTED",
  "lines": [
    {
      "id": 1,
      "lineNo": 1,
      "productCode": "P-001",
      "productName": "テスト商品A",
      "unitType": "CASE",
      "orderedQty": 5,
      "lineStatus": "PICKING_INSTRUCTED"
    }
  ]
}
```

#### エラーレスポンス

| HTTPステータス | エラーコード | 説明 |
|-------------|------------|------|
| `404` | `OUTBOUND_SLIP_NOT_FOUND` | 出荷伝票が存在しない |
| `409` | `OUTBOUND_INVALID_STATUS` | `ORDERED` 以外のステータスには引当不可 |
| `422` | `ALLOCATION_INSUFFICIENT` | 在庫が不足していて引当できない |

---

### 4. 業務ロジック

在庫引当は排他制御が必要な核心処理である。

```mermaid
flowchart TD
    START([開始]) --> FIND[outbound_slips を id で検索\nSELECT FOR UPDATE]
    FIND -->|存在しない| ERR_NF[404 OUTBOUND_SLIP_NOT_FOUND]
    FIND -->|OK| CHECK_STATUS{status == ORDERED?}
    CHECK_STATUS -->|No| ERR_STATUS[409 OUTBOUND_INVALID_STATUS]
    CHECK_STATUS -->|Yes| LOOP_LINE[各出荷明細をループ処理]

    LOOP_LINE --> LOCK_INV["inventories を\nproduct_id, warehouse_id で\nSELECT FOR UPDATE\n（悲観的ロック）"]
    LOCK_INV --> CHECK_STOCK{有効在庫数 >= 必要数?}
    CHECK_STOCK -->|No| ROLLBACK[トランザクションROLLBACK]
    ROLLBACK --> ERR_ALLOC[422 ALLOCATION_INSUFFICIENT]
    CHECK_STOCK -->|Yes| FIFO["在庫レコードをFIFO順に選択\n（入荷日 or 賞味期限 昇順）\nロット・ロケーション単位で引当"]
    FIFO --> CREATE_PIL[picking_instruction_lines INSERT\nlocation_code, lot_number,\nexpiry_date をセット\nqty_to_pick をセット]
    CREATE_PIL --> UPDATE_LINE_STATUS[outbound_slip_lines.line_status\n= PICKING_INSTRUCTED]
    UPDATE_LINE_STATUS -->|次の明細へ| LOOP_LINE
    UPDATE_LINE_STATUS -->|全明細完了| UPDATE_SLIP[outbound_slips.status\n= PICKING_INSTRUCTED]
    UPDATE_SLIP --> RESP[200 OK + 更新後の伝票オブジェクト]
    RESP --> COMMIT[COMMIT]
    COMMIT --> END([終了])
```

**排他制御（悲観的ロック）の詳細**

在庫引当は複数のユーザーが同時実行した場合に在庫の二重引当が発生するリスクがある。これを防ぐために以下の排他制御を実施する。

| ステップ | SQL | 説明 |
|---------|-----|------|
| 1 | `SELECT * FROM outbound_slips WHERE id=? FOR UPDATE` | 出荷伝票のロック取得。同一伝票への並行引当を防止 |
| 2 | `SELECT * FROM inventories WHERE product_id=? AND warehouse_id=? AND available_qty > 0 ORDER BY received_date ASC, expiry_date ASC FOR UPDATE` | 在庫レコードのロック取得。FIFO順に並べて先頭から順に引当 |
| 3 | 引当ロット・ロケーション・数量を `picking_instruction_lines` に記録 | 在庫減算はこの時点では行わない（出荷完了時に行う） |
| 4 | `outbound_slip_lines.line_status = 'PICKING_INSTRUCTED'` に更新 | — |
| 5 | 全明細完了後に `outbound_slips.status = 'PICKING_INSTRUCTED'` に更新 | — |

**重要**: 在庫からの実際の減算（`inventories.qty` の更新）はこのタイミングでは行わない。在庫減算は出荷完了時（`API-OUT-022`）に実施する。ただし引当済み在庫として `reserved_qty` 等で管理する場合は設計を拡張すること（本設計ではシンプル化のため省略）。

**ビジネスルール**

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `ORDERED` 状態の伝票のみ引当可能 | `OUTBOUND_INVALID_STATUS` |
| 2 | 在庫はFIFO（入荷日昇順）または賞味期限昇順で引当てる | — |
| 3 | 1明細でも在庫不足の場合はトランザクション全体をロールバックし 422 を返す | `ALLOCATION_INSUFFICIENT` |
| 4 | 引当処理中は `inventories` を `SELECT FOR UPDATE` でロックし、並行処理による二重引当を防止する | — |
| 5 | 1明細の必要数量が複数ロケーション／ロットにまたがる場合、複数の `picking_instruction_lines` レコードを生成する | — |

---

### 5. 補足事項

- 引当処理はトランザクション分離レベル `READ COMMITTED` で実行する（PostgreSQLデフォルト）。`SELECT FOR UPDATE` により行レベルロックを使用する。
- デッドロック回避のため、ロック取得順序は常に `outbound_slips → inventories（product_id昇順）` の順序とすること。

---

---

## 補助API: 一括在庫引当

### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | —（補助API） |
| **API名** | 一括在庫引当 |
| **メソッド** | `POST` |
| **パス** | `/api/v1/outbound/slips/bulk-allot` |
| **認証** | 要 |
| **対象ロール** | `SYSTEM_ADMIN`, `WAREHOUSE_MANAGER` |
| **概要** | 複数の出荷伝票に対して一括で在庫引当を実行する。1件の引当失敗があっても他の伝票の処理は続行する（部分成功）。 |
| **関連画面** | `OUT-001`（受注一覧・一括引当ボタン） |

---

### 2. リクエスト仕様

#### リクエストボディ

```json
{
  "slipIds": [1, 2, 3]
}
```

| フィールド名 | 型 | 必須 | バリデーション | 説明 |
|------------|-----|:----:|-------------|------|
| `slipIds` | Long[] | ○ | 1件以上、重複不可 | 引当対象の出荷伝票IDリスト |

---

### 3. レスポンス仕様

#### 成功レスポンス（200 OK）

全件失敗の場合も 200 を返し、`failed` 配列にエラー詳細を格納する。

```json
{
  "succeeded": [1, 2],
  "failed": [
    {
      "slipId": 3,
      "slipNumber": "OUT-20260313-003",
      "errorCode": "ALLOCATION_INSUFFICIENT",
      "message": "商品 P-003 の在庫が不足しています"
    }
  ]
}
```

| フィールド名 | 型 | 説明 |
|------------|-----|------|
| `succeeded` | Long[] | 引当成功した出荷伝票IDリスト |
| `failed` | Array | 引当失敗した伝票の情報リスト |
| `failed[].slipId` | Long | 失敗した出荷伝票ID |
| `failed[].slipNumber` | String | 失敗した出荷伝票の伝票番号 |
| `failed[].errorCode` | String | エラーコード |
| `failed[].message` | String | エラーメッセージ（日本語） |

#### エラーレスポンス

| HTTPステータス | エラーコード | 説明 |
|-------------|------------|------|
| `400` | `VALIDATION_ERROR` | `slipIds` が未指定または空 |

---

### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[slipIds バリデーション\n1件以上・重複なし]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| INIT[succeeded=[], failed=[] 初期化]
    INIT --> LOOP[slipIds をループ処理]
    LOOP --> TRY[個別引当処理を実行\nSEAVEPOINT単位でトランザクション制御]
    TRY -->|成功| ADD_SUCCESS[succeeded にIDを追加]
    TRY -->|失敗| ADD_FAIL[failed にエラー情報を追加\n当該伝票のトランザクションのみROLLBACK]
    ADD_SUCCESS -->|次の伝票へ| LOOP
    ADD_FAIL -->|次の伝票へ| LOOP
    LOOP -->|全件処理完了| RESP[200 OK + 結果サマリー]
    RESP --> END([終了])
```

**ビジネスルール**

| # | ルール |
|---|--------|
| 1 | 各伝票の引当処理はSAVEPOINT（部分ロールバック）を使って独立させる。1件の失敗が他の伝票に影響しない |
| 2 | `ORDERED` 以外のステータスの伝票は `failed` に `OUTBOUND_INVALID_STATUS` として記録する |
| 3 | 存在しないIDは `failed` に `OUTBOUND_SLIP_NOT_FOUND` として記録する |
| 4 | レスポンスは全件処理後に返す |

---

### 5. 補足事項

- PostgreSQLの `SAVEPOINT` を利用することで、アプリケーション層で部分ロールバックを実現する。
- Spring の `@Transactional(propagation = REQUIRES_NEW)` を各伝票の引当処理に適用することも可能。

---

---

## 補助API: 受注キャンセル

### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | —（補助API） |
| **API名** | 受注キャンセル |
| **メソッド** | `POST` |
| **パス** | `/api/v1/outbound/slips/{id}/cancel` |
| **認証** | 要 |
| **対象ロール** | `SYSTEM_ADMIN`, `WAREHOUSE_MANAGER` |
| **概要** | `ORDERED` または `PICKING_INSTRUCTED` 状態の受注をキャンセルする。引当済みの場合は引当情報も取り消す。 |
| **関連画面** | `OUT-003`（受注詳細・キャンセルボタン） |

---

### 2. リクエスト仕様

#### パスパラメータ

| パラメータ名 | 型 | 必須 | 説明 |
|------------|-----|:----:|------|
| `id` | Long | ○ | 出荷伝票ID |

#### リクエストボディ

```json
{
  "reason": "顧客都合によるキャンセル"
}
```

| フィールド名 | 型 | 必須 | バリデーション | 説明 |
|------------|-----|:----:|-------------|------|
| `reason` | String | — | 最大500文字 | キャンセル理由（任意） |

---

### 3. レスポンス仕様

#### 成功レスポンス（200 OK）

```json
{
  "id": 1,
  "slipNumber": "OUT-20260313-001",
  "status": "CANCELLED",
  "cancelledAt": "2026-03-13T10:00:00+09:00",
  "cancelledBy": 1
}
```

#### エラーレスポンス

| HTTPステータス | エラーコード | 説明 |
|-------------|------------|------|
| `404` | `OUTBOUND_SLIP_NOT_FOUND` | 出荷伝票が存在しない |
| `409` | `OUTBOUND_INVALID_STATUS` | `PICKING_COMPLETED` 以降はキャンセル不可 |

---

### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> FIND[outbound_slips を id で検索\nSELECT FOR UPDATE]
    FIND -->|存在しない| ERR_NF[404 OUTBOUND_SLIP_NOT_FOUND]
    FIND -->|OK| CHECK_STATUS{status が\nORDERED or\nPICKING_INSTRUCTED?}
    CHECK_STATUS -->|No| ERR_STATUS[409 OUTBOUND_INVALID_STATUS]
    CHECK_STATUS -->|Yes| CHECK_ALLOT{status ==\nPICKING_INSTRUCTED?}
    CHECK_ALLOT -->|Yes| DELETE_PIL[picking_instruction_lines を\noutbound_slip_line_id で DELETE\n引当情報を取り消し]
    CHECK_ALLOT -->|No| UPDATE_STATUS
    DELETE_PIL --> UPDATE_LINES[outbound_slip_lines.line_status\n= ORDERED に戻す]
    UPDATE_LINES --> UPDATE_STATUS[outbound_slips.status = CANCELLED\ncancelled_at, cancelled_by をセット]
    UPDATE_STATUS --> RESP[200 OK + 更新後のオブジェクト]
    RESP --> END([終了])
```

**ビジネスルール**

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | キャンセル可能なステータスは `ORDERED` と `PICKING_INSTRUCTED` のみ | `OUTBOUND_INVALID_STATUS` |
| 2 | `PICKING_INSTRUCTED` 状態でキャンセルする場合、対応する `picking_instruction_lines` を削除して引当を取り消す | — |
| 3 | `cancelled_at`、`cancelled_by` を記録する | — |

---

---

## API-OUT-011: ピッキング指示一覧取得

### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-OUT-011` |
| **API名** | ピッキング指示一覧取得 |
| **メソッド** | `GET` |
| **パス** | `/api/v1/outbound/picking` |
| **認証** | 要 |
| **対象ロール** | 全ロール |
| **概要** | ピッキング指示の一覧をページング形式で取得する。倉庫IDによる絞り込みが必須。 |
| **関連画面** | `OUT-011`（ピッキング指示一覧） |

---

### 2. リクエスト仕様

#### クエリパラメータ

| パラメータ名 | 型 | 必須 | デフォルト | 説明 |
|------------|-----|:----:|----------|------|
| `warehouseId` | Long | ○ | — | 倉庫ID（必須） |
| `instructionNumber` | String | — | — | 指示番号（前方一致） |
| `status` | String[] | — | — | ステータス（複数指定可）。例: `status=CREATED&status=IN_PROGRESS` |
| `createdDateFrom` | String | — | — | 作成日の開始日（`yyyy-MM-dd`） |
| `createdDateTo` | String | — | — | 作成日の終了日（`yyyy-MM-dd`） |
| `page` | Integer | — | `0` | ページ番号（0始まり） |
| `size` | Integer | — | `20` | 1ページあたりの件数（上限: 100） |
| `sort` | String | — | `createdAt,desc` | ソート指定 |

---

### 3. レスポンス仕様

#### 成功レスポンス（200 OK）

```json
{
  "content": [
    {
      "id": 50,
      "instructionNumber": "PIC-20260313-001",
      "warehouseId": 1,
      "warehouseName": "東京DC",
      "areaId": 5,
      "areaName": "A区画",
      "status": "CREATED",
      "lineCount": 10,
      "createdAt": "2026-03-13T09:00:00+09:00",
      "createdByName": "担当 太郎"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5,
  "totalPages": 1
}
```

#### エラーレスポンス

| HTTPステータス | エラーコード | 説明 |
|-------------|------------|------|
| `400` | `VALIDATION_ERROR` | `warehouseId` が未指定 |
| `404` | `WAREHOUSE_NOT_FOUND` | 指定倉庫が存在しない |

---

### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[warehouseId 必須チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| CHECK_WH[倉庫存在チェック]
    CHECK_WH -->|存在しない| ERR_WH[404 WAREHOUSE_NOT_FOUND]
    CHECK_WH -->|OK| QUERY[picking_instructions を条件検索\nページング・ソート適用]
    QUERY --> JOIN[areas, users テーブルをJOIN\nエリア名・作成者名を取得]
    JOIN --> COUNT[LINE COUNT を集計]
    COUNT --> RESP[200 OK + ページングレスポンス]
    RESP --> END([終了])
```

**ビジネスルール**:

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `warehouseId` は必須。未指定時は400を返す | `VALIDATION_ERROR` |
| 2 | 指定された倉庫が存在しない場合は404を返す | `WAREHOUSE_NOT_FOUND` |
| 3 | 検索結果が0件の場合は空の `content: []` を返す（エラーとしない） | — |

---

### 5. 補足事項

- **warehouseId 必須の理由**: ピッキング指示は特定倉庫に属するため、倉庫をまたいだ一覧取得は業務上不要。全件取得によるパフォーマンス劣化を防ぐためにも必須パラメータとしている。
- **lineCount**: 各ピッキング指示の明細件数。JOIN先の `picking_instruction_lines` で COUNT 集計して返す。
- **ソート**: デフォルトは `createdAt,desc`（新しい指示が先頭）。

---

---

## API-OUT-012: ピッキング指示作成

### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-OUT-012` |
| **API名** | ピッキング指示作成 |
| **メソッド** | `POST` |
| **パス** | `/api/v1/outbound/picking` |
| **認証** | 要 |
| **対象ロール** | `SYSTEM_ADMIN`, `WAREHOUSE_MANAGER` |
| **概要** | 在庫引当済みの受注（`PICKING_INSTRUCTED` 状態）からピッキング指示書を作成する。複数の受注を束ねて1つのピッキング指示にまとめることができる。 |
| **関連画面** | `OUT-011`（ピッキング指示一覧・指示作成ボタン） |

---

### 2. リクエスト仕様

#### リクエストボディ

```json
{
  "slipIds": [1, 2],
  "areaId": 5
}
```

| フィールド名 | 型 | 必須 | バリデーション | 説明 |
|------------|-----|:----:|-------------|------|
| `slipIds` | Long[] | ○ | 1件以上・重複不可 | 対象の出荷伝票IDリスト。`PICKING_INSTRUCTED` 状態のもののみ |
| `areaId` | Long | — | 存在するエリアID | 対象エリア絞り込み（省略時は全エリア対象） |

---

### 3. レスポンス仕様

#### 成功レスポンス（201 Created）

```json
{
  "id": 50,
  "instructionNumber": "PIC-20260313-001",
  "warehouseId": 1,
  "warehouseName": "東京DC",
  "areaId": 5,
  "areaName": "A区画",
  "status": "CREATED",
  "lines": [
    {
      "id": 101,
      "lineNo": 1,
      "outboundSlipNumber": "OUT-20260313-001",
      "outboundSlipLineId": 1,
      "locationId": 10,
      "locationCode": "A-01-01",
      "productCode": "P-001",
      "productName": "テスト商品A",
      "unitType": "CASE",
      "lotNumber": "LOT-001",
      "expiryDate": "2027-03-31",
      "qtyToPick": 5,
      "qtyPicked": null,
      "lineStatus": "PENDING"
    }
  ],
  "createdAt": "2026-03-13T09:00:00+09:00",
  "createdBy": 1
}
```

#### エラーレスポンス

| HTTPステータス | エラーコード | 説明 |
|-------------|------------|------|
| `400` | `VALIDATION_ERROR` | `slipIds` が未指定または空 |
| `404` | `OUTBOUND_SLIP_NOT_FOUND` | 指定IDの出荷伝票が存在しない |
| `404` | `AREA_NOT_FOUND` | 指定エリアが存在しない |
| `409` | `OUTBOUND_INVALID_STATUS` | `PICKING_INSTRUCTED` 以外の伝票が含まれている |

---

### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[入力バリデーション\nslipIds 1件以上]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| CHECK_AREA{areaId 指定あり?}
    CHECK_AREA -->|Yes| CHECK_AREA_EXISTS[エリア存在チェック]
    CHECK_AREA_EXISTS -->|存在しない| ERR_AREA[404 AREA_NOT_FOUND]
    CHECK_AREA_EXISTS -->|OK| CHECK_SLIPS
    CHECK_AREA -->|No| CHECK_SLIPS[各伝票の存在チェック\nステータスチェック]
    CHECK_SLIPS -->|存在しない| ERR_NF[404 OUTBOUND_SLIP_NOT_FOUND]
    CHECK_SLIPS -->|PICKING_INSTRUCTED以外| ERR_STATUS[409 OUTBOUND_INVALID_STATUS]
    CHECK_SLIPS -->|OK| FETCH_PIL[対象伝票の picking_instruction_lines を取得\nareaId 指定時はロケーションのエリアで絞り込み]
    FETCH_PIL --> GEN_NUM[ピッキング指示番号を自動採番\nPIC-yyyyMMdd-連番]
    GEN_NUM --> INSERT_PI[picking_instructions INSERT\nstatus=CREATED]
    INSERT_PI --> INSERT_PIL[picking_instruction_lines INSERT\nlocation_code等をコピー\nqty_to_pick, line_status=PENDING]
    INSERT_PIL --> RESP[201 Created + 指示オブジェクト]
    RESP --> END([終了])
```

**ビジネスルール**

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `slipIds` に含まれる伝票は全て `PICKING_INSTRUCTED` 状態でなければならない | `OUTBOUND_INVALID_STATUS` |
| 2 | ピッキング指示を作成しても受注伝票の `status` は `PICKING_INSTRUCTED` のまま変更しない（ピッキング指示と受注ステータスは独立管理） | — |
| 3 | `areaId` が指定された場合、対象ロケーションが当該エリアに属する引当明細のみを対象とする | — |
| 4 | 指示番号は `PIC-yyyyMMdd-NNN`（3桁連番）形式で自動採番する | — |
| 5 | `picking_instruction_lines` の `qty_to_pick` は、引当時に `inventory_allocations` テーブルに記録された `allocated_qty`（引当数量）をそのままセットする | — |

---

### 5. 補足事項

- 受注ステータス（`outbound_slips.status`）はピッキング指示作成時には変更しない。ステータスが `PICKING_COMPLETED` に遷移するのは `API-OUT-014`（ピッキング完了登録）のタイミングである。
- 1つの出荷伝票明細に対して複数のピッキング指示明細が生成される場合がある（複数ロケーション・ロットにまたがる引当の場合）。

---

---

## API-OUT-013: ピッキング指示詳細取得

### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-OUT-013` |
| **API名** | ピッキング指示詳細取得 |
| **メソッド** | `GET` |
| **パス** | `/api/v1/outbound/picking/{id}` |
| **認証** | 要 |
| **対象ロール** | 全ロール |
| **概要** | ピッキング指示IDを指定してヘッダ・全明細を取得する。ピッキング作業者が作業内容を確認するために使用する。 |
| **関連画面** | `OUT-012`（ピッキング指示詳細） |

---

### 2. リクエスト仕様

#### パスパラメータ

| パラメータ名 | 型 | 必須 | 説明 |
|------------|-----|:----:|------|
| `id` | Long | ○ | ピッキング指示ID |

---

### 3. レスポンス仕様

#### 成功レスポンス（200 OK）

```json
{
  "id": 50,
  "instructionNumber": "PIC-20260313-001",
  "warehouseId": 1,
  "warehouseName": "東京DC",
  "areaId": 5,
  "areaName": "A区画",
  "status": "CREATED",
  "lines": [
    {
      "id": 101,
      "lineNo": 1,
      "outboundSlipNumber": "OUT-20260313-001",
      "outboundSlipLineId": 1,
      "locationId": 10,
      "locationCode": "A-01-01",
      "productCode": "P-001",
      "productName": "テスト商品A",
      "unitType": "CASE",
      "lotNumber": "LOT-001",
      "expiryDate": "2027-03-31",
      "qtyToPick": 5,
      "qtyPicked": null,
      "lineStatus": "PENDING"
    }
  ],
  "createdAt": "2026-03-13T09:00:00+09:00",
  "createdBy": 1,
  "completedAt": null,
  "completedBy": null
}
```

#### エラーレスポンス

| HTTPステータス | エラーコード | 説明 |
|-------------|------------|------|
| `404` | `PICKING_NOT_FOUND` | 指定IDのピッキング指示が存在しない |

---

### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> FIND[picking_instructions を id で検索]
    FIND -->|存在しない| ERR[404 PICKING_NOT_FOUND]
    FIND -->|OK| FETCH_LINES[picking_instruction_lines を取得\nline_no 昇順]
    FETCH_LINES --> JOIN[outbound_slips をJOIN\nslipNumber を取得]
    JOIN --> RESP[200 OK + 詳細オブジェクト]
    RESP --> END([終了])
```

**ビジネスルール**:

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | 指定IDのピッキング指示が存在しない場合は404を返す | `PICKING_NOT_FOUND` |
| 2 | 明細は `line_no` 昇順で返す | — |

---

### 5. 補足事項

- **ステータスによるアクセス制限なし**: ピッキング指示はどのステータスでも取得可能（CREATED / IN_PROGRESS / COMPLETED）。
- **createdByName**: `users` テーブルをJOINして作成者氏名を返す。
- **completedAt / completedBy**: ピッキング完了前は `null`。`API-OUT-014` 完了時に設定される。

---

---

## API-OUT-014: ピッキング完了登録

### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-OUT-014` |
| **API名** | ピッキング完了登録 |
| **メソッド** | `PUT` |
| **パス** | `/api/v1/outbound/picking/{id}/complete` |
| **認証** | 要 |
| **対象ロール** | `SYSTEM_ADMIN`, `WAREHOUSE_MANAGER`, `WAREHOUSE_STAFF` |
| **概要** | ピッキング作業者がピッキングした数量を登録する。全明細完了時に指示ステータスを `COMPLETED` に更新し、関連する受注ステータスを `PICKING_COMPLETED` に変更する。 |
| **関連画面** | `OUT-012`（ピッキング指示詳細・完了入力） |

---

### 2. リクエスト仕様

#### パスパラメータ

| パラメータ名 | 型 | 必須 | 説明 |
|------------|-----|:----:|------|
| `id` | Long | ○ | ピッキング指示ID |

#### リクエストボディ

```json
{
  "lines": [
    { "lineId": 101, "qtyPicked": 5 },
    { "lineId": 102, "qtyPicked": 3 }
  ]
}
```

| フィールド名 | 型 | 必須 | バリデーション | 説明 |
|------------|-----|:----:|-------------|------|
| `lines` | Array | ○ | 1件以上 | ピッキング完了明細リスト |
| `lines[].lineId` | Long | ○ | 当該指示に属する明細ID | ピッキング指示明細ID |
| `lines[].qtyPicked` | Integer | ○ | 0以上 `qty_to_pick` 以下 | ピッキング完了数量（0は未ピッキングとして扱う） |

---

### 3. レスポンス仕様

#### 成功レスポンス（200 OK）

```json
{
  "id": 50,
  "instructionNumber": "PIC-20260313-001",
  "status": "COMPLETED",
  "completedAt": "2026-03-13T11:00:00+09:00",
  "completedBy": 1,
  "lines": [
    {
      "id": 101,
      "lineNo": 1,
      "qtyToPick": 5,
      "qtyPicked": 5,
      "lineStatus": "COMPLETED"
    }
  ]
}
```

#### エラーレスポンス

| HTTPステータス | エラーコード | 説明 |
|-------------|------------|------|
| `400` | `VALIDATION_ERROR` | `qtyPicked` が `qty_to_pick` を超えている等 |
| `404` | `PICKING_NOT_FOUND` | 指定IDのピッキング指示が存在しない |
| `409` | `OUTBOUND_INVALID_STATUS` | 既に `COMPLETED` 状態のピッキング指示 |

---

### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> FIND[picking_instructions を id で検索\nSELECT FOR UPDATE]
    FIND -->|存在しない| ERR_NF[404 PICKING_NOT_FOUND]
    FIND -->|OK| CHECK_STATUS{status == COMPLETED?}
    CHECK_STATUS -->|Yes| ERR_STATUS[409 OUTBOUND_INVALID_STATUS]
    CHECK_STATUS -->|No| VALIDATE_LINES[リクエストの lines バリデーション\n当該指示に属するlineIdか確認\nqtyPicked <= qty_to_pick]
    VALIDATE_LINES -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE_LINES -->|OK| UPDATE_PIL[picking_instruction_lines 更新\nqty_picked = リクエスト値\nline_status = COMPLETED]
    UPDATE_PIL --> CHECK_FIRST{初めての完了登録?}
    CHECK_FIRST -->|Yes| SET_IN_PROGRESS[picking_instructions.status\n= IN_PROGRESS]
    CHECK_FIRST -->|No| CHECK_ALL
    SET_IN_PROGRESS --> CHECK_ALL{全明細 COMPLETED?}
    CHECK_ALL -->|No| RESP_PARTIAL[200 OK + 更新後オブジェクト\nstatus=IN_PROGRESS]
    CHECK_ALL -->|Yes| SET_COMPLETED[picking_instructions.status = COMPLETED\ncompleted_at, completed_by をセット]
    SET_COMPLETED --> UPDATE_SLIPS[関連する outbound_slips の\nstatus を PICKING_COMPLETED に更新\n関連する outbound_slip_lines の\nline_status を PICKING_COMPLETED に更新]
    UPDATE_SLIPS --> RESP[200 OK + 更新後オブジェクト\nstatus=COMPLETED]
    RESP --> END([終了])
    RESP_PARTIAL --> END
```

**ビジネスルール**

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | リクエストの `lineId` は当該ピッキング指示に属する明細IDでなければならない | `VALIDATION_ERROR` |
| 2 | `qtyPicked` は `qty_to_pick` を超えることはできない | `VALIDATION_ERROR` |
| 3 | 最初の明細チェックが記録された時点で `picking_instructions.status` を `IN_PROGRESS` に更新する | — |
| 4 | 全明細が `COMPLETED` になった時点で `picking_instructions.status` を `COMPLETED` に更新し、`completed_at`、`completed_by` を記録する | — |
| 5 | 全明細完了時に、当該ピッキング指示に紐づく全ての `outbound_slips.status` を `PICKING_COMPLETED` に更新する | — |
| 6 | 全明細完了時に、対応する `outbound_slip_lines.line_status` を `PICKING_COMPLETED` に更新する | — |

---

### 5. 補足事項

- 1回の PUT リクエストで全明細分の完了数量をまとめて送信することも、一部の明細のみ送信することも可能。
- リクエストに含まれていない明細（`PENDING` のまま）は更新されない。
- 受注への反映（`outbound_slips.status = PICKING_COMPLETED`）は当該ピッキング指示の全明細が完了した場合のみ実施する。

---

---

## API-OUT-021: 出荷検品登録

### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-OUT-021` |
| **API名** | 出荷検品登録 |
| **メソッド** | `POST` |
| **パス** | `/api/v1/outbound/slips/{id}/inspect` |
| **認証** | 要 |
| **対象ロール** | `SYSTEM_ADMIN`, `WAREHOUSE_MANAGER`, `WAREHOUSE_STAFF` |
| **概要** | ピッキング完了済みの出荷伝票（`PICKING_COMPLETED` 状態）に対して出荷検品数量を登録し、ステータスを `INSPECTING` に更新する。 |
| **関連画面** | `OUT-004`（出荷検品） |

---

### 2. リクエスト仕様

#### パスパラメータ

| パラメータ名 | 型 | 必須 | 説明 |
|------------|-----|:----:|------|
| `id` | Long | ○ | 出荷伝票ID |

#### リクエストボディ

```json
{
  "lines": [
    { "lineId": 1, "inspectedQty": 5 },
    { "lineId": 2, "inspectedQty": 20 }
  ]
}
```

| フィールド名 | 型 | 必須 | バリデーション | 説明 |
|------------|-----|:----:|-------------|------|
| `lines` | Array | ○ | 1件以上 | 検品明細リスト |
| `lines[].lineId` | Long | ○ | 当該伝票に属する明細ID | 出荷明細ID |
| `lines[].inspectedQty` | Integer | ○ | 0以上 | 検品数量 |

---

### 3. レスポンス仕様

#### 成功レスポンス（200 OK）

```json
{
  "id": 1,
  "slipNumber": "OUT-20260313-001",
  "status": "INSPECTING",
  "lines": [
    {
      "id": 1,
      "lineNo": 1,
      "productCode": "P-001",
      "productName": "テスト商品A",
      "unitType": "CASE",
      "orderedQty": 5,
      "inspectedQty": 5,
      "lineStatus": "PICKING_COMPLETED"
    }
  ]
}
```

#### エラーレスポンス

| HTTPステータス | エラーコード | 説明 |
|-------------|------------|------|
| `400` | `VALIDATION_ERROR` | 入力値エラー（明細ID不正等） |
| `404` | `OUTBOUND_SLIP_NOT_FOUND` | 出荷伝票が存在しない |
| `409` | `OUTBOUND_INVALID_STATUS` | `PICKING_COMPLETED` 以外のステータスには登録不可 |

---

### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> FIND[outbound_slips を id で検索\nSELECT FOR UPDATE]
    FIND -->|存在しない| ERR_NF[404 OUTBOUND_SLIP_NOT_FOUND]
    FIND -->|OK| CHECK_STATUS{status ==\nPICKING_COMPLETED?}
    CHECK_STATUS -->|No| ERR_STATUS[409 OUTBOUND_INVALID_STATUS]
    CHECK_STATUS -->|Yes| VALIDATE_LINES[リクエストの lines バリデーション\n当該伝票に属するlineIdか確認]
    VALIDATE_LINES -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE_LINES -->|OK| UPDATE_LINES[outbound_slip_lines 更新\nshipped_qty = inspectedQty\n※検品数量を一時的にshipped_qtyへ格納]
    UPDATE_LINES --> UPDATE_SLIP[outbound_slips.status = INSPECTING]
    UPDATE_SLIP --> RESP[200 OK + 更新後の伝票オブジェクト]
    RESP --> END([終了])
```

**ビジネスルール**

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `PICKING_COMPLETED` 状態の伝票のみ対象 | `OUTBOUND_INVALID_STATUS` |
| 2 | 検品数量が受注数量（`ordered_qty`）と異なる場合も登録可能（差異は出荷完了時に確定する） | — |
| 3 | リクエストの `lineId` は当該伝票に属するものでなければならない | `VALIDATION_ERROR` |
| 4 | 全明細を一括で送信しなくても、部分的な検品数量入力（途中保存）が可能。未指定明細の `shipped_qty` は更新しない | — |

---

### 5. 補足事項

- **inspectedQty の格納先**: `outbound_slip_lines` テーブルには `inspected_qty` カラムは存在しない。検品数量は一時的に `shipped_qty` カラムへ格納する（`API-OUT-022` 出荷完了時に正式な出荷数として確定）。レスポンスの `inspectedQty` フィールドは `shipped_qty` の値を返す。
- **途中保存対応**: `review-records.md` のO-5指摘事項に対応。同一伝票に対して複数回の `POST /inspect` 呼び出しが可能で、後続の呼び出しが既存の検品数量を上書きする。
- **トランザクション**: `outbound_slip_lines` の `shipped_qty` 更新と `outbound_slips.status` の `INSPECTING` 更新を1トランザクションで処理する（`@Transactional`）。

---

---

## API-OUT-022: 出荷完了登録

### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-OUT-022` |
| **API名** | 出荷完了登録 |
| **メソッド** | `POST` |
| **パス** | `/api/v1/outbound/slips/{id}/ship` |
| **認証** | 要 |
| **対象ロール** | `SYSTEM_ADMIN`, `WAREHOUSE_MANAGER`, `WAREHOUSE_STAFF` |
| **概要** | 出荷検品済みの伝票（`INSPECTING` 状態）に対して出荷完了処理を行う。在庫の実減算・移動履歴の記録・伝票ステータス更新をトランザクションで一括処理する。 |
| **関連画面** | `OUT-004`（出荷検品・出荷完了ボタン） |

---

### 2. リクエスト仕様

#### パスパラメータ

| パラメータ名 | 型 | 必須 | 説明 |
|------------|-----|:----:|------|
| `id` | Long | ○ | 出荷伝票ID |

#### リクエストボディ

```json
{
  "carrier": "ヤマト運輸",
  "trackingNumber": "123456789012",
  "shippedDate": "2026-03-13",
  "note": ""
}
```

| フィールド名 | 型 | 必須 | バリデーション | 説明 |
|------------|-----|:----:|-------------|------|
| `carrier` | String | — | 最大100文字 | 配送業者名 |
| `trackingNumber` | String | — | 最大100文字 | 送り状番号 |
| `shippedDate` | String | ○ | `yyyy-MM-dd`、過去日不可 | 実際の出荷日 |
| `note` | String | — | 最大500文字 | 備考 |

---

### 3. レスポンス仕様

#### 成功レスポンス（200 OK）

```json
{
  "id": 1,
  "slipNumber": "OUT-20260313-001",
  "status": "SHIPPED",
  "carrier": "ヤマト運輸",
  "trackingNumber": "123456789012",
  "shippedAt": "2026-03-13T14:00:00+09:00",
  "shippedBy": 1,
  "lines": [
    {
      "id": 1,
      "lineNo": 1,
      "productCode": "P-001",
      "productName": "テスト商品A",
      "unitType": "CASE",
      "orderedQty": 5,
      "shippedQty": 5,
      "lineStatus": "SHIPPED"
    }
  ]
}
```

#### エラーレスポンス

| HTTPステータス | エラーコード | 説明 |
|-------------|------------|------|
| `400` | `VALIDATION_ERROR` | `shippedDate` 不正等 |
| `404` | `OUTBOUND_SLIP_NOT_FOUND` | 出荷伝票が存在しない |
| `409` | `OUTBOUND_INVALID_STATUS` | `INSPECTING` 以外のステータスには登録不可 |
| `422` | `INVENTORY_INSUFFICIENT` | 在庫が不足しており在庫減算できない |

---

### 4. 業務ロジック

出荷完了時の在庫減算は最も重要なトランザクション処理であり、厳密な排他制御が必要である。

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[入力バリデーション\nshippedDate形式チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| FIND[outbound_slips を id で検索\nSELECT FOR UPDATE]
    FIND -->|存在しない| ERR_NF[404 OUTBOUND_SLIP_NOT_FOUND]
    FIND -->|OK| CHECK_STATUS{status ==\nINSPECTING?}
    CHECK_STATUS -->|No| ERR_STATUS[409 OUTBOUND_INVALID_STATUS]
    CHECK_STATUS -->|Yes| FETCH_PIL[picking_instruction_lines を取得\nこの伝票の引当情報を取得]
    FETCH_PIL --> LOCK_INV["inventories を\nlocation_id + product_id + lot_number で\nSELECT FOR UPDATE\n（悲観的ロック）"]
    LOCK_INV --> CHECK_STOCK{在庫数 >= 出荷数?}
    CHECK_STOCK -->|No| ROLLBACK[ROLLBACK]
    ROLLBACK --> ERR_STOCK[422 INVENTORY_INSUFFICIENT]
    CHECK_STOCK -->|Yes| DEDUCT_INV["inventories の qty を減算\nUPDATE inventories\nSET qty = qty - shipped_qty\nWHERE location_id=? AND product_id=? AND lot_number=?"]
    DEDUCT_INV --> INSERT_MOVEMENT[inventory_movements INSERT\nmove_type = OUTBOUND\n出荷伝票番号・数量・ロケーション・ロット情報を記録]
    INSERT_MOVEMENT --> UPDATE_LINES[outbound_slip_lines 更新\nshipped_qty 確定\nline_status = SHIPPED]
    UPDATE_LINES --> UPDATE_SLIP[outbound_slips 更新\nstatus = SHIPPED\nshipped_at = 現在日時\nshipped_by = 実行者ID\ncarrier, tracking_number 更新]
    UPDATE_SLIP --> COMMIT[COMMIT]
    COMMIT --> RESP[200 OK + 更新後の伝票オブジェクト]
    RESP --> END([終了])
```

**在庫減算処理の詳細**

| ステップ | 処理内容 | SQL例 |
|---------|---------|-------|
| 1. ロック取得 | ピッキング指示明細で引当されたロケーション・商品・ロット番号の組み合わせで在庫レコードを `SELECT FOR UPDATE` | `SELECT * FROM inventories WHERE location_id=? AND product_id=? AND lot_number=? FOR UPDATE` |
| 2. 在庫チェック | 現在の `qty` が出荷数量（`shipped_qty`）以上であることを確認 | — |
| 3. 在庫減算 | `qty` から出荷数量を差し引く。`qty` が 0 になった場合はレコードを削除する（または 0 のまま保持する、設計方針に従う） | `UPDATE inventories SET qty = qty - ? WHERE id = ?` |
| 4. 移動履歴登録 | `inventory_movements` に `move_type=OUTBOUND` のレコードを INSERT | 出荷伝票番号・明細番号・ロケーション・商品・ロット・数量を記録 |
| 5. 伝票更新 | `outbound_slips.status = SHIPPED`、`shipped_at`、`shipped_by` を更新 | — |

**ビジネスルール**

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `INSPECTING` 状態の伝票のみ出荷完了可能 | `OUTBOUND_INVALID_STATUS` |
| 2 | 引当ロケーション・ロットの在庫が `shipped_qty` 未満の場合は 422 を返してロールバックする | `INVENTORY_INSUFFICIENT` |
| 3 | 在庫減算・移動履歴登録・ステータス更新は単一トランザクションで処理する | — |
| 4 | 在庫減算は `picking_instruction_lines` に記録された引当情報（ロケーション・ロット）に基づいて行う | — |
| 5 | `inventory_movements` に `move_type=OUTBOUND` で記録することで在庫の追跡可能性（トレーサビリティ）を担保する | — |
| 6 | `shippedDate` は当日以前の日付でなければならない（未来日不可） | `VALIDATION_ERROR` |
| 7 | `outbound_slip_lines.line_status` を `SHIPPED` に更新する | — |

---

### 5. 補足事項

- 出荷完了処理はシステムの最重要トランザクションのひとつであり、必ずDB接続エラー時にリトライ可能な設計とすること。
- `inventory_movements` には以下の情報を記録する: `move_type=OUTBOUND`, `outbound_slip_id`, `outbound_slip_number`, `outbound_slip_line_id`, `location_id`, `location_code`, `product_id`, `product_code`, `lot_number`, `expiry_date`, `qty`, `moved_at`, `moved_by`
- 出荷数量（`shipped_qty`）は出荷検品時（`API-OUT-021`）に `outbound_slip_lines` に格納された検品数量を使用する。
- 在庫ロック取得順序はデッドロック防止のため `inventories.id` 昇順で統一すること。

---

---

## エラーコード一覧（出荷管理）

| エラーコード | HTTPステータス | 説明 | 発生API |
|-----------|-------------|------|--------|
| `OUTBOUND_SLIP_NOT_FOUND` | 404 | 出荷伝票が見つからない | OUT-003, 004, allot, cancel, OUT-021, OUT-022 |
| `OUTBOUND_INVALID_STATUS` | 409 | 現在のステータスではその操作は不可 | OUT-004, allot, cancel, OUT-014, OUT-021, OUT-022 |
| `OUTBOUND_PARTNER_NOT_CUSTOMER` | 422 | 取引先種別が出荷先（CUSTOMER/BOTH）でない | OUT-002 |
| `OUTBOUND_PRODUCT_SHIPMENT_STOPPED` | 422 | 出荷禁止フラグが設定された商品 | OUT-002 |
| `PICKING_NOT_FOUND` | 404 | ピッキング指示が見つからない | OUT-013, OUT-014 |
| `ALLOCATION_INSUFFICIENT` | 422 | 在庫引当に必要な在庫が不足 | allot, bulk-allot |
| `INVENTORY_INSUFFICIENT` | 422 | 出荷完了時の在庫減算で在庫不足 | OUT-022 |

---

*最終更新: 2026-03-13*
