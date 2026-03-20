# アーキテクチャ設計書 整合性検証レポート

> 検証日: 2026-03-18
> 検証担当: シニアテクニカルアーキテクト（AI）

## 検証サマリー

| # | 検証観点 | 結果 | 重大度 |
|---|---------|------|--------|
| 1 | HikariCP接続数 vs PostgreSQL max_connections | ✅ 解決済み（2026-03-18） | Critical |
| 2 | ErrorResponse DTO の定義整合性 | ✅ 解決済み（2026-03-18） | Medium |
| 3 | 認証フローの整合性 | 整合 | - |
| 4 | パスワードリセットトークンのハッシュ方式 | ✅ 解決済み（2026-03-18） | Medium |
| 5 | スケーリング設定の整合性 | ✅ 解決済み（2026-03-18） | High |
| 6 | ログ・トレーシング設計の整合性 | ✅ 解決済み（2026-03-19） | Low |
| 7 | Terraform モジュール構成の整合性 | ✅ 解決済み（2026-03-19） | Low |
| 8 | BaseEntity / 監査カラムの整合性 | ✅ 解決済み（2026-03-18） | Medium |

---

## 不整合の詳細

### ~~不整合 #1: HikariCP接続数 — prd環境の理論最大接続数がDB上限を大幅超過~~ **✅ 解決済み（2026-03-18）**

- **解決内容**: HikariCP poolSizeをdev=5/prd=10に全設計書（02-system-architecture.md、05-database-architecture.md、13-non-functional-requirements.md）で統一。prd環境: 5レプリカ x 10 = 50接続でDB上限内に収まる構成に修正済み。
- **関連ファイル**:
  - `docs/architecture-design/02-system-architecture.md` セクション4.5, 4.6
  - `docs/architecture-design/05-database-architecture.md` セクション5.1, 5.3
  - `docs/architecture-design/06-infrastructure-architecture.md` セクション2.2
  - `docs/architecture-design/13-non-functional-requirements.md` セクション1.3.1, 3.1

---

### ~~不整合 #2: ErrorResponse DTO のフィールド定義が設計書間で異なる~~ **✅ 解決済み（2026-03-18）**

- **解決内容**: ErrorResponse DTOを08-common-infrastructureの5フィールド構成（code/message/timestamp/traceId/details）に全設計書で統一。04-backend-architecture.mdの3フィールド方針を撤回し、5フィールド構成を採用。
- **関連ファイル**:
  - `docs/architecture-design/04-backend-architecture.md` セクション4.2
  - `docs/architecture-design/08-common-infrastructure.md` セクション1.1
  - `docs/architecture-blueprint/08-common-infrastructure.md`

---

### 不整合 #3: 認証フローの整合性 — 整合

- **関連ファイル**:
  - `docs/architecture-design/03-frontend-architecture.md`（Axios interceptor, refresh queue）
  - `docs/architecture-design/07-auth-architecture.md`（JWT, Cookie, refresh token）
  - `docs/architecture-design/10-security-architecture.md`（CORS, CSRF, セキュリティヘッダー）
- **検証結果**: 整合している
  - フロントエンド: Axiosレスポンスインターセプターで401検知 → リフレッシュキュー制御 → POST /api/v1/auth/refresh → 成功時にリトライ / 失敗時にログイン画面遷移
  - バックエンド: SecurityFilterChainでCSRF無効化（SameSite=Lax）、JwtAuthenticationFilterでCookieからJWT抽出・検証、認証不要エンドポイントの定義が一致
  - セキュリティ: SameSite=Lax、httpOnly Cookie、X-Frame-Options: DENY、HSTS設定が3文書間で一貫

---

### ~~不整合 #4: パスワードリセットトークンのハッシュ方式~~ **✅ 解決済み（2026-03-18）**

- **解決内容**: パスワードリセットトークンのハッシュ方式をSHA-256、有効期限を30分に全設計書で統一。API-01-auth.mdの「BCrypt」「1時間」の記述も修正済み。
- **統一方針**: パスワードリセットトークンはSHA-256（使い捨て・短寿命のため）、リフレッシュトークンはBCrypt（長寿命のため）で使い分ける設計判断を明記。
- **関連ファイル**:
  - `docs/architecture-design/07-auth-architecture.md`（認証アーキテクチャ）
  - `docs/architecture-design/10-security-architecture.md` セクション5
  - `docs/data-model/02-master-tables.md`（password_reset_tokensテーブル定義）
  - `docs/functional-design/API-01-auth.md`（認証API設計）

---

### 不整合 #5: ~~スケーリング設定 — Container Apps の CPU/Memory が設計書間で矛盾~~ **解決済み**

- **解決日**: 2026-03-18
- **解決内容**: 02-system-architecture.md のprd CPU/Memoryを 0.5 vCPU / 1.0 Gi に統一（06-infrastructure-architecture.md、13-non-functional-requirements.md と整合）。環境比較表およびコスト見積もりも修正済み。
- **統一方針**: dev/prd共に 0.5 vCPU / 1.0 Gi。スケールアップが必要な場合は 1.0 vCPU / 2.0 Gi へ変更する（13-non-functional-requirements.md セクション3.2の垂直スケーリング方針に従う）

