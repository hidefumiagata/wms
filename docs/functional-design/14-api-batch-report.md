# 機能設計書 — API設計 バッチ管理・レポート（BAT / RPT）

## バッチ管理

---

### API-BAT-001 日替処理実行

#### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-BAT-001` |
| **API名** | 日替処理実行 |
| **メソッド** | `POST` |
| **パス** | `/api/v1/batch/daily-close` |
| **認証** | 要 |
| **対象ロール** | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| **概要** | 指定した営業日に対して日替処理を実行する。営業日更新→入荷集計→出荷集計→在庫集計→トランデータバックアップの5ステップを同期で順次実行し、実行結果を返す。 |
| **関連画面** | BAT-001（日替処理画面） |

---

#### 2. リクエスト仕様

##### リクエストボディ

```json
{
  "targetBusinessDate": "2026-03-14"
}
```

| フィールド名 | 型 | 必須 | バリデーション | 説明 |
|------------|-----|:----:|-------------|------|
| `targetBusinessDate` | String (date) | ○ | `yyyy-MM-dd` 形式 | 処理対象営業日 |

---

#### 3. レスポンス仕様

##### 成功レスポンス（200 OK）

```json
{
  "executionId": 45,
  "status": "SUCCESS",
  "targetBusinessDate": "2026-03-14",
  "startedAt": "2026-03-14T23:00:01+09:00",
  "completedAt": "2026-03-14T23:00:08+09:00",
  "steps": [
    { "step": 1, "name": "営業日更新",           "status": "SUCCESS" },
    { "step": 2, "name": "入荷実績集計",           "status": "SUCCESS" },
    { "step": 3, "name": "出荷実績集計",           "status": "SUCCESS" },
    { "step": 4, "name": "在庫集計",             "status": "SUCCESS" },
    { "step": 5, "name": "トランデータバックアップ", "status": "SUCCESS" }
  ],
  "unreceivedCount": 3,
  "unshippedCount": 1
}
```

失敗時（200 OK でステップ失敗が判明する場合も同形式）:

```json
{
  "executionId": 46,
  "status": "FAILED",
  "targetBusinessDate": "2026-03-15",
  "startedAt": "2026-03-15T23:00:01+09:00",
  "completedAt": "2026-03-15T23:00:04+09:00",
  "steps": [
    { "step": 1, "name": "営業日更新",           "status": "SUCCESS" },
    { "step": 2, "name": "入荷実績集計",           "status": "SUCCESS" },
    { "step": 3, "name": "出荷実績集計",           "status": "FAILED", "errorMessage": "集計クエリで例外が発生しました" },
    { "step": 4, "name": "在庫集計",             "status": "SKIPPED" },
    { "step": 5, "name": "トランデータバックアップ", "status": "SKIPPED" }
  ],
  "unreceivedCount": null,
  "unshippedCount": null
}
```

| フィールド名 | 型 | 説明 |
|------------|-----|------|
| `executionId` | Long | バッチ実行ログID（`batch_execution_logs.id`） |
| `status` | String | 全体ステータス（`SUCCESS` / `FAILED`）。本APIは同期実行のため `RUNNING` は返さない |
| `targetBusinessDate` | String (date) | 処理対象営業日 |
| `startedAt` | String (datetime) | 処理開始日時 |
| `completedAt` | String (datetime) | 処理完了日時 |
| `steps` | Array | 各ステップの実行結果 |
| `steps[].step` | Integer | ステップ番号（1〜5） |
| `steps[].name` | String | ステップ名 |
| `steps[].status` | String | ステップステータス（`SUCCESS` / `FAILED` / `SKIPPED`） |
| `steps[].errorMessage` | String | エラーメッセージ（FAILED 時のみ） |
| `unreceivedCount` | Integer | 未入荷件数（SUCCESS 時のみ。ステップ5完了後に `unreceived_list_records` から集計） |
| `unshippedCount` | Integer | 未出荷件数（SUCCESS 時のみ。ステップ5完了後に `unshipped_list_records` から集計） |

##### エラーレスポンス

| HTTPステータス | エラーコード | 発生条件 |
|-------------|------------|---------|
| `400 Bad Request` | `VALIDATION_ERROR` | `targetBusinessDate` が未指定または日付形式不正 |
| `401 Unauthorized` | `UNAUTHORIZED` | 未認証 |
| `403 Forbidden` | `FORBIDDEN` | 対象ロール以外 |
| `409 Conflict` | `BATCH_ALREADY_RUNNING` | 同一営業日に `SUCCESS` または `RUNNING` レコードが存在する |

---

#### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[入力バリデーション\ntargetBusinessDate 必須チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| CHECK_DUP{同一target_business_dateの\nバッチログ確認}

    CHECK_DUP -->|SUCCESS/RUNNING 存在| ERR_DUP[409 BATCH_ALREADY_RUNNING]
    CHECK_DUP -->|FAILED 存在| DEL_FAILED[FAILEDレコードを削除]
    CHECK_DUP -->|レコードなし| INSERT_LOG

    DEL_FAILED --> INSERT_LOG[batch_execution_logsに\nRUNNINGレコードをINSERT]

    INSERT_LOG --> STEP1[ステップ1: 営業日更新\nbusiness_date.current_business_date\n= targetBusinessDate]

    STEP1 -->|成功| STEP1_OK[step1_status = SUCCESS]
    STEP1 -->|失敗| STEP1_NG[step1_status = FAILED\nstep2〜5 = SKIPPED\nstatus = FAILED\ncompleted_at 更新]
    STEP1_NG --> RETURN_FAIL[200 OK レスポンス返却\nstatus=FAILED]

    STEP1_OK --> STEP2[ステップ2: 入荷実績集計\ninbound_summariesに当日入庫完了データを\n集計INSERT]
    STEP2 -->|成功| STEP2_OK[step2_status = SUCCESS]
    STEP2 -->|失敗| STEP2_NG[step2_status = FAILED\nstep3〜5 = SKIPPED\nstatus = FAILED\ncompleted_at 更新]
    STEP2_NG --> RETURN_FAIL

    STEP2_OK --> STEP3[ステップ3: 出荷実績集計\noutbound_summariesに当日出荷完了データを\n集計INSERT]
    STEP3 -->|成功| STEP3_OK[step3_status = SUCCESS]
    STEP3 -->|失敗| STEP3_NG[step3_status = FAILED\nstep4〜5 = SKIPPED\nstatus = FAILED\ncompleted_at 更新]
    STEP3_NG --> RETURN_FAIL

    STEP3_OK --> STEP4[ステップ4: 在庫集計\ninventory_snapshotsに\n在庫スナップショット集計INSERT]
    STEP4 -->|成功| STEP4_OK[step4_status = SUCCESS]
    STEP4 -->|失敗| STEP4_NG[step4_status = FAILED\nstep5 = SKIPPED\nstatus = FAILED\ncompleted_at 更新]
    STEP4_NG --> RETURN_FAIL

    STEP4_OK --> STEP5[ステップ5: トランデータバックアップ\n2ヶ月以上前の完了データを\nバックアップテーブルへコピー]
    STEP5 -->|成功| STEP5_OK[step5_status = SUCCESS\nstatus = SUCCESS\ncompleted_at 更新]
    STEP5 -->|失敗| STEP5_NG[step5_status = FAILED\nstatus = FAILED\ncompleted_at 更新]
    STEP5_NG --> RETURN_FAIL

    STEP5_OK --> COLLECT[未入荷件数・未出荷件数を集計\nunreceived_list_records\nunshipped_list_recordsをカウント]
    COLLECT --> RETURN_SUCCESS[200 OK レスポンス返却\nstatus=SUCCESS]
```

##### ビジネスルール

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | 同一 `targetBusinessDate` に `status=SUCCESS` または `status=RUNNING` のレコードが存在する場合は実行不可 | `BATCH_ALREADY_RUNNING` |
| 2 | 同一 `targetBusinessDate` に `status=FAILED` のレコードが存在する場合は、そのレコードを削除して再実行可能 | — |
| 3 | 各ステップはトランザクション境界が独立しており、失敗したステップのロールバックは当該ステップのみ適用 | — |
| 4 | ステップ失敗時は後続ステップをすべて `SKIPPED` にして処理を終了する（部分実行しない） | — |

##### 各ステップの処理詳細

| ステップ | 処理内容 | 対象テーブル |
|---------|---------|------------|
| ステップ1 | `business_date` テーブルの `current_business_date` を `targetBusinessDate` に更新。`updated_at`・`updated_by` も更新 | `business_date` |
| ステップ2 | `targetBusinessDate` に `status='STORED'`（入庫完了）となった入荷伝票を集計し `inbound_summaries` へ INSERT（倉庫別に件数・明細行数・数量合計） | `inbound_slips`, `inbound_slip_lines`, `inbound_summaries` |
| ステップ3 | `targetBusinessDate` に `status='SHIPPED'`（出荷完了）となった出荷伝票を集計し `outbound_summaries` へ INSERT（倉庫別に件数・明細行数・数量合計） | `outbound_slips`, `outbound_slip_lines`, `outbound_summaries` |
| ステップ4 | `targetBusinessDate` 末時点の `inventory` テーブルの在庫残高を `inventory_snapshots` へ INSERT（倉庫・商品・荷姿別の在庫スナップショット） | `inventory`, `inventory_snapshots` |
| ステップ5 | ① 2ヶ月以上前の入荷・出荷・在庫移動等の完了済みトランデータをバックアップテーブルへコピー（本テーブルからは削除しない）。② 未入荷リスト（`unreceived_list_records`）と未出荷リスト（`unshipped_list_records`）をバッチ営業日付きで生成（06-batch-processing.md「⑤ トランデータバックアップ＋未入荷・未出荷リスト生成」に準拠） | バックアップ対象テーブル, `inbound_slips`, `unreceived_list_records`, `outbound_slips`, `unshipped_list_records` |

---

#### 5. 補足事項

- **同期実行**: 本APIはHTTPリクエスト内で全5ステップを同期実行する。処理時間は通常数秒〜数十秒程度を想定。
- **フロントエンドのポーリング**: フロントエンドは実行中の進捗確認のため `GET /api/v1/batch/executions/{id}` を定期ポーリング（1〜2秒間隔）して画面を更新する。`status` が `SUCCESS` または `FAILED` になったらポーリングを終了する。
- **トランザクション**: 各ステップは独立したトランザクションで実行する。`batch_execution_logs` の `step{N}_status` 更新は各ステップの直後に行い、障害時にどのステップまで完了したかを確認できるようにする。
- **UNIQUE制約**: `batch_execution_logs` テーブルには `UNIQUE(target_business_date)` 制約を持つ。FAILEDレコード削除→新規INSERTの順序で二重実行防止を担保する。

---

### API-BAT-002 バッチ実行履歴一覧取得

#### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-BAT-002` |
| **API名** | バッチ実行履歴一覧取得 |
| **メソッド** | `GET` |
| **パス** | `/api/v1/batch/executions` |
| **認証** | 要 |
| **対象ロール** | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| **概要** | バッチ実行履歴をページング形式で返す。日付範囲・ステータス等で絞り込み可能。 |
| **関連画面** | BAT-001（日替処理画面）、BAT-002（バッチ実行履歴画面） |

