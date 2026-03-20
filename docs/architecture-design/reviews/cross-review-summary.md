# アーキテクチャ設計書 横断レビューサマリー

> レビュー日: 2026-03-18
> レビュー担当: アーキテクチャ横断レビューアー（AI）
> 対象: 全13本のアーキテクチャ設計レビュー記録票

---

## エグゼクティブサマリー

| 重大度 | 件数 |
|--------|------|
| Critical | 0件 |
| Major | 4件 |
| Minor | 14件 |
| Info（改善提案） | 12件 |
| **合計** | **30件** |

全13本のレビュー記録票から「要対応」「要アクション」「要確認」「改善提案」に分類された指摘事項を横断的に集約した。Critical（致命的なセキュリティ欠陥・データ整合性破壊）に該当する指摘はゼロであった。Major 4件は実装フェーズ開始前に方針確定が必要な項目であり、早期対応を推奨する。Minor 14件は実装フェーズで順次対応可能、Info 12件は将来改善として記録する。

### 横断的に検出された重複パターン

以下の問題が複数レビューで独立して検出されており、影響範囲が広い:

1. **エラーコード/エラーレスポンス形式の不統一** (No.1) — 3セクションにまたがる
2. **HikariCP接続数とPostgreSQL上限の不整合** (No.2) — 3セクションにまたがる
3. **パスワードリセットトークン関連の矛盾** (No.3, No.4) — 3セクションにまたがる
4. **システムパラメータ動的取得の未設計** (No.9) — 3セクションにまたがる

---

## 要対応事項一覧

