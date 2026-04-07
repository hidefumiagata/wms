package com.wms.interfacing.service;

import com.wms.inbound.entity.InboundSlip;
import com.wms.inbound.repository.InboundSlipRepository;
import com.wms.interfacing.blob.BlobStorageClient;
import com.wms.interfacing.entity.IfExecution;
import com.wms.interfacing.model.FieldError;
import com.wms.interfacing.model.MasterCache;
import com.wms.interfacing.model.RowError;
import com.wms.interfacing.model.SlipNumberGenerator;
import com.wms.interfacing.model.ValidationResult;
import com.wms.interfacing.repository.IfExecutionRepository;
import com.wms.master.entity.Partner;
import com.wms.master.entity.PartnerType;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.PartnerRepository;
import com.wms.master.repository.ProductRepository;
import com.wms.master.repository.WarehouseRepository;
import com.wms.outbound.entity.OutboundSlip;
import com.wms.outbound.repository.OutboundSlipRepository;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.security.WmsUserDetails;
import com.wms.shared.util.BusinessDateProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InterfaceService")
class InterfaceServiceTest {

    @Mock
    private BlobStorageClient blobStorageClient;
    @Mock
    private CsvParser csvParser;
    @Mock
    private InboundPlanCsvProcessor inboundPlanCsvProcessor;
    @Mock
    private OrderCsvProcessor orderCsvProcessor;
    @Mock
    private InboundSlipRepository inboundSlipRepository;
    @Mock
    private OutboundSlipRepository outboundSlipRepository;
    @Mock
    private IfExecutionRepository ifExecutionRepository;
    @Mock
    private PartnerRepository partnerRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private BusinessDateProvider businessDateProvider;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private InterfaceService interfaceService;