---

#### 2. リクエスト仕様

##### クエリパラメータ

| パラメータ名 | 型 | 必須 | デフォルト | 説明 |
|------------|-----|:----:|----------|------|
| `executedDateFrom` | String (date) | — | — | 実行日（From） `yyyy-MM-dd` |
| `executedDateTo` | String (date) | — | — | 実行日（To） `yyyy-MM-dd` |
| `targetBusinessDate` | String (date) | — | — | 処理対象営業日 `yyyy-MM-dd` |
| `status` | String | — | — | ステータス絞り込み（`SUCCESS` / `FAILED` / `RUNNING`） |
| `page` | Integer | — | `0` | ページ番号（0始まり） |
| `size` | Integer | — | `20` | 1ページあたりの件数（上限100） |
| `sort` | String | — | `startedAt,desc` | ソート指定 |

---

#### 3. レスポンス仕様

##### 成功レスポンス（200 OK）

```json
{
  "content": [
    {
      "id": 45,
      "targetBusinessDate": "2026-03-14",
      "status": "SUCCESS",
      "step1Status": "SUCCESS",
      "step2Status": "SUCCESS",
      "step3Status": "SUCCESS",
      "step4Status": "SUCCESS",
      "step5Status": "SUCCESS",
      "errorMessage": null,
      "startedAt": "2026-03-14T23:00:01+09:00",
      "completedAt": "2026-03-14T23:00:08+09:00",
      "executedByName": "田中 太郎"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 45,
  "totalPages": 3
}
```

| フィールド名 | 型 | 説明 |
|------------|-----|------|
| `id` | Long | バッチ実行ログID |
| `targetBusinessDate` | String (date) | 処理対象営業日 |
| `status` | String | 全体ステータス（`SUCCESS` / `FAILED` / `RUNNING`） |
| `step1Status` 〜 `step5Status` | String | 各ステップのステータス（`SUCCESS` / `FAILED` / `SKIPPED`） |
| `errorMessage` | String | エラーメッセージ（FAILED 時のみ） |
| `startedAt` | String (datetime) | 処理開始日時 |
| `completedAt` | String (datetime) | 処理完了日時（RUNNING 中は null） |
| `executedByName` | String | 実行者氏名 |

##### エラーレスポンス

| HTTPステータス | エラーコード | 発生条件 |
|-------------|------------|---------|
| `400 Bad Request` | `VALIDATION_ERROR` | 日付形式不正、status 値不正 |
| `401 Unauthorized` | `UNAUTHORIZED` | 未認証 |
| `403 Forbidden` | `FORBIDDEN` | 対象ロール以外 |

---

#### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[クエリパラメータバリデーション\n日付形式・status値確認]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| QUERY[batch_execution_logs を\n条件でフィルタリング]
    QUERY --> JOIN[users テーブルと JOIN して\nexecuted_by → executedByName に変換]
    JOIN --> PAGE[ページング・ソート適用]
    PAGE --> RETURN[200 OK ページングリスト返却]
```

##### ビジネスルール

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `status` パラメータは `SUCCESS` / `FAILED` / `RUNNING` のいずれかのみ受け付ける。それ以外の値は 400 を返す | `VALIDATION_ERROR` |
| 2 | `executedDateFrom` / `executedDateTo` は `started_at` の日付部分で絞り込む | — |
| 3 | `RUNNING` ステータスのレコードが存在する場合、そのレコードも一覧に含める（フロントエンドのポーリング用） | — |

---

#### 5. 補足事項

- `executedDateFrom` / `executedDateTo` は `started_at` の日付部分で絞り込む。
- `RUNNING` ステータスのレコードが存在する場合、そのレコードも一覧に表示される（フロントエンドでのポーリング用）。
- 日替処理の実行頻度は1日1回程度のため、総件数は限定的。ページング上限（100件/ページ）は通常の利用で上限に達することはない。

---

### API-BAT-003 バッチ実行履歴詳細取得

#### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-BAT-003` |
| **API名** | バッチ実行履歴詳細取得 |
| **メソッド** | `GET` |
| **パス** | `/api/v1/batch/executions/{id}` |
| **認証** | 要 |
| **対象ロール** | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| **概要** | バッチ実行履歴の詳細を1件取得する。フロントエンドが実行中の進捗ポーリングに使用する。 |

---

#### 2. リクエスト仕様

##### パスパラメータ

| パラメータ名 | 型 | 必須 | 説明 |
|------------|-----|:----:|------|
| `id` | Long | ○ | バッチ実行ログID |

---

#### 3. レスポンス仕様

##### 成功レスポンス（200 OK）

API-BAT-002 の `content` 配列の1要素と同形式のオブジェクトを返す。

##### エラーレスポンス

| HTTPステータス | エラーコード | 発生条件 |
|-------------|------------|---------|
| `401 Unauthorized` | `UNAUTHORIZED` | 未認証 |
| `403 Forbidden` | `FORBIDDEN` | 対象ロール以外 |
| `404 Not Found` | `BATCH_EXECUTION_NOT_FOUND` | 指定 ID のレコードが存在しない |

---

#### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[パスパラメータバリデーション\nid 必須チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| FIND{batch_execution_logs\nid で存在確認}
    FIND -->|なし| ERR_404[404 BATCH_EXECUTION_NOT_FOUND]
    FIND -->|あり| JOIN[users テーブルと JOIN して\nexecuted_by → executedByName に変換]
    JOIN --> RETURN[200 OK 詳細オブジェクト返却]
```

##### ビジネスルール

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | 指定 `id` に対応するレコードが存在しない場合は 404 を返す | `BATCH_EXECUTION_NOT_FOUND` |
| 2 | `RUNNING` 状態のレコードも取得可能（フロントエンドのポーリング用途） | — |

---

#### 5. 補足事項

- 本APIはフロントエンドが日替処理実行後に1〜2秒間隔でポーリングする用途に使用する。
- `status` が `SUCCESS` または `FAILED` になった時点でポーリングを停止することをフロントエンドに推奨する。
- 実行中（`RUNNING`）のレコードは `completedAt` が `null` となる。

---

---

## レポート出力

---

### レポートAPI 共通仕様

#### format パラメータ

すべてのレポートAPIは `format` クエリパラメータで出力形式を切り替える。

| `format` 値 | 説明 |
|------------|------|
| `json`（デフォルト） | データ配列を JSON で返す |
| `csv` | CSV形式でファイルダウンロード |

#### JSON レスポンス

- `Content-Type: application/json; charset=UTF-8`
- レスポンスボディはデータオブジェクトの配列（`[]`）を直接返す。
- ページングなし（全件返却）。件数が多い場合はクエリパラメータで期間等を絞ること。

#### CSV レスポンス

| ヘッダー | 値 |
|---------|-----|
| `Content-Type` | `text/csv; charset=UTF-8` |
| `Content-Disposition` | `attachment; filename="{レポート識別子}_{YYYYMMDD}.csv"` |

- 文字コード: UTF-8（BOM付き ※Excelでの文字化け防止）
- 1行目: ヘッダー行（日本語カラム名）
- 2行目以降: データ行

#### 認証・認可

全レポートAPIで共通:

- **認証**: 要
- **対象ロール**: 全ロール（SYSTEM_ADMIN / WAREHOUSE_MANAGER / WAREHOUSE_STAFF / VIEWER）

#### エラーレスポンス（全レポート共通）

| HTTPステータス | エラーコード | 発生条件 |
|-------------|------------|---------|
| `400 Bad Request` | `VALIDATION_ERROR` | 必須パラメータ未指定、日付形式不正 |
| `401 Unauthorized` | `UNAUTHORIZED` | 未認証 |
| `403 Forbidden` | `FORBIDDEN` | 権限不足 |
| `404 Not Found` | 各API参照 | 指定リソースが存在しない |

#### パフォーマンス考慮（全レポート共通）

レポートAPIはページングなしで全件返却するため、大量データ時のパフォーマンスに注意が必要:

| 対策 | 内容 |
|------|------|
| **期間絞り込み** | 検索期間を必須またはデフォルト設定して過大なデータ取得を防ぐ |
| **件数上限** | 取得件数が上限（デフォルト: 10,000件）を超えた場合は `400 VALIDATION_ERROR` を返し、絞り込み条件の追加を促す |
| **クエリ最適化** | インデックスを活用した効率的なクエリを実装する |
| **タイムアウト** | レポートクエリのタイムアウトは30秒を上限とする |

---

### API-RPT-001 入荷検品レポート

#### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-RPT-001` |
| **API名** | 入荷検品レポート |
| **メソッド** | `GET` |
| **パス** | `/api/v1/reports/inbound-inspection` |
| **認証** | 要 |
| **対象ロール** | 全ロール（SYSTEM_ADMIN / WAREHOUSE_MANAGER / WAREHOUSE_STAFF / VIEWER） |
| **概要** | 指定した入荷伝票の検品結果レポートを出力する。予定数・検品数・差異を品目ごとに出力する。 |
| **関連画面** | INB-003（入荷検品画面） |

