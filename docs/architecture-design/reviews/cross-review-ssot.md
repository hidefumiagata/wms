# アーキテクチャ設計書 SSOT準拠チェックレポート

> 検証日: 2026-03-18
> 検証担当: ドキュメント品質管理スペシャリスト（AI）
> 対象: `docs/architecture-design/01-overall-architecture.md` 〜 `13-non-functional-requirements.md`（全13本）

---

## 検証サマリー

| セクション | SSOT違反数 | 主な違反内容 |
|-----------|-----------|------------|
| 01-overall-architecture | 0件 | — |
| 02-system-architecture | 0件 | — |
| 03-frontend-architecture | 0件 | — |
| 04-backend-architecture | 0件 | — |
| 05-database-architecture | ~~1件~~ 0件 | ~~システムパラメータのデフォルト値をINSERT文に直接記載~~ ✅ 対応済み（2026-03-19） |
| 06-infrastructure-architecture | 0件 | — |
| 07-auth-architecture | 0件 | — |
| 08-common-infrastructure | 0件 | — |
| 09-interface-architecture | 0件 | — |
| 10-security-architecture | ~~2件~~ 0件 | ~~パスワードポリシー具体値の散文記載、アカウントロック閾値の散文記載~~ ✅ 対応済み（2026-03-19） |
| 11-monitoring-operations | 0件 | — |
| 12-development-deploy | 0件 | — |
| 13-non-functional-requirements | ~~1件~~ 0件 | ~~HikariCP接続数の値が他設計書と不整合~~ ✅ 対応済み（2026-03-18） |

**合計: 4件のSSSOT違反（全4件対応済み）**

---

## SSOT違反の詳細

### 違反 #1 — ✅ 対応完了（2026-03-19）

- **ファイル**: `docs/architecture-design/10-security-architecture.md`
- **該当箇所**: セクション6「パスワードポリシー実装設計」（行557〜560付近）
- **違反内容**: `PasswordPolicyValidator`クラスのJavadocコメントに、パスワードポリシーの具体値「8〜128文字、英大文字・英小文字・数字を各1文字以上必須」が散文として記載されている。セクション冒頭に参照リンクはあるが、コメント中の散文説明がポリシー値の複製に該当する。
- **SSOT定義場所**: `architecture-blueprint/10-security-architecture.md`
- **推奨修正**: Javadocコメントを以下のように変更する:
  ```java
  /**
   * パスワードポリシーバリデーター。
   * ポリシー定義値は architecture-blueprint/10-security-architecture.md を参照。
   */
  ```
  なお、コード内の定数 `MIN_LENGTH = 8` / `MAX_LENGTH = 128` やバリデーション実装自体は「実装ガイド」として必要なため違反とはしない。

> **対応完了** (2026-03-19): Javadoc コメントからパスワードポリシー具体値の散文記載を除去し、「ポリシー定義値は architecture-blueprint/10-security-architecture.md を参照」に変更

### 違反 #2 — ✅ 対応完了（2026-03-19）

- **ファイル**: `docs/architecture-design/10-security-architecture.md`
- **該当箇所**: セクション7「アカウントロックポリシー実装設計」（行678付近のフローチャート）
- **違反内容**: Mermaidフローチャート内に `failed_login_count >= 5?` と具体的なロック閾値「5」が散文的に記載されている。セクション冒頭の参照リンクは正しく設置されているが、フローチャート内のテキストがポリシー値の複製に該当する。
- **SSOT定義場所**: `architecture-blueprint/10-security-architecture.md`
- **推奨修正**: フローチャート内の条件を以下のように変更する:
  ```
  H{failed_login_count >= ロック閾値?}
  ```
  もしくは、フローチャート直下に「※ロック閾値はarchitecture-blueprint/10-security-architecture.mdで定義」と注記を追加する。コード内の `MAX_FAILED_ATTEMPTS = 5` 定数は実装ガイドとして許容する。

> **対応完了** (2026-03-19): フローチャート内の `failed_login_count >= 5?` を `failed_login_count >= ロック閾値?` に変更し、フローチャート直下に参照注記を追加

### 違反 #3 — ✅ 対応完了（2026-03-19）

- **ファイル**: `docs/architecture-design/05-database-architecture.md`
- **該当箇所**: セクション6.6「初期データ投入」（行675〜686付近）
- **違反内容**: `V100__insert_initial_system_parameters.sql` のINSERT文内に、システムパラメータのデフォルト値が直接記載されている:
  - `LOCATION_CAPACITY_CASE` = `1`
  - `LOCATION_CAPACITY_BALL` = `6`
  - `LOCATION_CAPACITY_PIECE` = `100`
  - `LOGIN_FAILURE_LOCK_COUNT` = `5`
  - `SESSION_TIMEOUT_MINUTES` = `60`
  - `PASSWORD_RESET_EXPIRY_MINUTES` = `30`

  これはマイグレーションSQLの例示として記載されているが、SSOTルールではシステムパラメータの一覧・デフォルト値は `data-model/02-master-tables.md` に定義するとされている。
