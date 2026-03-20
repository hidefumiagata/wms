# レビュー記録票: TST-INV-inventory.md

**レビュー実施日**: 2026-03-20
**対象ファイル**: docs/test-specifications/TST-INV-inventory.md
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 2 |
| 要対応（他ドキュメント） | 3 |
| 指摘なし | 7 |
| **合計** | **12** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | 棚卸シナリオ一覧 | SCR-09 INV-014 メッセージ一覧 MSG-E-INV014-005 | INV-014画面設計のEVT-INV014-004に「引当数チェック: 実数がallocated_qtyを下回るロケーションが存在する場合はエラー」とあるが、対応するテストシナリオが存在しなかった。SC-INV-074 として追加 | 対象ファイル修正済み | SC-INV-074（棚卸確定時の引当数下回りチェック）を棚卸シナリオ一覧と詳細に追加 |
| 2 | SC-INV-073 ステップ2 エラーメッセージ文 | SCR-09 INV-014 MSG-E-INV014-002 | テスト仕様のエラーメッセージ文が「実数が未入力の項目があります。」だが、画面設計書では「実数が未入力の項目があります。INV-013で全項目を入力してください」。後半部分が欠落していた | 対象ファイル修正済み | 画面設計書のメッセージ文に合わせて修正 |
| 3 | SC-INV-034 引当数下回り訂正エラー | API-07 API-INV-004 業務ロジック | 画面設計（SCR-08 INV-004）には `newQty >= allocated_qty` チェックと MSG-E-INV004-006 が定義されているが、API設計書（API-INV-004）の業務ルール一覧にこのバリデーションが記載されていない。テスト仕様は画面設計に基づき正しいが、API側で実装漏れになるリスクあり | 要対応（他ドキュメント） | API-07 API-INV-004 のビジネスルールに「newQty >= allocated_qty であること」を追加すべき。エラーコードとして `INVENTORY_CORRECTION_BELOW_ALLOCATED` 等を定義し、エラーレスポンス表にも追加が必要 |
> **対応完了** (2026-03-20): API-07 API-INV-004の業務ルールにCORRECTION_BELOW_ALLOCATEDエラーコードおよび引当数チェックルールを追加完了
| 4 | SC-INV-050 DB検証 stocktake_headers | data-model/03-transaction-tables.md stocktake_headers テーブル定義 | API-INV-012 のリクエストに `note`（備考）と `stocktakeDate`（実施日）フィールドがあるが、data-model の `stocktake_headers` テーブルにこれらのカラムが存在しない。テスト仕様自体は API 仕様に沿っているが、データモデルに不足がある | 要対応（他ドキュメント） | data-model/03-transaction-tables.md の `stocktake_headers` テーブルに `note text NULL`（備考）と `stocktake_date date NOT NULL`（棚卸実施日）カラムを追加すべき |
> **対応完了** (2026-03-20): data-model/03-transaction-tables.mdのstocktake_headersテーブルにnoteおよびstocktake_dateカラムを追加完了
| 5 | SC-INV-071 DB検証 reference_type | data-model/03-transaction-tables.md inventory_movements.reference_type | テスト仕様では `reference_type=STOCKTAKE_HEADER` としており、API設計書（API-INV-015）と一致する。しかしデータモデルの reference_type 説明では例として `STOCKTAKE_LINE` が記載されており、`STOCKTAKE_HEADER` が含まれていない。テスト仕様・API設計は正しいが、データモデルの例示が不正確 | 要対応（他ドキュメント） | data-model/03-transaction-tables.md の `inventory_movements.reference_type` の説明を `INBOUND_LINE / PICKING_LINE / STOCKTAKE_HEADER 等` に修正すべき |
> **対応完了** (2026-03-20): data-model/03-transaction-tables.mdのinventory_movements.reference_type例示をSTOCKTAKE_HEADERに修正完了
| 6 | SC-INV-001〜005 在庫一覧照会テスト | SCR-08 INV-001、API-07 API-INV-001 | 在庫照会の各シナリオ（ロケーション別、商品合計、フィルタ、倉庫切替、引当表示）は画面設計・API設計と整合している | 指摘なし | — |
| 7 | SC-INV-010〜015 在庫移動テスト | SCR-08 INV-002、API-07 API-INV-002 | 在庫移動の正常系・異常系シナリオは画面設計のメッセージID・API設計の業務ルール・データモデルのテーブル構造と整合。DB検証のカラム名（quantity, allocated_qty, inventory_movements の movement_type 等）もデータモデルと一致 | 指摘なし | — |
| 8 | SC-INV-020〜025 ばらしテスト | SCR-08 INV-003、API-07 API-INV-003、data-model products.case_quantity/ball_quantity | 変換レート計算（case_quantity, ball_quantity）、DB検証のmovement_type（BREAKDOWN_OUT/IN）、unit_type（CASE/BALL/PIECE）はすべて整合 | 指摘なし | — |
| 9 | SC-INV-030〜034 在庫訂正テスト | SCR-08 INV-004、API-07 API-INV-004 | 在庫訂正の正常系（増加・減少・変化なし）と異常系（棚卸ロック・引当数下回り）のシナリオは画面設計と整合。DB検証でCORRECTIONのmovement_type、correction_reasonカラムもデータモデルと一致 | 指摘なし | — |
| 10 | SC-INV-050〜052 棚卸開始テスト | SCR-09 INV-012、API-07 API-INV-012 | 棚卸開始シナリオ（範囲指定・ロック設定・重複開始エラー）は画面設計のメッセージID・API設計のHTTPステータスコードと整合 | 指摘なし | — |
| 11 | SC-INV-060〜062 実数入力テスト | SCR-09 INV-013、API-07 API-INV-014 | 実数入力・一時保存・確定ボタンの活性制御テストは画面設計と整合。stocktake_lines の quantity_counted/is_counted カラムはデータモデルと一致 | 指摘なし | — |
| 12 | SC-INV-070〜073, 080〜082 棚卸確定・ロックテスト | SCR-09 INV-014、API-07 API-INV-015 | 棚卸確定（在庫更新・STOCKTAKE_ADJUSTMENT記録・ロック解除）とロック中操作ブロックのテストは設計書と整合。HTTPステータス409の確認もAPI設計と一致 | 指摘なし | — |
