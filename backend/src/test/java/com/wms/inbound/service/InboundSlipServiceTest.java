package com.wms.inbound.service;

import com.wms.generated.model.CreateInboundLineRequest;
import com.wms.generated.model.CreateInboundSlipRequest;
import com.wms.generated.model.InboundLineStatus;
import com.wms.generated.model.InboundSlipStatus;
import com.wms.generated.model.InboundSlipType;
import com.wms.generated.model.InspectInboundRequest;
import com.wms.generated.model.InspectLineRequest;
import com.wms.generated.model.StoreInboundRequest;
import com.wms.generated.model.StoreLineRequest;
import com.wms.generated.model.UnitType;
import com.wms.inbound.entity.InboundSlip;
import com.wms.inbound.entity.InboundSlipLine;
import com.wms.inbound.repository.InboundSlipLineRepository;
import com.wms.inbound.repository.InboundSlipRepository;
import com.wms.inventory.service.InventoryService;
import com.wms.master.entity.Area;
import com.wms.master.entity.Location;
import com.wms.master.entity.Partner;
import com.wms.master.entity.PartnerType;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.AreaService;
import com.wms.master.service.LocationService;
import com.wms.master.service.PartnerService;
import com.wms.master.service.ProductService;
import com.wms.master.service.WarehouseService;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.DuplicateResourceException;
import com.wms.shared.exception.InvalidStateTransitionException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.security.WmsUserDetails;
import com.wms.shared.util.BusinessDateProvider;
import org.springframework.dao.DataIntegrityViolationException;
import com.wms.system.service.UserService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InboundSlipService")
class InboundSlipServiceTest {

    @Mock
    private InboundSlipRepository inboundSlipRepository;

    @Mock
    private InboundSlipLineRepository inboundSlipLineRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private WarehouseService warehouseService;

    @Mock
    private PartnerService partnerService;

    @Mock
    private ProductService productService;

    @Mock
    private LocationService locationService;

    @Mock
    private AreaService areaService;

    @Mock
    private BusinessDateProvider businessDateProvider;

    @Mock
    private UserService userService;

