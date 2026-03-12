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

- `@ControllerAdvice` で一元管理
- 全エラーレスポンスを統一フォーマットで返却

### エラーレスポンス形式

```json
{
  "code": "WMS-E-INB-001",
  "message": "入荷予定データが存在しません",
  "timestamp": "2026-03-12T10:00:00+09:00",
  "traceId": "abc123"
}
```

### エラーコード体系

```
WMS-{種別}-{モジュール}-{連番}
例：
WMS-E-INB-001  → エラー / 入荷 / 001
WMS-W-INV-001  → 警告 / 在庫 / 001
WMS-I-SHP-001  → 情報 / 出荷 / 001
```

#### モジュール略称一覧

| 略称 | モジュール |
|------|-----------|
| `INB` | inbound（入荷） |
| `INV` | inventory（在庫） |
| `ALL` | allocation（在庫引当） |
| `SHP` | outbound（出荷） |
| `MST` | master（マスタ） |
| `RPT` | report（レポート） |
| `BCH` | batch（バッチ） |
| `IFX` | interface（外部連携） |
| `CMN` | shared（共通） |
| `AUT` | 認証・認可 |

## その他共通設定

| 項目 | 設定 |
|------|------|
| **タイムゾーン** | JST（Asia/Tokyo）固定 |
| **文字コード** | UTF-8 統一 |
| **バリデーション** | Jakarta Bean Validation（Controller層）+ ビジネスルール（Service層） |
| **トランザクション** | `@Transactional` をService層に付与 |
| **トレースID** | リクエストごとにUUIDを付与、全ログに埋め込み |
