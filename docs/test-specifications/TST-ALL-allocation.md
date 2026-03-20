# テスト仕様書 — 引当管理（TST-ALL）

## ヘッダー情報

| 項目 | 記載内容 |
|------|---------|
| テスト仕様書ID | TST-ALL |
| テスト対象機能 | 在庫引当（引当実行・ばらし指示・引当済み一覧・引当解放） |
| 対象設計書 | SCR-13（ALL-001 在庫引当）、API-12（API-ALL-001〜006） |
| 作成者 | — |
| 作成日 | 2026-03-20 |
| レビュー者 | — |
| レビュー日 | — |

---

## テストシナリオ一覧

| シナリオID | シナリオ名 | 優先度 | 前提条件 | 結合 | E2E |
|-----------|----------|:------:|---------|:----:|:---:|
| SC-001 | 正常系: FIFO順で全量引当が成功する | 高 | ログイン済み（WAREHOUSE_MANAGER）、受注・在庫テストデータ登録済み | ○ | ○ |
| SC-002 | 正常系: 部分引当（在庫不足時に引当可能分のみ引当） | 高 | 同上 | ○ | ○ |
| SC-003 | 正常系: FEFO引当（賞味期限管理品は期限短い順） | 高 | 期限管理フラグONの商品在庫が複数ロケーションに存在 | ○ | ○ |
| SC-004 | 正常系: ばらし指示自動生成（ボール→バラ） | 高 | バラ在庫不足・ボール在庫ありの状態 | ○ | ○ |
| SC-004a | 正常系: ばらし指示自動生成（ケース→バラ） | 高 | バラ・ボール在庫不足・ケース在庫ありの状態 | ○ | ○ |
| SC-005 | 正常系: ばらし完了（在庫変動確認） | 高 | SC-004実行後、ばらし指示がINSTRUCTED状態 | ○ | ○ |
| SC-006 | 正常系: 引当済み一覧表示 | 中 | 引当済み受注が存在する状態 | ○ | ○ |
| SC-007 | 正常系: 引当解放（受注単位） | 高 | 引当済み受注が存在する状態 | ○ | ○ |
| SC-008 | E2E: 引当→ばらし完了→ピッキング指示連携 | 高 | 受注・在庫テストデータ登録済み、ばらし指示あり | — | ○ |
| SC-009 | 異常系: 受注未選択で引当実行 | 中 | ログイン済み | ○ | — |
| SC-010 | 異常系: ピッキング指示済み受注の引当解放拒否 | 高 | ピッキング指示済みの受注が存在 | ○ | — |
| SC-011 | 異常系: 既に完了済みのばらし指示を再完了 | 中 | ばらし完了済みの指示が存在 | ○ | — |
| SC-012 | 正常系: 複数受注の一括引当（全量+部分混在） | 中 | 複数受注、在庫は一部の受注にのみ十分 | ○ | ○ |
| SC-013 | 異常系: 権限不足（WAREHOUSE_STAFF）での引当実行 | 中 | WAREHOUSE_STAFFでログイン | ○ | — |

---

## テストシナリオ詳細

### SC-001: 正常系: FIFO順で全量引当が成功する

