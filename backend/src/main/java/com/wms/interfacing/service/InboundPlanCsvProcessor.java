package com.wms.interfacing.service;

import com.wms.inbound.entity.InboundSlip;
import com.wms.inbound.entity.InboundSlipLine;
import com.wms.interfacing.model.FieldError;
import com.wms.interfacing.model.MasterCache;
import com.wms.interfacing.model.RowError;
import com.wms.interfacing.model.SlipNumberGenerator;
import com.wms.interfacing.model.ValidationResult;
import com.wms.master.entity.Partner;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * IFX-001 入荷予定CSV固有のバリデーション・変換処理。
 */
@Component
@RequiredArgsConstructor
public class InboundPlanCsvProcessor {

    private static final String[] EXPECTED_HEADER = {
            "partner_code", "planned_date", "product_code", "unit_type",
            "planned_qty", "lot_number", "expiry_date", "note"
    };
    private static final int EXPECTED_COLUMN_COUNT = EXPECTED_HEADER.length;
    private static final Set<String> VALID_UNIT_TYPES = Set.of("CASE", "BALL", "PIECE");

    /**
     * ヘッダ行を検証する（L1）。
     */
    public void validateHeader(String[] header) {
        if (header.length != EXPECTED_COLUMN_COUNT) {
            throw new CsvParser.CsvParseException("WMS-E-IFX-003",
                    "ヘッダ行のカラム数が不正です（期待: " + EXPECTED_COLUMN_COUNT
                            + ", 実際: " + header.length + "）");
        }
        for (int i = 0; i < EXPECTED_HEADER.length; i++) {
            String actual = header[i].trim().toLowerCase();
            if (!EXPECTED_HEADER[i].equals(actual)) {
                throw new CsvParser.CsvParseException("WMS-E-IFX-004",
                        "ヘッダ行のカラム名が不正です（" + header[i].trim() + "）");
            }
        }
    }

    /**
     * 全データ行をバリデーションする（L2〜L5）。
     *
     * @return 行単位のバリデーション結果（エラーがある行のみ）
     */
    public ValidationResult validate(List<String[]> dataRows, MasterCache masterCache,
                                     LocalDate businessDate) {
        List<RowError> rowErrors = new ArrayList<>();
        int successCount = 0;

        // L2〜L4: 行レベルバリデーション
        for (int i = 0; i < dataRows.size(); i++) {
            String[] row = dataRows.get(i);
            int rowNumber = i + 1;
            List<FieldError> errors = validateRow(row, masterCache, businessDate);
            if (errors.isEmpty()) {
                successCount++;
            } else {
                rowErrors.add(new RowError(rowNumber, errors));
            }
        }

        // L5: クロスバリデーション（伝票内の同一商品重複チェック）
        List<RowError> crossErrors = validateCross(dataRows, rowErrors);
        // 既存のrowErrorsにクロスバリデーションエラーを統合
        Map<Integer, RowError> errorMap = new LinkedHashMap<>();
        for (RowError re : rowErrors) {
            errorMap.put(re.rowNumber(), re);
        }
        for (RowError ce : crossErrors) {
            RowError existing = errorMap.get(ce.rowNumber());
            if (existing != null) {
                List<FieldError> merged = new ArrayList<>(existing.errors());
                merged.addAll(ce.errors());
                errorMap.put(ce.rowNumber(), new RowError(ce.rowNumber(), merged));
            } else {
                errorMap.put(ce.rowNumber(), ce);
                successCount--; // previously success, now error
            }
        }

        List<RowError> allErrors = new ArrayList<>(errorMap.values());
        int errorCount = dataRows.size() - successCount;

        return new ValidationResult(dataRows.size(), successCount, errorCount, allErrors);
    }

