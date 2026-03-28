---
name: docs
description: ドキュメント作成・修正タスクを一気通貫で実行する。設計書の作成/修正→ID登録→レビュー→指摘対応→HTMLビルド→PR作成まで。
argument-hint: <作成/修正内容の説明 または Issue番号>
---

# ドキュメント作成・修正スキル

対象: $ARGUMENTS

---

## Phase 1: 準備

**目的**: 対象の特定、関連ドキュメントの把握、タスク計画

### Step 1.1: mainブランチの最新化

```
git checkout main
git pull origin main
```

### Step 1.2: プロジェクトルール読み込み

以下を**必ず**読み込む:
1. `CLAUDE.md` — SSOTルール・ドキュメント作成ルール
2. `docs/ARCHITECTURE-RULES.md` — RULE-DOC-* ルール

### Step 1.3: 対象の特定と関連ドキュメント読み込み

`docs/document-map.yaml` で対象モジュールの関連ドキュメントを確認する。

修正の場合: 対象ファイルを読み、修正箇所を特定する。
新規作成の場合: 既存の同種ドキュメントをテンプレートとして読む。
- API設計書: `docs/functional-design/API-*.md`（既存1つ）
- 画面設計書: `docs/functional-design/SCR-*.md`（既存1つ）
- 帳票設計書: `docs/functional-design/RPT-*.md`（既存1つ）

関連する要件定義書・データモデル・API設計書も読み込む。

### Step 1.4: タスク計画・Issue作成

作業タスクをチェックリスト形式で整理し、GitHub Issue を作成する:
- **ラベル**: `docs`
- **本文**: 概要、目的、タスク一覧、関連ドキュメント

既にIssueが存在する場合はタスク一覧を更新する。

### Step 1.5: ブランチ作成

```
git checkout -b docs/{Issue番号}_{短い説明}
```

---

## Phase 2: ドキュメント作成・修正

**目的**: SSOTルールを遵守してドキュメントを作成・修正する

### Step 2.1: ID登録（新規ドキュメントの場合）

**新規ID追加時は必ず先に `docs/functional-design/_id-registry.md` を更新する。**

1. 該当セクション（3.1 画面ID / 3.2 API ID / 3.3 RPT ID 等）にIDを追加
2. ID重複チェック: `node docs/scripts/build-docs.js --validate`

### Step 2.2: ドキュメント作成・修正

**SSOTルール厳守**:
- 1つの事実は1箇所にだけ定義する。他ドキュメントは参照リンクで示す
- テーブル定義はdata-model/を参照（API設計書にカラム一覧を複製しない）
- ビジネスルールはfunctional-requirements/を参照（複製しない）
- ステータス定義はAPI設計書冒頭がSSOT

**修正時の注意**:
- `Grep` で設計書内の参照箇所を全て洗い出してから対応する
- 同一内容が複数ファイルに書かれていたらSSOT側を正として他は参照リンクに置換

### Step 2.3: document-map.yaml 更新（新規ドキュメント追加時のみ）

新しいファイルを追加した場合は `docs/document-map.yaml` の該当モジュールに追記する。

### Step 2.4: タスク進捗更新

1タスク完了ごとに Issue のチェックリストにチェックを付ける。

---

## Phase 3: レビュー

**目的**: 作成・修正したドキュメントの品質を確認する

### Step 3.1: SSOT・ID整合性検証

```
node docs/scripts/build-docs.js --validate
```

全ルール V-001〜V-007 がパスすることを確認する。

### Step 3.2: ドキュメント整合レビュー

サブエージェントを起動し、以下を実施する:
- 変更箇所と関連ドキュメント（要件定義書・データモデル・他の設計書）のクロスチェック
- SSOT違反がないか確認
- 矛盾・修正漏れがないか確認

レビュー結果はPRコメントとして投稿する:
```markdown
## ドキュメント整合レビュー結果

| # | 対象箇所 | 参照ドキュメント | 指摘内容 | 対応 |
|---|---------|-----------------|---------|------|
| 1 | ... | ... | ... | ✅ 修正済み / 📋 Issue #NNN |
```

### Step 3.3: レビュー記録票作成（新規設計書の場合）

新規設計書を作成した場合は `/review` スキルを実行してレビュー記録票を作成する。
修正PRでは不要。

---

## Phase 4: 指摘対応

**目的**: レビュー指摘事項をすべて対応する

### 対応ルール

1. **対象ファイルの問題** → 即修正
2. **他ドキュメントの問題** → 修正可能なら修正。設計判断が必要なら新しいIssueを作成
3. **SSOT違反** → SSOT側を正として複製側を参照リンクに置換

指摘対応後、再度 `--validate` を実行して整合性を確認する。

---

## Phase 5: HTMLビルド・PR作成

**目的**: HTMLポータルを再生成しPRを作成する

### Step 5.1: HTMLビルド

```
node docs/scripts/build-docs.js
```

document-map.yaml を更新した場合は関連マップも再生成:
```
node docs/scripts/build-docs.js --generate-map
```

### Step 5.2: コミット・プッシュ

ドキュメント修正とHTMLビルドは**別コミット**にする:

```
# コミット1: ドキュメント修正
docs: 修正内容（日本語）/ English description

# コミット2: HTMLビルド（修正がある場合）
docs: HTMLポータル再生成 / Regenerate HTML portal
```

コミットメッセージに `Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>` を付与。

### Step 5.3: PR作成

PR本文に `Closes #{Issue番号}` を含めてIssueと紐付ける:

```markdown
Closes #{Issue番号}

## Summary
- 変更内容の箇条書き

## 変更ファイル
- 修正/追加したドキュメント一覧

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

---

## 完了条件

- [ ] `node docs/scripts/build-docs.js --validate` がパス
- [ ] HTMLポータルが最新状態で再生成済み
- [ ] ドキュメント整合レビュー完了・結果をPRコメント
- [ ] レビュー指摘対応済み（未対応分はIssue化済み）
- [ ] Issue のタスク一覧すべてチェック済み

**注意: PRのマージはユーザーが行う。Claudeは絶対にマージしない。**
