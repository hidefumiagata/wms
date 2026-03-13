# 画面設計レビュー記録票

> 作成日: 2026-03-13
> 対象: 全画面仕様書（02〜07）および HTMLモックアップ（全45画面）
> レビュー方針: 画面仕様側の誤りは即時修正。要件定義書側の修正が必要な場合は本票に記録してユーザーレビューを待つ。

---

## エグゼクティブサマリー

| 分類 | 件数 |
|------|------|
| **要件定義書修正要** | **22件** |
| **うち対応済** | **21件** |
| **保留（API仕様書作成時に対応）** | **1件**（#14） |
| **画面仕様修正済**（レビュー時に自動修正） | **6件** |
| 指摘事項なし | 69件 |
| **総チェック項目** | **97件** |

### 要件定義書修正要の対象ファイル別内訳

| 対象ファイル | 件数 |
|------------|------|
| `docs/functional-requirements/01-master-management.md` | 9件 |
| `docs/functional-requirements/02-inbound-management.md` | 4件 |
| `docs/functional-requirements/03-inventory-management.md` | 6件 |
| `docs/functional-requirements/04-outbound-management.md` | 1件 |
| `docs/architecture-blueprint/10-security-architecture.md` | 1件 |
| `docs/functional-design/01-screen-overview.md` | 1件 |

---

## 凡例

| 分類 | 説明 |
|------|------|
| `画面仕様修正済` | 画面仕様書・HTMLモックアップ側の誤りを検出し、即時修正して対応済み |
| `要件定義書修正要` | 要件定義書側への追記・修正が必要。ユーザーレビュー後に対応する |
| `指摘事項なし` | 整合性に問題なし |

---

## グループ別レビュー記録

---

### AUTH — 認証（AUTH-002）

対象成果物: `02-screen-auth.md` / `AUTH-002-change-password.html`
参照ドキュメント: `docs/architecture-blueprint/10-security-architecture.md`、`03-frontend-architecture.md`

| No | 対象画面 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|---------|----------------|---------|------|---------|
| A-1 | AUTH-002 | `10-security-architecture.md` パスワード管理 | パスワードポリシー「8文字以上」が画面仕様のバリデーション・メッセージと整合 | 指摘事項なし | — |
| A-2 | AUTH-002 | `10-security-architecture.md` 初回フラグ | `password_change_required` フラグの管理をPiniaストアで行う設計が整合 | 指摘事項なし | — |
| A-3 | AUTH-002 | `10-security-architecture.md` | `POST /api/v1/auth/change-password` エンドポイントが画面仕様に正しく記載 | 指摘事項なし | — |
| A-4 | AUTH-002 | `07-auth-architecture.md` | 対象ロール「全ロール」と認証アーキテクチャが整合 | 指摘事項なし | — |
| A-5 | AUTH-002 | `13-non-functional-requirements.md` | ナビゲーションガードによる迂回防止設計が非機能要件と整合 | 指摘事項なし | — |
| A-6 | AUTH-002 | `13-non-functional-requirements.md` | BCryptハッシュはバックエンド実装の関心事であり画面設計への影響なし | 指摘事項なし | — |
| A-7 | AUTH-002 | `03-frontend-architecture.md` フォームバリデーション | 画面仕様に「Element Plus フォームバリデーション」と記述していたが「VeeValidate + Zod」が確定技術のため修正 | **画面仕様修正済** | `02-screen-auth.md` メッセージ一覧の実装備考を「VeeValidate + Zod」に修正済 |
| A-8 | AUTH-002 | `03-frontend-architecture.md` | 401エラー時のリダイレクト処理が画面仕様のイベント一覧と整合 | 指摘事項なし | — |
| A-9 | AUTH-002 | `10-security-architecture.md` ログイン失敗ロック | `POST /api/v1/auth/change-password` の連続失敗に対するロックポリシーが未定義 | **要件定義書修正要** | `10-security-architecture.md` の「パスワード管理」テーブルにパスワード変更API連続失敗ロックポリシーを追記すること |
| A-10 | AUTH-002 | `13-non-functional-requirements.md` | 多言語対応（vue-i18n）は「日本語で作成→後で英語翻訳」の方針であり問題なし | 指摘事項なし | — |

---

### MST-商品 — 商品マスタ（MST-001/002/003）

対象成果物: `03-screen-master-product.md` / `MST-001〜003.html`
参照ドキュメント: `docs/functional-requirements/01-master-management.md` セクション1

