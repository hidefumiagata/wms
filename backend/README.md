# WMS Backend

Spring Boot 3.4 + Java 21 + PostgreSQL のバックエンドアプリケーション。

## 前提条件

- Java 21
- PostgreSQL 15+
- データベース `wms` が作成済みであること

## 起動

```bash
# 開発モード（デフォルト）
./gradlew bootRun

# プロファイル指定
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
```

## テスト

```bash
# 全テスト実行 + カバレッジレポート
./gradlew test jacocoTestReport

# レポート: build/reports/jacoco/test/html/index.html
```

## Flyway プロファイルとテストデータ

Spring Profile によって Flyway のマイグレーションロケーションが切り替わり、投入されるテストデータの規模が変わる。

### プロファイル一覧

| プロファイル | Flyway ロケーション | 用途 | データ量 |
|------------|-------------------|------|---------|
| `dev`（デフォルト） | `db/migration` | 開発 | スキーマのみ（admin ユーザー + システムパラメータ） |
| `demo` | `db/migration` + `db/testdata/common` + `db/testdata/demo` | ローカルデモ | マスタ + 5日分トランザクション |
| `loadtest` | `db/migration` + `db/testdata/common` + `db/testdata/loadtest` | 負荷テスト | マスタ + 1年分フルデータ |

### ディレクトリ構造

```
src/main/resources/db/
  migration/              # スキーマ定義（V1〜V18）。全プロファイルで実行
  testdata/
    common/               # 共通マスタデータ（R__01〜R__04）
      R__01_master_users.sql        # テストユーザー 4名
      R__02_master_warehouses.sql   # 倉庫・棟・エリア・ロケーション 172件
      R__03_master_partners.sql     # 取引先 20社（仕入先10 + 出荷先10）
      R__04_master_products.sql     # 商品 100品目（常温60/冷蔵25/冷凍15）
    demo/                 # デモ用トランザクション（R__10〜R__12）
      R__10_demo_inbound.sql        # 入荷伝票 20件
      R__11_demo_inventory.sql      # 在庫 53レコード
      R__12_demo_outbound.sql       # 出荷伝票 15件
    loadtest/             # 1年分データ（ジェネレータで生成。gitignore対象）
      R__20_loadtest_inbound.sql
      R__21_loadtest_outbound.sql
      R__22_loadtest_inventory.sql
      R__23_loadtest_daily_summary.sql
```

### 使い方

#### デモモードで起動

```bash
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
```

起動時に Flyway が共通マスタ + デモ用トランザクションを自動投入する。

#### 負荷テストモードで起動

```bash
# 1. SQLファイルを生成（初回のみ。約22MB）
./gradlew generateLoadTestData

# 2. loadtest プロファイルで起動
SPRING_PROFILES_ACTIVE=loadtest ./gradlew bootRun
```

生成されるデータ:
- 入荷伝票: 3,650件（10件/日 x 365日）
- 出荷伝票: 5,475件（15件/日 x 365日）
- 在庫: 100レコード（全品目）
- 日次集計: 365件

#### データのリセット

Flyway の Repeatable migration (`R__` プレフィックス) を使用しているため、SQLファイルの内容を変更すると次回起動時に再実行される。データを完全にリセットしたい場合はデータベースを再作成する。

```bash
dropdb wms && createdb wms
```

### テストデータの設計方針

- **冪等性**: 全 INSERT に `NOT EXISTS` ガードを付与。何度実行しても重複しない
- **FK解決**: ID直書きではなく業務コード（`warehouse_code`, `product_code` 等）で JOIN して FK を解決
- **日付動的生成**: デモ用伝票番号は `CURRENT_DATE` ベースで生成。いつ実行しても「直近5日分」のデータになる
- **loadtest は git 管理外**: 生成物は `.gitignore` で除外。`./gradlew generateLoadTestData` で再生成

## Gradle タスク一覧

| タスク | 説明 |
|-------|------|
| `bootRun` | アプリケーション起動 |
| `test` | 全テスト実行 |
| `jacocoTestReport` | カバレッジレポート生成 |
| `openApiGenerate` | OpenAPI からコード生成 |
| `generateLoadTestData` | 1年分テストデータSQL生成 |
| `checkstyleMain` | Checkstyle 実行 |
| `spotbugsMain` | SpotBugs 実行 |
