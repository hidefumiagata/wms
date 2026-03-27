---
name: dev
description: バックエンド開発タスクを一気通貫で実行する。開発準備→TDD開発→テストカバレッジ100%→レビュー→指摘対応→PR作成まで。
argument-hint: <開発内容の説明 または Issue番号>
---

# 開発タスクスキル

開発対象: $ARGUMENTS

---

## Phase 1: 開発準備

**目的**: 最新コードベースの取得、設計書の理解、タスク計画、Issue・ブランチの作成

### Step 1.1: mainブランチの最新化

```
git checkout main
git pull origin main
```

### Step 1.2: プロジェクトルール読み込み

以下のファイルを**必ず**読み込んでコンテキストに入れる:
1. `CLAUDE.md` — プロジェクト全体のルール
2. `CLAUDE-LESSONS-LEARNED.md` — 過去の学び
3. `docs/ARCHITECTURE-RULES.md` — アーキテクチャルール集

### Step 1.3: 対象設計書の特定と読み込み

$ARGUMENTS の内容から、関連する設計書を特定して読み込む:
- `docs/functional-design/API-*.md` — API設計書（バックエンドの場合）
- `docs/functional-design/SCR-*.md` — 画面設計書（フロントエンドの場合）
- `docs/functional-requirements/` — 機能要件定義書
- `docs/data-model/` — データモデル定義
- `openapi/wms-api.yaml` — OpenAPI定義（API実装の場合）

既存の類似実装も探して読む（既存パターンの踏襲のため）。

### Step 1.4: タスク計画

設計書を元に、作業タスクを細分化してチェックリスト形式のタスク一覧を作成する。
タスク粒度の目安:
- 1タスク = 1つのクラスまたは1つの機能単位
- テストは本体実装とセットで1タスク

### Step 1.5: GitHub Issue 作成

CLAUDE.md の Issue ルールに従い、以下の形式で Issue を作成する:
- **タイトル**: 簡潔な日本語
- **ラベル**: `feature` / `bugfix` / `docs`
- **本文**: 概要、目的、タスク一覧（チェックリスト形式）、関連ドキュメント

### Step 1.6: ブランチ作成

```
git checkout -b feature/{Issue番号}_{短い説明}
```

---

## Phase 2: TDD開発

**目的**: テスト駆動で機能を実装する

### Step 2.1: OpenAPI コード生成

バックエンド開発の場合、まず OpenAPI からコードを生成する:
```
cd backend && ./gradlew openApiGenerate
```

生成されたインターフェースとモデルクラスを確認する。

### Step 2.2: テストコード先行

**テスト駆動開発を厳守する。**

1. Service のテストクラスを先に作成する
   - 正常系テスト
   - 異常系テスト（バリデーションエラー、Not Found、ステータス不正等）
   - 境界値テスト
2. Controller のテストクラスを作成する
   - HTTPステータスコードの検証
   - レスポンスボディの検証

テストの書き方は既存テストを参考にする（`@ExtendWith(MockitoExtension.class)` + `@Mock` / `@InjectMocks` パターン）。

### Step 2.3: 実装

テストを通すための実装を行う:
1. **Entity** — JPA エンティティクラス
2. **Repository** — Spring Data JPA リポジトリ
3. **Service** — ビジネスロジック
4. **Controller** — OpenAPI 生成インターフェースを `implements`

実装時の必須ルール（docs/ARCHITECTURE-RULES.md 参照）:
- Controller は `{Domain}Api` インターフェースを implements する
- 手書きDTOは禁止、OpenAPI生成モデルのみ使用
- Entity → DTO 変換は Controller 内のprivateメソッドで実装
- Service層に `@Transactional` を付与
- 例外は `shared.exception` パッケージのカスタム例外を使用

### Step 2.4: タスク進捗更新

1タスク完了ごとに Issue のチェックリストにチェックを付ける。

---

## Phase 3: テストカバレッジ

**目的**: C0/C1 100% を達成する

### Step 3.1: カバレッジ計測

```
cd backend && ./gradlew test jacocoTestReport
```

### Step 3.2: カバレッジ分析

JaCoCo XML レポートを解析して、未カバーの行とブランチを特定する:
```python
# backend/build/reports/jacoco/test/jacocoTestReport.xml を解析
```

対象クラスごとに C0 (LINE) と C1 (BRANCH) を確認する。

### Step 3.3: テスト追加

未カバー箇所に対してテストを追加する。追加すべきテストの典型例:
- null/空値の分岐
- 条件分岐の false パス
- 例外スローのパス（orElseThrow 等）
- ループ内の break/continue
- 三項演算子の両側

### Step 3.4: 100% 達成確認

再度カバレッジを計測し、100% になるまで Step 3.2-3.3 を繰り返す。

**100% にできない場合**は理由を調べる。以下は許容される理由:
- `&&` 演算子の短絡評価によるJaCoCo制約
- 到達不可能な防御コード（事前バリデーションで弾かれるパス）
- 後続PRで実装予定のスタブメソッド

---

## Phase 4: PR 作成

**目的**: mainブランチへのPRを作成する

### Step 4.1: コミット・プッシュ