#### 2. リクエスト仕様

##### クエリパラメータ

| パラメータ名 | 型 | 必須 | 説明 |
|------------|-----|:----:|------|
| `slipId` | Long | ○ | 入荷伝票ID |
| `format` | String | — | `json`（デフォルト）/ `csv` |

#### 3. レスポンス仕様（JSON）

```json
[
  {
    "slipNumber": "INB-2026-00123",
    "supplierName": "テスト仕入先A",
    "plannedDate": "2026-03-14",
    "productCode": "P-001",
    "productName": "商品A",
    "casQuantity": 10,
    "plannedQuantityCas": 5,
    "inspectedQuantityCas": 5,
    "diffQuantityCas": 0,
    "plannedQuantityPcs": 50,
    "inspectedQuantityPcs": 50,
    "diffQuantityPcs": 0,
    "lotNumber": "LOT-001",
    "expiryDate": "2027-03-14"
  }
]
```

| フィールド名 | 型 | 説明 |
|------------|-----|------|
| `slipNumber` | String | 入荷伝票番号 |
| `supplierName` | String | 仕入先名 |
| `plannedDate` | String (date) | 入荷予定日 |
| `productCode` | String | 商品コード |
| `productName` | String | 商品名 |
| `casQuantity` | Integer | ケース入数 |
| `plannedQuantityCas` | Integer | 予定数（ケース） |
| `inspectedQuantityCas` | Integer | 検品数（ケース） |
| `diffQuantityCas` | Integer | 差異数（ケース）= 検品数 − 予定数 |
| `plannedQuantityPcs` | Integer | 予定数（バラ） |
| `inspectedQuantityPcs` | Integer | 検品数（バラ） |
| `diffQuantityPcs` | Integer | 差異数（バラ）= 検品数 − 予定数 |
| `lotNumber` | String | ロット番号 |
| `expiryDate` | String (date) | 期限日 |

#### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[slipId 必須チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| FIND_SLIP{入荷伝票 存在確認}
    FIND_SLIP -->|なし| ERR_404[404 INBOUND_SLIP_NOT_FOUND]
    FIND_SLIP -->|あり| QUERY[inbound_slip_lines から\n検品結果・商品情報を結合して取得]
    QUERY --> FORMAT{format?}
    FORMAT -->|json| RES_JSON[200 OK JSON配列]
    FORMAT -->|csv| RES_CSV[200 OK CSVダウンロード\nfilename: inbound_inspection_YYYYMMDD.csv]
```

**ビジネスルール**:

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `slipId` に対応する入荷伝票が存在しない場合は 404 を返す | `INBOUND_SLIP_NOT_FOUND` |

#### 5. 補足事項

- 本APIは1伝票の明細全件を返すため、ページングなし。1伝票あたりの明細数は通常数件〜数十件程度のため、パフォーマンス上の問題は発生しにくい。
- CSV出力時のファイル名: `inbound_inspection_{YYYYMMDD}.csv`（YYYYMMDD はリクエスト処理日）
- CSV出力時のヘッダー行（日本語）: `伝票番号,仕入先名,入荷予定日,商品コード,商品名,ケース入数,予定数(ケース),検品数(ケース),差異(ケース),予定数(バラ),検品数(バラ),差異(バラ),ロット番号,期限日`

---

### API-RPT-003 入荷予定レポート

#### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-RPT-003` |
| **API名** | 入荷予定レポート |
| **メソッド** | `GET` |
| **パス** | `/api/v1/reports/inbound-plan` |
| **認証** | 要 |
| **対象ロール** | 全ロール（SYSTEM_ADMIN / WAREHOUSE_MANAGER / WAREHOUSE_STAFF / VIEWER） |
| **概要** | 指定倉庫・期間の入荷予定一覧を出力する。 |
| **関連画面** | INB-001（入荷予定一覧画面）、RPT-001（レポート画面） |

#### 2. リクエスト仕様

##### クエリパラメータ

| パラメータ名 | 型 | 必須 | デフォルト | 説明 |
|------------|-----|:----:|----------|------|
| `warehouseId` | Long | ○ | — | 倉庫ID |
| `plannedDateFrom` | String (date) | — | — | 入荷予定日（From） |
| `plannedDateTo` | String (date) | — | — | 入荷予定日（To） |
| `status` | String | — | — | ステータス絞り込み |
| `format` | String | — | `json` | `json` / `csv` |

#### 3. レスポンス仕様（JSON）

```json
[
  {
    "slipNumber": "INB-2026-00124",
    "supplierName": "テスト仕入先A",
    "plannedDate": "2026-03-15",
    "productCode": "P-002",
    "productName": "商品B",
    "plannedQuantityCas": 10,
    "plannedQuantityPcs": 100,
    "status": "PLANNED",
    "statusLabel": "入荷予定"
  }
]
```

| フィールド名 | 型 | 説明 |
|------------|-----|------|
| `slipNumber` | String | 入荷伝票番号 |
| `supplierName` | String | 仕入先名 |
| `plannedDate` | String (date) | 入荷予定日 |
| `productCode` | String | 商品コード |
| `productName` | String | 商品名 |
| `plannedQuantityCas` | Integer | 予定数（ケース） |
| `plannedQuantityPcs` | Integer | 予定数（バラ） |
| `status` | String | ステータスコード |
| `statusLabel` | String | ステータス表示名 |

#### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[warehouseId 必須チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| FIND_WH{倉庫 存在確認}
    FIND_WH -->|なし| ERR_404[404 WAREHOUSE_NOT_FOUND]
    FIND_WH -->|あり| QUERY[inbound_slips + inbound_slip_lines\n条件でフィルタリング・集計]
    QUERY --> FORMAT{format?}
    FORMAT -->|json| RES_JSON[200 OK JSON配列]
    FORMAT -->|csv| RES_CSV[200 OK CSVダウンロード\nfilename: inbound_plan_YYYYMMDD.csv]
```

**ビジネスルール**:

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `warehouseId` に対応する倉庫が存在しない場合は 404 を返す | `WAREHOUSE_NOT_FOUND` |
| 2 | 取得件数が10,000件を超える場合は 400 を返し、期間絞り込みを促す | `VALIDATION_ERROR` |

#### 5. 補足事項

- 期間指定なしの場合は全期間が対象となるため、大量データになりやすい。`plannedDateFrom` / `plannedDateTo` で期間を絞り込むことを推奨する。
- CSV出力時のファイル名: `inbound_plan_{YYYYMMDD}.csv`
- CSV出力時のヘッダー行（日本語）: `伝票番号,仕入先名,入荷予定日,商品コード,商品名,予定数(ケース),予定数(バラ),ステータス`

---

### API-RPT-004 入庫実績レポート

#### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-RPT-004` |
| **API名** | 入庫実績レポート |
| **メソッド** | `GET` |
| **パス** | `/api/v1/reports/inbound-result` |
| **認証** | 要 |
| **対象ロール** | 全ロール（SYSTEM_ADMIN / WAREHOUSE_MANAGER / WAREHOUSE_STAFF / VIEWER） |
| **概要** | 指定倉庫・期間の入庫実績一覧を出力する。 |
| **関連画面** | INB-004（入庫実績照会画面）、RPT-001（レポート画面） |

#### 2. リクエスト仕様

##### クエリパラメータ

| パラメータ名 | 型 | 必須 | デフォルト | 説明 |
|------------|-----|:----:|----------|------|
| `warehouseId` | Long | ○ | — | 倉庫ID |
| `storedDateFrom` | String (date) | — | — | 入庫日（From） |
| `storedDateTo` | String (date) | — | — | 入庫日（To） |
| `partnerId` | Long | — | — | 仕入先ID |
| `format` | String | — | `json` | `json` / `csv` |

#### 3. レスポンス仕様（JSON）

```json
[
  {
    "slipNumber": "INB-2026-00120",
    "storedDate": "2026-03-10",
    "supplierName": "テスト仕入先A",
    "productCode": "P-001",
    "productName": "商品A",
    "plannedQuantityCas": 10,
    "inspectedQuantityCas": 10,
    "diffQuantityCas": 0,
    "storedLocationCode": "A-01-001"
  }
]
```

| フィールド名 | 型 | 説明 |
|------------|-----|------|
| `slipNumber` | String | 入荷伝票番号 |
| `storedDate` | String (date) | 入庫日 |
| `supplierName` | String | 仕入先名 |
| `productCode` | String | 商品コード |
| `productName` | String | 商品名 |
| `plannedQuantityCas` | Integer | 予定数（ケース） |
| `inspectedQuantityCas` | Integer | 検品数（ケース） |
| `diffQuantityCas` | Integer | 差異数（ケース） |
| `storedLocationCode` | String | 格納ロケーションコード |

#### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[warehouseId 必須チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| FIND_WH{倉庫 存在確認}
    FIND_WH -->|なし| ERR_404[404 WAREHOUSE_NOT_FOUND]
    FIND_WH -->|あり| QUERY[status='STORED' のデータを\nパートナー・日付でフィルタリング]
    QUERY --> FORMAT{format?}
    FORMAT -->|json| RES_JSON[200 OK JSON配列]
    FORMAT -->|csv| RES_CSV[200 OK CSVダウンロード\nfilename: inbound_result_YYYYMMDD.csv]
