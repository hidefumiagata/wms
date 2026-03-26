package com.wms.outbound.service;

import com.wms.allocation.entity.AllocationDetail;
import com.wms.allocation.entity.UnpackInstruction;
import com.wms.allocation.repository.AllocationDetailRepository;
import com.wms.allocation.repository.UnpackInstructionRepository;
import com.wms.generated.model.CompletePickingLineRequest;
import com.wms.generated.model.CompletePickingRequest;
import com.wms.generated.model.CreatePickingInstructionRequest;
import com.wms.generated.model.OutboundLineStatus;
import com.wms.generated.model.OutboundSlipStatus;
import com.wms.generated.model.PickingInstructionStatus;
import com.wms.generated.model.PickingLineStatus;
import com.wms.master.entity.Area;
import com.wms.master.entity.Location;
import com.wms.master.entity.Warehouse;
import com.wms.master.repository.LocationRepository;
import com.wms.master.service.AreaService;
import com.wms.master.service.WarehouseService;
import com.wms.outbound.entity.OutboundSlip;
import com.wms.outbound.entity.OutboundSlipLine;
import com.wms.outbound.entity.PickingInstruction;
import com.wms.outbound.entity.PickingInstructionLine;
import com.wms.outbound.repository.OutboundSlipRepository;
import com.wms.outbound.repository.PickingInstructionLineRepository;
import com.wms.outbound.repository.PickingInstructionRepository;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.InvalidStateTransitionException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.security.WmsUserDetails;
import com.wms.shared.util.BusinessDateProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PickingService")
class PickingServiceTest {

    @Mock
    private PickingInstructionRepository pickingInstructionRepository;

    @Mock
    private PickingInstructionLineRepository pickingInstructionLineRepository;

    @Mock
    private OutboundSlipRepository outboundSlipRepository;

    @Mock
    private AllocationDetailRepository allocationDetailRepository;

    @Mock
    private UnpackInstructionRepository unpackInstructionRepository;

    @Mock
    private WarehouseService warehouseService;

    @Mock
    private AreaService areaService;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private BusinessDateProvider businessDateProvider;

