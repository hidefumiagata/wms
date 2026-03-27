# WMS Terraform Infrastructure

WMSプロジェクトのAzureインフラストラクチャをTerraformで管理する。

## ディレクトリ構成

```
infra/
├── bootstrap.sh                    # 初期セットアップスクリプト
├── terraform-state/                # tfstate保管用Storage Account（初回のみ）
├── modules/
│   ├── vnet/                       # VNet + Subnet + NSG + Private DNS
│   ├── acr/                        # Azure Container Registry
│   ├── storage/                    # Blob Storage（Static Website + I/F）
│   ├── monitoring/                 # Log Analytics + App Insights
│   ├── communication-services/     # Azure Communication Services
│   ├── postgresql/                 # PostgreSQL Flexible Server
│   ├── container-apps/             # Container Apps Environment + App
│   └── front-door/                 # Front Door + WAF（prdのみ）
├── environments/
│   ├── dev/                        # dev環境（Japan East）
│   └── prd/                        # prd環境（Japan East + West）
```

## 前提条件

- Azure CLI (`az`) インストール済み
- Terraform >= 1.5.0 インストール済み
- Azureサブスクリプション 2つ:
  - メインサブスクリプション — Terraform state保管 + prd環境
  - `wms-dev` — 開発環境

---

## 初期セットアップ（1回だけ）

### 1. bootstrap.sh を実行

```bash
az login

cd infra
./bootstrap.sh \
  --main-sub <メインサブスクリプションID> \
  --dev-sub <wms-dev サブスクリプションID>
```

このスクリプトは以下を行う:
1. Terraform state用Storage Account（`stwmsterraform`）をプロビジョニング
2. 次のステップで必要な環境変数を案内

### 2. 環境変数を設定

サブスクリプションIDやシークレットはすべて環境変数で渡す（リポジトリにはコミットしない）。

```bash
# Terraform state backend（全環境共通）
export TF_STATE_SUBSCRIPTION_ID="<メインサブスクリプションID>"

# シークレット
export TF_VAR_db_admin_password="<PostgreSQL管理者パスワード>"
export TF_VAR_jwt_secret="<JWT署名鍵（32文字以上）>"
```

環境ごとのサブスクリプションID:

```bash
# dev環境の場合
export TF_VAR_subscription_id="<wms-dev サブスクリプションID>"

# prd環境の場合
export TF_VAR_subscription_id="<メインサブスクリプションID>"
```

---

## 手動実行

### Plan（変更内容の確認）

```bash
az login

# dev環境
cd infra/environments/dev
terraform init -backend-config="subscription_id=$TF_STATE_SUBSCRIPTION_ID"
terraform plan

# prd環境
cd infra/environments/prd
terraform init -backend-config="subscription_id=$TF_STATE_SUBSCRIPTION_ID"
terraform plan
```

### Apply（変更の適用）

```bash
# dev環境
cd infra/environments/dev
terraform init -backend-config="subscription_id=$TF_STATE_SUBSCRIPTION_ID"
terraform plan -out=tfplan    # 必ずplanを確認してから
terraform apply tfplan

# prd環境
cd infra/environments/prd
terraform init -backend-config="subscription_id=$TF_STATE_SUBSCRIPTION_ID"
terraform plan -out=tfplan    # 必ずplanを確認してから
terraform apply tfplan
```

---

## 環境の破棄（コスト節約）

dev環境は使わない時に破棄してコストをゼロにできる。

### Destroy（環境の破棄）

```bash
# dev環境
cd infra/environments/dev
terraform init -backend-config="subscription_id=$TF_STATE_SUBSCRIPTION_ID"
terraform plan -destroy       # 破棄内容を確認してから
terraform destroy

# prd環境（慎重に！）
cd infra/environments/prd
terraform init -backend-config="subscription_id=$TF_STATE_SUBSCRIPTION_ID"
terraform plan -destroy       # 破棄内容を必ず確認
terraform destroy
```

### 常設リソース（destroyされない）

- ACR（`acrwms`） — `lifecycle { prevent_destroy = true }`
- Terraform state Storage Account（`stwmsterraform`）

---

## コスト目安

| 状態 | dev | prd |
|------|-----|-----|
| 常設のみ（ACR + tfstate） | ~$5/月 | ~$5/月 |
| 稼働中 | +~$6/月 | +~$60/月 |
| DB停止時 | +~$0.1/月 | — |

---

## 関連ドキュメント

- [インフラストラクチャアーキテクチャ設計書](../docs/architecture-design/06-infrastructure-architecture.md)
- [開発・デプロイ設計書](../docs/architecture-design/12-development-deploy.md)
