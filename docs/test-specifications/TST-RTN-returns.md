# テスト仕様書 — 返品管理（TST-RTN）

## ヘッダー情報

| 項目 | 記載内容 |
|------|---------|
| テスト仕様書ID | TST-RTN |
| テスト対象機能 | 返品管理（返品登録・返品一覧・返品レポート） |
| 対象設計書 | SCR-14（RTN-001 返品登録）、API-13（API-RTN-001〜002）、RPT-18（返品レポート） |
| 作成者 | — |
| 作成日 | 2026-03-20 |
| レビュー者 | — |
| レビュー日 | — |

---

## テストシナリオ一覧

| シナリオID | シナリオ名 | 優先度 | 前提条件 | 結合 | E2E |
|-----------|----------|:------:|---------|:----:|:---:|
| SC-001 | 正常系: 入荷返品の登録（在庫影響なし） | 高 | ログイン済み（WAREHOUSE_MANAGER）、商品・取引先マスタ登録済み | ○ | ○ |
| SC-002 | 正常系: 在庫返品の登録（在庫即時減算） | 高 | 同上、在庫データ登録済み | ○ | ○ |
| SC-003 | 正常系: 出荷返品の登録（在庫影響なし、出荷伝票番号リンク） | 高 | 同上、出荷伝票データ登録済み | ○ | ○ |
| SC-004 | 正常系: 返品理由「その他」で備考必須入力 | 中 | ログイン済み、商品マスタ登録済み | ○ | ○ |
| SC-005 | 正常系: 返品理由の定型選択肢すべてで登録成功 | 中 | 同上 | ○ | — |
| SC-006 | 正常系: 返品レポート出力（RPT-18） | 中 | 返品データ複数件登録済み | ○ | ○ |
| SC-007 | 異常系: 在庫返品で有効在庫数超過 | 高 | 在庫データ登録済み | ○ | ○ |
| SC-008 | 異常系: 在庫返品で引当済み在庫が存在する場合の拒否 | 高 | 引当済み在庫が存在 | ○ | ○ |
| SC-009 | 異常系: 在庫返品で棚卸ロック中のロケーション | 高 | 棚卸中のロケーションが存在 | ○ | ○ |
| SC-010 | 異常系: 返品理由「その他」で備考未入力 | 中 | ログイン済み | ○ | ○ |
| SC-011 | E2E: 入荷検品画面からの返品登録遷移 | 高 | 入荷伝票が入荷検品中状態 | — | ○ |
| SC-012 | 正常系: ロット管理品・期限管理品の返品登録 | 中 | ロット管理/期限管理フラグONの商品在庫あり | ○ | ○ |
| SC-013 | 異常系: 必須項目未入力（返品種別・商品・数量・荷姿・理由） | 中 | ログイン済み | ○ | — |
| SC-014 | 異常系: 存在しない商品コードで登録試行 | 中 | ログイン済み | ○ | — |

---

## テストシナリオ詳細

