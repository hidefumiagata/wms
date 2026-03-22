# テスト仕様書 — 入荷管理 結合・E2Eテスト

## ヘッダー情報

| 項目 | 記載内容 |
|------|---------|
| テスト仕様書ID | TST-INB |
| テスト対象機能 | 入荷管理（入荷予定登録・一覧検索・入荷確認・キャンセル・入荷検品・入庫確定・入荷実績照会・検品レポート出力） |
| 対象設計書 | SCR-07（INB-001〜006）、API-06（API-INB-001〜010、API-INB-005含む）、RPT-01 |
| 対象要件定義書 | functional-requirements/02-inbound-management.md |
| 作成者 | — |
| 作成日 | — |
| レビュー者 | — |
| レビュー日 | — |

---

## テストシナリオ一覧

| シナリオID | シナリオ名 | 優先度 | 前提条件 | 結合 | E2E |
|-----------|----------|:------:|---------|:----:|:---:|
| SC-INB-001 | 正常系: 入荷予定登録（単一明細） | 高 | ログイン済み（WAREHOUSE_MANAGER）、マスタデータ登録済み | ○ | ○ |
| SC-INB-002 | 正常系: 入荷予定登録（複数明細行） | 高 | 同上 | ○ | ○ |
| SC-INB-003 | 正常系: 入荷予定登録（ロット管理品） | 高 | 同上。ロット管理商品がマスタに存在 | ○ | ○ |
| SC-INB-004 | 正常系: 入荷予定登録（期限管理品） | 高 | 同上。期限管理商品がマスタに存在 | ○ | ○ |
| SC-INB-005 | 異常系: 入荷予定登録バリデーションエラー | 高 | 同上 | ○ | ○ |
| SC-INB-006 | 正常系: 入荷予定削除（PLANNED状態） | 中 | PLANNED状態の入荷予定が存在 | ○ | — |
| SC-INB-007 | 異常系: 入荷予定削除（PLANNED以外は削除不可） | 中 | CONFIRMED/INSPECTING/STORED/CANCELLED状態の入荷予定が存在 | ○ | — |
| SC-INB-010 | 正常系: 入荷予定一覧表示・検索 | 高 | 入荷予定が複数件登録済み | ○ | ○ |
| SC-INB-011 | 正常系: 入荷予定一覧ステータス絞り込み | 中 | 各ステータスの入荷予定が存在 | ○ | ○ |
| SC-INB-020 | 正常系: 入荷確認（PLANNED → CONFIRMED） | 高 | PLANNED状態の入荷予定が存在 | ○ | ○ |
| SC-INB-021 | 異常系: 入荷確認（PLANNED以外からの遷移不可） | 高 | CONFIRMED/INSPECTING/PARTIAL_STORED/STORED/CANCELLED状態の入荷予定が存在 | ○ | — |
| SC-INB-030 | 正常系: 入荷キャンセル（PLANNEDから） | 高 | PLANNED状態の入荷予定が存在 | ○ | ○ |
| SC-INB-031 | 正常系: 入荷キャンセル（CONFIRMEDから） | 高 | CONFIRMED状態の入荷予定が存在 | ○ | ○ |
| SC-INB-031a | 正常系: 入荷キャンセル（INSPECTINGから） | 高 | INSPECTING状態の入荷予定が存在 | ○ | — |
| SC-INB-032 | 正常系: 入荷キャンセル（PARTIAL_STOREDから・在庫ロールバック） | 高 | PARTIAL_STORED状態で一部明細が入庫済み | ○ | ○ |
| SC-INB-033 | 異常系: 入荷キャンセル（STORED/CANCELLEDからの遷移不可） | 高 | STORED状態の入荷予定が存在 | ○ | — |
| SC-INB-040 | 正常系: 入荷検品（全数一致） | 高 | CONFIRMED状態の入荷予定が存在 | ○ | ○ |
| SC-INB-041 | 正常系: 入荷検品（差異あり） | 高 | CONFIRMED状態の入荷予定が存在 | ○ | ○ |
| SC-INB-042 | 異常系: 入荷検品（期限切れ商品の入荷防止） | 高 | CONFIRMED状態で期限管理品を含む入荷予定が存在 | ○ | ○ |
| SC-INB-043 | 正常系: 入荷検品（検品数の上書き更新） | 中 | INSPECTING状態の入荷予定が存在 | ○ | — |
| SC-INB-044 | 正常系: 入荷検品（PARTIAL_STORED状態で残明細を検品） | 高 | PARTIAL_STORED状態で一部明細が入庫済み | ○ | ○ |
| SC-INB-045 | 異常系: 入荷検品（PARTIAL_STORED状態で入庫済み明細を検品しようとした場合） | 高 | PARTIAL_STORED状態で入庫済み明細あり | ○ | — |
| SC-INB-050 | 正常系: 入庫確定（全明細一括・在庫加算・movements記録） | 高 | INSPECTING状態で全明細検品済み | ○ | ○ |
| SC-INB-051 | 正常系: 入庫確定（個別明細・ロケーション単一商品制約確認） | 高 | INSPECTING状態で検品済み明細あり | ○ | ○ |
| SC-INB-052 | 異常系: 入庫確定（別商品が既存するロケーションへの入庫拒否） | 高 | ロケーションに別商品の在庫あり | ○ | — |
| SC-INB-060 | 正常系: 一部入庫（PARTIAL_STORED）→ 残明細入庫 → 全明細完了（STORED） | 高 | INSPECTING状態で複数明細あり | ○ | ○ |
| SC-INB-061 | 正常系: 一部入庫状態からのキャンセル（在庫ロールバック検証） | 高 | PARTIAL_STORED状態 | ○ | ○ |
| SC-INB-070 | 正常系: 入荷実績照会 | 中 | STORED状態の入荷予定が存在 | ○ | ○ |
| SC-INB-071 | 正常系: 入荷実績照会（絞り込み検索） | 中 | 複数のSTORED入荷予定が存在 | ○ | ○ |
| SC-INB-080 | 正常系: 入荷検品レポート出力（RPT-01） | 中 | 検品済み入荷予定が存在 | ○ | ○ |
| SC-INB-090 | E2E: 入荷予定登録 → 確認 → 検品 → 入庫確定（フルフロー） | 高 | マスタデータ登録済み | — | ○ |

---

## テストシナリオ詳細

---