| No | 重大度 | 発見元 | 関連セクション | 指摘内容 | 推奨対応 |
|----|--------|--------|--------------|---------|---------|
| 1 | Major | review-04 No.4, review-08 No.1 | 04-backend, 08-common, _standard-api.md | **エラーコード体系の不統一**: ブループリントは `WMS-E-MST-001` 形式、API設計書は `PRODUCT_NOT_FOUND` フラット形式、ErrorResponseのフィールド名も `code` vs `errorCode` で不一致 | ✅ **対応済み（2026-03-18）**: 08-common-infrastructureの5フィールド構成（code/message/timestamp/traceId/details）に全設計書で統一 |
| 2 | Major | review-02 No.22, review-05 No.4, review-13 No.5 | 02-system, 05-database, 13-non-functional | **HikariCP接続数がPostgreSQL上限を超過するリスク**: prd環境でmax 5レプリカ x poolSize 20 = 100接続だが、PostgreSQL B1msの上限は50。review-05ではpoolSize=10に設計済みだが、review-02/13では20のまま | ✅ **対応済み（2026-03-18）**: dev=5/prd=10に全設計書で統一。5レプリカ x 10 = 50接続でDB上限内に収まる構成に修正 |
| 3 | Major | review-07 No.10 | 07-auth, API-01-auth, 00-authentication(FR) | **パスワードリセットトークン有効期限の矛盾**: 機能要件定義書とブループリントは「30分」、API設計書(API-AUTH-005)は「1時間」と記載 | ✅ **対応済み（2026-03-18）**: SHA-256、有効期限30分に統一。API-01-auth.mdも修正済み |
| 4 | Major | review-07 No.9 | 07-auth, 10-security, data-model/02 | **パスワードリセットトークンのハッシュ方式の矛盾**: データモデル定義書は「SHA-256等」、API設計書は「BCryptでハッシュ化」、セキュリティ設計書は「SHA-256」と記載 | ✅ **対応済み（2026-03-18）**: SHA-256に統一。データモデル定義書・API設計書・セキュリティ設計書すべてでSHA-256、有効期限30分に統一 |
| 5 | Minor | review-08 No.10 | 08-common, data-model/02 | **マスタテーブルのversionカラム未定義**: 共通基盤でBaseMasterEntityにversionカラム（楽観的ロック）を追加しているが、データモデル定義書にversionカラムの定義がない | ✅ **対応済み（2026-03-18）**: data-model/01-overviewの共通パターンと02-master-tablesの8テーブルにversionカラムを追加 |
| 6 | Minor | review-03 No.10 | 03-frontend | **外部連携I/Fモジュールの欠落**: フロントエンドのディレクトリ構成にinterfaceモジュール（Composable/View/Router）が含まれていない。機能要件ではI/F管理画面が定義されている | ✅ **対応済み（2026-03-19）**: 03-frontend-architecture.md に外部連携I/Fモジュールディレクトリを追加 |
| 7 | Minor | review-03 No.15 | 03-frontend | **Element Plusロケール設定の未記載**: main.tsにElement Plus日本語ロケール設定が未記述。日付ピッカー等のUI文言が英語表示になる | ✅ **対応済み（2026-03-19）**: 03-frontend-architecture.md に Element Plus ロケール設定を追記 |
| 8 | Minor | review-02 No.10 | 02-system, 06-infrastructure(BP) | **Terraformモジュール追加のブループリント未反映**: 設計書で`modules/monitoring`と`modules/identity`を新規追加したが、ブループリント側に未記載 | ✅ **対応済み（2026-03-19）**: Q4方針決定によりidentityモジュールをcontainer-appsに統合 |
| 9 | Minor | review-04 No.14, review-08 No.13, review-10 No.16 | 04-backend, 08-common, 10-security | **システムパラメータの動的取得方針が未設計**: ブループリントでは「キャッシュなし都度DB取得」と定義されているが、設計書のコード例ではハードコード定数を使用。LOGIN_FAILURE_LOCK_COUNT、SESSION_TIMEOUT_MINUTES等が対象 | 共通基盤設計書にシステムパラメータ取得のユーティリティクラス設計を追加する。実装時にはsystem_parametersテーブルからの動的取得を使用する |
| 10 | Minor | review-08 No.14 | 08-common, 06-batch(FR) | **バッチ処理時のSecurityContext設定方針が未定義**: バッチ実行時は認証なし（SYSTEM_USER_ID=0）で動作するが、実行者ユーザーIDが利用可能なため、SecurityContextに設定すべき | ✅ **対応済み（2026-03-19）**: Q5方針決定によりバッチSecurityContext=実行者IDを08-common セクション8.6に追加 |
| 11 | Minor | review-04 No.5 | 04-backend, 04a-allocation(FR) | **在庫引当の悲観ロック順序保証の詳細設計が必要**: ロック取得順序（product_id昇順→location_id昇順）とFEFO/FIFOアルゴリズムの組み合わせでロック順序が崩れる可能性がある | ✅ **対応済み（2026-03-19）**: Q10方針決定によりロック順序=inventory_id昇順に統一 |
| 12 | Minor | review-09 No.2 | 09-interface, data-model | **if_executionsテーブルのデータモデル未追加**: インターフェース設計書でif_executionsテーブルを定義したが、data-model側に正式なテーブル定義が追加されていない | data-model/03-transaction-tables.mdまたは新規ファイルにif_executionsテーブル定義を追加する |
| 13 | Minor | review-06 No.12 | 06-infrastructure, 06-infrastructure(BP) | **ACR Geo-replicationとSKUの矛盾**: ブループリントではprd環境でBasic SKU + Geo-replicationと記載あるが、Geo-replicationにはPremium SKUが必須 | ✅ **対応済み（2026-03-19）**: Q1方針決定によりACR Basic SKU維持、Geo-replication削除 |
| 14 | Minor | review-09 No.15 | 09-interface, 02-inbound(FR) | **期限切れ商品の入荷防止ルールが機能要件に未記載**: 設計書で期限管理対象商品のexpiry_dateが営業日以前の場合にエラーとするルールを追加したが、機能要件定義書に未記載 | functional-requirements/02-inbound-management.mdのビジネスルールに期限日バリデーションを追記する |
| 15 | Minor | review-08 No.7, No.8 | 08-common | **棚卸・ばらし指示ステータスEnumの未定義**: StocktakeStatus、UnpackingInstructionStatusのEnum定義が共通基盤に未追加 | ✅ **対応済み（2026-03-19）**: StocktakeStatus Enum と UnpackInstructionStatus Enum を08-common-infrastructure.mdに追加 |
| 16 | Minor | review-07 No.7, No.8 | 07-auth | **SecurityConfigコード例の不備**: AccessDeniedHandlerの設定が未反映。ログアウト時のrefresh_token Cookie非送信（Path制限による）の設計判断が未記載 | ✅ **対応済み（2026-03-19）**: SecurityConfigにAccessDeniedHandler追加、ログアウト時refresh_token Cookie非送信の設計判断明記 |
| 17 | Minor | review-04 No.9 | 04-backend | **build.gradleにTestcontainers依存が未追加**: テスト戦略でTestcontainersを前提としているが、dependenciesに含まれていない | ✅ **対応済み（2026-03-19）**: build.gradleにTestcontainers依存を追加 |
| 18 | Minor | review-11 No.6 | 11-monitoring, 06-infrastructure(BP) | **監視リソースのDestroy方針が未定義**: Log Analytics Workspace/Application InsightsがTerraform Destroy対象だと過去ログが消失する。常設リソースに含めるべきか未決定 | ✅ **対応済み（2026-03-19）**: Q2方針決定により環境同居destroy方針を確定 |
| 19 | Info | review-05 No.15 | 05-database | コールドスタート時のDB接続確立遅延への言及を追加すると良い | 次回設計書改訂時に追記を検討 |
| 20 | Info | review-06 No.6 | 06-infrastructure, 02-system(BP) | コスト概算値がブループリント(dev ~$9, prd ~$80)と設計書(dev ~$6.30, prd ~$60.50)で差異あり | ブループリント側の概算値を設計書の詳細見積もりに合わせて更新 |
| 21 | Info | review-06 No.13 | 06-infrastructure | prd環境のmax_connectionsを100に引き上げ検討（East+West同時接続時） | ✅ **対応済み（2026-03-19）**: Q3方針決定によりmax_connections=50維持、注記追加 |
| 22 | Info | review-12 No.8 | 12-development | VITE_API_BASE_URLのTerraform output→GitHub Variables手動更新の運用手順を追記すべき | 運用手順書作成時に対応 |
| 23 | Info | review-12 No.9 | 12-development | GitHub Actions Azure LoginをOIDCフェデレーションに移行するとシークレットローテーション不要 | ✅ **対応済み（2026-03-19）**: Q7方針決定によりOIDC見送り、注記追加 |
| 24 | Info | review-12 No.10 | 12-development | prd環境のGitHub Environment Protection Rules（手動承認）の有効化 | ✅ **対応済み（2026-03-19）**: Q8方針決定によりprd Protection Rules有効化をセクション7.4に追加 |
| 25 | Info | review-12 No.15 | 12-development | prd環境Flywayマイグレーション失敗時のロールバック手順を運用手順書に記載すべき | 運用手順書作成時に対応 |
| 26 | Info | review-13 No.1 | 13-non-functional | API応答時間目標の測定ポイント（バックエンド処理時間 vs ユーザー体感時間）の違いを明記すべき | 設計書に測定ポイント説明を追記（対応済みとの記載あり） |
| 27 | Info | review-13 No.6 | 13-non-functional | Container Appsスケールアウト条件（同時リクエスト20以上/レプリカ）の根拠が不明 | ✅ **対応済み（2026-03-19）**: Q6方針決定によりスケールアウト閾値=暫定値として明記 |
| 28 | Info | review-13 No.7 | 13-non-functional | テストカバレッジ80%の除外対象（自動生成コード、設定クラス等）を明確化すべき | 実装時にJaCoCo除外設定を定義 |
| 29 | Info | review-13 No.9 | 13-non-functional | DB障害時RTOの「数時間」が曖昧。具体的な手順と所要時間目安を運用手順書に記載すべき | 運用手順書作成時に詳細化 |
| 30 | Info | review-13 No.13 | 13-non-functional | インデックス再構築・ACRイメージ削除の定期タスク自動化を検討すべき | 運用安定後に自動化検討 |

