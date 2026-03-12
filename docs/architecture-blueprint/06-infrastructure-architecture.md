# インフラアーキテクチャ

→ 図：[diagrams/infrastructure-architecture.drawio](diagrams/infrastructure-architecture.drawio)（作成予定）

## 基本方針

| 項目 | 内容 |
|------|------|
| **クラウド** | Microsoft Azure |
| **リージョン** | Japan East |
| **IaC** | Terraform |
| **環境分離** | Azureサブスクリプション単位で分離 |
| **ネットワーク** | VNet（プライベートネットワーク構成） |

## サブスクリプション構成

```
Azure Tenant
├── サブスクリプション: wms-terraform    ← Terraform state専用
├── サブスクリプション: wms-dev          ← 開発環境
└── サブスクリプション: wms-prd          ← 本番環境
```

## Terraform State 管理

| 項目 | 内容 |
|------|------|
| **サブスクリプション** | wms-terraform |
| **リソースグループ** | rg-wms-terraform |
| **ストレージアカウント** | stwmsterraform |
| **Blobコンテナ** | tfstate |
| **State ファイル** | dev/terraform.tfstate、prd/terraform.tfstate |

## 各環境のリソース構成

### 開発環境（wms-dev サブスクリプション）

```
リソースグループ: rg-wms-dev (Japan East)
│
├── ネットワーク
│   ├── VNet: vnet-wms-dev (10.0.0.0/16)
│   │   ├── Subnet: snet-app-dev (10.0.1.0/24)  ← Container Apps
│   │   └── Subnet: snet-db-dev  (10.0.2.0/24)  ← PostgreSQL
│   └── NSG: nsg-wms-dev
│
├── コンピューティング
│   ├── Container Apps Environment: cae-wms-dev (VNet統合)
│   └── Container App: ca-wms-backend-dev
│       ├── min replicas: 0
│       └── max replicas: 3
│
├── データベース
│   └── PostgreSQL Flexible Server: psql-wms-dev
│       ├── SKU: B1ms (1vCore, 2GB)
│       └── VNet統合（snet-db-devに配置）
│
├── ストレージ
│   └── Storage Account: stwmsdev
│       ├── Blob Container: $web       ← フロントエンド（静的Webホスティング）
│       └── Blob Container: iffiles   ← I/Fファイル（入荷予定CSV・受注CSV）
│
└── コンテナレジストリ
    └── Azure Container Registry: acrwms  ← dev/prd共用
```

### 本番環境（wms-prd サブスクリプション）

dev環境と同構成。ACRはdev環境のものを共用する。

```
リソースグループ: rg-wms-prd (Japan East)
│
├── ネットワーク
│   ├── VNet: vnet-wms-prd (10.1.0.0/16)
│   │   ├── Subnet: snet-app-prd (10.1.1.0/24)
│   │   └── Subnet: snet-db-prd  (10.1.2.0/24)
│   └── NSG: nsg-wms-prd
│
├── コンピューティング
│   ├── Container Apps Environment: cae-wms-prd (VNet統合)
│   └── Container App: ca-wms-backend-prd
│       ├── min replicas: 0
│       └── max replicas: 3
│
├── データベース
│   └── PostgreSQL Flexible Server: psql-wms-prd
│       ├── SKU: B1ms (1vCore, 2GB)
│       └── VNet統合（snet-db-prdに配置）
│
└── ストレージ
    └── Storage Account: stwmsprd
        ├── Blob Container: $web
        └── Blob Container: iffiles
```

## Container Apps スケーリング設定

| 設定 | 値 |
|------|---|
| **min replicas** | 0（未使用時ゼロコスト） |
| **max replicas** | 3 |
| **スケールトリガー** | HTTPリクエスト数 |

## DNS / エンドポイント

デフォルトAzure URLを使用（独自ドメインなし）

| コンポーネント | URL形式 |
|--------------|--------|
| **フロントエンド（dev）** | `https://stwmsdev.z11.web.core.windows.net` |
| **フロントエンド（prd）** | `https://stwmsprd.z11.web.core.windows.net` |
| **バックエンドAPI（dev）** | `https://ca-wms-backend-dev.*.japaneast.azurecontainerapps.io` |
| **バックエンドAPI（prd）** | `https://ca-wms-backend-prd.*.japaneast.azurecontainerapps.io` |

## コスト概算

| 状態 | 月額概算 |
|------|---------|
| **稼働時** | ~$18/月（dev + prd） |
| **停止時** | ~$4/月（Blob Storageのみ） |

> ⚠️ PostgreSQL Flexible Serverは7日間で自動再起動されるため定期的な停止操作が必要

## Terraform ディレクトリ構成（案）

```
infra/
├── modules/
│   ├── container-apps/
│   ├── postgresql/
│   ├── storage/
│   ├── vnet/
│   └── acr/
├── environments/
│   ├── dev/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   └── terraform.tfvars
│   └── prd/
│       ├── main.tf
│       ├── variables.tf
│       └── terraform.tfvars
└── terraform-state/
    └── main.tf    ← tfstate用ストレージアカウント作成
```
