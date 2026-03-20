# データモデル定義 — バッチ・集計・バックアップ系テーブル

## バッチ管理系

### `business_date`（営業日管理）

システムの現在営業日を管理する単一レコードテーブル。日替処理の「営業日更新」ステップで更新される。

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | int | NOT NULL | 1 | PK（常に1のみ存在）。JPA Entityでは `Integer` 型を使用（他テーブルの `Long` とは異なる） |
| `current_business_date` | date | NOT NULL | — | 現在の営業日 |
| `updated_at` | timestamptz | NOT NULL | now() | 更新日時 |
| `updated_by` | bigint | NULL | — | 更新者（FK → users.id。バッチ実行者） |

> レコードは1件のみ。`SELECT ... FOR UPDATE` で排他制御して更新する。

---

### `batch_execution_logs`（バッチ実行履歴）

日替処理の実行履歴を全て記録する。成功・失敗を問わず保持。

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `target_business_date` | date | NOT NULL | — | 処理対象営業日 |
| `status` | varchar(20) | NOT NULL | `RUNNING` | 実行状態: `RUNNING`(実行中) / `SUCCESS`(成功) / `FAILED`(失敗) |
| `step1_status` | varchar(20) | NULL | — | 営業日更新: `SUCCESS` / `FAILED` / `SKIPPED` |
| `step2_status` | varchar(20) | NULL | — | 入荷実績集計: `SUCCESS` / `FAILED` / `SKIPPED` |
| `step3_status` | varchar(20) | NULL | — | 出荷実績集計: `SUCCESS` / `FAILED` / `SKIPPED` |
| `step4_status` | varchar(20) | NULL | — | 在庫集計: `SUCCESS` / `FAILED` / `SKIPPED` |
| `step5_status` | varchar(20) | NULL | — | トランデータバックアップ+リスト生成: `SUCCESS` / `FAILED` / `SKIPPED` |
| `step6_status` | varchar(20) | NULL | — | 日次集計レコード生成: `SUCCESS` / `FAILED` / `SKIPPED` |
| `error_message` | text | NULL | — | エラー発生時のメッセージ |
| `started_at` | timestamptz | NOT NULL | now() | 実行開始日時 |
| `completed_at` | timestamptz | NULL | — | 実行完了日時（成功・失敗問わず処理終了時にセット） |
| `executed_by` | bigint | NOT NULL | — | 実行者（FK → users.id） |

**制約**:
- `UNIQUE (target_business_date)` — 同一営業日の二重実行防止
  - ただし `status = 'FAILED'` のレコードが存在する場合は再実行可能（アプリ層で制御：FAILED のレコードを削除してから再登録）

> **二重実行防止の仕組み**: 実行開始時に `INSERT INTO batch_execution_logs` で RUNNING レコードを挿入。既にレコードが存在すれば UNIQUE 制約違反でエラー。ただし `FAILED` レコードの場合は削除して再実行可能。

---

## 集計系

### `inbound_summaries`（入荷実績サマリー）

日替処理の「入荷実績集計」ステップで生成。対象営業日の入庫完了データを集計する。

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `business_date` | date | NOT NULL | — | 対象営業日 |
| `warehouse_id` | bigint | NOT NULL | — | FK → warehouses.id |
| `warehouse_code` | varchar(50) | NOT NULL | — | 倉庫コード（集計時コピー） |
| `inbound_count` | int | NOT NULL | — | 入庫完了件数（入荷ヘッダ単位） |
| `inbound_line_count` | int | NOT NULL | — | 入庫完了明細件数 |
| `inbound_quantity_total` | bigint | NOT NULL | — | 入庫数量合計（バラ換算） |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |

**制約**:
- `UNIQUE (business_date, warehouse_id)`

---

### `outbound_summaries`（出荷実績サマリー）

日替処理の「出荷実績集計」ステップで生成。対象営業日の出荷完了データを集計する。

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `business_date` | date | NOT NULL | — | 対象営業日 |
| `warehouse_id` | bigint | NOT NULL | — | FK → warehouses.id |
| `warehouse_code` | varchar(50) | NOT NULL | — | 倉庫コード（集計時コピー） |
| `outbound_count` | int | NOT NULL | — | 出荷完了件数（出荷ヘッダ単位） |
| `outbound_line_count` | int | NOT NULL | — | 出荷完了明細件数 |
| `outbound_quantity_total` | bigint | NOT NULL | — | 出荷数量合計（バラ換算） |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |

**制約**:
- `UNIQUE (business_date, warehouse_id)`

---

