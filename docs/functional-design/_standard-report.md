# レポート設計書 標準テンプレート

> このファイルはレポート設計書（RPT-XX-*.md）を作成する際のひな型。設計書としては取り込まない。

---

## 共通仕様（全レポート共通）

### レンダリング方式

| 項目 | 内容 |
|------|------|
| **PDF生成ライブラリ** | OpenPDF（iText 2.x フォーク、LGPL/MPL）+ Thymeleaf HTMLテンプレート |
| **生成フロー** | Thymeleaf → HTML → OpenPDF（Flying Saucer）→ PDF バイナリ |
| **フォント** | Noto Sans JP（日本語）/ Noto Sans（英数字）。Google Fonts からビルド時に取得し `resources/fonts/` に配置 |
| **テンプレート配置** | `backend/src/main/resources/templates/reports/{report-id}.html` |

### 用紙共通設定

| 項目 | デフォルト値 | 備考 |
|------|------------|------|
| **用紙サイズ** | A4 | レポートごとに上書き可能 |
| **向き** | 横（Landscape） | 列数が多いレポートは横、少なければ縦を指定 |
| **余白** | 上15mm / 下15mm / 左10mm / 右10mm | |
| **文字サイズ（本文）** | 9pt | |
| **文字サイズ（ヘッダー行）** | 8pt / Bold | |
| **行高さ** | 6mm | |

### PDFヘッダー（全ページ共通）

```
┌─────────────────────────────────────────────────────────────────────┐
│ {レポートタイトル}                        出力日時: 2026-03-17 14:30  │
│ 倉庫: 東京第一倉庫 (WH-001)              出力者: admin              │
│ 条件: {抽出条件のサマリー表示}                                        │
├─────────────────────────────────────────────────────────────────────┤
```

| 要素 | 位置 | 内容 |
|------|------|------|
| レポートタイトル | 左上 | レポート名（例:「入荷検品レポート」） |
| 出力日時 | 右上 | PDF生成日時（JST、`yyyy-MM-dd HH:mm`） |
| 倉庫名 | 左下 | 選択中倉庫の名称とコード |
| 出力者 | 右下 | ログインユーザーの氏名 |
| 抽出条件 | 左下 | 指定されたパラメータのサマリー（例:「期間: 2026-03-01 ～ 2026-03-17 / 仕入先: テスト仕入先A」） |

### PDFフッター（全ページ共通）

```
├─────────────────────────────────────────────────────────────────────┤
│ WMS ShowCase                                      Page {N} / {TOTAL} │
└─────────────────────────────────────────────────────────────────────┘
```

| 要素 | 位置 | 内容 |
|------|------|------|
| システム名 | 左 | 「WMS ShowCase」固定 |
| ページ番号 | 右 | `Page {現在ページ} / {総ページ数}` |

### 数値書式共通ルール

| 種別 | 書式 | 例 |
|------|------|-----|
| 整数（数量） | 3桁カンマ区切り、右寄せ | `1,234` |
| 日付 | `yyyy-MM-dd` | `2026-03-17` |
| 日時 | `yyyy-MM-dd HH:mm` | `2026-03-17 14:30` |
| 割合（%） | 小数第1位まで、右寄せ | `98.5%` |
| 空値 | `—`（emダッシュ） | `—` |

---

## テンプレート: 個別レポート設計書

以下を各レポートごとに `RPT-XX-{name}.md` として作成する。

---

### テンプレート本文

````markdown
# レポート設計書 — {レポートタイトル}

## 1. レポート概要

| 項目 | 内容 |
|------|------|
| **レポートID** | `RPT-XX` |
| **レポート名** | {レポート名} |
| **用途** | {業務上の用途。誰が、いつ、何のために使うか} |
| **対応API** | `API-RPT-XXX`（[API-10-report.md](API-10-report.md) を参照） |
| **用紙サイズ** | A4 |
| **向き** | 横（Landscape）/ 縦（Portrait） |
| **呼び出し元画面** | {画面ID}（{画面名}）から出力ボタンで呼び出し |

> 入力パラメータ（検索条件）は [API-10-report.md](API-10-report.md) の API-RPT-XXX を参照。

## 2. PDFレイアウト

