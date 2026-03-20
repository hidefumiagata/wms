# テスト仕様書 — レポート出力（結合・E2Eテスト）

## ヘッダー情報

| 項目 | 記載内容 |
|------|---------|
| テスト仕様書ID | TST-RPT-001 |
| テスト対象機能 | レポート出力（RPT-01〜RPT-18、全17レポート） |
| 対象設計書 | API-10-report.md、RPT-01〜RPT-18（RPT-02 欠番） |
| 作成者 | |
| 作成日 | 2026-03-20 |
| レビュー者 | |
| レビュー日 | |

---

## 1. テスト対象レポート一覧

| RPT# | レポート名 | API ID | APIパス | 業務カテゴリ |
|------|-----------|--------|---------|------------|
| RPT-01 | 入荷検品レポート | API-RPT-001 | `/api/v1/reports/inbound-inspection` | 入荷管理 |
| RPT-03 | 入荷予定レポート | API-RPT-003 | `/api/v1/reports/inbound-plan` | 入荷管理 |
| RPT-04 | 入庫実績レポート | API-RPT-004 | `/api/v1/reports/inbound-result` | 入荷管理 |
| RPT-05 | 未入荷リスト（リアルタイム） | API-RPT-005 | `/api/v1/reports/unreceived-realtime` | 入荷管理 |
| RPT-06 | 未入荷リスト（確定） | API-RPT-006 | `/api/v1/reports/unreceived-confirmed` | 入荷管理 |
| RPT-07 | 在庫一覧レポート | API-RPT-007 | `/api/v1/reports/inventory` | 在庫管理 |
| RPT-08 | 在庫推移レポート | API-RPT-008 | `/api/v1/reports/inventory-transition` | 在庫管理 |
| RPT-09 | 在庫訂正一覧 | API-RPT-009 | `/api/v1/reports/inventory-correction` | 在庫管理 |
| RPT-10 | 棚卸リスト | API-RPT-010 | `/api/v1/reports/stocktake-list` | 在庫管理 |
| RPT-11 | 棚卸結果レポート | API-RPT-011 | `/api/v1/reports/stocktake-result` | 在庫管理 |
| RPT-12 | ピッキング指示書 | API-RPT-012 | `/api/v1/reports/picking-instruction` | 出荷管理 |
| RPT-13 | 出荷検品レポート | API-RPT-013 | `/api/v1/reports/shipping-inspection` | 出荷管理 |
| RPT-14 | 配送リスト | API-RPT-014 | `/api/v1/reports/delivery-list` | 出荷管理 |
| RPT-15 | 未出荷リスト（リアルタイム） | API-RPT-015 | `/api/v1/reports/unshipped-realtime` | 出荷管理 |
| RPT-16 | 未出荷リスト（確定） | API-RPT-016 | `/api/v1/reports/unshipped-confirmed` | 出荷管理 |
| RPT-17 | 日次集計レポート | API-RPT-017 | `/api/v1/reports/daily-summary` | バッチ |
| RPT-18 | 返品レポート | API-RPT-018 | `/api/v1/reports/returns` | 返品管理 |

---

## 2. テストシナリオ一覧

### 2.1. 共通テストパターン（全17レポート × 6パターン = 102ケース）

以下の6パターンは全17レポートに対して一律に実施する。個別シナリオ詳細は「3. 共通テストパターン詳細」に記載。

| シナリオID | シナリオ名 | 優先度 | 前提条件 | 結合 | E2E |
|-----------|----------|:------:|---------|:----:|:---:|
| SC-COM-001 | 正常系: JSON形式出力 | 高 | ログイン済み、対象データ存在 | ○ | ○ |
| SC-COM-002 | 正常系: CSV形式出力（UTF-8 BOM付き） | 高 | ログイン済み、対象データ存在 | ○ | ○ |
| SC-COM-003 | 正常系: PDF形式出力（ダウンロード確認） | 高 | ログイン済み、対象データ存在 | ○ | ○ |
| SC-COM-004 | 正常系: 検索条件による絞り込み | 高 | ログイン済み、複数データ存在 | ○ | ○ |
| SC-COM-005 | 正常系: データ0件時のレポート出力 | 中 | ログイン済み、該当データなし | ○ | ○ |
| SC-COM-006 | 正常系: 権限チェック（全ロール） | 高 | 各ロールでログイン済み | ○ | — |

### 2.2. 共通テストパターン マトリクス

下表の "○" は当該レポートで当該パターンを実施することを示す。全レポート全パターン対象。

| RPT# | レポート名 | COM-001 JSON | COM-002 CSV | COM-003 PDF | COM-004 絞込 | COM-005 0件 | COM-006 権限 |
|------|-----------|:----:|:----:|:----:|:----:|:----:|:----:|
| RPT-01 | 入荷検品レポート | ○ | ○ | ○ | ○ | ○ | ○ |
| RPT-03 | 入荷予定レポート | ○ | ○ | ○ | ○ | ○ | ○ |
| RPT-04 | 入庫実績レポート | ○ | ○ | ○ | ○ | ○ | ○ |
| RPT-05 | 未入荷リスト（RT） | ○ | ○ | ○ | ○ | ○ | ○ |
| RPT-06 | 未入荷リスト（確定） | ○ | ○ | ○ | ○ | ○ | ○ |
| RPT-07 | 在庫一覧レポート | ○ | ○ | ○ | ○ | ○ | ○ |
| RPT-08 | 在庫推移レポート | ○ | ○ | ○ | ○ | ○ | ○ |
| RPT-09 | 在庫訂正一覧 | ○ | ○ | ○ | ○ | ○ | ○ |
| RPT-10 | 棚卸リスト | ○ | ○ | ○ | ○ | ○ | ○ |
| RPT-11 | 棚卸結果レポート | ○ | ○ | ○ | ○ | ○ | ○ |
| RPT-12 | ピッキング指示書 | ○ | ○ | ○ | ○ | ○ | ○ |
| RPT-13 | 出荷検品レポート | ○ | ○ | ○ | ○ | ○ | ○ |
| RPT-14 | 配送リスト | ○ | ○ | ○ | ○ | ○ | ○ |
| RPT-15 | 未出荷リスト（RT） | ○ | ○ | ○ | ○ | ○ | ○ |
| RPT-16 | 未出荷リスト（確定） | ○ | ○ | ○ | ○ | ○ | ○ |
| RPT-17 | 日次集計レポート | ○ | ○ | ○ | ○ | ○ | ○ |
| RPT-18 | 返品レポート | ○ | ○ | ○ | ○ | ○ | ○ |

