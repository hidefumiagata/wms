package com.wms.allocation.service;

import com.wms.allocation.entity.AllocationDetail;
import com.wms.allocation.entity.UnpackInstruction;
import com.wms.allocation.repository.AllocationDetailRepository;
import com.wms.allocation.repository.UnpackInstructionRepository;
import com.wms.allocation.service.AllocationService.*;
import com.wms.generated.model.ExecuteAllocationRequest;
import com.wms.generated.model.ReleaseAllocationRequest;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.repository.InventoryMovementRepository;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.master.entity.Location;
import com.wms.master.entity.Product;
import com.wms.master.repository.LocationRepository;
import com.wms.master.service.ProductService;
import com.wms.outbound.entity.OutboundSlip;
import com.wms.outbound.entity.OutboundSlipLine;
import com.wms.outbound.repository.OutboundSlipRepository;
import com.wms.shared.exception.InvalidStateTransitionException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.security.WmsUserDetails;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationService")
class AllocationServiceTest {

    @Mock
    private OutboundSlipRepository outboundSlipRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryMovementRepository inventoryMovementRepository;

    @Mock
    private AllocationDetailRepository allocationDetailRepository;

    @Mock
    private UnpackInstructionRepository unpackInstructionRepository;

    @Mock
    private ProductService productService;

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private AllocationService allocationService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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

