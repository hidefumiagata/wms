# データモデル定義 — 概要・設計方針

## 設計方針

| 項目 | 内容 |
|------|------|
| **RDBMS** | Azure Database for PostgreSQL Flexible Server |
| **文字コード** | UTF-8 |
| **タイムゾーン** | Asia/Tokyo（アプリ層で変換。DB保存はUTC） |
| **主キー** | 全テーブル `id bigserial PRIMARY KEY`（サロゲートキー） |
| **論理削除** | マスタテーブルは `is_active boolean` フラグで管理。物理削除は行わない |
| **スキーマ管理** | Flyway マイグレーション |

---

## 命名規則

| 対象 | ルール | 例 |
|------|--------|-----|
| テーブル名 | スネークケース・複数形 | `products`, `inbound_slips` |
| カラム名 | スネークケース | `product_code`, `created_at` |
| 外部キー | `{参照テーブル単数形}_id` | `product_id`, `warehouse_id` |
| フラグ系 | `is_xxx` または `xxx_flag` | `is_active`, `lot_manage_flag` |
| 日時 | `xxx_at` (timestamp with time zone) | `created_at`, `shipped_at` |
| 日付 | `xxx_date` (date) | `planned_date`, `business_date` |
| 実行者 | `xxx_by` (bigint FK → users.id) | `created_by`, `executed_by` |

---

## 共通カラムパターン

### マスタテーブル共通カラム

| カラム名 | 型 | 説明 |
|---------|-----|------|
| `id` | bigserial | PK |
| `is_active` | boolean NOT NULL DEFAULT true | 有効/無効フラグ（論理削除） |
| `created_at` | timestamptz NOT NULL DEFAULT now() | 作成日時 |
| `created_by` | bigint FK → users.id | 作成者 |
| `updated_at` | timestamptz NOT NULL DEFAULT now() | 更新日時 |
| `updated_by` | bigint FK → users.id | 更新者 |

### トランザクションテーブル共通カラム

| カラム名 | 型 | 説明 |
|---------|-----|------|
| `id` | bigserial | PK |
| `created_at` | timestamptz NOT NULL DEFAULT now() | 作成日時 |
| `created_by` | bigint FK → users.id | 作成者 |
| `updated_at` | timestamptz NOT NULL DEFAULT now() | 最終更新日時 |
| `updated_by` | bigint FK → users.id | 最終更新者 |

### マスタ情報のコピー保持

トランザクションデータ（入荷・出荷等）は、マスタIDの外部キーに加え、登録時点の名称等をコピーカラムとして保持する。マスタの変更・無効化後もトランデータは登録時の情報を維持するため。

```
-- 例：入荷明細における商品情報の保持
product_id      bigint FK → products.id  -- 参照用
product_code    varchar NOT NULL          -- 登録時コピー
product_name    varchar NOT NULL          -- 登録時コピー
```

---

## 在庫管理の粒度

在庫は以下の5軸の組み合わせで1レコードを管理する。

| 軸 | カラム | 備考 |
|----|--------|------|
| ロケーション | `location_id` | 必須 |
| 商品 | `product_id` | 必須 |
| 荷姿 | `unit_type` | CASE / BALL / PIECE |
| ロット番号 | `lot_number` | ロット管理フラグOFFの場合 NULL |
| 期限日 | `expiry_date` | 賞味/使用期限管理フラグOFFの場合 NULL |

> PostgreSQL 15 以降の `NULLS NOT DISTINCT` を使用し、NULL を含む5軸の一意制約を実現する。

---

## 荷姿（unit_type）の定義

| 値 | 意味 | 収容上限（システムパラメータ） |
|----|------|-------------------------------|
| `CASE` | ケース | `LOCATION_CAPACITY_CASE`（デフォルト: 1） |
| `BALL` | ボール | `LOCATION_CAPACITY_BALL`（デフォルト: 6） |
| `PIECE` | バラ | `LOCATION_CAPACITY_PIECE`（デフォルト: 100） |