| No | 対象画面 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|---------|----------------|---------|------|---------|
| MP-1 | MST-001 | 01-master-management.md 機能一覧 | 無効化/有効化ボタンが一覧操作列に設計済みで整合 | 指摘事項なし | — |
| MP-2 | MST-001 | 01-master-management.md ビジネスルール | 無効化確認ダイアログに業務影響（入荷・出荷・棚卸の新規登録で選択不可）を明示済み | 指摘事項なし | — |
| MP-3 | MST-001 | 01-master-management.md | 有効/無効絞り込みの初期値「有効のみ」は業務上妥当。要件に初期値の明示なし | 指摘事項なし | — |
| MP-4 | MST-001 | 01-master-management.md 管理項目 | 一覧の表示列に「賞味/使用期限管理フラグ」「出荷禁止フラグ」を含めない設計とした。全フラグを一覧で確認するか否かの方針が未明記 | **要件定義書修正要** | `01-master-management.md` §1 機能一覧「商品一覧照会」に商品一覧の表示列範囲を明記すること |
| MP-5 | MST-002 | 01-master-management.md「カテゴリ」 | カテゴリの選択肢・管理方式（固定値 or 別マスタ）が未定義。暫定で「飲料/食品/日用品/その他」を定義 | **要件定義書修正要** | `01-master-management.md` §1 管理項目にカテゴリの入力方式（固定値 or 別マスタ）と選択肢を定義すること |
| MP-6 | MST-002 | 01-master-management.md「単位」 | 単位の入力方式（自由入力 or セレクト）が未定義。自由テキスト入力で設計したが運用一貫性の観点でセレクト方式が望ましい可能性あり | **要件定義書修正要** | `01-master-management.md` §1 管理項目に単位の入力方式を明記すること |
| MP-7 | MST-002/003 | 01-master-management.md ビジネスルール | 商品コードの重複チェック（フォーカスアウト時＋登録時）を設計済み。整合 | 指摘事項なし | — |
| MP-8 | MST-003 | 01-master-management.md | 商品コードをreadonly＋「変更不可」バッジで表示。整合 | 指摘事項なし | — |
| MP-9 | MST-003 | 01-master-management.md | 在庫存在時のロット管理フラグdisabled化・警告バッジ・ツールチップを設計済み。整合 | 指摘事項なし | — |
| MP-10 | 全画面 | 01-master-management.md アクセス権限 | 全画面の対象ロールをSYSTEM_ADMINのみとし、サイドバー仕様と整合 | 指摘事項なし | — |
| MP-11 | MST-002/003 | 01-master-management.md「ケース入数」 | フォームヒントに正確な定義（1ケースに含まれるボール数）を反映済み | 指摘事項なし | — |
| MP-12 | MST-001 | 01-master-management.md 共通設計方針 | CSVインポートボタンを除外済み（CSVインポート不要の方針と整合） | 指摘事項なし | — |

---

### MST-取引先・倉庫 — 取引先・倉庫マスタ（MST-011〜023）

対象成果物: `03-screen-master-partner-warehouse.md` / `MST-011〜023.html`
参照ドキュメント: `docs/functional-requirements/01-master-management.md` セクション2・3

