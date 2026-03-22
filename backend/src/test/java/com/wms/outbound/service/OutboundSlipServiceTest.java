package com.wms.outbound.service;

import com.wms.generated.model.CancelOutboundRequest;
import com.wms.generated.model.CreateOutboundLineRequest;
import com.wms.generated.model.CreateOutboundSlipRequest;
import com.wms.generated.model.OutboundLineStatus;
import com.wms.generated.model.OutboundSlipStatus;
import com.wms.generated.model.OutboundSlipType;
import com.wms.generated.model.UnitType;
import com.wms.master.entity.Partner;
import com.wms.master.entity.PartnerType;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.PartnerService;
import com.wms.master.service.ProductService;
import com.wms.master.service.WarehouseService;
import com.wms.outbound.entity.OutboundSlip;
import com.wms.outbound.entity.OutboundSlipLine;
import com.wms.outbound.repository.OutboundSlipRepository;
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
}
