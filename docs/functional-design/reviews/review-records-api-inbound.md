# API設計レビュー記録票 — 入荷管理（INB）

> 対象成果物: `docs/functional-design/11-api-inbound.md`
> レビュー日: 2026-03-14
> レビュー担当: Java バックエンド REST API設計スペシャリスト（AI）
> 参照ドキュメント: `docs/functional-design/08-api-overview.md`、`docs/functional-requirements/02-inbound-management.md`、`docs/data-model/03-transaction-tables.md`、`docs/functional-design/04-screen-inbound.md`、`docs/functional-design/review-records.md`

---

## エグゼクティブサマリー

| 分類 | 件数 |
|------|------|
| **API設計書修正済**（レビュー時に自動修正） | 5件 |
| **要対応**（他ドキュメントへの変更が必要） | 2件 |
| 指摘事項なし | 8件 |
| **総チェック項目** | 15件 |

---

| No | 対象API | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|--------|----------------|---------|------|---------|
| 1 | API-INB-001〜008, cancel | `08-api-overview.md` テンプレート | 全APIが5セクション形式（概要/リクエスト/レスポンス/業務ロジック/補足）に準拠していることを確認 | 指摘事項なし | — |
| 2 | API-INB-001〜008, cancel | `08-api-overview.md` 全API一覧 | メソッド・パス・ロールが全API一覧の定義と一致していることを確認 | 指摘事項なし | — |
| 3 | API-INB-001〜008, cancel | `03-transaction-tables.md` inbound_slipsテーブル | リクエスト/レスポンスのフィールド名がDBカラム名（スネークケース→キャメルケース変換）と整合していることを確認 | 指摘事項なし | — |
| 4 | API-INB-cancel | `02-inbound-management.md` §5 入荷キャンセル | 機能要件書§5では「入庫完了以外のステータスをキャンセル可」と定義しているが、API設計書では「`INSPECTING` 以降はキャンセル不可」と誤って制限していた。また `PARTIAL_STORED` キャンセル時の在庫ロールバック処理が完全に欠落していた | **API設計書修正済** | 1) ステータス遷移図に `INSPECTING → CANCELLED` / `PARTIAL_STORED → CANCELLED` を追加。2) API概要を「STORED/CANCELLED以外はキャンセル可」に変更。3) エラー条件を修正（STORED/CANCELLEDのみ不可）。4) フローチャートに PARTIAL_STORED 分岐と在庫ロールバック（`inventories UPDATE`・`inventory_movements INSERT movement_type=INBOUND_CANCEL`）を追加。5) ビジネスルール表を修正（ルール3・4追加）済 |
| 5 | API-INB-007 | `08-api-overview.md` エラーコード一覧 | エラーコード一覧に定義済みの `INBOUND_LOCATION_AREA_MISMATCH`（入荷エリア不一致）が API-INB-007 のフローチャートおよびビジネスルール表に一切記載されておらず、実装時に入荷エリアチェックが漏れる可能性があった | **API設計書修正済** | フローチャートに `CHECK_AREA[入荷エリアチェック area_type=INBOUND]` → `ERR_AREA[422 INBOUND_LOCATION_AREA_MISMATCH]` ノードを追加。エラーレスポンス表に `422 INBOUND_LOCATION_AREA_MISMATCH` 追加。ビジネスルール3として「指定ロケーションは入荷エリア（`areas.area_type = INBOUND`）に属していなければならない」を追加済（旧ルール4→5〜9に繰り下げ） |
| 6 | API-INB-002 | `review-records.md` I-12 | 画面設計書レビュー記録 I-12 の「API設計時に仕様確認が必要」アクションアイテムに対し、`warehouseId` の入力元（グローバルヘッダー選択中倉庫の自動セット）がAPI設計書の補足事項に未記載だった | **API設計書修正済** | 補足事項に「`warehouseId` はフロントエンドのグローバルヘッダーで選択中の倉庫IDを自動セットする」旨を追記済（review-records.md I-12 クローズ） |
| 7 | API-INB-004 | `02-inbound-management.md` ビジネスルール「修正不可」 | `PLANNED` ステータスのみ更新可能な設計根拠（`CONFIRMED` 以降は業務が進行中のため修正不可）が補足事項に未記載だった | **API設計書修正済** | 補足事項に「更新可能なのは `PLANNED` ステータスの伝票のみ。`CONFIRMED` 以降は業務上の操作が進んでいるため修正不可とし、修正が必要な場合はキャンセルして再登録する（要件定義書ビジネスルール「修正不可」に準拠）」を追記済 |
| 8 | API-INB-001〜008, cancel | `08-api-overview.md` 認証・認可仕様 | ロール別アクセス権限マトリクスとの整合を確認。参照系は全ロール、更新系はSYSTEM_ADMIN/WAREHOUSE_MANAGER/WAREHOUSE_STAFF、VIEWERは参照のみ | 指摘事項なし | — |
| 9 | API-INB-002 | `02-inbound-management.md` §1 管理項目 | 伝票番号の自動採番形式（`INB-YYYYMMDD-NNNN`）が業務要件と整合していることを確認 | 指摘事項なし | — |
| 10 | API-INB-006 | `02-inbound-management.md` §6 入荷検品 | 「入荷数の初期値は入荷予定数と同数にプリセット」する設計についての責務がフロントエンド側にあることが補足事項に明記されていることを確認（APIは変更がある行のみ受け取る設計） | 指摘事項なし | — |
| 11 | API-INB-007 | `02-inbound-management.md` §7 入庫指示・確定 | ロケーション自動割当（同一商品の既存在庫ロケーション → 空きロケーション優先）の候補表示は別途 `GET /api/v1/inventory/location-suggestions` 等で対応する想定。本APIは確定操作のみ | 指摘事項なし | — |
| 12 | API-INB-007 | `03-transaction-tables.md` inventory_movementsテーブル | `movement_type = INBOUND` として `inventory_movements` に明細単位でレコードを追記する設計がデータモデルと整合していることを確認 | 指摘事項なし | — |
| 13 | API-INB-cancel | `03-transaction-tables.md` inventory_movementsテーブル | `PARTIAL_STORED` キャンセル時の在庫ロールバックで `movement_type = INBOUND_CANCEL` を使用する設計が整合性を保てることを確認（追記専用テーブル方針に準拠） | 指摘事項なし | — |
| 14 | API-INB-002/004/006/007 | `08-api-overview.md` エラーコード一覧 | 全エラーコード（INBOUND_SLIP_NOT_FOUND, INBOUND_INVALID_STATUS, INBOUND_LINE_NOT_FOUND, INBOUND_LINE_NOT_INSPECTED, INBOUND_PARTNER_NOT_SUPPLIER, INBOUND_LOCATION_AREA_MISMATCH, DUPLICATE_PRODUCT_IN_LINES, PLANNED_DATE_TOO_EARLY, LOT_NUMBER_REQUIRED, EXPIRY_DATE_REQUIRED）が一覧に定義済みであることを確認 | 指摘事項なし | — |
| 15 | API-INB-001〜008 | `03-transaction-tables.md` inbound_slip_linesテーブル | 明細の `line_status` 遷移（PENDING → INSPECTED → STORED）がAPI設計書のステータス遷移図と整合していることを確認 | 指摘事項なし | — |

