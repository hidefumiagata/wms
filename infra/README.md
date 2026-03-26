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
1. GitHub Actions用サービスプリンシパル作成
2. Terraform state用Storage Account（`stwmsterraform`）をプロビジョニング
3. SPクレデンシャルを `~/.azure/wms-sp-credentials.json` に保存

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

### 4. GitHub Actions Secrets を設定

リポジトリの **Settings > Secrets and variables > Actions** で以下を設定する。
`ARM_CLIENT_SECRET` は `~/.azure/wms-sp-credentials.json` の `clientSecret` から取得し、取得後にファイルを削除する。

| Secret | 説明 | 値の取得元 |
|--------|------|----------|
| `ARM_CLIENT_ID` | サービスプリンシパル クライアントID | bootstrap.sh出力 |
| `ARM_CLIENT_SECRET` | サービスプリンシパル シークレット | `~/.azure/wms-sp-credentials.json` |
| `ARM_TENANT_ID` | Azure テナントID | bootstrap.sh出力 |
| `ARM_SUBSCRIPTION_ID` | wms-terraform サブスクリプションID | bootstrap.sh出力 |
| `TF_VAR_db_admin_password` | PostgreSQL管理者パスワード | 任意の強力なパスワード |
| `TF_VAR_jwt_secret` | JWT署名鍵（32文字以上） | 任意のランダム文字列 |

CLIで設定する場合:

```bash
gh secret set ARM_CLIENT_ID --body "<値>"
gh secret set ARM_CLIENT_SECRET --body "<値>"
gh secret set ARM_TENANT_ID --body "<値>"
gh secret set ARM_SUBSCRIPTION_ID --body "<値>"
gh secret set TF_VAR_db_admin_password --body "<値>"
gh secret set TF_VAR_jwt_secret --body "<値>"
```

### 5. GitHub Environment を作成（prd用）

prd環境への `terraform apply` は承認ゲートで保護されている。

1. リポジトリの **Settings > Environments** を開く
2. **New environment** → 名前: `production`
3. **Required reviewers** を有効にし、承認者を追加

---

## GitHub Actionsによる自動実行

### ワークフロー: `.github/workflows/terraform.yml`

`infra/` 配下のファイルが変更されると自動でトリガーされる。

### PRを出した時（plan）

```
infra/ 配下を変更 → PR作成
  ↓
terraform fmt -check（フォーマット検証）
  ↓
terraform validate（構文検証）
  ↓
terraform plan（dev/prd両方）
  ↓
plan結果がPRコメントに自動投稿（✅成功 / ❌失敗）
```

- dev, prd の両環境で並列にplanが実行される
- plan失敗時はワークフローが失敗し、マージがブロックされる

### mainにマージした時（apply）

```
PR → mainにマージ
  ↓
dev環境: terraform apply -auto-approve（自動）
  ↓
prd環境: terraform apply（承認ゲート待ち）
  ↓
承認者がGitHub上で承認 → prd apply実行
```

- **dev**: 自動適用（承認不要）
- **prd**: `production` Environment の承認ゲートを通過後に適用

### 手動でplan / applyを実行

Actions画面から任意のタイミングでplan/applyを実行できる。

1. リポジトリの **Actions** タブを開く
2. 左メニューから **Terraform** を選択
3. **Run workflow** をクリック
4. アクション（`plan` / `apply`）と環境（`dev` / `prd`）を選択
5. **Run workflow** を実行

```
plan選択時:  fmt check → validate → plan（結果をログに出力）
apply選択時: fmt check → validate → plan → apply（prdは承認ゲート付き）
```

---

## ローカルでの手動実行

GitHub Actionsを使わずにローカルから直接実行する場合:

```bash
# Azure認証
az login
az account set --subscription <対象サブスクリプションID>

# dev環境
cd infra/environments/dev
export TF_VAR_db_admin_password="<パスワード>"
export TF_VAR_jwt_secret="<JWT鍵>"
terraform init
terraform plan
terraform apply

# prd環境
cd infra/environments/prd
export TF_VAR_db_admin_password="<パスワード>"
export TF_VAR_jwt_secret="<JWT鍵>"
terraform init
terraform plan
terraform apply
```

---

## 環境の破棄（コスト節約）

dev環境は使わない時に破棄してコストをゼロにできる。

### GitHub Actionsで破棄（推奨）

ワークフロー: `.github/workflows/terraform-destroy.yml`

1. リポジトリの **Actions** タブを開く
2. 左メニューから **Terraform Destroy** を選択
3. **Run workflow** をクリック
4. 破棄する環境を選択（`dev` / `prd`）
5. 確認欄に同じ環境名を入力（誤操作防止）
6. **Run workflow** を実行

```
環境選択 + 確認入力
  ↓
terraform plan -destroy（破棄プラン表示）
  ↓
prd環境のみ: production-destroy Environment の承認ゲート待ち
  ↓
terraform apply（破棄実行）
```

- **dev**: 確認入力のみで実行
- **prd**: `production-destroy` Environment の承認ゲートが必要（別途Environmentを作成すること）

### ローカルで破棄

```bash
cd infra/environments/dev
export TF_VAR_db_admin_password="<パスワード>"
export TF_VAR_jwt_secret="<JWT鍵>"
terraform init
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
