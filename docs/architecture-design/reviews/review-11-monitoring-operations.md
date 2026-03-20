# アーキテクチャ設計レビュー記録票 — 監視・運用

> 対象成果物: `docs/architecture-design/11-monitoring-operations.md`
> レビュー日: 2026-03-18
> レビュー担当: SRE・監視運用設計スペシャリスト（AI）
> 参照ドキュメント:
> - `docs/architecture-blueprint/11-monitoring-operations.md`（監視・運用ブループリント）
> - `docs/architecture-blueprint/06-infrastructure-architecture.md`（インフラアーキテクチャ）
> - `docs/architecture-blueprint/04-backend-architecture.md`（バックエンドアーキテクチャ）
> - `docs/architecture-blueprint/03-frontend-architecture.md`（フロントエンドアーキテクチャ）
> - `docs/architecture-blueprint/08-common-infrastructure.md`（共通基盤）
> - `docs/architecture-blueprint/10-security-architecture.md`（セキュリティアーキテクチャ）
> - `docs/architecture-blueprint/13-non-functional-requirements.md`（非機能要件定義）
> - `docs/functional-requirements/00-authentication.md`（認証要件）
> - `docs/functional-requirements/06-batch-processing.md`（バッチ処理要件）
> - `docs/functional-requirements/07-interface.md`（外部連携要件）
> - `CLAUDE.md`（プロジェクト指針）

---

## エグゼクティブサマリー

本設計書は、WMS ShowCase プロジェクトの Azure ベースの監視・運用設計を包括的に定義している。ブループリント（11-monitoring-operations.md）で定義されたアラート4種を13種に拡充し、SLI/SLO定義、インシデント対応フロー、容量管理を新たに追加した。非機能要件定義との整合性を確認し、SLO値は非機能要件の要件値と一致させている。

全体として、小規模WMSプロジェクト（同時接続50名、Terraform Deploy/Destroy運用）に対して適切な監視深度と運用設計が行われている。過剰な複雑性を避けつつ、必要な可観測性を確保する方針はプロジェクトの規模に合致している。

---

## レビュー結果

| No | チェック観点 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|------------|----------------|---------|------|---------|
| 1 | ブループリントとの整合性 | 11-monitoring-operations.md（BP） | ブループリントのアラート4種（ERROR検知、API遅延、DB接続失敗、ログイン失敗）を全て包含し、13種に拡充。ヘルスチェック、バックアップ、DB運用、ログレベル運用、インシデント対応フローも全て引き継ぎ済み。整合性に問題なし | 確認 | OK |

> **対応完了** (2026-03-19): traceID 仕様を 32文字Hex に統一。11-monitoring-operations.md の「UUID v4」記述を「UUID v4ベースの32文字HexID」に修正
| 2 | 非機能要件との整合性（性能） | 13-non-functional-requirements.md | API応答時間（通常2秒/集計5秒）、バッチ30分以内、同時接続50名の要件値をSLO-002/003/005に反映済み。アラート閾値（API遅延 > 3秒）は非機能要件の3秒アラート基準と一致 | 確認 | OK |
| 3 | 非機能要件との整合性（可用性） | 13-non-functional-requirements.md | 目標稼働率99.5%をSLO-001に反映。RTO/RPO値（フロント/API: 数分、DB: 数時間、RPO: 最大1時間）はインシデント対応設計に反映 | 確認 | OK |
| 4 | セキュリティ要件との整合性 | 10-security-architecture.md | ログイン失敗多発アラート（A-004）、アカウントロック検知（A-013）を設計。認証ログ（成功/失敗/ロック）の監視をWorkbookに反映。PIIマスキングは共通基盤への参照で対応 | 確認 | OK |
| 5 | バッチ処理の監視 | 06-batch-processing.md（FR） | バッチ処理失敗アラート（A-010）を設計。日替処理の5ステップの実行状況をログ監視する設計。インシデントシナリオ4でバッチ失敗時の対応手順も定義済み | 確認 | OK |
| 6 | Terraform Deploy/Destroy運用との整合性 | 06-infrastructure-architecture.md | Destroyしない常設リソースにLog Analytics WorkspaceとApplication Insightsを明示していない点は要検討。Destroy時にログが失われる可能性がある | 改善提案 | 未対応（注記1） |