```

**ビジネスルール**:

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `warehouseId` に対応する倉庫が存在しない場合は 404 を返す | `WAREHOUSE_NOT_FOUND` |
| 2 | `status='STORED'`（入庫完了）のレコードのみを対象とする | — |
| 3 | 取得件数が10,000件を超える場合は 400 を返し、期間絞り込みを促す | `VALIDATION_ERROR` |

#### 5. 補足事項

- 入庫日（`storedDateFrom` / `storedDateTo`）は `inbound_slips.updated_at` の日付部分で絞り込む。
- CSV出力時のファイル名: `inbound_result_{YYYYMMDD}.csv`
- CSV出力時のヘッダー行（日本語）: `伝票番号,入庫日,仕入先名,商品コード,商品名,予定数(ケース),検品数(ケース),差異(ケース),格納ロケーション`

---

### API-RPT-005 未入荷リスト（リアルタイム）

#### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-RPT-005` |
| **API名** | 未入荷リスト（リアルタイム） |
| **メソッド** | `GET` |
| **パス** | `/api/v1/reports/unreceived-realtime` |
| **認証** | 要 |
| **対象ロール** | 全ロール（SYSTEM_ADMIN / WAREHOUSE_MANAGER / WAREHOUSE_STAFF / VIEWER） |
| **概要** | 指定日時点でまだ入荷していない伝票の一覧をリアルタイムで返す。集計バッチを介さずに `inbound_slips` を直接参照する。 |
| **関連画面** | RPT-001（レポート画面） |

#### 2. リクエスト仕様

##### クエリパラメータ

| パラメータ名 | 型 | 必須 | デフォルト | 説明 |
|------------|-----|:----:|----------|------|
| `warehouseId` | Long | ○ | — | 倉庫ID |
| `asOfDate` | String (date) | — | 現在の営業日 | 基準日（この日以前の予定日で未入荷のもの） |
| `format` | String | — | `json` | `json` / `csv` |

#### 3. レスポンス仕様（JSON）

```json
[
  {
    "slipNumber": "INB-2026-00115",
    "supplierName": "テスト仕入先B",
    "plannedDate": "2026-03-12",
    "productCode": "P-003",
    "productName": "商品C",
    "plannedQuantityCas": 5,
    "status": "INSPECTED",
    "statusLabel": "検品済",
    "delayDays": 2
  }
]
```

| フィールド名 | 型 | 説明 |
|------------|-----|------|
| `slipNumber` | String | 入荷伝票番号 |
| `supplierName` | String | 仕入先名 |
| `plannedDate` | String (date) | 入荷予定日 |
| `productCode` | String | 商品コード |
| `productName` | String | 商品名 |
| `plannedQuantityCas` | Integer | 予定数（ケース） |
| `status` | String | 現在のステータスコード |
| `statusLabel` | String | ステータス表示名 |
| `delayDays` | Integer | 遅延日数（asOfDate − plannedDate） |

#### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[warehouseId 必須チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| DEFAULT_DATE{asOfDate 指定あり?}
    DEFAULT_DATE -->|なし| GET_BD[business_date から\ncurrent_business_date を取得]
    DEFAULT_DATE -->|あり| QUERY
    GET_BD --> QUERY[inbound_slips から\nstatus NOT IN 'STORED','CANCELLED'\nかつ planned_date <= asOfDate を取得]
    QUERY --> FORMAT{format?}
    FORMAT -->|json| RES_JSON[200 OK JSON配列]
    FORMAT -->|csv| RES_CSV[200 OK CSVダウンロード\nfilename: unreceived_realtime_YYYYMMDD.csv]
```

**ビジネスルール**:

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `warehouseId` に対応する倉庫が存在しない場合は 404 を返す | `WAREHOUSE_NOT_FOUND` |
| 2 | `asOfDate` が未指定の場合は現在の営業日（`business_date.current_business_date`）を使用する | — |
| 3 | `STORED`（入庫完了）・`CANCELLED`（キャンセル）のステータスは除外する | — |

#### 5. 補足事項

- 本APIはリアルタイムで `inbound_slips` テーブルを直接参照するため、最新状態を反映する。
- `asOfDate` を現在営業日と同日にすると当日業務の進捗確認に使用できる。
- 大量の未入荷が蓄積している場合は件数が多くなる可能性があるため、件数上限（10,000件）を設ける。
- CSV出力時のファイル名: `unreceived_realtime_{YYYYMMDD}.csv`
- CSV出力時のヘッダー行（日本語）: `伝票番号,仕入先名,入荷予定日,商品コード,商品名,予定数(ケース),ステータス,遅延日数`

---

### API-RPT-006 未入荷リスト（確定）

#### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-RPT-006` |
| **API名** | 未入荷リスト（確定） |
| **メソッド** | `GET` |
| **パス** | `/api/v1/reports/unreceived-confirmed` |
| **認証** | 要 |
| **対象ロール** | 全ロール（SYSTEM_ADMIN / WAREHOUSE_MANAGER / WAREHOUSE_STAFF / VIEWER） |
| **概要** | 日替処理で確定した指定営業日の未入荷リストを返す。`unreceived_list_records` テーブルから取得するため、営業日末時点の確定データとなる。 |
| **関連画面** | RPT-001（レポート画面） |

#### 2. リクエスト仕様

##### クエリパラメータ

| パラメータ名 | 型 | 必須 | 説明 |
|------------|-----|:----:|------|
| `warehouseId` | Long | ○ | 倉庫ID |
| `batchBusinessDate` | String (date) | ○ | バッチ処理営業日（`unreceived_list_records.batch_business_date`） |
| `format` | String | — | `json`（デフォルト）/ `csv` |

#### 3. レスポンス仕様（JSON）

```json
[
  {
    "batchBusinessDate": "2026-03-14",
    "slipNumber": "INB-2026-00115",
    "supplierName": "テスト仕入先B",
    "plannedDate": "2026-03-12",
    "productCode": "P-003",
    "productName": "商品C",
    "plannedQuantityCas": 5,
    "statusAtBatch": "INSPECTED"
  }
]
```

| フィールド名 | 型 | 説明 |
|------------|-----|------|
| `batchBusinessDate` | String (date) | バッチ処理営業日 |
| `slipNumber` | String | 入荷伝票番号 |
| `supplierName` | String | 仕入先名 |
| `plannedDate` | String (date) | 入荷予定日 |
| `productCode` | String | 商品コード |
| `productName` | String | 商品名 |
| `plannedQuantityCas` | Integer | 予定数（ケース） |
| `statusAtBatch` | String | バッチ処理時点のステータスコード |

#### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[warehouseId・batchBusinessDate 必須チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| QUERY[unreceived_list_records から\nbatch_business_date + warehouse_id で取得]
    QUERY --> FORMAT{format?}
    FORMAT -->|json| RES_JSON[200 OK JSON配列]
    FORMAT -->|csv| RES_CSV[200 OK CSVダウンロード\nfilename: unreceived_confirmed_YYYYMMDD.csv]
```

**ビジネスルール**:

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `warehouseId` に対応する倉庫が存在しない場合は 404 を返す | `WAREHOUSE_NOT_FOUND` |
| 2 | 指定 `batchBusinessDate` の日替処理が実行されていない場合は空配列（`[]`）を返す（404 ではない） | — |

#### 5. 補足事項

- 本APIは日替処理で生成された `unreceived_list_records` テーブルの確定データを参照する。リアルタイム版（API-RPT-005）とは異なり、バッチ処理時点のスナップショットを返す。
- 指定した `batchBusinessDate` に日替処理が実行されていない場合は空配列を返す。
- `warehouseId` を受け取った場合、`warehouses` テーブルから対応する `warehouse_code` を取得し、`unreceived_list_records.warehouse_code` カラムで絞り込む（`unreceived_list_records` テーブルには `warehouse_id` カラムが存在せず `warehouse_code` カラムのみのため）。
- CSV出力時のファイル名: `unreceived_confirmed_{YYYYMMDD}.csv`
- CSV出力時のヘッダー行（日本語）: `バッチ処理営業日,伝票番号,仕入先名,入荷予定日,商品コード,商品名,予定数(ケース),バッチ時点ステータス`

---

### API-RPT-007 在庫一覧レポート

#### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-RPT-007` |
| **API名** | 在庫一覧レポート |
| **メソッド** | `GET` |
| **パス** | `/api/v1/reports/inventory` |
| **認証** | 要 |
| **対象ロール** | 全ロール（SYSTEM_ADMIN / WAREHOUSE_MANAGER / WAREHOUSE_STAFF / VIEWER） |
| **概要** | 指定倉庫の現在在庫を一覧で出力する。ロケーション・商品・荷姿単位。 |
| **関連画面** | INV-001（在庫一覧画面）、RPT-001（レポート画面） |

#### 2. リクエスト仕様

##### クエリパラメータ

| パラメータ名 | 型 | 必須 | デフォルト | 説明 |
|------------|-----|:----:|----------|------|
| `warehouseId` | Long | ○ | — | 倉庫ID |
| `locationCodePrefix` | String | — | — | ロケーションコードの前方一致 |
| `productId` | Long | — | — | 商品ID |
| `unitType` | String | — | — | 荷姿種別（`CASE` / `BALL` / `PIECE`） |
| `storageCondition` | String | — | — | 保管条件（`NORMAL` / `REFRIGERATED` / `FROZEN`）。商品マスタの保管条件コードに準拠 |
| `format` | String | — | `json` | `json` / `csv` |

#### 3. レスポンス仕様（JSON）

```json
[
  {
    "locationCode": "A-01-001",
    "buildingName": "1号棟",
    "areaName": "保管エリアA",
    "productCode": "P-001",
    "productName": "商品A",
    "unitType": "CAS",
    "quantity": 10,
    "lotNumber": "LOT-001",
    "expiryDate": "2027-03-14"
  }
]
```

| フィールド名 | 型 | 説明 |
|------------|-----|------|
| `locationCode` | String | ロケーションコード |
| `buildingName` | String | 棟名 |
| `areaName` | String | エリア名 |
| `productCode` | String | 商品コード |
| `productName` | String | 商品名 |
| `unitType` | String | 荷姿種別（`CAS` / `PCS`） |
| `quantity` | Integer | 在庫数量 |
| `lotNumber` | String | ロット番号 |
| `expiryDate` | String (date) | 期限日 |

#### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[warehouseId 必須チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| QUERY[inventory テーブルから\n条件でフィルタリング\n（warehouseId, locationCodePrefix, productId,\nunitType, storageCondition）\nロケーション・商品情報を JOIN]
    QUERY --> FORMAT{format?}
    FORMAT -->|json| RES_JSON[200 OK JSON配列]
    FORMAT -->|csv| RES_CSV[200 OK CSVダウンロード\nfilename: inventory_YYYYMMDD.csv]
