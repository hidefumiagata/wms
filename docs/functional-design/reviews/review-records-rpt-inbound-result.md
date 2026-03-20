# レビュー記録票: RPT-04-inbound-result.md

**レビュー実施日**: 2026-03-18
**対象ファイル**: docs/functional-design/RPT-04-inbound-result.md
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 0 |
| 要対応（他ドキュメント） | 3 |
| 指摘なし | 5 |
| **合計** | **8** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | RPT-04 セクション1「呼び出し元画面」 | SCR-07-inbound.md INB-006 | RPT-04は「INB-006（入庫実績照会画面）」と記載しており、画面設計書SCR-07のINB-006（入荷実績照会）と整合している。正しい。 | 指摘なし | — |
| 2 | RPT-04 セクション1「呼び出し元画面」vs API-10-report.md API-RPT-004 | API-10-report.md 266行目 | API-RPT-004の「関連画面」が `INB-004（入庫実績照会画面）` となっているが、正しくは `INB-006（入荷実績照会）`。INB-004は入荷検品画面である。RPT-04側は正しい。 | 要対応（他ドキュメント） | API-10-report.md の API-RPT-004 関連画面を `INB-006（入荷実績照会）` に修正する ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-004 関連画面IDをINB-006（入荷実績照会）に修正済み
| 3 | RPT-04 セクション2.2 カラム定義 | API-10-report.md API-RPT-004 レスポンス仕様 | RPT-04の明細カラム（slipNumber, storedDate, productCode, productName, plannedQuantityCas, inspectedQuantityCas, diffQuantityCas, storedLocationCode）がAPI-RPT-004のレスポンスフィールドと完全に一致している。 | 指摘なし | — |
| 4 | RPT-04 セクション2.3 グルーピングキー `supplierName` | API-10-report.md API-RPT-004 レスポンス仕様 | グルーピングキー `supplierName` はAPIレスポンスに含まれるフィールドであり整合している。 | 指摘なし | — |
| 5 | RPT-04 セクション4 対象データ条件 `status = 'STORED'` | data-model/03-transaction-tables.md, API-10-report.md | inbound_slipsテーブルのstatusに `STORED`（入庫完了）が定義されており、API-RPT-004のビジネスルール#2とも一致。正しい。 | 指摘なし | — |
| 6 | RPT-04 セクション4 ソート順 | API-10-report.md API-RPT-004 | RPT-04のソート順は「仕入先名（昇順）→入庫日（昇順）→伝票番号（昇順）」。API-RPT-004にはソート順の明示的な記載がない。API側にデータ取得仕様（ソート順含む）を追加すべき。 | 要対応（他ドキュメント） | API-10-report.md の API-RPT-004 にデータ取得仕様セクション（_standard-report.md テンプレートで定義）を追加し、ソート順を明記する ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-004 にデータ取得仕様セクション（ソート順含む）を追加済み
| 7 | API-RPT-004 業務ロジック フローチャート | API-10-report.md 312-322行目 | API-RPT-004のフローチャートにPDF出力分岐（`format=pdf`）が記載されていない。json/csvのみ。共通仕様でPDFをサポートしているため追記が必要。 | 要対応（他ドキュメント） | API-10-report.md の API-RPT-004 フローチャートに `format=pdf` の分岐を追加する ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-004 フローチャートにPDF分岐を追加済み
| 8 | RPT-04 全体構成 | _standard-report.md テンプレート | テンプレートの構成（1.レポート概要、2.PDFレイアウト、3.レイアウトイメージ、4.特記事項）に準拠しており、必要な全セクションが記載されている。 | 指摘なし | — |
