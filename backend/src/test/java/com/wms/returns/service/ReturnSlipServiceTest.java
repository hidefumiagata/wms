package com.wms.returns.service;

import com.wms.generated.model.CreateReturnRequest;
import com.wms.generated.model.ReturnReason;
import com.wms.generated.model.ReturnType;
import com.wms.inventory.service.InventoryService;
import com.wms.master.entity.Location;
import com.wms.master.entity.Partner;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.LocationService;
import com.wms.master.service.PartnerService;
import com.wms.master.service.ProductService;
import com.wms.master.service.WarehouseService;
import com.wms.returns.entity.ReturnSlip;
import com.wms.returns.repository.ReturnSlipRepository;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.security.WmsUserDetails;
import com.wms.shared.util.BusinessDateProvider;
import com.wms.system.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReturnSlipService")
class ReturnSlipServiceTest {

    @Mock private ReturnSlipRepository returnSlipRepository;
    @Mock private InventoryService inventoryService;
    @Mock private WarehouseService warehouseService;
    @Mock private PartnerService partnerService;
    @Mock private ProductService productService;
    @Mock private LocationService locationService;
    @Mock private BusinessDateProvider businessDateProvider;
    @Mock private UserService userService;

    @InjectMocks private ReturnSlipService returnSlipService;

    private Warehouse warehouse;
    private Product product;
    private Partner partner;
    private Location location;

