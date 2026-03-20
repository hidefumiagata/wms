# レビュー記録票: TST-IF-interface.md

**レビュー実施日**: 2026-03-20
**対象ファイル**: docs/test-specifications/TST-IF-interface.md
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
| 1 | SC-002 API検証 #1 レスポンスフィールド名 | architecture-design/09-interface-architecture.md セクション6.8 | バリデーションAPIレスポンスのフィールド名が `totalCount`, `errors` と記載されていたが、アーキテクチャ設計書のレスポンス形式では `totalRows`, `rows` と定義されている。フィールド名の不一致 | 対象ファイル修正済み | `totalCount=10` → `totalRows=10`、`errors=[]` → `rows=[]` に修正 |
| 2 | シナリオ一覧・詳細 | functional-design/SCR-15-interface.md IF-003画面項目一覧、functional-design/IF-02-order.md | IFX-002（受注CSV）の取り込みフルフローのテストシナリオが欠落していた。SC-001〜SC-008は全て入荷予定CSV（IFX-001）に特化しており、受注CSVの正常系フルフロー（outbound_slips/outbound_slip_linesへのDB登録検証）がなかった。SC-007に受注CSVの採番検証は含まれるが、完全な取り込みフローテストではなかった | 対象ファイル修正済み | SC-011（受注CSVフルフローテスト）を新規追加。outbound_slips/outbound_slip_linesのDB検証、if_executionsの`if_type='ORDER'`検証を含む |
| 3 | シナリオ一覧 | functional-design/SCR-15-interface.md 対象ロール、architecture-design/09-interface-architecture.md セクション1.2 | 権限テストが欠落していた。全テストシナリオがWAREHOUSE_MANAGERロールのみで実行されており、(1) SYSTEM_ADMINロールでの正常系テスト、(2) 権限不足ロール（WAREHOUSE_STAFF等）のアクセス拒否テストが含まれていなかった | 対象ファイル修正済み | SC-012（権限不足でのアクセス拒否テスト）とSC-014（SYSTEM_ADMINでの正常系テスト）を新規追加 |
| 4 | シナリオ一覧 | functional-design/SCR-15-interface.md MSG-E-IF001-004、architecture-design/09-interface-architecture.md セクション10.3 | ファイルサイズ上限（50MB）超過のテストシナリオが欠落していた。セキュリティ要件・画面設計書でファイルサイズ制限が定義されているがテストケースがなかった | 対象ファイル修正済み | SC-013（50MB超ファイルのバリデーション拒否テスト）を新規追加 |
| 5 | SC-010 テストステップ #17, #18 | architecture-design/09-interface-architecture.md セクション6.3 L1バリデーション | L1ファイルレベルエラー（データ行0件、行数超過）のテストステップで確認方法にエラーコードが記載されていなかった。ヘッダ不正（step 16）には`WMS-E-IFX-003`が記載されていたが、データ行0件と行数超過にはエラーコードが欠落 | 対象ファイル修正済み | step 17に`WMS-E-IFX-005`、step 18に`WMS-E-IFX-006`を追加 |
| 6 | SC-009 テストステップ #2 | functional-design/SCR-15-interface.md IF-003画面項目一覧（IF003-COL-IFTYPE, IF003-COL-BLOB-WARN） | 取り込み履歴一覧テーブルの表示確認項目に「I/F種別」列と「Blob移動警告」列が含まれていなかった。SCR-15のIF-003画面項目一覧にはIF003-COL-IFTYPE（I/F種別）とIF003-COL-BLOB-WARN（Blob移動警告アイコン）が定義されている | 対象ファイル修正済み | step 2の期待結果に「I/F種別」と「Blob移動警告アイコン列」の確認を追加 |
| 7 | SC-001〜SC-010 全体 | functional-requirements/07-interface.md | テストシナリオ全体の業務フローカバレッジを確認。機能要件定義書の4機能（ファイル一覧照会・バリデーション実行・取り込み実行・取り込み履歴照会）について、正常系・異常系が網羅されている | 指摘なし | — |
| 8 | SC-004, SC-005 DB検証 | data-model/03-transaction-tables.md if_executions テーブル定義 | if_executionsテーブルのカラム名（if_type, file_name, total_count, success_count, error_count, mode, status, blob_move_failed）がデータモデル定義と一致していることを確認 | 指摘なし | — |
| 9 | SC-006 DB検証 | functional-design/IF-01-inbound-plan.md セクション2.1, 2.4 | 伝票グルーピングロジック（partner_code + planned_date）およびnoteカラムのマッピング（グループ内最初の行のnoteを採用）がIF-01設計書と整合していることを確認 | 指摘なし | — |
| 10 | SC-007 DB検証 | functional-design/IF-01-inbound-plan.md セクション5.1 | 伝票採番フォーマット `INB-YYYYMMDD-NNNN` がIF-01設計書と整合していることを確認。営業日ベースでの採番、既存最大値+1のインクリメントルールも正しい | 指摘なし | — |
| 11 | SC-010 バリデーションエラーコード一覧 | architecture-design/09-interface-architecture.md セクション6.3〜6.7 | テストで使用しているエラーコード（WMS-E-IFX-301〜306, 401〜404, 501〜502, 003, 105）がアーキテクチャ設計書のバリデーションエラーコード定義と全て一致していることを確認 | 指摘なし | — |
