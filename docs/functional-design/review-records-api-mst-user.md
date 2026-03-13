# API設計レビュー記録票 — ユーザーマスタ（MST-USR）

> 対象成果物: `docs/functional-design/10-api-master-user.md`
> レビュー日: 2026-03-14
> レビュー担当: Java バックエンド REST API設計スペシャリスト（AI）
> 参照ドキュメント: `docs/functional-design/08-api-overview.md`、`docs/functional-requirements/01-master-management.md`、`docs/data-model/02-master-tables.md`、`docs/functional-design/03-screen-master-user.md`、`docs/architecture-blueprint/07-auth-architecture.md`、`docs/architecture-blueprint/10-security-architecture.md`

---

## エグゼクティブサマリー

| 分類 | 件数 |
|------|------|
| **API設計書修正済**（レビュー時に自動修正） | 7件 |
| **要対応**（他ドキュメントへの変更が必要） | 11件 |
| 指摘事項なし | 11件 |
| **総チェック項目** | 29件 |

---

| No | 対象API | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|--------|----------------|---------|------|---------|
| 1 | API-MST-USR-001〜006 | `08-api-overview.md` テンプレート | 全6APIが5セクション形式（概要/リクエスト/レスポンス/業務ロジック/補足）に準拠していることを確認 | 指摘事項なし | — |
| 2 | API-MST-USR-001〜006 | `08-api-overview.md` 全API一覧 | メソッド・パス・ロールが全API一覧の定義と一致していることを確認（MST-USR-001〜006 全て SYSTEM_ADMIN のみ） | 指摘事項なし | — |
| 3 | API-MST-USR-001〜006 | `02-master-tables.md` usersテーブル | リクエスト/レスポンスのフィールド名がDBカラム名（スネークケース→キャメルケース変換）と整合していることを確認 | 指摘事項なし | — |
| 4 | API-MST-USR-002/004 | `10-security-architecture.md` パスワード管理 | `passwordHash` がレスポンスに含まれておらず、BCryptハッシュが外部に露出しないことを確認。`password_change_required = true` の登録時固定も整合 | 指摘事項なし | — |
| 5 | API-MST-USR-004 | `01-master-management.md` §7 ビジネスルール | 自分自身のロール変更不可（`CANNOT_CHANGE_SELF_ROLE`）・無効化不可（`CANNOT_DEACTIVATE_SELF`）のルールがAPI設計書に正しく反映されていることを確認 | 指摘事項なし | — |
| 6 | API-MST-USR-006 | `07-auth-architecture.md` アカウントロック | ロック解除の冪等性保証（解除済みへの再解除も `204` を返す）が設計されていることを確認 | 指摘事項なし | — |
| 7 | API-MST-USR-006 | `08-api-overview.md` テンプレート | 補足事項に「フロントエンドは `204 No Content` 受信後にユーザー情報を再取得して `locked` フラグ表示を更新する」旨が未記載 | **API設計書修正済** | 補足事項に画面別の再取得方針（MST-061 は一覧再取得、MST-063 はユーザー詳細再取得）を追記済 |
| 8 | API-MST-USR-001〜006 | `08-api-overview.md` エラーコード一覧 | `CANNOT_CHANGE_SELF_ROLE`（自分自身のロール変更不可、422）が `08-api-overview.md` のエラーコード一覧に未定義 | **要対応** | `08-api-overview.md` のエラーコード一覧（マスタ管理共通パターン）に `CANNOT_CHANGE_SELF_ROLE \| 422 \| 自分自身のロールを変更しようとした` を追加すること |
| 9 | API-MST-USR-002/004 | `03-screen-master-user.md` MST-062/063 フォームバリデーション | 氏名フィールド（`userName`）のバリデーションが画面設計書では「1〜100文字」だが、DBカラム定義は `varchar(200)` でAPI設計書も「200文字以内」 | **要対応** | `03-screen-master-user.md` の MST-062 INP-002・MST-063 INP-021 のバリデーションを「100文字以内」→「200文字以内」に修正すること |
| 10 | API-MST-USR-002/004 | `03-screen-master-user.md` MST-062/063 フォームバリデーション | メールアドレスフィールド（`email`）のバリデーションが画面設計書では「254文字以内」だが、DBカラム定義は `varchar(200)` でAPI設計書も「200文字以内」 | **要対応** | `03-screen-master-user.md` の MST-062 INP-003・MST-063 INP-022 のバリデーションを「254文字以内」→「200文字以内」に修正すること（RFC 5321 ではなくDBスキーマ優先で統一） |
| 11 | API-MST-USR-001〜006 | `03-screen-master-user.md` 全イベント APIパス | 画面設計書内のAPIパスが `/api/v1/users`（旧パス）と記述されている箇所があり、正しくは `/api/v1/master/users` | **要対応** | `03-screen-master-user.md` の以下イベント定義のAPIパスを修正すること: EVT-061-002: `GET /api/v1/users` → `GET /api/v1/master/users`、EVT-062-002: `POST /api/v1/users` → `POST /api/v1/master/users`、EVT-063-001: `GET /api/v1/users/{id}` → `GET /api/v1/master/users/{id}`、EVT-063-004: `PUT /api/v1/users/{id}` → `PUT /api/v1/master/users/{id}` |
| 12 | API-MST-USR-006 | `03-screen-master-user.md` EVT-061-006, EVT-063-002 | アンロックAPIのパスが `PUT /api/v1/users/{id}/unlock` と記述されているが、正しくは `PATCH /api/v1/master/users/{id}/unlock` | **要対応** | `03-screen-master-user.md` の EVT-061-006・EVT-063-002 を `PATCH /api/v1/master/users/{id}/unlock` に修正すること |
| 13 | API-MST-USR-002 | `03-screen-master-user.md` EVT-062-003 | 画面設計書に `GET /api/v1/users/exists?code={code}` という未定義APIへの参照がある。`08-api-overview.md` の全API一覧にこのエンドポイントは存在しない | **要対応** | 設計方針を選択すること: (A) 登録時の重複は `409 DUPLICATE_CODE` でフィードバックし、リアルタイムチェックAPIは不要とする、または (B) `GET /api/v1/master/users/exists?code={code}` を新規エンドポイントとして `08-api-overview.md` に追加定義する。いずれかを選択後、`03-screen-master-user.md` を更新すること |
| 14 | API-MST-USR-002 | `07-auth-architecture.md`・`10-security-architecture.md` | パスワードポリシーに上限（最大文字数）が未定義。API設計書では「8文字以上」のみ定義。BCryptの入力上限（72バイト）を考慮して上限を設けるべき | **要対応** | `10-security-architecture.md` §パスワード管理に「最大128文字」の上限を追記すること。API設計書は `10-security-architecture.md` 修正後に連動して更新すること |
| 15 | API-MST-USR-001〜006 | `02-master-tables.md` usersテーブル | `lockedAt` の null 時省略（`@JsonInclude(NON_NULL)`）の設計が補足事項に明記されていることを確認 | 指摘事項なし | — |
| 16 | API-MST-USR-002 | `10-security-architecture.md` パスワード管理 | BCryptハッシュ化（強度12）がAPI設計書のビジネスルールに明記されていることを確認 | 指摘事項なし | — |
| 17 | API-MST-USR-004 | `07-auth-architecture.md` JWT設計 | ロール変更後も既存のJWTトークンは失効しない（次回ログイン時に反映）設計が補足事項に明記されていることを確認 | 指摘事項なし | — |
| 18 | API-MST-USR-005 | `01-master-management.md` §7 ビジネスルール | 自分自身の無効化不可（`CANNOT_DEACTIVATE_SELF`）ルールが正しく実装されていることを確認 | 指摘事項なし | — |
| 19 | API-MST-USR-001〜006 | `08-api-overview.md` 認証・認可仕様 | 全APIが SYSTEM_ADMIN のみのアクセス制限で定義されており、権限マトリクスと整合していることを確認 | 指摘事項なし | — |
| 20 | API-MST-USR-001〜006 | `08-api-overview.md` エラーコード一覧 | 使用されているエラーコード（USER_NOT_FOUND, DUPLICATE_CODE, CANNOT_DEACTIVATE_SELF, CANNOT_CHANGE_SELF_ROLE, USER_ALREADY_LOCKED）が一覧に定義済みであることを確認 ※ CANNOT_CHANGE_SELF_ROLE は要対応（No.8） | 指摘事項なし（No.8 除く） | — |
| 21 | API-MST-USR-003 | `02-master-tables.md` usersテーブル | 詳細取得レスポンスに `passwordHash` が含まれないことを確認（セキュリティ要件） | 指摘事項なし | — |
| 22 | API-MST-USR-006 | `10-security-architecture.md` ログイン失敗ロック | ロック解除時に `failed_login_count = 0` リセットするルールが API-MST-USR-006 ビジネスルール3に明記されていることを確認 | 指摘事項なし | — |
| 23 | API-MST-USR-002/004 | `01-master-management.md` §7 管理項目 | `userCode`（ユーザーコード）は登録後変更不可のルールがAPI設計書の補足事項に明記されていることを確認 | 指摘事項なし | — |