- **SSOT定義場所**: `data-model/02-master-tables.md`（system_parametersの初期データ）
- **推奨修正**: INSERT文の値部分を省略形にし、参照リンクを追加する:
  ```sql
  -- V100__insert_initial_system_parameters.sql
  -- システムパラメータ初期データ
  -- ※ 初期値の定義は data-model/02-master-tables.md を参照

  INSERT INTO system_parameters (param_key, param_value, default_value, ...)
  VALUES
    ('LOCATION_CAPACITY_CASE', ..., ...),
    -- ... 以下省略（data-model/02-master-tables.md の定義に従う）
  ```

> **対応完了** (2026-03-19): INSERT 文内のシステムパラメータ具体値を省略形に変更し、data-model/02-master-tables.md への参照リンクを追加

### ~~違反 #4~~ **✅ 対応済み（2026-03-18）**

- **ファイル**: `docs/architecture-design/13-non-functional-requirements.md`
- **該当箇所**: セクション1.3.1「バックエンドAPI最適化」（行53付近）
- **対応内容**: HikariCP poolSizeをdev=5/prd=10に全設計書（02-system-architecture.md、05-database-architecture.md、13-non-functional-requirements.md）で統一。値の不整合を解消済み。

---

## 良好な参照パターンの例

以下は、SSOTルールを正しく守っている好例である。

### 好例 #1: 01-overall-architecture.md — 技術選定の参照

> 技術選定の根拠は [architecture-blueprint/01-overall-architecture.md](../architecture-blueprint/01-overall-architecture.md) を参照。

技術スタック一覧は記載するが、選定根拠はブループリントへの参照リンクのみとしており、方針の複製を避けている。

### 好例 #2: 05-database-architecture.md — テーブル定義の参照

> テーブル定義の詳細は [データモデル定義書 — マスタ系テーブル](../data-model/02-master-tables.md) を参照。

インデックス設計は本設計書の責務として記載しつつ、テーブル定義・カラム定義はdata-modelへの参照に留めている。

### 好例 #3: 10-security-architecture.md — セキュリティヘッダー定義の参照

> ヘッダーの定義（SSOT）は [architecture-blueprint/10-security-architecture.md](../architecture-blueprint/10-security-architecture.md) を参照

セキュリティヘッダーの「値と目的」はブループリントで定義し、本設計書ではSpring Security実装コードのみを記載するパターン。ただし、ヘッダー一覧テーブルで値を再掲している箇所があり、これはヘッダー設定の実装コードとセットで読む必要があるため、実装ガイドとしてボーダーラインだが許容範囲と判定した。

### 好例 #4: 07-auth-architecture.md — 権限マトリクスの参照

> 権限マトリクスの詳細は [architecture-blueprint/07-auth-architecture.md](../architecture-blueprint/07-auth-architecture.md) を参照

権限マトリクスをまるごと複製せず、ブループリントへの参照リンクのみとし、本設計書では `@PreAuthorize` の実装コード例のみを記載している。

### 好例 #5: 11-monitoring-operations.md — ログ設計の参照

> ログフォーマット・PIIマスキングの詳細は [architecture-blueprint/08-common-infrastructure.md](../architecture-blueprint/08-common-infrastructure.md) を参照。

ログの方針定義（フォーマット・マスキングルール等）はブループリントに委ね、本設計書ではAzure Monitor/Log Analyticsの具体的な設定・KQLクエリに集中している。

### 好例 #6: 13-non-functional-requirements.md — セキュリティ要件の参照テーブル

セクション6.1「セキュリティ要件サマリー」で、各セキュリティ要件を一覧表にまとめつつ、「参照先」カラムでブループリントの該当セクションへのリンクを明示している。要件の詳細値はリンク先に委ねる良い構成。

---

## 総合評価

全13本のアーキテクチャ設計書のうち、SSOT違反は**4件**と少数に留まっている。全体として以下の傾向が確認できた。

### 良好な点

1. **全設計書の冒頭に参照ドキュメントが明示されている** — ブループリントとの関係が明確で、読者が方針の出典を辿りやすい。
2. **テーブル定義・カラム定義の複製が適切に回避されている** — data-modelへの参照リンクが一貫して使用されている。
3. **ビジネスルールの複製が見られない** — functional-requirementsの業務ルールが転記されているケースは検出されなかった。
4. **API仕様の複製がない** — functional-design/API-*.mdの内容は適切に参照に留められている。

### 改善すべき点

1. **実装コード例中のコメントにポリシー値を記載するパターン** — コード定数（`MIN_LENGTH = 8`等）は実装ガイドとして必要だが、Javadocやフローチャートに散文で値を記載するのは避けるべき。
2. **SQL例示に具体的なパラメータ値を含めるパターン** — マイグレーションSQLのサンプルであっても、SSOTで管理される値は省略形＋参照リンクが望ましい。
3. **複数設計書間の値の不整合** — HikariCPの接続数のように、同じ設計パラメータが複数の設計書に記載され、値が食い違うケースが発生している。SSOTの定義場所を1箇所に限定し、他は参照に留めることで防止可能。
