# Claude Code — 学んだこと（Lessons Learned）

> 本ファイルはClaude Codeがこのプロジェクトで学んだ教訓・パターン・ユーザーの好みを記録する。
> 新しい環境・セッションでも一貫した振る舞いを維持するために参照する。

## 1. 技術的な教訓

### Mermaidフローチャート
- ノード内の `()` はノード形状と衝突する。`UNIQUE(a, b)` → `UNIQUE: a, b` に置換
- `{}` ダイヤモンドノード内の `{id}` はURL変数でも構文エラーになる。`:id` を使う
- `\n` は多くの場合動作するが、特殊文字と組み合わせると壊れる。`<br/>` が安全
- 正規表現（`[A-Z]`, `\\d`, `$`）はMermaidノード内で使えない。説明文に簡略化する
- `mermaid-cli` (`npx mmdc`) で事前検証可能

### 設計書間の整合性
- ステータス値の不一致が最も頻発する問題。Enum定義（08-common-infrastructure.md）、data-model、API設計書、画面設計書の4箇所を常に同期させる必要がある
- 環境変数名の不一致も頻発（`JWT_SECRET` vs `JWT_SECRET_KEY`、`CORS_ALLOWED_ORIGINS` vs `FRONTEND_URL`）
- APIパスの `/master/` プレフィックスの有無が画面設計書とAPI設計書でずれやすい
- エラーコード体系は英語定数名（`DUPLICATE_CODE`等）に統一済み

