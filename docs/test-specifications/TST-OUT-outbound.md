# テスト仕様書 — 出荷管理（結合テスト・E2Eテスト）

---

## ヘッダー情報

| 項目 | 記載内容 |
|------|---------|
| テスト仕様書ID | TST-OUT-001 |
| テスト対象機能 | 出荷管理（受注登録・一覧検索・キャンセル・ピッキング・出荷検品・出荷完了） |
| 対象設計書 | SCR-10（OUT-001〜OUT-022）、API-08（API-OUT-001〜API-OUT-022）、API-12（API-ALL-002） |
| 作成者 | Claude Code |
| 作成日 | 2026-03-20 |
| レビュー者 | |
| レビュー日 | |

---

## テストシナリオ一覧

| シナリオID | シナリオ名 | 優先度 | 前提条件 | 結合 | E2E |
|-----------|----------|:------:|---------|:----:|:---:|
| SC-OUT-001 | 正常系: 受注登録（通常出荷・明細複数行） | 高 | ログイン済み（WAREHOUSE_MANAGER）、マスタデータ登録済み | ○ | ○ |
| SC-OUT-002 | 正常系: 受注登録（倉庫振替出荷） | 中 | 同上 + 振替先倉庫マスタ登録済み | ○ | ○ |
| SC-OUT-003 | 異常系: 受注登録 — 必須項目未入力 | 高 | ログイン済み（WAREHOUSE_MANAGER） | ○ | ○ |
| SC-OUT-004 | 異常系: 受注登録 — 出荷禁止商品 | 中 | 同上 + shipment_stop_flag=true の商品存在 | ○ | — |
| SC-OUT-005 | 正常系: 受注一覧表示・検索 | 高 | テスト用受注データ複数件登録済み | ○ | ○ |
| SC-OUT-006 | 正常系: 受注一覧ステータスフィルタ | 高 | 各ステータスの受注データ登録済み | ○ | ○ |
| SC-OUT-007 | 正常系: 受注キャンセル（ORDERED状態） | 高 | ORDERED状態の受注存在 | ○ | ○ |
| SC-OUT-008 | 正常系: 受注キャンセル（PARTIAL_ALLOCATED状態） | 高 | PARTIAL_ALLOCATED状態の受注存在 | ○ | ○ |
| SC-OUT-009 | 正常系: 受注キャンセル（ALLOCATED状態） | 高 | ALLOCATED状態の受注存在 | ○ | ○ |
| SC-OUT-010 | 異常系: 受注キャンセル（PICKING_COMPLETED以降） | 中 | PICKING_COMPLETED状態の受注存在 | ○ | — |
| SC-OUT-011 | 正常系: ピッキング指示作成 | 高 | ALLOCATED状態の受注存在、ばらし指示なし | ○ | ○ |
| SC-OUT-012 | 異常系: ピッキング指示作成 — ばらし未完了ブロック | 高 | ALLOCATED状態の受注 + status=INSTRUCTED のばらし指示存在 | ○ | ○ |
| SC-OUT-013 | 正常系: ピッキング完了入力 | 高 | ピッキング指示作成済み（CREATED状態） | ○ | ○ |
| SC-OUT-014 | 異常系: ピッキング完了入力 — qtyPicked 超過 | 中 | ピッキング指示作成済み | ○ | — |
| SC-OUT-015 | 正常系: 出荷検品（正常系・差異なし） | 高 | PICKING_COMPLETED状態の受注存在 | ○ | ○ |
| SC-OUT-016 | 正常系: 出荷検品（差異あり） | 高 | 同上 | ○ | ○ |
| SC-OUT-017 | 正常系: 出荷検品（再入力・部分保存） | 中 | INSPECTING状態の受注存在 | ○ | ○ |
| SC-OUT-018 | 正常系: 出荷完了（在庫減算・allocated_qty減算・移動履歴記録） | 高 | INSPECTING状態の受注、検品数入力済み | ○ | ○ |
| SC-OUT-019 | 異常系: 出荷完了 — 在庫不足 | 中 | INSPECTING状態 + 実在庫不足状態 | ○ | — |
| SC-OUT-020 | E2E: 受注→引当→ピッキング→検品→出荷完了の一連フロー | 高 | マスタデータ・在庫データ投入済み | ○ | ○ |

---

## テストシナリオ詳細

---

