# API設計レビュー記録票 — 在庫管理（INV）

> 対象成果物: `docs/functional-design/12-api-inventory.md`
> レビュー日: 2026-03-14
> レビュー担当: Java バックエンド REST API設計スペシャリスト（AI）
> 参照ドキュメント: `docs/functional-design/08-api-overview.md`、`docs/functional-requirements/03-inventory-management.md`、`docs/data-model/03-transaction-tables.md`、`docs/functional-design/05-screen-inventory-ops.md`、`docs/functional-design/05-screen-inventory-stocktake.md`

---

| No | 対象API | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|--------|----------------|---------|------|---------|
| 1 | 全体 | 03-transaction-tables.md | `inventory_movements` テーブルのカラム名が不整合。設計書では `created_at` / `created_by` と記載していたが、データモデル定義では `executed_at` / `executed_by` が正しいカラム名。全APIの記録内容表にも影響しないが、テーブルリファレンスの定義として修正が必要。 | バグ | 対応済（テーブルリファレンスを修正） |
| 2 | 全体（棚卸API） | — | 設計書全体にわたり「棟卸」という誤記が多数存在（「棚卸」が正しい）。検索すると約80箇所以上の誤記が確認された。 | 誤記 | 対応済（全箇所を「棚卸」に修正） |
| 3 | API-INV-011 | 08-api-overview.md | セクション4「業務ロジック」にMermaidフローチャートはあるが、ビジネスルール表が欠落していた。他のAPIとフォーマットを統一する必要がある。 | フォーマット不備 | 対応済（ビジネスルール表を追加） |
| 4 | API-INV-012 | 03-transaction-tables.md | `stocktake_headers.target_description` カラムについて、設計書テーブルリファレンスではNOT NULLとして記載されていたが、データモデル定義（03-transaction-tables.md）ではNULL可（`NULL | —`）が正しい。棟全体指定の場合に自動生成されるため必ずしもNULLにならないが、定義の整合を取る必要がある。 | データモデル不整合 | 対応済（NULL可に修正） |
| 5 | API-INV-013 | 05-screen-inventory-stocktake.md | 「関連画面」フィールドに `INV-012（棟卸実施）、INV-013（棟卸確定）` と記載されていたが、画面IDが誤り。正しくは `INV-013（棚卸実施・実数入力）、INV-014（棚卸確定）` 。 | 誤記 | 対応済（関連画面記述を修正） |
| 6 | API-INV-011, API-INV-012 | 03-inventory-management.md | 棚卸明細の上限2,000行（機能要件定義書に明記）に関する記載がAPI設計書にほぼなかった。運用上の制約として補足事項に記載すべき。 | 要件カバレッジ不足 | 対応済（API-INV-011補足事項、API-INV-012補足事項に追記） |
| 7 | API-INV-004 | 08-api-overview.md | 08-api-overview.mdの「在庫管理（移動・訂正）」ロール列に `WAREHOUSE_STAFF` が含まれているが、API-INV-004は `WAREHOUSE_MANAGER` のみが在庫訂正可能と設計書で明示している。また画面設計書（05-screen-inventory-ops.md INV-004）も対象ロールは `WAREHOUSE_MANAGER` のみ。API設計書側は正しい（より詳細なロール制限）が、08-api-overview.mdの一覧表との食い違いが発生している。 | 他ドキュメントとの不整合 | ✅ 対応完了（2026-03-18確認）API-07-inventory.md（SSOT）にて対象ロール `SYSTEM_ADMIN, WAREHOUSE_MANAGER（WAREHOUSE_STAFFは不可）` と正しく定義済み |
| 8 | API-INV-012 | 08-api-overview.md | 08-api-overview.mdの全API一覧では `API-INV-012` のロールが `SYSTEM_ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF` となっているが、API設計書では `WAREHOUSE_MANAGER（WAREHOUSE_STAFFは不可）` と定義している。機能要件・画面設計ともに WAREHOUSE_MANAGER 専権であるため、API設計書の定義が正しい。 | 他ドキュメントとの不整合 | ✅ 対応完了（2026-03-18確認）API-07-inventory.md（SSOT）にて対象ロール `SYSTEM_ADMIN, WAREHOUSE_MANAGER（WAREHOUSE_STAFFは不可）` と正しく定義済み |
| 9 | API-INV-002, API-INV-003 | 08-api-overview.md | `INVENTORY_CAPACITY_EXCEEDED`（422）エラーコードが在庫移動・ばらしで使用されているが、08-api-overview.mdの共通エラーコード一覧に記載がない。在庫管理ローカルのエラーコード一覧（12-api-inventory.md末尾）には記載されているが、共通エラーコード一覧への追記が望ましい。 | 他ドキュメントとの不整合 | ✅ 対応完了（2026-03-18確認）API-07-inventory.md エラーコード一覧に `INVENTORY_CAPACITY_EXCEEDED`（422）記載済み |
| 10 | API-INV-015 | 08-api-overview.md | `INVENTORY_STOCKTAKE_NOT_ALL_COUNTED`（409）エラーコードが使用されているが、08-api-overview.mdの共通エラーコード一覧に記載がない。在庫管理ローカルのエラーコード一覧には記載されているが、共通一覧への追記が望ましい。 | 他ドキュメントとの不整合 | ✅ 対応完了（2026-03-18確認）API-07-inventory.md エラーコード一覧に `INVENTORY_STOCKTAKE_NOT_ALL_COUNTED`（409）記載済み |
| 11 | API-INV-011 | 05-screen-inventory-stocktake.md | 画面設計書（INV-011のイベント一覧）では `GET /api/v1/stocktakes?page=1&size=20` と記載されているが、APIパスは `/api/v1/inventory/stocktakes` が正しい。画面設計書側の誤記。 | 他ドキュメントとの不整合 | ✅ 対応完了（2026-03-18修正）APIパス・ページ番号（`page=0`）ともに修正済み |
| 12 | API-INV-015 | 05-screen-inventory-stocktake.md | 画面設計書（INV-014確定画面）のイベント一覧では `POST /api/v1/stocktakes/:id/finalize` と記載されているが、API-INV-015のパスは `POST /api/v1/inventory/stocktakes/{id}/confirm` が正しい。パス名・動詞が異なる。 | 他ドキュメントとの不整合 | ✅ 対応完了（2026-03-18確認）SCR-09-inventory-stocktake.md にて `POST /api/v1/inventory/stocktakes/:id/confirm` に修正済み |
| 13 | API-INV-002, API-INV-003 | 08-api-overview.md | `INVENTORY_INSUFFICIENT` のHTTPステータスコードが `409` になっているが、08-api-overview.mdの共通エラーコード一覧では `422`（業務制約違反）と定義されている。データ競合ではなくビジネスルール違反のため422が正しい。 | バグ | 対応済（エラーレスポンス表・エラーコード一覧を409→422に修正） |
| 14 | API-INV-004 | 05-screen-inventory-ops.md | `reason`（訂正理由）フィールドの文字数上限がAPI設計書では「500文字」となっているが、画面設計書（INV-004 COR-REASON項目）では「200文字以内」と定義されている。画面バリデーションとAPI側のバリデーションを一致させる必要がある。 | 画面/API不整合 | 対応済（リクエスト仕様を1〜200文字に修正。エラー条件・補足事項にも200文字制限を追記） |