### SC-INB-001: 正常系: 入荷予定登録（単一明細）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-001 |
| シナリオ名 | 正常系: 入荷予定の新規登録が成功する（単一明細） |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。商品マスタにPRD-0001（通常商品・ロット管理OFF・期限管理OFF）、仕入先マスタにSUP-0001が登録済み |
| テストデータ | 商品コード: PRD-0001、仕入先コード: SUP-0001 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-001（入荷予定一覧）画面を開く | 一覧画面が表示される | URL `/inbound/slips` に遷移 |
| 2 | [＋ 新規登録] ボタンをクリック | INB-002（入荷予定登録）画面に遷移 | URL `/inbound/slips/new` |
| 3 | 入荷予定日に現在営業日+1日を入力 | 日付が入力される | — |
| 4 | 仕入先で「SUP-0001」を選択 | 仕入先名が表示される | セレクトボックスに選択値反映 |
| 5 | 明細1行目に商品コード「PRD-0001」を入力 | 商品名が自動補完される | 商品名フィールドに値表示 |
| 6 | 荷姿「ケース」を選択、予定数量「100」を入力 | 入力値が反映される | — |
| 7 | [登録する] ボタンをクリック | 確認ダイアログが表示される | — |
| 8 | 確認ダイアログで [OK] をクリック | 成功メッセージ「入荷予定を登録しました（伝票番号: INB-YYYYMMDD-NNNN）」が表示され、INB-003に遷移 | MSG-S-INB002-001 |
| 9 | INB-003で登録した伝票の詳細を確認 | 伝票番号がINB-YYYYMMDD-NNNN形式、ステータス「入荷予定」、明細1行 | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slips | レコードが1件追加。`status='PLANNED'`、`slip_type='NORMAL'`、`planned_date` が入力値と一致 |
| 2 | inbound_slip_lines | 明細が1件追加。`planned_qty=100`、`unit_type='CASE'`、`line_status='PENDING'` |
| 3 | inbound_slips | `partner_code='SUP-0001'`、`partner_name` がマスタの値と一致（トランへのマスタ情報コピー） |
| 4 | inbound_slips | `slip_number` が `INB-YYYYMMDD-NNNN` 形式で自動採番されている |
| 5 | inbound_slips | `created_by` がログインユーザーIDと一致 |

---

### SC-INB-002: 正常系: 入荷予定登録（複数明細行）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-002 |
| シナリオ名 | 正常系: 複数明細行の入荷予定登録が成功する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。商品マスタにPRD-0001、PRD-0002、PRD-0003が登録済み。仕入先マスタにSUP-0001が登録済み |
| テストデータ | 3商品・3明細行 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-002（入荷予定登録）画面を開く | 登録画面が表示される。明細は初期1行 | — |
| 2 | ヘッダー情報（入荷予定日・仕入先）を入力 | 入力値が反映される | — |
| 3 | 明細1行目: PRD-0001、ケース、100 を入力 | 商品名が自動補完される | — |
| 4 | [＋ 行追加] ボタンをクリック | 2行目の空行が追加される | 明細テーブルに2行目表示 |
| 5 | 明細2行目: PRD-0002、ボール、50 を入力 | 商品名が自動補完される | — |
| 6 | [＋ 行追加] ボタンをクリック | 3行目の空行が追加される | — |
| 7 | 明細3行目: PRD-0003、バラ、200 を入力 | 商品名が自動補完される | — |
| 8 | [登録する] ボタンをクリック → 確認ダイアログで [OK] | 成功メッセージが表示され、INB-003に遷移 | — |
| 9 | INB-003で明細を確認 | 3明細行が表示される | 各行の商品コード・荷姿・予定数量が入力値と一致 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slips | レコードが1件追加 |
| 2 | inbound_slip_lines | 明細が3件追加。`line_no` が1,2,3で連番。各行の `product_code`, `unit_type`, `planned_qty` が入力値と一致 |

---

### SC-INB-003: 正常系: 入荷予定登録（ロット管理品）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-003 |
| シナリオ名 | 正常系: ロット管理商品の入荷予定登録が成功する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。商品マスタにPRD-LOT-001（`lot_manage_flag=true`、`expiry_manage_flag=false`）が登録済み |
| テストデータ | PRD-LOT-001、ロット番号: LOT-2026-001 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-002画面で商品コード「PRD-LOT-001」を入力 | 商品名が自動補完。ロット番号入力欄が活性化、賞味期限入力欄は非活性のまま | ロット番号フィールドが入力可能 |
| 2 | ロット番号「LOT-2026-001」を入力 | 入力値が反映される | — |
| 3 | 予定数量「50」を入力し [登録する] → [OK] | 成功メッセージが表示される | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slip_lines | `lot_number='LOT-2026-001'`、`expiry_date=null` |

**異常系サブステップ（同シナリオ内で確認）:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| A1 | ロット管理商品でロット番号を未入力のまま [登録する] | エラーメッセージ「ロット管理対象の商品にはロット番号を入力してください」が表示 | MSG-E-INB002-008 |

---

### SC-INB-004: 正常系: 入荷予定登録（期限管理品）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-004 |
| シナリオ名 | 正常系: 期限管理商品の入荷予定登録が成功する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。商品マスタにPRD-EXP-001（`lot_manage_flag=false`、`expiry_manage_flag=true`）が登録済み |
| テストデータ | PRD-EXP-001、賞味期限: 現在営業日+365日 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-002画面で商品コード「PRD-EXP-001」を入力 | 商品名が自動補完。賞味期限入力欄が活性化、ロット番号入力欄は非活性のまま | 賞味期限フィールドが入力可能 |
| 2 | 賞味期限に現在営業日+365日を入力 | 入力値が反映される | — |
| 3 | 予定数量「30」を入力し [登録する] → [OK] | 成功メッセージが表示される | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slip_lines | `lot_number=null`、`expiry_date` が入力値と一致 |

**異常系サブステップ（同シナリオ内で確認）:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| A1 | 期限管理商品で賞味期限を未入力のまま [登録する] | エラーメッセージ「期限管理対象の商品には賞味期限を入力してください」が表示 | MSG-E-INB002-009 |
| A2 | 賞味期限に現在営業日より前の日付を入力して [登録する] | エラーメッセージ「賞味期限は現在営業日以降の日付を入力してください」が表示 | MSG-E-INB002-010 |

---

### SC-INB-005: 異常系: 入荷予定登録バリデーションエラー

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-005 |
| シナリオ名 | 異常系: 入荷予定登録の各バリデーションエラーが正しく表示される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み |
| テストデータ | — |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | 全項目未入力で [登録する] | 「入荷予定日は必須です」「仕入先は必須です」「少なくとも1件の入荷明細を入力してください」が表示 | MSG-E-INB002-001, MSG-E-INB002-003, MSG-E-INB002-007 |
| 2 | 入荷予定日に現在営業日の前日を入力 | 「入荷予定日は現在営業日以降の日付を入力してください」 | MSG-E-INB002-002 |
| 3 | 存在しない商品コード「XXXXXXXXX」を入力 | 「商品コード「XXXXXXXXX」は存在しません」 | MSG-E-INB002-004 |
| 4 | 予定数量に「0」を入力 | 「予定数量は1以上の整数を入力してください」 | MSG-E-INB002-005 |
| 5 | 同一商品コードを2行入力して [登録する] | 「同一商品が複数明細に含まれています。明細を統合してください」 | MSG-E-INB002-006 |
| 6 | 明細を全行削除して [登録する] | 「少なくとも1件の入荷明細を入力してください」 | MSG-E-INB002-007 |

**API検証（結合テストのみ）:**

| # | APIリクエスト | 期待HTTPステータス | 期待エラーコード |
|:-:|-------------|:-----------------:|---------------|
| 1 | POST /api/v1/inbound/slips （必須項目欠落） | 400 | VALIDATION_ERROR |
| 2 | POST /api/v1/inbound/slips （plannedDate が営業日より前） | 422 | PLANNED_DATE_TOO_EARLY |
| 3 | POST /api/v1/inbound/slips （partner_type が SUPPLIER/BOTH でない取引先） | 422 | INBOUND_PARTNER_NOT_SUPPLIER |
| 4 | POST /api/v1/inbound/slips （同一 productId 重複） | 409 | DUPLICATE_PRODUCT_IN_LINES |
| 5 | POST /api/v1/inbound/slips （ロット管理品で lotNumber 未指定） | 422 | LOT_NUMBER_REQUIRED |
| 6 | POST /api/v1/inbound/slips （期限管理品で expiryDate 未指定） | 422 | EXPIRY_DATE_REQUIRED |
| 7 | POST /api/v1/inbound/slips （VIEWERロールでアクセス） | 403 | FORBIDDEN |

