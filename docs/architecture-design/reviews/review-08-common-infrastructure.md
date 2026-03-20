# アーキテクチャ設計レビュー記録票 — 共通基盤

> 対象成果物: `docs/architecture-design/08-common-infrastructure.md`
> レビュー日: 2026-03-18
> レビュー担当: 共通基盤・横断関心事設計スペシャリスト（AI）
> 参照ドキュメント:
> - `docs/architecture-blueprint/08-common-infrastructure.md`（共通基盤ブループリント）
> - `docs/architecture-blueprint/04-backend-architecture.md`（バックエンドアーキテクチャ）
> - `docs/architecture-blueprint/03-frontend-architecture.md`（フロントエンドアーキテクチャ）
> - `docs/architecture-blueprint/07-auth-architecture.md`（認証・認可アーキテクチャ）
> - `docs/architecture-blueprint/10-security-architecture.md`（セキュリティアーキテクチャ）
> - `docs/functional-requirements/00-authentication.md`（認証・ログイン機能要件）
> - `docs/functional-requirements/01-master-management.md`（マスタ管理機能要件）
> - `docs/functional-requirements/01a-system-parameters.md`（システムパラメータ管理）
> - `docs/functional-requirements/02-inbound-management.md`（入荷管理機能要件）
> - `docs/functional-requirements/03-inventory-management.md`（在庫管理機能要件）
> - `docs/functional-requirements/04-outbound-management.md`（出荷管理機能要件）
> - `docs/functional-requirements/04a-allocation.md`（在庫引当機能要件）
> - `docs/functional-requirements/05-reports.md`（レポート機能要件）
> - `docs/functional-requirements/06-batch-processing.md`（バッチ処理機能要件）
> - `docs/functional-requirements/07-interface.md`（外部連携I/F機能要件）
> - `docs/functional-design/_standard-api.md`（API設計テンプレート）
> - `docs/functional-design/_standard-screen.md`（画面設計テンプレート）
> - `docs/data-model/01-overview.md`（データモデル概要）
> - `docs/data-model/02-master-tables.md`（マスタ系テーブル定義）

---

## エグゼクティブサマリー

共通基盤設計書は、ブループリント（08-common-infrastructure.md）で定義された方針を忠実に実装レベルに展開し、バックエンド（Java/Spring Boot）とフロントエンド（Vue 3/TypeScript）の双方にわたる9つの横断関心事を網羅した。SSOTルールを遵守し、ブループリントや他の定義場所への参照リンクを適切に記載している。コード例も実装時にそのまま参照できる粒度で記述されている。

全体として設計品質は良好であり、致命的な矛盾はない。以下に改善検討事項を記録する。

---

## レビュー結果

| No | チェック観点 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|------------|----------------|---------|------|---------|
| 1 | SSOT遵守 — エラーレスポンス形式 | blueprint/08 | 設計書のErrorResponseでは`code`フィールドを使用しているが、_standard-api.mdでは`errorCode`フィールドを使用。命名を統一する必要がある | 対応済み | ✅ **対応済み（2026-03-18）**: 08-common-infrastructureの5フィールド構成（code/message/timestamp/traceId/details）に全設計書で統一 |
| 2 | SSOT遵守 — パスワードポリシー値 | blueprint/10-security | パスワードポリシーの具体値（8〜128文字等）をカスタムバリデーション内にハードコードしている。SSOTルールでは「ポリシー値の複製禁止」とされている | 軽微 | 実装時にはsystem_parametersまたはapplication.ymlから読み込む方式に変更する。設計書レベルではコード例の明示性を優先した |
| 3 | ブループリントとの整合性 — モジュール略称 | blueprint/08 | ブループリントではモジュール略称「CMN」=shared（共通）と定義。設計書のEnumやパッケージ構成はこの略称を正しく反映している | 問題なし | — |
| 4 | ブループリントとの整合性 — ログフォーマット | blueprint/08 | ブループリントのログ項目（timestamp, level, logger, traceId, userId, message, module）を全てLogback設定・MDC設定でカバーしている | 問題なし | — |
| 5 | ブループリントとの整合性 — 例外クラス階層 | blueprint/08 | 5つのカスタム例外クラスとGlobalExceptionHandlerのマッピングがブループリントの定義と完全一致 | 問題なし | — |
| 6 | 機能要件との整合性 — ステータスEnum | functional-requirements/02, 04, 04a | InboundStatusにCANCELLEDを追加。OutboundStatusにCANCELLEDを追加。機能要件のキャンセル機能と整合している | 問題なし | — |
| 7 | 機能要件との整合性 — 棚卸ステータス | functional-requirements/03 | 棚卸ステータスのEnumが設計書に未定義。StocktakeStatus（OPEN/COUNTING/CONFIRMED等）を追加すべき | 軽微 | 棚卸管理の詳細設計時に追加する |