---

## 修正対応ログ

### No.4 対応詳細
**修正対象**: `docs/functional-design/11-api-inbound.md` キャンセルAPI（API-INB-cancel）

**修正理由**: 機能要件書（02-inbound-management.md §5）の定義：
- 「入庫完了（STORED）以外のステータスの入荷予定をキャンセルできる」
- キャンセル時の在庫戻し: PARTIAL_STOREDの場合は在庫減算ロールバック、INSPECTING以下は影響なし

**修正箇所1**: ステータス遷移図
- 追加: `INSPECTING --> CANCELLED : キャンセル（API-INB-cancel）`
- 追加: `PARTIAL_STORED --> CANCELLED : キャンセル（API-INB-cancel）`

**修正箇所2**: API概要テーブル
- 修正前: `PLANNED` または `CONFIRMED` 状態の入荷予定伝票を `CANCELLED` に遷移させる。`INSPECTING` 以降の伝票はキャンセル不可。
- 修正後: `STORED`（入庫完了）および `CANCELLED` 以外の入荷予定伝票を `CANCELLED` に遷移させる。`PARTIAL_STORED` の場合は入庫確定済み在庫を戻す処理を行う。

**修正箇所3**: エラーレスポンス表
- 修正前: `409 INBOUND_INVALID_STATUS | ステータスが PLANNED / CONFIRMED でない`
- 修正後: `409 INBOUND_INVALID_STATUS | ステータスが STORED（入庫完了）または CANCELLED（キャンセル済）`