---

### SC-INB-006: 正常系: 入荷予定削除（PLANNED状態）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-006 |
| シナリオ名 | 正常系: PLANNED状態の入荷予定を物理削除する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。PLANNED状態の入荷予定が1件存在 |
| テストデータ | INB-YYYYMMDD-NNNN（PLANNED状態） |

**API検証（結合テストのみ）:**

| # | APIリクエスト | 期待HTTPステータス | 検証内容 |
|:-:|-------------|:-----------------:|---------|
| 1 | DELETE /api/v1/inbound/slips/{id} | 204 | レスポンスボディなし |
| 2 | GET /api/v1/inbound/slips/{id}（削除後） | 404 | INBOUND_SLIP_NOT_FOUND |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slips | 対象レコードが物理削除されていること |
| 2 | inbound_slip_lines | 対象伝票の明細レコードが物理削除されていること |

---

### SC-INB-007: 異常系: 入荷予定削除（PLANNED以外は削除不可）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-007 |
| シナリオ名 | 異常系: PLANNED以外のステータスの入荷予定は削除できない |
| 前提条件 | CONFIRMED/INSPECTING/STORED/CANCELLED各状態の入荷予定が存在 |
| テストデータ | — |

**API検証（結合テストのみ）:**

| # | APIリクエスト | 対象ステータス | 期待HTTPステータス | 期待エラーコード |
|:-:|-------------|:------------:|:-----------------:|---------------|
| 1 | DELETE /api/v1/inbound/slips/{id} | CONFIRMED | 409 | INBOUND_INVALID_STATUS |
| 2 | DELETE /api/v1/inbound/slips/{id} | INSPECTING | 409 | INBOUND_INVALID_STATUS |
| 3 | DELETE /api/v1/inbound/slips/{id} | STORED | 409 | INBOUND_INVALID_STATUS |
| 4 | DELETE /api/v1/inbound/slips/{id} | CANCELLED | 409 | INBOUND_INVALID_STATUS |

---

### SC-INB-010: 正常系: 入荷予定一覧表示・検索

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-010 |
| シナリオ名 | 正常系: 入荷予定一覧が表示され、各条件で検索できる |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。異なるステータス・仕入先・日付の入荷予定が10件以上登録済み |
| テストデータ | 複数件の入荷予定データ（PLANNED/CONFIRMED/INSPECTING/PARTIAL_STORED/STORED/CANCELLED各ステータス） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-001画面を開く | デフォルト条件（入荷予定日: 営業日-7日〜営業日+30日）で一覧が表示される | EVT-INB001-001 |
| 2 | 伝票番号欄に登録済みの伝票番号の先頭部分を入力して [検索] | 前方一致で該当する伝票のみ表示される | — |
| 3 | 仕入先をSUP-0001に絞り込んで [検索] | SUP-0001の入荷予定のみ表示される | — |
| 4 | 入荷予定日のFrom/Toを特定の範囲に設定して [検索] | 範囲内の入荷予定のみ表示される | — |
| 5 | [クリア] ボタンをクリック | 検索条件が初期値にリセットされる | EVT-INB001-006 |
| 6 | 一覧の行をクリック | INB-003（入荷予定詳細）画面に遷移する | EVT-INB001-003 |

**API検証（結合テストのみ）:**

| # | APIリクエスト | 期待HTTPステータス | 検証内容 |
|:-:|-------------|:-----------------:|---------|
| 1 | GET /api/v1/inbound/slips?warehouseId=1 | 200 | ページングレスポンスが返却。`totalElements` が期待件数と一致 |
| 2 | GET /api/v1/inbound/slips?warehouseId=1&slipNumber=INB-2026 | 200 | 前方一致検索結果が正しい |
| 3 | GET /api/v1/inbound/slips?warehouseId=1&status=PLANNED&status=CONFIRMED | 200 | PLANNED/CONFIRMEDの伝票のみ返却 |

---

### SC-INB-011: 正常系: 入荷予定一覧ステータス絞り込み

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-011 |
| シナリオ名 | 正常系: ステータスを指定して入荷予定一覧を絞り込めること |
| 前提条件 | 各ステータスの入荷予定が少なくとも1件ずつ存在 |
| テストデータ | PLANNED×2件、CONFIRMED×1件、INSPECTING×1件、PARTIAL_STORED×1件、STORED×2件、CANCELLED×1件 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | ステータスで「入荷予定」を選択して [検索] | PLANNED の伝票のみ表示（2件） | ステータスバッジが「入荷予定」 |
| 2 | ステータスで「入庫完了」を選択して [検索] | STORED の伝票のみ表示（2件） | ステータスバッジが「入庫完了」 |
| 3 | ステータスで「キャンセル」を選択して [検索] | CANCELLED の伝票のみ表示（1件） | ステータスバッジが「キャンセル」 |
| 4 | ステータスで「すべて」を選択して [検索] | 全件表示（8件） | — |

---

### SC-INB-020: 正常系: 入荷確認（PLANNED → CONFIRMED）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-020 |
| シナリオ名 | 正常系: PLANNED状態の入荷予定を入荷確認してCONFIRMEDに遷移する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。PLANNED状態の入荷予定が1件存在 |
| テストデータ | INB-YYYYMMDD-NNNN（PLANNED状態） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-003（入荷予定詳細）画面で対象伝票を表示 | ステータスバッジ「入荷予定」。[入荷確認] ボタンが表示される | INB003-BTN02 表示 |
| 2 | [入荷確認] ボタンをクリック | 確認ダイアログ「入荷確認します。よろしいですか？」が表示 | MSG-W-INB003-001 |
| 3 | [OK] をクリック | 成功メッセージ「入荷確認しました」が表示。ステータスが「入荷確認済」に変更 | MSG-S-INB003-001 |
| 4 | 画面リロード後のボタン状態を確認 | [入荷確認] ボタンが非表示。[検品へ] ボタンが表示される | INB003-BTN04 表示 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slips | `status='CONFIRMED'`、`updated_at` が更新されている |

**API検証（結合テストのみ）:**

| # | APIリクエスト | 期待HTTPステータス | 検証内容 |
|:-:|-------------|:-----------------:|---------|
| 1 | POST /api/v1/inbound/slips/{id}/confirm | 200 | レスポンスの `status='CONFIRMED'` |

---

### SC-INB-021: 異常系: 入荷確認（PLANNED以外からの遷移不可）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-021 |
| シナリオ名 | 異常系: PLANNED以外のステータスから入荷確認を実行すると409エラーになる |
| 前提条件 | CONFIRMED/INSPECTING/STORED/CANCELLED各状態の入荷予定が存在 |
| テストデータ | — |

**API検証（結合テストのみ）:**

