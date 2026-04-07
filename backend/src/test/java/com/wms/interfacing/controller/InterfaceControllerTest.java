package com.wms.interfacing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.generated.model.InterfaceImportRequest;
import com.wms.generated.model.InterfaceValidateRequest;
import com.wms.generated.model.ImportMode;
import com.wms.interfacing.blob.BlobStorageClient;
import com.wms.interfacing.entity.IfExecution;
import com.wms.interfacing.model.CsvFieldError;
import com.wms.interfacing.model.CsvRowError;
import com.wms.interfacing.model.CsvValidationResult;
import com.wms.interfacing.service.InterfaceService;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.security.JwtAuthenticationFilter;
import com.wms.shared.security.JwtTokenProvider;
import com.wms.system.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterfaceController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("InterfaceController")
class InterfaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InterfaceService interfaceService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    @Nested
    @DisplayName("GET /api/v1/interface/{ifId}/files")
    class ListFiles {

        @Test
        @DisplayName("200 — ファイル一覧を返す")
        void listFiles_returns200() throws Exception {
            var file = new BlobStorageClient.BlobFileInfo(
                    "INB-PLAN-001.csv", 12288, OffsetDateTime.parse("2026-03-20T09:30:15+09:00"));
            when(interfaceService.listFiles("IFX-001")).thenReturn(List.of(file));

            mockMvc.perform(get("/api/v1/interface/IFX-001/files"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(1))
                    .andExpect(jsonPath("$.files[0].fileName").value("INB-PLAN-001.csv"))
                    .andExpect(jsonPath("$.files[0].fileSize").value(12288));
        }

        @Test
        @DisplayName("200 — 空のファイル一覧")
        void listFiles_empty_returns200() throws Exception {
            when(interfaceService.listFiles("IFX-001")).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/interface/IFX-001/files"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(0))
                    .andExpect(jsonPath("$.files").isEmpty());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/interface/{ifId}/validate")
    class ValidateFile {

        @Test
        @DisplayName("200 — バリデーション成功結果を返す")
        void validate_success_returns200() throws Exception {
            InterfaceService.InterfaceValidationResponse resp =
                    InterfaceService.InterfaceValidationResponse.success("INB-PLAN-001.csv",
                            new CsvValidationResult(10, 10, 0, List.of()));
            when(interfaceService.validate(eq("IFX-001"), eq("INB-PLAN-001.csv"), eq(1L)))
                    .thenReturn(resp);

            InterfaceValidateRequest request = new InterfaceValidateRequest()
                    .fileName("INB-PLAN-001.csv")
                    .warehouseId(1L);

            mockMvc.perform(post("/api/v1/interface/IFX-001/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fileName").value("INB-PLAN-001.csv"))
                    .andExpect(jsonPath("$.totalRows").value(10))
                    .andExpect(jsonPath("$.successCount").value(10))
                    .andExpect(jsonPath("$.errorCount").value(0));
        }

        @Test
        @DisplayName("200 — バリデーションエラーありの結果を返す")
        void validate_withErrors_returns200() throws Exception {
            var rowError = new CsvRowError(5, List.of(
                    new CsvFieldError("partner_code", "WMS-E-IFX-301",
                            "取引先コードが存在しません")));
            InterfaceService.InterfaceValidationResponse resp =
                    InterfaceService.InterfaceValidationResponse.success("INB-PLAN-001.csv",
                            new CsvValidationResult(10, 9, 1, List.of(rowError)));
            when(interfaceService.validate(any(), any(), any())).thenReturn(resp);

            InterfaceValidateRequest request = new InterfaceValidateRequest()
                    .fileName("INB-PLAN-001.csv")
                    .warehouseId(1L);

            mockMvc.perform(post("/api/v1/interface/IFX-001/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errorCount").value(1))
                    .andExpect(jsonPath("$.rows[0].rowNumber").value(5))
                    .andExpect(jsonPath("$.rows[0].errors[0].errorCode").value("WMS-E-IFX-301"));
        }

        @Test
        @DisplayName("200 — ファイルレベルエラーの結果を返す")
        void validate_fileError_returns200() throws Exception {
            InterfaceService.InterfaceValidationResponse resp =
                    InterfaceService.InterfaceValidationResponse.fileError(
                            "bad.csv", "WMS-E-IFX-003", "カラム数が不正です");
            when(interfaceService.validate(any(), any(), any())).thenReturn(resp);

            InterfaceValidateRequest request = new InterfaceValidateRequest()
                    .fileName("bad.csv")
                    .warehouseId(1L);

            mockMvc.perform(post("/api/v1/interface/IFX-001/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fileError.errorCode").value("WMS-E-IFX-003"))
                    .andExpect(jsonPath("$.rows").isEmpty());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/interface/{ifId}/import")
    class ImportFile {

        @Test
        @DisplayName("200 — SUCCESS_ONLYモードで取り込み成功")
        void importFile_successOnly_returns200() throws Exception {
            InterfaceService.InterfaceImportResponse resp =
                    new InterfaceService.InterfaceImportResponse(10, 0, "SUCCESS_ONLY", "COMPLETED");
            when(interfaceService.importFile(eq("IFX-001"), any(), any(), eq("SUCCESS_ONLY")))
                    .thenReturn(resp);

            InterfaceImportRequest request = new InterfaceImportRequest()
                    .fileName("INB-PLAN-001.csv")
                    .warehouseId(1L)
                    .mode(ImportMode.SUCCESS_ONLY);

            mockMvc.perform(post("/api/v1/interface/IFX-001/import")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.successCount").value(10))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.mode").value("SUCCESS_ONLY"));
        }

        @Test
        @DisplayName("200 — DISCARDモードで破棄成功")
        void importFile_discard_returns200() throws Exception {
            InterfaceService.InterfaceImportResponse resp =
                    new InterfaceService.InterfaceImportResponse(0, 5, "DISCARD", "DISCARDED");
            when(interfaceService.importFile(eq("IFX-001"), any(), any(), eq("DISCARD")))
                    .thenReturn(resp);

            InterfaceImportRequest request = new InterfaceImportRequest()
                    .fileName("INB-PLAN-001.csv")
                    .warehouseId(1L)
                    .mode(ImportMode.DISCARD);

            mockMvc.perform(post("/api/v1/interface/IFX-001/import")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DISCARDED"))
                    .andExpect(jsonPath("$.mode").value("DISCARD"));
        }

        @Test
        @DisplayName("422 — 業務ルール違反エラー")
        void importFile_businessRuleViolation_returns422() throws Exception {
            when(interfaceService.importFile(any(), any(), any(), any()))
                    .thenThrow(new BusinessRuleViolationException("CSV_PARSE_ERROR", "パースエラー"));

            InterfaceImportRequest request = new InterfaceImportRequest()
                    .fileName("bad.csv")
                    .warehouseId(1L)
                    .mode(ImportMode.SUCCESS_ONLY);

            mockMvc.perform(post("/api/v1/interface/IFX-001/import")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/interface/executions")
    class ListExecutions {

        private IfExecution createExecution() {
            return IfExecution.builder()
                    .id(1L)
                    .ifType("INBOUND_PLAN")
                    .fileName("INB-PLAN-001.csv")
                    .totalCount(100)
                    .successCount(97)
                    .errorCount(3)
                    .mode("SUCCESS_ONLY")
                    .status("COMPLETED")
                    .blobMoveFailed(false)
                    .warehouseId(1L)
                    .executedAt(OffsetDateTime.parse("2026-03-18T14:30:05+09:00"))
                    .executedBy(1L)
                    .build();
        }

        @Test
        @DisplayName("200 — 実行履歴一覧を返す")
        void listExecutions_returns200() throws Exception {
            IfExecution exec = createExecution();
            Page<IfExecution> page = new PageImpl<>(List.of(exec), PageRequest.of(0, 20), 1);
            when(interfaceService.listExecutions(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(page);
            when(userService.getUserFullNameMap(any())).thenReturn(Map.of(1L, "山田 太郎"));

            mockMvc.perform(get("/api/v1/interface/executions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].fileName").value("INB-PLAN-001.csv"))
                    .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                    .andExpect(jsonPath("$.content[0].executedByName").value("山田 太郎"));
        }

        @Test
        @DisplayName("200 — フィルタパラメータ付きで呼び出し")
        void listExecutions_withFilters_returns200() throws Exception {
            Page<IfExecution> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
            when(interfaceService.listExecutions(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/interface/executions")
                            .param("ifType", "IFX-001")
                            .param("dateFrom", "2026-03-01")
                            .param("dateTo", "2026-03-31")
                            .param("status", "COMPLETED")
                            .param("fileName", "INB"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.content").isEmpty());

            verify(interfaceService).listExecutions(
                    eq("INBOUND_PLAN"), any(), any(), eq("COMPLETED"),
                    isNull(), eq("INB"), any());
        }

        @Test
        @DisplayName("200 — warehouseIdフィルタ付きで呼び出し")
        void listExecutions_withWarehouseId_returns200() throws Exception {
            IfExecution exec = createExecution();
            Page<IfExecution> page = new PageImpl<>(List.of(exec), PageRequest.of(0, 20), 1);
            when(interfaceService.listExecutions(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(page);
            when(userService.getUserFullNameMap(any())).thenReturn(Map.of(1L, "山田 太郎"));

            mockMvc.perform(get("/api/v1/interface/executions")
                            .param("warehouseId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].warehouseId").value(1));

            verify(interfaceService).listExecutions(
                    isNull(), isNull(), isNull(), isNull(), eq(1L), isNull(), any());
        }

        @Test
        @DisplayName("200 — ユーザーが見つからない場合はIDを表示")
        void listExecutions_userNotFound_showsId() throws Exception {
            IfExecution exec = createExecution();
            Page<IfExecution> page = new PageImpl<>(List.of(exec), PageRequest.of(0, 20), 1);
            when(interfaceService.listExecutions(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(page);
            when(userService.getUserFullNameMap(any())).thenReturn(Map.of());

            mockMvc.perform(get("/api/v1/interface/executions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].executedByName").value("1"));
        }

        @Test
        @DisplayName("200 — ソートパラメータが適用される")
        void listExecutions_withSort_returns200() throws Exception {
            Page<IfExecution> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
            when(interfaceService.listExecutions(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/interface/executions")
                            .param("sort", "fileName,asc"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("200 — ソートにdirection省略の場合ASCが適用される")
        void listExecutions_sortWithoutDirection_usesAsc() throws Exception {
            Page<IfExecution> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
            when(interfaceService.listExecutions(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/interface/executions")
                            .param("sort", "fileName"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("200 — 不正ソートプロパティの場合デフォルトにフォールバック")
        void listExecutions_invalidSort_fallsBackToDefault() throws Exception {
            Page<IfExecution> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
            when(interfaceService.listExecutions(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/interface/executions")
                            .param("sort", "invalidField,desc"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("200 — 空ページの場合userService呼び出しあり（空Set）")
        void listExecutions_emptyPage_noUserFetch() throws Exception {
            Page<IfExecution> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
            when(interfaceService.listExecutions(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/interface/executions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }

    }

    @Nested
    @DisplayName("IFX-002 endpoints")
    class Ifx002Endpoints {

        @Test
        @DisplayName("GET /api/v1/interface/IFX-002/files — 200ファイル一覧")
        void listFiles_ifx002_returns200() throws Exception {
            var file = new BlobStorageClient.BlobFileInfo(
                    "ORD-001.csv", 8192, OffsetDateTime.parse("2026-03-22T10:00:00+09:00"));
            when(interfaceService.listFiles("IFX-002")).thenReturn(List.of(file));

            mockMvc.perform(get("/api/v1/interface/IFX-002/files"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(1))
                    .andExpect(jsonPath("$.files[0].fileName").value("ORD-001.csv"));
        }

        @Test
        @DisplayName("POST /api/v1/interface/IFX-002/validate — 200バリデーション成功")
        void validate_ifx002_returns200() throws Exception {
            InterfaceService.InterfaceValidationResponse resp =
                    InterfaceService.InterfaceValidationResponse.success("ORD-001.csv",
                            new CsvValidationResult(5, 5, 0, List.of()));
            when(interfaceService.validate(eq("IFX-002"), eq("ORD-001.csv"), eq(1L)))
                    .thenReturn(resp);

            InterfaceValidateRequest request = new InterfaceValidateRequest()
                    .fileName("ORD-001.csv")
                    .warehouseId(1L);

            mockMvc.perform(post("/api/v1/interface/IFX-002/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fileName").value("ORD-001.csv"))
                    .andExpect(jsonPath("$.successCount").value(5));
        }

        @Test
        @DisplayName("POST /api/v1/interface/IFX-002/import — 200 SUCCESS_ONLY")
        void importFile_ifx002_returns200() throws Exception {
            InterfaceService.InterfaceImportResponse resp =
                    new InterfaceService.InterfaceImportResponse(3, 2, "SUCCESS_ONLY", "COMPLETED");
            when(interfaceService.importFile(eq("IFX-002"), any(), any(), eq("SUCCESS_ONLY")))
                    .thenReturn(resp);

            InterfaceImportRequest request = new InterfaceImportRequest()
                    .fileName("ORD-001.csv")
                    .warehouseId(1L)
                    .mode(ImportMode.SUCCESS_ONLY);

            mockMvc.perform(post("/api/v1/interface/IFX-002/import")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.successCount").value(3))
                    .andExpect(jsonPath("$.errorCount").value(2))
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }
    }
}