---

## 対応優先度マトリクス

### 実装フェーズ開始前に対応必須（Major）

| No | 内容 | 対応者 | 状況 |
|----|------|--------|------|
| 1 | エラーコード体系の統一方針確定 | アーキテクト | ✅ 対応済み（2026-03-18） |
| 2 | HikariCP maximumPoolSizeの統一（全設計書で10に統一） | アーキテクト | ✅ 対応済み（2026-03-18） |
| 3 | パスワードリセットトークン有効期限の統一（30分に修正） | ドキュメント担当 | ✅ 対応済み（2026-03-18） |
| 4 | パスワードリセットトークンハッシュ方式の統一 | ドキュメント担当 | ✅ 対応済み（2026-03-18） |

### 設計書修正として対応（Minor - ドキュメント修正）

| No | 内容 | 対応者 | 状況 |
|----|------|--------|------|
| 5 | データモデルにversionカラム追加 | ドキュメント担当 | ✅ 対応済み（2026-03-18） |
| 6 | フロントエンドにinterfaceモジュール追加 | ドキュメント担当 | ✅ 対応済み（2026-03-19） |
| 7 | Element Plusロケール設定追記 | ドキュメント担当 | ✅ 対応済み（2026-03-19） |
| 8 | ブループリントにTerraformモジュール追記 | ドキュメント担当 | ✅ 対応済み（2026-03-19）: Q4によりidentity→container-apps統合 |
| 12 | データモデルにif_executionsテーブル追加 | ドキュメント担当 |
| 13 | ブループリントのACR SKU記述修正 | ドキュメント担当 | ✅ 対応済み（2026-03-19）: Q1によりBasic SKU維持・Geo-rep削除 |
| 14 | 機能要件に期限日バリデーション追記 | ドキュメント担当 |

