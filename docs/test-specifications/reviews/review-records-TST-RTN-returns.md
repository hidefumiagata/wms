# レビュー記録票: TST-RTN-returns.md

**レビュー実施日**: 2026-03-20
**対象ファイル**: `docs/test-specifications/TST-RTN-returns.md`
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 4 |
| 要対応（他ドキュメント） | 2 |
| 指摘なし | 5 |
| **合計** | **11** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | SC-001 DB検証 #1: `slip_number` | data-model/03-transaction-tables.md（return_slipsテーブル） | `return_slips` テーブルの伝票番号カラムは `return_number` であるが、テスト仕様書では `slip_number` と記載されていた | 対象ファイル修正済み | `slip_number` → `return_number` に修正 |
| 2 | SC-006 テストステップ #2: `totalElements=4` | functional-design/API-10-report.md（API-RPT-018） | API-RPT-018はJSON配列を返すAPIであり、ページングレスポンス（`totalElements`フィールド）ではない。ページングレスポンスを返すのはAPI-RTN-002（返品一覧取得）の方 | 対象ファイル修正済み | 確認方法を `totalElements=4` → `JSON配列の要素数=4` に修正。ステップ1にAPIパスを追記 |
| 3 | SC-007 テストステップ #3 | functional-design/SCR-14-returns.md（イベント一覧 EVT-RTN001-007〜008） | 在庫超過エラー（MSG-E-RTN001-007）はサーバーサイドで返されるエラーバナーであり、確認ダイアログ[OK]クリック後のAPI応答として表示される。テストステップに確認ダイアログのOKクリックが欠落していた | 対象ファイル修正済み | 「[登録]」の後に「→確認ダイアログ [OK]」を追加。また返品理由の入力も欠落していたため追記 |
| 4 | SC-008 テストステップ #2 / SC-009 テストステップ #2 | functional-design/SCR-14-returns.md（メッセージ一覧） | SC-007と同様、引当済みエラー（MSG-E-RTN001-008）・棚卸ロックエラー（MSG-E-RTN001-009）もサーバーサイドのエラーバナーであり、確認ダイアログ[OK]後に表示される。テストステップに確認ダイアログのOKクリックが欠落していた | 対象ファイル修正済み | 「[登録]」の後に「→確認ダイアログ [OK]」を追加。返品理由の入力も追記 |
| 5 | SC-006全体 | functional-design/API-10-report.md（API-RPT-018 リクエスト仕様） | API-RPT-018の`returnType`クエリパラメータの説明に`INBOUND` / `OUTBOUND`のみ記載されており、`INVENTORY`（在庫返品）が含まれていない。SC-006のテストデータには在庫返品1件が含まれるが、API側で`INVENTORY`がフィルタリング対象として定義されていない | 要対応（他ドキュメント） | `docs/functional-design/API-10-report.md` のAPI-RPT-018 `returnType`パラメータ説明に `INVENTORY` を追加する必要あり |
> **対応完了** (2026-03-20): API-10-report.mdのAPI-RPT-018 returnTypeパラメータにINVENTORYを追加完了
| 6 | SC-002 DB検証 #3 / データモデル | data-model/03-transaction-tables.md（return_slipsテーブル `return_reason` 定義） | `return_slips.return_reason` のコメントで `EXCESS_QUANTITY` の日本語名が「過剰入荷」と記載されているが、機能要件定義書（08-returns.md）およびAPI設計書（API-13-returns.md）では「数量過剰」と定義されている。SSOTに従えばAPI設計書の「数量過剰」が正 | 要対応（他ドキュメント） | `docs/data-model/03-transaction-tables.md` の `return_slips.return_reason` コメントを「過剰入荷」→「数量過剰」に修正する必要あり |
> **対応完了** (2026-03-20): data-model/03-transaction-tables.mdのreturn_slips.return_reasonのEXCESS_QUANTITY説明を「数量過剰」に修正完了
| 7 | SC-001〜SC-014 全体構成 | functional-requirements/08-returns.md | テストシナリオが機能要件の業務ルール（1伝票1商品、即時減算、引当保護、棚卸ロック保護、在庫マイナス禁止、入荷/出荷返品の在庫不変）を網羅していることを確認した | 指摘なし | — |
| 8 | SC-001〜SC-003 伝票番号形式 | functional-design/API-13-returns.md（伝票番号採番ルール） | 入荷返品=RTN-I-、在庫返品=RTN-S-、出荷返品=RTN-O- のプレフィックスがAPI設計書の採番ルールと一致 | 指摘なし | — |
| 9 | SC-005 返品理由コード全6種 | functional-design/API-13-returns.md（返品理由コード定義 SSOT） | QUALITY_DEFECT / EXCESS_QUANTITY / WRONG_DELIVERY / EXPIRED / DAMAGED / OTHER の全6種がテストされており、SSOTの定義と一致 | 指摘なし | — |
| 10 | SC-013 メッセージID | functional-design/SCR-14-returns.md（メッセージ一覧） | 必須項目未入力のバリデーションメッセージID（MSG-E-RTN001-001〜006, 010）が画面設計書のメッセージ一覧と一致 | 指摘なし | — |
| 11 | SC-012 ロット・期限管理 | functional-design/SCR-14-returns.md（画面項目 RTN-LOT, RTN-EXPIRY） / data-model/03-transaction-tables.md（return_slips.lot_number, expiry_date） | ロット番号・賞味期限フィールドの表示条件（lot_manage_flag, expiry_manage_flag）およびDB保存カラムが設計書・データモデルと一致 | 指摘なし | — |
