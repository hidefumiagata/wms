# データベースアーキテクチャ

## DB選定

| 項目 | 内容 |
|------|------|
| **DBMS** | PostgreSQL |
| **ホスティング** | Azure Database for PostgreSQL Flexible Server（B1ms） |
| **マイグレーション** | Flyway |

## 設計方針

### 削除方式

| データ種別 | 削除方式 | 詳細 |
|-----------|---------|------|
| **トランザクションデータ** | 物理削除 ＋ 履歴テーブルコピー | バッチ日替処理でトランテーブルから履歴テーブルへコピー後に物理削除。履歴検索はトランテーブルと履歴テーブルの両方を検索。 |
| **マスタデータ** | 論理削除 | `deleted_at` カラムで管理 |

**理由：** トランテーブルを常に小さく保ち性能を担保するため。大規模WMS（SAP等）でも採用されている実績ある方式。

### ロック方式

| 対象 | ロック方式 | 詳細 |
|------|-----------|------|
| **在庫引当処理** | 悲観的ロック（`SELECT FOR UPDATE`） | 引当処理中は在庫レコードを行ロック。他業務からのアクセスをブロック。 |
| **その他更新競合リスクのあるテーブル** | 楽観的ロック（`version` カラム） | 在庫テーブル等に `version` カラムを付与 |

```java
// Spring Data JPAでの悲観的ロック実装
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM Stock s WHERE s.locationId = :locationId AND s.productId = :productId")
Optional<Stock> findByLocationIdAndProductIdForUpdate(
    @Param("locationId") Long locationId,
    @Param("productId") Long productId
);
```

### 監査カラム

全テーブルに以下のカラムを付与する。

| カラム名 | 型 | 説明 |
|---------|---|------|
| `created_at` | TIMESTAMP WITH TIME ZONE | 作成日時 |
| `created_by` | VARCHAR | 作成者（ユーザID） |
| `updated_at` | TIMESTAMP WITH TIME ZONE | 最終更新日時 |
| `updated_by` | VARCHAR | 最終更新者（ユーザID） |

Spring Data JPA の `@EntityListeners(AuditingEntityListener.class)` で自動設定する。

### PK採番

| 方式 | 採用 |
|------|------|
| PostgreSQL `BIGSERIAL`（シーケンス） | ✅ 採用 |
| UUID | ✗（JOINコストが高い） |
| 採番テーブル | ✗（ボトルネックになりやすい） |

業務キー（商品コード・ロケーションコード等）はPKとは別に業務カラムとして管理する。

## テーブル分類

| 分類 | 対象テーブル | 削除方式 |
|------|------------|---------|
| **マスタ系** | 商品・取引先・ロケーション・ユーザ 等 | 論理削除 |
| **トランザクション系（当日）** | 入荷予定・入荷実績・受注・ピッキング・出荷 等 | 物理削除（日替で履歴へ移動） |
| **履歴系** | 入荷実績履歴・出荷実績履歴・在庫履歴 等 | 物理削除なし（蓄積） |
| **集計系** | 日次在庫集計・入荷集計・出荷集計 等 | 物理削除なし（蓄積） |
| **システム管理系** | システムパラメータ（営業日含む） | 更新のみ（削除なし） |

> 詳細なテーブル定義はデータモデル定義書（`docs/data-model/`）で管理する。

## 営業日管理テーブル

営業日（→ 概念の詳細は [01-overall-architecture.md](01-overall-architecture.md) 参照）は `system_parameters` テーブルで一元管理する。

### system_parameters テーブル（概要）

| カラム名 | 型 | 説明 |
|---------|----|------|
| `param_key` | VARCHAR（PK） | パラメータキー（例: `BUSINESS_DATE`） |
| `param_value` | VARCHAR | パラメータ値（例: `2026-03-13`） |
| `description` | VARCHAR | パラメータの説明 |
| `updated_at` | TIMESTAMP WITH TIME ZONE | 最終更新日時 |
| `updated_by` | VARCHAR | 最終更新者（ユーザIDまたはシステム） |

### 営業日パラメータ

| param_key | param_value（例） | 説明 |
|-----------|-----------------|------|
| `BUSINESS_DATE` | `2026-03-13` | 現在の営業日（日替処理で更新） |

> 営業日は日替処理（`POST /api/v1/batch/daily-close`）実行時に翌日付へ更新される。
> アプリ起動時にキャッシュせず、都度DBから取得する（営業日変更が即時反映されるため）。
