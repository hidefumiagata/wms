# レビュー記録票: RPT-08-inventory-transition.md

**レビュー実施日**: 2026-03-18
**対象ファイル**: docs/functional-design/RPT-08-inventory-transition.md
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 1 |
| 要対応（他ドキュメント） | 4 |
| 指摘なし | 3 |
| **合計** | **8** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | セクション2.2 カラム#6 ロット番号「---」表記、セクション4 特記事項「---」表記 | `_standard-report.md` 数値書式共通ルール（空値は「—」emダッシュ） | 標準テンプレートでは空値の表記は「—」（emダッシュ）と定められているが、RPT-08では「---」（ハイフン3つ）になっていた | 対象ファイル修正済み | 「---」を「—」（emダッシュ）に修正 |
| 2 | セクション4 特記事項 movementType一覧 | `API-10-report.md` L646 movementType定義 | API設計書のmovementType定義では `MOVE / BREAKDOWN / STOCKTAKE` と記載されているが、データモデル（`inventory_movements`テーブル）およびRPT-08の特記事項では `MOVE_OUT / MOVE_IN / BREAKDOWN_OUT / BREAKDOWN_IN / STOCKTAKE_ADJUSTMENT` と分離されている。RPT-08はデータモデルと整合しており正しい。API設計書の `movementType` フィールド説明（L646）を修正すべき | 要対応（他ドキュメント） | API-10-report.md のAPI-RPT-008 レスポンス仕様のmovementTypeフィールド説明を、データモデルのmovement_type値（INBOUND / OUTBOUND / MOVE_OUT / MOVE_IN / BREAKDOWN_OUT / BREAKDOWN_IN / CORRECTION / STOCKTAKE_ADJUSTMENT / INBOUND_CANCEL）に合わせて修正が必要 ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-008 movementType値をデータモデル定義に合わせて修正済み
| 3 | セクション2.2 カラム#7 `quantityBefore` | `data-model/03-transaction-tables.md` inventory_movementsテーブル定義 | `inventory_movements` テーブルには `quantity_before` カラムが存在しない。`quantity`（変動数）と `quantity_after`（変動後数量）のみ。`quantityBefore` は `quantity_after - quantity` で導出する計算フィールドだが、API設計書の業務ロジック（データ取得仕様）に計算フィールドとして記載されていない | 要対応（他ドキュメント） | API-10-report.md のAPI-RPT-008 業務ロジックに「データ取得仕様」サブセクションを追加し、`quantityBefore` = `quantity_after - quantity` の導出方法を明記すべき ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-008 にデータ取得仕様セクションを追加済み（quantityBefore計算フィールド定義含む）
| 4 | セクション2.2 カラム#5 荷姿表示「CAS/BAL/PCS」 | `data-model/03-transaction-tables.md` inventory_movements.unit_type | データモデルでは荷姿の値は `CASE / BALL / PIECE` だが、RPT-08のカラム定義備考では「CAS/BAL/PCS表示」と記載されている。これは表示時の短縮表記であり、変換ロジックが必要だがAPI設計書に変換仕様の記載がない | 要対応（他ドキュメント） | API-10-report.md のAPI-RPT-008にunitType表示変換仕様（CASE→CAS、BALL→BAL、PIECE→PCS）を追記するか、RPT-08側で表示変換はPDFテンプレート側の責務であることを明記すべき ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-008 にunitType表示変換仕様を追記済み
| 5 | セクション1 レポート概要 関連画面「INV-001」 | `API-10-report.md` L610、`SCR-08-inventory-ops.md` | RPT-08の呼び出し元画面は「INV-001（在庫一覧画面）」と記載されておりSCR-08と整合。しかしAPI-10-report.md L610ではAPI-RPT-008の関連画面が「RPT-001（レポート画面）」となっている | 要対応（他ドキュメント） | API-10-report.md のAPI-RPT-008の関連画面を「INV-001（在庫一覧照会画面）」に修正すべき ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-008 関連画面をINV-001（在庫一覧照会画面）に修正済み
| 6 | セクション2.2 カラム定義全体 | `API-10-report.md` API-RPT-008 レスポンス仕様 | レポートのカラム定義のAPIフィールド名とAPIレスポンスフィールド名が一致していることを確認。movementDate, movementTypeLabel, locationCode, unitType, lotNumber, quantityBefore, quantityChange, quantityAfter, referenceNumber はすべてAPI仕様と整合 | 指摘なし | — |
| 7 | セクション4 特記事項 デフォルト期間 | `API-10-report.md` API-RPT-008 ビジネスルール#3 | RPT-08の「dateFrom未指定時は当月1日、dateTo未指定時は本日がデフォルト（API側で制御）」はAPI設計書のビジネスルール#3と整合 | 指摘なし | — |
| 8 | セクション2.5 ページブレークルール・セクション2.3 グルーピング | `_standard-report.md` テンプレート | 標準テンプレートの必須セクション（2.1-2.5）がすべて記載されている。グルーピング・小計・合計行・ページブレークルールが適切に定義されている | 指摘なし | — |
