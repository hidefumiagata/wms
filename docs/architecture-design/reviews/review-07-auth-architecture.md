# アーキテクチャ設計レビュー記録票 — 認証アーキテクチャ

> 対象成果物: `docs/architecture-design/07-auth-architecture.md`
> レビュー日: 2026-03-18
> レビュー担当: 認証・認可セキュリティアーキテクチャ設計スペシャリスト（AI）
> 参照ドキュメント:
> - `docs/architecture-blueprint/07-auth-architecture.md`（認証・認可ブループリント）
> - `docs/architecture-blueprint/10-security-architecture.md`（セキュリティアーキテクチャ）
> - `docs/architecture-blueprint/04-backend-architecture.md`（バックエンドアーキテクチャ）
> - `docs/architecture-blueprint/03-frontend-architecture.md`（フロントエンドアーキテクチャ）
> - `docs/functional-requirements/00-authentication.md`（機能要件定義書 — 認証）
> - `docs/functional-design/API-01-auth.md`（認証API設計）
> - `docs/functional-design/SCR-01-auth.md`（認証画面設計）
> - `docs/data-model/02-master-tables.md`（マスタ系テーブル定義）
> - `CLAUDE.md`（プロジェクトコンテキスト）

---

## エグゼクティブサマリー

認証アーキテクチャ設計書は、ブループリント・機能要件定義書・API設計書・データモデル定義書との整合性が高く、Spring Security 6.x の最新APIを使用した実装設計として十分な品質にある。SSOTルールに従い、ポリシー値の複製を避け参照リンクで示している点も適切である。

以下のレビュー結果では、軽微な改善提案と確認事項を記録する。重大な不整合（Critical）は検出されなかった。

---

## レビュー結果

| No | チェック観点 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|------------|----------------|---------|------|---------|
| 1 | ブループリントとの整合性 | `07-auth-architecture.md`（BP） | トークン有効期限（AT: 1時間、RT: スライディング1時間）、ロール定義（4種）、RBAC権限マトリクスが一致していることを確認。整合性問題なし | 確認済 | 対応不要 |
| 2 | セキュリティアーキテクチャとの整合性 | `10-security-architecture.md` | パスワードポリシー（8〜128文字、英大文字・英小文字・数字各1）、BCrypt strength=12、ログイン失敗ロック（5回）、CORS設定、セキュリティヘッダーが一致。SSOTルールに従い値の複製を避け参照リンクで示している | 確認済 | 対応不要 |
| 3 | API設計書との整合性 | `API-01-auth.md` | 全6エンドポイント（login, logout, refresh, change-password, password-reset/request, password-reset/confirm）の仕様が一致。Cookie属性（HttpOnly, SameSite=Lax, Path制限）も一致 | 確認済 | 対応不要 |
| 4 | データモデルとの整合性 | `02-master-tables.md` | `users`テーブル（user_code, password_hash, role, failed_login_count, locked, locked_at, password_change_required）、`refresh_tokens`テーブル、`password_reset_tokens`テーブルの構造と設計書の参照が一致 | 確認済 | 対応不要 |
| 5 | フロントエンドアーキテクチャとの整合性 | `03-frontend-architecture.md` | Axiosインターセプターの401リフレッシュフロー、キュー制御（同時401リクエストの待機）、authStore/systemStoreの設計が一致 | 確認済 | 対応不要 |
| 6 | 画面設計書との整合性 | `SCR-01-auth.md` | AUTH-001〜004の画面フロー（初回パスワード変更の強制リダイレクト、パスワードリセットフロー）が認証フローシーケンス図と一致 | 確認済 | 対応不要 |
| 7 | SecurityConfig の AccessDeniedHandler | 設計書 セクション8.4 | `CustomAccessDeniedHandler` を定義しているが、`SecurityConfig` のコード例に `.exceptionHandling(exception -> exception.accessDeniedHandler(...))` が含まれていない。注意書きはあるが、コード例にも反映すべき | Minor | 未対応（実装時に対応） |

> **対応完了** (2026-03-19): 07-auth-architecture.md の SecurityConfig コード例に AccessDeniedHandler 設定（`.exceptionHandling(exception -> exception.accessDeniedHandler(...))`）を追加
| 8 | refresh_token の Path と logout | `API-01-auth.md` API-AUTH-002 | `refresh_token` の Cookie Path が `/api/v1/auth/refresh` に限定されているため、`POST /api/v1/auth/logout` 時にはブラウザから `refresh_token` Cookie が送信されない。ログアウト処理は `access_token` の JWT から userId を取得して DB のリフレッシュトークンを削除する設計であり、問題はない。ただしこの設計判断を明示的に記載するとよい | Minor | 未対応（実装時に対応） |