### SC-OUT-001: 正常系: 受注登録（通常出荷・明細複数行）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-OUT-001 |
| シナリオ名 | 正常系: 通常出荷の受注登録が明細複数行で成功する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。商品マスタにPRD-001, PRD-002, PRD-003登録済み。取引先マスタに出荷先（partner_type=CUSTOMER）のCUST-001登録済み。 |
| テストデータ | 商品コード PRD-001（ケース対応）、PRD-002（ボール対応）、PRD-003（バラ対応）。取引先コード CUST-001 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | OUT-001（受注一覧）画面を開く | 受注一覧画面が表示される | URL `/outbound/slips` に遷移 |
| 2 | [＋ 受注登録] ボタンをクリック | OUT-002（受注登録）画面に遷移 | URL `/outbound/slips/new` |
| 3 | 種別で「通常出荷」を選択（デフォルト） | 出荷先セレクトが表示される | — |
| 4 | 出荷予定日に現在営業日以降の日付を入力 | 日付ピッカーで選択可能 | — |
| 5 | 出荷先でCUST-001を選択 | 出荷先名が表示される | — |
| 6 | 明細1行目: 商品コード「PRD-001」入力 | 商品名が自動補完表示される | EVT-OUT-002-002 |
| 7 | 明細1行目: 荷姿「ケース」、数量「10」を入力 | — | — |
| 8 | [＋ 行追加] ボタンをクリック | 明細2行目が追加される | — |
| 9 | 明細2行目: 商品コード「PRD-002」、荷姿「ボール」、数量「20」を入力 | 商品名が自動補完表示される | — |
| 10 | [＋ 行追加] ボタンをクリック | 明細3行目が追加される | — |
| 11 | 明細3行目: 商品コード「PRD-003」、荷姿「バラ」、数量「50」を入力 | — | — |
| 12 | [登録] ボタンをクリック | 確認ダイアログが表示される | — |
| 13 | 確認ダイアログで [OK] をクリック | 成功メッセージ MSG-S-OUT002-001 が表示される | MSG-S-OUT002-001「受注を登録しました（伝票番号：{slipNo}）」 |
| 14 | OUT-003（受注詳細）画面に遷移 | 登録した伝票の詳細が表示される | 伝票番号がOUT-YYYYMMDD-NNNN形式、ステータス「受注」 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | outbound_slips | レコードが1件追加。status='ORDERED'、slip_type='NORMAL'、partner_id が CUST-001 に対応 |
| 2 | outbound_slip_lines | 明細が3件追加。line_no=1: product=PRD-001, unit_type='CASE', ordered_qty=10。line_no=2: product=PRD-002, unit_type='BALL', ordered_qty=20。line_no=3: product=PRD-003, unit_type='PIECE', ordered_qty=50 |
| 3 | outbound_slips | slip_number が OUT-YYYYMMDD-NNNN 形式で自動採番されていること |
| 4 | outbound_slips | warehouse_code, warehouse_name, partner_code, partner_name がマスタ値からコピーされていること |

---

### SC-OUT-002: 正常系: 受注登録（倉庫振替出荷）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-OUT-002 |
| シナリオ名 | 正常系: 倉庫振替出荷の受注登録が成功し、入荷伝票も同時生成される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。現在選択中の倉庫がWH-001。振替先倉庫WH-002が登録済み。商品PRD-001登録済み。 |
| テストデータ | 倉庫コード WH-001（振替元）、WH-002（振替先）、商品コード PRD-001 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | OUT-002（受注登録）画面を開く | — | URL `/outbound/slips/new` |
| 2 | 種別で「倉庫振替出荷」を選択 | 出荷先セレクトが非表示、振替先倉庫セレクトが表示される | EVT-OUT-002-001 |
| 3 | 出荷予定日に現在営業日以降の日付を入力 | — | — |
| 4 | 振替先倉庫で WH-002 を選択 | WH-001（現在倉庫）は選択肢に含まれないこと | — |
| 5 | 明細1行目: 商品コード「PRD-001」、荷姿「ケース」、数量「5」を入力 | — | — |
| 6 | [登録] ボタンをクリック → 確認ダイアログ → [OK] | 成功メッセージ表示 | MSG-S-OUT002-001 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | outbound_slips | レコード追加。slip_type='WAREHOUSE_TRANSFER'、status='ORDERED' |
| 2 | inbound_slips | 振替先倉庫（WH-002）に入荷伝票が1件生成。status='PLANNED'、slip_type='WAREHOUSE_TRANSFER' |
| 3 | outbound_slips, inbound_slips | 両伝票がtransfer_slip_number で照会可能であること |

---

### SC-OUT-003: 異常系: 受注登録 — 必須項目未入力

| 項目 | 内容 |
|------|------|
| シナリオID | SC-OUT-003 |
| シナリオ名 | 異常系: 必須項目未入力時にバリデーションエラーが表示される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み |
| テストデータ | なし |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | OUT-002（受注登録）画面を開く | — | — |
| 2 | 出荷予定日・出荷先を未入力のまま [登録] をクリック | バリデーションエラー表示 | MSG-E-OUT002-003「{項目名}は必須です」 |
| 3 | 出荷予定日・出荷先を入力し、明細行を0件にして [登録] をクリック | エラー表示 | MSG-E-OUT002-002「明細を1件以上入力してください」 |
| 4 | 明細1行目に存在しない商品コード「INVALID-999」を入力 | エラー表示 | MSG-E-OUT002-001「商品コード「INVALID-999」は登録されていません」 |
| 5 | 明細1行目の数量に「0」を入力し [登録] をクリック | バリデーションエラー（1以上の整数） | — |
| 6 | 出荷予定日に過去日を入力し [登録] をクリック | バリデーションエラー（現在営業日以降が必要） | API 400 VALIDATION_ERROR |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | outbound_slips | レコードが追加されていないこと |

---

### SC-OUT-004: 異常系: 受注登録 — 出荷禁止商品

| 項目 | 内容 |
|------|------|
| シナリオID | SC-OUT-004 |
| シナリオ名 | 異常系: 出荷禁止フラグが立っている商品を明細に含めるとエラー |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。商品PRD-STOP（shipment_stop_flag=true）がマスタに存在 |
| テストデータ | 商品コード PRD-STOP（出荷禁止） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | OUT-002で有効な基本情報を入力 | — | — |
| 2 | 明細1行目に商品コード「PRD-STOP」、荷姿「ケース」、数量「1」を入力 | — | — |
| 3 | [登録] ボタンをクリック | エラーレスポンス | API 422 OUTBOUND_PRODUCT_SHIPMENT_STOPPED |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | outbound_slips | レコードが追加されていないこと |

---

### SC-OUT-005: 正常系: 受注一覧表示・検索