| 項目 | 内容 |
|------|------|
| シナリオID | SC-001 |
| シナリオ名 | FIFO順で全量引当が成功する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。受注OUT-TEST-001（バラ10個, 商品PRD-001）がORDERED状態。同一商品のバラ在庫がロケーションA（入庫日3/10, 有効在庫5）とロケーションB（入庫日3/12, 有効在庫8）に存在する |
| テストデータ | `R__001_allocation_fifo.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | ALL-001（在庫引当）画面を開く | 引当実行タブが表示され、受注一覧にOUT-TEST-001が表示される | URL `/allocation` に遷移、一覧テーブルに該当受注が表示 |
| 2 | OUT-TEST-001のチェックボックスをONにする | チェック状態になる | — |
| 3 | [選択した受注を引当実行] ボタンをクリック | 引当結果エリアに「引当成功件数: 1件」が表示される | MSG-S-ALL001-001 |
| 4 | 引当結果エリアを確認 | 引当成功: OUT-TEST-001, ステータス「引当済」。ばらし指示一覧は空。未引当明細一覧は空 | allocatedSlips[0].status = ALLOCATED |
| 5 | 引当済み一覧タブに切り替え | OUT-TEST-001が「引当済」で表示される | ステータスタグが「引当済」 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | outbound_slips | OUT-TEST-001の`status`が`ALLOCATED`に更新されていること |
| 2 | allocation_details | 2件のレコードが作成されていること。ロケーションA: allocated_qty=5、ロケーションB: allocated_qty=5（FIFO順でAが先） |
| 3 | inventories（ロケーションA） | `allocated_qty`が5増加していること |
| 4 | inventories（ロケーションB） | `allocated_qty`が5増加していること |

---

### SC-002: 正常系: 部分引当（在庫不足時に引当可能分のみ引当）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-002 |
| シナリオ名 | 在庫不足時に部分引当が正しく行われる |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。受注OUT-TEST-002（バラ20個, 商品PRD-002）がORDERED状態。PRD-002のバラ有効在庫が合計12個のみ存在 |
| テストデータ | `R__002_allocation_partial.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | ALL-001画面を開く | 引当実行タブが表示される | — |
| 2 | OUT-TEST-002を選択し [選択した受注を引当実行] をクリック | 引当結果エリアに引当成功件数1件が表示される | MSG-S-ALL001-001 |
| 3 | 引当結果エリアを確認 | allocatedSlips: OUT-TEST-002, status=PARTIAL_ALLOCATED。未引当明細にPRD-002 shortageQty=8が表示される | MSG-I-ALL001-001（在庫不足のため一部引当） |
| 4 | 受注一覧を再検索 | OUT-TEST-002がステータス「一部引当」で表示される | ステータスタグ色が「一部引当」 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | outbound_slips | OUT-TEST-002の`status`が`PARTIAL_ALLOCATED` |
| 2 | allocation_details | 引当数量合計が12であること（不足分8は記録されない） |
| 3 | inventories | 該当在庫の`allocated_qty`が12増加していること |

---

### SC-003: 正常系: FEFO引当（賞味期限管理品は期限短い順）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-003 |
| シナリオ名 | 賞味期限管理品がFEFO順で引当される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。受注OUT-TEST-003（バラ8個, 商品PRD-003, expiry_manage_flag=true）がORDERED状態。PRD-003のバラ在庫が3ロケーションに存在: ロケーションA（期限2026-04-30, 有効在庫5）、ロケーションB（期限2026-06-30, 有効在庫10）、ロケーションC（期限2026-03-31, 有効在庫3） |
| テストデータ | `R__003_allocation_fefo.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | ALL-001画面を開く | 受注一覧にOUT-TEST-003が表示される | — |
| 2 | OUT-TEST-003を選択し [選択した受注を引当実行] をクリック | 引当成功1件。ステータス「引当済」 | MSG-S-ALL001-001 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | allocation_details | 2件のレコードが作成。引当順: ロケーションC（期限2026-03-31, qty=3）→ ロケーションA（期限2026-04-30, qty=5）。ロケーションBは引当なし。合計8 |
| 2 | inventories（ロケーションC） | `allocated_qty` += 3 |
| 3 | inventories（ロケーションA） | `allocated_qty` += 5 |
| 4 | inventories（ロケーションB） | `allocated_qty`変更なし |
| 5 | outbound_slips | status = `ALLOCATED` |

---

### SC-004: 正常系: ばらし指示自動生成（ボール→バラ）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-004 |
| シナリオ名 | バラ在庫不足時にボール→バラのばらし指示が自動生成される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。受注OUT-TEST-004（バラ10個, 商品PRD-004, ボール入数=6）がORDERED状態。PRD-004のバラ有効在庫が5、ボール有効在庫が2 |
| テストデータ | `R__004_allocation_unpack.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | ALL-001画面を開く | 受注一覧にOUT-TEST-004が表示される | — |
| 2 | OUT-TEST-004を選択し [選択した受注を引当実行] をクリック | 引当成功1件が表示される | MSG-S-ALL001-001 |
| 3 | 引当結果のばらし指示一覧を確認 | ばらし指示1件: PRD-004, 元荷姿BALL, 先荷姿PIECE, 数量6（ボール1個分）, ステータスINSTRUCTED | ばらし指示テーブルに1行表示 |
| 4 | 引当結果のallocatedSlipsを確認 | バラ5は直接引当済み。残り5はばらし待ち（仮引当） | status = ALLOCATED（全量引当計画済み） |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | unpack_instructions | 1件作成。product_id=PRD-004に対応するID, from_unit_type=BALL, to_unit_type=PIECE, from_qty=1, to_qty=6, status=INSTRUCTED |
| 2 | inventories（バラ） | `allocated_qty` += 5 |
| 3 | inventories（ボール） | `allocated_qty` += 1（仮確保） |
| 4 | allocation_details | バラ在庫からの引当5 + ばらし予定分の引当5 = 合計10が記録 |