| # | APIリクエスト | 対象ステータス | 期待HTTPステータス | 期待エラーコード |
|:-:|-------------|:------------:|:-----------------:|---------------|
| 1 | POST /api/v1/inbound/slips/{id}/confirm | CONFIRMED | 409 | INBOUND_INVALID_STATUS |
| 2 | POST /api/v1/inbound/slips/{id}/confirm | INSPECTING | 409 | INBOUND_INVALID_STATUS |
| 3 | POST /api/v1/inbound/slips/{id}/confirm | PARTIAL_STORED | 409 | INBOUND_INVALID_STATUS |
| 4 | POST /api/v1/inbound/slips/{id}/confirm | STORED | 409 | INBOUND_INVALID_STATUS |
| 5 | POST /api/v1/inbound/slips/{id}/confirm | CANCELLED | 409 | INBOUND_INVALID_STATUS |

---

### SC-INB-030: 正常系: 入荷キャンセル（PLANNEDから）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-030 |
| シナリオ名 | 正常系: PLANNED状態の入荷予定をキャンセルする（在庫影響なし） |
| 前提条件 | PLANNED状態の入荷予定が1件存在 |
| テストデータ | INB-YYYYMMDD-NNNN（PLANNED状態） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-003で対象伝票を表示 | [キャンセル] ボタンが表示される | — |
| 2 | [キャンセル] ボタンをクリック | 通常確認ダイアログ「入荷をキャンセルします。キャンセル後は修正できません。よろしいですか？」が表示 | MSG-W-INB003-002 |
| 3 | [OK] をクリック | 成功メッセージ「入荷をキャンセルしました」。ステータス「キャンセル」 | MSG-S-INB003-002 |
| 4 | 画面リロード | [キャンセル][入荷確認][検品へ][入庫確定へ] ボタンがすべて非表示 | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slips | `status='CANCELLED'`、`cancelled_at` がnullでないこと、`cancelled_by` がログインユーザーIDと一致 |
| 2 | inventories | 在庫テーブルに変動なし（PLANNEDからのキャンセルは在庫影響なし） |
| 3 | inventory_movements | 新規レコード追加なし |

---

### SC-INB-031: 正常系: 入荷キャンセル（CONFIRMEDから）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-031 |
| シナリオ名 | 正常系: CONFIRMED状態の入荷予定をキャンセルする（在庫影響なし） |
| 前提条件 | CONFIRMED状態の入荷予定が1件存在 |
| テストデータ | INB-YYYYMMDD-NNNN（CONFIRMED状態） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-003で対象伝票を表示 | ステータス「入荷確認済」。[キャンセル] ボタンが表示される | — |
| 2 | [キャンセル] → 確認ダイアログ [OK] | 成功メッセージ表示。ステータス「キャンセル」 | MSG-W-INB003-002、MSG-S-INB003-002 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slips | `status='CANCELLED'`、`cancelled_at` と `cancelled_by` が設定されている |
| 2 | inbound_slip_lines | 全明細の `line_status='CANCELLED'` |
| 3 | inventories | 在庫テーブルに変動なし |

---

### SC-INB-031a: 正常系: 入荷キャンセル（INSPECTINGから）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-031a |
| シナリオ名 | 正常系: INSPECTING状態の入荷予定をキャンセルする（在庫影響なし） |
| 前提条件 | INSPECTING状態の入荷予定が1件存在。全明細がline_status=INSPECTEDで入庫未実施 |
| テストデータ | INB-YYYYMMDD-NNNN（INSPECTING状態） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-003で対象伝票を表示 | ステータス「検品中」。[キャンセル] ボタンが表示される | — |
| 2 | [キャンセル] → 確認ダイアログ [OK] | 成功メッセージ表示。ステータス「キャンセル」 | MSG-W-INB003-002、MSG-S-INB003-002 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slips | `status='CANCELLED'`、`cancelled_at` と `cancelled_by` が設定されている |
| 2 | inbound_slip_lines | 全明細の `line_status='CANCELLED'` |
| 3 | inventories | 在庫テーブルに変動なし（INSPECTINGからのキャンセルは在庫影響なし） |
| 4 | inventory_movements | 新規レコード追加なし |

---

### SC-INB-032: 正常系: 入荷キャンセル（PARTIAL_STOREDから・在庫ロールバック）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-032 |
| シナリオ名 | 正常系: PARTIAL_STORED状態の入荷予定をキャンセルし、入庫済み在庫がロールバックされる |
| 前提条件 | PARTIAL_STORED状態の入荷予定が存在。2明細中1明細が入庫済み（検品数100、ロケーションA-01-01に入庫確定済み）。キャンセル前のA-01-01の在庫数量を記録しておく |
| テストデータ | 明細1: PRD-0001 検品数100 入庫済み（A-01-01）、明細2: PRD-0002 検品数50 未入庫 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-003で対象伝票を表示 | ステータス「一部入庫」。[キャンセル] ボタンが表示される | — |
| 2 | [キャンセル] ボタンをクリック | 在庫ロールバック警告ダイアログ「一部の商品が既に入庫されています。キャンセルすると入庫済みの在庫がロールバックされます。キャンセルしてよろしいですか？」が表示 | MSG-W-INB003-003 |
| 3 | [OK] をクリック | 成功メッセージ表示。ステータス「キャンセル」 | MSG-S-INB003-002 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slips | `status='CANCELLED'` |
| 2 | inbound_slip_lines | 全明細の `line_status='CANCELLED'`（STORED明細・未入庫明細ともに） |
| 3 | inventories | A-01-01のPRD-0001の在庫数量が入庫前の値に戻っている（100が減算されている） |
| 4 | inventory_movements | `movement_type='INBOUND_CANCEL'` のレコードが1件追加。`quantity` が負の値（入庫数量分の減算）。`reference_type='INBOUND_LINE'` |
| 5 | inventory_movements | `quantity_after` がロールバック後の在庫数量と一致 |

---

### SC-INB-033: 異常系: 入荷キャンセル（STORED/CANCELLEDからの遷移不可）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-033 |
| シナリオ名 | 異常系: STORED/CANCELLED状態からのキャンセルは409エラーになる |
| 前提条件 | STORED状態とCANCELLED状態の入荷予定がそれぞれ存在 |
| テストデータ | — |

**API検証（結合テストのみ）:**

| # | APIリクエスト | 対象ステータス | 期待HTTPステータス | 期待エラーコード |
|:-:|-------------|:------------:|:-----------------:|---------------|
| 1 | POST /api/v1/inbound/slips/{id}/cancel | STORED | 409 | INBOUND_INVALID_STATUS |
| 2 | POST /api/v1/inbound/slips/{id}/cancel | CANCELLED | 409 | INBOUND_INVALID_STATUS |

---

### SC-INB-040: 正常系: 入荷検品（全数一致）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-040 |
| シナリオ名 | 正常系: 全明細の入荷数が予定数と一致する検品を実行する |
| 前提条件 | CONFIRMED状態の入荷予定（明細2行: PRD-0001 予定100、PRD-0002 予定50）が存在 |
| テストデータ | — |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-003で対象伝票を表示し [検品へ] をクリック | INB-004（入荷検品）画面に遷移 | URL `/inbound/slips/:id/inspect` |
| 2 | 検品画面の初期表示を確認 | 各明細の入荷数が予定数量（100, 50）でプリセットされている。差異数は全行0 | EVT-INB004-001 |
| 3 | 入荷数を変更せず [検品内容を保存する] をクリック | 確認ダイアログ「全明細が予定数通りです。このまま確定しますか？」が表示 | MSG-I-INB004-001 |
| 4 | [OK] をクリック | 成功メッセージ「検品内容を保存しました」表示。INB-003に遷移 | MSG-S-INB004-001 |
| 5 | INB-003でステータスを確認 | ステータス「検品中」。検品数列に100、50が表示。差異数列は全行0 | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slips | `status='INSPECTING'`（CONFIRMED → INSPECTING に遷移） |
| 2 | inbound_slip_lines | 全明細の `inspected_qty` が `planned_qty` と一致。`line_status='INSPECTED'`。`inspected_at` がnullでないこと。`inspected_by` がログインユーザーID |