### 2.3. レポート固有テストシナリオ

| シナリオID | 対象RPT | シナリオ名 | 優先度 | 前提条件 | 結合 | E2E |
|-----------|--------|----------|:------:|---------|:----:|:---:|
| SC-RPT01-001 | RPT-01 | 正常系: 検品差異行のハイライト表示（PDF） | 高 | 差異ありデータ存在 | ○ | ○ |
| SC-RPT01-002 | RPT-01 | 正常系: 差異なし伝票の出力 | 中 | 差異なしデータのみ | ○ | — |
| SC-RPT01-003 | RPT-01 | 正常系: 未検品明細の表示 | 中 | 検品未実施明細あり | ○ | — |
| SC-RPT01-004 | RPT-01 | 異常系: 存在しない伝票IDの指定 | 高 | — | ○ | — |
| SC-RPT03-001 | RPT-03 | 正常系: ステータス別絞り込み | 中 | 複数ステータスのデータ存在 | ○ | ○ |
| SC-RPT03-002 | RPT-03 | 正常系: キャンセル行の取り消し線表示 | 中 | CANCELLED伝票あり | ○ | — |
| SC-RPT04-001 | RPT-04 | 正常系: 差異行のピンク背景表示 | 中 | 差異ありデータ存在 | ○ | — |
| SC-RPT04-002 | RPT-04 | 正常系: 返品数量の表示 | 中 | 入荷返品紐づきあり | ○ | ○ |
| SC-RPT05-001 | RPT-05 | 正常系: 遅延7日以上の行のピンク背景 | 中 | 遅延7日以上データあり | ○ | — |
| SC-RPT05-002 | RPT-05 | 正常系: 基準日指定によるリアルタイム取得 | 高 | 複数日のデータ存在 | ○ | ○ |
| SC-RPT06-001 | RPT-06 | 正常系: 日替処理確定データの出力 | 高 | 日替処理完了済み | ○ | ○ |
| SC-RPT06-002 | RPT-06 | 正常系: 日替処理未実行の営業日指定で空配列が返る | 高 | 日替処理未実行 | ○ | — |
| SC-RPT07-001 | RPT-07 | 正常系: allocatedQty/availableQtyの表示 | 高 | 引当ありデータ存在 | ○ | ○ |
| SC-RPT07-002 | RPT-07 | 正常系: 有効在庫0の行の灰色背景 | 中 | availableQty=0のデータ存在 | ○ | — |
| SC-RPT07-003 | RPT-07 | 正常系: 商品コード別グルーピング・小計 | 中 | 複数商品データ存在 | ○ | ○ |
| SC-RPT08-001 | RPT-08 | 正常系: 変動種別ラベルの正確な表示 | 中 | 複数変動種別のデータ存在 | ○ | — |
| SC-RPT08-002 | RPT-08 | 正常系: 在庫訂正行の黄色背景 | 中 | CORRECTION種別あり | ○ | — |
| SC-RPT08-003 | RPT-08 | 正常系: 日次小計と期間合計の算出 | 中 | 複数日のデータ存在 | ○ | ○ |
| SC-RPT09-001 | RPT-09 | 正常系: 増加訂正の薄青/減少訂正の薄赤背景 | 中 | 増減両方のデータ存在 | ○ | — |
| SC-RPT09-002 | RPT-09 | 正常系: 訂正理由への実施者名追記 | 中 | 訂正データ存在 | ○ | — |
| SC-RPT10-001 | RPT-10 | 正常系: PDF出力時の帳簿数量非表示（hideBookQty=true） | 高 | 棚卸データ存在 | ○ | ○ |
| SC-RPT10-002 | RPT-10 | 正常系: PDF出力時の帳簿数量表示（hideBookQty=false/未指定） | 高 | 棚卸データ存在 | ○ | — |
| SC-RPT10-003 | RPT-10 | 正常系: JSON/CSVにsystemQuantityフィールドが含まれること | 中 | 棚卸データ存在 | ○ | — |
| SC-RPT10-004 | RPT-10 | 正常系: ロケーション区切り太線の確認 | 低 | 複数ロケーションデータ存在 | ○ | — |
| SC-RPT11-001 | RPT-11 | 正常系: 差異行のピンク背景表示 | 中 | 差異ありデータ存在 | ○ | — |
| SC-RPT11-002 | RPT-11 | 正常系: 差異サマリー（過剰/不足/差異あり件数）の表示 | 高 | 差異ありデータ存在 | ○ | ○ |
| SC-RPT11-003 | RPT-11 | 正常系: 未確定（STARTED）状態での出力 | 中 | 棚卸中データ存在 | ○ | — |
| SC-RPT12-001 | RPT-12 | 正常系: ロケーション順ソートの確認 | 中 | 複数ロケーションデータ存在 | ○ | — |
| SC-RPT12-002 | RPT-12 | 正常系: チェック欄（□）の印字確認 | 低 | ピッキング指示データ存在 | ○ | — |
| SC-RPT12-003 | RPT-12 | 正常系: バッチピッキング（複数受注まとめ）対応 | 中 | 複数受注のピッキング指示存在 | ○ | ○ |
| SC-RPT13-001 | RPT-13 | 正常系: 差異行のピンク背景・判定NG表示 | 中 | 差異ありデータ存在 | ○ | — |
| SC-RPT13-002 | RPT-13 | 正常系: 全行差異なし時のALL OK判定 | 中 | 差異なしデータのみ | ○ | — |
| SC-RPT13-003 | RPT-13 | 正常系: 検品前データの出力（検品数/差異「—」表示） | 中 | 検品前ステータスのデータ存在 | ○ | — |
| SC-RPT14-001 | RPT-14 | 正常系: 伝票ヘッダー+商品明細のネスト構造表示 | 高 | 複数伝票・複数明細データ存在 | ○ | ○ |
| SC-RPT14-002 | RPT-14 | 正常系: 伝票小計・総合計の算出 | 中 | 複数伝票データ存在 | ○ | ○ |
| SC-RPT14-003 | RPT-14 | 正常系: 配送業者・送り状番号の未設定時「—」表示 | 中 | carrier/trackingNumber未設定データ存在 | ○ | — |
| SC-RPT15-001 | RPT-15 | 正常系: 遅延度（★/★★/★★★）の表示 | 中 | 遅延1日/2-3日/4日以上データ存在 | ○ | ○ |
| SC-RPT15-002 | RPT-15 | 正常系: 遅延度別の行背景色（白/薄黄/薄赤） | 中 | 遅延度バリエーションあり | ○ | — |
| SC-RPT16-001 | RPT-16 | 正常系: 同一伝票の省略表示（「〃」） | 中 | 複数明細の伝票データ存在 | ○ | — |
| SC-RPT16-002 | RPT-16 | 正常系: 遅延行のピンク背景表示 | 中 | 遅延データ存在 | ○ | — |
| SC-RPT17-001 | RPT-17 | 正常系: 倉庫別セクション+全倉庫合計の表示 | 高 | 複数倉庫の日替処理完了 | ○ | ○ |
| SC-RPT17-002 | RPT-17 | 正常系: 未処理アラートの赤色太字表示 | 中 | 未入荷/未出荷1件以上 | ○ | — |
| SC-RPT17-003 | RPT-17 | 正常系: 全倉庫合計の各項目合算値の正確性 | 高 | 複数倉庫データ存在 | ○ | ○ |
| SC-RPT17-004 | RPT-17 | 異常系: 日替処理未完了の営業日指定で404エラー | 高 | 日替処理未実行 | ○ | — |
| SC-RPT18-001 | RPT-18 | 正常系: 返品種別別グルーピングの表示 | 中 | 入荷返品・出荷返品データ存在 | ○ | ○ |
| SC-RPT18-002 | RPT-18 | 正常系: 関連伝票番号の入荷/出荷切り替え表示 | 中 | 入荷返品・出荷返品データ存在 | ○ | — |