    void setUpSecurityContext(Long userId) {
        WmsUserDetails userDetails = new WmsUserDetails(
                userId, "testuser", "password", "WH-001",
                List.of(new SimpleGrantedAuthority("ROLE_WAREHOUSE_MANAGER")));
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private OutboundSlip createSlip(Long id, String slipNumber, String status, Long warehouseId) {
        OutboundSlip slip = OutboundSlip.builder()
                .slipNumber(slipNumber)
                .slipType("NORMAL")
                .warehouseId(warehouseId)
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
                                         Long productId, String productCode, int orderedQty,
                                         String unitType, String lineStatus) {
        OutboundSlipLine line = OutboundSlipLine.builder()
                .outboundSlip(slip)
                .lineNo(lineNo)
                .productId(productId)
                .productCode(productCode)
                .productName("商品" + lineNo)
                .unitType(unitType)
                .orderedQty(orderedQty)
                .shippedQty(0)
                .lineStatus(lineStatus)
                .build();
        setField(line, "id", id);
        return line;
    }

    private Inventory createInventory(Long id, Long warehouseId, Long locationId,
                                       Long productId, String unitType, int quantity, int allocatedQty) {
        Inventory inv = Inventory.builder()
                .warehouseId(warehouseId)
                .locationId(locationId)
                .productId(productId)
                .unitType(unitType)
                .quantity(quantity)
                .allocatedQty(allocatedQty)
                .build();
        setField(inv, "id", id);
        return inv;
    }

    private Product createProduct(Long id, String productCode, int caseQty, int ballQty) {
        Product product = new Product();
        setField(product, "id", id);
        product.setProductCode(productCode);
        product.setProductName("商品" + productCode);
        product.setCaseQuantity(caseQty);
        product.setBallQuantity(ballQty);
        product.setIsHazardous(false);
        product.setLotManageFlag(false);
        product.setExpiryManageFlag(false);
        product.setShipmentStopFlag(false);
        product.setStorageCondition("NORMAL");
        return product;
    }

    // --- searchOrders ---

    @Nested
    @DisplayName("searchOrders")
    class SearchOrdersTests {

        @Test
        @DisplayName("引当対象受注を検索して返す")
        void searchOrders_returnsPage() {
            OutboundSlip slip = createSlip(1L, "OUT-20260320-0001", "ORDERED", 1L);
            Page<OutboundSlip> page = new PageImpl<>(List.of(slip));

            when(outboundSlipRepository.searchForAllocation(
                    eq(1L), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(page);

            Page<OutboundSlip> result = allocationService.searchOrders(
                    1L, null, null, null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getSlipNumber()).isEqualTo("OUT-20260320-0001");
        }

        @Test
        @DisplayName("ステータス指定なしの場合ORDERED/PARTIAL_ALLOCATEDがデフォルト")
        void searchOrders_defaultStatuses() {
            when(outboundSlipRepository.searchForAllocation(
                    eq(1L), eq(List.of("ORDERED", "PARTIAL_ALLOCATED")),
                    any(), any(), any(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            allocationService.searchOrders(1L, null, null, null, null, PageRequest.of(0, 20));

            verify(outboundSlipRepository).searchForAllocation(
                    eq(1L), eq(List.of("ORDERED", "PARTIAL_ALLOCATED")),
                    any(), any(), any(), any(Pageable.class));
        }
    }

    // --- searchAllocatedOrders ---

    @Nested
    @DisplayName("searchAllocatedOrders")
    class SearchAllocatedOrdersTests {

        @Test
        @DisplayName("引当済み受注を検索して返す")
        void searchAllocatedOrders_returnsPage() {
            OutboundSlip slip = createSlip(1L, "OUT-20260320-0001", "ALLOCATED", 1L);
            Page<OutboundSlip> page = new PageImpl<>(List.of(slip));

            when(outboundSlipRepository.findByStatusIn(
                    eq(List.of("ALLOCATED", "PARTIAL_ALLOCATED")), any(Pageable.class)))
                    .thenReturn(page);

            Page<OutboundSlip> result = allocationService.searchAllocatedOrders(PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo("ALLOCATED");
        }
    }

    // --- executeAllocation ---

    @Nested
    @DisplayName("executeAllocation")
    class ExecuteAllocationTests {

        @Test
        @DisplayName("正常系: 全量引当成功")
        void executeAllocation_fullAllocation() {
            setUpSecurityContext(10L);

            OutboundSlip slip = createSlip(1L, "OUT-20260320-0001", "ORDERED", 1L);
            OutboundSlipLine line = createLine(100L, slip, 1, 10L, "PRD-0001", 5, "PIECE", "ORDERED");
            slip.getLines().add(line);

            Product product = createProduct(10L, "PRD-0001", 24, 6);
            Inventory inv = createInventory(200L, 1L, 50L, 10L, "PIECE", 20, 0);

            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));
            when(productService.findById(10L)).thenReturn(product);
            when(inventoryRepository.findAvailableStock(1L, 10L)).thenReturn(List.of(inv));
            when(inventoryRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(inv));
            when(inventoryRepository.save(any(Inventory.class))).thenAnswer(i -> i.getArgument(0));
            when(allocationDetailRepository.save(any(AllocationDetail.class))).thenAnswer(i -> i.getArgument(0));
            when(outboundSlipRepository.save(any(OutboundSlip.class))).thenAnswer(i -> i.getArgument(0));

            ExecuteAllocationRequest request = new ExecuteAllocationRequest()
                    .outboundSlipIds(List.of(1L));

            AllocationResult result = allocationService.executeAllocation(request);

            assertThat(result.allocatedCount()).isEqualTo(1);
            assertThat(result.allocatedSlips()).hasSize(1);
            assertThat(result.allocatedSlips().get(0).status()).isEqualTo("ALLOCATED");
            assertThat(result.unallocatedLines()).isEmpty();
            assertThat(line.getLineStatus()).isEqualTo("ALLOCATED");
            assertThat(slip.getStatus()).isEqualTo("ALLOCATED");
            assertThat(inv.getAllocatedQty()).isEqualTo(5);
        }

        @Test
        @DisplayName("部分引当: 在庫不足で一部のみ引当")
        void executeAllocation_partialAllocation() {
            setUpSecurityContext(10L);

            OutboundSlip slip = createSlip(1L, "OUT-20260320-0001", "ORDERED", 1L);
            OutboundSlipLine line = createLine(100L, slip, 1, 10L, "PRD-0001", 10, "PIECE", "ORDERED");
            slip.getLines().add(line);

            Product product = createProduct(10L, "PRD-0001", 24, 6);
            Inventory inv = createInventory(200L, 1L, 50L, 10L, "PIECE", 3, 0);

            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));
            when(productService.findById(10L)).thenReturn(product);
            when(inventoryRepository.findAvailableStock(1L, 10L)).thenReturn(List.of(inv));
            when(inventoryRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(inv));
            when(inventoryRepository.save(any(Inventory.class))).thenAnswer(i -> i.getArgument(0));
            when(allocationDetailRepository.save(any(AllocationDetail.class))).thenAnswer(i -> i.getArgument(0));
            when(outboundSlipRepository.save(any(OutboundSlip.class))).thenAnswer(i -> i.getArgument(0));

            ExecuteAllocationRequest request = new ExecuteAllocationRequest()
                    .outboundSlipIds(List.of(1L));

            AllocationResult result = allocationService.executeAllocation(request);

            assertThat(result.allocatedCount()).isEqualTo(0);
            assertThat(result.allocatedSlips()).hasSize(1);
            assertThat(result.allocatedSlips().get(0).status()).isEqualTo("PARTIAL_ALLOCATED");
            assertThat(result.unallocatedLines()).hasSize(1);
            assertThat(result.unallocatedLines().get(0).shortageQty()).isEqualTo(7);
            assertThat(line.getLineStatus()).isEqualTo("PARTIAL_ALLOCATED");
            assertThat(slip.getStatus()).isEqualTo("PARTIAL_ALLOCATED");
        }

        @Test
        @DisplayName("在庫なし: 引当できない")
        void executeAllocation_noStock() {
            setUpSecurityContext(10L);

            OutboundSlip slip = createSlip(1L, "OUT-20260320-0001", "ORDERED", 1L);
            OutboundSlipLine line = createLine(100L, slip, 1, 10L, "PRD-0001", 5, "PIECE", "ORDERED");
            slip.getLines().add(line);

            Product product = createProduct(10L, "PRD-0001", 24, 6);

            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));
            when(productService.findById(10L)).thenReturn(product);
            when(inventoryRepository.findAvailableStock(1L, 10L)).thenReturn(List.of());
            when(outboundSlipRepository.save(any(OutboundSlip.class))).thenAnswer(i -> i.getArgument(0));

            ExecuteAllocationRequest request = new ExecuteAllocationRequest()
                    .outboundSlipIds(List.of(1L));

            AllocationResult result = allocationService.executeAllocation(request);

            assertThat(result.allocatedCount()).isEqualTo(0);
            assertThat(result.unallocatedLines()).hasSize(1);
            assertThat(result.unallocatedLines().get(0).shortageQty()).isEqualTo(5);
            // ステータスはORDEREDのまま
            assertThat(slip.getStatus()).isEqualTo("ORDERED");
        }

        @Test
        @DisplayName("ステータス不正: SHIPPED伝票は引当不可")
        void executeAllocation_invalidStatus() {
            OutboundSlip slip = createSlip(1L, "OUT-20260320-0001", "SHIPPED", 1L);

            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));

            ExecuteAllocationRequest request = new ExecuteAllocationRequest()
                    .outboundSlipIds(List.of(1L));

            assertThatThrownBy(() -> allocationService.executeAllocation(request))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("引当可能なステータスではありません");
        }