変更をコミットしてプッシュする。コミットメッセージは日英併記。

### Step 4.2: PR 作成

`gh pr create` で PR を作成する。**必ず対応する Issue と紐付ける**:
```
gh pr create --title "タイトル" --body "..." --label feature
```
作成後、Issue と紐付ける:
```
gh issue develop {Issue番号} --pr {PR番号}
```
または PR 本文に `Closes #{Issue番号}` を含める。

**注意: チェーンPR（baseがmain以外のブランチ）の場合、`Closes` による自動クローズは発動しない。** マージ後に `gh issue close {Issue番号}` で手動クローズすること。

PR本文に以下を含める:

```markdown
Closes #{Issue番号}

## Summary
- 変更内容の箇条書き

## Test coverage
| 指標 | 値 |
|------|-----|
| C0（ステートメント） | XX% |
| C1（ブランチ） | XX% |

## Test plan
- [x] テスト内容の箇条書き
```

100% でない場合はその理由も記載する。

---

## Phase 5: レビュー

**目的**: 3つの観点で並列レビューし、結果をPRコメントに投稿する

3つのサブエージェントを**並列で**起動する:

### Agent 1: 専門家レビュー
対象領域の技術的品質をレビューする:
- コード品質・可読性
- パフォーマンス
- 設計パターンの適切さ
- エラーハンドリング

### Agent 2: セキュリティレビュー
セキュリティ専門家の観点でSAST的な静的解析:
- OWASP Top 10
- インジェクション（SQL、コマンド、LDAP等）
- 認証・認可の不備
- 機密情報の漏洩
- 依存ライブラリの脆弱性

### Agent 3: 設計準拠レビュー
`docs/architecture-design/` および `docs/ARCHITECTURE-RULES.md` を読み込み、以下をチェック:
- API Firstルール（OpenAPI生成インターフェースのimplements）
- DTO規約（手書きDTO禁止、OpenAPI生成モデル使用）
- テストカバレッジ目標（C0/C1: 100%）
- 例外ハンドリング方針
- トランザクション管理方針
- ロギング規約

### レビュー結果の投稿

3つのレビュー結果を統合し、PRコメントとして投稿する:
```markdown
## レビュー結果

### 1. セキュリティレビュー — PASS/FAIL
...

### 2. 専門家レビュー
**Critical/Major/Minor/Suggestion**
...

### 3. 設計準拠レビュー
| # | チェック項目 | 結果 |
...
```

---

## Phase 6: レビュー指摘対応

**目的**: レビュー指摘事項をすべて対応する

### 対応ルール

1. **コード修正・テスト追加・リファクタリング** → そのPR内で即対応
   - 1件ごとに: 修正 → テスト → コミット → プッシュ
2. **設計書（docs/配下）の修正が必要** → 新しいIssueを作成
3. **設計判断・方針決定が必要** → 新しいIssueを作成

### 対応後の確認

指摘対応後に再度テストを実行して全テスト通過を確認する:
```
cd backend && ./gradlew test
```

### 最終確認: 全指摘事項の棚卸し

全レビュー指摘の対応状況を一覧表にまとめ、漏れがないか最終確認する。
PRコメントに以下の形式で対応結果を投稿する:

```markdown
## レビュー指摘 対応結果

| # | 重要度 | 指摘内容 | 対応 | コミット |
|---|--------|---------|------|---------|
| M-1 | Major | 〇〇の修正 | ✅ 対応済み | abc1234 |
| m-2 | Minor | △△の改善 | ✅ 対応済み | def5678 |
| S-1 | Suggestion | □□の提案 | 対応見送り（理由: ...） | — |
| F#1 | Medium | ××の不整合 | 📋 Issue #999 で追跡 | — |
```

### 未対応指摘のIssue化

以下に該当する指摘は、PR内での対応ではなく **GitHub Issue を作成して追跡**する:
- **設計書（docs/配下）の修正が必要な指摘**
- **設計判断・方針決定が必要な指摘**
- **スコープ外だが将来対応すべき指摘**

Issue作成時のルール:
- タイトルに元のレビュー指摘番号を含める（例: `[M-3] 〇〇の対応`）
- 本文に指摘内容・背景・対応方針案を記載する
- ラベルは指摘内容に応じて `feature` / `bugfix` / `docs` を付与する
- PRコメントの対応結果表にIssue番号を記載する

**対応見送りの理由は必ず明記する。** 「見送り」だけでは不十分。なぜ見送るのか（影響範囲が小さい、現状で問題ない、別PRで対応予定等）を書く。

---

## 完了条件

以下がすべて満たされていることを確認:
- [ ] 全テストがグリーン
- [ ] C0/C1 カバレッジが100%（または理由付きで例外）
- [ ] PR本文にカバレッジ記載
- [ ] 3種レビュー完了・結果をPRコメント
- [ ] **全指摘事項の対応状況を一覧表でPRコメントに投稿済み**
- [ ] **未対応指摘のうちIssue化が必要なものはすべてIssue作成済み**
- [ ] Issue のタスク一覧すべてチェック済み

**注意: PRのマージはユーザーが行う。Claudeは絶対にマージしない。**