| No | 対象画面 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|---------|----------------|---------|------|---------|
| MPW-1 | MST-011 | 01-master-management.md §2 機能一覧 | 無効化/有効化ボタンを一覧操作列に設計済み。整合 | 指摘事項なし | — |
| MPW-2 | MST-011 | 01-master-management.md §2 管理項目 | 担当者名・メールアドレスを一覧から省略。詳細確認は編集画面で可能。業務上問題なし | 指摘事項なし | — |
| MPW-3 | MST-011 | 01-master-management.md §2 ビジネスルール | 無効化確認ダイアログに「入荷・出荷登録で選択不可になります」を明示済み | 指摘事項なし | — |
| MPW-4 | MST-012/013 | 01-master-management.md §2 | 取引先コード登録後変更不可のルールをフォームヒント・readonly化で明示。整合 | 指摘事項なし | — |
| MPW-5 | MST-012 | 01-master-management.md §2 ビジネスルール | 種別ごとの入荷元/出荷先選択可否ルールをフォームヒントに補足。整合 | 指摘事項なし | — |
| MPW-6 | MST-012 | 01-master-management.md §2 管理項目 | 取引先コードのフォーマット規則（文字種・最大長）が要件定義書に未記載 | **要件定義書修正要** | `01-master-management.md` §2 管理項目に取引先コードのフォーマット規則を追記すること |
| MPW-7 | MST-013 | 01-master-management.md §2 | ページタイトル付近に有効/無効の現在状態をバッジ表示。妥当な設計 | 指摘事項なし | — |
| MPW-8 | MST-021 | 01-master-management.md §3 ビジネスルール | 在庫存在時の無効化不可ルールをdisabledボタン＋ツールチップで明示。整合 | 指摘事項なし | — |
| MPW-9 | MST-021 | 01-master-management.md §3 | エラーメッセージMSG-E-041を定義済み。APIエラーレスポンスはバックエンド設計書に委ねる | 指摘事項なし | — |
| MPW-10 | MST-022 | 01-master-management.md §3 管理項目 | 倉庫登録時の有効フラグを「ON固定」とした（登録直後に無効化するケースは想定外）が、要件定義書に明示なし | **要件定義書修正要** | `01-master-management.md` §3（および全マスタ共通）に「登録時の有効フラグはON固定」を追記すること |
| MPW-11 | MST-022 | 01-master-management.md §3 管理項目 | 倉庫コードのフォーマット規則が未記載 | **要件定義書修正要** | `01-master-management.md` §3 管理項目に倉庫コードのフォーマット規則を追記すること |
| MPW-12 | MST-023 | 01-master-management.md §3 | 編集画面でも在庫の有無をinfo-noteで通知する設計。妥当 | 指摘事項なし | — |
| MPW-13 | MST-021〜023 | 01-master-management.md 共通設計方針 | CSVインポートボタンを除外済み。整合 | 指摘事項なし | — |
| MPW-14 | 全画面 | 01-screen-overview.md 共通仕様 | 倉庫マスタ管理画面は全倉庫を管理する性質上、ヘッダーの倉庫切替の影響を受けないが、この仕様が明文化されていない | **要件定義書修正要** | `01-master-management.md` §3 または `01-screen-overview.md` 共通仕様に「倉庫マスタ管理画面はヘッダーの倉庫切替の影響を受けない（全倉庫一覧を表示する）」旨を明記すること |
| MPW-15 | 全画面 | 01-screen-overview.md テンプレート | 5セクション構成に完全準拠 | 指摘事項なし | — |
| MPW-16 | 全画面 | 01-master-management.md アクセス権限 | 全画面のロールがSYSTEM_ADMINのみであることとサイドバー仕様が整合 | 指摘事項なし | — |

---

### MST-棟・エリア・ロケーション（MST-031〜053）

対象成果物: `03-screen-master-facility.md` / `MST-031〜053.html`
参照ドキュメント: `docs/functional-requirements/01-master-management.md` セクション4・5・6

| No | 対象画面 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|---------|----------------|---------|------|---------|
| MF-1〜9 | MST-031〜053 | 01-master-management.md §4・5・6 | 棟/エリア/ロケーション各画面の設計が要件定義と全項目整合していることを確認。コード登録後変更不可ルール、配下エンティティ存在時の無効化不可ルール、ロケーションコードフォーマット（`棟-フロア-エリア-棚-段-並び`）の自動補完UIも含め問題なし | 指摘事項なし | — |

> レビュー指摘事項なし（9画面全て整合確認済み）

---

### MST-ユーザー（MST-061/062/063）

対象成果物: `03-screen-master-user.md` / `MST-061〜063.html`
参照ドキュメント: `docs/functional-requirements/01-master-management.md` セクション7、`docs/architecture-blueprint/07-auth-architecture.md`、`10-security-architecture.md`

| No | 対象画面 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|---------|----------------|---------|------|---------|
| MU-1 | MST-061 | 01-master-management.md §7 機能一覧 | 「ユーザー無効化/有効化」が独立機能として記載されているが、画面設計では編集画面（MST-063）のステータス変更で実現する方針とした。要件定義書に補足が必要 | **要件定義書修正要** | `01-master-management.md` §7 機能一覧に「有効/無効の切り替えは編集画面のステータスフィールドから行う」旨を補足すること |
| MU-2 | MST-061 | `10-security-architecture.md` ログイン失敗ロック | 一覧からの直接ロック解除ボタン＋編集画面のアラートバナーからも解除できる設計。整合 | 指摘事項なし | — |
| MU-3 | MST-062 | `10-security-architecture.md` パスワード管理 | 初期パスワードポリシー「8文字以上」をフォームヒント・バリデーションに反映。BCryptはバックエンド処理 | 指摘事項なし | — |
| MU-4 | MST-062 | 01-master-management.md §7 ビジネスルール | 「SYSTEM_ADMINが初期パスワードを設定する」要件を登録フォームに初期パスワード欄として設計。整合 | 指摘事項なし | — |
| MU-5 | MST-062 | `10-security-architecture.md` 初回ログイン | 初回ログインフラグはバックエンド自動設定のため登録フォームには表示不要。問題なし | 指摘事項なし | — |
| MU-6 | MST-063 | 01-master-management.md §7 ビジネスルール | 自分自身のロール変更・無効化不可ルールをdisabled制御で仕様化。API側にも同様チェックが必要な旨を備考に明記 | 指摘事項なし | — |
| MU-7 | MST-063 | `07-auth-architecture.md` ロール定義 | 4ロールの定義がアーキテクチャと一致 | 指摘事項なし | — |
| MU-8 | MST-061 | 01-screen-overview.md 共通仕様 | CSVインポートボタンを除外済み。整合 | 指摘事項なし | — |
| MU-9 | MST-061 | 01-master-management.md §7 管理項目 | メールアドレスを一覧に表示する設計（管理者の連絡先確認用途として妥当） | 指摘事項なし | — |
| MU-10 | MST-062 | 01-master-management.md §7 | 「初回ログインフラグ」と`password_change_required`の用語統一が保たれている | 指摘事項なし | — |
| MU-11 | 全画面 | `07-auth-architecture.md` 機能別アクセス権限マトリクス | ユーザー管理はSYSTEM_ADMINのみ。サイドバー仕様と整合 | 指摘事項なし | — |

