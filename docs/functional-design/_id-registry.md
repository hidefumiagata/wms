# ID レジストリ（SSOT）

> **本ファイルはWMSプロジェクトの全ID体系の唯一の定義場所（Single Source of Truth）です。**
> - 新規IDを追加する場合は、**必ず本ファイルを先に更新**してから個別設計書を作成してください
> - build-docs.js --validate で本ファイルと個別設計書の整合性を自動検証します
> - 個別設計書（_standard-api.md, _standard-screen.md等）にID一覧を複製しないでください

---

## 1. ドキュメントカテゴリ一覧

| カテゴリ | フォルダ | ファイル数 | ID管理方式 |
|---------|--------|:--------:|----------|
| プロジェクト計画書 | project-plan/ | 11 | ファイル名連番 |
| アーキテクチャブループリント | architecture-blueprint/ | 13 | ファイル名連番 |
| 機能要件定義書 | functional-requirements/ | 11 | 機能IDプレフィックス |
| データモデル定義 | data-model/ | 4 | ファイル名連番 |
| 機能設計書 - 画面 | functional-design/SCR-* | 15 | 画面ID |
| 機能設計書 - API | functional-design/API-* | 13 | API ID |
| 機能設計書 - 帳票 | functional-design/RPT-* | 17 | RPT ID（※RPT-02欠番） |
| 機能設計書 - バッチ | functional-design/BAT-* | 1 | BAT ID |
| 機能設計書 - I/F | functional-design/IF-* | 2 | IFX ID |
| アーキテクチャ設計書 | architecture-design/ | 13 | ファイル名連番 |
| テスト仕様書 | test-specifications/ | 2 | テストID（TST-*） |
| OpenAPI定義 | openapi/ | 1 | — |

---

## 2. ID体系定義（プレフィックス一覧）

### 2.1 機能IDプレフィックス（要件管理用）

| プレフィックス | 対象モジュール |
|-------------|-------------|
| WMS-AUTH | 認証管理 |
| WMS-MST | マスタ管理 |
| WMS-INB | 入荷管理 |
| WMS-INV | 在庫管理 |
| WMS-OUT | 出荷管理 |
| WMS-ALO | 在庫引当管理 |
| WMS-RTN | 返品管理 |
| WMS-BAT | バッチ管理 |
| WMS-SYS | システムパラメータ管理 |
| WMS-RPT | レポート |
| WMS-IF | 外部連携I/F |
| WMS-INF | インフラ/基盤 |

### 2.2 画面IDプレフィックス

| プレフィックス | 対象 | 設計書 |
|-------------|------|--------|
| AUTH | 認証 | SCR-01-auth.md |
| MST | マスタ管理 | SCR-02〜06 |
| INB | 入荷管理 | SCR-07-inbound.md |
| INV | 在庫管理 | SCR-08, SCR-09 |
| OUT | 出荷管理 | SCR-10-outbound.md |
| ALL | 在庫引当 | SCR-13-allocation.md |
| RTN | 返品管理 | SCR-14-returns.md |
| BAT | バッチ管理 | SCR-11-batch.md |
| SYS | システム管理 | SCR-12-system-parameters.md |
| IF | 外部連携I/F | SCR-15-interface.md |
| RPT | レポート出力 | 各業務メニュー内ダイアログ |
| PIC | ピッキング指示 | 伝票番号プレフィックスとして使用（画面IDではない） |

### 2.3 API IDプレフィックス

| プレフィックス | 対象 |
|-------------|------|
| `API-AUTH` | 認証 |
| `API-SYS` | システム共通 |
| `API-MST-PRD` | 商品マスタ |
| `API-MST-PAR` | 取引先マスタ |
| `API-MST-FAC` | 施設マスタ（倉庫・棟・エリア・ロケーション） |
| `API-MST-USR` | ユーザーマスタ |
| `API-INB` | 入荷管理 |
| `API-INV` | 在庫管理 |
| `API-OUT` | 出荷管理 |
| `API-ALL` | 在庫引当 |
| `API-RTN` | 返品管理 |
| `API-RPT` | レポート |
| `API-BAT` | バッチ管理 |
| `API-IF` | I/F管理 |

### 2.4 レポートIDプレフィックス

