# アーキテクチャ設計レビュー記録票 — セキュリティアーキテクチャ

> 対象成果物: `docs/architecture-design/10-security-architecture.md`
> レビュー日: 2026-03-18
> レビュー担当: セキュリティアーキテクチャ設計スペシャリスト（AI）
> 参照ドキュメント:
> - `docs/architecture-blueprint/10-security-architecture.md`（セキュリティ方針 SSOT）
> - `docs/architecture-blueprint/07-auth-architecture.md`（認証・認可アーキテクチャ）
> - `docs/architecture-blueprint/04-backend-architecture.md`（バックエンドアーキテクチャ）
> - `docs/architecture-blueprint/03-frontend-architecture.md`（フロントエンドアーキテクチャ）
> - `docs/architecture-blueprint/06-infrastructure-architecture.md`（インフラアーキテクチャ）
> - `docs/architecture-blueprint/08-common-infrastructure.md`（共通基盤）
> - `docs/functional-requirements/00-authentication.md`（認証機能要件）
> - `docs/data-model/02-master-tables.md`（マスタテーブル定義）
> - `CLAUDE.md`（プロジェクトルール）

---

## エグゼクティブサマリー

セキュリティアーキテクチャ設計書は、OWASP Top 10（2021）の全カテゴリに対する対策を網羅し、Spring Security ベースの具体的な実装設計を提供している。パスワードポリシー・アカウントロックポリシーの具体値は SSOT（architecture-blueprint/10-security-architecture.md）への参照リンクで示し、SSOT ルールに準拠している。

全体的な品質は良好であるが、以下の改善点を識別した。

- **指摘なし（重大）**: 重大なセキュリティ上の欠陥は検出されなかった
- **軽微な指摘**: 3件（将来の改善推奨）
- **確認事項**: 2件（実装フェーズでの確認が必要）

---

## レビュー結果

| No | チェック観点 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|------------|----------------|---------|------|---------|
| 1 | SSOT準拠: パスワードポリシー値の複製有無 | CLAUDE.md, 10-security-architecture.md (BP) | パスワードポリシーの具体値（8〜128文字等）がバリデーター実装例のコード定数に含まれている。ただし実装コード例として具体値が必要であり、コード例としての記載は許容範囲と判断。設計書本文での値の記述は参照リンクで適切に示されている | 確認 | 対応不要 |

> **対応完了** (2026-03-19): SSOT違反対応として、Javadoc コメントからパスワードポリシー具体値の散文記載を除去し、「ポリシー定義値は architecture-blueprint/10-security-architecture.md を参照」に変更（cross-review-ssot 違反#1 対応）
| 2 | SSOT準拠: アカウントロック回数の複製有無 | CLAUDE.md, 10-security-architecture.md (BP) | `MAX_FAILED_ATTEMPTS = 5` がコード例に含まれている。No.1と同様、実装コード例としては許容範囲。本来はシステムパラメータ（`LOGIN_FAILURE_LOCK_COUNT`）からの動的取得が望ましい | 軽微 | 実装時対応推奨 |