---

### INB — 入荷管理（INB-002〜006）

対象成果物: `04-screen-inbound.md` / `INB-002〜006.html`
参照ドキュメント: `docs/functional-requirements/02-inbound-management.md`

| No | 対象画面 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|---------|----------------|---------|------|---------|
| I-1 | INB-002 | 02-inbound-management.md §1 | 同一伝票内での同一商品コードの重複可否が未定義。画面仕様ではエラーとして弾く設計とした | **要件定義書修正要** | `02-inbound-management.md` §1 に「同一伝票内の商品コード重複は不可」を追記すること |
| I-2 | INB-002 | 02-inbound-management.md §1 | 入荷予定日の日付制約（過去日不可等）が未記載。画面仕様では「当日以降」として設計 | **要件定義書修正要** | `02-inbound-management.md` §1 に入荷予定日の日付制約（当日以降か否か）を明記すること |
| I-3 | INB-003 | 02-inbound-management.md §5 | キャンセル時の在庫戻し処理の発動条件（ステータス段階・在庫状態）が詳細化されていない | **要件定義書修正要** | `02-inbound-management.md` §5 にキャンセル時在庫戻しの発動条件（ステータスと在庫状態）を詳細化すること |
| I-4 | INB-003 | 02-inbound-management.md §5 | キャンセルボタンの表示条件を「入庫完了・キャンセル以外」に修正（キャンセル済への再キャンセル防止） | **画面仕様修正済** | `04-screen-inbound.md` INB003-BTN03の表示条件を「入庫完了・キャンセル以外」に修正済 |
| I-5 | INB-004 | 02-inbound-management.md §6 | 「最初の検品入力でステータスが検品中に遷移」→保存操作時（PUT APIコール時）に遷移する設計で整合 | 指摘事項なし | — |
| I-6 | INB-004 | 02-inbound-management.md §6 | 確定前の検品数修正可の要件を画面仕様で設計済み。整合 | 指摘事項なし | — |
| I-7 | INB-005 | 02-inbound-management.md §7 | 個別確定・全件一括確定の両方をボタンとして設計。整合 | 指摘事項なし | — |
| I-8 | INB-005 | 02-inbound-management.md §7 | ロケーション自動割当の優先順序を「★既存在庫あり」「○空きロケーション」として可視化。整合 | 指摘事項なし | — |
| I-9 | INB-006 | 02-inbound-management.md §8 | 照会の単位（伝票単位 or 明細単位）が未定義。明細単位で設計したため、同一伝票の複数明細が複数行表示される | **要件定義書修正要** | `02-inbound-management.md` §8 に照会の単位（伝票単位 or 明細単位）を明記すること |
| I-10 | INB-006 | 02-inbound-management.md §8 | 予定数量・検品数・差異数の3列表示が要件と整合 | 指摘事項なし | — |
| I-11 | 全画面 | 02-inbound-management.md ビジネスルール「修正不可」 | INB-002は新規登録専用、INB-003には編集ボタンを設けない設計で整合 | 指摘事項なし | — |
| I-12 | INB-002 | 02-inbound-management.md ビジネスルール「倉庫コードの保持」 | 入力フォームに倉庫フィールドを設けず、ヘッダー選択中倉庫コードをAPIリクエスト時に自動セットする想定。API設計時に仕様確認が必要 | **要件定義書修正要** | API仕様書作成時に「入荷予定登録APIは選択中倉庫コードを自動付与する」旨を記載すること |

---

### INV-在庫操作 — 在庫照会・移動・ばらし・訂正（INV-001〜004）

