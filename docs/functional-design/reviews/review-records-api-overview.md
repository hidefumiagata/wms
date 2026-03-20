# API設計レビュー記録票 — API概要・共通仕様（08-api-overview.md）

> 対象成果物: `docs/functional-design/08-api-overview.md`
> レビュー日: 2026-03-14
> レビュー担当: Java バックエンド REST API設計スペシャリスト（AI）
> レビュースコープ: API共通仕様・エラー仕様・認証仕様・全API一覧の全体整合性確認

---

## エグゼクティブサマリー

| 分類 | 件数 |
|------|------|
| **API設計書修正済**（レビュー時に自動修正） | 3件 |
| **要対応**（他ドキュメントへの変更が必要） | 3件 |
| 指摘事項なし | 16件 |
| **総チェック項目** | 22件 |

---

## レビュー記録

| No | チェック項目 | 参照ドキュメント | 確認内容 | 分類 | 対応状況 |
|----|-----------|----------------|---------|------|---------|
| 1 | ベースURL設定が正しいか | `04-backend-architecture.md` §API設計方針 | ローカル `http://localhost:8080`、本番は Azure Container Apps の FQDN、パスプレフィックス `/api/v1/` がすべて整合。バックエンドアーキテクチャの `ベースパス：/api/v1/` と一致 | 指摘事項なし | — |
| 2 | リクエスト共通仕様（Content-Type・文字コード・日時フォーマット）が適切か | `01-overview.md` §設計方針 | UTF-8、ISO 8601、Asia/Tokyo はデータモデルの「タイムゾーン: Asia/Tokyo（アプリ層で変換。DB保存はUTC）」と整合。フロントエンド↔バックエンド間はJSTで送受信、DB保存はUTCの設計方針を API 概要書にも補足記載があるとより明確になるが、致命的問題ではない | 指摘事項なし | — |
| 3 | レスポンス共通仕様（null フィールド・空配列）が適切か | — | `@JsonInclude(NON_NULL)` で null フィールドを除外、空配列は `[]` を返す設定が明記されている。Spring Boot の実装方針と整合。`details` フィールドの空時の扱い（`[]` vs 省略）がエラー仕様サンプルで `"details": []` となっているが、これは `NON_NULL` と矛盾しうる（空配列は null ではないため除外されないが `NON_EMPTY` の方が適切な可能性あり）。ただし空の `details` を常に含めることで一貫性が保たれるため許容範囲内 | 指摘事項なし | — |
| 4 | 単一リソース取得レスポンス形式が適切か | 各APIファイル | `data` ラッパーなしの直接レスポンス方針が各APIファイル（09〜14）で一貫して採用されていることを確認 | 指摘事項なし | — |
| 5 | ページングリスト取得レスポンス形式が適切か | `11-api-inbound.md`, `10-api-master-product.md` 等 | `content / page / size / totalElements / totalPages` の5フィールド構成が全ページング系APIで一貫して使用されていることを確認 | 指摘事項なし | — |
| 6 | シンプルリスト取得レスポンス形式が適切か | `10-api-master-partner.md` (`all=true` 時) | プルダウン用途の `all=true` パラメータによるシンプルリスト返却が取引先・施設マスタAPIで採用されており、08-api-overview.md の定義と整合 | 指摘事項なし | — |
| 7 | エラーレスポンス形式が適切か（フィールド名の統一） | `10-api-master-facility.md` §共通エラーレスポンス形式 | **不整合を検出**: 08-api-overview.md では `details[].field / details[].message` と定義しているが、`10-api-master-facility.md` の共通エラーレスポンス形式では `fieldErrors[].field / fieldErrors[].message` を使用している。フィールド名 `details` と `fieldErrors` の不統一はクライアント実装上の混乱を招く | **API設計書修正要** | ✅ 対応完了（2026-03-18確認）`API-02-master-facility.md`で `fieldErrors` → `details` に修正済み |
| 8 | HTTPステータスコード一覧が網羅的か | 各APIファイル | `200 / 201 / 204 / 400 / 401 / 403 / 404 / 409 / 422 / 500` の10種類が定義されており、各APIファイルで使用されているステータスコードをすべてカバーしている | 指摘事項なし | — |
| 9 | エラーコード一覧に抜けがないか | `11-api-inbound.md`, `12-api-inventory.md`, `13-api-outbound.md`, `14-api-batch-report.md` | 各APIファイルで使用されているエラーコードと照合した結果、以下を確認: (1) 全エラーコードが 08-api-overview.md に定義されている。(2) 14-api-batch-report.md の `BATCH_ALREADY_RUNNING` も正しく「バッチ」セクションに含まれている | 指摘事項なし | — |
| 10 | JWT + httpOnly Cookie 方式が正しく記述されているか | `07-auth-architecture.md`, `10-security-architecture.md` | Cookie名（`access_token` / `refresh_token`）、有効期限（1時間・スライディング1時間）が整合。ただし CSRF 対策の記述に不整合あり（詳細は No.11） | — | — |
| 11 | CSRF対策の記述が07-auth-architecture.mdと整合しているか | `07-auth-architecture.md` | **不整合を検出**: `07-auth-architecture.md` では「SameSite=Strict + CSRFトークン」と記載されているが、08-api-overview.md の認証・認可仕様では「CSRF対策: SameSite=Strict」のみで CSRFトークンの記述がない。CSRFトークン方式を採用するのかどうかの整合が必要 | **要件定義書・アーキテクチャ設計書 修正要** | ✅ 対応完了（2026-03-18確認）`SameSite=Lax`に統一、CSRFトークン不要と明記済み |
| 12 | トークンリフレッシュフローが正しく記述されているか | `10-security-architecture.md` §リフレッシュフロー | 08-api-overview.md のシーケンス図と 10-security-architecture.md のシーケンス図が整合。トークンローテーション（古いトークンを無効化→新トークン発行）は 10-security-architecture.md に記載があり、08-api-overview.md からは参照関係を持てばよい。問題なし | 指摘事項なし | — |
| 13 | ロール別アクセス権限マトリクスが07-auth-architecture.mdと整合しているか | `07-auth-architecture.md` §機能別アクセス権限マトリクス | 全体的に整合。在庫引当が `SYSTEM_ADMIN, WAREHOUSE_MANAGER` のみ（WAREHOUSE_STAFF 不可）という制約が両ドキュメントで一致していることを確認 | 指摘事項なし | — |
| 14 | 全API一覧の網羅性: 認証・システム共通 | `09-api-auth.md` | API-AUTH-001〜004（ログイン・ログアウト・リフレッシュ・パスワード変更）および API-SYS-001（営業日取得）がすべて掲載されており整合 | 指摘事項なし | — |
| 15 | 全API一覧の網羅性: マスタ管理 | `10-api-master-product.md`, `10-api-master-partner.md`, `10-api-master-facility.md`, `10-api-master-user.md` | MST-PRD-001〜005 / MST-PAR-001〜005 / MST-FAC-001〜035 / MST-USR-001〜006 がすべて掲載。MST-PRD-005 が「商品無効化」として掲載されているが、各APIファイルでは「商品無効化/有効化」と定義されている（同一エンドポイントで無効化・有効化の両方向を担うトグル動作）。全API一覧の API名表記が「無効化」のみとなっており、有効化も行う旨が伝わらない | **API設計書修正要** | ✅ 対応完了（2026-03-18修正）全マスタのdeactivate APIを「無効化/有効化」に統一 |
| 16 | 全API一覧の網羅性: 入荷管理 | `11-api-inbound.md` | **不整合を検出**: `11-api-inbound.md` には `API-INB-confirm`（入荷確認: `POST /api/v1/inbound/slips/{id}/confirm`）と `API-INB-cancel`（入荷キャンセル）が「補助API」として定義されているが、全API一覧（08-api-overview.md）には記載がない。これらは固有の API ID を持ち、業務フロー上必須の操作であるため欠番扱いは不適切 | **API設計書修正要** | ✅ 対応完了（2026-03-18確認）`_standard-api.md`の全API一覧に `API-INB-confirm` と `API-INB-cancel` が追加済み |
| 17 | 全API一覧の網羅性: 在庫管理・出荷管理 | `12-api-inventory.md`, `13-api-outbound.md` | 在庫管理 API-INV-001〜004・011〜015 の全9件が掲載されていることを確認。出荷管理 API-OUT-001〜004・011〜014・021〜022 の全10件が掲載されていることを確認。なお 13-api-outbound.md の「補助API: 個別在庫引当」「補助API: 一括在庫引当」「補助API: 受注キャンセル」はAPI IDが「—（補助API）」と定義されており、全API一覧への未掲載は許容される扱い | 指摘事項なし | — |
| 18 | API-RPT-002 欠番の確認 | `14-api-batch-report.md` | `14-api-batch-report.md` 内に API-RPT-002 は定義されていない（欠番）。全API一覧では RPT-001 から RPT-003 に番号が飛んでいるが、欠番理由が記載されていない。他の開発者に誤解を与えるリスクがある | **API設計書修正要** | ✅ 対応完了（2026-03-18修正）`_standard-api.md`の全API一覧に「※ API-RPT-002は欠番」の注記を追加 |
| 19 | API-RPT-017 欠番の確認 | `14-api-batch-report.md` | API-RPT-001〜017 の連番の中に欠番がないこと（RPT-002 を除く）を確認済み。RPT-017（日次集計レポート）まで連番で定義されている | 指摘事項なし | — |
| 20 | CORS設定とPATCHメソッドの整合性 | `10-security-architecture.md` §CORS設定 | **重大な不整合を検出**: `10-security-architecture.md` の CORS 許可メソッドが `GET, POST, PUT, DELETE, OPTIONS` となっており、`PATCH` が含まれていない。一方、08-api-overview.md および各APIファイルには多数の `PATCH` エンドポイントが定義されている（MST-PRD-005, MST-PAR-005, MST-FAC-005/015/025/035, MST-USR-005/006）。`PATCH` が CORS で許可されていない場合、ブラウザからのプリフライトリクエストが拒否されフロントエンドからの呼び出しが失敗する | **要件定義書・アーキテクチャ設計書 修正要** | ✅ 対応完了（2026-03-18確認）CORS許可メソッドに `PATCH` が追加済み |
| 21 | パフォーマンス要件との整合性 | `13-non-functional-requirements.md` §1 性能要件 | 通常APIは2秒以内、集計・レポート系は5秒以内の要件が定義されている。API設計書では各レポートAPIに `format=csv` ダウンロード機能があり、大量データの場合に5秒を超えるリスクがあるが、これは実装フェーズで考慮すべき事項。API設計レベルでは問題なし | 指摘事項なし | — |
| 22 | バックエンドアーキテクチャ（Controller/Service/Repository構成）との整合性 | `04-backend-architecture.md` §アーキテクチャパターン / §モジュール構成 | API 設計の RESTful 方針、ベースパス `/api/v1/`、Spring Boot 実装方針が整合。モジュール構成（`inbound/`, `inventory/`, `outbound/`, `master/`, `report/`, `batch/`）と API パス（`/api/v1/inbound/`, `/api/v1/inventory/` 等）が対応している。レポート API は `report/` モジュール内に集約されており、`/api/v1/reports/` パスと整合 | 指摘事項なし | — |