**修正箇所4**: フローチャート
- `CHECK{status IN PLANNED, CONFIRMED?}` → `CHECK{status IN PLANNED, CONFIRMED, INSPECTING, PARTIAL_STORED?}`
- PARTIAL_STORED 分岐を追加し、在庫ロールバック処理（`inventories UPDATE quantity -= stored_qty`、`inventory_movements INSERT movement_type=INBOUND_CANCEL`）を追加

**修正箇所5**: ビジネスルール表
- ルール1修正: INSPECTING/PARTIAL_STORED/STOREDはキャンセル不可 → STORED/CANCELLEDのみキャンセル不可
- ルール3追加: PARTIAL_STOREDキャンセル時の在庫ロールバック処理
- ルール4追加: PLANNED/CONFIRMED/INSPECTINGキャンセルは在庫影響なし

### No.5 対応詳細
**修正対象**: `docs/functional-design/11-api-inbound.md` API-INB-007 業務ロジック

- エラーレスポンス表に `422 INBOUND_LOCATION_AREA_MISMATCH` 追加
- フローチャートに `CHECK_AREA[入荷エリアチェック area_type=INBOUND]` → `ERR_AREA[422 INBOUND_LOCATION_AREA_MISMATCH]` を CHECK_LOC と CHECK_STOCKTAKE の間に挿入
- ビジネスルール3として「指定ロケーションは入荷エリア（`areas.area_type = INBOUND`）に属していなければならない」を追加（旧ルール4〜8を5〜9に繰り下げ）

### No.6 対応詳細
**修正対象**: `docs/functional-design/11-api-inbound.md` API-INB-002 補足事項

- 補足事項に以下を追記:
  「`warehouseId` はフロントエンドのグローバルヘッダーで選択中の倉庫IDを自動セットする（ユーザーが直接入力しない）。これにより、選択倉庫に紐づく入荷予定が自動登録される（画面設計書レビュー記録 I-12 対応）。」

### No.7 対応詳細
**修正対象**: `docs/functional-design/11-api-inbound.md` API-INB-004 補足事項

- 補足事項に以下を追記:
  「更新可能なのは `PLANNED` ステータスの伝票のみ。`CONFIRMED` 以降は業務上の操作（確認・検品・入庫）が進んでいるため修正不可とし、修正が必要な場合はキャンセルして再登録する（要件定義書ビジネスルール「修正不可」に準拠）。」

---

## 要件定義書・アーキテクチャ設計書 修正要 アクションアイテム

| # | 修正対象ドキュメント | 修正箇所 | 修正内容 | 優先度 | 確認結果 |
|---|-------------------|---------|---------|-------|--------|
| 1 | `docs/data-model/03-transaction-tables.md` | inventory_movements テーブル `movement_type` 定義 | `INBOUND_CANCEL`（入荷キャンセル時の在庫ロールバック）を movement_type の有効値として追加すること | 高 | ✅ 対応完了（2026-03-18確認） |
| 2 | `docs/functional-design/SCR-07-inbound.md` | INB-003 キャンセルボタン挙動 | `PARTIAL_STORED` ステータスのキャンセル時に在庫ロールバックが発生する旨の警告ダイアログを表示するイベント定義を追加すること（ユーザーに在庫変動を周知するため） | 中 | ✅ 対応完了（2026-03-18確認） |
