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
- Azureサブスクリプション 3つ作成済み:
  - `wms-terraform` — Terraform state保管用
  - `wms-dev` — 開発環境
  - `wms-prd` — 本番環境

---

## 初期セットアップ（1回だけ）

### 1. bootstrap.sh を実行

```bash
az login

cd infra
./bootstrap.sh \
  --terraform-sub <wms-terraform サブスクリプションID> \
  --dev-sub <wms-dev サブスクリプションID> \
  --prd-sub <wms-prd サブスクリプションID>
```

このスクリプトは以下を行う:
1. Terraform state用Storage Account（`stwmsterraform`）をプロビジョニング
2. 各環境のサブスクリプション情報を出力

### 2. backend.tf のサブスクリプションIDを更新

```bash
# bootstrap.sh出力に表示されるwms-terraformサブスクリプションIDで置き換え
vi infra/environments/dev/backend.tf
vi infra/environments/prd/backend.tf
```

`subscription_id = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"` を実際の値に変更する。

### 3. terraform.tfvars のサブスクリプションIDを更新

```bash
vi infra/environments/dev/terraform.tfvars   # subscription_id
vi infra/environments/prd/terraform.tfvars   # subscription_id
```

---

## 手動実行

### Plan（変更内容の確認）

```bash
# Azure認証
az login
az account set --subscription <対象サブスクリプションID>

# 環境変数を設定
export TF_VAR_db_admin_password="<パスワード>"
export TF_VAR_jwt_secret="<JWT鍵>"

# dev環境
cd infra/environments/dev
terraform init
terraform plan

# prd環境
cd infra/environments/prd
terraform init
terraform plan
```

### Apply（変更の適用）

```bash
az login
az account set --subscription <対象サブスクリプションID>

export TF_VAR_db_admin_password="<パスワード>"
export TF_VAR_jwt_secret="<JWT鍵>"

# dev環境
cd infra/environments/dev
terraform init
terraform plan -out=tfplan    # 必ずplanを確認してから
terraform apply tfplan

# prd環境
cd infra/environments/prd
terraform init
terraform plan -out=tfplan    # 必ずplanを確認してから
terraform apply tfplan
```

---

## 環境の破棄（コスト節約）

dev環境は使わない時に破棄してコストをゼロにできる。

### Destroy（環境の破棄）

```bash
az login
az account set --subscription <対象サブスクリプションID>

export TF_VAR_db_admin_password="<パスワード>"
export TF_VAR_jwt_secret="<JWT鍵>"

# dev環境
cd infra/environments/dev
terraform init
terraform plan -destroy       # 破棄内容を確認してから
terraform destroy

# prd環境（慎重に！）
cd infra/environments/prd
terraform init
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
