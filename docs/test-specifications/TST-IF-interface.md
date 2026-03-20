# テスト仕様書 — 外部連携I/F管理（TST-IF）

## ヘッダー情報

| 項目 | 記載内容 |
|------|---------|
| テスト仕様書ID | TST-IF |
| テスト対象機能 | 外部連携I/F管理（ファイル一覧・バリデーション・取り込み・履歴照会） |
| 対象設計書 | SCR-15（IF-001〜IF-003）、IF-01-inbound-plan.md、IF-02-order.md |
| 作成者 | |
| 作成日 | 2026-03-20 |
| レビュー者 | |
| レビュー日 | |

---

## テストシナリオ一覧

| シナリオID | シナリオ名 | 優先度 | 前提条件 | 結合 | E2E |
|-----------|----------|:------:|---------|:----:|:---:|
| SC-001 | 正常系: ファイル一覧表示（I/F種別タブ切替） | 高 | ログイン済み、WAREHOUSE_MANAGER。Blob Storageのpendingフォルダにファイル配置済み | ○ | ○ |
| SC-002 | 正常系: バリデーション実行（正常ファイル） | 高 | 入荷予定CSVが全行バリデーション成功 | ○ | ○ |
| SC-003 | 正常系: バリデーション実行（エラー含むファイル） | 高 | 入荷予定CSVにバリデーションエラー行が含まれる | ○ | ○ |
| SC-004 | 正常系: 取り込み実行（SUCCESS_ONLYモード — 成功行のみDB登録） | 高 | バリデーション済みCSVに成功行とエラー行が混在 | ○ | ○ |
| SC-005 | 正常系: 取り込み実行（DISCARDモード — DB登録なし） | 高 | pendingフォルダにCSVが存在 | ○ | ○ |
| SC-006 | 正常系: 伝票グルーピング（同一仕入先+予定日→1伝票） | 高 | 同一partner_code+planned_dateの複数行を含むCSV | ○ | — |
| SC-007 | 正常系: 伝票採番（営業日ベース） | 高 | 営業日が確定済み。既存伝票の採番状態が把握されている | ○ | — |
| SC-008 | 正常系: Blob移動（pending→processed） | 高 | 取り込み実行後のBlob Storage状態を確認 | ○ | — |
| SC-009 | 正常系: 取り込み履歴照会 | 中 | 複数件の取り込み履歴が存在（完了・破棄・失敗混在） | ○ | ○ |
| SC-010 | 異常系: バリデーションエラー（マスタ不存在、期限切れ、重複商品等） | 高 | 各種エラーパターンのCSVを用意 | ○ | ○ |
| SC-011 | 正常系: 受注CSV（IFX-002）の取り込みフルフロー | 高 | ログイン済み、WAREHOUSE_MANAGER。受注CSVがpendingに配置済み。出荷先マスタ・商品マスタ登録済み | ○ | ○ |
| SC-012 | 異常系: 権限不足（WAREHOUSE_STAFFロール）のアクセス拒否 | 中 | WAREHOUSE_STAFFでログイン済み | ○ | — |
| SC-013 | 異常系: ファイルサイズ超過（50MB超） | 中 | 50MBを超えるCSVファイルがpendingに存在 | ○ | — |
| SC-014 | 正常系: SYSTEM_ADMINロールでのI/F操作 | 中 | SYSTEM_ADMINでログイン済み。入荷予定CSVがpendingに存在 | ○ | — |

---

## テストシナリオ詳細

### SC-001: 正常系: ファイル一覧表示（I/F種別タブ切替）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-001 |
| シナリオ名 | 正常系: ファイル一覧画面でI/F種別タブを切り替え、各種別のファイルが正しく表示される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。Blob Storage の `iffiles/inbound-plan/pending/` に入荷予定CSV 2件、`iffiles/order/pending/` に受注CSV 1件が配置済み |
| テストデータ | `INB-PLAN-001.csv`（12KB）、`INB-PLAN-002.csv`（8KB）、`ORD-001.csv`（15KB）をBlob Storageに配置 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | IF-001（ファイル一覧）画面を開く | 画面が表示される。初期タブ「入荷予定(IFX-001)」が選択され、入荷予定CSVファイル2件が一覧表示される | URL `/interface`。IF001-FILE-COUNT に「全 2ファイル」 |
| 2 | 一覧テーブルの内容を確認する | ファイル名（INB-PLAN-001.csv, INB-PLAN-002.csv）、サイズ（12KB, 8KB）、配置日時が表示される。各行に「バリデーション」「取り込み」ボタンが表示される | — |
| 3 | 「受注(IFX-002)」タブをクリックする | タブが切り替わり、受注CSVファイル1件が表示される | IF001-FILE-COUNT に「全 1ファイル」、ファイル名 `ORD-001.csv` |
| 4 | 「入荷予定(IFX-001)」タブに戻る | 入荷予定CSVファイル2件が再表示される | IF001-FILE-COUNT に「全 2ファイル」 |
| 5 | 「更新」ボタンをクリック | 一覧が再取得される（同じ内容が表示される） | ローディング表示後に一覧が更新される |

