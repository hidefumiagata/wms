# アーキテクチャ設計レビュー記録票 — フロントエンドアーキテクチャ

> 対象成果物: `docs/architecture-design/03-frontend-architecture.md`
> レビュー日: 2026-03-18
> レビュー担当: フロントエンドアーキテクチャ設計スペシャリスト（AI）
> 参照ドキュメント:
> - `docs/architecture-blueprint/01-overall-architecture.md`（全体アーキテクチャ方針）
> - `docs/architecture-blueprint/03-frontend-architecture.md`（フロントエンドアーキテクチャ ブループリント）
> - `docs/architecture-blueprint/07-auth-architecture.md`（認証・認可アーキテクチャ）
> - `docs/architecture-blueprint/08-common-infrastructure.md`（共通基盤）
> - `docs/functional-requirements/00-authentication.md`（認証機能要件）
> - `docs/functional-requirements/01-master-management.md`（マスタ管理機能要件）
> - `docs/functional-requirements/01a-system-parameters.md`（システムパラメータ機能要件）
> - `docs/functional-requirements/02-inbound-management.md`（入荷管理機能要件）
> - `docs/functional-requirements/03-inventory-management.md`（在庫管理機能要件）
> - `docs/functional-requirements/04-outbound-management.md`（出荷管理機能要件）
> - `docs/functional-requirements/04a-allocation.md`（在庫引当機能要件）
> - `docs/functional-requirements/05-reports.md`（レポート機能要件）
> - `docs/functional-requirements/06-batch-processing.md`（バッチ処理機能要件）
> - `docs/functional-requirements/07-interface.md`（外部連携機能要件）
> - `docs/functional-design/SCR-01-auth.md`（画面設計 認証）
> - `docs/functional-design/SCR-02-master-facility.md`（画面設計 マスタ管理）
> - `docs/functional-design/SCR-07-inbound.md`（画面設計 入荷管理）
> - `docs/functional-design/_standard-screen.md`（画面設計 標準テンプレート）
> - `CLAUDE.md`（プロジェクト指針）

---

## エグゼクティブサマリー

フロントエンドアーキテクチャ設計書は、ブループリント（03-frontend-architecture.md）の方針を忠実に詳細化し、実装フェーズで必要となる具体的な設計規約・コード例を提供する内容で作成した。

**主な設計内容:**
- ディレクトリ構成（モジュール分割方針、全ファイルのツリー表示）
- Vue 3 Composition API 設計規約（Composable内部の構成順序、命名規則）
- Pinia ストア設計（authStore / systemStore の完全な実装コード）
- vue-i18n 多言語対応設計（遅延読み込み、メッセージキー命名規則）
- Element Plus 利用規約（テーマカスタマイズ、コンポーネントマッピング）
- ルーティング設計（認証ガード、ロールチェック、パスワード変更強制）
- API通信層設計（401リフレッシュキュー制御、セッションタイムアウト警告）
- 共通コンポーネント設計（WmsTable, WmsSearchForm等 11コンポーネント）
- ビルド・デプロイ設計（Vite設定、Azure Blob Storageデプロイ）

**全体評価:** ブループリントとの整合性は高く、機能要件・画面設計の仕様を網羅的にカバーしている。以下のレビューで検出した指摘事項に対応することで、実装フェーズへの移行に十分な品質となる。

---

## レビュー結果

| No | チェック観点 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|------------|----------------|---------|------|---------|
| 1 | ブループリントとの整合性: Composable設計 | `03-frontend-architecture.md`（BP） | ブループリントの「1画面1ファイル、画面間共通Composableを作らない」方針を踏襲。Composable一覧が全画面ID（AUTH-001〜SYS-001）と1対1対応しており整合性あり | 情報 | 確認済み |
| 2 | ブループリントとの整合性: Piniaストア | `03-frontend-architecture.md`（BP） | authStore / systemStore の2ストア構成を踏襲。`passwordChangeRequired` フラグを authStore に追加しているが、これは認証要件（AUTH-002）で必要な項目であり妥当 | 情報 | 確認済み |
| 3 | ブループリントとの整合性: API通信 | `03-frontend-architecture.md`（BP） | 401リフレッシュフローのキュー制御を具体的に実装。ブループリントのシーケンス図と一致 | 情報 | 確認済み |
| 4 | 認証フローの網羅性 | `07-auth-architecture.md`, `00-authentication.md` | ナビゲーションガードにパスワード変更強制（`requiresPasswordChange`）を実装。AUTH-002の「変更完了まで他操作ブロック」要件を充足 | 情報 | 確認済み |
| 5 | セッションタイムアウト | `00-authentication.md` | 55分警告 + 60分強制ログアウトの二重チェックを実装。機能要件の仕様と一致 | 情報 | 確認済み |
| 6 | 倉庫切替の動作 | `01-master-management.md` | 倉庫切替時のwatch検知・再検索パターンを記述。倉庫マスタ管理画面が影響を受けない旨も明記。機能要件と整合 | 情報 | 確認済み |
| 7 | SSOTルール準拠 | `CLAUDE.md` | エラーコード体系・パスワードポリシー値・テーブル定義等の具体値は複製せず、参照リンクで示す方針を遵守 | 情報 | 確認済み |
| 8 | i18n遅延読み込みの実装詳細 | — | `beforeEnter` で言語リソースを読み込む方式を記述しているが、言語切替時に既読み込み済みモジュールの新言語リソースを再読み込みするロジックが未記述 | 軽微 | 未対応（実装フェーズで対応） |
| 9 | views/ ディレクトリの記載場所 | `03-frontend-architecture.md`（BP） | ブループリントのディレクトリ構成には `views/` が含まれているが、設計書本文のメインツリーでは `views/` を省略し付録Aに分離した。ファイル数が多いための可読性配慮だが、最初のツリーに `views/` のコメントを追加するとわかりやすい | 軽微 | 未対応（読者の参照性向上のため推奨） |