---

### SC-004a: 正常系: ばらし指示自動生成（ケース→バラ）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-004a |
| シナリオ名 | バラ・ボール在庫不足時にケース→バラのばらし指示が自動生成される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。受注OUT-TEST-004a（バラ50個, 商品PRD-004a, ケース入数=6, ボール入数=5）がORDERED状態。PRD-004aのバラ有効在庫が10、ボール有効在庫が0、ケース有効在庫が3 |
| テストデータ | `R__004a_allocation_unpack_case.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | ALL-001画面を開く | 受注一覧にOUT-TEST-004aが表示される | — |
| 2 | OUT-TEST-004aを選択し [選択した受注を引当実行] をクリック | 引当成功1件が表示される | MSG-S-ALL001-001 |
| 3 | 引当結果のばらし指示一覧を確認 | ばらし指示1件: PRD-004a, 元荷姿CASE, 先荷姿PIECE, 数量2ケース（2×6×5=60バラ）, ステータスINSTRUCTED | ばらし指示テーブルに1行表示 |
| 4 | 引当結果のallocatedSlipsを確認 | バラ10は直接引当済み。残り40はばらし待ち（仮引当） | status = ALLOCATED（全量引当計画済み） |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | unpack_instructions | 1件作成。product_id=PRD-004aに対応するID, from_unit_type=CASE, to_unit_type=PIECE, from_qty=2, to_qty=60, status=INSTRUCTED |
| 2 | inventories（バラ） | `allocated_qty` += 10 |
| 3 | inventories（ケース） | `allocated_qty` += 2（仮確保） |
| 4 | allocation_details | バラ在庫からの引当10 + ばらし予定分の引当40 = 合計50が記録 |

---

### SC-005: 正常系: ばらし完了（在庫変動確認）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-005 |
| シナリオ名 | ばらし指示を完了し、在庫変動が正しく記録される |
| 前提条件 | SC-004実行後の状態。ばらし指示ID=101がINSTRUCTED状態。ボール在庫のallocated_qty=1、バラ在庫のallocated_qty=5 |
| テストデータ | SC-004の実行結果を使用 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | ALL-001画面の引当結果エリアでばらし指示一覧を確認 | PRD-004のばらし指示がINSTRUCTED状態で表示される | — |
| 2 | ばらし指示の [完了] ボタンをクリック | MSG-S-ALL001-002「ばらし指示を完了しました」が表示される | ばらし指示のステータスがCOMPLETEDに変更 |
| 3 | ばらし指示一覧を確認 | ステータスが「完了」に変更されている | COMPLETEDタグ表示 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | unpack_instructions | status=COMPLETED, completed_at IS NOT NULL, completed_by IS NOT NULL |
| 2 | inventories（ボール） | Step1: allocated_qty -= 1 → 0。Step2: quantity -= 1 |
| 3 | inventories（バラ） | Step3: quantity += 6。Step4: allocated_qty += 5（ばらし分の引当） |
| 4 | inventory_movements | 2件追加。BREAKDOWN_OUT（ボール -1）とBREAKDOWN_IN（バラ +6） |
| 5 | allocation_details | ばらし分の引当明細のinventory_idがバラ在庫IDに付け替えられていること |
| 6 | inventories | 全レコードで `allocated_qty <= quantity` のCHECK制約が満たされていること |

---

### SC-006: 正常系: 引当済み一覧表示

| 項目 | 内容 |
|------|------|
| シナリオID | SC-006 |
| シナリオ名 | 引当済み一覧タブで引当済み受注が正しく表示される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。引当済み（ALLOCATED）受注2件と一部引当（PARTIAL_ALLOCATED）受注1件が存在 |
| テストデータ | `R__006_allocated_orders.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | ALL-001画面を開き、[引当済み一覧] タブをクリック | 引当済み一覧テーブルが表示される | EVT-ALL001-006 発火 |
| 2 | 一覧テーブルの内容を確認 | 3件表示。ALLOCATED受注2件とPARTIAL_ALLOCATED受注1件 | ステータスタグが正しい色で表示 |
| 3 | 各受注の明細数・引当済み明細数を確認 | lineCountとallocatedLineCountが正しい値で表示される | — |
| 4 | ソート順を確認 | 出荷予定日昇順で表示されている | planned_date ASC |