        @Test
        @DisplayName("存在しない伝票はResourceNotFoundException")
        void executeAllocation_slipNotFound() {
            when(outboundSlipRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            ExecuteAllocationRequest request = new ExecuteAllocationRequest()
                    .outboundSlipIds(List.of(999L));

            assertThatThrownBy(() -> allocationService.executeAllocation(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("出荷伝票が見つかりません");
        }

        @Test
        @DisplayName("上位荷姿からのばらし指示生成")
        void executeAllocation_withUnpackInstruction() {
            setUpSecurityContext(10L);

            OutboundSlip slip = createSlip(1L, "OUT-20260320-0001", "ORDERED", 1L);
            OutboundSlipLine line = createLine(100L, slip, 1, 10L, "PRD-0001", 12, "PIECE", "ORDERED");
            slip.getLines().add(line);

            Product product = createProduct(10L, "PRD-0001", 24, 6);
            // PIECEの在庫はない、CASEの在庫がある
            Inventory caseInv = createInventory(201L, 1L, 50L, 10L, "CASE", 5, 0);

            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));
            when(productService.findById(10L)).thenReturn(product);
            when(inventoryRepository.findAvailableStock(1L, 10L)).thenReturn(List.of(caseInv));
            when(inventoryRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(caseInv));
            when(inventoryRepository.save(any(Inventory.class))).thenAnswer(i -> i.getArgument(0));
            when(allocationDetailRepository.save(any(AllocationDetail.class))).thenAnswer(i -> i.getArgument(0));
            when(unpackInstructionRepository.save(any(UnpackInstruction.class))).thenAnswer(i -> {
                UnpackInstruction saved = i.getArgument(0);
                setField(saved, "id", 500L);
                return saved;
            });
            when(outboundSlipRepository.save(any(OutboundSlip.class))).thenAnswer(i -> i.getArgument(0));

            ExecuteAllocationRequest request = new ExecuteAllocationRequest()
                    .outboundSlipIds(List.of(1L));

            AllocationResult result = allocationService.executeAllocation(request);

            assertThat(result.allocatedCount()).isEqualTo(1);
            assertThat(result.unpackInstructions()).hasSize(1);
            assertThat(result.unpackInstructions().get(0).fromUnitType()).isEqualTo("CASE");
            assertThat(result.unpackInstructions().get(0).toUnitType()).isEqualTo("PIECE");
            // 12 PIECEs needed, CASE=24 per case, so 1 case needed -> toQty = 24, allocate 12
            assertThat(result.unpackInstructions().get(0).quantity()).isEqualTo(1);
            assertThat(caseInv.getAllocatedQty()).isEqualTo(1);
        }
    }

