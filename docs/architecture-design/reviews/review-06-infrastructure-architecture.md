# アーキテクチャ設計レビュー記録票 — インフラストラクチャアーキテクチャ

> 対象成果物: `docs/architecture-design/06-infrastructure-architecture.md`
> レビュー日: 2026-03-18
> レビュー担当: クラウドインフラストラクチャ設計スペシャリスト（AI）
> 参照ドキュメント:
> - `docs/architecture-blueprint/06-infrastructure-architecture.md`（インフラブループリント）
> - `docs/architecture-blueprint/01-overall-architecture.md`（全体アーキテクチャ方針）
> - `docs/architecture-blueprint/02-system-architecture.md`（システム構成）
> - `docs/architecture-blueprint/08-common-infrastructure.md`（共通基盤）
> - `docs/architecture-blueprint/10-security-architecture.md`（セキュリティ）
> - `docs/architecture-blueprint/11-monitoring-operations.md`（監視・運用）
> - `docs/architecture-blueprint/12-development-deploy.md`（開発・デプロイ）
> - `docs/functional-requirements/`（機能要件定義書一式）
> - `CLAUDE.md`（プロジェクト指示書）

---

## エグゼクティブサマリー

本設計書はブループリント（06-infrastructure-architecture.md）で定めた方針を忠実に詳細化し、Terraform HCLコードレベルまで落とし込んだ設計書である。全10セクション + 付録4件で構成され、Azure Container Apps / PostgreSQL / Blob Storage / VNet / Front Door の各リソースについて具体的なパラメータ値とTerraformコードを記載した。

**総合評価: 合格（軽微な改善提案あり）**

全体として、ブループリントとの整合性が取れており、SSOTルールに従って方針の複製を避け参照リンクで示す方式を採用している。Terraform HCLは実装可能なレベルで記述されている。

---

## レビューチェック結果

| No | チェック観点 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|------------|----------------|---------|------|---------|
| 1 | ブループリントとの整合性 | 06-infrastructure-architecture.md | 全パラメータ（スケーリング設定、SKU、VNet CIDR、フェイルオーバー方式）がブループリントと一致 | 確認OK | - |
| 2 | SSOTルール遵守 | CLAUDE.md | 方針はブループリントへの参照リンクで示し、本書は実装詳細のみを記述。SSOTルール準拠 | 確認OK | - |
| 3 | 命名規則の一貫性 | - | 全リソースの命名規則を1.3節に定義。命名パターンが全セクションで一貫している | 確認OK | - |
| 4 | Terraformコードの妥当性 | 12-development-deploy.md | HCLコードはazurerm provider v4.x準拠で記述。required_version >= 1.5.0 を指定 | 確認OK | - |
| 5 | ネットワーク分離 | 10-security-architecture.md | VNet統合 + サブネット委任 + NSG + Private DNS Zone でDBをプライベートに保護。セキュリティ方針と整合 | 確認OK | - |
| 6 | コスト見積もりの妥当性 | 02-system-architecture.md | dev ~$6.30/月、prd ~$60.50/月。ブループリントの概算（dev ~$9, prd ~$80）と差異あり。ブループリントの値はFront Door Standardの価格変動等により概算値として許容範囲 | 注意 | 許容 |
| 7 | Container Apps スケーリング | 06-infrastructure-architecture.md | concurrent_requests=10 のHTTPスケールルールを設定。WMSの同時リクエスト規模を考慮すると妥当な初期値 | 確認OK | - |
| 8 | コールドスタート対策 | - | dev環境(min:0)向けにStartupプローブ閾値10、JVMオプション、lazy-initializationの3段階対策を記載 | 確認OK | - |
| 9 | DR設計 | 06-infrastructure-architecture.md | Active-Passive方式、RPO/RTO、手動DB復旧手順をブループリントと整合して詳細化。フローチャートも追加 | 確認OK | - |
| 10 | VNet Peering設計 | 06-infrastructure-architecture.md | prd環境のEast↔West Peering設定を記載。Japan WestからJapan East DBへのクロスリージョン接続を実現 | 確認OK | - |
| 11 | Secret管理 | 12-development-deploy.md | DB接続パスワード、JWT秘密鍵等はContainer Apps Secret + GitHub Actions Secretsで管理。環境変数一覧に明記 | 確認OK | - |
| 12 | ACR Geo-replication | 06-infrastructure-architecture.md | ブループリントでは prd: Basic SKU + Geo-replication と記載あるがGeo-repにはPremium必須。設計書ではコスト優先でBasicとした。ブループリント側の修正検討が必要 | 軽微 | 要確認 |

> **対応完了** (2026-03-19): Q1方針決定により ACR Basic SKU を維持し Geo-replication を削除。06-infrastructure-architecture.md および blueprint/06-infrastructure-architecture.md を修正
| 13 | PostgreSQL max_connections | - | B1msデフォルトのmax_connectionsは256だが、設計書では50に制限。Container Apps max replicas=5でコネクションプール含めても十分だが、prd環境のEast+West同時接続時を考慮すると100程度への引き上げを検討 | 改善提案 | 次フェーズ |

> **対応完了** (2026-03-19): Q3方針決定により max_connections=50 を維持。06-infrastructure-architecture.md に East+West 同時接続時の注意事項を注記として追加
| 14 | 監視設計 | 11-monitoring-operations.md | Log Analytics + アラートルール5件を定義。ブループリントのアラート4件に加え、Container App再起動検知を追加 | 確認OK | - |
| 15 | Terraform state管理 | 06-infrastructure-architecture.md | Blob Storageのversioning_enabledを有効化し、state破損時のロールバックに対応。ブループリントの方針通り | 確認OK | - |
| 16 | Front Door WAF | 06-infrastructure-architecture.md | Standard SKU + Microsoft Default Rule Setを適用。WMSの業務要件として十分 | 確認OK | - |

---

## 改善提案サマリー

| 優先度 | 内容 | 対応時期 |
|--------|------|---------|
| ~~低~~ | ~~ブループリントのACR Geo-replication記載とPremium SKU要件の矛盾を解消~~ | ✅ 対応済み（2026-03-19）: Q1方針決定によりBasic SKU維持・Geo-rep削除 |
| ~~低~~ | ~~prd環境のmax_connectionsを100に引き上げ検討~~ | ✅ 対応済み（2026-03-19）: Q3方針決定により50維持・注記追加 |
| 低 | コスト概算値のブループリントとの差異を、ブループリント側に反映 | 次回ブループリント改訂時 |
