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

```
HTTP Request
     │
     ▼
┌─────────────┐
│  Controller  │  リクエスト受付・レスポンス返却・バリデーション
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Service    │  ビジネスロジック・トランザクション管理
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ Repository  │  DB アクセス（Spring Data JPA）
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  Database   │  PostgreSQL（Azure Flexible Server）
└─────────────┘
```

## モジュール構成

各業務モジュールは独立したパッケージとして実装し、モジュール間の依存は `shared` を経由する。

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

## バッチ実行API

```
POST /api/v1/batch/daily-close        # 日替処理
POST /api/v1/batch/inbound-summary    # 入荷実績集計
POST /api/v1/batch/outbound-summary   # 出荷実績集計
POST /api/v1/batch/inventory-summary  # 在庫集計
POST /api/v1/batch/backup             # トランデータバックアップ
```
