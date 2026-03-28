# WMS ShowCase Project - Claude Instructions

## プロジェクト概要
- **目的1**: 本格的なWMS（倉庫管理システム）を構築する
- **目的2**: Claude Codeの機能を可能な限り使い倒す
- **コンセプト**: Claude Code ShowCase（Claude Codeで本格業務システムが作れることを示す）

## 技術スタック（確定済み）
- **クラウド**: Azure
- **フロントエンド**: Vue 3 + TypeScript + Element Plus + Vite + Pinia + vue-i18n（日英）
- **バックエンド**: Spring Boot 3.x + Java 21 + Gradle
- **DB**: Azure Database for PostgreSQL Flexible Server（B1ms）
- **コンテナ**: Azure Container Apps（min replicas=0）
- **ストレージ**: Azure Blob Storage（フロントエンド静的ホスティング + I/Fファイル）
- **コンテナレジストリ**: Azure Container Registry (ACR)
- **IaC**: Terraform
- **API設計**: API First（OpenAPI → コード自動生成）
- **認証**: Spring Security + JWT + httpOnly Cookie

## グランドルール
1. 議論の結論は必ずドキュメントに残す（私から提案、またはユーザーから指示）
2. コンテキスト圧縮が近づいたら警告 → 記録保存 → 新セッション開始

## ドキュメント作成ルール
- 構成図：draw.io形式（.drawioファイル）
- フローチャート：Mermaid（Markdownに埋め込み）
- ドキュメント言語：日本語で作成→後で英語翻訳（xxx.md / xxx.en.md）
- コミットメッセージ：日英併記
- HTMLポータル：draw.ioファイルをインライン表示する（diagrams.net viewer JS使用）※build-docs.js要対応

## ドキュメントSSOT（Single Source of Truth）ルール

**1つの事実は1箇所にだけ定義する。** 他のドキュメントは参照リンクで示す。

### 情報の定義場所（SSOT）一覧

| 情報 | SSOT（定義場所） | 他ドキュメントでの記載ルール |
|------|-----------------|------------------------|
| **ビジネスルール・業務フロー** | functional-requirements/*.md | 他は「functional-requirements/XXXを参照」とリンク。ルールの複製禁止 |
| **技術方針・共通規約**（認証方式、エラーハンドリング、DTO規約、ロック方式等） | architecture-blueprint/*.md | 他は「architecture-blueprint/XXXを参照」とリンク。方針の複製禁止 |
| **テーブル定義・カラム定義** | data-model/*.md | API設計書・画面設計書にカラム一覧を複製しない。必要なら「data-model/XXXを参照」 |
| **APIエンドポイント・リクエスト/レスポンス仕様** | functional-design/API-*.md | 画面設計書(SCR)にはAPI IDとエンドポイントパスのみ記載。パラメータ詳細はAPI設計書を参照 |
| **画面項目・バリデーション・メッセージ・UX** | functional-design/SCR-*.md | API設計書に画面メッセージを複製しない |
| **パスワードポリシー** | architecture-blueprint/10-security-architecture.md | 画面設計書は「セキュリティアーキテクチャを参照」。ポリシー値の複製禁止 |
| **ステータス遷移・ステータスコード定義** | functional-design/API-*.md（各モジュールのAPI設計書冒頭） | 画面設計書はマッピング表で参照。functional-requirementsはステータス名のみ記載 |
| **システムパラメータの一覧・デフォルト値** | data-model/02-master-tables.md（system_parametersの初期データ） | 他は「data-modelを参照」。値の複製禁止 |
| **バッチ処理の内部ロジック・SQL** | functional-design/BAT-*.md | 他は参照リンクのみ |
| **I/Fのデータマッピング・採番ロジック** | functional-design/IF-*.md | 他は参照リンクのみ |
| **レポートPDFレイアウト・カラム定義** | functional-design/RPT-*.md | 他は参照リンクのみ |
| **センシティブエンドポイント一覧** | architecture-design/08-common-infrastructure.md セクション4.5 | 他は参照リンクのみ |
| **全ID体系（画面ID・API ID・RPT ID・BAT ID・IFX ID・伝票番号・メッセージID）** | functional-design/_id-registry.md | 個別設計書にID一覧を複製しない。新規ID追加時は _id-registry.md を先に更新する |
| **APIインターフェース定義（エンドポイント・リクエスト/レスポンス型）** | openapi/wms-api.yaml | functional-design/API-*.mdは業務ロジック設計のSSOT。APIの型定義はOpenAPIがSSOT |
| **テスト戦略・カバレッジ目標・完了基準** | test-specifications/00-test-plan.md | 他は「test-specificationsを参照」。カバレッジ値の複製禁止 |

### SSOTの運用ルール

1. **新しい情報を追加する時**: まずSSOT（定義場所）に書く。他ドキュメントには参照リンクのみ
2. **情報を変更する時**: SSOTのみ変更する。参照リンクは変更不要（リンク先が常に最新）
3. **レビューで矛盾を見つけた時**: SSOT側を正とし、複製側を参照リンクに置き換える
4. **例外**: 画面設計書のイベント一覧にはAPIエンドポイントパス（`POST /api/v1/xxx`）を記載してよい（開発時の利便性のため）。ただしリクエスト/レスポンスの詳細は複製しない

## ドキュメント関連マップ

実装・レビュー・設計変更の際は **[docs/document-map.yaml](docs/document-map.yaml)** で対象モジュールの関連ドキュメントを確認すること。

- **SSOT**: `docs/document-map.yaml`（YAMLが定義場所）
- **閲覧用**: `docs/document-map.md`（自動生成。`node docs/scripts/build-docs.js --generate-map` で再生成）
- モジュールごとに要件定義・API設計・画面設計・帳票・テスト仕様・データモデル・アーキテクチャの関連が定義されている
- 新しいドキュメントを追加した場合は `document-map.yaml` も更新すること

## アーキテクチャルール（実装時の必読事項）

実装作業を開始する前に必ず **[docs/ARCHITECTURE-RULES.md](docs/ARCHITECTURE-RULES.md)** を読み込むこと。
全ルールは `[RULE-XXX-NNN]` 形式で定義されている。

## 過去の学び
詳細は [CLAUDE-LESSONS-LEARNED.md](CLAUDE-LESSONS-LEARNED.md) を参照。新しいセッション開始時に必ず読み込むこと。

## 開発ルール（実装フェーズ）

開発タスクは `/dev` スキルに従って実行すること。以下はスキルを補完するルール。

### Issueルール
- **タイトル**: 簡潔な日本語
- **言語**: 日本語
- **ラベル**: `feature` / `bugfix` / `docs`
- **テンプレート**: `.github/ISSUE_TEMPLATE/` にYAML形式で定義済み

### 絶対ルール
- **PRのマージはユーザーが行う（Claudeは絶対にマージしない）**

# File Search
ファイル検索には Glob ツールを使うこと。Bash の `find` は deny されており使用できない。

# Content Search
ファイル内容の検索には Grep ツールを使うこと。単純な検索で Bash の `grep` を使わない。
パイプチェーン（`grep | sed | sort` 等）が必要な複雑な処理のみ Bash を許容する。

# File Editing
ファイルの編集・作成には Bash (sed, awk, echo 等) ではなく、Edit / Write ツールを使うこと。

# JSON Inspection
JSON ファイルの内容確認には `jq` を使うこと。Python やサブエージェントではなく、Bash + `jq` で完結させる。

# Git Commands
- `cd` から始めない。作業ディレクトリはプロジェクトルートを前提とする。
- `git add` と `git commit` は別々の Bash 呼び出しで実行する（`&&` で繋げない）。