# レビュー記録票: RPT-15-unshipped-realtime.md

**レビュー実施日**: 2026-03-18
**対象ファイル**: docs/functional-design/RPT-15-unshipped-realtime.md
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 1 |
| 要対応（他ドキュメント） | 4 |
| 指摘なし | 4 |
| **合計** | **9** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | セクション3 レイアウトイメージのステータス表示名 | API-08-outbound.md ステータス遷移定義（SSOT） | レイアウト例で使用しているステータス表示名が API-08-outbound.md のSSOT定義と不一致。「受注」→「受注済み」、「ﾋﾟｯｷﾝｸﾞ」→「ﾋﾟｯｷﾝｸﾞ完了」、「引当済」→「一部引当」（`ALLOCATED`ステータスはAPI-08に存在しない）、「検品中」→「出荷検品中」に修正 | 対象ファイル修正済み | レイアウトイメージ内の4つのステータス表示名をAPI-08-outbound.md定義に合わせて修正 |
| 2 | API-RPT-015 関連画面 | API-10-report.md L1229 / RPT-15 L13 | API-RPT-015の「関連画面」が `RPT-001（レポート画面）` のみだが、RPT-15では `OUT-001（受注一覧画面）` を呼び出し元画面と定義している。API側にもOUT-001を追記すべき | 要対応 | API-10-report.md の API-RPT-015 関連画面に `OUT-001（受注一覧画面）` を追記 ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-015 関連画面にOUT-001（受注一覧画面）を追記済み
| 3 | 機能要件の対象データ定義 vs API実装 | functional-requirements/05-reports.md L126 / API-10-report.md L1280 | 機能要件では「現在営業日の出荷予定日の受注のうち、出荷完了していないもの」と定義（当日分のみ）。一方API-RPT-015は `planned_date <= asOfDate` で当日以前の全未出荷を対象としている。APIの方が実用的だが、要件定義との整合を確認・更新すべき | 要対応 | functional-requirements/05-reports.md のRPT-15対象定義を「asOfDate以前の出荷予定日で未出荷のもの」に修正するか、意図的な差異であれば要件側に注記を追加 ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): functional-requirements/05-reports.md のRPT-15対象定義を修正済み（未出荷リスト対象範囲の明確化）
| 4 | SCR-10 ステータスコードマッピング | SCR-10-outbound.md L22-23 / API-08-outbound.md ステータス遷移 | SCR-10の受注一覧ステータスマッピングで `ALLOCATED`（引当済）と `PICKING`（ピッキング中）を使用しているが、API-08-outbound.md（SSOT）には `ALLOCATED` ステータスが存在せず、`PICKING` も存在しない（`PICKING_INSTRUCTED` / `PICKING_COMPLETED` が正）。SCR-10のマッピングをSSOTに合わせるべき | 要対応 | SCR-10-outbound.md のステータスコードマッピングをAPI-08-outbound.md定義に合わせて修正 ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): SCR-10-outbound.md のステータスコードマッピングをSSOT（API-08-outbound.md）に合わせて修正済み
| 5 | データモデル outbound_slips.status | data-model/03-transaction-tables.md L86 / API-08-outbound.md | データモデルの `outbound_slips.status` カラム説明に `PARTIAL_ALLOCATED`（一部引当）が含まれていないが、API-08-outbound.md では定義されている。データモデルのステータス一覧を更新すべき | 要対応 | data-model/03-transaction-tables.md の outbound_slips.status カラム説明に `PARTIAL_ALLOCATED` と `ALLOCATED`（該当する場合）を追加 ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): data-model/03-transaction-tables.md の outbound_slips.status にPARTIAL_ALLOCATEDを追加済み
| 6 | カラム幅合計 | _standard-report.md | 幅合計277mmはA4横の印字可能幅（約277mm）以内で問題なし | 指摘なし | — |
| 7 | APIフィールド名の整合性 | API-10-report.md API-RPT-015 レスポンス仕様 | RPT-15の明細テーブルで使用している全APIフィールド名（slipNumber, customerName, plannedShipDate, delayDays, productCode, productName, totalQuantityCas, statusLabel）がAPI-RPT-015のレスポンス定義と一致 | 指摘なし | — |
| 8 | グルーピング・小計・合計の定義 | — | customerNameでのグルーピング、小計行（件数+CS合計）、合計行の定義が内部的に一貫しており、レイアウトイメージとも整合 | 指摘なし | — |
| 9 | 特記事項の遅延度分類 | — | 遅延度の3段階分類（1日/2-3日/4日以上）と条件付き書式がレイアウトイメージの例と整合 | 指摘なし | — |