---

### SC-INB-041: 正常系: 入荷検品（差異あり）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-041 |
| シナリオ名 | 正常系: 入荷数に差異がある検品を実行する |
| 前提条件 | CONFIRMED状態の入荷予定（明細2行: PRD-0001 予定100、PRD-0002 予定50）が存在 |
| テストデータ | — |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-004画面で明細1行目の入荷数を「98」に変更 | 差異数が「-2」に即時更新。差異数が赤字表示。行背景が淡黄色にハイライト | EVT-INB004-002 |
| 2 | 明細2行目の入荷数を「52」に変更 | 差異数が「+2」に即時更新 | — |
| 3 | [検品内容を保存する] をクリック | 警告ダイアログ「2件の明細に差異があります。このまま確定しますか？」が表示 | MSG-W-INB004-001 |
| 4 | [OK] をクリック | 成功メッセージ表示。INB-003に遷移 | — |
| 5 | INB-003で差異を確認 | 明細1: 検品数98、差異-2（赤字）。明細2: 検品数52、差異+2（赤字） | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slip_lines | 明細1: `inspected_qty=98`。明細2: `inspected_qty=52`。全明細 `line_status='INSPECTED'` |

---

### SC-INB-042: 異常系: 入荷検品（期限切れ商品の入荷防止）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-042 |
| シナリオ名 | 異常系: 賞味期限が営業日以前の商品は入荷を受け付けない |
| 前提条件 | CONFIRMED状態の入荷予定。期限管理品（PRD-EXP-001、`expiry_manage_flag=true`）を含む明細が存在。登録時の賞味期限が現在営業日の翌日以降で登録済み |
| テストデータ | PRD-EXP-001の賞味期限を現在営業日以前に変更して検品を試みる |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-004画面で対象伝票の検品画面を表示 | 期限管理品の明細が表示される | — |
| 2 | 検品内容を保存する（APIリクエストで賞味期限を営業日以前の値で送信） | エラー: 賞味期限が営業日以前の商品は入荷できない旨のエラーが返却される | — |

**API検証（結合テストのみ）:**

| # | APIリクエスト | 期待HTTPステータス | 検証内容 |
|:-:|-------------|:-----------------:|---------|
| 1 | POST /api/v1/inbound/slips/{id}/inspect （期限切れ商品の検品数を設定） | 422 | 期限切れ商品の入荷が防止されること。エラーコード: `EXPIRY_DATE_EXPIRED`（賞味期限が営業日以前） |

---

### SC-INB-043: 正常系: 入荷検品（検品数の上書き更新）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-043 |
| シナリオ名 | 正常系: INSPECTING状態で検品数を上書き更新できる |
| 前提条件 | INSPECTING状態の入荷予定。明細の `line_status=INSPECTED`、`inspected_qty=98` |
| テストデータ | — |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-004画面で対象伝票の検品画面を開く | 前回保存した検品数（98）がプリセットされている | — |
| 2 | 入荷数を「100」に修正して [検品内容を保存する] | 成功メッセージ表示 | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slip_lines | `inspected_qty=100` に更新されていること。`inspected_at` が再検品時刻に更新 |

**異常系サブステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| A1 | `line_status=STORED` の明細に対してAPIで検品数上書きを試行 | 409 INBOUND_LINE_ALREADY_STORED | STOREDの明細は検品数上書き不可 |

---

### SC-INB-044: 正常系: 入荷検品（PARTIAL_STORED状態で残明細を検品）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-044 |
| シナリオ名 | 正常系: PARTIAL_STORED状態で残明細（PENDING/INSPECTED）を検品できる |
| 前提条件 | PARTIAL_STORED状態の入荷予定。明細2行: 明細1 `line_status=STORED`（入庫済み）、明細2 `line_status=PENDING`（未検品） |
| テストデータ | — |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-004画面を開く | 入庫済み明細（明細1）の入荷数入力欄が無効化（読み取り専用）されている。残明細（明細2）は入力可能 | — |
| 2 | 明細2の入荷数を入力して [検品内容を保存する] | 成功メッセージ表示。INB-003へ遷移 | MSG-S-INB004-001 |
| 3 | INB-003でステータスを確認 | ステータスが「一部入庫」（PARTIAL_STORED）のまま維持される | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slip_lines | 明細2の `line_status=INSPECTED`、`inspected_qty` が入力値に更新。明細1は変更なし |
| 2 | inbound_slips | `status=PARTIAL_STORED` のまま変更なし |

---

### SC-INB-045: 異常系: 入荷検品（PARTIAL_STORED状態で入庫済み明細を検品しようとした場合）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-045 |
| シナリオ名 | 異常系: PARTIAL_STORED状態で入庫済み明細（STORED）を検品しようとするとエラー |
| 前提条件 | PARTIAL_STORED状態の入荷予定。明細1 `line_status=STORED`（入庫済み） |
| テストデータ | — |

**テストステップ（結合テスト）:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | APIで明細1（STORED）を含む検品リクエストを送信 | 409 INBOUND_LINE_ALREADY_STORED | APIレスポンスのエラーコード確認 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slip_lines | 明細1の `inspected_qty` は変更されていない（ロールバック済み） |

---

### SC-INB-050: 正常系: 入庫確定（全明細一括・在庫加算・movements記録）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-050 |
| シナリオ名 | 正常系: 全明細を一括入庫確定し、在庫が正しく加算される |
| 前提条件 | INSPECTING状態の入荷予定。明細2行: PRD-0001 検品数100、PRD-0002 検品数50。全明細 `line_status=INSPECTED`。ロケーションA-01-01（入荷エリア、空き）、A-01-02（入荷エリア、空き）が存在。入庫前のinventories/inventory_movementsの状態を記録 |
| テストデータ | — |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-003で [入庫確定へ] をクリック | INB-005（入庫指示・確定）画面に遷移 | URL `/inbound/slips/:id/store` |
| 2 | 入庫先ロケーション候補がプリセットされていることを確認 | システム自動割当候補が表示。各明細にロケーションが初期選択されている | EVT-INB005-001 |
| 3 | 明細1のロケーションをA-01-01、明細2のロケーションをA-01-02に設定 | 選択値が反映される | — |
| 4 | [全件入庫確定] ボタンをクリック | 確認ダイアログ「未入庫の全明細を入庫確定します。よろしいですか？」が表示 | MSG-W-INB005-001 |
| 5 | [OK] をクリック | 成功メッセージ「全明細の入庫確定が完了しました。ステータスが「入庫完了」になりました」。INB-003に遷移 | MSG-S-INB005-002 |
| 6 | INB-003でステータスを確認 | ステータス「入庫完了」。[キャンセル][入庫確定へ] ボタンが非表示 | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slips | `status='STORED'` |
| 2 | inbound_slip_lines | 全明細の `line_status='STORED'`。`putaway_location_id` が設定値と一致。`stored_at` がnullでない。`stored_by` がログインユーザーID |
| 3 | inventories | A-01-01にPRD-0001の在庫レコードが存在し、`quantity` が検品数100分加算されていること |
| 4 | inventories | A-01-02にPRD-0002の在庫レコードが存在し、`quantity` が検品数50分加算されていること |
| 5 | inventory_movements | `movement_type='INBOUND'` のレコードが2件追加。各レコードの `quantity` が正の値（100, 50）。`reference_type='INBOUND_LINE'` |
| 6 | inventory_movements | 各レコードの `quantity_after` が入庫後の在庫数量と一致 |

