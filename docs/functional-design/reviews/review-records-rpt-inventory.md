# レビュー記録票: RPT-07-inventory.md

**レビュー実施日**: 2026-03-18
**対象ファイル**: docs/functional-design/RPT-07-inventory.md
**レビュアー**: Claude Code (自動レビュー)

## サマリー

| 分類 | 件数 |
|------|:----:|
| 対象ファイル修正済み | 3 |
| 要対応（他ドキュメント） | 3 |
| 指摘なし | 0 |
| **合計** | **6** |

## 詳細指摘テーブル

| No | 対象箇所 | 参照ドキュメント | 指摘内容 | 分類 | 対応内容 |
|----|---------|-----------------|---------|------|---------|
| 1 | RPT-07 2.2 カラム定義 #6, #7 備考 / 4. 特記事項 | _standard-report.md 数値書式共通ルール | 空値表示に「---」（ハイフン3つ）を使用していたが、標準テンプレートでは「—」（emダッシュ）と定義されている。 | 対象ファイル修正済み | 「---」を「—」に統一。カラム定義備考、特記事項、レイアウトイメージの3箇所を修正 |
| 2 | RPT-07 4. 特記事項 ソート順 | RPT-07 2.3 グルーピング・小計 | ソート順が「エリアコード昇順→ロケーションコード昇順→商品コード昇順」と記載されていたが、グルーピングキーが `productCode` であるため、商品コードが第1ソートキーでなければグルーピングが正しく機能しない。 | 対象ファイル修正済み | ソート順を「商品コード昇順（グルーピングキー）→エリアコード昇順→ロケーションコード昇順（グループ内ソート）」に修正 |
| 3 | RPT-07 3. レイアウトイメージ | — | レイアウトイメージ内のロット管理OFF/期限管理OFFの表示が「---」だったため「—」に修正。 | 対象ファイル修正済み | レイアウトイメージ内の空値表示を「—」に統一 |
| 4 | API-10-report.md API-RPT-007 3. レスポンス仕様 | RPT-07 2.2 カラム定義 #9, #10 | RPT-07では `allocatedQty`（引当数量）と `availableQty`（有効在庫数）をカラムとして定義しているが、API-RPT-007のレスポンス仕様にこれらのフィールドが存在しない。APIレスポンスにこの2フィールドを追加する必要がある。 | 要対応（他ドキュメント） | API-10-report.md の API-RPT-007 レスポンス仕様に `allocatedQty` (Integer, 引当数量) と `availableQty` (Integer, 有効在庫数 = quantity - allocatedQty) を追加する必要がある。サンプルJSONにも追加すること。 ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-007 レスポンス仕様にallocatedQty・availableQtyフィールドを追加済み
| 5 | API-10-report.md API-RPT-007 3. レスポンス仕様 フィールド表 | データモデル inventories テーブル / RPT-07 2.2 カラム定義 #5 | `unitType` フィールドの説明が「`CAS` / `PCS`」のみで `BAL`（ボール）が欠落している。RPT-07では `CAS/BAL/PCS` と記載されている。 | 要対応（他ドキュメント） | API-10-report.md の API-RPT-007 レスポンス仕様のフィールド表で `unitType` の説明を「`CAS` / `BAL` / `PCS`」に修正する必要がある。 ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-007 unitTypeフィールド説明にBALを追加済み
| 6 | API-10-report.md API-RPT-007 2. リクエスト仕様 | data-model/02-master-tables.md (products.storage_condition, areas.storage_condition) | `storageCondition` パラメータの値が `NORMAL` / `REFRIGERATED` / `FROZEN` と記載されているが、データモデルでは `AMBIENT` / `REFRIGERATED` / `FROZEN` と定義されている。`NORMAL` は `AMBIENT` の誤り。 | 要対応（他ドキュメント） | API-10-report.md の API-RPT-007 リクエスト仕様で `storageCondition` の値を `AMBIENT` / `REFRIGERATED` / `FROZEN` に修正する必要がある。 ✅ 対応完了（2026-03-18修正） |

> **対応完了** (2026-03-19): API-10-report.md の API-RPT-007 storageCondition値をNORMAL→AMBIENTに修正済み