### 2.1. ヘッダー（1ページ目のみ追加表示する情報がある場合）

{共通ヘッダーに加えて1ページ目のみに表示する情報があれば記載。なければ「共通ヘッダーのみ（_standard-report.md 参照）」}

### 2.2. 明細テーブル カラム定義

| # | カラム名 | APIフィールド | 幅(mm) | 配置 | 書式 | 備考 |
|---|---------|-------------|--------|------|------|------|
| 1 | No. | （行番号） | 8 | 右 | 整数 | 自動採番 |
| 2 | {列名} | `{fieldName}` | {幅} | 左/中/右 | {書式} | {備考} |
| 3 | ... | ... | ... | ... | ... | ... |

> 幅の合計は用紙の印字可能幅以内に収める。A4横: 約277mm、A4縦: 約190mm。

### 2.3. グルーピング・小計

{グルーピングが必要な場合のルール。不要なら「グルーピングなし（フラットリスト）」}

例:
```
グルーピングキー: {fieldName}（例: supplierName）
小計行: {対象カラム}の合計を表示
グループヘッダー: {グルーピングキーの値}を太字で表示
ページブレーク: グループ単位で改ページする / しない
```

### 2.4. 合計行（テーブル末尾）

{最終行に合計を表示する場合の定義。不要なら「合計行なし」}

| 表示位置 | 内容 |
|---------|------|
| {カラム名}列 | 「合計」ラベル |
| {数量列}列 | 全行の合計値 |

### 2.5. ページブレークルール

| ルール | 内容 |
|--------|------|
| 行数制限 | {N}行/ページ（超過時に自動改ページ） |
| グループ改ページ | する / しない |
| 孤立行防止 | グループの最終行が次ページに1行だけ送られる場合、グループごと改ページする |

## 3. レイアウトイメージ

{ASCIIアートまたは説明で、実際のPDF出力イメージを示す}

```
┌───────────────────────────────────────────────────────────────┐
│ {レポートタイトル}                    出力日時: 2026-03-17 14:30│
│ 倉庫: 東京第一倉庫 (WH-001)          出力者: admin            │
│ 条件: {パラメータサマリー}                                      │
├────┬──────┬──────────┬──────┬──────┬──────┬──────┬────────────┤
│ No.│{col1}│  {col2}  │{col3}│{col4}│{col5}│{col6}│   {col7}   │
├────┼──────┼──────────┼──────┼──────┼──────┼──────┼────────────┤
│  1 │ xxx  │xxxxxxxx  │   10 │   10 │    0 │  ... │    ...     │
│  2 │ xxx  │xxxxxxxx  │    5 │    4 │   -1 │  ... │    ...     │
│  : │  :   │    :     │   :  │   :  │   :  │   :  │     :      │
├────┴──────┼──────────┼──────┼──────┼──────┼──────┼────────────┤
│           │  合計    │   15 │   14 │   -1 │      │            │
├───────────┴──────────┴──────┴──────┴──────┴──────┴────────────┤
│ WMS ShowCase                                  Page 1 / 1      │
└───────────────────────────────────────────────────────────────┘
```

## 4. 特記事項

{レポート固有の注意点。該当なしなら「なし」}