---

### SC-INB-051: 正常系: 入庫確定（個別明細・ロケーション単一商品制約確認）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-051 |
| シナリオ名 | 正常系: 個別明細を入庫確定し、同一ロケーションに同一商品の在庫が加算される |
| 前提条件 | INSPECTING状態の入荷予定。明細1行: PRD-0001 検品数50。ロケーションA-01-01にPRD-0001の既存在庫（100個）あり |
| テストデータ | 既存在庫: A-01-01 / PRD-0001 / 100個 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-005画面で明細のロケーションにA-01-01を選択 | 自動割当根拠に「既存在庫あり」と表示 | INB005-D07 |
| 2 | [確定] ボタン（個別）をクリック | 成功メッセージ「PRD-0001を入庫確定しました（ロケーション: A-01-01）」 | MSG-S-INB005-001 |
| 3 | 当該行の入庫状態を確認 | 「入庫済」バッジ表示。確定ボタンが非活性 | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inventories | A-01-01のPRD-0001の`quantity`が150（既存100+入庫50）に更新 |
| 2 | inventory_movements | `movement_type='INBOUND'`、`quantity=50`、`quantity_after=150` |

---

### SC-INB-052: 異常系: 入庫確定（別商品が既存するロケーションへの入庫拒否）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-052 |
| シナリオ名 | 異常系: 別商品の在庫が存在するロケーションへの入庫が拒否される |
| 前提条件 | INSPECTING状態の入荷予定。明細: PRD-0001。ロケーションA-01-01にPRD-0002の既存在庫あり |
| テストデータ | 既存在庫: A-01-01 / PRD-0002 / 50個 |

**API検証（結合テストのみ）:**

| # | APIリクエスト | 期待HTTPステータス | 期待エラーコード |
|:-:|-------------|:-----------------:|---------------|
| 1 | POST /api/v1/inbound/slips/{id}/store（lineId=明細1、locationId=A-01-01のID） | 422 | LOCATION_PRODUCT_MISMATCH |

**追加エラーケース:**

| # | APIリクエスト | 期待HTTPステータス | 期待エラーコード | 説明 |
|:-:|-------------|:-----------------:|---------------|------|
| 2 | POST /api/v1/inbound/slips/{id}/store（入荷エリア以外のロケーション） | 422 | INBOUND_LOCATION_AREA_MISMATCH | 入荷エリアでないロケーションへの入庫 |
| 3 | POST /api/v1/inbound/slips/{id}/store（棚卸ロック中ロケーション） | 409 | INVENTORY_STOCKTAKE_IN_PROGRESS | 棚卸中ロケーションへの入庫 |
| 4 | POST /api/v1/inbound/slips/{id}/store（line_status=PENDING の明細） | 409 | INBOUND_LINE_NOT_INSPECTED | 未検品明細の入庫 |
| 5 | POST /api/v1/inbound/slips/{id}/store（line_status=STORED の明細） | 409 | INBOUND_LINE_NOT_INSPECTED | 入庫済み明細の再入庫 |

---

### SC-INB-060: 正常系: 一部入庫 → 残明細入庫 → 全明細完了

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-060 |
| シナリオ名 | 正常系: 一部明細のみ入庫確定後、残りを入庫して全明細完了（STORED）に遷移する |
| 前提条件 | INSPECTING状態の入荷予定。明細3行: PRD-0001 検品数100、PRD-0002 検品数50、PRD-0003 検品数200。全明細 `line_status=INSPECTED` |
| テストデータ | ロケーション A-01-01, A-01-02, A-01-03 全て入荷エリア・空き |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-005で明細1のみ個別 [確定]（A-01-01） | 成功メッセージ。明細1が「入庫済」 | MSG-S-INB005-001 |
| 2 | INB-003に戻ってステータスを確認 | ステータス「一部入庫」 | — |
| 3 | 再度 INB-005を開く | 明細1は「入庫済」で確定ボタン非活性。明細2,3は「未入庫」で確定ボタン活性 | — |
| 4 | 明細2を個別 [確定]（A-01-02） | 成功メッセージ。明細2が「入庫済」 | — |
| 5 | INB-003でステータス確認 | まだ「一部入庫」（明細3が未入庫） | — |
| 6 | INB-005で明細3を個別 [確定]（A-01-03） | 成功メッセージ。全明細「入庫済」。INB-003に戻るとステータス「入庫完了」 | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slips | ステップ1後: `status='PARTIAL_STORED'` |
| 2 | inbound_slips | ステップ4後: `status='PARTIAL_STORED'`（まだ明細3が未入庫） |
| 3 | inbound_slips | ステップ6後: `status='STORED'`（全明細完了） |
| 4 | inbound_slip_lines | ステップ6後: 全明細の `line_status='STORED'` |
| 5 | inventories | A-01-01: PRD-0001 qty=100、A-01-02: PRD-0002 qty=50、A-01-03: PRD-0003 qty=200 |
| 6 | inventory_movements | `movement_type='INBOUND'` のレコードが3件。各レコードの `quantity` が検品数と一致 |

---

### SC-INB-061: 正常系: 一部入庫状態からのキャンセル（在庫ロールバック検証）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-061 |
| シナリオ名 | 正常系: PARTIAL_STORED状態からキャンセルし、入庫済み在庫が正確にロールバックされる |
| 前提条件 | SC-INB-060のステップ4完了後を想定。PARTIAL_STORED状態。明細1（PRD-0001）と明細2（PRD-0002）が入庫済み。明細3（PRD-0003）は未入庫。入庫前の在庫数量を記録しておく |
| テストデータ | 入庫済み: A-01-01 PRD-0001 100個、A-01-02 PRD-0002 50個 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-003で [キャンセル] をクリック | 在庫ロールバック警告ダイアログが表示（MSG-W-INB003-003） | — |
| 2 | [OK] をクリック | 成功メッセージ。ステータス「キャンセル」 | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slips | `status='CANCELLED'` |
| 2 | inventories | A-01-01のPRD-0001在庫: 入庫分100が減算されていること |
| 3 | inventories | A-01-02のPRD-0002在庫: 入庫分50が減算されていること |
| 4 | inventory_movements | `movement_type='INBOUND_CANCEL'` のレコードが2件追加（PRD-0001分、PRD-0002分） |
| 5 | inventory_movements | 各INBOUND_CANCELレコードの`quantity`が負の値。`quantity_after` がロールバック後の在庫数量と一致 |
| 6 | inventory_movements | PRD-0003（未入庫）に対するINBOUND_CANCELレコードは存在しないこと |

---