> **対応完了** (2026-03-19): 08-common-infrastructure.md に StocktakeStatus Enum（OPEN/COUNTING/CONFIRMED 等）を追加
| 8 | 機能要件との整合性 — ばらし指示ステータス | functional-requirements/04a | ばらし指示のステータス（INSTRUCTED/COMPLETED）のEnumが未定義 | 軽微 | 在庫引当モジュールの詳細設計時に追加する |

> **対応完了** (2026-03-19): 08-common-infrastructure.md に UnpackInstructionStatus Enum（INSTRUCTED/COMPLETED）を追加
| 9 | データモデルとの整合性 — BaseEntity | data-model/01 | BaseEntityの共通カラム（created_at, created_by, updated_at, updated_by）がデータモデル定義と一致 | 問題なし | — |
| 10 | データモデルとの整合性 — 楽観的ロック | data-model/02 | BaseMasterEntityにversionカラムを追加しているが、data-model/02-master-tables.mdにはversionカラムの定義がない | 対応済み | ✅ **対応済み（2026-03-18）**: data-model/01-overviewの共通パターンと02-master-tablesの8テーブルにversionカラムを追加 |
| 11 | 画面設計との整合性 — Element Plusコンポーネント | _standard-screen.md | バリデーションメッセージの表示方式（el-form-itemの:errorバインド）が画面設計テンプレートの記法と整合 | 問題なし | — |
| 12 | API設計との整合性 — ページネーション | _standard-api.md, blueprint/04 | PageResponseのフィールド（content, page, size, totalElements, totalPages）がAPI設計テンプレートと完全一致 | 問題なし | — |
| 13 | 網羅性 — システムパラメータ取得 | functional-requirements/01a | 設計書にはシステムパラメータの取得・キャッシュ方針が明記されていない。ブループリントでは「キャッシュなし」と定義されている | 軽微 | 営業日と同様にキャッシュなしで都度DB取得する方針を明記するか、または各モジュールの詳細設計で定義する |
| 14 | 網羅性 — バッチ処理時の監査カラム | functional-requirements/06 | SecurityAuditorAwareでは認証なし時にSYSTEM_USER_ID=0を使用する設計。バッチ実行時は実行者のユーザーIDが利用可能なため、バッチのService呼び出し前にSecurityContextに実行者を設定する必要がある | 要確認 | バッチ処理設計書で、バッチ実行時のSecurityContext設定方針を定義する |

> **対応完了** (2026-03-19): Q5方針決定により、バッチ実行時の SecurityContext に実行者IDを設定する方針を 08-common-infrastructure.md セクション8.6に追加
| 15 | コード品質 — record型の活用 | — | DTO（Request/Response/SearchCriteria）にJava recordを採用しており、イミュータブル設計として適切 | 問題なし | — |
| 16 | フロントエンド — i18nメッセージ網羅性 | _standard-screen.md | ja.jsonのメッセージ定義はサンプルレベル。全画面のメッセージを実装時に順次追加する前提 | 情報 | 各画面実装時にメッセージキーを追加していく運用とする |
| 17 | セキュリティ — トレースIDのレスポンスヘッダー出力 | blueprint/10-security | X-Trace-Idヘッダーをレスポンスに付与する設計。本番環境での情報漏洩リスクを検討すべき | 軽微 | トレースIDは内部識別子であり機密情報ではない。エラー発生時のサポート問い合わせで有用なため、本番でも出力する方針とする |

---

## 総合評価

| 評価項目 | 評価 |
|---------|------|
| ブループリントとの整合性 | 良好。方針の複製を避け参照リンクで示している |
| 機能要件との整合性 | 良好。一部Enum追加が必要（棚卸・ばらし指示ステータス） |
| データモデルとの整合性 | 概ね良好。versionカラムのデータモデル追加が必要 |
| SSOTルール遵守 | 良好。情報の定義場所への参照を適切に記載 |
| コード例の品質 | 良好。実装時にそのまま参照可能な粒度 |
| 網羅性 | 9つの横断関心事を漏れなくカバー |

### 要対応事項サマリー

1. ~~**ErrorResponseのフィールド名統一**（`code` vs `errorCode`）~~ ✅ 対応済み（2026-03-18）: 5フィールド構成に統一
2. ~~**データモデルへのversionカラム追加**~~ ✅ 対応済み（2026-03-18）: 01-overviewと02-master-tablesに追加
3. ~~**バッチ処理時のSecurityContext設定方針** — バッチ処理設計書で定義~~ ✅ 対応済み（2026-03-19）: Q5方針決定によりセクション8.6に追加