---

### 不整合 #6: ログ・トレーシング設計 — TraceIdフィルターのクラス名・traceID長の不一致 — ✅ 対応完了（2026-03-19）

- **関連ファイル**:
  - `docs/architecture-design/04-backend-architecture.md` セクション9.2
  - `docs/architecture-design/08-common-infrastructure.md` セクション4.1
  - `docs/architecture-design/11-monitoring-operations.md` セクション1.4
- **04-backend-architecture.md の記述**: クラス名 `MdcFilter`、traceId は `UUID.randomUUID().toString().replace("-", "").substring(0, 16)`（16文字のHex）
- **08-common-infrastructure.md の記述**: クラス名 `TraceIdFilter`、traceId は `UUID.randomUUID().toString().replace("-", "").substring(0, 16)`（16文字のHex）。レスポンスヘッダーに `X-Trace-Id` を付与する設計あり
- **11-monitoring-operations.md の記述**: 「UUID v4を生成」と記載。HTTPヘッダー `X-Trace-Id` でフロントエンド→バックエンド間を伝播と記載
- **問題**:
  1. **フィルタークラス名の不一致**: 04-backend-architecture.md は `MdcFilter`、08-common-infrastructure.md は `TraceIdFilter`
  2. **traceIdの長さの記述差異**: 04/08 は16文字Hexだが、11は「UUID v4」と記載（UUID v4のフル形式は36文字ハイフン込み or 32文字Hex）
  3. 08-common-infrastructure.md にはレスポンスヘッダー `X-Trace-Id` の出力があるが、04-backend-architecture.md には記載がない
- **推奨解決策**:
  - クラス名を `TraceIdFilter`（08-common-infrastructure.md 側）に統一し、04-backend-architecture.md を修正する
  - 11-monitoring-operations.md の「UUID v4」を「UUID v4ベースの16文字HexID」に修正する
  - レスポンスヘッダー `X-Trace-Id` の出力有無を統一する

> **対応完了** (2026-03-19):
> - クラス名を `TraceIdFilter` に統一（04-backend-architecture.md の `MdcFilter` を修正）
> - traceID 仕様を 32文字Hex に統一（04-backend-architecture.md、08-common-infrastructure.md、11-monitoring-operations.md）
> - レスポンスヘッダー `X-Trace-Id` の出力を全設計書で統一

---

### 不整合 #7: Terraform モジュール構成 — identity モジュールの有無 — ✅ 対応完了（2026-03-19）

- **関連ファイル**:
  - `docs/architecture-design/02-system-architecture.md` セクション10.1
  - `docs/architecture-design/06-infrastructure-architecture.md` セクション7.1
  - `docs/architecture-design/12-development-deploy.md` セクション3.7
- **02-system-architecture.md の記述**: `modules/` 配下に `identity/`（Managed Identity + ロール割当）モジュールが定義されている
- **06-infrastructure-architecture.md の記述**: `modules/` 配下に `identity/` モジュールが存在しない。`monitoring/` 配下に `alerts.tf` が追加されている
- **12-development-deploy.md の記述**: Terraform ワークフローは `infra/environments/{env}` ディレクトリで実行。モジュール構成への言及なし
- **問題**:
  1. **`identity` モジュール**: 02-system-architecture.md にのみ存在し、06-infrastructure-architecture.md には含まれていない
  2. **`monitoring` モジュール内部構成**: 06-infrastructure-architecture.md は `alerts.tf` を含むが、02-system-architecture.md には記載がない
  3. **`front-door/` の命名**: 02-system-architecture.md は `front-door/`、06-infrastructure-architecture.md も `front-door/`（一致）
- **推奨解決策**:
  - 06-infrastructure-architecture.md のモジュール一覧に `identity/` を追加する（または02-system-architecture.md から削除してManaged Identity設定を `container-apps` モジュールに統合する）
  - モジュール構成のSSOTを06-infrastructure-architecture.md に定め、02-system-architecture.md は参照リンクとする

> **対応完了** (2026-03-19): Q4方針決定により、identity モジュールを container-apps モジュールに統合。02-system-architecture.md から独立 identity モジュールを削除し、Managed Identity 設定を container-apps モジュール内に移動。06-infrastructure-architecture.md をモジュール構成の SSOT とする方針を確定。

---

### ~~不整合 #8: BaseEntity / 監査カラムの整合性 — version カラムの位置づけ~~ **✅ 解決済み（2026-03-18）**

- **解決内容**: data-model/01-overview.mdのマスタテーブル共通カラムパターンに `version integer NOT NULL DEFAULT 0` を追加。data-model/02-master-tables.mdの8テーブル（products, partners, warehouses等）にもversionカラムを追加済み。
- **関連ファイル**:
  - `docs/architecture-design/01-overall-architecture.md` セクション3.2
  - `docs/architecture-design/04-backend-architecture.md`（JPA Auditing）
  - `docs/architecture-design/08-common-infrastructure.md`（監査証跡設計）
  - `docs/data-model/01-overview.md`（共通カラム定義）
  - `docs/data-model/02-master-tables.md`（マスタテーブル定義）

