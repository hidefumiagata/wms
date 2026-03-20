# テスト計画書

## 1. テスト戦略概要

| 項目 | 内容 |
|------|------|
| テストレベル | 単体テスト、結合テスト、E2Eテストの3種類 |
| 自動化方針 | 全テストレベルで自動化を実施 |
| テスト実装パターン | [architecture-design/04-backend-architecture.md](../architecture-design/04-backend-architecture.md) を参照（SSOTルール） |
| CI/CDでのテスト実行 | [architecture-design/12-development-deploy.md](../architecture-design/12-development-deploy.md) を参照（SSOTルール） |

## 2. テストレベル定義

### 2.1 単体テスト

| 項目 | 内容 |
|------|------|
| 対象 | Service層、Repository層、ユーティリティクラス、フロントエンドComposable |
| ツール（バックエンド） | JUnit 5 + Mockito |
| ツール（フロントエンド） | Vitest + Vue Test Utils |
| 外部依存 | 全てモック/スタブで代替。DB、Blob Storage、Azure Communication Services等の外部サービスへの実接続なし |
| カバレッジ目標 | C0（命令網羅）100%、C1（分岐網羅）100%。C2（条件網羅）は対象外（条件組み合わせ爆発のコストに見合わないため） |
| 完了基準 | 全テストケース100%合格 + カバレッジ目標達成 + テスト品質ルール遵守 |
| テスト仕様書 | 不要（コードベースでテストを管理） |

### 2.2 結合テスト

| 項目 | 内容 |
|------|------|
| 対象 | フロントエンド + バックエンドAPI + PostgreSQLを結合したシナリオテスト |
| ツール | Playwright |
| DB | 実DBを使用（Testcontainers or Docker Compose PostgreSQL） |
| テストデータ | Flywayで投入（1年分のシナリオデータを自動生成） |
| 完了基準 | 全シナリオテスト100%合格 |
| テスト仕様書 | 必要（シナリオベース。テンプレートは [01-test-template.md](./01-test-template.md) を使用） |

### 2.3 E2Eテスト

| 項目 | 内容 |
|------|------|
| 対象 | 本番相当環境（Azure Container Apps + PostgreSQL Flexible Server + Blob Storage）での全シナリオ実行 |
| ツール | Playwright（結合テストと同一シナリオを使用） |
| テストデータ | Flywayで投入（結合テストと同一データ） |
| 完了基準 | 結合テストの全シナリオが本番相当環境で100%合格 |
| テスト仕様書 | 結合テストと共通（同一シナリオを異なる環境で実行） |

## 3. テスト環境

### 3.1 環境マトリクス

| テストレベル | ローカル | CI（GitHub Actions） | dev（Azure） |
|------------|:-------:|:------------------:|:-----------:|
| 単体テスト | ○ | ○ | — |
| 結合テスト | ○ | ○ | ○ |
| E2Eテスト | — | — | ○ |

### 3.2 ローカル環境

#### 単体テスト
- バックエンド: `./gradlew test`（モック/スタブのみ、外部依存なし）
- フロントエンド: `npm run test:run`

#### 結合テスト（ローカル）
ローカルにPostgreSQLをインストールできない前提で、以下の方式で実現:

**方式: Testcontainers（Docker経由のPostgreSQL自動起動）**

