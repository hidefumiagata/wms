# データモデル定義 — トランザクション系テーブル

## 入荷系

### `inbound_slips`（入荷ヘッダ）

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `slip_number` | varchar(50) | NOT NULL | — | 伝票番号。システム全体で一意。自動採番 |
| `slip_type` | varchar(30) | NOT NULL | — | 入荷種別: `NORMAL`(通常入荷) / `WAREHOUSE_TRANSFER`(倉庫振替入荷) |
| `transfer_slip_number` | varchar(50) | NULL | — | 振替伝票番号。倉庫振替入荷の場合、振替元の出荷伝票番号を保持（参照用） |
| `warehouse_id` | bigint | NOT NULL | — | FK → warehouses.id |
| `warehouse_code` | varchar(50) | NOT NULL | — | 倉庫コード（登録時コピー） |
| `warehouse_name` | varchar(200) | NOT NULL | — | 倉庫名（登録時コピー） |
| `partner_id` | bigint | NULL | — | FK → partners.id（仕入先。倉庫振替入荷の場合NULL可） |
| `partner_code` | varchar(50) | NULL | — | 取引先コード（登録時コピー） |
| `partner_name` | varchar(200) | NULL | — | 取引先名（登録時コピー） |
| `planned_date` | date | NOT NULL | — | 入荷予定日 |
| `status` | varchar(30) | NOT NULL | `PLANNED` | ステータス: `PLANNED`(入荷予定) / `CONFIRMED`(入荷確認済) / `INSPECTING`(検品中) / `PARTIAL_STORED`(一部入庫) / `STORED`(入庫完了) / `CANCELLED`(キャンセル) |
| `note` | text | NULL | — | 備考 |
| `cancelled_at` | timestamptz | NULL | — | キャンセル日時 |
| `cancelled_by` | bigint | NULL | — | キャンセル者（FK → users.id） |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |
| `created_by` | bigint | NOT NULL | — | 作成者（FK → users.id） |
| `updated_at` | timestamptz | NOT NULL | now() | 更新日時 |
| `updated_by` | bigint | NOT NULL | — | 更新者（FK → users.id） |

**制約・インデックス**:
- `UNIQUE (slip_number)`
- `INDEX (warehouse_id, planned_date)` — 一覧検索用
- `INDEX (warehouse_id, status)`
- `INDEX (transfer_slip_number)` — 振替伝票照会用

---

### `inbound_slip_lines`（入荷明細）

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `inbound_slip_id` | bigint | NOT NULL | — | FK → inbound_slips.id |
| `line_no` | int | NOT NULL | — | 明細行番号 |
| `product_id` | bigint | NOT NULL | — | FK → products.id |
| `product_code` | varchar(50) | NOT NULL | — | 商品コード（登録時コピー） |
| `product_name` | varchar(200) | NOT NULL | — | 商品名（登録時コピー） |
| `unit_type` | varchar(10) | NOT NULL | — | 荷姿: `CASE`(ケース) / `BALL`(ボール) / `PIECE`(バラ) |
| `planned_qty` | int | NOT NULL | — | 入荷予定数 |
| `inspected_qty` | int | NULL | — | 検品数（検品完了後にセット） |
| `lot_number` | varchar(100) | NULL | — | ロット番号（lot_manage_flag=true の商品の入荷時に必須） |
| `expiry_date` | date | NULL | — | 賞味/使用期限日（expiry_manage_flag=true の商品の入荷時に必須） |
| `putaway_location_id` | bigint | NULL | — | FK → locations.id（入庫確定後にセット） |
| `putaway_location_code` | varchar(50) | NULL | — | 入庫先ロケーションコード（入庫確定時コピー） |
| `line_status` | varchar(20) | NOT NULL | `PENDING` | 明細ステータス: `PENDING`(未処理) / `INSPECTED`(検品済) / `STORED`(入庫済) / `CANCELLED`(キャンセル済) |
| `inspected_at` | timestamptz | NULL | — | 検品日時 |
| `inspected_by` | bigint | NULL | — | 検品者（FK → users.id） |
| `stored_at` | timestamptz | NULL | — | 入庫確定日時 |
| `stored_by` | bigint | NULL | — | 入庫確定者（FK → users.id） |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |
| `updated_at` | timestamptz | NOT NULL | now() | 更新日時 |

