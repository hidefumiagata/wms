package com.wms.inbound.controller;

import com.wms.inbound.entity.InboundSlip;
import com.wms.inbound.entity.InboundSlipLine;
import com.wms.inbound.service.InboundSlipService;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.security.JwtAuthenticationFilter;
import com.wms.shared.security.JwtTokenProvider;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InboundSlipController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("InboundSlipController")
class InboundSlipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InboundSlipService inboundSlipService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static final String SLIPS_URL = "/api/v1/inbound/slips";

    private InboundSlip createSlip(Long id, String slipNumber, String status) {
        InboundSlip slip = InboundSlip.builder()
                .slipNumber(slipNumber)
                .slipType("NORMAL")
                .warehouseId(1L)
                .warehouseCode("WH-001")
                .warehouseName("東京DC")
                .partnerId(5L)
                .partnerCode("SUP-0001")
                .partnerName("ABC商事")
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

    private InboundSlipLine createLine(Long id, InboundSlip slip, int lineNo,
                                        String productCode, int plannedQty) {
        InboundSlipLine line = InboundSlipLine.builder()
                .inboundSlip(slip)
                .lineNo(lineNo)
                .productId((long) lineNo)
                .productCode(productCode)
                .productName("商品" + lineNo)
                .unitType("CASE")
                .plannedQty(plannedQty)
                .lineStatus("PENDING")
                .build();
        setField(line, "id", id);
        return line;
    }

    @Nested
    @DisplayName("GET /api/v1/inbound/slips")
    class ListTests {

        @Test
        @DisplayName("入荷予定一覧をページング形式で返す")
        void list_returns200() throws Exception {
            InboundSlip slip = createSlip(1L, "INB-20260320-0001", "PLANNED");

            when(inboundSlipService.search(
                    eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(slip)));
            when(inboundSlipService.countLinesBySlipId(1L)).thenReturn(3L);

            mockMvc.perform(get(SLIPS_URL).param("warehouseId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].slipNumber").value("INB-20260320-0001"))
                    .andExpect(jsonPath("$.content[0].slipType").value("NORMAL"))
                    .andExpect(jsonPath("$.content[0].status").value("PLANNED"))
                    .andExpect(jsonPath("$.content[0].lineCount").value(3))
                    .andExpect(jsonPath("$.content[0].warehouseCode").value("WH-001"))
                    .andExpect(jsonPath("$.content[0].partnerCode").value("SUP-0001"))
                    .andExpect(jsonPath("$.content[0].partnerName").value("ABC商事"))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("空の場合でも200を返す")
        void list_empty_returns200() throws Exception {
            when(inboundSlipService.search(
                    eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get(SLIPS_URL).param("warehouseId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("倉庫が存在しない場合404を返す")
        void list_warehouseNotFound_returns404() throws Exception {
            when(inboundSlipService.search(
                    eq(999L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenThrow(new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "倉庫 が見つかりません (id=999)"));

            mockMvc.perform(get(SLIPS_URL).param("warehouseId", "999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("ステータスフィルタ付きで検索できる")
        void list_withStatusFilter_returns200() throws Exception {
            when(inboundSlipService.search(
                    eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get(SLIPS_URL)
                            .param("warehouseId", "1")
                            .param("status", "PLANNED")
                            .param("status", "CONFIRMED"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("descソート指定で検索できる")
        void list_withDescSort_returns200() throws Exception {
            when(inboundSlipService.search(
                    eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get(SLIPS_URL)
                            .param("warehouseId", "1")
                            .param("sort", "createdAt,desc"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("不正なソートプロパティはデフォルトにフォールバック")
        void list_withInvalidSortProperty_returns200() throws Exception {
            when(inboundSlipService.search(
                    eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get(SLIPS_URL)
                            .param("warehouseId", "1")
                            .param("sort", "invalidProp,asc"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("日付範囲フィルタ付きで検索できる")
        void list_withDateRange_returns200() throws Exception {
            when(inboundSlipService.search(
                    eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get(SLIPS_URL)
                            .param("warehouseId", "1")
                            .param("plannedDateFrom", "2026-03-01")
                            .param("plannedDateTo", "2026-03-31"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/inbound/slips/{id}")
    class GetDetailTests {

        @Test
        @DisplayName("入荷予定詳細を返す")
        void getDetail_returns200() throws Exception {
            InboundSlip slip = createSlip(1L, "INB-20260320-0001", "INSPECTING");
            InboundSlipLine line = createLine(1L, slip, 1, "PRD-0001", 100);
            line.setInspectedQty(98);
            line.setLineStatus("INSPECTED");
            slip.getLines().add(line);

            when(inboundSlipService.findByIdWithLines(1L)).thenReturn(slip);
            when(inboundSlipService.resolveUserName(10L)).thenReturn("山田 太郎");

            mockMvc.perform(get(SLIPS_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.slipNumber").value("INB-20260320-0001"))
                    .andExpect(jsonPath("$.slipType").value("NORMAL"))
                    .andExpect(jsonPath("$.status").value("INSPECTING"))
                    .andExpect(jsonPath("$.warehouseId").value(1))
                    .andExpect(jsonPath("$.warehouseCode").value("WH-001"))
                    .andExpect(jsonPath("$.warehouseName").value("東京DC"))
                    .andExpect(jsonPath("$.partnerId").value(5))
                    .andExpect(jsonPath("$.partnerCode").value("SUP-0001"))
                    .andExpect(jsonPath("$.partnerName").value("ABC商事"))
                    .andExpect(jsonPath("$.createdByName").value("山田 太郎"))
                    .andExpect(jsonPath("$.lines", hasSize(1)))
                    .andExpect(jsonPath("$.lines[0].lineNo").value(1))
                    .andExpect(jsonPath("$.lines[0].productCode").value("PRD-0001"))
                    .andExpect(jsonPath("$.lines[0].plannedQty").value(100))
                    .andExpect(jsonPath("$.lines[0].inspectedQty").value(98))
                    .andExpect(jsonPath("$.lines[0].diffQty").value(-2))
                    .andExpect(jsonPath("$.lines[0].lineStatus").value("INSPECTED"));
        }

        @Test
        @DisplayName("検品前の明細はdiffQtyがnull")
        void getDetail_pendingLine_diffQtyNull() throws Exception {
            InboundSlip slip = createSlip(1L, "INB-20260320-0001", "PLANNED");
            InboundSlipLine line = createLine(1L, slip, 1, "PRD-0001", 100);
            slip.getLines().add(line);

            when(inboundSlipService.findByIdWithLines(1L)).thenReturn(slip);
            when(inboundSlipService.resolveUserName(10L)).thenReturn(null);

            mockMvc.perform(get(SLIPS_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lines[0].inspectedQty").doesNotExist())
                    .andExpect(jsonPath("$.lines[0].diffQty").doesNotExist());
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void getDetail_notFound_returns404() throws Exception {
            when(inboundSlipService.findByIdWithLines(999L))
                    .thenThrow(new ResourceNotFoundException("INBOUND_SLIP_NOT_FOUND",
                            "入荷伝票が見つかりません (id=999)"));

            mockMvc.perform(get(SLIPS_URL + "/999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("倉庫振替入荷の詳細を返す（partnerId=null）")
        void getDetail_warehouseTransfer_returns200() throws Exception {
            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260320-0002")
                    .slipType("WAREHOUSE_TRANSFER")
                    .transferSlipNumber("OUT-20260320-0001")
                    .warehouseId(1L)
                    .warehouseCode("WH-001")
                    .warehouseName("東京DC")
                    .partnerId(null)
                    .partnerCode(null)
                    .partnerName(null)
                    .plannedDate(LocalDate.of(2026, 3, 20))
                    .status("PLANNED")
                    .lines(new ArrayList<>())
                    .build();
            setField(slip, "id", 2L);
            setField(slip, "createdAt", OffsetDateTime.now());
            setField(slip, "createdBy", 10L);
            setField(slip, "updatedAt", OffsetDateTime.now());
            setField(slip, "updatedBy", 10L);

            when(inboundSlipService.findByIdWithLines(2L)).thenReturn(slip);
            when(inboundSlipService.resolveUserName(10L)).thenReturn(null);

            mockMvc.perform(get(SLIPS_URL + "/2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slipType").value("WAREHOUSE_TRANSFER"))
                    .andExpect(jsonPath("$.transferSlipNumber").value("OUT-20260320-0001"))
                    .andExpect(jsonPath("$.partnerId").doesNotExist())
                    .andExpect(jsonPath("$.partnerCode").doesNotExist());
        }
    }

    @Nested
    @DisplayName("未実装スタブエンドポイント")
    class StubTests {

        @Test
        @DisplayName("POST /slips (create) は500を返す")
        void create_returns500() throws Exception {
            mockMvc.perform(post(SLIPS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"warehouseId\":1,\"plannedDate\":\"2026-03-20\",\"slipType\":\"NORMAL\",\"partnerId\":1,\"lines\":[{\"productId\":1,\"unitType\":\"CASE\",\"plannedQty\":10}]}"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("DELETE /slips/{id} は500を返す")
        void delete_returns500() throws Exception {
            mockMvc.perform(delete(SLIPS_URL + "/1"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("POST /slips/{id}/confirm は500を返す")
        void confirm_returns500() throws Exception {
            mockMvc.perform(post(SLIPS_URL + "/1/confirm"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("POST /slips/{id}/cancel は500を返す")
        void cancel_returns500() throws Exception {
            mockMvc.perform(post(SLIPS_URL + "/1/cancel"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("POST /slips/{id}/inspect は500を返す")
        void inspect_returns500() throws Exception {
            mockMvc.perform(post(SLIPS_URL + "/1/inspect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lines\":[{\"lineId\":1,\"inspectedQty\":10}]}"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("POST /slips/{id}/store は500を返す")
        void store_returns500() throws Exception {
            mockMvc.perform(post(SLIPS_URL + "/1/store")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lines\":[{\"lineId\":1,\"locationId\":1}]}"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("GET /results は500を返す")
        void results_returns500() throws Exception {
            mockMvc.perform(get("/api/v1/inbound/results")
                            .param("warehouseId", "1"))
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
