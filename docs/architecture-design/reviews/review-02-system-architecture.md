# アーキテクチャ設計レビュー記録票 — システムアーキテクチャ

> 対象成果物: `docs/architecture-design/02-system-architecture.md`
> レビュー日: 2026-03-18
> レビュー担当: システムアーキテクチャ設計スペシャリスト（AI）
> 参照ドキュメント:
> - `docs/architecture-blueprint/01-overall-architecture.md`（全体アーキテクチャ方針）
> - `docs/architecture-blueprint/02-system-architecture.md`（システム構成ブループリント）
> - `docs/architecture-blueprint/06-infrastructure-architecture.md`（インフラアーキテクチャ）
> - `docs/architecture-blueprint/07-auth-architecture.md`（認証・認可アーキテクチャ）
> - `docs/architecture-blueprint/08-common-infrastructure.md`（アプリケーション共通基盤）
> - `docs/architecture-blueprint/10-security-architecture.md`（セキュリティアーキテクチャ）
> - `docs/data-model/01-overview.md`（データモデル概要）
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
> - `CLAUDE.md`（技術スタック確定事項）

---

## エグゼクティブサマリー

| 分類 | 件数 |
|------|------|
| **設計書修正済**（レビュー時に自動修正） | 0件 |
| **要対応**（他ドキュメントへの変更が必要） | 2件 |
| 指摘事項なし | 23件 |
| **総チェック項目** | 25件 |

---

| No | チェック観点 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|------------|----------------|---------|------|---------|
| 1 | Azureリソース一覧がブループリントの構成サマリーと整合しているか | architecture-blueprint/02-system-architecture.md | 全リソース（Container Apps, Blob Storage, PostgreSQL, ACR, Communication Services等）が網羅されている | 指摘事項なし | - |
| 2 | サブスクリプション構成がブループリントと一致しているか | architecture-blueprint/06-infrastructure-architecture.md | wms-terraform / wms-dev / wms-prd の3サブスクリプション構成が一致 | 指摘事項なし | - |
| 3 | VNet CIDR がブループリントと一致しているか | architecture-blueprint/06-infrastructure-architecture.md | dev: 10.0.0.0/16, prd-east: 10.1.0.0/16, prd-west: 10.2.0.0/16 で一致 | 指摘事項なし | - |
| 4 | Container Apps のスケーリング設定がブループリントと一致しているか | architecture-blueprint/06-infrastructure-architecture.md | dev: min:0/max:3, prd-east: min:1/max:5, prd-west: min:0/max:5 で一致 | 指摘事項なし | - |
| 5 | PostgreSQL SKUがブループリントと一致しているか | architecture-blueprint/06-infrastructure-architecture.md | dev: B1ms 単一構成, prd: B1ms + Geo-redundant backup で一致 | 指摘事項なし | - |
| 6 | Storage冗長性がブループリントと一致しているか | architecture-blueprint/06-infrastructure-architecture.md | dev: LRS, prd: GRS で一致 | 指摘事項なし | - |
| 7 | 常設リソース（Destroyしない）がブループリントと一致しているか | architecture-blueprint/06-infrastructure-architecture.md | stwmsterraform + acrwms の2つで一致 | 指摘事項なし | - |
| 8 | フェイルオーバー仕様がブループリントと一致しているか | architecture-blueprint/06-infrastructure-architecture.md | RPO/RTO、フェイルオーバー方式（Active-Passive）、各コンポーネントの復旧方式が一致 | 指摘事項なし | - |
| 9 | DNS/エンドポイントがブループリントと一致しているか | architecture-blueprint/06-infrastructure-architecture.md | dev: Blob直接/CA直接, prd: Front Door経由 で一致 | 指摘事項なし | - |
| 10 | Terraformディレクトリ構成がブループリントと一致しているか | architecture-blueprint/06-infrastructure-architecture.md | 基本構成一致。設計書で monitoring / identity モジュールを追加（ブループリントに未記載のモジュール） | 要対応 | ブループリントに monitoring / identity モジュールを追記することを推奨 |