**制約**:
- `UNIQUE (inbound_slip_id, line_no)`
- `unit_type IN ('CASE', 'BALL', 'PIECE')`

---

## 出荷系

### `outbound_slips`（出荷ヘッダ / 受注）

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `slip_number` | varchar(50) | NOT NULL | — | 伝票番号（受注番号）。システム全体で一意 |
| `slip_type` | varchar(30) | NOT NULL | — | 出荷種別: `NORMAL`(通常出荷) / `WAREHOUSE_TRANSFER`(倉庫振替出荷) |
| `transfer_slip_number` | varchar(50) | NULL | — | 振替伝票番号（倉庫振替出荷の場合、振替先入荷伝票との照会用） |
| `warehouse_id` | bigint | NOT NULL | — | FK → warehouses.id |
| `warehouse_code` | varchar(50) | NOT NULL | — | 倉庫コード（登録時コピー） |
| `warehouse_name` | varchar(200) | NOT NULL | — | 倉庫名（登録時コピー） |
| `partner_id` | bigint | NULL | — | FK → partners.id（出荷先） |
| `partner_code` | varchar(50) | NULL | — | 取引先コード（登録時コピー） |
| `partner_name` | varchar(200) | NULL | — | 取引先名（登録時コピー） |
| `planned_date` | date | NOT NULL | — | 出荷予定日 |
| `carrier` | varchar(100) | NULL | — | 配送業者 |
| `tracking_number` | varchar(100) | NULL | — | 送り状番号（出荷完了後にセット） |
| `status` | varchar(30) | NOT NULL | `ORDERED` | ステータス: `ORDERED`(受注) / `PARTIAL_ALLOCATED`(一部引当) / `ALLOCATED`(引当完了・ピッキング指示可能) / `PICKING_COMPLETED`(ピッキング完了) / `INSPECTING`(出荷検品中) / `SHIPPED`(出荷完了) / `CANCELLED`(キャンセル) |
| `note` | text | NULL | — | 備考 |
| `shipped_at` | timestamptz | NULL | — | 出荷完了日時 |
| `shipped_by` | bigint | NULL | — | 出荷完了者（FK → users.id） |
| `cancelled_at` | timestamptz | NULL | — | キャンセル日時 |
| `cancelled_by` | bigint | NULL | — | キャンセル者（FK → users.id） |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |
| `created_by` | bigint | NOT NULL | — | 作成者（FK → users.id） |
| `updated_at` | timestamptz | NOT NULL | now() | 更新日時 |
| `updated_by` | bigint | NOT NULL | — | 更新者（FK → users.id） |

**制約・インデックス**:
- `UNIQUE (slip_number)`
- `INDEX (warehouse_id, planned_date)`
- `INDEX (warehouse_id, status)`
- `INDEX (transfer_slip_number)`

---

### `outbound_slip_lines`（出荷明細）

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `outbound_slip_id` | bigint | NOT NULL | — | FK → outbound_slips.id |
| `line_no` | int | NOT NULL | — | 明細行番号 |
| `product_id` | bigint | NOT NULL | — | FK → products.id |
| `product_code` | varchar(50) | NOT NULL | — | 商品コード（登録時コピー） |
| `product_name` | varchar(200) | NOT NULL | — | 商品名（登録時コピー） |
| `unit_type` | varchar(10) | NOT NULL | — | 荷姿: `CASE` / `BALL` / `PIECE` |
| `ordered_qty` | int | NOT NULL | — | 受注数量 |
| `inspected_qty` | int | NULL | — | 検品済み数量。出荷検品API（API-OUT-021）で検品数量を記録する |
| `shipped_qty` | int | NOT NULL | 0 | 最終出荷数量。出荷完了API（API-OUT-022）で最終出荷数量を記録する（通常 `inspected_qty` と同値） |
| `line_status` | varchar(30) | NOT NULL | `ORDERED` | 明細ステータス: `ORDERED`(受注) / `PARTIAL_ALLOCATED`(一部引当済。明細の受注数量の一部のみ引当完了) / `ALLOCATED`(引当完了) / `PICKING_COMPLETED`(ピッキング完了) / `SHIPPED`(出荷済) |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |
| `updated_at` | timestamptz | NOT NULL | now() | 更新日時 |

