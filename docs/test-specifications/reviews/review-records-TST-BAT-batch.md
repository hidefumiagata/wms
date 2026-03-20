# レビュー記録票: TST-BAT-batch.md

**レビュー実施日**: 2026-03-20
**対象ファイル**: docs/test-specifications/TST-BAT-batch.md
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 1 |
| 要対応（他ドキュメント） | 0 |
| 指摘なし | 10 |
| **合計** | **11** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | ヘッダー「対象設計書」 | SCR-11, API-09, BAT-01 | SCR-11（BAT-001, BAT-002）、API-09（API-BAT-001〜003）、BAT-01-daily-close.md を正しく参照している | 指摘なし | — |
| 2 | SC-001 全体 | BAT-01-daily-close.md Section 8 | 6ステップ構成は業務要件（5ステップ）に対してStep 6（日次集計レコード生成）が追加された実装仕様を正しく反映している | 指摘なし | — |
| 3 | SC-001 DB検証 | data-model/04-batch-tables.md | 全テーブル名・カラム名（`business_date`, `inbound_summaries`, `outbound_summaries`, `inventory_snapshots`, `unreceived_list_records`, `unshipped_list_records`, `daily_summary_records`, `batch_execution_logs`, `inbound_slips_backup`, `outbound_slips_backup`）がデータモデルと一致 | 指摘なし | — |
| 4 | SC-001 メッセージID | SCR-11-batch.md BAT-001 メッセージ一覧 | MSG-W-BAT001-001, MSG-S-BAT001-001 の参照・メッセージ文が画面設計書と一致 | 指摘なし | — |
| 5 | SC-002 API検証 | API-09-batch.md API-BAT-001 | 409 Conflict + `BATCH_ALREADY_RUNNING` エラーコードがAPI設計書のエラーレスポンス定義と一致 | 指摘なし | — |
| 6 | SC-003 再実行ロジック | API-09-batch.md ビジネスルール#2,#5, BAT-01 Section 5 | FAILEDレコード削除→完了済みステップスキップ→未完了ステップから再開の動作がAPI設計書・バッチ設計書の再実行フローと一致 | 指摘なし | — |
| 7 | SC-004 冪等性検証 | BAT-01-daily-close.md Step 1 ビジネスルール#2 | 営業日が二重に進行しないことの検証が、Step 1の冪等性設計（`current_business_date = targetBusinessDate` ならスキップ）と整合 | 指摘なし | — |
| 8 | SC-005 営業日連続性 | BAT-01-daily-close.md Step 1 ビジネスルール#1 | `current + 1 = target` の連続性チェック違反（19→22）でStep 1失敗の期待結果がバッチ設計と一致。API検証で200 OK + status=FAILED を期待しているのもAPI-BAT-001の仕様（同期実行でステップ失敗は200 OKで返却）と整合 | 指摘なし | — |
| 9 | SC-006 未入荷/未出荷リスト | BAT-01-daily-close.md Step 5b/5c SQL | リスト生成条件（予定日 <= 営業日、status NOT IN ('STORED'/'SHIPPED', 'CANCELLED')）、キャンセル済み除外、完了済み除外の検証がバッチ設計のSQLと一致 | 指摘なし | — |
| 10 | SC-007 バラ換算ロジック | BAT-01-daily-close.md Step 6 SQL | CASE/BALL/PIECEのバラ換算（`case_quantity * ball_quantity` / `ball_quantity` / 1）がバッチ設計のSQLロジックと一致 | 指摘なし | — |
| 11 | テストシナリオ一覧の網羅性 | BAT-01-daily-close.md Section 9 テスト観点 | BAT-01が挙げるテスト観点12項目のうち、観点#2「0件データでエラーにならない」のシナリオが欠落している。対象営業日に入荷・出荷・在庫・返品データが0件の状態で日替処理を実行し、全ステップがSUCCESSとなることを確認するシナリオを追加すべき | 対象ファイル修正済み | SC-010として0件データシナリオを追加 |
