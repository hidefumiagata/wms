# バックエンドアーキテクチャ

## 技術スタック

| 役割 | 採用技術 | バージョン方針 |
|------|---------|-------------|
| **フレームワーク** | Spring Boot 3.x | 最新安定版 |
| **言語** | Java 21（LTS） | Java 21 |
| **ORM** | Spring Data JPA + Hibernate | Spring Boot同梱 |
| **DBマイグレーション** | Flyway | 最新安定版 |
| **OpenAPI** | Springdoc OpenAPI | 最新安定版 |
| **認証** | Spring Security + JWT（jjwt） | 最新安定版 |
| **バリデーション** | Jakarta Bean Validation | Spring Boot同梱 |
| **ビルドツール** | Gradle | 最新安定版 |
| **単体テスト** | JUnit 5 + Mockito | Spring Boot同梱 |
| **APIテスト** | Spring Boot Test + MockMvc | Spring Boot同梱 |

## アーキテクチャパターン

**標準3層アーキテクチャ** を採用する。

```mermaid
flowchart TD
    REQ([HTTP Request])
    CTL["Controller\nリクエスト受付・レスポンス返却・バリデーション"]
    SVC["Service\nビジネスロジック・トランザクション管理"]
    REP["Repository\nDB アクセス（Spring Data JPA）"]
    DB[("Database\nPostgreSQL（Azure Flexible Server）")]

    REQ --> CTL --> SVC --> REP --> DB
```

## モジュール構成

各業務モジュールは独立したパッケージとして実装する。

### モジュール間の依存ルール

| 呼び出しパターン | 可否 | 例 |
|----------------|------|-----|
| 業務モジュール → `shared` | ✅ | 全モジュールから共通基盤を参照 |
| 業務モジュール → 他の業務モジュールの **Service** | ✅ | `outbound.AllocationService` → `inventory.InventoryService` |
| 業務モジュール → 他の業務モジュールの **Repository** | ✗ | 他モジュールのDBアクセスは必ずService経由 |
| 業務モジュール → 他の業務モジュールの **Controller** | ✗ | Controller間の直接呼び出しは禁止 |

> モジュール間の Service 直接呼び出しを許可する。イベント駆動やFacadeパターンによる間接化は、現時点ではオーバーヘッドが大きいため採用しない。

```
backend/
└── src/main/java/com/wms/
    ├── inbound/        # 入荷管理
    │   ├── controller/
    │   ├── service/
    │   ├── repository/
    │   └── dto/
    ├── inventory/      # 在庫管理
    ├── allocation/     # 在庫引当
    ├── outbound/       # 出荷管理
    ├── master/         # マスタ管理
    ├── report/         # レポート
    ├── batch/          # バッチ処理
    ├── interfacing/    # 外部連携I/F
    ├── system/         # システム共通（営業日取得API等）
    └── shared/         # 共通基盤
        ├── config/     # Spring設定（Security・CORS・OpenAPI等）
        ├── exception/  # 例外ハンドリング
        ├── logging/    # ロギング
        ├── security/   # JWT認証
        └── util/       # ユーティリティ
```

## API設計方針

- RESTful API
- ベースパス：`/api/v1/`
- OpenAPIドキュメント：`/swagger-ui.html`（Springdoc自動生成）
- レスポンス形式：JSON統一
- リクエスト Content-Type：`application/json`

### ページネーション共通仕様

一覧取得APIはすべて **ページベース** のページネーションを採用する（Spring Data の `Pageable` を利用）。

#### リクエストパラメータ

| パラメータ | 型 | デフォルト | 制約 | 説明 |
|-----------|-----|-----------|------|------|
| `page` | Integer | `0` | 0以上 | ページ番号（0始まり） |
| `size` | Integer | `20` | 1〜100 | 1ページあたりの件数 |
| `sort` | String | API別に定義 | `{field},{asc\|desc}` | ソート条件 |

#### レスポンス形式

```json
{
  "content": [ /* リソースの配列 */ ],
  "page": 0,
  "size": 20,
  "totalElements": 142,
  "totalPages": 8
}
```

| フィールド | 型 | 説明 |
|-----------|-----|------|
| `content` | Array | 該当ページのリソース配列 |
| `page` | Integer | 現在のページ番号（0始まり） |
| `size` | Integer | 1ページあたりの件数 |
| `totalElements` | Long | 総件数 |
| `totalPages` | Integer | 総ページ数 |