| 項目 | 内容 |
|------|------|
| シナリオID | SC-OUT-005 |
| シナリオ名 | 正常系: 受注一覧の表示と条件検索が正しく動作する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。受注データ25件以上登録済み（ページング確認用）。出荷予定日・出荷先が異なるデータを含む。 |
| テストデータ | 出荷先 CUST-001 の受注5件、CUST-002 の受注5件、各種出荷予定日のデータ |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | OUT-001（受注一覧）画面を開く | 一覧が表示される。デフォルト検索条件（出荷予定日From=営業日-7日、To=営業日+30日）で取得 | URL `/outbound/slips`、API `GET /api/v1/outbound/slips` |
| 2 | ページングで2ページ目に移動 | 2ページ目のデータが表示される（20件/ページ） | page=1 パラメータ |
| 3 | 伝票番号に「OUT-」と入力して [検索] をクリック | 前方一致で伝票番号が絞り込まれる | — |
| 4 | 出荷予定日FromとToを同一日に設定して [検索] | 該当日の受注のみ表示 | — |
| 5 | 出荷先で「CUST-001」を選択して [検索] | CUST-001向けの受注のみ表示 | — |
| 6 | [クリア] ボタンをクリック | 検索条件が初期値にリセットされる | EVT-OUT-001-002 |
| 7 | 伝票番号リンクをクリック | OUT-003（受注詳細）に遷移 | EVT-OUT-001-004 |

---

### SC-OUT-006: 正常系: 受注一覧ステータスフィルタ

| 項目 | 内容 |
|------|------|
| シナリオID | SC-OUT-006 |
| シナリオ名 | 正常系: ステータスでの絞り込みが正しく動作する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。ORDERED, PARTIAL_ALLOCATED, ALLOCATED, PICKING_COMPLETED, INSPECTING, SHIPPED, CANCELLED 各ステータスのデータが存在 |
| テストデータ | 各ステータスごとに最低1件の受注データ |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | ステータスで「受注」を選択して [検索] | ORDERED の受注のみ表示 | API パラメータ status=ORDERED |
| 2 | ステータスで「引当完了」を選択して [検索] | ALLOCATED の受注のみ表示 | status=ALLOCATED |
| 3 | ステータスで「出荷完了」を選択して [検索] | SHIPPED の受注のみ表示 | status=SHIPPED |
| 4 | ステータスで「キャンセル」を選択して [検索] | CANCELLED の受注のみ表示 | status=CANCELLED |
| 5 | ステータスを「すべて」に戻して [検索] | 全ステータスの受注が表示される | status パラメータなし |

---

### SC-OUT-007: 正常系: 受注キャンセル（ORDERED状態）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-OUT-007 |
| シナリオ名 | 正常系: ORDERED状態の受注をキャンセルできる（引当情報なし） |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。ORDERED状態の受注（OUT-TEST-007）が1件存在 |
| テストデータ | ORDERED状態の受注 OUT-TEST-007（明細2件） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | OUT-003（受注詳細）で対象受注を表示 | ステータス「受注」、[キャンセル] ボタンが表示される | — |
| 2 | [キャンセル] ボタンをクリック | 確認ダイアログ表示 | MSG-W-OUT003-002「この受注をキャンセルします。引当済み在庫は解放されます。よろしいですか？」 |
| 3 | 確認ダイアログで [OK] をクリック | 成功メッセージ表示、ステータスが「キャンセル」に変更 | MSG-S-OUT003-002、API `POST /api/v1/outbound/slips/{id}/cancel` → 200 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | outbound_slips | status='CANCELLED'、cancelled_at IS NOT NULL、cancelled_by IS NOT NULL |
| 2 | allocation_details | 対象受注に紐づくレコードが存在しないこと（ORDEREDなので元々なし） |

---

### SC-OUT-008: 正常系: 受注キャンセル（PARTIAL_ALLOCATED状態）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-OUT-008 |
| シナリオ名 | 正常系: PARTIAL_ALLOCATED状態の受注キャンセルで引当解放が実行される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。PARTIAL_ALLOCATED状態の受注（OUT-TEST-008）が存在。一部明細が引当済み（allocation_details にレコードあり）。引当済み在庫の allocated_qty が加算済み。 |
| テストデータ | PARTIAL_ALLOCATED受注。明細2件中1件のみ引当済み（PRD-001: 引当数10、PRD-002: 未引当）。inventories の allocated_qty=10 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | OUT-003で対象受注を表示 | ステータス「一部引当」、引当済み明細の引当数が表示される | — |
| 2 | [キャンセル] ボタンをクリック → 確認ダイアログ → [OK] | 成功メッセージ表示 | MSG-S-OUT003-002 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | outbound_slips | status='CANCELLED'、cancelled_at, cancelled_by が設定 |
| 2 | outbound_slip_lines | 全明細の line_status='ORDERED' に戻されていること |
| 3 | inventories | 引当解放: PRD-001 の allocated_qty が 10 減算されていること（キャンセル前 allocated_qty - 10） |
| 4 | allocation_details | 対象受注に紐づくレコードが全件 DELETE されていること |
| 5 | unpack_instructions | 対象受注に紐づくばらし指示が全件 DELETE されていること |

---

### SC-OUT-009: 正常系: 受注キャンセル（ALLOCATED状態）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-OUT-009 |
| シナリオ名 | 正常系: ALLOCATED状態の受注キャンセルで全引当が解放される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。ALLOCATED状態の受注（OUT-TEST-009）が存在。全明細引当済み。allocation_details, picking_instruction_lines にレコードあり。 |
| テストデータ | ALLOCATED受注。明細2件（PRD-001: 引当数10, PRD-002: 引当数20）。inventories の allocated_qty にそれぞれ 10, 20 が加算済み。picking_instruction_lines にピッキング明細が存在（ピッキング指示作成済みの場合） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | OUT-003で対象受注を表示 | ステータス「引当完了」、全明細の引当数が表示される | — |
| 2 | [キャンセル] ボタンをクリック → 確認ダイアログ → [OK] | 成功メッセージ表示 | MSG-S-OUT003-002 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | outbound_slips | status='CANCELLED' |
| 2 | outbound_slip_lines | 全明細の line_status='ORDERED' に戻されていること |
| 3 | inventories | PRD-001 の allocated_qty が 10 減算。PRD-002 の allocated_qty が 20 減算 |
| 4 | allocation_details | 対象受注に紐づくレコードが全件 DELETE |
| 5 | unpack_instructions | 対象受注に紐づくレコードが全件 DELETE |
| 6 | picking_instruction_lines | 対象受注の outbound_slip_line_id に紐づくレコードが全件 DELETE |

