package com.wms.interfacing.service;

import com.wms.inbound.entity.InboundSlip;
import com.wms.inbound.repository.InboundSlipRepository;
import com.wms.interfacing.blob.BlobStorageClient;
import com.wms.interfacing.entity.IfExecution;
import com.wms.interfacing.repository.IfExecutionRepository;
import com.wms.master.entity.Partner;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.PartnerRepository;
import com.wms.master.repository.ProductRepository;
import com.wms.master.repository.WarehouseRepository;
import com.wms.outbound.entity.OutboundSlip;
import com.wms.outbound.repository.OutboundSlipRepository;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.security.WmsUserDetails;
import com.wms.shared.util.BusinessDateProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InterfaceService {

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50MB
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("^[a-zA-Z0-9_\\-]+\\.csv$");

    private static final Map<String, String> IF_TYPE_DIRECTORY = Map.of(
            "IFX-001", "inbound-plan",
            "IFX-002", "order"
    );

    private static final Map<String, String> IF_TYPE_DB_VALUE = Map.of(
            "IFX-001", "INBOUND_PLAN",
            "IFX-002", "ORDER"
    );

    private final BlobStorageClient blobStorageClient;
    private final CsvParser csvParser;
    private final InboundPlanCsvProcessor inboundPlanCsvProcessor;
    private final OrderCsvProcessor orderCsvProcessor;
    private final InboundSlipRepository inboundSlipRepository;
    private final OutboundSlipRepository outboundSlipRepository;
    private final IfExecutionRepository ifExecutionRepository;
    private final PartnerRepository partnerRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final BusinessDateProvider businessDateProvider;
    private final TransactionTemplate transactionTemplate;

    /**
     * 取り込み履歴一覧を取得する。
     */
    public Page<IfExecution> listExecutions(String ifType, OffsetDateTime dateFrom,
                                             OffsetDateTime dateTo, String status,
                                             String fileName, Pageable pageable) {
        return ifExecutionRepository.search(ifType, dateFrom, dateTo, status, fileName, pageable);
    }

    /**
     * pendingフォルダのファイル一覧を取得する。
     */
    public List<BlobStorageClient.BlobFileInfo> listFiles(String ifId) {
        String directory = resolveDirectory(ifId);
        return blobStorageClient.listPendingFiles(directory);
    }

    /**
     * CSVファイルのバリデーションを実行する（DB書き込みなし）。
     */
    public InterfaceValidationResponse validate(String ifId, String fileName, Long warehouseId) {
        String directory = resolveDirectory(ifId);
        validateFileName(fileName);

        // ファイルサイズチェック
        long fileSize = blobStorageClient.getFileSize(directory, fileName);
        if (fileSize > MAX_FILE_SIZE) {
            throw new BusinessRuleViolationException("FILE_SIZE_EXCEEDED",
                    "ファイルサイズが上限（50MB）を超えています");
        }

        // CSVパース
        InputStream inputStream = blobStorageClient.downloadFile(directory, fileName);
        CsvParser.CsvParseResult parseResult;
        try {
            parseResult = csvParser.parse(inputStream);
        } catch (CsvParser.CsvParseException e) {
            return InterfaceValidationResponse.fileError(fileName, e.getErrorCode(), e.getMessage());
        }

        // ヘッダ検証
        try {
            validateHeaderByIfId(ifId, parseResult.getHeader());
        } catch (CsvParser.CsvParseException e) {
            return InterfaceValidationResponse.fileError(fileName, e.getErrorCode(), e.getMessage());
        }

        // マスタキャッシュ構築
        InboundPlanCsvProcessor.MasterCache masterCache =
                buildMasterCache(parseResult.getDataRows(), warehouseId);

        // L2〜L5バリデーション
        LocalDate businessDate = businessDateProvider.today();
        InboundPlanCsvProcessor.ValidationResult result =
                validateByIfId(ifId, parseResult.getDataRows(), masterCache, businessDate);

        return InterfaceValidationResponse.success(fileName, result);
    }

    /**
     * CSVファイルの取り込みを実行する。
     * Blob移動はトランザクション外で実行する（設計書 IF-01 セクション4.1準拠）。
     */
    public InterfaceImportResponse importFile(String ifId, String fileName, Long warehouseId,
                                              String mode) {
        String directory = resolveDirectory(ifId);
        validateFileName(fileName);
        validateFileSize(directory, fileName);
        Long currentUserId = getCurrentUserId();

        // DISCARD モード
        if ("DISCARD".equals(mode)) {
            return handleDiscard(directory, fileName, ifId, warehouseId, currentUserId);
        }

        // SUCCESS_ONLY モード: CSV再読み込み + 再バリデーション
        InputStream inputStream = blobStorageClient.downloadFile(directory, fileName);
        CsvParser.CsvParseResult parseResult;
        try {
            parseResult = csvParser.parse(inputStream);
        } catch (CsvParser.CsvParseException e) {
            throw new BusinessRuleViolationException("CSV_PARSE_ERROR", e.getMessage());
        }

        try {
            validateHeaderByIfId(ifId, parseResult.getHeader());
        } catch (CsvParser.CsvParseException e) {
            throw new BusinessRuleViolationException("CSV_HEADER_ERROR", e.getMessage());
        }

        InboundPlanCsvProcessor.MasterCache masterCache =
                buildMasterCache(parseResult.getDataRows(), warehouseId);
        LocalDate businessDate = businessDateProvider.today();

        InboundPlanCsvProcessor.ValidationResult validationResult =
                validateByIfId(ifId, parseResult.getDataRows(), masterCache, businessDate);

        // DB操作をトランザクション内で実行
        IfExecution execution = transactionTemplate.execute(status -> {
            saveSlipsByIfId(ifId, parseResult.getDataRows(), validationResult, masterCache,
                    warehouseId, businessDate, currentUserId, fileName);

            return saveExecution(ifId, fileName, null,
                    validationResult.getTotalRows(), validationResult.getSuccessCount(),
                    validationResult.getErrorCount(), mode, "COMPLETED",
                    warehouseId, currentUserId);
        });

        // Blob移動はトランザクション外（DB確定後）
        moveBlobSafely(directory, fileName, execution);

        return new InterfaceImportResponse(
                validationResult.getSuccessCount(),
                validationResult.getErrorCount(),
                mode, "COMPLETED");
    }

    private InterfaceImportResponse handleDiscard(String directory, String fileName,
                                                   String ifId, Long warehouseId,
                                                   Long currentUserId) {
        // CSVをパースしてバリデーション結果を取得（件数カウント用）
        InputStream inputStream = blobStorageClient.downloadFile(directory, fileName);
        int totalCount = 0;
        try {
            CsvParser.CsvParseResult parseResult = csvParser.parse(inputStream);
            totalCount = parseResult.getDataRows().size();
        } catch (CsvParser.CsvParseException e) {
            // パースエラーでも破棄は可能
            log.warn("CSV parse error during discard (continuing): {}", e.getMessage());
        }

        final int count = totalCount;
        IfExecution execution = transactionTemplate.execute(status ->
                saveExecution(ifId, fileName, null,
                        count, 0, count, "DISCARD", "DISCARDED",
                        warehouseId, currentUserId));

        // Blob移動はトランザクション外
        moveBlobSafely(directory, fileName, execution);

        return new InterfaceImportResponse(0, totalCount, "DISCARD", "DISCARDED");
    }

    private void moveBlobSafely(String directory, String fileName, IfExecution execution) {
        try {
            String destPath = blobStorageClient.moveToProcessed(directory, fileName);
            execution.setBlobMoveFailed(false);
            ifExecutionRepository.save(execution);
            log.info("Blob moved to processed: {}", destPath);
        } catch (Exception e) {
            log.error("Blob move failed for file: {}. DB records are already committed.", fileName, e);
            execution.setBlobMoveFailed(true);
            ifExecutionRepository.save(execution);
        }
    }

    private IfExecution saveExecution(String ifId, String fileName, String blobPath,
                                      int totalCount, int successCount, int errorCount,
                                      String mode, String status,
                                      Long warehouseId, Long currentUserId) {
        IfExecution execution = IfExecution.builder()
                .ifType(IF_TYPE_DB_VALUE.get(ifId))
                .fileName(fileName)
                .blobPath(blobPath)
                .totalCount(totalCount)
                .successCount(successCount)
                .errorCount(errorCount)
                .mode(mode)
                .status(status)
                .blobMoveFailed(false)
                .warehouseId(warehouseId)
                .executedAt(OffsetDateTime.now(JST))
                .executedBy(currentUserId)
                .build();
        return ifExecutionRepository.save(execution);
    }

    private void validateHeaderByIfId(String ifId, String[] header) {
        if ("IFX-001".equals(ifId)) {
            inboundPlanCsvProcessor.validateHeader(header);
        } else if ("IFX-002".equals(ifId)) {
            orderCsvProcessor.validateHeader(header);
        } else {
            throw new BusinessRuleViolationException("INVALID_IF_TYPE",
                    "不正なI/F種別です: " + ifId);
        }
    }

    private InboundPlanCsvProcessor.ValidationResult validateByIfId(
            String ifId, List<String[]> dataRows,
            InboundPlanCsvProcessor.MasterCache masterCache, LocalDate businessDate) {
        if ("IFX-001".equals(ifId)) {
            return inboundPlanCsvProcessor.validate(dataRows, masterCache, businessDate);
        } else if ("IFX-002".equals(ifId)) {
            return orderCsvProcessor.validate(dataRows, masterCache, businessDate);
        } else {
            throw new BusinessRuleViolationException("INVALID_IF_TYPE",
                    "不正なI/F種別です: " + ifId);
        }
    }

    private void saveSlipsByIfId(String ifId, List<String[]> dataRows,
                                  InboundPlanCsvProcessor.ValidationResult validationResult,
                                  InboundPlanCsvProcessor.MasterCache masterCache,
                                  Long warehouseId, LocalDate businessDate,
                                  Long currentUserId, String fileName) {
        if ("IFX-001".equals(ifId)) {
            List<InboundSlip> slips = inboundPlanCsvProcessor.buildSlips(
                    dataRows, validationResult, masterCache, warehouseId,
                    this::generateInboundSlipNumber, businessDate, currentUserId);
            if (!slips.isEmpty()) {
                inboundSlipRepository.saveAll(slips);
                log.info("IFX-001 import: {} slips created from {}", slips.size(), fileName);
            }
        } else if ("IFX-002".equals(ifId)) {
            List<OutboundSlip> slips = orderCsvProcessor.buildSlips(
                    dataRows, validationResult, masterCache, warehouseId,
                    this::generateOutboundSlipNumber, businessDate, currentUserId);
            if (!slips.isEmpty()) {
                outboundSlipRepository.saveAll(slips);
                log.info("IFX-002 import: {} slips created from {}", slips.size(), fileName);
            }
        } else {
            throw new BusinessRuleViolationException("INVALID_IF_TYPE",
                    "不正なI/F種別です: " + ifId);
        }
    }

    private String generateInboundSlipNumber(LocalDate businessDate) {
        String dateStr = businessDate.format(DATE_FORMAT);
        int maxSeq = inboundSlipRepository.findMaxSequenceByDate("INB-" + dateStr + "-");
        int nextSeq = maxSeq + 1;
        if (nextSeq > 9999) {
            throw new BusinessRuleViolationException("SLIP_NUMBER_EXCEEDED",
                    "伝票番号が上限（9999）に達しました。日付: INB-" + dateStr + "-");
        }
        return String.format("INB-%s-%04d", dateStr, nextSeq);
    }

    private String generateOutboundSlipNumber(LocalDate businessDate) {
        String dateStr = businessDate.format(DATE_FORMAT);
        int maxSeq = outboundSlipRepository.findMaxSequenceByDate(dateStr);
        int nextSeq = maxSeq + 1;
        if (nextSeq > 9999) {
            throw new BusinessRuleViolationException("SLIP_NUMBER_EXCEEDED",
                    "伝票番号が上限（9999）に達しました。日付: OUT-" + dateStr + "-");
        }
        return String.format("OUT-%s-%04d", dateStr, nextSeq);
    }

    InboundPlanCsvProcessor.MasterCache buildMasterCache(List<String[]> dataRows,
                                                          Long warehouseId) {
        Set<String> partnerCodes = dataRows.stream()
                .map(row -> row.length > 0 ? CsvParser.normalizeEmpty(row[0]) : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> productCodes = dataRows.stream()
                .map(row -> row.length > 2 ? CsvParser.normalizeEmpty(row[2]) : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, Partner> partnerMap = partnerCodes.isEmpty()
                ? Map.of()
                : partnerRepository.findByPartnerCodeIn(partnerCodes).stream()
                .collect(Collectors.toMap(Partner::getPartnerCode, Function.identity()));

        Map<String, Product> productMap = productCodes.isEmpty()
                ? Map.of()
                : productRepository.findByProductCodeIn(productCodes).stream()
                .collect(Collectors.toMap(Product::getProductCode, Function.identity()));

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> ResourceNotFoundException.of(
                        "WAREHOUSE_NOT_FOUND", "warehouse", warehouseId));

        return new InboundPlanCsvProcessor.MasterCache(partnerMap, productMap, warehouse);
    }

    private String resolveDirectory(String ifId) {
        String directory = IF_TYPE_DIRECTORY.get(ifId);
        if (directory == null) {
            throw new BusinessRuleViolationException("INVALID_IF_TYPE",
                    "不正なI/F種別です: " + ifId);
        }
        return directory;
    }

    private void validateFileName(String fileName) {
        if (fileName == null || !SAFE_FILE_NAME.matcher(fileName).matches()) {
            throw new BusinessRuleViolationException("INVALID_FILE_NAME",
                    "ファイル名が不正です。英数字・ハイフン・アンダースコアと.csv拡張子のみ使用可能です");
        }
    }

    private void validateFileSize(String directory, String fileName) {
        long fileSize = blobStorageClient.getFileSize(directory, fileName);
        if (fileSize > MAX_FILE_SIZE) {
            throw new BusinessRuleViolationException("FILE_SIZE_EXCEEDED",
                    "ファイルサイズが上限（50MB）を超えています");
        }
    }

    private Long getCurrentUserId() {
        WmsUserDetails userDetails = (WmsUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return userDetails.getUserId();
    }

    // --- Response records ---

    public record InterfaceValidationResponse(
            String fileName,
            int totalRows,
            int successCount,
            int errorCount,
            String fileErrorCode,
            String fileErrorMessage,
            List<InboundPlanCsvProcessor.RowError> rowErrors) {

        public static InterfaceValidationResponse fileError(String fileName,
                                                             String errorCode,
                                                             String message) {
            return new InterfaceValidationResponse(fileName, 0, 0, 0,
                    errorCode, message, List.of());
        }

        public static InterfaceValidationResponse success(
                String fileName,
                InboundPlanCsvProcessor.ValidationResult result) {
            return new InterfaceValidationResponse(fileName,
                    result.getTotalRows(), result.getSuccessCount(), result.getErrorCount(),
                    null, null, result.getRowErrors());
        }

        public boolean hasFileError() {
            return fileErrorCode != null;
        }
    }

    public record InterfaceImportResponse(
            int successCount,
            int errorCount,
            String mode,
            String status) {
    }
}
