# データモデル定義 — マスタ系テーブル

## `products`（商品マスタ）

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `product_code` | varchar(50) | NOT NULL | — | 商品コード。システム全体で一意。登録後変更不可 |
| `product_name` | varchar(200) | NOT NULL | — | 商品名 |
| `product_name_kana` | varchar(200) | NULL | — | 商品名カナ |
| `case_quantity` | int | NOT NULL | — | ケース入数（1ケース＝何ボール） |
| `ball_quantity` | int | NOT NULL | — | ボール入数（1ボール＝何バラ） |
| `barcode` | varchar(100) | NULL | — | バーコード / JANコード |
| `storage_condition` | varchar(20) | NOT NULL | — | 保管条件: `AMBIENT`(常温) / `REFRIGERATED`(冷蔵) / `FROZEN`(冷凍) |
| `is_hazardous` | boolean | NOT NULL | false | 危険物フラグ |
| `lot_manage_flag` | boolean | NOT NULL | false | ロット管理フラグ。ONの場合、入荷時にロット番号必須 |
| `expiry_manage_flag` | boolean | NOT NULL | false | 賞味/使用期限管理フラグ。ONの場合、入荷時に期限日必須 |
| `shipment_stop_flag` | boolean | NOT NULL | false | 出荷禁止フラグ。ONの場合、出荷指示・ピッキングで選択不可 |
| `is_active` | boolean | NOT NULL | true | 有効/無効フラグ |
| `version` | integer | NOT NULL | 0 | 楽観的ロック用バージョン番号（JPA `@Version`） |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |
| `created_by` | bigint | NOT NULL | — | 作成者（FK → users.id） |
| `updated_at` | timestamptz | NOT NULL | now() | 更新日時 |
| `updated_by` | bigint | NOT NULL | — | 更新者（FK → users.id） |

**制約・インデックス**:
- `UNIQUE (product_code)`
- `lot_manage_flag` または `expiry_manage_flag` が true の場合、その商品の在庫が存在する間は変更不可（アプリ層で制御）

---

## `partners`（取引先マスタ）

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `partner_code` | varchar(50) | NOT NULL | — | 取引先コード。システム全体で一意。登録後変更不可 |
| `partner_name` | varchar(200) | NOT NULL | — | 取引先名 |
| `partner_name_kana` | varchar(200) | NULL | — | 取引先名カナ |
| `partner_type` | varchar(20) | NOT NULL | — | 種別: `SUPPLIER`(仕入先) / `CUSTOMER`(出荷先) / `BOTH`(両方) |
| `address` | varchar(500) | NULL | — | 住所 |
| `phone` | varchar(50) | NULL | — | 電話番号 |
| `contact_person` | varchar(100) | NULL | — | 担当者名 |
| `email` | varchar(200) | NULL | — | メールアドレス |
| `is_active` | boolean | NOT NULL | true | 有効/無効フラグ |
| `version` | integer | NOT NULL | 0 | 楽観的ロック用バージョン番号（JPA `@Version`） |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |
| `created_by` | bigint | NOT NULL | — | 作成者（FK → users.id） |
| `updated_at` | timestamptz | NOT NULL | now() | 更新日時 |
| `updated_by` | bigint | NOT NULL | — | 更新者（FK → users.id） |

**制約**:
- `UNIQUE (partner_code)`
- `partner_type IN ('SUPPLIER', 'CUSTOMER', 'BOTH')`
- 入荷予定の仕入先として選択可能: `partner_type IN ('SUPPLIER', 'BOTH')`
- 受注の出荷先として選択可能: `partner_type IN ('CUSTOMER', 'BOTH')`

---

## `warehouses`（倉庫マスタ）

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `warehouse_code` | varchar(50) | NOT NULL | — | 倉庫コード。システム全体で一意。登録後変更不可 |
| `warehouse_name` | varchar(200) | NOT NULL | — | 倉庫名 |
| `warehouse_name_kana` | varchar(200) | NULL | — | 倉庫名カナ |
| `address` | varchar(500) | NULL | — | 住所 |
| `is_active` | boolean | NOT NULL | true | 有効/無効フラグ |
| `version` | integer | NOT NULL | 0 | 楽観的ロック用バージョン番号（JPA `@Version`） |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |
| `created_by` | bigint | NOT NULL | — | 作成者（FK → users.id） |
| `updated_at` | timestamptz | NOT NULL | now() | 更新日時 |
| `updated_by` | bigint | NOT NULL | — | 更新者（FK → users.id） |

**制約**:
- `UNIQUE (warehouse_code)`
- 在庫が存在する倉庫は無効化不可（アプリ層で制御）

---