---

### SC-007: 正常系: 引当解放（受注単位）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-007 |
| シナリオ名 | 引当済み受注の引当を解放し、在庫が復元される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。受注OUT-TEST-007がALLOCATED状態。2ロケーションから合計15個が引当済み（ロケーションA: 10, ロケーションB: 5） |
| テストデータ | `R__007_allocation_release.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | ALL-001画面の [引当済み一覧] タブを開く | OUT-TEST-007が「引当済」で表示される | — |
| 2 | OUT-TEST-007の [引当解放] ボタンをクリック | 確認ダイアログが表示される | MSG-W-ALL001-001 |
| 3 | 確認ダイアログで [引当解放する] をクリック | MSG-S-ALL001-003「引当を解放しました」が表示される | el-message--success |
| 4 | 引当済み一覧を確認 | OUT-TEST-007が一覧から消えている | 引当済み一覧再取得 |
| 5 | 引当実行タブに戻る | OUT-TEST-007がステータス「受注」で再表示される | status=ORDERED |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | outbound_slips | OUT-TEST-007の`status`が`ORDERED`に更新 |
| 2 | allocation_details | OUT-TEST-007に紐づくレコードが全削除されていること |
| 3 | inventories（ロケーションA） | `allocated_qty`が10減少していること |
| 4 | inventories（ロケーションB） | `allocated_qty`が5減少していること |
| 5 | unpack_instructions | OUT-TEST-007に紐づくINSTRUCTED状態の指示が削除されていること（存在した場合） |

---

### SC-008: E2E: 引当→ばらし完了→ピッキング指示連携

| 項目 | 内容 |
|------|------|
| シナリオID | SC-008 |
| シナリオ名 | 引当実行からばらし完了を経てピッキング指示が可能になるE2Eフロー |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。受注OUT-TEST-008（バラ15個, 商品PRD-008, ボール入数=6）がORDERED状態。PRD-008のバラ有効在庫3、ボール有効在庫3 |
| テストデータ | `R__008_e2e_allocation_picking.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | ALL-001画面でOUT-TEST-008を選択し引当実行 | 引当成功。ばらし指示が生成される（ボール2→バラ12） | ばらし指示一覧に表示 |
| 2 | ばらし指示が未完了の状態で出荷管理画面からピッキング指示を試みる | ピッキング指示が作成できない（ばらし未完了のため） | エラーメッセージ表示 |
| 3 | ALL-001画面に戻り、ばらし指示の [完了] ボタンをクリック | ばらし完了。在庫変動が記録される | MSG-S-ALL001-002 |
| 4 | 出荷管理画面からピッキング指示を作成 | ピッキング指示が正常に作成される | ピッキング指示一覧に表示 |
| 5 | ピッキング指示の引当ロケーションを確認 | ばらし後のバラ在庫ロケーションが指定されている | allocation_detailsの情報に基づくロケーション |

---

### SC-009: 異常系: 受注未選択で引当実行

| 項目 | 内容 |
|------|------|
| シナリオID | SC-009 |
| シナリオ名 | 受注を選択せずに引当実行ボタンを押した場合のエラー |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。受注一覧に受注が表示されている |
| テストデータ | — |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | ALL-001画面を開く | 引当実行タブが表示される | — |
| 2 | チェックボックスを何も選択せずに [選択した受注を引当実行] をクリック | MSG-E-ALL001-001「引当実行する受注を1件以上選択してください」が表示される | エラーメッセージ表示 |

---

### SC-010: 異常系: ピッキング指示済み受注の引当解放拒否

