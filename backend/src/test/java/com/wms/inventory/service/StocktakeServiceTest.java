package com.wms.inventory.service;

import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryMovement;
import com.wms.inventory.entity.StocktakeHeader;
import com.wms.inventory.entity.StocktakeLine;
import com.wms.inventory.repository.InventoryMovementRepository;
import com.wms.inventory.repository.InventoryRepository;
import com.wms.inventory.repository.StocktakeHeaderRepository;
import com.wms.inventory.repository.StocktakeLineRepository;
import com.wms.master.entity.Area;
import com.wms.master.entity.Building;
import com.wms.master.entity.Location;
import com.wms.master.entity.Warehouse;
import com.wms.master.entity.Product;
import com.wms.master.repository.LocationRepository;
import com.wms.master.service.AreaService;
import com.wms.master.service.BuildingService;
import com.wms.master.service.ProductService;
import com.wms.master.service.WarehouseService;
import com.wms.shared.exception.BusinessRuleViolationException;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StocktakeService")
class StocktakeServiceTest {

    @Mock private StocktakeHeaderRepository headerRepository;
    @Mock private StocktakeLineRepository lineRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryMovementRepository inventoryMovementRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private WarehouseService warehouseService;
    @Mock private BuildingService buildingService;
    @Mock private AreaService areaService;
    @Mock private ProductService productService;
    @InjectMocks private StocktakeService service;

    @AfterEach void tearDown() { SecurityContextHolder.clearContext(); }