---

### SC-OUT-010: 異常系: 受注キャンセル（PICKING_COMPLETED以降）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-OUT-010 |
| シナリオ名 | 異常系: PICKING_COMPLETED以降のステータスではキャンセルが拒否される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。PICKING_COMPLETED状態の受注が存在 |
| テストデータ | PICKING_COMPLETED状態の受注 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | API `POST /api/v1/outbound/slips/{id}/cancel` を直接呼び出し | 409 OUTBOUND_INVALID_STATUS エラー | HTTP 409 |
| 2 | INSPECTING状態の受注に対して同様にキャンセルAPI呼び出し | 409 OUTBOUND_INVALID_STATUS エラー | HTTP 409 |
| 3 | SHIPPED状態の受注に対して同様にキャンセルAPI呼び出し | 409 OUTBOUND_INVALID_STATUS エラー | HTTP 409 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | outbound_slips | ステータスが変更されていないこと |

---

### SC-OUT-011: 正常系: ピッキング指示作成

| 項目 | 内容 |
|------|------|
| シナリオID | SC-OUT-011 |
| シナリオ名 | 正常系: ALLOCATED状態の複数受注からピッキング指示を作成できる |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。ALLOCATED状態の受注2件（OUT-TEST-011A, OUT-TEST-011B）が存在。未完了のばらし指示なし。エリアマスタにA区画が存在。 |
| テストデータ | ALLOCATED受注2件。エリアID=5（A区画）。各受注に引当済み明細あり |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | OUT-012（ピッキング指示作成）画面を開く | 引当済み受注一覧が表示される | URL `/outbound/picking/new`、EVT-OUT-012-001 |
| 2 | 対象エリアで「A区画」を選択 | — | — |
| 3 | 受注 OUT-TEST-011A, OUT-TEST-011B のチェックボックスを選択 | 「2件選択中」と表示 | EVT-OUT-012-003 |
| 4 | [ピッキング指示作成] ボタンをクリック | 確認ダイアログ表示 | MSG-W-OUT012-001「2件の受注でピッキング指示を作成します。よろしいですか？」 |
| 5 | 確認ダイアログで [OK] をクリック | 成功メッセージ表示、OUT-013に遷移 | MSG-S-OUT012-001 |
| 6 | OUT-013（ピッキング完了入力）画面でピッキングリストが表示される | ロケーション順にソートされた明細が表示される | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | picking_instructions | レコード1件追加。instruction_number が PIC-yyyyMMdd-NNN 形式。status='CREATED' |
| 2 | picking_instruction_lines | 引当明細に基づくピッキング明細が追加。qty_to_pick が allocation_details の allocated_qty と一致。line_status='PENDING' |
| 3 | outbound_slips | 対象受注の status は 'ALLOCATED' のまま変更されていないこと（ピッキング指示作成ではステータス変更しない） |

---

### SC-OUT-012: 異常系: ピッキング指示作成 — ばらし未完了ブロック

| 項目 | 内容 |
|------|------|
| シナリオID | SC-OUT-012 |
| シナリオ名 | 異常系: 未完了のばらし指示がある受注ではピッキング指示を作成できない |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。ALLOCATED状態の受注が存在。当該受注に紐づく unpack_instructions に status='INSTRUCTED' のレコードが存在。 |
| テストデータ | ALLOCATED受注 + 未完了ばらし指示（status=INSTRUCTED） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | OUT-012（ピッキング指示作成）画面で対象受注を選択 | — | — |
| 2 | 対象エリアを選択 | — | — |
| 3 | [ピッキング指示作成] ボタンをクリック → 確認ダイアログ → [OK] | エラーメッセージ表示 | API 409 UNPACK_NOT_COMPLETED |
| 4 | ピッキング指示が作成されていないことを確認 | エラー表示のまま画面遷移しない | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | picking_instructions | 新規レコードが追加されていないこと |
| 2 | unpack_instructions | status='INSTRUCTED' のレコードが変更されていないこと |

---

### SC-OUT-013: 正常系: ピッキング完了入力

| 項目 | 内容 |
|------|------|
| シナリオID | SC-OUT-013 |
| シナリオ名 | 正常系: 全明細のピッキング完了を入力し、受注ステータスがPICKING_COMPLETEDに遷移する |
| 前提条件 | WAREHOUSE_STAFFでログイン済み。CREATED状態のピッキング指示（PIC-TEST-013）が存在。明細3件。 |
| テストデータ | ピッキング指示 PIC-TEST-013。明細3件（lineId: 101, 102, 103。qty_to_pick: 5, 10, 3） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | OUT-013（ピッキング完了入力）画面を表示 | ピッキングリスト（ロケーション順）が表示。進捗「0/3 明細完了」 | EVT-OUT-013-001 |
| 2 | 明細1（lineId=101）の完了チェックをオン | 進捗「1/3 明細完了」に更新 | EVT-OUT-013-002 |
| 3 | 明細2（lineId=102）の完了チェックをオン | 進捗「2/3 明細完了」 | — |
| 4 | 明細3（lineId=103）の完了チェックをオン | 進捗「3/3 明細完了」。[ピッキング完了確定] ボタンが活性化 | — |
| 5 | [ピッキング完了確定] ボタンをクリック | 確認ダイアログ表示 | MSG-W-OUT013-001「ピッキングを完了します。出荷検品へ進みますか？」 |
| 6 | 確認ダイアログで [OK] をクリック | 成功メッセージ表示。OUT-021へ遷移 | MSG-S-OUT013-001、API `PUT /api/v1/outbound/picking/{id}/complete` → 200 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | picking_instructions | status='COMPLETED'、completed_at IS NOT NULL、completed_by IS NOT NULL |
| 2 | picking_instruction_lines | 全明細の qty_picked = qty_to_pick、line_status='COMPLETED' |
| 3 | outbound_slips | 関連する受注の status='PICKING_COMPLETED' |
| 4 | outbound_slip_lines | 関連する明細の line_status='PICKING_COMPLETED' |

