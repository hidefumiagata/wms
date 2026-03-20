# レビュー記録票: RPT-16-unshipped-confirmed.md

**レビュー実施日**: 2026-03-18
**対象ファイル**: docs/functional-design/RPT-16-unshipped-confirmed.md
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 2 |
| 要対応（他ドキュメント） | 3 |
| 指摘なし | 5 |
| **合計** | **10** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | セクション1 呼び出し元画面 | SCR-10-outbound.md OUT-001 | 「OUT-001（出荷一覧画面）」と記載されていたが、SCR-10-outbound.md では OUT-001 は「受注一覧」画面である。RPT-15でも「受注一覧画面」と記載されている | 対象ファイル修正済み | 「出荷一覧画面」→「受注一覧画面」に修正 |
| 2 | セクション4 RPT-15との関係 | RPT-15-unshipped-realtime.md | 「同一のレイアウトを使用する。違いはデータ取得タイミングのみ」と記載されていたが、実際にはRPT-15は10列（遅延日数・遅延度列あり）・出荷先別グルーピング・グループヘッダーあり、RPT-16は8列・伝票番号別グルーピング・グループヘッダーなしと、レイアウトが大きく異なる | 対象ファイル修正済み | レイアウトが異なることを明記し、具体的な違いを記述 |
| 3 | API-RPT-016 レスポンス例 | API-10-report.md L1341 | APIレスポンス例の `statusAtBatch` が `"PICKING"` となっているが、`outbound_slips.status` の定義値に `PICKING` は存在しない。正しくは `PICKING_INSTRUCTED` 等であるべき | 要対応（他ドキュメント） | API-10-report.md の API-RPT-016 レスポンス例の `statusAtBatch` 値を修正する必要あり ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-016 レスポンス例のstatusAtBatch値を修正済み
| 4 | API-RPT-016 業務ロジックフローチャート | API-10-report.md L1359-1367 | フローチャートに `pdf` 形式の分岐が欠落している。`json` と `csv` のみ記載されており、`pdf` 出力のパスがない | 要対応（他ドキュメント） | API-10-report.md の API-RPT-016 フローチャートに `pdf` 分岐を追加する必要あり ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-016 フローチャートにPDF分岐を追加済み
| 5 | API-RPT-016 データ取得仕様 | _standard-report.md API-10-report.md | _standard-report.md のガイドラインで要求されている「データ取得仕様」サブセクション（主テーブル・結合、フィルタ条件マッピング、計算フィールド、ソート順）が API-RPT-016 の業務ロジック内に記載されていない | 要対応（他ドキュメント） | API-10-report.md の API-RPT-016 に「データ取得仕様」サブセクションを追加する必要あり ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-016 にデータ取得仕様セクションを追加済み
| 6 | セクション2.2 カラム定義 | API-10-report.md | 明細テーブルカラム定義のAPIフィールド名（slipNumber, customerName, plannedShipDate, productCode, productName, totalQuantityCas, statusAtBatch）がAPIレスポンス仕様のフィールド名と一致している | 指摘なし | — |
| 7 | セクション2.2 幅合計 | _standard-report.md | 幅合計277mmはA4横の印字可能幅以内に収まっている | 指摘なし | — |
| 8 | セクション4 ステータスマッピング | data-model/03-transaction-tables.md | RPT-16のステータスマッピング（ORDERED, PICKING_INSTRUCTED, PICKING_COMPLETED, INSPECTING）は outbound_slips.status の定義値のうち SHIPPED と CANCELLED を除いたものと整合している | 指摘なし | — |
| 9 | セクション4 ソート順 | API-10-report.md | ソート順（出荷予定日ASC→伝票番号ASC→商品コードASC）はグルーピングキー（slipNumber）を含み、合理的な順序である | 指摘なし | — |
| 10 | セクション4 データ取得元 | data-model/04-batch-tables.md | データ取得元として記載されている `unshipped_list_records` テーブルはデータモデル定義に存在し、`batch_business_date`, `slip_number`, `partner_name`, `planned_date`, `product_code`, `product_name`, `current_status` の各カラムが定義されている | 指摘なし | — |
