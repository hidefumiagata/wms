package com.wms.outbound.controller;

import com.wms.allocation.service.AllocationService;
import com.wms.generated.model.CreatePickingInstructionRequest;
import com.wms.master.entity.Area;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.AreaService;
import com.wms.master.service.WarehouseService;
import com.wms.outbound.entity.OutboundSlip;
import com.wms.outbound.entity.PickingInstruction;
import com.wms.outbound.entity.PickingInstructionLine;
import com.wms.outbound.service.OutboundSlipService;
import com.wms.outbound.service.PickingService;
import com.wms.shared.exception.InvalidStateTransitionException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.security.JwtAuthenticationFilter;
import com.wms.shared.security.JwtTokenProvider;
import com.wms.system.entity.User;
import com.wms.system.repository.UserRepository;

import java.util.Set;
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
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OutboundSlipController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PickingController (via OutboundSlipController)")
class PickingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OutboundSlipService outboundSlipService;

    @MockitoBean
    private PickingService pickingService;

    @MockitoBean
    private AllocationService allocationService;

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

    private static final String PICKING_URL = "/api/v1/outbound/picking";

    private PickingInstruction createInstruction(Long id, String number, String status) {
        PickingInstruction pi = PickingInstruction.builder()
                .instructionNumber(number)
                .warehouseId(1L)
                .status(status)
                .lines(new ArrayList<>())
                .build();
        setField(pi, "id", id);
        setField(pi, "createdAt", OffsetDateTime.now());
        setField(pi, "createdBy", 10L);
        return pi;
    }

    private PickingInstructionLine createLine(Long id, PickingInstruction pi, int lineNo) {
        PickingInstructionLine line = PickingInstructionLine.builder()
                .pickingInstruction(pi)
                .lineNo(lineNo)
                .outboundSlipLineId(100L + lineNo)
                .locationId(10L)
                .locationCode("A-01-01")
                .productId(200L)
                .productCode("PRD-0001")
                .productName("商品1")
                .unitType("CASE")
                .qtyToPick(5)
                .qtyPicked(0)
                .lineStatus("PENDING")
                .build();
        setField(line, "id", id);
        return line;
    }

    private Warehouse createWarehouse() {
        Warehouse wh = new Warehouse();
        setField(wh, "id", 1L);
        wh.setWarehouseCode("WH-001");
        wh.setWarehouseName("東京DC");
        return wh;
    }

    private User createUser() {
        return User.builder()
                .id(10L)
                .userCode("U-001")
                .fullName("担当 太郎")
                .email("test@example.com")
                .passwordHash("hash")
                .role("WAREHOUSE_MANAGER")
                .build();
    }

    // ==================== listPickingInstructions ====================

    @Nested
    @DisplayName("GET /api/v1/outbound/picking")
    class ListTests {

        @Test
        @DisplayName("ピッキング指示一覧をページング形式で返す")
        void list_returns200() throws Exception {
            PickingInstruction pi = createInstruction(1L, "PIC-20260320-001", "CREATED");

            when(pickingService.search(
                    eq(1L), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(pi)));
            when(pickingService.countLinesByInstructionId(1L)).thenReturn(3L);

            Warehouse wh = createWarehouse();
            when(warehouseService.findByIds(any())).thenReturn(Map.of(1L, wh));

            User user = createUser();
            when(userRepository.findAllById(any())).thenReturn(List.of(user));

            mockMvc.perform(get(PICKING_URL).param("warehouseId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].instructionNumber").value("PIC-20260320-001"))
                    .andExpect(jsonPath("$.content[0].status").value("CREATED"))
                    .andExpect(jsonPath("$.content[0].lineCount").value(3))
                    .andExpect(jsonPath("$.content[0].warehouseName").value("東京DC"))
                    .andExpect(jsonPath("$.content[0].createdByName").value("担当 太郎"))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("ステータスフィルタ付きで一覧を返す")
        void list_withStatusFilter_returns200() throws Exception {
            PickingInstruction pi = createInstruction(1L, "PIC-20260320-001", "CREATED");

            when(pickingService.search(
                    eq(1L), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(pi)));
            when(pickingService.countLinesByInstructionId(1L)).thenReturn(1L);

            Warehouse wh = createWarehouse();
            when(warehouseService.findByIds(any())).thenReturn(Map.of(1L, wh));

            User user = createUser();
            when(userRepository.findAllById(any())).thenReturn(List.of(user));

            mockMvc.perform(get(PICKING_URL)
                            .param("warehouseId", "1")
                            .param("status", "CREATED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("倉庫やユーザーがマップに存在しない場合nullフィールドで返す")
        void list_nullWarehouseAndUser_returns200() throws Exception {
            PickingInstruction pi = createInstruction(1L, "PIC-20260320-001", "CREATED");
            // warehouseId=1L だがマップには含めない

            when(pickingService.search(
                    eq(1L), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(pi)));
            when(pickingService.countLinesByInstructionId(1L)).thenReturn(1L);

            // warehouseマップとuserマップを空で返す
            when(warehouseService.findByIds(any())).thenReturn(Map.of());
            when(userRepository.findAllById(any())).thenReturn(List.of());

            mockMvc.perform(get(PICKING_URL).param("warehouseId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].warehouseName").doesNotExist())
                    .andExpect(jsonPath("$.content[0].createdByName").doesNotExist());
        }

        @Test
        @DisplayName("areaId付きの指示で一覧を返す")
        void list_withArea_returns200() throws Exception {
            PickingInstruction pi = createInstruction(1L, "PIC-20260320-001", "CREATED");
            setField(pi, "areaId", 5L);

            when(pickingService.search(
                    eq(1L), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(pi)));
            when(pickingService.countLinesByInstructionId(1L)).thenReturn(1L);

            Warehouse wh = createWarehouse();
            when(warehouseService.findByIds(any())).thenReturn(Map.of(1L, wh));

            Area area = new Area();
            setField(area, "id", 5L);
            area.setAreaName("A棟");
            when(areaService.findByIds(Set.of(5L))).thenReturn(Map.of(5L, area));

            User user = createUser();
            when(userRepository.findAllById(any())).thenReturn(List.of(user));

            mockMvc.perform(get(PICKING_URL).param("warehouseId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].areaName").value("A棟"));
        }

        @Test
        @DisplayName("倉庫が存在しない場合404を返す")
        void list_warehouseNotFound_returns404() throws Exception {
            when(pickingService.search(
                    eq(999L), any(), any(), any(), any(), any(Pageable.class)))
                    .thenThrow(new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "倉庫 が見つかりません (id=999)"));

            mockMvc.perform(get(PICKING_URL).param("warehouseId", "999"))
                    .andExpect(status().isNotFound());
        }
    }

    // ==================== createPickingInstruction ====================

    @Nested
    @DisplayName("POST /api/v1/outbound/picking")
    class CreateTests {

        private static final String CREATE_REQUEST_JSON = """
                {
                    "slipIds": [1, 2]
                }
                """;

        @Test
        @DisplayName("正常に201 Createdを返す")
        void create_returns201() throws Exception {
            PickingInstruction created = createInstruction(50L, "PIC-20260320-001", "CREATED");
            PickingInstructionLine line = createLine(101L, created, 1);
            created.getLines().add(line);

            when(pickingService.createPickingInstruction(any(CreatePickingInstructionRequest.class)))
                    .thenReturn(created);
            when(pickingService.findByIdWithLines(50L)).thenReturn(created);

            Warehouse wh = createWarehouse();
            when(warehouseService.findById(1L)).thenReturn(wh);

            User user = createUser();
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));

            OutboundSlip slip = OutboundSlip.builder()
                    .slipNumber("OUT-20260320-0001")
                    .slipType("NORMAL").warehouseId(1L).warehouseCode("WH-001").warehouseName("東京DC")
                    .plannedDate(LocalDate.of(2026, 3, 20)).status("ALLOCATED").build();
            setField(slip, "id", 1L);
            setField(slip, "createdAt", OffsetDateTime.now());
            setField(slip, "createdBy", 10L);
            setField(slip, "updatedAt", OffsetDateTime.now());
            setField(slip, "updatedBy", 10L);
            when(outboundSlipService.findBySlipLineId(101L)).thenReturn(slip);

            mockMvc.perform(post(PICKING_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_REQUEST_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.id").value(50))
                    .andExpect(jsonPath("$.instructionNumber").value("PIC-20260320-001"))
                    .andExpect(jsonPath("$.status").value("CREATED"))
                    .andExpect(jsonPath("$.lines", hasSize(1)));
        }

        @Test
        @DisplayName("ALLOCATED以外の伝票が含まれている場合409")
        void create_invalidStatus_returns409() throws Exception {
            when(pickingService.createPickingInstruction(any(CreatePickingInstructionRequest.class)))
                    .thenThrow(new InvalidStateTransitionException("OUTBOUND_INVALID_STATUS",
                            "ALLOCATED以外のステータスの伝票が含まれています"));

            mockMvc.perform(post(PICKING_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_REQUEST_JSON))
                    .andExpect(status().isConflict());
        }
    }

    // ==================== getPickingInstruction ====================

    @Nested
    @DisplayName("GET /api/v1/outbound/picking/{id}")
    class GetDetailTests {

        @Test
        @DisplayName("ピッキング指示詳細を返す")
        void get_returns200() throws Exception {
            PickingInstruction pi = createInstruction(50L, "PIC-20260320-001", "CREATED");
            PickingInstructionLine line = createLine(101L, pi, 1);
            pi.getLines().add(line);

            when(pickingService.findByIdWithLines(50L)).thenReturn(pi);

            Warehouse wh = createWarehouse();
            when(warehouseService.findById(1L)).thenReturn(wh);

            User user = createUser();
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));

            OutboundSlip slip = OutboundSlip.builder()
                    .slipNumber("OUT-20260320-0001")
                    .slipType("NORMAL").warehouseId(1L).warehouseCode("WH-001").warehouseName("東京DC")
                    .plannedDate(LocalDate.of(2026, 3, 20)).status("ALLOCATED").build();
            setField(slip, "id", 1L);
            setField(slip, "createdAt", OffsetDateTime.now());
            setField(slip, "createdBy", 10L);
            setField(slip, "updatedAt", OffsetDateTime.now());
            setField(slip, "updatedBy", 10L);
            when(outboundSlipService.findBySlipLineId(101L)).thenReturn(slip);

            mockMvc.perform(get(PICKING_URL + "/50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(50))
                    .andExpect(jsonPath("$.instructionNumber").value("PIC-20260320-001"))
                    .andExpect(jsonPath("$.warehouseName").value("東京DC"))
                    .andExpect(jsonPath("$.createdByName").value("担当 太郎"))
                    .andExpect(jsonPath("$.lines", hasSize(1)))
                    .andExpect(jsonPath("$.lines[0].locationCode").value("A-01-01"));
        }

        @Test
        @DisplayName("areaId付きの場合エリア名を含めて返す")
        void get_withArea_returns200() throws Exception {
            PickingInstruction pi = createInstruction(50L, "PIC-20260320-001", "CREATED");
            setField(pi, "areaId", 5L);
            PickingInstructionLine line = createLine(101L, pi, 1);
            pi.getLines().add(line);

            when(pickingService.findByIdWithLines(50L)).thenReturn(pi);

            Warehouse wh = createWarehouse();
            when(warehouseService.findById(1L)).thenReturn(wh);

            Area area = new Area();
            setField(area, "id", 5L);
            area.setAreaName("A棟");
            when(areaService.findById(5L)).thenReturn(area);

            User user = createUser();
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));

            OutboundSlip slip = OutboundSlip.builder()
                    .slipNumber("OUT-20260320-0001")
                    .slipType("NORMAL").warehouseId(1L).warehouseCode("WH-001").warehouseName("東京DC")
                    .plannedDate(LocalDate.of(2026, 3, 20)).status("ALLOCATED").build();
            setField(slip, "id", 1L);
            setField(slip, "createdAt", OffsetDateTime.now());
            setField(slip, "createdBy", 10L);
            setField(slip, "updatedAt", OffsetDateTime.now());
            setField(slip, "updatedBy", 10L);
            when(outboundSlipService.findBySlipLineId(101L)).thenReturn(slip);

            mockMvc.perform(get(PICKING_URL + "/50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.areaName").value("A棟"));
        }

        @Test
        @DisplayName("areaIdがnull、userが見つからない場合もnullフィールドで正常に返す")
        void get_nullAreaAndUser_returns200() throws Exception {
            PickingInstruction pi = createInstruction(50L, "PIC-20260320-001", "CREATED");
            // areaId = null (デフォルト)
            PickingInstructionLine line = createLine(101L, pi, 1);
            pi.getLines().add(line);

            when(pickingService.findByIdWithLines(50L)).thenReturn(pi);

            Warehouse wh = createWarehouse();
            when(warehouseService.findById(1L)).thenReturn(wh);

            // user not found
            when(userRepository.findById(10L)).thenReturn(Optional.empty());

            OutboundSlip slip = OutboundSlip.builder()
                    .slipNumber("OUT-20260320-0001")
                    .slipType("NORMAL").warehouseId(1L).warehouseCode("WH-001").warehouseName("東京DC")
                    .plannedDate(LocalDate.of(2026, 3, 20)).status("ALLOCATED").build();
            setField(slip, "id", 1L);
            setField(slip, "createdAt", OffsetDateTime.now());
            setField(slip, "createdBy", 10L);
            setField(slip, "updatedAt", OffsetDateTime.now());
            setField(slip, "updatedBy", 10L);
            when(outboundSlipService.findBySlipLineId(101L)).thenReturn(slip);

            mockMvc.perform(get(PICKING_URL + "/50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.areaName").doesNotExist())
                    .andExpect(jsonPath("$.createdByName").doesNotExist());
        }

        @Test
        @DisplayName("buildSlipNumberMap内で例外が発生しても空文字で返す")
        void get_slipNumberLookupFails_returns200() throws Exception {
            PickingInstruction pi = createInstruction(50L, "PIC-20260320-001", "CREATED");
            PickingInstructionLine line = createLine(101L, pi, 1);
            pi.getLines().add(line);

            when(pickingService.findByIdWithLines(50L)).thenReturn(pi);

            Warehouse wh = createWarehouse();
            when(warehouseService.findById(1L)).thenReturn(wh);

            User user = createUser();
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));

            // findBySlipLineId throws exception
            when(outboundSlipService.findBySlipLineId(101L))
                    .thenThrow(new ResourceNotFoundException("NOT_FOUND", "not found"));

            mockMvc.perform(get(PICKING_URL + "/50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lines[0].outboundSlipNumber").value(""));
        }

        @Test
        @DisplayName("存在しない場合404を返す")
        void get_notFound_returns404() throws Exception {
            when(pickingService.findByIdWithLines(999L))
                    .thenThrow(new ResourceNotFoundException("PICKING_NOT_FOUND", "ピッキング指示が見つかりません"));

            mockMvc.perform(get(PICKING_URL + "/999"))
                    .andExpect(status().isNotFound());
        }
    }

    // ==================== completePickingInstruction ====================

    @Nested
    @DisplayName("PUT /api/v1/outbound/picking/{id}/complete")
    class CompleteTests {

        private static final String COMPLETE_REQUEST_JSON = """
                {
                    "lines": [
                        { "lineId": 101, "qtyPicked": 5 }
                    ]
                }
                """;

        @Test
        @DisplayName("正常に200を返す")
        void complete_returns200() throws Exception {
            PickingInstruction pi = createInstruction(50L, "PIC-20260320-001", "COMPLETED");
            PickingInstructionLine line = createLine(101L, pi, 1);
            line.setQtyPicked(5);
            line.setLineStatus("COMPLETED");
            pi.getLines().add(line);
            pi.setCompletedAt(OffsetDateTime.now());
            pi.setCompletedBy(10L);

            when(pickingService.completePickingInstruction(eq(50L), any())).thenReturn(pi);
            when(pickingService.findByIdWithLines(50L)).thenReturn(pi);

            Warehouse wh = createWarehouse();
            when(warehouseService.findById(1L)).thenReturn(wh);

            User user = createUser();
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));

            OutboundSlip slip = OutboundSlip.builder()
                    .slipNumber("OUT-20260320-0001")
                    .slipType("NORMAL").warehouseId(1L).warehouseCode("WH-001").warehouseName("東京DC")
                    .plannedDate(LocalDate.of(2026, 3, 20)).status("PICKING_COMPLETED").build();
            setField(slip, "id", 1L);
            setField(slip, "createdAt", OffsetDateTime.now());
            setField(slip, "createdBy", 10L);
            setField(slip, "updatedAt", OffsetDateTime.now());
            setField(slip, "updatedBy", 10L);
            when(outboundSlipService.findBySlipLineId(101L)).thenReturn(slip);

            mockMvc.perform(put(PICKING_URL + "/50/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(COMPLETE_REQUEST_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(50))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.lines[0].qtyPicked").value(5));
        }

        @Test
        @DisplayName("存在しないピッキング指示の場合404")
        void complete_notFound_returns404() throws Exception {
            when(pickingService.completePickingInstruction(eq(999L), any()))
                    .thenThrow(new ResourceNotFoundException("PICKING_NOT_FOUND", "ピッキング指示が見つかりません"));

            mockMvc.perform(put(PICKING_URL + "/999/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(COMPLETE_REQUEST_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("既にCOMPLETEDの場合409")
        void complete_alreadyCompleted_returns409() throws Exception {
            when(pickingService.completePickingInstruction(eq(50L), any()))
                    .thenThrow(new InvalidStateTransitionException("OUTBOUND_INVALID_STATUS",
                            "既に完了済みのピッキング指示です"));

            mockMvc.perform(put(PICKING_URL + "/50/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(COMPLETE_REQUEST_JSON))
                    .andExpect(status().isConflict());
        }
    }

    private static void setField(Object obj, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = findField(obj.getClass(), fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new RuntimeException("Field not found: " + fieldName);
    }
}
