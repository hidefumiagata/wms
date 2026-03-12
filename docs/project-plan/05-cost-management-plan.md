# コスト管理計画書 (Cost Management Plan)

## 1. コスト方針

| 項目 | 内容 |
|------|------|
| **基本方針** | 極力ゼロ（Azure低コストプラン・無料枠・OSSのみ） |
| **対象期間** | 開発期間 + ShowCase公開期間 |
| **コスト管理者** | プロジェクトオーナー |

## 2. Azure リソースコスト概算

### 稼働状態別コスト

| 状態 | 月額概算 | 備考 |
|------|---------|------|
| **稼働時（dev + prd両方）** | ~$18/月 | PostgreSQL B1ms × 2 + Container Apps + Blob |
| **停止時（Blob Storageのみ）** | ~$4/月 | PostgreSQL手動停止後 |

### リソース別コスト内訳

| リソース | SKU/プラン | 月額概算 |
|---------|-----------|---------|
| **Azure Container Apps（バックエンド）** | min replicas=0 | ~$0〜3（リクエスト量次第） |
| **Azure Database for PostgreSQL Flexible Server（dev）** | B1ms | ~$5/月 |
| **Azure Database for PostgreSQL Flexible Server（prd）** | B1ms | ~$5/月 |
| **Azure Blob Storage（dev + prd）** | LRS | ~$1/月（数GB以下） |
| **Azure Container Registry** | Basic | ~$5/月 |
| **Log Analytics Workspace** | 従量課金（5GB/日まで無料） | ~$0（無料枠内想定） |

> Container Apps は min replicas=0 のため、未使用時は課金なし。

## 3. コスト削減施策

### PostgreSQL 停止運用

未使用時（開発・デモ以外）はPostgreSQL Flexible Serverを手動停止する。

| 操作 | コマンド |
|------|---------|
| **停止** | `az postgres flexible-server stop --resource-group <rg> --name <server>` |
| **起動** | `az postgres flexible-server start --resource-group <rg> --name <server>` |

> ⚠️ Azure Flexible Serverは7日間連続停止で自動再起動。長期未使用時は定期的な手動停止が必要。

### 開発環境の運用方針

| 項目 | 方針 |
|------|------|
| **dev環境** | 開発・テスト時のみ起動。未使用時はDB停止 |
| **prd環境** | ShowCase公開期間のみ常時起動。それ以外はDB停止 |
| **Container Apps** | min replicas=0 のため自動ゼロスケール。停止操作不要 |

## 4. 開発ツールコスト

| ツール | コスト | 備考 |
|-------|--------|------|
| **Claude Code** | 使用量課金 | Anthropic API |
| **GitHub** | 無料 | Publicリポジトリ |
| **GitHub Actions** | 無料 | Public リポジトリは無料枠 2,000分/月 |
| **Terraform** | 無料 | OSS版 |
| **IDE（VSCode）** | 無料 | |

## 5. コスト監視

| 項目 | 設定 |
|------|------|
| **Azure Cost Management** | サブスクリプション単位でコストアラート設定（月$20超過で通知） |
| **確認頻度** | 月次（開発期間中は週次） |
