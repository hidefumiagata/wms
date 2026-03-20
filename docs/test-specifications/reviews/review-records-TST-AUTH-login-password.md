# レビュー記録票: TST-AUTH-login-password.md

**レビュー実施日**: 2026-03-20
**対象ファイル**: docs/test-specifications/TST-AUTH-login-password.md
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 1 |
| 要対応（他ドキュメント） | 3 |
| 指摘なし（確認済み） | 10 |
| **合計** | **14** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | SC-001 DB検証 #2: `refresh_tokens` の `expires_at` が「現在日時 + 1時間」 | architecture-blueprint/07-auth-architecture.md, 10-security-architecture.md | アーキテクチャブループリント（SSOT）ではリフレッシュトークンの有効期限は「最終アクセスから**24時間**で失効」と定義されている。一方、API設計書（API-AUTH-003）の業務ロジックフローチャートでは「現在日時 + 1時間」と記載。テスト仕様書はAPI設計書に従い「1時間」としているが、SSoTであるアーキテクチャブループリントとの間に矛盾がある。どちらかを統一する必要がある。 | 要対応（他ドキュメント） | アーキテクチャブループリント（07, 10）とAPI設計書（API-01 API-AUTH-003）の間でリフレッシュトークン有効期限の記載を統一すること。統一後にテスト仕様書も合わせて更新する |
> **対応完了** (2026-03-20): API-01-auth.mdのリフレッシュトークン有効期限を24時間に修正完了
| 2 | テストシナリオ一覧（全体） | functional-requirements/00-authentication.md | 機能要件定義書に「パスワード変更APIの連続5回失敗でもアカウントロック（ログイン失敗ロックと同一の仕組み）」と明記されているが、このテストシナリオが存在しない。パスワード変更画面で旧パスワードを5回連続で誤った場合のロック→解除→再変更のE2Eシナリオを追加すべき | 対象ファイル修正済み | SC-022として「異常系: パスワード変更5回連続失敗でアカウントロック」シナリオを追加 |
| 3 | SC-001〜SC-004（全ロールログイン） | functional-design/SCR-01-auth.md（EVT-AUTH001-002, EVT-AUTH001-003） | SCR-01のイベント一覧 EVT-AUTH001-002〜003 の API レスポンスマッピングにメッセージID誤りがある。`INVALID_CREDENTIALS` → `MSG-E-AUTH001-001` と記載されているが、メッセージ一覧では `MSG-E-AUTH001-001` = 「ユーザーコードは必須です」（必須チェック）。正しくは `MSG-E-AUTH001-003`。同様に `ACCOUNT_LOCKED` → `MSG-E-AUTH001-002` も正しくは `MSG-E-AUTH001-004`。テスト仕様書側はメッセージ一覧に基づき正しいID（003, 004）を使用しているため修正不要 | 要対応（他ドキュメント） | SCR-01-auth.md の EVT-AUTH001-002 レスポンスマッピング内のメッセージID参照を修正すること: `INVALID_CREDENTIALS`/`ACCOUNT_INACTIVE` → `MSG-E-AUTH001-003`、`ACCOUNT_LOCKED` → `MSG-E-AUTH001-004` |
> **対応完了** (2026-03-20): SCR-01-auth.mdのメッセージIDマッピングをMSG-E-AUTH001-003に修正完了
| 4 | SC-005 メッセージID `MSG-E-AUTH001-003` | functional-design/SCR-01-auth.md メッセージ一覧 | テスト仕様書のメッセージID・メッセージ文がSCR-01のメッセージ一覧と一致していることを確認 | 指摘なし | — |
| 5 | SC-007 メッセージID `MSG-E-AUTH001-004` | functional-design/SCR-01-auth.md メッセージ一覧 | テスト仕様書のメッセージID・メッセージ文がSCR-01のメッセージ一覧と一致していることを確認 | 指摘なし | — |
| 6 | SC-008 無効化アカウント → `MSG-E-AUTH001-003` | functional-design/API-01-auth.md API-AUTH-001 | API設計書で `ACCOUNT_INACTIVE` は `INVALID_CREDENTIALS` と同一メッセージ（セキュリティ上、無効アカウントの存在を露出しない）。テスト仕様書の期待結果と一致 | 指摘なし | — |
| 7 | SC-009 アカウントロックフロー（5回失敗→ロック→解除→再ログイン） | functional-requirements/00-authentication.md, functional-design/API-01-auth.md | 機能要件・API設計と整合。DB検証項目も `locked`, `locked_at`, `failed_login_count` の遷移を正しく検証 | 指摘なし | — |
| 8 | SC-010 初回パスワード変更強制 | functional-design/SCR-01-auth.md AUTH-002 | ナビゲーションガードによる遷移ブロック（Step 2-3）、成功メッセージ `MSG-S-AUTH002-001` がSCR-01と一致 | 指摘なし | — |
| 9 | SC-012 メッセージID `MSG-E-AUTH002-007` | functional-design/SCR-01-auth.md AUTH-002 メッセージ一覧 | メッセージIDと文言が一致。DB検証で `failed_login_count` 増加を確認しているのも API-AUTH-004 の業務ロジックと整合 | 指摘なし | — |
| 10 | SC-014 `409 Conflict SAME_PASSWORD` | functional-design/API-01-auth.md API-AUTH-004 | API設計書のエラーレスポンス仕様と一致 | 指摘なし | — |
| 11 | SC-015 パスワードリセットフロー | functional-design/API-01-auth.md API-AUTH-005/006, data-model/02-master-tables.md | DB検証（password_reset_tokens の expires_at = 現在日時+30分、使用後レコード削除、users更新）がAPI設計書・データモデルと整合 | 指摘なし | — |
| 12 | SC-016/017 無効トークン `MSG-E-AUTH004-004` | functional-design/SCR-01-auth.md AUTH-004 メッセージ一覧 | メッセージID・文言・HTTPステータス（401 INVALID_TOKEN）がSCR-01・API設計書と一致 | 指摘なし | — |
| 13 | SC-018 セッションタイムアウト（55分警告→60分ログアウト） | functional-requirements/00-authentication.md | 警告タイミング・自動ログアウトタイミング・メッセージID `MSG-I-AUTH001-001` が機能要件・画面設計と一致 | 指摘なし | — |
| 14 | テストシナリオ一覧（全体） | functional-design/SCR-01-auth.md AUTH-001 | SCR-01 AUTH-001 の画面レイアウトに「残り試行回数を表示する」との記載があるが、エラーハンドリング補足で「残り回数の表示は行わない」と矛盾している。テスト仕様書には残り回数表示のテストがないが、SCR-01内の記載を統一する必要がある | 要対応（他ドキュメント） | SCR-01-auth.md の AUTH-001 セクション2「フォームエリアの詳細」の残り試行回数表示の記述を削除するか、エラーハンドリング補足セクションと統一すること |
> **対応完了** (2026-03-20): SCR-01-auth.mdの残り試行回数表示を「表示しない」に統一完了
