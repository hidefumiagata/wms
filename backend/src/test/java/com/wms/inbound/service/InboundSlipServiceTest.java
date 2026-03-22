package com.wms.inbound.service;

import com.wms.generated.model.CreateInboundLineRequest;
import com.wms.generated.model.CreateInboundSlipRequest;
import com.wms.generated.model.InboundLineStatus;
import com.wms.generated.model.InboundSlipStatus;
import com.wms.generated.model.InboundSlipType;
import com.wms.generated.model.UnitType;
import com.wms.inbound.entity.InboundSlip;
import com.wms.inbound.entity.InboundSlipLine;
import com.wms.inbound.repository.InboundSlipLineRepository;
import com.wms.inbound.repository.InboundSlipRepository;
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
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.DuplicateResourceException;
import com.wms.shared.exception.InvalidStateTransitionException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.security.WmsUserDetails;
import com.wms.shared.util.BusinessDateProvider;
import org.springframework.dao.DataIntegrityViolationException;
import com.wms.system.entity.User;
import com.wms.system.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private InboundSlipService inboundSlipService;

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
        @DisplayName("存在するユーザーIDでフルネームを返す")
        void resolveUserName_exists() {
            User user = new User();
            user.setFullName("山田 太郎");
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));

            assertThat(inboundSlipService.resolveUserName(10L)).isEqualTo("山田 太郎");
        }

        @Test
        @DisplayName("存在しないユーザーIDでnullを返す")
        void resolveUserName_notExists() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThat(inboundSlipService.resolveUserName(999L)).isNull();
        }

        @Test
        @DisplayName("nullのユーザーIDでnullを返す")
        void resolveUserName_null() {
            assertThat(inboundSlipService.resolveUserName(null)).isNull();
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
                    .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
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
                    .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
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

    @Nested
    @DisplayName("cancel")
    class CancelTests {

        @AfterEach
        void clearSecurityContext() {
            SecurityContextHolder.clearContext();
        }

        private void setUpSecurityContext(Long userId) {
            WmsUserDetails userDetails = new WmsUserDetails(
                    userId, "testuser", "password", "WH-001",
                    List.of(new SimpleGrantedAuthority("ROLE_WAREHOUSE_STAFF")));
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        @Test
        @DisplayName("PLANNEDステータスの伝票をキャンセルできる")
        void cancel_planned_success() {
            setUpSecurityContext(10L);
            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.PLANNED.getValue())
                    .warehouseId(1L)
                    .lines(new ArrayList<>())
                    .build();
            setField(slip, "id", 1L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            InboundSlip result = inboundSlipService.cancel(1L);

            assertThat(result.getStatus()).isEqualTo(InboundSlipStatus.CANCELLED.getValue());
            assertThat(result.getCancelledBy()).isEqualTo(10L);
            assertThat(result.getCancelledAt()).isNotNull();
            verify(inboundSlipRepository).save(slip);
        }

        @Test
        @DisplayName("CONFIRMEDステータスの伝票をキャンセルできる")
        void cancel_confirmed_success() {
            setUpSecurityContext(10L);
            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.CONFIRMED.getValue())
                    .warehouseId(1L)
                    .lines(new ArrayList<>())
                    .build();
            setField(slip, "id", 1L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            InboundSlip result = inboundSlipService.cancel(1L);

            assertThat(result.getStatus()).isEqualTo(InboundSlipStatus.CANCELLED.getValue());
        }

        @Test
        @DisplayName("INSPECTINGステータスの伝票をキャンセルできる")
        void cancel_inspecting_success() {
            setUpSecurityContext(10L);
            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260322-0001")
                    .status(InboundSlipStatus.INSPECTING.getValue())
                    .warehouseId(1L)
                    .lines(new ArrayList<>())
                    .build();
            setField(slip, "id", 1L);
            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            InboundSlip result = inboundSlipService.cancel(1L);

            assertThat(result.getStatus()).isEqualTo(InboundSlipStatus.CANCELLED.getValue());
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

            Inventory inventory = Inventory.builder()
                    .warehouseId(1L)
                    .locationId(200L)
                    .productId(100L)
                    .unitType("CASE")
                    .lotNumber("LOT-001")
                    .expiryDate(LocalDate.of(2027, 3, 22))
                    .quantity(148)
                    .allocatedQty(0)
                    .build();
            setField(inventory, "id", 500L);

            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    200L, 100L, "CASE", "LOT-001", LocalDate.of(2027, 3, 22)))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(inventoryMovementRepository.save(any(InventoryMovement.class))).thenAnswer(inv -> inv.getArgument(0));
            when(inboundSlipRepository.save(any(InboundSlip.class))).thenAnswer(inv -> inv.getArgument(0));

            InboundSlip result = inboundSlipService.cancel(1L);

            assertThat(result.getStatus()).isEqualTo(InboundSlipStatus.CANCELLED.getValue());
            assertThat(inventory.getQuantity()).isEqualTo(100); // 148 - 48

            ArgumentCaptor<InventoryMovement> movementCaptor = ArgumentCaptor.forClass(InventoryMovement.class);
            verify(inventoryMovementRepository).save(movementCaptor.capture());
            InventoryMovement savedMovement = movementCaptor.getValue();
            assertThat(savedMovement.getMovementType()).isEqualTo("INBOUND_CANCEL");
            assertThat(savedMovement.getQuantity()).isEqualTo(-48);
            assertThat(savedMovement.getQuantityAfter()).isEqualTo(100);
            assertThat(savedMovement.getWarehouseId()).isEqualTo(1L);
            assertThat(savedMovement.getLocationId()).isEqualTo(200L);
            assertThat(savedMovement.getLocationCode()).isEqualTo("LOC-A01");
            assertThat(savedMovement.getProductId()).isEqualTo(100L);
            assertThat(savedMovement.getProductCode()).isEqualTo("PRD-0001");
            assertThat(savedMovement.getReferenceId()).isEqualTo(1L);
            assertThat(savedMovement.getReferenceType()).isEqualTo("INBOUND_SLIP");
            assertThat(savedMovement.getExecutedBy()).isEqualTo(10L);
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
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    200L, 100L, "CASE", "LOT-001", LocalDate.of(2027, 3, 22)))
                    .thenReturn(Optional.empty());

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

            Inventory inventory = Inventory.builder()
                    .warehouseId(1L).locationId(200L).productId(100L).unitType("CASE")
                    .quantity(10).allocatedQty(0).build(); // quantity < inspectedQty (48)
            setField(inventory, "id", 1L);

            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    200L, 100L, "CASE", null, null)).thenReturn(Optional.of(inventory));

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

            Inventory inventory = Inventory.builder()
                    .warehouseId(1L).locationId(200L).productId(100L).unitType("CASE")
                    .quantity(50).allocatedQty(25).build(); // newQty=20 < allocatedQty=25
            setField(inventory, "id", 1L);

            when(inboundSlipRepository.findByIdWithLines(1L)).thenReturn(Optional.of(slip));
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    200L, 100L, "CASE", null, null)).thenReturn(Optional.of(inventory));

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
}
