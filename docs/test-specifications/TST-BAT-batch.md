# テスト仕様書 — バッチ管理（TST-BAT）

## ヘッダー情報

| 項目 | 記載内容 |
|------|---------|
| テスト仕様書ID | TST-BAT |
| テスト対象機能 | バッチ管理（日替処理実行・バッチ実行履歴・日次集計レポート） |
| 対象設計書 | SCR-11（BAT-001, BAT-002）、API-09（API-BAT-001〜003）、BAT-01-daily-close.md |
| 作成者 | |
| 作成日 | 2026-03-20 |
| レビュー者 | |
| レビュー日 | |

---

## テストシナリオ一覧

| シナリオID | シナリオ名 | 優先度 | 前提条件 | 結合 | E2E |
|-----------|----------|:------:|---------|:----:|:---:|
| SC-001 | 正常系: 日替処理実行（全6ステップ正常完了） | 高 | ログイン済み、WAREHOUSE_MANAGER。入荷・出荷・在庫・返品のテストデータ登録済み | ○ | ○ |
| SC-002 | 異常系: 二重実行防止（同一営業日に再実行で409） | 高 | SC-001 が成功済み（同一営業日にSUCCESSレコード存在） | ○ | ○ |
| SC-003 | 正常系: 途中失敗→再実行（完了済みステップスキップ） | 高 | 同一営業日にFAILEDレコード存在（Step 3で失敗想定） | ○ | ○ |
| SC-004 | 正常系: 営業日更新の冪等性（再実行で二重進行しない） | 高 | Step 1完了後にStep 2以降で失敗した状態 | ○ | — |
| SC-005 | 異常系: 営業日連続性チェック（2日飛ばしでエラー） | 高 | 現在営業日から2日以上先の日付を指定 | ○ | ○ |
| SC-006 | 正常系: 未入荷/未出荷リスト生成の検証 | 高 | 未入庫の入荷予定・未出荷の受注データが存在 | ○ | — |
| SC-007 | 正常系: 日次集計レコードの検証（返品数含む） | 高 | 入荷・出荷・在庫・返品の実績データが存在 | ○ | — |
| SC-008 | 正常系: バッチ実行履歴の照会 | 中 | 複数件の実行履歴が存在（成功・失敗混在） | ○ | ○ |
| SC-009 | 正常系: 日次集計レポート出力（RPT-17） | 中 | SC-001 が成功済み（日次集計レコードが生成済み） | ○ | ○ |
| SC-010 | 正常系: 0件データでの日替処理実行 | 中 | ログイン済み、WAREHOUSE_MANAGER。対象営業日に入荷・出荷・在庫・返品データが一切存在しない | ○ | — |

---

## テストシナリオ詳細