## `buildings`（棟マスタ）

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `building_code` | varchar(10) | NOT NULL | — | 棟コード（A, B, C 等）。倉庫内で一意。登録後変更不可 |
| `building_name` | varchar(200) | NOT NULL | — | 棟名称 |
| `warehouse_id` | bigint | NOT NULL | — | FK → warehouses.id |
| `is_active` | boolean | NOT NULL | true | 有効/無効フラグ |
| `version` | integer | NOT NULL | 0 | 楽観的ロック用バージョン番号（JPA `@Version`） |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |
| `created_by` | bigint | NOT NULL | — | 作成者（FK → users.id） |
| `updated_at` | timestamptz | NOT NULL | now() | 更新日時 |
| `updated_by` | bigint | NOT NULL | — | 更新者（FK → users.id） |

**制約**:
- `UNIQUE (warehouse_id, building_code)`
- 配下にエリアが存在する棟は無効化不可（アプリ層で制御）

---

## `areas`（エリアマスタ）

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `area_code` | varchar(20) | NOT NULL | — | エリアコード。棟内で一意。登録後変更不可 |
| `area_name` | varchar(200) | NOT NULL | — | エリア名称 |
| `warehouse_id` | bigint | NOT NULL | — | FK → warehouses.id（冗長保持） |
| `building_id` | bigint | NOT NULL | — | FK → buildings.id |
| `storage_condition` | varchar(20) | NOT NULL | — | 保管条件: `AMBIENT` / `REFRIGERATED` / `FROZEN` |
| `area_type` | varchar(20) | NOT NULL | — | エリア種別: `STOCK`(在庫) / `INBOUND`(入荷) / `OUTBOUND`(出荷) / `RETURN`(返品) |
| `is_active` | boolean | NOT NULL | true | 有効/無効フラグ |
| `version` | integer | NOT NULL | 0 | 楽観的ロック用バージョン番号（JPA `@Version`） |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |
| `created_by` | bigint | NOT NULL | — | 作成者（FK → users.id） |
| `updated_at` | timestamptz | NOT NULL | now() | 更新日時 |
| `updated_by` | bigint | NOT NULL | — | 更新者（FK → users.id） |

**制約**:
- `UNIQUE (building_id, area_code)`
- 配下にロケーションが存在するエリアは無効化不可（アプリ層で制御）
- `INBOUND` / `OUTBOUND` / `RETURN` エリアにはロケーションを1件のみ登録可能（アプリ層で制御）

---

## `locations`（ロケーションマスタ）

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `location_code` | varchar(50) | NOT NULL | — | ロケーションコード。倉庫内で一意。登録後変更不可。在庫エリアは `棟-フロア-エリア-棚-段-並び`形式（例: `A-01-A-01-01-01`） |
| `location_name` | varchar(200) | NULL | — | ロケーション名称（任意の説明的な名前） |
| `warehouse_id` | bigint | NOT NULL | — | FK → warehouses.id（冗長保持） |
| `area_id` | bigint | NOT NULL | — | FK → areas.id |
| `is_active` | boolean | NOT NULL | true | 有効/無効フラグ |
| `is_stocktaking_locked` | boolean | NOT NULL | false | 棚卸ロック中フラグ。棚卸開始時にtrueにセットし、確定時にfalseに戻す |
| `version` | integer | NOT NULL | 0 | 楽観的ロック用バージョン番号（JPA `@Version`） |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |
| `created_by` | bigint | NOT NULL | — | 作成者（FK → users.id） |
| `updated_at` | timestamptz | NOT NULL | now() | 更新日時 |
| `updated_by` | bigint | NOT NULL | — | 更新者（FK → users.id） |

**制約・インデックス**:
- `UNIQUE (warehouse_id, location_code)`
- `INDEX (warehouse_id, location_code)` — 前方一致絞り込みに使用
- 在庫が存在するロケーションは無効化不可（アプリ層で制御）
- 棚卸中のロケーションは無効化不可（アプリ層で制御）

---

## `users`（ユーザーマスタ）

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `user_code` | varchar(50) | NOT NULL | — | ユーザーコード（ログインID）。システム全体で一意。登録後変更不可 |
| `full_name` | varchar(200) | NOT NULL | — | 氏名 |
| `email` | varchar(200) | NOT NULL | — | メールアドレス（通知・連絡先。ログインIDとは別） |
| `password_hash` | varchar(255) | NOT NULL | — | BCrypt ハッシュ化パスワード |
| `role` | varchar(30) | NOT NULL | — | ロール: `SYSTEM_ADMIN` / `WAREHOUSE_MANAGER` / `WAREHOUSE_STAFF` / `VIEWER` |
| `is_active` | boolean | NOT NULL | true | 有効/無効フラグ |
| `version` | integer | NOT NULL | 0 | 楽観的ロック用バージョン番号（JPA `@Version`） |
| `password_change_required` | boolean | NOT NULL | true | 初回ログインフラグ。ONの間はパスワード変更を強制 |
| `failed_login_count` | int | NOT NULL | 0 | 連続ログイン失敗回数。ログイン成功時にリセット |
| `locked` | boolean | NOT NULL | false | アカウントロックフラグ（連続5回失敗でON） |
| `locked_at` | timestamptz | NULL | — | ロック発生日時 |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |
| `created_by` | bigint | NULL | — | 作成者（FK → users.id。最初のSYSTEM_ADMINのみNULL可） |
| `updated_at` | timestamptz | NOT NULL | now() | 更新日時 |
| `updated_by` | bigint | NULL | — | 更新者（FK → users.id） |

