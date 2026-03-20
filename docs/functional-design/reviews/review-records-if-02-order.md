# レビュー記録票: IF-02-order.md

**レビュー実施日**: 2026-03-19
**対象ファイル**: docs/functional-design/IF-02-order.md
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 6 |
| 要対応（他ドキュメント） | 0 |
| 指摘なし | 5 |
| **合計** | **11** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | ドキュメントヘッダ | IF-01-inbound-plan.md | IF-01にはテーブル定義・マスタ定義への参照リンクがあるが、IF-02には欠落していた。SSOTルールに基づきデータモデルへの参照が必要 | 対象ファイル修正済み | テーブル定義（03-transaction-tables.md）とマスタ定義（02-master-tables.md）への参照リンクを追加。目次セクションも追加 |
| 2 | セクション3.1, 3.2（INSERT SQL） | 03-transaction-tables.md, IF-01-inbound-plan.md | outbound_slips INSERT SQLに `created_at`/`updated_at` カラムが含まれていなかった。outbound_slip_lines INSERT SQLも同様。IF-01では明示的に `NOW()` で指定しており、データモデルのNOT NULL制約との整合性が不明確 | 対象ファイル修正済み | 両INSERT SQLに `created_at`/`updated_at` カラムと `NOW()` 値を追加。IF-01と同一の方針に統一 |
| 3 | セクション3（取り込みSQL） | IF-01-inbound-plan.md, 03-transaction-tables.md | IF-01には `if_executions` INSERT SQLがセクション3.3として記載されているが、IF-02には存在しなかった。取り込み履歴の記録はトランザクション設計で言及されているがSQLが不足 | 対象ファイル修正済み | セクション3.3として `if_executions` INSERT SQL（`if_type = 'ORDER'`）を追加 |
| 4 | セクション5.2（採番方式） | IF-01-inbound-plan.md | IF-01は `SELECT ... FOR UPDATE` による排他ロック付き採番を使用しているが、IF-02は `SELECT MAX + 1` のみでFOR UPDATEがなかった。並行実行時の採番重複リスクがある設計不整合 | 対象ファイル修正済み | `SELECT FOR UPDATE` 方式に変更し、IF-01と同一の排他制御方式に統一。Repositoryクエリも追加 |
| 5 | セクション5.3（コンカレンシー制御） | IF-01-inbound-plan.md | IF-01のコンカレンシー制御にはデッドロック防止策（planned_date昇順ソート）が明記されているが、IF-02には欠落。またIF-02は独自のseqCounterMapによるアプリ層管理が記載されていたが、FOR UPDATE方式では不要 | 対象ファイル修正済み | IF-01と同一のデッドロック防止策（sorted keys）を含むコンカレンシー制御に書き換え |
| 6 | セクション2（データマッピング） | IF-01-inbound-plan.md | IF-01にはセクション2.4「CSVカラムのマッピング対象外」と2.6「CSVの空文字列の扱い」があるが、IF-02には欠落。noteカラムの扱いがテスト観点（N-14）では言及されているが、マッピング仕様として明記されていなかった | 対象ファイル修正済み | セクション2.5「CSVカラムのマッピング対象外」と2.6「CSVの空文字列の扱い」を追加 |
| 7 | セクション3.4 バッチINSERT設定 | IF-01-inbound-plan.md | IF-01の `batch_size` は100、IF-02は50だった。同一アプリケーションの `application.yml` 設定であり値は統一すべき | 対象ファイル修正済み | `batch_size` を100に変更しIF-01と統一（No.6の修正に含む） |
| 8 | セクション2.2 マッピング | 03-transaction-tables.md | outbound_slipsのカラム定義とマッピングテーブルの全カラムが正しく対応していることを確認。ステータス値 `ORDERED`、slip_type `NORMAL` も整合 | 指摘なし | — |
| 9 | セクション2.3 マッピング | 03-transaction-tables.md | outbound_slip_linesのカラム定義とマッピングテーブルの全カラムが正しく対応していることを確認。line_status `ORDERED`、shipped_qty デフォルト `0` も整合 | 指摘なし | — |
| 10 | セクション2.4 マスタ参照バリデーション | 09-interface-architecture.md セクション6.5, 6.6 | エラーコード（WMS-E-IFX-301〜306）がアーキテクチャ設計書のL3バリデーション定義と完全に一致。IFX-002固有の出荷禁止チェック（WMS-E-IFX-306）も正しく含まれている | 指摘なし | — |
| 11 | セクション1 I/F概要 | 09-interface-architecture.md セクション2, 3 | Blob Storageパス `iffiles/order/pending/` とファイル名パターン `ORD-{連番3桁}.csv` がアーキテクチャ設計書と一致 | 指摘なし | — |
| 12 | テスト観点（V-01〜V-16） | 09-interface-architecture.md セクション6.4 | L2バリデーションのエラーコード（WMS-E-IFX-201〜206）に対応するテストケースが網羅されている。L5クロスバリデーション（WMS-E-IFX-502）もV-12でカバー | 指摘なし | — |