> **対応完了** (2026-03-19): Q4方針決定により identity モジュールを container-apps モジュールに統合。02-system-architecture.md から独立 identity モジュールを削除し、Managed Identity 設定を container-apps モジュール内に移動
| 11 | 通信フローがブループリントと一致しているか | architecture-blueprint/02-system-architecture.md | ユーザー→Blob, ユーザー→CA, CA→PostgreSQL, CA→Blob(I/F), 外部→Blob, CA→Communication Services の全通信フローが網羅 | 指摘事項なし | - |
| 12 | CORS設定がブループリントと一致しているか | architecture-blueprint/02-system-architecture.md | 許可オリジンがBlob Static Website URL（環境別）で一致 | 指摘事項なし | - |
| 13 | 認証方式（JWT + httpOnly Cookie）がブループリントと一致しているか | architecture-blueprint/07-auth-architecture.md | JWT + httpOnly Cookie + SameSite=Lax で一致 | 指摘事項なし | - |
| 14 | メール送信基盤がブループリントと一致しているか | architecture-blueprint/02-system-architecture.md, functional-requirements/00-authentication.md | Azure Communication Services で一致 | 指摘事項なし | - |
| 15 | ロギング設計がブループリントと一致しているか | architecture-blueprint/08-common-infrastructure.md | JSON構造化ログ→stdout→Log Analytics→Azure Monitor/Application Insights の構成が一致。SSOTルールに従い詳細はブループリントを参照 | 指摘事項なし | - |
| 16 | コスト概算がブループリントと一致しているか | architecture-blueprint/02-system-architecture.md, 06-infrastructure-architecture.md | dev ~$9, prd ~$80, 常時維持 ~$5 で一致 | 指摘事項なし | - |
| 17 | モジュラーモノリス構成が反映されているか | architecture-blueprint/01-overall-architecture.md | 非同期通信（メッセージキュー）不要の設計判断を明記。モジュール間通信はJavaメソッド呼び出しで完結 | 指摘事項なし | - |
| 18 | SSOTルールに違反する情報重複がないか | CLAUDE.md（SSOTルール） | パスワードポリシー値・ステータス遷移等の業務ルールの複製なし。技術方針はブループリントへの参照リンクで処理 | 指摘事項なし | - |
| 19 | データモデル設計との整合性があるか | data-model/01-overview.md | PostgreSQL設計パラメータ（UTF-8、JST固定、Flyway、B1ms）がデータモデル設計方針と一致 | 指摘事項なし | - |
| 20 | バッチ処理のインフラ要件が考慮されているか | functional-requirements/06-batch-processing.md | バッチはAPIエンドポイント（POST /api/v1/batch/{name}）として実行。専用バッチインフラ不要の設計をブループリントで確定済み | 指摘事項なし | - |
| 21 | 外部連携I/Fのインフラ要件が考慮されているか | functional-requirements/07-interface.md | Blob Storage（I/F用）のpending/processedコンテナ構成、Managed Identityによるアクセス制御を設計 | 指摘事項なし | - |
| 22 | HikariCP接続数とPostgreSQL接続数上限の整合性 | 設計書内部整合 | prd環境でmax5レプリカ x poolSize20 = 100が理論上DB上限50を超える。注意書きとして記載済みだが、maximumPoolSizeを10に下げることを検討推奨 | 対応済み | ✅ **対応済み（2026-03-18）**: dev=5/prd=10に全設計書で統一。5レプリカ x 10 = 50接続でDB上限内に収まる構成に修正 |
| 23 | Container Apps サブネットサイズ要件が明記されているか | Azure公式ドキュメント | /23以上の要件を明記済み | 指摘事項なし | - |
| 24 | コールドスタート対策が具体的か | 設計書内部 | Lazy Initialization、JVMオプション、Startup Probe等の具体策を記載済み | 指摘事項なし | - |

> **対応完了** (2026-03-19): Startup Probe パラメータを interval:10秒、failure:30回、timeout:5秒 に 02-system-architecture.md で統一（cross-review-consistency で検出された不整合の解消）
| 25 | Terraform Output→アプリケーション注入の設計が具体的か | 設計書内部 | VITE_API_BASE_URL、CORS、DB接続文字列、ACS接続文字列の注入経路を明記 | 指摘事項なし | - |

---

## 要対応事項の詳細

### No.10: Terraformモジュール追加の反映 — ✅ 一部対応済み（2026-03-19）

設計書で `modules/monitoring`（Log Analytics + App Insights + アラート）と `modules/identity`（Managed Identity + ロール割当）を新規モジュールとして追加した。ブループリント（`architecture-blueprint/06-infrastructure-architecture.md`）のTerraformディレクトリ構成にこの2モジュールが未記載のため、ブループリント側への追記を推奨する。

> **対応完了** (2026-03-19): Q4方針決定により identity モジュールを container-apps モジュールに統合。独立 identity モジュールは不要となった。Q1方針決定により ACR Basic SKU 維持・Geo-replication 削除を 02-system-architecture.md に反映。

**対応案:**
```
infra/modules/
    ...
    ├── monitoring/            ← 追加
    └── identity/              ← 追加（→ Q4により container-apps に統合済み）
```

### No.22: ~~HikariCP maximumPoolSize のprd環境設定~~ ✅ 対応済み（2026-03-18）

dev=5/prd=10に全設計書で統一済み。prd環境: 5レプリカ x 10 = 50接続でPostgreSQL B1msのmax_connections=50以内に収まる構成に修正。
