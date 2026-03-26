# アーキテクチャルール集

> **目的**: 実装時に絶対に守るべきルールを一行形式で蒸留したもの。
> 詳細は各参照先ドキュメントを確認すること。
> このファイルは CLAUDE.md から参照され、常時コンテキストに読み込まれる。

---

## バックエンド

### API設計

- **[RULE-API-001]** Controllerは必ずOpenAPI Generatorが生成した `{Domain}Api` インターフェースを `implements` する。手書きのエンドポイント定義は禁止。
  → 詳細: [architecture-blueprint/04-backend-architecture.md](architecture-blueprint/04-backend-architecture.md)

- **[RULE-API-002]** 全エンドポイントのパスプレフィックスは `/api/v1/`。

- **[RULE-API-003]** 一覧取得APIは全てページネーション対応（`page`, `size`, `sort` パラメータ）。レスポンス形式は `content / page / size / totalElements / totalPages` の5フィールド固定。プルダウン用途のみ `all=true` で配列直接返却可。

- **[RULE-API-004]** 単一リソースのレスポンスはエンベロープなし（`data` ラッパー不要）。直接リソースオブジェクトを返す。

- **[RULE-API-005]** HTTPステータスコードの使い分け: `201 Created`（新規作成）, `204 No Content`（削除・完了アクション）, `409 Conflict`（重複・楽観的ロック・状態不整合）, `422 Unprocessable Entity`（業務ルール違反）。
  → 詳細: [functional-design/_standard-api.md](functional-design/_standard-api.md)

### DTO規約

- **[RULE-DTO-001]** 手書きDTOは禁止。OpenAPI Generatorが生成したモデルクラスのみ使用する。
  → 詳細: [architecture-blueprint/04-backend-architecture.md](architecture-blueprint/04-backend-architecture.md)

- **[RULE-DTO-002]** Controllerは Entity を直接レスポンスに返してはならない。必ずOpenAPI生成DTOに変換して返す。

- **[RULE-DTO-003]** Entity→DTO変換は各DTOの `static from(Entity)` ファクトリメソッドで実装する（MapStruct不使用）。

- **[RULE-DTO-004]** DTOの配置場所: 各モジュールの `dto/` パッケージ（例: `com.wms.master.dto/`）。

### 例外・エラーハンドリング

- **[RULE-ERR-001]** 例外は全て `GlobalExceptionHandler`（`@ControllerAdvice`）で一元処理。Controller層で個別にtry-catchしない。
  → 詳細: [architecture-blueprint/08-common-infrastructure.md](architecture-blueprint/08-common-infrastructure.md)

- **[RULE-ERR-002]** カスタム例外クラスは `shared.exception` パッケージに配置。全て `WmsException`（abstract）を継承する。
  使用クラス: `ResourceNotFoundException`(404) / `DuplicateResourceException`(409) / `BusinessRuleViolationException`(422) / `OptimisticLockConflictException`(409) / `InvalidStateTransitionException`(409)

- **[RULE-ERR-003]** Repository層の `DataIntegrityViolationException` 等はService層でキャッチし、上記カスタム例外に変換してからスローする。

- **[RULE-ERR-004]** エラーコードは英語の定数名形式 `{RESOURCE}_{ERROR_TYPE}`（例: `WAREHOUSE_NOT_FOUND`, `DUPLICATE_CODE`）。定数クラスは作らず例外スロー箇所にリテラルで記述する。

- **[RULE-ERR-005]** エラーレスポンス形式は `{ "code", "message", "timestamp", "traceId" }`。バリデーションエラー時のみ `"details": [{ "field", "message" }]` を付与。

### トランザクション・Service層

- **[RULE-SVC-001]** `@Transactional` はService層にのみ付与する（Controller・Repository層には付与しない）。

- **[RULE-SVC-002]** 他モジュールのRepositoryを直接呼び出すことは禁止。他モジュールへのアクセスは必ずそのモジュールのService経由。Controller間の直接呼び出しも禁止。

- **[RULE-SVC-003]** バリデーションはController層（Jakarta Bean Validation）と Service層（ビジネスルール）の2層で実施。

### ロギング

- **[RULE-LOG-001]** ログ形式はJSON（構造化ログ）。SLF4J + Logbackを使用し標準出力へ出力。
  → 詳細: [architecture-blueprint/08-common-infrastructure.md](architecture-blueprint/08-common-infrastructure.md)

