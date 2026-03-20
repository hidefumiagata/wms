# API設計レビュー記録票 — 出荷管理（OUT）

> 対象成果物: `docs/functional-design/13-api-outbound.md`
> レビュー日: 2026-03-14
> レビュー担当: Java バックエンド REST API設計スペシャリスト（AI）
> 参照ドキュメント: `docs/functional-design/08-api-overview.md`、`docs/functional-requirements/04-outbound-management.md`、`docs/data-model/03-transaction-tables.md`、`docs/functional-design/06-screen-outbound.md`

---

| No | 対象API | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|--------|----------------|---------|------|---------|
| 1 | テーブル定義 | `03-transaction-tables.md` outbound_slips | `slip_number` の型が設計書 `VARCHAR(30)` に対しデータモデルは `varchar(50)` と不整合 | **API設計書修正済** | 設計書の `slip_number` / `transfer_slip_number` を `VARCHAR(30)` → `varchar(50)` に修正済 |
| 2 | API-OUT-011 | `08-api-overview.md` テンプレート | セクション4（業務ロジック）にMermaidフローチャートはあるがビジネスルール表が欠落 | **API設計書修正済** | ビジネスルール表（warehouseId必須、倉庫存在チェック、0件時の挙動）を追加済 |
| 3 | API-OUT-011 | `08-api-overview.md` テンプレート | セクション5（補足事項）が欠落 | **API設計書修正済** | 補足事項（warehouseId必須の理由、lineCountの取得方法、ソートデフォルト）を追加済 |
| 4 | API-OUT-012 | `03-transaction-tables.md` | ビジネスルール5「`picking_instruction_lines` の `qty_to_pick` は対応する `picking_instruction_lines`（引当時に生成されたもの）の `qty_to_pick` をそのままセットする」が自己参照になっており論理的に誤り | **API設計書修正済** | `inventory_allocations` テーブルの `allocated_qty`（引当数量）をセットする旨に修正済 |
| 5 | API-OUT-013 | `08-api-overview.md` テンプレート | セクション4にMermaidフローチャートはあるがビジネスルール表が欠落 | **API設計書修正済** | ビジネスルール表（存在チェック、明細ソート）を追加済 |
| 6 | API-OUT-013 | `08-api-overview.md` テンプレート | セクション5（補足事項）が欠落 | **API設計書修正済** | 補足事項（ステータス制限なし、createdByName取得、completedAt/completedByのnull挙動）を追加済 |
| 7 | API-OUT-021 | `03-transaction-tables.md` outbound_slip_lines | レスポンスJSON・ビジネスルール表で `inspectedQty` フィールドを使用しているが、`outbound_slip_lines` テーブルに `inspected_qty` カラムは存在しない。フローチャートでは `shipped_qty` に一時格納と正しく記述されている | **API設計書修正済** | セクション5補足事項に「inspectedQtyの格納先はshipped_qty（一時格納）であり、out-022完了時に確定する」旨を明記済 |
| 8 | API-OUT-021 | `review-records.md` O-5 | 途中保存対応（MSG-S-107追加済み）。API設計側でも複数回のPOST /inspect呼び出しを許容し、後続呼び出しで上書きする設計が必要 | **API設計書修正済** | ビジネスルール4「途中保存（部分的な入力）可能」および補足事項に追記済 |
| 9 | API-OUT-001〜004 | `08-api-overview.md` テンプレート | 受注管理APIs（001〜004）の5セクション形式への準拠を確認 | 指摘事項なし | — |
| 10 | API-OUT-014 | `04-outbound-management.md` §6 | ピッキング完了登録時に受注ステータスを `PICKING_COMPLETED` に更新する設計が業務フローと整合していることを確認 | 指摘事項なし | — |
| 11 | API-OUT-022 | `04-outbound-management.md` §8 | 出荷完了時に在庫を実減算する設計（`inventories.quantity` から `shipped_qty` を減算）が業務要件と整合していることを確認 | 指摘事項なし | — |
| 12 | API-OUT-002 | `review-records.md` O-1 | 倉庫振替時の入荷伝票自動生成についてAPI設計書に明記されていることを確認（補足事項に記載済み） | 指摘事項なし | — |
| 13 | API-OUT-012 | `review-records.md` O-4 | バッチピッキング後の受注別検品遷移フロー（対象受注一覧+「出荷検品へ」ボタン）はAPI-OUT-012のレスポンスに関連受注IDリストが含まれており対応済みであることを確認 | 指摘事項なし | — |
| 14 | API-OUT-001〜022 | `08-api-overview.md` エラーコード一覧 | OUTBOUND_SLIP_NOT_FOUND, OUTBOUND_INVALID_STATUS, PICKING_NOT_FOUND, ALLOCATION_INSUFFICIENT 等が適切に使用されていることを確認 | 指摘事項なし | — |
| 15 | 全体 | `08-api-overview.md` 認証・認可仕様 | ロール別アクセス権限マトリクスとの整合を確認。参照系は全ロール、更新系はSYSTEM_ADMIN/WAREHOUSE_MANAGER/WAREHOUSE_STAFF、在庫引当はSYSTEM_ADMIN/WAREHOUSE_MANAGERのみ | 指摘事項なし | — |

---

## 修正対応ログ

### No.1 対応詳細
**修正対象**: `docs/functional-design/13-api-outbound.md` テーブル定義セクション
- `slip_number` / `transfer_slip_number` の型: `VARCHAR(30)` → `varchar(50)`
- **修正理由**: `docs/data-model/03-transaction-tables.md` の定義は `varchar(50)` であり、設計書内テーブル定義が古い値を参照していた

### No.2〜3 対応詳細
**修正対象**: `docs/functional-design/13-api-outbound.md` API-OUT-011 セクション4・5
- セクション4にビジネスルール表を追加
- セクション5に補足事項を追加

### No.4 対応詳細
**修正対象**: `docs/functional-design/13-api-outbound.md` API-OUT-012 ビジネスルール5
- **修正前**: `picking_instruction_lines`（引当時に生成されたもの）の `qty_to_pick` を参照（自己参照）
- **修正後**: `inventory_allocations` テーブルの `allocated_qty`（引当数量）を参照

### No.5〜6 対応詳細
**修正対象**: `docs/functional-design/13-api-outbound.md` API-OUT-013 セクション4・5
- セクション4にビジネスルール表を追加
- セクション5に補足事項を追加

### No.7〜8 対応詳細
**修正対象**: `docs/functional-design/13-api-outbound.md` API-OUT-021 セクション4・5
- ビジネスルール4に途中保存対応を追記
- セクション5に `inspectedQty` → `shipped_qty` 一時格納の設計説明と途中保存の動作仕様を追加

---

## 要件定義書・アーキテクチャ設計書 修正要 アクションアイテム

| # | 修正対象ドキュメント | 修正箇所 | 修正内容 | 優先度 | 対応状況 |
|---|-------------------|---------|---------|-------|---------|
| 1 | `docs/data-model/03-transaction-tables.md` | outbound_slip_lines テーブル | `inspected_qty` カラムの追加、または `shipped_qty` を検品数量の一時格納に使用する旨の注記を追加すること（現状はAPI設計書の補足事項のみに記載） | 中 | ✅ 対応完了（2026-03-18確認） |
| 2 | `docs/functional-design/SCR-10-outbound.md` | OUT-021 検品画面 | 途中保存（部分入力）時のAPI呼び出し方法（`POST /inspect` を複数回実行）をイベント定義に明記すること | 低 | ✅ 対応完了（2026-03-18確認） |