### SC-001: 正常系: 日替処理実行（全6ステップ正常完了）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-001 |
| シナリオ名 | 正常系: 日替処理の全6ステップが正常に完了する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。現在営業日が `2026-03-19`。入荷伝票（STORED）・出荷伝票（SHIPPED）・在庫データ・返品データ（COMPLETED）がテストデータとして登録済み。2ヶ月以上前の完了済みトランデータあり |
| テストデータ | `R__010_batch_test_data.sql`（入荷2件STORED、出荷3件SHIPPED、在庫10件、返品1件COMPLETED、2ヶ月前の完了済み入荷・出荷各1件） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | BAT-001（日替処理実行）画面を開く | 画面が表示される。現在の営業日: `2026/03/19`、対象営業日に `2026/03/20` がデフォルト設定される | URL `/batch/daily-close` に遷移 |
| 2 | 対象営業日が `2026/03/20` であることを確認し、「日替処理実行」ボタンをクリック | 確認ダイアログ（MSG-W-BAT001-001）が表示される | `2026/03/20 の日替処理を実行します。この操作は取り消せません。実行しますか？` |
| 3 | 確認ダイアログで「実行」をクリック | 処理進捗カードが表示され、ステップが順次実行される。実行ボタンが無効化される | 経過時間カウンターが動作開始 |
| 4 | 全ステップ完了を待つ | 6ステップ全てが「完了」になる。MSG-S-BAT001-001 が表示される | 全ステップに完了アイコンが表示。`日替処理が完了しました（対象営業日: 2026/03/20）` |
| 5 | 実行結果カードを確認する | ステータス: 成功、対象営業日: `2026/03/20`、実行者名が表示される。未入荷リスト件数・未出荷リスト件数が表示される | 実行結果カードが「成功」バッジで表示 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | `business_date` | `current_business_date = '2026-03-20'` に更新されていること |
| 2 | `inbound_summaries` | `business_date = '2026-03-20'` のレコードが倉庫単位で存在すること。`inbound_count`, `inbound_quantity_total` がテストデータと一致 |
| 3 | `outbound_summaries` | `business_date = '2026-03-20'` のレコードが倉庫単位で存在すること。`outbound_count`, `outbound_quantity_total` がテストデータと一致 |
| 4 | `inventory_snapshots` | `business_date = '2026-03-20'` のスナップショットが倉庫・商品・荷姿単位で存在すること |
| 5 | `inbound_slips_backup` | 2ヶ月以上前の入庫完了データがコピーされていること |
| 6 | `outbound_slips_backup` | 2ヶ月以上前の出荷完了データがコピーされていること |
| 7 | `unreceived_list_records` | `batch_business_date = '2026-03-20'` の未入荷レコードが存在すること（予定日 <= 営業日かつ未入庫完了の伝票） |
| 8 | `unshipped_list_records` | `batch_business_date = '2026-03-20'` の未出荷レコードが存在すること（予定日 <= 営業日かつ未出荷完了の伝票） |
| 9 | `daily_summary_records` | `business_date = '2026-03-20'` のレコードが倉庫単位で存在すること。入荷数・出荷数・返品数・在庫数・未入荷件数・未出荷件数が正しい値 |
| 10 | `batch_execution_logs` | `target_business_date = '2026-03-20'`, `status = 'SUCCESS'`, `step1_status` 〜 `step6_status` が全て `'SUCCESS'` であること |

---

### SC-002: 異常系: 二重実行防止（同一営業日に再実行で409）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-002 |
| シナリオ名 | 異常系: 処理済みの営業日を指定して実行するとエラーになる |
| 前提条件 | SC-001 が成功済み。`batch_execution_logs` に `target_business_date = '2026-03-20'`, `status = 'SUCCESS'` のレコードが存在する |
| テストデータ | SC-001 の実行結果を使用 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | BAT-001画面を開く | 画面が表示される。現在の営業日: `2026/03/20` | URL `/batch/daily-close` |
| 2 | 対象営業日に `2026/03/20`（処理済み）を入力する | 日付が入力される | — |
| 3 | 「日替処理実行」ボタンをクリック | 確認ダイアログが表示される | — |
| 4 | 確認ダイアログで「実行」をクリック | MSG-E-BAT001-001 が表示される | `2026/03/20 は既に処理済みです。同一営業日への二重実行はできません。` |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | `batch_execution_logs` | `target_business_date = '2026-03-20'` のレコードが1件のまま（新規レコードが追加されていない）。`status = 'SUCCESS'` のまま |

**API検証（結合テストのみ）:**

| # | 検証内容 |
|:-:|---------|
| 1 | `POST /api/v1/batch/daily-close` に `{"targetBusinessDate":"2026-03-20"}` を送信すると、`409 Conflict`（`BATCH_ALREADY_RUNNING`）が返却されること |

---

