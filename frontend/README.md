# WMS Frontend

Vue 3 + TypeScript + Element Plus + Vite のフロントエンドアプリケーション。

## 前提条件

- Node.js 18+
- npm

## セットアップ

```bash
npm ci
```

## 開発

```bash
# 開発サーバー起動
npm run dev

# リント
npm run lint:fix

# フォーマット
npm run format

# テスト
npm run test

# カバレッジ付きテスト
npm run test:coverage
```

## ビルド & デプロイ（Azure）

### 前提条件

- Azure CLI (`az`) インストール済み & ログイン済み
- Terraform で dev 環境が構築済み

### 1. ビルド

```bash
# dev 環境向け
VITE_API_BASE_URL="https://ca-wms-backend-dev.<CONTAINER_APPS_DOMAIN>" npm run build

# prd 環境向け
VITE_API_BASE_URL="https://<FRONT_DOOR_URL>" npm run build
```

`CONTAINER_APPS_DOMAIN` は Terraform の output または Azure Portal から取得する。

出力先: `dist/`

### 2. Azure Blob Storage にデプロイ

```bash
# dev 環境
az storage blob upload-batch \
  --account-name stwmsdev \
  --source dist \
  --destination '$web' \
  --overwrite

# prd 環境
az storage blob upload-batch \
  --account-name stwmsprd \
  --source dist \
  --destination '$web' \
  --overwrite
```

## npm スクリプト一覧

| スクリプト | 説明 |
|-----------|------|
| `dev` | 開発サーバー起動 |
| `build` | 本番ビルド |
| `preview` | ビルド結果のプレビュー |
| `test` | テスト実行 |
| `test:watch` | テスト（ウォッチモード） |
| `test:coverage` | カバレッジ付きテスト |
| `generate:api` | OpenAPI からクライアントコード生成 |
| `lint` / `lint:fix` | ESLint |
| `format` / `format:check` | Prettier |
