# レビュー記録票: TST-INB-inbound.md

**レビュー実施日**: 2026-03-20
**対象ファイル**: docs/test-specifications/TST-INB-inbound.md
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 4 |
| 要対応（他ドキュメント） | 1 |
| 指摘なし | 3 |
| **合計** | **8** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | SC-INB-021 API検証テーブル | API-06-inbound.md ステータス遷移図 | PARTIAL_STORED状態からの入荷確認テストケースが欠落していた。API-06のステータス遷移図によると、confirm はPLANNEDからのみ可能であるため、PARTIAL_STOREDからの確認も409エラーとなるべきだがテストケースがなかった | 対象ファイル修正済み | PARTIAL_STOREDのテストケース（#3）を追加し、既存番号を繰り下げ。前提条件にもPARTIAL_STOREDを追記 |
| 2 | シナリオ一覧・詳細 | API-06-inbound.md API-INB-007（キャンセル）、functional-requirements/02-inbound-management.md セクション5 | INSPECTING状態からの入荷キャンセル（正常系）のテストシナリオが欠落していた。要件定義では「入庫完了以外のステータスの入荷予定をキャンセルできる」と記載されており、PLANNED（SC-INB-030）、CONFIRMED（SC-INB-031）、PARTIAL_STORED（SC-INB-032）はカバーされていたがINSPECTINGが抜けていた | 対象ファイル修正済み | SC-INB-031a（INSPECTING状態からのキャンセル・在庫影響なし）を新規追加 |
| 3 | シナリオ一覧・詳細 | API-06-inbound.md API-INB-005（入荷予定削除） | API-INB-005（入荷予定削除: DELETE /api/v1/inbound/slips/{id}）のテストシナリオが完全に欠落していた。PLANNED状態のみ削除可能という業務ロジックの検証が必要 | 対象ファイル修正済み | SC-INB-006（正常系: PLANNED状態の物理削除）とSC-INB-007（異常系: PLANNED以外は削除不可）を新規追加 |
| 4 | SC-INB-042 API検証テーブル | API-06-inbound.md API-INB-008、functional-requirements/02-inbound-management.md | 期限切れ商品の検品エラー時のHTTPステータスが「422 or 400」と曖昧に記載されていた。要件定義では「エラーとし、入荷を受け付けない」と明記されており、ビジネスルール違反は422が適切 | 対象ファイル修正済み | HTTPステータスを`422`に確定し、エラーコード`EXPIRY_DATE_EXPIRED`を明記 |
| 5 | SC-INB-042 API検証テーブル | API-06-inbound.md API-INB-008 エラーレスポンス一覧 | API-INB-008の検品登録APIのエラーレスポンス一覧に、期限切れ商品（`expiry_date`が営業日以前）の場合のエラーコード（`EXPIRY_DATE_EXPIRED`等）が定義されていない。要件定義（02-inbound-management.md）では「賞味期限が営業日以前の場合はエラー」と明記されているが、API設計書にエラーコードの定義がない | 要対応（他ドキュメント） | API-06-inbound.md の API-INB-008 エラーレスポンス一覧に `422 EXPIRY_DATE_EXPIRED`（賞味期限が営業日以前）を追加する必要がある |
> **対応完了** (2026-03-20): API-06-inbound.mdのAPI-INB-008エラーレスポンス一覧にEXPIRY_DATE_EXPIREDエラーコードを追加完了
| 6 | SC-INB-001〜SC-INB-090 全体 | functional-requirements/02-inbound-management.md | テストシナリオ全体の業務フローカバレッジを確認。入荷予定登録（手動）・入荷予定一覧照会・入荷確認・入荷キャンセル（全ステータス）・入荷検品・入庫指示確定・入荷実績照会・検品レポートの全機能をカバーしている。E2Eフルフローテスト（SC-INB-090）も適切 | 指摘なし | — |
| 7 | SC-INB-050〜SC-INB-052 DB検証 | data-model/03-transaction-tables.md | inventories・inventory_movementsテーブルへの影響検証項目を確認。UPSERTキー（location_id, product_id, unit_type, lot_number, expiry_date）、movement_typeの値（INBOUND, INBOUND_CANCEL）、quantity_afterの検証が適切に含まれている | 指摘なし | — |
| 8 | テストデータ要件 | data-model/02-master-tables.md, data-model/03-transaction-tables.md | テストデータ要件のテーブル名・カラム名がデータモデル定義と整合していることを確認。partners、products、locations、areas、inventories等のテストデータ定義が正しい | 指摘なし | — |
