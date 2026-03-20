# API設計レビュー記録票 — 商品マスタ（MST-PRD）

> 対象成果物: `docs/functional-design/10-api-master-product.md`
> レビュー日: 2026-03-14
> レビュー担当: Java バックエンド REST API設計スペシャリスト（AI）
> 参照ドキュメント: `docs/functional-design/08-api-overview.md`、`docs/functional-requirements/01-master-management.md`、`docs/data-model/02-master-tables.md`、`docs/functional-design/03-screen-master-product.md`

---

| No | 対象API | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|--------|----------------|---------|------|---------|
| 1 | API-MST-PRD-001〜005 | `08-api-overview.md` テンプレート | 全5APIが5セクション形式（概要/リクエスト/レスポンス/業務ロジック/補足）に準拠していることを確認 | 指摘事項なし | — |
| 2 | API-MST-PRD-001〜005 | `08-api-overview.md` 全API一覧 | メソッド・パス・ロールが全API一覧の定義と一致していることを確認 | 指摘事項なし | — |
| 3 | API-MST-PRD-001〜005 | `02-master-tables.md` productsテーブル | リクエスト/レスポンスのフィールド名がDBカラム名（スネークケース→キャメルケース変換）と整合していることを確認 | 指摘事項なし | — |
| 4 | API-MST-PRD-001 | `02-master-tables.md` productsテーブル | クエリパラメータ `shipmentStop` がレスポンスフィールド名 `shipmentStopFlag`・DBカラム `shipment_stop_flag` と命名不統一 | **API設計書修正済** | クエリパラメータを `shipmentStop` → `shipmentStopFlag` に修正。Mermaidフローチャート内の記述も同様に修正済 |
| 5 | API-MST-PRD-001 | `01-master-management.md` §1 管理項目 | `unit`（単位）カラムが削除済みであることを確認。API設計書に単位フィールドは含まれていない | 指摘事項なし | — |
| 6 | API-MST-PRD-001 | `01-master-management.md` §1 管理項目 | カテゴリ項目が削除済みであることを確認。API設計書にカテゴリフィールドは含まれていない | 指摘事項なし | — |
| 7 | API-MST-PRD-001〜005 | `08-api-overview.md` 認証・認可仕様 | 参照系API（001/003）は全ロール対象、更新系API（002/004/005）はSYSTEM_ADMIN・WAREHOUSE_MANAGERのみ。定義と整合 | 指摘事項なし | — |
| 8 | API-MST-PRD-004 | `01-master-management.md` §1 ビジネスルール | 商品コード変更不可ルール（`productCode` はリクエストボディで受け取っても無視）が補足事項に明記されている | 指摘事項なし | — |
| 9 | API-MST-PRD-005 | `08-api-overview.md` エラーコード一覧 | `CANNOT_DEACTIVATE_HAS_INVENTORY` が適切に使用されている | 指摘事項なし | — |
| 10 | API-MST-PRD-004 | `02-master-tables.md` productsテーブル | ロット管理フラグ・賞味期限管理フラグの変更禁止（在庫存在時）ルールがビジネスルール表に記載されているが、対応するエラーコード（`CANNOT_CHANGE_LOT_MANAGE_FLAG` 相当）が08-api-overview.mdのエラーコード一覧に未定義 | **要対応** | `_standard-api.md`（旧`08-api-overview.md`）のエラーコード一覧（マスタ管理共通パターン）に `CANNOT_CHANGE_LOT_MANAGE_FLAG`（422）および `CANNOT_CHANGE_EXPIRY_MANAGE_FLAG`（422）が追加済み。✅ 対応完了（2026-03-18確認） |
| 11 | API-MST-PRD-001〜005 | `03-screen-master-product.md` | 画面仕様のAPIパスが `/api/v1/products`（旧パス）と記述されている箇所があり、正しくは `/api/v1/master/products` | **要対応** | `SCR-05-master-product.md`（旧`03-screen-master-product.md`）の全APIパス記述が `/api/v1/master/products` に統一済み。✅ 対応完了（2026-03-18確認） |
| 12 | API-MST-PRD-002/004 | `03-screen-master-product.md` | 画面設計書に「カテゴリ」「単位」の入力フィールドが残存しているが、データモデルから削除済みのため整合していない | **要対応** | `03-screen-master-product.md` のMST-002・MST-003画面仕様からカテゴリ・単位フィールドの記述を削除すること ✅ 対応完了（2026-03-18修正） |
| 13 | API-MST-PRD-002/004/005 | `03-screen-master-product.md` | 画面設計書の対象ロールが「SYSTEM_ADMINのみ」と記述されているが、API仕様では「SYSTEM_ADMIN, WAREHOUSE_MANAGER」が更新可能。整合していない | **要対応** | `SCR-05-master-product.md`（旧`03-screen-master-product.md`）の対象ロールがMST-001/MST-002/MST-003すべてで「SYSTEM_ADMIN, WAREHOUSE_MANAGER」に統一済み。✅ 対応完了（2026-03-18確認） |
| 14 | API-MST-PRD-001〜005 | `08-api-overview.md` エラーコード一覧 | 全エラーコード（PRODUCT_NOT_FOUND, DUPLICATE_CODE等）が一覧に定義済みであることを確認 | 指摘事項なし | — |
| 15 | API-MST-PRD-001〜005 | `08-api-overview.md` 標準レスポンス形式 | 一覧APIはページングレスポンス形式、単一APIは直接リソース返却形式に準拠していることを確認 | 指摘事項なし | — |