---

## 3. 共通テストパターン詳細

### SC-COM-001: 正常系: JSON形式出力

| 項目 | 内容 |
|------|------|
| シナリオID | SC-COM-001 |
| シナリオ名 | 正常系: JSON形式でレポートデータが正しく返却される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。テストデータが登録済み |
| テストデータ | 各レポートに対応するテストデータ（後述のテストデータセクション参照） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | レポートAPIに `format=json` でGETリクエストを送信 | HTTP 200 が返却される | HTTPステータスコード |
| 2 | レスポンスヘッダーを確認 | `Content-Type: application/json; charset=UTF-8` | ヘッダー値 |
| 3 | レスポンスボディを確認 | JSON配列形式のデータが返却される | JSONパース成功 |
| 4 | データ件数を確認 | テストデータに合致する件数が返却される | 配列の要素数 |
| 5 | フィールド値を確認 | API設計書のレスポンス仕様に定義された全フィールドが含まれる | フィールド存在・型チェック |

**DB検証（結合テストのみ）:**

| # | 検証対象 | 検証内容 |
|:-:|---------|---------|
| 1 | レスポンスデータ | DB上の元データと突合し、値が一致すること |

---

### SC-COM-002: 正常系: CSV形式出力（UTF-8 BOM付き）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-COM-002 |
| シナリオ名 | 正常系: CSV形式でファイルダウンロードが正しく動作する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。テストデータが登録済み |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | レポートAPIに `format=csv` でGETリクエストを送信 | HTTP 200 が返却される | HTTPステータスコード |
| 2 | レスポンスヘッダー `Content-Type` を確認 | `text/csv; charset=UTF-8` | ヘッダー値 |
| 3 | レスポンスヘッダー `Content-Disposition` を確認 | `attachment; filename="{レポート識別子}_{YYYYMMDD}.csv"` 形式 | ヘッダー値 |
| 4 | レスポンスボディ先頭3バイトを確認 | UTF-8 BOM（`0xEF 0xBB 0xBF`）が付与されている | バイト値 |
| 5 | CSVの1行目を確認 | 日本語ヘッダー行が存在する | CSV解析 |
| 6 | CSVの2行目以降を確認 | テストデータに対応するデータ行が出力されている | CSV解析 |
| 7 | CSVのカラム数を確認 | ヘッダー行とデータ行のカラム数が一致する | カラム数カウント |

---

### SC-COM-003: 正常系: PDF形式出力（ダウンロード確認）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-COM-003 |
| シナリオ名 | 正常系: PDF形式でファイルダウンロードが正しく動作する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。テストデータが登録済み |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | レポートAPIに `format=pdf` でGETリクエストを送信 | HTTP 200 が返却される | HTTPステータスコード |
| 2 | レスポンスヘッダー `Content-Type` を確認 | `application/pdf` | ヘッダー値 |
| 3 | レスポンスヘッダー `Content-Disposition` を確認 | `attachment; filename="{レポート識別子}_{YYYYMMDD}.pdf"` 形式 | ヘッダー値 |
| 4 | レスポンスボディがPDFバイナリであることを確認 | 先頭バイトが `%PDF` マジックバイトで始まる | バイト値 |
| 5 | PDFのページ数を確認 | 1ページ以上であること | PDFBox等でページ数取得 |
| 6 | PDFのテキスト抽出を確認 | レポートタイトル・倉庫名・出力日時が含まれる | PDFBox PDFTextStripper |
| 7 | PDFの共通ヘッダーを確認 | レポートタイトル、倉庫名、出力者名、出力日時が正しく表示される | テキスト抽出 |
| 8 | PDFの共通フッターを確認 | 「WMS ShowCase」とページ番号（`Page N / M`）が表示される | テキスト抽出 |

