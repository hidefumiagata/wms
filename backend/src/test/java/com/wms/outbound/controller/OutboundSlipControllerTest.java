package com.wms.outbound.controller;

import com.wms.generated.model.CancelOutboundRequest;
import com.wms.generated.model.CreateOutboundSlipRequest;
import com.wms.master.service.AreaService;
import com.wms.master.service.WarehouseService;
import com.wms.outbound.entity.OutboundSlip;
import com.wms.outbound.entity.OutboundSlipLine;
import com.wms.outbound.service.OutboundSlipService;
import com.wms.outbound.service.PickingService;
import com.wms.shared.exception.InvalidStateTransitionException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.security.JwtAuthenticationFilter;
import com.wms.shared.security.JwtTokenProvider;
import com.wms.system.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OutboundSlipController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("OutboundSlipController")
class OutboundSlipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OutboundSlipService outboundSlipService;

    @MockitoBean
    private PickingService pickingService;

    @MockitoBean
    private WarehouseService warehouseService;

    @MockitoBean
    private AreaService areaService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static final String SLIPS_URL = "/api/v1/outbound/slips";

    private OutboundSlip createSlip(Long id, String slipNumber, String status) {
        OutboundSlip slip = OutboundSlip.builder()
                .slipNumber(slipNumber)
                .slipType("NORMAL")
                .warehouseId(1L)
                .warehouseCode("WH-001")
                .warehouseName("東京DC")
                .partnerId(5L)
                .partnerCode("CUS-0001")
                .partnerName("顧客A")
                .plannedDate(LocalDate.of(2026, 3, 20))
                .status(status)
                .lines(new ArrayList<>())
                .build();
        setField(slip, "id", id);
        setField(slip, "createdAt", OffsetDateTime.now());
        setField(slip, "createdBy", 10L);
        setField(slip, "updatedAt", OffsetDateTime.now());
        setField(slip, "updatedBy", 10L);
        return slip;
    }

    private OutboundSlipLine createLine(Long id, OutboundSlip slip, int lineNo,
                                         String productCode, int orderedQty) {
        OutboundSlipLine line = OutboundSlipLine.builder()
                .outboundSlip(slip)
                .lineNo(lineNo)
                .productId((long) lineNo)
                .productCode(productCode)
                .productName("商品" + lineNo)
                .unitType("CASE")
                .orderedQty(orderedQty)
                .shippedQty(0)
                .lineStatus("ORDERED")
                .build();
        setField(line, "id", id);
        return line;
    }

    @Nested
    @DisplayName("GET /api/v1/outbound/slips")
    class ListTests {

        @Test
        @DisplayName("出荷伝票一覧をページング形式で返す")
        void list_returns200() throws Exception {
            OutboundSlip slip = createSlip(1L, "OUT-20260320-0001", "ORDERED");

            when(outboundSlipService.search(
                    eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(slip)));
            when(outboundSlipService.countLinesBySlipId(1L)).thenReturn(3L);

            mockMvc.perform(get(SLIPS_URL).param("warehouseId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].slipNumber").value("OUT-20260320-0001"))
                    .andExpect(jsonPath("$.content[0].slipType").value("NORMAL"))
                    .andExpect(jsonPath("$.content[0].status").value("ORDERED"))
                    .andExpect(jsonPath("$.content[0].lineCount").value(3))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("ステータスフィルタ指定で一覧を返す")
        void list_withStatusFilter_returns200() throws Exception {
            OutboundSlip slip = createSlip(1L, "OUT-20260320-0001", "ORDERED");

            when(outboundSlipService.search(
                    eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(slip)));
            when(outboundSlipService.countLinesBySlipId(1L)).thenReturn(2L);

            mockMvc.perform(get(SLIPS_URL)
                            .param("warehouseId", "1")
                            .param("status", "ORDERED", "ALLOCATED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("ソート指定descで一覧を返す")
        void list_withSortDesc_returns200() throws Exception {
            OutboundSlip slip = createSlip(1L, "OUT-20260320-0001", "ORDERED");

            when(outboundSlipService.search(
                    eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(slip)));
            when(outboundSlipService.countLinesBySlipId(1L)).thenReturn(1L);

            mockMvc.perform(get(SLIPS_URL)
                            .param("warehouseId", "1")
                            .param("sort", "slipNumber,desc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("無効なソートプロパティの場合デフォルトにフォールバックする")
        void list_withInvalidSortProperty_fallsBackToDefault() throws Exception {
            OutboundSlip slip = createSlip(1L, "OUT-20260320-0001", "ORDERED");

            when(outboundSlipService.search(
                    eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(slip)));
            when(outboundSlipService.countLinesBySlipId(1L)).thenReturn(1L);

            mockMvc.perform(get(SLIPS_URL)
                            .param("warehouseId", "1")
                            .param("sort", "invalidProperty"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("倉庫が存在しない場合404を返す")
        void list_warehouseNotFound_returns404() throws Exception {
            when(outboundSlipService.search(
                    eq(999L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenThrow(new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "倉庫 が見つかりません (id=999)"));

            mockMvc.perform(get(SLIPS_URL).param("warehouseId", "999"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/outbound/slips (create)")
    class CreateTests {

        private static final String CREATE_REQUEST_JSON = """
                {
                    "warehouseId": 1,
                    "partnerId": 5,
                    "plannedDate": "2026-03-22",
                    "slipType": "NORMAL",
                    "note": "テスト備考",
                    "lines": [
                        {
                            "productId": 10,
                            "unitType": "CASE",
                            "orderedQty": 100
                        }
                    ]
                }
                """;

        @Test
        @DisplayName("正常に201 Createdを返す")
        void create_returns201() throws Exception {
            OutboundSlip created = createSlip(1L, "OUT-20260322-0001", "ORDERED");
            OutboundSlipLine line = createLine(1L, created, 1, "PRD-0001", 100);
            created.getLines().add(line);

            when(outboundSlipService.create(any(CreateOutboundSlipRequest.class))).thenReturn(created);
            when(outboundSlipService.findByIdWithLines(1L)).thenReturn(created);

            mockMvc.perform(post(SLIPS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_REQUEST_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.slipNumber").value("OUT-20260322-0001"))
                    .andExpect(jsonPath("$.status").value("ORDERED"))
                    .andExpect(jsonPath("$.lines", hasSize(1)))
                    .andExpect(jsonPath("$.lines[0].productCode").value("PRD-0001"));
        }

        @Test
        @DisplayName("明細が空の場合400を返す")
        void create_emptyLines_returns400() throws Exception {
            String json = """
                    {
                        "warehouseId": 1,
                        "partnerId": 5,
                        "plannedDate": "2026-03-22",
                        "slipType": "NORMAL",
                        "lines": []
                    }
                    """;

            mockMvc.perform(post(SLIPS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/outbound/slips/{id}")
    class GetDetailTests {

        @Test
        @DisplayName("出荷伝票詳細を返す")
        void getDetail_returns200() throws Exception {
            OutboundSlip slip = createSlip(1L, "OUT-20260320-0001", "ORDERED");
            OutboundSlipLine line = createLine(1L, slip, 1, "PRD-0001", 100);
            slip.getLines().add(line);

            when(outboundSlipService.findByIdWithLines(1L)).thenReturn(slip);

            mockMvc.perform(get(SLIPS_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.slipNumber").value("OUT-20260320-0001"))
                    .andExpect(jsonPath("$.slipType").value("NORMAL"))
                    .andExpect(jsonPath("$.status").value("ORDERED"))
                    .andExpect(jsonPath("$.warehouseId").value(1))
                    .andExpect(jsonPath("$.warehouseCode").value("WH-001"))
                    .andExpect(jsonPath("$.warehouseName").value("東京DC"))
                    .andExpect(jsonPath("$.partnerId").value(5))
                    .andExpect(jsonPath("$.partnerCode").value("CUS-0001"))
                    .andExpect(jsonPath("$.partnerName").value("顧客A"))
                    .andExpect(jsonPath("$.lines", hasSize(1)))
                    .andExpect(jsonPath("$.lines[0].lineNo").value(1))
                    .andExpect(jsonPath("$.lines[0].productCode").value("PRD-0001"))
                    .andExpect(jsonPath("$.lines[0].orderedQty").value(100))
                    .andExpect(jsonPath("$.lines[0].lineStatus").value("ORDERED"));
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void getDetail_notFound_returns404() throws Exception {
            when(outboundSlipService.findByIdWithLines(999L))
                    .thenThrow(new ResourceNotFoundException("OUTBOUND_SLIP_NOT_FOUND",
                            "出荷伝票が見つかりません (id=999)"));

            mockMvc.perform(get(SLIPS_URL + "/999"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/outbound/slips/{id}")
    class DeleteTests {

        @Test
        @DisplayName("正常に204 No Contentを返す")
        void delete_returns204() throws Exception {
            doNothing().when(outboundSlipService).delete(1L);

            mockMvc.perform(delete(SLIPS_URL + "/1"))
                    .andExpect(status().isNoContent());

            verify(outboundSlipService).delete(1L);
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void delete_notFound_returns404() throws Exception {
            doThrow(new ResourceNotFoundException("OUTBOUND_SLIP_NOT_FOUND",
                    "出荷伝票が見つかりません (id=999)"))
                    .when(outboundSlipService).delete(999L);

            mockMvc.perform(delete(SLIPS_URL + "/999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("ORDERED以外のステータスで409を返す")
        void delete_invalidStatus_returns409() throws Exception {
            doThrow(new InvalidStateTransitionException("OUTBOUND_INVALID_STATUS",
                    "ORDERED以外のステータスの出荷伝票は削除できません"))
                    .when(outboundSlipService).delete(1L);

            mockMvc.perform(delete(SLIPS_URL + "/1"))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/outbound/slips/{id}/cancel")
    class CancelTests {

        @Test
        @DisplayName("正常に200を返す")
        void cancel_returns200() throws Exception {
            OutboundSlip cancelled = createSlip(1L, "OUT-20260320-0001", "CANCELLED");
            setField(cancelled, "cancelledAt", OffsetDateTime.now());
            setField(cancelled, "cancelledBy", 10L);

            when(outboundSlipService.cancel(eq(1L), any(CancelOutboundRequest.class))).thenReturn(cancelled);

            mockMvc.perform(post(SLIPS_URL + "/1/cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\": \"テストキャンセル\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.slipNumber").value("OUT-20260320-0001"))
                    .andExpect(jsonPath("$.status").value("CANCELLED"));

            verify(outboundSlipService).cancel(eq(1L), any(CancelOutboundRequest.class));
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void cancel_notFound_returns404() throws Exception {
            when(outboundSlipService.cancel(eq(999L), any(CancelOutboundRequest.class)))
                    .thenThrow(new ResourceNotFoundException("OUTBOUND_SLIP_NOT_FOUND",
                            "出荷伝票が見つかりません (id=999)"));

            mockMvc.perform(post(SLIPS_URL + "/999/cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\": \"テスト\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("キャンセル不可ステータスで409を返す")
        void cancel_invalidStatus_returns409() throws Exception {
            when(outboundSlipService.cancel(eq(1L), any(CancelOutboundRequest.class)))
                    .thenThrow(new InvalidStateTransitionException("OUTBOUND_INVALID_STATUS",
                            "キャンセル可能なステータスではありません"));

            mockMvc.perform(post(SLIPS_URL + "/1/cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\": \"テスト\"}"))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("Stub endpoints (未実装)")
    class StubTests {

        @Test
        @DisplayName("GET /api/v1/outbound/picking -> 500 (UnsupportedOperation)")
        void listPickingInstructions_throwsUnsupported() throws Exception {
            mockMvc.perform(get("/api/v1/outbound/picking")
                            .param("warehouseId", "1"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("POST /api/v1/outbound/picking -> 500 (UnsupportedOperation)")
        void createPickingInstruction_throwsUnsupported() throws Exception {
            mockMvc.perform(post("/api/v1/outbound/picking")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"slipIds\": [1]}"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("GET /api/v1/outbound/picking/{id} -> 500 (UnsupportedOperation)")
        void getPickingInstruction_throwsUnsupported() throws Exception {
            mockMvc.perform(get("/api/v1/outbound/picking/1"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("PUT /api/v1/outbound/picking/{id}/complete -> 500 (UnsupportedOperation)")
        void completePickingInstruction_throwsUnsupported() throws Exception {
            mockMvc.perform(put("/api/v1/outbound/picking/1/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lines\": [{\"lineId\": 1, \"qtyPicked\": 10}]}"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("POST /api/v1/outbound/slips/{id}/inspect -> 500 (UnsupportedOperation)")
        void inspectOutboundSlip_throwsUnsupported() throws Exception {
            mockMvc.perform(post(SLIPS_URL + "/1/inspect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lines\": [{\"lineId\": 1, \"inspectedQty\": 10}]}"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("POST /api/v1/outbound/slips/{id}/ship -> 500 (UnsupportedOperation)")
        void shipOutboundSlip_throwsUnsupported() throws Exception {
            mockMvc.perform(post(SLIPS_URL + "/1/ship")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"shippedDate\": \"2026-03-25\"}"))
                    .andExpect(status().isInternalServerError());
        }
    }

    private static void setField(Object obj, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