### SC-003: 正常系: 途中失敗→再実行（完了済みステップスキップ）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-003 |
| シナリオ名 | 正常系: 途中で失敗した日替処理を再実行すると、完了済みステップがスキップされる |
| 前提条件 | `batch_execution_logs` に `target_business_date = '2026-03-21'`, `status = 'FAILED'`, `step1_status = 'SUCCESS'`, `step2_status = 'SUCCESS'`, `step3_status = 'FAILED'`, `step4_status` 〜 `step6_status = 'SKIPPED'` のレコードが存在する |
| テストデータ | Step 3失敗の原因を解消したテストデータ。`R__011_batch_retry_data.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | BAT-001画面を開く | 画面が表示される | URL `/batch/daily-close` |
| 2 | 対象営業日に `2026/03/21` を入力し、「日替処理実行」ボタンをクリック | 確認ダイアログが表示される | — |
| 3 | 確認ダイアログで「実行」をクリック | 処理進捗カードが表示される。Step 1, Step 2 は「スキップ（完了済み）」と表示され、Step 3 から実行が開始される。MSG-I-BAT001-001 がStep 1, 2 に表示される | ステップ1: `スキップ（完了済み）`、ステップ2: `スキップ（完了済み）`、ステップ3: `実行中...` |
| 4 | 全ステップ完了を待つ | Step 3〜6 が「完了」になる。MSG-S-BAT001-001 が表示される | 全ステップに完了またはスキップアイコンが表示 |
| 5 | 実行結果カードを確認する | ステータス: 成功 | 成功バッジが表示 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | `batch_execution_logs` | 旧FAILEDレコードが削除され、新しい `status = 'SUCCESS'` のレコードが1件存在すること。全ステップが `SUCCESS` |
| 2 | `inbound_summaries` | Step 2 はスキップだが、ON CONFLICT DO UPDATE により冪等。レコードが正しく存在すること |
| 3 | `outbound_summaries` | Step 3 が正常完了し、集計レコードが存在すること |
| 4 | `daily_summary_records` | Step 6 完了後に日次集計レコードが正しく生成されていること |

---

### SC-004: 正常系: 営業日更新の冪等性（再実行で二重進行しない）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-004 |
| シナリオ名 | 正常系: 営業日更新済みの状態で再実行しても営業日が二重に進行しない |
| 前提条件 | 営業日が既に `2026-03-22` に更新済み（Step 1完了）だが、Step 2以降で失敗した状態。`batch_execution_logs` に FAILED レコードが存在（`step1_status = 'SUCCESS'`） |
| テストデータ | `business_date.current_business_date = '2026-03-22'`。FAILEDログに `target_business_date = '2026-03-22'` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | `POST /api/v1/batch/daily-close` に `{"targetBusinessDate":"2026-03-22"}` を送信 | Step 1 がスキップされ、Step 2 から実行開始。200 OK が返却される | レスポンスの `steps[0].status = "SUCCESS"`（スキップ扱い） |
| 2 | レスポンスの `status` を確認 | `"SUCCESS"` であること | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | `business_date` | `current_business_date = '2026-03-22'` のまま（`2026-03-23` に進んでいないこと） |
| 2 | `batch_execution_logs` | 新しい SUCCESS レコードが作成され、旧 FAILED レコードが削除されていること |

---

### SC-005: 異常系: 営業日連続性チェック（2日飛ばしでエラー）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-005 |
| シナリオ名 | 異常系: 営業日が連続していない場合にStep 1でエラーになる |
| 前提条件 | 現在営業日が `2026-03-19`。対象営業日に `2026-03-22`（3日後）を指定 |
| テストデータ | `business_date.current_business_date = '2026-03-19'` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | BAT-001画面を開く | 現在の営業日: `2026/03/19`、対象営業日: `2026/03/20`（デフォルト） | — |
| 2 | 対象営業日を `2026/03/22` に変更し、「日替処理実行」ボタンをクリック → 確認ダイアログで「実行」 | 処理進捗が表示され、Step 1 が失敗する。MSG-E-BAT001-002 が表示される | Step 1: 失敗（赤色強調）、Step 2〜6: `SKIPPED` |
| 3 | 実行結果カードを確認する | ステータス: 失敗。エラー内容にStep 1のエラーメッセージ（「営業日が連続していない」旨）が表示される | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | `business_date` | `current_business_date = '2026-03-19'` のまま（変更されていないこと） |
| 2 | `batch_execution_logs` | `target_business_date = '2026-03-22'`, `status = 'FAILED'`, `step1_status = 'FAILED'`, `step2_status` 〜 `step6_status = 'SKIPPED'` |

**API検証（結合テストのみ）:**

| # | 検証内容 |
|:-:|---------|
| 1 | `POST /api/v1/batch/daily-close` に `{"targetBusinessDate":"2026-03-22"}` を送信すると、200 OK で `status = "FAILED"`, `steps[0].status = "FAILED"`, `steps[0].errorMessage` に営業日連続性エラーメッセージが含まれること |

---

### SC-006: 正常系: 未入荷/未出荷リスト生成の検証

| 項目 | 内容 |
|------|------|
| シナリオID | SC-006 |
| シナリオ名 | 正常系: 日替処理で未入荷リスト・未出荷リストが正しく生成される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。以下のテストデータが登録済み: (a) 入荷予定日 <= 対象営業日で未入庫完了（status=PLANNED or INSPECTED）の入荷伝票3件、(b) 出荷予定日 <= 対象営業日で未出荷完了（status=ORDERED or PICKED）の出荷伝票2件、(c) キャンセル済み伝票1件（リスト対象外） |
| テストデータ | `R__012_unreceived_unshipped_data.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | 日替処理を実行する（SC-001と同様の手順） | 全6ステップ正常完了 | — |
| 2 | 実行結果カードの未入荷リスト件数を確認 | 未入荷リスト: 3件 | BAT001-RESULT-UNINBOUND |
| 3 | 実行結果カードの未出荷リスト件数を確認 | 未出荷リスト: 2件 | BAT001-RESULT-UNOUTBOUND |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | `unreceived_list_records` | 対象営業日のレコードが3件存在すること。`slip_number`, `planned_date`, `partner_code`, `product_code`, `planned_qty`, `current_status` が正しいこと |
| 2 | `unreceived_list_records` | キャンセル済み伝票（status=CANCELLED）がリストに含まれていないこと |
| 3 | `unreceived_list_records` | 入庫完了済み伝票（status=STORED）がリストに含まれていないこと |
| 4 | `unshipped_list_records` | 対象営業日のレコードが2件存在すること。`slip_number`, `planned_date`, `partner_code`, `product_code`, `ordered_qty`, `current_status` が正しいこと |
| 5 | `unshipped_list_records` | キャンセル済み伝票（status=CANCELLED）がリストに含まれていないこと |
| 6 | `unshipped_list_records` | 出荷完了済み伝票（status=SHIPPED）がリストに含まれていないこと |
| 7 | `unreceived_list_records` | 入荷予定日が対象営業日より**後**の伝票がリストに含まれていないこと |

