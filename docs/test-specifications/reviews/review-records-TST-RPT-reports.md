# レビュー記録票: TST-RPT-reports.md

**レビュー実施日**: 2026-03-20
**対象ファイル**: docs/test-specifications/TST-RPT-reports.md
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 13 |
| 要対応（他ドキュメント） | 2 |
| 指摘なし | 0 |
| **合計** | **15** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | 2.3 検索条件パラメータ RPT-07行 | API-10-report.md API-RPT-007 | パラメータ名 `locationCode` はAPI設計書では `locationCodePrefix` と定義されている | 対象ファイル修正済み | `locationCodePrefix` に修正 |
| 2 | 2.3 検索条件パラメータ RPT-08行 | API-10-report.md API-RPT-008 | `locationCode` はAPI-RPT-008のクエリパラメータに存在しない（APIは `warehouseId`, `productId`, `dateFrom`, `dateTo` のみ） | 対象ファイル修正済み | `locationCode` を削除 |
| 3 | 2.3 検索条件パラメータ RPT-09行 | API-10-report.md API-RPT-009 | パラメータ名 `dateFrom`/`dateTo` はAPI設計書では `correctionDateFrom`/`correctionDateTo` と定義されている | 対象ファイル修正済み | `correctionDateFrom`/`correctionDateTo` に修正 |
| 4 | 2.3 検索条件パラメータ RPT-10行 | API-10-report.md API-RPT-010 | `warehouseId` はAPI-RPT-010のクエリパラメータに存在しない。APIは `stocktakeId` または `buildingId` + `areaId` を使用する | 対象ファイル修正済み | `stocktakeId`（または `buildingId` + `areaId`）に修正 |
| 5 | 2.3 検索条件パラメータ RPT-11行 | API-10-report.md API-RPT-011 | `warehouseId` はAPI-RPT-011のクエリパラメータに存在しない。APIは `stocktakeId` のみを使用する | 対象ファイル修正済み | `stocktakeId` のみに修正 |
| 6 | 2.3 検索条件パラメータ RPT-12行 | API-10-report.md API-RPT-012 | パラメータ名 `instructionId` はAPI設計書では `pickingInstructionId` と定義されている。また `warehouseId` はAPI-RPT-012のパラメータに存在しない | 対象ファイル修正済み | `pickingInstructionId` に修正 |
| 7 | 2.3 検索条件パラメータ RPT-13行 | API-10-report.md API-RPT-013 | パラメータ名 `slipIds`（複数形）はAPI設計書では `slipId`（単数形）と定義されている | 対象ファイル修正済み | `slipId` に修正 |
| 8 | 2.3 検索条件パラメータ RPT-14行 | API-10-report.md API-RPT-014 | パラメータ名 `dateFrom`/`dateTo` はAPI設計書では `plannedDateFrom`/`plannedDateTo` と定義されている | 対象ファイル修正済み | `plannedDateFrom`/`plannedDateTo` に修正 |
| 9 | 2.3 検索条件パラメータ RPT-17行 | API-10-report.md API-RPT-017 | パラメータ名 `businessDate` はAPI設計書では `targetBusinessDate` と定義されている | 対象ファイル修正済み | `targetBusinessDate` に修正。テストステップ内のURL例・Playwrightコード例も同時修正 |
| 10 | 2.3 検索条件パラメータ RPT-18行 | API-10-report.md API-RPT-018 | パラメータ名 `dateFrom`/`dateTo` はAPI設計書では `returnDateFrom`/`returnDateTo` と定義されている | 対象ファイル修正済み | `returnDateFrom`/`returnDateTo` に修正 |
| 11 | 2.3 SC-RPT06-002 | API-10-report.md API-RPT-006 業務ロジック | 「異常系: 日替処理未実行の営業日指定」で404エラーを期待しているが、API設計書では「指定 batchBusinessDate の日替処理が実行されていない場合は空配列（`[]`）を返す（404 ではない）」と定義されている | 対象ファイル修正済み | シナリオ名を「正常系: 日替処理未実行の営業日指定で空配列が返る」に修正 |
| 12 | SC-RPT10-002 期待結果 | RPT-10-stocktake-list.md 特記事項 | テストシナリオで「hideBookQty=false の場合にPDFに帳簿数量が表示される」ことを期待しているが、RPT-10設計書の特記事項に「帳簿数量（systemQuantity）はPDFに表示しない」「PDFテンプレートでは描画しない」と明記されており、hideBookQtyの値にかかわらずPDFでは常に帳簿数量は非表示 | 対象ファイル修正済み | 期待結果を「PDFに帳簿数量が含まれないこと」に修正。根拠の注記を追加 |
| 13 | 8. Playwrightコード例 SC-RPT10-001 | API-10-report.md API-RPT-010 | Playwrightコードで `warehouseId: '1'` を指定しているが、API-RPT-010のクエリパラメータに `warehouseId` は存在しない | 対象ファイル修正済み | `warehouseId` パラメータを削除 |
| 14 | API-10-report.md API-RPT-010 | functional-design/RPT-10-stocktake-list.md, functional-requirements/05-reports.md | `hideBookQty` パラメータがAPI設計書（API-RPT-010）のクエリパラメータ一覧に定義されていない。機能要件定義書とRPT-10レポート設計書では `hideBookQty=true` によるPDF帳簿数量非表示が記述されているが、APIのリクエスト仕様に反映されていない | 要対応（他ドキュメント） | API-10-report.md の API-RPT-010 クエリパラメータに `hideBookQty` (Boolean, 任意, デフォルト: true) を追加する必要がある |
> **対応完了** (2026-03-20): API-10-report.mdのAPI-RPT-010クエリパラメータにhideBookQtyパラメータを追加完了
| 15 | API-10-report.md API-RPT-018 | functional-requirements/08-returns.md | 機能要件定義書では返品種別として INBOUND / INVENTORY / OUTBOUND の3種別が定義されているが、API-RPT-018の `returnType` フィルタパラメータは `INBOUND` / `OUTBOUND` のみ対応（`INVENTORY`（在庫返品）が欠落）。テスト仕様書のテストデータにも在庫返品が含まれていない | 要対応（他ドキュメント） | API-10-report.md の API-RPT-018 で `returnType` の値に `INVENTORY` を追加するか、意図的に除外している場合はその理由を明記する必要がある |
> **対応完了** (2026-03-20): API-10-report.mdのAPI-RPT-018 returnTypeにINVENTORYを追加完了（RTN側レビューと重複指摘）