---

### SC-OUT-014: 異常系: ピッキング完了入力 — qtyPicked 超過

| 項目 | 内容 |
|------|------|
| シナリオID | SC-OUT-014 |
| シナリオ名 | 異常系: ピッキング数量がqty_to_pickを超過するとバリデーションエラー |
| 前提条件 | WAREHOUSE_STAFFでログイン済み。CREATED状態のピッキング指示が存在 |
| テストデータ | ピッキング指示。明細1件（lineId=201、qty_to_pick=5） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | API `PUT /api/v1/outbound/picking/{id}/complete` を qtyPicked=6（超過）で呼び出し | 400 VALIDATION_ERROR エラー | HTTP 400 |
| 2 | 既にCOMPLETED状態のピッキング指示に対して完了APIを呼び出し | 409 OUTBOUND_INVALID_STATUS エラー | HTTP 409 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | picking_instruction_lines | qty_picked が更新されていないこと |

---

### SC-OUT-015: 正常系: 出荷検品（差異なし）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-OUT-015 |
| シナリオ名 | 正常系: 全明細の検品数がピッキング数と一致し、出荷検品が完了する |
| 前提条件 | WAREHOUSE_STAFFでログイン済み。PICKING_COMPLETED状態の受注（OUT-TEST-015）が存在。明細2件（PRD-001: ordered_qty=10, PRD-002: ordered_qty=20） |
| テストデータ | PICKING_COMPLETED受注。明細2件 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | OUT-021（出荷検品）画面を表示 | 検品入力テーブルが表示。各明細のピッキング数が表示。検品数の初期値=ピッキング数 | EVT-OUT-021-001 |
| 2 | 明細1の検品数が「10」であることを確認 | 差異列: 0 | — |
| 3 | 明細2の検品数が「20」であることを確認 | 差異列: 0 | — |
| 4 | [検品完了・出荷確定へ] ボタンをクリック | 差異なしのため確認ダイアログなし、または通常の確認ダイアログ表示 | — |
| 5 | 確認 → OUT-022（出荷完了）画面に遷移 | 出荷内容確認テーブルに検品数が表示される | API `POST /api/v1/outbound/slips/{id}/inspect` → 200 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | outbound_slips | status='INSPECTING' |
| 2 | outbound_slip_lines | inspected_qty=10（明細1）、inspected_qty=20（明細2）が格納されていること |

---

### SC-OUT-016: 正常系: 出荷検品（差異あり）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-OUT-016 |
| シナリオ名 | 正常系: 検品数がピッキング数と異なる場合も確定でき、差異が記録される |
| 前提条件 | WAREHOUSE_STAFFでログイン済み。PICKING_COMPLETED状態の受注。明細2件（PRD-001: ordered_qty=10, PRD-002: ordered_qty=20） |
| テストデータ | 同SC-OUT-015 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | OUT-021（出荷検品）画面を表示 | 検品入力テーブル表示 | — |
| 2 | 明細1の検品数を「10」のままにする | 差異: 0 | — |
| 3 | 明細2の検品数を「18」に変更（ピッキング数20から差異-2） | 差異列に「-2」表示、警告バッジ表示 | EVT-OUT-021-002 |
| 4 | [検品完了・出荷確定へ] ボタンをクリック | 差異ありの警告ダイアログ表示 | MSG-W-OUT021-001「検品数がピッキング数と異なる明細があります。差異を記録してこのまま進みますか？」 |
| 5 | 警告ダイアログで [OK] をクリック | OUT-022へ遷移 | API `POST /api/v1/outbound/slips/{id}/inspect` → 200 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | outbound_slips | status='INSPECTING' |
| 2 | outbound_slip_lines | inspected_qty=10（明細1）、inspected_qty=18（明細2）。差異がそのまま記録されていること |

---

### SC-OUT-017: 正常系: 出荷検品（再入力・部分保存）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-OUT-017 |
| シナリオ名 | 正常系: INSPECTING状態で途中保存（一時保存）し、再入力で上書き保存できる |
| 前提条件 | WAREHOUSE_STAFFでログイン済み。PICKING_COMPLETED状態の受注。明細3件 |
| テストデータ | PICKING_COMPLETED受注。明細3件（PRD-001: qty=10, PRD-002: qty=20, PRD-003: qty=5） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | OUT-021（出荷検品）画面を表示 | 検品入力テーブル表示 | — |
| 2 | 明細1の検品数を「10」、明細2の検品数を「20」に入力。明細3は未入力 | — | — |
| 3 | [一時保存] ボタンをクリック | 保存成功メッセージ表示 | MSG-S-OUT021-001「検品数を保存しました」、API `POST /api/v1/outbound/slips/{id}/inspect` → 200 |
| 4 | 画面を一旦離れ、再度 OUT-021 を開く | 保存済みの検品数（明細1: 10, 明細2: 20）が表示。明細3は未入力のまま | ステータスが INSPECTING に遷移済み |
| 5 | 明細2の検品数を「18」に変更（上書き） | 差異列が更新される | — |
| 6 | 明細3の検品数を「5」に入力 | — | — |
| 7 | [検品完了・出荷確定へ] ボタンをクリック → 確認 → OK | OUT-022へ遷移 | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | outbound_slip_lines | 一時保存後: 明細1 inspected_qty=10, 明細2 inspected_qty=20, 明細3 inspected_qty=NULL（データモデルでは inspected_qty カラムに検品数を記録） |
| 2 | outbound_slip_lines | 最終保存後: 明細1 inspected_qty=10, 明細2 inspected_qty=18（上書き）, 明細3 inspected_qty=5 |
| 3 | outbound_slips | status='INSPECTING'（一時保存時にINSPECTINGへ遷移済み） |

