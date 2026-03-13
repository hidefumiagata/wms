# API設計レビュー記録票 — 施設マスタ（MST-FAC）

> 対象成果物: `docs/functional-design/10-api-master-facility.md`
> レビュー日: 2026-03-14
> レビュー担当: Java バックエンド REST API設計スペシャリスト（AI）
> 参照ドキュメント: `docs/functional-design/08-api-overview.md`、`docs/functional-requirements/01-master-management.md`、`docs/data-model/02-master-tables.md`、`docs/functional-design/03-screen-master-facility.md`、`docs/functional-design/03-screen-master-partner-warehouse.md`

---

## エグゼクティブサマリー

| 分類 | 件数 |
|------|------|
| **API設計書修正済**（レビュー時に自動修正） | 6件 |
| **要対応**（他ドキュメントへの変更が必要） | 5件 |
| 指摘事項なし | 6件 |
| **総チェック項目** | 17件 |

---

| No | 対象API | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|--------|----------------|---------|------|---------|
| 1 | 共通仕様（冒頭） | `08-api-overview.md` エラー仕様 | 共通エラーレスポンス形式のフィールド名が `"fieldErrors"` となっているが、08-api-overview.mdの標準は `"details"` | **API設計書修正済** | `fieldErrors` → `details` に修正済 |
| 2 | API-MST-FAC-002 | `01-master-management.md` §3 管理項目 | `warehouseCode` バリデーションが「最大50文字、英数字・ハイフンのみ」となっているが、要件定義書の定義は「英大文字4文字固定（例: WARA）」 | **API設計書修正済** | バリデーションを `^[A-Z]{4}$`（英大文字4文字固定）に修正済 |
| 3 | API-MST-FAC-014 | `08-api-overview.md` テンプレート | セクション5（補足事項）が欠落 | **API設計書修正済** | トランザクション境界・楽観ロックに関する補足事項を追加済 |
| 4 | API-MST-FAC-014 | — | ビジネスルール表に「無効化済みでも更新可能」ルールが未記載 | **API設計書修正済** | BR-002「無効化済みの棟でも更新可能」を追加済 |
| 5 | API-MST-FAC-023 | `08-api-overview.md` テンプレート | 業務ロジックセクションにビジネスルール表が欠落（他の取得APIとの統一性が不足） | **API設計書修正済** | ビジネスルール表（存在チェック、無効化済み取得可能）を追加済 |
| 6 | API-MST-FAC-024 | `08-api-overview.md` テンプレート | セクション5（補足事項）が欠落。BR-003（無効化済みでも更新可能）も未記載 | **API設計書修正済** | セクション5および BR-003 を追加済 |
| 7 | API-MST-FAC-033 | `08-api-overview.md` テンプレート | 業務ロジックセクションにビジネスルール表が欠落 | **API設計書修正済** | ビジネスルール表（存在チェック、無効化済み取得可能）を追加済 |
| 8 | 全20API | `08-api-overview.md` テンプレート | 1. API概要テーブルに「認証」行が欠落（「認可ロール」のみ記載、「認証: 要」が省略されている） | **要対応** | 全APIの概要テーブルに「**認証** \| 要（JWT httpOnly Cookie）」行を追加すること。または、ファイル冒頭の共通仕様で「全APIはJWT認証必須」と明記されているため許容範囲とするか方針を確認 |
| 9 | 全20API | `08-api-overview.md` テンプレート | 1. API概要テーブルに「関連画面」行が欠落（全APIで省略） | **要対応** | 各APIに対応する画面IDを関連画面として追加すること（例: FAC-001→MST-021, FAC-011→MST-031 等） |
| 10 | API-MST-FAC-002/012/022/032 | `03-screen-master-facility.md` | 棟名称・エリア名称・ロケーション名称の文字数制限が画面設計書では「100文字」だが、DBカラム定義・API設計書では「200文字」で不整合 | **要対応** | `03-screen-master-facility.md` の MST-032/042/043/052/053 フォームバリデーションの名称フィールド上限を100文字 → 200文字に修正すること |
| 11 | API-MST-FAC-008/018/028/038 相当 | `08-api-overview.md` エラーコード一覧 | `CANNOT_DEACTIVATE_STOCKTAKE_IN_PROGRESS`（FAC-035で使用）、`AREA_LOCATION_LIMIT_EXCEEDED`（FAC-032で使用）、`INVALID_LOCATION_CODE_FORMAT`（FAC-032で使用）が08-api-overview.mdのエラーコード一覧に未定義 | **要対応** | `08-api-overview.md` のエラーコード一覧（施設マスタ区分）に上記3エラーコードを追加すること |
| 12 | API-MST-FAC-002 | `03-screen-master-partner-warehouse.md` MST-022 | 棟登録画面（MST-032）に「有効/無効」ラジオボタンが存在するが、API設計ではisActive=true固定（登録時の有効フラグはON固定の共通ルール）。画面設計が実装と乖離する可能性がある | **要対応** | `03-screen-master-facility.md` の MST-032 から「有効/無効」ラジオボタンを削除し、「登録時はON固定」の注記に変更すること |
| 13 | API-MST-FAC-001〜035 | `02-master-tables.md` | warehouses/buildings/areas/locationsテーブルのカラム名・型とリクエスト/レスポンスフィールドの全体整合確認 | 指摘事項なし | — |
| 14 | API-MST-FAC-001〜035 | `08-api-overview.md` 全API一覧 | 全20APIのメソッド・パス・ロールが全API一覧の定義と一致することを確認 | 指摘事項なし | — |
| 15 | API-MST-FAC-005/015/025/035 | `08-api-overview.md` エラーコード一覧 | CANNOT_DEACTIVATE_HAS_CHILDREN（配下データあり）が適切に使用されていることを確認 | 指摘事項なし | — |
| 16 | API-MST-FAC-031/032 | `01-master-management.md` §6 | ロケーションコードの自動採番形式（棟-フロア-エリア-棚-段-並び）がAPI設計書に正しく反映されていることを確認 | 指摘事項なし | — |
| 17 | API-MST-FAC-001/011/021/031 | `01-master-management.md` §3 | 倉庫マスタ管理画面はヘッダーの倉庫切替の影響を受けない設計（全倉庫一覧を表示）がAPIの仕様と整合していることを確認 | 指摘事項なし | — |

