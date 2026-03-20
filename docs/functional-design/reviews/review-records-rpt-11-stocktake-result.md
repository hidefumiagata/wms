# レビュー記録票: RPT-11-stocktake-result.md

**レビュー実施日**: 2026-03-18
**対象ファイル**: `docs/functional-design/RPT-11-stocktake-result.md`
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 1 |
| 要対応（他ドキュメント） | 2 |
| 指摘なし | 5 |
| **合計** | **8** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | セクション3 レイアウトイメージ 荷姿列 | data-model/03-transaction-tables.md（inventories, stocktake_lines） | レイアウトイメージ内の荷姿コードが `CAS`/`PCS`/`BAL` と表記されていたが、データモデルの定義は `CASE`/`BALL`/`PIECE`。セクション2.2のカラム定義（`CASE`/`BALL`/`PIECE`）とも不整合 | 対象ファイル修正済み | レイアウトイメージ内の荷姿コードを `CASE`/`PIECE`/`BALL`/`CASE` に修正 |
| 2 | セクション1 関連画面 `INV-014（棚卸確定）` | API-10-report.md API-RPT-011 セクション1 | RPT-11の呼び出し元画面は `INV-014（棚卸確定）` と記載（SCR-09-inventory-stocktake.md INV-014 の画面概要・イベント一覧とも整合）。一方、API-RPT-011の `関連画面` は `INV-STK-002（棚卸結果画面）、RPT-001（レポート画面）` と記載されており、画面IDが不一致。RPT-11側が正しい | 要対応（他ドキュメント） | API-10-report.md の API-RPT-011 `関連画面` を `INV-014（棚卸確定）` に修正する必要あり ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-011 関連画面をINV-014（棚卸確定）に修正済み
| 3 | — | API-10-report.md API-RPT-011 セクション4 業務ロジック フローチャート | API-RPT-011の業務ロジックフローチャートで `format` 分岐が `json`/`csv` のみ記載され、`pdf` が欠落している。レポート共通仕様では `json`/`csv`/`pdf` の3形式が定義されている | 要対応（他ドキュメント） | API-10-report.md の API-RPT-011 フローチャートに `pdf` 分岐（`FORMAT -->|pdf| RES_PDF[200 OK PDFダウンロード]`）を追加する必要あり ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-011 フローチャートにPDF分岐を追加済み
| 4 | セクション2.2 カラム定義 APIフィールド名 | API-10-report.md API-RPT-011 セクション3 | RPT-11のカラム定義のAPIフィールド名（`locationCode`, `productCode`, `productName`, `unitType`, `systemQuantity`, `actualQuantity`, `diffQuantity`, `diffRate`, `lotNumber`）がAPI-RPT-011のレスポンスフィールド定義と完全一致 | 指摘なし | — |
| 5 | セクション2.1 ヘッダー ステータス値 | data-model/03-transaction-tables.md stocktake_headers | ヘッダーに表示するステータス値 `CONFIRMED`/`STARTED` がデータモデルの `stocktake_headers.status` 定義と一致 | 指摘なし | — |
| 6 | セクション1 用途・呼び出し元画面 | functional-requirements/03-inventory-management.md セクション5（棚卸） | 機能要件の棚卸結果レポートの記述（「棚卸確定後に出力」「ロケーション・商品・荷姿・棚卸前数量・実数・差異数を含む」「差異があった明細を識別できる」）とRPT-11の設計内容が整合。追加項目（ロット番号・差異率）は要件の範囲を満たした上での拡充であり問題なし | 指摘なし | — |
| 7 | セクション4 特記事項 未確定時の出力 | API-10-report.md API-RPT-011 ビジネスルール#3 | RPT-11の「未確定時の出力」仕様（STARTED状態でも出力可能、未入力行は `—` 表示）がAPI-RPT-011のビジネスルール#3（「棚卸確定前でも出力可能（未入力行は `actualQuantity = null`）」）と整合 | 指摘なし | — |
| 8 | セクション3 レイアウトイメージ 数値検算 | — | レイアウトイメージ内の数値を検算。明細合計（帳簿数110=10+50+20+30、実数109=10+47+22+30、差異数-1=0+(-3)+2+0）、小計（第1グループ: 60/57/-3、第2グループ: 50/52/+2）、差異サマリー（過剰+2、不足-3、差異あり2/4件）すべて正確 | 指摘なし | — |
