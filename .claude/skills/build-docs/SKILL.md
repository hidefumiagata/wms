---
name: build-docs
description: docsフォルダのHTMLポータルを再生成する。MarkdownやDrawioファイルを変更した後に実行する。
argument-hint: [フォルダ名(省略=全体)]
allowed-tools: Bash
disable-model-invocation: true
---

以下のコマンドを実行してHTMLを再生成してください：

```
node docs/scripts/build-docs.js $ARGUMENTS
```

有効なフォルダ名（引数として指定可能）:
- `project-plan` — プロジェクト計画書
- `architecture-blueprint` — アーキテクチャブループリント
- `functional-requirements` — 機能要件定義書
- `data-model` — データモデル定義
- `functional-design` — 機能設計書（モックアップも再生成）
- `architecture-design` — アーキテクチャ設計書
- `test-specifications` — テスト仕様書

引数を省略した場合は全フォルダ + `docs/index.html`（メインポータル）を再生成します。

実行完了後、生成されたファイルと各ファイルサイズを報告してください。