> **対応完了** (2026-03-19): 07-auth-architecture.md にログアウト時 refresh_token Cookie が Path 制限により非送信となる設計判断を明記
| 9 | パスワードリセットトークンのハッシュ方式 | `02-master-tables.md` | データモデル定義では `token_hash` を「SHA-256等」と記載しているが、API設計書（API-AUTH-005/006）では「BCryptでハッシュ化」と記載されている。設計書ではBCrypt方式を採用している。データモデル定義書の「SHA-256等」の記述を更新することを推奨 | 対応済み | ✅ **対応済み（2026-03-18）**: SHA-256に全設計書で統一。API-01-auth.mdも修正済み |
| 10 | パスワードリセットトークンの有効期限 | `00-authentication.md`, `API-01-auth.md` | 機能要件定義書では「30分」、ブループリントでは「30分（システムパラメータで変更可能）」、API設計書（API-AUTH-005）では「1時間」と記載されている。設計書では明示していない（system_parametersのPASSWORD_RESET_EXPIRY_MINUTES=30を参照）。API設計書の「1時間」はブループリント・機能要件定義書と矛盾しているため、API設計書側の修正を推奨 | 対応済み | ✅ **対応済み（2026-03-18）**: 有効期限30分に統一。API-01-auth.mdの「1時間」を「30分」に修正済み |
| 11 | Swagger UI のプロファイル制御 | 設計書 セクション2.2 | コメントで「本番ではプロファイルで無効化」と記載しているが、具体的なプロファイル設定方法が未記載。`@Profile("dev")` での Springdoc 設定や、`springdoc.api-docs.enabled=false` の本番設定を実装時に対応すべき | Info | 実装時に対応 |
| 12 | JWT秘密鍵の最小長チェック | 設計書 セクション3.2 | 環境変数 `JWT_SECRET` が256bit未満の場合に起動時エラーとする仕組みが未記載。jjwt ライブラリが自動で検証するが、明示的なバリデーションを追加することを推奨 | Info | 実装時に対応 |
| 13 | ログアウト時の期限切れJWT処理 | `API-01-auth.md` API-AUTH-002 | `parseClaimsAllowExpired` メソッドで期限切れJWTからもuserIdを取得する設計は、API設計書の「期限切れトークンからユーザーIDを抽出してリフレッシュトークンを削除する」仕様と一致。問題なし | 確認済 | 対応不要 |
| 14 | リフレッシュトークンのBCrypt照合方式 | `API-01-auth.md` API-AUTH-003 | BCryptは一方向ハッシュのため「ハッシュ化して検索」が不可能であり、userId で絞り込んだ後に BCrypt.matches で照合する設計は API 設計書の記述と一致。`refresh_tokens` テーブルの UNIQUE(token_hash) 制約は BCrypt の場合、同一入力でも異なるハッシュが生成されるため実質的に一意となり問題ない | 確認済 | 対応不要 |
| 15 | SSOTルール遵守 | `CLAUDE.md` | パスワードポリシー値、ロック条件値、セッションタイムアウト値を直接記載せず、参照リンクで示している。SSOTルールに準拠 | 確認済 | 対応不要 |
| 16 | Spring Security 6.x API使用 | 設計書全体 | `SecurityFilterChain` Bean（`WebSecurityConfigurerAdapter` 非使用）、`authorizeHttpRequests`（`authorizeRequests` 非使用）、Lambda DSL を使用しており、Spring Security 6.x の最新APIに準拠 | 確認済 | 対応不要 |

---

## 総評

### 良好な点

1. **SSOTルールの遵守**: ポリシー値（パスワードポリシー、ロック閾値、セッションタイムアウト等）の複製を避け、ブループリント・セキュリティアーキテクチャへの参照リンクで示している
2. **Spring Security 6.x 最新API**: 非推奨APIを使用せず、Lambda DSL、`SecurityFilterChain` Bean 方式で記述している
3. **セキュリティ対策の網羅性**: OWASP Top 10 への対応、タイミング攻撃対策、ユーザー列挙対策、トークンローテーションによるリプレイ攻撃対策が設計レベルで記述されている
4. **フロントエンド連携**: Axiosインターセプターのキュー制御、セッションタイムアウト警告ダイアログの設計まで網羅している
5. **Mermaid図の充実**: 認証フロー（ログイン・ログアウト・リフレッシュ）のシーケンス図、アカウントロックの状態遷移図が分かりやすい

### 改善推奨事項

1. ~~**No.9**: `password_reset_tokens.token_hash` のハッシュ方式について、データモデル定義書との記述統一が必要~~ ✅ 対応済み（2026-03-18）
2. ~~**No.10**: パスワードリセットトークンの有効期限について、API設計書（1時間）とブループリント/機能要件定義書（30分）の矛盾解消が必要~~ ✅ 対応済み（2026-03-18）
3. ~~**No.7**: SecurityConfig コード例に AccessDeniedHandler の設定を追加すること~~ ✅ 対応済み（2026-03-19）
4. ~~**No.8**: ログアウト時に refresh_token Cookie が送信されない設計判断の明記~~ ✅ 対応済み（2026-03-19）

---

## 次のアクション

| # | アクション | 優先度 | 担当 |
|---|-----------|--------|------|
| 1 | ~~API設計書（API-AUTH-005）のパスワードリセットトークン有効期限を「30分」に修正~~ | 中 | ✅ 対応済み（2026-03-18） |
| 2 | ~~データモデル定義書の `password_reset_tokens.token_hash` 説明をSHA-256に統一~~ | 中 | ✅ 対応済み（2026-03-18） |
| 3 | ~~SecurityConfig コード例に AccessDeniedHandler 設定を追加~~ | 低 | ✅ 対応済み（2026-03-19） |
| 4 | ~~ログアウトフロー補足（refresh_token Cookie の Path 制限による非送信の明記）~~ | 低 | ✅ 対応済み（2026-03-19） |
