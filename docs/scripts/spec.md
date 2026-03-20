# ドキュメントHTMLビルド仕様書

## 概要

`docs/scripts/build-docs.js` が単一のエントリポイントとして、WMSドキュメントポータルの全HTMLを生成する。
npm依存なし（Node.js 標準モジュールのみ使用）。

---

## ファイル構成

```
docs/
  scripts/
    build-docs.js        # メインビルドスクリプト（唯一のJSエントリポイント）
    spec.md              # 本仕様書
    lib/
      mermaid.min.js          # Mermaid描画ライブラリ（ビルド時に自動ダウンロード）
      viewer-static.min.js    # diagrams.net viewerライブラリ（ビルド時に自動ダウンロード）
  index.html             # メインポータル（自動生成）
  [カテゴリフォルダ]/
    index.html           # カテゴリ別ドキュメントページ（自動生成）
    reviews/             # レビュー記録票フォルダ（存在する場合のみ）
      index.html         # レビュー記録票ビューワー全件（自動生成）
      index-screen.html  # 画面設計レビュー（自動生成・functional-designのみ）
      index-api.html     # API設計レビュー（自動生成・functional-designのみ）
      index-report.html  # 帳票設計レビュー（自動生成・functional-designのみ）
      index-other.html   # その他設計レビュー（自動生成・functional-designのみ）
      *.md               # レビュー記録票（手動管理・編集禁止）
  functional-design/
    index.html           # ハブページ（サブカテゴリカード一覧）（自動生成）
    index-screen.html    # 画面設計ページ（自動生成）
    index-api.html       # API設計ページ（自動生成）
    index-report.html    # 帳票設計ページ（自動生成・対象ファイルなしはスキップ）
    index-other.html     # その他設計ページ（自動生成・対象ファイルなしはスキップ）
    mockups/
      index.html         # モックアップビューワー（自動生成）
      *.html             # モックアップHTML（手動管理・編集禁止）
      _wms-design.css    # モックアップ共通CSS（手動管理・編集禁止）
```

---

## 使い方

```bash
# 全フォルダ再生成（docs/index.html + 全カテゴリ + mockups/index.html）
node docs/scripts/build-docs.js

# 指定フォルダのみ再生成
node docs/scripts/build-docs.js functional-requirements
node docs/scripts/build-docs.js functional-design
node docs/scripts/build-docs.js architecture-blueprint

# ID整合性検証（HTMLは生成しない）
node docs/scripts/build-docs.js --validate
```

---

## 対象ドキュメントフォルダ

| フォルダ名 | タイトル（日本語） | タイトル（英語） |
|---|---|---|
| project-plan | プロジェクト計画書 | Project Plan |
| architecture-blueprint | アーキテクチャブループリント | Architecture Blueprint |
| functional-requirements | 機能要件定義書 | Functional Requirements |
| data-model | データモデル定義 | Data Model |
| functional-design | 機能設計書 | Functional Design |
| architecture-design | アーキテクチャ設計書 | Architecture Design |
| test-specifications | テスト仕様書 | Test Specifications |

> **注**: `openapi/`（OpenAPI定義 `wms-api.yaml`）はリポジトリルートに配置される。ビルド対象外だがAPIインターフェースのSSOTとして重要。

---

## 生成ルール

### 1. カテゴリ別 index.html

- フォルダ内の `.md` ファイルをファイル名の昇順で読み込む
- 各 `.md` ファイルの **1行目**（通常 `# タイトル`）をTOCのタイトルとして使用する
- `#` プレフィックスは取り除いてタイトル文字列とする
- TOCは各セクションへのアンカーリンク（`#ファイル名ベース`）
- 全 `.md` のHTMLをTOCの下に縦に並べてレンダリングする
- `reviews/` サブフォルダが存在するカテゴリは末尾に **「レビュー記録を見る」** リンクを追加する（`reviews/index.html` へ）
- **`functional-design` カテゴリは例外**: 通常のカテゴリページを生成せず、サブカテゴリに分割する（後述「8. 機能設計書サブカテゴリ」参照）

### 2. Markdownレンダリング