| プレフィックス | 対象 |
|-------------|------|
| RPT | レポート（RPT-01〜RPT-18、RPT-02欠番） |

### 2.5 バッチIDプレフィックス

| プレフィックス | 対象 |
|-------------|------|
| BAT | 日替バッチ処理 |

### 2.6 I/F IDプレフィックス

| プレフィックス | 対象 |
|-------------|------|
| IFX | 外部連携インターフェース |

### 2.7 伝票番号プレフィックス

| プレフィックス | フォーマット | 対象 |
|-------------|-----------|------|
| INB | INB-YYYYMMDD-NNNN | 入荷伝票 |
| OUT | OUT-YYYYMMDD-NNNN | 出荷伝票 |
| ST | ST-YYYY-NNNNN | 棚卸伝票 |
| PIC | PIC-YYYYMMDD-NNNN | ピッキング指示 |
| RTN-I | RTN-I-YYYYMMDD-XXXX | 入荷返品伝票 |
| RTN-S | RTN-S-YYYYMMDD-XXXX | 在庫返品伝票 |
| RTN-O | RTN-O-YYYYMMDD-XXXX | 出荷返品伝票 |

### 2.8 テストIDプレフィックス

| プレフィックス | 対象 |
|-------------|------|
| TST-AUTH | 認証テスト |
| TST-MST | マスタ管理テスト |
| TST-INB | 入荷管理テスト |
| TST-INV | 在庫管理テスト |
| TST-OUT | 出荷管理テスト |
| TST-ALL | 引当管理テスト |
| TST-RTN | 返品管理テスト |
| TST-BAT | バッチテスト |
| TST-SYS | システムパラメータテスト |
| TST-IF | I/Fテスト |
| TST-RPT | レポートテスト |

### 2.9 メッセージIDプレフィックス

| フォーマット | 例 | 用途 |
|-----------|-----|------|
| MSG-E-{画面ID}-{連番} | MSG-E-AUTH001-001 | エラー |
| MSG-W-{画面ID}-{連番} | MSG-W-INB002-001 | 警告（確認ダイアログ） |
| MSG-S-{画面ID}-{連番} | MSG-S-RTN001-001 | 成功通知 |
| MSG-I-{画面ID}-{連番} | MSG-I-INV011-001 | 情報通知 |

---

## 3. 全ID一覧

### 3.1 画面ID一覧

#### 認証

| 画面ID | 画面名 | URL |
|--------|--------|-----|
| AUTH-001 | ログイン | `/login` |
| AUTH-002 | 初回パスワード変更 | `/change-password` |
| AUTH-003 | パスワードリセット申請 | `/auth/reset-request` |
| AUTH-004 | パスワード再設定 | `/auth/reset-password` |

#### マスタ管理

| 画面ID | 画面名 | URL |
|--------|--------|-----|
| MST-001 | 商品一覧 | `/master/products` |
| MST-002 | 商品登録 | `/master/products/new` |
| MST-003 | 商品編集 | `/master/products/:id/edit` |
| MST-011 | 取引先一覧 | `/master/partners` |
| MST-012 | 取引先登録 | `/master/partners/new` |
| MST-013 | 取引先編集 | `/master/partners/:id/edit` |
| MST-021 | 倉庫一覧 | `/master/warehouses` |
| MST-022 | 倉庫登録 | `/master/warehouses/new` |
| MST-023 | 倉庫編集 | `/master/warehouses/:id/edit` |
| MST-031 | 棟一覧 | `/master/buildings` |
| MST-032 | 棟登録 | `/master/buildings/new` |
| MST-033 | 棟編集 | `/master/buildings/:id/edit` |
| MST-041 | エリア一覧 | `/master/areas` |
| MST-042 | エリア登録 | `/master/areas/new` |
| MST-043 | エリア編集 | `/master/areas/:id/edit` |
| MST-051 | ロケーション一覧 | `/master/locations` |
| MST-052 | ロケーション登録 | `/master/locations/new` |
| MST-053 | ロケーション編集 | `/master/locations/:id/edit` |
| MST-061 | ユーザー一覧 | `/master/users` |
| MST-062 | ユーザー登録 | `/master/users/new` |
| MST-063 | ユーザー編集 | `/master/users/:id/edit` |

#### 入荷管理