---

## 修正対応ログ

| No | 修正内容 | 修正箇所 | 修正日 |
|----|---------|---------|-------|
| 1 | `inventory_movements` テーブルリファレンスの `created_at`/`created_by` を `executed_at`/`executed_by` に修正 | 12-api-inventory.md テーブルリファレンス | 2026-03-14 |
| 2 | 「棟卸」→「棚卸」誤記を全箇所修正（約80箇所以上） | 12-api-inventory.md 全体 | 2026-03-14 |
| 3 | API-INV-011 セクション4にビジネスルール表を追加 | 12-api-inventory.md API-INV-011 | 2026-03-14 |
| 4 | `stocktake_headers.target_description` の NULL制約をNULL可に修正 | 12-api-inventory.md テーブルリファレンス | 2026-03-14 |
| 5 | API-INV-013 関連画面を `INV-013（棚卸実施・実数入力）、INV-014（棚卸確定）` に修正 | 12-api-inventory.md API-INV-013 | 2026-03-14 |
| 6 | API-INV-011/012 補足事項に明細上限2,000行の記載を追加 | 12-api-inventory.md API-INV-011, API-INV-012 | 2026-03-14 |
| 7 | `INVENTORY_INSUFFICIENT` HTTPステータスを 409 → 422 に修正（エラーレスポンス表×2、エラーコード一覧×1） | 12-api-inventory.md API-INV-002, API-INV-003, エラーコード一覧 | 2026-03-14 |
| 8 | API-INV-004 `reason` フィールドの文字数上限を 500文字 → 200文字 に修正（リクエスト仕様・エラー条件・補足事項） | 12-api-inventory.md API-INV-004 | 2026-03-14 |