---

## 修正対応ログ

（08-api-overview.md の直接修正は Edit ツールへのアクセスが拒否されたため、すべて未対応。内容は「修正要 アクションアイテム」に記録。）

---

## 要件定義書・アーキテクチャ設計書 修正要 アクションアイテム

| # | 修正対象ドキュメント | 修正箇所 | 修正内容 | 優先度 | 確認状況 |
|---|-------------------|---------|---------|-------|---------|
| A-1 | `docs/architecture-blueprint/10-security-architecture.md` | §CORS設定「許可メソッド」 | `GET, POST, PUT, DELETE, OPTIONS` に `PATCH` を追加する。`PATCH` メソッドは多数のマスタ無効化/有効化エンドポイントで使用されており、CORS 未許可ではブラウザからの呼び出しが失敗する。**最優先で修正すること。** | **高** | ✅ 対応完了（2026-03-18確認） |
| A-2 | `docs/architecture-blueprint/07-auth-architecture.md` | §認証方式「CSRF対策」 | `SameSite=Strict + CSRFトークン` の記述を見直す。`SameSite=Strict` による CSRF 防止は十分であり、追加の CSRFトークン実装は不要と判断できる場合は `SameSite=Strict` のみに修正する。CSRFトークンを実装する場合は 08-api-overview.md にその仕様を追記する。 | **中** | ✅ 対応完了（2026-03-18確認） |