| 画面ID | 画面名 | URL |
|--------|--------|-----|
| INB-001 | 入荷予定一覧 | `/inbound/slips` |
| INB-002 | 入荷予定登録 | `/inbound/slips/new` |
| INB-003 | 入荷予定詳細 | `/inbound/slips/:id` |
| INB-004 | 入荷検品 | `/inbound/slips/:id/inspect` |
| INB-005 | 入庫指示・確定 | `/inbound/slips/:id/store` |
| INB-006 | 入荷実績照会 | `/inbound/results` |

#### 在庫管理

| 画面ID | 画面名 | URL |
|--------|--------|-----|
| INV-001 | 在庫一覧照会 | `/inventory` |
| INV-002 | 在庫移動登録 | `/inventory/move` |
| INV-003 | ばらし登録 | `/inventory/breakdown` |
| INV-004 | 在庫訂正登録 | `/inventory/correction` |
| INV-011 | 棚卸一覧 | `/inventory/stocktakes` |
| INV-012 | 棚卸開始 | `/inventory/stocktakes/new` |
| INV-013 | 棚卸実施（実数入力） | `/inventory/stocktakes/:id` |
| INV-014 | 棚卸確定 | `/inventory/stocktakes/:id/confirm` |

#### 出荷管理

| 画面ID | 画面名 | URL |
|--------|--------|-----|
| OUT-001 | 受注一覧 | `/outbound/slips` |
| OUT-002 | 受注登録 | `/outbound/slips/new` |
| OUT-003 | 受注詳細 | `/outbound/slips/:id` |
| OUT-011 | ピッキング指示一覧 | `/outbound/picking` |
| OUT-012 | ピッキング指示作成 | `/outbound/picking/new` |
| OUT-013 | ピッキング完了入力 | `/outbound/picking/:id` |
| OUT-021 | 出荷検品 | `/outbound/slips/:id/inspect` |
| OUT-022 | 出荷完了（配送情報入力） | `/outbound/slips/:id/ship` |

#### バッチ管理

| 画面ID | 画面名 | URL |
|--------|--------|-----|
| BAT-001 | 日替処理実行 | `/batch/daily-close` |
| BAT-002 | バッチ実行履歴一覧 | `/batch/history` |

#### システム管理

| 画面ID | 画面名 | URL |
|--------|--------|-----|
| SYS-001 | システムパラメータ設定 | `/system/parameters` |

#### 在庫引当

| 画面ID | 画面名 | URL |
|--------|--------|-----|
| ALL-001 | 在庫引当 | `/allocation` |

#### 返品管理

| 画面ID | 画面名 | URL |
|--------|--------|-----|
| RTN-001 | 返品登録 | `/returns/new` |

#### レポート出力（各業務メニュー内ダイアログ）

| 画面ID | レポート名 | 呼出元画面 |
|--------|-----------|----------|
| RPT-001 | 入荷検品レポート出力 | INB-001, INB-003 |
| RPT-003 | 入荷予定レポート出力 | INB-001 |
| RPT-004 | 入庫実績レポート出力 | INB-006 |
| RPT-005 | 未入荷リスト（リアルタイム）出力 | INB-001 |
| RPT-006 | 未入荷リスト（確定）出力 | INB-001 |
| RPT-007 | 在庫一覧レポート出力 | INV-001 |
| RPT-008 | 在庫推移レポート出力 | INV-001 |
| RPT-009 | 在庫訂正一覧出力 | INV-004 |
| RPT-010 | 棚卸リスト出力 | INV-012, INV-013 |
| RPT-011 | 棚卸結果レポート出力 | INV-014 |
| RPT-012 | ピッキング指示書出力 | OUT-012, OUT-013 |
| RPT-013 | 出荷検品レポート出力 | OUT-021 |
| RPT-014 | 配送リスト出力 | OUT-001 |
| RPT-015 | 未出荷リスト（リアルタイム）出力 | OUT-001 |
| RPT-016 | 未出荷リスト（確定）出力 | OUT-001 |
| RPT-017 | 日次集計レポート出力 | BAT-002 |
| RPT-018 | 返品レポート出力 | RTN-001 |

### 3.2 API ID一覧

#### 認証（auth）