対象成果物: `05-screen-inventory-ops.md` / `INV-001〜004.html`
参照ドキュメント: `docs/functional-requirements/03-inventory-management.md`

| No | 対象画面 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|---------|----------------|---------|------|---------|
| IO-1 | INV-001 | 03-inventory-management.md §1 | 「ロケーション別」タブ＋「商品合計」タブの2モード設計が要件の両要求を満たす | 指摘事項なし | — |
| IO-2 | INV-001 | 03-inventory-management.md §1 | バラ換算合計列を商品合計タブに追加したが、この要件が要件定義書に未記載 | **要件定義書修正要** | `03-inventory-management.md` §1 に「商品ごとの全荷姿バラ換算合計も表示できる」旨を追記すること |
| IO-3 | INV-001 | 03-inventory-management.md ビジネスルール「棚卸ロック」 | 棚卸ロック中ロケーションの視覚的表示（タグ等）が画面仕様に未定義 | **要件定義書修正要** | `03-inventory-management.md` または在庫一覧画面仕様に棚卸ロック中ロケーションの視覚表示要件を追加すること |
| IO-4 | INV-002 | 03-inventory-management.md §2 | 移動元在庫不足エラー・移動先ロケーション・商品・数量の設計が整合 | 指摘事項なし | — |
| IO-5 | INV-002 | 03-inventory-management.md §ロケーション収容制約 | 充填率プレビューバーをUX向上のため追加したが要件定義書に記載なし | **要件定義書修正要** | `03-inventory-management.md` §ロケーション収容制約に「在庫移動画面での移動後充填率プレビュー表示」要件を追記すること |
| IO-6 | INV-002 | 03-inventory-management.md ビジネスルール「ロケーション単一商品」 | 異なる商品の移動先エラーを設計済み。整合 | 指摘事項なし | — |
| IO-7 | INV-003 | 03-inventory-management.md §3 | ばらし方向制約（まとめは対象外）をグレーアウトで実現。整合 | 指摘事項なし | — |
| IO-8 | INV-003 | 03-inventory-management.md ビジネスルール「荷姿変換の自動計算」 | 端数エラーをリアルタイムチェックするプレビューカードで設計。整合 | 指摘事項なし | — |
| IO-9 | INV-003 | 03-inventory-management.md §3 | ばらし先ロケーションを任意入力（空欄=元と同一）で設計。整合 | 指摘事項なし | — |
| IO-10 | INV-004 | 03-inventory-management.md §4 | 訂正理由必須・履歴記録・削除修正不可をフォーム設計・警告バナーで表現。整合 | 指摘事項なし | — |
| IO-11 | INV-004 | 03-inventory-management.md §4 | RPT-009（訂正一覧レポート）呼出ボタンを配置済み。整合 | 指摘事項なし | — |
| IO-12 | INV-004 | 03-inventory-management.md ビジネスルール「在庫マイナス禁止」 | 在庫訂正への「訂正後数量は0以上」ルールが要件定義書のビジネスルール表に明示されていない | **要件定義書修正要** | `03-inventory-management.md` §ビジネスルール「在庫マイナス禁止」の対象に「在庫訂正（訂正後数量は0以上）」を追記すること |
| IO-13 | 全画面 | 01-screen-overview.md | 共通バリデーション仕様に準拠。整合 | 指摘事項なし | — |
| IO-14 | 全画面 | 01-screen-overview.md | 権限エラー（403/401）は共通仕様に委ねる設計。問題なし | 指摘事項なし | — |

---

### INV-棚卸 — 棚卸管理（INV-011〜014）

対象成果物: `05-screen-inventory-stocktake.md` / `INV-011〜014.html`
参照ドキュメント: `docs/functional-requirements/03-inventory-management.md`