---

### SC-COM-004: 正常系: 検索条件による絞り込み

| 項目 | 内容 |
|------|------|
| シナリオID | SC-COM-004 |
| シナリオ名 | 正常系: 検索条件でデータが正しく絞り込まれる |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。検索条件に合致するデータ・合致しないデータの両方が存在 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | 絞り込み条件なし（必須パラメータのみ）でリクエスト | 全件が返却される | 件数確認 |
| 2 | 絞り込み条件を指定してリクエスト | 条件に合致するデータのみ返却される | データ値確認 |
| 3 | 条件に合致しないデータが含まれていないことを確認 | 合致しないデータは返却されない | 全レコード検証 |

**レポート別の検索条件パラメータ:**

| RPT# | 主要な絞り込みパラメータ |
|------|----------------------|
| RPT-01 | `slipId` |
| RPT-03 | `warehouseId`, `plannedDateFrom`, `plannedDateTo`, `status`, `partnerId` |
| RPT-04 | `warehouseId`, `storedDateFrom`, `storedDateTo`, `partnerId` |
| RPT-05 | `warehouseId`, `asOfDate` |
| RPT-06 | `warehouseId`, `batchBusinessDate` |
| RPT-07 | `warehouseId`, `locationCodePrefix`, `productId`, `unitType`, `storageCondition` |
| RPT-08 | `warehouseId`, `productId`, `dateFrom`, `dateTo` |
| RPT-09 | `warehouseId`, `correctionDateFrom`, `correctionDateTo` |
| RPT-10 | `stocktakeId`（または `buildingId` + `areaId`） |
| RPT-11 | `stocktakeId` |
| RPT-12 | `pickingInstructionId` |
| RPT-13 | `slipId` |
| RPT-14 | `warehouseId`, `plannedDateFrom`, `plannedDateTo`, `carrier` |
| RPT-15 | `warehouseId`, `asOfDate` |
| RPT-16 | `warehouseId`, `batchBusinessDate` |
| RPT-17 | `targetBusinessDate` |
| RPT-18 | `warehouseId`, `returnDateFrom`, `returnDateTo`, `returnType`, `partnerId` |

---

### SC-COM-005: 正常系: データ0件時のレポート出力

| 項目 | 内容 |
|------|------|
| シナリオID | SC-COM-005 |
| シナリオ名 | 正常系: 該当データが0件の場合に空レポートが生成される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。該当データが存在しない条件を指定 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | 該当データなしの条件で `format=json` リクエスト | HTTP 200 + 空配列 `[]` が返却される | レスポンスボディ |
| 2 | 該当データなしの条件で `format=csv` リクエスト | HTTP 200 + ヘッダー行のみのCSVが返却される | CSV行数 |
| 3 | 該当データなしの条件で `format=pdf` リクエスト | HTTP 200 + 「該当データがありません」メッセージを含むPDFが返却される | PDFテキスト抽出 |

---

### SC-COM-006: 正常系: 権限チェック（全ロール）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-COM-006 |
| シナリオ名 | 正常系: 全ロール（SYSTEM_ADMIN / WAREHOUSE_MANAGER / WAREHOUSE_STAFF / VIEWER）でアクセス可能 |
| 前提条件 | 各ロールのテストユーザーが存在 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | SYSTEM_ADMINロールでレポートAPIにリクエスト | HTTP 200 が返却される | ステータスコード |
| 2 | WAREHOUSE_MANAGERロールでレポートAPIにリクエスト | HTTP 200 が返却される | ステータスコード |
| 3 | WAREHOUSE_STAFFロールでレポートAPIにリクエスト | HTTP 200 が返却される | ステータスコード |
| 4 | VIEWERロールでレポートAPIにリクエスト | HTTP 200 が返却される | ステータスコード |
| 5 | 未認証（トークンなし）でレポートAPIにリクエスト | HTTP 401 Unauthorized | ステータスコード |

---

## 4. レポート固有テストシナリオ詳細

### SC-RPT01-001: RPT-01 検品差異行のハイライト表示（PDF）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-RPT01-001 |
| シナリオ名 | 正常系: 検品差異がある行がピンク背景・太字差異数で表示される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。入荷検品完了済みの伝票が存在し、一部明細に差異あり（`diffQuantityPcs != 0` または `diffQuantityCas != 0`） |
| テストデータ | 入荷伝票に明細4行: 差異なし2行、ケース差異あり1行（`diffQuantityCas = -1`）、バラ差異あり1行（`diffQuantityPcs = +6`） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | `GET /api/v1/reports/inbound-inspection?slipId={id}&format=json` | 4行のJSON配列が返却される | JSON件数 |
| 2 | レスポンスのdiffQuantityCas/diffQuantityPcsを確認 | 差異なし行は0、差異あり行は非0値が返却される | フィールド値 |
| 3 | `GET /api/v1/reports/inbound-inspection?slipId={id}&format=pdf` | PDFが生成される | PDFバイナリ |
| 4 | PDFテキスト抽出で差異数値を確認 | 差異数が正しく表示されている | PDFBox |
| 5 | 合計行の差異合計値を確認 | ケース差異合計・バラ差異合計が正しく算出されている | PDFテキスト抽出 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slip_lines | テストデータの `inspected_qty` と `planned_qty` の差分がレスポンスの差異値と一致すること |