    private List<FieldError> validateRow(String[] row, MasterCache masterCache,
                                         LocalDate businessDate) {
        List<FieldError> errors = new ArrayList<>();

        // カラム数チェック
        String partnerCode = row.length > 0 ? CsvParser.normalizeEmpty(row[0]) : null;
        String plannedDateStr = row.length > 1 ? CsvParser.normalizeEmpty(row[1]) : null;
        String productCode = row.length > 2 ? CsvParser.normalizeEmpty(row[2]) : null;
        String unitType = row.length > 3 ? CsvParser.normalizeEmpty(row[3]) : null;
        String plannedQtyStr = row.length > 4 ? CsvParser.normalizeEmpty(row[4]) : null;
        String lotNumber = row.length > 5 ? CsvParser.normalizeEmpty(row[5]) : null;
        String expiryDateStr = row.length > 6 ? CsvParser.normalizeEmpty(row[6]) : null;
        String note = row.length > 7 ? CsvParser.normalizeEmpty(row[7]) : null;

        // L2: 形式チェック
        // partner_code: 必須・50文字以内
        if (partnerCode == null) {
            errors.add(new FieldError("partner_code", "WMS-E-IFX-101",
                    "仕入先コードは必須です"));
        } else if (partnerCode.length() > 50) {
            errors.add(new FieldError("partner_code", "WMS-E-IFX-101",
                    "仕入先コードは50文字以内で入力してください"));
        }

        // planned_date: 必須・yyyy-MM-dd形式
        LocalDate plannedDate = null;
        if (plannedDateStr == null) {
            errors.add(new FieldError("planned_date", "WMS-E-IFX-102",
                    "入荷予定日は必須です"));
        } else {
            try {
                plannedDate = LocalDate.parse(plannedDateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e) {
                errors.add(new FieldError("planned_date", "WMS-E-IFX-102",
                        "入荷予定日はyyyy-MM-dd形式で入力してください"));
            }
        }

        // product_code: 必須・50文字以内
        if (productCode == null) {
            errors.add(new FieldError("product_code", "WMS-E-IFX-103",
                    "商品コードは必須です"));
        } else if (productCode.length() > 50) {
            errors.add(new FieldError("product_code", "WMS-E-IFX-103",
                    "商品コードは50文字以内で入力してください"));
        }

        // unit_type: 必須・CASE/BALL/PIECE
        if (unitType == null) {
            errors.add(new FieldError("unit_type", "WMS-E-IFX-104",
                    "荷姿は必須です"));
        } else if (!VALID_UNIT_TYPES.contains(unitType)) {
            errors.add(new FieldError("unit_type", "WMS-E-IFX-104",
                    "荷姿はCASE/BALL/PIECEのいずれかで入力してください"));
        }

        // planned_qty: 必須・正の整数
        if (plannedQtyStr == null) {
            errors.add(new FieldError("planned_qty", "WMS-E-IFX-105",
                    "入荷予定数量は必須です"));
        } else {
            try {
                int qty = Integer.parseInt(plannedQtyStr);
                if (qty < 1) {
                    errors.add(new FieldError("planned_qty", "WMS-E-IFX-105",
                            "入荷予定数量は1以上の正の整数で入力してください"));
                }
            } catch (NumberFormatException e) {
                errors.add(new FieldError("planned_qty", "WMS-E-IFX-105",
                        "入荷予定数量は1以上の正の整数で入力してください"));
            }
        }

        // lot_number: 100文字以内
        if (lotNumber != null && lotNumber.length() > 100) {
            errors.add(new FieldError("lot_number", "WMS-E-IFX-106",
                    "ロット番号は100文字以内で入力してください"));
        }

        // expiry_date: yyyy-MM-dd形式
        LocalDate expiryDate = null;
        if (expiryDateStr != null) {
            try {
                expiryDate = LocalDate.parse(expiryDateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e) {
                errors.add(new FieldError("expiry_date", "WMS-E-IFX-107",
                        "期限日はyyyy-MM-dd形式で入力してください"));
            }
        }

        // note: 500文字以内
        if (note != null && note.length() > 500) {
            errors.add(new FieldError("note", "WMS-E-IFX-108",
                    "備考は500文字以内で入力してください"));
        }

        // L3: マスタ参照バリデーション
        if (partnerCode != null && partnerCode.length() <= 50) {
            Partner partner = masterCache.getPartner(partnerCode);
            if (partner == null) {
                errors.add(new FieldError("partner_code", "WMS-E-IFX-301",
                        "取引先コード（" + partnerCode + "）が取引先マスタに存在しません"));
            } else if (!partner.getIsActive()) {
                errors.add(new FieldError("partner_code", "WMS-E-IFX-302",
                        "取引先コード（" + partnerCode + "）は無効化されています"));
            } else if (partner.getPartnerType() != com.wms.master.entity.PartnerType.SUPPLIER
                    && partner.getPartnerType() != com.wms.master.entity.PartnerType.BOTH) {
                errors.add(new FieldError("partner_code", "WMS-E-IFX-303",
                        "取引先コード（" + partnerCode + "）は仕入先ではありません"));
            }
        }

        Product product = null;
        if (productCode != null && productCode.length() <= 50) {
            product = masterCache.getProduct(productCode);
            if (product == null) {
                errors.add(new FieldError("product_code", "WMS-E-IFX-304",
                        "商品コード（" + productCode + "）が商品マスタに存在しません"));
            } else if (!product.getIsActive()) {
                errors.add(new FieldError("product_code", "WMS-E-IFX-305",
                        "商品コード（" + productCode + "）は無効化されています"));
            }
        }

        // L4: 業務ルールバリデーション
        if (plannedDate != null && plannedDate.isBefore(businessDate)) {
            errors.add(new FieldError("planned_date", "WMS-E-IFX-401",
                    "入荷予定日は現在営業日以降の日付を指定してください"));
        }

        if (product != null && product.getIsActive()) {
            if (product.getLotManageFlag() && lotNumber == null) {
                errors.add(new FieldError("lot_number", "WMS-E-IFX-402",
                        "ロット管理対象商品のためロット番号は必須です"));
            }
            if (product.getExpiryManageFlag()) {
                if (expiryDate == null && expiryDateStr == null) {
                    errors.add(new FieldError("expiry_date", "WMS-E-IFX-403",
                            "期限管理対象商品のため期限日は必須です"));
                } else if (expiryDate != null && !expiryDate.isAfter(businessDate)) {
                    errors.add(new FieldError("expiry_date", "WMS-E-IFX-404",
                            "期限日は現在営業日より後の日付を指定してください"));
                }
            }
        }

        return errors;
    }

    /**
     * L5: クロスバリデーション — 同一伝票内の同一商品重複チェック。
     */
    private List<RowError> validateCross(List<String[]> dataRows,
                                         List<RowError> existingErrors) {
        Set<Integer> errorRowNumbers = existingErrors.stream()
                .map(RowError::rowNumber)
                .collect(Collectors.toSet());

        // グルーピング: partner_code + planned_date → product_code → rowNumbers
        Map<String, Map<String, List<Integer>>> slipProductRows = new LinkedHashMap<>();
        for (int i = 0; i < dataRows.size(); i++) {
            int rowNumber = i + 1;
            if (errorRowNumbers.contains(rowNumber)) {
                continue; // L2〜L4でエラーの行はスキップ
            }
            String[] row = dataRows.get(i);
            String partnerCode = CsvParser.normalizeEmpty(row[0]);
            String plannedDate = CsvParser.normalizeEmpty(row[1]);
            String productCode = CsvParser.normalizeEmpty(row[2]);
            if (partnerCode == null || plannedDate == null || productCode == null) {
                continue;
            }
            String slipKey = partnerCode + "|" + plannedDate;
            slipProductRows
                    .computeIfAbsent(slipKey, k -> new LinkedHashMap<>())
                    .computeIfAbsent(productCode, k -> new ArrayList<>())
                    .add(rowNumber);
        }

        List<RowError> crossErrors = new ArrayList<>();
        for (Map<String, List<Integer>> productRows : slipProductRows.values()) {
            for (Map.Entry<String, List<Integer>> entry : productRows.entrySet()) {
                if (entry.getValue().size() > 1) {
                    // 2番目以降の重複行にエラーを付与
                    for (int i = 1; i < entry.getValue().size(); i++) {
                        int rowNum = entry.getValue().get(i);
                        crossErrors.add(new RowError(rowNum, List.of(
                                new FieldError("product_code", "WMS-E-IFX-501",
                                        "同一伝票内に同一商品コード（" + entry.getKey()
                                                + "）が重複しています"))));
                    }
                }
            }
        }
        return crossErrors;
    }

    /**
     * バリデーション成功行からInboundSlipエンティティ群を構築する。
     */
    public List<InboundSlip> buildSlips(List<String[]> dataRows, ValidationResult validationResult,
                                        MasterCache masterCache, Long warehouseId,
                                        SlipNumberGenerator slipNumberGenerator,
                                        LocalDate businessDate, Long currentUserId) {
        // エラー行番号を抽出
        Set<Integer> errorRows = validationResult.getRowErrors().stream()
                .map(RowError::rowNumber)
                .collect(Collectors.toSet());

        // 成功行のみ抽出（1始まりrowNumber → 0始まりindex）
        List<IndexedRow> successRows = new ArrayList<>();
        for (int i = 0; i < dataRows.size(); i++) {
            if (!errorRows.contains(i + 1)) {
                successRows.add(new IndexedRow(i, dataRows.get(i)));
            }
        }

        // partner_code + planned_date でグルーピング
        Map<String, List<IndexedRow>> grouped = new LinkedHashMap<>();
        for (IndexedRow row : successRows) {
            String partnerCode = CsvParser.normalizeEmpty(row.data()[0]);
            String plannedDate = CsvParser.normalizeEmpty(row.data()[1]);
            String key = partnerCode + "|" + plannedDate;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        // 空グループを除外
        grouped.entrySet().removeIf(e -> e.getValue().isEmpty());

        Warehouse warehouse = masterCache.getWarehouse();
        List<InboundSlip> slips = new ArrayList<>();

        for (Map.Entry<String, List<IndexedRow>> entry : grouped.entrySet()) {
            List<IndexedRow> rows = entry.getValue();
            String[] firstRow = rows.get(0).data();

            String partnerCode = CsvParser.normalizeEmpty(firstRow[0]);
            LocalDate plannedDate = LocalDate.parse(CsvParser.normalizeEmpty(firstRow[1]));
            Partner partner = masterCache.getPartner(partnerCode);
            String note = CsvParser.normalizeEmpty(firstRow[7]);

            String slipNumber = slipNumberGenerator.generate(businessDate);

            InboundSlip slip = InboundSlip.builder()
                    .slipNumber(slipNumber)
                    .slipType("NORMAL")
                    .warehouseId(warehouseId)
                    .warehouseCode(warehouse.getWarehouseCode())
                    .warehouseName(warehouse.getWarehouseName())
                    .partnerId(partner.getId())
                    .partnerCode(partner.getPartnerCode())
                    .partnerName(partner.getPartnerName())
                    .plannedDate(plannedDate)
                    .status("PLANNED")
                    .note(note)
                    .build();

            int lineNo = 1;
            for (IndexedRow row : rows) {
                String prodCode = CsvParser.normalizeEmpty(row.data()[2]);
                Product product = masterCache.getProduct(prodCode);
                String unitType = CsvParser.normalizeEmpty(row.data()[3]);
                int plannedQty = Integer.parseInt(CsvParser.normalizeEmpty(row.data()[4]));
                String lotNum = CsvParser.normalizeEmpty(row.data()[5]);
                String expiryStr = CsvParser.normalizeEmpty(row.data()[6]);
                LocalDate expiryDate = expiryStr != null
                        ? LocalDate.parse(expiryStr) : null;

                InboundSlipLine line = InboundSlipLine.builder()
                        .lineNo(lineNo++)
                        .productId(product.getId())
                        .productCode(product.getProductCode())
                        .productName(product.getProductName())
                        .unitType(unitType)
                        .plannedQty(plannedQty)
                        .lotNumber(lotNum)
                        .expiryDate(expiryDate)
                        .lineStatus("PENDING")
                        .build();

                slip.addLine(line);
            }

            slips.add(slip);
        }

        return slips;
    }

    // --- Inner types ---

    private record IndexedRow(int index, String[] data) {
    }
}