---

## 要件定義書・アーキテクチャ設計書 修正要 アクションアイテム

| # | 修正対象ドキュメント | 修正箇所 | 修正内容 | 優先度 |
|---|-------------------|---------|---------|-------|
| 1 | `docs/functional-design/08-api-overview.md` | 全API一覧 > 在庫管理 > API-INV-004 | ロール列を `SYSTEM_ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF` → `SYSTEM_ADMIN, WAREHOUSE_MANAGER` に修正。在庫訂正はWAREHOUSE_STAFFには権限なし。 | 高 | **対応済**（08-api-overview.md修正確認） |
| 2 | `docs/functional-design/08-api-overview.md` | 全API一覧 > 在庫管理 > API-INV-012 | ロール列を `SYSTEM_ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF` → `SYSTEM_ADMIN, WAREHOUSE_MANAGER` に修正。棚卸開始はWAREHOUSE_STAFFには権限なし。 | 高 | **対応済**（08-api-overview.md修正確認） |
| 3 | `docs/functional-design/08-api-overview.md` | エラーコード一覧 > 在庫管理 | `INVENTORY_CAPACITY_EXCEEDED`（422: ロケーション収容数上限超過）を追加 | 中 | **対応済**（08-api-overview.md追加確認） |
| 4 | `docs/functional-design/08-api-overview.md` | エラーコード一覧 > 在庫管理 | `INVENTORY_STOCKTAKE_NOT_ALL_COUNTED`（409: 未入力の実数がある）を追加 | 中 | **対応済**（08-api-overview.md追加確認） |
| 5 | `docs/functional-design/SCR-09-inventory-stocktake.md`（旧05-screen-inventory-stocktake.md） | INV-011 イベント一覧 > EVT-INV011-001 | APIパスを `GET /api/v1/stocktakes?page=1&size=20` → `GET /api/v1/inventory/stocktakes?page=0&size=20` に修正（パスとページ番号0始まりの誤記） | 中 | ✅ 対応完了（2026-03-18修正） |
| 6 | `docs/functional-design/SCR-09-inventory-stocktake.md`（旧05-screen-inventory-stocktake.md） | INV-014 イベント一覧 > EVT-INV014-005 | APIパスを `POST /api/v1/stocktakes/:id/finalize` → `POST /api/v1/inventory/stocktakes/{id}/confirm` に修正（パス名・エンドポイント名の誤記） | 中 | ✅ 対応完了（2026-03-18確認） |
| 7 | `docs/functional-design/SCR-09-inventory-stocktake.md`（旧05-screen-inventory-stocktake.md） | INV-012 イベント一覧 > EVT-INV012-006 | APIパスを `POST /api/v1/stocktakes` → `POST /api/v1/inventory/stocktakes` に修正 | 中 | ✅ 対応完了（2026-03-18確認） |
| 8 | `docs/data-model/03-transaction-tables.md` | inventories テーブル | 楽観的ロック（`@Version`）の注記は存在するが、`version` カラム自体がテーブル定義に記載されていない。Javaエンティティ側の `@Version` アノテーション対象カラムとして `version bigint NOT NULL DEFAULT 0` を追加すること。 | 低 | ✅ 対応完了（2026-03-18確認） |