**API検証（結合テストのみ）:**

| # | 検証内容 |
|:-:|---------|
| 1 | `GET /api/v1/interface/IFX-001/files` でファイル名・サイズ・配置日時を含むリストが返却されること |
| 2 | `GET /api/v1/interface/IFX-002/files` で受注ファイルのリストが返却されること |
| 3 | pendingフォルダが空の場合、空リストが返却されること |

---

### SC-002: 正常系: バリデーション実行（正常ファイル）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-002 |
| シナリオ名 | 正常系: 全行バリデーション成功のCSVファイルでバリデーション結果が正しく表示される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。`INB-PLAN-001.csv`（10行、全行正常データ）がpendingに存在。CSVに含まれる`partner_code`, `product_code`が全てマスタに登録済み |
| テストデータ | `INB-PLAN-001.csv`（ヘッダ1行+データ10行。取引先コード: SUP-0001, SUP-0002。商品コード: PRD-001〜PRD-005） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | IF-001画面で「入荷予定(IFX-001)」タブの `INB-PLAN-001.csv` の「バリデーション」ボタンをクリック | ローディング表示後、IF-002（バリデーション結果）ダイアログが表示される | ダイアログタイトル: `バリデーション結果 — INB-PLAN-001.csv` |
| 2 | サマリーを確認する | 総件数: 10件、成功: 10件、エラー: 0件。MSG-I-IF002-001 が表示される | IF002-TOTAL-COUNT = 10、IF002-SUCCESS-COUNT = 10、IF002-ERROR-COUNT = 0 |
| 3 | エラー詳細テーブルの表示を確認 | エラー詳細テーブルは非表示 | — |
| 4 | ボタン状態を確認する | 「成功行のみ取り込む」が有効、「全件破棄」が有効、「閉じる」が有効 | — |
| 5 | 「閉じる」ボタンをクリック | ダイアログが閉じ、IF-001に戻る | — |

**API検証（結合テストのみ）:**

| # | 検証内容 |
|:-:|---------|
| 1 | `POST /api/v1/interface/IFX-001/validate` に `{fileName: "INB-PLAN-001.csv", warehouseId: 1}` を送信すると、`totalRows=10`, `successCount=10`, `errorCount=0`, `rows=[]` が返却されること |

---

### SC-003: 正常系: バリデーション実行（エラー含むファイル）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-003 |
| シナリオ名 | 正常系: エラー行を含むCSVファイルのバリデーションでエラー詳細が正しく表示される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。`INB-PLAN-002.csv`（20行、うち3行にエラー）がpendingに存在 |
| テストデータ | `INB-PLAN-002.csv` のエラーパターン: 行5に存在しない取引先コード（SUP-9999）、行12に数量0、行18にロット管理商品でlot_number未入力 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | IF-001画面で `INB-PLAN-002.csv` の「バリデーション」ボタンをクリック | IF-002ダイアログが表示される | — |
| 2 | サマリーを確認する | 総件数: 20件、成功: 17件、エラー: 3件 | IF002-TOTAL-COUNT = 20、IF002-SUCCESS-COUNT = 17、IF002-ERROR-COUNT = 3（赤色バッジ） |
| 3 | エラー詳細テーブルを確認する | 3件のエラーが行番号順に表示される | エラー詳細テーブルが表示されている |
| 4 | エラー行5の内容を確認 | 行#: 5、カラム名: `partner_code`、エラーコード: `WMS-E-IFX-301`、メッセージ: `取引先コード(SUP-9999)が取引先マスタに存在しません` | — |
| 5 | エラー行12の内容を確認 | 行#: 12、カラム名: `planned_qty`、エラーコード: `WMS-E-IFX-105`、メッセージ: 入荷予定数量は1以上の正の整数であること | — |
| 6 | エラー行18の内容を確認 | 行#: 18、カラム名: `lot_number`、エラーコード: `WMS-E-IFX-402`、メッセージ: ロット管理対象商品のためロット番号は必須であること | — |
| 7 | ボタン状態を確認 | 「成功行のみ取り込む」「全件破棄」「閉じる」が全て有効 | — |