| No | 対象画面 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|---------|----------------|---------|------|---------|
| IS-1 | INV-011〜014 | 03-inventory-management.md §5 | 棚卸4ステップフローが画面分割と整合（INV-013が実数入力、INV-014が差異確認+確定） | 指摘事項なし | — |
| IS-2 | INV-012 | 03-inventory-management.md §5 ステップ1 | 棚卸リスト帳票を棚卸開始前（対象ロケーション選択後）でも出力可能とする設計だが、要件に明示なし | **要件定義書修正要** | `03-inventory-management.md` §5 ステップ1 に「対象ロケーション選択後であれば棚卸開始前でも棚卸リストを出力できる」と追記すること |
| IS-3 | INV-013 | 03-inventory-management.md §在庫の管理単位 | 在庫の3軸（ロケーション×商品×荷姿）が棚卸実数入力テーブルに反映。整合 | 指摘事項なし | — |
| IS-4 | INV-013 | 03-inventory-management.md ビジネスルール「棚卸ロック」 | 棚卸ロックの案内を警告バナーで表示、確定時に自動解除する設計。整合 | 指摘事項なし | — |
| IS-5 | INV-013 | 01-screen-overview.md 共通バリデーション | 実数入力欄のバリデーション（0以上の整数）をMSGで定義。整合 | 指摘事項なし | — |
| IS-6 | INV-014 | 03-inventory-management.md ビジネスルール | 棚卸確定時の在庫更新とRPT-011（棚卸結果レポート）出力をまとめて設計。整合 | 指摘事項なし | — |
| IS-7 | INV-014 | 03-inventory-management.md ビジネスルール「トランへのマスタ情報コピー」 | トラン生成はバックエンドAPI側の関心事。フロントエンド画面仕様への影響なし | 指摘事項なし | — |
| IS-8 | INV-011 | 01-screen-overview.md 全画面一覧 | 画面ID・URL・対象ロールが一覧と一致 | 指摘事項なし | — |
| IS-9 | INV-012 | 01-screen-overview.md サイドバー | INV-012はWAREHOUSE_MANAGER専用。INV-011の「棚卸開始」ボタンをWAREHOUSE_MANAGERのみ表示する設計で対処済み | 指摘事項なし | — |
| IS-10 | INV-013 | 01-screen-overview.md 共通権限エラー | 403エラーは共通仕様に委ねる。問題なし | 指摘事項なし | — |
| IS-11 | INV-014 | 03-inventory-management.md §5 | 「差異ありのみ表示」フィルターはフロントエンド完結の設計。大規模棚卸での明細数上限が未定義 | **要件定義書修正要** | `03-inventory-management.md` §5 または非機能要件に一回の棚卸で扱う最大明細数の目安を定義すること |
| IS-12 | INV-013/014 | 03-inventory-management.md §荷姿の種類 | 荷姿3種（ケース/ボール/バラ）がテーブルに反映。整合 | 指摘事項なし | — |

---

### OUT — 出荷管理（OUT-001〜022）

対象成果物: `06-screen-outbound.md` / `OUT-001〜022.html`
参照ドキュメント: `docs/functional-requirements/04-outbound-management.md`

| No | 対象画面 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|---------|----------------|---------|------|---------|
| O-1 | OUT-002 | 04-outbound-management.md §2 倉庫振替登録 | 倉庫振替時に出荷伝票＋入荷伝票を同時生成する仕様の説明がイベント一覧に不足していた | **画面仕様修正済** | `06-screen-outbound.md` EVT-OUT-002-006に「倉庫振替時は振替先倉庫の入荷伝票を自動生成する」旨を追記済 |
| O-2 | OUT-001 | 04-outbound-management.md §5 在庫引当 | 引当時のばらし指示発行はINV-003のスコープ。本画面ではMSG-E-101で「在庫不足・荷姿不一致の場合はダイアログで詳細表示」として対応済み | 指摘事項なし | — |
| O-3 | OUT-011 | 04-outbound-management.md §6 ピッキング指示 | RPT-012（ピッキングリスト帳票）呼出ボタンをOUT-011に配置済み。整合 | 指摘事項なし | — |
| O-4 | OUT-013 | 04-outbound-management.md §6・§7 | ピッキング完了後に「どの受注の検品を行うか」を選択するフローが未定義。1ピッキング指示が複数受注を含む場合の検品遷移設計が必要 | **要件定義書修正要** | `04-outbound-management.md` §6 に「バッチピッキング後の受注別検品遷移フロー」を追記すること（案A: 対象受注リストダイアログ、案B: 受注グルーピング表示） |
| O-5 | OUT-021 | 04-outbound-management.md §7 出荷検品 | 途中保存と出荷確定への遷移を分離する設計が必要だった。MSG-S-107「検品数を保存しました」を追加して途中保存を許容 | **画面仕様修正済** | `06-screen-outbound.md` OUT-021にMSG-S-107を追加し途中保存フローを明示済 |
| O-6 | OUT-022 | 04-outbound-management.md §8・§9 | 出荷確定と配送情報入力を同一画面にまとめた設計（効率性の観点から妥当） | 指摘事項なし | — |
| O-7 | OUT-003 | 04-outbound-management.md §10 受注キャンセル | ピッキング中キャンセル時の警告メッセージMSG-W-104に「ピッキング指示から当該明細が除外されます」の補足が不足 | 画面仕様修正要（次版） | 次版でMSG-W-104の文言を補足修正すること |
| O-8 | OUT-012 | 01-screen-overview.md 画面ID体系 | ピッキング指示番号プレフィックス「PIC」が01-screen-overview.mdの画面ID体系表に未定義 | **要件定義書修正要** | `01-screen-overview.md` の画面ID体系表に「PIC: ピッキング指示（OUT内部エンティティの伝票番号体系）」を追記するか、データモデル側で管理方針を明記すること |
| O-9 | 全画面 | 01-screen-overview.md 共通バリデーション | 共通バリデーション仕様に準拠。整合 | 指摘事項なし | — |
| O-10 | OUT-022 | 04-outbound-management.md §9 | RPT-014（配送リスト）への導線をOUT-003にも追加するとUX向上につながる（現状はOUT-001のみ）。改善提案として記録 | 指摘事項なし | 改善提案 |