---

### SC-OUT-018: 正常系: 出荷完了（在庫減算・allocated_qty減算・移動履歴記録）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-OUT-018 |
| シナリオ名 | 正常系: 出荷完了で在庫が正しく減算され、inventory_movementsにOUTBOUNDレコードが記録される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。INSPECTING状態の受注。全明細の検品数（shipped_qty）入力済み。inventories に十分な在庫あり。 |
| テストデータ | INSPECTING受注。明細2件（PRD-001: shipped_qty=10, PRD-002: shipped_qty=18）。inventories: PRD-001 quantity=100, allocated_qty=10。PRD-002 quantity=50, allocated_qty=18。 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | OUT-022（出荷完了）画面を表示 | 出荷内容確認テーブル（読み取り専用）に検品数が表示される | EVT-OUT-022-001 |
| 2 | 配送業者で「ヤマト運輸」を選択 | — | — |
| 3 | 送り状番号に「123456789012」を入力 | — | — |
| 4 | 出荷日に現在営業日を入力（デフォルト） | — | — |
| 5 | [出荷確定] ボタンをクリック | 確認ダイアログ表示 | MSG-W-OUT022-001「出荷を確定します。引当在庫が実在庫から減算されます。よろしいですか？」 |
| 6 | 確認ダイアログで [OK] をクリック | 成功メッセージ表示。OUT-003に遷移。ステータス「出荷完了」 | MSG-S-OUT022-001、API `POST /api/v1/outbound/slips/{id}/ship` → 200 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | outbound_slips | status='SHIPPED'、shipped_at IS NOT NULL、shipped_by IS NOT NULL、carrier='ヤマト運輸'、tracking_number='123456789012' |
| 2 | outbound_slip_lines | 全明細の line_status='SHIPPED'、shipped_qty が確定値 |
| 3 | inventories（PRD-001） | quantity = 100 - 10 = 90、allocated_qty = 10 - 10 = 0 |
| 4 | inventories（PRD-002） | quantity = 50 - 18 = 32、allocated_qty = 18 - 18 = 0 |
| 5 | inventory_movements | PRD-001 に対する movement_type='OUTBOUND' レコード1件。quantity=10（負値）。reference_id, reference_type='PICKING_LINE', location_id, product_id, lot_number が正しいこと |
| 6 | inventory_movements | PRD-002 に対する movement_type='OUTBOUND' レコード1件。quantity=18（負値） |

---

### SC-OUT-019: 異常系: 出荷完了 — 在庫不足

| 項目 | 内容 |
|------|------|
| シナリオID | SC-OUT-019 |
| シナリオ名 | 異常系: 出荷完了時に在庫が不足している場合、ロールバックされてエラーとなる |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。INSPECTING状態の受注。検品数入力済み。inventories の quantity が shipped_qty 未満の状態を作成。 |
| テストデータ | INSPECTING受注。明細1件（PRD-001: shipped_qty=10）。inventories: PRD-001 quantity=5（不足）、allocated_qty=10 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | OUT-022で配送情報を入力して [出荷確定] → [OK] | エラーメッセージ表示 | API 422 INVENTORY_INSUFFICIENT |
| 2 | 画面にエラーメッセージが表示される | 出荷完了されず、ステータスがINSPECTINGのまま | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | outbound_slips | status='INSPECTING' のまま変更されていないこと |
| 2 | inventories | quantity, allocated_qty が変更されていないこと（ロールバック済み） |
| 3 | inventory_movements | 新規レコードが追加されていないこと |

---

### SC-OUT-020: E2E: 受注→引当→ピッキング→検品→出荷完了の一連フロー