---

## 整合が確認された項目

### 認証フロー（検証観点 #3）

フロントエンドのAxiosインターセプター（リフレッシュキュー制御）、バックエンドのJWT検証フロー、セキュリティヘッダー設定（SameSite=Lax、httpOnly、X-Frame-Options: DENY、HSTS）は3文書間で一貫しており、矛盾は検出されなかった。

### その他の整合確認項目

| 項目 | 検証結果 |
|------|---------|
| PostgreSQL バージョン | 全文書で 16 で統一 |
| PostgreSQL SKU | 全文書で B1ms (1 vCore, 2 GiB) で統一 |
| Spring Boot バージョン | 全文書で 3.x + Java 21 で統一 |
| Container Apps min/max replicas | 02-system と 06-infrastructure で一致（dev: 0/3, prd-east: 1/5, prd-west: 0/5） |
| 例外クラス階層 | 01-overall, 04-backend, 08-common-infrastructure（ブループリント・設計書とも）で一致 |
| Terraform State 管理 | 02-system, 06-infrastructure, 12-development-deploy で一致（stwmsterraform / tfstate / {env}/terraform.tfstate） |
| スロークエリログ閾値 | 05-database は 500ms、13-non-functional は 1000ms で差異あり（ただし異なる文脈：DBパラメータ vs 監視閾値として許容範囲） |
| Front Door ヘルスプローブ | 02-system と 06-infrastructure と 11-monitoring で一致（/actuator/health, 30秒間隔, HTTPS） |
| ヘルスプローブ設定 | ~~02-system と 06-infrastructure で Startup Probe の設定に差異あり（02: 5秒間隔/30回, 06: 10秒間隔/10回）。ただし 11-monitoring-operations.md は 10秒間隔/30回と別の値~~ ✅ 一部対応済み（2026-03-19）: 02-system-architecture.md と 11-monitoring-operations.md を interval:10秒/failure:30回/timeout:5秒に統一。06-infrastructure-architecture.md は別途対応中 |

---

## 推奨優先度

| 優先度 | 不整合 # | 対応内容 |
|--------|---------|---------|
| ~~**P1（即時対応）**~~ | #1 | ~~HikariCP接続数を設計書間で統一し、prd環境でDB上限を超えない構成に修正~~ ✅ 解決済み（2026-03-18） |
| ~~**P1（即時対応）**~~ | #5 | ~~Container Apps CPU/Memory をprd環境で統一~~ ✅ 解決済み（2026-03-18） |
| ~~**P2（早期対応）**~~ | #8 | ~~data-model に `version` カラムを追加（実装開始前に必須）~~ ✅ 解決済み（2026-03-18） |
| ~~**P2（早期対応）**~~ | #2 | ~~ErrorResponse DTO のフィールド定義を統一~~ ✅ 解決済み（2026-03-18） |
| ~~**P2（早期対応）**~~ | #4 | ~~07-auth-architecture.md にパスワードリセットトークン設計を追記~~ ✅ 解決済み（2026-03-18） |
| ~~**P3（次回更新時）**~~ | #6 | ~~TraceIdフィルターのクラス名・traceID仕様を統一~~ ✅ 対応済み（2026-03-19） |
| ~~**P3（次回更新時）**~~ | #7 | ~~Terraform identity モジュールの扱いを統一~~ ✅ 対応済み（2026-03-19）: Q4により container-apps に統合 |

---

## 補足: ヘルスプローブ設定の軽微な不一致 — ✅ 一部対応済み（2026-03-19）

Startup Probe の `interval_seconds` と `failure_count_threshold` が3文書で微妙に異なる。

| パラメータ | 02-system-architecture | 06-infrastructure-architecture | 11-monitoring-operations |
|-----------|----------------------|------------------------------|------------------------|
| 間隔 | ~~5秒~~ → 10秒 ✅ | 10秒 | ~~10秒~~ → 10秒 ✅ |
| 失敗閾値 | ~~30回~~ → 30回 ✅ | 10回 | ~~30回~~ → 30回 ✅ |
| 最大待機時間 | ~~150秒~~ → 300秒 ✅ | 100秒 | ~~300秒~~ → 300秒 ✅ |
| timeout | → 5秒 ✅ | — | → 5秒 ✅ |

> **対応完了** (2026-03-19): Startup Probe パラメータを interval:10秒、failure:30回、timeout:5秒 に 02-system-architecture.md と 11-monitoring-operations.md で統一。06-infrastructure-architecture.md は別途対応中。

Readiness Probe の間隔も微妙に異なる（02: 10秒, 06: 10秒, 11: 15秒）。

**推奨**: 06-infrastructure-architecture.md の Terraform HCL をSSOTとし、他文書を合わせる。