> ページングが不要なプルダウン用途では `all=true` パラメータでシンプルリスト（配列のみ）を返すパターンを各APIで個別に定義する。

### HTTPステータスコード使い分け

| ステータス | 用途 |
|-----------|------|
| `200 OK` | 取得成功・更新成功 |
| `201 Created` | リソース新規作成成功 |
| `400 Bad Request` | リクエスト形式不正・バリデーションエラー（Jakarta Bean Validation） |
| `401 Unauthorized` | 未認証・トークン期限切れ |
| `403 Forbidden` | 権限不足（ロール不一致） |
| `404 Not Found` | 対象リソースが存在しない |
| `409 Conflict` | 楽観的ロック競合・業務上の整合性エラー（例: 重複登録、状態遷移不正） |
| `422 Unprocessable Entity` | 形式は正しいが業務ルール違反（例: 在庫不足、棚卸中ロケーションへの操作） |
| `500 Internal Server Error` | サーバー内部エラー |

### 単一リソース取得時のレスポンス

単一リソース（詳細取得・登録・更新）はエンベロープなしでリソースオブジェクトを直接返す。

```json
{
  "id": 1,
  "warehouseCode": "WH-001",
  "warehouseName": "東京第一倉庫",
  ...
}
```

## DTO規約

### 命名規則

| 種別 | 命名パターン | 例 |
|------|-------------|-----|
| リクエスト（作成） | `Create{Resource}Request` | `CreateWarehouseRequest` |
| リクエスト（更新） | `Update{Resource}Request` | `UpdateWarehouseRequest` |
| レスポンス（単体） | `{Resource}Response` | `WarehouseResponse` |
| レスポンス（一覧要素） | `{Resource}Response`（単体と共用）または `{Resource}ListItem` | `WarehouseListItem` |
| 検索条件 | `{Resource}SearchCriteria` | `WarehouseSearchCriteria` |

### 配置場所

各モジュールの `dto/` パッケージに配置する。

```
com.wms.master.dto/
├── CreateWarehouseRequest.java
├── UpdateWarehouseRequest.java
├── WarehouseResponse.java
└── WarehouseSearchCriteria.java
```

### Entity ↔ DTO 変換

| 項目 | 方針 |
|------|------|
| **変換手段** | 手動マッピング（各DTOクラスに `static` ファクトリメソッドを定義） |
| **Entity → Response** | `{Response}.from(Entity)` staticメソッド |
| **Request → Entity** | `{Request}.toEntity()` インスタンスメソッドまたは Service 層で組み立て |
| **Controllerの制約** | **Controller は Entity を直接返してはならない**。必ず DTO に変換して返す |

```java
// 例: WarehouseResponse.java
public record WarehouseResponse(
    Long id,
    String warehouseCode,
    String warehouseName,
    Boolean isActive,
    Integer version        // 楽観的ロック用
) {
    public static WarehouseResponse from(Warehouse entity) {
        return new WarehouseResponse(
            entity.getId(),
            entity.getWarehouseCode(),
            entity.getWarehouseName(),
            entity.getIsActive(),
            entity.getVersion()
        );
    }
}
```

> **MapStruct を採用しない理由:** フィールド数が限定的で手動マッピングのコストが低く、変換ロジックの可読性・デバッグ容易性を優先する。将来フィールド数が増加した場合は MapStruct 導入を再検討する。

## バッチ実行API

```
POST /api/v1/batch/daily-close    # 日替処理（営業日更新・入荷実績集計・出荷実績集計・在庫集計・バックアップを順次実行）
```

内部的には5処理を順番に実行するが、エンドポイントは1つ。詳細は機能要件定義書 [06-batch-processing.md](../../functional-requirements/06-batch-processing.md) 参照。

## システム共通API

業務モジュールに依存しない共通情報を提供するAPIを `shared` モジュール内に実装する。

| メソッド | パス | 説明 | 認証 |
|---------|------|------|------|
| `GET` | `/api/v1/system/business-date` | 現在の営業日を取得 | 要（ログイン済みユーザー） |

### レスポンス例（GET /api/v1/system/business-date）

```json
{
  "businessDate": "2026-03-13"
}
```

> フロントエンドはログイン直後にこのAPIを呼び出して営業日を取得し、全画面のヘッダーに表示する。
> 営業日はPiniaストア（`systemStore`）で保持し、バッチ実行後に再取得して更新する。