---

## テーブル一覧

### マスタ系

| テーブル名 | 論理名 | 説明 |
|-----------|--------|------|
| `products` | 商品マスタ | 商品情報。ロット・期限管理フラグを持つ |
| `partners` | 取引先マスタ | 仕入先・出荷先・両方の区分あり |
| `warehouses` | 倉庫マスタ | 倉庫の基本情報 |
| `buildings` | 棟マスタ | 倉庫配下の棟 |
| `areas` | エリアマスタ | 棟配下のエリア。在庫/入荷/出荷/返品の種別あり |
| `locations` | ロケーションマスタ | エリア配下の棚番。在庫管理の最小単位 |
| `users` | ユーザーマスタ | ログインユーザー。ロール・ロック状態を管理 |
| `refresh_tokens` | リフレッシュトークン | JWT リフレッシュトークン管理 |
| `system_parameters` | システムパラメータ | ロケーション収容上限等の可変設定値 |

### トランザクション系（入荷）

| テーブル名 | 論理名 | 説明 |
|-----------|--------|------|
| `inbound_slips` | 入荷ヘッダ | 入荷予定の伝票ヘッダ |
| `inbound_slip_lines` | 入荷明細 | 商品明細。検品数・入庫先ロケーション等を保持 |

### トランザクション系（出荷）

| テーブル名 | 論理名 | 説明 |
|-----------|--------|------|
| `outbound_slips` | 出荷ヘッダ | 受注の伝票ヘッダ |
| `outbound_slip_lines` | 出荷明細 | 商品明細 |
| `picking_instructions` | ピッキング指示ヘッダ | 複数受注をまとめたピッキング指示 |
| `picking_instruction_lines` | ピッキング指示明細 | ロケーション単位のピッキング明細。出荷明細の分割ピッキングに対応 |

### トランザクション系（在庫）

| テーブル名 | 論理名 | 説明 |
|-----------|--------|------|
| `inventories` | 在庫 | 現在在庫数量。5軸（ロケーション・商品・荷姿・ロット・期限日）で管理 |
| `inventory_movements` | 在庫変動履歴 | 入庫・出庫・移動・ばらし・訂正・棚卸の全変動履歴 |
| `stocktake_headers` | 棚卸ヘッダ | 棚卸セッションのヘッダ |
| `stocktake_lines` | 棚卸明細 | ロケーション・商品単位の実数と差異 |

### バッチ・集計系

| テーブル名 | 論理名 | 説明 |
|-----------|--------|------|
| `business_date` | 営業日管理 | システムの現在営業日（単一レコード） |
| `batch_execution_logs` | バッチ実行履歴 | 日替処理の実行履歴と各ステップの結果 |
| `inbound_summaries` | 入荷実績サマリー | 営業日別の入荷集計 |
| `outbound_summaries` | 出荷実績サマリー | 営業日別の出荷集計 |
| `inventory_snapshots` | 在庫スナップショット | 営業日末時点の在庫数量サマリー |
| `unreceived_list_records` | 未入荷リスト（確定） | 日替処理で確定した未入荷リスト |
| `unshipped_list_records` | 未出荷リスト（確定） | 日替処理で確定した未出荷リスト |

### バックアップ系

| テーブル名 | 論理名 | 説明 |
|-----------|--------|------|
| `inbound_slips_backup` | 入荷ヘッダバックアップ | 完了済み・2か月超の入荷ヘッダのアーカイブ |
| `inbound_slip_lines_backup` | 入荷明細バックアップ | 同上・明細 |
| `outbound_slips_backup` | 出荷ヘッダバックアップ | 完了済み・2か月超の出荷ヘッダのアーカイブ |
| `outbound_slip_lines_backup` | 出荷明細バックアップ | 同上・明細 |
| `inventory_movements_backup` | 在庫変動履歴バックアップ | 完了済み・2か月超の変動履歴のアーカイブ |