---

### BAT — バッチ管理（BAT-001/002）

対象成果物: `07-screen-batch.md` / `BAT-001〜002.html`
参照ドキュメント: `docs/functional-requirements/06-batch-processing.md`、`docs/functional-requirements/05-reports.md`

| No | 対象画面 | 参照ドキュメント | 指摘内容 | 分類 | 対応状況 |
|----|---------|----------------|---------|------|---------|
| B-1 | BAT-001 | 06-batch-processing.md §日替処理の内容 | 5ステップに加え「未入荷リスト・未出荷リストの自動生成」が日替処理に含まれるが実行結果カードに未反映だった | **画面仕様修正済** | `07-screen-batch.md` にBAT001-RESULT-UNINBOUND / UNOUTBOUND 項目を追加済 |
| B-2 | BAT-001 | 06-batch-processing.md §ビジネスルール | 「再実行時は最初から全処理を実行」のルールがモックアップの失敗時説明文に未反映だった | **画面仕様修正済** | HTMLモックアップ（BAT-001-daily-close.html）の失敗ブロックに再実行案内文を追記済 |
| B-3 | BAT-002 | 01-screen-overview.md §RPT-017 | RPT-017の呼出元がBAT-002であることが01-screen-overview.mdに明記済み。整合 | 指摘事項なし | — |
| B-4 | BAT-002 | 05-reports.md §17 日次集計レポート | レポート出力内容（入荷件数・数量、出荷件数・数量、在庫数量）が要件と整合。未入荷・未出荷件数も追加済み | 指摘事項なし | — |
| B-5 | BAT-001/002 | 06-batch-processing.md §実行方式「実行権限」 | 両画面のロール設定がSYSTEM_ADMIN・WAREHOUSE_MANAGERのみ。サイドバー仕様と整合 | 指摘事項なし | — |
| B-6 | BAT-001 | 06-batch-processing.md §ビジネスルール「処理中断」 | 失敗時の中断・ステップ強調表示をモックアップで設計済み。整合 | 指摘事項なし | — |
| B-7 | BAT-002 | 06-batch-processing.md §機能一覧 | エラー内容を詳細ドロワー（インライン展開）に格納する設計が要件趣旨と矛盾しない | 指摘事項なし | — |
| B-8 | BAT-001 | 06-batch-processing.md §日替処理の内容 | 1回の操作で5処理を実行する設計。整合 | 指摘事項なし | — |

> 要件定義書側の修正が必要な事項は検出されなかった

---

## 要件定義書修正要 — 一覧（ユーザーレビュー用）