**制約**:
- `UNIQUE (outbound_slip_id, line_no)`

---

### `picking_instructions`（ピッキング指示ヘッダ）

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `instruction_number` | varchar(50) | NOT NULL | — | ピッキング指示番号。一意。自動採番 |
| `warehouse_id` | bigint | NOT NULL | — | FK → warehouses.id |
| `area_id` | bigint | NULL | — | FK → areas.id（指定エリア。NULL の場合は倉庫全体） |
| `status` | varchar(20) | NOT NULL | `CREATED` | ステータス: `CREATED`(作成済) / `IN_PROGRESS`(作業中) / `COMPLETED`(完了) |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |
| `created_by` | bigint | NOT NULL | — | 作成者（FK → users.id） |
| `completed_at` | timestamptz | NULL | — | 完了日時 |
| `completed_by` | bigint | NULL | — | 完了者（FK → users.id） |

**制約**:
- `UNIQUE (instruction_number)`

---

### `picking_instruction_lines`（ピッキング指示明細）

1出荷明細（`outbound_slip_lines`）が複数ロケーションに分割される場合、複数レコードとして登録する。

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `picking_instruction_id` | bigint | NOT NULL | — | FK → picking_instructions.id |
| `line_no` | int | NOT NULL | — | 明細行番号 |
| `outbound_slip_line_id` | bigint | NOT NULL | — | FK → outbound_slip_lines.id（対応する出荷明細） |
| `location_id` | bigint | NOT NULL | — | FK → locations.id（ピッキング元ロケーション） |
| `location_code` | varchar(50) | NOT NULL | — | ロケーションコード（作成時コピー） |
| `product_id` | bigint | NOT NULL | — | FK → products.id |
| `product_code` | varchar(50) | NOT NULL | — | 商品コード（作成時コピー） |
| `product_name` | varchar(200) | NOT NULL | — | 商品名（作成時コピー） |
| `unit_type` | varchar(10) | NOT NULL | — | 荷姿: `CASE` / `BALL` / `PIECE` |
| `lot_number` | varchar(100) | NULL | — | ロット番号（在庫から引き当て時にセット） |
| `expiry_date` | date | NULL | — | 期限日（在庫から引き当て時にセット） |
| `qty_to_pick` | int | NOT NULL | — | ピッキング指示数量 |
| `qty_picked` | int | NOT NULL | 0 | ピッキング完了数量 |
| `line_status` | varchar(20) | NOT NULL | `PENDING` | 明細ステータス: `PENDING`(未完了) / `COMPLETED`(完了) |
| `completed_at` | timestamptz | NULL | — | 完了日時 |
| `completed_by` | bigint | NULL | — | 完了者（FK → users.id） |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |
| `updated_at` | timestamptz | NOT NULL | now() | 更新日時 |

**制約・インデックス**:
- `UNIQUE (picking_instruction_id, line_no)`
- `INDEX (outbound_slip_line_id)` — 出荷明細からの検索用
- `INDEX (picking_instruction_id, location_code)` — ロケーション順ソート（ピッキング指示書出力）

---

## 在庫系

### `inventories`（在庫）

