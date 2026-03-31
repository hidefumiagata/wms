package com.wms.interfacing.service;

import com.wms.master.entity.Partner;
import com.wms.master.entity.PartnerType;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import com.wms.outbound.entity.OutboundSlip;
import com.wms.outbound.entity.OutboundSlipLine;
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
 * IFX-002 受注CSV固有のバリデーション・変換処理。
 */
@Component
@RequiredArgsConstructor
public class OrderCsvProcessor {

    private static final String[] EXPECTED_HEADER = {
            "partner_code", "planned_date", "product_code", "unit_type",
            "ordered_qty", "note"
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
     */
    public InboundPlanCsvProcessor.ValidationResult validate(
            List<String[]> dataRows,
            InboundPlanCsvProcessor.MasterCache masterCache,
            LocalDate businessDate) {

        List<InboundPlanCsvProcessor.RowError> rowErrors = new ArrayList<>();
        int successCount = 0;

        // L2〜L4: 行レベルバリデーション
        for (int i = 0; i < dataRows.size(); i++) {
            String[] row = dataRows.get(i);
            int rowNumber = i + 1;
            List<InboundPlanCsvProcessor.FieldError> errors =
                    validateRow(row, masterCache, businessDate);
            if (errors.isEmpty()) {
                successCount++;
            } else {
                rowErrors.add(new InboundPlanCsvProcessor.RowError(rowNumber, errors));
            }
        }

        // L5: クロスバリデーション（伝票内の同一商品重複チェック）
        List<InboundPlanCsvProcessor.RowError> crossErrors = validateCross(dataRows, rowErrors);
        Map<Integer, InboundPlanCsvProcessor.RowError> errorMap = new LinkedHashMap<>();
        for (InboundPlanCsvProcessor.RowError re : rowErrors) {
            errorMap.put(re.rowNumber(), re);
        }
        for (InboundPlanCsvProcessor.RowError ce : crossErrors) {
            InboundPlanCsvProcessor.RowError existing = errorMap.get(ce.rowNumber());
            if (existing != null) {
                List<InboundPlanCsvProcessor.FieldError> merged =
                        new ArrayList<>(existing.errors());
                merged.addAll(ce.errors());
                errorMap.put(ce.rowNumber(),
                        new InboundPlanCsvProcessor.RowError(ce.rowNumber(), merged));
            } else {
                errorMap.put(ce.rowNumber(), ce);
                successCount--;
            }
        }

        List<InboundPlanCsvProcessor.RowError> allErrors = new ArrayList<>(errorMap.values());
        int errorCount = dataRows.size() - successCount;

        return new InboundPlanCsvProcessor.ValidationResult(
                dataRows.size(), successCount, errorCount, allErrors);
    }

    private List<InboundPlanCsvProcessor.FieldError> validateRow(
            String[] row,
            InboundPlanCsvProcessor.MasterCache masterCache,
            LocalDate businessDate) {

        List<InboundPlanCsvProcessor.FieldError> errors = new ArrayList<>();

        String partnerCode = row.length > 0 ? CsvParser.normalizeEmpty(row[0]) : null;
        String plannedDateStr = row.length > 1 ? CsvParser.normalizeEmpty(row[1]) : null;
        String productCode = row.length > 2 ? CsvParser.normalizeEmpty(row[2]) : null;
        String unitType = row.length > 3 ? CsvParser.normalizeEmpty(row[3]) : null;
        String orderedQtyStr = row.length > 4 ? CsvParser.normalizeEmpty(row[4]) : null;
        String note = row.length > 5 ? CsvParser.normalizeEmpty(row[5]) : null;

        // L2: 形式チェック
        // partner_code: 必須・50文字以内
        if (partnerCode == null) {
            errors.add(new InboundPlanCsvProcessor.FieldError("partner_code", "WMS-E-IFX-201",
                    "出荷先コードは必須です"));
        } else if (partnerCode.length() > 50) {
            errors.add(new InboundPlanCsvProcessor.FieldError("partner_code", "WMS-E-IFX-201",
                    "出荷先コードは50文字以内で入力してください"));
        }

        // planned_date: 必須・yyyy-MM-dd形式
        LocalDate plannedDate = null;
        if (plannedDateStr == null) {
            errors.add(new InboundPlanCsvProcessor.FieldError("planned_date", "WMS-E-IFX-202",
                    "出荷予定日は必須です"));
        } else {
            try {
                plannedDate = LocalDate.parse(plannedDateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e) {
                errors.add(new InboundPlanCsvProcessor.FieldError("planned_date", "WMS-E-IFX-202",
                        "出荷予定日はyyyy-MM-dd形式で入力してください"));
            }
        }

        // product_code: 必須・50文字以内
        if (productCode == null) {
            errors.add(new InboundPlanCsvProcessor.FieldError("product_code", "WMS-E-IFX-203",
                    "商品コードは必須です"));
        } else if (productCode.length() > 50) {
            errors.add(new InboundPlanCsvProcessor.FieldError("product_code", "WMS-E-IFX-203",
                    "商品コードは50文字以内で入力してください"));
        }

        // unit_type: 必須・CASE/BALL/PIECE
        if (unitType == null) {
            errors.add(new InboundPlanCsvProcessor.FieldError("unit_type", "WMS-E-IFX-204",
                    "荷姿は必須です"));
        } else if (!VALID_UNIT_TYPES.contains(unitType)) {
            errors.add(new InboundPlanCsvProcessor.FieldError("unit_type", "WMS-E-IFX-204",
                    "荷姿はCASE/BALL/PIECEのいずれかで入力してください"));
        }

        // ordered_qty: 必須・正の整数
        if (orderedQtyStr == null) {
            errors.add(new InboundPlanCsvProcessor.FieldError("ordered_qty", "WMS-E-IFX-205",
                    "受注数量は必須です"));
        } else {
            try {
                int qty = Integer.parseInt(orderedQtyStr);
                if (qty < 1) {
                    errors.add(new InboundPlanCsvProcessor.FieldError("ordered_qty", "WMS-E-IFX-205",
                            "受注数量は1以上の正の整数で入力してください"));
                }
            } catch (NumberFormatException e) {
                errors.add(new InboundPlanCsvProcessor.FieldError("ordered_qty", "WMS-E-IFX-205",
                        "受注数量は1以上の正の整数で入力してください"));
            }
        }

        // note: 500文字以内
        if (note != null && note.length() > 500) {
            errors.add(new InboundPlanCsvProcessor.FieldError("note", "WMS-E-IFX-206",
                    "備考は500文字以内で入力してください"));
        }

        // L3: マスタ参照バリデーション
        if (partnerCode != null && partnerCode.length() <= 50) {
            Partner partner = masterCache.getPartner(partnerCode);
            if (partner == null) {
                errors.add(new InboundPlanCsvProcessor.FieldError("partner_code", "WMS-E-IFX-301",
                        "取引先コード（" + partnerCode + "）が取引先マスタに存在しません"));
            } else if (!partner.getIsActive()) {
                errors.add(new InboundPlanCsvProcessor.FieldError("partner_code", "WMS-E-IFX-302",
                        "取引先コード（" + partnerCode + "）は無効化されています"));
            } else if (partner.getPartnerType() != PartnerType.CUSTOMER
                    && partner.getPartnerType() != PartnerType.BOTH) {
                errors.add(new InboundPlanCsvProcessor.FieldError("partner_code", "WMS-E-IFX-303",
                        "取引先コード（" + partnerCode + "）は出荷先ではありません"));
            }
        }

        if (productCode != null && productCode.length() <= 50) {
            Product product = masterCache.getProduct(productCode);
            if (product == null) {
                errors.add(new InboundPlanCsvProcessor.FieldError("product_code", "WMS-E-IFX-304",
                        "商品コード（" + productCode + "）が商品マスタに存在しません"));
            } else if (!product.getIsActive()) {
                errors.add(new InboundPlanCsvProcessor.FieldError("product_code", "WMS-E-IFX-305",
                        "商品コード（" + productCode + "）は無効化されています"));
            } else if (product.getShipmentStopFlag()) {
                errors.add(new InboundPlanCsvProcessor.FieldError("product_code", "WMS-E-IFX-306",
                        "商品コード（" + productCode + "）は出荷禁止が設定されています"));
            }
        }

        // L4: IFX-002には業務ルールバリデーションなし（日付前方チェックはIFX-001のみ）

        return errors;
    }

    /**
     * L5: クロスバリデーション — 同一伝票内の同一商品重複チェック。
     */
    private List<InboundPlanCsvProcessor.RowError> validateCross(
            List<String[]> dataRows,
            List<InboundPlanCsvProcessor.RowError> existingErrors) {

        Set<Integer> errorRowNumbers = existingErrors.stream()
                .map(InboundPlanCsvProcessor.RowError::rowNumber)
                .collect(Collectors.toSet());

        // グルーピング: partner_code + planned_date → product_code → rowNumbers
        Map<String, Map<String, List<Integer>>> slipProductRows = new LinkedHashMap<>();
        for (int i = 0; i < dataRows.size(); i++) {
            int rowNumber = i + 1;
            if (errorRowNumbers.contains(rowNumber)) {
                continue;
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

        List<InboundPlanCsvProcessor.RowError> crossErrors = new ArrayList<>();
        for (Map<String, List<Integer>> productRows : slipProductRows.values()) {
            for (Map.Entry<String, List<Integer>> entry : productRows.entrySet()) {
                if (entry.getValue().size() > 1) {
                    for (int i = 1; i < entry.getValue().size(); i++) {
                        int rowNum = entry.getValue().get(i);
                        crossErrors.add(new InboundPlanCsvProcessor.RowError(rowNum, List.of(
                                new InboundPlanCsvProcessor.FieldError("product_code",
                                        "WMS-E-IFX-502",
                                        "同一伝票内に同一商品コード（" + entry.getKey()
                                                + "）が重複しています"))));
                    }
                }
            }
        }
        return crossErrors;
    }

    /**
     * バリデーション成功行からOutboundSlipエンティティ群を構築する。
     */
    public List<OutboundSlip> buildSlips(
            List<String[]> dataRows,
            InboundPlanCsvProcessor.ValidationResult validationResult,
            InboundPlanCsvProcessor.MasterCache masterCache,
            Long warehouseId,
            InboundPlanCsvProcessor.SlipNumberGenerator slipNumberGenerator,
            LocalDate businessDate,
            Long currentUserId) {

        Set<Integer> errorRows = validationResult.getRowErrors().stream()
                .map(InboundPlanCsvProcessor.RowError::rowNumber)
                .collect(Collectors.toSet());

        // 成功行のみ抽出
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

        grouped.entrySet().removeIf(e -> e.getValue().isEmpty());

        Warehouse warehouse = masterCache.getWarehouse();
        List<OutboundSlip> slips = new ArrayList<>();

        for (Map.Entry<String, List<IndexedRow>> entry : grouped.entrySet()) {
            List<IndexedRow> rows = entry.getValue();
            String[] firstRow = rows.get(0).data();

            String partnerCode = CsvParser.normalizeEmpty(firstRow[0]);
            LocalDate plannedDate = LocalDate.parse(CsvParser.normalizeEmpty(firstRow[1]));
            Partner partner = masterCache.getPartner(partnerCode);

            String slipNumber = slipNumberGenerator.generate(businessDate);

            OutboundSlip slip = OutboundSlip.builder()
                    .slipNumber(slipNumber)
                    .slipType("NORMAL")
                    .warehouseId(warehouseId)
                    .warehouseCode(warehouse.getWarehouseCode())
                    .warehouseName(warehouse.getWarehouseName())
                    .partnerId(partner.getId())
                    .partnerCode(partner.getPartnerCode())
                    .partnerName(partner.getPartnerName())
                    .plannedDate(plannedDate)
                    .status("ORDERED")
                    .build();

            int lineNo = 1;
            for (IndexedRow row : rows) {
                String prodCode = CsvParser.normalizeEmpty(row.data()[2]);
                Product product = masterCache.getProduct(prodCode);
                String unitType = CsvParser.normalizeEmpty(row.data()[3]);
                int orderedQty = Integer.parseInt(CsvParser.normalizeEmpty(row.data()[4]));

                OutboundSlipLine line = OutboundSlipLine.builder()
                        .lineNo(lineNo++)
                        .productId(product.getId())
                        .productCode(product.getProductCode())
                        .productName(product.getProductName())
                        .unitType(unitType)
                        .orderedQty(orderedQty)
                        .shippedQty(0)
                        .lineStatus("ORDERED")
                        .build();

                slip.addLine(line);
            }

            slips.add(slip);
        }

        return slips;
    }

    private record IndexedRow(int index, String[] data) {
    }
}
