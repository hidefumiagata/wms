# Claude Code — 学んだこと（Lessons Learned）

> 本ファイルはClaude Codeがこのプロジェクトで学んだ教訓・パターン・ユーザーの好みを記録する。
> 新しい環境・セッションでも一貫した振る舞いを維持するために参照する。

## 1. 技術的な教訓

### Mermaidフローチャート
- ノード内の `()` はノード形状と衝突する。`UNIQUE(a, b)` → `UNIQUE: a, b` に置換
- `{}` ダイヤモンドノード内の `{id}` はURL変数でも構文エラーになる。`:id` を使う
- `\n` は多くの場合動作するが、特殊文字と組み合わせると壊れる。`<br/>` が安全
- 正規表現（`[A-Z]`, `\\d`, `$`）はMermaidノード内で使えない。説明文に簡略化する
- `mermaid-cli` (`npx mmdc`) で事前検証可能

### 設計書間の整合性
- ステータス値の不一致が最も頻発する問題。Enum定義（08-common-infrastructure.md）、data-model、API設計書、画面設計書の4箇所を常に同期させる必要がある
- 環境変数名の不一致も頻発（`JWT_SECRET` vs `JWT_SECRET_KEY`、`CORS_ALLOWED_ORIGINS` vs `FRONTEND_URL`）
- APIパスの `/master/` プレフィックスの有無が画面設計書とAPI設計書でずれやすい
- エラーコード体系は英語定数名（`DUPLICATE_CODE`等）に統一済み

### OpenAPI定義
- OpenAPI 3.1のnullable構文は `type: ['string', 'null']`（`nullable: true` は3.0の書き方）
- Redocly CLIでバリデーション: `npx @redocly/cli lint openapi/wms-api.yaml`
- 共通コンポーネント（ResourceId, PageParam, SizeParam, ReportFormat, ValidationError, Unauthorized, Forbidden, OptimisticLockConflict）を積極的に再利用する
- レポートAPIのように同一パターンのエンドポイントが多い場合、レビューで403等の共通レスポンスの付け忘れが頻発する

### 並列作業の注意点
- 同一ファイルを複数エージェントが同時修正するとコンフリクトする。ファイル単位でエージェントを分けること
- 大きなファイル（API-10-report.md、06-infrastructure-architecture.md等）は1エージェントに集中させる
- 設計書レビューは13 API分を並列エージェントで実行可能（各エージェントが設計書1つ+OpenAPIの該当部分を読む）

## 2. 開発フェーズの学び

### OpenAPIと設計書の整合性
- 設計フェーズ完了後、OpenAPI定義を作成したら必ず全設計書との整合性レビューを行う
- 頻出する差分パターン:
  - エンドポイントパスの命名不一致（shipping-list vs delivery-list）
  - 403 Forbiddenレスポンスの付け忘れ（特に大量エンドポイント作成時）
  - レスポンススキーマのrequired配列の未定義
  - バリデーション制約（minLength等）の未定義
  - 設計書内部の矛盾（同一ドキュメント内で異なる値を記載）

### コード修正時に設計書との乖離に注意
- コードを修正すると設計書のコード例が古くなる（例: `JacksonConfig.java` 削除後も設計書に残存）
- パッケージ構造図、設定例、コードスニペットは設計書の複数箇所に散在していることが多い
- 修正時は `grep` で設計書内の参照箇所を全て洗い出してから対応する

### テスト品質の指摘パターン
- `@DisplayName` の有無の不統一は必ず指摘される。全テストに付けること
- テストメソッド命名は `{method}_{condition}_{expectedResult}` の3部構成を守る
- `verify(mock).doFilter()` だけのテスト（副作用の未検証）は弱いアサーションとして指摘される。`ListAppender` でログ出力内容まで検証する
- `System.currentTimeMillis()` は経過時間計測に非推奨。`System.nanoTime()` を使う

### 正規表現の設計
- **PII マスキング用の電話番号正規表現は厳密に**。WMS業務データ（注文番号、ロケーションコード等）に偽陽性を起こさないよう、ハイフン区切り形式のみに限定する
- **メール正規表現のpossessive quantifier** (`++`) はドメイン部に `.` を含む文字クラスと併用すると動作しない。ドメイン部は構造化したパターンにする
- **ReDoS対策**: ログメッセージに適用する正規表現は外部入力に触れる可能性があるため、possessive quantifierやatomic groupの利用を検討する

### AOP・フィルター設計
- `catch(Exception)` と `throws Throwable` の不一致に注意。Spring AOPの `ProceedingJoinPoint.proceed()` は `Throwable` を投げるため、`catch(Throwable)` にすべき
- MDCのキーをAOPで設定する場合、ネストした呼び出し（ServiceA→ServiceB）で上書きされる。`finally` で前の値をsave/restoreするパターンが必須
- サーブレットフィルターの `doFilter` 例外時にもログ出力するには `try/catch/finally` が必要。正常時INFOのみでは5xx系リクエストが記録されない

### Jackson設定の一本化
- Spring Bootの `application.yml` Jackson設定と `JacksonConfig.java` Bean の重複は排除する
- `java.time.LocalDateTime` を使う場合、`write-dates-as-timestamps: false` で十分。`date-format`（simpleDateFormat）は `java.util.Date` 向けの設定であり不要
- 設定を一本化する際は、設計書のコード例も必ず同時更新する