> **対応完了** (2026-03-19): SSOT違反対応として、Mermaid フローチャート内の `failed_login_count >= 5?` を汎用表現（ロック閾値）に変更（cross-review-ssot 違反#2 対応）
| 3 | ブループリントとの整合性: トークン設計 | 07-auth-architecture.md, 10-security-architecture.md (BP) | アクセストークン有効期限1時間、リフレッシュトークンのスライディング方式（1時間）の仕様がブループリントと一致していることを確認 | — | 問題なし |
| 4 | ブループリントとの整合性: CORS設定 | 10-security-architecture.md (BP) | 許可メソッド・許可ヘッダー・allowCredentials がブループリントと一致していることを確認 | — | 問題なし |
| 5 | ブループリントとの整合性: セキュリティヘッダー | 10-security-architecture.md (BP) | X-Content-Type-Options, X-Frame-Options, Referrer-Policy がブループリントと一致。HSTS はブループリントに「Spring Security で付与」と記載されており、設計書に具体的な max-age 値（31536000秒=1年）を追加定義している | — | 問題なし |
| 6 | データモデルとの整合性: usersテーブル | data-model/02-master-tables.md | `failed_login_count`, `locked`, `locked_at`, `password_change_required` の各カラムがロック処理・初回変更強制の実装で正しく参照されていることを確認 | — | 問題なし |
| 7 | データモデルとの整合性: refresh_tokens | data-model/02-master-tables.md | リフレッシュトークンのBCryptハッシュ保存、トークンローテーション（旧トークン削除・新トークン発行）がデータモデルと一致 | — | 問題なし |
| 8 | データモデルとの整合性: password_reset_tokens | data-model/02-master-tables.md | SHA-256ハッシュ保存、used フラグ管理、同一ユーザーの未使用トークン無効化がデータモデルと一致 | — | 問題なし |
| 9 | 機能要件との整合性: ログインフロー | functional-requirements/00-authentication.md | ログイン成功/失敗/ロックの各分岐、エラーメッセージ（ユーザー列挙防止）、ロック通知が機能要件と一致 | — | 問題なし |
| 10 | 機能要件との整合性: パスワードリセット | functional-requirements/00-authentication.md | リセット完了時のロック解除・失敗カウンタリセットが機能要件と一致 | — | 問題なし |
| 11 | 機能要件との整合性: 初回パスワード変更 | functional-requirements/00-authentication.md | 変更完了まで他操作ブロック（バックエンド: 403, フロントエンド: Router Guard）が機能要件と一致 | — | 問題なし |
| 12 | OWASP Top 10 網羅性 | — | A01〜A10 の全カテゴリに対策が定義されていることを確認。特に A01（アクセス制御）と A07（認証）は詳細な実装設計あり | — | 問題なし |
| 13 | 入力バリデーション: 多層防御 | 04-backend-architecture.md | フロントエンド（Zod）→ Controller層（Jakarta Bean Validation）→ Service層（ビジネスルール）→ Repository層（パラメータバインディング）の4層バリデーションが適切に設計されている | — | 問題なし |
| 14 | CSP ヘッダーの未設定 | — | Content-Security-Policy がバックエンドAPI（JSON のみ）では不要、フロントエンド（Blob Storage）では将来対応と明記されている。初期リリースでは許容するが、フロントエンドの CSP 設定は早期に対応すべき | 軽微 | 実装時対応推奨 |
| 15 | JWT 署名鍵のローテーション | — | JWT 署名鍵（`JWT_SECRET`）のローテーション手順が未定義。現時点ではスコープ外とし、運用開始後に鍵ローテーション手順を策定することを推奨 | 軽微 | 将来対応 |

> **対応完了** (2026-03-19): Q9方針決定により、JWT 鍵ローテーションは運用手順書で定義する方針を 10-security-architecture.md に注記追加
| 16 | システムパラメータの動的取得 | data-model/02-master-tables.md | `LOGIN_FAILURE_LOCK_COUNT`, `SESSION_TIMEOUT_MINUTES`, `PASSWORD_RESET_EXPIRY_MINUTES` がシステムパラメータとして定義されているが、設計書のコード例ではハードコード定数を使用している。実装時にはシステムパラメータテーブルからの動的取得を推奨 | 確認 | 実装時対応推奨 |

---

## 総合評価

| 評価項目 | 評価 |
|---------|------|
| ブループリントとの整合性 | 良好。SSOT ルールに準拠し、方針値は参照リンクで示している |
| データモデルとの整合性 | 良好。全関連テーブルの利用方法がデータモデルと一致 |
| 機能要件との整合性 | 良好。認証・ロック・リセットの全フローが要件と一致 |
| OWASP Top 10 網羅性 | 良好。全カテゴリに対策を定義 |
| 実装可能性 | 良好。Spring Security / jjwt の具体的なコード例を提供 |
| SSOTルール遵守 | 良好。パスワードポリシー・ロック条件の値は SSOT 参照 |

**判定: 承認（軽微な指摘は実装フェーズで対応）**