---

### SC-004: 正常系: 取り込み実行（SUCCESS_ONLYモード — 成功行のみDB登録）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-004 |
| シナリオ名 | 正常系: SUCCESS_ONLYモードで取り込み、バリデーション成功行のみがDB登録される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。SC-003のバリデーション結果を使用（20行中17行成功、3行エラー）。取引先・商品マスタ登録済み。現在営業日 `2026-03-20` |
| テストデータ | `INB-PLAN-002.csv`（20行、3行エラー含む）。マスタ: SUP-0001, SUP-0002（有効な仕入先）、PRD-001〜PRD-010（有効な商品） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | IF-002ダイアログで「成功行のみ取り込む」ボタンをクリック | 確認ダイアログ（MSG-W-IF002-001）が表示される | `INB-PLAN-002.csv のバリデーション成功行（17件）を取り込みます。エラー行（3件）はスキップされます。実行しますか？` |
| 2 | 確認ダイアログで「実行」をクリック | ローディング表示後、MSG-S-IF001-001 が表示される。ダイアログが閉じ、IF-001のファイル一覧が更新される | `INB-PLAN-002.csv の取り込みが完了しました。（成功: 17件 / エラー: 3件）` |
| 3 | IF-001のファイル一覧を確認 | `INB-PLAN-002.csv` が一覧から消えている（processedへ移動済み） | ファイル件数が1つ減少 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | `inbound_slips` | 成功行17行から伝票グルーピングされた件数の伝票が登録されていること。全伝票の `status = 'PLANNED'` |
| 2 | `inbound_slip_lines` | 成功行のみ（17行）が明細として登録されていること。エラー行（3行）の `product_code` が存在しないこと |
| 3 | `inbound_slips` | `slip_number` が `INB-20260320-XXXX` 形式で採番されていること |
| 4 | `inbound_slips` | `warehouse_id`, `warehouse_code`, `warehouse_name` が正しいこと |
| 5 | `inbound_slips` | `partner_id`, `partner_code`, `partner_name` が正しいこと（マスタから取得した値のスナップショット） |
| 6 | `inbound_slip_lines` | `product_id`, `product_code`, `product_name` が正しいこと |
| 7 | `inbound_slip_lines` | `line_no` が伝票内で1始まりの連番であること |
| 8 | `inbound_slip_lines` | `line_status = 'PENDING'`, `inspected_qty = NULL`, `putaway_location_id = NULL` |
| 9 | `if_executions` | 1件のレコードが追加。`if_type = 'INBOUND_PLAN'`, `mode = 'SUCCESS_ONLY'`, `status = 'COMPLETED'`, `total_count = 20`, `success_count = 17`, `error_count = 3` |
| 10 | `if_executions` | `blob_move_failed = false` |

---

### SC-005: 正常系: 取り込み実行（DISCARDモード — DB登録なし）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-005 |
| シナリオ名 | 正常系: DISCARDモードで取り込み実行し、DB登録なし・ファイル移動のみ行われる |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。`INB-PLAN-003.csv` がpendingに存在 |
| テストデータ | `INB-PLAN-003.csv`（任意の内容） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | IF-001画面で `INB-PLAN-003.csv` の「取り込み」ボタンをクリック | 取り込みモード選択の確認ダイアログ（MSG-W-IF001-001）が表示される | — |
| 2 | 「全件破棄」を選択して「実行」をクリック | MSG-S-IF001-002 が表示される | `INB-PLAN-003.csv を破棄しました。ファイルはprocessedフォルダへ移動されました。` |
| 3 | IF-001のファイル一覧を確認 | `INB-PLAN-003.csv` が一覧から消えている | ファイル件数が減少 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | `inbound_slips` | `INB-PLAN-003.csv` 由来のレコードが**存在しない**こと |
| 2 | `if_executions` | 1件のレコードが追加。`if_type = 'INBOUND_PLAN'`, `mode = 'DISCARD'`, `status = 'DISCARDED'` |

**Blob検証（結合テストのみ）:**