- **改行コード**: `\r\n` → `\n` に正規化してから処理する
- **コードブロック**: ` ```lang ``` ` → `<pre><code class="lang-{lang}">` （HTMLエスケープ）
- **Mermaidブロック**: ` ```mermaid ``` ` → `<div class="mermaid">` （mermaid.run()で描画）
- **テーブル**: Markdown表 → `<table><thead><tbody>` に変換
- **見出し**: `#` `##` `###` `####` → `<h1>` `<h2>` `<h3>` `<h4>`
- **水平線**: `---` → `<hr>`
- **引用**: `> テキスト` → `<blockquote>`
- **リスト**: `- ` / `* ` → `<ul><li>`, `1. ` → `<ol><li>`
- **インライン**: `**太字**`, `*斜体*`, `~~取り消し~~`, `` `コード` ``, `[リンク](url)` を変換
- **段落**: 上記以外の連続テキスト行を `<p>` で包む
- **Markdownの内容は改変しない**（レンダリングのみ）

### 3. draw.io図のインライン表示

- Markdownに `[ラベル](relative/path/to/file.drawio)` 形式のリンクがある場合、HTMLに図をインライン表示する
- ビルド時に drawio ファイルの XML を読み込み、`<div class="mxgraph" data-mxgraph="...">` に埋め込む
- 表示には diagrams.net viewer JS (`viewer-static.min.js`) を使用する
- `viewer-static.min.js` を `docs/scripts/lib/` に格納する。なければビルド時に `https://viewer.diagrams.net/js/viewer-static.min.js` から自動ダウンロード
- 各カテゴリHTMLから viewer-static.min.js への相対パス: `../scripts/lib/viewer-static.min.js`
- drawio ファイルが存在しない場合はリンクとエラーメッセージを表示する

### 4. Mermaid描画

- `mermaid.min.js` を `docs/scripts/lib/mermaid.min.js` に格納する
- ビルド時に存在チェックし、なければ CDN (`https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js`) から自動ダウンロードする
- 生成HTML内で `mermaid.initialize({ startOnLoad: false })` + `mermaid.run()` を使用する
- 各カテゴリHTMLから `mermaid.min.js` への相対パス: `../scripts/lib/mermaid.min.js`

### 5. モックアップビューワー (functional-design/mockups/index.html)

- `mockups/` フォルダ内の `*.html` ファイル（`_` 始まり・`index.html` 除く）を対象とする
- ファイル名の昇順で並べる
- 各モックアップの表示タイトルはHTMLの `<title>` タグから取得する
- **表示形式**: 各モックアップを縦に1ページに並べる（`<iframe>` 使用）
  - iframeのロード後にJSでコンテンツの高さに自動リサイズする
  - iframeとモックアップHTML、index.htmlは同一ディレクトリのためsame-originで動作する
- 左サイドバーにモックアップ名のツリーリストを設ける
  - 各項目をクリックすると、対応する画面モックアップ（iframe）の位置まで **自動スクロール** する
  - スクロール対象は `window.scrollTo` ではなく、各 iframe のラッパー要素の `scrollIntoView({ behavior: 'smooth' })` を使用する
  - 現在表示中のモックアップに対応するサイドバー項目をハイライト表示する（`IntersectionObserver` で追跡）

### 6. レビュー記録票ビューワー ([カテゴリ]/reviews/index.html)

- カテゴリフォルダ内に `reviews/` サブフォルダが存在する場合に生成する
- `reviews/` フォルダ内の `.md` ファイルを対象とする（`.tmp.md` で終わるファイルは除外）
- ファイル名の昇順で並べる
- 各 `.md` ファイルの **1行目**（`# タイトル`）をTOCのタイトルとして使用する
- **表示形式**: TOC + 全レコードの内容を縦に連結してレンダリング（カテゴリ別 index.html と同じルール）
- ヘッダーにカテゴリ名（例: 「機能設計書 — レビュー記録」）を表示する
- 「← ドキュメントに戻る」リンクを設ける（`../index.html` へ）
- Markdownレンダリング・Mermaid・draw.io表示は「2. Markdownレンダリング」「3. draw.io図のインライン表示」「4. Mermaid描画」と同じルールを適用する
- `mermaid.min.js` と `viewer-static.min.js` への相対パスは `../../scripts/lib/` とする

### 7. 機能設計書サブカテゴリ (functional-design/)

`functional-design` カテゴリは `.md` ファイルのファイル名パターンで4つのサブカテゴリに分類し、それぞれ独立したページを生成する。

#### サブカテゴリ定義

| サブカテゴリ | 出力ファイル | 対象 .md パターン | タイトル |
|---|---|---|---|
| 画面設計 | `index-screen.html` | ファイル名に `-screen-` を含む | 画面設計 / Screen Design |
| API設計 | `index-api.html` | ファイル名に `-api-` を含む | API設計 / API Design |
| 帳票設計 | `index-report.html` | ファイル名に `-report-` を含む | 帳票設計 / Report Design |
| その他設計 | `index-other.html` | 上記3つのいずれにも該当しない `.md`（`_` 始まりを除く）。BAT-*, IF-*, OTH-* 等が該当 | その他設計 / Other Design |

