# レビュー記録票: BAT-01-daily-close.md

**レビュー実施日**: 2026-03-19
**対象ファイル**: docs/functional-design/BAT-01-daily-close.md
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 9 |
| 要対応（他ドキュメント） | 2 |
| 指摘なし | 0 |
| **合計** | **11** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | Step 2 SQL: `l.actual_quantity` | data-model/03-transaction-tables.md `inbound_slip_lines` | `actual_quantity` カラムは `inbound_slip_lines` に存在しない。入庫完了数として使うべきカラムは `inspected_qty`（検品数） | 対象ファイル修正済み | `l.inspected_qty` に修正 |
| 2 | Step 2 SQL: `p.pcs_per_case` / `p.pcs_per_ball` | data-model/02-master-tables.md `products` | `pcs_per_case` / `pcs_per_ball` カラムは `products` に存在しない。正しくは `case_quantity`（1ケース＝何ボール）と `ball_quantity`（1ボール＝何バラ）。CASE→バラ換算は `case_quantity * ball_quantity` | 対象ファイル修正済み | `p.case_quantity * p.ball_quantity`（CASE時）/ `p.ball_quantity`（BALL時）に修正。Step 3, Step 6 の返品・在庫サブクエリも同様に修正 |
| 3 | Step 2 SQL: `s.stored_at::date` | data-model/03-transaction-tables.md | `stored_at` は `inbound_slips`（ヘッダ）には存在しない。`stored_at` は `inbound_slip_lines`（明細）のカラム | 対象ファイル修正済み | `l.stored_at::date` に修正。補足テキストも「`inbound_slip_lines` の入庫確定日時カラム」に修正 |
| 4 | Step 3 SQL: `l.actual_quantity` | data-model/03-transaction-tables.md `outbound_slip_lines` | `actual_quantity` カラムは `outbound_slip_lines` に存在しない。出荷済み数量は `shipped_qty` | 対象ファイル修正済み | `l.shipped_qty` に修正 |
| 5 | Step 5b SQL: `l.planned_quantity` | data-model/03-transaction-tables.md `inbound_slip_lines` | `planned_quantity` カラムは存在しない。正しくは `planned_qty` | 対象ファイル修正済み | `l.planned_qty` に修正 |
| 6 | Step 5c SQL / パフォーマンス7.1: `s.planned_shipping_date` | data-model/03-transaction-tables.md `outbound_slips` | `planned_shipping_date` カラムは `outbound_slips` に存在しない。正しくは `planned_date` | 対象ファイル修正済み | `s.planned_date` に修正（SQL本文およびパフォーマンス考慮のインデックス名も修正） |
| 7 | Step 6 SQL 返品サブクエリ: `r.created_at::date = :targetBusinessDate` | data-model/03-transaction-tables.md `return_slips` | 返品の日付フィルタに `created_at` を使用しているが、`return_slips` には営業日ベースの `return_date` カラムが存在する。営業日で集計すべき | 対象ファイル修正済み | `r.return_date = :targetBusinessDate` に修正 |
| 8 | Step 2 パフォーマンス7.1: `inbound_slips(status, stored_at)` | data-model/03-transaction-tables.md | `stored_at` は `inbound_slips` ではなく `inbound_slip_lines` のカラム。インデックスの記載が不正確 | 対象ファイル修正済み | `inbound_slips(status)` + `inbound_slip_lines(stored_at)` に修正 |
| 9 | Step 4 SQL コメント: "荷姿単位のまま集計" | data-model/04-batch-tables.md `inventory_snapshots.total_quantity` | コメントに「荷姿単位のまま集計」とあるが、Step 6 でバラ換算しているため誤解を招く | 対象ファイル修正済み | コメントを「荷姿単位のまま集計。バラ換算はStep 6で実施」に補足追加 |
| 10 | API-BAT-001 レスポンスの steps は 1〜5 のみ | docs/functional-design/API-09-batch.md API-BAT-001 | BAT-01 では6ステップ（Step 6: 日次集計レコード生成）を定義しているが、API-BAT-001 のレスポンス仕様では `steps[].step` が 1〜5 で定義されており、Step 6 が含まれていない。API概要の説明文も「5ステップ」となっている | 要対応（他ドキュメント） | API-09-batch.md の API-BAT-001 レスポンス仕様に Step 6（日次集計レコード生成）を追加し、ステップ数の記述を6に更新する必要がある。API-BAT-002 の `step1Status` 〜 `step5Status` にも `step6Status` の追加が必要 |

> **対応完了** (2026-03-19): API-09-batch.mdにStep 6を追加完了。API-BAT-001/002のレスポンス・フローチャート・処理詳細を6ステップに拡張済み
| 11 | `inventory_snapshots.total_quantity` の説明が "バラ換算" | data-model/04-batch-tables.md | データモデルでは `total_quantity` の説明が「在庫数量合計（バラ換算）」だが、Step 4 の実装では荷姿単位のまま格納し、Step 6 でバラ換算している。データモデルの説明と実装が不整合 | 要対応（他ドキュメント） | data-model/04-batch-tables.md の `inventory_snapshots.total_quantity` の説明を「在庫数量合計（荷姿単位）」に修正するか、Step 4 のSQL側でバラ換算して格納するか、方針を決定して統一する必要がある |

> **対応完了** (2026-03-19): data-model/04-batch-tables.mdの`inventory_snapshots.total_quantity`の説明を「荷姿単位のまま集計。バラ換算はdaily_summary_recordsで実施」に修正済み
