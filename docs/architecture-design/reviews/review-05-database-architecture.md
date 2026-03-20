# アーキテクチャ設計レビュー記録票 — データベースアーキテクチャ

> 対象成果物: `docs/architecture-design/05-database-architecture.md`
> レビュー日: 2026-03-18
> レビュー担当: データベースアーキテクチャ設計スペシャリスト（AI）
> 参照ドキュメント:
> - `docs/architecture-blueprint/01-overall-architecture.md`（全体アーキテクチャ方針）
> - `docs/architecture-blueprint/04-backend-architecture.md`（バックエンドアーキテクチャ）
> - `docs/architecture-blueprint/05-database-architecture.md`（データベースアーキテクチャ・ブループリント）
> - `docs/data-model/01-overview.md`（データモデル概要・設計方針）
> - `docs/data-model/02-master-tables.md`（マスタ系テーブル定義）
> - `docs/data-model/03-transaction-tables.md`（トランザクション系テーブル定義）
> - `docs/data-model/04-batch-tables.md`（バッチ・集計・バックアップ系テーブル定義）
> - `docs/functional-requirements/00-authentication.md`（認証・ログイン）
> - `docs/functional-requirements/01-master-management.md`（マスタ管理）
> - `docs/functional-requirements/01a-system-parameters.md`（システムパラメータ管理）
> - `docs/functional-requirements/02-inbound-management.md`（入荷管理）
> - `docs/functional-requirements/03-inventory-management.md`（在庫管理）
> - `docs/functional-requirements/04-outbound-management.md`（出荷管理）
> - `docs/functional-requirements/04a-allocation.md`（在庫引当）
> - `docs/functional-requirements/05-reports.md`（レポート）
> - `docs/functional-requirements/06-batch-processing.md`（バッチ処理）
> - `docs/functional-requirements/07-interface.md`（外部連携I/F）
> - `CLAUDE.md`（プロジェクト指針）

---

## エグゼクティブサマリー

本設計書はWMSプロジェクトのデータベース物理設計・運用設計を網羅的に記述している。Azure Database for PostgreSQL Flexible Server（B1ms）のリソース制約を踏まえた実践的なパラメータ設計、全テーブルのインデックス戦略、HikariCPコネクションプール設計、Flywayマイグレーション設計、バックアップ・リストア設計、パフォーマンスチューニング方針を含む。SSOTルールに準拠し、テーブル定義の複製を避けてデータモデル定義書への参照リンクを使用している。

全体として設計品質は良好であり、ブループリントの方針と矛盾する内容は検出されなかった。以下に詳細なチェック結果を記載する。

---

## レビュー結果

| No | チェック観点 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|------------|----------------|---------|------|---------|
| 1 | ブループリントとの整合性 | architecture-blueprint/05-database-architecture.md | 削除方式（論理削除/物理削除+履歴）、ロック方式（楽観的/悲観的）、監査カラム、PK採番（BIGSERIAL）、テーブル分類 — 全てブループリントの方針に準拠している | 適合 | 問題なし |
| 2 | SSOTルール準拠 | CLAUDE.md | テーブル定義・カラム定義はデータモデル定義書への参照リンクで示し、本設計書には複製していない。パスワードポリシーもセキュリティアーキテクチャへの参照で済ませている | 適合 | 問題なし |
| 3 | PostgreSQL バージョンと NULLS NOT DISTINCT | data-model/01-overview.md | 在庫テーブルの5軸一意制約で `NULLS NOT DISTINCT`（PostgreSQL 15以降）を使用。設計書ではPG16を指定しており整合性あり | 適合 | 問題なし |
| 4 | B1ms リソース制約の考慮 | — | `max_connections=50`、`shared_buffers=512MB`（RAM 2GiBの25%）、`work_mem=4MB` はB1msに対して適切。HikariCPの `maximum-pool-size=10` × 最大3レプリカ = 30接続で余裕あり | 適合 | 問題なし |
| 5 | インデックス設計の網羅性 | data-model/02-master-tables.md, 03-transaction-tables.md, 04-batch-tables.md | 全33テーブルに対してインデックスを定義済み。データモデル定義書で言及されている制約・インデックスを全て含んでいる | 適合 | 問題なし |
| 6 | 在庫引当の悲観的ロック | architecture-blueprint/05-database-architecture.md, functional-requirements/04a-allocation.md | `inventories` テーブルの `SELECT FOR UPDATE` による悲観的ロックをトランザクション設計で明記。デッドロック防止策（ID昇順ロック）も記載 | 適合 | 問題なし |
| 7 | 楽観的ロックの整合性 | architecture-blueprint/05-database-architecture.md | `version` カラムによる楽観的ロックの対象テーブルとトランザクション境界が整合している。マスタ更新は楽観的ロック、在庫操作は悲観的ロックの使い分けが明確 | 適合 | 問題なし |
| 8 | パーティショニング判断 | — | 初期段階ではパーティショニング不適用、データ量見積もり（年間約80MB/トランザクション系）を根拠として妥当。導入基準（1,000万行超）も明記 | 適合 | 問題なし |
| 9 | Flyway マイグレーション構成 | architecture-blueprint/04-backend-architecture.md | バージョン範囲（V001〜V099: スキーマ、V100〜V199: 初期データ、V200〜: 変更）の分類は運用しやすい設計。ファイル構成も依存関係順（users→products→...→backup）に沿っている | 適合 | 問題なし |

