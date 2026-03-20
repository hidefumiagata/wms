# レビュー記録票: IF-01-inbound-plan.md

**レビュー実施日**: 2026-03-19
**対象ファイル**: docs/functional-design/IF-01-inbound-plan.md
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 3 |
| 要対応（他ドキュメント） | 2 |
| 指摘なし | 5 |
| **合計** | **10** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | セクション4.3 ステータス行 | 07-interface.md, 09-interface-architecture.md セクション7.4 | 「全件エラーかつDISCARDの場合は DISCARDED」という記述がSUCCESS_ONLYモードとDISCARDモードを混在させており曖昧。SUCCESS_ONLYモードで全行エラーの場合のステータスが不明確 | 対象ファイル修正済み | SUCCESS_ONLYは常にCOMPLETED（success_count=0含む）、DISCARDはDISCARDEDと明記し、セクション4.4への参照リンクを追加 |
| 2 | セクション2.2〜2.3 | 09-interface-architecture.md セクション4.2 | CSVフォーマットに定義された `note`（備考）カラムがマッピングテーブルに記載されていない。テストケースN-07で言及はあるが、設計書本文のマッピングセクションに明示がなく、実装者が見落とす可能性がある | 対象ファイル修正済み | セクション2.4として「CSVカラムのマッピング対象外」を追加し、noteカラムの扱い（バリデーション対象だがDB登録時は無視）を明記 |
| 3 | セクション6.1 テストケースN-07 | — | テストケースの検証内容が「noteは明細に無いため無視される」と記載されているが、伝票（inbound_slips）にもnoteカラムが無い点が不正確 | 対象ファイル修正済み | 「伝票・明細のいずれにも保存されないこと」に修正し、セクション2.4への参照リンクを追加 |
| 4 | セクション5.1 伝票番号のYYYYMMDD部分 | API-06-inbound.md（API-INB-002 伝票番号採番ルール） | IF-01では `YYYYMMDD` = 入荷予定日（`planned_date`）と定義しているが、API-INB-002（手動登録）では `YYYYMMDD` = 登録日（営業日ではなくシステム日付）と定義している。IF-01セクション5.1の注記に「手動画面登録（API-INB-002）と同一の採番ルールを使用する」とあるが、日付の基準が異なる。I/Fはplanned_dateベース、手動はシステム日付ベースとなり、同一の採番ルールではない | 要対応（他ドキュメント） | API-06-inbound.mdの採番ルールとIF-01の採番ルールでYYYYMMDD部分の日付基準を統一する必要がある。planned_dateベース（IF-01方式）にするか、システム日付ベース（API-INB-002方式）にするか、設計判断が必要。統一後、IF-01セクション5.1の注記も更新すること |

> **対応完了** (2026-03-19): IF-01, IF-02ともに`current_business_date`（営業日）ベースに統一済み。手動登録API（API-06-inbound.md）と採番ルールを統一
| 5 | セクション2.2 inbound_slipsマッピング | data-model/03-transaction-tables.md（inbound_slips） | データモデルの `inbound_slips` テーブルには `note` カラムが存在しないが、API-06-inbound.md（API-INB-002リクエスト、API-INB-003レスポンス）では `note` フィールドが定義されている。IF-01自体はnoteをマッピングしない設計であり問題ないが、API設計書とデータモデルの不整合が存在する | 要対応（他ドキュメント） | data-model/03-transaction-tables.mdの `inbound_slips` に `note` カラムを追加するか、API-06-inbound.mdから `note` フィールドを削除するか、設計判断が必要 |

> **対応完了** (2026-03-19): data-model/03-transaction-tables.mdの`inbound_slips`と`outbound_slips`に`note`カラムを追加済み
| 6 | セクション2.2 warehouse関連カラム | data-model/03-transaction-tables.md, data-model/02-master-tables.md | `warehouse_id`, `warehouse_code`, `warehouse_name` のマッピングがwarehousesテーブル参照で正しく定義されている | 指摘なし | — |
| 7 | セクション2.3 inbound_slip_linesマッピング | data-model/03-transaction-tables.md（inbound_slip_lines） | 全カラムのマッピングがデータモデルと整合している。line_status初期値PENDING、inspected_qty=NULL等も正しい | 指摘なし | — |
| 8 | セクション3.3 if_executions INSERT | data-model/03-transaction-tables.md（if_executions） | if_executionsテーブルの全カラムが正しくINSERT文に含まれている。カラム名・値・説明がデータモデルと一致 | 指摘なし | — |
| 9 | セクション6.3 バリデーションテストケース | 09-interface-architecture.md セクション6 | エラーコード（WMS-E-IFX-301〜501）がアーキテクチャ設計書のバリデーションルールと一致している | 指摘なし | — |
| 10 | セクション4 トランザクション設計 | 09-interface-architecture.md セクション7.4, 07-interface.md | トランザクション境界（DB登録→Blob移動の順序）、1ファイル=1トランザクション、Blob移動失敗時のblob_move_failedフラグ更新がアーキテクチャ設計書と整合している | 指摘なし | — |