---

### SC-007: 正常系: 日次集計レコードの検証（返品数含む）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-007 |
| シナリオ名 | 正常系: 日次集計レコードに入荷・出荷・在庫・返品・未入荷・未出荷の全項目が正しく記録される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。倉庫A, Bの2倉庫が存在。(a) 倉庫Aに入庫完了2件・出荷完了1件・在庫5商品・返品1件(COMPLETED)・未入荷2件・未出荷1件、(b) 倉庫Bに入庫完了0件・出荷完了1件・在庫3商品・返品0件・未入荷0件・未出荷0件 |
| テストデータ | `R__013_daily_summary_data.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | 日替処理を実行する | 全6ステップ正常完了 | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | `daily_summary_records`（倉庫A） | `inbound_count = 2`, `outbound_count = 1`, `return_count = 1`, `return_quantity_total` がバラ換算で正しい値 |
| 2 | `daily_summary_records`（倉庫A） | `inventory_quantity_total` が在庫スナップショットのバラ換算合計と一致すること |
| 3 | `daily_summary_records`（倉庫A） | `unreceived_count = 2`, `unshipped_count = 1` |
| 4 | `daily_summary_records`（倉庫B） | `inbound_count = 0`, `outbound_count = 1`, `return_count = 0`, `return_quantity_total = 0` |
| 5 | `daily_summary_records`（倉庫B） | `unreceived_count = 0`, `unshipped_count = 0` |
| 6 | `daily_summary_records` | バラ換算の計算が正しいこと（CASE: `case_quantity * ball_quantity`、BALL: `ball_quantity`、PIECE: 1） |

---

### SC-008: 正常系: バッチ実行履歴の照会

| 項目 | 内容 |
|------|------|
| シナリオID | SC-008 |
| シナリオ名 | 正常系: バッチ実行履歴一覧で過去の実行結果を検索・確認できる |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。複数件の実行履歴（成功3件、失敗1件）が存在 |
| テストデータ | `R__014_batch_history_data.sql`（4件の `batch_execution_logs` レコード） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | BAT-002（バッチ実行履歴一覧）画面を開く | 一覧画面が表示され、直近1ヶ月の履歴が実行日時降順で表示される | URL `/batch/history` |
| 2 | 結果フィルタで「失敗」を選択し「検索」をクリック | 失敗レコードのみが一覧に表示される | 赤色の「失敗」バッジのレコードのみ表示 |
| 3 | 「クリア」ボタンをクリック | フィルタがリセットされ、全件表示に戻る | — |
| 4 | 成功レコードの「詳細」ボタンをクリック | 詳細ドロワーが展開される。6ステップの結果（全SUCCESS）、処理時間、未入荷/未出荷件数が表示される | BAT002-DETAIL-STEPS に6ステップの結果表示 |
| 5 | 失敗レコードの「詳細」ボタンをクリック | 詳細ドロワーが展開される。失敗ステップが赤色表示され、エラー内容が表示される。SKIPPED ステップも表示される | BAT002-DETAIL-ERROR にエラーメッセージ表示 |
| 6 | 対象営業日フィルタに特定の日付を入力して検索 | 該当営業日のレコードのみ表示される | — |

**API検証（結合テストのみ）:**

| # | 検証内容 |
|:-:|---------|
| 1 | `GET /api/v1/batch/executions?status=FAILED` で失敗レコードのみ返却されること |
| 2 | `GET /api/v1/batch/executions?targetBusinessDate=2026-03-20` で該当営業日のみ返却されること |
| 3 | `GET /api/v1/batch/executions/{id}` で詳細（`step1Status` 〜 `step6Status`、`executedByName`）が正しく返却されること |
| 4 | 存在しない `id` で `GET /api/v1/batch/executions/{id}` を呼ぶと `404 BATCH_EXECUTION_NOT_FOUND` が返却されること |

---

### SC-009: 正常系: 日次集計レポート出力（RPT-17）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-009 |
| シナリオ名 | 正常系: バッチ実行履歴画面から日次集計レポートをPDF出力できる |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。SC-001 が成功済み（`2026-03-20` の日替処理完了） |
| テストデータ | SC-001 の結果（`daily_summary_records` にレコード存在） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | BAT-002画面を開く | 実行履歴一覧が表示される | URL `/batch/history` |
| 2 | 「日次集計レポート出力」ボタンをクリック | RPT-017ダイアログが表示される。直近の処理済み営業日がデフォルト選択されている | RPT017-BIZ-DATE に `2026/03/20` |
| 3 | 対象営業日 `2026/03/20` を選択して「PDF出力」ボタンをクリック | PDFダウンロードが開始される。MSG-S-BAT002-001 が表示される | `日次集計レポートのダウンロードを開始しました。` |
| 4 | ダウンロードされたPDFを確認する | 対象営業日・入荷件数/数量・出荷件数/数量・在庫数量・未入荷件数・未出荷件数が記載されている | PDF内容の目視確認 |
| 5 | 日替処理未実行の営業日を指定して「PDF出力」をクリック | MSG-E-BAT002-001 が表示される | `{対象営業日} の日替処理実行履歴が存在しません。対象営業日を確認してください。` |

**API検証（結合テストのみ）:**

| # | 検証内容 |
|:-:|---------|
| 1 | `GET /api/v1/reports/daily-summary?targetBusinessDate=2026-03-20` でPDFバイナリが返却されること（Content-Type: `application/pdf`） |
| 2 | 未実行営業日を指定すると適切なエラーレスポンスが返却されること |

---

### SC-010: 正常系: 0件データでの日替処理実行

| 項目 | 内容 |
|------|------|
| シナリオID | SC-010 |
| シナリオ名 | 正常系: 対象営業日に業務データが0件でも日替処理が正常完了する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。対象営業日に入庫完了・出荷完了・在庫・返品データが一切存在しない。倉庫マスタにアクティブ倉庫が1件以上存在する |
| テストデータ | `R__015_batch_zero_data.sql`（対象営業日のトランザクションデータなし。倉庫マスタのみ） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | BAT-001画面を開き、対象営業日を確認して「日替処理実行」→確認ダイアログ「実行」 | 処理進捗が表示され、6ステップ全てが「完了」になる。MSG-S-BAT001-001 が表示される | 全ステップに完了アイコン表示 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | `business_date` | `current_business_date` が対象営業日に更新されていること |
| 2 | `inbound_summaries` | 対象営業日のレコードが存在しないこと（入庫完了0件のため集計レコード未生成） |
| 3 | `outbound_summaries` | 対象営業日のレコードが存在しないこと（出荷完了0件のため集計レコード未生成） |
| 4 | `inventory_snapshots` | 対象営業日のレコードが存在しないこと（在庫0件の場合スナップショット未生成） |
| 5 | `unreceived_list_records` | 対象営業日のレコードが存在しないこと（未入荷対象0件） |
| 6 | `unshipped_list_records` | 対象営業日のレコードが存在しないこと（未出荷対象0件） |
| 7 | `daily_summary_records` | アクティブ倉庫分のレコードが存在し、全数値項目が0であること |
| 8 | `batch_execution_logs` | `status = 'SUCCESS'`、全ステップが `SUCCESS` であること |

---

## Playwrightコード例

```typescript
test.describe('TST-BAT: バッチ管理', () => {

  test('SC-001: 日替処理の全6ステップが正常完了する', async ({ page }) => {
    await loginAs(page, 'WAREHOUSE_MANAGER');

    // Step 1: BAT-001画面を開く
    await page.goto('/batch/daily-close');
    await expect(page.locator('[data-testid="current-business-date"]')).toContainText('2026/03/19');
    await expect(page.locator('[data-testid="target-business-date"]')).toHaveValue('2026-03-20');

    // Step 2: 実行ボタンクリック
    await page.click('[data-testid="daily-close-run-btn"]');
    await expect(page.locator('.el-message-box')).toBeVisible();

    // Step 3: 確認ダイアログ「実行」
    await page.click('.el-message-box__btns button:has-text("実行")');
    await expect(page.locator('[data-testid="progress-card"]')).toBeVisible();
    await expect(page.locator('[data-testid="daily-close-run-btn"]')).toBeDisabled();

    // Step 4: 全ステップ完了を待つ（最大60秒）
    await expect(page.locator('[data-testid="result-status"]')).toContainText('成功', { timeout: 60000 });

    // Step 5: 実行結果確認
    await expect(page.locator('[data-testid="result-date"]')).toContainText('2026/03/20');
    await expect(page.locator('[data-testid="result-unreceived"]')).toBeVisible();
    await expect(page.locator('[data-testid="result-unshipped"]')).toBeVisible();
  });

  test('SC-002: 同一営業日の二重実行がエラーになる', async ({ page }) => {
    await loginAs(page, 'WAREHOUSE_MANAGER');

    await page.goto('/batch/daily-close');
    await page.fill('[data-testid="target-business-date"]', '2026-03-20');
    await page.click('[data-testid="daily-close-run-btn"]');
    await page.click('.el-message-box__btns button:has-text("実行")');

    // エラーメッセージの確認
    await expect(page.locator('.el-message--error')).toContainText('既に処理済みです');
  });

  test('SC-003: 途中失敗→再実行でスキップが表示される', async ({ page }) => {
    await loginAs(page, 'WAREHOUSE_MANAGER');

    await page.goto('/batch/daily-close');
    await page.fill('[data-testid="target-business-date"]', '2026-03-21');
    await page.click('[data-testid="daily-close-run-btn"]');
    await page.click('.el-message-box__btns button:has-text("実行")');

    // スキップ表示を確認
    await expect(page.locator('[data-testid="step-1-status"]')).toContainText('スキップ');
    await expect(page.locator('[data-testid="step-2-status"]')).toContainText('スキップ');

    // 全体成功を待つ
    await expect(page.locator('[data-testid="result-status"]')).toContainText('成功', { timeout: 60000 });
  });

  test('SC-008: バッチ実行履歴の照会', async ({ page }) => {
    await loginAs(page, 'WAREHOUSE_MANAGER');

    // Step 1: BAT-002画面を開く
    await page.goto('/batch/history');
    await expect(page.locator('table tbody tr')).toHaveCount(4);

    // Step 2: 失敗フィルタ
    await page.selectOption('[data-testid="filter-result"]', 'FAILED');
    await page.click('[data-testid="search-btn"]');
    const failedRows = page.locator('table tbody tr');
    await expect(failedRows).toHaveCount(1);
    await expect(failedRows.first().locator('.el-tag--danger')).toBeVisible();

    // Step 4: 詳細ドロワー展開
    await page.click('[data-testid="clear-btn"]');
    await page.click('table tbody tr:first-child [data-testid="detail-btn"]');
    await expect(page.locator('[data-testid="detail-steps"]')).toBeVisible();
  });

  test('SC-009: 日次集計レポートPDF出力', async ({ page }) => {
    await loginAs(page, 'WAREHOUSE_MANAGER');

    await page.goto('/batch/history');
    await page.click('[data-testid="report-btn"]');
    await expect(page.locator('.el-dialog')).toBeVisible();

    const downloadPromise = page.waitForEvent('download');
    await page.click('[data-testid="pdf-output-btn"]');
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/daily-summary.*\.pdf/);
  });
});
```