| # | 検証内容 |
|:-:|---------|
| 1 | `iffiles/inbound-plan/pending/INB-PLAN-003.csv` が存在しないこと |
| 2 | `iffiles/inbound-plan/processed/{yyyy}/{MM}/{dd}/{timestamp}_INB-PLAN-003.csv` が存在すること |

---

### SC-006: 正常系: 伝票グルーピング（同一仕入先+予定日→1伝票）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-006 |
| シナリオ名 | 正常系: 同一partner_code + planned_dateの行が1つの伝票にグルーピングされる |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。CSVに以下のパターンが含まれる: グループA（SUP-0001 + 2026-03-20: 3行）、グループB（SUP-0002 + 2026-03-21: 2行）、グループC（SUP-0001 + 2026-03-21: 1行） |
| テストデータ | `INB-PLAN-GROUP.csv`（6データ行、3グループ）。全行バリデーション成功 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | SUCCESS_ONLYモードで取り込みを実行する（SC-004と同様の手順） | 取り込み完了。成功: 6件 | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | `inbound_slips` | 3件の伝票が登録されていること |
| 2 | `inbound_slips`（伝票1） | `partner_code = 'SUP-0001'`, `planned_date = '2026-03-20'` |
| 3 | `inbound_slip_lines`（伝票1） | 3件の明細が登録。`line_no = 1, 2, 3`。商品コードがCSV出現順であること |
| 4 | `inbound_slips`（伝票2） | `partner_code = 'SUP-0002'`, `planned_date = '2026-03-21'` |
| 5 | `inbound_slip_lines`（伝票2） | 2件の明細が登録。`line_no = 1, 2` |
| 6 | `inbound_slips`（伝票3） | `partner_code = 'SUP-0001'`, `planned_date = '2026-03-21'` |
| 7 | `inbound_slip_lines`（伝票3） | 1件の明細が登録。`line_no = 1` |
| 8 | `inbound_slips` | `note` カラムに各グループの最初の行の備考が設定されていること |

---

### SC-007: 正常系: 伝票採番（営業日ベース）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-007 |
| シナリオ名 | 正常系: 伝票番号が営業日ベースで正しく採番される |
| 前提条件 | 現在営業日 `2026-03-20`。`inbound_slips` に `slip_number = 'INB-20260320-0002'` が既に存在（2件登録済み） |
| テストデータ | 既存伝票2件（`INB-20260320-0001`, `INB-20260320-0002`）。新規取り込みCSV（2グループ = 2伝票生成予定） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | 入荷予定CSV（2グループ）をSUCCESS_ONLYモードで取り込む | 取り込み完了 | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | `inbound_slips` | 新規伝票の `slip_number` が `INB-20260320-0003`, `INB-20260320-0004` であること（既存最大値+1からインクリメント） |
| 2 | `inbound_slips` | 伝票番号の営業日部分が `current_business_date`（`20260320`）と一致すること（CSVの `planned_date` ではない） |

**受注CSV（IFX-002）での採番検証:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 3 | `outbound_slips` | 受注CSV取り込み時の伝票番号が `OUT-YYYYMMDD-XXXX` 形式で採番されること |
| 4 | `outbound_slips` | 既存伝票がある場合、連番が正しくインクリメントされること |

---

### SC-008: 正常系: Blob移動（pending→processed）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-008 |
| シナリオ名 | 正常系: 取り込み実行後にCSVファイルがpendingからprocessedフォルダへ移動される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。`iffiles/inbound-plan/pending/INB-PLAN-MOVE.csv` が存在 |
| テストデータ | `INB-PLAN-MOVE.csv`（全行バリデーション成功） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | SUCCESS_ONLYモードで取り込みを実行する | 取り込み完了 | — |

**Blob検証（結合テストのみ）:**

| # | 検証内容 |
|:-:|---------|
| 1 | `iffiles/inbound-plan/pending/INB-PLAN-MOVE.csv` が**存在しない**こと |
| 2 | `iffiles/inbound-plan/processed/{yyyy}/{MM}/{dd}/{timestamp}_INB-PLAN-MOVE.csv` が存在すること |
| 3 | 移動先ファイルの内容が元ファイルと同一であること |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | `if_executions` | `blob_path` がprocessed後の完全パスであること |
| 2 | `if_executions` | `blob_move_failed = false` であること |

**DISCARDモードでの検証:**

| # | 検証内容 |
|:-:|---------|
| 3 | DISCARDモードで実行した場合も、ファイルがpendingからprocessedへ移動されること |
| 4 | DISCARDモード時の `if_executions.blob_path` がprocessed後のパスであること |

