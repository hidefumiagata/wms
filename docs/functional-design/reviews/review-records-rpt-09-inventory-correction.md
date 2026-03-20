# レビュー記録票: RPT-09-inventory-correction.md

**レビュー実施日**: 2026-03-18
**対象ファイル**: docs/functional-design/RPT-09-inventory-correction.md
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 1 |
| 要対応（他ドキュメント） | 2 |
| 指摘なし | 4 |
| **合計** | **7** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | セクション2.3 ソート順 | セクション4 特記事項 | セクション2.3では「訂正日の昇順でソート」のみ記載されていたが、セクション4では「同一日内はロケーションコード昇順」という第2ソートキーも記載されていた。セクション2.3の記載が不完全 | 対象ファイル修正済み | セクション2.3に第2ソートキー（locationCode昇順）を追記した |
| 2 | API-RPT-009 業務ロジック フローチャート | docs/functional-design/API-10-report.md（688行目付近） | API-RPT-009のフローチャートに `pdf` 分岐が欠落している。`json` と `csv` のみ記載されており、`pdf` 出力パスが含まれていない。他のレポートAPIと同様に `pdf` 分岐を追加すべき | 要対応（他ドキュメント） | API-10-report.md の API-RPT-009 フローチャートに `pdf` 分岐を追加する必要あり ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-009 フローチャートにPDF分岐を追加済み
| 3 | API-RPT-009 データ取得仕様 | docs/functional-design/API-10-report.md、docs/functional-design/_standard-report.md | _standard-report.md のガイドでは各レポートAPIに「データ取得仕様」サブセクション（主テーブル・結合、フィルタ条件マッピング、計算フィールド、ソート順）を必須としているが、API-RPT-009にはデータ取得仕様が未記載。特に `quantityBefore` が `quantity_after - quantity` で導出される計算フィールドであることを明示すべき | 要対応（他ドキュメント） | API-10-report.md の API-RPT-009 にデータ取得仕様セクションを追加する必要あり ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-009 にデータ取得仕様セクションを追加済み（quantityBefore計算フィールド定義含む）
| 4 | 明細カラムとAPIレスポンスフィールドの整合性 | docs/functional-design/API-10-report.md（716-731行目） | 明細テーブルの全カラム（correctionDate, locationCode, productCode, productName, unitType, quantityBefore, quantityAfter, quantityChange, reason）がAPIレスポンスフィールドと一致している。operatorNameは特記事項で訂正理由列への括弧付き追記として明示されており、設計上の意図が明確 | 指摘なし | — |
| 5 | 機能要件との整合性（含む情報項目） | docs/functional-requirements/05-reports.md（85-88行目） | 機能要件の「訂正日時・対象ロケーション・商品・荷姿・訂正前数量・訂正後数量・訂正理由・実施者を含む」に対し、RPT-09は全項目を網羅している。要件では「訂正日時」だがレポートでは「訂正日」（日付のみ）としている点は、レポート用途（一覧の監査証跡）として日付粒度で十分と判断 | 指摘なし | — |
| 6 | 幅合計・レイアウト | docs/functional-design/_standard-report.md | 幅合計277mmはA4横の印字可能幅以内。レイアウトイメージも標準テンプレートのヘッダー/フッター仕様に準拠している | 指摘なし | — |
| 7 | 呼び出し元画面との整合性 | docs/functional-design/SCR-08-inventory-ops.md（INV-004セクション） | INV-004の `BTN-REPORT`（訂正一覧レポート）ボタンおよび `EVT-INV004-006` イベントでRPT-009を呼び出す設計と整合している | 指摘なし | — |