---

### SC-RPT01-004: RPT-01 存在しない伝票IDの指定

| 項目 | 内容 |
|------|------|
| シナリオID | SC-RPT01-004 |
| シナリオ名 | 異常系: 存在しない入荷伝票IDを指定した場合に404エラーが返される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | `GET /api/v1/reports/inbound-inspection?slipId=999999` | HTTP 404 が返却される | ステータスコード |
| 2 | レスポンスボディを確認 | エラーコード `INBOUND_SLIP_NOT_FOUND` が含まれる | JSONフィールド |

---

### SC-RPT07-001: RPT-07 allocatedQty/availableQtyの表示

| 項目 | 内容 |
|------|------|
| シナリオID | SC-RPT07-001 |
| シナリオ名 | 正常系: 引当数量・有効在庫数が正しく計算・表示される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。在庫データに引当済みのものが含まれる（`allocatedQty > 0`） |
| テストデータ | 商品P-001: quantity=100, allocatedQty=20（availableQty=80）。商品P-002: quantity=30, allocatedQty=0（availableQty=30） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | `GET /api/v1/reports/inventory?warehouseId={id}&format=json` | 在庫データが返却される | JSON |
| 2 | P-001のレスポンスを確認 | `quantity=100`, `allocatedQty=20`, `availableQty=80` | フィールド値 |
| 3 | P-002のレスポンスを確認 | `quantity=30`, `allocatedQty=0`, `availableQty=30` | フィールド値 |
| 4 | `format=pdf` でPDFを生成 | 在庫数量・引当数量・有効在庫数の3列が正しく表示される | PDFテキスト抽出 |
| 5 | 商品コード別グルーピングを確認 | 商品コードごとにグループヘッダーと小計行が表示される | PDFテキスト抽出 |
| 6 | 合計行を確認 | 在庫数量合計=130、引当数量合計=20、有効在庫数合計=110 | PDFテキスト抽出 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inventories | `quantity` と `allocated_qty` がレスポンス値と一致すること |
| 2 | — | `availableQty = quantity - allocatedQty` がアプリ側で正しく計算されていること |

---

### SC-RPT10-001: RPT-10 PDF出力時の帳簿数量非表示（hideBookQty=true）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-RPT10-001 |
| シナリオ名 | 正常系: hideBookQty=trueの場合、PDF内に帳簿数量カラムが表示されない |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。棚卸対象データが存在 |
| テストデータ | 棚卸リスト対象の在庫データ3件（systemQuantityを持つ） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | `GET /api/v1/reports/stocktake-list?...&hideBookQty=true&format=json` | JSONレスポンスには `systemQuantity` フィールドが含まれる | フィールド存在 |
| 2 | `GET /api/v1/reports/stocktake-list?...&hideBookQty=true&format=pdf` | PDFが生成される | PDFバイナリ |
| 3 | PDFテキスト抽出で「帳簿数量」カラムヘッダーの有無を確認 | 「帳簿数」「systemQuantity」等のテキストが含まれない | PDFBox |
| 4 | PDFテキスト抽出で「実数（記入欄）」列を確認 | 空欄の枠線列が存在する | PDF構造 |

---

### SC-RPT10-002: RPT-10 PDF出力時も帳簿数量が非表示であること（hideBookQty未指定）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-RPT10-002 |
| シナリオ名 | 正常系: hideBookQty未指定の場合でも、PDFに帳簿数量が表示されない（設計書の仕様通り） |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。棚卸対象データが存在 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | `GET /api/v1/reports/stocktake-list?...&format=pdf`（hideBookQty省略） | PDFが生成される | PDFバイナリ |
| 2 | PDFテキスト抽出を確認 | 帳簿数量（systemQuantity）がPDFに含まれないこと | PDFBox |

> **根拠**: RPT-10設計書の特記事項に「帳簿数量（systemQuantity）はPDFに表示しない」「PDFテンプレートでは描画しない」と明記されている。hideBookQtyパラメータの有無にかかわらず、PDFでは常に帳簿数量は非表示である。JSON/CSV出力には `systemQuantity` フィールドが含まれる。

---

### SC-RPT14-001: RPT-14 伝票ヘッダー+商品明細のネスト構造表示

| 項目 | 内容 |
|------|------|
| シナリオID | SC-RPT14-001 |
| シナリオ名 | 正常系: 配送リストが伝票ヘッダー行+商品明細行の2段構成で正しく表示される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。出荷完了済み伝票が複数存在し、各伝票に複数商品明細あり |
| テストデータ | 伝票3件: OUT-00050（明細2行）、OUT-00051（明細2行）、OUT-00055（明細1行） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | `GET /api/v1/reports/delivery-list?...&format=json` | ネスト構造のJSONが返却される | JSON構造 |
| 2 | 各伝票オブジェクトの `lines[]` を確認 | 各伝票に紐づく商品明細が正しく含まれる | フィールド値 |
| 3 | `format=pdf` でPDFを生成 | PDFが生成される | PDFバイナリ |
| 4 | PDFテキスト抽出で伝票ヘッダー情報を確認 | 伝票番号・出荷先名・出荷予定日・ステータス・配送業者・送り状番号が表示される | PDFBox |
| 5 | PDFテキスト抽出で商品明細を確認 | 各伝票の下に商品コード・商品名・荷姿・数量が表示される | PDFBox |
| 6 | 伝票小計を確認 | 各伝票の数量合計が正しい（OUT-00050=23, OUT-00051=15, OUT-00055=2） | PDFテキスト抽出 |
| 7 | 総合計を確認 | 全伝票数量合計=40 | PDFテキスト抽出 |

---

### SC-RPT17-001: RPT-17 倉庫別セクション+全倉庫合計の表示

