# アーキテクチャ設計レビュー記録票 — 開発・デプロイ

> 対象成果物: `docs/architecture-design/12-development-deploy.md`
> レビュー日: 2026-03-18
> レビュー担当: DevOps・CI/CDパイプライン設計スペシャリスト（AI）
> 参照ドキュメント:
> - `docs/architecture-blueprint/12-development-deploy.md`（開発・デプロイアーキテクチャ）
> - `docs/architecture-blueprint/06-infrastructure-architecture.md`（インフラアーキテクチャ）
> - `docs/architecture-blueprint/04-backend-architecture.md`（バックエンドアーキテクチャ）
> - `docs/architecture-blueprint/03-frontend-architecture.md`（フロントエンドアーキテクチャ）
> - `docs/architecture-blueprint/11-monitoring-operations.md`（監視・運用アーキテクチャ）
> - `CLAUDE.md`（プロジェクト規約・ブランチ戦略）

---

## エグゼクティブサマリー

開発・デプロイ設計書は、ブループリントで定義された方針を具体的な設計・実装レベルに落とし込んでいる。GitHub Actionsワークフローの具体的なYAML定義、Dockerfileのマルチステージビルド設計、環境プロモーション戦略が整備されている。ブループリントとの整合性は概ね良好であり、SSOT原則に基づき方針の複製を避けて参照リンクを活用している。以下に軽微な指摘事項と改善提案を記録する。

---

## レビュー結果

| No | チェック観点 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|------------|----------------|---------|------|---------|
| 1 | ブループリントとの整合性 | `architecture-blueprint/12-development-deploy.md` | ブループリントのブランチ戦略・CI/CDフロー・Dockerfile設計・イメージタグ戦略と設計書の内容が一致している。問題なし | 確認OK | - |
| 2 | ブランチ保護ルール | `architecture-blueprint/12-development-deploy.md` | ブループリントに「CI構築後：CI通過必須。CI構築前：制限なし」とあり、設計書でも段階的適用を明記している。問題なし | 確認OK | - |
| 3 | SSOT原則 | `CLAUDE.md` | テスト戦略の詳細を品質管理計画書への参照にとどめている。カバレッジ目標値はブループリント側をSSOTとして参照している。SSOT原則に適合 | 確認OK | - |
| 4 | インフラ構成との整合 | `architecture-blueprint/06-infrastructure-architecture.md` | dev/prd環境のContainer Apps設定、Blob Storage設定、ACR設定がインフラ設計と一致している。prdのEast/West両方へのデプロイが反映されている | 確認OK | - |
| 5 | フロントエンドデプロイ | `architecture-blueprint/03-frontend-architecture.md` | フロントエンドはBlob Storageの静的ウェブサイトホスティングにデプロイする方針が正しく反映されている。Dockerイメージ不要の判断は妥当 | 確認OK | - |
| 6 | ヘルスチェック | `architecture-blueprint/11-monitoring-operations.md` | Dockerfileの `HEALTHCHECK` が `/actuator/health` を使用しており、監視設計のヘルスプローブ設定と整合している | 確認OK | - |
| 7 | Terraform ワークフロー | `architecture-blueprint/06-infrastructure-architecture.md` | Terraform state管理（wms-terraform サブスク）の方針と一致。環境ごとの `terraform.tfvars` を使った構成管理が反映されている | 確認OK | - |
| 8 | cd-dev.yml のフロントエンドAPI URL | `architecture-blueprint/06-infrastructure-architecture.md` | `VITE_API_BASE_URL` をGitHub Actions Variablesから注入する設計だが、ブループリントに「Terraform outputから動的に注入する」とある。Terraform apply後にVariablesを手動更新する運用手順を追記すると望ましい | 改善提案 | 未対応（軽微） |
| 9 | GitHub Actions の Azure Login | - | `azure/login@v2` にサービスプリンシパル（JSON）を使用しているが、OpenID Connect（OIDC）フェデレーションへの移行を将来検討すると良い。シークレットのローテーション不要になる | 改善提案 | 未対応（将来検討） |

> **対応完了** (2026-03-19): Q7方針決定により OIDC 移行は見送り。12-development-deploy.md に「OIDC は将来検討。現時点ではサービスプリンシパル方式を維持」の注記を追加
| 10 | cd-prd.yml の environment 設定 | - | `environment: production` を設定しているのは良い。GitHub の Environment Protection Rules（手動承認）を有効化すると、誤った本番デプロイを防止できる | 改善提案 | 未対応（将来検討） |

> **対応完了** (2026-03-19): Q8方針決定により prd 環境の GitHub Environment Protection Rules（手動承認）を有効化する方針を確定。12-development-deploy.md セクション7.4に追加
| 11 | Dockerfile のセキュリティ | `architecture-blueprint/12-development-deploy.md` | ブループリントのDockerfileには非rootユーザーの設定がなかったが、設計書では `appuser` を追加している。セキュリティベストプラクティスとして妥当な追加 | 確認OK（改善） | - |
| 12 | コミットメッセージ規約 | `CLAUDE.md` | CLAUDE.mdに「コミットメッセージ：日英併記」とあり、設計書のConventional Commits + 日英併記の形式はこれに適合している | 確認OK | - |
| 13 | CI の paths-ignore | - | `docs/**` と `infra/**` をCI対象外としている。ドキュメントのみの変更でCIが走らないのは効率的だが、infra変更時にもアプリケーションテストが必要になるケースは限定的であるため妥当 | 確認OK | - |
| 14 | concurrency 設定 | - | ci.yml に `cancel-in-progress: true` を設定しており、同一PRへの連続pushで古いCI実行がキャンセルされる。GitHub Actions の無料枠節約として適切 | 確認OK | - |
| 15 | DBマイグレーション戦略 | `architecture-blueprint/04-backend-architecture.md` | Flyway によるマイグレーションが全環境で起動時自動実行される設計。prd環境でのマイグレーション失敗時のロールバック手順は、運用手順書で別途整備が望ましい | 改善提案 | 未対応（運用手順書で対応） |

---

## 総合評価

| 評価項目 | 結果 |
|---------|------|
| ブループリントとの整合性 | 適合 |
| SSOT原則の遵守 | 適合 |
| 実装可能性 | 高い（GitHub Actionsワークフローが具体的に定義されている） |
| セキュリティ考慮 | 良好（非rootユーザー、Secrets管理、environment保護） |
| 運用性 | 良好（ロールバック手順、トラブルシューティング付き） |

**結論:** 設計書は承認可能な品質に達している。改善提案3件はいずれも軽微であり、実装フェーズで段階的に対応することを推奨する。