---

## 修正対応ログ

### No.7 対応詳細
**修正対象**: `docs/functional-design/10-api-master-user.md` API-MST-USR-006 補足事項

**修正前**: 補足事項に画面再取得の記述なし

**追記内容**:
```
- フロントエンドは `204 No Content` 受信後、`GET /api/v1/master/users/{id}` を呼び出してユーザー情報を再取得し、
  画面上の `locked` フラグ表示を更新すること（MST-061 は一覧再取得、MST-063 はユーザー詳細再取得）。
```

---

## 要件定義書・アーキテクチャ設計書 修正要 アクションアイテム

| # | 修正対象ドキュメント | 修正箇所 | 修正内容 | 優先度 |
|---|-------------------|---------|---------|-------|
| 1 | `docs/functional-design/08-api-overview.md` | エラーコード一覧 マスタ管理共通パターン | `CANNOT_CHANGE_SELF_ROLE \| 422 \| 自分自身のロールを変更しようとした` を追加すること | 高 |
| 2 | `docs/functional-design/03-screen-master-user.md` | MST-062 INP-002・MST-063 INP-021 氏名バリデーション | 「1〜100文字」→「1〜200文字」に修正（DBカラム `varchar(200)` に準拠） | 高 |
| 3 | `docs/functional-design/03-screen-master-user.md` | MST-062 INP-003・MST-063 INP-022 メールアドレスバリデーション | 「254文字以内」→「200文字以内」に修正（DBカラム `varchar(200)` に準拠） | 高 |
| 4 | `docs/functional-design/03-screen-master-user.md` | EVT-061-002, EVT-062-002, EVT-063-001, EVT-063-004 APIパス | `/api/v1/users` → `/api/v1/master/users` に修正（`/master/` プレフィックス追加） | 高 |
| 5 | `docs/functional-design/03-screen-master-user.md` | EVT-061-006・EVT-063-002 アンロックAPIパス | `PUT /api/v1/users/{id}/unlock` → `PATCH /api/v1/master/users/{id}/unlock` に修正 | 高 |
| 6 | `docs/functional-design/03-screen-master-user.md` | EVT-062-003 重複チェックAPI | `GET /api/v1/users/exists` → `GET /api/v1/master/users/exists` に修正（API-MST-USR-007 追加により正式エンドポイントとして対応済み） | 中 |
| 7 | `docs/architecture-blueprint/10-security-architecture.md` | §パスワード管理 パスワードポリシー | 「8文字以上」に「128文字以内」の上限を追記すること（BCrypt 72バイト上限を考慮。API設計書側はAPI-MST-USR-002補足事項で先行確定済み） | 低 |