### SC-001: 正常系: 入荷返品の登録（在庫影響なし）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-001 |
| シナリオ名 | 入荷返品を登録し、在庫に影響がないことを確認する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。商品PRD-001（テスト商品A）、取引先SUP-0001が登録済み。在庫にPRD-001がロケーションA-01-01に10ケース存在（変動しないことを確認するため） |
| テストデータ | `R__001_return_inbound.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | RTN-001（返品登録）画面を開く | 返品登録画面が表示される | URL `/returns/new` に遷移 |
| 2 | 返品種別で「入荷返品」を選択 | 関連伝票番号フィールドが表示される。ロケーション選択は非表示 | EVT-RTN001-004 |
| 3 | 商品コード「PRD-001」を入力しEnter | 商品名「テスト商品A」が自動表示される | EVT-RTN001-002, EVT-RTN001-003 |
| 4 | 数量「5」、荷姿「ケース」を入力 | — | — |
| 5 | 返品理由「品質不良」を選択 | — | — |
| 6 | 関連伝票番号に「INB-20260318-0001」を入力 | — | — |
| 7 | [登録] ボタンをクリック | 確認ダイアログ表示: 「テスト商品A を 5ケース 返品登録しますか？（返品種別: 入荷返品）」 | MSG-W-RTN001-001 |
| 8 | 確認ダイアログで [OK] をクリック | MSG-S-RTN001-001「返品伝票 RTN-I-YYYYMMDD-XXXX を登録しました。」が表示される | el-message--success |
| 9 | フォームがクリアされていることを確認 | 全フィールドが初期状態に戻っている | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | return_slips | 1件追加。return_type=INBOUND, status=COMPLETED, return_number がRTN-I-YYYYMMDD-XXXX形式, related_slip_number=INB-20260318-0001, return_reason=QUALITY_DEFECT |
| 2 | inventories | PRD-001のロケーションA-01-01の在庫数量が変更されていないこと（quantity=10のまま） |
| 3 | inventory_movements | 新規レコードが追加されていないこと（入荷返品は在庫変動なし） |

---

### SC-002: 正常系: 在庫返品の登録（在庫即時減算）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-002 |
| シナリオ名 | 在庫返品を登録し、在庫が即時に減算されることを確認する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。商品PRD-002のケース在庫がロケーションB-02-01に20ケース存在（allocated_qty=0）。棚卸ロック中でない |
| テストデータ | `R__002_return_inventory.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | RTN-001画面を開く | 返品登録画面が表示される | — |
| 2 | 返品種別で「在庫返品」を選択 | ロケーション選択フィールドが表示される。関連伝票番号は非表示 | EVT-RTN001-004 |
| 3 | 商品コード「PRD-002」を入力しEnter | 商品名が自動表示される | — |
| 4 | 荷姿「ケース」を選択 | — | — |
| 5 | ロケーション候補が表示されることを確認 | B-02-01が選択肢に含まれる | EVT-RTN001-005 |
| 6 | ロケーション「B-02-01」を選択 | 現在在庫数「20ケース」、有効在庫数「20ケース」が表示される | EVT-RTN001-006 |
| 7 | 数量「3」を入力 | — | — |
| 8 | 返品理由「数量過剰」を選択 | — | — |
| 9 | [登録] ボタンをクリック | 確認ダイアログが表示される | MSG-W-RTN001-001 |
| 10 | 確認ダイアログで [OK] をクリック | 成功メッセージが表示される。伝票番号がRTN-S-YYYYMMDD-XXXX形式 | MSG-S-RTN001-001 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | return_slips | 1件追加。return_type=INVENTORY, status=COMPLETED, location_id が設定されていること, return_reason=EXCESS_QUANTITY |
| 2 | inventories | PRD-002のロケーションB-02-01の`quantity`が20→17に減少していること |
| 3 | inventory_movements | 1件追加。movement_type=RETURN_OUT, quantity=-3（負値）, quantity_after=17, reference_type=RETURN_SLIP |

---

### SC-003: 正常系: 出荷返品の登録（在庫影響なし、出荷伝票番号リンク）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-003 |
| シナリオ名 | 出荷返品を登録し、出荷伝票番号がリンクされ在庫に影響がないことを確認する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。商品PRD-003が登録済み。出荷伝票OUT-20260315-001が出荷完了状態。在庫にPRD-003が5ケース存在（変動しないことを確認するため） |
| テストデータ | `R__003_return_outbound.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | RTN-001画面を開く | 返品登録画面が表示される | — |
| 2 | 返品種別で「出荷返品」を選択 | 関連伝票番号フィールドが表示される。ロケーション選択は非表示 | EVT-RTN001-004 |
| 3 | 商品コード「PRD-003」を入力しEnter | 商品名が自動表示される | — |
| 4 | 数量「2」、荷姿「ケース」を入力 | — | — |
| 5 | 返品理由「誤配送」を選択 | — | — |
| 6 | 関連伝票番号に「OUT-20260315-001」を入力 | — | — |
| 7 | [登録] ボタンをクリック→確認ダイアログで [OK] | 成功メッセージ表示。伝票番号がRTN-O-YYYYMMDD-XXXX形式 | MSG-S-RTN001-001 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | return_slips | 1件追加。return_type=OUTBOUND, related_slip_number=OUT-20260315-001, return_reason=WRONG_DELIVERY |
| 2 | inventories | PRD-003の在庫数量が変更されていないこと |
| 3 | inventory_movements | 新規レコードが追加されていないこと（出荷返品は在庫変動なし） |

---

### SC-004: 正常系: 返品理由「その他」で備考必須入力

| 項目 | 内容 |
|------|------|
| シナリオID | SC-004 |
| シナリオ名 | 返品理由「その他」選択時に備考を入力して正常に登録できる |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。商品PRD-001が登録済み |
| テストデータ | `R__004_return_other_reason.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | RTN-001画面で入荷返品を選択し、商品・数量・荷姿を入力 | — | — |
| 2 | 返品理由「その他」を選択 | 備考フィールドが必須表示（アスタリスク等）になる | — |
| 3 | 返品理由備考に「サンプル品の返却のため」を入力 | — | — |
| 4 | [登録] →確認ダイアログ [OK] | 成功メッセージ表示 | MSG-S-RTN001-001 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | return_slips | return_reason=OTHER, return_reason_note=「サンプル品の返却のため」 |