---

### SC-009: 正常系: 取り込み履歴照会

| 項目 | 内容 |
|------|------|
| シナリオID | SC-009 |
| シナリオ名 | 正常系: 取り込み履歴一覧で過去の取り込み結果を検索・確認できる |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。複数件の取り込み履歴（COMPLETED 2件、DISCARDED 1件）が存在 |
| テストデータ | `R__020_if_history_data.sql`（`if_executions` テーブルに3件のレコード） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | IF-003（取り込み履歴）画面を開く | 一覧画面が表示される。直近1ヶ月の履歴が実行日時降順で表示される | URL `/interface/history`。IF003-RESULT-COUNT に「全 3件」 |
| 2 | 一覧テーブルの内容を確認する | 実行日時・ファイル名・I/F種別・総件数・成功件数・エラー件数・モード・結果・実行者が表示される。Blob移動警告アイコン列も存在すること | — |
| 3 | COMPLETEDレコードの表示を確認 | 結果列: 緑バッジ「完了」、モード列:「成功行のみ」 | — |
| 4 | DISCARDEDレコードの表示を確認 | 結果列: 灰バッジ「破棄」、モード列:「全件破棄」 | — |
| 5 | I/F種別フィルタで「入荷予定(IFX-001)」を選択して検索 | 入荷予定の履歴のみ表示される | — |
| 6 | 結果フィルタで「破棄」を選択して検索 | DISCARDEDレコードのみ表示される | — |
| 7 | 「クリア」ボタンをクリック | フィルタが初期状態に戻り、全件表示される | — |
| 8 | ファイル名フィルタに「INB」を入力して検索 | ファイル名に「INB」を含む履歴のみ表示される（部分一致） | — |

**API検証（結合テストのみ）:**

| # | 検証内容 |
|:-:|---------|
| 1 | `GET /api/v1/interface/executions` でページング形式の履歴リストが返却されること |
| 2 | `GET /api/v1/interface/executions?ifType=INBOUND_PLAN` でI/F種別フィルタが機能すること |
| 3 | `GET /api/v1/interface/executions?status=DISCARDED` でステータスフィルタが機能すること |
| 4 | `GET /api/v1/interface/executions?fileName=INB` でファイル名部分一致検索が機能すること |

---

### SC-010: 異常系: バリデーションエラー（マスタ不存在、期限切れ、重複商品等）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-010 |
| シナリオ名 | 異常系: 各種バリデーションエラーが正しく検出・表示される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。各種エラーパターンのCSVファイルが用意されている |
| テストデータ | `INB-PLAN-ERR.csv`（入荷予定用: 各種エラーパターン）、`ORD-ERR.csv`（受注用: 各種エラーパターン） |

**テストステップ（入荷予定: IFX-001）:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | `INB-PLAN-ERR.csv` のバリデーションを実行 | エラー一覧が表示される | IF-002ダイアログのエラー詳細テーブル |
| 2 | 存在しない取引先コード（SUP-9999）の行を確認 | エラーコード: `WMS-E-IFX-301`。メッセージ: 取引先マスタに存在しない | — |
| 3 | 無効化された取引先（is_active=false）の行を確認 | エラーコード: `WMS-E-IFX-302` | — |
| 4 | CUSTOMER種別の取引先（SUPPLIERまたはBOTHでない）の行を確認 | エラーコード: `WMS-E-IFX-303` | — |
| 5 | 存在しない商品コード（PRD-9999）の行を確認 | エラーコード: `WMS-E-IFX-304` | — |
| 6 | 無効化された商品（is_active=false）の行を確認 | エラーコード: `WMS-E-IFX-305` | — |
| 7 | 過去日付の入荷予定日の行を確認 | エラーコード: `WMS-E-IFX-401` | — |
| 8 | ロット管理商品でlot_number未入力の行を確認 | エラーコード: `WMS-E-IFX-402` | — |
| 9 | 期限管理商品でexpiry_date未入力の行を確認 | エラーコード: `WMS-E-IFX-403` | — |
| 10 | 期限管理商品でexpiry_dateが現在営業日以前の行を確認 | エラーコード: `WMS-E-IFX-404` | — |
| 11 | 同一伝票内で同一product_codeが重複する行を確認 | エラーコード: `WMS-E-IFX-501` | — |

