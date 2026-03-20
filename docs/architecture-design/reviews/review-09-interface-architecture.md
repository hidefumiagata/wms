# アーキテクチャ設計レビュー記録票 — インターフェースアーキテクチャ

> 対象成果物: `docs/architecture-design/09-interface-architecture.md`
> レビュー日: 2026-03-18
> レビュー担当: システム間連携・インターフェースアーキテクチャ設計スペシャリスト（AI）
> 参照ドキュメント:
> - `docs/architecture-blueprint/09-interface-architecture.md`（ブループリント）
> - `docs/architecture-blueprint/01-overall-architecture.md`（全体アーキテクチャ）
> - `docs/architecture-blueprint/04-backend-architecture.md`（バックエンドアーキテクチャ）
> - `docs/architecture-blueprint/06-infrastructure-architecture.md`（インフラアーキテクチャ）
> - `docs/architecture-blueprint/08-common-infrastructure.md`（共通基盤）
> - `docs/functional-requirements/02-inbound-management.md`（入荷管理）
> - `docs/functional-requirements/04-outbound-management.md`（出荷管理）
> - `docs/functional-requirements/06-batch-processing.md`（バッチ処理）
> - `docs/functional-requirements/07-interface.md`（外部連携I/F）
> - `docs/functional-requirements/01-master-management.md`（マスタ管理）
> - `docs/data-model/01-overview.md`（データモデル概要）
> - `docs/data-model/02-master-tables.md`（マスタテーブル定義）
> - `docs/data-model/03-transaction-tables.md`（トランザクションテーブル定義）

---

## エグゼクティブサマリー

インターフェースアーキテクチャ設計書は、ブループリント（09-interface-architecture.md）で定義された方針を忠実に詳細化し、実装に必要な具体的な仕様を網羅している。CSVフォーマット定義・バリデーション設計・エラーハンドリング・トランザクション制御の各観点で十分な設計がなされている。SSOTルールに従い、ビジネスルールやテーブル定義の詳細は参照元ドキュメントへのリンクで示しており、情報の重複は最小限に抑えられている。

**総合評価: A（軽微な指摘のみ、設計品質は良好）**

---

## レビュー結果

| No | チェック観点 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|------------|----------------|---------|------|---------|
| 1 | ブループリントとの整合性 | 09-interface-architecture.md | ブループリントで定義されたI/F一覧（IFX-001/IFX-002）、ステートレス2ステップ設計、取り込みモード（SUCCESS_ONLY/DISCARD）、Blobフォルダ構成、APIエンドポイントの全てが設計書に反映されている。整合性に問題なし | 確認OK | — |
| 2 | SSOTルール準拠 | CLAUDE.md | テーブル定義（data-model）、ビジネスルール（functional-requirements）、技術方針（architecture-blueprint）への参照リンクで示しており、情報の複製は行っていない。ただし、if_executionsテーブルの詳細定義はブループリントの概要から拡張したものであり、data-modelのSSOTに追加が必要 | 軽微 | 未対応（data-model側への反映は別タスク） |
| 3 | 入荷管理との整合性 | 02-inbound-management.md, API-06-inbound.md | 入荷予定登録の業務ルール（同一伝票内の同一商品コード重複禁止、入荷予定日の営業日制約、ロット/期限管理の条件付き必須）がバリデーション設計に正しく反映されている | 確認OK | — |
| 4 | 出荷管理との整合性 | 04-outbound-management.md, API-08-outbound.md | 受注登録の業務ルール（出荷禁止フラグチェック）がバリデーション設計に反映されている。受注CSVにはlot_number/expiry_dateが不要（出荷時点では不要、引当時に在庫から決定）であり、フォーマット設計は正しい | 確認OK | — |
| 5 | マスタ管理との整合性 | 01-master-management.md | 取引先種別（SUPPLIER/CUSTOMER/BOTH）による入荷元/出荷先の選択制約がバリデーション（L3マスタ参照チェック）に反映されている | 確認OK | — |
| 6 | データモデルとの整合性 | data-model/03-transaction-tables.md | CSVカラムからエンティティへの変換仕様が、inbound_slips/inbound_slip_lines/outbound_slips/outbound_slip_linesのテーブル定義と整合している。マスタ情報のコピー保持（partner_code/partner_name等）も正しく設計されている | 確認OK | — |
| 7 | エラーコード体系 | 08-common-infrastructure.md | エラーコードが `WMS-E-IFX-{連番}` 形式で、共通基盤のエラーコード体系（WMS-{種別}-{モジュール}-{連番}）に準拠している | 確認OK | — |
| 8 | 営業日基準 | 06-batch-processing.md, 01-overall-architecture.md | 取り込み時の営業日基準ルールが明記されている。入荷予定日の営業日以降制約のバリデーション（L4: WMS-E-IFX-401）も正しい | 確認OK | — |
| 9 | トランザクション制御 | 04-backend-architecture.md | DB→Blob順のトランザクション設計が文書化されている。Blob移動失敗時の対応（ERRORログ＋blob_move_failedフラグ）も定義されている。ただし、blob_move_failedがtrueの履歴に対するリカバリ運用手順は記載があるが、管理画面からの再移動機能は未定義 | 情報 | 将来的なエンハンスとして検討 |
| 10 | クラス構成 | 04-backend-architecture.md | interfacingモジュールのクラス構成が標準3層アーキテクチャ（Controller/Service/Repository）に準拠している。BlobStorageClientをblob/パッケージに分離した設計は適切 | 確認OK | — |
| 11 | パフォーマンス最適化 | — | マスタ検索のバッチ化（IN句一括検索→ローカルMap）の最適化設計が含まれている。10,000行で30秒以内のバリデーション目標は現実的 | 確認OK | — |
| 12 | セキュリティ | 10-security-architecture.md | JWT認証・ロール認可・Blob Storage認証方式・CSVインジェクション対策・ファイルサイズ制限が定義されている | 確認OK | — |
| 13 | バリデーション結果レスポンス | — | レスポンスにエラー行のみを含む設計（成功行はカウントのみ）はレスポンスサイズ抑制として合理的。ただし、フロントエンド側で「成功行の内容プレビュー」機能が必要になった場合は設計変更が必要 | 情報 | 現時点ではブループリントの方針通り |
| 14 | processedディレクトリの日付整理 | — | processedフォルダ内を年/月/日のサブディレクトリで整理する設計は、ブループリントの概要設計（フラットなprocessed/）から詳細化したもの。追跡性向上の観点で妥当な拡張 | 確認OK | — |
| 15 | 期限管理商品の期限日チェック | 02-inbound-management.md | 入荷予定CSVで期限管理対象商品のexpiry_dateが現在営業日以前の場合にエラー（WMS-E-IFX-404）とする設計を追加している。機能要件定義書には明示的な記載はないが、期限切れ商品の入荷を防止する合理的なビジネスルール。機能要件定義書への追記を推奨する | 要確認 | 機能要件定義書とのすり合わせが必要 |

---

## 未対応事項・今後の課題

| No | 課題 | 優先度 | 対応方針 |
|----|------|--------|---------|
| 1 | if_executionsテーブルの正式なデータモデル定義書（data-model/）への追加 | 中 | 別タスクでdata-model/03-transaction-tables.mdまたは新規ファイルに追加 |
| 2 | 機能要件定義書への期限日バリデーション（入荷予定で期限切れを防止）の追記 | 低 | 02-inbound-management.mdのビジネスルールに追記 |
| 3 | Blob移動失敗時の管理画面からのリカバリ機能 | 低 | 運用開始後の状況を見て検討 |
