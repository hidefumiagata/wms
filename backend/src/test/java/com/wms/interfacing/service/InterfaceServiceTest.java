package com.wms.interfacing.service;

import com.wms.inbound.entity.InboundSlip;
import com.wms.inbound.repository.InboundSlipRepository;
import com.wms.interfacing.blob.BlobStorageClient;
import com.wms.interfacing.entity.IfExecution;
import com.wms.interfacing.model.CsvFieldError;
import com.wms.interfacing.model.CsvMasterCache;
import com.wms.interfacing.model.CsvRowError;
import com.wms.interfacing.model.SlipNumberGenerator;
import com.wms.interfacing.model.CsvValidationResult;
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

            CsvValidationResult validationResult =
                    new CsvValidationResult(1, 1, 0, List.of());
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

            CsvValidationResult validationResult =
                    new CsvValidationResult(1, 1, 0, List.of());
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

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(true).build();
            when(ifExecutionRepository.save(any())).thenReturn(execution);

            when(blobStorageClient.moveToProcessed("inbound-plan", fileName))
                    .thenReturn("inbound-plan/processed/2026/03/20/20260320_120000_INB-PLAN-001.csv");

            InterfaceService.InterfaceImportResponse result =
                    interfaceService.importFile("IFX-001", fileName, warehouseId, "SUCCESS_ONLY");

            assertThat(result.status()).isEqualTo("COMPLETED");
            assertThat(result.successCount()).isEqualTo(1);
            assertThat(result.mode()).isEqualTo("SUCCESS_ONLY");
            verify(inboundSlipRepository).saveAll(anyList());
            // Regression guard (#374): tx1(saveExecution) + tx2(flag更新) = 2回
            verify(ifExecutionRepository, org.mockito.Mockito.times(2)).save(any());
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

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(true).build();
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

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(true).build();
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
        @DisplayName("異常系 — Blob移動失敗でもtx1確定しflagはtrueのまま残る (#374)")
        void importFile_blobMoveFailed_flagRemainsTrueAndRowPersisted() {
            stubDiscardBasics("INB-PLAN-001.csv");
            String fileName = "INB-PLAN-001.csv";
            Long warehouseId = 1L;

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(true).build();
            when(ifExecutionRepository.save(any())).thenReturn(execution);

            when(blobStorageClient.moveToProcessed("inbound-plan", fileName))
                    .thenThrow(new RuntimeException("Blob move failed"));

            InterfaceService.InterfaceImportResponse result =
                    interfaceService.importFile("IFX-001", fileName, warehouseId, "DISCARD");

            assertThat(result.status()).isEqualTo("DISCARDED");
            // saveExecution 内の1回のみ。catch ブロックでの追加 save は削除済み（悲観デフォルトと同値のため不要）
            verify(ifExecutionRepository, org.mockito.Mockito.times(1)).save(any());
            // tx1(saveExecution) の 1回のみ実行される。tx2(flag更新) は moveBlob が先に例外を投げるため呼ばれない
            verify(transactionTemplate, org.mockito.Mockito.times(1)).execute(any());
            // 悲観デフォルトのまま残ること
            assertThat(execution.getBlobMoveFailed()).isTrue();
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("異常系 — Blob移動成功後のflag更新tx失敗でもtx1は確定しflagはtrueのまま (#374) [DISCARD]")
        void importFile_discard_flagUpdateTxFailed_rowPersistsWithFlagTrue() {
            String fileName = "INB-PLAN-001.csv";
            Long warehouseId = 1L;

            when(blobStorageClient.getFileSize("inbound-plan", fileName)).thenReturn(1024L);
            when(blobStorageClient.downloadFile("inbound-plan", fileName))
                    .thenReturn(new ByteArrayInputStream("h\nd\n".getBytes()));
            CsvParser.CsvParseResult parseResult =
                    new CsvParser.CsvParseResult(new String[]{"h"}, List.<String[]>of(new String[]{"d"}));
            when(csvParser.parse(any())).thenReturn(parseResult);

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(true).build();
            when(ifExecutionRepository.save(any())).thenReturn(execution);

            // Blob移動は成功
            when(blobStorageClient.moveToProcessed("inbound-plan", fileName))
                    .thenReturn("processed/path");

            // transactionTemplate.execute: 1回目(tx1=saveExecution)は通す、2回目(tx2=flag更新)は例外
            when(transactionTemplate.execute(any()))
                    .thenAnswer(inv -> {
                        org.springframework.transaction.support.TransactionCallback<?> callback = inv.getArgument(0);
                        return callback.doInTransaction(null);
                    })
                    .thenThrow(new RuntimeException("flag update tx failed"));

            // moveBlobSafely の catch が握りつぶすので正常応答が返る
            InterfaceService.InterfaceImportResponse result =
                    interfaceService.importFile("IFX-001", fileName, warehouseId, "DISCARD");

            assertThat(result.status()).isEqualTo("DISCARDED");
            // tx1 の saveExecution のみ。tx2 は中で例外を投げるので save 到達しない
            verify(ifExecutionRepository, org.mockito.Mockito.times(1)).save(any());
            // execute は 2回（tx1 成功 / tx2 throw）
            verify(transactionTemplate, org.mockito.Mockito.times(2)).execute(any());
            // flag は true のまま（moveBlobSafely 内で execution.setBlobMoveFailed(false) まで
            // 到達せずに execute が例外を投げるため）
            assertThat(execution.getBlobMoveFailed()).isTrue();
        }

        private void stubDiscardBasics(String fileName) {
            setupTransactionTemplate();
            when(blobStorageClient.getFileSize("inbound-plan", fileName)).thenReturn(1024L);
            when(blobStorageClient.downloadFile("inbound-plan", fileName))
                    .thenReturn(new ByteArrayInputStream("h\nd\n".getBytes()));
            CsvParser.CsvParseResult parseResult =
                    new CsvParser.CsvParseResult(new String[]{"h"}, List.<String[]>of(new String[]{"d"}));
            when(csvParser.parse(any())).thenReturn(parseResult);
        }

        @Test
        @DisplayName("正常系 — Blob移動成功時は独立txでblobMoveFailedがfalseに更新される (#374)")
        void importFile_blobMoveSuccess_flagUpdatedToFalseInIndependentTx() {
            setupTransactionTemplate();
            String fileName = "INB-PLAN-001.csv";
            Long warehouseId = 1L;

            when(blobStorageClient.getFileSize("inbound-plan", fileName)).thenReturn(1024L);
            when(blobStorageClient.downloadFile("inbound-plan", fileName))
                    .thenReturn(new ByteArrayInputStream("h\nd\n".getBytes()));

            CsvParser.CsvParseResult parseResult =
                    new CsvParser.CsvParseResult(new String[]{"h"}, List.<String[]>of(new String[]{"d"}));
            when(csvParser.parse(any())).thenReturn(parseResult);

            // saveExecution で悲観デフォルトtrueで保存されたものがここに返る想定
            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(true).build();
            when(ifExecutionRepository.save(any())).thenReturn(execution);

            when(blobStorageClient.moveToProcessed("inbound-plan", fileName))
                    .thenReturn("processed/path");

            InterfaceService.InterfaceImportResponse result =
                    interfaceService.importFile("IFX-001", fileName, warehouseId, "DISCARD");

            assertThat(result.status()).isEqualTo("DISCARDED");
            // saveExecution(1回) + moveBlobSafely内の独立tx(1回) = 計2回
            verify(ifExecutionRepository, org.mockito.Mockito.times(2)).save(any());
            // flag が false に更新されていること
            assertThat(execution.getBlobMoveFailed()).isFalse();
            // transactionTemplate が 2回 execute される (tx1: saveExecution, tx2: flag更新)
            verify(transactionTemplate, org.mockito.Mockito.times(2)).execute(any());
        }

        @Test
        @DisplayName("悲観デフォルト — saveExecution は blobMoveFailed=true / blobPath=null で新規作成する (#374)")
        void importFile_ifExecution_createdWithPessimisticDefault() {
            setupTransactionTemplate();
            String fileName = "INB-PLAN-001.csv";
            Long warehouseId = 1L;

            when(blobStorageClient.getFileSize("inbound-plan", fileName)).thenReturn(1024L);
            when(blobStorageClient.downloadFile("inbound-plan", fileName))
                    .thenReturn(new ByteArrayInputStream("h\nd\n".getBytes()));

            CsvParser.CsvParseResult parseResult =
                    new CsvParser.CsvParseResult(new String[]{"h"}, List.<String[]>of(new String[]{"d"}));
            when(csvParser.parse(any())).thenReturn(parseResult);

            IfExecution savedEcho = IfExecution.builder().id(1L).blobMoveFailed(true).build();
            when(ifExecutionRepository.save(any())).thenReturn(savedEcho);
            when(blobStorageClient.moveToProcessed("inbound-plan", fileName))
                    .thenReturn("processed/path");

            org.mockito.ArgumentCaptor<IfExecution> captor =
                    org.mockito.ArgumentCaptor.forClass(IfExecution.class);

            interfaceService.importFile("IFX-001", fileName, warehouseId, "DISCARD");

            // tx1(saveExecution) + tx2(moveBlobSafely内のflag更新) = 2回
            verify(ifExecutionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
            IfExecution firstArg = captor.getAllValues().get(0);
            IfExecution secondArg = captor.getAllValues().get(1);

            assertThat(firstArg.getBlobMoveFailed())
                    .as("1回目(saveExecution) は悲観デフォルト true")
                    .isTrue();
            assertThat(firstArg)
                    .as("tx1 と tx2 で渡される IfExecution は別インスタンスでも同一参照でもどちらでも構わないが、"
                            + "少なくとも tx2 は mock の返却した savedEcho でなければならない")
                    .isNotSameAs(secondArg);
            assertThat(firstArg.getBlobPath())
                    .as("saveExecution の1回目引数は Blob 移動前なので blobPath は null")
                    .isNull();
        }

        @Test
        @DisplayName("異常系 — SUCCESS_ONLY: Blob移動失敗時もflagはtrueのまま (#374)")
        void importFile_successOnly_blobMoveFailed_flagRemainsTrue() {
            setupTransactionTemplate();
            stubSuccessOnlyInboundBasics();
            String fileName = "INB-PLAN-001.csv";
            Long warehouseId = 1L;

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(true).build();
            when(ifExecutionRepository.save(any())).thenReturn(execution);
            when(blobStorageClient.moveToProcessed("inbound-plan", fileName))
                    .thenThrow(new RuntimeException("Blob move failed"));

            InterfaceService.InterfaceImportResponse result =
                    interfaceService.importFile("IFX-001", fileName, warehouseId, "SUCCESS_ONLY");

            assertThat(result.status()).isEqualTo("COMPLETED");
            assertThat(result.successCount()).isEqualTo(1);
            // tx1 の saveExecution 1回のみ。slips の saveAll はカウント外。
            verify(ifExecutionRepository, org.mockito.Mockito.times(1)).save(any());
            // tx1(handleImport 内の execute) の1回のみ
            verify(transactionTemplate, org.mockito.Mockito.times(1)).execute(any());
            // 悲観デフォルトのまま
            assertThat(execution.getBlobMoveFailed()).isTrue();
        }

        @Test
        @DisplayName("正常系 — SUCCESS_ONLY: Blob移動成功時にflagがfalseに更新される独立tx (#374)")
        void importFile_successOnly_blobMoveSuccess_flagUpdatedInIndependentTx() {
            setupTransactionTemplate();
            stubSuccessOnlyInboundBasics();
            String fileName = "INB-PLAN-001.csv";
            Long warehouseId = 1L;

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(true).build();
            when(ifExecutionRepository.save(any())).thenReturn(execution);
            when(blobStorageClient.moveToProcessed("inbound-plan", fileName))
                    .thenReturn("inbound-plan/processed/2026/03/20/xxx.csv");

            InterfaceService.InterfaceImportResponse result =
                    interfaceService.importFile("IFX-001", fileName, warehouseId, "SUCCESS_ONLY");

            assertThat(result.status()).isEqualTo("COMPLETED");
            // tx1(saveExecution) + tx2(flag更新) = 2回（slips の saveAll はカウント外）
            verify(ifExecutionRepository, org.mockito.Mockito.times(2)).save(any());
            // tx1(handleImport) と tx2(moveBlobSafely) の 2回
            verify(transactionTemplate, org.mockito.Mockito.times(2)).execute(any());
            assertThat(execution.getBlobMoveFailed()).isFalse();
        }

        /**
         * IFX-001 SUCCESS_ONLY 経路の共通スタブ。
         * マスタ参照・CSVパース・バリデーション・slips 構築までをモック化する。
         */
        private void stubSuccessOnlyInboundBasics() {
            String fileName = "INB-PLAN-001.csv";
            Long warehouseId = 1L;

            when(blobStorageClient.getFileSize("inbound-plan", fileName)).thenReturn(1024L);
            String csvContent = "partner_code,planned_date,product_code,unit_type,planned_qty,"
                    + "lot_number,expiry_date,note\nSUP-0001,2026-03-25,PRD-001,CASE,100,,,\n";
            when(blobStorageClient.downloadFile("inbound-plan", fileName))
                    .thenReturn(new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8)));

            String[] header = {"partner_code", "planned_date", "product_code",
                    "unit_type", "planned_qty", "lot_number", "expiry_date", "note"};
            List<String[]> dataRows = List.<String[]>of(
                    new String[]{"SUP-0001", "2026-03-25", "PRD-001", "CASE", "100", "", "", ""});
            when(csvParser.parse(any())).thenReturn(
                    new CsvParser.CsvParseResult(header, dataRows));

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

            CsvValidationResult validationResult =
                    new CsvValidationResult(1, 1, 0, List.of());
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

            CsvValidationResult valResult =
                    new CsvValidationResult(1, 0, 1, List.of(
                            new CsvRowError(1, List.of(
                                    new CsvFieldError("partner_code",
                                            "WMS-E-IFX-301", "err")))));
            when(inboundPlanCsvProcessor.validate(any(), any(), any())).thenReturn(valResult);
            when(inboundPlanCsvProcessor.buildSlips(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(true).build();
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
    @DisplayName("buildCsvMasterCache")
    class BuildCsvMasterCache {

        @Test
        @DisplayName("正常系 — マスタキャッシュが正しく構築される")
        void buildCsvMasterCache_success() {
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

            CsvMasterCache cache =
                    interfaceService.buildMasterCache(dataRows, warehouseId);

            assertThat(cache.getPartner("SUP-0001")).isNotNull();
            assertThat(cache.getProduct("PRD-001")).isNotNull();
            assertThat(cache.getWarehouse()).isNotNull();
        }

        @Test
        @DisplayName("正常系 — 空データ行でもキャッシュが構築される")
        void buildCsvMasterCache_emptyDataRows() {
            Long warehouseId = 1L;
            Warehouse warehouse = new Warehouse();
            warehouse.setWarehouseCode("WH-001");
            warehouse.setWarehouseName("Warehouse 1");
            setField(warehouse, "id", warehouseId);
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

            // Empty data rows — no partner/product codes to look up
            List<String[]> dataRows = List.<String[]>of(new String[]{"", "", ""});

            CsvMasterCache cache =
                    interfaceService.buildMasterCache(dataRows, warehouseId);

            assertThat(cache.getWarehouse()).isNotNull();
            // No partner/product lookups performed
            verify(partnerRepository, never()).findByPartnerCodeIn(any());
            verify(productRepository, never()).findByProductCodeIn(any());
        }

        @Test
        @DisplayName("異常系 — 倉庫が存在しない場合ResourceNotFoundException")
        void buildCsvMasterCache_warehouseNotFound() {
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
        void buildCsvMasterCache_zeroLengthRow_handledGracefully() {
            Long warehouseId = 1L;
            Warehouse warehouse = new Warehouse();
            warehouse.setWarehouseCode("WH-001");
            warehouse.setWarehouseName("WH 1");
            setField(warehouse, "id", warehouseId);
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

            // row.length == 0: row.length > 0 is false, row.length > 2 is false
            List<String[]> dataRows = List.<String[]>of(new String[]{});

            CsvMasterCache cache =
                    interfaceService.buildMasterCache(dataRows, warehouseId);

            assertThat(cache.getWarehouse()).isNotNull();
        }

        @Test
        @DisplayName("境界値 — カラム数が1の行（partner_codeのみ）")
        void buildCsvMasterCache_singleColumnRow_handledGracefully() {
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

            CsvMasterCache cache =
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

            CsvValidationResult validationResult =
                    new CsvValidationResult(1, 1, 0, List.of());
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

            CsvValidationResult validationResult =
                    new CsvValidationResult(1, 1, 0, List.of());
            when(inboundPlanCsvProcessor.validate(any(), any(), any()))
                    .thenReturn(validationResult);

            final String[] capturedSlipNumber = new String[1];
            when(inboundPlanCsvProcessor.buildSlips(any(), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(inv -> {
                        SlipNumberGenerator gen = inv.getArgument(4);
                        capturedSlipNumber[0] = gen.generate(LocalDate.of(2026, 3, 20));
                        return List.of();
                    });

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(true).build();
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

            CsvValidationResult validationResult =
                    new CsvValidationResult(1, 1, 0, List.of());
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

            CsvValidationResult validationResult =
                    new CsvValidationResult(1, 1, 0, List.of());
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

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(true).build();
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

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(true).build();
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

            CsvValidationResult validationResult =
                    new CsvValidationResult(1, 1, 0, List.of());
            when(orderCsvProcessor.validate(any(), any(), any()))
                    .thenReturn(validationResult);

            final String[] capturedSlipNumber = new String[1];
            when(orderCsvProcessor.buildSlips(any(), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(inv -> {
                        SlipNumberGenerator gen = inv.getArgument(4);
                        capturedSlipNumber[0] = gen.generate(LocalDate.of(2026, 3, 20));
                        return List.of();
                    });

            IfExecution execution = IfExecution.builder().id(1L).blobMoveFailed(true).build();
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

            CsvValidationResult validationResult =
                    new CsvValidationResult(1, 1, 0, List.of());
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
