# レビュー記録票: RPT-06-unreceived-confirmed.md

**レビュー実施日**: 2026-03-18
**対象ファイル**: docs/functional-design/RPT-06-unreceived-confirmed.md
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 0 |
| 要対応（他ドキュメント） | 1 |
| 指摘なし | 8 |
| **合計** | **9** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | RPT-06 §1 レポート概要 | functional-requirements/05-reports.md §6 | 機能要件「前日以前の入荷予定日を過ぎても入庫完了していない入荷予定（日替処理確定済み）」「入荷予定日・仕入先・商品・予定数量・現在ステータスを含む」と一致。用途記述も要件に整合している | 指摘なし | — |
| 2 | RPT-06 §1 対応API | API-10-report.md API-RPT-006 | `API-RPT-006` の参照が正しい。パスも `/api/v1/reports/unreceived-confirmed` で一致 | 指摘なし | — |
| 3 | RPT-06 §2.2 カラム定義 | API-10-report.md API-RPT-006 §3 | APIフィールド名（`slipNumber`, `plannedDate`, `productCode`, `productName`, `plannedQuantityCas`, `statusAtBatch`）がAPI-RPT-006のレスポンス仕様と一致。`supplierName` はグルーピングキーとして使用されており、これもAPIレスポンスに含まれる | 指摘なし | — |
| 4 | RPT-06 §2.2 カラム#4 遅延日数 | API-10-report.md API-RPT-006 §3 | API-RPT-006のレスポンスには `delayDays` フィールドが含まれない。RPT-06はカラム定義で「（計算値）」、特記事項で「PDF生成時に `batchBusinessDate - plannedDate` で算出」と正しく記述している。RPT-05（リアルタイム版）のAPI-RPT-005には `delayDays` が含まれる点との差異も適切に反映されている | 指摘なし | — |
| 5 | RPT-06 §2.2 カラム#8 バッチ時点ステータス | API-10-report.md API-RPT-006 §3 / RPT-05 §2.2 | RPT-05が `statusLabel`（リアルタイムの日本語ラベル）を使用するのに対し、RPT-06は `statusAtBatch`（バッチ時点のステータスコード）を使用。カラム名も「バッチ時点ステータス」として区別されており、適切に差別化されている | 指摘なし | — |
| 6 | RPT-06 §2.2 幅合計 | _standard-report.md | 幅合計 277mm（10+40+28+20+30+60+25+30+34）が正しく計算されており、A4横の印字可能幅以内。RPT-05と同一のカラム幅構成で一貫性がある | 指摘なし | — |
| 7 | RPT-06 §4 特記事項 データソース | data-model/04-batch-tables.md `unreceived_list_records` | `unreceived_list_records` テーブルの参照が正しい。テーブルのカラム（`batch_business_date`, `slip_number`, `planned_date`, `partner_name`, `product_code`, `product_name`, `planned_qty`, `current_status`）とAPIレスポンスフィールドの対応が整合している | 指摘なし | — |
| 8 | RPT-06 §4 特記事項 除外ステータス | functional-requirements/06-batch-processing.md | 「STORED（入庫完了）およびCANCELLED（キャンセル）のステータスは含まれない」の記述は、機能要件の「入庫完了していない入荷予定」と整合。詳細はAPI-RPT-006に委譲しておりSSOTルールに準拠 | 指摘なし | — |
| 9 | API-10-report.md API-RPT-006 §4 フローチャート | API-10-report.md API-RPT-006 §4 | API-RPT-006のフローチャートに `pdf` フォーマット分岐が欠落している。`json` と `csv` の分岐のみ記載されているが、`format` パラメータは `json`/`csv`/`pdf` の3値を取る（§2 リクエスト仕様）。RPT-06はPDF出力レポートであるため、API側のフローチャートに `pdf` 分岐を追加すべき | 要対応（他ドキュメント） | API-10-report.md の API-RPT-006 §4 フローチャートに `FORMAT -->|pdf| RES_PDF[200 OK PDFダウンロード\nfilename: unreceived_confirmed_YYYYMMDD.pdf]` 分岐を追加する ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-006 フローチャートにPDF分岐を追加済み