- **[RULE-LOG-002]** 全ログにリクエスト単位のUUID（traceId）とuserId を含める。

- **[RULE-LOG-003]** メールアドレス・電話番号等のPII（個人情報）はLogbackカスタムフィルターで自動マスクする。

### テスト（バックエンド）

- **[RULE-TEST-001]** 単体テストのカバレッジ目標: C0（命令網羅）100%, C1（分岐網羅）100%。C2（条件網羅）は対象外。JaCoCoで計測（`./gradlew test jacocoTestReport`）。
  → 詳細: [test-specifications/00-test-plan.md](test-specifications/00-test-plan.md)

- **[RULE-TEST-002]** 単体テストの外部依存（DB・外部サービス）は全てMock/Stubで代替。実DBへの接続は結合テストのみ。

- **[RULE-TEST-003]** 結合テストはTestcontainers（PostgreSQL）を使用。`@SpringBootTest` + `@Testcontainers` で実DB接続テストを実施。

---

## データベース

### 削除方式

- **[RULE-DB-001]** マスタデータは論理削除（`is_active` フラグ）。物理削除は行わない。
  → 詳細: [architecture-blueprint/05-database-architecture.md](architecture-blueprint/05-database-architecture.md)

- **[RULE-DB-002]** トランザクションデータは物理削除 + 履歴テーブルへのコピー。日替処理（BAT-01）でトランテーブルから履歴テーブルへ移動後に物理削除。

### ロック方式

- **[RULE-DB-003]** 在庫引当処理は悲観的ロック（`@Lock(LockModeType.PESSIMISTIC_WRITE)`）を使用。

- **[RULE-DB-004]** 更新競合リスクのあるテーブル（マスタ・トランザクションヘッダ等）は楽観的ロック（`@Version` アノテーション付き `version integer` カラム）を使用。`OptimisticLockingFailureException` をService層でキャッチし `OptimisticLockConflictException`（409）に変換する。

### 共通カラム・設計ルール

- **[RULE-DB-005]** 全テーブルに監査カラム（`created_at`, `created_by`, `updated_at`, `updated_by`）を付与。Spring Data JPAの `@EntityListeners(AuditingEntityListener.class)` で自動設定。

- **[RULE-DB-006]** PKは PostgreSQL `BIGSERIAL`（シーケンス）を使用。UUIDと採番テーブルは不使用。

- **[RULE-DB-007]** 業務キー（商品コード・ロケーションコード等）はPKとは別のカラムとして管理。

- **[RULE-DB-008]** DBスキーマ変更はFlywayマイグレーション（`V{n}__{description}.sql`）で管理。直接のDDL実行は禁止。

- **[RULE-DB-009]** 営業日はキャッシュせず `business_date` テーブルから都度取得（日替処理後の即時反映のため）。

---

## フロントエンド

### API型定義

- **[RULE-FE-001]** `src/types/generated/`（OpenAPI自動生成）配下のファイルは手動編集禁止。型の拡張が必要な場合は別ファイルで `extends` する。
  → 詳細: [architecture-blueprint/03-frontend-architecture.md](architecture-blueprint/03-frontend-architecture.md)

- **[RULE-FE-002]** AxiosインスタンスはAPIクライアント（`src/api/client.ts`）を使用。`withCredentials: true` 設定（httpOnly Cookie自動送信のため）。直接 `axios.create()` を各Composableで呼ぶことは禁止。

### Composable設計

- **[RULE-FE-003]** 画面ロジック（API呼び出し・フォームバリデーション・状態管理）は1画面1Composableに閉じ込める。画面間で共通のComposableは作らない。

- **[RULE-FE-004]** Composable命名規則: 一覧画面→ `use{Resource}List`, 登録・編集画面→ `use{Resource}Form`, 特殊画面→ `use{ScreenName}`。

- **[RULE-FE-005]** `.vue` ファイルは表示とイベントバインディングのみ。ビジネスロジックはComposableに委譲する。

### エラーハンドリング

- **[RULE-FE-006]** Axiosインターセプターで処理するステータス: `401`（リフレッシュ試行→失敗時ログイン画面へ）, `403`（ElMessage.error表示）, `500`（ElMessage.error表示）。これらはComposableのtry-catchには届かない。

- **[RULE-FE-007]** `400`, `409`, `422` はインターセプターで処理せず、各Composableのtry-catchで画面固有のエラー処理を実施。