---

### SC-005: 正常系: 返品理由の定型選択肢すべてで登録成功

| 項目 | 内容 |
|------|------|
| シナリオID | SC-005 |
| シナリオ名 | 全6種の返品理由コードで返品登録が成功する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。商品マスタ登録済み |
| テストデータ | `R__005_return_all_reasons.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | 返品理由=QUALITY_DEFECT で入荷返品を登録 | 201 Created | return_reason=QUALITY_DEFECT |
| 2 | 返品理由=EXCESS_QUANTITY で入荷返品を登録 | 201 Created | return_reason=EXCESS_QUANTITY |
| 3 | 返品理由=WRONG_DELIVERY で入荷返品を登録 | 201 Created | return_reason=WRONG_DELIVERY |
| 4 | 返品理由=EXPIRED で入荷返品を登録 | 201 Created | return_reason=EXPIRED |
| 5 | 返品理由=DAMAGED で入荷返品を登録 | 201 Created | return_reason=DAMAGED |
| 6 | 返品理由=OTHER（備考あり）で入荷返品を登録 | 201 Created | return_reason=OTHER, return_reason_note IS NOT NULL |

---

### SC-006: 正常系: 返品レポート出力（RPT-18）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-006 |
| シナリオ名 | 返品レポート（RPT-18）が正しい内容で出力される |
| 前提条件 | 返品データが複数件登録済み（入荷返品2件、在庫返品1件、出荷返品1件）。期間指定可能な日付範囲 |
| テストデータ | `R__006_return_report.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | API-RPT-018（返品レポート取得）を期間指定で呼び出し（`GET /api/v1/reports/returns?format=json`） | 200 OK。返品データが返品種別ごとにグルーピングされている | — |
| 2 | JSON形式のレスポンスを確認 | 入荷返品2件 + 在庫返品1件 + 出荷返品1件 = 計4件 | JSON配列の要素数=4 |
| 3 | 各レコードの内容を確認 | returnNumber, returnDate, productCode, productName, quantity, unitType, returnReasonLabel, relatedSlipNumber, partnerName が正しく設定されている | — |
| 4 | PDF形式で返品レポートを出力 | PDFが生成される。A4横、返品種別ごとのグルーピング・小計あり | PDFダウンロード確認 |
| 5 | PDF内の合計行を確認 | 全件の数量合計が正しいこと | — |
| 6 | ソート順を確認 | 返品種別昇順→返品日昇順→伝票番号昇順 | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | return_slips | 検索条件に合致するレコード数がレスポンスのtotalElementsと一致すること |

---

### SC-007: 異常系: 在庫返品で有効在庫数超過

