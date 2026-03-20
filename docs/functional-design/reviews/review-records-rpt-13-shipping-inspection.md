# レビュー記録票: RPT-13-shipping-inspection.md

**レビュー実施日**: 2026-03-18
**対象ファイル**: docs/functional-design/RPT-13-shipping-inspection.md
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 0 |
| 要対応（他ドキュメント） | 3 |
| 指摘なし | 3 |
| **合計** | **6** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | セクション1「用途」 | functional-requirements/05-reports.md #13 | 機能要件定義書では出荷検品レポートを「指定期間・出荷先・商品で絞り込んだ出荷検品実績の一覧を出力する」と定義しているが、RPT-13設計書およびAPI-RPT-013では1出荷伝票単位のレポートとして設計されている。要件は一覧出力（複数伝票横断）を想定しているが、設計は単伝票レポートになっている。設計判断として意図的であれば要件側を更新すべき。 | 要対応（他ドキュメント） | functional-requirements/05-reports.md の #13 を更新して「受注伝票単位で出荷検品結果を出力する」旨に修正するか、設計側を一覧レポート形式に変更する ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): functional-requirements/05-reports.md RPT-13対象定義を「受注伝票単位で出荷検品結果を出力」に修正済み
| 2 | セクション2.2 カラム#4 荷姿 | API-10-report.md API-RPT-013 レスポンス例 | RPT-13では `unitType` の表示値を `CASE/BALL/PIECE` としており、データモデル（outbound_slip_lines.unit_type）と一致している。しかしAPI-10-report.md のAPI-RPT-013レスポンスJSONサンプルでは `"unitType": "CAS"` と記載されており、コード値が不一致（`CAS` vs `CASE`）。API側のサンプルが誤り。 | 要対応（他ドキュメント） | API-10-report.md の API-RPT-013 レスポンスJSONサンプルの `"unitType": "CAS"` を `"unitType": "CASE"` に修正する ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-013 レスポンスJSONサンプルのunitType値をCASEに修正済み
| 3 | セクション1「対応API」 | API-10-report.md API-RPT-013 セクション1 | RPT-13では関連画面を `OUT-021`（出荷検品画面）としているが、API-10-report.md の API-RPT-013 では関連画面を `OUT-003（出荷検品画面）、RPT-001（レポート画面）` としている。SCR-10-outbound.md ではOUT-021の btn-report が RPT-013 を呼び出す設計になっており、RPT-13の記載（OUT-021）が正しい。API側の関連画面IDを修正すべき。 | 要対応（他ドキュメント） | API-10-report.md の API-RPT-013 関連画面を `OUT-021（出荷検品画面）` に修正する ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-013 関連画面をOUT-021（出荷検品画面）に修正済み
| 4 | セクション2.2 カラム定義 | data-model/03-transaction-tables.md outbound_slip_lines | `pickedQuantity`（ピッキング数）は outbound_slip_lines に直接カラムとして存在せず、picking_instruction_lines.qty_picked を集計して導出する必要がある。ただしこれはAPI側のデータ取得仕様の問題であり、RPT-13ではAPIフィールド参照として正しく記載されている。 | 指摘なし | RPT-13の記載自体は問題ない。API-RPT-013のデータ取得仕様で結合・集計方法を記載すべき（現在未記載だが別途対応） |
| 5 | セクション2.2 幅合計 | _standard-report.md | 幅合計277mmがA4横印字可能幅として計算されており、標準テンプレートの記載（A4横: 約277mm）と一致している。 | 指摘なし | 問題なし |
| 6 | セクション4 特記事項 | _standard-report.md 共通仕様 | 差異行の強調表示（背景色ピンク、赤文字）、データ0件時のメッセージ表示、空値表示（`—`）など、標準テンプレートのルールに準拠して記載されている。 | 指摘なし | 問題なし |
