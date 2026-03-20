# アーキテクチャ設計レビュー記録票 — バックエンドアーキテクチャ

> 対象成果物: `docs/architecture-design/04-backend-architecture.md`
> レビュー日: 2026-03-18
> レビュー担当: バックエンドアーキテクチャ設計スペシャリスト（AI）
> 参照ドキュメント:
> - `docs/architecture-blueprint/01-overall-architecture.md`（全体アーキテクチャ方針）
> - `docs/architecture-blueprint/04-backend-architecture.md`（バックエンドブループリント）
> - `docs/architecture-blueprint/05-database-architecture.md`（データベースアーキテクチャ）
> - `docs/architecture-blueprint/07-auth-architecture.md`（認証・認可アーキテクチャ）
> - `docs/architecture-blueprint/08-common-infrastructure.md`（共通基盤）
> - `docs/architecture-blueprint/10-security-architecture.md`（セキュリティアーキテクチャ）
> - `docs/functional-requirements/00-authentication.md`（認証機能要件）
> - `docs/functional-requirements/01-master-management.md`（マスタ管理機能要件）
> - `docs/functional-requirements/01a-system-parameters.md`（システムパラメータ機能要件）
> - `docs/functional-requirements/02-inbound-management.md`（入荷管理機能要件）
> - `docs/functional-requirements/03-inventory-management.md`（在庫管理機能要件）
> - `docs/functional-requirements/04-outbound-management.md`（出荷管理機能要件）
> - `docs/functional-requirements/04a-allocation.md`（在庫引当機能要件）
> - `docs/functional-requirements/06-batch-processing.md`（バッチ処理機能要件）
> - `docs/functional-design/API-01-auth.md`（認証API設計）
> - `docs/functional-design/API-06-inbound.md`（入荷管理API設計）
> - `docs/functional-design/_standard-api.md`（API標準テンプレート）
> - `docs/data-model/01-overview.md`（データモデル概要）

---

## エグゼクティブサマリー

バックエンドアーキテクチャ設計書は、ブループリントの方針に準拠し、Spring Boot 3.x + Java 21 の技術スタックに基づく実装設計を網羅的に記述している。プロジェクト構造、レイヤードアーキテクチャ、DTO設計、例外ハンドリング、バリデーション、トランザクション管理、排他制御、ページネーション、ロギング、テスト戦略の全10セクションを対象とした。

全体的にブループリントとの整合性は高く、Java 21の機能（Record、Sealed Classes、Pattern Matching、Text Blocks）の活用方針も適切に定義されている。コード例が豊富に含まれており、実装者にとって具体的な指針となる品質を確保している。

以下のレビューでは、ブループリントとの整合性、機能要件との整合性、API設計書との整合性、内部整合性の4観点でチェックを実施した。

---

## レビュー結果

| No | チェック観点 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|------------|----------------|---------|------|---------|
| 1 | ブループリント整合性 | 04-backend-architecture.md（BP） | パッケージ構成の `shared` 配下に `entity/` パッケージ（BaseEntity、AuditorAwareImpl）を追加している。ブループリントには未記載だが、設計として妥当な拡張であり問題なし | 情報 | 対応不要 |
| 2 | ブループリント整合性 | 08-common-infrastructure.md（BP） | ブループリントのエラーレスポンスには `code`/`message`/`timestamp`/`traceId` のフィールドがあるが、設計書では `errorCode`/`message`/`details` としている。API設計書（_standard-api.md）側のフォーマットをSSOTとした設計判断を設計書内に明記済み。妥当 | 情報 | 対応不要 |
| 3 | ブループリント整合性 | 04-backend-architecture.md（BP） | ブループリントの DTO 命名で `{Resource}ListItem` パターンが記載されているが、設計書では `{Resource}Response`（単体と共用）のみ定義。一覧専用DTOが必要な場面では `ListItem` パターンも使用可能である旨を追記が望ましい | 軽微 | 未対応（実装時に判断） |
| 4 | API設計書整合性 | _standard-api.md | API設計書のエラーコードは `PRODUCT_NOT_FOUND` 等のフラット命名だが、ブループリントは `WMS-E-MST-001` 形式。設計書では後者を採用している。API設計書側のエラーコードとの対応表が未定義であるため、実装フェーズで統一方針の確定が必要 | 対応済み | ✅ **対応済み（2026-03-18）**: ErrorResponse DTOを08-common-infrastructureの5フィールド構成（code/message/timestamp/traceId/details）に統一 |
| 5 | 機能要件整合性 | 04a-allocation.md | 在庫引当の悲観ロックについて、ロック取得順序（product_id昇順 -> location_id昇順）を定義しているが、引当アルゴリズム（FEFO/FIFO）との組み合わせでロック順序が崩れる可能性がある。引当実装時にロック順序保証の詳細設計が必要 | 要確認 | 未対応（詳細設計で対応） |