    private static void setField(Object obj, String fieldName, Object value) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("Field not found: " + fieldName);
    }

    @SuppressWarnings("unchecked")
    private void setupTransactionTemplate() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    private void setupSecurityContext() {
        WmsUserDetails userDetails = mock(WmsUserDetails.class);
        when(userDetails.getUserId()).thenReturn(1L);
        SecurityContext context = mock(SecurityContext.class);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);
    }

    @Nested
    @DisplayName("listExecutions")
    class ListExecutions {

        @Test
        @DisplayName("正常系 — 全パラメータnullでリポジトリに委譲する")
        void listExecutions_allNull_delegatesToRepository() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<IfExecution> expected = new PageImpl<>(List.of(), pageable, 0);
            when(ifExecutionRepository.search(null, null, null, null, null, null, pageable))
                    .thenReturn(expected);

            Page<IfExecution> result = interfaceService.listExecutions(
                    null, null, null, null, null, null, pageable);

            assertThat(result).isSameAs(expected);
            verify(ifExecutionRepository).search(null, null, null, null, null, null, pageable);
        }

        @Test
        @DisplayName("正常系 — 全フィルタ指定でリポジトリに委譲する")
        void listExecutions_allFilters_delegatesToRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            OffsetDateTime from = OffsetDateTime.parse("2026-03-01T00:00:00+09:00");
            OffsetDateTime to = OffsetDateTime.parse("2026-04-01T00:00:00+09:00");
            IfExecution exec = IfExecution.builder()
                    .id(1L).ifType("INBOUND_PLAN").fileName("test.csv")
                    .totalCount(10).successCount(10).errorCount(0)
                    .mode("SUCCESS_ONLY").status("COMPLETED")
                    .blobMoveFailed(false).warehouseId(5L)
                    .executedAt(OffsetDateTime.now()).executedBy(1L)
                    .build();
            Page<IfExecution> expected = new PageImpl<>(List.of(exec), pageable, 1);
            when(ifExecutionRepository.search("INBOUND_PLAN", from, to, "COMPLETED", 5L, "test", pageable))
                    .thenReturn(expected);

            Page<IfExecution> result = interfaceService.listExecutions(
                    "INBOUND_PLAN", from, to, "COMPLETED", 5L, "test", pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getIfType()).isEqualTo("INBOUND_PLAN");
            verify(ifExecutionRepository).search("INBOUND_PLAN", from, to, "COMPLETED", 5L, "test", pageable);
        }
    }

    @Nested
    @DisplayName("listFiles")
    class ListFiles {

        @Test
        @DisplayName("正常系 — IFX-001のファイル一覧を返す")
        void listFiles_inboundPlan_returnsList() {
            var fileInfo = new BlobStorageClient.BlobFileInfo(
                    "INB-PLAN-001.csv", 12288, OffsetDateTime.now());
            when(blobStorageClient.listPendingFiles("inbound-plan"))
                    .thenReturn(List.of(fileInfo));

            List<BlobStorageClient.BlobFileInfo> result =
                    interfaceService.listFiles("IFX-001");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).fileName()).isEqualTo("INB-PLAN-001.csv");
        }

        @Test
        @DisplayName("正常系 — IFX-002のファイル一覧を返す")
        void listFiles_order_returnsList() {
            when(blobStorageClient.listPendingFiles("order"))
                    .thenReturn(List.of());

            List<BlobStorageClient.BlobFileInfo> result =
                    interfaceService.listFiles("IFX-002");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("異常系 — 不正なI/F種別でBusinessRuleViolationException")
        void listFiles_invalidIfType_throwsException() {
            assertThatThrownBy(() -> interfaceService.listFiles("IFX-999"))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("正常系 — バリデーション成功結果を返す")
        void validate_success_returnsResult() {
            String fileName = "INB-PLAN-001.csv";
            Long warehouseId = 1L;

            when(blobStorageClient.getFileSize("inbound-plan", fileName)).thenReturn(1024L);
            when(blobStorageClient.downloadFile("inbound-plan", fileName))
                    .thenReturn(new ByteArrayInputStream("test".getBytes()));

            String[] header = {"partner_code", "planned_date", "product_code",
                    "unit_type", "planned_qty", "lot_number", "expiry_date", "note"};
            List<String[]> dataRows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""});
            CsvParser.CsvParseResult parseResult = new CsvParser.CsvParseResult(header, dataRows);
            when(csvParser.parse(any())).thenReturn(parseResult);

            Warehouse warehouse = new Warehouse();
            warehouse.setWarehouseCode("WH-001");
            warehouse.setWarehouseName("Warehouse 1");
            setField(warehouse, "id", warehouseId);
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
            when(partnerRepository.findByPartnerCodeIn(any())).thenReturn(List.of());
            when(productRepository.findByProductCodeIn(any())).thenReturn(List.of());
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 20));

            ValidationResult validationResult =
                    new ValidationResult(1, 1, 0, List.of());
            when(inboundPlanCsvProcessor.validate(any(), any(), any()))
                    .thenReturn(validationResult);

            InterfaceService.InterfaceValidationResponse result =
                    interfaceService.validate("IFX-001", fileName, warehouseId);

            assertThat(result.hasFileError()).isFalse();
            assertThat(result.totalRows()).isEqualTo(1);
            assertThat(result.successCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("異常系 — パストラバーサルを含むファイル名で拒否")
        void validate_pathTraversal_throwsException() {
            assertThatThrownBy(() -> interfaceService.validate(
                    "IFX-001", "../../secret.csv", 1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("ファイル名が不正");
        }

        @Test
        @DisplayName("異常系 — ファイルサイズ超過でBusinessRuleViolationException")
        void validate_fileSizeExceeded_throwsException() {
            when(blobStorageClient.getFileSize("inbound-plan", "big.csv"))
                    .thenReturn(51L * 1024 * 1024);

            assertThatThrownBy(() -> interfaceService.validate("IFX-001", "big.csv", 1L))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("異常系 — CSVパースエラーでfileError応答")
        void validate_csvParseError_returnsFileError() {
            when(blobStorageClient.getFileSize("inbound-plan", "bad.csv")).thenReturn(100L);
            when(blobStorageClient.downloadFile("inbound-plan", "bad.csv"))
                    .thenReturn(new ByteArrayInputStream("".getBytes()));
            when(csvParser.parse(any())).thenThrow(
                    new CsvParser.CsvParseException("WMS-E-IFX-002", "ヘッダ行が存在しません"));

            InterfaceService.InterfaceValidationResponse result =
                    interfaceService.validate("IFX-001", "bad.csv", 1L);

            assertThat(result.hasFileError()).isTrue();
            assertThat(result.fileErrorCode()).isEqualTo("WMS-E-IFX-002");
        }

        @Test
        @DisplayName("異常系 — ヘッダ検証エラーでfileError応答")
        void validate_headerError_returnsFileError() {
            when(blobStorageClient.getFileSize("inbound-plan", "bad.csv")).thenReturn(100L);
            when(blobStorageClient.downloadFile("inbound-plan", "bad.csv"))
                    .thenReturn(new ByteArrayInputStream("a,b\n1,2\n".getBytes()));

            String[] header = {"a", "b"};
            CsvParser.CsvParseResult parseResult =
                    new CsvParser.CsvParseResult(header, List.<String[]>of(new String[]{"1", "2"}));
            when(csvParser.parse(any())).thenReturn(parseResult);
            var ex = new CsvParser.CsvParseException("WMS-E-IFX-003", "カラム数不正");
            org.mockito.Mockito.doThrow(ex).when(inboundPlanCsvProcessor).validateHeader(header);

            InterfaceService.InterfaceValidationResponse result =
                    interfaceService.validate("IFX-001", "bad.csv", 1L);

            assertThat(result.hasFileError()).isTrue();
            assertThat(result.fileErrorCode()).isEqualTo("WMS-E-IFX-003");
        }
    }

    @Nested
    @DisplayName("importFile")
    class ImportFile {

        @BeforeEach
        void setUp() {
            setupSecurityContext();
        }

        @Test
        @DisplayName("正常系 — SUCCESS_ONLYモードで取り込み成功")
        void importFile_successOnly_completed() {
            setupTransactionTemplate();
            String fileName = "INB-PLAN-001.csv";
            Long warehouseId = 1L;

            when(blobStorageClient.getFileSize("inbound-plan", fileName)).thenReturn(1024L);

            String csvContent = "partner_code,planned_date,product_code,unit_type,planned_qty,lot_number,expiry_date,note\n"
                    + "SUP-0001,2026-03-25,PRD-001,CASE,100,,,\n";

            when(blobStorageClient.downloadFile("inbound-plan", fileName))
                    .thenReturn(new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8)));

            String[] header = {"partner_code", "planned_date", "product_code",
                    "unit_type", "planned_qty", "lot_number", "expiry_date", "note"};
            List<String[]> dataRows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""});
            CsvParser.CsvParseResult parseResult = new CsvParser.CsvParseResult(header, dataRows);
            when(csvParser.parse(any())).thenReturn(parseResult);

            Warehouse warehouse = new Warehouse();
            warehouse.setWarehouseCode("WH-001");
            warehouse.setWarehouseName("Warehouse 1");
            setField(warehouse, "id", warehouseId);
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

            Partner partner = new Partner();
            partner.setPartnerCode("SUP-0001");
            partner.setPartnerName("Supplier 1");
            partner.setPartnerType(PartnerType.SUPPLIER);
            setField(partner, "id", 1L);
            setField(partner, "isActive", true);
            when(partnerRepository.findByPartnerCodeIn(any())).thenReturn(List.of(partner));

            Product product = new Product();
            product.setProductCode("PRD-001");
            product.setProductName("Product 1");
            product.setLotManageFlag(false);
            product.setExpiryManageFlag(false);
            setField(product, "id", 10L);
            setField(product, "isActive", true);
            when(productRepository.findByProductCodeIn(any())).thenReturn(List.of(product));

            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 20));

            ValidationResult validationResult =
                    new ValidationResult(1, 1, 0, List.of());
            when(inboundPlanCsvProcessor.validate(any(), any(), any()))
                    .thenReturn(validationResult);

            InboundSlip mockSlip = InboundSlip.builder()
                    .slipNumber("INB-20260320-0001")
                    .slipType("NORMAL")
                    .status("PLANNED")
                    .build();
            when(inboundPlanCsvProcessor.buildSlips(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(mockSlip));

            when(inboundSlipRepository.saveAll(anyList())).thenReturn(List.of(mockSlip));

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(false).build();
            when(ifExecutionRepository.save(any())).thenReturn(execution);

            when(blobStorageClient.moveToProcessed("inbound-plan", fileName))
                    .thenReturn("inbound-plan/processed/2026/03/20/20260320_120000_INB-PLAN-001.csv");

            InterfaceService.InterfaceImportResponse result =
                    interfaceService.importFile("IFX-001", fileName, warehouseId, "SUCCESS_ONLY");

            assertThat(result.status()).isEqualTo("COMPLETED");
            assertThat(result.successCount()).isEqualTo(1);
            assertThat(result.mode()).isEqualTo("SUCCESS_ONLY");
            verify(inboundSlipRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("正常系 — DISCARDモードでDB登録なし")
        void importFile_discard_noDbInsert() {
            setupTransactionTemplate();
            String fileName = "INB-PLAN-001.csv";
            Long warehouseId = 1L;

            when(blobStorageClient.getFileSize("inbound-plan", fileName)).thenReturn(1024L);
            when(blobStorageClient.downloadFile("inbound-plan", fileName))
                    .thenReturn(new ByteArrayInputStream("h\nd\n".getBytes()));

            CsvParser.CsvParseResult parseResult =
                    new CsvParser.CsvParseResult(new String[]{"h"}, List.<String[]>of(new String[]{"d"}));
            when(csvParser.parse(any())).thenReturn(parseResult);

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(false).build();
            when(ifExecutionRepository.save(any())).thenReturn(execution);

            when(blobStorageClient.moveToProcessed("inbound-plan", fileName))
                    .thenReturn("processed/path");

            InterfaceService.InterfaceImportResponse result =
                    interfaceService.importFile("IFX-001", fileName, warehouseId, "DISCARD");

            assertThat(result.status()).isEqualTo("DISCARDED");
            assertThat(result.mode()).isEqualTo("DISCARD");
            verify(inboundSlipRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("異常系 — DISCARDモードでCSVパースエラーでも破棄可能")
        void importFile_discard_csvParseError_stillDiscards() {
            setupTransactionTemplate();
            String fileName = "bad.csv";
            Long warehouseId = 1L;

            when(blobStorageClient.getFileSize("inbound-plan", fileName)).thenReturn(100L);
            when(blobStorageClient.downloadFile("inbound-plan", fileName))
                    .thenReturn(new ByteArrayInputStream("".getBytes()));
            when(csvParser.parse(any())).thenThrow(
                    new CsvParser.CsvParseException("WMS-E-IFX-002", "ヘッダ行が存在しません"));

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(false).build();
            when(ifExecutionRepository.save(any())).thenReturn(execution);

            when(blobStorageClient.moveToProcessed("inbound-plan", fileName))
                    .thenReturn("processed/path");

            InterfaceService.InterfaceImportResponse result =
                    interfaceService.importFile("IFX-001", fileName, warehouseId, "DISCARD");

            assertThat(result.status()).isEqualTo("DISCARDED");
        }

        @Test
        @DisplayName("異常系 — SUCCESS_ONLYでCSVパースエラーはBusinessRuleViolationException")
        void importFile_successOnly_csvParseError_throwsException() {
            when(blobStorageClient.getFileSize("inbound-plan", "bad.csv")).thenReturn(100L);
            when(blobStorageClient.downloadFile("inbound-plan", "bad.csv"))
                    .thenReturn(new ByteArrayInputStream("".getBytes()));
            when(csvParser.parse(any())).thenThrow(
                    new CsvParser.CsvParseException("WMS-E-IFX-002", "error"));

            assertThatThrownBy(() -> interfaceService.importFile(
                    "IFX-001", "bad.csv", 1L, "SUCCESS_ONLY"))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("異常系 — Blob移動失敗時はblobMoveFailedがtrueになる")
        void importFile_blobMoveFailed_flaggedButCompleted() {
            setupTransactionTemplate();
            String fileName = "INB-PLAN-001.csv";
            Long warehouseId = 1L;

            when(blobStorageClient.getFileSize("inbound-plan", fileName)).thenReturn(1024L);
            when(blobStorageClient.downloadFile("inbound-plan", fileName))
                    .thenReturn(new ByteArrayInputStream("h\nd\n".getBytes()));

            CsvParser.CsvParseResult parseResult =
                    new CsvParser.CsvParseResult(new String[]{"h"}, List.<String[]>of(new String[]{"d"}));
            when(csvParser.parse(any())).thenReturn(parseResult);

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(false).build();
            when(ifExecutionRepository.save(any())).thenReturn(execution);

            when(blobStorageClient.moveToProcessed("inbound-plan", fileName))
                    .thenThrow(new RuntimeException("Blob move failed"));

            InterfaceService.InterfaceImportResponse result =
                    interfaceService.importFile("IFX-001", fileName, warehouseId, "DISCARD");

            assertThat(result.status()).isEqualTo("DISCARDED");
            verify(ifExecutionRepository, org.mockito.Mockito.times(2)).save(any());
        }
    }

    @Nested
    @DisplayName("importFile — additional coverage")
    class ImportFileAdditional {

        @Test
        @DisplayName("SUCCESS_ONLYで全行エラーの場合、saveAllが呼ばれない")
        void importFile_successOnly_allErrors_noSave() {
            setupSecurityContext();
            setupTransactionTemplate();
            String fileName = "INB-PLAN-001.csv";
            Long warehouseId = 1L;

            when(blobStorageClient.getFileSize("inbound-plan", fileName)).thenReturn(1024L);
            when(blobStorageClient.downloadFile("inbound-plan", fileName))
                    .thenReturn(new ByteArrayInputStream("csv".getBytes()));

            String[] header = {"partner_code", "planned_date", "product_code",
                    "unit_type", "planned_qty", "lot_number", "expiry_date", "note"};
            List<String[]> dataRows = List.<String[]>of(
                    new String[]{"BAD", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""});
            when(csvParser.parse(any())).thenReturn(
                    new CsvParser.CsvParseResult(header, dataRows));

            Warehouse warehouse = new Warehouse();
            warehouse.setWarehouseCode("WH-001");
            warehouse.setWarehouseName("WH 1");
            setField(warehouse, "id", warehouseId);
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
            when(partnerRepository.findByPartnerCodeIn(any())).thenReturn(List.of());
            when(productRepository.findByProductCodeIn(any())).thenReturn(List.of());
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 20));

            ValidationResult valResult =
                    new ValidationResult(1, 0, 1, List.of(
                            new RowError(1, List.of(
                                    new FieldError("partner_code",
                                            "WMS-E-IFX-301", "err")))));
            when(inboundPlanCsvProcessor.validate(any(), any(), any())).thenReturn(valResult);
            when(inboundPlanCsvProcessor.buildSlips(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(false).build();
            when(ifExecutionRepository.save(any())).thenReturn(execution);
            when(blobStorageClient.moveToProcessed("inbound-plan", fileName))
                    .thenReturn("processed/path");

            InterfaceService.InterfaceImportResponse result =
                    interfaceService.importFile("IFX-001", fileName, warehouseId, "SUCCESS_ONLY");

            assertThat(result.status()).isEqualTo("COMPLETED");
            assertThat(result.successCount()).isEqualTo(0);
            verify(inboundSlipRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("異常系 — importFileでファイルサイズ超過はBusinessRuleViolationException")
        void importFile_fileSizeExceeded_throwsException() {
            when(blobStorageClient.getFileSize("inbound-plan", "big.csv"))
                    .thenReturn(51L * 1024 * 1024);

            assertThatThrownBy(() -> interfaceService.importFile(
                    "IFX-001", "big.csv", 1L, "SUCCESS_ONLY"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("50MB");
        }

        @Test
        @DisplayName("異常系 — fileNameがnullでBusinessRuleViolationException")
        void importFile_nullFileName_throwsException() {
            assertThatThrownBy(() -> interfaceService.importFile(
                    "IFX-001", null, 1L, "SUCCESS_ONLY"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("ファイル名が不正");
        }

        @Test
        @DisplayName("SUCCESS_ONLYでヘッダ検証エラーはBusinessRuleViolationException")
        void importFile_successOnly_headerError_throwsException() {
            when(blobStorageClient.getFileSize("inbound-plan", "bad.csv")).thenReturn(100L);
            when(blobStorageClient.downloadFile("inbound-plan", "bad.csv"))
                    .thenReturn(new ByteArrayInputStream("a,b\n1,2\n".getBytes()));

            String[] header = {"a", "b"};
            when(csvParser.parse(any())).thenReturn(
                    new CsvParser.CsvParseResult(header, List.<String[]>of(new String[]{"1", "2"})));
            org.mockito.Mockito.doThrow(
                    new CsvParser.CsvParseException("WMS-E-IFX-003", "header error"))
                    .when(inboundPlanCsvProcessor).validateHeader(header);

            assertThatThrownBy(() -> interfaceService.importFile(
                    "IFX-001", "bad.csv", 1L, "SUCCESS_ONLY"))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    @DisplayName("buildMasterCache")
    class BuildMasterCache {

        @Test
        @DisplayName("正常系 — マスタキャッシュが正しく構築される")
        void buildMasterCache_success() {
            Long warehouseId = 1L;
            Warehouse warehouse = new Warehouse();
            warehouse.setWarehouseCode("WH-001");
            warehouse.setWarehouseName("Warehouse 1");
            setField(warehouse, "id", warehouseId);
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

            Partner partner = new Partner();
            partner.setPartnerCode("SUP-0001");
            when(partnerRepository.findByPartnerCodeIn(any())).thenReturn(List.of(partner));

            Product product = new Product();
            product.setProductCode("PRD-001");
            when(productRepository.findByProductCodeIn(any())).thenReturn(List.of(product));

            List<String[]> dataRows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""});

            MasterCache cache =
                    interfaceService.buildMasterCache(dataRows, warehouseId);

            assertThat(cache.getPartner("SUP-0001")).isNotNull();
            assertThat(cache.getProduct("PRD-001")).isNotNull();
            assertThat(cache.getWarehouse()).isNotNull();
        }

        @Test
        @DisplayName("正常系 — 空データ行でもキャッシュが構築される")
        void buildMasterCache_emptyDataRows() {
            Long warehouseId = 1L;
            Warehouse warehouse = new Warehouse();
            warehouse.setWarehouseCode("WH-001");
            warehouse.setWarehouseName("Warehouse 1");
            setField(warehouse, "id", warehouseId);
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

            // Empty data rows — no partner/product codes to look up
            List<String[]> dataRows = List.<String[]>of(new String[]{"", "", ""});

            MasterCache cache =
                    interfaceService.buildMasterCache(dataRows, warehouseId);

            assertThat(cache.getWarehouse()).isNotNull();
            // No partner/product lookups performed
            verify(partnerRepository, never()).findByPartnerCodeIn(any());
            verify(productRepository, never()).findByProductCodeIn(any());
        }

        @Test
        @DisplayName("異常系 — 倉庫が存在しない場合ResourceNotFoundException")
        void buildMasterCache_warehouseNotFound() {
            when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());
            when(partnerRepository.findByPartnerCodeIn(any())).thenReturn(List.of());
            when(productRepository.findByProductCodeIn(any())).thenReturn(List.of());

            List<String[]> dataRows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""});

            assertThatThrownBy(() -> interfaceService.buildMasterCache(dataRows, 999L))
                    .isInstanceOf(com.wms.shared.exception.ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("境界値 — カラム数が0の行でもNullPointerにならない")
        void buildMasterCache_zeroLengthRow_handledGracefully() {
            Long warehouseId = 1L;
            Warehouse warehouse = new Warehouse();
            warehouse.setWarehouseCode("WH-001");
            warehouse.setWarehouseName("WH 1");
            setField(warehouse, "id", warehouseId);
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

            // row.length == 0: row.length > 0 is false, row.length > 2 is false
            List<String[]> dataRows = List.<String[]>of(new String[]{});

            MasterCache cache =
                    interfaceService.buildMasterCache(dataRows, warehouseId);

            assertThat(cache.getWarehouse()).isNotNull();
        }

        @Test
        @DisplayName("境界値 — カラム数が1の行（partner_codeのみ）")
        void buildMasterCache_singleColumnRow_handledGracefully() {
            Long warehouseId = 1L;
            Warehouse warehouse = new Warehouse();
            warehouse.setWarehouseCode("WH-001");
            warehouse.setWarehouseName("WH 1");
            setField(warehouse, "id", warehouseId);
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
            when(partnerRepository.findByPartnerCodeIn(any())).thenReturn(List.of());

            // row.length == 1: row.length > 0 is true (partner_code extracted),
            // row.length > 2 is false (no product_code)
            List<String[]> dataRows = List.<String[]>of(new String[]{"SUP-0001"});

            MasterCache cache =
                    interfaceService.buildMasterCache(dataRows, warehouseId);

            assertThat(cache.getWarehouse()).isNotNull();
        }
    }

    @Nested
    @DisplayName("generateSlipNumber — via importFile")
    class GenerateSlipNumber {

        @BeforeEach
        void setUp() {
            setupSecurityContext();
            setupTransactionTemplate();
        }

        @Test
        @DisplayName("異常系 — 伝票番号が9999超過でBusinessRuleViolationException")
        void importFile_slipNumberExceeded_throwsException() {
            String fileName = "INB-PLAN-001.csv";
            Long warehouseId = 1L;

            when(blobStorageClient.getFileSize("inbound-plan", fileName)).thenReturn(1024L);
            when(blobStorageClient.downloadFile("inbound-plan", fileName))
                    .thenReturn(new ByteArrayInputStream(
                            "csv".getBytes(StandardCharsets.UTF_8)));

            String[] header = {"partner_code", "planned_date", "product_code",
                    "unit_type", "planned_qty", "lot_number", "expiry_date", "note"};
            List<String[]> dataRows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""});
            when(csvParser.parse(any())).thenReturn(
                    new CsvParser.CsvParseResult(header, dataRows));

            Warehouse warehouse = new Warehouse();
            warehouse.setWarehouseCode("WH-001");
            warehouse.setWarehouseName("WH 1");
            setField(warehouse, "id", warehouseId);
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

            com.wms.master.entity.Partner partner = new com.wms.master.entity.Partner();
            partner.setPartnerCode("SUP-0001");
            partner.setPartnerName("Supplier 1");
            partner.setPartnerType(com.wms.master.entity.PartnerType.SUPPLIER);
            setField(partner, "id", 1L);
            setField(partner, "isActive", true);
            when(partnerRepository.findByPartnerCodeIn(any())).thenReturn(List.of(partner));

            com.wms.master.entity.Product product = new com.wms.master.entity.Product();
            product.setProductCode("PRD-001");
            product.setProductName("Product 1");
            product.setLotManageFlag(false);
            product.setExpiryManageFlag(false);
            setField(product, "id", 10L);
            setField(product, "isActive", true);
            when(productRepository.findByProductCodeIn(any())).thenReturn(List.of(product));

            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 20));

            ValidationResult validationResult =
                    new ValidationResult(1, 1, 0, List.of());
            when(inboundPlanCsvProcessor.validate(any(), any(), any()))
                    .thenReturn(validationResult);

            when(inboundSlipRepository.findMaxSequenceByDate(any())).thenReturn(9999);

            when(inboundPlanCsvProcessor.buildSlips(any(), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(inv -> {
                        SlipNumberGenerator gen = inv.getArgument(4);
                        gen.generate(LocalDate.of(2026, 3, 20));
                        return List.of();
                    });

            assertThatThrownBy(() -> interfaceService.importFile(
                    "IFX-001", fileName, warehouseId, "SUCCESS_ONLY"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("9999");
        }

        @Test
        @DisplayName("正常系 — 伝票番号が正常に採番される（INB-YYYYMMDD-NNNN形式）")
        void importFile_slipNumberGenerated_correctly() {
            String fileName = "INB-PLAN-001.csv";
            Long warehouseId = 1L;

            when(blobStorageClient.getFileSize("inbound-plan", fileName)).thenReturn(1024L);
            when(blobStorageClient.downloadFile("inbound-plan", fileName))
                    .thenReturn(new ByteArrayInputStream(
                            "csv".getBytes(StandardCharsets.UTF_8)));

            String[] header = {"partner_code", "planned_date", "product_code",
                    "unit_type", "planned_qty", "lot_number", "expiry_date", "note"};
            List<String[]> dataRows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""});
            when(csvParser.parse(any())).thenReturn(
                    new CsvParser.CsvParseResult(header, dataRows));

            Warehouse warehouse = new Warehouse();
            warehouse.setWarehouseCode("WH-001");
            warehouse.setWarehouseName("WH 1");
            setField(warehouse, "id", warehouseId);
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

            com.wms.master.entity.Partner partner = new com.wms.master.entity.Partner();
            partner.setPartnerCode("SUP-0001");
            partner.setPartnerName("Supplier 1");
            partner.setPartnerType(com.wms.master.entity.PartnerType.SUPPLIER);
            setField(partner, "id", 1L);
            setField(partner, "isActive", true);
            when(partnerRepository.findByPartnerCodeIn(any())).thenReturn(List.of(partner));

            com.wms.master.entity.Product product = new com.wms.master.entity.Product();
            product.setProductCode("PRD-001");
            product.setProductName("Product 1");
            product.setLotManageFlag(false);
            product.setExpiryManageFlag(false);
            setField(product, "id", 10L);
            setField(product, "isActive", true);
            when(productRepository.findByProductCodeIn(any())).thenReturn(List.of(product));

            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 20));
            when(inboundSlipRepository.findMaxSequenceByDate("INB-20260320-")).thenReturn(5);

            ValidationResult validationResult =
                    new ValidationResult(1, 1, 0, List.of());
            when(inboundPlanCsvProcessor.validate(any(), any(), any()))
                    .thenReturn(validationResult);

            final String[] capturedSlipNumber = new String[1];
            when(inboundPlanCsvProcessor.buildSlips(any(), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(inv -> {
                        SlipNumberGenerator gen = inv.getArgument(4);
                        capturedSlipNumber[0] = gen.generate(LocalDate.of(2026, 3, 20));
                        return List.of();
                    });

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(false).build();
            when(ifExecutionRepository.save(any())).thenReturn(execution);
            when(blobStorageClient.moveToProcessed("inbound-plan", fileName))
                    .thenReturn("processed/path");

            interfaceService.importFile("IFX-001", fileName, warehouseId, "SUCCESS_ONLY");

            assertThat(capturedSlipNumber[0]).isEqualTo("INB-20260320-0006");
        }

    }

    // ========================================
    // IFX-002 (Order) specific tests
    // ========================================

    @Nested
    @DisplayName("validate — IFX-002")
    class ValidateOrder {

        @Test
        @DisplayName("正常系 — IFX-002バリデーション成功結果を返す")
        void validate_ifx002_success() {
            String fileName = "ORD-001.csv";
            Long warehouseId = 1L;

            when(blobStorageClient.getFileSize("order", fileName)).thenReturn(1024L);
            when(blobStorageClient.downloadFile("order", fileName))
                    .thenReturn(new ByteArrayInputStream("test".getBytes()));

            String[] header = {"partner_code", "planned_date", "product_code",
                    "unit_type", "ordered_qty", "note"};
            List<String[]> dataRows = List.<String[]>of(
                    new String[]{"CUS-0001", "2026-03-22", "PRD-001", "CASE", "50", ""});
            when(csvParser.parse(any())).thenReturn(
                    new CsvParser.CsvParseResult(header, dataRows));

            Warehouse warehouse = new Warehouse();
            warehouse.setWarehouseCode("WH-001");
            warehouse.setWarehouseName("Warehouse 1");
            setField(warehouse, "id", warehouseId);
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
            when(partnerRepository.findByPartnerCodeIn(any())).thenReturn(List.of());
            when(productRepository.findByProductCodeIn(any())).thenReturn(List.of());
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 20));

            ValidationResult validationResult =
                    new ValidationResult(1, 1, 0, List.of());
            when(orderCsvProcessor.validate(any(), any(), any()))
                    .thenReturn(validationResult);

            InterfaceService.InterfaceValidationResponse result =
                    interfaceService.validate("IFX-002", fileName, warehouseId);

            assertThat(result.hasFileError()).isFalse();
            assertThat(result.totalRows()).isEqualTo(1);
            assertThat(result.successCount()).isEqualTo(1);
            verify(orderCsvProcessor).validateHeader(header);
            verify(orderCsvProcessor).validate(any(), any(), any());
        }

        @Test
        @DisplayName("異常系 — IFX-002ヘッダ検証エラーでfileError応答")
        void validate_ifx002_headerError_returnsFileError() {
            when(blobStorageClient.getFileSize("order", "bad.csv")).thenReturn(100L);
            when(blobStorageClient.downloadFile("order", "bad.csv"))
                    .thenReturn(new ByteArrayInputStream("a,b\n1,2\n".getBytes()));

            String[] header = {"a", "b"};
            when(csvParser.parse(any())).thenReturn(
                    new CsvParser.CsvParseResult(header, List.<String[]>of(new String[]{"1", "2"})));
            org.mockito.Mockito.doThrow(
                    new CsvParser.CsvParseException("WMS-E-IFX-003", "カラム数不正"))
                    .when(orderCsvProcessor).validateHeader(header);

            InterfaceService.InterfaceValidationResponse result =
                    interfaceService.validate("IFX-002", "bad.csv", 1L);

            assertThat(result.hasFileError()).isTrue();
            assertThat(result.fileErrorCode()).isEqualTo("WMS-E-IFX-003");
        }
    }

    @Nested
    @DisplayName("importFile — IFX-002")
    class ImportFileOrder {

        @BeforeEach
        void setUp() {
            setupSecurityContext();
        }

        @Test
        @DisplayName("正常系 — IFX-002 SUCCESS_ONLYモードで取り込み成功")
        void importFile_ifx002_successOnly_completed() {
            setupTransactionTemplate();
            String fileName = "ORD-001.csv";
            Long warehouseId = 1L;

            when(blobStorageClient.getFileSize("order", fileName)).thenReturn(1024L);
            when(blobStorageClient.downloadFile("order", fileName))
                    .thenReturn(new ByteArrayInputStream("csv".getBytes(StandardCharsets.UTF_8)));

            String[] header = {"partner_code", "planned_date", "product_code",
                    "unit_type", "ordered_qty", "note"};
            List<String[]> dataRows = List.<String[]>of(
                    new String[]{"CUS-0001", "2026-03-22", "PRD-001", "CASE", "50", ""});
            when(csvParser.parse(any())).thenReturn(
                    new CsvParser.CsvParseResult(header, dataRows));

            Warehouse warehouse = new Warehouse();
            warehouse.setWarehouseCode("WH-001");
            warehouse.setWarehouseName("Warehouse 1");
            setField(warehouse, "id", warehouseId);
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

            Partner partner = new Partner();
            partner.setPartnerCode("CUS-0001");
            partner.setPartnerName("Customer 1");
            partner.setPartnerType(PartnerType.CUSTOMER);
            setField(partner, "id", 1L);
            setField(partner, "isActive", true);
            when(partnerRepository.findByPartnerCodeIn(any())).thenReturn(List.of(partner));

            Product product = new Product();
            product.setProductCode("PRD-001");
            product.setProductName("Product 1");
            product.setLotManageFlag(false);
            product.setExpiryManageFlag(false);
            setField(product, "id", 10L);
            setField(product, "isActive", true);
            when(productRepository.findByProductCodeIn(any())).thenReturn(List.of(product));

            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 20));

            ValidationResult validationResult =
                    new ValidationResult(1, 1, 0, List.of());
            when(orderCsvProcessor.validate(any(), any(), any()))
                    .thenReturn(validationResult);

            OutboundSlip mockSlip = OutboundSlip.builder()
                    .slipNumber("OUT-20260320-0001")
                    .slipType("NORMAL")
                    .status("ORDERED")
                    .build();
            when(orderCsvProcessor.buildSlips(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(mockSlip));
            when(outboundSlipRepository.saveAll(anyList())).thenReturn(List.of(mockSlip));

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(false).build();
            when(ifExecutionRepository.save(any())).thenReturn(execution);
            when(blobStorageClient.moveToProcessed("order", fileName))
                    .thenReturn("order/processed/2026/03/20/20260320_120000_ORD-001.csv");

            InterfaceService.InterfaceImportResponse result =
                    interfaceService.importFile("IFX-002", fileName, warehouseId, "SUCCESS_ONLY");

            assertThat(result.status()).isEqualTo("COMPLETED");
            assertThat(result.successCount()).isEqualTo(1);
            assertThat(result.mode()).isEqualTo("SUCCESS_ONLY");
            verify(outboundSlipRepository).saveAll(anyList());
            verify(inboundSlipRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("正常系 — IFX-002 DISCARDモードでDB登録なし")
        void importFile_ifx002_discard_noDbInsert() {
            setupTransactionTemplate();
            String fileName = "ORD-001.csv";
            Long warehouseId = 1L;

            when(blobStorageClient.getFileSize("order", fileName)).thenReturn(1024L);
            when(blobStorageClient.downloadFile("order", fileName))
                    .thenReturn(new ByteArrayInputStream("h\nd\n".getBytes()));

            CsvParser.CsvParseResult parseResult =
                    new CsvParser.CsvParseResult(new String[]{"h"}, List.<String[]>of(new String[]{"d"}));
            when(csvParser.parse(any())).thenReturn(parseResult);

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(false).build();
            when(ifExecutionRepository.save(any())).thenReturn(execution);
            when(blobStorageClient.moveToProcessed("order", fileName))
                    .thenReturn("processed/path");

            InterfaceService.InterfaceImportResponse result =
                    interfaceService.importFile("IFX-002", fileName, warehouseId, "DISCARD");

            assertThat(result.status()).isEqualTo("DISCARDED");
            assertThat(result.mode()).isEqualTo("DISCARD");
            verify(outboundSlipRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("正常系 — IFX-002伝票番号が正常に採番される（OUT-YYYYMMDD-NNNN形式）")
        void importFile_ifx002_slipNumberGenerated_correctly() {
            setupTransactionTemplate();
            String fileName = "ORD-001.csv";
            Long warehouseId = 1L;

            when(blobStorageClient.getFileSize("order", fileName)).thenReturn(1024L);
            when(blobStorageClient.downloadFile("order", fileName))
                    .thenReturn(new ByteArrayInputStream("csv".getBytes(StandardCharsets.UTF_8)));

            String[] header = {"partner_code", "planned_date", "product_code",
                    "unit_type", "ordered_qty", "note"};
            List<String[]> dataRows = List.<String[]>of(
                    new String[]{"CUS-0001", "2026-03-22", "PRD-001", "CASE", "50", ""});
            when(csvParser.parse(any())).thenReturn(
                    new CsvParser.CsvParseResult(header, dataRows));

            Warehouse warehouse = new Warehouse();
            warehouse.setWarehouseCode("WH-001");
            warehouse.setWarehouseName("WH 1");
            setField(warehouse, "id", warehouseId);
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
            when(partnerRepository.findByPartnerCodeIn(any())).thenReturn(List.of());
            when(productRepository.findByProductCodeIn(any())).thenReturn(List.of());
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 20));
            when(outboundSlipRepository.findMaxSequenceByDate("20260320")).thenReturn(3);

            ValidationResult validationResult =
                    new ValidationResult(1, 1, 0, List.of());
            when(orderCsvProcessor.validate(any(), any(), any()))
                    .thenReturn(validationResult);

            final String[] capturedSlipNumber = new String[1];
            when(orderCsvProcessor.buildSlips(any(), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(inv -> {
                        SlipNumberGenerator gen = inv.getArgument(4);
                        capturedSlipNumber[0] = gen.generate(LocalDate.of(2026, 3, 20));
                        return List.of();
                    });

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(false).build();
            when(ifExecutionRepository.save(any())).thenReturn(execution);
            when(blobStorageClient.moveToProcessed("order", fileName))
                    .thenReturn("processed/path");

            interfaceService.importFile("IFX-002", fileName, warehouseId, "SUCCESS_ONLY");

            assertThat(capturedSlipNumber[0]).isEqualTo("OUT-20260320-0004");
        }

        @Test
        @DisplayName("異常系 — IFX-002伝票番号が9999超過でBusinessRuleViolationException")
        void importFile_ifx002_slipNumberExceeded_throwsException() {
            setupTransactionTemplate();
            String fileName = "ORD-001.csv";
            Long warehouseId = 1L;

            when(blobStorageClient.getFileSize("order", fileName)).thenReturn(1024L);
            when(blobStorageClient.downloadFile("order", fileName))
                    .thenReturn(new ByteArrayInputStream("csv".getBytes(StandardCharsets.UTF_8)));

            String[] header = {"partner_code", "planned_date", "product_code",
                    "unit_type", "ordered_qty", "note"};
            List<String[]> dataRows = List.<String[]>of(
                    new String[]{"CUS-0001", "2026-03-22", "PRD-001", "CASE", "50", ""});
            when(csvParser.parse(any())).thenReturn(
                    new CsvParser.CsvParseResult(header, dataRows));

            Warehouse warehouse = new Warehouse();
            warehouse.setWarehouseCode("WH-001");
            warehouse.setWarehouseName("WH 1");
            setField(warehouse, "id", warehouseId);
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
            when(partnerRepository.findByPartnerCodeIn(any())).thenReturn(List.of());
            when(productRepository.findByProductCodeIn(any())).thenReturn(List.of());
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 20));
            when(outboundSlipRepository.findMaxSequenceByDate(any())).thenReturn(9999);

            ValidationResult validationResult =
                    new ValidationResult(1, 1, 0, List.of());
            when(orderCsvProcessor.validate(any(), any(), any()))
                    .thenReturn(validationResult);

            when(orderCsvProcessor.buildSlips(any(), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(inv -> {
                        SlipNumberGenerator gen = inv.getArgument(4);
                        gen.generate(LocalDate.of(2026, 3, 20));
                        return List.of();
                    });

            assertThatThrownBy(() -> interfaceService.importFile(
                    "IFX-002", fileName, warehouseId, "SUCCESS_ONLY"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("9999");
        }

        @Test
        @DisplayName("異常系 — IFX-002 SUCCESS_ONLYでヘッダ検証エラーはBusinessRuleViolationException")
        void importFile_ifx002_headerError_throwsException() {
            when(blobStorageClient.getFileSize("order", "bad.csv")).thenReturn(100L);
            when(blobStorageClient.downloadFile("order", "bad.csv"))
                    .thenReturn(new ByteArrayInputStream("a,b\n1,2\n".getBytes()));

            String[] header = {"a", "b"};
            when(csvParser.parse(any())).thenReturn(
                    new CsvParser.CsvParseResult(header, List.<String[]>of(new String[]{"1", "2"})));
            org.mockito.Mockito.doThrow(
                    new CsvParser.CsvParseException("WMS-E-IFX-003", "header error"))
                    .when(orderCsvProcessor).validateHeader(header);

            assertThatThrownBy(() -> interfaceService.importFile(
                    "IFX-002", "bad.csv", 1L, "SUCCESS_ONLY"))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }
}