| 項目 | 内容 |
|------|------|
| シナリオID | SC-OUT-020 |
| シナリオ名 | E2E: 受注登録から出荷完了までの完全なフローが正しく動作する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。商品PRD-001（ケース）、PRD-002（バラ）がマスタ登録済み。出荷先CUST-001が登録済み。inventories に十分な在庫あり（PRD-001: quantity=100, allocated_qty=0、PRD-002: quantity=200, allocated_qty=0）。エリアマスタにA区画が登録済み。 |
| テストデータ | 商品 PRD-001（ケース、在庫100）、PRD-002（バラ、在庫200）。取引先 CUST-001。エリア A区画 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| **フェーズ1: 受注登録** | | | |
| 1 | OUT-001 → [＋ 受注登録] | OUT-002に遷移 | — |
| 2 | 種別「通常出荷」、出荷予定日=営業日+1、出荷先=CUST-001、明細1: PRD-001/ケース/10、明細2: PRD-002/バラ/30 を入力 | — | — |
| 3 | [登録] → 確認 → [OK] | 成功メッセージ。伝票番号取得（例: OUT-YYYYMMDD-NNNN） | status=ORDERED |
| **フェーズ2: 在庫引当** | | | |
| 4 | OUT-003（受注詳細）で [引当実行] ボタンをクリック | 確認ダイアログ表示 | MSG-W-OUT003-001 |
| 5 | 確認ダイアログで [OK] | 引当完了メッセージ。ステータス「引当完了」に変更 | API `POST /api/v1/allocation/execute` → 200。status=ALLOCATED |
| 6 | 受注詳細で引当数・引当ロケーションが表示される | 明細1: 引当数=10、明細2: 引当数=30 | — |
| **フェーズ3: ピッキング指示作成** | | | |
| 7 | OUT-003で [ピッキング指示作成] ボタンをクリック | OUT-012に遷移。当該受注が選択済み | — |
| 8 | 対象エリア「A区画」を選択、[ピッキング指示作成] → 確認 → [OK] | 成功メッセージ。ピッキング指示番号取得。OUT-013に遷移 | API `POST /api/v1/outbound/picking` → 201 |
| **フェーズ4: ピッキング完了** | | | |
| 9 | OUT-013（ピッキング完了入力）でピッキングリストを確認 | ロケーション順にソートされた明細が表示 | — |
| 10 | 全明細の完了チェックをオン | 進捗が全完了表示 | — |
| 11 | [ピッキング完了確定] → 確認 → [OK] | 成功メッセージ。OUT-021に遷移 | API `PUT /api/v1/outbound/picking/{id}/complete` → 200。受注status=PICKING_COMPLETED |
| **フェーズ5: 出荷検品** | | | |
| 12 | OUT-021（出荷検品）画面で検品入力テーブルを確認 | 各明細のピッキング数・検品数（初期値）が表示 | — |
| 13 | 全明細の検品数がピッキング数と一致することを確認 | 差異列: 全て0 | — |
| 14 | [検品完了・出荷確定へ] → 確認 | OUT-022に遷移 | API `POST /api/v1/outbound/slips/{id}/inspect` → 200。status=INSPECTING |
| **フェーズ6: 出荷完了** | | | |
| 15 | OUT-022（出荷完了）で出荷内容を確認 | 検品数がそのまま出荷数として表示 | — |
| 16 | 配送業者「佐川急便」、送り状番号「987654321098」、出荷日=現在営業日を入力 | — | — |
| 17 | [出荷確定] → 確認 → [OK] | 成功メッセージ。OUT-003に遷移。ステータス「出荷完了」 | MSG-S-OUT022-001。status=SHIPPED |
| **フェーズ7: 事後検証** | | | |
| 18 | OUT-003で受注詳細を確認 | ステータス「出荷完了」、配送業者・送り状番号が表示される | — |
| 19 | OUT-001で受注一覧表示、ステータス「出荷完了」でフィルタ | 該当受注が表示される | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | outbound_slips | status='SHIPPED'、carrier='佐川急便'、tracking_number='987654321098'、shipped_at IS NOT NULL |
| 2 | outbound_slip_lines | 全明細: line_status='SHIPPED'、shipped_qty=ordered_qty（差異なしの場合） |
| 3 | inventories（PRD-001） | quantity = 100 - 10 = 90、allocated_qty = 0（引当→出荷で解放） |
| 4 | inventories（PRD-002） | quantity = 200 - 30 = 170、allocated_qty = 0 |
| 5 | inventory_movements | movement_type='OUTBOUND' のレコードが明細数分（2件以上）追加。各レコードに reference_id, reference_type, location_id, product_id, lot_number, quantity が正しくセットされていること |
| 6 | picking_instructions | status='COMPLETED'、completed_at IS NOT NULL |
| 7 | picking_instruction_lines | 全明細: line_status='COMPLETED'、qty_picked = qty_to_pick |
| 8 | allocation_details | 対象受注の引当レコードが存在すること（出荷完了後も引当履歴として保持） |

---

## Playwrightコード例

### SC-OUT-001: 受注登録（通常出荷・明細複数行）

```typescript
test('SC-OUT-001: 通常出荷の受注登録が明細複数行で成功する', async ({ page }) => {
  // 前提: ログイン済み
  await loginAs(page, 'WAREHOUSE_MANAGER');

  // Step 1: 受注一覧を開く
  await page.goto('/outbound/slips');
  await expect(page.locator('h1')).toContainText('受注一覧');

  // Step 2: 受注登録ボタンクリック
  await page.click('button:has-text("受注登録")');
  await expect(page).toHaveURL(/\/outbound\/slips\/new/);

  // Step 3-5: 基本情報入力
  await page.check('[data-testid="slip-type-normal"]');
  await page.fill('[data-testid="ship-date"]', '2026-03-21');
  await page.selectOption('[data-testid="partner-id"]', { label: 'CUST-001' });

  // Step 6-7: 明細1行目
  await page.fill('[data-testid="product-code-0"]', 'PRD-001');
  await page.locator('[data-testid="product-code-0"]').blur();
  await expect(page.locator('[data-testid="product-name-0"]')).not.toBeEmpty();
  await page.selectOption('[data-testid="unit-type-0"]', 'CASE');
  await page.fill('[data-testid="qty-0"]', '10');

  // Step 8-9: 明細2行目
  await page.click('button:has-text("行追加")');
  await page.fill('[data-testid="product-code-1"]', 'PRD-002');
  await page.locator('[data-testid="product-code-1"]').blur();
  await page.selectOption('[data-testid="unit-type-1"]', 'BALL');
  await page.fill('[data-testid="qty-1"]', '20');

  // Step 10-11: 明細3行目
  await page.click('button:has-text("行追加")');
  await page.fill('[data-testid="product-code-2"]', 'PRD-003');
  await page.locator('[data-testid="product-code-2"]').blur();
  await page.selectOption('[data-testid="unit-type-2"]', 'PIECE');
  await page.fill('[data-testid="qty-2"]', '50');

  // Step 12-13: 登録
  await page.click('button:has-text("登録")');
  await page.click('button:has-text("OK")');
  await expect(page.locator('.el-message--success')).toBeVisible();

  // Step 14: 受注詳細で確認
  await expect(page).toHaveURL(/\/outbound\/slips\/\d+/);
  await expect(page.locator('[data-testid="slip-status"]')).toContainText('受注');
  await expect(page.locator('[data-testid="slip-number"]')).toMatch(/OUT-\d{8}-\d{4}/);
});
```