---

## 追加修正ログ（第2次レビュー）

第2次レビューで以下の追加修正を実施済み:

| # | 対象API | 修正内容 |
|---|--------|---------|
| A | `API-MST-USR-001〜007` | 対象API範囲を「〜006」→「〜007」に更新 |
| B | `API-MST-USR-002` | フローチャートINSERTノードに `updated_by = ログイン中ユーザーID`・`updated_at = NOW()` を追加 |
| C | `API-MST-USR-002` | ビジネスルール5追加: INSERT時の `updated_by` = `created_by` 同一設定ルール |
| D | `API-MST-USR-002` | 補足事項にパスワード上限128文字の設計根拠（BCrypt 72バイト上限）を追記 |
| E | `API-MST-USR-004` | 補足事項に `PUT` 選択理由（`userCode` 変更不可な実質部分更新であることの説明）を追記 |
| F | `API-MST-USR-005` | 概要テキストを修正（一覧画面対応削除→編集画面からの操作に統一）、関連画面から `MST-061` を削除（要件定義「一覧からの独立ボタンは設けない」に準拠） |
| G | `API-MST-USR-006` | 補足事項にロック解除時の `refresh_tokens` 残存に関する既知仕様を追記 |
| H | `API-MST-USR-007`（新規） | ユーザーコード存在確認APIを追加（`GET /api/v1/master/users/exists`、SYSTEM_ADMINのみ）。画面設計書 EVT-062-003 の未定義API問題を解消 |
| I | `08-api-overview.md` | API-MST-USR-005 のAPI名を「ユーザー無効化」→「ユーザー無効化/有効化」に修正 |
| J | `08-api-overview.md` | API-MST-USR-007 を全API一覧に追加 |
| K | 付録エラーコード一覧 | `VALIDATION_ERROR` の対象API列に `007` を追加 |