| 項目 | 内容 |
|------|------|
| シナリオID | SC-RPT17-001 |
| シナリオ名 | 正常系: 日次集計レポートが倉庫別セクションと全倉庫合計を正しく表示する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。2倉庫（東京DC・大阪DC）で日替処理が完了済み |
| テストデータ | 東京DC: 入荷12件/1,230個、出荷8件/870個、返品2件/150個、在庫5,600個、未入荷3件、未出荷1件。大阪DC: 入荷5件/520個、出荷3件/340個、返品1件/60個、在庫3,200個、未入荷0件、未出荷2件 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | `GET /api/v1/reports/daily-summary?targetBusinessDate={date}&format=json` | 2件の倉庫データ配列が返却される | JSON配列サイズ |
| 2 | 東京DCのサマリー値を確認 | 入荷12件/1,230個、出荷8件/870個、返品2件/150個、在庫5,600個、未入荷3件、未出荷1件 | フィールド値 |
| 3 | 大阪DCのサマリー値を確認 | 入荷5件/520個、出荷3件/340個、返品1件/60個、在庫3,200個、未入荷0件、未出荷2件 | フィールド値 |
| 4 | `format=pdf` でPDFを生成 | PDFが生成される | PDFバイナリ |
| 5 | PDFテキスト抽出で倉庫別セクションを確認 | 東京DC・大阪DCの各セクションが表示され、入荷実績・出荷実績・返品実績・在庫状況・未処理アラートの5セクションが含まれる | PDFBox |
| 6 | 全倉庫合計セクションを確認 | 入荷件数合計=17、入荷数量合計=1,750、出荷件数合計=11、出荷数量合計=1,210、返品件数合計=3、返品数量合計=210、在庫総数量合計=8,800、未入荷合計=3、未出荷合計=3 | PDFテキスト抽出 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_summaries | 対象営業日の入荷集計データと一致すること |
| 2 | outbound_summaries | 対象営業日の出荷集計データと一致すること |
| 3 | return_slips | 対象営業日の返品集計データと一致すること |
| 4 | inventory_snapshots | 対象営業日の在庫スナップショットと一致すること |
| 5 | unreceived_list_records | 対象営業日の未入荷件数と一致すること |
| 6 | unshipped_list_records | 対象営業日の未出荷件数と一致すること |

---

### SC-RPT17-004: RPT-17 日替処理未完了の営業日指定

| 項目 | 内容 |
|------|------|
| シナリオID | SC-RPT17-004 |
| シナリオ名 | 異常系: 日替処理が完了していない営業日を指定した場合に404エラーが返される |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。指定営業日の日替処理が未実行 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | `GET /api/v1/reports/daily-summary?targetBusinessDate={未処理日}` | HTTP 404 が返却される | ステータスコード |
| 2 | レスポンスボディを確認 | エラーコード `BATCH_EXECUTION_NOT_FOUND` が含まれる | JSONフィールド |

---

## 5. E2Eテストシナリオ（画面経由）

E2Eテストでは、画面のレポート出力ボタンからのダウンロードフローを検証する。

### SC-E2E-RPT-001: 画面からのレポートダウンロードフロー

| 項目 | 内容 |
|------|------|
| シナリオID | SC-E2E-RPT-001 |
| シナリオ名 | 正常系: 呼び出し元画面からレポートを出力しダウンロードできる |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。テストデータ登録済み |

**テストステップ（RPT-01の例: INB-003画面から呼び出し）:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-003（入荷予定詳細画面）を開く | 入荷伝票の詳細画面が表示される | URL確認 |
| 2 | 出力形式をPDFに選択する | PDFが選択される | UIフォーム値 |
| 3 | 「検品レポート」ボタンをクリック | ファイルダウンロードが開始される | ダウンロードイベント |
| 4 | ダウンロードされたファイルを確認 | PDFファイルが保存されている | ファイル存在チェック |
| 5 | PDFファイルの内容を確認 | 該当伝票の検品結果が正しく含まれている | PDFテキスト抽出 |

> **呼び出し元画面マッピング:**

| RPT# | 呼び出し元画面 | 画面パス |
|------|-------------|---------|
| RPT-01 | INB-003 入荷予定詳細 | `/inbound/slips/{id}` |
| RPT-03 | INB-001 入荷予定一覧 | `/inbound/slips` |
| RPT-04 | INB-006 入庫実績照会 | `/inbound/results` |
| RPT-05 | INB-001 入荷一覧 | `/inbound/slips` |
| RPT-06 | INB-001 入荷一覧 | `/inbound/slips` |
| RPT-07 | INV-001 在庫一覧 | `/inventory` |
| RPT-08 | INV-001 在庫一覧 | `/inventory` |
| RPT-09 | INV-004 在庫訂正登録 | `/inventory/corrections` |
| RPT-10 | INV-012 棚卸開始 / INV-013 棚卸実施 | `/inventory/stocktake/{id}` |
| RPT-11 | INV-014 棚卸確定 | `/inventory/stocktake/{id}/confirm` |
| RPT-12 | OUT-011 ピッキング指示一覧 | `/outbound/picking` |
| RPT-13 | OUT-021 出荷検品 | `/outbound/inspection/{id}` |
| RPT-14 | OUT-001 受注一覧 | `/outbound/orders` |
| RPT-15 | OUT-001 受注一覧 | `/outbound/orders` |
| RPT-16 | OUT-001 受注一覧 | `/outbound/orders` |
| RPT-17 | BAT-002 バッチ実行履歴一覧 | `/batch/history` |
| RPT-18 | RTN-001 返品登録 | `/returns` |

---

## 6. 共通エラーパターン

以下のエラーケースは全レポートAPIに共通して実施する。