現在の在庫数量を保持する。5軸（ロケーション・商品・荷姿・ロット番号・期限日）の組み合わせで1レコード。

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `warehouse_id` | bigint | NOT NULL | — | FK → warehouses.id（冗長保持。検索高速化） |
| `location_id` | bigint | NOT NULL | — | FK → locations.id |
| `product_id` | bigint | NOT NULL | — | FK → products.id |
| `unit_type` | varchar(10) | NOT NULL | — | 荷姿: `CASE` / `BALL` / `PIECE` |
| `lot_number` | varchar(100) | NULL | — | ロット番号。ロット管理フラグOFFの場合 NULL |
| `expiry_date` | date | NULL | — | 期限日。期限管理フラグOFFの場合 NULL |
| `quantity` | int | NOT NULL | 0 | 在庫数量（0以上） |
| `allocated_qty` | int | NOT NULL | 0 | 引当数量（0以上）。出荷確定時に減算される |
| `version` | bigint | NOT NULL | 0 | 楽観的ロック用バージョン番号（Javaエンティティの `@Version` アノテーションが管理） |
| `updated_at` | timestamptz | NOT NULL | now() | 最終更新日時 |

**制約・インデックス**:
- `UNIQUE (location_id, product_id, unit_type, lot_number, expiry_date) NULLS NOT DISTINCT` — PostgreSQL 15以降
- `CHECK (quantity >= 0)`
- `CHECK (allocated_qty >= 0)`
- `CHECK (allocated_qty <= quantity)` — 引当数は在庫数を超えない
- `INDEX (warehouse_id, product_id)` — 商品別在庫検索
- `INDEX (warehouse_id, location_id)` — ロケーション別在庫検索

> 在庫数量は直接UPDATE。楽観的ロック（バージョン番号）を使用して並行更新の整合性を保証する（Javaエンティティ側で `@Version` アノテーション）。
> 有効在庫数（新規引当・移動等で利用可能な数量）は `quantity - allocated_qty` で算出する。

---

### `inventory_movements`（在庫変動履歴）

在庫の全増減を記録する追記専用テーブル。更新・削除は行わない。

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `warehouse_id` | bigint | NOT NULL | — | FK → warehouses.id |
| `location_id` | bigint | NOT NULL | — | FK → locations.id |
| `location_code` | varchar(50) | NOT NULL | — | ロケーションコード（記録時コピー） |
| `product_id` | bigint | NOT NULL | — | FK → products.id |
| `product_code` | varchar(50) | NOT NULL | — | 商品コード（記録時コピー） |
| `product_name` | varchar(200) | NOT NULL | — | 商品名（記録時コピー） |
| `unit_type` | varchar(10) | NOT NULL | — | 荷姿 |
| `lot_number` | varchar(100) | NULL | — | ロット番号 |
| `expiry_date` | date | NULL | — | 期限日 |
| `movement_type` | varchar(30) | NOT NULL | — | 変動種別（下記参照） |
| `quantity` | int | NOT NULL | — | 変動数量（入庫方向は正、出庫方向は負） |
| `quantity_after` | int | NOT NULL | — | 変動後の在庫数量 |
| `reference_id` | bigint | NULL | — | 関連レコードID（入荷明細ID・出荷明細ID等） |
| `reference_type` | varchar(50) | NULL | — | 関連テーブル種別（`INBOUND_LINE` / `PICKING_LINE` / `STOCKTAKE_HEADER` 等） |
| `correction_reason` | varchar(500) | NULL | — | 在庫訂正理由（`movement_type = CORRECTION` の場合） |
| `executed_at` | timestamptz | NOT NULL | now() | 変動発生日時 |
| `executed_by` | bigint | NOT NULL | — | 実行者（FK → users.id） |

**movement_type の値**:

| 値 | 説明 |
|----|------|
| `INBOUND` | 入庫（入荷確定） |
| `OUTBOUND` | 出庫（ピッキング完了） |
| `MOVE_OUT` | 在庫移動（移動元ロケーション） |
| `MOVE_IN` | 在庫移動（移動先ロケーション） |
| `BREAKDOWN_OUT` | ばらし（上位荷姿の減少） |
| `BREAKDOWN_IN` | ばらし（下位荷姿の増加） |
| `CORRECTION` | 在庫訂正（手動） |
| `STOCKTAKE_ADJUSTMENT` | 棚卸調整（棚卸確定時の差異反映） |
| `INBOUND_CANCEL` | 入荷キャンセルによる在庫戻し |
| `RETURN_OUT` | 在庫返品による減算 |