### 実装フェーズで対応（Minor - 実装時）

| No | 内容 | 対応タイミング |
|----|------|--------------|
| 9 | システムパラメータ動的取得の実装 | 共通基盤実装時 |
| 10 | ~~バッチ処理SecurityContext設定~~ | ~~バッチ処理実装時~~ ✅ 対応済み（2026-03-19）: Q5により08-common セクション8.6に追加 |
| 11 | ~~在庫引当ロック順序の詳細設計~~ | ~~引当モジュール実装時~~ ✅ 対応済み（2026-03-19）: Q10によりinventory_id昇順に統一 |
| 15 | ~~棚卸・ばらし指示ステータスEnum追加~~ | ~~各モジュール実装時~~ ✅ 対応済み（2026-03-19）: StocktakeStatus・UnpackInstructionStatus追加 |
| 16 | ~~SecurityConfigコード例の補完~~ | ~~認証実装時~~ ✅ 対応済み（2026-03-19）: AccessDeniedHandler追加・refresh_token非送信明記 |
| 17 | ~~Testcontainers依存追加~~ | ~~プロジェクト初期セットアップ時~~ ✅ 対応済み（2026-03-19） |
| 18 | ~~監視リソースの常設方針確定~~ | ~~インフラ構築時~~ ✅ 対応済み（2026-03-19）: Q2により環境同居destroy方針確定 |

---

## セクション間の整合性マトリクス

以下のセクション間で矛盾・不整合が検出された:

| セクションA | セクションB | 不整合内容 | 対応No |
|------------|------------|-----------|--------|
| 08-common (ErrorResponse) | _standard-api.md (エラーレスポンス) | ~~フィールド名 `code` vs `errorCode`~~ ✅ 解決済み（2026-03-18） | 1 |
| 02-system (poolSize=20) | 05-database (poolSize=10) | ~~prd環境のHikariCP maximumPoolSize~~ ✅ 解決済み（2026-03-18） | 2 |
| API-01-auth (1時間) | 00-authentication FR (30分) | ~~パスワードリセットトークン有効期限~~ ✅ 解決済み（2026-03-18） | 3 |
| data-model/02 (SHA-256) | API-01-auth (BCrypt) | ~~パスワードリセットトークンハッシュ方式~~ ✅ 解決済み（2026-03-18） | 4 |
| 08-common (version使用) | data-model/02 (version未定義) | ~~マスタテーブルのversionカラム~~ ✅ 解決済み（2026-03-18） | 5 |
| 06-infra BP (ACR Basic+Geo-rep) | Azure仕様 (Geo-repはPremium必須) | ~~ACR SKUとGeo-replication~~ ✅ 解決済み（2026-03-19）: Q1によりBasic維持・Geo-rep削除 | 13 |