- {例: 差異がある行は背景色をピンク（#FFF1F1）にする}
- {例: ロット管理フラグOFFの商品はロット番号列を「—」で表示}
- {例: データ0件の場合は「該当データがありません」メッセージを中央表示}
````

---

## ファイル命名規則

| パターン | 例 |
|---------|-----|
| `RPT-{NN}-{name}.md` | `RPT-01-inbound-inspection.md` |

RPT番号は functional-requirements/05-reports.md のレポート番号と一致させる。

| RPT-# | レポート名 | 対応API |
|-------|-----------|---------|
| RPT-01 | 入荷検品レポート | API-RPT-001 |
| RPT-03 | 入荷予定レポート | API-RPT-003 |
| RPT-04 | 入庫実績レポート | API-RPT-004 |
| RPT-05 | 未入荷リスト（リアルタイム） | API-RPT-005 |
| RPT-06 | 未入荷リスト（確定） | API-RPT-006 |
| RPT-07 | 在庫一覧レポート | API-RPT-007 |
| RPT-08 | 在庫推移レポート | API-RPT-008 |
| RPT-09 | 在庫訂正一覧 | API-RPT-009 |
| RPT-10 | 棚卸リスト | API-RPT-010 |
| RPT-11 | 棚卸結果レポート | API-RPT-011 |
| RPT-12 | ピッキング指示書 | API-RPT-012 |
| RPT-13 | 出荷検品レポート | API-RPT-013 |
| RPT-14 | 配送リスト | API-RPT-014 |
| RPT-15 | 未出荷リスト（リアルタイム） | API-RPT-015 |
| RPT-16 | 未出荷リスト（確定） | API-RPT-016 |
| RPT-17 | 日次集計レポート | API-RPT-017 |

---

## API-10-report.md 業務ロジック記載ガイド（データ取得仕様）

レポートの**データ取得ロジック**はAPI設計書（API-10-report.md）の「4. 業務ロジック」セクションに定義する（SSOTルール）。
各レポートAPIの業務ロジックには、フローチャートとビジネスルールに加え、以下の **「データ取得仕様」** サブセクションを必ず記載すること。

### テンプレート: データ取得仕様

以下を API-10-report.md の各APIの「4. 業務ロジック」内に追記する。

````markdown
##### データ取得仕様

**主テーブル・結合**

```sql
-- 論理SQL（実装はJPQL/Criteria APIでも可。結合構造とカラムマッピングを示す目的）
SELECT ...
FROM {主テーブル} AS {alias}
  JOIN {結合テーブル1} AS {alias} ON {結合条件}
  LEFT JOIN {結合テーブル2} AS {alias} ON {結合条件}
WHERE {固定条件}
ORDER BY {ソート条件}
```

**フィルタ条件（クエリパラメータ → WHERE句マッピング）**

| クエリパラメータ | WHERE句 | 備考 |
|----------------|---------|------|
| `warehouseId` | `t.warehouse_id = :warehouseId` | 必須 |
| `dateFrom` | `t.target_date >= :dateFrom` | 任意。未指定時は条件なし |
| `dateTo` | `t.target_date <= :dateTo` | 任意。未指定時は条件なし |
| `status` | `t.status = :status` | 任意。未指定時は全ステータス |

**計算フィールド（レスポンスフィールド ← 導出元）**

| レスポンスフィールド | 導出方法 |
|--------------------|---------|
| `supplierName` | `partners.partner_name`（`inbound_slips.supplier_id` → `partners.id`） |
| `diffQuantityPcs` | `inspectedQuantityPcs - plannedQuantityPcs`（アプリ側計算） |
| `statusLabel` | `status` の enum → 日本語ラベル変換（アプリ側） |

**ソート順**

| 優先度 | カラム | 方向 |
|--------|-------|------|
| 1 | `{ソートキー1}` | ASC / DESC |
| 2 | `{ソートキー2}` | ASC / DESC |

**集計（該当する場合のみ）**

| 集計種別 | 対象 | 方法 |
|---------|------|------|
| グループ集計 | `{グルーピングキー}` ごとの小計 | SQL `GROUP BY` / アプリ側ストリーム集計 |
| 全体合計 | 最終行の合計 | アプリ側で全行を合算 |
````

### 記載のポイント

- **論理SQL**: 実装言語（JPQL等）に依存しない形で結合構造を示す。実装者がRepository層を書く際のリファレンスとする
- **フィルタ条件マッピング**: クエリパラメータがSQLのどの条件になるかを明示する。「未指定時の挙動」も必ず書く
- **計算フィールド**: DBに直接存在しないレスポンスフィールドの導出方法を明示する。SQL側で計算するか、アプリ側で計算するかも記載する
- **ソート順**: PDFの印字順序を決定するため必須。グルーピングがある場合はグルーピングキーが第1ソートキーとなる

---

## SSOTルール（レポート設計書における情報管理）

| 情報 | 定義場所（SSOT） | レポート設計書での記載 |
|------|-----------------|-------------------|
| 入力パラメータ | API-10-report.md | 「API-RPT-XXXを参照」のみ |
| JSONレスポンスフィールド | API-10-report.md | カラム定義の`APIフィールド`列で参照 |
| ビジネスルール（対象データの抽出条件） | API-10-report.md の業務ロジック | 「API-RPT-XXXの業務ロジックを参照」 |
| **データ取得仕様（SQL構造・フィルタマッピング・計算フィールド）** | **API-10-report.md の業務ロジック内** | 「API-RPT-XXXのデータ取得仕様を参照」 |
| **PDFレイアウト（カラム定義・幅・配置・書式）** | **RPT-XX-*.md（ここが唯一の定義場所）** | — |
| **グルーピング・小計・ページブレーク** | **RPT-XX-*.md（ここが唯一の定義場所）** | — |
| **条件付き書式（差異行の強調等）** | **RPT-XX-*.md（ここが唯一の定義場所）** | — |

---

## 実装ガイド（コーディング参考情報）

### フォント設定（m-15）

PDF生成にはNoto Sans JPフォントを使用する。

1. `src/main/resources/fonts/` にフォントファイルを配置
   - `NotoSansJP-Regular.ttf`
   - `NotoSansJP-Bold.ttf`
2. `PdfGenerationService` でフォント登録:
```java
renderer.getFontResolver().addFont(
    "fonts/NotoSansJP-Regular.ttf",
    BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
renderer.getFontResolver().addFont(
    "fonts/NotoSansJP-Bold.ttf",
    BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
```

### カラム幅のCSS変換ガイド（m-16）

RPT設計書のmm単位をCSS `mm` 単位にそのまま使用する。Flying SaucerはCSS `mm` をサポートする。

```css
/* RPT-01 カラム幅の例 */
.col-slip-number { width: 35mm; }
.col-product-code { width: 30mm; }
.col-product-name { width: 52mm; }
```

A4横の印字可能幅: `277mm`（左右余白10mm × 2を除く）
A4縦の印字可能幅: `190mm`（左右余白10mm × 2を除く）

### ヘッダー・フッターのCSS実装（m-17）

```css
@page {
  size: A4 landscape;
  margin: 15mm 10mm;

  @top-left {
    content: "WMS レポート名";
    font-size: 8pt;
  }
  @top-right {
    content: "出力日: " attr(data-print-date);
    font-size: 8pt;
  }
  @bottom-center {
    content: "Page " counter(page) " / " counter(pages);
    font-size: 8pt;
  }
}
```

### Thymeleafテンプレート雛形（m-18）

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8"/>
  <style>
    @page { size: A4 landscape; margin: 15mm 10mm; }
    body { font-family: 'Noto Sans JP', sans-serif; font-size: 9pt; }
    table { width: 100%; border-collapse: collapse; }
    th, td { border: 1px solid #333; padding: 2mm; }
    th { background: #1a1a2e; color: white; font-size: 8pt; }
    .text-right { text-align: right; }
    .text-center { text-align: center; }
    .group-header { background: #e8e8e8; font-weight: bold; }
    .subtotal-row { background: #f4f4f4; font-weight: bold; }
    .total-row { background: #1a1a2e; color: white; font-weight: bold; }
    .page-break { page-break-before: always; }
  </style>
</head>
<body>
  <!-- ヘッダー情報 -->
  <div class="report-header">
    <h1 th:text="${reportTitle}">レポートタイトル</h1>
    <span th:text="'出力日: ' + ${printDate}">出力日: 2026-03-20</span>
    <span th:text="'倉庫: ' + ${warehouseName}">倉庫: 東京DC</span>
  </div>

  <!-- 明細テーブル -->
  <table>
    <thead>
      <tr>
        <th style="width: 35mm;">カラム1</th>
        <th style="width: 52mm;">カラム2</th>
      </tr>
    </thead>
    <tbody>
      <!-- グループヘッダー -->
      <tr th:each="group : ${groups}" class="group-header">
        <td th:colspan="2" th:text="${group.name}">グループ名</td>
      </tr>
      <!-- 明細行 -->
      <tr th:each="row : ${group.rows}">
        <td th:text="${row.field1}">値1</td>
        <td class="text-right" th:text="${#numbers.formatInteger(row.field2, 1, 'COMMA')}">1,234</td>
      </tr>
      <!-- 小計行 -->
      <tr class="subtotal-row">
        <td>小計</td>
        <td class="text-right" th:text="${#numbers.formatInteger(group.subtotal, 1, 'COMMA')}">5,678</td>
      </tr>
    </tbody>
    <tfoot>
      <tr class="total-row">
        <td>合計</td>
        <td class="text-right" th:text="${#numbers.formatInteger(grandTotal, 1, 'COMMA')}">99,999</td>
      </tr>
    </tfoot>
  </table>
</body>
</html>
```

### 数値フォーマットのThymeleaf式（m-19）

| 書式 | Thymeleaf式 | 表示例 |
|------|------------|--------|
| 3桁カンマ | `${#numbers.formatInteger(value, 1, 'COMMA')}` | 1,234 |
| 日付 | `${#temporals.format(date, 'yyyy-MM-dd')}` | 2026-03-20 |
| 日時 | `${#temporals.format(datetime, 'yyyy-MM-dd HH:mm')}` | 2026-03-20 14:30 |
| 割合 | `${#numbers.formatDecimal(value, 1, 1)}%` | 98.5% |
| 空値 | `${value != null ? value : '—'}` | — |

### 大量データ時のメモリ管理（m-20）

- 件数上限10,000件はAPIパラメータバリデーションで強制
- 10,000件のPDF生成はメモリ上で処理（ヒープ目安: 256MB〜512MB）
- Container Appsのメモリ設定（1Gi）で十分カバー可能
- 将来的にメモリ問題が発生した場合はページ単位のストリーミング生成を検討

### RPT-17（日次集計）テンプレートガイド（m-21）

RPT-17は通常のテーブル形式ではなく、倉庫別セクション×キー・バリュー形式の特殊レイアウト。

```html
<div th:each="warehouse : ${warehouses}">
  <h2 th:text="${warehouse.warehouseName}">東京DC</h2>
  <table class="kv-table">
    <tr><th>入荷件数</th><td th:text="${warehouse.inboundCount}">10</td></tr>
    <tr><th>入荷数量合計（バラ換算）</th><td th:text="${#numbers.formatInteger(warehouse.inboundQtyTotal, 1, 'COMMA')}">1,234</td></tr>
    <!-- 他のKV項目... -->
  </table>
  <div class="page-break"></div>
</div>
<!-- 全倉庫合計セクション -->
<h2>全倉庫合計</h2>
<table class="kv-table">...</table>
```

### PDFテスト方針（m-22）

```java
@Test
void generateInboundInspectionReport_returnsValidPdf() {
    byte[] pdf = pdfGenerationService.generatePdf("rpt-01", testData);

    // 1. PDFバイナリが返ること
    assertThat(pdf).isNotEmpty();
    assertThat(pdf[0]).isEqualTo((byte) '%'); // PDFマジックバイト

    // 2. ページ数の検証（Apache PDFBox使用）
    try (PDDocument doc = Loader.loadPDF(pdf)) {
        assertThat(doc.getNumberOfPages()).isGreaterThanOrEqualTo(1);
    }

    // 3. テキスト抽出の検証
    try (PDDocument doc = Loader.loadPDF(pdf)) {
        PDFTextStripper stripper = new PDFTextStripper();
        String text = stripper.getText(doc);
        assertThat(text).contains("入荷検品レポート");
    }
}
```

テスト用依存:
```groovy
testImplementation 'org.apache.pdfbox:pdfbox:3.0.2'
```

### パスワード強度インジケーター Composable（m-25）

```typescript
// composables/usePasswordStrength.ts
export function usePasswordStrength() {
  const strength = ref<'weak' | 'medium' | 'strong'>('weak')

  function evaluate(password: string) {
    let score = 0
    if (password.length >= 8) score++
    if (password.length >= 12) score++
    if (/[A-Z]/.test(password)) score++
    if (/[a-z]/.test(password)) score++
    if (/[0-9]/.test(password)) score++
    if (/[^A-Za-z0-9]/.test(password)) score++

    strength.value = score <= 2 ? 'weak' : score <= 4 ? 'medium' : 'strong'
  }

  return { strength, evaluate }
}
```

AUTH-002（パスワード変更）とAUTH-004（パスワードリセット確認）で共用する。