    @InjectMocks
    private InboundSlipService inboundSlipService;

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

            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260320-0001")
                    .status("PLANNED")
                    .build();
            Page<InboundSlip> page = new PageImpl<>(List.of(slip));
            when(inboundSlipRepository.search(
                    eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(page);

            Page<InboundSlip> result = inboundSlipService.search(
                    1L, null, null, null, null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getSlipNumber()).isEqualTo("INB-20260320-0001");
            verify(warehouseService).findById(1L);
        }

        @Test
        @DisplayName("倉庫が存在しない場合ResourceNotFoundExceptionをスローする")
        void search_warehouseNotFound_throws() {
            when(warehouseService.findById(999L))
                    .thenThrow(new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "倉庫 が見つかりません (id=999)"));

            assertThatThrownBy(() -> inboundSlipService.search(
                    999L, null, null, null, null, null, PageRequest.of(0, 20)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("倉庫")
                    .extracting("errorCode").isEqualTo("WAREHOUSE_NOT_FOUND");
        }

        @Test
        @DisplayName("ステータスフィルタ付きで検索できる")
        void search_withStatusFilter() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            when(inboundSlipRepository.search(
                    eq(1L), any(), eq(List.of("PLANNED", "CONFIRMED")), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<InboundSlip> result = inboundSlipService.search(
                    1L, null, List.of("PLANNED", "CONFIRMED"), null, null, null,
                    PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("日付範囲フィルタ付きで検索できる")
        void search_withDateRange() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            LocalDate from = LocalDate.of(2026, 3, 1);
            LocalDate to = LocalDate.of(2026, 3, 31);
            when(inboundSlipRepository.search(
                    eq(1L), any(), any(), eq(from), eq(to), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<InboundSlip> result = inboundSlipService.search(
                    1L, null, null, from, to, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("仕入先IDフィルタ付きで検索できる")
        void search_withPartnerId() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            when(inboundSlipRepository.search(
                    eq(1L), any(), any(), any(), any(), eq(5L), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<InboundSlip> result = inboundSlipService.search(
                    1L, null, null, null, null, 5L, PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("伝票番号フィルタ付きで検索できる（LikeEscapeUtil適用）")
        void search_withSlipNumber() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            when(inboundSlipRepository.search(
                    eq(1L), eq("INB-2026"), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<InboundSlip> result = inboundSlipService.search(
                    1L, "INB-2026", null, null, null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findById")
    class FindByIdTests {

        @Test
        @DisplayName("存在するIDで伝票を返す")
        void findById_exists_returnsSlip() {
            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260320-0001")
                    .status("PLANNED")
                    .build();
            when(inboundSlipRepository.findById(1L)).thenReturn(Optional.of(slip));

            InboundSlip result = inboundSlipService.findById(1L);

            assertThat(result.getSlipNumber()).isEqualTo("INB-20260320-0001");
        }

        @Test
        @DisplayName("存在しないIDでINBOUND_SLIP_NOT_FOUNDをスローする")
        void findById_notExists_throws() {
            when(inboundSlipRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inboundSlipService.findById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("入荷伝票")
                    .extracting("errorCode").isEqualTo("INBOUND_SLIP_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("findByIdWithLines")
    class FindByIdWithLinesTests {

        @Test
        @DisplayName("存在するIDで伝票+明細を返す")
        void findByIdWithLines_exists() {
            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260320-0001")
                    .status("PLANNED")
                    .build();
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            InboundSlip result = inboundSlipService.findByIdWithLines(1L);

            assertThat(result.getSlipNumber()).isEqualTo("INB-20260320-0001");
        }

        @Test
        @DisplayName("存在しないIDでINBOUND_SLIP_NOT_FOUNDをスローする")
        void findByIdWithLines_notExists_throws() {
            when(inboundSlipRepository.findByIdWithLines(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inboundSlipService.findByIdWithLines(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_SLIP_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("countLinesBySlipId")
    class CountLinesTests {

        @Test
        @DisplayName("明細件数を返す")
        void countLines_returnsCount() {
            when(inboundSlipLineRepository.countByInboundSlipId(1L)).thenReturn(3L);

            assertThat(inboundSlipService.countLinesBySlipId(1L)).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("resolveUserName")
    class ResolveUserNameTests {

        @Test
        @DisplayName("UserService経由でフルネームを返す")
        void resolveUserName_delegatesToUserService() {
            when(userService.getUserFullName(10L)).thenReturn("山田 太郎");

            assertThat(inboundSlipService.resolveUserName(10L)).isEqualTo("山田 太郎");
            verify(userService).getUserFullName(10L);
        }

        @Test
        @DisplayName("UserServiceがnullを返す場合はnullを返す")
        void resolveUserName_returnsNull() {
            when(userService.getUserFullName(999L)).thenReturn(null);

            assertThat(inboundSlipService.resolveUserName(999L)).isNull();
        }

        @Test
        @DisplayName("nullのユーザーIDを委譲する")
        void resolveUserName_null() {
            when(userService.getUserFullName(null)).thenReturn(null);

            assertThat(inboundSlipService.resolveUserName(null)).isNull();
            verify(userService).getUserFullName(null);
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
            p.setPartnerCode("SUP-0001");
            p.setPartnerName("ABC商事");
            p.setPartnerType(type);
            setField(p, "id", 5L);
            return p;
        }

        private Product createProduct(Long id, String code, boolean active,
                                       boolean lotManage, boolean expiryManage) {
            Product p = new Product();
            p.setProductCode(code);
            p.setProductName("商品" + code);
            p.setLotManageFlag(lotManage);
            p.setExpiryManageFlag(expiryManage);
            setField(p, "id", id);
            setField(p, "isActive", active);
            return p;
        }

        private CreateInboundSlipRequest buildRequest() {
            return new CreateInboundSlipRequest()
                    .warehouseId(1L)
                    .partnerId(5L)
                    .plannedDate(TODAY)
                    .slipType(InboundSlipType.NORMAL)
                    .note("テスト備考")
                    .lines(List.of(
                            new CreateInboundLineRequest()
                                    .productId(10L)
                                    .unitType(UnitType.CASE)
                                    .plannedQty(100)
                    ));
        }

        @Test
        @DisplayName("正常系: 入荷伝票を作成できる")
        void create_success() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L)).thenReturn(createWarehouse());
            when(partnerService.findById(5L)).thenReturn(createPartner(PartnerType.SUPPLIER));
            when(productService.findById(10L)).thenReturn(createProduct(10L, "PRD-0001", true, false, false));
            when(inboundSlipRepository.findMaxSequenceByDate("20260322")).thenReturn(0);
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> {
                InboundSlip s = inv.getArgument(0);
                setField(s, "id", 1L);
                return s;
            });

            InboundSlip result = inboundSlipService.create(buildRequest());

            assertThat(result.getSlipNumber()).isEqualTo("INB-20260322-0001");
            assertThat(result.getStatus()).isEqualTo("PLANNED");
            assertThat(result.getWarehouseCode()).isEqualTo("WH-001");
            assertThat(result.getPartnerCode()).isEqualTo("SUP-0001");
            assertThat(result.getLines()).hasSize(1);
            assertThat(result.getLines().get(0).getLineStatus()).isEqualTo("PENDING");
            assertThat(result.getLines().get(0).getProductCode()).isEqualTo("PRD-0001");
            assertThat(result.getLines().get(0).getLineNo()).isEqualTo(1);

            verify(inboundSlipRepository).save(any(InboundSlip.class));
        }

        @Test
        @DisplayName("伝票番号が連番で生成される")
        void create_slipNumberSequence() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L)).thenReturn(createWarehouse());
            when(partnerService.findById(5L)).thenReturn(createPartner(PartnerType.SUPPLIER));
            when(productService.findById(10L)).thenReturn(createProduct(10L, "PRD-0001", true, false, false));
            when(inboundSlipRepository.findMaxSequenceByDate("20260322")).thenReturn(5);
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> {
                InboundSlip s = inv.getArgument(0);
                setField(s, "id", 6L);
                return s;
            });

            InboundSlip result = inboundSlipService.create(buildRequest());

            assertThat(result.getSlipNumber()).isEqualTo("INB-20260322-0006");
        }

        @Test
        @DisplayName("伝票番号衝突時にリトライして成功する")
        void create_slipNumberCollision_retrySuccess() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L)).thenReturn(createWarehouse());
            when(partnerService.findById(5L)).thenReturn(createPartner(PartnerType.SUPPLIER));
            when(productService.findById(10L)).thenReturn(createProduct(10L, "PRD-0001", true, false, false));
            // First call returns 0, save throws collision; second call returns 1 for retry
            when(inboundSlipRepository.findMaxSequenceByDate("20260322"))
                    .thenReturn(0)
                    .thenReturn(1);
            when(inboundSlipRepository.save(any(InboundSlip.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"))
                    .thenAnswer(inv -> {
                        InboundSlip s = inv.getArgument(0);
                        setField(s, "id", 1L);
                        return s;
                    });

            InboundSlip result = inboundSlipService.create(buildRequest());

            assertThat(result.getSlipNumber()).isEqualTo("INB-20260322-0002");
            assertThat(result.getLines()).hasSize(1);
        }

        @Test
        @DisplayName("倉庫が存在しない場合ResourceNotFoundExceptionをスローする")
        void create_warehouseNotFound_throws() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L))
                    .thenThrow(new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "倉庫が見つかりません"));

            assertThatThrownBy(() -> inboundSlipService.create(buildRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("WAREHOUSE_NOT_FOUND");
        }

        @Test
        @DisplayName("仕入先が存在しない場合ResourceNotFoundExceptionをスローする")
        void create_partnerNotFound_throws() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L)).thenReturn(createWarehouse());
            when(partnerService.findById(5L))
                    .thenThrow(new ResourceNotFoundException("PARTNER_NOT_FOUND", "取引先が見つかりません"));

            assertThatThrownBy(() -> inboundSlipService.create(buildRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("PARTNER_NOT_FOUND");
        }

        @Test
        @DisplayName("仕入先がCUSTOMERの場合BusinessRuleViolationExceptionをスローする")
        void create_partnerNotSupplier_throws() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L)).thenReturn(createWarehouse());
            when(partnerService.findById(5L)).thenReturn(createPartner(PartnerType.CUSTOMER));

            assertThatThrownBy(() -> inboundSlipService.create(buildRequest()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_PARTNER_NOT_SUPPLIER");
        }

        @Test
        @DisplayName("BOTHタイプの仕入先は受け入れる")
        void create_partnerBoth_succeeds() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L)).thenReturn(createWarehouse());
            when(partnerService.findById(5L)).thenReturn(createPartner(PartnerType.BOTH));
            when(productService.findById(10L)).thenReturn(createProduct(10L, "PRD-0001", true, false, false));
            when(inboundSlipRepository.findMaxSequenceByDate("20260322")).thenReturn(0);
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> {
                InboundSlip s = inv.getArgument(0);
                setField(s, "id", 1L);
                return s;
            });

            InboundSlip result = inboundSlipService.create(buildRequest());

            assertThat(result.getPartnerCode()).isEqualTo("SUP-0001");
        }

        @Test
        @DisplayName("入荷予定日が過去日の場合BusinessRuleViolationExceptionをスローする")
        void create_plannedDateTooEarly_throws() {
            when(businessDateProvider.today()).thenReturn(TODAY);

            CreateInboundSlipRequest request = buildRequest()
                    .plannedDate(TODAY.minusDays(1));

            assertThatThrownBy(() -> inboundSlipService.create(request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("PLANNED_DATE_TOO_EARLY");
        }

        @Test
        @DisplayName("商品が存在しない場合ResourceNotFoundExceptionをスローする")
        void create_productNotFound_throws() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L)).thenReturn(createWarehouse());
            when(partnerService.findById(5L)).thenReturn(createPartner(PartnerType.SUPPLIER));
            when(productService.findById(10L))
                    .thenThrow(new ResourceNotFoundException("PRODUCT_NOT_FOUND", "商品が見つかりません"));

            assertThatThrownBy(() -> inboundSlipService.create(buildRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("PRODUCT_NOT_FOUND");
        }

        @Test
        @DisplayName("無効な商品の場合BusinessRuleViolationExceptionをスローする")
        void create_productInactive_throws() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L)).thenReturn(createWarehouse());
            when(partnerService.findById(5L)).thenReturn(createPartner(PartnerType.SUPPLIER));
            when(productService.findById(10L)).thenReturn(createProduct(10L, "PRD-0001", false, false, false));

            assertThatThrownBy(() -> inboundSlipService.create(buildRequest()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("PRODUCT_INACTIVE");
        }

        @Test
        @DisplayName("ロット管理商品でロット番号未指定の場合BusinessRuleViolationExceptionをスローする")
        void create_lotNumberRequired_throws() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L)).thenReturn(createWarehouse());
            when(partnerService.findById(5L)).thenReturn(createPartner(PartnerType.SUPPLIER));
            when(productService.findById(10L)).thenReturn(createProduct(10L, "PRD-0001", true, true, false));

            assertThatThrownBy(() -> inboundSlipService.create(buildRequest()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("LOT_NUMBER_REQUIRED");
        }

        @Test
        @DisplayName("期限管理商品で期限日未指定の場合BusinessRuleViolationExceptionをスローする")
        void create_expiryDateRequired_throws() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L)).thenReturn(createWarehouse());
            when(partnerService.findById(5L)).thenReturn(createPartner(PartnerType.SUPPLIER));
            when(productService.findById(10L)).thenReturn(createProduct(10L, "PRD-0001", true, false, true));

            assertThatThrownBy(() -> inboundSlipService.create(buildRequest()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("EXPIRY_DATE_REQUIRED");
        }

        @Test
        @DisplayName("同一商品が複数行にある場合DuplicateResourceExceptionをスローする")
        void create_duplicateProduct_throws() {
            when(businessDateProvider.today()).thenReturn(TODAY);

            CreateInboundSlipRequest request = buildRequest()
                    .lines(List.of(
                            new CreateInboundLineRequest().productId(10L).unitType(UnitType.CASE).plannedQty(100),
                            new CreateInboundLineRequest().productId(10L).unitType(UnitType.PIECE).plannedQty(50)
                    ));

            assertThatThrownBy(() -> inboundSlipService.create(request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .extracting("errorCode").isEqualTo("DUPLICATE_PRODUCT_IN_LINES");
        }

        @Test
        @DisplayName("倉庫振替入荷ではpartnerIdがnullでも作成できる")
        void create_warehouseTransfer_noPartner_success() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L)).thenReturn(createWarehouse());
            when(productService.findById(10L)).thenReturn(createProduct(10L, "PRD-0001", true, false, false));
            when(inboundSlipRepository.findMaxSequenceByDate("20260322")).thenReturn(0);
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> {
                InboundSlip s = inv.getArgument(0);
                setField(s, "id", 1L);
                return s;
            });

            CreateInboundSlipRequest request = new CreateInboundSlipRequest()
                    .warehouseId(1L)
                    .partnerId(null)
                    .plannedDate(TODAY)
                    .slipType(InboundSlipType.WAREHOUSE_TRANSFER)
                    .lines(List.of(
                            new CreateInboundLineRequest()
                                    .productId(10L)
                                    .unitType(UnitType.CASE)
                                    .plannedQty(100)
                    ));

            InboundSlip result = inboundSlipService.create(request);

            assertThat(result.getSlipType()).isEqualTo("WAREHOUSE_TRANSFER");
            assertThat(result.getPartnerId()).isNull();
            assertThat(result.getPartnerCode()).isNull();
        }

        @Test
        @DisplayName("WAREHOUSE_TRANSFERでpartnerIdを指定した場合も成功する")
        void create_warehouseTransferWithPartner_success() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L)).thenReturn(createWarehouse());
            when(partnerService.findById(5L)).thenReturn(createPartner(PartnerType.SUPPLIER));
            when(productService.findById(10L)).thenReturn(createProduct(10L, "PRD-0001", true, false, false));
            when(inboundSlipRepository.findMaxSequenceByDate("20260322")).thenReturn(0);
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> {
                InboundSlip s = inv.getArgument(0);
                setField(s, "id", 1L);
                return s;
            });

            CreateInboundSlipRequest request = new CreateInboundSlipRequest()
                    .warehouseId(1L)
                    .partnerId(5L)
                    .plannedDate(TODAY)
                    .slipType(InboundSlipType.WAREHOUSE_TRANSFER)
                    .lines(List.of(
                            new CreateInboundLineRequest()
                                    .productId(10L)
                                    .unitType(UnitType.CASE)
                                    .plannedQty(100)
                    ));

            InboundSlip result = inboundSlipService.create(request);

            assertThat(result.getSlipType()).isEqualTo("WAREHOUSE_TRANSFER");
            assertThat(result.getPartnerId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("ロット管理商品でロット番号が空白の場合LOT_NUMBER_REQUIREDをスローする")
        void create_lotManageWithBlankLotNumber_throws() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L)).thenReturn(createWarehouse());
            when(partnerService.findById(5L)).thenReturn(createPartner(PartnerType.SUPPLIER));
            when(productService.findById(10L)).thenReturn(createProduct(10L, "PRD-0001", true, true, false));

            CreateInboundSlipRequest request = buildRequest()
                    .lines(List.of(
                            new CreateInboundLineRequest()
                                    .productId(10L)
                                    .unitType(UnitType.CASE)
                                    .plannedQty(100)
                                    .lotNumber("   ")
                    ));

            assertThatThrownBy(() -> inboundSlipService.create(request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("LOT_NUMBER_REQUIRED");
        }

        @Test
        @DisplayName("NORMAL入荷でpartnerIdがnullの場合BusinessRuleViolationExceptionをスローする")
        void create_normalWithoutPartner_throws() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L)).thenReturn(createWarehouse());

            CreateInboundSlipRequest request = buildRequest().partnerId(null);

            assertThatThrownBy(() -> inboundSlipService.create(request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_PARTNER_REQUIRED");
        }

        @Test
        @DisplayName("ロット管理商品でロット番号指定済みの場合は成功する")
        void create_lotManageWithLotNumber_success() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L)).thenReturn(createWarehouse());
            when(partnerService.findById(5L)).thenReturn(createPartner(PartnerType.SUPPLIER));
            when(productService.findById(10L)).thenReturn(createProduct(10L, "PRD-0001", true, true, false));
            when(inboundSlipRepository.findMaxSequenceByDate("20260322")).thenReturn(0);
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> {
                InboundSlip s = inv.getArgument(0);
                setField(s, "id", 1L);
                return s;
            });

            CreateInboundSlipRequest request = new CreateInboundSlipRequest()
                    .warehouseId(1L)
                    .partnerId(5L)
                    .plannedDate(TODAY)
                    .slipType(InboundSlipType.NORMAL)
                    .lines(List.of(
                            new CreateInboundLineRequest()
                                    .productId(10L)
                                    .unitType(UnitType.CASE)
                                    .plannedQty(100)
                                    .lotNumber("LOT-001")
                    ));

            InboundSlip result = inboundSlipService.create(request);

            assertThat(result.getLines().get(0).getLotNumber()).isEqualTo("LOT-001");
        }

        @Test
        @DisplayName("期限管理商品で期限日指定済みの場合は成功する")
        void create_expiryManageWithExpiryDate_success() {
            when(businessDateProvider.today()).thenReturn(TODAY);
            when(warehouseService.findById(1L)).thenReturn(createWarehouse());
            when(partnerService.findById(5L)).thenReturn(createPartner(PartnerType.SUPPLIER));
            when(productService.findById(10L)).thenReturn(createProduct(10L, "PRD-0001", true, false, true));
            when(inboundSlipRepository.findMaxSequenceByDate("20260322")).thenReturn(0);
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> {
                InboundSlip s = inv.getArgument(0);
                setField(s, "id", 1L);
                return s;
            });

            LocalDate expiryDate = LocalDate.of(2027, 3, 22);
            CreateInboundSlipRequest request = new CreateInboundSlipRequest()
                    .warehouseId(1L)
                    .partnerId(5L)
                    .plannedDate(TODAY)
                    .slipType(InboundSlipType.NORMAL)
                    .lines(List.of(
                            new CreateInboundLineRequest()
                                    .productId(10L)
                                    .unitType(UnitType.CASE)
                                    .plannedQty(100)
                                    .expiryDate(expiryDate)
                    ));

            InboundSlip result = inboundSlipService.create(request);

            assertThat(result.getLines().get(0).getExpiryDate()).isEqualTo(expiryDate);
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("PLANNEDステータスの伝票を削除できる")
        void delete_planned_success() {
            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status("PLANNED")
                    .build();
            setField(slip, "id", 1L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            inboundSlipService.delete(1L);

            verify(inboundSlipRepository).delete(slip);
        }

        @Test
        @DisplayName("存在しないIDで削除するとResourceNotFoundExceptionをスローする")
        void delete_notFound_throws() {
            when(inboundSlipRepository.findByIdWithLines(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inboundSlipService.delete(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_SLIP_NOT_FOUND");
        }

        @Test
        @DisplayName("CONFIRMEDステータスの伝票を削除するとInvalidStateTransitionExceptionをスローする")
        void delete_confirmed_throws() {
            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status("CONFIRMED")
                    .build();
            setField(slip, "id", 1L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            assertThatThrownBy(() -> inboundSlipService.delete(1L))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_INVALID_STATUS");

            verify(inboundSlipRepository, never()).delete(any());
        }

        @Test
        @DisplayName("INSPECTINGステータスの伝票を削除するとInvalidStateTransitionExceptionをスローする")
        void delete_inspecting_throws() {
            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status("INSPECTING")
                    .build();
            setField(slip, "id", 1L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            assertThatThrownBy(() -> inboundSlipService.delete(1L))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_INVALID_STATUS");
        }

    }

    @Nested
    @DisplayName("confirm")
    class ConfirmTests {

        @Test
        @DisplayName("PLANNEDステータスの伝票を確定できる")
        void confirm_planned_success() {
            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.PLANNED.getValue())
                    .build();
            setField(slip, "id", 1L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            InboundSlip result = inboundSlipService.confirm(1L);

            assertThat(result.getStatus()).isEqualTo(InboundSlipStatus.CONFIRMED.getValue());
            verify(inboundSlipRepository).save(slip);
        }

        @Test
        @DisplayName("存在しないIDで確定するとResourceNotFoundExceptionをスローする")
        void confirm_notFound_throws() {
            when(inboundSlipRepository.findByIdWithLines(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inboundSlipService.confirm(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_SLIP_NOT_FOUND");
        }

        @Test
        @DisplayName("CONFIRMEDステータスの伝票を確定するとInvalidStateTransitionExceptionをスローする")
        void confirm_confirmed_throws() {
            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.CONFIRMED.getValue())
                    .build();
            setField(slip, "id", 1L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            assertThatThrownBy(() -> inboundSlipService.confirm(1L))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_INVALID_STATUS");

            verify(inboundSlipRepository, never()).save(any());
        }

        @Test
        @DisplayName("STOREDステータスの伝票を確定するとInvalidStateTransitionExceptionをスローする")
        void confirm_stored_throws() {
            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.STORED.getValue())
                    .build();
            setField(slip, "id", 1L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            assertThatThrownBy(() -> inboundSlipService.confirm(1L))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_INVALID_STATUS");

            verify(inboundSlipRepository, never()).save(any());
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
        @DisplayName("PLANNEDステータスの伝票をキャンセルすると明細もCANCELLEDになる")
        void cancel_planned_success() {
            setUpSecurityContext(10L);
            InboundSlipLine pendingLine = InboundSlipLine.builder()
                    .lineNo(1)
                    .productId(100L)
                    .productCode("PRD-0001")
                    .productName("商品A")
                    .unitType("CASE")
                    .plannedQty(10)
                    .lineStatus(InboundLineStatus.PENDING.getValue())
                    .build();
            setField(pendingLine, "id", 11L);
            List<InboundSlipLine> lines = new ArrayList<>();
            lines.add(pendingLine);
            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.PLANNED.getValue())
                    .warehouseId(1L)
                    .lines(lines)
                    .build();
            setField(slip, "id", 1L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            InboundSlip result = inboundSlipService.cancel(1L);

            assertThat(result.getStatus()).isEqualTo(InboundSlipStatus.CANCELLED.getValue());
            assertThat(result.getCancelledBy()).isEqualTo(10L);
            assertThat(result.getCancelledAt()).isNotNull();
            assertThat(result.getLines()).allSatisfy(line ->
                    assertThat(line.getLineStatus()).isEqualTo(InboundLineStatus.CANCELLED.getValue()));
            verify(inboundSlipRepository).save(slip);
        }

        @Test
        @DisplayName("CONFIRMEDステータスの伝票をキャンセルすると明細もCANCELLEDになる")
        void cancel_confirmed_success() {
            setUpSecurityContext(10L);
            InboundSlipLine pendingLine = InboundSlipLine.builder()
                    .lineNo(1)
                    .productId(100L)
                    .productCode("PRD-0001")
                    .productName("商品A")
                    .unitType("CASE")
                    .plannedQty(10)
                    .lineStatus(InboundLineStatus.PENDING.getValue())
                    .build();
            setField(pendingLine, "id", 11L);
            List<InboundSlipLine> lines = new ArrayList<>();
            lines.add(pendingLine);
            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.CONFIRMED.getValue())
                    .warehouseId(1L)
                    .lines(lines)
                    .build();
            setField(slip, "id", 1L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            InboundSlip result = inboundSlipService.cancel(1L);

            assertThat(result.getStatus()).isEqualTo(InboundSlipStatus.CANCELLED.getValue());
            assertThat(result.getLines()).allSatisfy(line ->
                    assertThat(line.getLineStatus()).isEqualTo(InboundLineStatus.CANCELLED.getValue()));
        }

        @Test
        @DisplayName("INSPECTINGステータスの伝票をキャンセルすると明細もCANCELLEDになる")
        void cancel_inspecting_success() {
            setUpSecurityContext(10L);
            InboundSlipLine inspectedLine = InboundSlipLine.builder()
                    .lineNo(1)
                    .productId(100L)
                    .productCode("PRD-0001")
                    .productName("商品A")
                    .unitType("CASE")
                    .plannedQty(10)
                    .inspectedQty(10)
                    .lineStatus(InboundLineStatus.INSPECTED.getValue())
                    .build();
            setField(inspectedLine, "id", 11L);
            List<InboundSlipLine> lines = new ArrayList<>();
            lines.add(inspectedLine);
            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.INSPECTING.getValue())
                    .warehouseId(1L)
                    .lines(lines)
                    .build();
            setField(slip, "id", 1L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            InboundSlip result = inboundSlipService.cancel(1L);

            assertThat(result.getStatus()).isEqualTo(InboundSlipStatus.CANCELLED.getValue());
            assertThat(result.getLines()).allSatisfy(line ->
                    assertThat(line.getLineStatus()).isEqualTo(InboundLineStatus.CANCELLED.getValue()));
        }

        @Test
        @DisplayName("PARTIAL_STOREDステータスの伝票をキャンセルすると在庫がロールバックされる")
        void cancel_partialStored_rollbackInventory() {
            setUpSecurityContext(10L);

            InboundSlipLine storedLine = InboundSlipLine.builder()
                    .lineNo(1)
                    .productId(100L)
                    .productCode("PRD-0001")
                    .productName("商品A")
                    .unitType("CASE")
                    .lotNumber("LOT-001")
                    .expiryDate(LocalDate.of(2027, 3, 22))
                    .plannedQty(50)
                    .inspectedQty(48)
                    .putawayLocationId(200L)
                    .putawayLocationCode("LOC-A01")
                    .lineStatus(InboundLineStatus.STORED.getValue())
                    .build();
            setField(storedLine, "id", 11L);

            InboundSlipLine pendingLine = InboundSlipLine.builder()
                    .lineNo(2)
                    .productId(101L)
                    .productCode("PRD-0002")
                    .productName("商品B")
                    .unitType("PIECE")
                    .plannedQty(30)
                    .lineStatus(InboundLineStatus.PENDING.getValue())
                    .build();
            setField(pendingLine, "id", 12L);

            List<InboundSlipLine> lines = new ArrayList<>();
            lines.add(storedLine);
            lines.add(pendingLine);

            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.PARTIAL_STORED.getValue())
                    .warehouseId(1L)
                    .lines(lines)
                    .build();
            setField(slip, "id", 1L);

            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            doNothing().when(inventoryService).rollbackInboundStock(any(InventoryService.RollbackInboundCommand.class));
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            InboundSlip result = inboundSlipService.cancel(1L);

            assertThat(result.getStatus()).isEqualTo(InboundSlipStatus.CANCELLED.getValue());
            assertThat(result.getLines()).allSatisfy(line ->
                    assertThat(line.getLineStatus()).isEqualTo(InboundLineStatus.CANCELLED.getValue()));

            verify(inventoryService).rollbackInboundStock(any(InventoryService.RollbackInboundCommand.class));
        }

        @Test
        @DisplayName("PARTIAL_STOREDで在庫が見つからない場合ResourceNotFoundExceptionをスローする")
        void cancel_partialStored_inventoryNotFound_throws() {
            setUpSecurityContext(10L);

            InboundSlipLine storedLine = InboundSlipLine.builder()
                    .lineNo(1)
                    .productId(100L)
                    .productCode("PRD-0001")
                    .productName("商品A")
                    .unitType("CASE")
                    .lotNumber("LOT-001")
                    .expiryDate(LocalDate.of(2027, 3, 22))
                    .plannedQty(50)
                    .inspectedQty(48)
                    .putawayLocationId(200L)
                    .putawayLocationCode("LOC-A01")
                    .lineStatus(InboundLineStatus.STORED.getValue())
                    .build();
            setField(storedLine, "id", 11L);

            List<InboundSlipLine> lines = new ArrayList<>();
            lines.add(storedLine);

            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.PARTIAL_STORED.getValue())
                    .warehouseId(1L)
                    .lines(lines)
                    .build();
            setField(slip, "id", 1L);

            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            doThrow(new ResourceNotFoundException("INVENTORY_NOT_FOUND", "在庫が見つかりません"))
                    .when(inventoryService).rollbackInboundStock(any(InventoryService.RollbackInboundCommand.class));

            assertThatThrownBy(() -> inboundSlipService.cancel(1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("INVENTORY_NOT_FOUND");
        }

        @Test
        @DisplayName("PARTIAL_STOREDで在庫不足の場合BusinessRuleViolationExceptionをスローする")
        void cancel_partialStored_insufficientInventory_throws() {
            setUpSecurityContext(10L);

            InboundSlipLine storedLine = InboundSlipLine.builder()
                    .lineNo(1).productId(100L).productCode("PRD-0001").productName("商品A")
                    .unitType("CASE").plannedQty(50).inspectedQty(48)
                    .putawayLocationId(200L).putawayLocationCode("LOC-A01")
                    .lineStatus(InboundLineStatus.STORED.getValue()).build();
            setField(storedLine, "id", 11L);

            List<InboundSlipLine> lines = new ArrayList<>();
            lines.add(storedLine);

            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.PARTIAL_STORED.getValue())
                    .warehouseId(1L).lines(lines).build();
            setField(slip, "id", 1L);

            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            doThrow(new BusinessRuleViolationException("INVENTORY_INSUFFICIENT", "在庫ロールバックで在庫数が負になります"))
                    .when(inventoryService).rollbackInboundStock(any(InventoryService.RollbackInboundCommand.class));

            assertThatThrownBy(() -> inboundSlipService.cancel(1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("INVENTORY_INSUFFICIENT");
        }

        @Test
        @DisplayName("PARTIAL_STOREDで引当済み数量超過の場合BusinessRuleViolationExceptionをスローする")
        void cancel_partialStored_allocatedExceeds_throws() {
            setUpSecurityContext(10L);

            InboundSlipLine storedLine = InboundSlipLine.builder()
                    .lineNo(1).productId(100L).productCode("PRD-0001").productName("商品A")
                    .unitType("CASE").plannedQty(50).inspectedQty(30)
                    .putawayLocationId(200L).putawayLocationCode("LOC-A01")
                    .lineStatus(InboundLineStatus.STORED.getValue()).build();
            setField(storedLine, "id", 11L);

            List<InboundSlipLine> lines = new ArrayList<>();
            lines.add(storedLine);

            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.PARTIAL_STORED.getValue())
                    .warehouseId(1L).lines(lines).build();
            setField(slip, "id", 1L);

            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            doThrow(new BusinessRuleViolationException("INVENTORY_ALLOCATED", "引当済み数量が在庫ロールバック後の数量を超えます"))
                    .when(inventoryService).rollbackInboundStock(any(InventoryService.RollbackInboundCommand.class));

            assertThatThrownBy(() -> inboundSlipService.cancel(1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("INVENTORY_ALLOCATED");
        }

        @Test
        @DisplayName("存在しないIDでキャンセルするとResourceNotFoundExceptionをスローする")
        void cancel_notFound_throws() {
            when(inboundSlipRepository.findByIdWithLines(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inboundSlipService.cancel(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_SLIP_NOT_FOUND");
        }

        @Test
        @DisplayName("STOREDステータスの伝票をキャンセルするとInvalidStateTransitionExceptionをスローする")
        void cancel_stored_throws() {
            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.STORED.getValue())
                    .build();
            setField(slip, "id", 1L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            assertThatThrownBy(() -> inboundSlipService.cancel(1L))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_INVALID_STATUS");

            verify(inboundSlipRepository, never()).save(any());
        }

        @Test
        @DisplayName("CANCELLEDステータスの伝票をキャンセルするとInvalidStateTransitionExceptionをスローする")
        void cancel_cancelled_throws() {
            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.CANCELLED.getValue())
                    .build();
            setField(slip, "id", 1L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            assertThatThrownBy(() -> inboundSlipService.cancel(1L))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_INVALID_STATUS");

            verify(inboundSlipRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("inspect")
    class InspectTests {

        @AfterEach
        void clearSecurityContext() {
            SecurityContextHolder.clearContext();
        }

        private InboundSlip createSlipWithLine(String headerStatus, String lineStatus, Long lineId) {
            InboundSlipLine line = InboundSlipLine.builder()
                    .lineNo(1)
                    .productId(100L)
                    .productCode("PRD-0001")
                    .productName("商品A")
                    .unitType("CASE")
                    .plannedQty(50)
                    .lineStatus(lineStatus)
                    .build();
            setField(line, "id", lineId);

            List<InboundSlipLine> lines = new ArrayList<>();
            lines.add(line);

            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(headerStatus)
                    .warehouseId(1L)
                    .lines(lines)
                    .build();
            setField(slip, "id", 1L);
            return slip;
        }

        @Test
        @DisplayName("正常系: CONFIRMED→INSPECTINGに遷移する")
        void inspect_confirmed_success() {
            setUpSecurityContext(10L);
            InboundSlip slip = createSlipWithLine(
                    InboundSlipStatus.CONFIRMED.getValue(),
                    InboundLineStatus.PENDING.getValue(), 11L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            InspectInboundRequest request = new InspectInboundRequest()
                    .lines(List.of(new InspectLineRequest().lineId(11L).inspectedQty(48)));

            InboundSlip result = inboundSlipService.inspect(1L, request);

            assertThat(result.getStatus()).isEqualTo(InboundSlipStatus.INSPECTING.getValue());
            assertThat(result.getLines().get(0).getInspectedQty()).isEqualTo(48);
            assertThat(result.getLines().get(0).getLineStatus()).isEqualTo(InboundLineStatus.INSPECTED.getValue());
            assertThat(result.getLines().get(0).getInspectedBy()).isEqualTo(10L);
            assertThat(result.getLines().get(0).getInspectedAt()).isNotNull();
        }

        @Test
        @DisplayName("正常系: INSPECTINGステータスで再検品できる")
        void inspect_inspecting_reInspect_success() {
            setUpSecurityContext(10L);
            InboundSlip slip = createSlipWithLine(
                    InboundSlipStatus.INSPECTING.getValue(),
                    InboundLineStatus.INSPECTED.getValue(), 11L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            InspectInboundRequest request = new InspectInboundRequest()
                    .lines(List.of(new InspectLineRequest().lineId(11L).inspectedQty(50)));

            InboundSlip result = inboundSlipService.inspect(1L, request);

            assertThat(result.getStatus()).isEqualTo(InboundSlipStatus.INSPECTING.getValue());
            assertThat(result.getLines().get(0).getInspectedQty()).isEqualTo(50);
        }

        @Test
        @DisplayName("正常系: PARTIAL_STOREDステータスでも検品できる")
        void inspect_partialStored_success() {
            setUpSecurityContext(10L);

            InboundSlipLine storedLine = InboundSlipLine.builder()
                    .lineNo(1).productId(100L).productCode("PRD-0001").productName("商品A")
                    .unitType("CASE").plannedQty(50).inspectedQty(48)
                    .lineStatus(InboundLineStatus.STORED.getValue()).build();
            setField(storedLine, "id", 11L);

            InboundSlipLine pendingLine = InboundSlipLine.builder()
                    .lineNo(2).productId(101L).productCode("PRD-0002").productName("商品B")
                    .unitType("PIECE").plannedQty(30)
                    .lineStatus(InboundLineStatus.PENDING.getValue()).build();
            setField(pendingLine, "id", 12L);

            List<InboundSlipLine> lines = new ArrayList<>();
            lines.add(storedLine);
            lines.add(pendingLine);

            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.PARTIAL_STORED.getValue())
                    .warehouseId(1L).lines(lines).build();
            setField(slip, "id", 1L);

            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            InspectInboundRequest request = new InspectInboundRequest()
                    .lines(List.of(new InspectLineRequest().lineId(12L).inspectedQty(28)));

            InboundSlip result = inboundSlipService.inspect(1L, request);

            assertThat(result.getLines().get(1).getInspectedQty()).isEqualTo(28);
            assertThat(result.getLines().get(1).getLineStatus()).isEqualTo(InboundLineStatus.INSPECTED.getValue());
        }

        @Test
        @DisplayName("STORED明細は検品できない")
        void inspect_storedLine_throws() {
            setUpSecurityContext(10L);
            InboundSlip slip = createSlipWithLine(
                    InboundSlipStatus.PARTIAL_STORED.getValue(),
                    InboundLineStatus.STORED.getValue(), 11L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            InspectInboundRequest request = new InspectInboundRequest()
                    .lines(List.of(new InspectLineRequest().lineId(11L).inspectedQty(48)));

            assertThatThrownBy(() -> inboundSlipService.inspect(1L, request))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_LINE_ALREADY_STORED");
        }

        @Test
        @DisplayName("明細が見つからない場合ResourceNotFoundExceptionをスローする")
        void inspect_lineNotFound_throws() {
            setUpSecurityContext(10L);
            InboundSlip slip = createSlipWithLine(
                    InboundSlipStatus.CONFIRMED.getValue(),
                    InboundLineStatus.PENDING.getValue(), 11L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            InspectInboundRequest request = new InspectInboundRequest()
                    .lines(List.of(new InspectLineRequest().lineId(999L).inspectedQty(48)));

            assertThatThrownBy(() -> inboundSlipService.inspect(1L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_LINE_NOT_FOUND");
        }

        @Test
        @DisplayName("PLANNEDステータスでは検品できない")
        void inspect_planned_throws() {
            InboundSlip slip = createSlipWithLine(
                    InboundSlipStatus.PLANNED.getValue(),
                    InboundLineStatus.PENDING.getValue(), 11L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            InspectInboundRequest request = new InspectInboundRequest()
                    .lines(List.of(new InspectLineRequest().lineId(11L).inspectedQty(48)));

            assertThatThrownBy(() -> inboundSlipService.inspect(1L, request))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_INVALID_STATUS");
        }

        @Test
        @DisplayName("STOREDステータスでは検品できない")
        void inspect_stored_throws() {
            InboundSlip slip = createSlipWithLine(
                    InboundSlipStatus.STORED.getValue(),
                    InboundLineStatus.STORED.getValue(), 11L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            InspectInboundRequest request = new InspectInboundRequest()
                    .lines(List.of(new InspectLineRequest().lineId(11L).inspectedQty(48)));

            assertThatThrownBy(() -> inboundSlipService.inspect(1L, request))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_INVALID_STATUS");
        }

        @Test
        @DisplayName("CANCELLEDステータスでは検品できない")
        void inspect_cancelled_throws() {
            InboundSlip slip = createSlipWithLine(
                    InboundSlipStatus.CANCELLED.getValue(),
                    InboundLineStatus.PENDING.getValue(), 11L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            InspectInboundRequest request = new InspectInboundRequest()
                    .lines(List.of(new InspectLineRequest().lineId(11L).inspectedQty(48)));

            assertThatThrownBy(() -> inboundSlipService.inspect(1L, request))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_INVALID_STATUS");
        }

        @Test
        @DisplayName("伝票が存在しない場合ResourceNotFoundExceptionをスローする")
        void inspect_slipNotFound_throws() {
            when(inboundSlipRepository.findByIdWithLines(999L)).thenReturn(Optional.empty());

            InspectInboundRequest request = new InspectInboundRequest()
                    .lines(List.of(new InspectLineRequest().lineId(11L).inspectedQty(48)));

            assertThatThrownBy(() -> inboundSlipService.inspect(999L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_SLIP_NOT_FOUND");
        }

        @Test
        @DisplayName("重複lineIdがある場合BusinessRuleViolationExceptionをスローする")
        void inspect_duplicateLineId_throws() {
            setUpSecurityContext(10L);
            InboundSlip slip = createSlipWithLine(InboundSlipStatus.CONFIRMED.getValue(),
                    InboundLineStatus.PENDING.getValue(), 11L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            InspectInboundRequest request = new InspectInboundRequest()
                    .lines(List.of(
                            new InspectLineRequest().lineId(11L).inspectedQty(48),
                            new InspectLineRequest().lineId(11L).inspectedQty(50)));

            assertThatThrownBy(() -> inboundSlipService.inspect(1L, request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("DUPLICATE_LINE_IN_REQUEST");
        }

        @Test
        @DisplayName("検品数が負の場合BusinessRuleViolationExceptionをスローする")
        void inspect_negativeQty_throws() {
            setUpSecurityContext(10L);
            InboundSlip slip = createSlipWithLine(InboundSlipStatus.CONFIRMED.getValue(),
                    InboundLineStatus.PENDING.getValue(), 11L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            InspectInboundRequest request = new InspectInboundRequest()
                    .lines(List.of(new InspectLineRequest().lineId(11L).inspectedQty(-1)));

            assertThatThrownBy(() -> inboundSlipService.inspect(1L, request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_INSPECTED_QTY_NEGATIVE");
        }

    }

    @Nested
    @DisplayName("store")
    class StoreTests {

        @AfterEach
        void clearSecurityContext() {
            SecurityContextHolder.clearContext();
        }

        private Location createLocation(Long id, Long areaId, boolean active, boolean stocktakeLocked) {
            Location loc = new Location();
            loc.setWarehouseId(1L);
            loc.setAreaId(areaId);
            loc.setLocationCode("LOC-A01");
            loc.setLocationName("入荷ロケ1");
            loc.setIsStocktakingLocked(stocktakeLocked);
            setField(loc, "id", id);
            setField(loc, "isActive", active);
            return loc;
        }

        private Area createArea(Long id, String areaType) {
            Area area = new Area();
            area.setWarehouseId(1L);
            area.setBuildingId(1L);
            area.setAreaCode("AREA-001");
            area.setAreaName("入荷エリア");
            area.setStorageCondition("NORMAL");
            area.setAreaType(areaType);
            setField(area, "id", id);
            setField(area, "isActive", true);
            return area;
        }

        private InboundSlip createSlipWithInspectedLine(String headerStatus, Long lineId, int inspectedQty) {
            InboundSlipLine line = InboundSlipLine.builder()
                    .lineNo(1)
                    .productId(100L)
                    .productCode("PRD-0001")
                    .productName("商品A")
                    .unitType("CASE")
                    .plannedQty(50)
                    .inspectedQty(inspectedQty)
                    .lotNumber("LOT-001")
                    .expiryDate(java.time.LocalDate.of(2027, 3, 22))
                    .lineStatus(InboundLineStatus.INSPECTED.getValue())
                    .build();
            setField(line, "id", lineId);

            List<InboundSlipLine> lines = new ArrayList<>();
            lines.add(line);

            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(headerStatus)
                    .warehouseId(1L)
                    .lines(lines)
                    .build();
            setField(slip, "id", 1L);
            return slip;
        }

        @Test
        @DisplayName("正常系: 全明細格納でINSPECTING→STOREDに遷移する")
        void store_allLinesStored_success() {
            setUpSecurityContext(10L);
            InboundSlip slip = createSlipWithInspectedLine(
                    InboundSlipStatus.INSPECTING.getValue(), 11L, 48);

            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(locationService.findById(200L)).thenReturn(createLocation(200L, 300L, true, false));
            when(areaService.findById(300L)).thenReturn(createArea(300L, "INBOUND"));
            when(inventoryService.existsDifferentProductAtLocation(200L, 100L)).thenReturn(false);
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            StoreInboundRequest request = new StoreInboundRequest()
                    .lines(List.of(new StoreLineRequest().lineId(11L).locationId(200L)));

            InboundSlip result = inboundSlipService.store(1L, request);

            assertThat(result.getStatus()).isEqualTo(InboundSlipStatus.STORED.getValue());
            assertThat(result.getLines().get(0).getLineStatus()).isEqualTo(InboundLineStatus.STORED.getValue());
            assertThat(result.getLines().get(0).getPutawayLocationId()).isEqualTo(200L);
            assertThat(result.getLines().get(0).getPutawayLocationCode()).isEqualTo("LOC-A01");
            assertThat(result.getLines().get(0).getStoredBy()).isEqualTo(10L);
            assertThat(result.getLines().get(0).getStoredAt()).isNotNull();

            verify(inventoryService).storeInboundStock(any(InventoryService.StoreInboundCommand.class));
        }

        @Test
        @DisplayName("正常系: 一部明細のみ格納でPARTIAL_STOREDに遷移する")
        void store_partialLines_partialStored() {
            setUpSecurityContext(10L);

            InboundSlipLine inspectedLine = InboundSlipLine.builder()
                    .lineNo(1).productId(100L).productCode("PRD-0001").productName("商品A")
                    .unitType("CASE").plannedQty(50).inspectedQty(48)
                    .lotNumber("LOT-001").expiryDate(java.time.LocalDate.of(2027, 3, 22))
                    .lineStatus(InboundLineStatus.INSPECTED.getValue()).build();
            setField(inspectedLine, "id", 11L);

            InboundSlipLine pendingLine = InboundSlipLine.builder()
                    .lineNo(2).productId(101L).productCode("PRD-0002").productName("商品B")
                    .unitType("PIECE").plannedQty(30)
                    .lineStatus(InboundLineStatus.PENDING.getValue()).build();
            setField(pendingLine, "id", 12L);

            List<InboundSlipLine> lines = new ArrayList<>();
            lines.add(inspectedLine);
            lines.add(pendingLine);

            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.INSPECTING.getValue())
                    .warehouseId(1L).lines(lines).build();
            setField(slip, "id", 1L);

            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(locationService.findById(200L)).thenReturn(createLocation(200L, 300L, true, false));
            when(areaService.findById(300L)).thenReturn(createArea(300L, "INBOUND"));
            when(inventoryService.existsDifferentProductAtLocation(200L, 100L)).thenReturn(false);
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            StoreInboundRequest request = new StoreInboundRequest()
                    .lines(List.of(new StoreLineRequest().lineId(11L).locationId(200L)));

            InboundSlip result = inboundSlipService.store(1L, request);

            assertThat(result.getStatus()).isEqualTo(InboundSlipStatus.PARTIAL_STORED.getValue());
            assertThat(result.getLines().get(0).getLineStatus()).isEqualTo(InboundLineStatus.STORED.getValue());
            assertThat(result.getLines().get(1).getLineStatus()).isEqualTo(InboundLineStatus.PENDING.getValue());
        }

        @Test
        @DisplayName("INSPECTED以外の明細は格納できない（PENDING）")
        void store_lineNotInspected_throws() {
            setUpSecurityContext(10L);
            InboundSlipLine pendingLine = InboundSlipLine.builder()
                    .lineNo(1).productId(100L).productCode("PRD-0001").productName("商品A")
                    .unitType("CASE").plannedQty(50)
                    .lineStatus(InboundLineStatus.PENDING.getValue()).build();
            setField(pendingLine, "id", 11L);

            List<InboundSlipLine> lines = new ArrayList<>();
            lines.add(pendingLine);

            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.INSPECTING.getValue())
                    .warehouseId(1L).lines(lines).build();
            setField(slip, "id", 1L);

            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            StoreInboundRequest request = new StoreInboundRequest()
                    .lines(List.of(new StoreLineRequest().lineId(11L).locationId(200L)));

            assertThatThrownBy(() -> inboundSlipService.store(1L, request))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_LINE_NOT_INSPECTED");
        }

        @Test
        @DisplayName("STORED明細は格納できない")
        void store_lineAlreadyStored_throws() {
            setUpSecurityContext(10L);
            InboundSlipLine storedLine = InboundSlipLine.builder()
                    .lineNo(1).productId(100L).productCode("PRD-0001").productName("商品A")
                    .unitType("CASE").plannedQty(50).inspectedQty(48)
                    .lineStatus(InboundLineStatus.STORED.getValue()).build();
            setField(storedLine, "id", 11L);

            List<InboundSlipLine> lines = new ArrayList<>();
            lines.add(storedLine);

            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.PARTIAL_STORED.getValue())
                    .warehouseId(1L).lines(lines).build();
            setField(slip, "id", 1L);

            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            StoreInboundRequest request = new StoreInboundRequest()
                    .lines(List.of(new StoreLineRequest().lineId(11L).locationId(200L)));

            assertThatThrownBy(() -> inboundSlipService.store(1L, request))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_LINE_NOT_INSPECTED");
        }

        @Test
        @DisplayName("検品数が0の明細は格納できない")
        void store_inspectedQtyZero_throws() {
            setUpSecurityContext(10L);
            InboundSlip slip = createSlipWithInspectedLine(
                    InboundSlipStatus.INSPECTING.getValue(), 11L, 0);

            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            StoreInboundRequest request = new StoreInboundRequest()
                    .lines(List.of(new StoreLineRequest().lineId(11L).locationId(200L)));

            assertThatThrownBy(() -> inboundSlipService.store(1L, request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("INSPECTED_QTY_ZERO");
        }

        @Test
        @DisplayName("検品数がnullの明細は格納できない")
        void store_inspectedQtyNull_throws() {
            setUpSecurityContext(10L);

            InboundSlipLine line = InboundSlipLine.builder()
                    .lineNo(1).productId(100L).productCode("PRD-0001").productName("商品A")
                    .unitType("CASE").plannedQty(50).inspectedQty(null)
                    .lineStatus(InboundLineStatus.INSPECTED.getValue()).build();
            setField(line, "id", 11L);

            List<InboundSlipLine> lines = new ArrayList<>();
            lines.add(line);

            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.INSPECTING.getValue())
                    .warehouseId(1L).lines(lines).build();
            setField(slip, "id", 1L);

            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            StoreInboundRequest request = new StoreInboundRequest()
                    .lines(List.of(new StoreLineRequest().lineId(11L).locationId(200L)));

            assertThatThrownBy(() -> inboundSlipService.store(1L, request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("INSPECTED_QTY_ZERO");
        }

        @Test
        @DisplayName("ロケーションが存在しない場合ResourceNotFoundExceptionをスローする")
        void store_locationNotFound_throws() {
            setUpSecurityContext(10L);
            InboundSlip slip = createSlipWithInspectedLine(
                    InboundSlipStatus.INSPECTING.getValue(), 11L, 48);

            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(locationService.findById(999L))
                    .thenThrow(new ResourceNotFoundException("LOCATION_NOT_FOUND", "ロケーション が見つかりません (id=999)"));

            StoreInboundRequest request = new StoreInboundRequest()
                    .lines(List.of(new StoreLineRequest().lineId(11L).locationId(999L)));

            assertThatThrownBy(() -> inboundSlipService.store(1L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("LOCATION_NOT_FOUND");
        }

        @Test
        @DisplayName("無効なロケーションには格納できない")
        void store_locationInactive_throws() {
            setUpSecurityContext(10L);
            InboundSlip slip = createSlipWithInspectedLine(
                    InboundSlipStatus.INSPECTING.getValue(), 11L, 48);

            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(locationService.findById(200L)).thenReturn(createLocation(200L, 300L, false, false));

            StoreInboundRequest request = new StoreInboundRequest()
                    .lines(List.of(new StoreLineRequest().lineId(11L).locationId(200L)));

            assertThatThrownBy(() -> inboundSlipService.store(1L, request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("LOCATION_INACTIVE");
        }

        @Test
        @DisplayName("入荷エリア以外のロケーションには格納できない")
        void store_areNotInbound_throws() {
            setUpSecurityContext(10L);
            InboundSlip slip = createSlipWithInspectedLine(
                    InboundSlipStatus.INSPECTING.getValue(), 11L, 48);

            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(locationService.findById(200L)).thenReturn(createLocation(200L, 300L, true, false));
            when(areaService.findById(300L)).thenReturn(createArea(300L, "STOCK"));

            StoreInboundRequest request = new StoreInboundRequest()
                    .lines(List.of(new StoreLineRequest().lineId(11L).locationId(200L)));

            assertThatThrownBy(() -> inboundSlipService.store(1L, request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("AREA_NOT_INBOUND");
        }

        @Test
        @DisplayName("棚卸中のロケーションには格納できない")
        void store_stocktakeLocked_throws() {
            setUpSecurityContext(10L);
            InboundSlip slip = createSlipWithInspectedLine(
                    InboundSlipStatus.INSPECTING.getValue(), 11L, 48);

            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(locationService.findById(200L)).thenReturn(createLocation(200L, 300L, true, true));
            when(areaService.findById(300L)).thenReturn(createArea(300L, "INBOUND"));

            StoreInboundRequest request = new StoreInboundRequest()
                    .lines(List.of(new StoreLineRequest().lineId(11L).locationId(200L)));

            assertThatThrownBy(() -> inboundSlipService.store(1L, request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("LOCATION_STOCKTAKE_LOCKED");
        }

        @Test
        @DisplayName("同一ロケーションに異なる商品がある場合格納できない")
        void store_differentProductAtLocation_throws() {
            setUpSecurityContext(10L);
            InboundSlip slip = createSlipWithInspectedLine(
                    InboundSlipStatus.INSPECTING.getValue(), 11L, 48);

            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(locationService.findById(200L)).thenReturn(createLocation(200L, 300L, true, false));
            when(areaService.findById(300L)).thenReturn(createArea(300L, "INBOUND"));
            when(inventoryService.existsDifferentProductAtLocation(200L, 100L)).thenReturn(true);

            StoreInboundRequest request = new StoreInboundRequest()
                    .lines(List.of(new StoreLineRequest().lineId(11L).locationId(200L)));

            assertThatThrownBy(() -> inboundSlipService.store(1L, request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("DIFFERENT_PRODUCT_AT_LOCATION");
        }

        @Test
        @DisplayName("PLANNEDステータスでは格納できない")
        void store_planned_throws() {
            InboundSlip slip = createSlipWithInspectedLine(
                    InboundSlipStatus.PLANNED.getValue(), 11L, 48);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            StoreInboundRequest request = new StoreInboundRequest()
                    .lines(List.of(new StoreLineRequest().lineId(11L).locationId(200L)));

            assertThatThrownBy(() -> inboundSlipService.store(1L, request))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_INVALID_STATUS");
        }

        @Test
        @DisplayName("CONFIRMEDステータスでは格納できない")
        void store_confirmed_throws() {
            InboundSlip slip = createSlipWithInspectedLine(
                    InboundSlipStatus.CONFIRMED.getValue(), 11L, 48);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            StoreInboundRequest request = new StoreInboundRequest()
                    .lines(List.of(new StoreLineRequest().lineId(11L).locationId(200L)));

            assertThatThrownBy(() -> inboundSlipService.store(1L, request))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_INVALID_STATUS");
        }

        @Test
        @DisplayName("STOREDステータスでは格納できない")
        void store_stored_throws() {
            InboundSlip slip = createSlipWithInspectedLine(
                    InboundSlipStatus.STORED.getValue(), 11L, 48);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            StoreInboundRequest request = new StoreInboundRequest()
                    .lines(List.of(new StoreLineRequest().lineId(11L).locationId(200L)));

            assertThatThrownBy(() -> inboundSlipService.store(1L, request))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_INVALID_STATUS");
        }

        @Test
        @DisplayName("CANCELLEDステータスでは格納できない")
        void store_cancelled_throws() {
            InboundSlip slip = createSlipWithInspectedLine(
                    InboundSlipStatus.CANCELLED.getValue(), 11L, 48);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            StoreInboundRequest request = new StoreInboundRequest()
                    .lines(List.of(new StoreLineRequest().lineId(11L).locationId(200L)));

            assertThatThrownBy(() -> inboundSlipService.store(1L, request))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_INVALID_STATUS");
        }

        @Test
        @DisplayName("明細が見つからない場合ResourceNotFoundExceptionをスローする")
        void store_lineNotFound_throws() {
            setUpSecurityContext(10L);
            InboundSlip slip = createSlipWithInspectedLine(
                    InboundSlipStatus.INSPECTING.getValue(), 11L, 48);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            StoreInboundRequest request = new StoreInboundRequest()
                    .lines(List.of(new StoreLineRequest().lineId(999L).locationId(200L)));

            assertThatThrownBy(() -> inboundSlipService.store(1L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_LINE_NOT_FOUND");
        }

        @Test
        @DisplayName("伝票が存在しない場合ResourceNotFoundExceptionをスローする")
        void store_slipNotFound_throws() {
            when(inboundSlipRepository.findByIdWithLines(999L)).thenReturn(Optional.empty());

            StoreInboundRequest request = new StoreInboundRequest()
                    .lines(List.of(new StoreLineRequest().lineId(11L).locationId(200L)));

            assertThatThrownBy(() -> inboundSlipService.store(999L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("INBOUND_SLIP_NOT_FOUND");
        }

        @Test
        @DisplayName("正常系: PARTIAL_STOREDから残りを格納してSTOREDに遷移する")
        void store_partialStoredToStored_success() {
            setUpSecurityContext(10L);

            InboundSlipLine storedLine = InboundSlipLine.builder()
                    .lineNo(1).productId(100L).productCode("PRD-0001").productName("商品A")
                    .unitType("CASE").plannedQty(50).inspectedQty(48)
                    .putawayLocationId(200L).putawayLocationCode("LOC-A01")
                    .lineStatus(InboundLineStatus.STORED.getValue()).build();
            setField(storedLine, "id", 11L);

            InboundSlipLine inspectedLine = InboundSlipLine.builder()
                    .lineNo(2).productId(101L).productCode("PRD-0002").productName("商品B")
                    .unitType("PIECE").plannedQty(30).inspectedQty(28)
                    .lotNumber("LOT-002").expiryDate(java.time.LocalDate.of(2027, 6, 1))
                    .lineStatus(InboundLineStatus.INSPECTED.getValue()).build();
            setField(inspectedLine, "id", 12L);

            List<InboundSlipLine> lines = new ArrayList<>();
            lines.add(storedLine);
            lines.add(inspectedLine);

            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.PARTIAL_STORED.getValue())
                    .warehouseId(1L).lines(lines).build();
            setField(slip, "id", 1L);

            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(locationService.findById(201L)).thenReturn(createLocation(201L, 300L, true, false));
            when(areaService.findById(300L)).thenReturn(createArea(300L, "INBOUND"));
            when(inventoryService.existsDifferentProductAtLocation(201L, 101L)).thenReturn(false);
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            StoreInboundRequest request = new StoreInboundRequest()
                    .lines(List.of(new StoreLineRequest().lineId(12L).locationId(201L)));

            InboundSlip result = inboundSlipService.store(1L, request);

            assertThat(result.getStatus()).isEqualTo(InboundSlipStatus.STORED.getValue());
        }

        @Test
        @DisplayName("重複lineIdがある場合BusinessRuleViolationExceptionをスローする")
        void store_duplicateLineId_throws() {
            setUpSecurityContext(10L);
            InboundSlip slip = createSlipWithInspectedLine(InboundSlipStatus.INSPECTING.getValue(), 11L, 48);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            StoreInboundRequest request = new StoreInboundRequest()
                    .lines(List.of(
                            new StoreLineRequest().lineId(11L).locationId(200L),
                            new StoreLineRequest().lineId(11L).locationId(201L)));

            assertThatThrownBy(() -> inboundSlipService.store(1L, request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("DUPLICATE_LINE_IN_REQUEST");
        }

        @Test
        @DisplayName("ロケーションの倉庫IDが伝票と異なる場合BusinessRuleViolationExceptionをスローする")
        void store_locationWarehouseMismatch_throws() {
            setUpSecurityContext(10L);
            InboundSlip slip = createSlipWithInspectedLine(InboundSlipStatus.INSPECTING.getValue(), 11L, 48);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));

            // Location belongs to warehouse 2, but slip is warehouse 1
            Location wrongWhLocation = new Location();
            wrongWhLocation.setWarehouseId(2L);
            wrongWhLocation.setAreaId(300L);
            wrongWhLocation.setLocationCode("LOC-B01");
            setField(wrongWhLocation, "id", 200L);
            setField(wrongWhLocation, "isActive", true);
            when(locationService.findById(200L)).thenReturn(wrongWhLocation);

            StoreInboundRequest request = new StoreInboundRequest()
                    .lines(List.of(new StoreLineRequest().lineId(11L).locationId(200L)));

            assertThatThrownBy(() -> inboundSlipService.store(1L, request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("LOCATION_WAREHOUSE_MISMATCH");
        }
    }

    @Nested
    @DisplayName("findResults")
    class FindResultsTests {

        @Test
        @DisplayName("格納済み明細一覧を返す")
        void findResults_returnsPage() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 22));

            InboundSlipLine line = InboundSlipLine.builder()
                    .lineNo(1).productId(100L).productCode("PRD-0001").productName("商品A")
                    .unitType("CASE").plannedQty(50).inspectedQty(48)
                    .lineStatus("STORED").build();
            setField(line, "id", 11L);

            Page<InboundSlipLine> page = new PageImpl<>(List.of(line));
            when(inboundSlipLineRepository.searchResults(
                    eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(page);

            Page<InboundSlipLine> result = inboundSlipService.findResults(
                    1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 22),
                    null, null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            verify(warehouseService).findById(1L);
        }

        @Test
        @DisplayName("倉庫が存在しない場合ResourceNotFoundExceptionをスローする")
        void findResults_warehouseNotFound_throws() {
            when(warehouseService.findById(999L))
                    .thenThrow(new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "倉庫 が見つかりません (id=999)"));

            assertThatThrownBy(() -> inboundSlipService.findResults(
                    999L, null, null, null, null, null, PageRequest.of(0, 20)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("WAREHOUSE_NOT_FOUND");
        }

        @Test
        @DisplayName("日付パラメータがnullの場合デフォルト値が使用される")
        void findResults_defaultDates() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 22));
            when(inboundSlipLineRepository.searchResults(
                    eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<InboundSlipLine> result = inboundSlipService.findResults(
                    1L, null, null, null, null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
            verify(businessDateProvider).today();
        }

        @Test
        @DisplayName("フィルタ付きで検索できる")
        void findResults_withFilters() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 22));
            when(inboundSlipLineRepository.searchResults(
                    eq(1L), any(), any(), eq(5L), eq("INB-2026"), eq("PRD"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<InboundSlipLine> result = inboundSlipService.findResults(
                    1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 22),
                    5L, "INB-2026", "PRD", PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("LIKE特殊文字がエスケープされる")
        void findResults_escapesLikeChars() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 22));
            when(inboundSlipLineRepository.searchResults(
                    eq(1L), any(), any(), any(), eq("INB\\%"), eq("PRD\\_"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<InboundSlipLine> result = inboundSlipService.findResults(
                    1L, null, null, null, "INB%", "PRD_", PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("storedDateFromのみ指定した場合storedDateToはデフォルトになる")
        void findResults_onlyFromDate() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 22));
            when(inboundSlipLineRepository.searchResults(
                    eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<InboundSlipLine> result = inboundSlipService.findResults(
                    1L, LocalDate.of(2026, 3, 10), null, null, null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
            verify(businessDateProvider).today();
        }

        @Test
        @DisplayName("storedDateToのみ指定した場合storedDateFromはデフォルトになる")
        void findResults_onlyToDate() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 22));
            when(inboundSlipLineRepository.searchResults(
                    eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<InboundSlipLine> result = inboundSlipService.findResults(
                    1L, null, LocalDate.of(2026, 3, 15), null, null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
            verify(businessDateProvider).today();
        }
    }
}