| 項目 | 内容 |
|------|------|
| シナリオID | SC-010 |
| シナリオ名 | ピッキング指示済み以降のステータスの受注は引当解放できない |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。受注OUT-TEST-010がPICKING_COMPLETED状態 |
| テストデータ | `R__010_picking_completed.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | POST /api/v1/allocation/release で OUT-TEST-010 の引当解放をAPIで直接リクエスト | 409 RELEASE_NOT_ALLOWED が返される | HTTPステータス409 |
| 2 | レスポンスを確認 | MSG-E-ALL001-003「ピッキング指示済み以降の受注は引当解放できません」相当のメッセージ | errorCode = RELEASE_NOT_ALLOWED |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | outbound_slips | ステータスが変更されていないこと |
| 2 | allocation_details | レコードが削除されていないこと |
| 3 | inventories | allocated_qtyが変更されていないこと |

---

### SC-011: 異常系: 既に完了済みのばらし指示を再完了

| 項目 | 内容 |
|------|------|
| シナリオID | SC-011 |
| シナリオ名 | 完了済みのばらし指示に対して完了操作を行うとエラーになる |
| 前提条件 | ばらし指示ID=201がCOMPLETED状態 |
| テストデータ | `R__011_completed_unpack.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | PUT /api/v1/allocation/unpack-instructions/201/complete をAPIで直接リクエスト | 400 ALREADY_COMPLETED が返される | HTTPステータス400 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | unpack_instructions | ステータスがCOMPLETEDのまま変更なし |
| 2 | inventories | 在庫数量に変動がないこと |
| 3 | inventory_movements | 新規レコードが追加されていないこと |

---

### SC-012: 正常系: 複数受注の一括引当（全量+部分混在）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-012 |
| シナリオ名 | 複数受注を一括引当し、全量引当と部分引当が混在する結果を確認する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。受注A（バラ5個, PRD-012A）・受注B（バラ20個, PRD-012B）がORDERED状態。PRD-012Aのバラ有効在庫10、PRD-012Bのバラ有効在庫8 |
| テストデータ | `R__012_bulk_allocation.sql` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | ALL-001画面で受注A・受注Bの両方にチェックを入れ [選択した受注を引当実行] をクリック | 引当結果エリアに結果が表示される | MSG-S-ALL001-001 |
| 2 | 引当結果を確認 | allocatedCount=2。受注A: ALLOCATED（全量引当成功）、受注B: PARTIAL_ALLOCATED（部分引当） | — |
| 3 | 未引当明細を確認 | 受注BのPRD-012B shortageQty=12が表示される | MSG-I-ALL001-001 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | outbound_slips | 受注A: status=ALLOCATED、受注B: status=PARTIAL_ALLOCATED |
| 2 | allocation_details | 受注A: 合計5、受注B: 合計8 |

---

### SC-013: 異常系: 権限不足（WAREHOUSE_STAFF）での引当実行

| 項目 | 内容 |
|------|------|
| シナリオID | SC-013 |
| シナリオ名 | WAREHOUSE_STAFFロールで引当実行APIを呼び出すと403エラーになる |
| 前提条件 | WAREHOUSE_STAFFでログイン済み |
| テストデータ | — |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | POST /api/v1/allocation/execute をAPIで直接リクエスト | 403 FORBIDDEN が返される | HTTPステータス403 |
| 2 | GET /api/v1/allocation/orders をAPIで直接リクエスト | 403 FORBIDDEN が返される | HTTPステータス403 |

---

## Playwrightコード例

### SC-001: FIFO順で全量引当が成功する

```typescript
test('SC-001: FIFO順で全量引当が成功する', async ({ page }) => {
  // 前提: ログイン済み
  await loginAs(page, 'WAREHOUSE_MANAGER');

  // Step 1: 在庫引当画面を開く
  await page.goto('/allocation');
  await expect(page.locator('h1')).toContainText('在庫引当');

  // Step 2: 受注を選択
  const row = page.locator('tr', { hasText: 'OUT-TEST-001' });
  await row.locator('input[type="checkbox"]').check();

  // Step 3: 引当実行
  await page.click('button:has-text("選択した受注を引当実行")');
  await expect(page.locator('.el-message--success')).toContainText('引当を実行しました');

  // Step 4: 引当結果を確認
  await expect(page.locator('[data-testid="allocated-count"]')).toContainText('1');
  await expect(page.locator('[data-testid="unpack-instructions"]')).toBeEmpty();
  await expect(page.locator('[data-testid="unallocated-lines"]')).toBeEmpty();

  // Step 5: 引当済み一覧タブで確認
  await page.click('text=引当済み一覧');
  await expect(page.locator('tr', { hasText: 'OUT-TEST-001' }))
    .toContainText('引当済');
});
```

