package com.wms.report.service;

import com.wms.generated.model.PickingInstructionReportItem;
import com.wms.generated.model.ReportFormat;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.WarehouseRepository;
import com.wms.outbound.entity.PickingInstruction;
import com.wms.outbound.repository.PickingInstructionRepository;
import com.wms.report.repository.OutboundReportRepository;
import com.wms.report.repository.projection.PickingInstructionRow;
import com.wms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.wms.report.service.CsvGenerationService.fmtDate;
import static com.wms.report.service.CsvGenerationService.fmtInteger;
import static com.wms.report.service.CsvGenerationService.fmtOrDash;
import static com.wms.report.service.ReportServiceUtils.PICKING_STATUS_LABELS;
import static com.wms.report.service.ReportServiceUtils.formatWarehouseName;
import static com.wms.report.service.ReportServiceUtils.getCurrentUserName;
import static com.wms.report.service.ReportServiceUtils.todayFileDate;

/**
 * RPT-12: ピッキング指示書サービス。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PickingInstructionReportService {

    private final OutboundReportRepository outboundReportRepository;
    private final PickingInstructionRepository pickingInstructionRepository;
    private final WarehouseRepository warehouseRepository;
    private final ReportExportService reportExportService;

    private static final String[] CSV_HEADERS = {
            "ロケーションコード", "商品コード", "商品名", "荷姿",
            "指示数量", "出荷伝票番号", "出荷先名", "出荷予定日", "ロット番号"
    };

    public ResponseEntity<List<PickingInstructionReportItem>> generate(
            Long pickingInstructionId, ReportFormat format) {

        log.info("RPT-12 ピッキング指示書生成開始: pickingInstructionId={}, format={}",
                pickingInstructionId, format);

        PickingInstruction instruction = pickingInstructionRepository.findById(pickingInstructionId)
                .orElseThrow(() -> ResourceNotFoundException.of("PICKING_NOT_FOUND", "ピッキング指示", pickingInstructionId));

        Warehouse warehouse = warehouseRepository.findById(instruction.getWarehouseId())
                .orElseThrow(() -> ResourceNotFoundException.of("WAREHOUSE_NOT_FOUND", "倉庫", instruction.getWarehouseId()));

        String warehouseName = formatWarehouseName(warehouse);

        List<PickingInstructionRow> rows = outboundReportRepository.findPickingInstructionReportData(pickingInstructionId);

        List<PickingInstructionReportItem> items = rows.stream()
                .map(this::toReportItem)
                .toList();

        Map<String, Object> extraVars = new HashMap<>();
        extraVars.put("instructionNumber", instruction.getInstructionNumber());
        extraVars.put("status", PICKING_STATUS_LABELS.getOrDefault(
                instruction.getStatus(), instruction.getStatus()));

        ReportMeta meta = new ReportMeta(
                "ピッキング指示書",
                "rpt-12-picking-instruction",
                "picking_instruction_" + todayFileDate(),
                warehouseName,
                getCurrentUserName(),
                "指示No: " + instruction.getInstructionNumber(),
                CSV_HEADERS,
                row -> csvRowMapper((PickingInstructionReportItem) row),
                extraVars
        );

        log.info("RPT-12 ピッキング指示書生成完了: pickingInstructionId={}, 件数={}",
                pickingInstructionId, items.size());
        return reportExportService.export(items, format, meta);
    }

    private PickingInstructionReportItem toReportItem(PickingInstructionRow row) {
        PickingInstructionReportItem item = new PickingInstructionReportItem();
        item.setLocationCode(row.getLocationCode());
        item.setProductCode(row.getProductCode());
        item.setProductName(row.getProductName());
        item.setUnitType(row.getUnitType());
        item.setInstructedQuantity(row.getQtyToPick() != null ? row.getQtyToPick() : 0);
        item.setOutboundSlipNumber(row.getSlipNumber());
        item.setCustomerName(row.getPartnerName());
        item.setPlannedShipDate(row.getPlannedDate());
        item.setLotNumber(row.getLotNumber());
        return item;
    }

    private String[] csvRowMapper(PickingInstructionReportItem item) {
        return new String[]{
                item.getLocationCode(),
                item.getProductCode(),
                item.getProductName(),
                fmtOrDash(item.getUnitType()),
                fmtInteger(item.getInstructedQuantity()),
                item.getOutboundSlipNumber(),
                fmtOrDash(item.getCustomerName()),
                fmtDate(item.getPlannedShipDate()),
                fmtOrDash(item.getLotNumber())
        };
    }
}
