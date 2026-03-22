package com.wms.allocation.controller;

import com.wms.allocation.entity.UnpackInstruction;
import com.wms.allocation.service.AllocationService;
import com.wms.allocation.service.AllocationService.*;
import com.wms.outbound.entity.OutboundSlip;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AllocationController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AllocationController")
class AllocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AllocationService allocationService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    static void setField(Object obj, String fieldName, Object value) {
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

    // --- GET /api/v1/allocation/orders ---

    @Nested
    @DisplayName("GET /api/v1/allocation/orders")
    class GetAllocationOrdersTests {

        @Test
        @DisplayName("引当対象受注一覧を返す")
        void getAllocationOrders_returns200() throws Exception {
            OutboundSlip slip = createSlip(1L, "OUT-20260320-0001", "ORDERED");

            when(allocationService.searchOrders(
                    eq(1L), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(slip)));
            when(allocationService.countLinesBySlipId(1L)).thenReturn(3L);

            mockMvc.perform(get("/api/v1/allocation/orders")
                            .param("warehouseId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].slipNumber").value("OUT-20260320-0001"))
                    .andExpect(jsonPath("$.content[0].status").value("ORDERED"))
                    .andExpect(jsonPath("$.content[0].lineCount").value(3))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    // --- GET /api/v1/allocation/allocated-orders ---

    @Nested
    @DisplayName("GET /api/v1/allocation/allocated-orders")
    class GetAllocatedOrdersTests {

        @Test
        @DisplayName("引当済み受注一覧を返す")
        void getAllocatedOrders_returns200() throws Exception {
            OutboundSlip slip = createSlip(1L, "OUT-20260320-0001", "ALLOCATED");

            when(allocationService.searchAllocatedOrders(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(slip)));
            when(allocationService.countLinesBySlipId(1L)).thenReturn(2L);
            when(allocationService.countAllocatedLinesBySlipId(1L)).thenReturn(2L);

            mockMvc.perform(get("/api/v1/allocation/allocated-orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].status").value("ALLOCATED"))
                    .andExpect(jsonPath("$.content[0].lineCount").value(2))
                    .andExpect(jsonPath("$.content[0].allocatedLineCount").value(2));
        }
    }

    // --- GET /api/v1/allocation/unpack-instructions ---

    @Nested
    @DisplayName("GET /api/v1/allocation/unpack-instructions")
    class GetUnpackInstructionsTests {

        @Test
        @DisplayName("ばらし指示一覧を返す")
        void getUnpackInstructions_returns200() throws Exception {
            UnpackInstruction unpack = UnpackInstruction.builder()
                    .outboundSlipId(1L)
                    .locationId(50L)
                    .productId(10L)
                    .fromUnitType("CASE")
                    .fromQty(2)
                    .toUnitType("PIECE")
                    .toQty(48)
                    .status("INSTRUCTED")
                    .warehouseId(1L)
                    .build();
            setField(unpack, "id", 500L);
            setField(unpack, "createdAt", OffsetDateTime.now());

            when(allocationService.searchUnpackInstructions(any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(unpack)));

            mockMvc.perform(get("/api/v1/allocation/unpack-instructions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(500))
                    .andExpect(jsonPath("$.content[0].fromUnitType").value("CASE"))
                    .andExpect(jsonPath("$.content[0].toUnitType").value("PIECE"))
                    .andExpect(jsonPath("$.content[0].status").value("INSTRUCTED"));
        }
    }

    // --- POST /api/v1/allocation/execute ---

    @Nested
    @DisplayName("POST /api/v1/allocation/execute")
    class ExecuteAllocationTests {

        private static final String EXECUTE_REQUEST_JSON = """
                {
                    "outboundSlipIds": [1, 2]
                }
                """;

        @Test
        @DisplayName("正常に引当実行結果を返す")
        void execute_returns200() throws Exception {
            AllocationResult result = new AllocationResult(
                    2,
                    List.of(
                            new AllocatedSlipInfo(1L, "OUT-20260320-0001", "ALLOCATED",
                                    List.of(new AllocatedLineInfo(1, "PRD-0001", "商品1", 10, 10))),
                            new AllocatedSlipInfo(2L, "OUT-20260320-0002", "ALLOCATED",
                                    List.of(new AllocatedLineInfo(1, "PRD-0002", "商品2", 5, 5)))
                    ),
                    List.of(),
                    List.of()
            );

            when(allocationService.executeAllocation(any())).thenReturn(result);

            mockMvc.perform(post("/api/v1/allocation/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(EXECUTE_REQUEST_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.allocatedCount").value(2))
                    .andExpect(jsonPath("$.allocatedSlips", hasSize(2)))
                    .andExpect(jsonPath("$.allocatedSlips[0].slipNumber").value("OUT-20260320-0001"))
                    .andExpect(jsonPath("$.allocatedSlips[0].status").value("ALLOCATED"))
                    .andExpect(jsonPath("$.unpackInstructions", hasSize(0)))
                    .andExpect(jsonPath("$.unallocatedLines", hasSize(0)));
        }

        @Test
        @DisplayName("ステータス不正で400を返す")
        void execute_invalidStatus_returns400() throws Exception {
            when(allocationService.executeAllocation(any()))
                    .thenThrow(new InvalidStateTransitionException("OUTBOUND_INVALID_STATUS",
                            "引当可能なステータスではありません"));

            mockMvc.perform(post("/api/v1/allocation/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(EXECUTE_REQUEST_JSON))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("伝票が存在しない場合404を返す")
        void execute_slipNotFound_returns404() throws Exception {
            when(allocationService.executeAllocation(any()))
                    .thenThrow(new ResourceNotFoundException("OUTBOUND_SLIP_NOT_FOUND",
                            "出荷伝票が見つかりません"));

            mockMvc.perform(post("/api/v1/allocation/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(EXECUTE_REQUEST_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    // --- POST /api/v1/allocation/release ---

    @Nested
    @DisplayName("POST /api/v1/allocation/release")
    class ReleaseAllocationTests {

        private static final String RELEASE_REQUEST_JSON = """
                {
                    "outboundSlipIds": [1]
                }
                """;

        @Test
        @DisplayName("正常に引当解放結果を返す")
        void release_returns200() throws Exception {
            AllocationReleaseInfo result = new AllocationReleaseInfo(
                    1,
                    List.of(new ReleasedSlipInfo(1L, "OUT-20260320-0001", "ALLOCATED", "ORDERED"))
            );

            when(allocationService.releaseAllocation(any())).thenReturn(result);

            mockMvc.perform(post("/api/v1/allocation/release")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(RELEASE_REQUEST_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.releasedCount").value(1))
                    .andExpect(jsonPath("$.releasedSlips", hasSize(1)))
                    .andExpect(jsonPath("$.releasedSlips[0].slipNumber").value("OUT-20260320-0001"))
                    .andExpect(jsonPath("$.releasedSlips[0].previousStatus").value("ALLOCATED"))
                    .andExpect(jsonPath("$.releasedSlips[0].newStatus").value("ORDERED"));
        }

        @Test
        @DisplayName("ステータス不正で400を返す")
        void release_invalidStatus_returns400() throws Exception {
            when(allocationService.releaseAllocation(any()))
                    .thenThrow(new InvalidStateTransitionException("OUTBOUND_INVALID_STATUS",
                            "引当解放可能なステータスではありません"));

            mockMvc.perform(post("/api/v1/allocation/release")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(RELEASE_REQUEST_JSON))
                    .andExpect(status().isConflict());
        }
    }

    // --- PUT /api/v1/allocation/unpack-instructions/{id}/complete ---

    @Nested
    @DisplayName("PUT /api/v1/allocation/unpack-instructions/{id}/complete")
    class CompleteUnpackTests {

        @Test
        @DisplayName("正常にばらし完了結果を返す")
        void complete_returns200() throws Exception {
            OffsetDateTime completedAt = OffsetDateTime.now();
            UnpackCompletionInfo result = new UnpackCompletionInfo(
                    500L, "COMPLETED", completedAt,
                    List.of(
                            new MovementInfo("BREAKDOWN_OUT", "PRD-0001", "CASE", -1, "A-01-01"),
                            new MovementInfo("BREAKDOWN_IN", "PRD-0001", "PIECE", 24, "A-01-01")
                    )
            );

            when(allocationService.completeUnpackInstruction(500L)).thenReturn(result);

            mockMvc.perform(put("/api/v1/allocation/unpack-instructions/500/complete"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(500))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.inventoryMovements", hasSize(2)))
                    .andExpect(jsonPath("$.inventoryMovements[0].movementType").value("BREAKDOWN_OUT"))
                    .andExpect(jsonPath("$.inventoryMovements[1].movementType").value("BREAKDOWN_IN"));
        }

        @Test
        @DisplayName("存在しないばらし指示で404を返す")
        void complete_notFound_returns404() throws Exception {
            when(allocationService.completeUnpackInstruction(999L))
                    .thenThrow(new ResourceNotFoundException("UNPACK_INSTRUCTION_NOT_FOUND",
                            "ばらし指示が見つかりません"));

            mockMvc.perform(put("/api/v1/allocation/unpack-instructions/999/complete"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("既に完了済みのばらし指示で400を返す")
        void complete_alreadyCompleted_returns400() throws Exception {
            when(allocationService.completeUnpackInstruction(500L))
                    .thenThrow(new InvalidStateTransitionException("UNPACK_ALREADY_COMPLETED",
                            "既に完了済みのばらし指示です"));

            mockMvc.perform(put("/api/v1/allocation/unpack-instructions/500/complete"))
                    .andExpect(status().isConflict());
        }
    }
}