**インデックス**:
- `INDEX (warehouse_id, product_id, executed_at)` — 在庫推移レポート用
- `INDEX (warehouse_id, location_id, executed_at)` — ロケーション別履歴用

---

### `stocktake_headers`（棚卸ヘッダ）

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `stocktake_number` | varchar(50) | NOT NULL | — | 棚卸番号。一意。自動採番 |
| `warehouse_id` | bigint | NOT NULL | — | FK → warehouses.id |
| `target_description` | varchar(500) | NULL | — | 対象ロケーション範囲の説明（例: `A棟 1F全エリア`） |
| `stocktake_date` | date | NOT NULL | — | 棚卸実施日 |
| `status` | varchar(20) | NOT NULL | `STARTED` | ステータス: `STARTED`(棚卸中) / `CONFIRMED`(棚卸確定) |
| `note` | text | NULL | — | 備考 |
| `started_at` | timestamptz | NOT NULL | now() | 棚卸開始日時 |
| `started_by` | bigint | NOT NULL | — | 開始者（FK → users.id） |
| `confirmed_at` | timestamptz | NULL | — | 棚卸確定日時 |
| `confirmed_by` | bigint | NULL | — | 確定者（FK → users.id） |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |
| `updated_at` | timestamptz | NOT NULL | now() | 更新日時 |

**制約**:
- `UNIQUE (stocktake_number)`
- 棚卸中（`STARTED`）のロケーションは無効化不可（アプリ層で制御）

---

### `stocktake_lines`（棚卸明細）

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `stocktake_header_id` | bigint | NOT NULL | — | FK → stocktake_headers.id |
| `location_id` | bigint | NOT NULL | — | FK → locations.id |
| `location_code` | varchar(50) | NOT NULL | — | ロケーションコード（作成時コピー） |
| `product_id` | bigint | NOT NULL | — | FK → products.id |
| `product_code` | varchar(50) | NOT NULL | — | 商品コード（作成時コピー） |
| `product_name` | varchar(200) | NOT NULL | — | 商品名（作成時コピー） |
| `unit_type` | varchar(10) | NOT NULL | — | 荷姿 |
| `lot_number` | varchar(100) | NULL | — | ロット番号 |
| `expiry_date` | date | NULL | — | 期限日 |
| `quantity_before` | int | NOT NULL | — | 棚卸前在庫数量（棚卸開始時点の在庫数） |
| `quantity_counted` | int | NULL | — | 実数（棚卸入力値） |
| `quantity_diff` | int | NULL | — | 差異数（`quantity_counted - quantity_before`。確定時に算出） |
| `is_counted` | boolean | NOT NULL | false | 実数入力済みフラグ |
| `counted_at` | timestamptz | NULL | — | 実数入力日時 |
| `counted_by` | bigint | NULL | — | 実数入力者（FK → users.id） |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |
| `updated_at` | timestamptz | NOT NULL | now() | 更新日時 |

**インデックス**:
- `INDEX (stocktake_header_id, location_code)` — ロケーションコード順ソート（棚卸リスト出力）

---

### `allocation_details`（引当明細）

受注明細とロケーション在庫の引当紐付けを記録する。

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `outbound_slip_id` | bigint | NOT NULL | — | FK → outbound_slips.id |
| `outbound_slip_line_id` | bigint | NOT NULL | — | FK → outbound_slip_lines.id |
| `inventory_id` | bigint | NOT NULL | — | FK → inventories.id（引当元在庫） |
| `location_id` | bigint | NOT NULL | — | FK → locations.id（引当ロケーション） |
| `product_id` | bigint | NOT NULL | — | FK → products.id |
| `unit_type` | varchar(10) | NOT NULL | — | 荷姿: `CASE` / `BALL` / `PIECE` |
| `lot_number` | varchar(100) | NULL | — | ロット番号 |
| `expiry_date` | date | NULL | — | 期限日 |
| `allocated_qty` | int | NOT NULL | — | 引当数量 |
| `warehouse_id` | bigint | NOT NULL | — | FK → warehouses.id |
| `created_at` | timestamptz | NOT NULL | now() | 引当日時 |
| `created_by` | bigint | NOT NULL | — | 引当実行者（FK → users.id） |