> **対応完了** (2026-03-19): SSOT違反対応として、V100__insert_initial_system_parameters.sql の INSERT 文内のシステムパラメータ具体値を省略形に変更し、data-model/02-master-tables.md への参照リンクを追加（cross-review-ssot 違反#3 対応）
| 10 | バックアップ・アーカイブ設計 | data-model/04-batch-tables.md, functional-requirements/06-batch-processing.md | Azure自動バックアップ（7日間PITR）と業務データアーカイブ（日替処理のバックアップテーブル）の二層構造。データモデル定義書のバックアップ条件（完了済み+2か月超）と整合 | 適合 | 問題なし |
| 11 | データ量見積もりの妥当性 | — | 年間約80MB（トランザクション系）+ 80MB（バックアップ系）+ 20MB（集計系）= 約180MB/年。B1msの32GiB初期ストレージで100年以上運用可能な計算。見積もりは保守的で妥当 | 情報 | — |
| 12 | DB認証設計のユーザー分離 | — | `wms_admin`（管理者）、`wms_migration`（DDL+DML）、`wms_app`（DMLのみ）の3ユーザー構成は最小権限の原則に沿っており適切。アプリユーザーにDDL権限を与えていない | 適合 | 問題なし |
| 13 | スロークエリ検知 | — | `log_min_duration_statement=500ms` の設定と `pg_stat_statements` の有効化を記載。B1msの性能制約下でのクエリ監視体制として妥当 | 適合 | 問題なし |
| 14 | ENUM型不使用の方針 | — | PostgreSQL ENUM型を使用せず `varchar + CHECK制約` で管理する方針。Flywayとの相性、値追加の容易さ、Java enum との二重管理回避の観点から適切な判断 | 適合 | 問題なし |
| 15 | Container Apps (min replicas=0) との整合 | architecture-blueprint/01-overall-architecture.md | スケールイン/アウト時のコネクション管理を `max-lifetime` による自動リフレッシュで対応する旨を記載。コールドスタート時のDB接続確立遅延への言及があるとより良い | 改善提案 | 未対応（軽微） |
| 16 | 外部キー制約のバックアップテーブル除外 | data-model/04-batch-tables.md | バックアップテーブルは外部キー制約なしの方針を明記。データモデル定義書のバックアップ設計方針と整合している | 適合 | 問題なし |
| 17 | 日替処理のトランザクション設計 | functional-requirements/06-batch-processing.md | ステップごとに独立トランザクションで実行する設計。機能要件の「途中失敗時は完了済みステップをスキップして再開」のルールと整合する | 適合 | 問題なし |
| 18 | open-in-view: false 設定 | — | JPA の `open-in-view: false` を設定し、LazyInitializationException を明示的に検出する方針。N+1問題の早期発見に有効 | 適合 | 問題なし |

---

## 総合評価

| 評価項目 | 結果 |
|---------|------|
| ブループリントとの整合性 | 合格 |
| SSOTルール準拠 | 合格 |
| データモデル定義書との整合性 | 合格 |
| 機能要件との整合性 | 合格 |
| Azure 固有設計の具体性 | 合格 |
| 実装可能性 | 合格 |

**総合判定: 合格（承認）**

改善提案1件（No.15: コールドスタート時のDB接続遅延への言及追加）は軽微であり、設計書の品質を損なうものではない。必要に応じて次回改訂時に追記を検討する。