| API ID | メソッド | パス | API名 | 認証 | ロール |
|--------|---------|------|-------|:----:|-------|
| `API-AUTH-001` | `POST` | `/api/v1/auth/login` | ログイン | 不要 | — |
| `API-AUTH-002` | `POST` | `/api/v1/auth/logout` | ログアウト | 要 | 全ロール |
| `API-AUTH-003` | `POST` | `/api/v1/auth/refresh` | トークンリフレッシュ | 不要（refresh_token Cookie） | — |
| `API-AUTH-004` | `POST` | `/api/v1/auth/change-password` | パスワード変更 | 要 | 全ロール |
| `API-AUTH-005` | `POST` | `/api/v1/auth/password-reset/request` | パスワードリセット申請 | 不要 | — |
| `API-AUTH-006` | `POST` | `/api/v1/auth/password-reset/confirm` | パスワード再設定 | 不要 | — |

#### システム共通（system）

| API ID | メソッド | パス | API名 | 認証 | ロール |
|--------|---------|------|-------|:----:|-------|
| `API-SYS-001` | `GET` | `/api/v1/system/business-date` | 営業日取得 | 要 | 全ロール |
| `API-SYS-002` | `GET` | `/api/v1/system/parameters` | パラメータ一覧取得 | 要 | SYSTEM_ADMIN |
| `API-SYS-003` | `PUT` | `/api/v1/system/parameters/{paramKey}` | パラメータ値更新 | 要 | SYSTEM_ADMIN |

#### 商品マスタ（master/products）

| API ID | メソッド | パス | API名 | 認証 | ロール |
|--------|---------|------|-------|:----:|-------|
| `API-MST-PRD-001` | `GET` | `/api/v1/master/products` | 商品一覧取得 | 要 | 全ロール |
| `API-MST-PRD-002` | `POST` | `/api/v1/master/products` | 商品登録 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-MST-PRD-003` | `GET` | `/api/v1/master/products/{id}` | 商品取得 | 要 | 全ロール |
| `API-MST-PRD-004` | `PUT` | `/api/v1/master/products/{id}` | 商品更新 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-MST-PRD-005` | `PATCH` | `/api/v1/master/products/{id}/deactivate` | 商品無効化/有効化 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |

#### 取引先マスタ（master/partners）

| API ID | メソッド | パス | API名 | 認証 | ロール |
|--------|---------|------|-------|:----:|-------|
| `API-MST-PAR-001` | `GET` | `/api/v1/master/partners` | 取引先一覧取得 | 要 | 全ロール |
| `API-MST-PAR-002` | `POST` | `/api/v1/master/partners` | 取引先登録 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-MST-PAR-003` | `GET` | `/api/v1/master/partners/{id}` | 取引先取得 | 要 | 全ロール |
| `API-MST-PAR-004` | `PUT` | `/api/v1/master/partners/{id}` | 取引先更新 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-MST-PAR-005` | `PATCH` | `/api/v1/master/partners/{id}/deactivate` | 取引先無効化/有効化 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |

#### 施設マスタ（master/facilities）

**倉庫**

| API ID | メソッド | パス | API名 | 認証 | ロール |
|--------|---------|------|-------|:----:|-------|
| `API-MST-FAC-001` | `GET` | `/api/v1/master/warehouses` | 倉庫一覧取得 | 要 | 全ロール |
| `API-MST-FAC-002` | `POST` | `/api/v1/master/warehouses` | 倉庫登録 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-MST-FAC-003` | `GET` | `/api/v1/master/warehouses/{id}` | 倉庫取得 | 要 | 全ロール |
| `API-MST-FAC-004` | `PUT` | `/api/v1/master/warehouses/{id}` | 倉庫更新 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-MST-FAC-005` | `PATCH` | `/api/v1/master/warehouses/{id}/deactivate` | 倉庫無効化/有効化 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |

**棟**

| API ID | メソッド | パス | API名 | 認証 | ロール |
|--------|---------|------|-------|:----:|-------|
| `API-MST-FAC-011` | `GET` | `/api/v1/master/buildings` | 棟一覧取得 | 要 | 全ロール |
| `API-MST-FAC-012` | `POST` | `/api/v1/master/buildings` | 棟登録 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-MST-FAC-013` | `GET` | `/api/v1/master/buildings/{id}` | 棟取得 | 要 | 全ロール |
| `API-MST-FAC-014` | `PUT` | `/api/v1/master/buildings/{id}` | 棟更新 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-MST-FAC-015` | `PATCH` | `/api/v1/master/buildings/{id}/deactivate` | 棟無効化/有効化 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |

**エリア**

| API ID | メソッド | パス | API名 | 認証 | ロール |
|--------|---------|------|-------|:----:|-------|
| `API-MST-FAC-021` | `GET` | `/api/v1/master/areas` | エリア一覧取得 | 要 | 全ロール |
| `API-MST-FAC-022` | `POST` | `/api/v1/master/areas` | エリア登録 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-MST-FAC-023` | `GET` | `/api/v1/master/areas/{id}` | エリア取得 | 要 | 全ロール |
| `API-MST-FAC-024` | `PUT` | `/api/v1/master/areas/{id}` | エリア更新 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-MST-FAC-025` | `PATCH` | `/api/v1/master/areas/{id}/deactivate` | エリア無効化/有効化 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |

**ロケーション**

| API ID | メソッド | パス | API名 | 認証 | ロール |
|--------|---------|------|-------|:----:|-------|
| `API-MST-FAC-031` | `GET` | `/api/v1/master/locations` | ロケーション一覧取得 | 要 | 全ロール |
| `API-MST-FAC-032` | `POST` | `/api/v1/master/locations` | ロケーション登録 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-MST-FAC-033` | `GET` | `/api/v1/master/locations/{id}` | ロケーション取得 | 要 | 全ロール |
| `API-MST-FAC-034` | `PUT` | `/api/v1/master/locations/{id}` | ロケーション更新 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-MST-FAC-035` | `PATCH` | `/api/v1/master/locations/{id}/deactivate` | ロケーション無効化/有効化 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |

#### ユーザーマスタ（master/users）

| API ID | メソッド | パス | API名 | 認証 | ロール |
|--------|---------|------|-------|:----:|-------|
| `API-MST-USR-001` | `GET` | `/api/v1/master/users` | ユーザー一覧取得 | 要 | SYSTEM_ADMIN |
| `API-MST-USR-002` | `POST` | `/api/v1/master/users` | ユーザー登録 | 要 | SYSTEM_ADMIN |
| `API-MST-USR-003` | `GET` | `/api/v1/master/users/{id}` | ユーザー取得 | 要 | SYSTEM_ADMIN |
| `API-MST-USR-004` | `PUT` | `/api/v1/master/users/{id}` | ユーザー更新 | 要 | SYSTEM_ADMIN |
| `API-MST-USR-005` | `PATCH` | `/api/v1/master/users/{id}/deactivate` | ユーザー無効化/有効化 | 要 | SYSTEM_ADMIN |
| `API-MST-USR-006` | `PATCH` | `/api/v1/master/users/{id}/unlock` | アカウントロック解除 | 要 | SYSTEM_ADMIN |
| `API-MST-USR-007` | `GET` | `/api/v1/master/users/exists` | ユーザーコード存在確認 | 要 | SYSTEM_ADMIN |

#### 入荷管理（inbound）

| API ID | メソッド | パス | API名 | 認証 | ロール |
|--------|---------|------|-------|:----:|-------|
| `API-INB-001` | `GET` | `/api/v1/inbound/slips` | 入荷予定一覧取得 | 要 | 全ロール |
| `API-INB-002` | `POST` | `/api/v1/inbound/slips` | 入荷予定登録 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF |
| `API-INB-003` | `GET` | `/api/v1/inbound/slips/{id}` | 入荷予定詳細取得 | 要 | 全ロール |
| `API-INB-005` | `DELETE` | `/api/v1/inbound/slips/{id}` | 入荷予定削除 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF |
| `API-INB-006` | `POST` | `/api/v1/inbound/slips/{id}/confirm` | 入荷確認 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF |
| `API-INB-007` | `POST` | `/api/v1/inbound/slips/{id}/cancel` | 入荷キャンセル | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF |
| `API-INB-008` | `POST` | `/api/v1/inbound/slips/{id}/inspect` | 検品登録 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF |
| `API-INB-009` | `POST` | `/api/v1/inbound/slips/{id}/store` | 入庫確定 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF |
| `API-INB-010` | `GET` | `/api/v1/inbound/results` | 入荷実績照会 | 要 | 全ロール |