**制約・インデックス**:
- `INDEX (outbound_slip_line_id)` — 受注明細別の引当検索
- `INDEX (inventory_id)` — 在庫別の引当検索

---

### `unpack_instructions`（ばらし指示）

引当時に自動生成されるばらし指示を管理する。

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `outbound_slip_id` | bigint | NOT NULL | — | FK → outbound_slips.id（関連受注） |
| `location_id` | bigint | NOT NULL | — | FK → locations.id（ばらし実施ロケーション） |
| `product_id` | bigint | NOT NULL | — | FK → products.id |
| `from_unit_type` | varchar(10) | NOT NULL | — | ばらし元荷姿: `CASE` / `BALL` |
| `from_qty` | int | NOT NULL | — | ばらし元数量 |
| `to_unit_type` | varchar(10) | NOT NULL | — | ばらし先荷姿: `BALL` / `PIECE` |
| `to_qty` | int | NOT NULL | — | ばらし先数量（自動計算） |
| `status` | varchar(20) | NOT NULL | 'INSTRUCTED' | ステータス: `INSTRUCTED`(指示済) / `COMPLETED`(完了) |
| `warehouse_id` | bigint | NOT NULL | — | FK → warehouses.id |
| `created_at` | timestamptz | NOT NULL | now() | 指示作成日時 |
| `created_by` | bigint | NOT NULL | — | 指示作成者（FK → users.id） |
| `completed_at` | timestamptz | NULL | — | 完了日時 |
| `completed_by` | bigint | NULL | — | 完了者（FK → users.id） |

**制約・インデックス**:
- `INDEX (outbound_slip_id)` — 受注別のばらし指示検索
- `INDEX (status)` — ステータス別検索

> ばらし完了時に `inventory_movements` にも種別「BREAKDOWN_OUT / BREAKDOWN_IN」で変動履歴を記録する。

---

## 返品系

### `return_slips`（返品伝票）

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `return_number` | varchar(30) | NOT NULL | — | 返品伝票番号（`RTN-I-YYYYMMDD-XXXX` / `RTN-S-YYYYMMDD-XXXX` / `RTN-O-YYYYMMDD-XXXX`） |
| `return_type` | varchar(20) | NOT NULL | — | 返品種別: `INBOUND`(入荷返品) / `INVENTORY`(在庫返品) / `OUTBOUND`(出荷返品) |
| `status` | varchar(20) | NOT NULL | `COMPLETED` | ステータス: `REGISTERED`(登録済) / `COMPLETED`(完了) |
| `warehouse_id` | bigint | NOT NULL | — | FK → warehouses.id |
| `warehouse_code` | varchar(50) | NOT NULL | — | 倉庫コード（登録時コピー） |
| `warehouse_name` | varchar(200) | NOT NULL | — | 倉庫名（登録時コピー） |
| `partner_id` | bigint | NULL | — | FK → partners.id（仕入先） |
| `partner_code` | varchar(50) | NULL | — | 仕入先コード（登録時コピー） |
| `partner_name` | varchar(200) | NULL | — | 仕入先名（登録時コピー） |
| `product_id` | bigint | NOT NULL | — | FK → products.id |
| `product_code` | varchar(50) | NOT NULL | — | 商品コード（登録時コピー） |
| `product_name` | varchar(200) | NOT NULL | — | 商品名（登録時コピー） |
| `quantity` | int | NOT NULL | — | 返品数量 |
| `unit_type` | varchar(10) | NOT NULL | — | 荷姿: `CASE`(ケース) / `BALL`(ボール) / `PIECE`(バラ) |
| `location_id` | bigint | NULL | — | FK → locations.id（在庫返品の場合のみ） |
| `return_reason` | varchar(30) | NOT NULL | — | 返品理由コード: `QUALITY_DEFECT`(品質不良) / `EXCESS_QUANTITY`(数量過剰) / `WRONG_DELIVERY`(誤配送) / `EXPIRED`(期限切れ) / `DAMAGED`(破損) / `OTHER`(その他) |
| `return_reason_note` | text | NULL | — | 返品理由備考 |
| `related_slip_number` | varchar(50) | NULL | — | 関連伝票番号（入荷伝票番号 or 出荷伝票番号） |
| `lot_number` | varchar(100) | NULL | — | ロット番号（任意） |
| `expiry_date` | date | NULL | — | 賞味期限（任意） |
| `return_date` | date | NOT NULL | — | 返品日（営業日） |
| `created_by` | bigint | NOT NULL | — | 作成者（FK → users.id） |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |
| `updated_by` | bigint | NOT NULL | — | 更新者（FK → users.id） |
| `updated_at` | timestamptz | NOT NULL | now() | 更新日時 |
| `version` | bigint | NOT NULL | 0 | 楽観的ロック用バージョン番号（Javaエンティティの `@Version` アノテーションが管理） |