    // --- completeUnpackInstruction ---

    @Nested
    @DisplayName("completeUnpackInstruction")
    class CompleteUnpackTests {

        @Test
        @DisplayName("正常系: ばらし完了")
        void completeUnpack_success() {
            setUpSecurityContext(10L);

            UnpackInstruction unpack = UnpackInstruction.builder()
                    .outboundSlipId(1L)
                    .locationId(50L)
                    .productId(10L)
                    .fromUnitType("CASE")
                    .fromQty(1)
                    .toUnitType("PIECE")
                    .toQty(24)
                    .status("INSTRUCTED")
                    .warehouseId(1L)
                    .build();
            setField(unpack, "id", 500L);

            Product product = createProduct(10L, "PRD-0001", 24, 6);
            Location location = new Location();
            setField(location, "id", 50L);
            location.setLocationCode("A-01-01");
            location.setLocationName("棚A-01-01");

            Inventory sourceInv = createInventory(200L, 1L, 50L, 10L, "CASE", 5, 1);

            when(unpackInstructionRepository.findById(500L)).thenReturn(Optional.of(unpack));
            when(productService.findById(10L)).thenReturn(product);
            when(locationRepository.findById(50L)).thenReturn(Optional.of(location));
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    50L, 10L, "CASE", null, null)).thenReturn(Optional.of(sourceInv));
            when(inventoryRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(sourceInv));
            when(inventoryRepository.save(any(Inventory.class))).thenAnswer(i -> i.getArgument(0));
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    50L, 10L, "PIECE", null, null)).thenReturn(Optional.empty());
            when(allocationDetailRepository.findByOutboundSlipId(1L)).thenReturn(List.of());
            when(inventoryMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(unpackInstructionRepository.save(any(UnpackInstruction.class))).thenAnswer(i -> i.getArgument(0));

            UnpackCompletionInfo result = allocationService.completeUnpackInstruction(500L);

            assertThat(result.id()).isEqualTo(500L);
            assertThat(result.status()).isEqualTo("COMPLETED");
            assertThat(result.completedAt()).isNotNull();
            assertThat(result.movements()).hasSize(2);
            assertThat(unpack.getStatus()).isEqualTo("COMPLETED");

            // sourceInvのqty/allocatedQty更新確認
            assertThat(sourceInv.getQuantity()).isEqualTo(4); // 5 - 1
            assertThat(sourceInv.getAllocatedQty()).isEqualTo(0); // 1 - 1

            verify(inventoryMovementRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("既に完了済みの場合は例外")
        void completeUnpack_alreadyCompleted() {
            UnpackInstruction unpack = UnpackInstruction.builder()
                    .outboundSlipId(1L)
                    .locationId(50L)
                    .productId(10L)
                    .fromUnitType("CASE")
                    .fromQty(1)
                    .toUnitType("PIECE")
                    .toQty(24)
                    .status("COMPLETED")
                    .warehouseId(1L)
                    .build();
            setField(unpack, "id", 500L);

            when(unpackInstructionRepository.findById(500L)).thenReturn(Optional.of(unpack));

            assertThatThrownBy(() -> allocationService.completeUnpackInstruction(500L))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("既に完了済みのばらし指示です");
        }

        @Test
        @DisplayName("存在しないばらし指示はResourceNotFoundException")
        void completeUnpack_notFound() {
            when(unpackInstructionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> allocationService.completeUnpackInstruction(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("ばらし指示が見つかりません");
        }
    }

    // --- releaseAllocation ---

    @Nested
    @DisplayName("releaseAllocation")
    class ReleaseAllocationTests {

        @Test
        @DisplayName("正常系: 引当解放")
        void releaseAllocation_success() {
            setUpSecurityContext(10L);

            OutboundSlip slip = createSlip(1L, "OUT-20260320-0001", "ALLOCATED", 1L);
            OutboundSlipLine line = createLine(100L, slip, 1, 10L, "PRD-0001", 5, "PIECE", "ALLOCATED");
            slip.getLines().add(line);

            AllocationDetail detail = AllocationDetail.builder()
                    .outboundSlipId(1L)
                    .outboundSlipLineId(100L)
                    .inventoryId(200L)
                    .locationId(50L)
                    .productId(10L)
                    .unitType("PIECE")
                    .allocatedQty(5)
                    .warehouseId(1L)
                    .build();

            Inventory inv = createInventory(200L, 1L, 50L, 10L, "PIECE", 20, 5);

            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));
            when(allocationDetailRepository.findByOutboundSlipId(1L)).thenReturn(List.of(detail));
            when(inventoryRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(inv));
            when(inventoryRepository.save(any(Inventory.class))).thenAnswer(i -> i.getArgument(0));
            when(outboundSlipRepository.save(any(OutboundSlip.class))).thenAnswer(i -> i.getArgument(0));

            ReleaseAllocationRequest request = new ReleaseAllocationRequest()
                    .outboundSlipIds(List.of(1L));

            AllocationReleaseInfo result = allocationService.releaseAllocation(request);

            assertThat(result.releasedCount()).isEqualTo(1);
            assertThat(result.releasedSlips()).hasSize(1);
            assertThat(result.releasedSlips().get(0).previousStatus()).isEqualTo("ALLOCATED");
            assertThat(result.releasedSlips().get(0).newStatus()).isEqualTo("ORDERED");

            assertThat(inv.getAllocatedQty()).isEqualTo(0);
            assertThat(slip.getStatus()).isEqualTo("ORDERED");
            assertThat(line.getLineStatus()).isEqualTo("ORDERED");

            verify(allocationDetailRepository).deleteByOutboundSlipId(1L);
            verify(unpackInstructionRepository).deleteInstructedByOutboundSlipId(1L);
        }

        @Test
        @DisplayName("ステータス不正: ORDERED伝票は解放不可")
        void releaseAllocation_invalidStatus() {
            OutboundSlip slip = createSlip(1L, "OUT-20260320-0001", "ORDERED", 1L);

            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));

            ReleaseAllocationRequest request = new ReleaseAllocationRequest()
                    .outboundSlipIds(List.of(1L));

            assertThatThrownBy(() -> allocationService.releaseAllocation(request))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("引当解放可能なステータスではありません");
        }

        @Test
        @DisplayName("存在しない伝票はResourceNotFoundException")
        void releaseAllocation_slipNotFound() {
            when(outboundSlipRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            ReleaseAllocationRequest request = new ReleaseAllocationRequest()
                    .outboundSlipIds(List.of(999L));

            assertThatThrownBy(() -> allocationService.releaseAllocation(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("出荷伝票が見つかりません");
        }
    }

    // --- getConversionRate ---

    @Nested
    @DisplayName("getConversionRate")
    class ConversionRateTests {

        @Test
        @DisplayName("CASE→PIECE = caseQuantity")
        void caseToPiece() {
            Product product = createProduct(10L, "PRD-0001", 24, 6);
            assertThat(allocationService.getConversionRate("CASE", "PIECE", product)).isEqualTo(24);
        }

        @Test
        @DisplayName("BALL→PIECE = ballQuantity")
        void ballToPiece() {
            Product product = createProduct(10L, "PRD-0001", 24, 6);
            assertThat(allocationService.getConversionRate("BALL", "PIECE", product)).isEqualTo(6);
        }

        @Test
        @DisplayName("CASE→BALL = caseQuantity / ballQuantity")
        void caseToBall() {
            Product product = createProduct(10L, "PRD-0001", 24, 6);
            assertThat(allocationService.getConversionRate("CASE", "BALL", product)).isEqualTo(4);
        }

        @Test
        @DisplayName("同一荷姿は0")
        void sameUnitType() {
            Product product = createProduct(10L, "PRD-0001", 24, 6);
            assertThat(allocationService.getConversionRate("PIECE", "PIECE", product)).isEqualTo(0);
        }

        @Test
        @DisplayName("下位→上位は0")
        void pieceToCase() {
            Product product = createProduct(10L, "PRD-0001", 24, 6);
            assertThat(allocationService.getConversionRate("PIECE", "CASE", product)).isEqualTo(0);
        }
    }

    // --- searchUnpackInstructions ---

    @Nested
    @DisplayName("searchUnpackInstructions")
    class SearchUnpackTests {

        @Test
        @DisplayName("ばらし指示一覧を返す")
        void searchUnpack_returnsPage() {
            UnpackInstruction unpack = UnpackInstruction.builder()
                    .outboundSlipId(1L)
                    .locationId(50L)
                    .productId(10L)
                    .fromUnitType("CASE")
                    .fromQty(1)
                    .toUnitType("PIECE")
                    .toQty(24)
                    .status("INSTRUCTED")
                    .warehouseId(1L)
                    .build();
            setField(unpack, "id", 500L);

            Page<UnpackInstruction> page = new PageImpl<>(List.of(unpack));
            when(unpackInstructionRepository.search(eq(1L), eq("INSTRUCTED"), any(Pageable.class)))
                    .thenReturn(page);

            Page<UnpackInstruction> result = allocationService.searchUnpackInstructions(
                    1L, "INSTRUCTED", PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
        }
    }
}