> **対応完了** (2026-03-19): 03-frontend-architecture.md のメインツリーに `views/` の省略コメントを追加
| 10 | 外部連携I/F画面のComposable/View | `07-interface.md` | 外部連携I/F（IFX-001/002）のファイルアップロード画面に対応するComposable・Viewファイルがディレクトリ構成に含まれていない。機能要件ではI/F管理画面が定義されている | 要対応 | 未対応（interface/ モジュールのComposable・Viewを追加すべき） |

> **対応完了** (2026-03-19): 03-frontend-architecture.md に外部連携I/Fモジュールディレクトリ（`composables/interface/`、`views/interface/`、`router/routes/interface.ts`）を追加
| 11 | レポート画面のComposable | `05-reports.md`, `_standard-screen.md` | レポート出力はダイアログ方式（ReportExportDialog）で実装するため個別のComposableは不要と判断しているが、RPT-001〜RPT-017の出力条件パラメータ管理について明示的に記述があるとよい | 軽微 | 未対応（実装フェーズで詳細化） |
| 12 | テスト設計の詳細 | — | `tests/` ディレクトリ構成は記述しているが、テスト方針（単体テスト対象範囲、E2Eテストシナリオ方針、カバレッジ目標等）は未記述。テスト仕様書（別ドキュメント）で対応予定と推察される | 情報 | 対象外（テスト仕様書で定義） |
| 13 | エラーハンドリングのAPI型安全性 | `08-common-infrastructure.md` | Composable内のエラーハンドリングで `e as { response?: ... }` と型アサーションを使用しているが、AxiosError型を直接使用する方がタイプセーフ | 軽微 | 未対応（実装時にAxiosError型ガードに変更推奨） |
| 14 | 営業日表示のフォーマット | `01-master-management.md`, `_standard-screen.md` | ヘッダーの営業日表示を「YYYY/MM/DD」形式とする仕様は標準テンプレートと一致。systemStoreでは「YYYY-MM-DD」（ISO形式）で保持し、表示時にフォーマット変換する方針は妥当 | 情報 | 確認済み |
| 15 | Element Plus日本語ロケール | — | Element Plus 自体の日本語ロケール（el-date-picker等のUI文言）の設定が未記述。`app.use(ElementPlus, { locale: jaLocale })` の設定が必要 | 要対応 | 未対応（main.tsにElement Plusロケール設定を追加すべき） |

> **対応完了** (2026-03-19): 03-frontend-architecture.md に Element Plus 日本語ロケール設定（`import ja from 'element-plus/es/locale/lang/ja'`）を追記

---

## 指摘事項サマリー

| 分類 | 件数 |
|------|------|
| 情報（確認済み・問題なし） | 8件 |
| 軽微（実装フェーズで対応可） | 4件 |
| 要対応（設計書修正推奨） | 2件 |

### 要対応事項の詳細

**No.10: 外部連携I/Fモジュールの追加** ✅ 対応完了（2026-03-19）
- `composables/interface/` ディレクトリに `useInterfaceFileList.ts`, `useInterfaceImport.ts` を追加する
- `views/interface/` に `InterfaceFileList.vue`, `InterfaceImport.vue` を追加する
- `router/routes/interface.ts` にルート定義を追加する
- 優先度: 中（画面数は少ないが、モジュールとして存在することの認識が重要）

**No.15: Element Plus ロケール設定** ✅ 対応完了（2026-03-19）
- `main.ts` に Element Plus の日本語ロケール設定を追加する
- `import ja from 'element-plus/es/locale/lang/ja'` を使用
- 言語切替時に `app.config.globalProperties.$ELEMENT` のロケールも切り替える
- 優先度: 高（日付ピッカー等のUI文言が英語になる問題を防止）

---

## 結論

本設計書はブループリントの方針を適切に詳細化しており、実装フェーズの着手に十分な品質を有する。要対応2件（外部連携I/Fモジュール追加、Element Plusロケール設定）を設計書に反映した上で、実装フェーズに移行することを推奨する。軽微指摘4件は実装フェーズでの対応で問題ない。
