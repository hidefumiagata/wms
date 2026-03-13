# API設計レビュー記録票 — 取引先マスタ（MST-PAR）

> 対象成果物: `docs/functional-design/10-api-master-partner.md`
> レビュー日: 2026-03-14
> レビュー担当: Java バックエンド REST API設計スペシャリスト（AI）
> 参照ドキュメント: `docs/functional-design/08-api-overview.md`、`docs/functional-requirements/01-master-management.md`、`docs/data-model/02-master-tables.md`、`docs/functional-design/03-screen-master-partner-warehouse.md`

---

| No | 対象API | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|--------|----------------|---------|------|---------|
| 1 | API-MST-PAR-001〜005 | `08-api-overview.md` テンプレート | 全5APIが5セクション形式（概要/リクエスト/レスポンス/業務ロジック/補足）に準拠していることを確認 | 指摘事項なし | — |
| 2 | API-MST-PAR-001〜005 | `08-api-overview.md` 全API一覧 | メソッド・パス・ロールが全API一覧の定義と一致していることを確認 | 指摘事項なし | — |
| 3 | API-MST-PAR-001〜005 | `02-master-tables.md` partnersテーブル | リクエスト/レスポンスのフィールド名がDBカラム名（スネークケース→キャメルケース変換）と整合していることを確認 | 指摘事項なし | — |
| 4 | API-MST-PAR-001 | `03-screen-master-partner-warehouse.md` | 関連画面IDが `MST-PAR-001`（誤）と記載されていた。正しくは `MST-011`（取引先一覧） | **API設計書修正済** | 関連画面を `MST-PAR-001` → `MST-011` に修正済 |
| 5 | API-MST-PAR-002 | `03-screen-master-partner-warehouse.md` | 関連画面IDが `MST-PAR-002`（誤）と記載されていた。正しくは `MST-012`（取引先登録） | **API設計書修正済** | 関連画面を `MST-PAR-002` → `MST-012` に修正済 |
| 6 | API-MST-PAR-003 | `03-screen-master-partner-warehouse.md` | 関連画面IDが `MST-PAR-003`、`MST-PAR-004`（誤）と記載されていた。正しくは `MST-011`（一覧）・`MST-013`（編集） | **API設計書修正済** | 関連画面を `MST-011, MST-013` に修正済 |
| 7 | API-MST-PAR-004 | `03-screen-master-partner-warehouse.md` | 関連画面IDが `MST-PAR-004`（誤）と記載されていた。正しくは `MST-013`（取引先編集） | **API設計書修正済** | 関連画面を `MST-PAR-004` → `MST-013` に修正済 |
| 8 | API-MST-PAR-005 | `03-screen-master-partner-warehouse.md` | 関連画面IDが `MST-PAR-001`、`MST-PAR-003`（誤）と記載されていた。正しくは `MST-011`（一覧）・`MST-013`（編集） | **API設計書修正済** | 関連画面を `MST-011, MST-013` に修正済 |
| 9 | API-MST-PAR-001 | `08-api-overview.md` 標準レスポンス形式 | `all=true` パラメータによるシンプルリスト切り替え設計は08-api-overview.mdの標準仕様を拡張したもの。取引先プルダウン用途（仕入先のみ・出荷先のみのフィルタ）として妥当 | 指摘事項なし | — |
| 10 | API-MST-PAR-002/004 | `01-master-management.md` §2 管理項目 | `partnerNameKana`（フリガナ）はデータモデルおよびAPI設計ではNULL許容（任意）だが、画面設計では「必須」として設計されている可能性がある | **要対応** | `03-screen-master-partner-warehouse.md` のMST-012/MST-013フォームバリデーションでの `partnerNameKana` 必須/任意の設定を確認・修正すること |
| 11 | API-MST-PAR-002/004 | `01-master-management.md` §2 管理項目 | `partnerCode` の最大文字数: 画面設計「20文字」に対しデータモデル・API設計では「半角英数字・記号、50文字以内」（01-master-management.md対応済）と不整合 | **要対応** | `03-screen-master-partner-warehouse.md` のMST-012 `partnerCode` バリデーションの最大文字数を50文字に修正すること |
| 12 | API-MST-PAR-001〜005 | `03-screen-master-partner-warehouse.md` | 画面設計書内のAPIパス参照が `/api/v1/partners` および `/api/v1/partners/{id}/disable`・`/api/v1/partners/{id}/enable` となっており、正しい `/api/v1/master/partners` および `/api/v1/master/partners/{id}/deactivate` と不整合 | **要対応** | `03-screen-master-partner-warehouse.md` の全APIパス参照を修正すること（プレフィックス `/master/` の追加、`/disable`・`/enable` → `/deactivate` への変更） |
| 13 | API-MST-PAR-005 | `08-api-overview.md` エラーコード一覧 | 取引先無効化時に入荷中・出荷中の伝票が存在する場合のエラーコード（`CANNOT_DEACTIVATE_HAS_ACTIVE_INBOUND` / `CANNOT_DEACTIVATE_HAS_ACTIVE_OUTBOUND` 相当）が08-api-overview.mdのエラーコード一覧に未定義 | **要対応** | `08-api-overview.md` のエラーコード一覧（マスタ管理共通パターン）に入荷中・出荷中伝票が存在する取引先の無効化不可エラーコードを追加すること |
| 14 | API-MST-PAR-001〜005 | `08-api-overview.md` エラーコード一覧 | PARTNER_NOT_FOUND, DUPLICATE_CODE, CANNOT_DEACTIVATE_HAS_CHILDREN（本来は配下テーブルなし）が定義済みであることを確認 | 指摘事項なし | — |
| 15 | API-MST-PAR-002/004 | `01-master-management.md` §2 ビジネスルール | `partnerType` は `SUPPLIER` / `CUSTOMER` / `BOTH` の3値。種別によって入荷元・出荷先の選択可否が異なるルールがAPI業務ロジックに記載されていることを確認 | 指摘事項なし | — |