### SC-004: ばらし指示自動生成

```typescript
test('SC-004: ばらし指示自動生成（ボール→バラ）', async ({ page }) => {
  await loginAs(page, 'WAREHOUSE_MANAGER');

  await page.goto('/allocation');

  // 受注を選択して引当実行
  const row = page.locator('tr', { hasText: 'OUT-TEST-004' });
  await row.locator('input[type="checkbox"]').check();
  await page.click('button:has-text("選択した受注を引当実行")');
  await expect(page.locator('.el-message--success')).toBeVisible();

  // ばらし指示一覧を確認
  const unpackRow = page.locator('[data-testid="unpack-instructions"] tr', { hasText: 'PRD-004' });
  await expect(unpackRow).toBeVisible();
  await expect(unpackRow.locator('td:nth-child(2)')).toContainText('BALL');  // 元荷姿
  await expect(unpackRow.locator('td:nth-child(3)')).toContainText('PIECE'); // 先荷姿
  await expect(unpackRow.locator('td:nth-child(5)')).toContainText('INSTRUCTED');
});
```

### SC-004a: ばらし指示自動生成（ケース→バラ）

```typescript
test('SC-004a: ばらし指示自動生成（ケース→バラ）', async ({ page }) => {
  await loginAs(page, 'WAREHOUSE_MANAGER');

  await page.goto('/allocation');

  // 受注を選択して引当実行
  const row = page.locator('tr', { hasText: 'OUT-TEST-004a' });
  await row.locator('input[type="checkbox"]').check();
  await page.click('button:has-text("選択した受注を引当実行")');
  await expect(page.locator('.el-message--success')).toBeVisible();

  // ばらし指示一覧を確認
  const unpackRow = page.locator('[data-testid="unpack-instructions"] tr', { hasText: 'PRD-004a' });
  await expect(unpackRow).toBeVisible();
  await expect(unpackRow.locator('td:nth-child(2)')).toContainText('CASE');  // 元荷姿
  await expect(unpackRow.locator('td:nth-child(3)')).toContainText('PIECE'); // 先荷姿
  await expect(unpackRow.locator('td:nth-child(5)')).toContainText('INSTRUCTED');
});
```

### SC-005: ばらし完了

```typescript
test('SC-005: ばらし完了で在庫が正しく変動する', async ({ page }) => {
  await loginAs(page, 'WAREHOUSE_MANAGER');

  await page.goto('/allocation');

  // SC-004の結果を前提にばらし完了ボタンをクリック
  const unpackRow = page.locator('[data-testid="unpack-instructions"] tr', { hasText: 'PRD-004' });
  await unpackRow.locator('button:has-text("完了")').click();
  await expect(page.locator('.el-message--success')).toContainText('ばらし指示を完了しました');

  // ステータスがCOMPLETEDに変更されていることを確認
  await expect(unpackRow.locator('td:nth-child(5)')).toContainText('COMPLETED');
});
```

### SC-007: 引当解放

```typescript
test('SC-007: 引当解放で在庫が復元される', async ({ page }) => {
  await loginAs(page, 'WAREHOUSE_MANAGER');

  await page.goto('/allocation');

  // 引当済み一覧タブに切り替え
  await page.click('text=引当済み一覧');

  // 引当解放ボタンをクリック
  const row = page.locator('tr', { hasText: 'OUT-TEST-007' });
  await row.locator('button:has-text("引当解放")').click();

  // 確認ダイアログで「引当解放する」をクリック
  await expect(page.locator('.el-message-box')).toContainText('引当を解放します');
  await page.click('button:has-text("引当解放する")');

  // 成功メッセージを確認
  await expect(page.locator('.el-message--success')).toContainText('引当を解放しました');

  // 引当済み一覧から消えていることを確認
  await expect(page.locator('tr', { hasText: 'OUT-TEST-007' })).not.toBeVisible();

  // 引当実行タブに戻って受注が「受注」ステータスで再表示されることを確認
  await page.click('text=引当実行');
  await expect(page.locator('tr', { hasText: 'OUT-TEST-007' })).toContainText('受注');
});
```