| 項目 | 内容 |
|------|------|
| シナリオID | SC-007 |
| シナリオ名 | 返品数量が有効在庫数を超える場合に拒否される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。商品PRD-007のケース在庫がロケーションC-01-01に5ケース存在（allocated_qty=0） |
| テストデータ | `R__007_return_insufficient.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | RTN-001画面で在庫返品を選択 | ロケーション選択が表示される | — |
| 2 | 商品PRD-007、荷姿ケース、ロケーションC-01-01を選択 | 現在在庫数5、有効在庫数5が表示される | — |
| 3 | 数量「10」（有効在庫超過）、返品理由を入力し [登録] →確認ダイアログ [OK] | エラー: MSG-E-RTN001-007「返品数量が有効在庫数を超えています。（有効在庫数: 5ケース）」 | フォーム上部にエラーバナー表示 |

**API直接テスト（結合テストのみ）:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | POST /api/v1/returns（returnType=INVENTORY, quantity=10, 有効在庫5） | 422 RETURN_INSUFFICIENT_QUANTITY | HTTPステータス422 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | return_slips | 新規レコードが追加されていないこと |
| 2 | inventories | 在庫数量が変更されていないこと |

---

### SC-008: 異常系: 在庫返品で引当済み在庫が存在する場合の拒否

| 項目 | 内容 |
|------|------|
| シナリオID | SC-008 |
| シナリオ名 | 引当済み在庫がある場合に在庫返品が拒否される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。商品PRD-008のケース在庫がロケーションD-01-01に10ケース存在（allocated_qty=3）。有効在庫7 |
| テストデータ | `R__008_return_allocated.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | RTN-001画面で在庫返品、商品PRD-008、荷姿ケース、ロケーションD-01-01を選択 | 有効在庫数「7ケース」が表示。引当数がある旨の警告色表示 | RTN-LOC-AVAIL-QTY に警告色 |
| 2 | 数量「2」、返品理由を入力し [登録] →確認ダイアログ [OK] | エラー: MSG-E-RTN001-008「引当済みの在庫があるため返品できません。（引当数: 3ケース）」 | フォーム上部にエラーバナー表示 |

**API直接テスト（結合テストのみ）:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | POST /api/v1/returns（returnType=INVENTORY, allocated_qty > 0の在庫） | 422 RETURN_ALLOCATED_INVENTORY | HTTPステータス422 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | return_slips | 新規レコードが追加されていないこと |
| 2 | inventories | 在庫数量・引当数量が変更されていないこと |

---

### SC-009: 異常系: 在庫返品で棚卸ロック中のロケーション

| 項目 | 内容 |
|------|------|
| シナリオID | SC-009 |
| シナリオ名 | 棚卸ロック中のロケーションからの在庫返品が拒否される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。ロケーションE-01-01が棚卸中（stocktake_headers.status=STARTED）。PRD-009の在庫がE-01-01に存在 |
| テストデータ | `R__009_return_stocktake_locked.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | RTN-001画面で在庫返品、商品PRD-009、荷姿バラ、ロケーションE-01-01を選択 | — | — |
| 2 | 数量「1」、返品理由を入力し [登録] →確認ダイアログ [OK] | エラー: MSG-E-RTN001-009「指定のロケーションは棚卸中のため返品できません。」 | フォーム上部にエラーバナー表示 |

**API直接テスト（結合テストのみ）:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | POST /api/v1/returns（returnType=INVENTORY, 棚卸ロック中ロケーション） | 422 RETURN_STOCKTAKE_LOCKED | HTTPステータス422 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | return_slips | 新規レコードが追加されていないこと |
| 2 | inventories | 在庫数量が変更されていないこと |

---

### SC-010: 異常系: 返品理由「その他」で備考未入力

| 項目 | 内容 |
|------|------|
| シナリオID | SC-010 |
| シナリオ名 | 返品理由「その他」選択時に備考未入力で登録すると拒否される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み |
| テストデータ | — |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | RTN-001画面で入荷返品を選択、商品・数量・荷姿を入力 | — | — |
| 2 | 返品理由「その他」を選択し、備考を空のまま [登録] をクリック | MSG-E-RTN001-011「返品理由が「その他」の場合、備考は必須です。」が表示される | インラインエラー表示 |

**API直接テスト（結合テストのみ）:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | POST /api/v1/returns（returnReason=OTHER, returnReasonNote=null） | 400 VALIDATION_ERROR | HTTPステータス400 |

---

### SC-011: E2E: 入荷検品画面からの返品登録遷移

| 項目 | 内容 |
|------|------|
| シナリオID | SC-011 |
| シナリオ名 | 入荷検品画面から返品登録画面に遷移し、返品種別と関連伝票番号がプリセットされる |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。入荷伝票INB-20260318-0005が入荷検品中状態 |
| テストデータ | `R__011_inbound_to_return.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | 入荷検品画面（INB-003）でINB-20260318-0005を開く | 入荷検品画面が表示される | — |
| 2 | [返品登録] ボタンをクリック | RTN-001画面に遷移する | URL `/returns/new?returnType=INBOUND&relatedSlipNumber=INB-20260318-0005` |
| 3 | 返品登録画面の初期値を確認 | 返品種別が「入荷返品」にプリセット。関連伝票番号に「INB-20260318-0005」がプリセット | EVT-RTN001-001 |
| 4 | 商品・数量・荷姿・返品理由を入力して [登録] →確認ダイアログ [OK] | 返品伝票が正常に登録される | MSG-S-RTN001-001 |
| 5 | 登録された返品伝票を確認 | related_slip_number=INB-20260318-0005 が設定されている | — |