---

## 修正対応ログ

### No.4〜8 対応詳細

**修正対象**: `docs/functional-design/10-api-master-partner.md`

**修正内容**: 各APIの「関連画面」フィールドに使用されていた架空の画面ID（`MST-PAR-001`〜`MST-PAR-004`）を実際の画面ID（`MST-011`〜`MST-013`）に修正。

| API | 修正前 | 修正後 |
|-----|--------|--------|
| API-MST-PAR-001 | MST-PAR-001 | MST-011 |
| API-MST-PAR-002 | MST-PAR-002 | MST-012 |
| API-MST-PAR-003 | MST-PAR-003, MST-PAR-004 | MST-011, MST-013 |
| API-MST-PAR-004 | MST-PAR-004 | MST-013 |
| API-MST-PAR-005 | MST-PAR-001, MST-PAR-003 | MST-011, MST-013 |

---

## 要件定義書・アーキテクチャ設計書 修正要 アクションアイテム

| # | 修正対象ドキュメント | 修正箇所 | 修正内容 | 優先度 |
|---|-------------------|---------|---------|-------|
| 1 | `docs/functional-design/03-screen-master-partner-warehouse.md` | MST-011 EVT-MST011-001等 APIパス参照 | `/api/v1/partners` → `/api/v1/master/partners` に修正すること | 高 |
| 2 | `docs/functional-design/03-screen-master-partner-warehouse.md` | MST-011 EVT-MST011-006/007 APIパス参照 | `/api/v1/partners/{id}/disable` / `/api/v1/partners/{id}/enable` → `/api/v1/master/partners/{id}/deactivate` に修正すること（有効化・無効化は同一エンドポイント、`isActive` フラグで制御） | 高 |
| 3 | `docs/functional-design/03-screen-master-partner-warehouse.md` | MST-012 `partnerCode` バリデーション | 最大文字数を20文字 → 50文字に修正すること（`01-master-management.md` §2 管理項目の定義に準拠） | 中 |
| 4 | `docs/functional-design/03-screen-master-partner-warehouse.md` | MST-012/MST-013 `partnerNameKana` バリデーション | データモデル・API設計ではNULL許容（任意項目）であるため、画面設計での「必須」バリデーション設定を「任意」に修正すること（または要件定義書で必須に統一する方針を明確化） | 中 |
| 5 | `docs/functional-design/08-api-overview.md` | エラーコード一覧 マスタ管理共通パターン | `CANNOT_DEACTIVATE_HAS_ACTIVE_INBOUND`（422）と `CANNOT_DEACTIVATE_HAS_ACTIVE_OUTBOUND`（422）を追加すること → **対応済** |

---

## 追加修正ログ（第2次レビュー）

| # | 対象 | 修正内容 |
|---|------|---------|
| A | `10-api-master-partner.md` | 入荷ステータス `PENDING` を正しい値 `PLANNED`、`CONFIRMED` に修正（エラーレスポンス表・フローチャートSQL・ビジネスルール表の3箇所） |
| B | `08-api-overview.md` | `CANNOT_DEACTIVATE_HAS_ACTIVE_TRANSACTION` を `CANNOT_DEACTIVATE_HAS_ACTIVE_INBOUND` + `CANNOT_DEACTIVATE_HAS_ACTIVE_OUTBOUND` に分割して追加 |
