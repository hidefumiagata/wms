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

## ドキュメント構成
- docs/project-plan/           - プロジェクト計画書（全12セクション完成）
- docs/architecture-blueprint/ - アーキテクチャブループリント（全12セクション完成）
- docs/functional-requirements/ - 機能要件定義書（全7セクション完成）
- docs/data-model/             - データモデル定義（作成予定）
- docs/functional-design/      - 機能設計書（作成予定）
- docs/architecture-design/    - アーキテクチャ設計書（作成予定）
- docs/test-specifications/    - テスト仕様書（作成予定）

## 開発ルール（実装フェーズ）
- 作業前にGitHub Issueを作成する
- ブランチ命名規則: `feature/[Issue#]_短い説明`（例: `feature/12_inventory-management`）
- mainへの直接コミット不可（ドキュメント整備フェーズ完了後）

### ブランチ戦略の詳細
- 新しい実装タスクを始める前に GitHub Issue を作成する
- Issue番号を使ったブランチを作成してから作業を開始する
- PRはmainブランチへ向けて作成する

**Why:** ドキュメント整備フェーズ完了後、実装フェーズからはIssueとブランチで作業単位を管理したい。

## プロジェクト計画書 進捗
- [x] セクション1: プロジェクト憲章
- [x] セクション2: スコープ管理計画（WBS）※ドラフト、後で拡充
- [x] セクション3: 要件管理計画
- [x] セクション4: スケジュール管理計画 ※管理外（サンデープログラミングのため）
- [x] セクション5: コスト管理計画
- [x] セクション6: 品質管理計画
- [x] セクション7: リソース管理計画
- [x] セクション8: コミュニケーション管理計画
- [x] セクション9: リスク管理計画
- [x] セクション10: 調達管理計画
- [x] セクション11: ステークホルダー管理計画
- [x] セクション12: 変更管理計画