---

## 修正対応ログ

### No.4 対応詳細

**修正対象**: `docs/functional-design/10-api-master-product.md`

**修正箇所1**: API-MST-PRD-001 クエリパラメータ表
- **修正前**: `| \`shipmentStop\` | Boolean | — | — | 出荷禁止フラグ... |`
- **修正後**: `| \`shipmentStopFlag\` | Boolean | — | — | 出荷禁止フラグ... |`

**修正箇所2**: API-MST-PRD-001 業務ロジック Mermaidフローチャート
- **修正前**: `APPLY_FILTERS --> FILTER_STOP[shipmentStop: 完全一致]`
- **修正後**: `APPLY_FILTERS --> FILTER_STOP[shipmentStopFlag: 完全一致]`

**修正理由**: DBカラム名 `shipment_stop_flag` に対応するJavaフィールド/APIパラメータ名は `shipmentStopFlag` が正しく、レスポンスボディ定義と統一するため修正。

---

## 要件定義書・アーキテクチャ設計書 修正要 アクションアイテム

| # | 修正対象ドキュメント | 修正箇所 | 修正内容 | 優先度 | 対応状況 |
|---|-------------------|---------|---------|-------|---------|
| 1 | `docs/functional-design/08-api-overview.md` | エラーコード一覧 マスタ管理共通パターン | 在庫存在時のフラグ変更不可エラーコードを追加すること（例: `CANNOT_CHANGE_LOT_MANAGE_FLAG` / `CANNOT_CHANGE_EXPIRY_MANAGE_FLAG`、HTTPステータス 422） | 中 | ✅ 対応完了（2026-03-18確認） |
| 2 | `docs/functional-design/03-screen-master-product.md` | 全画面のAPIパス記述 | `/api/v1/products` → `/api/v1/master/products` に修正すること | 高 | ✅ 対応完了（2026-03-18確認） |
| 3 | `docs/functional-design/03-screen-master-product.md` | MST-002/MST-003 フォーム項目 | データモデルから削除済みの「カテゴリ」「単位」フィールドの記述を削除すること | 中 | ✅ 対応完了（2026-03-18修正）MST-001/MST-002/MST-003のASCIIレイアウト図からカテゴリ・単位を削除済み |
| 4 | `docs/functional-design/03-screen-master-product.md` または `docs/functional-requirements/01-master-management.md` | 対象ロール定義 | 商品マスタの更新操作（登録・更新・無効化）を「SYSTEM_ADMINのみ」に限定するか「SYSTEM_ADMIN, WAREHOUSE_MANAGER」に拡張するかを明確化し、API設計書・画面設計書を統一すること | 高 | ✅ 対応完了（2026-03-18確認） |