**制約**:
- `UNIQUE (user_code)`
- `role IN ('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')`
- 自身のロール変更・無効化は不可（アプリ層で制御）

---

## `refresh_tokens`（リフレッシュトークン）

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `user_id` | bigint | NOT NULL | — | FK → users.id |
| `token_hash` | varchar(255) | NOT NULL | — | トークンのBCryptハッシュ |
| `expires_at` | timestamptz | NOT NULL | — | トークン有効期限 |
| `created_at` | timestamptz | NOT NULL | now() | 発行日時 |

**制約・インデックス**:
- `UNIQUE (user_id)`
- `INDEX (token_hash)`
- リフレッシュ時にトークンローテーション（旧トークン削除・新トークン発行）

---

## `system_parameters`（システムパラメータ）

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `param_key` | varchar(100) | NOT NULL | — | パラメータキー |
| `param_value` | varchar(500) | NOT NULL | — | パラメータ値 |
| `default_value` | varchar(500) | NOT NULL | — | デフォルト値（画面表示用） |
| `display_name` | varchar(200) | NOT NULL | — | 画面表示名 |
| `category` | varchar(50) | NOT NULL | — | カテゴリ（画面グルーピング用） |
| `value_type` | varchar(20) | NOT NULL | — | 値の型: `INTEGER` / `STRING` |
| `description` | varchar(500) | NULL | — | 説明 |
| `display_order` | int | NOT NULL | 0 | 画面表示順 |
| `version` | integer | NOT NULL | 0 | 楽観的ロック用バージョン番号（JPA `@Version`） |
| `updated_at` | timestamptz | NOT NULL | now() | 更新日時 |
| `updated_by` | bigint | NULL | — | 更新者（FK → users.id） |

**制約**:
- `UNIQUE (param_key)`

**初期データ（Flyway）**:

| param_key | param_value | default_value | display_name | category | value_type | description |
|-----------|------------|---------------|-------------|----------|-----------|-------------|
| `LOCATION_CAPACITY_CASE` | `1` | `1` | ロケーション収容上限（ケース） | `INVENTORY` | `INTEGER` | 1ロケーションあたりのケース最大数 |
| `LOCATION_CAPACITY_BALL` | `6` | `6` | ロケーション収容上限（ボール） | `INVENTORY` | `INTEGER` | 1ロケーションあたりのボール最大数 |
| `LOCATION_CAPACITY_PIECE` | `100` | `100` | ロケーション収容上限（バラ） | `INVENTORY` | `INTEGER` | 1ロケーションあたりのバラ最大数 |
| `LOGIN_FAILURE_LOCK_COUNT` | `5` | `5` | ログイン失敗ロック回数 | `SECURITY` | `INTEGER` | 連続ログイン失敗でアカウントをロックする回数 |
| `SESSION_TIMEOUT_MINUTES` | `60` | `60` | セッションタイムアウト（分） | `SECURITY` | `INTEGER` | 最終操作からセッションが失効するまでの時間（分） |
| `PASSWORD_RESET_EXPIRY_MINUTES` | `30` | `30` | パスワードリセットリンク有効期限（分） | `SECURITY` | `INTEGER` | パスワードリセットリンクの有効期限（分） |

---

## `password_reset_tokens`（パスワードリセットトークン）

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|-----|------|-----------|------|
| `id` | bigserial | NOT NULL | — | PK |
| `user_id` | bigint | NOT NULL | — | FK → users.id |
| `token_hash` | varchar(256) | NOT NULL | — | リセットトークンのハッシュ値（SHA-256） |
| `expires_at` | timestamptz | NOT NULL | — | 有効期限（発行時刻 + 30分） |
| `created_at` | timestamptz | NOT NULL | now() | 作成日時 |

**制約**:
- `UNIQUE (user_id)` — 同一ユーザーに有効なトークンは1件のみ
- `INDEX (token_hash)` — トークン検索用

> **無効化方式: レコード削除（DELETE）**
> - 新規トークン発行時: 同一ユーザーの既存レコードを DELETE してから INSERT
> - リセット完了時: 使用したトークンレコードを DELETE
> - 使用済み・期限切れトークンはDBに残さない（クリーンアップ不要）
> - 監査証跡はアプリケーションログに記録する