    private static void setField(Object obj, String fieldName, Object value) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
                return;
            } catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        throw new RuntimeException("Field not found: " + fieldName);
    }

    @BeforeEach
    void setUp() {
        warehouse = new Warehouse();
        setField(warehouse, "id", 1L);
        warehouse.setWarehouseCode("WH-001");
        warehouse.setWarehouseName("東京DC");

        product = new Product();
        setField(product, "id", 10L);
        product.setProductCode("PRD-0001");
        product.setProductName("テスト商品A");
        // Product isActive defaults to true via MasterBaseEntity

        partner = new Partner();
        setField(partner, "id", 20L);
        partner.setPartnerCode("SUP-0001");
        partner.setPartnerName("株式会社ABC商事");

        location = new Location();
        setField(location, "id", 30L);
        location.setLocationCode("A-01-01");
        location.setIsStocktakingLocked(false);
        location.setWarehouseId(1L);
    }

    private void setUpSecurityContext() {
        WmsUserDetails userDetails = new WmsUserDetails(
                100L, "testuser", "password", "WH-001",
                List.of(new SimpleGrantedAuthority("ROLE_WAREHOUSE_STAFF")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    private CreateReturnRequest buildInventoryRequest() {
        return new CreateReturnRequest()
                .returnType(ReturnType.INVENTORY)
                .productId(10L)
                .quantity(5)
                .unitType(CreateReturnRequest.UnitTypeEnum.CASE)
                .locationId(30L)
                .returnReason(ReturnReason.QUALITY_DEFECT);
    }

    private CreateReturnRequest buildInboundRequest() {
        return new CreateReturnRequest()
                .returnType(ReturnType.INBOUND)
                .productId(10L)
                .quantity(5)
                .unitType(CreateReturnRequest.UnitTypeEnum.CASE)
                .returnReason(ReturnReason.DAMAGED);
    }

    private ReturnSlip buildSavedSlip(Long id, String returnNumber, String returnType,
                                       String unitType, String returnReason) {
        ReturnSlip slip = ReturnSlip.builder()
                .returnNumber(returnNumber)
                .returnType(returnType)
                .status("COMPLETED")
                .warehouseId(1L)
                .warehouseCode("WH-001")
                .warehouseName("東京DC")
                .productId(10L)
                .productCode("PRD-0001")
                .productName("テスト商品A")
                .quantity(5)
                .unitType(unitType)
                .returnReason(returnReason)
                .returnDate(LocalDate.of(2026, 3, 18))
                .build();
        setField(slip, "id", id);
        return slip;
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("在庫返品 - 正常系: 在庫減算あり、COMPLETED")
        void create_inventoryReturn_success() {
            setUpSecurityContext();
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 18));
            when(productService.findById(10L)).thenReturn(product);
            when(warehouseService.findByCode("WH-001")).thenReturn(warehouse);
            when(locationService.findById(30L)).thenReturn(location);
            when(returnSlipRepository.findMaxSequenceByDate("20260318")).thenReturn(0);
            ReturnSlip savedSlip = buildSavedSlip(1L, "RTN-S-20260318-0001", "INVENTORY", "CASE", "QUALITY_DEFECT");
            savedSlip = ReturnSlip.builder()
                    .returnNumber("RTN-S-20260318-0001").returnType("INVENTORY").status("COMPLETED")
                    .warehouseId(1L).productId(10L).productCode("PRD-0001").productName("テスト商品A")
                    .quantity(5).unitType("CASE").locationId(30L).returnReason("QUALITY_DEFECT")
                    .returnDate(LocalDate.of(2026, 3, 18)).build();
            setField(savedSlip, "id", 1L);
            when(returnSlipRepository.save(any(ReturnSlip.class))).thenReturn(savedSlip);

            ReturnSlip result = returnSlipService.create(buildInventoryRequest());

            assertThat(result.getReturnNumber()).isEqualTo("RTN-S-20260318-0001");
            assertThat(result.getStatus()).isEqualTo("COMPLETED");
            verify(inventoryService).deductReturnStock(any(InventoryService.DeductReturnCommand.class));
        }

        @Test
        @DisplayName("入荷返品 - 正常系: 在庫操作なし")
        void create_inboundReturn_success() {
            setUpSecurityContext();
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 18));
            when(productService.findById(10L)).thenReturn(product);
            when(warehouseService.findByCode("WH-001")).thenReturn(warehouse);
            when(returnSlipRepository.findMaxSequenceByDate("20260318")).thenReturn(0);
            ReturnSlip savedSlip = buildSavedSlip(2L, "RTN-I-20260318-0001", "INBOUND", "CASE", "DAMAGED");
            when(returnSlipRepository.save(any(ReturnSlip.class))).thenReturn(savedSlip);

            ReturnSlip result = returnSlipService.create(buildInboundRequest());

            assertThat(result.getReturnNumber()).startsWith("RTN-I-");
            verify(inventoryService, never()).deductReturnStock(any());
        }

        @Test
        @DisplayName("出荷返品 - 正常系: 在庫操作なし")
        void create_outboundReturn_success() {
            setUpSecurityContext();
            CreateReturnRequest request = new CreateReturnRequest()
                    .returnType(ReturnType.OUTBOUND)
                    .productId(10L)
                    .quantity(3)
                    .unitType(CreateReturnRequest.UnitTypeEnum.PIECE)
                    .returnReason(ReturnReason.WRONG_DELIVERY)
                    .relatedSlipNumber("OUT-20260318-0001");
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 18));
            when(productService.findById(10L)).thenReturn(product);
            when(warehouseService.findByCode("WH-001")).thenReturn(warehouse);
            when(returnSlipRepository.findMaxSequenceByDate("20260318")).thenReturn(2);
            ReturnSlip savedSlip = ReturnSlip.builder()
                    .returnNumber("RTN-O-20260318-0003").returnType("OUTBOUND").status("COMPLETED")
                    .warehouseId(1L).productId(10L).productCode("PRD-0001").productName("テスト商品A")
                    .quantity(3).unitType("PIECE").returnReason("WRONG_DELIVERY")
                    .returnDate(LocalDate.of(2026, 3, 18)).build();
            setField(savedSlip, "id", 3L);
            when(returnSlipRepository.save(any(ReturnSlip.class))).thenReturn(savedSlip);

            ReturnSlip result = returnSlipService.create(request);

            assertThat(result.getReturnNumber()).isEqualTo("RTN-O-20260318-0003");
            verify(inventoryService, never()).deductReturnStock(any());
        }

        @Test
        @DisplayName("取引先指定あり - 正常系")
        void create_withPartner_success() {
            setUpSecurityContext();
            CreateReturnRequest request = buildInboundRequest().partnerId(20L);
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 18));
            when(productService.findById(10L)).thenReturn(product);
            when(partnerService.findById(20L)).thenReturn(partner);
            when(warehouseService.findByCode("WH-001")).thenReturn(warehouse);
            when(returnSlipRepository.findMaxSequenceByDate("20260318")).thenReturn(0);
            when(returnSlipRepository.save(any(ReturnSlip.class))).thenAnswer(inv -> {
                ReturnSlip s = inv.getArgument(0);
                setField(s, "id", 4L);
                return s;
            });

            ReturnSlip result = returnSlipService.create(request);

            assertThat(result.getPartnerCode()).isEqualTo("SUP-0001");
            assertThat(result.getPartnerName()).isEqualTo("株式会社ABC商事");
        }

        @Test
        @DisplayName("在庫返品 - locationId未指定: VALIDATION_ERROR")
        void create_inventoryWithoutLocation_throwsValidation() {
            setUpSecurityContext();
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 18));
            CreateReturnRequest request = new CreateReturnRequest()
                    .returnType(ReturnType.INVENTORY)
                    .productId(10L)
                    .quantity(5)
                    .unitType(CreateReturnRequest.UnitTypeEnum.CASE)
                    .returnReason(ReturnReason.QUALITY_DEFECT);

            assertThatThrownBy(() -> returnSlipService.create(request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("ロケーションID");
        }

        @Test
        @DisplayName("返品理由OTHER - 備考未入力: VALIDATION_ERROR")
        void create_otherReasonWithoutNote_throwsValidation() {
            setUpSecurityContext();
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 18));
            CreateReturnRequest request = buildInboundRequest()
                    .returnReason(ReturnReason.OTHER);

            assertThatThrownBy(() -> returnSlipService.create(request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("備考");
        }

        @Test
        @DisplayName("返品理由OTHER - 備考空白: VALIDATION_ERROR")
        void create_otherReasonWithBlankNote_throwsValidation() {
            setUpSecurityContext();
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 18));
            CreateReturnRequest request = buildInboundRequest()
                    .returnReason(ReturnReason.OTHER)
                    .returnReasonNote("   ");

            assertThatThrownBy(() -> returnSlipService.create(request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("備考");
        }

        @Test
        @DisplayName("無効な商品: VALIDATION_ERROR")
        void create_inactiveProduct_throwsValidation() {
            setUpSecurityContext();
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 18));
            product.deactivate();
            when(productService.findById(10L)).thenReturn(product);

            assertThatThrownBy(() -> returnSlipService.create(buildInboundRequest()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("無効な商品");
        }

        @Test
        @DisplayName("棚卸ロック中: RETURN_STOCKTAKE_LOCKED")
        void create_stocktakeLocked_throwsBusinessRule() {
            setUpSecurityContext();
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 18));
            when(productService.findById(10L)).thenReturn(product);
            when(warehouseService.findByCode("WH-001")).thenReturn(warehouse);
            location.setIsStocktakingLocked(true);
            when(locationService.findById(30L)).thenReturn(location);

            assertThatThrownBy(() -> returnSlipService.create(buildInventoryRequest()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("棚卸ロック中");
        }

        @Test
        @DisplayName("伝票番号衝突 - リトライ成功")
        void create_slipNumberCollision_retrySuccess() {
            setUpSecurityContext();
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 18));
            when(productService.findById(10L)).thenReturn(product);
            when(warehouseService.findByCode("WH-001")).thenReturn(warehouse);
            when(returnSlipRepository.findMaxSequenceByDate("20260318")).thenReturn(0).thenReturn(1);
            ReturnSlip savedSlip = buildSavedSlip(5L, "RTN-I-20260318-0002", "INBOUND", "CASE", "DAMAGED");
            when(returnSlipRepository.save(any(ReturnSlip.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"))
                    .thenReturn(savedSlip);

            ReturnSlip result = returnSlipService.create(buildInboundRequest());

            assertThat(result.getReturnNumber()).isEqualTo("RTN-I-20260318-0002");
        }

        @Test
        @DisplayName("返品理由OTHER - 備考あり: 正常")
        void create_otherReasonWithNote_success() {
            setUpSecurityContext();
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 18));
            when(productService.findById(10L)).thenReturn(product);
            when(warehouseService.findByCode("WH-001")).thenReturn(warehouse);
            when(returnSlipRepository.findMaxSequenceByDate("20260318")).thenReturn(0);
            CreateReturnRequest request = buildInboundRequest()
                    .returnReason(ReturnReason.OTHER)
                    .returnReasonNote("特殊な理由");
            ReturnSlip savedSlip = ReturnSlip.builder()
                    .returnNumber("RTN-I-20260318-0001").returnType("INBOUND").status("COMPLETED")
                    .warehouseId(1L).productId(10L).productCode("PRD-0001").productName("テスト商品A")
                    .quantity(5).unitType("CASE").returnReason("OTHER").returnReasonNote("特殊な理由")
                    .returnDate(LocalDate.of(2026, 3, 18)).build();
            setField(savedSlip, "id", 6L);
            when(returnSlipRepository.save(any(ReturnSlip.class))).thenReturn(savedSlip);

            ReturnSlip result = returnSlipService.create(request);

            assertThat(result.getReturnReasonNote()).isEqualTo("特殊な理由");
        }

        @Test
        @DisplayName("Entity保存時にマスタ情報がコピーされる")
        void create_copiesMasterData() {
            setUpSecurityContext();
            when(businessDateProvider.today()).thenReturn(LocalDate.of(2026, 3, 18));
            when(productService.findById(10L)).thenReturn(product);
            when(partnerService.findById(20L)).thenReturn(partner);
            when(warehouseService.findByCode("WH-001")).thenReturn(warehouse);
            when(returnSlipRepository.findMaxSequenceByDate("20260318")).thenReturn(0);
            when(returnSlipRepository.save(any(ReturnSlip.class))).thenAnswer(inv -> {
                ReturnSlip s = inv.getArgument(0);
                setField(s, "id", 7L);
                return s;
            });

            CreateReturnRequest request = buildInboundRequest().partnerId(20L);
            returnSlipService.create(request);

            ArgumentCaptor<ReturnSlip> captor = ArgumentCaptor.forClass(ReturnSlip.class);
            verify(returnSlipRepository).save(captor.capture());
            ReturnSlip saved = captor.getValue();
            assertThat(saved.getWarehouseCode()).isEqualTo("WH-001");
            assertThat(saved.getWarehouseName()).isEqualTo("東京DC");
            assertThat(saved.getProductCode()).isEqualTo("PRD-0001");
            assertThat(saved.getProductName()).isEqualTo("テスト商品A");
            assertThat(saved.getPartnerCode()).isEqualTo("SUP-0001");
            assertThat(saved.getPartnerName()).isEqualTo("株式会社ABC商事");
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("正常系: 条件付き検索")
        void search_withConditions_success() {
            Pageable pageable = PageRequest.of(0, 20);
            when(warehouseService.findById(1L)).thenReturn(warehouse);
            Page<ReturnSlip> page = new PageImpl<>(List.of(), pageable, 0);
            when(returnSlipRepository.search(eq(1L), eq("INVENTORY"),
                    any(), any(), any(), any(), any(), eq(pageable))).thenReturn(page);

            Page<ReturnSlip> result = returnSlipService.search(
                    1L, "INVENTORY", null, null, null, null, null, pageable);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("returnDateFromがreturnDateToより後: VALIDATION_ERROR")
        void search_fromAfterTo_throwsValidation() {
            when(warehouseService.findById(1L)).thenReturn(warehouse);
            LocalDate from = LocalDate.of(2026, 3, 20);
            LocalDate to = LocalDate.of(2026, 3, 18);

            assertThatThrownBy(() -> returnSlipService.search(
                    1L, null, from, to, null, null, null, PageRequest.of(0, 20)))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("返品日");
        }

        @Test
        @DisplayName("returnDateFrom指定、returnDateTo未指定: 正常")
        void search_fromOnlyNoTo_success() {
            Pageable pageable = PageRequest.of(0, 20);
            when(warehouseService.findById(1L)).thenReturn(warehouse);
            Page<ReturnSlip> page = new PageImpl<>(List.of(), pageable, 0);
            when(returnSlipRepository.search(eq(1L), any(), any(), any(), any(), any(), any(), eq(pageable)))
                    .thenReturn(page);

            Page<ReturnSlip> result = returnSlipService.search(
                    1L, null, LocalDate.of(2026, 3, 1), null, null, null, null, pageable);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("returnDateTo指定、returnDateFrom未指定: 正常")
        void search_toOnlyNoFrom_success() {
            Pageable pageable = PageRequest.of(0, 20);
            when(warehouseService.findById(1L)).thenReturn(warehouse);
            Page<ReturnSlip> page = new PageImpl<>(List.of(), pageable, 0);
            when(returnSlipRepository.search(eq(1L), any(), any(), any(), any(), any(), any(), eq(pageable)))
                    .thenReturn(page);

            Page<ReturnSlip> result = returnSlipService.search(
                    1L, null, null, LocalDate.of(2026, 3, 31), null, null, null, pageable);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("両日付指定(From <= To): 正常")
        void search_validDateRange_success() {
            Pageable pageable = PageRequest.of(0, 20);
            when(warehouseService.findById(1L)).thenReturn(warehouse);
            Page<ReturnSlip> page = new PageImpl<>(List.of(), pageable, 0);
            when(returnSlipRepository.search(eq(1L), any(), any(), any(), any(), any(), any(), eq(pageable)))
                    .thenReturn(page);

            Page<ReturnSlip> result = returnSlipService.search(
                    1L, null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                    null, null, null, pageable);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("正常系: 全条件null")
        void search_allNullConditions_success() {
            Pageable pageable = PageRequest.of(0, 20);
            when(warehouseService.findById(1L)).thenReturn(warehouse);
            Page<ReturnSlip> page = new PageImpl<>(List.of(), pageable, 0);
            when(returnSlipRepository.search(eq(1L), eq(null),
                    eq(null), eq(null), eq(null), eq(null), eq(null), eq(pageable)))
                    .thenReturn(page);

            Page<ReturnSlip> result = returnSlipService.search(
                    1L, null, null, null, null, null, null, pageable);

            assertThat(result.getTotalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("resolveUserName")
    class ResolveUserName {

        @Test
        @DisplayName("正常系: ユーザー名解決")
        void resolveUserName_success() {
            when(userService.getUserFullName(100L)).thenReturn("山田 太郎");

            String name = returnSlipService.resolveUserName(100L);

            assertThat(name).isEqualTo("山田 太郎");
        }
    }
}
