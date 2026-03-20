# 機能設計書 — I/F設計 入荷予定取り込み（IFX-001）

> **業務要件**: [07-interface.md](../functional-requirements/07-interface.md) を参照
> **方式設計**（CSVフォーマット・バリデーションルール・フロー・クラス構成）: [09-interface-architecture.md](../architecture-design/09-interface-architecture.md) を参照
> **テーブル定義**: [03-transaction-tables.md](../data-model/03-transaction-tables.md)（inbound_slips / inbound_slip_lines / if_executions）を参照
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
| **I/F ID** | IFX-001 |
| **I/F名** | 入荷予定取り込みI/F |
| **方向** | 外部 → WMS |
| **形式** | CSV（UTF-8、BOMなし） |
| **取り込み先テーブル** | `inbound_slips` / `inbound_slip_lines` |
| **取り込み後ステータス** | `PLANNED`（入荷予定） |
| **実装方式** | モック（手動配置） |
| **Blob Storageパス** | [09-interface-architecture.md セクション2](../architecture-design/09-interface-architecture.md#2-blob-storageディレクトリ構成) を参照 |
| **CSVフォーマット** | [09-interface-architecture.md セクション4.2](../architecture-design/09-interface-architecture.md#42-ifx-001-入荷予定取り込みcsvフォーマット) を参照 |
| **バリデーションルール** | [09-interface-architecture.md セクション6](../architecture-design/09-interface-architecture.md#6-データ検証設計バリデーション) を参照 |
| **取り込みフロー** | [09-interface-architecture.md セクション7](../architecture-design/09-interface-architecture.md#7-取り込みフロー詳細設計) を参照 |
| **実行権限** | SYSTEM_ADMIN / WAREHOUSE_MANAGER |

---

## 2. データマッピング詳細

### 2.1 伝票グルーピングロジック

CSVの各データ行を `partner_code` + `planned_date` の組み合わせでグルーピングし、1グループ = 1件の入荷伝票（`inbound_slips`）として登録する。グループ内の各行は入荷明細（`inbound_slip_lines`）の1レコードとなる。

```java
// InboundPlanCsvProcessor.java — グルーピング処理
public Map<InboundSlipKey, List<InboundPlanCsvRow>> groupBySlip(List<InboundPlanCsvRow> validRows) {
    return validRows.stream()
        .collect(Collectors.groupingBy(
            row -> new InboundSlipKey(row.getPartnerCode(), row.getPlannedDate()),
            LinkedHashMap::new,  // CSVの出現順序を保持
            Collectors.toList()
        ));
}

// グルーピングキー
public record InboundSlipKey(String partnerCode, LocalDate plannedDate) {}
```

**グルーピング例**:

| CSV行 | partner_code | planned_date | product_code | グループ | 伝票 |
|:------:|:------------:|:------------:|:------------:|:--------:|:----:|
| 1 | SUP-0001 | 2026-03-20 | PRD-001 | A | 伝票1 |
| 2 | SUP-0001 | 2026-03-20 | PRD-002 | A | 伝票1 |
| 3 | SUP-0002 | 2026-03-21 | PRD-003 | B | 伝票2 |
| 4 | SUP-0001 | 2026-03-21 | PRD-001 | C | 伝票3 |

### 2.2 inbound_slips（ヘッダ）マッピング

| # | 対象カラム | 変換元 | 変換ルール |
|---|----------|--------|----------|
| 1 | `slip_number` | — | 自動採番（[セクション5](#5-伝票採番ロジック) 参照） |
| 2 | `slip_type` | — | 固定値: `NORMAL` |
| 3 | `transfer_slip_number` | — | `NULL`（通常入荷のため） |
| 4 | `warehouse_id` | リクエストパラメータ `warehouseId` | ユーザーが画面で選択中の倉庫ID をそのまま設定 |
| 5 | `warehouse_code` | `warehouses` テーブル | `warehouseId` で warehouses を検索し `warehouse_code` をコピー |
| 6 | `warehouse_name` | `warehouses` テーブル | `warehouseId` で warehouses を検索し `warehouse_name` をコピー |
| 7 | `partner_id` | CSV `partner_code` | `partners` テーブルを `partner_code` で検索し `id` を取得 |
| 8 | `partner_code` | CSV `partner_code` | CSV値をそのままコピー |
| 9 | `partner_name` | `partners` テーブル | 検索結果の `partner_name` をコピー |
| 10 | `planned_date` | CSV `planned_date` | `yyyy-MM-dd` 文字列を `LocalDate` にパースし設定 |
| 11 | `status` | — | 固定値: `PLANNED` |
| 12 | `note` | CSV `note` | グループ内の最初の行（CSV出現順で先頭）の `note` 値を設定。空文字列は `NULL` に変換 |
| 13 | `cancelled_at` | — | `NULL` |
| 13 | `cancelled_by` | — | `NULL` |
| 14 | `created_at` | — | `now()`（JPA `@CreationTimestamp`） |
| 15 | `created_by` | JWTトークン | 認証済みユーザーの `user_id` |
| 16 | `updated_at` | — | `now()`（JPA `@UpdateTimestamp`） |
| 17 | `updated_by` | JWTトークン | 認証済みユーザーの `user_id` |

### 2.3 inbound_slip_lines（明細）マッピング

| # | 対象カラム | 変換元 | 変換ルール |
|---|----------|--------|----------|
| 1 | `inbound_slip_id` | — | 親伝票の `id`（INSERT後に取得） |
| 2 | `line_no` | — | グループ内のCSV行出現順に1始まりの連番を付与 |
| 3 | `product_id` | CSV `product_code` | `products` テーブルを `product_code` で検索し `id` を取得 |
| 4 | `product_code` | CSV `product_code` | CSV値をそのままコピー |
| 5 | `product_name` | `products` テーブル | 検索結果の `product_name` をコピー |
| 6 | `unit_type` | CSV `unit_type` | CSV値をそのまま設定（`CASE` / `BALL` / `PIECE`） |
| 7 | `planned_qty` | CSV `planned_qty` | 整数パース後に設定 |
| 8 | `inspected_qty` | — | `NULL`（入荷予定時点では未検品） |
| 9 | `lot_number` | CSV `lot_number` | CSV値をそのまま設定（空文字列は `NULL` に変換） |
| 10 | `expiry_date` | CSV `expiry_date` | `yyyy-MM-dd` 文字列を `LocalDate` にパース（空は `NULL`） |
| 11 | `putaway_location_id` | — | `NULL`（入庫確定前） |
| 12 | `putaway_location_code` | — | `NULL`（入庫確定前） |
| 13 | `line_status` | — | 固定値: `PENDING` |
| 14 | `inspected_at` | — | `NULL` |
| 15 | `inspected_by` | — | `NULL` |
| 16 | `stored_at` | — | `NULL` |
| 17 | `stored_by` | — | `NULL` |
| 18 | `created_at` | — | `now()`（JPA `@CreationTimestamp`） |
| 19 | `updated_at` | — | `now()`（JPA `@UpdateTimestamp`） |

### 2.4 CSVのnoteカラムのマッピング

CSVフォーマット（[09-interface-architecture.md セクション4.2](../architecture-design/09-interface-architecture.md#42-ifx-001-入荷予定取り込みcsvフォーマット)）で定義されている `note`（備考）カラムは、`inbound_slips.note` にマッピングする。同一伝票（同一 `partner_code` + `planned_date`）内に複数行が存在する場合、最初の行（CSV出現順で先頭）の `note` 値をヘッダの備考として採用する。CSVの `note` 値はバリデーション（L2: 500文字以内チェック）の対象とする。

### 2.5 コード → ID解決の実装

マスタ参照の最適化として、CSV内のユニークなコードを一括検索する（[09-interface-architecture.md セクション10.2](../architecture-design/09-interface-architecture.md#102-マスタ検索の最適化) 参照）。

```java
// InboundPlanCsvProcessor.java — コード→ID解決
public class MasterCache {
    private final Map<String, Partner> partnerMap;   // key: partner_code
    private final Map<String, Product> productMap;   // key: product_code
    private final Warehouse warehouse;

    public static MasterCache build(
            List<InboundPlanCsvRow> rows,
            Long warehouseId,
            PartnerRepository partnerRepo,
            ProductRepository productRepo,
            WarehouseRepository warehouseRepo) {

        // 1. ユニークなコードを抽出
        Set<String> partnerCodes = rows.stream()
            .map(InboundPlanCsvRow::getPartnerCode)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        Set<String> productCodes = rows.stream()
            .map(InboundPlanCsvRow::getProductCode)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        // 2. IN句で一括検索
        Map<String, Partner> partnerMap = partnerRepo
            .findByPartnerCodeIn(partnerCodes).stream()
            .collect(Collectors.toMap(Partner::getPartnerCode, Function.identity()));

        Map<String, Product> productMap = productRepo
            .findByProductCodeIn(productCodes).stream()
            .collect(Collectors.toMap(Product::getProductCode, Function.identity()));

        // 3. 倉庫情報取得
        Warehouse warehouse = warehouseRepo.findById(warehouseId)
            .orElseThrow(() -> new BusinessException("WMS-E-IFX-901",
                "倉庫ID(" + warehouseId + ")が存在しません"));

        return new MasterCache(partnerMap, productMap, warehouse);
    }
}
```

### 2.6 CSVの空文字列の扱い

| CSVの値 | 変換後の値 | 対象カラム |
|---------|----------|----------|
| `""` （空文字列） | `NULL` | `lot_number`, `expiry_date`, `note` |
| `" "` （空白のみ） | `NULL` | `lot_number`, `expiry_date`, `note` |
| 未指定（カンマ連続 `,,`） | `NULL` | `lot_number`, `expiry_date`, `note` |

```java
// CsvParser.java — 空文字列のNULL変換
private String normalizeEmpty(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
}
```

---

## 3. 取り込みSQL

### 3.1 inbound_slips INSERT

```sql
INSERT INTO inbound_slips (
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
    status,
    note,
    cancelled_at,
    cancelled_by,
    created_at,
    created_by,
    updated_at,
    updated_by
) VALUES (
    :slipNumber,          -- 自動採番（セクション5参照）
    'NORMAL',             -- 固定値
    NULL,                 -- 通常入荷のためNULL
    :warehouseId,         -- リクエストパラメータ
    :warehouseCode,       -- warehousesテーブルから取得
    :warehouseName,       -- warehousesテーブルから取得
    :partnerId,           -- partnersテーブルから取得
    :partnerCode,         -- CSVのpartner_code
    :partnerName,         -- partnersテーブルから取得
    :plannedDate,         -- CSVのplanned_date
    'PLANNED',            -- 固定値
    :note,                -- CSVのnote（グループ内最初の行の値）
    NULL,                 -- キャンセル日時
    NULL,                 -- キャンセル者
    NOW(),                -- 作成日時
    :currentUserId,       -- 認証ユーザーID
    NOW(),                -- 更新日時
    :currentUserId        -- 認証ユーザーID
)
RETURNING id;             -- 明細INSERT用にIDを取得
```

### 3.2 inbound_slip_lines INSERT

```sql
INSERT INTO inbound_slip_lines (
    inbound_slip_id,
    line_no,
    product_id,
    product_code,
    product_name,
    unit_type,
    planned_qty,
    inspected_qty,
    lot_number,
    expiry_date,
    putaway_location_id,
    putaway_location_code,
    line_status,
    inspected_at,
    inspected_by,
    stored_at,
    stored_by,
    created_at,
    updated_at
) VALUES (
    :inboundSlipId,       -- 親伝票のID（RETURNING idで取得）
    :lineNo,              -- 1始まり連番
    :productId,           -- productsテーブルから取得
    :productCode,         -- CSVのproduct_code
    :productName,         -- productsテーブルから取得
    :unitType,            -- CSVのunit_type
    :plannedQty,          -- CSVのplanned_qty
    NULL,                 -- 検品数（未検品）
    :lotNumber,           -- CSVのlot_number（NULLable）
    :expiryDate,          -- CSVのexpiry_date（NULLable）
    NULL,                 -- 入庫先ロケーションID（未確定）
    NULL,                 -- 入庫先ロケーションコード（未確定）
    'PENDING',            -- 固定値
    NULL,                 -- 検品日時
    NULL,                 -- 検品者
    NULL,                 -- 入庫日時
    NULL,                 -- 入庫者
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
    'INBOUND_PLAN',       -- 固定値
    :fileName,            -- 元ファイル名（例: INB-PLAN-001.csv）
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

JPA `saveAll()` を使用し、Spring Boot の JDBC バッチINSERT設定で一括登録を行う。

```yaml
# application.yml — バッチINSERT設定
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 100            # 100件ずつバッチINSERT
          order_inserts: true        # INSERT文をテーブル単位で並べ替え
          order_updates: true        # UPDATE文をテーブル単位で並べ替え
        id:
          new_generator_mappings: true
```

```java
// InterfaceService.java — 一括登録
@Transactional
public ImportResult importInboundPlan(ImportRequest request) {
    // ... バリデーション・グルーピング処理（省略）...

    List<InboundSlip> slipsToSave = new ArrayList<>();

    for (Map.Entry<InboundSlipKey, List<InboundPlanCsvRow>> entry : grouped.entrySet()) {
        InboundSlipKey key = entry.getKey();
        List<InboundPlanCsvRow> rows = entry.getValue();

        // ヘッダ構築
        InboundSlip slip = buildInboundSlip(key, masterCache, currentUserId);

        // 明細構築
        int lineNo = 1;
        for (InboundPlanCsvRow row : rows) {
            InboundSlipLine line = buildInboundSlipLine(row, lineNo++, masterCache);
            slip.addLine(line);  // 親子リレーション設定
        }

        slipsToSave.add(slip);
    }

    // JPA saveAll → CascadeType.ALL で明細も一括INSERT
    inboundSlipRepository.saveAll(slipsToSave);

    // 取り込み履歴保存
    saveIfExecution(request, slipsToSave, validationResult);

    return buildImportResult(slipsToSave, validationResult);
}
```

**JPA エンティティのカスケード設定**:

```java
@Entity
@Table(name = "inbound_slips")
public class InboundSlip {
    // ...

    @OneToMany(mappedBy = "inboundSlip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<InboundSlipLine> lines = new ArrayList<>();

    public void addLine(InboundSlipLine line) {
        lines.add(line);
        line.setInboundSlip(this);
    }
}
```

**ID生成戦略**:

PostgreSQL の `bigserial`（`IDENTITY` 戦略）を使用する場合、Hibernate のバッチINSERT は `GenerationType.IDENTITY` では無効になる。これを回避するため `GenerationType.SEQUENCE` を使用する。

```java
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "inbound_slip_seq")
@SequenceGenerator(name = "inbound_slip_seq", sequenceName = "inbound_slips_id_seq",
    allocationSize = 50)  // シーケンスのプリアロケーション
private Long id;
```

---

## 4. トランザクション設計

### 4.1 トランザクション境界

```
┌─────────────────────────────────────────────────────────────────┐
│ InterfaceService.importInboundPlan()                            │
│                                                                 │
│  [トランザクション外]                                              │
│  1. Blob Storage から CSV 読み取り                                │
│  2. 再バリデーション（L1〜L5）                                     │
│  3. 成功行の抽出とグルーピング                                      │
│  4. マスタ一括検索（MasterCache構築）                               │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ @Transactional                                          │    │
│  │                                                         │    │
│  │  5. inbound_slips INSERT（N件）                          │    │
│  │  6. inbound_slip_lines INSERT（M件）                     │    │
│  │  7. if_executions INSERT（1件）                          │    │
│  │                                                         │    │
│  │  → 全て成功: COMMIT                                      │    │
│  │  → いずれか失敗: ROLLBACK（5〜7全てロールバック）           │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  [トランザクション外]                                              │
│  8. Blob ファイルを pending → processed へ移動                   │
│     → 移動失敗時: if_executions の blob_move_failed を                │
│       true に UPDATE（別トランザクション）+ ERROR ログ出力           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 1ファイル = 1トランザクション

1回の取り込み操作（1 CSVファイル）は1トランザクションで処理する。

| 方針 | 説明 |
|------|------|
| **トランザクション粒度** | 1ファイル内で生成される全伝票（複数のinbound_slips + 各inbound_slip_lines）を1トランザクションで一括コミット |
| **根拠** | ファイル単位の整合性を担保する。部分的な伝票登録による不整合を防止 |

### 4.3 SUCCESS_ONLY モードの部分成功

`mode = SUCCESS_ONLY` の場合、バリデーション成功行のみを取り込む。

```
CSV 100行
  ├── バリデーション成功: 95行 → グルーピング → N件の伝票として登録
  └── バリデーションエラー: 5行 → スキップ（DB登録なし）
```

**部分成功の扱い**:

| 項目 | 内容 |
|------|------|
| **DB登録対象** | バリデーション成功行のみ。エラー行から生成される伝票は全て除外 |
| **伝票の完全性** | グルーピング後、1伝票内にエラー行と成功行が混在する場合、成功行のみで伝票を構成する |
| **空伝票の防止** | グルーピング後、全明細がエラーとなった伝票はヘッダも生成しない |
| **取り込み履歴** | `success_count` にDB登録された行数、`error_count` にスキップされた行数を記録 |
| **ステータス** | `mode=SUCCESS_ONLY` の場合は常に `COMPLETED`（成功行0件でも `COMPLETED` + `success_count=0`）。`mode=DISCARD` の場合は `DISCARDED`（[セクション4.4](#44-discardモードの処理) 参照） |

```java
// グルーピング後のフィルタリング
Map<InboundSlipKey, List<InboundPlanCsvRow>> grouped = groupBySlip(successRows);

// 空グループを除外（全明細がエラーのグループ）
grouped.entrySet().removeIf(entry -> entry.getValue().isEmpty());

if (grouped.isEmpty() && request.getMode() == ImportMode.SUCCESS_ONLY) {
    // 全行エラーの場合: DB登録なし、ファイルはprocessedへ移動
    // if_executions に status='COMPLETED', success_count=0 で記録
}
```

### 4.4 DISCARDモードの処理

```
CSV 100行（バリデーションエラー3行含む）
  └── 全件スキップ（DB登録なし）
  └── ファイルを processed へ移動
  └── if_executions に status='DISCARDED' で記録
```

### 4.5 失敗時のロールバック

| 障害パターン | ロールバック範囲 | ファイル状態 | 復旧 |
|------------|--------------|-----------|------|
| DB INSERT中の例外 | 伝票・明細・履歴全てロールバック | pending に残留 | ユーザーが再度取り込み操作 |
| Blob移動失敗（DB登録成功後） | ロールバックなし（DB確定済み） | pending に残留 | 手動でprocessedへ移動 or 履歴で確認 |

詳細は [09-interface-architecture.md セクション7.4](../architecture-design/09-interface-architecture.md#74-トランザクション制御) を参照。

---

## 5. 伝票採番ロジック

### 5.1 採番フォーマット

```
INB-YYYYMMDD-NNNN
```

| 部位 | 説明 | 例 |
|------|------|-----|
| `INB` | 入荷伝票プレフィックス（固定） | `INB` |
| `YYYYMMDD` | 現在営業日（`current_business_date`） | `20260320` |
| `NNNN` | 日付内連番（4桁ゼロ埋め、1始まり） | `0001` |

例: `INB-20260320-0001`, `INB-20260320-0002`, `INB-20260321-0001`

> 手動画面登録（API-INB-002）と同一の採番ルールを使用する（[API-06-inbound.md](API-06-inbound.md) 参照）。

### 5.2 採番方式: SELECT FOR UPDATE

PostgreSQLネイティブシーケンスは日付リセットに対応できないため、`SELECT ... FOR UPDATE` によるアプリケーション採番を使用する。

```java
// SlipNumberGenerator.java
@Component
public class SlipNumberGenerator {

    @Autowired
    private InboundSlipRepository inboundSlipRepository;

    /**
     * 入荷伝票番号を採番する。
     * @param businessDate 現在営業日（採番の日付部分に使用）
     * @return 新しい伝票番号（例: INB-20260320-0001）
     */
    public String generateInboundSlipNumber(LocalDate businessDate) {
        String dateStr = businessDate.format(DateTimeFormatter.BASIC_ISO_DATE); // 20260320
        String prefix = "INB-" + dateStr + "-";

        // 同一日付の最大番号を排他ロック付きで取得
        Optional<String> maxSlipNumber = inboundSlipRepository
            .findMaxSlipNumberByPrefix(prefix);

        int nextSeq;
        if (maxSlipNumber.isPresent()) {
            // "INB-20260320-0003" → 3 → 4
            String seqPart = maxSlipNumber.get().substring(prefix.length());
            nextSeq = Integer.parseInt(seqPart) + 1;
        } else {
            nextSeq = 1;
        }

        if (nextSeq > 9999) {
            throw new BusinessException("WMS-E-IFX-902",
                "伝票番号が上限（9999）に達しました。日付: " + dateStr);
        }

        return prefix + String.format("%04d", nextSeq);
    }
}
```

**Repository クエリ**:

```java
// InboundSlipRepository.java
@Query(value = """
    SELECT slip_number FROM inbound_slips
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

for (InboundSlipKey key : grouped.keySet()) {
    String slipNumber = slipNumberGenerator.generateInboundSlipNumber(businessDate);
    // ... 伝票構築 ...
}
```

### 5.4 日付リセット

- 日付部分は `current_business_date`（現在営業日）を使用する
- 1ファイル内の全伝票は同一営業日で採番される（`planned_date` に関わらず統一）
- 日をまたいでの連番リセットは自動的に発生する（同一日付の既存レコードが無ければ `0001` から開始）
- 手動登録API（[API-06-inbound.md](API-06-inbound.md)）と同一の日付基準を使用する

---

## 6. テスト観点

### 6.1 正常系

| # | テストケース | 検証内容 |
|---|------------|---------|
| N-01 | 1行のCSVファイル取り込み | 1伝票1明細が正しく登録されること |
| N-02 | 複数行・同一伝票のCSV取り込み | 同一 partner_code + planned_date がグルーピングされ、1伝票N明細になること |
| N-03 | 複数伝票に分かれるCSV取り込み | 異なる partner_code / planned_date の組み合わせが別伝票になること |
| N-04 | 最大行数（10,000行）のCSV取り込み | 性能目標（60秒以内）を満たすこと |
| N-05 | ロット番号・期限日ありのCSV取り込み | `lot_number`, `expiry_date` が正しく設定されること |
| N-06 | ロット番号・期限日なしのCSV取り込み | `lot_number`, `expiry_date` が NULL であること |
| N-07 | 備考ありのCSV取り込み | CSVの `note` カラムが `inbound_slips.note` に正しくマッピングされること。同一伝票内に複数行がある場合、最初の行の `note` が採用されること（[セクション2.4](#24-csvのnoteカラムのマッピング) 参照） |
| N-08 | 伝票番号の自動採番 | `INB-YYYYMMDD-NNNN` 形式で採番されること |
| N-09 | 同一日付の複数回取り込み | 連番が正しくインクリメントされること |
| N-10 | 取り込み履歴の記録 | `if_executions` に正しい値が記録されること |
| N-11 | ファイルのprocessedへの移動 | pending から processed へ正しいパスで移動されること |
| N-12 | DISCARDモードの取り込み | DB登録なし、ファイル移動あり、履歴がDISCARDEDで記録されること |

### 6.2 SUCCESS_ONLY（部分成功）

| # | テストケース | 検証内容 |
|---|------------|---------|
| P-01 | エラー行と成功行の混在 | エラー行はスキップされ、成功行のみDB登録されること |
| P-02 | 1伝票内で一部行がエラー | エラー行を除いた明細で伝票が構成されること |
| P-03 | 1伝票の全行がエラー | その伝票はヘッダも生成されないこと |
| P-04 | 全行エラーの場合 | DB登録0件、ファイルは移動、履歴に success_count=0 で記録されること |

### 6.3 バリデーション

バリデーションの詳細テストケースは [09-interface-architecture.md セクション6](../architecture-design/09-interface-architecture.md#6-データ検証設計バリデーション) のルールに基づく。

| # | テストケース | 対応エラーコード |
|---|------------|---------------|
| V-01 | 存在しない partner_code | `WMS-E-IFX-301` |
| V-02 | 無効化された取引先 | `WMS-E-IFX-302` |
| V-03 | CUSTOMER種別の取引先（SUPPLIERまたはBOTHでない） | `WMS-E-IFX-303` |
| V-04 | 存在しない product_code | `WMS-E-IFX-304` |
| V-05 | 無効化された商品 | `WMS-E-IFX-305` |
| V-06 | 過去日付の planned_date | `WMS-E-IFX-401` |
| V-07 | ロット管理商品で lot_number 未入力 | `WMS-E-IFX-402` |
| V-08 | 期限管理商品で expiry_date 未入力 | `WMS-E-IFX-403` |
| V-09 | 期限管理商品で expiry_date が現在営業日以前 | `WMS-E-IFX-404` |
| V-10 | 同一伝票内で同一 product_code の重複 | `WMS-E-IFX-501` |

### 6.4 伝票採番

| # | テストケース | 検証内容 |
|---|------------|---------|
| S-01 | 初回採番 | `INB-YYYYMMDD-0001` が生成されること |
| S-02 | 既存伝票がある日付での採番 | 最大番号+1の連番が生成されること |
| S-03 | 連番が9999に達した場合 | `WMS-E-IFX-902` エラーが発生すること |
| S-04 | 異なる日付の同時採番 | それぞれ独立に採番されること |
| S-05 | 同一日付の並行取り込み | `SELECT FOR UPDATE` により排他制御され、番号が重複しないこと |

### 6.5 トランザクション・異常系

| # | テストケース | 検証内容 |
|---|------------|---------|
| T-01 | DB INSERT中の例外 | 全伝票・全明細・履歴がロールバックされること。ファイルはpendingに残留 |
| T-02 | Blob移動失敗 | DB登録は確定済み。`blob_move_failed=true` で履歴が更新されること |
| T-03 | Blob読み取り失敗 | リトライ（3回）後にエラーレスポンスが返ること |
| T-04 | バリデーション後にマスタが変更された場合 | 取り込み実行時の再バリデーションでエラーとなること |

### 6.6 データ整合性

| # | テストケース | 検証内容 |
|---|------------|---------|
| D-01 | partner_name のスナップショット | 取り込み時点の `partner_name` が伝票に保存されること（後でマスタ変更しても伝票は不変） |
| D-02 | product_name のスナップショット | 取り込み時点の `product_name` が明細に保存されること |
| D-03 | warehouse_code/name のスナップショット | 取り込み時点の倉庫情報が伝票に保存されること |
| D-04 | line_no の連番性 | 1伝票内の明細行番号が1始まりの連番であること |
| D-05 | 空文字列のNULL変換 | CSVの空値が正しくNULLに変換されること |