#### 在庫管理（inventory）

| API ID | メソッド | パス | API名 | 認証 | ロール |
|--------|---------|------|-------|:----:|-------|
| `API-INV-001` | `GET` | `/api/v1/inventory` | 在庫一覧照会 | 要 | 全ロール |
| `API-INV-002` | `POST` | `/api/v1/inventory/move` | 在庫移動登録 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF |
| `API-INV-003` | `POST` | `/api/v1/inventory/breakdown` | ばらし登録 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF |
| `API-INV-004` | `POST` | `/api/v1/inventory/correction` | 在庫訂正登録 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-INV-011` | `GET` | `/api/v1/inventory/stocktakes` | 棚卸一覧取得 | 要 | 全ロール |
| `API-INV-012` | `POST` | `/api/v1/inventory/stocktakes` | 棚卸開始 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-INV-013` | `GET` | `/api/v1/inventory/stocktakes/{id}` | 棚卸詳細取得 | 要 | 全ロール |
| `API-INV-014` | `PUT` | `/api/v1/inventory/stocktakes/{id}/lines` | 棚卸実数入力 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF |
| `API-INV-015` | `POST` | `/api/v1/inventory/stocktakes/{id}/confirm` | 棚卸確定 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF |

#### 出荷管理（outbound）

| API ID | メソッド | パス | API名 | 認証 | ロール |
|--------|---------|------|-------|:----:|-------|
| `API-OUT-001` | `GET` | `/api/v1/outbound/slips` | 受注一覧取得 | 要 | 全ロール |
| `API-OUT-002` | `POST` | `/api/v1/outbound/slips` | 受注登録 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF |
| `API-OUT-003` | `GET` | `/api/v1/outbound/slips/{id}` | 受注詳細取得 | 要 | 全ロール |
| `API-OUT-004` | `DELETE` | `/api/v1/outbound/slips/{id}` | 受注削除 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF |
| `API-OUT-005` | `POST` | `/api/v1/outbound/slips/{id}/cancel` | 受注キャンセル | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-OUT-011` | `GET` | `/api/v1/outbound/picking` | ピッキング指示一覧取得 | 要 | 全ロール |
| `API-OUT-012` | `POST` | `/api/v1/outbound/picking` | ピッキング指示作成（在庫引当） | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-OUT-013` | `GET` | `/api/v1/outbound/picking/{id}` | ピッキング指示詳細取得 | 要 | 全ロール |
| `API-OUT-014` | `PUT` | `/api/v1/outbound/picking/{id}/complete` | ピッキング完了登録 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF |
| `API-OUT-021` | `POST` | `/api/v1/outbound/slips/{id}/inspect` | 出荷検品登録 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF |
| `API-OUT-022` | `POST` | `/api/v1/outbound/slips/{id}/ship` | 出荷完了登録 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF |

#### 在庫引当（allocation）

