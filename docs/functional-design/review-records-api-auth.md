# API設計レビュー記録票 — 認証・システム共通（AUTH / SYS）

> 対象成果物: `docs/functional-design/09-api-auth.md`
> レビュー日: 2026-03-14
> レビュー担当: Java バックエンド REST API設計スペシャリスト（AI）
> 参照ドキュメント: `docs/functional-design/08-api-overview.md`、`docs/architecture-blueprint/07-auth-architecture.md`、`10-security-architecture.md`、`docs/data-model/02-master-tables.md`、`docs/functional-design/02-screen-auth.md`

---

## エグゼクティブサマリー

| 分類 | 件数 |
|------|------|
| **API設計書修正済**（レビュー時に自動修正） | 4件 |
| **要対応**（他ドキュメントへの変更が必要） | 1件 |
| 指摘事項なし | 7件 |
| **総チェック項目** | 12件 |

---

| No | 対象API | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|--------|----------------|---------|------|---------|
| 1 | API-AUTH-001〜004, API-SYS-001 | `08-api-overview.md` テンプレート | 全5APIが5セクション形式（概要/リクエスト/レスポンス/業務ロジック/補足）に準拠していることを確認 | 指摘事項なし | — |
| 2 | API-AUTH-001〜004, API-SYS-001 | `08-api-overview.md` 全API一覧 | メソッド・パス・認証・ロールが全API一覧の定義と一致していることを確認 | 指摘事項なし | — |
| 3 | API-AUTH-001〜004 | `08-api-overview.md` エラーコード一覧 | 使用されているエラーコード（INVALID_CREDENTIALS, ACCOUNT_LOCKED, ACCOUNT_INACTIVE, TOKEN_EXPIRED, REFRESH_TOKEN_EXPIRED, SAME_PASSWORD）が全て一覧に定義されていることを確認 | 指摘事項なし | — |
| 4 | API-AUTH-001 | `02-master-tables.md` usersテーブル | ロック時に `locked=true` のみ更新し `locked_at=現在日時` の更新が漏れていた（ユーザー不存在時・パスワード不一致時の2箇所） | **API設計書修正済** | フローチャートの `SET_LOCK1` / `SET_LOCK2` ノードを `locked=true\nlocked_at=現在日時\nに更新` に修正済 |
| 5 | API-AUTH-003 | `07-auth-architecture.md` リフレッシュトークン管理 | フローチャートに `HASH_TOKEN["受信したrefresh_tokenをBCryptでハッシュ化"]` → `FIND_TOKEN["token_hashで検索"]` の設計誤りがあった。BCryptは一方向ハッシュのため同じ入力でも毎回異なるハッシュ値が生成され「ハッシュ値でDB検索」は不可能 | **API設計書修正済** | フローチャートを「JWTからuserIdをデコード → user_idで絞り込み → BCrypt.matches()で照合」フローに修正。ビジネスルール5も同様に修正済 |
| 6 | API-AUTH-004 | `10-security-architecture.md` ログイン失敗ロック | `POST /api/v1/auth/change-password` の連続5回失敗でも同様にアカウントロックするポリシーがセキュリティアーキテクチャに定義されているが、API設計書の業務ロジックにロック処理が一切記述されていなかった | **API設計書修正済** | エラーレスポンス表に `ACCOUNT_LOCKED` 追加、フローチャートに失敗回数インクリメント・ロック処理を追加、ビジネスルール1修正・2追加、成功時の `failed_login_count=0` リセットも追加済 |
| 7 | API-AUTH-004 | `02-master-tables.md` usersテーブル | ロック時に `locked_at` 更新が漏れていた（修正6の対応内で合わせて修正） | **API設計書修正済** | フローチャートの SET_LOCK ノードに `locked_at=現在日時` を追加済 |
| 8 | API-AUTH-004 | `02-screen-auth.md` EVT-AUTH002-003 | 画面設計書のイベント定義に「`200 OK`: パスワード変更成功」と記述されているが、API設計書では `204 No Content` が正しい | **要対応** | `02-screen-auth.md` EVT-AUTH002-003 の成功レスポンスコードを `200 OK` → `204 No Content（レスポンスボディなし）` に修正すること |
| 9 | API-AUTH-001/002 | `07-auth-architecture.md` JWT設計 | アクセストークン（JWT）のPayload内容（userId, userCode, role, iat, exp）と署名アルゴリズム（HS256）の設計がアーキテクチャ定義と整合していることを確認 | 指摘事項なし | — |
| 10 | API-AUTH-003 | `08-api-overview.md` 認証仕様 | スライディング方式リフレッシュトークン（最終アクセスから1時間延長）の設計が共通認証仕様と整合していることを確認 | 指摘事項なし | — |
| 11 | API-AUTH-004 | `10-security-architecture.md` パスワード管理 | パスワードポリシー「8文字以上、BCryptハッシュ」がAPI設計書に正しく反映されていることを確認 | 指摘事項なし | — |
| 12 | API-SYS-001 | `docs/data-model/04-batch-tables.md` business_dateテーブル | `business_date` テーブルのレコード不存在時に500を返す設計がビジネスルールに明記されていることを確認 | 指摘事項なし | — |

---

## 修正対応ログ

### No.4 対応詳細
**修正対象**: `docs/functional-design/09-api-auth.md` API-AUTH-001 業務ロジック フローチャート
- `SET_LOCK1`: `"locked=trueに更新"` → `"locked=true\nlocked_at=現在日時\nに更新"`
- `SET_LOCK2`: `"locked=trueに更新"` → `"locked=true\nlocked_at=現在日時\nに更新"`

### No.5 対応詳細
**修正対象**: `docs/functional-design/09-api-auth.md` API-AUTH-003 業務ロジック フローチャート + ビジネスルール
- **フローチャート修正前**: `CHECK_COOKIE→HASH_TOKEN→FIND_TOKEN（token_hashで検索）`
- **フローチャート修正後**: `CHECK_COOKIE→DECODE_JWT（userIdデコード）→FIND_TOKEN（user_idで検索）→BCRYPT_CHECK（BCrypt.matches照合）`
- **ビジネスルール5修正**: 「BCryptハッシュ化してDB検索」→「user_idで絞り込み後、BCrypt.matches()照合」に変更

### No.6〜7 対応詳細
**修正対象**: `docs/functional-design/09-api-auth.md` API-AUTH-004
- エラーレスポンス表に `401 ACCOUNT_LOCKED` 行を追加
- フローチャートに失敗カウントインクリメント → ロック判定 → ACCOUNT_LOCKED レスポンスのフローを追加
- UPDATE_PW ノードに `failed_login_count = 0` リセットを追加
- ビジネスルール表: ルール1修正、ルール2追加（ロックポリシー）、旧ルール2→3に繰り下げ、ルール5修正（failed_login_count=0リセット追加）

---

## 要件定義書・アーキテクチャ設計書 修正要 アクションアイテム

| # | 修正対象ドキュメント | 修正箇所 | 修正内容 | 優先度 |
|---|-------------------|---------|---------|-------|
| 1 | `docs/functional-design/02-screen-auth.md` | EVT-AUTH002-003 成功レスポンスコード | `200 OK` → `204 No Content（レスポンスボディなし）` に修正。フロントエンドはレスポンスボディを参照せず、204受信時にPiniaストアの `passwordChangeRequired` を `false` に更新して `/` へ遷移する実装とすること | 高 |