### SC-INB-070: 正常系: 入荷実績照会

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-070 |
| シナリオ名 | 正常系: 入庫完了した入荷実績を照会できる |
| 前提条件 | STORED状態の入荷予定が複数件存在 |
| テストデータ | — |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-006（入荷実績照会）画面を開く | デフォルト条件（入庫日: 当月1日〜現在営業日）で実績一覧が表示される | EVT-INB006-001 |
| 2 | 一覧テーブルで伝票番号・入庫完了日・仕入先・商品コード・商品名・予定数量・検品数・差異数が表示されることを確認 | 各カラムに値が表示されている | — |
| 3 | 差異数が0以外の行を確認 | 差異数が赤字表示されている | — |
| 4 | 伝票番号リンクをクリック | INB-003（当該伝票の詳細）に遷移 | EVT-INB006-004 |

**API検証（結合テストのみ）:**

| # | APIリクエスト | 期待HTTPステータス | 検証内容 |
|:-:|-------------|:-----------------:|---------|
| 1 | GET /api/v1/inbound/results?warehouseId=1 | 200 | `line_status=STORED` の明細のみ返却。`diffQty` が正しく計算されている |

---

### SC-INB-071: 正常系: 入荷実績照会（絞り込み検索）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-071 |
| シナリオ名 | 正常系: 各条件で入荷実績を絞り込めること |
| 前提条件 | 複数のSTORED入荷予定（異なる仕入先・商品・入庫日）が存在 |
| テストデータ | — |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | 仕入先をSUP-0001に絞り込んで [検索] | SUP-0001の実績のみ表示 | — |
| 2 | 商品コード「PRD-0001」を入力して [検索] | PRD-0001を含む実績のみ表示 | — |
| 3 | 入庫日の範囲を狭めて [検索] | 範囲内の実績のみ表示 | — |
| 4 | 伝票番号の先頭部分を入力して [検索] | 前方一致で該当する実績のみ表示 | — |
| 5 | [クリア] をクリック | 検索条件が初期値にリセット | — |

**API検証（結合テストのみ）:**

| # | APIリクエスト | 期待HTTPステータス | 検証内容 |
|:-:|-------------|:-----------------:|---------|
| 1 | GET /api/v1/inbound/results?warehouseId=1&partnerId=5 | 200 | 仕入先ID=5の実績のみ返却 |
| 2 | GET /api/v1/inbound/results?warehouseId=1&productCode=PRD-0001 | 200 | PRD-0001の実績のみ返却 |
| 3 | GET /api/v1/inbound/results?warehouseId=1&slipNumber=INB-2026 | 200 | 前方一致で該当する実績のみ返却 |

---

### SC-INB-080: 正常系: 入荷検品レポート出力（RPT-01）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-080 |
| シナリオ名 | 正常系: 入荷検品レポート（RPT-01）がPDF出力される |
| 前提条件 | 検品済み（INSPECTING以降のステータス）の入荷予定が存在。明細に差異あり行・ロット管理品・期限管理品を含む |
| テストデータ | 明細3行: 差異なし1件、差異あり1件（差異-2）、ロット管理品1件 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-003で [検品レポート] ボタンをクリック | RPT-01出力ダイアログが表示される | EVT-INB003-007 |
| 2 | ダイアログ内でPDF出力を実行 | PDFファイルがダウンロードされる | — |
| 3 | PDFの内容を確認 — ヘッダー | 伝票番号・仕入先名・入荷予定日・倉庫名・出力日時・出力者が表示 | — |
| 4 | PDFの内容を確認 — 明細 | 商品コード・商品名・ケース入数・予定数(ケース/バラ)・検品数(ケース/バラ)・差異(ケース/バラ)・ロット番号・期限日が表示 | — |
| 5 | PDFの内容を確認 — 差異行 | 差異がゼロでない行はピンク背景（#FFF1F1）で強調。差異数値は太字 | — |
| 6 | PDFの内容を確認 — ロット管理OFF | ロット管理OFFの商品はロット番号列が「—」 | — |
| 7 | PDFの内容を確認 — 期限管理OFF | 期限管理OFFの商品は期限日列が「—」 | — |
| 8 | PDFの内容を確認 — 合計行 | テーブル末尾に合計行が表示。各数量列の合計値が正しい | — |

---

### SC-INB-090: E2E: 入荷予定登録 → 確認 → 検品 → 入庫確定（フルフロー）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-INB-090 |
| シナリオ名 | E2Eフルフロー: 入荷予定登録から入庫完了までの一連操作を通して実行する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。商品マスタ（PRD-0001通常品、PRD-LOT-001ロット管理品、PRD-EXP-001期限管理品）、仕入先マスタ（SUP-0001）、ロケーション（入荷エリアに3件以上の空きロケーション）が登録済み |
| テストデータ | — |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-002で入荷予定を登録（3明細: PRD-0001 ケース 100、PRD-LOT-001 ケース 50 LOT-2026-001、PRD-EXP-001 バラ 30 期限=営業日+365日） | 登録成功。INB-003に遷移。ステータス「入荷予定」 | — |
| 2 | INB-001で検索し、登録した伝票が一覧に表示されることを確認 | 伝票番号・ステータス「入荷予定」が一覧に表示 | — |
| 3 | INB-003で [入荷確認] をクリック → [OK] | ステータス「入荷確認済」 | — |
| 4 | INB-003で [検品へ] をクリック | INB-004に遷移 | — |
| 5 | 明細1: 入荷数を98に変更（差異-2）。明細2,3: 予定数通り | 差異数が正しく表示 | — |
| 6 | [検品内容を保存する] → 確認ダイアログ [OK] | 成功。INB-003に遷移。ステータス「検品中」 | — |
| 7 | INB-003で [検品レポート] をクリックしPDF出力 | PDFがダウンロードされ、差異行がピンク背景 | — |
| 8 | INB-003で [入庫確定へ] をクリック | INB-005に遷移 | — |
| 9 | 明細1のみ個別 [確定]（ロケーション選択） | 成功。明細1が「入庫済」 | — |
| 10 | INB-003に戻る | ステータス「一部入庫」 | — |
| 11 | 再度 INB-005を開き、[全件入庫確定] で残り2明細を一括確定 | 成功。INB-003に遷移。ステータス「入庫完了」 | — |
| 12 | INB-006（入荷実績照会）で当該伝票を検索 | 3明細の実績が表示。予定数・検品数・差異数が正しい | — |

**DB検証（結合テストのみ・最終状態）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slips | `status='STORED'` |
| 2 | inbound_slip_lines | 全3明細 `line_status='STORED'`。明細1: `inspected_qty=98`、明細2: `inspected_qty=50`、明細3: `inspected_qty=30` |
| 3 | inventories | 3ロケーションに各商品の在庫が正しく登録されている |
| 4 | inventory_movements | `movement_type='INBOUND'` のレコードが3件。各`quantity`が検品数と一致 |
| 5 | inbound_slip_lines | 全明細に `putaway_location_id`、`stored_at`、`stored_by` が設定されている |

---

## Playwrightコード例

### SC-INB-001: 入荷予定の新規登録