### SC-OUT-020: E2E 一連フロー

```typescript
test('SC-OUT-020: 受注→引当→ピッキング→検品→出荷完了の一連フロー', async ({ page }) => {
  await loginAs(page, 'WAREHOUSE_MANAGER');

  // フェーズ1: 受注登録
  await page.goto('/outbound/slips/new');
  await page.check('[data-testid="slip-type-normal"]');
  await page.fill('[data-testid="ship-date"]', '2026-03-21');
  await page.selectOption('[data-testid="partner-id"]', { label: 'CUST-001' });
  await page.fill('[data-testid="product-code-0"]', 'PRD-001');
  await page.locator('[data-testid="product-code-0"]').blur();
  await page.selectOption('[data-testid="unit-type-0"]', 'CASE');
  await page.fill('[data-testid="qty-0"]', '10');
  await page.click('button:has-text("行追加")');
  await page.fill('[data-testid="product-code-1"]', 'PRD-002');
  await page.locator('[data-testid="product-code-1"]').blur();
  await page.selectOption('[data-testid="unit-type-1"]', 'PIECE');
  await page.fill('[data-testid="qty-1"]', '30');
  await page.click('button:has-text("登録")');
  await page.click('button:has-text("OK")');
  await expect(page.locator('.el-message--success')).toBeVisible();

  // 伝票番号を取得
  const slipNumber = await page.locator('[data-testid="slip-number"]').textContent();
  await expect(page.locator('[data-testid="slip-status"]')).toContainText('受注');

  // フェーズ2: 在庫引当
  await page.click('button:has-text("引当実行")');
  await page.click('button:has-text("OK")');
  await expect(page.locator('.el-message--success')).toBeVisible();
  await expect(page.locator('[data-testid="slip-status"]')).toContainText('引当完了');

  // フェーズ3: ピッキング指示作成
  await page.click('button:has-text("ピッキング指示作成")');
  await expect(page).toHaveURL(/\/outbound\/picking\/new/);
  await page.selectOption('[data-testid="pick-area"]', { label: 'A区画' });
  await page.click('button:has-text("ピッキング指示作成")');
  await page.click('button:has-text("OK")');
  await expect(page.locator('.el-message--success')).toBeVisible();

  // フェーズ4: ピッキング完了
  await expect(page).toHaveURL(/\/outbound\/picking\/\d+/);
  const checkboxes = page.locator('[data-testid^="pick-complete-"]');
  const count = await checkboxes.count();
  for (let i = 0; i < count; i++) {
    await checkboxes.nth(i).check();
  }
  await page.click('button:has-text("ピッキング完了確定")');
  await page.click('button:has-text("OK")');
  await expect(page.locator('.el-message--success')).toBeVisible();

  // フェーズ5: 出荷検品
  await expect(page).toHaveURL(/\/outbound\/slips\/\d+\/inspect/);
  // 検品数はデフォルトでピッキング数と同値
  await page.click('button:has-text("検品完了")');
  await expect(page).toHaveURL(/\/outbound\/slips\/\d+\/ship/);

  // フェーズ6: 出荷完了
  await page.selectOption('[data-testid="carrier"]', '佐川急便');
  await page.fill('[data-testid="tracking-no"]', '987654321098');
  await page.click('button:has-text("出荷確定")');
  await page.click('button:has-text("OK")');
  await expect(page.locator('.el-message--success')).toBeVisible();

  // フェーズ7: 事後検証
  await expect(page.locator('[data-testid="slip-status"]')).toContainText('出荷完了');
  await expect(page.locator('[data-testid="carrier"]')).toContainText('佐川急便');
  await expect(page.locator('[data-testid="tracking-number"]')).toContainText('987654321098');
});
```

---

## テストデータ準備SQL例

```sql
-- テスト用マスタデータ
-- 商品マスタ（products テーブルの必須カラムを含む）
INSERT INTO products (id, product_code, product_name, case_quantity, ball_quantity, storage_condition, shipment_stop_flag, lot_manage_flag, expiry_manage_flag, is_active, created_by, updated_by)
VALUES
  (101, 'PRD-001', 'テスト商品A', 12, 6, 'AMBIENT', false, false, false, true, 1, 1),
  (102, 'PRD-002', 'テスト商品B', 12, 6, 'AMBIENT', false, false, false, true, 1, 1),
  (103, 'PRD-003', 'テスト商品C', 12, 6, 'AMBIENT', false, false, false, true, 1, 1),
  (104, 'PRD-STOP', '出荷禁止商品', 12, 6, 'AMBIENT', true, false, false, true, 1, 1);

-- 取引先マスタ
INSERT INTO partners (id, partner_code, partner_name, partner_type, is_active, created_by, updated_by)
VALUES
  (10, 'CUST-001', '株式会社テスト商事', 'CUSTOMER', true, 1, 1);

-- 在庫データ（E2Eテスト用）
-- unit_type はテスト対象の荷姿に合わせて設定
INSERT INTO inventories (id, warehouse_id, location_id, product_id, unit_type, lot_number, quantity, allocated_qty)
VALUES
  (1001, 1, 10, 101, 'CASE', 'LOT-001', 100, 0),
  (1002, 1, 10, 102, 'PIECE', 'LOT-002', 200, 0),
  (1003, 1, 10, 103, 'PIECE', 'LOT-003', 500, 0);
```

---

*最終更新: 2026-03-20*