---

## API設計書（08-api-overview.md）修正要 アクションアイテム

| # | 修正箇所 | 修正内容 | 優先度 | 確認状況 |
|---|---------|---------|-------|---------|
| B-1 | §全API一覧「入荷管理」 | `API-INB-confirm`（入荷確認: `POST /api/v1/inbound/slips/{id}/confirm`、SYSTEM_ADMIN・WAREHOUSE_MANAGER・WAREHOUSE_STAFF）および `API-INB-cancel`（入荷キャンセル: `POST /api/v1/inbound/slips/{id}/cancel`、同ロール）を追加する。これらは業務フロー上必須の操作であり、固有のAPI IDを持つため全API一覧への掲載が必要 | **高** | ✅ 対応完了（2026-03-18確認） |
| B-2 | §全API一覧「レポート」 | API-RPT-002 の欠番理由を補足注記として追記する（例: 「API-RPT-002 は欠番（当初予定していた入荷検品サマリーレポートを API-RPT-001 に統合したため）」等の記載） | **低** | ✅ 対応完了（2026-03-18修正）`_standard-api.md` の全API一覧に「※ API-RPT-002は欠番」の注記を追加 |
| B-3 | §全API一覧（各マスタ管理グループ） | `PATCH .../deactivate` エンドポイントの API 名表記を「商品無効化」から「商品無効化/有効化」に統一する（MST-PRD-005, MST-PAR-005, MST-FAC-005/015/025/035, MST-USR-005）。実際のAPI設計書では無効化・有効化の両方向をサポートしており、一覧の表記が実態と合っていない | **低** | ✅ 対応完了（2026-03-18修正）`_standard-api.md` の全API一覧で施設・取引先・倉庫・商品・棟・エリア・ロケーションの各deactivate APIを「無効化/有効化」に統一 |

