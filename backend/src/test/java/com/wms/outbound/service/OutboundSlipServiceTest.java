package com.wms.outbound.service;

import com.wms.allocation.entity.AllocationDetail;
import com.wms.allocation.repository.AllocationDetailRepository;
import com.wms.generated.model.CancelOutboundRequest;
import com.wms.generated.model.CreateOutboundLineRequest;
import com.wms.generated.model.CreateOutboundSlipRequest;
import com.wms.generated.model.InspectOutboundLineRequest;
import com.wms.generated.model.InspectOutboundRequest;
import com.wms.generated.model.OutboundLineStatus;
import com.wms.generated.model.OutboundSlipStatus;
import com.wms.generated.model.OutboundSlipType;
import com.wms.generated.model.ShipOutboundRequest;
import com.wms.generated.model.UnitType;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryMovement;
import com.wms.inventory.repository.InventoryMovementRepository;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.master.entity.Partner;
import com.wms.master.entity.PartnerType;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.PartnerService;
import com.wms.master.service.ProductService;
import com.wms.master.service.WarehouseService;
import com.wms.outbound.entity.OutboundSlip;
import com.wms.outbound.entity.OutboundSlipLine;
import com.wms.outbound.entity.PickingInstructionLine;
import com.wms.outbound.repository.OutboundSlipRepository;
import com.wms.outbound.repository.PickingInstructionLineRepository;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.DuplicateResourceException;
import com.wms.shared.exception.InvalidStateTransitionException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.security.WmsUserDetails;
import com.wms.shared.util.BusinessDateProvider;
import com.wms.system.entity.User;
import com.wms.system.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
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
@DisplayName("OutboundSlipService")
class OutboundSlipServiceTest {

    @Mock
    private OutboundSlipRepository outboundSlipRepository;

    @Mock
    private PickingInstructionLineRepository pickingInstructionLineRepository;

    @Mock
    private AllocationDetailRepository allocationDetailRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryMovementRepository inventoryMovementRepository;

    @Mock
    private WarehouseService warehouseService;

    @Mock
    private PartnerService partnerService;

    @Mock
    private ProductService productService;