    void setUpSecurity() {
        WmsUserDetails ud = new WmsUserDetails(10L, "user", "pw", "WH-001",
                List.of(new SimpleGrantedAuthority("ROLE_WAREHOUSE_MANAGER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities()));
    }

    static void setField(Object obj, String name, Object value) {
        Class<?> c = obj.getClass();
        while (c != null) {
            try { var f = c.getDeclaredField(name); f.setAccessible(true); f.set(obj, value); return; }
            catch (NoSuchFieldException e) { c = c.getSuperclass(); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        throw new RuntimeException("Field not found: " + name);
    }

    Location loc(Long id, String code, boolean locked) {
        Location l = new Location(); setField(l, "id", id);
        l.setLocationCode(code); l.setWarehouseId(1L); l.setIsStocktakingLocked(locked); return l;
    }

    Building building(Long id, String name) {
        Building b = new Building(); setField(b, "id", id); b.setBuildingName(name); return b;
    }

    Area area(Long id, Long buildingId, String name) {
        Area a = new Area(); setField(a, "id", id); a.setBuildingId(buildingId); a.setAreaName(name); return a;
    }

    StocktakeHeader headerWithLines(Long id, String status, List<StocktakeLine> lines) {
        StocktakeHeader h = StocktakeHeader.builder()
                .stocktakeNumber("ST-2026-00001")
                .warehouseId(1L)
                .status(status)
                .stocktakeDate(LocalDate.of(2026, 3, 13))
                .startedAt(java.time.OffsetDateTime.now())
                .startedBy(10L)
                .build();
        setField(h, "id", id);
        // Use reflection to set the lines list directly since builder default may not work with setField
        for (StocktakeLine line : lines) {
            h.addLine(line);
        }
        return h;
    }

    StocktakeLine line(Long id, Long locationId, String locationCode, Long productId,
                       String productCode, String productName, String unitType,
                       String lotNumber, LocalDate expiryDate, int qtyBefore,
                       Integer qtyCounted, boolean isCounted) {
        StocktakeLine l = StocktakeLine.builder()
                .locationId(locationId)
                .locationCode(locationCode)
                .productId(productId)
                .productCode(productCode)
                .productName(productName)
                .unitType(unitType)
                .lotNumber(lotNumber)
                .expiryDate(expiryDate)
                .quantityBefore(qtyBefore)
                .quantityCounted(qtyCounted)
                .isCounted(isCounted)
                .build();
        setField(l, "id", id);
        return l;
    }

    // =========================================================================
    // startStocktake
    // =========================================================================

    @Nested
    @DisplayName("startStocktake")
    class StartStocktake {

        @Test @DisplayName("正常系: 棚卸開始（棟全体）")
        void start_wholeBldg_success() {
            setUpSecurity();
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            when(buildingService.findById(2L)).thenReturn(building(2L, "A棟"));
            Location l1 = loc(10L, "A-01-01", false);
            when(locationRepository.findActiveByWarehouseAndBuilding(1L, 2L, null)).thenReturn(List.of(l1));

            Inventory inv = Inventory.builder().warehouseId(1L).locationId(10L).productId(100L)
                    .unitType("CASE").quantity(5).allocatedQty(0).build();
            setField(inv, "id", 1L);
            Product prod = new Product(); setField(prod, "id", 100L);
            prod.setProductCode("P-001"); prod.setProductName("テスト商品");
            when(inventoryRepository.findByLocationIdsWithPositiveQty(any())).thenReturn(List.of(inv));
            when(productService.findAllByIds(any())).thenReturn(List.of(prod));

            when(headerRepository.findMaxSequenceByYear("2026")).thenReturn(null);
            when(headerRepository.save(any(StocktakeHeader.class))).thenAnswer(i -> {
                StocktakeHeader h = i.getArgument(0); setField(h, "id", 42L); return h;
            });
            when(locationRepository.saveAll(any())).thenReturn(List.of());

            var result = service.startStocktake(1L, 2L, null, LocalDate.of(2026, 3, 13), "月次");

            assertThat(result.stocktakeNumber()).isEqualTo("ST-2026-00001");
            assertThat(result.targetDescription()).isEqualTo("A棟 全エリア");
            assertThat(result.totalLines()).isEqualTo(1);
            verify(locationRepository).saveAll(any());

            // buildingId がヘッダに保存されていることを検証
            verify(headerRepository).save(argThat(h -> h.getBuildingId().equals(2L)));
        }

        @Test @DisplayName("正常系: 棚卸開始（エリア指定）")
        void start_areaSpecified_success() {
            setUpSecurity();
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            when(buildingService.findById(2L)).thenReturn(building(2L, "A棟"));
            when(areaService.findById(5L)).thenReturn(area(5L, 2L, "冷蔵エリア"));
            Location l1 = loc(10L, "A-01-01", false);
            when(locationRepository.findActiveByWarehouseAndBuilding(1L, 2L, 5L)).thenReturn(List.of(l1));

            when(inventoryRepository.findByLocationIdsWithPositiveQty(any())).thenReturn(List.of());
            when(productService.findAllByIds(any())).thenReturn(List.of());
            when(headerRepository.findMaxSequenceByYear("2026")).thenReturn(3);
            when(headerRepository.save(any())).thenAnswer(i -> { StocktakeHeader h = i.getArgument(0); setField(h, "id", 42L); return h; });
            when(locationRepository.saveAll(any())).thenReturn(List.of());

            var result = service.startStocktake(1L, 2L, 5L, LocalDate.of(2026, 3, 13), null);

            assertThat(result.stocktakeNumber()).isEqualTo("ST-2026-00004");
            assertThat(result.targetDescription()).isEqualTo("A棟 冷蔵エリア");
        }

        @Test @DisplayName("エリアが棟に属さない場合エラー")
        void start_areaMismatch_throws() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            when(buildingService.findById(2L)).thenReturn(building(2L, "A棟"));
            when(areaService.findById(5L)).thenReturn(area(5L, 99L, "他棟エリア"));

            assertThatThrownBy(() -> service.startStocktake(1L, 2L, 5L, LocalDate.of(2026, 3, 13), null))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("AREA_NOT_FOUND");
        }

        @Test @DisplayName("対象ロケーションなしの場合エラー")
        void start_noLocations_throws() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            when(buildingService.findById(2L)).thenReturn(building(2L, "A棟"));
            when(locationRepository.findActiveByWarehouseAndBuilding(1L, 2L, null)).thenReturn(List.of());

            assertThatThrownBy(() -> service.startStocktake(1L, 2L, null, LocalDate.of(2026, 3, 13), null))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
        }

        @Test @DisplayName("棚卸ロック中のロケーションがある場合エラー")
        void start_locked_throws() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            when(buildingService.findById(2L)).thenReturn(building(2L, "A棟"));
            when(locationRepository.findActiveByWarehouseAndBuilding(1L, 2L, null))
                    .thenReturn(List.of(loc(10L, "A-01-01", true)));

            assertThatThrownBy(() -> service.startStocktake(1L, 2L, null, LocalDate.of(2026, 3, 13), null))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("INVENTORY_STOCKTAKE_IN_PROGRESS");
        }

        @Test @DisplayName("在庫明細が2000行超の場合エラー")
        void start_tooManyLines_throws() {
            setUpSecurity();
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            when(buildingService.findById(2L)).thenReturn(building(2L, "A棟"));
            Location l1 = loc(10L, "A-01-01", false);
            when(locationRepository.findActiveByWarehouseAndBuilding(1L, 2L, null)).thenReturn(List.of(l1));

            // Create 2001 inventory items
            List<Inventory> bigList = new ArrayList<>();
            for (int i = 0; i < 2001; i++) {
                Inventory inv = Inventory.builder().warehouseId(1L).locationId(10L).productId((long) i)
                        .unitType("CASE").quantity(1).allocatedQty(0).build();
                setField(inv, "id", (long) (i + 1));
                bigList.add(inv);
            }
            when(inventoryRepository.findByLocationIdsWithPositiveQty(any())).thenReturn(bigList);

            assertThatThrownBy(() -> service.startStocktake(1L, 2L, null, LocalDate.of(2026, 3, 13), null))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
        }

        @Test @DisplayName("商品マップにない在庫の場合、商品コード・名が空文字になる")
        void start_productNotInMap_usesEmptyStrings() {
            setUpSecurity();
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            when(buildingService.findById(2L)).thenReturn(building(2L, "A棟"));
            Location l1 = loc(10L, "A-01-01", false);
            when(locationRepository.findActiveByWarehouseAndBuilding(1L, 2L, null)).thenReturn(List.of(l1));

            // Inventory with productId=999, but productService returns empty list
            Inventory inv = Inventory.builder().warehouseId(1L).locationId(10L).productId(999L)
                    .unitType("CASE").quantity(3).allocatedQty(0).build();
            setField(inv, "id", 1L);
            when(inventoryRepository.findByLocationIdsWithPositiveQty(any())).thenReturn(List.of(inv));
            when(productService.findAllByIds(any())).thenReturn(List.of()); // no product found

            when(headerRepository.findMaxSequenceByYear("2026")).thenReturn(null);
            when(headerRepository.save(any(StocktakeHeader.class))).thenAnswer(i -> {
                StocktakeHeader h = i.getArgument(0); setField(h, "id", 42L); return h;
            });
            when(locationRepository.saveAll(any())).thenReturn(List.of());

            var result = service.startStocktake(1L, 2L, null, LocalDate.of(2026, 3, 13), null);

            assertThat(result.totalLines()).isEqualTo(1);
        }
    }

    // =========================================================================
    // saveStocktakeLines
    // =========================================================================

    @Nested
    @DisplayName("saveStocktakeLines")
    class SaveStocktakeLines {

        @Test @DisplayName("正常系: 明細の実数入力")
        void saveLines_success() {
            setUpSecurity();
            StocktakeHeader header = StocktakeHeader.builder()
                    .stocktakeNumber("ST-2026-00001").warehouseId(1L)
                    .status("STARTED").stocktakeDate(LocalDate.of(2026, 3, 13))
                    .startedAt(java.time.OffsetDateTime.now()).startedBy(10L).build();
            setField(header, "id", 1L);
            when(headerRepository.findById(1L)).thenReturn(Optional.of(header));

            StocktakeLine sl = line(10L, 100L, "A-01-01", 200L, "P-001", "商品A",
                    "CASE", null, null, 5, null, false);
            sl.setStocktakeHeader(header);
            when(lineRepository.findById(10L)).thenReturn(Optional.of(sl));
            when(headerRepository.save(any())).thenReturn(header);
            when(lineRepository.countByHeaderId(1L)).thenReturn(3L);
            when(lineRepository.countCountedByHeaderId(1L)).thenReturn(1L);

            var inputs = List.of(new StocktakeService.LineInput(10L, 7));
            var result = service.saveStocktakeLines(1L, inputs);

            assertThat(result.updatedCount()).isEqualTo(1);
            assertThat(result.totalLines()).isEqualTo(3);
            assertThat(result.countedLines()).isEqualTo(1);
            assertThat(sl.getQuantityCounted()).isEqualTo(7);
            assertThat(sl.isCounted()).isTrue();
        }

        @Test @DisplayName("inputsがnullの場合エラー")
        void saveLines_nullInputs_throws() {
            assertThatThrownBy(() -> service.saveStocktakeLines(1L, null))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
        }

        @Test @DisplayName("inputsが空の場合エラー")
        void saveLines_emptyInputs_throws() {
            assertThatThrownBy(() -> service.saveStocktakeLines(1L, List.of()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
        }

        @Test @DisplayName("棚卸ヘッダが見つからない場合エラー")
        void saveLines_headerNotFound_throws() {
            when(headerRepository.findById(999L)).thenReturn(Optional.empty());

            var inputs = List.of(new StocktakeService.LineInput(10L, 5));
            assertThatThrownBy(() -> service.saveStocktakeLines(999L, inputs))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("STOCKTAKE_NOT_FOUND");
        }

        @Test @DisplayName("棚卸ステータスがSTARTEDでない場合エラー")
        void saveLines_invalidStatus_throws() {
            StocktakeHeader header = StocktakeHeader.builder()
                    .stocktakeNumber("ST-2026-00001").warehouseId(1L)
                    .status("CONFIRMED").stocktakeDate(LocalDate.of(2026, 3, 13))
                    .startedAt(java.time.OffsetDateTime.now()).startedBy(10L).build();
            setField(header, "id", 1L);
            when(headerRepository.findById(1L)).thenReturn(Optional.of(header));

            var inputs = List.of(new StocktakeService.LineInput(10L, 5));
            assertThatThrownBy(() -> service.saveStocktakeLines(1L, inputs))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("STOCKTAKE_INVALID_STATUS");
        }

        @Test @DisplayName("実数が負の場合エラー")
        void saveLines_negativeQty_throws() {
            setUpSecurity();
            StocktakeHeader header = StocktakeHeader.builder()
                    .stocktakeNumber("ST-2026-00001").warehouseId(1L)
                    .status("STARTED").stocktakeDate(LocalDate.of(2026, 3, 13))
                    .startedAt(java.time.OffsetDateTime.now()).startedBy(10L).build();
            setField(header, "id", 1L);
            when(headerRepository.findById(1L)).thenReturn(Optional.of(header));

            var inputs = List.of(new StocktakeService.LineInput(10L, -1));
            assertThatThrownBy(() -> service.saveStocktakeLines(1L, inputs))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
        }

        @Test @DisplayName("明細が見つからない場合エラー")
        void saveLines_lineNotFound_throws() {
            setUpSecurity();
            StocktakeHeader header = StocktakeHeader.builder()
                    .stocktakeNumber("ST-2026-00001").warehouseId(1L)
                    .status("STARTED").stocktakeDate(LocalDate.of(2026, 3, 13))
                    .startedAt(java.time.OffsetDateTime.now()).startedBy(10L).build();
            setField(header, "id", 1L);
            when(headerRepository.findById(1L)).thenReturn(Optional.of(header));
            when(lineRepository.findById(999L)).thenReturn(Optional.empty());

            var inputs = List.of(new StocktakeService.LineInput(999L, 5));
            assertThatThrownBy(() -> service.saveStocktakeLines(1L, inputs))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("STOCKTAKE_LINE_NOT_FOUND");
        }

        @Test @DisplayName("明細が指定棚卸に属していない場合エラー")
        void saveLines_lineBelongsToDifferentHeader_throws() {
            setUpSecurity();
            StocktakeHeader header = StocktakeHeader.builder()
                    .stocktakeNumber("ST-2026-00001").warehouseId(1L)
                    .status("STARTED").stocktakeDate(LocalDate.of(2026, 3, 13))
                    .startedAt(java.time.OffsetDateTime.now()).startedBy(10L).build();
            setField(header, "id", 1L);
            when(headerRepository.findById(1L)).thenReturn(Optional.of(header));

            // Line belongs to header id=99, not id=1
            StocktakeHeader otherHeader = StocktakeHeader.builder()
                    .stocktakeNumber("ST-2026-00099").warehouseId(1L)
                    .status("STARTED").stocktakeDate(LocalDate.of(2026, 3, 13))
                    .startedAt(java.time.OffsetDateTime.now()).startedBy(10L).build();
            setField(otherHeader, "id", 99L);

            StocktakeLine sl = line(10L, 100L, "A-01-01", 200L, "P-001", "商品A",
                    "CASE", null, null, 5, null, false);
            sl.setStocktakeHeader(otherHeader);
            when(lineRepository.findById(10L)).thenReturn(Optional.of(sl));

            var inputs = List.of(new StocktakeService.LineInput(10L, 5));
            assertThatThrownBy(() -> service.saveStocktakeLines(1L, inputs))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("STOCKTAKE_LINE_NOT_FOUND");
        }
    }

    // =========================================================================
    // confirmStocktake
    // =========================================================================

    @Nested
    @DisplayName("confirmStocktake")
    class ConfirmStocktake {

        @Test @DisplayName("正常系: 棚卸確定（差異あり、在庫ロック成功）")
        void confirm_withDiff_success() {
            setUpSecurity();
            StocktakeLine sl = line(10L, 100L, "A-01-01", 200L, "P-001", "商品A",
                    "CASE", "LOT-001", LocalDate.of(2027, 1, 1), 5, 8, true);
            StocktakeHeader header = headerWithLines(1L, "STARTED", List.of(sl));
            when(headerRepository.findByIdWithLines(1L)).thenReturn(Optional.of(header));

            Inventory inv = Inventory.builder().warehouseId(1L).locationId(100L).productId(200L)
                    .unitType("CASE").quantity(5).allocatedQty(0).build();
            setField(inv, "id", 50L);
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    100L, 200L, "CASE", "LOT-001", LocalDate.of(2027, 1, 1)))
                    .thenReturn(Optional.of(inv));
            when(inventoryRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(inv));
            when(inventoryRepository.save(any())).thenReturn(inv);
            when(inventoryMovementRepository.save(any())).thenReturn(null);

            Location loc = loc(100L, "A-01-01", true);
            when(locationRepository.findAllById(any())).thenReturn(List.of(loc));
            when(locationRepository.saveAll(any())).thenReturn(List.of());
            when(headerRepository.save(any())).thenReturn(header);

            var result = service.confirmStocktake(1L);

            assertThat(result.status()).isEqualTo("CONFIRMED");
            assertThat(result.adjustedLines()).isEqualTo(1);
            assertThat(result.totalLines()).isEqualTo(1);
            assertThat(inv.getQuantity()).isEqualTo(8);
            assertThat(loc.getIsStocktakingLocked()).isFalse();
        }

        @Test @DisplayName("正常系: 差異なしの場合、在庫更新されない")
        void confirm_noDiff_noAdjustment() {
            setUpSecurity();
            StocktakeLine sl = line(10L, 100L, "A-01-01", 200L, "P-001", "商品A",
                    "CASE", null, null, 5, 5, true); // same qty
            StocktakeHeader header = headerWithLines(1L, "STARTED", List.of(sl));
            when(headerRepository.findByIdWithLines(1L)).thenReturn(Optional.of(header));

            Location loc = loc(100L, "A-01-01", true);
            when(locationRepository.findAllById(any())).thenReturn(List.of(loc));
            when(locationRepository.saveAll(any())).thenReturn(List.of());
            when(headerRepository.save(any())).thenReturn(header);

            var result = service.confirmStocktake(1L);

            assertThat(result.status()).isEqualTo("CONFIRMED");
            assertThat(result.adjustedLines()).isEqualTo(0);
            verify(inventoryRepository, never()).findByIdForUpdate(anyLong());
        }

        @Test @DisplayName("棚卸が見つからない場合エラー")
        void confirm_notFound_throws() {
            when(headerRepository.findByIdWithLines(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirmStocktake(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("STOCKTAKE_NOT_FOUND");
        }

        @Test @DisplayName("ステータスがSTARTEDでない場合エラー")
        void confirm_invalidStatus_throws() {
            StocktakeHeader header = headerWithLines(1L, "CONFIRMED", List.of());
            when(headerRepository.findByIdWithLines(1L)).thenReturn(Optional.of(header));

            assertThatThrownBy(() -> service.confirmStocktake(1L))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("STOCKTAKE_INVALID_STATUS");
        }

        @Test @DisplayName("未入力の実数がある場合エラー")
        void confirm_notAllCounted_throws() {
            setUpSecurity();
            StocktakeLine sl = line(10L, 100L, "A-01-01", 200L, "P-001", "商品A",
                    "CASE", null, null, 5, null, false); // not counted
            StocktakeHeader header = headerWithLines(1L, "STARTED", List.of(sl));
            when(headerRepository.findByIdWithLines(1L)).thenReturn(Optional.of(header));

            assertThatThrownBy(() -> service.confirmStocktake(1L))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .extracting("errorCode").isEqualTo("INVENTORY_STOCKTAKE_NOT_ALL_COUNTED");
        }

        @Test @DisplayName("差異ありだが在庫レコードが見つからない場合、warn出力してスキップ")
        void confirm_inventoryNotFoundForLine_logsWarning() {
            setUpSecurity();
            StocktakeLine sl = line(10L, 100L, "A-01-01", 200L, "P-001", "商品A",
                    "CASE", null, null, 5, 10, true);
            StocktakeHeader header = headerWithLines(1L, "STARTED", List.of(sl));
            when(headerRepository.findByIdWithLines(1L)).thenReturn(Optional.of(header));

            // inventory not found for the line
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    100L, 200L, "CASE", null, null))
                    .thenReturn(Optional.empty());

            Location loc = loc(100L, "A-01-01", true);
            when(locationRepository.findAllById(any())).thenReturn(List.of(loc));
            when(locationRepository.saveAll(any())).thenReturn(List.of());
            when(headerRepository.save(any())).thenReturn(header);

            var result = service.confirmStocktake(1L);

            assertThat(result.adjustedLines()).isEqualTo(0);
            verify(inventoryRepository, never()).findByIdForUpdate(anyLong());
        }

        @Test @DisplayName("在庫ロックに失敗した場合、warn出力してスキップ（movementは記録）")
        void confirm_lockFailed_logsWarningButRecordsMovement() {
            setUpSecurity();
            StocktakeLine sl = line(10L, 100L, "A-01-01", 200L, "P-001", "商品A",
                    "CASE", "LOT-X", null, 5, 3, true);
            StocktakeHeader header = headerWithLines(1L, "STARTED", List.of(sl));
            when(headerRepository.findByIdWithLines(1L)).thenReturn(Optional.of(header));

            Inventory inv = Inventory.builder().warehouseId(1L).locationId(100L).productId(200L)
                    .unitType("CASE").quantity(5).allocatedQty(0).build();
            setField(inv, "id", 50L);
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    100L, 200L, "CASE", "LOT-X", null))
                    .thenReturn(Optional.of(inv));
            // Lock returns empty
            when(inventoryRepository.findByIdForUpdate(50L)).thenReturn(Optional.empty());
            when(inventoryMovementRepository.save(any())).thenReturn(null);

            Location loc = loc(100L, "A-01-01", true);
            when(locationRepository.findAllById(any())).thenReturn(List.of(loc));
            when(locationRepository.saveAll(any())).thenReturn(List.of());
            when(headerRepository.save(any())).thenReturn(header);

            var result = service.confirmStocktake(1L);

            assertThat(result.adjustedLines()).isEqualTo(1);
            // inventory.save should NOT be called since lock failed
            verify(inventoryRepository, never()).save(any());
            // but movement IS still recorded
            verify(inventoryMovementRepository).save(any(InventoryMovement.class));
        }

        @Test @DisplayName("複数明細: 差異ありと差異なしが混在")
        void confirm_mixedDiffAndNoDiff_success() {
            setUpSecurity();
            // Line with diff (qty 5 -> 8)
            StocktakeLine sl1 = line(10L, 100L, "A-01-01", 200L, "P-001", "商品A",
                    "CASE", null, null, 5, 8, true);
            // Line without diff (qty 3 -> 3)
            StocktakeLine sl2 = line(11L, 101L, "A-01-02", 201L, "P-002", "商品B",
                    "PIECE", null, null, 3, 3, true);
            StocktakeHeader header = headerWithLines(1L, "STARTED", List.of(sl1, sl2));
            when(headerRepository.findByIdWithLines(1L)).thenReturn(Optional.of(header));

            Inventory inv1 = Inventory.builder().warehouseId(1L).locationId(100L).productId(200L)
                    .unitType("CASE").quantity(5).allocatedQty(0).build();
            setField(inv1, "id", 50L);
            when(inventoryRepository.findByLocationIdAndProductIdAndUnitTypeAndLotNumberAndExpiryDate(
                    100L, 200L, "CASE", null, null))
                    .thenReturn(Optional.of(inv1));
            when(inventoryRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(inv1));
            when(inventoryRepository.save(any())).thenReturn(inv1);
            when(inventoryMovementRepository.save(any())).thenReturn(null);

            Location loc1 = loc(100L, "A-01-01", true);
            Location loc2 = loc(101L, "A-01-02", true);
            when(locationRepository.findAllById(any())).thenReturn(List.of(loc1, loc2));
            when(locationRepository.saveAll(any())).thenReturn(List.of());
            when(headerRepository.save(any())).thenReturn(header);

            var result = service.confirmStocktake(1L);

            assertThat(result.adjustedLines()).isEqualTo(1);
            assertThat(result.totalLines()).isEqualTo(2);
            assertThat(loc1.getIsStocktakingLocked()).isFalse();
            assertThat(loc2.getIsStocktakingLocked()).isFalse();
        }
    }
}