| API ID | メソッド | パス | API名 | 認証 | ロール |
|--------|---------|------|-------|:----:|-------|
| `API-ALL-001` | `GET` | `/api/v1/allocation/orders` | 引当対象受注一覧取得 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-ALL-002` | `POST` | `/api/v1/allocation/execute` | 引当実行 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-ALL-003` | `GET` | `/api/v1/allocation/unpack-instructions` | ばらし指示一覧取得 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-ALL-004` | `POST` | `/api/v1/allocation/unpack-instructions/{id}/complete` | ばらし完了 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-ALL-005` | `GET` | `/api/v1/allocation/allocated-orders` | 引当済み受注一覧取得 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-ALL-006` | `POST` | `/api/v1/allocation/release` | 引当解放 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |

#### 返品管理（returns）

| API ID | メソッド | パス | API名 | 認証 | ロール |
|--------|---------|------|-------|:----:|-------|
| `API-RTN-001` | `POST` | `/api/v1/returns` | 返品登録 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF |
| `API-RTN-002` | `GET` | `/api/v1/returns` | 返品一覧取得 | 要 | 全ロール |

#### レポート（reports）

| API ID | メソッド | パス | API名 | 認証 | ロール |
|--------|---------|------|-------|:----:|-------|
| `API-RPT-001` | `GET` | `/api/v1/reports/inbound-inspection` | 入荷検品レポート | 要 | 全ロール |
| | | | ※ API-RPT-002は欠番 | | |
| `API-RPT-003` | `GET` | `/api/v1/reports/inbound-plan` | 入荷予定レポート | 要 | 全ロール |
| `API-RPT-004` | `GET` | `/api/v1/reports/inbound-result` | 入庫実績レポート | 要 | 全ロール |
| `API-RPT-005` | `GET` | `/api/v1/reports/unreceived-realtime` | 未入荷リスト（リアルタイム） | 要 | 全ロール |
| `API-RPT-006` | `GET` | `/api/v1/reports/unreceived-confirmed` | 未入荷リスト（確定） | 要 | 全ロール |
| `API-RPT-007` | `GET` | `/api/v1/reports/inventory` | 在庫一覧レポート | 要 | 全ロール |
| `API-RPT-008` | `GET` | `/api/v1/reports/inventory-transition` | 在庫推移レポート | 要 | 全ロール |
| `API-RPT-009` | `GET` | `/api/v1/reports/inventory-correction` | 在庫訂正一覧 | 要 | 全ロール |
| `API-RPT-010` | `GET` | `/api/v1/reports/stocktake-list` | 棚卸リスト | 要 | 全ロール |
| `API-RPT-011` | `GET` | `/api/v1/reports/stocktake-result` | 棚卸結果レポート | 要 | 全ロール |
| `API-RPT-012` | `GET` | `/api/v1/reports/picking-instruction` | ピッキング指示書 | 要 | 全ロール |
| `API-RPT-013` | `GET` | `/api/v1/reports/shipping-inspection` | 出荷検品レポート | 要 | 全ロール |
| `API-RPT-014` | `GET` | `/api/v1/reports/delivery-list` | 配送リスト | 要 | 全ロール |
| `API-RPT-015` | `GET` | `/api/v1/reports/unshipped-realtime` | 未出荷リスト（リアルタイム） | 要 | 全ロール |
| `API-RPT-016` | `GET` | `/api/v1/reports/unshipped-confirmed` | 未出荷リスト（確定） | 要 | 全ロール |
| `API-RPT-017` | `GET` | `/api/v1/reports/daily-summary` | 日次集計レポート | 要 | 全ロール |
| `API-RPT-018` | `GET` | `/api/v1/reports/returns` | 返品レポート出力 | 要 | 全ロール |

#### バッチ管理（batch）

| API ID | メソッド | パス | API名 | 認証 | ロール |
|--------|---------|------|-------|:----:|-------|
| `API-BAT-001` | `POST` | `/api/v1/batch/daily-close` | 日替処理実行 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-BAT-002` | `GET` | `/api/v1/batch/executions` | バッチ実行履歴一覧取得 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-BAT-003` | `GET` | `/api/v1/batch/executions/{id}` | バッチ実行履歴詳細取得（ポーリング用） | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |

#### I/F管理（interface）

| API ID | メソッド | パス | API名 | 認証 | ロール |
|--------|---------|------|-------|:----:|-------|
| `API-IF-001` | `GET` | `/api/v1/interface/{ifId}/files` | ファイル一覧取得 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-IF-002` | `POST` | `/api/v1/interface/{ifId}/validate` | バリデーション実行 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-IF-003` | `POST` | `/api/v1/interface/{ifId}/import` | 取り込み実行 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |
| `API-IF-004` | `GET` | `/api/v1/interface/executions` | 取り込み履歴一覧 | 要 | SYSTEM_ADMIN, WAREHOUSE_MANAGER |

### 3.3 レポートID一覧

| RPT ID | レポート名 | 設計書 | 対応API |
|--------|----------|--------|---------|
| RPT-01 | 入荷検品レポート | RPT-01-inbound-inspection.md | API-RPT-001 |
| | ※ RPT-02は欠番 | | |
| RPT-03 | 入荷予定レポート | RPT-03-inbound-plan.md | API-RPT-003 |
| RPT-04 | 入庫実績レポート | RPT-04-inbound-result.md | API-RPT-004 |
| RPT-05 | 未入荷リスト（リアルタイム） | RPT-05-unreceived-realtime.md | API-RPT-005 |
| RPT-06 | 未入荷リスト（確定） | RPT-06-unreceived-confirmed.md | API-RPT-006 |
| RPT-07 | 在庫一覧レポート | RPT-07-inventory.md | API-RPT-007 |
| RPT-08 | 在庫推移レポート | RPT-08-inventory-transition.md | API-RPT-008 |
| RPT-09 | 在庫訂正一覧 | RPT-09-inventory-correction.md | API-RPT-009 |
| RPT-10 | 棚卸リスト | RPT-10-stocktake-list.md | API-RPT-010 |
| RPT-11 | 棚卸結果レポート | RPT-11-stocktake-result.md | API-RPT-011 |
| RPT-12 | ピッキング指示書 | RPT-12-picking-instruction.md | API-RPT-012 |
| RPT-13 | 出荷検品レポート | RPT-13-shipping-inspection.md | API-RPT-013 |
| RPT-14 | 配送リスト | RPT-14-delivery-list.md | API-RPT-014 |
| RPT-15 | 未出荷リスト（リアルタイム） | RPT-15-unshipped-realtime.md | API-RPT-015 |
| RPT-16 | 未出荷リスト（確定） | RPT-16-unshipped-confirmed.md | API-RPT-016 |
| RPT-17 | 日次集計レポート | RPT-17-daily-summary.md | API-RPT-017 |
| RPT-18 | 返品レポート | RPT-18-returns.md | API-RPT-018 |

### 3.4 バッチID一覧

| BAT ID | バッチ名 | 設計書 | 対応API |
|--------|---------|--------|---------|
| BAT-001 | 日替処理 | BAT-01-daily-close.md | API-BAT-001 |

### 3.5 I/F ID一覧

| IFX ID | I/F名 | 設計書 | 方向 | 形式 |
|--------|-------|--------|------|------|
| IFX-001 | 入荷予定取り込み | IF-01-inbound-plan.md | 外部→WMS | CSV |
| IFX-002 | 受注取り込み | IF-02-order.md | 外部→WMS | CSV |

---

## 4. ID間の対応マップ

### 4.1 機能ID → 設計ID 対応表

| 機能IDプレフィックス | 画面ID | API ID | RPT ID | BAT ID | IFX ID |
|-------------------|--------|--------|--------|--------|--------|
| WMS-AUTH | AUTH-001〜004 | API-AUTH-001〜006 | — | — | — |
| WMS-MST | MST-001〜063 | API-MST-PRD/PAR/FAC/USR | — | — | — |
| WMS-INB | INB-001〜006 | API-INB-001〜010 | RPT-01, RPT-03〜06 | — | IFX-001 |
| WMS-INV | INV-001〜014 | API-INV-001〜015 | RPT-07〜11 | — | — |
| WMS-OUT | OUT-001〜022 | API-OUT-001〜022 | RPT-12〜16 | — | IFX-002 |
| WMS-ALO | ALL-001 | API-ALL-001〜006 | — | — | — |
| WMS-RTN | RTN-001 | API-RTN-001〜002 | RPT-18 | — | — |
| WMS-BAT | BAT-001〜002 | API-BAT-001〜003 | RPT-17 | BAT-001 | — |
| WMS-SYS | SYS-001 | API-SYS-001〜003 | — | — | — |
| WMS-RPT | RPT-001〜018 | API-RPT-001〜018 | RPT-01〜18 | — | — |
| WMS-IF | IF-001〜003 | API-IF-001〜004 | — | — | IFX-001〜002 |

---

## 5. 自動検証ルール（build-docs.js用）

| ルールID | チェック内容 | 重大度 |
|---------|-----------|--------|
| V-001 | 3.1の画面IDに対応するSCR-*.mdファイルが存在するか | ERROR |
| V-002 | 3.2のAPI IDに対応するAPI-*.mdファイル内にそのIDの定義があるか | ERROR |
| V-003 | SCR-*.mdのイベント一覧が参照するAPIパスが3.2に存在するか | WARNING |
| V-004 | 3.3のRPT IDに対応するRPT-*.mdファイルが存在するか | ERROR |
| V-005 | 1のファイル数と実際のファイル数が一致するか | WARNING |
| V-006 | 同一プレフィックス内でIDの重複がないか | ERROR |
| V-007 | 2の全プレフィックスに対応するIDが3に1つ以上存在するか | WARNING |