### `inventory_snapshots`（在庫スナップショット）

日替処理の「在庫集計」ステップで生成。営業日末時点の在庫数量サマリー。商品・倉庫・荷姿単位で集計。

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `business_date` | date | NOT NULL | — | 対象営業日 |
| `warehouse_id` | bigint | NOT NULL | — | FK → warehouses.id |
| `warehouse_code` | varchar(50) | NOT NULL | — | 倉庫コード（集計時コピー） |
| `product_id` | bigint | NOT NULL | — | FK → products.id |
| `product_code` | varchar(50) | NOT NULL | — | 商品コード（集計時コピー） |
| `product_name` | varchar(200) | NOT NULL | — | 商品名（集計時コピー） |
| `unit_type` | varchar(10) | NOT NULL | — | 荷姿 |
| `total_quantity` | bigint | NOT NULL | — | 在庫数量合計（荷姿単位のまま集計。バラ換算は `daily_summary_records` で実施） |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |

**制約**:
- `UNIQUE (business_date, warehouse_id, product_id, unit_type)`

---

### `daily_summary_records`（日次集計レコード）

日替処理で自動生成。対象営業日の倉庫ごとの業務実績サマリーを1レコードにまとめて保持する。

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `business_date` | date | NOT NULL | — | 対象営業日 |
| `warehouse_id` | bigint | NOT NULL | — | FK → warehouses.id |
| `warehouse_code` | varchar(50) | NOT NULL | — | 倉庫コード（集計時コピー） |
| `inbound_count` | int | NOT NULL | 0 | 入庫完了件数 |
| `inbound_line_count` | int | NOT NULL | 0 | 入庫完了明細件数 |
| `inbound_quantity_total` | bigint | NOT NULL | 0 | 入庫数量合計（バラ換算） |
| `outbound_count` | int | NOT NULL | 0 | 出荷完了件数 |
| `outbound_line_count` | int | NOT NULL | 0 | 出荷完了明細件数 |
| `outbound_quantity_total` | bigint | NOT NULL | 0 | 出荷数量合計（バラ換算） |
| `return_quantity_total` | int | NOT NULL | 0 | 返品数量合計（バラ換算） |
| `return_count` | int | NOT NULL | 0 | 返品件数 |
| `inventory_quantity_total` | bigint | NOT NULL | 0 | 在庫総数量（バラ換算。営業日末時点） |
| `unreceived_count` | int | NOT NULL | 0 | 未入荷件数（累積） |
| `unshipped_count` | int | NOT NULL | 0 | 未出荷件数（累積） |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |

**制約**:
- `UNIQUE (business_date, warehouse_id)`

---

### `unreceived_list_records`（未入荷リスト確定）

日替処理で自動生成。入荷予定日が対象営業日以前で入庫完了していない入荷予定を記録する。

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `batch_business_date` | date | NOT NULL | — | 日替処理の対象営業日 |
| `inbound_slip_id` | bigint | NOT NULL | — | FK → inbound_slips.id |
| `slip_number` | varchar(50) | NOT NULL | — | 伝票番号（コピー） |
| `planned_date` | date | NOT NULL | — | 入荷予定日（コピー） |
| `warehouse_code` | varchar(50) | NOT NULL | — | 倉庫コード（コピー） |
| `partner_code` | varchar(50) | NULL | — | 仕入先コード（コピー） |
| `partner_name` | varchar(200) | NULL | — | 仕入先名（コピー） |
| `product_code` | varchar(50) | NOT NULL | — | 商品コード（コピー） |
| `product_name` | varchar(200) | NOT NULL | — | 商品名（コピー） |
| `unit_type` | varchar(10) | NOT NULL | — | 荷姿（コピー） |
| `planned_qty` | int | NOT NULL | — | 入荷予定数（コピー） |
| `current_status` | varchar(30) | NOT NULL | — | 記録時点のステータス（コピー） |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |

**インデックス**:
- `INDEX (batch_business_date, warehouse_code)`

---

### `unshipped_list_records`（未出荷リスト確定）

