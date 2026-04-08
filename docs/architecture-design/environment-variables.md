# 環境変数定義 — Environment Variables Registry

> 本ファイルは全環境変数名のSSOT（Single Source of Truth）である。
> 他の設計書からは本ファイルへの参照リンクのみとし、変数名・値の複製を禁止する。

## 1. アプリケーション環境変数

Container Apps に設定する環境変数。バックエンド Spring Boot アプリケーションが参照する。

| 環境変数 | 説明 | Secret | application.yml プロパティ | dev デフォルト | prd デフォルト |
|---------|------|--------|--------------------------|-------------|-------------|
| `SPRING_PROFILES_ACTIVE` | Spring プロファイル | No | `spring.profiles.active` | `dev` | `prd` |
| `LOG_LEVEL` | ログレベル | No | `logging.level.root` | `DEBUG` | `INFO` |
| `DATABASE_URL` | DB接続文字列 | Yes | `spring.datasource.url` | `jdbc:postgresql://localhost:5432/wms` | (環境依存) |
| `DATABASE_USERNAME` | DBユーザー名 | Yes | `spring.datasource.username` | `wms` | `wmsadmin` |
| `DATABASE_PASSWORD` | DBパスワード | Yes | `spring.datasource.password` | `wms` | (Secrets) |
| `JWT_SECRET_KEY` | JWTトークン署名鍵 | Yes | `jwt.secret-key` | `dev-secret-key-must-be-at-least-256-bits-long-for-hs256` | (Secrets) |
| `JWT_ACCESS_TOKEN_EXPIRATION` | アクセストークン有効期限（ミリ秒） | No | `jwt.access-token-expiration` | `3600000`（1時間） | `3600000`（1時間） |
| `JWT_REFRESH_TOKEN_EXPIRATION` | リフレッシュトークン有効期限（ミリ秒） | No | `jwt.refresh-token-expiration` | `86400000`（24時間） | `86400000`（24時間） |
| `CORS_ALLOWED_ORIGINS` | CORS許可オリジン | No | `cors.allowed-origins` | `http://localhost:5173` | Front Door URL |
| `AZURE_STORAGE_ACCOUNT_NAME` | Blob Storageアカウント名（Managed Identity） | No | `wms.storage.account-name` | `devstorageaccount` | `stwmsprdeast` |
| `ACS_CONNECTION_STRING` | Azure Communication Services接続文字列 | Yes | `wms.acs.connection-string` | (環境依存) | (環境依存) |
| `ACS_SENDER_ADDRESS` | メール送信元アドレス | No | `wms.acs.sender-address` | `DoNotReply@...` | `DoNotReply@...` |
| `APPLICATIONINSIGHTS_CONNECTION_STRING` | Application Insights接続文字列 | No | (自動検出) | (環境依存) | (環境依存) |
| `AZURE_FRONTDOOR_ID` | Front Door ID（X-Azure-FDIDヘッダー検証用） | No | `wms.frontdoor.id` | (未設定) | (Front Door ID) |

## 2. フロントエンド環境変数

Vite ビルド時に埋め込まれる環境変数。

| 環境変数 | 説明 | ビルド時注入 |
|---------|------|-----------|
| `VITE_API_BASE_URL` | APIベースURL | Yes |

## 3. ローカル開発環境変数

Docker Compose（PostgreSQL）用。

| 環境変数 | 説明 | デフォルト値 |
|---------|------|-----------|
| `POSTGRES_DB` | データベース名 | `wms` |
| `POSTGRES_USER` | DBユーザー名 | `wms` |
| `POSTGRES_PASSWORD` | DBパスワード | `wms` |
| `TZ` | タイムゾーン | `Asia/Tokyo` |

## 4. GitHub Actions Secrets

CI/CD パイプラインで使用するシークレット。

| Secret名 | 用途 | 設定タイミング |
|----------|------|-------------|
| `AZURE_CREDENTIALS` | Azure サービスプリンシパル（JSON） | 初回構築時 |
| `ACR_LOGIN_SERVER` | ACR ログインサーバーURL | 初回構築時 |
| `ACR_USERNAME` | ACR 管理者ユーザー名 | 初回構築時 |
| `ACR_PASSWORD` | ACR 管理者パスワード | 初回構築時 |
| `ARM_CLIENT_ID` | Terraform用SP クライアントID | 初回構築時 |
| `ARM_CLIENT_SECRET` | Terraform用SP シークレット | 初回構築時 |
| `ARM_SUBSCRIPTION_ID` | Terraform対象サブスクリプションID | 初回構築時 |
| `ARM_TENANT_ID` | Azure AD テナントID | 初回構築時 |
| `DEV_DB_PASSWORD` | dev環境 DB接続パスワード | dev環境構築時 |
| `PRD_DB_PASSWORD` | prd環境 DB接続パスワード | prd環境構築時 |
| `JWT_SECRET_DEV` | dev環境 JWTシークレット | dev環境構築時 |
| `JWT_SECRET_PRD` | prd環境 JWTシークレット | prd環境構築時 |

## 5. GitHub Actions Variables

Repository Variables（非機密値）。

| Variable名 | 用途 | 設定タイミング |
|------------|------|-------------|
| `CONTAINER_APPS_DOMAIN` | dev Container Appsドメイン | Terraform apply後 |
| `PRD_API_BASE_URL` | prd APIベースURL | Terraform apply後 |
| `PRD_FRONTEND_URL` | prd フロントエンドURL | Terraform apply後 |

> `AZURE_CREDENTIALS` と `ARM_*` の使い分け: CI/CD（cd-dev.yml / cd-prd.yml）は `AZURE_CREDENTIALS`（JSON一括形式）、Terraform（terraform.yml）は `ARM_*`（個別4変数）を使用。