**テストステップ（受注: IFX-002）:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 12 | 「受注(IFX-002)」タブに切り替え、`ORD-ERR.csv` のバリデーションを実行 | エラー一覧が表示される | — |
| 13 | 出荷禁止商品（shipment_stop_flag=true）の行を確認 | エラーコード: `WMS-E-IFX-306` | — |
| 14 | SUPPLIER種別の取引先（CUSTOMERまたはBOTHでない）の行を確認 | エラーコード: `WMS-E-IFX-303` | — |
| 15 | 同一伝票内で同一product_codeが重複する行を確認 | エラーコード: `WMS-E-IFX-502` | — |

**テストステップ（L1ファイルレベルエラー）:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 16 | ヘッダ行のカラム数が不正なCSVでバリデーション実行 | ファイルエラーアラートが表示される。サマリー・エラー詳細テーブルは非表示。「閉じる」のみ有効 | エラーコード: `WMS-E-IFX-003` |
| 17 | データ行0件（ヘッダのみ）のCSVでバリデーション実行 | ファイルレベルエラーが表示される | エラーコード: `WMS-E-IFX-005` |
| 18 | 10,001行以上のCSVでバリデーション実行 | ファイルレベルエラー（行数超過）が表示される | エラーコード: `WMS-E-IFX-006` |

**API検証（結合テストのみ）:**

| # | 検証内容 |
|:-:|---------|
| 1 | バリデーションAPIのレスポンスに各エラーの `rowNumber`, `columnName`, `errorCode`, `message` が正しく含まれること |
| 2 | 1行に複数エラーがある場合、全エラーが返却されること |

---

### SC-011: 正常系: 受注CSV（IFX-002）の取り込みフルフロー

| 項目 | 内容 |
|------|------|
| シナリオID | SC-011 |
| シナリオ名 | 正常系: 受注CSVファイルをSUCCESS_ONLYモードで取り込み、出荷伝票がDB登録される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。`ORD-001.csv`（5行、全行正常データ）がpendingに存在。出荷先マスタ（CUS-0001, CUS-0002）、商品マスタ（PRD-001〜PRD-005）登録済み。現在営業日 `2026-03-20` |
| テストデータ | `ORD-001.csv`（ヘッダ1行+データ5行。2グループ: CUS-0001+2026-03-22が3行、CUS-0002+2026-03-23が2行） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | IF-001画面で「受注(IFX-002)」タブをクリック | 受注CSVファイル一覧が表示される。`ORD-001.csv` が表示 | — |
| 2 | `ORD-001.csv` の「バリデーション」ボタンをクリック | IF-002ダイアログ表示。総件数: 5件、成功: 5件、エラー: 0件 | — |
| 3 | 「成功行のみ取り込む」ボタンをクリック→確認ダイアログで「実行」 | 取り込み完了。MSG-S-IF001-001表示 | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | `outbound_slips` | 2件の伝票が登録されていること。全伝票の `status = 'ORDERED'`, `slip_type = 'NORMAL'` |
| 2 | `outbound_slips`（伝票1） | `partner_code = 'CUS-0001'`, `planned_date = '2026-03-22'` |
| 3 | `outbound_slip_lines`（伝票1） | 3件の明細。`line_no = 1, 2, 3`。`line_status = 'ORDERED'` |
| 4 | `outbound_slips`（伝票2） | `partner_code = 'CUS-0002'`, `planned_date = '2026-03-23'` |
| 5 | `outbound_slip_lines`（伝票2） | 2件の明細。`line_no = 1, 2`。`line_status = 'ORDERED'` |
| 6 | `outbound_slips` | `slip_number` が `OUT-20260320-XXXX` 形式で採番されていること（営業日ベース） |
| 7 | `outbound_slip_lines` | `inspected_qty = NULL`, `shipped_qty = 0` であること |
| 8 | `if_executions` | 1件のレコード追加。`if_type = 'ORDER'`, `mode = 'SUCCESS_ONLY'`, `status = 'COMPLETED'`, `total_count = 5`, `success_count = 5`, `error_count = 0` |

---

### SC-012: 異常系: 権限不足（WAREHOUSE_STAFFロール）のアクセス拒否

| 項目 | 内容 |
|------|------|
| シナリオID | SC-012 |
| シナリオ名 | 異常系: WAREHOUSE_STAFFロールのユーザーがI/F管理画面にアクセスできないことを確認 |
| 前提条件 | WAREHOUSE_STAFFでログイン済み |
| テストデータ | — |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | `/interface` に直接アクセス | アクセス拒否（403）またはメニューに表示されない | — |

