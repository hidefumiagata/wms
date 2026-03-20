# I/F設計書 — IFX-002: 受注取り込みI/F

> **業務要件**: [07-interface.md](../functional-requirements/07-interface.md) を参照
> **方式設計（CSV仕様・バリデーション・フロー・クラス構成）**: [09-interface-architecture.md](../architecture-design/09-interface-architecture.md) を参照
> **出荷管理業務ルール**: [04-outbound-management.md](../functional-requirements/04-outbound-management.md) を参照
> **テーブル定義**: [03-transaction-tables.md](../data-model/03-transaction-tables.md)（outbound_slips / outbound_slip_lines / if_executions）を参照
> **マスタ定義**: [02-master-tables.md](../data-model/02-master-tables.md)（partners / products）を参照

---

## 目次

1. [I/F概要](#1-if概要)
2. [データマッピング詳細](#2-データマッピング詳細)
3. [取り込みSQL](#3-取り込みsql)
4. [トランザクション設計](#4-トランザクション設計)
5. [伝票採番ロジック](#5-伝票採番ロジック)
6. [テスト観点](#6-テスト観点)

---

## 1. I/F概要

| 項目 | 内容 |
|------|------|
| **I/F ID** | IFX-002 |
| **I/F名** | 受注取り込みI/F |
| **方向** | 外部 → WMS |
| **形式** | CSV（UTF-8、BOMなし） |
| **取り込み先テーブル** | `outbound_slips` / `outbound_slip_lines` |
| **取り込み後ステータス** | `ORDERED`（受注） |
| **実行権限** | SYSTEM_ADMIN / WAREHOUSE_MANAGER |
| **Blob Storageパス** | `iffiles/order/pending/` → `iffiles/order/processed/` |
| **ファイル名パターン** | `ORD-{連番3桁}.csv`（例: `ORD-001.csv`） |

CSVフォーマット定義（カラム定義・ヘッダ行仕様・最大行数等）およびバリデーションルール（L1〜L5）は [09-interface-architecture.md セクション4.3, 6](../architecture-design/09-interface-architecture.md) を参照。

---

## 2. データマッピング詳細

### 2.1 伝票グルーピングロジック

CSV行を出荷伝票（ヘッダ＋明細）に変換する際、同一 `partner_code` + `planned_date` の組み合わせを1件の出荷伝票としてまとめる。

```
グルーピングキー: partner_code + "|" + planned_date
  例: "CUS-0001|2026-03-22" → 1件の outbound_slips
  各CSV行 → outbound_slip_lines の1明細
```

```java
// 擬似コード: CSVパース後のグルーピング
Map<String, List<OrderCsvRow>> grouped = validRows.stream()
    .collect(Collectors.groupingBy(
        row -> row.getPartnerCode() + "|" + row.getPlannedDate(),
        LinkedHashMap::new,  // CSV出現順を保持
        Collectors.toList()
    ));
// grouped の各エントリが1伝票に対応する
// Key: "CUS-0001|2026-03-22" → 1件の outbound_slips
// Value: List<OrderCsvRow> → 各行が outbound_slip_lines の1明細
```

**同一伝票内の同一商品チェック**: 1伝票（同一 `partner_code` + `planned_date`）内に同一 `product_code` が2行以上存在した場合はバリデーションエラー（`WMS-E-IFX-502`）とする。

### 2.2 outbound_slips（ヘッダ）マッピング

| outbound_slips カラム | 値の決定方法 | 備考 |
|----------------------|------------|------|
| `id` | DB自動採番（bigserial） | — |
| `slip_number` | 自動採番（[セクション5](#5-伝票採番ロジック) 参照） | `OUT-YYYYMMDD-XXXX` |
| `slip_type` | 固定値: `NORMAL` | 通常出荷 |
| `transfer_slip_number` | `NULL` | 倉庫振替ではないため |
| `warehouse_id` | リクエストパラメータ `warehouseId` から取得 | 画面で選択中の倉庫 |
| `warehouse_code` | `warehouses` テーブルから `warehouse_id` で検索→コード取得 | 登録時コピー |
| `warehouse_name` | `warehouses` テーブルから `warehouse_id` で検索→名称取得 | 登録時コピー |
| `partner_id` | CSV `partner_code` → `partners` テーブル検索→ID取得 | コード→ID解決 |
| `partner_code` | CSV `partner_code` をそのままコピー | 登録時コピー |
| `partner_name` | `partners` テーブルから取得した `partner_name` | 登録時コピー |
| `planned_date` | CSV `planned_date` を `yyyy-MM-dd` → `LocalDate` パース | — |
| `carrier` | `NULL` | 取り込み時は未設定 |
| `tracking_number` | `NULL` | 取り込み時は未設定 |
| `status` | 固定値: `ORDERED` | 受注ステータス |
| `shipped_at` | `NULL` | — |
| `shipped_by` | `NULL` | — |
| `cancelled_at` | `NULL` | — |
| `cancelled_by` | `NULL` | — |
| `created_at` | `now()` | DB側デフォルト |
| `created_by` | JWT認証ユーザーのID | — |
| `updated_at` | `now()` | DB側デフォルト |
| `updated_by` | JWT認証ユーザーのID | — |

### 2.3 outbound_slip_lines（明細）マッピング

| outbound_slip_lines カラム | 値の決定方法 | 備考 |
|---------------------------|------------|------|
| `id` | DB自動採番（bigserial） | — |
| `outbound_slip_id` | 親 `outbound_slips` の `id` | INSERT後に取得 |
| `line_no` | 伝票内の連番（1始まり） | グルーピング後のリスト内のインデックス+1 |
| `product_id` | CSV `product_code` → `products` テーブル検索→ID取得 | コード→ID解決 |
| `product_code` | CSV `product_code` をそのままコピー | 登録時コピー |
| `product_name` | `products` テーブルから取得した `product_name` | 登録時コピー |
| `unit_type` | CSV `unit_type` をそのまま保持 | バリデーション済み |
| `ordered_qty` | CSV `ordered_qty` を整数パース | — |
| `inspected_qty` | `NULL` | 出荷検品前のため未設定 |
| `shipped_qty` | `0`（デフォルト） | — |
| `line_status` | 固定値: `ORDERED` | — |
| `created_at` | `now()` | DB側デフォルト |
| `updated_at` | `now()` | DB側デフォルト |

### 2.4 コード→ID解決処理

バリデーション成功行に対して、以下のマスタ検索を一括で実行しMapにキャッシュする（[09-interface-architecture.md セクション10.2](../architecture-design/09-interface-architecture.md) 参照）。

```java
// 擬似コード: マスタ一括検索
Set<String> partnerCodes = validRows.stream()
    .map(OrderCsvRow::getPartnerCode)
    .collect(Collectors.toSet());

Set<String> productCodes = validRows.stream()
    .map(OrderCsvRow::getProductCode)
    .collect(Collectors.toSet());

// バッチ検索 → Map化
Map<String, Partner> partnerMap = partnerRepository
    .findByPartnerCodeIn(partnerCodes).stream()
    .collect(Collectors.toMap(Partner::getPartnerCode, Function.identity()));

Map<String, Product> productMap = productRepository
    .findByProductCodeIn(productCodes).stream()
    .collect(Collectors.toMap(Product::getProductCode, Function.identity()));

// 倉庫情報はリクエストパラメータから1件取得
Warehouse warehouse = warehouseRepository.findById(warehouseId)
    .orElseThrow(() -> new BusinessException("WMS-E-IFX-901", "倉庫が見つかりません"));
```

**マスタ参照時の検証項目**（バリデーションで検証済みだが再確認）:

| 検証内容 | エラーコード |
|---------|-----------|
| 取引先コードが `partners` テーブルに存在する | `WMS-E-IFX-301` |
| 取引先が有効（`is_active = true`） | `WMS-E-IFX-302` |
| 取引先種別が `CUSTOMER` または `BOTH` | `WMS-E-IFX-303` |
| 商品コードが `products` テーブルに存在する | `WMS-E-IFX-304` |
| 商品が有効（`is_active = true`） | `WMS-E-IFX-305` |
| 商品に出荷禁止フラグが設定されていない（`shipment_stop_flag = false`） | `WMS-E-IFX-306` |

### 2.5 CSVカラムのマッピング対象外

CSVフォーマット（[09-interface-architecture.md セクション4.3](../architecture-design/09-interface-architecture.md#43-ifx-002-受注取り込みcsvフォーマット)）で定義されている `note`（備考）カラムは、`outbound_slips` / `outbound_slip_lines` のいずれにもマッピングしない。CSVの `note` 値はバリデーション（L2: 500文字以内チェック）の対象とするが、DB登録時には保存されない。外部システム向けにこの仕様を周知すること。

### 2.6 CSVの空文字列の扱い

| CSVの値 | 変換後の値 | 対象カラム |
|---------|----------|----------|
| `""` （空文字列） | `NULL` | `note` |
| `" "` （空白のみ） | `NULL` | `note` |
| 未指定（カンマ連続 `,,`） | `NULL` | `note` |

---

## 3. 取り込みSQL

### 3.1 outbound_slips INSERT

```sql
INSERT INTO outbound_slips (
    slip_number,
    slip_type,
    transfer_slip_number,
    warehouse_id,
    warehouse_code,
    warehouse_name,
    partner_id,
    partner_code,
    partner_name,
    planned_date,
    carrier,
    tracking_number,
    status,
    shipped_at,
    shipped_by,
    cancelled_at,
    cancelled_by,
    created_at,
    created_by,
    updated_at,
    updated_by
) VALUES (
    :slipNumber,          -- 自動採番（セクション5参照）
    'NORMAL',             -- 固定値
    NULL,                 -- 振替なし
    :warehouseId,         -- リクエストパラメータ
    :warehouseCode,       -- warehousesから取得
    :warehouseName,       -- warehousesから取得
    :partnerId,           -- partnersから解決
    :partnerCode,         -- CSVから
    :partnerName,         -- partnersから取得
    :plannedDate,         -- CSVから（LocalDate）
    NULL,                 -- 配送業者（取り込み時は未設定）
    NULL,                 -- 送り状番号（取り込み時は未設定）
    'ORDERED',            -- 固定値
    NULL,                 -- 出荷日時（未出荷）
    NULL,                 -- 出荷者（未出荷）
    NULL,                 -- キャンセル日時
    NULL,                 -- キャンセル者
    NOW(),                -- 作成日時
    :currentUserId,       -- JWT認証ユーザー
    NOW(),                -- 更新日時
    :currentUserId        -- JWT認証ユーザー
)
RETURNING id;             -- 明細INSERT用に取得
```

### 3.2 outbound_slip_lines INSERT

```sql
INSERT INTO outbound_slip_lines (
    outbound_slip_id,
    line_no,
    product_id,
    product_code,
    product_name,
    unit_type,
    ordered_qty,
    inspected_qty,
    shipped_qty,
    line_status,
    created_at,
    updated_at
) VALUES (
    :outboundSlipId,      -- 親ヘッダのRETURNING id
    :lineNo,              -- 伝票内連番（1始まり）
    :productId,           -- productsから解決
    :productCode,         -- CSVから
    :productName,         -- productsから取得
    :unitType,            -- CSVから（バリデーション済み）
    :orderedQty,          -- CSVから（整数パース済み）
    NULL,                 -- 検品数量（未検品）
    0,                    -- 出荷済み数量（デフォルト）
    'ORDERED',            -- 固定値
    NOW(),                -- 作成日時
    NOW()                 -- 更新日時
);
```

### 3.3 if_executions INSERT（取り込み履歴）

```sql
INSERT INTO if_executions (
    if_type,
    file_name,
    blob_path,
    total_count,
    success_count,
    error_count,
    mode,
    status,
    error_message,
    blob_move_failed,
    warehouse_id,
    executed_at,
    executed_by
) VALUES (
    'ORDER',              -- 固定値
    :fileName,            -- 元ファイル名（例: ORD-001.csv）
    :blobPath,            -- processed後の完全パス
    :totalCount,          -- CSV総行数（ヘッダ除く）
    :successCount,        -- バリデーション成功行数
    :errorCount,          -- バリデーションエラー行数
    :mode,                -- 'SUCCESS_ONLY' or 'DISCARD'
    :status,              -- 'COMPLETED' or 'DISCARDED' or 'FAILED'
    :errorMessage,        -- エラーメッセージ（FAILED時のみ、それ以外はNULL）
    false,                -- Blob移動失敗フラグ（初期値false、移動失敗時にUPDATE）
    :warehouseId,         -- 取り込み対象倉庫ID
    NOW(),                -- 実行日時
    :currentUserId        -- 実行ユーザーID
);
```

### 3.4 バッチINSERT実装方針

JPA `saveAll()` を使用し、Spring Data JPAの一括保存を活用する。パフォーマンス最適化のため以下の設定を行う。

```yaml
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 100       # 100件ずつバッチINSERT
        order_inserts: true     # INSERT文をテーブル順に並べ替え
        order_updates: true     # UPDATE文をテーブル順に並べ替え
```

```java
// 擬似コード: エンティティ構築とバッチ保存
List<OutboundSlip> slipsToSave = new ArrayList<>();

for (Map.Entry<String, List<OrderCsvRow>> entry : grouped.entrySet()) {
    String[] keyParts = entry.getKey().split("\\|");
    String partnerCode = keyParts[0];
    String plannedDateStr = keyParts[1];
    List<OrderCsvRow> rows = entry.getValue();

    Partner partner = partnerMap.get(partnerCode);
    LocalDate plannedDate = LocalDate.parse(plannedDateStr);

    // ヘッダ構築
    OutboundSlip slip = new OutboundSlip();
    slip.setSlipNumber(generateSlipNumber(plannedDate)); // セクション5参照
    slip.setSlipType("NORMAL");
    slip.setWarehouseId(warehouse.getId());
    slip.setWarehouseCode(warehouse.getWarehouseCode());
    slip.setWarehouseName(warehouse.getWarehouseName());
    slip.setPartnerId(partner.getId());
    slip.setPartnerCode(partner.getPartnerCode());
    slip.setPartnerName(partner.getPartnerName());
    slip.setPlannedDate(plannedDate);
    slip.setStatus("ORDERED");
    slip.setCreatedBy(currentUserId);
    slip.setUpdatedBy(currentUserId);

    // 明細構築
    List<OutboundSlipLine> lines = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
        OrderCsvRow row = rows.get(i);
        Product product = productMap.get(row.getProductCode());

        OutboundSlipLine line = new OutboundSlipLine();
        line.setOutboundSlip(slip);       // JPA関連付け
        line.setLineNo(i + 1);            // 1始まり
        line.setProductId(product.getId());
        line.setProductCode(product.getProductCode());
        line.setProductName(product.getProductName());
        line.setUnitType(row.getUnitType());
        line.setOrderedQty(row.getOrderedQty());
        line.setShippedQty(0);
        line.setLineStatus("ORDERED");
        lines.add(line);
    }
    slip.setLines(lines);
    slipsToSave.add(slip);
}

// バッチ保存（cascade = CascadeType.ALL で明細も同時保存）
outboundSlipRepository.saveAll(slipsToSave);
outboundSlipRepository.flush();  // バッチINSERT即時実行
```

---

## 4. トランザクション設計

### 4.1 トランザクション範囲

トランザクション制御は [09-interface-architecture.md セクション7.4](../architecture-design/09-interface-architecture.md) の共通設計に従う。

```
[トランザクション外] Blob Storage読み取り
[トランザクション外] 再バリデーション（L1〜L5）
[トランザクション外] マスタ一括検索・Map構築
------- @Transactional 開始 -------
[トランザクション内] outbound_slips / outbound_slip_lines INSERT
[トランザクション内] if_executions INSERT（取り込み履歴）
------- @Transactional コミット -------
[トランザクション外] Blobファイル移動（pending → processed）
```

### 4.2 1ファイル = 1トランザクション

1つのCSVファイルの取り込みは1つのDBトランザクションとして実行する。ファイル内のいずれかの伝票の登録に失敗した場合は、そのファイルの全伝票がロールバックされる。

### 4.3 SUCCESS_ONLYモード時の部分成功

| 項目 | 仕様 |
|------|------|
| **対象行** | バリデーション成功行のみを取り込み対象とする |
| **エラー行** | スキップする（DBには登録しない） |
| **トランザクション** | 成功行の全伝票登録を1トランザクションで実行 |
| **部分失敗** | 成功行の登録中にDBエラーが発生した場合は全伝票ロールバック |
| **履歴記録** | `if_executions` に `success_count`（登録行数）と `error_count`（スキップ行数）を記録 |

### 4.4 DISCARDモード

| 項目 | 仕様 |
|------|------|
| **DB登録** | 行わない |
| **ファイル移動** | `pending` → `processed` へ移動する |
| **履歴記録** | `if_executions` に `status = 'DISCARDED'` として記録 |

### 4.5 障害パターンとリカバリ

| パターン | 影響 | リカバリ |
|---------|------|---------|
| DB INSERT中の例外 | 全伝票ロールバック。ファイルは `pending` に残留 | ユーザーが原因を確認し再操作 |
| Blob移動失敗（DB登録成功後） | DB登録は確定済み。ファイルが `pending` に残留 | `if_executions` に `blob_move_failed = true` を記録。手動でファイル移動 |
| アプリ再起動（トランザクション中） | 未コミット分はロールバック。ファイルは `pending` に残留 | ユーザーが再操作 |

---

## 5. 伝票採番ロジック

### 5.1 採番フォーマット

```
OUT-{YYYYMMDD}-{4桁連番}
```

| 要素 | 説明 | 例 |
|------|------|-----|
| `OUT` | 出荷伝票固定プレフィックス | `OUT` |
| `YYYYMMDD` | 現在営業日（`current_business_date`） | `20260322` |
| `4桁連番` | その日付の通し番号（0001始まり） | `0001` |

例: `OUT-20260322-0001`, `OUT-20260322-0002`

### 5.2 採番方式: SELECT FOR UPDATE

PostgreSQLネイティブシーケンスは日付リセットに対応できないため、`SELECT ... FOR UPDATE` によるアプリケーション採番を使用する（[IF-01-inbound-plan.md](IF-01-inbound-plan.md) と同一方式）。

```java
// SlipNumberGenerator.java — 出荷伝票番号の採番
public String generateOutboundSlipNumber(LocalDate businessDate) {
    String dateStr = businessDate.format(DateTimeFormatter.BASIC_ISO_DATE); // 20260322
    String prefix = "OUT-" + dateStr + "-";

    // 同一日付の最大番号を排他ロック付きで取得
    Optional<String> maxSlipNumber = outboundSlipRepository
        .findMaxSlipNumberByPrefix(prefix);

    int nextSeq;
    if (maxSlipNumber.isPresent()) {
        // "OUT-20260322-0003" → 3 → 4
        String seqPart = maxSlipNumber.get().substring(prefix.length());
        nextSeq = Integer.parseInt(seqPart) + 1;
    } else {
        nextSeq = 1;
    }

    if (nextSeq > 9999) {
        throw new BusinessException("WMS-E-IFX-901",
            "伝票番号の採番上限（9999）に達しました: " + prefix);
    }

    return prefix + String.format("%04d", nextSeq);
}
```

**Repository クエリ**:

```java
// OutboundSlipRepository.java
@Query(value = """
    SELECT slip_number FROM outbound_slips
    WHERE slip_number LIKE :prefix || '%'
    ORDER BY slip_number DESC
    LIMIT 1
    FOR UPDATE
    """, nativeQuery = true)
Optional<String> findMaxSlipNumberByPrefix(@Param("prefix") String prefix);
```

### 5.3 コンカレンシー制御

| 項目 | 内容 |
|------|------|
| **排他方式** | `SELECT ... FOR UPDATE`（行ロック） |
| **ロック範囲** | 同一日付プレフィックスの最大番号行のみ |
| **デッドロック防止** | 営業日ベースの採番のため、同一トランザクション内では同一日付プレフィックスのみ。デッドロックリスクなし |
| **スループット** | 同一日付の同時採番はシリアライズされるが、異なる日付の採番は並行可能 |

```java
// 営業日を取得して採番に使用（1ファイル内の全伝票で同一営業日）
LocalDate businessDate = businessDateService.getCurrentBusinessDate();

for (String key : grouped.keySet()) {
    String slipNumber = slipNumberGenerator.generateOutboundSlipNumber(businessDate);
    // ... 伝票構築 ...
}
```

**同時実行の追加安全策**: I/F取り込みは画面UIで同一I/Fの並行実行を制限する（[09-interface-architecture.md セクション10.1](../architecture-design/09-interface-architecture.md) 参照）。加えて、`outbound_slips.slip_number` にUNIQUE制約があるため、万が一の重複はDB側で検出される。重複発生時はトランザクション全体がロールバックされ、ユーザーに再操作を促す。

### 5.4 日付リセット

採番連番は日付（`current_business_date`）単位でリセットされる。1ファイル内の全伝票は同一営業日で採番される（`planned_date` に関わらず統一）。手動登録API（[API-08-outbound.md](API-08-outbound.md)）と同一の日付基準を使用する。

---

## 6. テスト観点

### 6.1 正常系

| No | テスト観点 | テスト内容 |
|----|----------|----------|
| N-01 | 基本取り込み | CSVファイル1件（1伝票・1明細）を取り込み、`outbound_slips` と `outbound_slip_lines` が正しく登録されること |
| N-02 | 複数伝票 | 異なる `partner_code` + `planned_date` の組み合わせが別伝票として登録されること |
| N-03 | 複数明細 | 同一伝票に複数の商品明細が正しく登録され、`line_no` が1始まりの連番であること |
| N-04 | 伝票採番 | `OUT-YYYYMMDD-XXXX` 形式で正しく採番されること |
| N-05 | 連番の連続性 | 同一ファイル内に同一 `planned_date` の伝票が複数ある場合、連番が連続すること（例: 0001, 0002） |
| N-06 | 既存伝票がある場合の採番 | DBに既存伝票がある日付でINSERTした場合、既存の最大値+1から採番されること |
| N-07 | コード→ID解決 | `partner_code` → `partner_id` / `partner_name`、`product_code` → `product_id` / `product_name` が正しくコピーされること |
| N-08 | ステータス | 取り込み後の伝票ステータスが `ORDERED`、明細ステータスが `ORDERED` であること |
| N-09 | 倉庫情報 | リクエストの `warehouseId` から `warehouse_code` / `warehouse_name` が正しくコピーされること |
| N-10 | SUCCESS_ONLYモード | エラー行がスキップされ、成功行のみが登録されること |
| N-11 | DISCARDモード | DB登録が行われず、ファイルが `processed` に移動されること |
| N-12 | 取り込み履歴 | `if_executions` に正しく履歴が記録されること（`if_type = 'ORDER'`） |
| N-13 | Blobファイル移動 | 取り込み後にファイルが `processed/{yyyy}/{MM}/{dd}/{timestamp}_{fileName}` に移動されること |
| N-14 | 備考フィールド | CSV `note` カラムが `outbound_slip_lines` に反映されないこと（outbound_slip_linesにnoteカラムなし） |
| N-15 | 大量データ | 10,000行のCSVが60秒以内に取り込み完了すること |

### 6.2 バリデーション系

| No | テスト観点 | テスト内容 |
|----|----------|----------|
| V-01 | 必須チェック | `partner_code`, `planned_date`, `product_code`, `unit_type`, `ordered_qty` が空の場合にエラーとなること |
| V-02 | 日付形式 | `planned_date` が `yyyy-MM-dd` 以外の形式でエラーとなること |
| V-03 | 数量チェック | `ordered_qty` が0以下・小数・文字列の場合にエラーとなること |
| V-04 | unit_type | `CASE` / `BALL` / `PIECE` 以外の値でエラーとなること |
| V-05 | 文字数超過 | 各カラムの最大長を超える値でエラーとなること |
| V-06 | 取引先不在 | 存在しない `partner_code` でエラーとなること |
| V-07 | 取引先無効 | `is_active = false` の取引先でエラーとなること |
| V-08 | 取引先種別 | `partner_type = 'SUPPLIER'`（仕入先のみ）の取引先でエラーとなること |
| V-09 | 商品不在 | 存在しない `product_code` でエラーとなること |
| V-10 | 商品無効 | `is_active = false` の商品でエラーとなること |
| V-11 | 出荷禁止 | `shipment_stop_flag = true` の商品でエラーとなること |
| V-12 | 伝票内重複 | 同一伝票内に同一 `product_code` が2行以上でエラーとなること |
| V-13 | ヘッダ不正 | ヘッダ行のカラム数・カラム名が不正な場合にエラーとなること |
| V-14 | 行数超過 | 10,001行以上のCSVでエラーとなること |
| V-15 | データ行0件 | ヘッダのみのCSVでエラーとなること |
| V-16 | 複数エラー | 1行に複数のエラーがある場合、全エラーが返却されること |

### 6.3 異常系

| No | テスト観点 | テスト内容 |
|----|----------|----------|
| E-01 | DB登録エラー | INSERT失敗時に全伝票がロールバックされ、ファイルが `pending` に残留すること |
| E-02 | 伝票番号重複 | UNIQUE制約違反が発生した場合にトランザクションがロールバックされること |
| E-03 | 採番上限 | 同一日付で9999件超の採番でエラーとなること |
| E-04 | Blob読み取り失敗 | Blob Storage接続エラー時にリトライ後、適切なエラーメッセージが返却されること |
| E-05 | Blob移動失敗 | DB登録成功後にBlob移動が失敗した場合、`if_executions.blob_move_failed = true` が記録されること |
| E-06 | 再バリデーション不一致 | バリデーション後〜取り込み実行間にマスタが変更された場合、再バリデーションでエラーが検出されること |
| E-07 | 文字コード不正 | UTF-8以外のファイルでL1バリデーションエラーとなること |

### 6.4 境界値

| No | テスト観点 | テスト内容 |
|----|----------|----------|
| B-01 | 最小データ | 1行のみのCSV（ヘッダ+1データ行）が正常に取り込めること |
| B-02 | 最大データ | 10,000行のCSVが正常に取り込めること |
| B-03 | 数量境界 | `ordered_qty = 1` が正常に取り込めること |
| B-04 | 文字数最大 | 各カラムが最大長の値で正常に取り込めること |
| B-05 | 備考空文字 | `note` が空文字のCSV行が正常に取り込めること |
| B-06 | 日付境界 | 当日日付の `planned_date` が正常に取り込めること |