```

**ビジネスルール**:

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `warehouseId` に対応する倉庫が存在しない場合は 404 を返す | `WAREHOUSE_NOT_FOUND` |
| 2 | 在庫数量が0のレコードは除外する（`quantity > 0` のみ対象） | — |
| 3 | 取得件数が10,000件を超える場合は 400 を返し、絞り込み条件の追加を促す | `VALIDATION_ERROR` |

#### 5. 補足事項

- 現時点の `inventory` テーブルをリアルタイムで参照するため、在庫操作が多い時間帯はクエリ時間が延びる可能性がある。
- ロケーションコード昇順でソートして返す。
- CSV出力時のファイル名: `inventory_{YYYYMMDD}.csv`
- CSV出力時のヘッダー行（日本語）: `ロケーションコード,棟名,エリア名,商品コード,商品名,荷姿,在庫数量,ロット番号,期限日`

---

### API-RPT-008 在庫推移レポート

#### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-RPT-008` |
| **API名** | 在庫推移レポート |
| **メソッド** | `GET` |
| **パス** | `/api/v1/reports/inventory-transition` |
| **認証** | 要 |
| **対象ロール** | 全ロール（SYSTEM_ADMIN / WAREHOUSE_MANAGER / WAREHOUSE_STAFF / VIEWER） |
| **概要** | 指定倉庫・商品の在庫移動履歴を日付順に出力する。`inventory_movements` から集計し、入出庫・移動・訂正等の変動要因を含む。 |
| **関連画面** | RPT-001（レポート画面） |

#### 2. リクエスト仕様

##### クエリパラメータ

| パラメータ名 | 型 | 必須 | デフォルト | 説明 |
|------------|-----|:----:|----------|------|
| `warehouseId` | Long | ○ | — | 倉庫ID |
| `productId` | Long | ○ | — | 商品ID |
| `dateFrom` | String (date) | — | 当月1日 | 対象期間（From） |
| `dateTo` | String (date) | — | 本日 | 対象期間（To） |
| `format` | String | — | `json` | `json` / `csv` |

#### 3. レスポンス仕様（JSON）

```json
[
  {
    "movementDate": "2026-03-10",
    "movementType": "INBOUND",
    "movementTypeLabel": "入庫",
    "locationCode": "A-01-001",
    "unitType": "CAS",
    "quantityBefore": 5,
    "quantityChange": 10,
    "quantityAfter": 15,
    "referenceNumber": "INB-2026-00120",
    "lotNumber": "LOT-001"
  }
]
```

| フィールド名 | 型 | 説明 |
|------------|-----|------|
| `movementDate` | String (date) | 在庫変動日 |
| `movementType` | String | 変動種別コード（INBOUND / OUTBOUND / MOVE / BREAKDOWN / CORRECTION / STOCKTAKE） |
| `movementTypeLabel` | String | 変動種別表示名 |
| `locationCode` | String | ロケーションコード |
| `unitType` | String | 荷姿種別 |
| `quantityBefore` | Integer | 変動前在庫数 |
| `quantityChange` | Integer | 変動数（増加は正、減少は負） |
| `quantityAfter` | Integer | 変動後在庫数 |
| `referenceNumber` | String | 参照番号（入荷伝票番号・出荷伝票番号等） |
| `lotNumber` | String | ロット番号 |

#### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[warehouseId・productId 必須チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| FIND_PRD{商品 存在確認}
    FIND_PRD -->|なし| ERR_404[404 PRODUCT_NOT_FOUND]
    FIND_PRD -->|あり| QUERY[inventory_movements から\n倉庫・商品・日付範囲でフィルタリング\n日付昇順でソート]
    QUERY --> FORMAT{format?}
    FORMAT -->|json| RES_JSON[200 OK JSON配列]
    FORMAT -->|csv| RES_CSV[200 OK CSVダウンロード\nfilename: inventory_transition_YYYYMMDD.csv]
```

**ビジネスルール**:

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `warehouseId` に対応する倉庫が存在しない場合は 404 を返す | `WAREHOUSE_NOT_FOUND` |
| 2 | `productId` に対応する商品が存在しない場合は 404 を返す | `PRODUCT_NOT_FOUND` |
| 3 | `dateFrom` が未指定の場合は当月1日、`dateTo` が未指定の場合は本日（営業日）をデフォルトとする | — |
| 4 | 取得件数が10,000件を超える場合は 400 を返し、期間絞り込みを促す | `VALIDATION_ERROR` |

#### 5. 補足事項

- 在庫移動履歴が多い商品（入出庫頻度が高い）は期間を短く絞り込むことを推奨する。
- 古いデータはバックアップテーブルに移行されている可能性があるため、2ヶ月以上前のデータは参照できない場合がある（バックアップ対象は完了済みデータのみ）。
- CSV出力時のファイル名: `inventory_transition_{YYYYMMDD}.csv`
- CSV出力時のヘッダー行（日本語）: `変動日,変動種別,ロケーションコード,荷姿,変動前数量,変動数,変動後数量,参照番号,ロット番号`

---

### API-RPT-009 在庫訂正一覧

#### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-RPT-009` |
| **API名** | 在庫訂正一覧 |
| **メソッド** | `GET` |
| **パス** | `/api/v1/reports/inventory-correction` |
| **認証** | 要 |
| **対象ロール** | 全ロール（SYSTEM_ADMIN / WAREHOUSE_MANAGER / WAREHOUSE_STAFF / VIEWER） |
| **概要** | 指定倉庫・期間の在庫訂正履歴一覧を出力する。`inventory_movements` テーブルから `movement_type = 'CORRECTION'` のレコードを抽出する。 |
| **関連画面** | INV-003（在庫訂正画面）、RPT-001（レポート画面） |

#### 2. リクエスト仕様

##### クエリパラメータ

| パラメータ名 | 型 | 必須 | デフォルト | 説明 |
|------------|-----|:----:|----------|------|
| `warehouseId` | Long | ○ | — | 倉庫ID |
| `correctionDateFrom` | String (date) | — | 当月1日 | 訂正日（From） |
| `correctionDateTo` | String (date) | — | 本日 | 訂正日（To） |
| `format` | String | — | `json` | `json` / `csv` |

#### 3. レスポンス仕様（JSON）

```json
[
  {
    "correctionDate": "2026-03-10",
    "locationCode": "A-01-001",
    "productCode": "P-001",
    "productName": "商品A",
    "unitType": "CAS",
    "quantityBefore": 10,
    "quantityAfter": 9,
    "quantityChange": -1,
    "reason": "棚卸差異調整",
    "operatorName": "田中 太郎"
  }
]
```

| フィールド名 | 型 | 説明 |
|------------|-----|------|
| `correctionDate` | String (date) | 訂正日 |
| `locationCode` | String | ロケーションコード |
| `productCode` | String | 商品コード |
| `productName` | String | 商品名 |
| `unitType` | String | 荷姿種別 |
| `quantityBefore` | Integer | 訂正前数量 |
| `quantityAfter` | Integer | 訂正後数量 |
| `quantityChange` | Integer | 変動数（訂正後 − 訂正前） |
| `reason` | String | 訂正理由 |
| `operatorName` | String | 実施者氏名 |

#### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[warehouseId 必須チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| QUERY[inventory_movements から\nmovement_type='CORRECTION'\nかつ倉庫・日付でフィルタリング]
    QUERY --> FORMAT{format?}
    FORMAT -->|json| RES_JSON[200 OK JSON配列]
    FORMAT -->|csv| RES_CSV[200 OK CSVダウンロード\nfilename: inventory_correction_YYYYMMDD.csv]
```

**ビジネスルール**:

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `warehouseId` に対応する倉庫が存在しない場合は 404 を返す | `WAREHOUSE_NOT_FOUND` |
| 2 | `movement_type = 'CORRECTION'` のレコードのみを対象とする | — |
| 3 | 取得件数が10,000件を超える場合は 400 を返し、期間絞り込みを促す | `VALIDATION_ERROR` |

#### 5. 補足事項

- 在庫訂正は通常少頻度の操作のため、大量データになることは少ない。
- 訂正日は `inventory_movements.executed_at` の日付部分で絞り込む。
- CSV出力時のファイル名: `inventory_correction_{YYYYMMDD}.csv`
- CSV出力時のヘッダー行（日本語）: `訂正日,ロケーションコード,商品コード,商品名,荷姿,訂正前数量,訂正後数量,変動数,訂正理由,実施者`

---

### API-RPT-010 棚卸リスト

#### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-RPT-010` |
| **API名** | 棚卸リスト |
| **メソッド** | `GET` |
| **パス** | `/api/v1/reports/stocktake-list` |
| **認証** | 要 |
| **対象ロール** | 全ロール（SYSTEM_ADMIN / WAREHOUSE_MANAGER / WAREHOUSE_STAFF / VIEWER） |
| **概要** | 棚卸作業用のリストを出力する。棚卸ID指定時は棚卸対象の在庫データを、buildingId+areaId指定時は棚卸開始前プレビューとして現在在庫を出力する。実数入力欄は空欄。 |
| **関連画面** | INV-STK-001（棚卸画面）、RPT-001（レポート画面） |

#### 2. リクエスト仕様

##### クエリパラメータ

| パラメータ名 | 型 | 必須 | 説明 |
|------------|-----|:----:|------|
| `stocktakeId` | Long | ※1 | 棚卸ID（棚卸開始後） |
| `buildingId` | Long | ※1 | 棟ID（プレビュー用） |
| `areaId` | Long | — | エリアID（プレビュー用・絞り込み） |
| `format` | String | — | `json`（デフォルト）/ `csv` |

> ※1: `stocktakeId` または `buildingId` のどちらか一方が必須

#### 3. レスポンス仕様（JSON）