```typescript
test('SC-INB-001: 入荷予定の新規登録が成功する（単一明細）', async ({ page }) => {
  // 前提: ログイン済み
  await loginAs(page, 'WAREHOUSE_MANAGER');

  // Step 1: 入荷予定一覧を開く
  await page.goto('/inbound/slips');
  await expect(page.locator('h1')).toContainText('入荷予定一覧');

  // Step 2: 新規登録ボタンクリック
  await page.click('button:has-text("新規登録")');
  await expect(page).toHaveURL(/\/inbound\/slips\/new/);

  // Step 3-6: フォーム入力
  await page.fill('[data-testid="planned-date"]', '2026-04-01');
  await page.selectOption('[data-testid="partner-select"]', { label: 'SUP-0001' });
  await page.fill('[data-testid="product-code-0"]', 'PRD-0001');
  await page.waitForSelector('[data-testid="product-name-0"]:not(:empty)');
  await page.selectOption('[data-testid="unit-type-0"]', 'CASE');
  await page.fill('[data-testid="quantity-0"]', '100');

  // Step 7-8: 登録
  await page.click('button:has-text("登録する")');
  await page.click('button:has-text("OK")'); // 確認ダイアログ
  await expect(page.locator('.el-message--success')).toBeVisible();

  // Step 9: 詳細画面で確認
  await expect(page).toHaveURL(/\/inbound\/slips\/\d+/);
  await expect(page.locator('[data-testid="status-badge"]')).toContainText('入荷予定');
  await expect(page.locator('[data-testid="slip-number"]')).toMatch(/INB-\d{8}-\d{4}/);
});
```

### SC-INB-090: E2Eフルフロー

```typescript
test('SC-INB-090: 入荷予定登録から入庫完了までのフルフロー', async ({ page }) => {
  await loginAs(page, 'WAREHOUSE_MANAGER');

  // Phase 1: 入荷予定登録
  await page.goto('/inbound/slips/new');
  await page.fill('[data-testid="planned-date"]', '2026-04-01');
  await page.selectOption('[data-testid="partner-select"]', { label: 'SUP-0001' });

  // 明細1: 通常商品
  await page.fill('[data-testid="product-code-0"]', 'PRD-0001');
  await page.waitForSelector('[data-testid="product-name-0"]:not(:empty)');
  await page.selectOption('[data-testid="unit-type-0"]', 'CASE');
  await page.fill('[data-testid="quantity-0"]', '100');

  // 明細2: ロット管理品
  await page.click('button:has-text("行追加")');
  await page.fill('[data-testid="product-code-1"]', 'PRD-LOT-001');
  await page.waitForSelector('[data-testid="product-name-1"]:not(:empty)');
  await page.selectOption('[data-testid="unit-type-1"]', 'CASE');
  await page.fill('[data-testid="lot-number-1"]', 'LOT-2026-001');
  await page.fill('[data-testid="quantity-1"]', '50');

  // 明細3: 期限管理品
  await page.click('button:has-text("行追加")');
  await page.fill('[data-testid="product-code-2"]', 'PRD-EXP-001');
  await page.waitForSelector('[data-testid="product-name-2"]:not(:empty)');
  await page.selectOption('[data-testid="unit-type-2"]', 'PIECE');
  await page.fill('[data-testid="expiry-date-2"]', '2027-04-01');
  await page.fill('[data-testid="quantity-2"]', '30');

  // 登録
  await page.click('button:has-text("登録する")');
  await page.click('button:has-text("OK")');
  await expect(page.locator('.el-message--success')).toBeVisible();
  await expect(page.locator('[data-testid="status-badge"]')).toContainText('入荷予定');

  // Phase 2: 入荷確認
  await page.click('button:has-text("入荷確認")');
  await page.click('button:has-text("OK")');
  await expect(page.locator('.el-message--success')).toBeVisible();
  await expect(page.locator('[data-testid="status-badge"]')).toContainText('入荷確認済');

  // Phase 3: 入荷検品（差異あり）
  await page.click('button:has-text("検品へ")');
  await expect(page).toHaveURL(/\/inbound\/slips\/\d+\/inspect/);
  await page.fill('[data-testid="inspected-qty-0"]', '98'); // 差異-2
  // 明細2,3は予定通り（初期値のまま）
  await page.click('button:has-text("検品内容を保存する")');
  await page.click('button:has-text("OK")');
  await expect(page.locator('.el-message--success')).toBeVisible();
  await expect(page.locator('[data-testid="status-badge"]')).toContainText('検品中');

  // Phase 4: 入庫確定（一部 → 全件）
  await page.click('button:has-text("入庫確定へ")');
  await expect(page).toHaveURL(/\/inbound\/slips\/\d+\/store/);

  // 明細1のみ個別確定
  await page.click('[data-testid="store-btn-0"]');
  await expect(page.locator('[data-testid="store-status-0"]')).toContainText('入庫済');

  // 詳細に戻って一部入庫確認
  await page.click('button:has-text("詳細に戻る")');
  await expect(page.locator('[data-testid="status-badge"]')).toContainText('一部入庫');

  // 残りを全件入庫確定
  await page.click('button:has-text("入庫確定へ")');
  await page.click('button:has-text("全件入庫確定")');
  await page.click('button:has-text("OK")');
  await expect(page.locator('.el-message--success')).toBeVisible();
  await expect(page.locator('[data-testid="status-badge"]')).toContainText('入庫完了');

  // Phase 5: 入荷実績照会で確認
  await page.goto('/inbound/results');
  await expect(page.locator('table')).toContainText('PRD-0001');
  await expect(page.locator('table')).toContainText('PRD-LOT-001');
  await expect(page.locator('table')).toContainText('PRD-EXP-001');
});
```

---

## テストデータ要件

### マスタデータ（事前登録が必要）

| テーブル | データ | 用途 |
|---------|--------|------|
| warehouses | WH-001（東京DC） | 全テスト共通 |
| partners | SUP-0001（仕入先・`partner_type=SUPPLIER`） | 入荷予定登録 |
| partners | CUS-0001（得意先・`partner_type=CUSTOMER`） | 仕入先種別エラーテスト用 |
| products | PRD-0001（通常商品・ロット管理OFF・期限管理OFF） | 基本テスト |
| products | PRD-0002, PRD-0003（通常商品） | 複数明細テスト |
| products | PRD-LOT-001（`lot_manage_flag=true`・`expiry_manage_flag=false`） | ロット管理テスト |
| products | PRD-EXP-001（`lot_manage_flag=false`・`expiry_manage_flag=true`） | 期限管理テスト |
| locations | A-01-01, A-01-02, A-01-03（入荷エリア所属） | 入庫確定テスト |
| locations | B-01-01（出荷エリア所属） | エリア不一致エラーテスト |
| areas | INBOUND_AREA（`area_type=INBOUND`） | 入庫確定テスト |
| users | WAREHOUSE_MANAGER権限ユーザー | 全テスト共通 |
| users | VIEWERロールユーザー | 権限テスト |

### 既存在庫データ（特定テストシナリオで使用）

| テーブル | データ | 対象シナリオ |
|---------|--------|------------|
| inventories | A-01-01 / PRD-0001 / qty=100 | SC-INB-051（同一商品追加入庫） |
| inventories | A-01-01 / PRD-0002 / qty=50 | SC-INB-052（別商品ロケーション制約） |

---

## 備考

- ステータス遷移の詳細は [API-06-inbound.md](../functional-design/API-06-inbound.md) のステータス遷移図を参照。
- ビジネスルール（営業日基準・修正不可・検品差異の扱い等）は [02-inbound-management.md](../functional-requirements/02-inbound-management.md) を参照。
- パスワードポリシー・認証方式は [10-security-architecture.md](../architecture-blueprint/10-security-architecture.md) を参照。
- テーブル定義は [data-model](../data-model/) を参照。