| シナリオID | シナリオ名 | リクエスト例 | 期待結果 | 結合 | E2E |
|-----------|----------|------------|---------|:----:|:---:|
| SC-ERR-001 | 必須パラメータ未指定 | `GET /api/v1/reports/inbound-plan`（warehouseId省略） | 400 `VALIDATION_ERROR` | ○ | — |
| SC-ERR-002 | 日付形式不正 | `format=json&plannedDateFrom=2026/03/01` | 400 `VALIDATION_ERROR` | ○ | — |
| SC-ERR-003 | 未認証アクセス | Cookie/Authorizationヘッダーなし | 401 `UNAUTHORIZED` | ○ | — |
| SC-ERR-004 | 不正なformatパラメータ | `format=xlsx` | 400 `VALIDATION_ERROR` | ○ | — |
| SC-ERR-005 | 件数上限超過（10,000件超） | 期間を広く指定して10,000件を超過 | 400 `VALIDATION_ERROR`（期間絞り込みを促すメッセージ） | ○ | — |

---

## 7. テストデータ

### 7.1. 前提マスタデータ

| テーブル | テストデータ概要 |
|---------|---------------|
| warehouses | 東京DC（ID:1）、大阪DC（ID:2） |
| partners | 仕入先A（SP-001）、仕入先B（SP-002）、出荷先A、出荷先B |
| products | P-001〜P-005（ロット管理ON/OFF、期限管理ON/OFF混在） |
| locations | A-01-A-01〜A-02-B-01（複数エリア・棟） |
| users | SYSTEM_ADMIN/WAREHOUSE_MANAGER/WAREHOUSE_STAFF/VIEWERの各ロール |

### 7.2. トランザクションデータ

| テスト対象 | テストデータ概要 |
|-----------|---------------|
| 入荷検品（RPT-01） | 入荷伝票1件: 明細4行（差異なし2行、ケース差異あり1行、バラ差異あり1行） |
| 入荷予定（RPT-03） | 入荷伝票5件: PLANNED/INSPECTING/STORED/CANCELLED混在、仕入先2社 |
| 入庫実績（RPT-04） | 入庫完了伝票3件: 差異あり1件、返品紐づきあり1件 |
| 未入荷RT（RPT-05） | 遅延伝票3件: 遅延1日/5日/10日 |
| 未入荷確定（RPT-06） | 日替処理済みの未入荷スナップショットデータ3件 |
| 在庫一覧（RPT-07） | 在庫レコード5件: 引当あり2件、引当なし2件、有効在庫0件1件 |
| 在庫推移（RPT-08） | 在庫変動履歴10件: INBOUND/OUTBOUND/MOVE/CORRECTION/STOCKTAKE混在 |
| 在庫訂正（RPT-09） | 在庫訂正3件: 増加1件、減少2件 |
| 棚卸リスト（RPT-10） | 棚卸対象在庫10件: ロケーション3箇所 |
| 棚卸結果（RPT-11） | 棚卸確定済みデータ: 差異あり2件、差異なし2件、未入力1件 |
| ピッキング指示書（RPT-12） | ピッキング指示1件: 明細5行、ロケーション3箇所、受注2件 |
| 出荷検品（RPT-13） | 出荷伝票1件: 明細3行（差異なし2行、差異あり1行） |
| 配送リスト（RPT-14） | 出荷完了伝票3件: 各2明細、配送業者2社、送り状番号未設定1件 |
| 未出荷RT（RPT-15） | 未出荷伝票4件: 遅延1日/3日/5日/7日 |
| 未出荷確定（RPT-16） | 日替処理済みの未出荷スナップショットデータ5件 |
| 日次集計（RPT-17） | 2倉庫分の日替処理完了データ |
| 返品（RPT-18） | 返品伝票3件: 入荷返品2件、出荷返品1件 |

---

## 8. Playwrightコード例

### 共通ヘルパー: レポートダウンロード検証

```typescript
import { test, expect, Page } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

/**
 * レポートAPIからJSONを取得し基本検証を行う
 */
async function verifyJsonReport(page: Page, apiPath: string, params: Record<string, string>) {
  const query = new URLSearchParams({ ...params, format: 'json' }).toString();
  const response = await page.request.get(`${apiPath}?${query}`);

  expect(response.status()).toBe(200);
  expect(response.headers()['content-type']).toContain('application/json');

  const data = await response.json();
  expect(Array.isArray(data)).toBeTruthy();
  return data;
}

/**
 * レポートAPIからCSVを取得し基本検証を行う
 */
async function verifyCsvReport(page: Page, apiPath: string, params: Record<string, string>) {
  const query = new URLSearchParams({ ...params, format: 'csv' }).toString();
  const response = await page.request.get(`${apiPath}?${query}`);

  expect(response.status()).toBe(200);
  expect(response.headers()['content-type']).toContain('text/csv');
  expect(response.headers()['content-disposition']).toContain('attachment; filename=');

  const body = await response.body();
  // UTF-8 BOM check
  expect(body[0]).toBe(0xEF);
  expect(body[1]).toBe(0xBB);
  expect(body[2]).toBe(0xBF);

  return body.toString('utf-8');
}

/**
 * レポートAPIからPDFを取得し基本検証を行う
 */
async function verifyPdfReport(page: Page, apiPath: string, params: Record<string, string>) {
  const query = new URLSearchParams({ ...params, format: 'pdf' }).toString();
  const response = await page.request.get(`${apiPath}?${query}`);

  expect(response.status()).toBe(200);
  expect(response.headers()['content-type']).toContain('application/pdf');
  expect(response.headers()['content-disposition']).toContain('attachment; filename=');

  const body = await response.body();
  // PDF magic bytes: %PDF
  expect(body[0]).toBe(0x25); // %
  expect(body[1]).toBe(0x50); // P
  expect(body[2]).toBe(0x44); // D
  expect(body[3]).toBe(0x46); // F

  return body;
}
```

### SC-COM-001〜003 の共通テスト例（RPT-01）