**API検証（結合テストのみ）:**

| # | 検証内容 |
|:-:|---------|
| 1 | `GET /api/v1/interface/IFX-001/files` にWAREHOUSE_STAFF権限のJWTで送信すると `403 Forbidden` が返却されること |
| 2 | `POST /api/v1/interface/IFX-001/validate` にWAREHOUSE_STAFF権限のJWTで送信すると `403 Forbidden` が返却されること |
| 3 | `POST /api/v1/interface/IFX-001/import` にWAREHOUSE_STAFF権限のJWTで送信すると `403 Forbidden` が返却されること |

---

### SC-013: 異常系: ファイルサイズ超過（50MB超）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-013 |
| シナリオ名 | 異常系: 50MBを超えるファイルに対してバリデーション・取り込みが拒否される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。50MBを超えるCSVファイルがpendingに配置済み |
| テストデータ | `INB-PLAN-LARGE.csv`（51MB） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | IF-001画面で `INB-PLAN-LARGE.csv` の「バリデーション」ボタンをクリック | MSG-E-IF001-004 が表示される: `ファイルサイズが上限（50MB）を超えています。` | el-message（type: error） |

---

### SC-014: 正常系: SYSTEM_ADMINロールでのI/F操作

| 項目 | 内容 |
|------|------|
| シナリオID | SC-014 |
| シナリオ名 | 正常系: SYSTEM_ADMINロールでファイル一覧表示・バリデーション・取り込みが実行できる |
| 前提条件 | SYSTEM_ADMINでログイン済み。入荷予定CSVがpendingに存在 |
| テストデータ | `INB-PLAN-ADMIN.csv`（3行、全行正常） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | IF-001画面を開く | ファイル一覧が正常に表示される | — |
| 2 | `INB-PLAN-ADMIN.csv` のバリデーションを実行 | バリデーション結果が正常に表示される（3件成功） | — |
| 3 | SUCCESS_ONLYモードで取り込みを実行 | 取り込み完了。MSG-S-IF001-001表示 | — |

**API検証（結合テストのみ）:**

| # | 検証内容 |
|:-:|---------|
| 1 | SYSTEM_ADMIN権限のJWTで `GET /api/v1/interface/IFX-001/files` が `200 OK` で返却されること |
| 2 | SYSTEM_ADMIN権限のJWTで `POST /api/v1/interface/IFX-001/validate` が `200 OK` で返却されること |
| 3 | SYSTEM_ADMIN権限のJWTで `POST /api/v1/interface/IFX-001/import` が `200 OK` で取り込みが成功すること |

---

## Playwrightコード例