```json
[
  {
    "locationCode": "A-01-001",
    "areaName": "保管エリアA",
    "productCode": "P-001",
    "productName": "商品A",
    "unitType": "CAS",
    "systemQuantity": 10,
    "actualQuantity": null,
    "lotNumber": "LOT-001",
    "expiryDate": "2027-03-14"
  }
]
```

| フィールド名 | 型 | 説明 |
|------------|-----|------|
| `locationCode` | String | ロケーションコード |
| `areaName` | String | エリア名 |
| `productCode` | String | 商品コード |
| `productName` | String | 商品名 |
| `unitType` | String | 荷姿種別 |
| `systemQuantity` | Integer | システム在庫数（棚卸前） |
| `actualQuantity` | Integer | 実数（null = 未入力） |
| `lotNumber` | String | ロット番号 |
| `expiryDate` | String (date) | 期限日 |

#### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE{stocktakeId または\nbuildingId のどちらか指定?}
    VALIDATE -->|両方なし| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|stocktakeId 指定| FIND_STK{棚卸 存在確認}
    VALIDATE -->|buildingId 指定| FIND_BLD{棟 存在確認}

    FIND_STK -->|なし| ERR_STK[404 STOCKTAKE_NOT_FOUND]
    FIND_STK -->|あり| QUERY_STK[stocktake_lines + inventory から\nロケーション順に取得]

    FIND_BLD -->|なし| ERR_BLD[404 BUILDING_NOT_FOUND]
    FIND_BLD -->|あり| QUERY_BLD[inventory テーブルから\n棟・エリアでフィルタリング\nactualQuantity = null で返却]

    QUERY_STK --> FORMAT{format?}
    QUERY_BLD --> FORMAT
    FORMAT -->|json| RES_JSON[200 OK JSON配列\nロケーション昇順]
    FORMAT -->|csv| RES_CSV[200 OK CSVダウンロード\nfilename: stocktake_list_YYYYMMDD.csv]
```

**ビジネスルール**:

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `stocktakeId` と `buildingId` の両方が未指定の場合は 400 を返す | `VALIDATION_ERROR` |
| 2 | `stocktakeId` 指定時に棚卸が存在しない場合は 404 を返す | `STOCKTAKE_NOT_FOUND` |
| 3 | `buildingId` 指定時に棟が存在しない場合は 404 を返す | `BUILDING_NOT_FOUND` |
| 4 | 結果はロケーションコード昇順でソートして返す（現場でのピッキング動線に合わせる） | — |

#### 5. 補足事項

- **棚卸開始後（`stocktakeId` 指定）**: `stocktake_lines` から棚卸対象の在庫情報を取得する。既に実数入力済みのデータは `actualQuantity` に値が入る。
- **棚卸開始前プレビュー（`buildingId` 指定）**: `inventory` テーブルから現在在庫を取得し、`actualQuantity = null`（空欄）で返す。棚卸作業帳票の印刷に使用する。
- CSV出力時のファイル名: `stocktake_list_{YYYYMMDD}.csv`
- CSV出力時のヘッダー行（日本語）: `ロケーションコード,エリア名,商品コード,商品名,荷姿,システム在庫数,実数,ロット番号,期限日`

---

### API-RPT-011 棚卸結果レポート

#### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-RPT-011` |
| **API名** | 棚卸結果レポート |
| **メソッド** | `GET` |
| **パス** | `/api/v1/reports/stocktake-result` |
| **認証** | 要 |
| **対象ロール** | 全ロール（SYSTEM_ADMIN / WAREHOUSE_MANAGER / WAREHOUSE_STAFF / VIEWER） |
| **概要** | 棚卸確定後の結果レポートを出力する。棚卸前在庫・実数・差異数・差異率を出力する。 |
| **関連画面** | INV-STK-002（棚卸結果画面）、RPT-001（レポート画面） |

#### 2. リクエスト仕様

##### クエリパラメータ

| パラメータ名 | 型 | 必須 | 説明 |
|------------|-----|:----:|------|
| `stocktakeId` | Long | ○ | 棚卸ID |
| `format` | String | — | `json`（デフォルト）/ `csv` |

#### 3. レスポンス仕様（JSON）

```json
[
  {
    "locationCode": "A-01-001",
    "productCode": "P-001",
    "productName": "商品A",
    "unitType": "CAS",
    "systemQuantity": 10,
    "actualQuantity": 9,
    "diffQuantity": -1,
    "diffRate": -10.0,
    "lotNumber": "LOT-001"
  }
]
```

| フィールド名 | 型 | 説明 |
|------------|-----|------|
| `locationCode` | String | ロケーションコード |
| `productCode` | String | 商品コード |
| `productName` | String | 商品名 |
| `unitType` | String | 荷姿種別 |
| `systemQuantity` | Integer | システム在庫数（棚卸前） |
| `actualQuantity` | Integer | 実数 |
| `diffQuantity` | Integer | 差異数 = 実数 − システム在庫数 |
| `diffRate` | Double | 差異率（%）= 差異数 / システム在庫数 × 100（システム在庫数=0の場合は null） |
| `lotNumber` | String | ロット番号 |

#### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[stocktakeId 必須チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| FIND_STK{棚卸 存在確認}
    FIND_STK -->|なし| ERR_404[404 STOCKTAKE_NOT_FOUND]
    FIND_STK -->|あり| CHECK_STATUS{棚卸ステータス確認\nCONFIRMED か?}
    CHECK_STATUS -->|未確定| WARN[確定前でも出力可\nactualQuantity が null の行あり]
    CHECK_STATUS -->|確定済| QUERY
    WARN --> QUERY[stocktake_lines から取得\n差異数・差異率を計算]
    QUERY --> FORMAT{format?}
    FORMAT -->|json| RES_JSON[200 OK JSON配列]
    FORMAT -->|csv| RES_CSV[200 OK CSVダウンロード\nfilename: stocktake_result_YYYYMMDD.csv]
```

**ビジネスルール**:

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `stocktakeId` に対応する棚卸が存在しない場合は 404 を返す | `STOCKTAKE_NOT_FOUND` |
| 2 | システム在庫数が0の場合、差異率は null を返す（ゼロ除算回避） | — |
| 3 | 棚卸確定前でも出力可能（未入力行は `actualQuantity = null`） | — |

#### 5. 補足事項

- 差異がある明細（`diffQuantity != 0`）を識別しやすくするため、CSV出力時は差異フラグ列を追加することを検討できる。
- 棚卸確定（`CONFIRMED`）後に出力することを推奨するが、確定前でも出力可能とする。
- CSV出力時のファイル名: `stocktake_result_{YYYYMMDD}.csv`
- CSV出力時のヘッダー行（日本語）: `ロケーションコード,商品コード,商品名,荷姿,システム在庫数,実数,差異数,差異率(%),ロット番号`

---

### API-RPT-012 ピッキング指示書

#### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-RPT-012` |
| **API名** | ピッキング指示書 |
| **メソッド** | `GET` |
| **パス** | `/api/v1/reports/picking-instruction` |
| **認証** | 要 |
| **対象ロール** | 全ロール（SYSTEM_ADMIN / WAREHOUSE_MANAGER / WAREHOUSE_STAFF / VIEWER） |
| **概要** | 指定ピッキング指示のピッキング指示書を出力する。ロケーション順に商品・数量・受注番号を出力する。 |
| **関連画面** | OUT-002（ピッキング画面）、RPT-001（レポート画面） |

#### 2. リクエスト仕様

##### クエリパラメータ

| パラメータ名 | 型 | 必須 | 説明 |
|------------|-----|:----:|------|
| `pickingInstructionId` | Long | ○ | ピッキング指示ID |
| `format` | String | — | `json`（デフォルト）/ `csv` |

#### 3. レスポンス仕様（JSON）

```json
[
  {
    "locationCode": "A-01-001",
    "productCode": "P-001",
    "productName": "商品A",
    "unitType": "CAS",
    "instructedQuantity": 3,
    "outboundSlipNumber": "OUT-2026-00050",
    "customerName": "テスト出荷先A",
    "plannedShipDate": "2026-03-15",
    "lotNumber": "LOT-001"
  }
]
```

| フィールド名 | 型 | 説明 |
|------------|-----|------|
| `locationCode` | String | ロケーションコード |
| `productCode` | String | 商品コード |
| `productName` | String | 商品名 |
| `unitType` | String | 荷姿種別 |
| `instructedQuantity` | Integer | ピッキング指示数量 |
| `outboundSlipNumber` | String | 対応出荷伝票番号 |
| `customerName` | String | 出荷先名 |
| `plannedShipDate` | String (date) | 出荷予定日 |
| `lotNumber` | String | ロット番号 |

#### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[pickingInstructionId 必須チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| FIND_PICK{ピッキング指示 存在確認}
    FIND_PICK -->|なし| ERR_404[404 PICKING_NOT_FOUND]
    FIND_PICK -->|あり| QUERY[picking_instruction_lines から取得\nロケーション昇順にソート]
    QUERY --> FORMAT{format?}
    FORMAT -->|json| RES_JSON[200 OK JSON配列]
    FORMAT -->|csv| RES_CSV[200 OK CSVダウンロード\nfilename: picking_instruction_YYYYMMDD.csv]
```

**ビジネスルール**:

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `pickingInstructionId` に対応するピッキング指示が存在しない場合は 404 を返す | `PICKING_NOT_FOUND` |
| 2 | 結果はロケーションコード昇順でソートして返す（効率的なピッキング動線を実現） | — |

#### 5. 補足事項

- ピッキング指示書は現場スタッフが携行して使用する作業帳票。ロケーション昇順ソートにより動線を最適化する。
- 1ピッキング指示あたりの明細数は通常数件〜数十件のため、ページングなしでも問題ない。
- CSV出力時のファイル名: `picking_instruction_{YYYYMMDD}.csv`
- CSV出力時のヘッダー行（日本語）: `ロケーションコード,商品コード,商品名,荷姿,指示数量,出荷伝票番号,出荷先名,出荷予定日,ロット番号`

---

### API-RPT-013 出荷検品レポート

#### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-RPT-013` |
| **API名** | 出荷検品レポート |
| **メソッド** | `GET` |
| **パス** | `/api/v1/reports/shipping-inspection` |
| **認証** | 要 |
| **対象ロール** | 全ロール（SYSTEM_ADMIN / WAREHOUSE_MANAGER / WAREHOUSE_STAFF / VIEWER） |
| **概要** | 指定出荷伝票の出荷検品結果レポートを出力する。ピッキング数・検品数・差異を品目ごとに出力する。 |
| **関連画面** | OUT-003（出荷検品画面）、RPT-001（レポート画面） |