```typescript
test.describe('RPT-01: 入荷検品レポート - 共通パターン', () => {
  const API_PATH = '/api/v1/reports/inbound-inspection';
  let slipId: string;

  test.beforeAll(async ({ request }) => {
    // テストデータのセットアップ（省略）
    slipId = '1'; // テストデータの入荷伝票ID
  });

  test('SC-COM-001: JSON形式出力', async ({ page }) => {
    await loginAs(page, 'WAREHOUSE_MANAGER');
    const data = await verifyJsonReport(page, API_PATH, { slipId });
    expect(data.length).toBeGreaterThan(0);
    expect(data[0]).toHaveProperty('slipNumber');
    expect(data[0]).toHaveProperty('productCode');
    expect(data[0]).toHaveProperty('diffQuantityCas');
    expect(data[0]).toHaveProperty('diffQuantityPcs');
  });

  test('SC-COM-002: CSV形式出力（UTF-8 BOM付き）', async ({ page }) => {
    await loginAs(page, 'WAREHOUSE_MANAGER');
    const csv = await verifyCsvReport(page, API_PATH, { slipId });
    const lines = csv.split('\n').filter(l => l.trim());
    expect(lines.length).toBeGreaterThan(1); // ヘッダー + データ行
    expect(lines[0]).toContain('伝票番号');
    expect(lines[0]).toContain('商品コード');
  });

  test('SC-COM-003: PDF形式出力', async ({ page }) => {
    await loginAs(page, 'WAREHOUSE_MANAGER');
    const pdf = await verifyPdfReport(page, API_PATH, { slipId });
    expect(pdf.length).toBeGreaterThan(0);
  });
});
```

### SC-RPT10-001 固有テスト例

```typescript
test('SC-RPT10-001: hideBookQty=true でPDF帳簿数量非表示', async ({ page }) => {
  await loginAs(page, 'WAREHOUSE_MANAGER');

  // JSON出力: systemQuantity フィールドが含まれることを確認
  const jsonData = await verifyJsonReport(page, '/api/v1/reports/stocktake-list', {
    stocktakeId: '1',
    hideBookQty: 'true',
  });
  expect(jsonData.length).toBeGreaterThan(0);
  expect(jsonData[0]).toHaveProperty('systemQuantity');

  // PDF出力: hideBookQty=true
  const pdfHidden = await verifyPdfReport(page, '/api/v1/reports/stocktake-list', {
    stocktakeId: '1',
    hideBookQty: 'true',
  });
  // PDFのテキスト抽出で帳簿数量のヘッダーが含まれないことを確認
  // ※ 実装時はPDFBoxを使用してテキスト抽出し検証する
  expect(pdfHidden.length).toBeGreaterThan(0);
});
```

### SC-RPT17-001 固有テスト例

```typescript
test('SC-RPT17-001: 倉庫別セクション+全倉庫合計', async ({ page }) => {
  await loginAs(page, 'WAREHOUSE_MANAGER');

  const data = await verifyJsonReport(page, '/api/v1/reports/daily-summary', {
    targetBusinessDate: '2026-03-14',
  });

  // 2倉庫分のデータ
  expect(data.length).toBe(2);

  // 東京DC
  const tokyo = data.find((d: any) => d.warehouseName === '東京DC');
  expect(tokyo).toBeDefined();
  expect(tokyo.inboundCount).toBe(12);
  expect(tokyo.inboundQuantityTotal).toBe(1230);
  expect(tokyo.outboundCount).toBe(8);
  expect(tokyo.outboundQuantityTotal).toBe(870);
  expect(tokyo.returnCount).toBe(2);
  expect(tokyo.returnQuantityTotal).toBe(150);
  expect(tokyo.inventoryQuantityTotal).toBe(5600);
  expect(tokyo.unreceivedCount).toBe(3);
  expect(tokyo.unshippedCount).toBe(1);

  // 大阪DC
  const osaka = data.find((d: any) => d.warehouseName === '大阪DC');
  expect(osaka).toBeDefined();
  expect(osaka.inboundCount).toBe(5);

  // PDF出力
  const pdf = await verifyPdfReport(page, '/api/v1/reports/daily-summary', {
    targetBusinessDate: '2026-03-14',
  });
  expect(pdf.length).toBeGreaterThan(0);
});
```

---

## 9. パフォーマンス要件

| 項目 | 基準値 |
|------|-------|
| JSON/CSV出力の応答時間 | 3秒以内（10,000件以下） |
| PDF出力の応答時間 | 10秒以内（10,000件以下） |
| 同時出力リクエスト | 5リクエスト並列でタイムアウトしないこと |
| PDFファイルサイズ | 10,000件で10MB以下 |
| クエリタイムアウト | 30秒（API設計書記載の上限値） |

---

## 10. テスト実施上の注意事項

1. **PDFの視覚的検証**: PDFの条件付き書式（背景色・太字・赤字等）はPDFテキスト抽出では検証困難なため、結合テストではHTMLテンプレートのレンダリング結果（CSS付きHTML）で代替検証する。E2EテストではPlaywrightのスクリーンショット比較を併用する
2. **テストデータの独立性**: 各レポートテストで使用するデータは他のテストに影響しないよう、テスト開始時にセットアップ・終了時にクリーンアップする
3. **タイムゾーン**: 出力日時はJSTで表示される。テスト環境のタイムゾーンを `Asia/Tokyo` に固定すること
4. **CSV文字化け検証**: CSVをExcelで開いた際にUTF-8 BOM付きにより日本語が正しく表示されることを手動確認する（自動化困難）
5. **RPT-05/RPT-15（リアルタイム版）のテスト**: リアルタイムデータを参照するため、テストデータのタイミングによって結果が変動する可能性がある。テスト実行前にデータ状態を確定すること
6. **RPT-06/RPT-16/RPT-17（確定版・日次集計）**: 日替処理（バッチ）の実行完了が前提条件。テストデータセットアップ時に日替処理を事前実行しておくこと
