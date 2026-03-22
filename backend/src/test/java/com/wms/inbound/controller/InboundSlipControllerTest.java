package com.wms.inbound.controller;

import com.wms.generated.model.CreateInboundSlipRequest;
import com.wms.generated.model.InspectInboundRequest;
import com.wms.generated.model.StoreInboundRequest;
import com.wms.inbound.entity.InboundSlip;
import com.wms.inbound.entity.InboundSlipLine;
import com.wms.inbound.service.InboundSlipService;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.DuplicateResourceException;
import com.wms.shared.exception.InvalidStateTransitionException;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
        @DisplayName("ソートにカンマなしの場合ASCデフォルトで検索できる")
        void list_withSortNoDirction_returns200() throws Exception {
            when(inboundSlipService.search(
                    eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get(SLIPS_URL)
                            .param("warehouseId", "1")
                            .param("sort", "slipNumber"))
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
    @DisplayName("POST /api/v1/inbound/slips (create)")
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
                            "plannedQty": 100
                        }
                    ]
                }
                """;

        @Test
        @DisplayName("正常に201 Createdを返す")
        void create_returns201() throws Exception {
            InboundSlip created = createSlip(1L, "INB-20260322-0001", "PLANNED");
            InboundSlipLine line = createLine(1L, created, 1, "PRD-0001", 100);
            created.getLines().add(line);

            when(inboundSlipService.create(any(CreateInboundSlipRequest.class))).thenReturn(created);
            when(inboundSlipService.findByIdWithLines(1L)).thenReturn(created);
            when(inboundSlipService.resolveUserName(10L)).thenReturn("山田 太郎");

            mockMvc.perform(post(SLIPS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_REQUEST_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.slipNumber").value("INB-20260322-0001"))
                    .andExpect(jsonPath("$.status").value("PLANNED"))
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

        @Test
        @DisplayName("倉庫が存在しない場合404を返す")
        void create_warehouseNotFound_returns404() throws Exception {
            when(inboundSlipService.create(any(CreateInboundSlipRequest.class)))
                    .thenThrow(new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "倉庫が見つかりません"));

            mockMvc.perform(post(SLIPS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_REQUEST_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("仕入先が存在しない場合404を返す")
        void create_partnerNotFound_returns404() throws Exception {
            when(inboundSlipService.create(any(CreateInboundSlipRequest.class)))
                    .thenThrow(new ResourceNotFoundException("PARTNER_NOT_FOUND", "取引先が見つかりません"));

            mockMvc.perform(post(SLIPS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_REQUEST_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("仕入先タイプ不正で422を返す")
        void create_partnerNotSupplier_returns422() throws Exception {
            when(inboundSlipService.create(any(CreateInboundSlipRequest.class)))
                    .thenThrow(new BusinessRuleViolationException("INBOUND_PARTNER_NOT_SUPPLIER",
                            "仕入先がSUPPLIERではありません"));

            mockMvc.perform(post(SLIPS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_REQUEST_JSON))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("入荷予定日が過去日で422を返す")
        void create_plannedDateTooEarly_returns422() throws Exception {
            when(inboundSlipService.create(any(CreateInboundSlipRequest.class)))
                    .thenThrow(new BusinessRuleViolationException("PLANNED_DATE_TOO_EARLY",
                            "入荷予定日は営業日以降を指定してください"));

            mockMvc.perform(post(SLIPS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_REQUEST_JSON))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("同一商品重複で409を返す")
        void create_duplicateProduct_returns409() throws Exception {
            when(inboundSlipService.create(any(CreateInboundSlipRequest.class)))
                    .thenThrow(new DuplicateResourceException("DUPLICATE_PRODUCT_IN_LINES",
                            "同一伝票内に同じ商品が複数指定されています"));

            mockMvc.perform(post(SLIPS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_REQUEST_JSON))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("ロット番号未指定で422を返す")
        void create_lotNumberRequired_returns422() throws Exception {
            when(inboundSlipService.create(any(CreateInboundSlipRequest.class)))
                    .thenThrow(new BusinessRuleViolationException("LOT_NUMBER_REQUIRED",
                            "ロット管理対象商品にはロット番号が必須です"));

            mockMvc.perform(post(SLIPS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_REQUEST_JSON))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("期限日未指定で422を返す")
        void create_expiryDateRequired_returns422() throws Exception {
            when(inboundSlipService.create(any(CreateInboundSlipRequest.class)))
                    .thenThrow(new BusinessRuleViolationException("EXPIRY_DATE_REQUIRED",
                            "期限管理対象商品には期限日が必須です"));

            mockMvc.perform(post(SLIPS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_REQUEST_JSON))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("商品が存在しない場合404を返す")
        void create_productNotFound_returns404() throws Exception {
            when(inboundSlipService.create(any(CreateInboundSlipRequest.class)))
                    .thenThrow(new ResourceNotFoundException("PRODUCT_NOT_FOUND", "商品が見つかりません"));

            mockMvc.perform(post(SLIPS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_REQUEST_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/inbound/slips/{id}")
    class DeleteTests {

        @Test
        @DisplayName("正常に204 No Contentを返す")
        void delete_returns204() throws Exception {
            doNothing().when(inboundSlipService).delete(1L);

            mockMvc.perform(delete(SLIPS_URL + "/1"))
                    .andExpect(status().isNoContent());

            verify(inboundSlipService).delete(1L);
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void delete_notFound_returns404() throws Exception {
            doThrow(new ResourceNotFoundException("INBOUND_SLIP_NOT_FOUND",
                    "入荷伝票が見つかりません (id=999)"))
                    .when(inboundSlipService).delete(999L);

            mockMvc.perform(delete(SLIPS_URL + "/999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("PLANNED以外のステータスで409を返す")
        void delete_invalidStatus_returns409() throws Exception {
            doThrow(new InvalidStateTransitionException("INBOUND_INVALID_STATUS",
                    "PLANNED以外のステータスの入荷伝票は削除できません"))
                    .when(inboundSlipService).delete(1L);

            mockMvc.perform(delete(SLIPS_URL + "/1"))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/inbound/slips/{id}/confirm")
    class ConfirmTests {

        @Test
        @DisplayName("正常に200を返す")
        void confirm_returns200() throws Exception {
            InboundSlip confirmed = createSlip(1L, "INB-20260320-0001", "CONFIRMED");

            when(inboundSlipService.confirm(1L)).thenReturn(confirmed);
            when(inboundSlipService.resolveUserName(10L)).thenReturn("山田 太郎");

            mockMvc.perform(post(SLIPS_URL + "/1/confirm"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"));

            verify(inboundSlipService).confirm(1L);
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void confirm_notFound_returns404() throws Exception {
            when(inboundSlipService.confirm(999L))
                    .thenThrow(new ResourceNotFoundException("INBOUND_SLIP_NOT_FOUND",
                            "入荷伝票が見つかりません (id=999)"));

            mockMvc.perform(post(SLIPS_URL + "/999/confirm"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("PLANNED以外のステータスで409を返す")
        void confirm_invalidStatus_returns409() throws Exception {
            when(inboundSlipService.confirm(1L))
                    .thenThrow(new InvalidStateTransitionException("INBOUND_INVALID_STATUS",
                            "PLANNED以外のステータスの入荷伝票は確定できません"));

            mockMvc.perform(post(SLIPS_URL + "/1/confirm"))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/inbound/slips/{id}/cancel")
    class CancelTests {

        @Test
        @DisplayName("正常に200を返す")
        void cancel_returns200() throws Exception {
            InboundSlip cancelled = createSlip(1L, "INB-20260320-0001", "CANCELLED");

            when(inboundSlipService.cancel(1L)).thenReturn(cancelled);
            when(inboundSlipService.resolveUserName(10L)).thenReturn("山田 太郎");

            mockMvc.perform(post(SLIPS_URL + "/1/cancel"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.status").value("CANCELLED"));

            verify(inboundSlipService).cancel(1L);
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void cancel_notFound_returns404() throws Exception {
            when(inboundSlipService.cancel(999L))
                    .thenThrow(new ResourceNotFoundException("INBOUND_SLIP_NOT_FOUND",
                            "入荷伝票が見つかりません (id=999)"));

            mockMvc.perform(post(SLIPS_URL + "/999/cancel"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("STOREDステータスで409を返す")
        void cancel_invalidStatus_returns409() throws Exception {
            when(inboundSlipService.cancel(1L))
                    .thenThrow(new InvalidStateTransitionException("INBOUND_INVALID_STATUS",
                            "STOREDステータスの入荷伝票はキャンセルできません"));

            mockMvc.perform(post(SLIPS_URL + "/1/cancel"))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/inbound/slips/{id}/inspect")
    class InspectTests {

        @Test
        @DisplayName("正常に200を返す")
        void inspect_returns200() throws Exception {
            InboundSlip inspected = createSlip(1L, "INB-20260320-0001", "INSPECTING");
            InboundSlipLine line = createLine(1L, inspected, 1, "PRD-0001", 100);
            line.setInspectedQty(98);
            line.setLineStatus("INSPECTED");
            inspected.getLines().add(line);

            when(inboundSlipService.inspect(eq(1L), any(InspectInboundRequest.class))).thenReturn(inspected);
            when(inboundSlipService.resolveUserName(10L)).thenReturn("山田 太郎");

            mockMvc.perform(post(SLIPS_URL + "/1/inspect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lines\":[{\"lineId\":1,\"inspectedQty\":98}]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.status").value("INSPECTING"))
                    .andExpect(jsonPath("$.lines[0].inspectedQty").value(98))
                    .andExpect(jsonPath("$.lines[0].lineStatus").value("INSPECTED"));

            verify(inboundSlipService).inspect(eq(1L), any(InspectInboundRequest.class));
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void inspect_notFound_returns404() throws Exception {
            when(inboundSlipService.inspect(eq(999L), any(InspectInboundRequest.class)))
                    .thenThrow(new ResourceNotFoundException("INBOUND_SLIP_NOT_FOUND",
                            "入荷伝票が見つかりません (id=999)"));

            mockMvc.perform(post(SLIPS_URL + "/999/inspect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lines\":[{\"lineId\":1,\"inspectedQty\":10}]}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("不正なステータスで409を返す")
        void inspect_invalidStatus_returns409() throws Exception {
            when(inboundSlipService.inspect(eq(1L), any(InspectInboundRequest.class)))
                    .thenThrow(new InvalidStateTransitionException("INBOUND_INVALID_STATUS",
                            "検品可能なステータスではありません"));

            mockMvc.perform(post(SLIPS_URL + "/1/inspect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lines\":[{\"lineId\":1,\"inspectedQty\":10}]}"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("空のlinesで400を返す")
        void inspect_emptyLines_returns400() throws Exception {
            mockMvc.perform(post(SLIPS_URL + "/1/inspect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lines\":[]}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/inbound/slips/{id}/store")
    class StoreTests {

        @Test
        @DisplayName("正常に200を返す")
        void store_returns200() throws Exception {
            InboundSlip stored = createSlip(1L, "INB-20260320-0001", "STORED");
            InboundSlipLine line = createLine(1L, stored, 1, "PRD-0001", 100);
            line.setInspectedQty(98);
            line.setLineStatus("STORED");
            stored.getLines().add(line);

            when(inboundSlipService.store(eq(1L), any(StoreInboundRequest.class))).thenReturn(stored);
            when(inboundSlipService.resolveUserName(10L)).thenReturn("山田 太郎");

            mockMvc.perform(post(SLIPS_URL + "/1/store")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lines\":[{\"lineId\":1,\"locationId\":200}]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.status").value("STORED"))
                    .andExpect(jsonPath("$.lines[0].lineStatus").value("STORED"));

            verify(inboundSlipService).store(eq(1L), any(StoreInboundRequest.class));
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void store_notFound_returns404() throws Exception {
            when(inboundSlipService.store(eq(999L), any(StoreInboundRequest.class)))
                    .thenThrow(new ResourceNotFoundException("INBOUND_SLIP_NOT_FOUND",
                            "入荷伝票が見つかりません (id=999)"));

            mockMvc.perform(post(SLIPS_URL + "/999/store")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lines\":[{\"lineId\":1,\"locationId\":200}]}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("不正なステータスで409を返す")
        void store_invalidStatus_returns409() throws Exception {
            when(inboundSlipService.store(eq(1L), any(StoreInboundRequest.class)))
                    .thenThrow(new InvalidStateTransitionException("INBOUND_INVALID_STATUS",
                            "格納可能なステータスではありません"));

            mockMvc.perform(post(SLIPS_URL + "/1/store")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lines\":[{\"lineId\":1,\"locationId\":200}]}"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("ビジネスルール違反で422を返す")
        void store_businessRuleViolation_returns422() throws Exception {
            when(inboundSlipService.store(eq(1L), any(StoreInboundRequest.class)))
                    .thenThrow(new BusinessRuleViolationException("LOCATION_INACTIVE",
                            "無効なロケーションです"));

            mockMvc.perform(post(SLIPS_URL + "/1/store")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lines\":[{\"lineId\":1,\"locationId\":200}]}"))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("空のlinesで400を返す")
        void store_emptyLines_returns400() throws Exception {
            mockMvc.perform(post(SLIPS_URL + "/1/store")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lines\":[]}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/inbound/results")
    class ResultsTests {

        private static final String RESULTS_URL = "/api/v1/inbound/results";

        private InboundSlipLine createResultLine(Long lineId, InboundSlip slip) {
            InboundSlipLine line = InboundSlipLine.builder()
                    .inboundSlip(slip)
                    .lineNo(1)
                    .productId(100L)
                    .productCode("PRD-0001")
                    .productName("商品A")
                    .unitType("CASE")
                    .plannedQty(50)
                    .inspectedQty(48)
                    .lotNumber("LOT-001")
                    .expiryDate(LocalDate.of(2027, 1, 1))
                    .putawayLocationId(200L)
                    .putawayLocationCode("LOC-A01")
                    .lineStatus("STORED")
                    .build();
            setField(line, "id", lineId);
            setField(line, "storedAt", OffsetDateTime.now());
            setField(line, "storedBy", 10L);
            return line;
        }

        @Test
        @DisplayName("入荷実績一覧をページング形式で返す")
        void results_returns200() throws Exception {
            InboundSlip slip = createSlip(1L, "INB-20260320-0001", "STORED");
            InboundSlipLine line = createResultLine(11L, slip);

            when(inboundSlipService.findResults(
                    eq(1L), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(line)));
            when(inboundSlipService.resolveUserName(10L)).thenReturn("テスト太郎");

            mockMvc.perform(get(RESULTS_URL).param("warehouseId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].slipId").value(1))
                    .andExpect(jsonPath("$.content[0].slipNumber").value("INB-20260320-0001"))
                    .andExpect(jsonPath("$.content[0].slipType").value("NORMAL"))
                    .andExpect(jsonPath("$.content[0].partnerCode").value("SUP-0001"))
                    .andExpect(jsonPath("$.content[0].partnerName").value("ABC商事"))
                    .andExpect(jsonPath("$.content[0].lineId").value(11))
                    .andExpect(jsonPath("$.content[0].lineNo").value(1))
                    .andExpect(jsonPath("$.content[0].productCode").value("PRD-0001"))
                    .andExpect(jsonPath("$.content[0].productName").value("商品A"))
                    .andExpect(jsonPath("$.content[0].unitType").value("CASE"))
                    .andExpect(jsonPath("$.content[0].lotNumber").value("LOT-001"))
                    .andExpect(jsonPath("$.content[0].plannedQty").value(50))
                    .andExpect(jsonPath("$.content[0].inspectedQty").value(48))
                    .andExpect(jsonPath("$.content[0].diffQty").value(-2))
                    .andExpect(jsonPath("$.content[0].locationCode").value("LOC-A01"))
                    .andExpect(jsonPath("$.content[0].storedByName").value("テスト太郎"))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("空の場合でも200を返す")
        void results_empty_returns200() throws Exception {
            when(inboundSlipService.findResults(
                    eq(1L), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get(RESULTS_URL).param("warehouseId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("inspectedQtyがnullの場合diffQtyもnull")
        void results_nullInspectedQty_returns200() throws Exception {
            InboundSlip slip = createSlip(1L, "INB-20260320-0001", "STORED");
            InboundSlipLine line = createResultLine(11L, slip);
            line.setInspectedQty(null);

            when(inboundSlipService.findResults(
                    eq(1L), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(line)));
            when(inboundSlipService.resolveUserName(any())).thenReturn(null);

            mockMvc.perform(get(RESULTS_URL).param("warehouseId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].inspectedQty").doesNotExist())
                    .andExpect(jsonPath("$.content[0].diffQty").doesNotExist());
        }

        @Test
        @DisplayName("倉庫が存在しない場合404を返す")
        void results_warehouseNotFound_returns404() throws Exception {
            when(inboundSlipService.findResults(
                    eq(999L), any(), any(), any(), any(), any(), any()))
                    .thenThrow(new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "倉庫 が見つかりません (id=999)"));

            mockMvc.perform(get(RESULTS_URL).param("warehouseId", "999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("フィルタパラメータ付きで検索できる")
        void results_withFilters_returns200() throws Exception {
            when(inboundSlipService.findResults(
                    eq(1L), any(), any(), eq(5L), eq("INB-2026"), eq("PRD"), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get(RESULTS_URL)
                            .param("warehouseId", "1")
                            .param("partnerId", "5")
                            .param("slipNumber", "INB-2026")
                            .param("productCode", "PRD")
                            .param("storedDateFrom", "2026-03-01")
                            .param("storedDateTo", "2026-03-22"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("descソート指定で検索できる")
        void results_withDescSort_returns200() throws Exception {
            when(inboundSlipService.findResults(
                    eq(1L), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get(RESULTS_URL)
                            .param("warehouseId", "1")
                            .param("sort", "storedAt,desc"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("不正なソートプロパティはデフォルトにフォールバック")
        void results_withInvalidSort_returns200() throws Exception {
            when(inboundSlipService.findResults(
                    eq(1L), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get(RESULTS_URL)
                            .param("warehouseId", "1")
                            .param("sort", "invalidField,asc"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ソートにカンマなしの場合ASCデフォルトで検索できる")
        void results_withSortNoDirection_returns200() throws Exception {
            when(inboundSlipService.findResults(
                    eq(1L), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get(RESULTS_URL)
                            .param("warehouseId", "1")
                            .param("sort", "productCode"))
                    .andExpect(status().isOk());
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