> **対応完了** (2026-03-19): Q10方針決定により、ロック順序を inventory_id 昇順に統一する方針を 04-backend-architecture.md に反映
| 6 | 機能要件整合性 | 06-batch-processing.md | バッチ処理の各ステップを独立トランザクションとする設計は記載されているが、途中失敗時の「完了済みステップをスキップして再開」する仕組みの詳細（ステップ完了フラグの管理方法）は実装時に詳細設計が必要 | 情報 | 未対応（詳細設計で対応） |
| 7 | 内部整合性 | 設計書内 | `SortValidator` で許可フィールドをハードコードしている設計だが、Entity のフィールド追加時にメンテナンス漏れのリスクがある。アノテーションベースの宣言的アプローチも選択肢として検討可能 | 軽微 | 未対応（実装時に判断） |
| 8 | セキュリティ整合性 | 10-security-architecture.md | CORS設定で `allowCredentials: true` を設定する方針が SecurityConfig 骨格に明示されていない。`CorsConfig` で別途設定する前提だが、設定クラスの責務分担を明確にすべき | 軽微 | 未対応（実装時に対応） |
| 9 | テスト戦略 | 設計書内 | Testcontainers を使用する前提で記載されているが、build.gradle の dependencies に `testcontainers` が含まれていない。依存関係への追加が必要 | 軽微 | 未対応（実装時に追加） |

> **対応完了** (2026-03-19): 04-backend-architecture.md の build.gradle に Testcontainers 依存を追加
| 10 | ブループリント整合性 | 08-common-infrastructure.md（BP） | ブループリントで定義されているタイムゾーン「JST（Asia/Tokyo）固定」がapplication.ymlとJackson設定で適切に反映されている。整合性OK | 情報 | 対応不要 |
| 11 | データモデル整合性 | data-model/01-overview.md | Entity の BaseEntity で `OffsetDateTime` を使用しているが、データモデルでは `timestamptz` を使用。PostgreSQL の `timestamptz` と Java の `OffsetDateTime` のマッピングは Hibernate 6.x で標準サポートされており、整合性OK | 情報 | 対応不要 |
| 12 | ブループリント整合性 | 05-database-architecture.md（BP） | PK採番方針（BIGSERIAL）が Entity の `@GeneratedValue(strategy = GenerationType.IDENTITY)` と整合している | 情報 | 対応不要 |
| 13 | Lombok使用 | 設計書内 | build.gradle に Lombok を依存追加し、Entity で `@Getter`/`@RequiredArgsConstructor` を使用している。DTO は Record で代替するため Lombok 不要。Entity のボイラープレート削減のみに限定するのは妥当 | 情報 | 対応不要 |
| 14 | 機能要件整合性 | 01a-system-parameters.md | システムパラメータの「キャッシュせず都度DBから取得」方針が設計書に未記載。ブループリント（08-common-infrastructure.md）には営業日の都度取得が記載されており、同様の方針がシステムパラメータにも適用される。実装時に注意 | 情報 | 未対応（実装時に対応） |

---

## 総合評価

| 観点 | 評価 | コメント |
|------|------|---------|
| **ブループリントとの整合性** | A（良好） | 主要な設計方針（3層アーキテクチャ、DTO規約、エラーハンドリング、ロック方式等）がブループリントと整合している。エラーレスポンス形式の差異はAPI設計書をSSOTとする判断を明記しており妥当 |
| **機能要件の網羅性** | A（良好） | 認証・マスタ管理・入荷・在庫・出荷・引当・バッチの各機能要件に対応する設計パターンが示されている。引当の悲観ロック、バッチの段階的トランザクション等の重要設計も記載済み |
| **実装可能性** | A（良好） | コード例が豊富で、Spring Boot 3.x + Java 21 の具体的な実装パターンを示している。実装者がすぐに着手可能な品質 |
| **保守性** | A（良好） | パッケージ構成、命名規約、テスト戦略が明確に定義されており、長期的な保守性を担保する設計 |

### 要アクション項目

1. ~~**No.4**: エラーコード体系の統一方針を実装フェーズ開始前に確定する（ブループリント形式 vs API設計書形式）~~ ✅ 対応済み（2026-03-18）
2. ~~**No.5**: 在庫引当の悲観ロック順序保証の詳細設計を引当モジュール実装時に策定する~~ ✅ 対応済み（2026-03-19）: Q10方針決定によりロック順序=inventory_id昇順に統一
3. ~~**No.9**: build.gradle に Testcontainers 依存を追加する~~ ✅ 対応済み（2026-03-19）: build.gradleにTestcontainers依存を追加