---

## 修正対応ログ

### No.1 対応詳細
**修正対象**: `docs/functional-design/10-api-master-facility.md` 冒頭 共通エラーレスポンス形式
- **修正前**: `"fieldErrors": [...]`
- **修正後**: `"details": [...]`
- **修正理由**: 08-api-overview.md の標準エラーレスポンス形式では `details` フィールドを使用。統一性のため修正。

### No.2 対応詳細
**修正対象**: `docs/functional-design/10-api-master-facility.md` API-MST-FAC-002 リクエスト仕様
- **修正前**: `warehouseCode`: 最大50文字、英数字・ハイフンのみ（正規表現: `^[A-Za-z0-9\-]+$`）
- **修正後**: `warehouseCode`: 英大文字4文字固定（正規表現: `^[A-Z]{4}$`）（例: `WARA`）
- **修正理由**: `01-master-management.md` §3 管理項目に「英大文字4文字固定」と明記されている

### No.3〜7 対応詳細
**修正対象**: 各API セクション欠落への補完
- FAC-014: BR-002追加 + セクション5（補足事項）追加
- FAC-023: ビジネスルール表追加
- FAC-024: BR-003追加 + セクション5（補足事項）追加
- FAC-033: ビジネスルール表追加

---

## 要件定義書・アーキテクチャ設計書 修正要 アクションアイテム

| # | 修正対象ドキュメント | 修正箇所 | 修正内容 | 優先度 |
|---|-------------------|---------|---------|-------|
| 1 | `docs/functional-design/03-screen-master-facility.md` | MST-032/042/043/052/053 フォームバリデーション | 棟名称・エリア名称・ロケーション名称の最大文字数を100文字 → 200文字に修正すること（DBカラム定義 varchar(200) に準拠） | 中 |
| 2 | `docs/functional-design/03-screen-master-facility.md` | MST-032 有効/無効ラジオボタン | 棟登録画面の「有効/無効」ラジオボタンを削除し、「登録時はON固定（全マスタ共通仕様）」の注記に変更すること | 中 |
| 3 | `docs/functional-design/08-api-overview.md` | エラーコード一覧 施設マスタ | 以下のエラーコードを追加すること: `CANNOT_DEACTIVATE_STOCKTAKE_IN_PROGRESS`（422: 棚卸中のため無効化不可）、`AREA_LOCATION_LIMIT_EXCEEDED`（422: エリアのロケーション登録上限超過）、`INVALID_LOCATION_CODE_FORMAT`（400: ロケーションコード形式不正） | 中 |
| 4 | `docs/functional-design/10-api-master-facility.md` | 全20API 概要テーブル | 「認証」行（`認証 \| 要（JWT httpOnly Cookie）`）を追加するか、共通仕様に「全APIはJWT認証必須」の明記で代替するかの方針を確認して統一すること | 低 |
| 5 | `docs/functional-design/10-api-master-facility.md` | 全20API 概要テーブル | 「関連画面」行を各APIに追加すること（FAC-001→MST-021, FAC-002→MST-022, FAC-011→MST-031, FAC-012→MST-032, FAC-021→MST-041, FAC-022→MST-042, FAC-031→MST-051, FAC-032→MST-052 等） | 低 |