| # | 修正対象ファイル | 修正箇所 | 修正内容 | 優先度 |
|---|----------------|---------|---------|-------|
| 1 | `docs/architecture-blueprint/10-security-architecture.md` | パスワード管理テーブル | ~~パスワード変更API（`POST /api/v1/auth/change-password`）の連続失敗ロックポリシーを追記~~ ログインと同一ポリシー（連続5回失敗でロック）を適用。ログイン失敗ロックセクションに追記済み | **対応済** |
| 2 | `docs/functional-requirements/01-master-management.md` | §1 機能一覧「商品一覧照会」 | ~~商品一覧の表示列範囲（全フラグを表示するか否か）を明記~~ 表示列を「商品コード・商品名・保管条件・危険物フラグ・ロット管理フラグ・有効/無効」と明記。賞味/使用期限・出荷禁止フラグは編集画面のみ | **対応済** |
| 3 | `docs/functional-requirements/01-master-management.md` | §1 管理項目「カテゴリ」 | ~~カテゴリの入力方式（固定値 or 別マスタ化）と選択肢を定義~~ カテゴリ項目を削除（不要と判断） | **対応済** |
| 4 | `docs/functional-requirements/01-master-management.md` | §1 管理項目「単位」 | ~~単位の入力方式（自由入力 or 選択式）を明記~~ 単位は荷姿（ケース/ボール/バラ）で代替するため項目自体を削除。01-master-management.md §1・02-master-tables.md products テーブルから `unit` カラムを削除済み | **対応済** |
| 5 | `docs/functional-requirements/01-master-management.md` | §2 管理項目 | ~~取引先コードのフォーマット規則（文字種・最大長）を追記~~ 「半角英数字・記号、50文字以内」を追記済み | **対応済** |
| 6 | `docs/functional-requirements/01-master-management.md` | §2・3（全マスタ共通） | ~~登録時の有効フラグ初期値（ON固定）を明記~~ 共通設計方針セクションに「全マスタ共通。新規登録時の有効フラグはON固定」を追記済み | **対応済** |
| 7 | `docs/functional-requirements/01-master-management.md` | §3 管理項目 | ~~倉庫コードのフォーマット規則を追記~~ 「英大文字4文字固定」を追記済み | **対応済** |
| 8 | `docs/functional-requirements/01-master-management.md` | §3 または `01-screen-overview.md` | ~~倉庫マスタ管理画面はヘッダーの倉庫切替の影響を受けない（全倉庫一覧を表示）旨を明記~~ §3 ビジネスルールに追記済み | **対応済** |
| 9 | `docs/functional-requirements/01-master-management.md` | §7 機能一覧 | ~~「ユーザー無効化/有効化」は編集画面のステータスフィールドで実現する旨を補足~~ 機能一覧に「切り替えはユーザー編集画面のステータスフィールドから行う」を追記済み | **対応済** |
| 10 | `docs/functional-requirements/02-inbound-management.md` | §1 入荷予定登録（手動） | ~~同一伝票内の商品コード重複不可を追記~~ | **対応済** |
| 11 | `docs/functional-requirements/02-inbound-management.md` | §1 入荷予定登録（手動） | ~~入荷予定日の日付制約（当日以降か否か）を明記~~ 業務日付以降に設定 | **対応済** |
| 12 | `docs/functional-requirements/02-inbound-management.md` | §5 入荷キャンセル | ~~キャンセル時の在庫戻し発動条件（ステータス段階・在庫状態）を詳細化~~ | **対応済** |
| 13 | `docs/functional-requirements/02-inbound-management.md` | §8 入荷実績照会 | ~~照会の単位（伝票単位 or 明細単位）を明記~~ 伝票単位（2段階表示）と確定。追記済み | **対応済** |
| 14 | （API仕様書） | 入荷予定登録API | 選択中倉庫コードを自動付与する仕様を記載（API仕様書作成時に対応） — **保留**（API仕様書作成フェーズで対応） | 低 |
| 15 | `docs/functional-requirements/03-inventory-management.md` | §1 在庫照会 | 商品ごとの全荷姿バラ換算合計も表示できる旨を追記 | 低 |
| 16 | `docs/functional-requirements/03-inventory-management.md` | §1（または画面仕様） | ~~棚卸ロック中ロケーションの在庫一覧での視覚表示要件を追記~~ §1 在庫照会に「ロックアイコンまたはバッジを付与」を追記済み | **対応済** |
| 17 | `docs/functional-requirements/03-inventory-management.md` | §ロケーション収容制約 | ~~在庫移動画面での充填率プレビュー表示要件を追記~~ ロケーション収容制約セクションに「在庫移動画面では移動後の充填率をプレビュー表示する」を追記済み | **対応済** |
| 18 | `docs/functional-requirements/03-inventory-management.md` | §ビジネスルール「在庫マイナス禁止」 | ~~在庫訂正への「訂正後数量は0以上」ルール適用を明示~~ ビジネスルール表の「在庫マイナス禁止」行に追記済み | **対応済** |
| 19 | `docs/functional-requirements/03-inventory-management.md` | §5 棚卸 ステップ1 | 棚卸開始前（対象ロケーション選択後）でも棚卸リストを出力できる旨を追記 | 低 |
| 20 | `docs/functional-requirements/03-inventory-management.md` | §5 または非機能要件 | ~~一回の棚卸で扱う最大明細数の目安（パフォーマンス設計の前提）を定義~~ 2,000行を上限として §5 に追記済み | **対応済** |
| 21 | `docs/functional-requirements/04-outbound-management.md` | §6 ピッキング指示作成 | ~~バッチピッキング後の受注別検品遷移フローを追記~~ OUT-013に対象受注一覧＋「出荷検品へ」ボタンで個別遷移する方式を追記 | **対応済** |
| 22 | `docs/functional-design/01-screen-overview.md` | 画面ID体系表 | ~~ピッキング指示番号プレフィックス「PIC」の位置付けを追記~~ 画面ID体系表に「PIC: ピッキング指示（OUT内部エンティティの伝票番号体系）」を追記済み | **対応済** |
