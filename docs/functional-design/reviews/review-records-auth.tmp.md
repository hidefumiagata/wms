# レビュー記録票 — 認証（AUTH）

> 対象成果物: `docs/functional-design/02-screen-auth.md`（AUTH-002）、`docs/functional-design/mockups/AUTH-002-change-password.html`
> レビュー日: 2026-03-13
> 参照ドキュメント: `docs/functional-requirements/` 配下、`docs/architecture-blueprint/` 配下

---

| No | 対象画面 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|---------|----------------|---------|------|---------|
| 1 | AUTH-002 | `docs/architecture-blueprint/10-security-architecture.md` — パスワード管理 | パスワードポリシーが「8文字以上」と定義されている。画面仕様の `MSG-E-012` および項目バリデーション欄に「8文字以上」と正しく記載されており整合している。 | 指摘事項なし | — |
| 2 | AUTH-002 | `docs/architecture-blueprint/10-security-architecture.md` — 初回フラグ | `password_change_required` フラグの存在が明記されている。画面仕様の EVT-AUTH002-001 および EVT-AUTH002-003 においてフロントエンド Pinia ストアでこのフラグを管理し、成功時に `false` に更新する旨を記載。整合している。 | 指摘事項なし | — |
| 3 | AUTH-002 | `docs/architecture-blueprint/10-security-architecture.md` — 認証関連エンドポイント | `POST /api/v1/auth/change-password` が定義されている。画面仕様の EVT-AUTH002-003 に同エンドポイントおよびリクエストボディを記載。整合している。 | 指摘事項なし | — |
| 4 | AUTH-002 | `docs/architecture-blueprint/07-auth-architecture.md` — ロール定義 | ロールは SYSTEM_ADMIN / WAREHOUSE_MANAGER / WAREHOUSE_STAFF / VIEWER の4種。初回ログインは全ロールが対象であり、画面仕様の「対象ロール: 全ロール」と整合している。 | 指摘事項なし | — |
| 5 | AUTH-002 | `docs/architecture-blueprint/13-non-functional-requirements.md` — セキュリティ要件「初回ログイン」 | 「パスワード変更を強制（変更完了まで他操作不可）」と定義されている。画面仕様の EVT-AUTH002-001「Vue Router ナビゲーションガードでブラウザバック・URL直接入力含め一切の迂回を防止」と整合している。 | 指摘事項なし | — |
| 6 | AUTH-002 | `docs/architecture-blueprint/13-non-functional-requirements.md` — セキュリティ要件「パスワードポリシー」 | 「8文字以上。BCrypt ハッシュ保存」と定義されている。BCrypt はバックエンド処理であり画面仕様に記載不要。フロントエンドバリデーション（8文字以上）は仕様に記載済み。整合している。 | 指摘事項なし | — |
| 7 | AUTH-002 | `docs/architecture-blueprint/03-frontend-architecture.md` — フォームバリデーション | フォームバリデーションに「VeeValidate + Zod」を使用すると定義されている。画面仕様では「Element Plus フォームバリデーション」と記述していたが、アーキテクチャ定義では VeeValidate + Zod が採用技術として確定している。誤記のため修正。 | 画面仕様修正済 | `02-screen-auth.md` — セクション5メッセージ一覧の備考欄の表示方法を「VeeValidate + Zod によるフォームバリデーション」に修正済 |
| 8 | AUTH-002 | `docs/architecture-blueprint/03-frontend-architecture.md` — API通信 | レスポンスインターセプターで「401エラー時にログイン画面へリダイレクト」と定義されている。EVT-AUTH002-003 に「401 Unauthorized → `/login` へリダイレクト」を記載済みであり整合している。 | 指摘事項なし | — |
| 9 | AUTH-002 | `docs/architecture-blueprint/10-security-architecture.md` — ログイン失敗ロック | ロック条件（連続5回失敗）は AUTH-001（ログイン）の仕様範囲。AUTH-002 はすでに認証済みユーザーが対象であるため、ロック機能との直接の関連はない。ただし `POST /api/v1/auth/change-password` の失敗回数に対するロックポリシーは要件定義書・アーキテクチャ設計書に未定義。 | 要件定義書修正要 | `docs/architecture-blueprint/10-security-architecture.md` の「パスワード管理」テーブルに「パスワード変更API の連続失敗に対するロックポリシー」の記述を追加すること。画面仕様は当該ポリシーが未定義であるため、現時点で MSG-E-016 の注記として「失敗時のロックポリシーは要件定義後に反映」と追記済。 |
| 10 | AUTH-002 | `docs/architecture-blueprint/13-non-functional-requirements.md` — ユーザビリティ要件 | 多言語対応（vue-i18n）が要件として定義されている。画面仕様のメッセージ一覧は日本語のみ記載されている。これは他の画面設計書も同様の方針（日本語で作成→後で英語翻訳）であり、CLAUDE.md のドキュメント作成ルールと整合している。 | 指摘事項なし | — |

---

## 修正対応ログ

### No.7 対応詳細

**修正箇所**: `docs/functional-design/02-screen-auth.md` セクション5「メッセージ一覧」の末尾補足

**修正前**:
```
- `MSG-E-010` 〜 `MSG-E-015`: フォーム項目直下にインラインエラーとして表示（Element Plus フォームバリデーション）
```

**修正後**:
```
- `MSG-E-010` 〜 `MSG-E-015`: フォーム項目直下にインラインエラーとして表示（VeeValidate + Zod によるフォームバリデーション）
```

### No.9 対応詳細

**修正箇所**: `docs/functional-design/02-screen-auth.md` セクション5「メッセージ一覧」MSG-E-016 の備考追記

`MSG-E-016` の備考に「パスワード変更APIの連続失敗ロックポリシーは未定義のため、要件確定後に画面仕様を更新すること」を注記として追加済。

---

## 要件定義書修正要 アクションアイテム

| # | 修正対象ドキュメント | 修正内容 | 優先度 |
|---|-------------------|---------|-------|
| 1 | `docs/architecture-blueprint/10-security-architecture.md` | 「パスワード管理」テーブルに `POST /api/v1/auth/change-password` の連続失敗ロックポリシー（失敗回数上限・ロック動作・解除方法）を追記する | 中 |