#### 2. リクエスト仕様

##### クエリパラメータ

| パラメータ名 | 型 | 必須 | 説明 |
|------------|-----|:----:|------|
| `slipId` | Long | ○ | 出荷伝票ID |
| `format` | String | — | `json`（デフォルト）/ `csv` |

#### 3. レスポンス仕様（JSON）

```json
[
  {
    "slipNumber": "OUT-2026-00050",
    "customerName": "テスト出荷先A",
    "plannedShipDate": "2026-03-15",
    "productCode": "P-001",
    "productName": "商品A",
    "unitType": "CAS",
    "pickedQuantity": 3,
    "inspectedQuantity": 3,
    "diffQuantity": 0
  }
]
```

| フィールド名 | 型 | 説明 |
|------------|-----|------|
| `slipNumber` | String | 出荷伝票番号 |
| `customerName` | String | 出荷先名 |
| `plannedShipDate` | String (date) | 出荷予定日 |
| `productCode` | String | 商品コード |
| `productName` | String | 商品名 |
| `unitType` | String | 荷姿種別 |
| `pickedQuantity` | Integer | ピッキング数量 |
| `inspectedQuantity` | Integer | 検品数量 |
| `diffQuantity` | Integer | 差異数 = 検品数 − ピッキング数 |

#### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[slipId 必須チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| FIND_SLIP{出荷伝票 存在確認}
    FIND_SLIP -->|なし| ERR_404[404 OUTBOUND_SLIP_NOT_FOUND]
    FIND_SLIP -->|あり| QUERY[outbound_slip_lines から\nピッキング数・検品数を取得\n差異を計算]
    QUERY --> FORMAT{format?}
    FORMAT -->|json| RES_JSON[200 OK JSON配列]
    FORMAT -->|csv| RES_CSV[200 OK CSVダウンロード\nfilename: shipping_inspection_YYYYMMDD.csv]
```

**ビジネスルール**:

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `slipId` に対応する出荷伝票が存在しない場合は 404 を返す | `OUTBOUND_SLIP_NOT_FOUND` |

#### 5. 補足事項

- 本APIは1出荷伝票の明細全件を返すため、ページングなし。1伝票あたりの明細数は通常数件〜数十件のため問題ない。
- CSV出力時のファイル名: `shipping_inspection_{YYYYMMDD}.csv`
- CSV出力時のヘッダー行（日本語）: `伝票番号,出荷先名,出荷予定日,商品コード,商品名,荷姿,ピッキング数,検品数,差異数`

---

### API-RPT-014 配送リスト

#### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-RPT-014` |
| **API名** | 配送リスト |
| **メソッド** | `GET` |
| **パス** | `/api/v1/reports/delivery-list` |
| **認証** | 要 |
| **対象ロール** | 全ロール（SYSTEM_ADMIN / WAREHOUSE_MANAGER / WAREHOUSE_STAFF / VIEWER） |
| **概要** | 指定倉庫・期間の出荷予定リストを配送用に出力する。 |
| **関連画面** | OUT-001（受注一覧画面）、RPT-001（レポート画面） |

#### 2. リクエスト仕様

##### クエリパラメータ

| パラメータ名 | 型 | 必須 | デフォルト | 説明 |
|------------|-----|:----:|----------|------|
| `warehouseId` | Long | ○ | — | 倉庫ID |
| `plannedDateFrom` | String (date) | — | — | 出荷予定日（From） |
| `plannedDateTo` | String (date) | — | — | 出荷予定日（To） |
| `status` | String | — | — | ステータス絞り込み |
| `carrier` | String | — | — | 配送業者名で絞り込み（部分一致） |
| `format` | String | — | `json` | `json` / `csv` |

#### 3. レスポンス仕様（JSON）

```json
[
  {
    "slipNumber": "OUT-2026-00050",
    "customerName": "テスト出荷先A",
    "deliveryAddress": "東京都千代田区1-1-1",
    "plannedShipDate": "2026-03-15",
    "status": "ALLOCATED",
    "statusLabel": "引当済",
    "carrier": "ヤマト運輸",
    "trackingNumber": "1234567890",
    "totalQuantityCas": 5,
    "totalQuantityPcs": 50
  }
]
```

| フィールド名 | 型 | 説明 |
|------------|-----|------|
| `slipNumber` | String | 出荷伝票番号 |
| `customerName` | String | 出荷先名 |
| `deliveryAddress` | String | 配送先住所 |
| `plannedShipDate` | String (date) | 出荷予定日 |
| `status` | String | ステータスコード |
| `statusLabel` | String | ステータス表示名 |
| `carrier` | String | 配送業者名（null = 未設定） |
| `trackingNumber` | String | 送り状番号（null = 未設定） |
| `totalQuantityCas` | Integer | 合計数量（ケース） |
| `totalQuantityPcs` | Integer | 合計数量（バラ） |

#### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[warehouseId 必須チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| FIND_WH{倉庫 存在確認}
    FIND_WH -->|なし| ERR_404[404 WAREHOUSE_NOT_FOUND]
    FIND_WH -->|あり| QUERY[outbound_slips + partners から\n条件でフィルタリング\n出荷予定日昇順にソート]
    QUERY --> FORMAT{format?}
    FORMAT -->|json| RES_JSON[200 OK JSON配列]
    FORMAT -->|csv| RES_CSV[200 OK CSVダウンロード\nfilename: delivery_list_YYYYMMDD.csv]
```

**ビジネスルール**:

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `warehouseId` に対応する倉庫が存在しない場合は 404 を返す | `WAREHOUSE_NOT_FOUND` |
| 2 | 取得件数が10,000件を超える場合は 400 を返し、期間絞り込みを促す | `VALIDATION_ERROR` |

#### 5. 補足事項

- 配送リストは配送業者への連絡・出荷確認に使用する帳票。出荷予定日昇順でソートして返す。
- `deliveryAddress` は `partners` テーブルの配送先住所を参照する。
- CSV出力時のファイル名: `delivery_list_{YYYYMMDD}.csv`
- CSV出力時のヘッダー行（日本語）: `伝票番号,出荷先名,配送先住所,出荷予定日,ステータス,合計数量(ケース),合計数量(バラ)`

---

### API-RPT-015 未出荷リスト（リアルタイム）

#### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-RPT-015` |
| **API名** | 未出荷リスト（リアルタイム） |
| **メソッド** | `GET` |
| **パス** | `/api/v1/reports/unshipped-realtime` |
| **認証** | 要 |
| **対象ロール** | 全ロール（SYSTEM_ADMIN / WAREHOUSE_MANAGER / WAREHOUSE_STAFF / VIEWER） |
| **概要** | 指定日時点でまだ出荷していない伝票の一覧をリアルタイムで返す。集計バッチを介さずに `outbound_slips` を直接参照する。 |
| **関連画面** | RPT-001（レポート画面） |

#### 2. リクエスト仕様

##### クエリパラメータ

| パラメータ名 | 型 | 必須 | デフォルト | 説明 |
|------------|-----|:----:|----------|------|
| `warehouseId` | Long | ○ | — | 倉庫ID |
| `asOfDate` | String (date) | — | 現在の営業日 | 基準日（この日以前の予定日で未出荷のもの） |
| `format` | String | — | `json` | `json` / `csv` |

#### 3. レスポンス仕様（JSON）

```json
[
  {
    "slipNumber": "OUT-2026-00045",
    "customerName": "テスト出荷先B",
    "plannedShipDate": "2026-03-12",
    "productCode": "P-002",
    "productName": "商品B",
    "totalQuantityCas": 3,
    "status": "PICKING",
    "statusLabel": "ピッキング中",
    "delayDays": 2
  }
]
```

| フィールド名 | 型 | 説明 |
|------------|-----|------|
| `slipNumber` | String | 出荷伝票番号 |
| `customerName` | String | 出荷先名 |
| `plannedShipDate` | String (date) | 出荷予定日 |
| `productCode` | String | 商品コード |
| `productName` | String | 商品名 |
| `totalQuantityCas` | Integer | 合計数量（ケース） |
| `status` | String | 現在のステータスコード |
| `statusLabel` | String | ステータス表示名 |
| `delayDays` | Integer | 遅延日数（asOfDate − plannedShipDate） |

#### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[warehouseId 必須チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| DEFAULT_DATE{asOfDate 指定あり?}
    DEFAULT_DATE -->|なし| GET_BD[business_date から\ncurrent_business_date を取得]
    DEFAULT_DATE -->|あり| QUERY
    GET_BD --> QUERY[outbound_slips から\nstatus NOT IN 'SHIPPED','CANCELLED'\nかつ planned_date <= asOfDate を取得]
    QUERY --> FORMAT{format?}
    FORMAT -->|json| RES_JSON[200 OK JSON配列]
    FORMAT -->|csv| RES_CSV[200 OK CSVダウンロード\nfilename: unshipped_realtime_YYYYMMDD.csv]
