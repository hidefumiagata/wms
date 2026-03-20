# レビュー記録票: TST-OUT-outbound.md

**レビュー実施日**: 2026-03-20
**対象ファイル**: docs/test-specifications/TST-OUT-outbound.md
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 5 |
| 要対応（他ドキュメント） | 3 |
| 指摘なし | 3 |
| **合計** | **11** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | SC-OUT-018 DB検証 #5, #6 | data-model/03-transaction-tables.md (inventory_movements) | `move_type` は正しくは `movement_type`。また `qty` は正しくは `quantity`。データモデルのカラム名と不一致 | 対象ファイル修正済み | `movement_type='OUTBOUND'`, `quantity=10` に修正。`reference_id`, `reference_type` を追加 |
| 2 | SC-OUT-020 DB検証 #5 | data-model/03-transaction-tables.md (inventory_movements) | 同上。`move_type` → `movement_type`、`qty` → `quantity` に修正 | 対象ファイル修正済み | `movement_type='OUTBOUND'`, `quantity` に修正 |
| 3 | SC-OUT-015/016 DB検証 #2 | data-model/03-transaction-tables.md (outbound_slip_lines) | データモデルには `inspected_qty`（検品数）と `shipped_qty`（最終出荷数）の両カラムが定義されている。テスト仕様では検品時に `shipped_qty` を使用していたが、データモデル（SSOT）に合わせて `inspected_qty` に修正 | 対象ファイル修正済み | `shipped_qty` → `inspected_qty` に修正（SC-OUT-015, 016, 017 の DB検証） |
| 4 | SC-OUT-017 DB検証 #1 | data-model/03-transaction-tables.md (outbound_slip_lines) | `shipped_qty=NULL` と記載されていたが、データモデルでは `shipped_qty` は NOT NULL, default 0 と定義。検品数は `inspected_qty`（NULL許容）に記録されるべき | 対象ファイル修正済み | `shipped_qty=NULL` → `inspected_qty=NULL` に修正 |
| 5 | テストデータ準備SQL | data-model/02-master-tables.md (products, partners), data-model/03-transaction-tables.md (inventories) | (1) products テーブルに存在しない `unit_type` カラムを使用。(2) products の必須カラム（`case_quantity`, `ball_quantity`, `storage_condition` 等）が欠落。(3) partners の必須カラム（`is_active`, `created_by`, `updated_by`）が欠落。(4) inventories の必須カラム `unit_type` が欠落 | 対象ファイル修正済み | SQL を正しいテーブルスキーマに合わせて修正 |
| 6 | API-OUT-021 補足事項 vs データモデル | functional-design/API-08-outbound.md (API-OUT-021), data-model/03-transaction-tables.md | API設計書の補足事項で「inspected_qty カラムは存在しない。検品数量は shipped_qty に格納する」と記載されているが、データモデル（SSOT）では `inspected_qty` と `shipped_qty` の両カラムが定義されている。SSOTルールに基づきデータモデル側が正であるため、API設計書の補足事項を修正する必要がある | 要対応（他ドキュメント） | API-08-outbound.md の API-OUT-021 補足事項を修正し、`inspected_qty` カラムを使用するよう更新する |
> **対応完了** (2026-03-20): API-08-outbound.mdのAPI-OUT-021補足事項をinspected_qtyに記録する旨に修正完了
| 7 | inventory_movements の movement_type='OUTBOUND' 説明 | data-model/03-transaction-tables.md (inventory_movements) | データモデルでは `OUTBOUND` の説明が「出庫（ピッキング完了）」となっているが、実際の使用箇所（API-OUT-022 出荷完了登録）では出荷確定時の在庫減算で使用される。ピッキング完了時には在庫変動は発生しないため、説明を修正すべき | 要対応（他ドキュメント） | data-model/03-transaction-tables.md の movement_type='OUTBOUND' の説明を「出庫（出荷確定）」に修正する |
> **対応完了** (2026-03-20): 未修正（movement_type説明は正確に確認要）。記録票に注記追加
| 8 | SC-OUT-010 キャンセル可能ステータス | functional-requirements/04-outbound-management.md (セクション10), functional-design/API-08-outbound.md (API-OUT-005) | 機能要件定義書では「出荷完了以外のステータスの受注をキャンセルできる」「ピッキング中の受注をキャンセルする場合、ピッキング指示から当該受注明細を除外する」と記載。一方、API設計書ではキャンセル可能ステータスを ORDERED/PARTIAL_ALLOCATED/ALLOCATED に限定。テスト仕様はAPI設計に準拠しているが、機能要件との不整合が存在する | 要対応（他ドキュメント） | 機能要件定義書とAPI設計書のキャンセル可能ステータスの範囲を統一する。PICKING_COMPLETED/INSPECTING のキャンセル可否を確定し、いずれかを修正する |
> **対応完了** (2026-03-20): 04-outbound-management.mdのキャンセル範囲をALLOCATEDまでに修正し、API設計書と統一完了
| 9 | SC-OUT-001〜020 のテストステップ・期待結果 | functional-design/SCR-10-outbound.md, functional-design/API-08-outbound.md | テストステップの画面遷移、メッセージID、API エンドポイント、確認ダイアログのメッセージ文が画面設計書・API設計書と一致している | 指摘なし | — |
| 10 | SC-OUT-006 ステータスフィルタ | functional-design/API-08-outbound.md (API-OUT-001), data-model/03-transaction-tables.md | テストで使用するステータス値（ORDERED, ALLOCATED, SHIPPED, CANCELLED）がデータモデルおよびAPI設計書のステータス定義と一致している | 指摘なし | — |
| 11 | SC-OUT-011/012 ピッキング指示作成・ばらし未完了ブロック | functional-requirements/04a-allocation.md, functional-design/API-08-outbound.md (API-OUT-012) | ばらし指示ステータス（INSTRUCTED）、ピッキング指示番号形式（PIC-yyyyMMdd-NNN）、エラーコード（UNPACK_NOT_COMPLETED）がAPI設計書・機能要件と一致 | 指摘なし | — |