- **[RULE-FE-008]** 楽観的ロック競合（409 + `OPTIMISTIC_LOCK_CONFLICT`）はシンプル通知方式。`ElMessage.error('他のユーザーが更新済みです。画面を再読み込みしてください')` を表示し、差分マージは行わない。

- **[RULE-FE-009]** 更新系APIには取得時の `version` 値をリクエストボディに含めて送信する（楽観的ロック）。

### Piniaストア

- **[RULE-FE-010]** グローバルストアは `authStore`（ユーザー情報・ログイン状態）と `systemStore`（営業日・選択倉庫・言語）のみ。一覧の検索結果・フォーム入力値・ダイアログ開閉状態はComposable内の `ref`/`reactive` で管理。

---

## 認証・セキュリティ

- **[RULE-SEC-001]** 認証はJWT + httpOnly Cookie方式。フロントエンドでトークンをlocalStorageやsessionStorageに保存することは禁止（XSS対策）。
  → 詳細: [architecture-blueprint/07-auth-architecture.md](architecture-blueprint/07-auth-architecture.md)

- **[RULE-SEC-002]** CSRF対策は二重防御: SameSite=Lax（Cookie設定）+ `X-Requested-With` カスタムヘッダー検証（`CsrfCustomHeaderFilter`）。CSRFトークン方式は不使用。

- **[RULE-SEC-003]** APIの認可はSpring Securityの `@PreAuthorize` アノテーションをControllerメソッドに付与。4ロール（`SYSTEM_ADMIN`, `WAREHOUSE_MANAGER`, `WAREHOUSE_STAFF`, `VIEWER`）で制御。
  → ロール別アクセス権限マトリクス: [architecture-blueprint/07-auth-architecture.md](architecture-blueprint/07-auth-architecture.md)

- **[RULE-SEC-004]** パスワードポリシー: 8〜128文字、英大文字・英小文字・数字を各1文字以上必須。BCrypt（strength=12）でハッシュ化。
  → 詳細: [architecture-blueprint/10-security-architecture.md](architecture-blueprint/10-security-architecture.md)

- **[RULE-SEC-005]** 初回ログイン時は `password_change_required = true` の場合にパスワード変更を強制。変更完了まで他操作不可。

- **[RULE-SEC-006]** アクセストークン有効期限: 1時間。リフレッシュトークン: スライディング方式（最終アクセスから24時間で失効）。リフレッシュ時は旧トークンを無効化し新トークンを発行（トークンローテーション）。

---

## ドキュメント・SSOT管理

- **[RULE-DOC-001]** 新しいID（画面ID・API ID・RPT ID・BAT ID・IFX ID）は `docs/functional-design/_id-registry.md` に先に登録してから設計書・コードに反映する。
  → SSOT: [functional-design/_id-registry.md](functional-design/_id-registry.md)

- **[RULE-DOC-002]** テーブル定義・カラム定義は `docs/data-model/` がSSOT。API設計書・画面設計書にカラム一覧を複製しない。

- **[RULE-DOC-003]** APIエンドポイント・リクエスト/レスポンス型定義は `openapi/wms-api.yaml` がSSOT。functional-designのAPI設計書（`API-*.md`）は業務ロジック設計のSSOT（両者は役割が異なる）。

- **[RULE-DOC-004]** 技術方針・共通規約の変更は `docs/architecture-blueprint/` のみ変更する。`docs/architecture-design/` は実装設計の詳細化であり、blueprintの方針に従う。

---

## 共通・その他

- **[RULE-CMN-001]** タイムゾーンはJST（Asia/Tokyo）固定。日時フォーマットはISO 8601（例: `2026-03-13T09:00:00+09:00`）。

- **[RULE-CMN-002]** 文字コードはUTF-8統一。

- **[RULE-CMN-003]** APIレスポンスの `null` フィールドはレスポンスに含めない（`@JsonInclude(NON_NULL)`）。空配列は `[]`（nullにしない）。

- **[RULE-CMN-004]** ブランチ命名: `feature/[Issue#]_短い説明`。mainへの直接コミット不可。1 Issue = 1ブランチ = 1 PR。

- **[RULE-CMN-005]** PRには `./gradlew test jacocoTestReport` でカバレッジを計測し、C0/C1の実測値を記載する（featureおよびbugfixラベルのPRのみ）。
