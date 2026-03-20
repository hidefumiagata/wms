# システム構成

→ 図：[diagrams/system-architecture.drawio](diagrams/system-architecture.drawio)

## 構成サマリー

| コンポーネント | Azureサービス | ステータス |
|--------------|-------------|----------|
| **フロントエンド** | Azure Blob Storage Static Website | ✅ 確定 |
| **I/Fファイルストレージ** | Azure Blob Storage | ✅ 確定 |
| **バックエンドAPI** | Azure Container Apps | ✅ 確定 |
| **コンテナレジストリ** | Azure Container Registry (ACR) | ✅ 確定 |
| **データベース** | Azure Database for PostgreSQL Flexible Server（B1ms） | ✅ 確定 |
| **認証方式** | JWT + httpOnly Cookie | ✅ 確定 |
| **バッチ実行** | `POST /api/v1/batch/{name}`（手動実行） | ✅ 確定 |
| **メール送信** | Azure Communication Services（Email） | ✅ 確定 |
| **クラウド** | Azure | ✅ 確定 |
| **IaC** | Terraform | ✅ 確定 |

## コスト方針

詳細なコスト見積もりは [architecture-design/02-system-architecture.md](../architecture-design/02-system-architecture.md) の付録Aを参照。

- Azure Container Apps：min replicas=0 でリクエストなし時はゼロコスト
- PostgreSQL Flexible Server：未使用時は停止（ストレージのみ課金）
- ⚠️ PostgreSQL は7日間で自動再起動されるため定期的に停止操作が必要

## 通信フロー

- ユーザー → Azure Blob Storage：静的ファイル（HTML/CSS/JS）取得
- ユーザー → Azure Container Apps：HTTPS / REST API（JWT認証）
- バックエンド → PostgreSQL：SQL
- バックエンド → Azure Blob Storage：I/Fファイル取得
- 外部システム → Azure Blob Storage：CSVアップロード（モック）
- バックエンド → Azure Communication Services：パスワードリセットメール送信

## CORS設定

フロントエンド（Blob Storage）とバックエンドAPIは異なるオリジンになるため、APIサーバー側でCORSを設定する。
許可オリジン：Blob Static Website URL（dev/prd各環境）