日替処理で自動生成。出荷予定日が対象営業日以前で出荷完了していない受注を記録する。

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `batch_business_date` | date | NOT NULL | — | 日替処理の対象営業日 |
| `outbound_slip_id` | bigint | NOT NULL | — | FK → outbound_slips.id |
| `slip_number` | varchar(50) | NOT NULL | — | 伝票番号（コピー） |
| `planned_date` | date | NOT NULL | — | 出荷予定日（コピー） |
| `warehouse_code` | varchar(50) | NOT NULL | — | 倉庫コード（コピー） |
| `partner_code` | varchar(50) | NULL | — | 出荷先コード（コピー） |
| `partner_name` | varchar(200) | NULL | — | 出荷先名（コピー） |
| `product_code` | varchar(50) | NOT NULL | — | 商品コード（コピー） |
| `product_name` | varchar(200) | NOT NULL | — | 商品名（コピー） |
| `unit_type` | varchar(10) | NOT NULL | — | 荷姿（コピー） |
| `ordered_qty` | int | NOT NULL | — | 受注数量（コピー） |
| `current_status` | varchar(30) | NOT NULL | — | 記録時点のステータス（コピー） |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |

**インデックス**:
- `INDEX (batch_business_date, warehouse_code)`

---

## バックアップ系

日替処理の「トランデータバックアップ」ステップで、ステータスが完了状態かつ2か月以上前のトランザクションデータを複製する。元テーブルとスキーマを同一とし、`_backup` サフィックスを付与する。

### バックアップ対象テーブル一覧

| バックアップテーブル名 | 元テーブル | バックアップ条件 |
|----------------------|-----------|----------------|
| `inbound_slips_backup` | `inbound_slips` | `status = 'STORED'` かつ `updated_at < 現在 - 2ヶ月` |
| `inbound_slip_lines_backup` | `inbound_slip_lines` | 親の `inbound_slips` がバックアップ対象 |
| `outbound_slips_backup` | `outbound_slips` | `status = 'SHIPPED'` かつ `updated_at < 現在 - 2ヶ月` |
| `outbound_slip_lines_backup` | `outbound_slip_lines` | 親の `outbound_slips` がバックアップ対象 |
| `inventory_movements_backup` | `inventory_movements` | `executed_at < 現在 - 2ヶ月` |

### バックアップテーブルの追加カラム

元テーブルの全カラムに加え、以下のカラムを追加する。

| カラム名 | 型 | 説明 |
|---------|-----|------|
| `backed_up_at` | timestamptz NOT NULL DEFAULT now() | バックアップ実行日時 |
| `batch_execution_log_id` | bigint NOT NULL | FK → batch_execution_logs.id（どの日替処理でバックアップされたか） |

### バックアップ処理の方針

- **複製方式**: `INSERT INTO xxx_backup SELECT ... FROM xxx WHERE [条件]`
- **元データの扱い**: バックアップ後も元テーブルのデータは削除しない（参照整合性を維持）
- **保持期間**: 無期限（Azure PostgreSQL の自動バックアップとは別の業務データアーカイブ）
- **重複防止**: 既にバックアップ済みのレコードは `ON CONFLICT DO NOTHING` で重複挿入をスキップ（ただし元テーブルには `backed_up_at` カラムなし。バックアップテーブル側の `id` + `batch_execution_log_id` の一意制約で管理）

### バックアップテーブルのDDL定義

各バックアップテーブルは元テーブルの全カラム + 追加2カラムで構成する。

#### 共通の追加カラム
| カラム名 | 型 | 説明 |
|---------|-----|------|
| `backed_up_at` | timestamptz NOT NULL DEFAULT now() | バックアップ実行日時 |
| `batch_execution_log_id` | bigint NOT NULL FK → batch_execution_logs.id | バッチ実行ログID |

#### 制約定義
| テーブル | PK | UNIQUE制約 |
|---------|-----|-----------|
| `inbound_slips_backup` | `id`（元テーブルのPKをそのまま使用、autoincrement不要） | `UNIQUE (id, batch_execution_log_id)` |
| `inbound_slip_lines_backup` | 同上 | `UNIQUE (id, batch_execution_log_id)` |
| `outbound_slips_backup` | 同上 | `UNIQUE (id, batch_execution_log_id)` |
| `outbound_slip_lines_backup` | 同上 | `UNIQUE (id, batch_execution_log_id)` |
| `inventory_movements_backup` | 同上 | `UNIQUE (id, batch_execution_log_id)` |

#### DDL例（inbound_slips_backup）
```sql
CREATE TABLE inbound_slips_backup (
    -- 元テーブル（inbound_slips）の全カラムをそのまま定義
    -- ※ id は bigint NOT NULL（bigserialではない）
    -- 追加カラム
    backed_up_at timestamptz NOT NULL DEFAULT now(),
    batch_execution_log_id bigint NOT NULL REFERENCES batch_execution_logs(id),
    -- 制約
    UNIQUE (id, batch_execution_log_id)
);
```

> 他4テーブルも同様の構造。元テーブルのカラム定義は data-model/03-transaction-tables.md を参照。