---

### SC-012: 正常系: ロット管理品・期限管理品の返品登録

| 項目 | 内容 |
|------|------|
| シナリオID | SC-012 |
| シナリオ名 | ロット管理・期限管理フラグONの商品で返品登録時にロット番号・賞味期限が入力できる |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。商品PRD-012（lot_manage_flag=true, expiry_manage_flag=true）の在庫がロケーションF-01-01に存在（allocated_qty=0） |
| テストデータ | `R__012_return_lot_expiry.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | RTN-001画面で在庫返品を選択 | — | — |
| 2 | 商品コード「PRD-012」を入力しEnter | 商品名が自動表示。ロット番号フィールドと賞味期限フィールドが表示される | EVT-RTN001-003（lot_manage_flag, expiry_manage_flagの判定） |
| 3 | ロット番号「LOT-2026-001」、賞味期限「2026-12-31」を入力 | — | — |
| 4 | 荷姿・ロケーション・数量・返品理由を入力して [登録] →確認ダイアログ [OK] | 成功メッセージ表示 | MSG-S-RTN001-001 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | return_slips | lot_number=LOT-2026-001, expiry_date=2026-12-31 が記録されていること |
| 2 | inventories | 在庫数量が返品数量分だけ減少していること |

---

### SC-013: 異常系: 必須項目未入力

| 項目 | 内容 |
|------|------|
| シナリオID | SC-013 |
| シナリオ名 | 必須項目が未入力の場合にバリデーションエラーが表示される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み |
| テストデータ | — |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | 返品種別を未選択のまま [登録] | MSG-E-RTN001-001「返品種別は必須です。」 | インラインエラー |
| 2 | 返品種別のみ選択し [登録] | MSG-E-RTN001-002「商品コードは必須です。」 | インラインエラー |
| 3 | 返品種別+商品を入力、数量を空のまま [登録] | MSG-E-RTN001-004「数量は1以上の整数を入力してください。」 | インラインエラー |
| 4 | 返品種別+商品+数量を入力、荷姿を未選択で [登録] | MSG-E-RTN001-005「荷姿は必須です。」 | インラインエラー |
| 5 | 全項目入力、返品理由を未選択で [登録] | MSG-E-RTN001-010「返品理由は必須です。」 | インラインエラー |
| 6 | 在庫返品選択時、ロケーション未選択で [登録] | MSG-E-RTN001-006「在庫返品の場合、ロケーションは必須です。」 | インラインエラー |

---

### SC-014: 異常系: 存在しない商品コードで登録試行

| 項目 | 内容 |
|------|------|
| シナリオID | SC-014 |
| シナリオ名 | 存在しない商品コードで返品登録を試行するとエラーになる |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み |
| テストデータ | — |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | 商品コードに「NONEXIST-999」を入力しEnter | MSG-I-RTN001-001「検索条件に一致する商品がありません。」 | info トースト通知 |

**API直接テスト（結合テストのみ）:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | POST /api/v1/returns（productId=999999, 存在しないID） | 404 PRODUCT_NOT_FOUND | HTTPステータス404 |

---

## Playwrightコード例

### SC-002: 在庫返品の登録（在庫即時減算）

```typescript
test('SC-002: 在庫返品で在庫が即時減算される', async ({ page }) => {
  await loginAs(page, 'WAREHOUSE_MANAGER');

  // Step 1: 返品登録画面を開く
  await page.goto('/returns/new');
  await expect(page.locator('h1')).toContainText('返品登録');

  // Step 2: 在庫返品を選択
  await page.selectOption('[data-testid="return-type"]', 'INVENTORY');
  await expect(page.locator('[data-testid="location-section"]')).toBeVisible();

  // Step 3: 商品選択
  await page.fill('[data-testid="product-code"]', 'PRD-002');
  await page.press('[data-testid="product-code"]', 'Enter');
  await expect(page.locator('[data-testid="product-name"]')).not.toBeEmpty();

  // Step 4-6: 荷姿・ロケーション選択
  await page.selectOption('[data-testid="unit-type"]', 'CASE');
  await page.selectOption('[data-testid="location"]', { label: /B-02-01/ });
  await expect(page.locator('[data-testid="current-qty"]')).toContainText('20');
  await expect(page.locator('[data-testid="available-qty"]')).toContainText('20');

  // Step 7-8: 数量・理由入力
  await page.fill('[data-testid="quantity"]', '3');
  await page.selectOption('[data-testid="return-reason"]', 'EXCESS_QUANTITY');

  // Step 9-10: 登録
  await page.click('button:has-text("登録")');
  await expect(page.locator('.el-message-box')).toContainText('返品登録しますか');
  await page.click('button:has-text("OK")');
  await expect(page.locator('.el-message--success')).toContainText('返品伝票');
});
```

### SC-008: 引当済み在庫の返品拒否

```typescript
test('SC-008: 引当済み在庫の在庫返品が拒否される', async ({ page }) => {
  await loginAs(page, 'WAREHOUSE_MANAGER');

  await page.goto('/returns/new');

  // 在庫返品を選択し、引当済み在庫のロケーションを指定
  await page.selectOption('[data-testid="return-type"]', 'INVENTORY');
  await page.fill('[data-testid="product-code"]', 'PRD-008');
  await page.press('[data-testid="product-code"]', 'Enter');
  await page.selectOption('[data-testid="unit-type"]', 'CASE');
  await page.selectOption('[data-testid="location"]', { label: /D-01-01/ });

  // 有効在庫数に警告色表示を確認
  await expect(page.locator('[data-testid="available-qty"]')).toHaveClass(/warning/);

  // 数量入力して登録試行
  await page.fill('[data-testid="quantity"]', '2');
  await page.selectOption('[data-testid="return-reason"]', 'DAMAGED');
  await page.click('button:has-text("登録")');
  await page.click('button:has-text("OK")');

  // エラーメッセージを確認
  await expect(page.locator('.alert-error')).toContainText('引当済みの在庫があるため返品できません');
});
```

### SC-011: 入荷検品画面からの遷移

```typescript
test('SC-011: 入荷検品画面から返品登録に遷移する', async ({ page }) => {
  await loginAs(page, 'WAREHOUSE_MANAGER');

  // 入荷検品画面を開く
  await page.goto('/inbound/slips/INB-20260318-0005/inspection');

  // 返品登録ボタンをクリック
  await page.click('button:has-text("返品登録")');

  // 返品登録画面に遷移し、初期値がプリセットされていることを確認
  await expect(page).toHaveURL(/\/returns\/new\?returnType=INBOUND/);
  await expect(page.locator('[data-testid="return-type"]')).toHaveValue('INBOUND');
  await expect(page.locator('[data-testid="related-slip-number"]'))
    .toHaveValue('INB-20260318-0005');

  // 残りの項目を入力して登録
  await page.fill('[data-testid="product-code"]', 'PRD-001');
  await page.press('[data-testid="product-code"]', 'Enter');
  await page.fill('[data-testid="quantity"]', '3');
  await page.selectOption('[data-testid="unit-type"]', 'CASE');
  await page.selectOption('[data-testid="return-reason"]', 'QUALITY_DEFECT');

  await page.click('button:has-text("登録")');
  await page.click('button:has-text("OK")');
  await expect(page.locator('.el-message--success')).toContainText('返品伝票');
});
```
