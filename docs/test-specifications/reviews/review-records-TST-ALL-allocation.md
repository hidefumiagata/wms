# レビュー記録票: TST-ALL-allocation.md

**レビュー実施日**: 2026-03-20
**対象ファイル**: docs/test-specifications/TST-ALL-allocation.md
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 2 |
| 要対応（他ドキュメント） | 1 |
| 指摘なし | 8 |
| **合計** | **11** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | SC-003 DB検証 #1 allocation_details | data-model/03-transaction-tables.md (allocation_details) | 「3件のレコードが作成」と記載されているが、実際にはロケーションC（qty=3）とロケーションA（qty=5）の2件のみ引当が発生し、ロケーションBは引当なし。正しくは「2件」 | 対象ファイル修正済み | 「3件」→「2件」に修正 |
| 2 | SC-004 DB検証 #1 unpack_instructions | data-model/03-transaction-tables.md (unpack_instructions) | `quantity=6` と記載されているが、unpack_instructionsテーブルには `quantity` カラムは存在せず、`from_qty`（ばらし元数量）と `to_qty`（ばらし先数量）の2カラムで管理される。正しくは `from_qty=1, to_qty=6` | 対象ファイル修正済み | `quantity=6` → `from_qty=1, to_qty=6` に修正 |
| 3 | SC-005 DB検証 #4 inventory_movements | functional-requirements/04a-allocation.md | 機能要件定義書では在庫変動記録の種別を「UNPACK」と記載（86行目）しているが、データモデル定義書（inventory_movements.movement_type）およびAPI設計書（API-ALL-004レスポンス）では `BREAKDOWN_OUT` / `BREAKDOWN_IN` を使用。テスト仕様書は `BREAKDOWN_OUT` / `BREAKDOWN_IN` を使用しておりデータモデルと整合しているが、機能要件定義書側の記載が古い | 要対応（他ドキュメント） | functional-requirements/04a-allocation.md の「種別「UNPACK」として記録する」を「種別「BREAKDOWN_OUT」（減算）と「BREAKDOWN_IN」（加算）の2レコードとして記録する」に修正すべき |
> **対応完了** (2026-03-20): 04a-allocation.mdのUNPACKをBREAKDOWN_OUT/BREAKDOWN_INに修正完了
| 4 | SC-001〜SC-008 テストステップ全体 | SCR-13-allocation.md, API-12-allocation.md | 画面イベント（EVT-ALL001-xxx）、メッセージID（MSG-*-ALL001-xxx）、APIエンドポイントパスがそれぞれ画面設計書・API設計書と一致している | 指摘なし | — |
| 5 | SC-001 DB検証 outbound_slips.status | data-model/03-transaction-tables.md (outbound_slips) | ステータス値 `ALLOCATED` がデータモデルの定義値と一致 | 指摘なし | — |
| 6 | SC-002 部分引当のステータス | API-12-allocation.md (API-ALL-002) | `PARTIAL_ALLOCATED` ステータスがAPI設計書のレスポンス仕様と一致 | 指摘なし | — |
| 7 | SC-005 ばらし完了の5ステップ処理順序 | API-12-allocation.md (API-ALL-004) | DB検証の記載内容（Step1〜Step5）がAPI設計書のばらし完了ビジネスロジックフローと整合 | 指摘なし | — |
| 8 | SC-007 引当解放のDB検証 | API-12-allocation.md (API-ALL-006) | allocation_details削除、在庫のallocated_qty減算、unpack_instructions削除がAPI設計書の業務ロジックフローと一致 | 指摘なし | — |
| 9 | SC-010 引当解放拒否のHTTPステータス | API-12-allocation.md (API-ALL-006) | 409 RELEASE_NOT_ALLOWED がAPI設計書のエラーコード一覧と一致 | 指摘なし | — |
| 10 | SC-011 ばらし再完了拒否のHTTPステータス | API-12-allocation.md (API-ALL-004) | 400 ALREADY_COMPLETED がAPI設計書のエラーコード一覧と一致 | 指摘なし | — |
| 11 | SC-013 権限チェック対象ロール | API-12-allocation.md (API-ALL-001, API-ALL-002) | WAREHOUSE_STAFFが引当実行API・受注一覧APIの対象ロール外であることがAPI設計書と一致。なおAPI-ALL-004（ばらし完了）はWAREHOUSE_STAFFも対象ロールに含まれており、テストで403になるのは引当実行・一覧取得APIに限定されている点は適切 | 指摘なし | — |