Testcontainersを使用し、テスト実行時にDockerコンテナとしてPostgreSQLを自動起動・自動破棄する。
ローカルにPostgreSQLをインストールする必要がなく、Docker Desktopのみが前提条件。

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class InboundIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("wms_test")
        .withUsername("wms_test")
        .withPassword("wms_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

Flywayが自動的にマイグレーション+テストデータ投入を実行。
Playwrightはローカル起動したSpring Boot + Vue devサーバーに対して実行。

```
[Docker] PostgreSQL ← Testcontainers自動起動
    ↑
[ローカル] Spring Boot (ランダムポート)
    ↑
[ローカル] Vue dev server (localhost:5173)
    ↑
[Playwright] ブラウザ自動操作
```

### 3.3 CI環境（GitHub Actions）

```yaml
# ci.yml に結合テストジョブを追加
integration-test:
  runs-on: ubuntu-latest
  services:
    postgres:
      image: postgres:16-alpine
      env:
        POSTGRES_DB: wms_test
        POSTGRES_USER: wms_test
        POSTGRES_PASSWORD: wms_test
      ports: ['5432:5432']
      options: >-
        --health-cmd pg_isready
        --health-interval 10s
        --health-timeout 5s
        --health-retries 5
  steps:
    - uses: actions/checkout@v4
    - name: Setup JDK 21
      uses: actions/setup-java@v4
      with: { distribution: temurin, java-version: '21' }
    - name: Setup Node 20
      uses: actions/setup-node@v4
      with: { node-version: '20' }
    - name: Build & Start Backend
      run: ./gradlew bootRun &
    - name: Build & Start Frontend
      run: cd frontend && npm ci && npm run dev &
    - name: Install Playwright
      run: npx playwright install --with-deps chromium
    - name: Run Integration Tests
      run: npx playwright test
```

### 3.4 dev環境（E2Eテスト）

Azure Container Apps + PostgreSQL Flexible Serverの本番相当環境でPlaywrightを実行。
GitHub Actionsの手動ワークフロー（workflow_dispatch）で実行。

## 4. テストデータ管理

### 4.1 テストデータ投入方式

FlywayのRepeatable Migration（`R__`プレフィックス）でテストデータを管理:

```
src/main/resources/db/testdata/
├── R__001_master_data.sql         ← マスタデータ（倉庫・棟・エリア・ロケーション・商品・取引先・ユーザー）
├── R__002_inbound_data.sql        ← 入荷データ（1年分、各ステータスのデータ含む）
├── R__003_outbound_data.sql       ← 出荷データ（1年分、各ステータスのデータ含む）
├── R__004_inventory_data.sql      ← 在庫データ（引当済み含む）
├── R__005_stocktake_data.sql      ← 棚卸データ
├── R__006_allocation_data.sql     ← 引当データ（ばらし指示含む）
├── R__007_return_data.sql         ← 返品データ
├── R__008_batch_data.sql          ← バッチ実行履歴・集計データ
└── R__009_interface_data.sql      ← I/F取込履歴データ
```

### 4.2 1年分データの自動生成方針

| データ種別 | 件数目安 | 内容 |
|----------|---------|------|
| 入荷伝票 | 3,650件（10件/日 x 365日） | PLANNED/CONFIRMED/STORED/CANCELLED の各ステータス分布 |
| 出荷伝票 | 7,300件（20件/日 x 365日） | ORDERED〜SHIPPED/CANCELLED の各ステータス分布 |
| 在庫レコード | 5,000件 | 複数倉庫・ロケーション・商品・荷姿の組み合わせ |
| 棚卸 | 12件（月1回 x 12ヶ月） | STARTED/CONFIRMED の分布 |
| 返品伝票 | 365件（1件/日） | 3種別（入荷/在庫/出荷）の分布 |
| バッチ実行履歴 | 365件 | SUCCESS/FAILED の分布 |
| 日次集計レコード | 365件 | 全倉庫分 |

### 4.3 テストデータの環境切替

```yaml
# application-test.yml
spring:
  flyway:
    locations:
      - classpath:db/migration        # 本番マイグレーション
      - classpath:db/testdata          # テストデータ（テスト環境のみ）
```

dev環境・CI環境では `SPRING_PROFILES_ACTIVE=dev,test` でテストデータを有効化。

## 5. カバレッジ目標（SSOT）

> 本セクションがカバレッジ目標のSSOTです。他ドキュメントのカバレッジ記載は本セクションを参照してください。

### 5.1 バックエンド

| レイヤー | C0（命令） | C1（分岐） |
|---------|:--------:|:--------:|
| Service層 | 100% | 100% |
| Controller層 | 100% | 100% |
| Repository層 | 100% | 100% |
| ユーティリティ | 100% | 100% |

> **C2（条件網羅）は対象外。** 複合条件（`A && B && C`）の全組み合わせテストは条件数に対して指数的にケースが増加し、コストに見合わない。代わりにテスト品質ルール（5.3）で業務ロジックの検証品質を担保する。

#### 除外対象
- 自動生成コード（Lombok getter/setter、JPA metamodel等）
- Spring Boot Application クラス（main メソッド）
- DTO record クラス（ロジックなし）

### 5.2 フロントエンド

| 対象 | C0（命令） | C1（分岐） |
|-----|:--------:|:--------:|
| Composable | 100% | 100% |
| ユーティリティ | 100% | 100% |
| Piniaストア | 100% | 100% |

#### 除外対象
- Vueテンプレート（HTML部分）
- ルーティング定義
- 外部ライブラリのラッパー

### 5.3 テスト品質ルール

カバレッジ数値だけでは「意味のないテスト」（カバレッジを稼ぐだけでロジックを検証しないテスト）を防げない。以下のルールを全単体テストに適用する。

#### ルール1: 業務的アサーション必須

全テストメソッドに**最低1つの業務的アサーション**を含むこと。`assertNotNull` のみ、例外が出ないことだけの確認は不可。

```java
// NG: アサーションが甘い
@Test
void createSlip_success() {
    var result = service.create(request);
    assertNotNull(result);  // ← これだけでは不可
}

// OK: 業務的な値を検証
@Test
void createSlip_success() {
    var result = service.create(request);
    assertEquals("PLANNED", result.getStatus());
    assertEquals("SUP-0001", result.getPartnerCode());
    assertEquals(1, result.getLines().size());
    assertEquals(100, result.getLines().get(0).getPlannedQty());
}
```

#### ルール2: 全APIの異常系テスト必須

全APIエンドポイントに対し、以下のHTTPステータスのテストを**網羅的に**含むこと。正常系だけでカバレッジ100%にすることを防ぐ。

| HTTPステータス | テスト内容 | 全API必須 |
|:------------:|----------|:--------:|
| 200/201 | 正常系 | ○ |
| 400 | バリデーションエラー（必須項目未入力、型不正等） | ○ |
| 401 | 未認証（JWTなし/期限切れ） | ○ |
| 403 | 権限不足（対象ロール以外でアクセス） | ○ |
| 404 | リソース未存在（存在しないID指定） | 該当APIのみ |
| 409 | 競合（楽観ロック、重複コード等） | 該当APIのみ |
| 422 | 業務ルール違反 | 該当APIのみ |

#### ルール3: 在庫変動のアサーション必須

在庫変動を伴うAPI（入庫確定、出荷完了、在庫移動、ばらし、棚卸確定、引当実行/解放、返品登録）のテストでは、操作前後の `inventories` テーブルの **`quantity` と `allocated_qty` の増減を明示的にassert** すること。

```java
// OK: 在庫変動を明示的に検証
@Test
void storeInbound_updatesInventory() {
    // 操作前の在庫を記録
    int qtyBefore = getInventoryQuantity(locationId, productId, unitType);
    int allocBefore = getInventoryAllocatedQty(locationId, productId, unitType);

    // 入庫確定実行
    service.store(slipId, storeRequest);

    // 操作後の在庫を検証
    int qtyAfter = getInventoryQuantity(locationId, productId, unitType);
    int allocAfter = getInventoryAllocatedQty(locationId, productId, unitType);
    assertEquals(qtyBefore + 100, qtyAfter);     // quantity が +100
    assertEquals(allocBefore, allocAfter);         // allocated_qty は変化なし
}
```

## 6. テスト完了基準

| テストレベル | 完了基準 |
|------------|---------|
| 単体テスト | 全テストケース100%合格 + C0/C1カバレッジ100%達成（除外対象を除く） + テスト品質ルール（5.3）遵守 |
| 結合テスト | 全シナリオテスト100%合格 |
| E2Eテスト | 結合テストの全シナリオが本番相当環境（dev）で100%合格 |

### リリース判定基準

| 条件 | 必須/推奨 |
|------|---------|
| 単体テスト完了基準達成 | 必須 |
| 結合テスト完了基準達成 | 必須 |
| E2Eテスト完了基準達成 | 必須 |
| 既知の未修正バグなし（Critical/Major） | 必須 |
| 既知の未修正バグなし（Minor） | 推奨 |

## 7. テスト仕様書の作成タイミング

| テストレベル | テスト仕様書 | 作成タイミング |
|------------|-----------|-------------|
| 単体テスト | 不要（テストコード自体が仕様） | 実装と同時 |
| 結合テスト | シナリオテスト仕様書（[01-test-template.md](./01-test-template.md) テンプレート使用） | 各機能の実装完了後 |
| E2Eテスト | 結合テストと共通 | 結合テスト仕様書と同一 |

### テスト観点の入力源

各機能設計書に記載されたテスト観点セクションを入力として、結合テストシナリオに展開する:

| 設計書 | テスト観点 |
|--------|----------|
| BAT-01-daily-close.md | 12ケース |
| IF-01-inbound-plan.md | 40ケース |
| IF-02-order.md | 44ケース |

> テスト観点のSSOTは各機能設計書。テスト仕様書はそれを具体的なシナリオに展開するもの。

## 8. 参照ドキュメント

| ドキュメント | 参照内容 |
|------------|---------|
| [architecture-design/04-backend-architecture.md](../architecture-design/04-backend-architecture.md) | テスト実装パターン（JUnit/Mockito/Testcontainersのコード例） |
| [architecture-design/12-development-deploy.md](../architecture-design/12-development-deploy.md) | CI/CDワークフローのテスト実行ステップ |
| [functional-design/_standard-report.md](../functional-design/_standard-report.md) | PDFテスト方針（PDFBoxでページ数・テキスト検証） |
| functional-design/各設計書 | 個別機能のテスト観点 |