```

**ビジネスルール**:

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `warehouseId` に対応する倉庫が存在しない場合は 404 を返す | `WAREHOUSE_NOT_FOUND` |
| 2 | `asOfDate` が未指定の場合は現在の営業日（`business_date.current_business_date`）を使用する | — |
| 3 | `SHIPPED`（出荷完了）・`CANCELLED`（キャンセル）のステータスは除外する | — |

#### 5. 補足事項

- 本APIはリアルタイムで `outbound_slips` テーブルを直接参照するため、最新状態を反映する。
- `asOfDate` を現在営業日と同日にすると当日業務の出荷督促確認に使用できる。
- 大量の未出荷が蓄積している場合は件数が多くなる可能性があるため、件数上限（10,000件）を設ける。
- CSV出力時のファイル名: `unshipped_realtime_{YYYYMMDD}.csv`
- CSV出力時のヘッダー行（日本語）: `伝票番号,出荷先名,出荷予定日,商品コード,商品名,合計数量(ケース),ステータス,遅延日数`

---

### API-RPT-016 未出荷リスト（確定）

#### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-RPT-016` |
| **API名** | 未出荷リスト（確定） |
| **メソッド** | `GET` |
| **パス** | `/api/v1/reports/unshipped-confirmed` |
| **認証** | 要 |
| **対象ロール** | 全ロール（SYSTEM_ADMIN / WAREHOUSE_MANAGER / WAREHOUSE_STAFF / VIEWER） |
| **概要** | 日替処理で確定した指定営業日の未出荷リストを返す。`unshipped_list_records` テーブルから取得するため、営業日末時点の確定データとなる。 |
| **関連画面** | RPT-001（レポート画面） |

#### 2. リクエスト仕様

##### クエリパラメータ

| パラメータ名 | 型 | 必須 | 説明 |
|------------|-----|:----:|------|
| `warehouseId` | Long | ○ | 倉庫ID |
| `batchBusinessDate` | String (date) | ○ | バッチ処理営業日（`unshipped_list_records.batch_business_date`） |
| `format` | String | — | `json`（デフォルト）/ `csv` |

#### 3. レスポンス仕様（JSON）

```json
[
  {
    "batchBusinessDate": "2026-03-14",
    "slipNumber": "OUT-2026-00045",
    "customerName": "テスト出荷先B",
    "plannedShipDate": "2026-03-12",
    "productCode": "P-002",
    "productName": "商品B",
    "totalQuantityCas": 3,
    "statusAtBatch": "PICKING"
  }
]
```

| フィールド名 | 型 | 説明 |
|------------|-----|------|
| `batchBusinessDate` | String (date) | バッチ処理営業日 |
| `slipNumber` | String | 出荷伝票番号 |
| `customerName` | String | 出荷先名 |
| `plannedShipDate` | String (date) | 出荷予定日 |
| `productCode` | String | 商品コード |
| `productName` | String | 商品名 |
| `totalQuantityCas` | Integer | 合計数量（ケース） |
| `statusAtBatch` | String | バッチ処理時点のステータスコード |

#### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[warehouseId・batchBusinessDate 必須チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| QUERY[unshipped_list_records から\nbatch_business_date + warehouse_id で取得]
    QUERY --> FORMAT{format?}
    FORMAT -->|json| RES_JSON[200 OK JSON配列]
    FORMAT -->|csv| RES_CSV[200 OK CSVダウンロード\nfilename: unshipped_confirmed_YYYYMMDD.csv]
```

**ビジネスルール**:

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | `warehouseId` に対応する倉庫が存在しない場合は 404 を返す | `WAREHOUSE_NOT_FOUND` |
| 2 | 指定 `batchBusinessDate` の日替処理が実行されていない場合は空配列（`[]`）を返す（404 ではない） | — |

#### 5. 補足事項

- 本APIは日替処理で生成された `unshipped_list_records` テーブルの確定データを参照する。リアルタイム版（API-RPT-015）とは異なり、バッチ処理時点のスナップショットを返す。
- 指定した `batchBusinessDate` に日替処理が実行されていない場合は空配列を返す。
- `warehouseId` を受け取った場合、`warehouses` テーブルから対応する `warehouse_code` を取得し、`unshipped_list_records.warehouse_code` カラムで絞り込む（`unshipped_list_records` テーブルには `warehouse_id` カラムが存在せず `warehouse_code` カラムのみのため）。
- CSV出力時のファイル名: `unshipped_confirmed_{YYYYMMDD}.csv`
- CSV出力時のヘッダー行（日本語）: `バッチ処理営業日,伝票番号,出荷先名,出荷予定日,商品コード,商品名,合計数量(ケース),バッチ時点ステータス`

---

### API-RPT-017 日次集計レポート

#### 1. API概要

| 項目 | 内容 |
|------|------|
| **API ID** | `API-RPT-017` |
| **API名** | 日次集計レポート |
| **メソッド** | `GET` |
| **パス** | `/api/v1/reports/daily-summary` |
| **認証** | 要 |
| **対象ロール** | 全ロール（SYSTEM_ADMIN / WAREHOUSE_MANAGER / WAREHOUSE_STAFF / VIEWER） |
| **概要** | 日替処理で集計した指定営業日の日次サマリーを返す。入荷件数・数量、出荷件数・数量、在庫数量（営業日末時点）、未入荷件数、未出荷件数を含む。 |
| **関連画面** | RPT-001（レポート画面）、BAT-001（日替処理画面） |

#### 2. リクエスト仕様

##### クエリパラメータ

| パラメータ名 | 型 | 必須 | 説明 |
|------------|-----|:----:|------|
| `targetBusinessDate` | String (date) | ○ | 対象営業日（日替処理が完了している必要がある） |
| `format` | String | — | `json`（デフォルト）/ `csv` |

#### 3. レスポンス仕様（JSON）

```json
[
  {
    "businessDate": "2026-03-14",
    "warehouseId": 1,
    "warehouseName": "東京DC",
    "inboundCount": 12,
    "inboundLineCount": 45,
    "inboundQuantityTotal": 1230,
    "outboundCount": 8,
    "outboundLineCount": 30,
    "outboundQuantityTotal": 870,
    "inventoryQuantityTotal": 5600,
    "unreceivedCount": 3,
    "unshippedCount": 1
  }
]
```

| フィールド名 | 型 | 説明 |
|------------|-----|------|
| `businessDate` | String (date) | 対象営業日 |
| `warehouseId` | Long | 倉庫ID |
| `warehouseName` | String | 倉庫名 |
| `inboundCount` | Integer | 入荷件数（当日入庫完了伝票数） |
| `inboundLineCount` | Integer | 入荷明細行数 |
| `inboundQuantityTotal` | Integer | 入荷数量合計（バラ換算） |
| `outboundCount` | Integer | 出荷件数（当日出荷完了伝票数） |
| `outboundLineCount` | Integer | 出荷明細行数 |
| `outboundQuantityTotal` | Integer | 出荷数量合計（バラ換算） |
| `inventoryQuantityTotal` | Integer | 在庫数量合計（営業日末時点・バラ換算） |
| `unreceivedCount` | Integer | 未入荷件数（当日時点の累積） |
| `unshippedCount` | Integer | 未出荷件数（当日時点の累積） |

##### エラーレスポンス

| HTTPステータス | エラーコード | 発生条件 |
|-------------|------------|---------|
| `400 Bad Request` | `VALIDATION_ERROR` | `targetBusinessDate` が未指定または日付形式不正 |
| `404 Not Found` | `BATCH_EXECUTION_NOT_FOUND` | 指定日付の `batch_execution_logs` に `status=SUCCESS` のレコードが存在しない |

#### 4. 業務ロジック

```mermaid
flowchart TD
    START([開始]) --> VALIDATE[targetBusinessDate 必須チェック]
    VALIDATE -->|NG| ERR_VAL[400 VALIDATION_ERROR]
    VALIDATE -->|OK| CHECK_BATCH{batch_execution_logsに\nstatus=SUCCESSのレコード存在確認}
    CHECK_BATCH -->|なし| ERR_404[404 BATCH_EXECUTION_NOT_FOUND]
    CHECK_BATCH -->|あり| QUERY_INB[inbound_summaries から\n対象日の入荷集計データ取得]
    QUERY_INB --> QUERY_OUT[outbound_summaries から\n対象日の出荷集計データ取得]
    QUERY_OUT --> QUERY_INV[inventory_snapshots から\n対象日の在庫スナップショット取得\n倉庫別に合計]
    QUERY_INV --> QUERY_UNRCV[unreceived_list_records から\nbatch_business_date=対象日の件数カウント]
    QUERY_UNRCV --> QUERY_UNSHIP[unshipped_list_records から\nbatch_business_date=対象日の件数カウント]
    QUERY_UNSHIP --> MERGE[倉庫別に全データをマージ]
    MERGE --> FORMAT{format?}
    FORMAT -->|json| RES_JSON[200 OK JSON配列\n倉庫ごとの集計行]
    FORMAT -->|csv| RES_CSV[200 OK CSVダウンロード\nfilename: daily_summary_YYYYMMDD.csv]
```

**ビジネスルール**:

| # | ルール | エラーコード |
|---|--------|------------|
| 1 | 指定日の日替処理が `SUCCESS` で完了していない場合は 404 を返す（`RUNNING` や `FAILED` の場合も 404） | `BATCH_EXECUTION_NOT_FOUND` |
| 2 | 複数倉庫が存在する場合、倉庫ごとに1行出力する（倉庫IDの昇順） | — |
| 3 | 倉庫に入出荷実績がない場合でも、在庫スナップショットがあれば出力する | — |

#### 5. 補足事項

- 複数倉庫が存在する場合、倉庫ごとに1行出力する（倉庫IDの昇順）。
- `inboundQuantityTotal` / `outboundQuantityTotal` / `inventoryQuantityTotal` はすべてバラ（PCS）換算での合計値。
- CSV出力の場合もヘッダー行を含む全倉庫分のデータを出力する。
- 日次集計レポートは `inbound_summaries`、`outbound_summaries`、`inventory_snapshots`、`unreceived_list_records`、`unshipped_list_records` の5テーブルから集計するため、日替処理（`SUCCESS`）完了後でなければ取得できない。
- CSV出力時のファイル名: `daily_summary_{YYYYMMDD}.csv`
- CSV出力時のヘッダー行（日本語）: `対象営業日,倉庫ID,倉庫名,入荷件数,入荷明細行数,入荷数量合計,出荷件数,出荷明細行数,出荷数量合計,在庫数量合計,未入荷件数,未出荷件数`

---