- 対象 `.md` が0件のサブカテゴリは出力ファイルを生成しない
- 各サブカテゴリページの構造はカテゴリ別 index.html（ルール1）と同じ（TOC + レンダリング）
- `functional-design/index.html` はサブカテゴリのハブページとして生成する（後述）

#### ハブページ (functional-design/index.html)

- 各サブカテゴリをカードグリッドで表示する（メインポータルと同様のデザイン）
- 各カードは対応する `index-{sub}.html` へリンクする
- ファイルが0件のサブカテゴリカードは非活性表示（`opacity:0.5; pointer-events:none`）
- パンくずリスト: `Home > 機能設計書`

#### モックアップリンク

- **画面設計サブカテゴリページ**（`index-screen.html`）のみ **「モックアップを見る」** リンクを末尾に追加する（`mockups/index.html` へ）

#### レビュー記録リンク（サブカテゴリごとのフィルタリング）

`reviews/` フォルダ内の `.md` ファイルをサブカテゴリごとにフィルタリングし、各サブカテゴリページの末尾に「レビュー記録を見る」リンクを表示する。
対象レビューファイルは `reviews/index-{sub}.html` に分割して生成する。

| サブカテゴリ | レビューファイル対象パターン | レビュー出力ファイル |
|---|---|---|
| 画面設計 | `-api-` を含まない `.md`（`.tmp.md` 除外） | `reviews/index-screen.html` |
| API設計 | ファイル名に `-api-` を含む `.md`（`.tmp.md` 除外） | `reviews/index-api.html` |
| 帳票設計 | ファイル名に `-report-` を含む `.md`（`.tmp.md` 除外） | `reviews/index-report.html` |
| その他設計 | 上記いずれにも該当しない `.md`（`.tmp.md` 除外） | `reviews/index-other.html` |

- 対象レビューファイルが0件のサブカテゴリには「レビュー記録を見る」リンクを表示しない
- `reviews/index.html`（全件）は引き続き生成する
- 各レビューサブページの「← ドキュメントに戻る」は対応するサブカテゴリページへ（例: `../index-screen.html`）

### 8. メインポータル (docs/index.html)

- 全カテゴリをカードグリッドで表示する
- 各カードはカテゴリの `index.html` へリンクする
- フォルダ内の `.md` ファイル数を表示する

---

## デザイン仕様

- カラーテーマ: ダークネイビー (`#1a1a2e`, `#16213e`, `#0f3460`)
- フォント: `'Segoe UI', 'Hiragino Sans', 'Yu Gothic', sans-serif`
- テーブルヘッダー背景: `#1a1a2e`（白文字）
- コードブロック背景: `#1a1a2e`（明るいグレー文字）
- Mermaidブロック背景: `#f8f9ff`

---

## ID整合性検証（--validate）

`node docs/scripts/build-docs.js --validate` を実行すると、`functional-design/_id-registry.md`（ID体系のSSOT）と実際の設計書ファイルの整合性を自動検証する。HTMLは生成しない。

### 検証ルール

| ルールID | チェック内容 | 重大度 | 終了コード |
|---------|-----------|--------|----------|
| V-001 | 画面ID一覧のIDに対応するSCR-*.mdファイルが存在するか | ERROR | 1 |
| V-002 | API ID一覧のIDが対応するAPI-*.mdファイル内に定義されているか | ERROR | 1 |
| V-003 | SCR-*.mdのイベント一覧が参照するAPIパスがAPI ID一覧に存在するか | WARNING | 0 |
| V-004 | RPT ID一覧に対応するRPT-*.mdファイルが存在するか | ERROR | 1 |
| V-005 | カテゴリ一覧のファイル数と実際のファイル数が一致するか | WARNING | 0 |
| V-006 | 同一プレフィックス内でIDの重複がないか | ERROR | 1 |
| V-007 | 全プレフィックスに対応するIDが1つ以上存在するか | WARNING | 0 |

### 終了コード

- `0`: 全チェック合格（WARNINGのみの場合も0）
- `1`: ERRORが1件以上存在

### SSOT

ID体系の唯一の定義場所は `functional-design/_id-registry.md` である。新規ID追加時は本ファイルを先に更新し、`--validate` で検証してから個別設計書を作成する。

---

## 変更管理

- このスクリプトに機能追加・変更を加える場合は本 `spec.md` を同時に更新する
- モックアップHTML (`mockups/*.html`) はビルドスクリプトで生成・編集しない