## 10-api-master-facility.md 修正要 アクションアイテム

| # | 修正箇所 | 修正内容 | 優先度 | 確認状況 |
|---|---------|---------|-------|---------|
| C-1 | §共通エラーレスポンス形式 | エラーレスポンスのフィールド名を `fieldErrors` から `details` に統一する。08-api-overview.md では `details[].field / details[].message` と定義されているが、10-api-master-facility.md では `fieldErrors` を使用しており不整合が生じている。クライアント実装上の混乱を防ぐため統一が必要 | **高** | ✅ 対応完了（2026-03-18確認） |

---

## API設計書全体サマリー（横断的な気づき）

### 1. 補助APIの位置づけ不統一

各APIファイルで「補助API」と称するエンドポイントの扱いが一貫していない。

- `11-api-inbound.md` の「補助API: 入荷確認（`API-INB-confirm`）」「補助API: 入荷キャンセル（`API-INB-cancel`）」は固有のAPI IDを持ち、業務的に必須の操作であるため、全API一覧への掲載が必要。
- `13-api-outbound.md` の補助API（個別在庫引当・一括在庫引当・受注キャンセル）はAPI IDが「—（補助API）」と定義されており、内部実装上の位置づけであるため、全API一覧への未掲載は許容される。
- `14-api-batch-report.md` の「バッチ実行履歴詳細取得（`GET /api/v1/batch/executions/{id}`）」もAPI IDが「—（補助API）」だが、フロントエンドがポーリングに使用する重要なエンドポイントであり、全API一覧への掲載が望ましい。