**制約・インデックス**:
- `UNIQUE (return_number)`
- `INDEX idx_return_slips_warehouse_date (warehouse_id, return_date)` — 一覧検索用
- `INDEX idx_return_slips_product (product_id)` — 商品別検索
- `INDEX idx_return_slips_partner (partner_id)` — 取引先別検索
- `INDEX idx_return_slips_type (return_type)` — 返品種別別検索

---

## 外部連携系

### `if_executions`（外部連携I/F実行履歴）

外部連携I/F（CSV取り込み）の実行履歴を記録するテーブル。詳細設計は [09-interface-architecture.md](../architecture-design/09-interface-architecture.md) セクション10.4を参照。

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `if_type` | varchar(30) | NOT NULL | — | I/F種別: `INBOUND_PLAN`(入荷予定取り込み) / `ORDER`(受注取り込み) |
| `file_name` | varchar(500) | NOT NULL | — | 元ファイル名 |
| `blob_path` | varchar(1000) | NOT NULL | — | Blob Storage パス（processed後の完全パス） |
| `total_count` | int | NOT NULL | — | 総レコード数（ヘッダ除く） |
| `success_count` | int | NOT NULL | — | 成功件数 |
| `error_count` | int | NOT NULL | — | エラー件数 |
| `mode` | varchar(20) | NOT NULL | — | 実行モード: `SUCCESS_ONLY`(成功行のみ取り込み) / `DISCARD`(全件破棄) |
| `status` | varchar(20) | NOT NULL | — | 実行状態: `COMPLETED`(取り込み成功) / `DISCARDED`(全件破棄) / `FAILED`(取り込み失敗) |
| `error_message` | text | NULL | — | エラー発生時のメッセージ（FAILED時のみ） |
| `blob_move_failed` | boolean | NOT NULL | false | Blob移動失敗フラグ |
| `warehouse_id` | bigint | NOT NULL | — | FK → warehouses.id（取り込み対象倉庫） |
| `executed_at` | timestamptz | NOT NULL | now() | 実行日時 |
| `executed_by` | bigint | NOT NULL | — | 実行ユーザー（FK → users.id） |

**status の値**:

| 値 | 意味 |
|----|------|
| `COMPLETED` | 取り込み成功（mode=SUCCESS_ONLYでDB登録完了） |
| `DISCARDED` | 全件破棄（mode=DISCARDでDB登録なし） |
| `FAILED` | 取り込み失敗（DB登録中にエラー発生） |

**制約・インデックス**:
- `INDEX (if_type, executed_at DESC)` — 履歴一覧取得用
- `INDEX (warehouse_id, executed_at DESC)` — 倉庫別履歴一覧用

---