    @InjectMocks
    private PickingService pickingService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setUpSecurityContext() {
        WmsUserDetails userDetails = new WmsUserDetails(
                10L, "testuser", "password", "WH-001",
                List.of(new SimpleGrantedAuthority("ROLE_WAREHOUSE_MANAGER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

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

    private OutboundSlip createOutboundSlip(Long id, String slipNumber, String status) {
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

    private OutboundSlipLine createOutboundSlipLine(Long id, OutboundSlip slip, int lineNo) {
        OutboundSlipLine line = OutboundSlipLine.builder()
                .outboundSlip(slip)
                .lineNo(lineNo)
                .productId(100L + lineNo)
                .productCode("PRD-000" + lineNo)
                .productName("商品" + lineNo)
                .unitType("CASE")
                .orderedQty(10)
                .shippedQty(0)
                .lineStatus(OutboundLineStatus.ALLOCATED.getValue())
                .build();
        setField(line, "id", id);
        return line;
    }

    private AllocationDetail createAllocation(Long id, Long slipId, Long slipLineId,
                                               Long locationId, Long productId) {
        return AllocationDetail.builder()
                .id(id)
                .outboundSlipId(slipId)
                .outboundSlipLineId(slipLineId)
                .inventoryId(1L)
                .locationId(locationId)
                .productId(productId)
                .unitType("CASE")
                .allocatedQty(10)
                .warehouseId(1L)
                .build();
    }

    private Location createLocation(Long id, Long areaId, String locationCode) {
        Location loc = new Location();
        setField(loc, "id", id);
        loc.setWarehouseId(1L);
        loc.setAreaId(areaId);
        loc.setLocationCode(locationCode);
        loc.setLocationName("ロケーション" + locationCode);
        return loc;
    }

    private PickingInstruction createPickingInstruction(Long id, String instructionNumber, String status) {
        PickingInstruction pi = PickingInstruction.builder()
                .instructionNumber(instructionNumber)
                .warehouseId(1L)
                .status(status)
                .lines(new ArrayList<>())
                .build();
        setField(pi, "id", id);
        setField(pi, "createdAt", OffsetDateTime.now());
        setField(pi, "createdBy", 10L);
        return pi;
    }

    private PickingInstructionLine createPickingLine(Long id, PickingInstruction pi,
                                                      int lineNo, Long slipLineId, int qtyToPick) {
        PickingInstructionLine line = PickingInstructionLine.builder()
                .pickingInstruction(pi)
                .lineNo(lineNo)
                .outboundSlipLineId(slipLineId)
                .locationId(10L)
                .locationCode("A-01-01")
                .productId(100L)
                .productCode("PRD-0001")
                .productName("商品1")
                .unitType("CASE")
                .qtyToPick(qtyToPick)
                .qtyPicked(0)
                .lineStatus(PickingLineStatus.PENDING.getValue())
                .build();
        setField(line, "id", id);
        return line;
    }

    // ==================== search ====================

    @Nested
    @DisplayName("search - ピッキング指示一覧検索")
    class SearchTests {

        @Test
        @DisplayName("正常に検索結果を返す")
        void search_success() {
            Warehouse wh = new Warehouse();
            setField(wh, "id", 1L);
            when(warehouseService.findById(1L)).thenReturn(wh);

            PickingInstruction pi = createPickingInstruction(1L, "PIC-20260320-001", "CREATED");
            when(pickingInstructionRepository.search(eq(1L), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(pi)));

            Page<PickingInstruction> result = pickingService.search(
                    1L, null, null, null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getInstructionNumber()).isEqualTo("PIC-20260320-001");
        }

        @Test
        @DisplayName("指示番号・日付範囲指定で検索する")
        void search_withFilters() {
            Warehouse wh = new Warehouse();
            setField(wh, "id", 1L);
            when(warehouseService.findById(1L)).thenReturn(wh);

            when(pickingInstructionRepository.search(eq(1L), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            pickingService.search(1L, "PIC-2026%",
                    List.of("CREATED"), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                    PageRequest.of(0, 20));

            verify(pickingInstructionRepository).search(eq(1L), any(), any(), any(), any(), any(Pageable.class));
        }

        @Test
        @DisplayName("倉庫が存在しない場合404")
        void search_warehouseNotFound() {
            when(warehouseService.findById(999L))
                    .thenThrow(new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "倉庫 が見つかりません"));

            assertThatThrownBy(() -> pickingService.search(999L, null, null, null, null, PageRequest.of(0, 20)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== findByIdWithLines ====================

    @Nested
    @DisplayName("findByIdWithLines - ピッキング指示詳細取得")
    class FindByIdTests {

        @Test
        @DisplayName("正常に詳細を返す")
        void findById_success() {
            PickingInstruction pi = createPickingInstruction(1L, "PIC-20260320-001", "CREATED");
            when(pickingInstructionRepository.findByIdWithLines(1L)).thenReturn(Optional.of(pi));

            PickingInstruction result = pickingService.findByIdWithLines(1L);
            assertThat(result.getInstructionNumber()).isEqualTo("PIC-20260320-001");
        }

        @Test
        @DisplayName("存在しない場合404")
        void findById_notFound() {
            when(pickingInstructionRepository.findByIdWithLines(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pickingService.findByIdWithLines(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("ピッキング指示が見つかりません");
        }
    }

    // ==================== countLinesByInstructionId ====================

    @Nested
    @DisplayName("countLinesByInstructionId")
    class CountLinesTests {

        @Test
        @DisplayName("明細件数を返す")
        void countLines_success() {
            when(pickingInstructionRepository.countLinesByInstructionId(1L)).thenReturn(5L);
            assertThat(pickingService.countLinesByInstructionId(1L)).isEqualTo(5L);
        }
    }

    // ==================== createPickingInstruction ====================

    @Nested
    @DisplayName("createPickingInstruction - ピッキング指示作成")
    class CreateTests {

        @Test
        @DisplayName("正常にピッキング指示を作成する")
        void create_success() {
            setUpSecurityContext();
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 20));

            // 出荷伝票
            OutboundSlip slip = createOutboundSlip(1L, "OUT-20260320-0001", OutboundSlipStatus.ALLOCATED.getValue());
            OutboundSlipLine slipLine = createOutboundSlipLine(10L, slip, 1);
            slip.getLines().add(slipLine);
            when(outboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            // ばらし指示なし
            when(unpackInstructionRepository.findByOutboundSlipIdAndStatus(1L, "INSTRUCTED"))
                    .thenReturn(List.of());

            // 引当明細
            AllocationDetail alloc = createAllocation(1L, 1L, 10L, 20L, 101L);
            when(allocationDetailRepository.findByOutboundSlipId(1L)).thenReturn(List.of(alloc));

            // ロケーション
            Location loc = createLocation(20L, 5L, "A-01-01");
            when(locationRepository.findById(20L)).thenReturn(Optional.of(loc));

            // 採番
            when(pickingInstructionRepository.findMaxSequenceByDate("20260320")).thenReturn(0);

            // 保存
            when(pickingInstructionRepository.save(any(PickingInstruction.class)))
                    .thenAnswer(inv -> {
                        PickingInstruction saved = inv.getArgument(0);
                        setField(saved, "id", 50L);
                        setField(saved, "createdAt", OffsetDateTime.now());
                        setField(saved, "createdBy", 10L);
                        return saved;
                    });

            CreatePickingInstructionRequest request = new CreatePickingInstructionRequest(List.of(1L));

            PickingInstruction result = pickingService.createPickingInstruction(request);

            assertThat(result.getInstructionNumber()).isEqualTo("PIC-20260320-001");
            assertThat(result.getStatus()).isEqualTo(PickingInstructionStatus.CREATED.getValue());
            assertThat(result.getLines()).hasSize(1);
            assertThat(result.getLines().get(0).getQtyToPick()).isEqualTo(10);
            assertThat(result.getLines().get(0).getLocationCode()).isEqualTo("A-01-01");

            verify(pickingInstructionRepository).save(any(PickingInstruction.class));
        }

        @Test
        @DisplayName("ALLOCATED以外の伝票が含まれている場合409")
        void create_invalidStatus() {
            OutboundSlip slip = createOutboundSlip(1L, "OUT-20260320-0001", OutboundSlipStatus.ORDERED.getValue());
            when(outboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            CreatePickingInstructionRequest request = new CreatePickingInstructionRequest(List.of(1L));

            assertThatThrownBy(() -> pickingService.createPickingInstruction(request))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("ステータス");
        }

        @Test
        @DisplayName("出荷伝票が存在しない場合404")
        void create_slipNotFound() {
            when(outboundSlipRepository.findByIdWithLines(999L)).thenReturn(Optional.empty());

            CreatePickingInstructionRequest request = new CreatePickingInstructionRequest(List.of(999L));

            assertThatThrownBy(() -> pickingService.createPickingInstruction(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("出荷伝票が見つかりません");
        }

        @Test
        @DisplayName("未完了のばらし指示がある場合409")
        void create_unpackNotCompleted() {
            OutboundSlip slip = createOutboundSlip(1L, "OUT-20260320-0001", OutboundSlipStatus.ALLOCATED.getValue());
            when(outboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            UnpackInstruction unpack = UnpackInstruction.builder()
                    .outboundSlipId(1L)
                    .status("INSTRUCTED")
                    .build();
            when(unpackInstructionRepository.findByOutboundSlipIdAndStatus(1L, "INSTRUCTED"))
                    .thenReturn(List.of(unpack));

            CreatePickingInstructionRequest request = new CreatePickingInstructionRequest(List.of(1L));

            assertThatThrownBy(() -> pickingService.createPickingInstruction(request))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("未完了のばらし指示が存在する");
        }

        @Test
        @DisplayName("areaIdが指定された場合、エリア存在チェックを行う")
        void create_areaNotFound() {
            when(areaService.findById(999L))
                    .thenThrow(new ResourceNotFoundException("AREA_NOT_FOUND", "エリア が見つかりません (id=999)"));

            CreatePickingInstructionRequest request = new CreatePickingInstructionRequest(List.of(1L));
            request.setAreaId(999L);

            assertThatThrownBy(() -> pickingService.createPickingInstruction(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("エリア が見つかりません");
        }

        @Test
        @DisplayName("複数伝票を指定した場合に正常に作成できる")
        void create_multipleSlips_success() {
            setUpSecurityContext();
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 20));

            OutboundSlip slip1 = createOutboundSlip(1L, "OUT-20260320-0001", OutboundSlipStatus.ALLOCATED.getValue());
            OutboundSlipLine slipLine1 = createOutboundSlipLine(10L, slip1, 1);
            slip1.getLines().add(slipLine1);
            OutboundSlip slip2 = createOutboundSlip(2L, "OUT-20260320-0002", OutboundSlipStatus.ALLOCATED.getValue());
            OutboundSlipLine slipLine2 = createOutboundSlipLine(20L, slip2, 1);
            slip2.getLines().add(slipLine2);
            when(outboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip1));
            when(outboundSlipRepository.findByIdWithLines(2L)).thenReturn(Optional.of(slip2));
            when(unpackInstructionRepository.findByOutboundSlipIdAndStatus(eq(1L), eq("INSTRUCTED"))).thenReturn(List.of());
            when(unpackInstructionRepository.findByOutboundSlipIdAndStatus(eq(2L), eq("INSTRUCTED"))).thenReturn(List.of());

            AllocationDetail alloc1 = createAllocation(1L, 1L, 10L, 20L, 101L);
            AllocationDetail alloc2 = createAllocation(2L, 2L, 20L, 30L, 102L);
            when(allocationDetailRepository.findByOutboundSlipId(1L)).thenReturn(List.of(alloc1));
            when(allocationDetailRepository.findByOutboundSlipId(2L)).thenReturn(List.of(alloc2));

            Location loc1 = createLocation(20L, 5L, "A-01-01");
            Location loc2 = createLocation(30L, 5L, "A-02-01");
            when(locationRepository.findById(20L)).thenReturn(Optional.of(loc1));
            when(locationRepository.findById(30L)).thenReturn(Optional.of(loc2));
            when(pickingInstructionRepository.findMaxSequenceByDate("20260320")).thenReturn(0);
            when(pickingInstructionRepository.save(any(PickingInstruction.class)))
                    .thenAnswer(inv -> {
                        PickingInstruction saved = inv.getArgument(0);
                        setField(saved, "id", 50L);
                        setField(saved, "createdAt", OffsetDateTime.now());
                        setField(saved, "createdBy", 10L);
                        return saved;
                    });

            CreatePickingInstructionRequest request = new CreatePickingInstructionRequest(List.of(1L, 2L));

            PickingInstruction result = pickingService.createPickingInstruction(request);

            assertThat(result.getLines()).hasSize(2);
        }

        @Test
        @DisplayName("areaId指定時にエリア外のロケーションがフィルタされる")
        void create_areaFilter_skipsNonMatchingLocations() {
            setUpSecurityContext();
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 20));

            OutboundSlip slip = createOutboundSlip(1L, "OUT-20260320-0001", OutboundSlipStatus.ALLOCATED.getValue());
            OutboundSlipLine slipLine = createOutboundSlipLine(10L, slip, 1);
            slip.getLines().add(slipLine);
            when(outboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(unpackInstructionRepository.findByOutboundSlipIdAndStatus(1L, "INSTRUCTED"))
                    .thenReturn(List.of());

            AllocationDetail alloc1 = createAllocation(1L, 1L, 10L, 20L, 101L);
            AllocationDetail alloc2 = createAllocation(2L, 1L, 10L, 30L, 101L);
            when(allocationDetailRepository.findByOutboundSlipId(1L)).thenReturn(List.of(alloc1, alloc2));

            // alloc1のロケーション: areaId=5 → マッチ
            Location loc1 = createLocation(20L, 5L, "A-01-01");
            // alloc2のロケーション: areaId=6 → フィルタされる
            Location loc2 = createLocation(30L, 6L, "B-01-01");
            when(locationRepository.findById(20L)).thenReturn(Optional.of(loc1));
            when(locationRepository.findById(30L)).thenReturn(Optional.of(loc2));

            when(areaService.findById(5L)).thenReturn(new Area());
            when(pickingInstructionRepository.findMaxSequenceByDate("20260320")).thenReturn(0);
            when(pickingInstructionRepository.save(any(PickingInstruction.class)))
                    .thenAnswer(inv -> {
                        PickingInstruction saved = inv.getArgument(0);
                        setField(saved, "id", 50L);
                        setField(saved, "createdAt", OffsetDateTime.now());
                        setField(saved, "createdBy", 10L);
                        return saved;
                    });

            CreatePickingInstructionRequest request = new CreatePickingInstructionRequest(List.of(1L));
            request.setAreaId(5L);

            PickingInstruction result = pickingService.createPickingInstruction(request);

            assertThat(result.getLines()).hasSize(1);
            assertThat(result.getLines().get(0).getLocationCode()).isEqualTo("A-01-01");
        }

        @Test
        @DisplayName("areaId指定時にロケーションが見つからない引当明細はスキップされる")
        void create_areaFilter_skipsNullLocation() {
            setUpSecurityContext();
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 20));

            OutboundSlip slip = createOutboundSlip(1L, "OUT-20260320-0001", OutboundSlipStatus.ALLOCATED.getValue());
            OutboundSlipLine slipLine = createOutboundSlipLine(10L, slip, 1);
            slip.getLines().add(slipLine);
            when(outboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(unpackInstructionRepository.findByOutboundSlipIdAndStatus(1L, "INSTRUCTED"))
                    .thenReturn(List.of());

            AllocationDetail alloc1 = createAllocation(1L, 1L, 10L, 20L, 101L);
            AllocationDetail alloc2 = createAllocation(2L, 1L, 10L, 99L, 101L);
            when(allocationDetailRepository.findByOutboundSlipId(1L)).thenReturn(List.of(alloc1, alloc2));

            Location loc1 = createLocation(20L, 5L, "A-01-01");
            when(locationRepository.findById(20L)).thenReturn(Optional.of(loc1));
            // alloc2のロケーションが見つからない
            when(locationRepository.findById(99L)).thenReturn(Optional.empty());

            when(areaService.findById(5L)).thenReturn(new Area());
            when(pickingInstructionRepository.findMaxSequenceByDate("20260320")).thenReturn(0);
            when(pickingInstructionRepository.save(any(PickingInstruction.class)))
                    .thenAnswer(inv -> {
                        PickingInstruction saved = inv.getArgument(0);
                        setField(saved, "id", 50L);
                        setField(saved, "createdAt", OffsetDateTime.now());
                        setField(saved, "createdBy", 10L);
                        return saved;
                    });

            CreatePickingInstructionRequest request = new CreatePickingInstructionRequest(List.of(1L));
            request.setAreaId(5L);

            PickingInstruction result = pickingService.createPickingInstruction(request);

            assertThat(result.getLines()).hasSize(1);
        }

        @Test
        @DisplayName("出荷明細に対応するslipLineが見つからない場合も明細作成される")
        void create_slipLineNotFound_usesEmptyNames() {
            setUpSecurityContext();
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 20));

            OutboundSlip slip = createOutboundSlip(1L, "OUT-20260320-0001", OutboundSlipStatus.ALLOCATED.getValue());
            // slipLineは存在しない（空のlines）
            when(outboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(unpackInstructionRepository.findByOutboundSlipIdAndStatus(1L, "INSTRUCTED"))
                    .thenReturn(List.of());

            // 引当明細のslipLineIdが伝票のlinesに存在しない
            AllocationDetail alloc = createAllocation(1L, 1L, 999L, 20L, 101L);
            when(allocationDetailRepository.findByOutboundSlipId(1L)).thenReturn(List.of(alloc));

            Location loc = createLocation(20L, 5L, "A-01-01");
            when(locationRepository.findById(20L)).thenReturn(Optional.of(loc));
            when(pickingInstructionRepository.findMaxSequenceByDate("20260320")).thenReturn(0);
            when(pickingInstructionRepository.save(any(PickingInstruction.class)))
                    .thenAnswer(inv -> {
                        PickingInstruction saved = inv.getArgument(0);
                        setField(saved, "id", 50L);
                        setField(saved, "createdAt", OffsetDateTime.now());
                        setField(saved, "createdBy", 10L);
                        return saved;
                    });

            CreatePickingInstructionRequest request = new CreatePickingInstructionRequest(List.of(1L));

            PickingInstruction result = pickingService.createPickingInstruction(request);

            assertThat(result.getLines()).hasSize(1);
            assertThat(result.getLines().get(0).getProductName()).isEmpty();
            assertThat(result.getLines().get(0).getProductCode()).isEmpty();
        }

        @Test
        @DisplayName("ロケーションが見つからない場合ResourceNotFoundException")
        void create_locationNotFound_throws() {
            setUpSecurityContext();

            OutboundSlip slip = createOutboundSlip(1L, "OUT-20260320-0001", OutboundSlipStatus.ALLOCATED.getValue());
            OutboundSlipLine slipLine = createOutboundSlipLine(10L, slip, 1);
            slip.getLines().add(slipLine);
            when(outboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(unpackInstructionRepository.findByOutboundSlipIdAndStatus(1L, "INSTRUCTED"))
                    .thenReturn(List.of());

            AllocationDetail alloc = createAllocation(1L, 1L, 10L, 99L, 101L);
            when(allocationDetailRepository.findByOutboundSlipId(1L)).thenReturn(List.of(alloc));
            when(locationRepository.findById(99L)).thenReturn(Optional.empty());

            CreatePickingInstructionRequest request = new CreatePickingInstructionRequest(List.of(1L));

            assertThatThrownBy(() -> pickingService.createPickingInstruction(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("ロケーションが見つかりません");
        }

        @Test
        @DisplayName("引当明細が存在しない場合エラー")
        void create_noAllocations() {
            OutboundSlip slip = createOutboundSlip(1L, "OUT-20260320-0001", OutboundSlipStatus.ALLOCATED.getValue());
            when(outboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(unpackInstructionRepository.findByOutboundSlipIdAndStatus(1L, "INSTRUCTED"))
                    .thenReturn(List.of());
            when(allocationDetailRepository.findByOutboundSlipId(1L)).thenReturn(List.of());

            CreatePickingInstructionRequest request = new CreatePickingInstructionRequest(List.of(1L));

            assertThatThrownBy(() -> pickingService.createPickingInstruction(request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("ピッキング対象の引当明細が存在しません");
        }
    }

    // ==================== completePickingInstruction ====================

    @Nested
    @DisplayName("completePickingInstruction - ピッキング完了登録")
    class CompleteTests {

        @Test
        @DisplayName("全明細完了でCOMPLETED + 出荷伝票PICKING_COMPLETED")
        void complete_allLines() {
            setUpSecurityContext();

            PickingInstruction pi = createPickingInstruction(50L, "PIC-20260320-001",
                    PickingInstructionStatus.CREATED.getValue());
            PickingInstructionLine line1 = createPickingLine(101L, pi, 1, 10L, 5);
            PickingInstructionLine line2 = createPickingLine(102L, pi, 2, 11L, 3);
            pi.getLines().add(line1);
            pi.getLines().add(line2);

            when(pickingInstructionRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(pi));

            // 出荷伝票の更新用
            OutboundSlip slip = createOutboundSlip(1L, "OUT-20260320-0001", OutboundSlipStatus.ALLOCATED.getValue());
            OutboundSlipLine slipLine1 = createOutboundSlipLine(10L, slip, 1);
            OutboundSlipLine slipLine2 = createOutboundSlipLine(11L, slip, 2);
            slip.getLines().add(slipLine1);
            slip.getLines().add(slipLine2);
            when(outboundSlipRepository.findBySlipLineId(10L)).thenReturn(Optional.of(slip));
            when(outboundSlipRepository.findBySlipLineId(11L)).thenReturn(Optional.of(slip));

            when(pickingInstructionRepository.save(any(PickingInstruction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(outboundSlipRepository.save(any(OutboundSlip.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            CompletePickingRequest request = new CompletePickingRequest(List.of(
                    new CompletePickingLineRequest(101L, 5),
                    new CompletePickingLineRequest(102L, 3)));

            PickingInstruction result = pickingService.completePickingInstruction(50L, request);

            assertThat(result.getStatus()).isEqualTo(PickingInstructionStatus.COMPLETED.getValue());
            assertThat(result.getCompletedAt()).isNotNull();
            assertThat(result.getCompletedBy()).isEqualTo(10L);
            assertThat(result.getLines().get(0).getQtyPicked()).isEqualTo(5);
            assertThat(result.getLines().get(1).getQtyPicked()).isEqualTo(3);

            // 出荷伝票がPICKING_COMPLETEDに更新される
            verify(outboundSlipRepository).save(any(OutboundSlip.class));
        }

        @Test
        @DisplayName("一部明細完了でIN_PROGRESS")
        void complete_partialLines() {
            setUpSecurityContext();

            PickingInstruction pi = createPickingInstruction(50L, "PIC-20260320-001",
                    PickingInstructionStatus.CREATED.getValue());
            PickingInstructionLine line1 = createPickingLine(101L, pi, 1, 10L, 5);
            PickingInstructionLine line2 = createPickingLine(102L, pi, 2, 11L, 3);
            pi.getLines().add(line1);
            pi.getLines().add(line2);

            when(pickingInstructionRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(pi));
            when(pickingInstructionRepository.save(any(PickingInstruction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // 1行目だけ完了
            CompletePickingRequest request = new CompletePickingRequest(List.of(
                    new CompletePickingLineRequest(101L, 5)));

            PickingInstruction result = pickingService.completePickingInstruction(50L, request);

            assertThat(result.getStatus()).isEqualTo(PickingInstructionStatus.IN_PROGRESS.getValue());
            assertThat(result.getCompletedAt()).isNull();
            verify(outboundSlipRepository, never()).findBySlipLineId(any());
        }

        @Test
        @DisplayName("全明細完了時に出荷伝票の一部明細のみPICKING_COMPLETEDになる")
        void complete_partialSlipLines_updated() {
            setUpSecurityContext();

            PickingInstruction pi = createPickingInstruction(50L, "PIC-20260320-001",
                    PickingInstructionStatus.CREATED.getValue());
            PickingInstructionLine line1 = createPickingLine(101L, pi, 1, 10L, 5);
            pi.getLines().add(line1);

            when(pickingInstructionRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(pi));

            OutboundSlip slip = createOutboundSlip(1L, "OUT-20260320-0001", OutboundSlipStatus.ALLOCATED.getValue());
            OutboundSlipLine slipLine1 = createOutboundSlipLine(10L, slip, 1);
            OutboundSlipLine slipLine2 = createOutboundSlipLine(11L, slip, 2); // ピッキング指示に含まれない
            slip.getLines().add(slipLine1);
            slip.getLines().add(slipLine2);
            when(outboundSlipRepository.findBySlipLineId(10L)).thenReturn(Optional.of(slip));
            when(pickingInstructionRepository.save(any(PickingInstruction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(outboundSlipRepository.save(any(OutboundSlip.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            CompletePickingRequest request = new CompletePickingRequest(List.of(
                    new CompletePickingLineRequest(101L, 5)));

            pickingService.completePickingInstruction(50L, request);

            // slipLine1のみPICKING_COMPLETEDに更新
            assertThat(slipLine1.getLineStatus()).isEqualTo(OutboundLineStatus.PICKING_COMPLETED.getValue());
            // slipLine2はALLOCATEDのまま
            assertThat(slipLine2.getLineStatus()).isEqualTo(OutboundLineStatus.ALLOCATED.getValue());
        }

        @Test
        @DisplayName("IN_PROGRESSステータスから一部明細完了してもIN_PROGRESSのまま")
        void complete_partialFromInProgress() {
            setUpSecurityContext();

            PickingInstruction pi = createPickingInstruction(50L, "PIC-20260320-001",
                    PickingInstructionStatus.IN_PROGRESS.getValue());
            PickingInstructionLine line1 = createPickingLine(101L, pi, 1, 10L, 5);
            line1.setLineStatus(PickingLineStatus.COMPLETED.getValue());
            line1.setQtyPicked(5);
            PickingInstructionLine line2 = createPickingLine(102L, pi, 2, 11L, 3);
            PickingInstructionLine line3 = createPickingLine(103L, pi, 3, 12L, 4);
            pi.getLines().add(line1);
            pi.getLines().add(line2);
            pi.getLines().add(line3);

            when(pickingInstructionRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(pi));
            when(pickingInstructionRepository.save(any(PickingInstruction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            CompletePickingRequest request = new CompletePickingRequest(List.of(
                    new CompletePickingLineRequest(102L, 3)));

            PickingInstruction result = pickingService.completePickingInstruction(50L, request);

            // まだ全明細完了していないのでIN_PROGRESSのまま
            assertThat(result.getStatus()).isEqualTo(PickingInstructionStatus.IN_PROGRESS.getValue());
        }

        @Test
        @DisplayName("全明細完了時に出荷伝票が見つからない場合もスキップされる")
        void complete_slipNotFound_skipped() {
            setUpSecurityContext();

            PickingInstruction pi = createPickingInstruction(50L, "PIC-20260320-001",
                    PickingInstructionStatus.CREATED.getValue());
            PickingInstructionLine line1 = createPickingLine(101L, pi, 1, 10L, 5);
            pi.getLines().add(line1);

            when(pickingInstructionRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(pi));
            // 出荷伝票が見つからない
            when(outboundSlipRepository.findBySlipLineId(10L)).thenReturn(Optional.empty());
            when(pickingInstructionRepository.save(any(PickingInstruction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            CompletePickingRequest request = new CompletePickingRequest(List.of(
                    new CompletePickingLineRequest(101L, 5)));

            PickingInstruction result = pickingService.completePickingInstruction(50L, request);

            assertThat(result.getStatus()).isEqualTo(PickingInstructionStatus.COMPLETED.getValue());
            verify(outboundSlipRepository, never()).save(any());
        }

        @Test
        @DisplayName("既にCOMPLETEDの場合409")
        void complete_alreadyCompleted() {
            PickingInstruction pi = createPickingInstruction(50L, "PIC-20260320-001",
                    PickingInstructionStatus.COMPLETED.getValue());
            when(pickingInstructionRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(pi));

            CompletePickingRequest request = new CompletePickingRequest(List.of(
                    new CompletePickingLineRequest(101L, 5)));

            assertThatThrownBy(() -> pickingService.completePickingInstruction(50L, request))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("完了済み");
        }

        @Test
        @DisplayName("存在しないlineIdの場合エラー")
        void complete_invalidLineId() {
            setUpSecurityContext();

            PickingInstruction pi = createPickingInstruction(50L, "PIC-20260320-001",
                    PickingInstructionStatus.CREATED.getValue());
            PickingInstructionLine line1 = createPickingLine(101L, pi, 1, 10L, 5);
            pi.getLines().add(line1);

            when(pickingInstructionRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(pi));

            CompletePickingRequest request = new CompletePickingRequest(List.of(
                    new CompletePickingLineRequest(999L, 5)));

            assertThatThrownBy(() -> pickingService.completePickingInstruction(50L, request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("lineIdが当該ピッキング指示に存在しません");
        }

        @Test
        @DisplayName("qtyPickedがqtyToPickを超える場合エラー")
        void complete_qtyExceeded() {
            setUpSecurityContext();

            PickingInstruction pi = createPickingInstruction(50L, "PIC-20260320-001",
                    PickingInstructionStatus.CREATED.getValue());
            PickingInstructionLine line1 = createPickingLine(101L, pi, 1, 10L, 5);
            pi.getLines().add(line1);

            when(pickingInstructionRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(pi));

            CompletePickingRequest request = new CompletePickingRequest(List.of(
                    new CompletePickingLineRequest(101L, 10)));

            assertThatThrownBy(() -> pickingService.completePickingInstruction(50L, request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("ピッキング完了数量がピッキング予定数量を超えています");
        }

        @Test
        @DisplayName("存在しないピッキング指示の場合404")
        void complete_notFound() {
            when(pickingInstructionRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            CompletePickingRequest request = new CompletePickingRequest(List.of(
                    new CompletePickingLineRequest(101L, 5)));

            assertThatThrownBy(() -> pickingService.completePickingInstruction(999L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("ピッキング指示が見つかりません");
        }
    }

    @Nested
    @DisplayName("sumPickedQtyBySlipLineIds")
    class SumPickedQtyTests {

        @Test
        @DisplayName("明細ごとのピッキング数量合計を返す")
        void sumPickedQty_returnsMapGroupedByLineId() {
            PickingInstructionLine l1 = PickingInstructionLine.builder()
                    .outboundSlipLineId(10L).qtyPicked(30).build();
            PickingInstructionLine l2 = PickingInstructionLine.builder()
                    .outboundSlipLineId(10L).qtyPicked(20).build();
            PickingInstructionLine l3 = PickingInstructionLine.builder()
                    .outboundSlipLineId(20L).qtyPicked(50).build();

            when(pickingInstructionLineRepository.findByOutboundSlipLineIdIn(List.of(10L, 20L)))
                    .thenReturn(List.of(l1, l2, l3));

            var result = pickingService.sumPickedQtyBySlipLineIds(List.of(10L, 20L));

            assertThat(result).hasSize(2);
            assertThat(result.get(10L)).isEqualTo(50);
            assertThat(result.get(20L)).isEqualTo(50);
        }

        @Test
        @DisplayName("空リストの場合は空マップを返す")
        void sumPickedQty_emptyList_returnsEmptyMap() {
            var result = pickingService.sumPickedQtyBySlipLineIds(List.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("nullの場合は空マップを返す")
        void sumPickedQty_null_returnsEmptyMap() {
            var result = pickingService.sumPickedQtyBySlipLineIds(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("集計値が0の明細はマップから除外される")
        void sumPickedQty_zeroValue_excluded() {
            PickingInstructionLine l1 = PickingInstructionLine.builder()
                    .outboundSlipLineId(10L).qtyPicked(0).build();
            PickingInstructionLine l2 = PickingInstructionLine.builder()
                    .outboundSlipLineId(20L).qtyPicked(50).build();

            when(pickingInstructionLineRepository.findByOutboundSlipLineIdIn(List.of(10L, 20L)))
                    .thenReturn(List.of(l1, l2));

            var result = pickingService.sumPickedQtyBySlipLineIds(List.of(10L, 20L));

            assertThat(result).hasSize(1);
            assertThat(result.get(20L)).isEqualTo(50);
            assertThat(result.containsKey(10L)).isFalse();
        }
    }
}