```typescript
test.describe('TST-IF: 外部連携I/F管理', () => {

  test('SC-001: ファイル一覧表示とタブ切替', async ({ page }) => {
    await loginAs(page, 'WAREHOUSE_MANAGER');

    // Step 1: IF-001画面を開く（初期タブ: 入荷予定）
    await page.goto('/interface');
    await expect(page.locator('[data-testid="tab-IFX-001"]')).toHaveAttribute('aria-selected', 'true');
    await expect(page.locator('[data-testid="file-count"]')).toContainText('全 2ファイル');

    // Step 2: ファイル一覧確認
    await expect(page.locator('table tbody tr')).toHaveCount(2);
    await expect(page.locator('table')).toContainText('INB-PLAN-001.csv');
    await expect(page.locator('table')).toContainText('INB-PLAN-002.csv');

    // Step 3: 受注タブに切替
    await page.click('[data-testid="tab-IFX-002"]');
    await expect(page.locator('[data-testid="file-count"]')).toContainText('全 1ファイル');
    await expect(page.locator('table')).toContainText('ORD-001.csv');

    // Step 4: 入荷予定タブに戻る
    await page.click('[data-testid="tab-IFX-001"]');
    await expect(page.locator('[data-testid="file-count"]')).toContainText('全 2ファイル');
  });

  test('SC-002: バリデーション実行（全件成功）', async ({ page }) => {
    await loginAs(page, 'WAREHOUSE_MANAGER');
    await page.goto('/interface');

    // Step 1: バリデーション実行
    await page.click('table tbody tr:first-child [data-testid="validate-btn"]');
    await expect(page.locator('.el-dialog')).toBeVisible();

    // Step 2: サマリー確認
    await expect(page.locator('[data-testid="total-count"]')).toContainText('10');
    await expect(page.locator('[data-testid="success-count"]')).toContainText('10');

    // Step 3: エラーテーブル非表示
    await expect(page.locator('[data-testid="error-table"]')).not.toBeVisible();

    // Step 5: 閉じる
    await page.click('[data-testid="close-btn"]');
    await expect(page.locator('.el-dialog')).not.toBeVisible();
  });

  test('SC-003: バリデーション実行（エラー含む）', async ({ page }) => {
    await loginAs(page, 'WAREHOUSE_MANAGER');
    await page.goto('/interface');

    // INB-PLAN-002.csvのバリデーション
    await page.click('table tbody tr:nth-child(2) [data-testid="validate-btn"]');
    await expect(page.locator('.el-dialog')).toBeVisible();

    // サマリー確認
    await expect(page.locator('[data-testid="total-count"]')).toContainText('20');
    await expect(page.locator('[data-testid="success-count"]')).toContainText('17');
    await expect(page.locator('[data-testid="error-count"]')).toContainText('3');

    // エラー詳細テーブル確認
    await expect(page.locator('[data-testid="error-table"] tbody tr')).toHaveCount(3);
    await expect(page.locator('[data-testid="error-table"]')).toContainText('WMS-E-IFX-301');
    await expect(page.locator('[data-testid="error-table"]')).toContainText('WMS-E-IFX-105');
    await expect(page.locator('[data-testid="error-table"]')).toContainText('WMS-E-IFX-402');
  });

  test('SC-004: SUCCESS_ONLYモード取り込み', async ({ page }) => {
    await loginAs(page, 'WAREHOUSE_MANAGER');
    await page.goto('/interface');

    // バリデーション実行
    await page.click('table tbody tr:nth-child(2) [data-testid="validate-btn"]');
    await expect(page.locator('.el-dialog')).toBeVisible();

    // 成功行のみ取り込む
    await page.click('[data-testid="import-success-btn"]');
    await expect(page.locator('.el-message-box')).toBeVisible();
    await page.click('.el-message-box__btns button:has-text("実行")');

    // 完了メッセージ確認
    await expect(page.locator('.el-message--success')).toContainText('取り込みが完了しました');

    // ファイル一覧からCSVが消えていることを確認
    await expect(page.locator('table')).not.toContainText('INB-PLAN-002.csv');
  });

  test('SC-005: DISCARDモード取り込み', async ({ page }) => {
    await loginAs(page, 'WAREHOUSE_MANAGER');
    await page.goto('/interface');

    // 取り込みボタンクリック
    await page.click('table tbody tr:first-child [data-testid="import-btn"]');

    // 全件破棄を選択
    await page.click('[data-testid="mode-discard"]');
    await page.click('.el-message-box__btns button:has-text("実行")');

    // 完了メッセージ確認
    await expect(page.locator('.el-message--success')).toContainText('破棄しました');
  });

  test('SC-009: 取り込み履歴照会', async ({ page }) => {
    await loginAs(page, 'WAREHOUSE_MANAGER');

    // IF-003画面を開く
    await page.goto('/interface/history');
    await expect(page.locator('[data-testid="result-count"]')).toContainText('全 3件');

    // I/F種別フィルタ
    await page.selectOption('[data-testid="filter-iftype"]', 'INBOUND_PLAN');
    await page.click('[data-testid="search-btn"]');
    // 入荷予定の履歴のみ表示

    // クリア
    await page.click('[data-testid="clear-btn"]');
    await expect(page.locator('[data-testid="result-count"]')).toContainText('全 3件');
  });

  test('SC-010: バリデーションエラー各種', async ({ page }) => {
    await loginAs(page, 'WAREHOUSE_MANAGER');
    await page.goto('/interface');

    // エラーCSVのバリデーション
    // INB-PLAN-ERR.csv をpendingに配置済みと仮定
    await page.click('table tbody tr:has-text("INB-PLAN-ERR") [data-testid="validate-btn"]');
    await expect(page.locator('.el-dialog')).toBeVisible();

    // エラーコードの存在確認
    const errorTable = page.locator('[data-testid="error-table"]');
    await expect(errorTable).toContainText('WMS-E-IFX-301'); // マスタ不存在
    await expect(errorTable).toContainText('WMS-E-IFX-402'); // ロット番号必須
    await expect(errorTable).toContainText('WMS-E-IFX-501'); // 重複商品
  });
});
```
