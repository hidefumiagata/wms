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
  functional-design/
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

---

## 生成ルール

### 1. カテゴリ別 index.html

- フォルダ内の `.md` ファイルをファイル名の昇順で読み込む
- 各 `.md` ファイルの **1行目**（通常 `# タイトル`）をTOCのタイトルとして使用する
- `#` プレフィックスは取り除いてタイトル文字列とする
- TOCは各セクションへのアンカーリンク（`#ファイル名ベース`）
- 全 `.md` のHTMLをTOCの下に縦に並べてレンダリングする
- `functional-design` カテゴリは末尾に **「モックアップを見る」** リンクを追加する（`mockups/index.html` へ）

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

### 4. モックアップビューワー (functional-design/mockups/index.html)

- `mockups/` フォルダ内の `*.html` ファイル（`_` 始まり・`index.html` 除く）を対象とする
- ファイル名の昇順で並べる
- 各モックアップの表示タイトルはHTMLの `<title>` タグから取得する
- **表示形式**: 各モックアップを縦に1ページに並べる（`<iframe>` 使用）
  - iframeのロード後にJSでコンテンツの高さに自動リサイズする
  - iframeとモックアップHTML、index.htmlは同一ディレクトリのためsame-originで動作する
- 左サイドバーにモックアップ名のリスト（クリックでスクロール）を設ける

### 5. メインポータル (docs/index.html)

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

## 変更管理

- このスクリプトに機能追加・変更を加える場合は本 `spec.md` を同時に更新する
- モックアップHTML (`mockups/*.html`) はビルドスクリプトで生成・編集しない