**推奨**: API IDを持つエンドポイントはすべて全API一覧に掲載し、内部補助目的でAPI IDを持たないエンドポイントのみ補助API扱いとするよう統一する。

### 2. エラーレスポンスのフィールド名不統一

前述の通り、`10-api-master-facility.md` が `fieldErrors` を使用している点は 08-api-overview.md の `details` と異なる。他のAPIファイル（09, 10-product, 10-partner, 10-user, 11, 12, 13, 14）では `details` で統一されているため、施設マスタAPIのみ例外になっている。

### 3. CORS設定のPATCH未許可問題（重大）

`10-security-architecture.md` の CORS 許可メソッドに `PATCH` が含まれていない。全マスタ管理の無効化/有効化エンドポイントが `PATCH` メソッドを使用しているため、この問題が解消されないと実装フェーズで全マスタ無効化/有効化機能がフロントエンドから呼び出せない致命的な問題が発生する。

### 4. 入荷ステータス「CONFIRMED」の業務的位置づけ

`11-api-inbound.md` のステータス遷移図では `PLANNED → CONFIRMED → INSPECTING` の遷移が定義されている。しかし機能要件定義書（`02-inbound-management.md`）では入荷確認ステップが明確化されているかどうかを確認する必要がある。入荷確認（CONFIRMED）は業務的に「担当者が入荷予定を確認・承認する」ステップとして定義されているが、画面設計レビュー記録票（review-records.md）では入荷管理の I-12 で「INB-002 は新規登録専用、INB-003 には編集ボタンを設けない設計」とあり、CONFIRMED 遷移の画面上の操作フローが明確でない可能性がある。API設計フェーズでの確認事項として記録する。

### 5. リフレッシュトークンのスライディング方式の動作詳細

08-api-overview.md では「スライディング方式（最終アクセスから1時間）」と記載されているが、`10-security-architecture.md` では「リフレッシュ時に古いトークンを無効化し新しいトークンを発行（トークンローテーション）」と記載されている。「最終アクセスから1時間」がリフレッシュAPIへのアクセスを指すのか、任意のAPIアクセスを指すのかが08-api-overview.mdから不明確。実装仕様として「リフレッシュトークンの有効期限はリフレッシュAPIを呼び出すたびにリセットされる」旨を明記することが望ましい。

### 6. タイムゾーン処理の補足必要性

08-api-overview.md のリクエスト共通仕様では「タイムゾーン: Asia/Tokyo（フロントエンド↔バックエンド間の送受信はJST）」と記載されているが、`01-overview.md`（データモデル）では「DB保存はUTC」と定義されている。APIを通じた送受信はJST、DBへの永続化はUTCという変換がバックエンド内部で自動的に行われることを、08-api-overview.md の共通仕様として補足記載することが望ましい。

### 7. ページングサイズ上限の一貫性

08-api-overview.md §ページング仕様では `size` の上限を `100` と定義している。各APIファイルでの確認：
- 商品マスタ（上限100）、取引先マスタ（上限100）、入荷管理（上限100）、バッチ履歴（上限100）で一貫して守られている。
- 施設マスタAPIでは `1〜100` と明示されており整合している。
全APIで上限100が守られており問題なし。