    @Mock
    private BusinessDateProvider businessDateProvider;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OutboundSlipService outboundSlipService;

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
                List.of(new SimpleGrantedAuthority("ROLE_WAREHOUSE_STAFF")));
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Nested
    @DisplayName("search")
    class SearchTests {

        @Test
        @DisplayName("倉庫存在チェック後に検索結果を返す")
        void search_returnsPage() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());

            OutboundSlip slip = OutboundSlip.builder()
                    .slipNumber("OUT-20260320-0001")
                    .status("ORDERED")
                    .build();
            Page<OutboundSlip> page = new PageImpl<>(List.of(slip));
            when(outboundSlipRepository.search(
                    eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(page);

            Page<OutboundSlip> result = outboundSlipService.search(
                    1L, null, null, null, null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getSlipNumber()).isEqualTo("OUT-20260320-0001");
            verify(warehouseService).findById(1L);
        }

        @Test
        @DisplayName("倉庫が存在しない場合ResourceNotFoundExceptionをスローする")
        void search_warehouseNotFound_throws() {
            when(warehouseService.findById(999L))
                    .thenThrow(new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "倉庫 が見つかりません (id=999)"));

            assertThatThrownBy(() -> outboundSlipService.search(
                    999L, null, null, null, null, null, PageRequest.of(0, 20)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("倉庫")
                    .extracting("errorCode").isEqualTo("WAREHOUSE_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("findByIdWithLines")
    class FindByIdWithLinesTests {

        @Test
        @DisplayName("存在するIDで伝票+明細を返す")
        void findByIdWithLines_exists() {
            OutboundSlip slip = OutboundSlip.builder()
                    .slipNumber("OUT-20260320-0001")
                    .status("ORDERED")
                    .build();
            when(outboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            OutboundSlip result = outboundSlipService.findByIdWithLines(1L);

            assertThat(result.getSlipNumber()).isEqualTo("OUT-20260320-0001");
        }

        @Test
        @DisplayName("存在しないIDでOUTBOUND_SLIP_NOT_FOUNDをスローする")
        void findByIdWithLines_notExists_throws() {
            when(outboundSlipRepository.findByIdWithLines(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> outboundSlipService.findByIdWithLines(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("OUTBOUND_SLIP_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        private static final LocalDate TODAY = LocalDate.of(2026, 3, 22);

        private Warehouse createWarehouse() {
            Warehouse w = new Warehouse();
            w.setWarehouseCode("WH-001");
            w.setWarehouseName("東京DC");
            setField(w, "id", 1L);
            return w;
        }

        private Partner createPartner(PartnerType type) {
            Partner p = new Partner();
            p.setPartnerCode("CUS-0001");
            p.setPartnerName("顧客A");
            p.setPartnerType(type);
            setField(p, "id", 5L);
            return p;
        }

        private Product createProduct(Long id, String code, boolean active, boolean shipmentStop) {
            Product p = new Product();
            p.setProductCode(code);
            p.setProductName("商品" + code);
            p.setShipmentStopFlag(shipmentStop);
            setField(p, "id", id);
            setField(p, "isActive", active);
            return p;
        }

        private CreateOutboundSlipRequest buildRequest() {
            return new CreateOutboundSlipRequest()
                    .warehouseId(1L)
                    .partnerId(5L)
                    .plannedDate(TODAY)
                    .slipType(OutboundSlipType.NORMAL)
                    .note("テスト備考")
                    .lines(List.of(
                            new CreateOutboundLineRequest()
                                    .productId(10L)
                                    .unitType(UnitType.CASE)
                                    .orderedQty(100)
                    ));
        }

        @Test
        @DisplayName("正常系: 出荷伝票を作成できる")
        void create_success() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L)).thenReturn(createWarehouse());
            when(partnerService.findById(5L)).thenReturn(createPartner(PartnerType.CUSTOMER));
            when(productService.findById(10L)).thenReturn(createProduct(10L, "PRD-0001", true, false));
            when(outboundSlipRepository.findMaxSequenceByDate("20260322")).thenReturn(0);
            when(outboundSlipRepository.save(any(OutboundSlip.class))).thenAnswer(inv -> {
                OutboundSlip s = inv.getArgument(0);
                setField(s, "id", 1L);
                return s;
            });

            OutboundSlip result = outboundSlipService.create(buildRequest());

            assertThat(result.getSlipNumber()).isEqualTo("OUT-20260322-0001");
            assertThat(result.getStatus()).isEqualTo("ORDERED");
            assertThat(result.getWarehouseCode()).isEqualTo("WH-001");
            assertThat(result.getPartnerCode()).isEqualTo("CUS-0001");
            assertThat(result.getLines()).hasSize(1);
            assertThat(result.getLines().get(0).getLineStatus()).isEqualTo("ORDERED");
            assertThat(result.getLines().get(0).getProductCode()).isEqualTo("PRD-0001");
            assertThat(result.getLines().get(0).getLineNo()).isEqualTo(1);

            verify(outboundSlipRepository).save(any(OutboundSlip.class));
        }

        @Test
        @DisplayName("出荷予定日が過去日の場合BusinessRuleViolationExceptionをスローする")
        void create_plannedDateTooEarly_throws() {
            when(businessDateProvider.today()).thenReturn(TODAY);

            CreateOutboundSlipRequest request = buildRequest()
                    .plannedDate(TODAY.minusDays(1));

            assertThatThrownBy(() -> outboundSlipService.create(request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("PLANNED_DATE_TOO_EARLY");
        }

        @Test
        @DisplayName("同一商品が複数行にある場合DuplicateResourceExceptionをスローする")
        void create_duplicateProduct_throws() {
            when(businessDateProvider.today()).thenReturn(TODAY);

            CreateOutboundSlipRequest request = buildRequest()
                    .lines(List.of(
                            new CreateOutboundLineRequest().productId(10L).unitType(UnitType.CASE).orderedQty(100),
                            new CreateOutboundLineRequest().productId(10L).unitType(UnitType.PIECE).orderedQty(50)
                    ));

            assertThatThrownBy(() -> outboundSlipService.create(request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .extracting("errorCode").isEqualTo("DUPLICATE_PRODUCT_IN_LINES");
        }

        @Test
        @DisplayName("出荷先がCUSTOMERでもBOTHでもない場合BusinessRuleViolationExceptionをスローする")
        void create_partnerNotCustomer_throws() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L)).thenReturn(createWarehouse());
            when(partnerService.findById(5L)).thenReturn(createPartner(PartnerType.SUPPLIER));

            assertThatThrownBy(() -> outboundSlipService.create(buildRequest()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("OUTBOUND_PARTNER_NOT_CUSTOMER");
        }

        @Test
        @DisplayName("出荷禁止商品の場合BusinessRuleViolationExceptionをスローする")
        void create_shipmentStopped_throws() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L)).thenReturn(createWarehouse());
            when(partnerService.findById(5L)).thenReturn(createPartner(PartnerType.CUSTOMER));
            when(productService.findById(10L)).thenReturn(createProduct(10L, "PRD-0001", true, true));

            assertThatThrownBy(() -> outboundSlipService.create(buildRequest()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("OUTBOUND_PRODUCT_SHIPMENT_STOPPED");
        }

        @Test
        @DisplayName("伝票番号衝突時にリトライして成功する")
        void create_slipNumberCollision_retrySuccess() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L)).thenReturn(createWarehouse());
            when(partnerService.findById(5L)).thenReturn(createPartner(PartnerType.CUSTOMER));
            when(productService.findById(10L)).thenReturn(createProduct(10L, "PRD-0001", true, false));
            when(outboundSlipRepository.findMaxSequenceByDate("20260322"))
                    .thenReturn(0)
                    .thenReturn(1);
            when(outboundSlipRepository.save(any(OutboundSlip.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"))
                    .thenAnswer(inv -> {
                        OutboundSlip s = inv.getArgument(0);
                        setField(s, "id", 1L);
                        return s;
                    });

            OutboundSlip result = outboundSlipService.create(buildRequest());

            assertThat(result.getSlipNumber()).isEqualTo("OUT-20260322-0002");
            assertThat(result.getLines()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("ORDEREDステータスの伝票を削除できる")
        void delete_ordered_success() {
            OutboundSlip slip = OutboundSlip.builder()
                    .slipNumber("OUT-20260322-0001")
                    .status(OutboundSlipStatus.ORDERED.getValue())
                    .build();
            setField(slip, "id", 1L);
            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));

            outboundSlipService.delete(1L);

            verify(outboundSlipRepository).delete(slip);
        }

        @Test
        @DisplayName("存在しないIDで削除するとResourceNotFoundExceptionをスローする")
        void delete_notFound_throws() {
            when(outboundSlipRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> outboundSlipService.delete(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("OUTBOUND_SLIP_NOT_FOUND");
        }

        @Test
        @DisplayName("ORDERED以外のステータスの伝票を削除するとInvalidStateTransitionExceptionをスローする")
        void delete_notOrdered_throws() {
            OutboundSlip slip = OutboundSlip.builder()
                    .slipNumber("OUT-20260322-0001")
                    .status(OutboundSlipStatus.ALLOCATED.getValue())
                    .build();
            setField(slip, "id", 1L);
            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));

            assertThatThrownBy(() -> outboundSlipService.delete(1L))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("OUTBOUND_INVALID_STATUS");

            verify(outboundSlipRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("cancel")
    class CancelTests {

        @AfterEach
        void clearSecurityContext() {
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("ORDEREDステータスの伝票をキャンセルすると明細もCANCELLEDになる")
        void cancel_ordered_success() {
            setUpSecurityContext(10L);
            OutboundSlipLine orderedLine = OutboundSlipLine.builder()
                    .lineNo(1)
                    .productId(100L)
                    .productCode("PRD-0001")
                    .productName("商品A")
                    .unitType("CASE")
                    .orderedQty(10)
                    .shippedQty(0)
                    .lineStatus(OutboundLineStatus.ORDERED.getValue())
                    .build();
            setField(orderedLine, "id", 11L);
            List<OutboundSlipLine> lines = new ArrayList<>();
            lines.add(orderedLine);
            OutboundSlip slip = OutboundSlip.builder()
                    .slipNumber("OUT-20260322-0001")
                    .status(OutboundSlipStatus.ORDERED.getValue())
                    .warehouseId(1L)
                    .lines(lines)
                    .build();
            setField(slip, "id", 1L);
            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));
            when(outboundSlipRepository.save(any(OutboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            CancelOutboundRequest request = new CancelOutboundRequest().reason("テストキャンセル");
            OutboundSlip result = outboundSlipService.cancel(1L, request);

            assertThat(result.getStatus()).isEqualTo(OutboundSlipStatus.CANCELLED.getValue());
            assertThat(result.getCancelledBy()).isEqualTo(10L);
            assertThat(result.getCancelledAt()).isNotNull();
            assertThat(result.getCancelReason()).isEqualTo("テストキャンセル");
            assertThat(result.getLines()).allSatisfy(line ->
                    assertThat(line.getLineStatus()).isEqualTo(OutboundLineStatus.CANCELLED.getValue()));
            verify(outboundSlipRepository).save(slip);
        }

        @Test
        @DisplayName("存在しないIDでキャンセルするとResourceNotFoundExceptionをスローする")
        void cancel_notFound_throws() {
            setUpSecurityContext(10L);
            when(outboundSlipRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> outboundSlipService.cancel(999L, new CancelOutboundRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("OUTBOUND_SLIP_NOT_FOUND");
        }

        @Test
        @DisplayName("キャンセル不可ステータスの場合InvalidStateTransitionExceptionをスローする")
        void cancel_shippedStatus_throws() {
            setUpSecurityContext(10L);
            OutboundSlip slip = OutboundSlip.builder()
                    .slipNumber("OUT-20260322-0001")
                    .status(OutboundSlipStatus.SHIPPED.getValue())
                    .build();
            setField(slip, "id", 1L);
            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));

            assertThatThrownBy(() -> outboundSlipService.cancel(1L, new CancelOutboundRequest()))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("OUTBOUND_INVALID_STATUS");

            verify(outboundSlipRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("inspect")
    class InspectTests {

        private OutboundSlip createPickingCompletedSlip() {
            OutboundSlipLine line1 = OutboundSlipLine.builder()
                    .lineNo(1)
                    .productId(100L)
                    .productCode("PRD-0001")
                    .productName("商品A")
                    .unitType("CASE")
                    .orderedQty(10)
                    .shippedQty(0)
                    .lineStatus(OutboundLineStatus.PICKING_COMPLETED.getValue())
                    .build();
            setField(line1, "id", 11L);

            OutboundSlipLine line2 = OutboundSlipLine.builder()
                    .lineNo(2)
                    .productId(200L)
                    .productCode("PRD-0002")
                    .productName("商品B")
                    .unitType("PIECE")
                    .orderedQty(20)
                    .shippedQty(0)
                    .lineStatus(OutboundLineStatus.PICKING_COMPLETED.getValue())
                    .build();
            setField(line2, "id", 12L);

            List<OutboundSlipLine> lines = new ArrayList<>();
            lines.add(line1);
            lines.add(line2);

            OutboundSlip slip = OutboundSlip.builder()
                    .slipNumber("OUT-20260322-0001")
                    .status(OutboundSlipStatus.PICKING_COMPLETED.getValue())
                    .warehouseId(1L)
                    .lines(lines)
                    .build();
            setField(slip, "id", 1L);
            return slip;
        }

        @Test
        @DisplayName("正常系: PICKING_COMPLETED の伝票に検品数量を登録できる")
        void inspect_pickingCompleted_success() {
            OutboundSlip slip = createPickingCompletedSlip();
            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));
            when(outboundSlipRepository.save(any(OutboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            InspectOutboundRequest request = new InspectOutboundRequest(List.of(
                    new InspectOutboundLineRequest(11L, 10),
                    new InspectOutboundLineRequest(12L, 18)
            ));

            OutboundSlip result = outboundSlipService.inspect(1L, request);

            assertThat(result.getStatus()).isEqualTo(OutboundSlipStatus.INSPECTING.getValue());
            assertThat(result.getLines().get(0).getInspectedQty()).isEqualTo(10);
            assertThat(result.getLines().get(1).getInspectedQty()).isEqualTo(18);
            verify(outboundSlipRepository).save(slip);
        }

        @Test
        @DisplayName("INSPECTING ステータスでも再入力可能（上書き）")
        void inspect_inspecting_overwrite_success() {
            OutboundSlip slip = createPickingCompletedSlip();
            slip.setStatus(OutboundSlipStatus.INSPECTING.getValue());
            slip.getLines().get(0).setInspectedQty(5); // 前回の値
            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));
            when(outboundSlipRepository.save(any(OutboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            InspectOutboundRequest request = new InspectOutboundRequest(List.of(
                    new InspectOutboundLineRequest(11L, 8)
            ));

            OutboundSlip result = outboundSlipService.inspect(1L, request);

            assertThat(result.getStatus()).isEqualTo(OutboundSlipStatus.INSPECTING.getValue());
            assertThat(result.getLines().get(0).getInspectedQty()).isEqualTo(8);
        }

        @Test
        @DisplayName("部分的な検品数量入力が可能（未指定明細は更新しない）")
        void inspect_partialLines_success() {
            OutboundSlip slip = createPickingCompletedSlip();
            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));
            when(outboundSlipRepository.save(any(OutboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            InspectOutboundRequest request = new InspectOutboundRequest(List.of(
                    new InspectOutboundLineRequest(11L, 10)
            ));

            OutboundSlip result = outboundSlipService.inspect(1L, request);

            assertThat(result.getLines().get(0).getInspectedQty()).isEqualTo(10);
            assertThat(result.getLines().get(1).getInspectedQty()).isNull();
        }

        @Test
        @DisplayName("伝票が存在しない場合ResourceNotFoundExceptionをスローする")
        void inspect_notFound_throws() {
            when(outboundSlipRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            InspectOutboundRequest request = new InspectOutboundRequest(List.of(
                    new InspectOutboundLineRequest(11L, 10)
            ));

            assertThatThrownBy(() -> outboundSlipService.inspect(999L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("OUTBOUND_SLIP_NOT_FOUND");
        }

        @Test
        @DisplayName("ORDERED ステータスでは検品不可")
        void inspect_orderedStatus_throws() {
            OutboundSlip slip = createPickingCompletedSlip();
            slip.setStatus(OutboundSlipStatus.ORDERED.getValue());
            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));

            InspectOutboundRequest request = new InspectOutboundRequest(List.of(
                    new InspectOutboundLineRequest(11L, 10)
            ));

            assertThatThrownBy(() -> outboundSlipService.inspect(1L, request))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("OUTBOUND_INVALID_STATUS");
        }

        @Test
        @DisplayName("SHIPPED ステータスでは検品不可")
        void inspect_shippedStatus_throws() {
            OutboundSlip slip = createPickingCompletedSlip();
            slip.setStatus(OutboundSlipStatus.SHIPPED.getValue());
            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));

            InspectOutboundRequest request = new InspectOutboundRequest(List.of(
                    new InspectOutboundLineRequest(11L, 10)
            ));

            assertThatThrownBy(() -> outboundSlipService.inspect(1L, request))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("OUTBOUND_INVALID_STATUS");
        }

        @Test
        @DisplayName("重複lineIdの場合BusinessRuleViolationExceptionをスローする")
        void inspect_duplicateLineId_throws() {
            OutboundSlip slip = createPickingCompletedSlip();
            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));

            InspectOutboundRequest request = new InspectOutboundRequest(List.of(
                    new InspectOutboundLineRequest(11L, 10),
                    new InspectOutboundLineRequest(11L, 5)
            ));

            assertThatThrownBy(() -> outboundSlipService.inspect(1L, request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("当該伝票に属さないlineIdの場合BusinessRuleViolationExceptionをスローする")
        void inspect_invalidLineId_throws() {
            OutboundSlip slip = createPickingCompletedSlip();
            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));

            InspectOutboundRequest request = new InspectOutboundRequest(List.of(
                    new InspectOutboundLineRequest(999L, 10)
            ));

            assertThatThrownBy(() -> outboundSlipService.inspect(1L, request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
        }
    }

    @Nested
    @DisplayName("ship")
    class ShipTests {

        @AfterEach
        void clearSecurityContext() {
            SecurityContextHolder.clearContext();
        }

        private OutboundSlip createInspectingSlip() {
            OutboundSlipLine line1 = OutboundSlipLine.builder()
                    .lineNo(1)
                    .productId(100L)
                    .productCode("PRD-0001")
                    .productName("商品A")
                    .unitType("CASE")
                    .orderedQty(10)
                    .inspectedQty(10)
                    .shippedQty(0)
                    .lineStatus(OutboundLineStatus.PICKING_COMPLETED.getValue())
                    .build();
            setField(line1, "id", 11L);

            OutboundSlipLine line2 = OutboundSlipLine.builder()
                    .lineNo(2)
                    .productId(200L)
                    .productCode("PRD-0002")
                    .productName("商品B")
                    .unitType("PIECE")
                    .orderedQty(20)
                    .inspectedQty(18)
                    .shippedQty(0)
                    .lineStatus(OutboundLineStatus.PICKING_COMPLETED.getValue())
                    .build();
            setField(line2, "id", 12L);

            List<OutboundSlipLine> lines = new ArrayList<>();
            lines.add(line1);
            lines.add(line2);

            OutboundSlip slip = OutboundSlip.builder()
                    .slipNumber("OUT-20260322-0001")
                    .status(OutboundSlipStatus.INSPECTING.getValue())
                    .warehouseId(1L)
                    .lines(lines)
                    .build();
            setField(slip, "id", 1L);
            return slip;
        }

        private AllocationDetail createAllocationDetail(Long id, Long slipLineId,
                Long inventoryId, Long locationId, Long productId,
                String unitType, String lotNumber, int allocatedQty) {
            AllocationDetail detail = AllocationDetail.builder()
                    .outboundSlipId(1L)
                    .outboundSlipLineId(slipLineId)
                    .inventoryId(inventoryId)
                    .locationId(locationId)
                    .productId(productId)
                    .unitType(unitType)
                    .lotNumber(lotNumber)
                    .allocatedQty(allocatedQty)
                    .warehouseId(1L)
                    .build();
            setField(detail, "id", id);
            return detail;
        }

        private Inventory createInventory(Long id, Long locationId, Long productId,
                int quantity, int allocatedQty) {
            Inventory inv = Inventory.builder()
                    .warehouseId(1L)
                    .locationId(locationId)
                    .productId(productId)
                    .unitType("CASE")
                    .quantity(quantity)
                    .allocatedQty(allocatedQty)
                    .build();
            setField(inv, "id", id);
            return inv;
        }

        private PickingInstructionLine createPickingLine(Long locationId, Long productId,
                String locationCode) {
            return PickingInstructionLine.builder()
                    .outboundSlipLineId(11L)
                    .locationId(locationId)
                    .locationCode(locationCode)
                    .productId(productId)
                    .productCode("PRD-0001")
                    .productName("商品A")
                    .unitType("CASE")
                    .qtyToPick(10)
                    .qtyPicked(10)
                    .lineNo(1)
                    .lineStatus("COMPLETED")
                    .build();
        }

        @Test
        @DisplayName("正常系: INSPECTING の伝票を出荷完了できる")
        void ship_success() {
            setUpSecurityContext(10L);
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 23));
            OutboundSlip slip = createInspectingSlip();
            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));

            // allocation_details
            List<AllocationDetail> allocations = List.of(
                    createAllocationDetail(1L, 11L, 501L, 301L, 100L, "CASE", "LOT-001", 10),
                    createAllocationDetail(2L, 12L, 502L, 302L, 200L, "PIECE", "LOT-002", 18)
            );
            when(allocationDetailRepository.findByOutboundSlipId(1L)).thenReturn(allocations);

            // picking lines
            PickingInstructionLine pickLine = createPickingLine(301L, 100L, "A-01-01");
            PickingInstructionLine pickLine2 = PickingInstructionLine.builder()
                    .outboundSlipLineId(12L)
                    .locationId(302L)
                    .locationCode("A-02-01")
                    .productId(200L)
                    .productCode("PRD-0002")
                    .productName("商品B")
                    .unitType("PIECE")
                    .qtyToPick(18)
                    .qtyPicked(18)
                    .lineNo(2)
                    .lineStatus("COMPLETED")
                    .build();
            when(pickingInstructionLineRepository.findByOutboundSlipLineIdIn(List.of(11L, 12L)))
                    .thenReturn(List.of(pickLine, pickLine2));

            // inventories
            Inventory inv1 = createInventory(501L, 301L, 100L, 50, 10);
            Inventory inv2 = createInventory(502L, 302L, 200L, 30, 18);
            when(inventoryRepository.findByIdForUpdate(501L)).thenReturn(Optional.of(inv1));
            when(inventoryRepository.findByIdForUpdate(502L)).thenReturn(Optional.of(inv2));
            when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(inventoryMovementRepository.save(any(InventoryMovement.class))).thenAnswer(inv -> inv.getArgument(0));
            when(outboundSlipRepository.save(any(OutboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            ShipOutboundRequest request = new ShipOutboundRequest(LocalDate.of(2026, 3, 22))
                    .carrier("ヤマト運輸")
                    .trackingNumber("123456789012")
                    .note("配送メモ");

            OutboundSlip result = outboundSlipService.ship(1L, request);

            assertThat(result.getStatus()).isEqualTo(OutboundSlipStatus.SHIPPED.getValue());
            assertThat(result.getCarrier()).isEqualTo("ヤマト運輸");
            assertThat(result.getTrackingNumber()).isEqualTo("123456789012");
            assertThat(result.getNote()).isEqualTo("配送メモ");
            assertThat(result.getShippedAt()).isNotNull();
            assertThat(result.getShippedBy()).isEqualTo(10L);
            assertThat(result.getLines()).allSatisfy(line -> {
                assertThat(line.getLineStatus()).isEqualTo(OutboundLineStatus.SHIPPED.getValue());
            });
            assertThat(result.getLines().get(0).getShippedQty()).isEqualTo(10);
            assertThat(result.getLines().get(1).getShippedQty()).isEqualTo(18);

            // 在庫が減算されていることを検証
            assertThat(inv1.getQuantity()).isEqualTo(40);
            assertThat(inv1.getAllocatedQty()).isEqualTo(0);
            assertThat(inv2.getQuantity()).isEqualTo(12);
            assertThat(inv2.getAllocatedQty()).isEqualTo(0);

            verify(inventoryMovementRepository, org.mockito.Mockito.times(2)).save(any(InventoryMovement.class));
        }

        @Test
        @DisplayName("伝票が存在しない場合ResourceNotFoundExceptionをスローする")
        void ship_notFound_throws() {
            setUpSecurityContext(10L);
            when(outboundSlipRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            ShipOutboundRequest request = new ShipOutboundRequest(LocalDate.of(2026, 3, 22));

            assertThatThrownBy(() -> outboundSlipService.ship(999L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("OUTBOUND_SLIP_NOT_FOUND");
        }

        @Test
        @DisplayName("ORDERED ステータスでは出荷完了不可")
        void ship_orderedStatus_throws() {
            setUpSecurityContext(10L);
            OutboundSlip slip = createInspectingSlip();
            slip.setStatus(OutboundSlipStatus.ORDERED.getValue());
            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));

            ShipOutboundRequest request = new ShipOutboundRequest(LocalDate.of(2026, 3, 22));

            assertThatThrownBy(() -> outboundSlipService.ship(1L, request))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("OUTBOUND_INVALID_STATUS");
        }

        @Test
        @DisplayName("PICKING_COMPLETED ステータスでは出荷完了不可")
        void ship_pickingCompletedStatus_throws() {
            setUpSecurityContext(10L);
            OutboundSlip slip = createInspectingSlip();
            slip.setStatus(OutboundSlipStatus.PICKING_COMPLETED.getValue());
            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));

            ShipOutboundRequest request = new ShipOutboundRequest(LocalDate.of(2026, 3, 22));

            assertThatThrownBy(() -> outboundSlipService.ship(1L, request))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("OUTBOUND_INVALID_STATUS");
        }

        @Test
        @DisplayName("在庫不足の場合BusinessRuleViolationExceptionをスローする")
        void ship_insufficientInventory_throws() {
            setUpSecurityContext(10L);
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 23));
            OutboundSlip slip = createInspectingSlip();
            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));

            List<AllocationDetail> allocations = List.of(
                    createAllocationDetail(1L, 11L, 501L, 301L, 100L, "CASE", "LOT-001", 10)
            );
            when(allocationDetailRepository.findByOutboundSlipId(1L)).thenReturn(allocations);

            PickingInstructionLine pickLine = createPickingLine(301L, 100L, "A-01-01");
            when(pickingInstructionLineRepository.findByOutboundSlipLineIdIn(List.of(11L, 12L)))
                    .thenReturn(List.of(pickLine));

            // 在庫不足: quantity=5 < required=10
            Inventory inv = createInventory(501L, 301L, 100L, 5, 10);
            when(inventoryRepository.findByIdForUpdate(501L)).thenReturn(Optional.of(inv));

            ShipOutboundRequest request = new ShipOutboundRequest(LocalDate.of(2026, 3, 22));

            assertThatThrownBy(() -> outboundSlipService.ship(1L, request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("INVENTORY_INSUFFICIENT");
        }

        @Test
        @DisplayName("carrier/trackingNumber/noteがnullでも出荷完了できる")
        void ship_optionalFieldsNull_success() {
            setUpSecurityContext(10L);
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 23));
            OutboundSlip slip = createInspectingSlip();
            slip.setNote("元の備考");
            when(outboundSlipRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slip));

            when(allocationDetailRepository.findByOutboundSlipId(1L)).thenReturn(List.of());
            when(pickingInstructionLineRepository.findByOutboundSlipLineIdIn(List.of(11L, 12L)))
                    .thenReturn(List.of());
            when(outboundSlipRepository.save(any(OutboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            ShipOutboundRequest request = new ShipOutboundRequest(LocalDate.of(2026, 3, 22));

            OutboundSlip result = outboundSlipService.ship(1L, request);

            assertThat(result.getStatus()).isEqualTo(OutboundSlipStatus.SHIPPED.getValue());
            assertThat(result.getCarrier()).isNull();
            assertThat(result.getTrackingNumber()).isNull();
            assertThat(result.getNote()).isEqualTo("元の備考"); // noteがnullなら元の値を保持
        }
    }
}