> **対応完了** (2026-03-19): Q2方針決定により、Log Analytics Workspace / Application Insights を環境と同居させ Terraform Destroy 時に一緒に削除する方針を確定。06-infrastructure-architecture.md および 11-monitoring-operations.md に反映
| 7 | SSOTルールの遵守 | CLAUDE.md | 技術方針はブループリントへの参照リンクで示し、閾値・設定値の複製を最小限にしている。ログフォーマットやPIIマスキングは08-common-infrastructure.mdへの参照で対応 | 確認 | OK |
| 8 | Application Insights SDK | 04-backend-architecture.md | `azure-spring-boot-starter-monitor` を指定しているが、2025年以降は `azure-monitor-spring-boot-starter`（Azure Monitor OpenTelemetry distro）が推奨。実装時に最新のSDK名を確認すること | 情報 | 注記のみ |
| 9 | フロントエンドの監視 | 03-frontend-architecture.md | フロントエンド（Blob Storage上の静的サイト）の監視はApplication Insightsのブラウザテレメトリで対応可能だが、本設計書では明示的に記載していない。MVP段階ではバックエンド監視を優先し、フロントエンド監視は将来課題とする方針は妥当 | 情報 | 将来課題 |
| 10 | コスト影響 | 06-infrastructure-architecture.md | Log Analytics無料枠5GB/月に対し、見積もり2〜4GB/月で収まる想定。DEBUGレベル変更時の注意事項も記載済み。Azure Monitor アラートの無料枠（1000ルール/月）に対して13ルールで十分余裕あり | 確認 | OK |
| 11 | 外部連携I/Fの監視 | 07-interface.md | CSV取り込み処理のエラー監視について、アプリケーションERRORログ検知（A-001）でカバーされる。取り込み失敗の専用アラートは設定していないが、業務画面（取り込み履歴）で確認可能であり、MVP段階では十分 | 情報 | 将来課題 |
| 12 | DB接続プール監視 | 08-common-infrastructure.md | HikariCP接続プール使用数をメトリクスとして定義しているが、アラート一覧には含まれていない。接続プール枯渇はDB接続エラー（A-003）として検知される設計。明示的なプール監視アラートは現時点では不要 | 確認 | OK |
| 13 | prd Multi-Region監視 | 06-infrastructure-architecture.md | prd環境のJapan East / Japan West 両リージョンの監視設計が含まれている（Front Doorエラー率アラートA-012、Geo-redundant backup）。フェイルオーバー検知はFront Doorヘルスプローブで自動化 | 確認 | OK |

---

## 注記

### 注記1: Terraform Destroy時の監視リソース — ✅ 方針確定（2026-03-19）

Destroyしない常設リソースとして、ブループリント（06-infrastructure-architecture.md）ではACRとTerraform state用Blobのみが定義されている。Log Analytics WorkspaceやApplication InsightsをDestroy対象にした場合、過去のログデータが失われる。

**推奨**: 監視リソース（Log Analytics Workspace, Application Insights）のDestroy/常設の方針を、インフラアーキテクチャ設計時に明確化すること。無料枠内であれば常設にするコスト影響はほぼゼロである。

> **対応完了** (2026-03-19): Q2方針決定により、Log Analytics / App Insights は環境と同居させ Destroy 時に一緒に削除する方針に確定。開発環境のログは一時的なもので永続保持不要と判断。

---

## 総合評価

| 評価項目 | 評価 |
|---------|------|
| ブループリントとの整合性 | 良好 |
| 非機能要件のカバー率 | 良好 |
| 運用実行可能性 | 良好 |
| 設計の具体性（閾値・設定値） | 良好 |
| SSOTルール遵守 | 良好 |
| 将来拡張性 | 良好 |

**判定: 承認（軽微な改善提案あり）**

改善提案（注記1）はインフラ設計フェーズで検討する事項であり、本設計書の承認をブロックするものではない。
