# レビュー記録票: TST-MST-master-management.md

**レビュー実施日**: 2026-03-20
**対象ファイル**: docs/test-specifications/TST-MST-master-management.md
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 1 |
| 要対応（他ドキュメント） | 1 |
| 指摘なし | 6 |
| **合計** | **8** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | Part A 共通パラメタライズドテスト マスタ種別テーブル（倉庫・棟・エリア・ロケーションの「操作可能ロール」列） | API-02-master-facility.md（各APIの認可ロール）、07-auth-architecture.md（機能別アクセス権限マトリクス: マスタ管理（更新）= SYSTEM_ADMIN + WAREHOUSE_MANAGER） | 倉庫・棟・エリア・ロケーションの操作可能ロールが `SYSTEM_ADMIN` のみと記載されていたが、API設計書およびアーキテクチャブループリントの権限マトリクスでは `SYSTEM_ADMIN, WAREHOUSE_MANAGER` と定義されている。テスト仕様書のロールを修正した。 | 対象ファイル修正済み | `SYSTEM_ADMIN` → `SYSTEM_ADMIN, WAREHOUSE_MANAGER` に修正 |
| 2 | （対象外: 画面設計書） | SCR-04-master-warehouse.md（対象ロール: SYSTEM_ADMIN のみ）、SCR-02-master-facility.md（対象ロール: SYSTEM_ADMIN のみ） | 画面設計書（SCR-04, SCR-02）では倉庫・棟・エリア・ロケーション管理画面の対象ロールが `SYSTEM_ADMIN` のみと記載されているが、API設計書（API-02）および認可アーキテクチャ（07-auth-architecture.md 権限マトリクス）では `SYSTEM_ADMIN, WAREHOUSE_MANAGER` と定義されている。SSOT（07-auth-architecture.md）に従い、画面設計書側の修正が必要。 | 要対応（他ドキュメント） | SCR-04-master-warehouse.md および SCR-02-master-facility.md の対象ロールを `SYSTEM_ADMIN, WAREHOUSE_MANAGER` に修正する必要あり |
> **対応完了** (2026-03-20): SCR-04-master-warehouse.mdおよびSCR-02-master-facility.mdの対象ロールをSYSTEM_ADMIN, WAREHOUSE_MANAGERに修正完了
| 3 | Part A SC-C05〜SC-C12 DB検証 | data-model/02-master-tables.md | DB検証のカラム名（`is_active`, `version`, `created_by`, `updated_by`）がデータモデル定義と一致していることを確認。問題なし。 | 指摘なし | — |
| 4 | Part B SC-PRD01〜PRD05 | functional-requirements/01-master-management.md（商品マスタ ビジネスルール）、API-04-master-product.md | 商品マスタ固有シナリオ（ロット管理フラグ・賞味期限管理フラグの在庫存在時変更不可、在庫あり商品の無効化不可、出荷禁止フラグ切り替え）が要件・API設計と整合していることを確認。APIメソッド（PATCH）・エンドポイント・エラーコードも一致。 | 指摘なし | — |
| 5 | Part C SC-PAR01〜PAR05 | API-03-master-partner.md（API-MST-PAR-005 無効化/有効化 ビジネスルール）、functional-requirements/01-master-management.md | 取引先マスタ固有シナリオのステータスチェック（PLANNED/CONFIRMED/INSPECTING, PENDING/ALLOCATED/PICKING/INSPECTING）がAPI設計書のビジネスルールと一致。エラーコード（`CANNOT_DEACTIVATE_HAS_ACTIVE_INBOUND`, `CANNOT_DEACTIVATE_HAS_ACTIVE_OUTBOUND`）も正確。 | 指摘なし | — |
| 6 | Part D SC-WH01〜WH04 | functional-requirements/01-master-management.md（倉庫マスタ ビジネスルール）、API-02-master-facility.md | 倉庫マスタ固有シナリオ（在庫あり無効化不可、倉庫切替プルダウン動作、倉庫管理画面の独立性）が要件と一致。 | 指摘なし | — |
| 7 | Part E SC-FAC01〜FAC13 | functional-requirements/01-master-management.md（棟・エリア・ロケーション ビジネスルール）、data-model/02-master-tables.md、API-02-master-facility.md | 施設階層シナリオ（コード一意性スコープ、無効化制約、入荷/出荷/返品エリア1ロケーション制約、棚卸中無効化不可）が要件・API設計・データモデルと整合。エラーコード（`CANNOT_DEACTIVATE_HAS_CHILDREN`, `CANNOT_DEACTIVATE_STOCKTAKE_IN_PROGRESS`等）も正確。 | 指摘なし | — |
| 8 | Part F SC-USR01〜USR09 | functional-requirements/01-master-management.md（ユーザーマスタ ビジネスルール）、API-05-master-user.md、data-model/02-master-tables.md | ユーザーマスタ固有シナリオ（初期パスワード・パスワードバリデーション・自己ロール変更/無効化禁止・アカウントロック解除・初回ログインフラグ）が要件・API設計・データモデルと整合。SC-USR04/USR05でPUT APIを使用しているが、これはAPI-MST-USR-004（ユーザー更新）の仕様と一致。 | 指摘なし | — |
