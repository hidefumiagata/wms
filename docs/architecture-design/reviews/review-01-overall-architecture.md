# アーキテクチャ設計レビュー記録票 — 全体アーキテクチャ

> 対象成果物: `docs/architecture-design/01-overall-architecture.md`
> レビュー日: 2026-03-18
> レビュー担当: エンタープライズ全体アーキテクチャ設計スペシャリスト（AI）
> 参照ドキュメント:
> - `docs/architecture-blueprint/01-overall-architecture.md`（モジュラーモノリス方針、モジュール構成）
> - `docs/architecture-blueprint/02-system-architecture.md`（Azure構成、通信フロー）
> - `docs/architecture-blueprint/03-frontend-architecture.md`（フロントエンドアーキテクチャ）
> - `docs/architecture-blueprint/04-backend-architecture.md`（3層アーキテクチャ、DTO規約）
> - `docs/architecture-blueprint/05-database-architecture.md`（ロック方式、監査カラム）
> - `docs/architecture-blueprint/06-infrastructure-architecture.md`（インフラ構成）
> - `docs/architecture-blueprint/07-auth-architecture.md`（認証・認可方式）
> - `docs/architecture-blueprint/08-common-infrastructure.md`（エラーハンドリング、ロギング）
> - `docs/architecture-blueprint/09-interface-architecture.md`（外部連携方式）
> - `docs/architecture-blueprint/10-security-architecture.md`（セキュリティ方針）
> - `docs/architecture-blueprint/12-development-deploy.md`（開発・デプロイ方針）
> - `docs/architecture-blueprint/13-non-functional-requirements.md`（非機能要件）
> - `docs/functional-requirements/00-authentication.md`（認証機能要件）
> - `docs/functional-requirements/01-master-management.md`（マスタ管理機能要件）
> - `docs/functional-requirements/01a-system-parameters.md`（システムパラメータ機能要件）
> - `docs/functional-requirements/02-inbound-management.md`（入荷管理機能要件）
> - `docs/functional-requirements/03-inventory-management.md`（在庫管理機能要件）
> - `docs/functional-requirements/04-outbound-management.md`（出荷管理機能要件）
> - `docs/functional-requirements/04a-allocation.md`（在庫引当機能要件）
> - `docs/functional-requirements/05-reports.md`（レポート機能要件）
> - `docs/functional-requirements/06-batch-processing.md`（バッチ処理機能要件）
> - `docs/functional-requirements/07-interface.md`（外部連携機能要件）
> - `docs/data-model/01-overview.md`（データモデル概要）

---

## エグゼクティブサマリー

| 分類 | 件数 |
|------|------|
| **設計書修正済**（レビュー時に自動修正） | 1件 |
| **要対応**（他ドキュメントへの変更が必要） | 0件 |
| 指摘事項なし | 19件 |
| **総チェック項目** | 20件 |

---

| No | チェック観点 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|------------|----------------|---------|------|---------|
| 1 | モジュラーモノリス方針との整合性 | blueprint/01 | 設計書のモジュール構成（10モジュール）がブループリント定義と完全一致 | 指摘事項なし | - |
| 2 | レイヤー構成の整合性 | blueprint/04 | 3層アーキテクチャ（Controller/Service/Repository）がブループリント方針に準拠 | 指摘事項なし | - |
| 3 | モジュール間依存ルールの整合性 | blueprint/04 | Service直接呼び出し許可、Repository/Controller直接参照禁止がブループリントと一致 | 指摘事項なし | - |
| 4 | DTO規約の整合性 | blueprint/04 | 命名規則（Create/Update/Response）、手動マッピング方式がブループリントと一致 | 指摘事項なし | - |
| 5 | ロック方式の整合性 | blueprint/05 | 在庫引当に悲観的ロック、マスタに楽観的ロックの使い分けがブループリントと一致 | 指摘事項なし | - |
| 6 | 監査カラムの整合性 | blueprint/05, data-model/01 | BaseEntity の `created_at/by`, `updated_at/by` がデータモデル定義の共通カラムと一致 | 指摘事項なし | - |
| 7 | エラーハンドリングの整合性 | blueprint/08 | 例外クラス階層（WmsException系5クラス）がブループリント定義と完全一致 | 指摘事項なし | - |
| 8 | 認証フローの整合性 | blueprint/07, blueprint/10 | JWT + httpOnly Cookie、Spring Security + @PreAuthorizeがブループリント方針と一致 | 指摘事項なし | - |
| 9 | 営業日制御の整合性 | blueprint/01, blueprint/08, FR/06 | キャッシュなし都度取得、冪等性確保がブループリントおよび機能要件と一致 | 指摘事項なし | - |
| 10 | 入荷管理機能要件の実現可能性 | FR/02 | ステータス遷移（入荷予定→入荷確認済→検品中→一部入庫→入庫完了）がEntity内遷移マップで正しく定義されている | 指摘事項なし | - |
| 11 | 在庫管理機能要件の実現可能性 | FR/03 | 5軸在庫管理（ロケーション×商品×荷姿×ロット×期限日）がRepository悲観的ロッククエリに正しく反映。NULL対応もIS NULL条件で実装 | 指摘事項なし | - |
| 12 | 在庫引当機能要件の実現可能性 | FR/04a | AllocationServiceの設計がFIFO/FEFO引当、ばらし指示自動生成、部分引当の要件を満たす構造 | 指摘事項なし | - |
| 13 | 出荷管理機能要件の実現可能性 | FR/04 | OutboundServiceのモジュール間連携（allocation, inventory）が出荷フロー（引当→ピッキング→検品→出荷確定）を実現可能 | 指摘事項なし | - |
| 14 | バッチ処理機能要件の実現可能性 | FR/06 | DailyCloseServiceのステップ分割設計が、途中失敗→未完了ステップから再開の要件を満たす | 指摘事項なし | - |
| 15 | 外部連携機能要件の実現可能性 | FR/07, blueprint/09 | interfacingモジュールのCsvValidation/CsvImportServiceがステートレス2ステップ方式に対応 | 指摘事項なし | - |
| 16 | 倉庫コンテキストの設計整合性 | FR/01 | Axiosインターセプターによる倉庫ID自動付与が機能要件の倉庫切替仕様と整合。適用除外APIリストも妥当 | 指摘事項なし | - |
| 17 | 他セクションとの関係マップの網羅性 | 全ブループリント | 13セクション（02〜13）との責務分担が明確に定義されている。各セクションで「何を定義するか」が一意に決定可能 | 指摘事項なし | - |
| 18 | ADRの網羅性 | 全ブループリント | 主要な設計判断7件をADR形式で記録。ブループリントで明示された判断（モジュラーモノリス、Service直接呼び出し、楽観的ロック通知方式、営業日キャッシュなし、DTO手動マッピング、悲観的ロック、バッチTX分割）を網羅 | 指摘事項なし | - |
| 19 | SSOT違反の有無 | CLAUDE.md | ブループリントの内容をそのままコピーしていない。方針はブループリントへの参照リンクで示し、本書は実装方法に専念している。テーブル定義はdata-modelへの参照に留め、複製していない | 指摘事項なし | - |
| 20 | パッケージ構成の一貫性 | 設計書内部 | `shared/util/WarehouseContextHolder.java` がパッケージ一覧にあるが、セクション8.2で「暗黙的なスレッドローカル管理は行わず、明示的にパラメータとして渡す」と設計しており矛盾。パッケージ一覧から `WarehouseContextHolder.java` を削除して修正 | 設計書修正済 | 修正完了 |
