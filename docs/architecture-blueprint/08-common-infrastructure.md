# アプリケーション共通基盤

## ロギング設計

### ログ収集フロー

```
Spring Boot（SLF4J + Logback）
    │ JSON形式で標準出力
    ▼
Azure Container Apps
    │ 自動収集
    ▼
Log Analytics Workspace
    │ KQLクエリで検索・分析
    ├── Azure Monitor アラート → メール通知（ERRORログ検知）
    └── Application Insights → API・画面パフォーマンス可視化
```

### ログ設定

| 項目 | 設定 |
|------|------|
| **ライブラリ** | SLF4J + Logback |
| **形式** | JSON（構造化ログ） |
| **出力先** | 標準出力（Container Appsが自動収集） |
| **ログレベル** | 本番：INFO / 開発：DEBUG |
| **保存期間** | Log Analytics Workspace のデフォルト（30日） |

### ログ項目（標準フォーマット）

```json
{
  "timestamp": "2026-03-12T10:00:00+09:00",
  "level": "INFO",
  "logger": "com.wms.inbound.InboundService",
  "traceId": "abc123",
  "userId": "12345",
  "message": "入荷検品完了",
  "module": "inbound"
}
```

### PII（個人情報）マスキング

Logbackカスタムフィルターでログ出力前に自動マスク。

| 対象 | マスク例 |
|------|---------|
| メールアドレス | `user@example.com` → `**@**.***` |
| 電話番号 | `090-1234-5678` → `***-****-****` |
| 氏名 | 必要に応じて設定 |

### Azureモニタリングサービス

| サービス | 用途 | 無料枠 |
|---------|------|--------|
| **Log Analytics Workspace** | ログ集約・保存・KQL検索 | 5GB/月 |
| **Azure Monitor アラート** | ERRORログ検知→自動通知 | 1000ルール/月 |
| **Application Insights** | API・画面パフォーマンス可視化 | 5GB/月 |

## エラーハンドリング

### 方針

- `@ControllerAdvice`（`GlobalExceptionHandler`）で一元管理
- 全エラーレスポンスを統一フォーマットで返却
- Service 層では業務例外をスローし、Controller 層では例外をスローしない（`GlobalExceptionHandler` に委譲）

### エラーレスポンス形式

```json
{
  "code": "INBOUND_SLIP_NOT_FOUND",
  "message": "入荷予定データが存在しません",
  "timestamp": "2026-03-12T10:00:00+09:00",
  "traceId": "abc123"
}
```

バリデーションエラー（複数フィールド）の場合は `details` を付与する。

```json
{
  "code": "VALIDATION_ERROR",
  "message": "入力内容にエラーがあります",
  "timestamp": "2026-03-12T10:00:00+09:00",
  "traceId": "abc123",
  "details": [
    { "field": "warehouseCode", "message": "倉庫コードは必須です" },
    { "field": "warehouseName", "message": "倉庫名は50文字以内で入力してください" }
  ]
}
```

### 例外クラス階層

すべてのカスタム例外は `shared.exception` パッケージに配置する。

```
RuntimeException
└── WmsException（abstract）              ← 全カスタム例外の基底クラス
    ├── ResourceNotFoundException         ← 404 Not Found
    ├── DuplicateResourceException        ← 409 Conflict（一意制約違反・重複登録）
    ├── BusinessRuleViolationException    ← 422 Unprocessable Entity（業務ルール違反）
    ├── OptimisticLockConflictException   ← 409 Conflict（楽観的ロック競合）
    └── InvalidStateTransitionException   ← 409 Conflict（状態遷移不正）
```

| 例外クラス | HTTPステータス | 用途・例 |
|-----------|-------------|---------|
| `ResourceNotFoundException` | 404 | 指定IDのリソースが存在しない、論理削除済みリソースへのアクセス |
| `DuplicateResourceException` | 409 | 倉庫コード重複、ユーザーID重複 |
| `BusinessRuleViolationException` | 422 | 在庫不足、棚卸中ロケーションへの操作、ロック済みアカウント |
| `OptimisticLockConflictException` | 409 | `@Version` による楽観的ロック検出時 |
| `InvalidStateTransitionException` | 409 | 入荷ステータスが「検品済」なのに再度検品しようとした等 |

#### WmsException 基底クラス

```java
public abstract class WmsException extends RuntimeException {
    private final String errorCode;  // 英語定数名形式（例: DUPLICATE_CODE, VALIDATION_ERROR）

    protected WmsException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}
```

### GlobalExceptionHandler マッピング

`@ControllerAdvice` クラスで例外 → HTTPレスポンスへの変換を一元管理する。

| 例外 | ステータス | 処理 |
|------|-----------|------|
| `ResourceNotFoundException` | 404 | エラーコード・メッセージを返却 |
| `DuplicateResourceException` | 409 | エラーコード・メッセージを返却 |
| `BusinessRuleViolationException` | 422 | エラーコード・メッセージを返却 |
| `OptimisticLockConflictException` | 409 | エラーコード・メッセージを返却 |
| `InvalidStateTransitionException` | 409 | エラーコード・メッセージを返却 |
| `MethodArgumentNotValidException` | 400 | Jakarta Bean Validation エラー。`details` 付きレスポンス |
| `AccessDeniedException` | 403 | Spring Security 権限不足 |
| `AuthenticationException` | 401 | Spring Security 認証エラー |
| `Exception`（その他） | 500 | スタックトレースをログ出力し、汎用エラーメッセージを返却（詳細は非公開） |

> **原則:** Service 層は上記のカスタム例外をスローする。Repository 層の例外（`DataIntegrityViolationException` 等）は Service 層でキャッチしてカスタム例外に変換する。Controller 層は例外処理を行わず、`GlobalExceptionHandler` に委譲する。

### エラーコード体系

エラーコードは英語の定数名を使用する。命名規則は `{RESOURCE}_{ERROR_TYPE}` または `{ERROR_TYPE}`（リソース非依存の場合）とする。

```
例：
  WAREHOUSE_NOT_FOUND        — 倉庫が見つからない
  DUPLICATE_CODE             — コード重複
  OPTIMISTIC_LOCK_CONFLICT   — 楽観的ロック競合
  VALIDATION_ERROR           — 入力バリデーションエラー
  INVALID_SORT_FIELD         — 不正なソートフィールド
  INTERNAL_SERVER_ERROR      — サーバー内部エラー
  FORBIDDEN                  — 権限不足
  UNAUTHORIZED               — 認証エラー
```

エラーコードは各モジュールの Service 層で定義する。定数クラスの作成は行わず、各例外スロー箇所でリテラルとして記述する（エラーコードの検索性をIDEのテキスト検索で確保する）。

## その他共通設定

| 項目 | 設定 |
|------|------|
| **タイムゾーン** | JST（Asia/Tokyo）固定 |
| **文字コード** | UTF-8 統一 |
| **バリデーション** | Jakarta Bean Validation（Controller層）+ ビジネスルール（Service層） |
| **トランザクション** | `@Transactional` をService層に付与 |
| **トレースID** | リクエストごとにUUIDを付与、全ログに埋め込み |
| **営業日** | `business_date` テーブルから都度取得（キャッシュなし）。全業務処理は営業日を基準とする。詳細は [01-overall-architecture.md](01-overall-architecture.md) 参照 |
