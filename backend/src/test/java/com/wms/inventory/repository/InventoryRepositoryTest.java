package com.wms.inventory.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InventoryRepository JPAスライステスト。
 * BR-001 (#402): 「在庫レコードはあるが quantity=0」を在庫なしと判定するロジック
 * （existsByWarehouseIdWithPositiveQty / existsByLocationIdWithPositiveQty）
 * の境界値を検証する。
 *
 * <p>Service層単体テストではRepositoryをMock化しているため、
 * SQL述語 `quantity > 0` の境界値（quantity=0 / quantity=1 / 混在）が
 * 実走されない問題に対応するため、@DataJpaTestで実DB（H2 PostgreSQLモード）に対して検証する。</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@DisplayName("InventoryRepository")
class InventoryRepositoryTest {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long WAREHOUSE_A = 1L;
    private static final long WAREHOUSE_B = 2L;
    private static final long LOCATION_A = 1L;
    private static final long LOCATION_B = 2L;
    private static final long PRODUCT_ID = 1L;

    @BeforeEach
    void setUp() {
        // クリーンアップ
        jdbcTemplate.execute("DELETE FROM inventories");
    }

    // ============================================================
    // existsByWarehouseIdWithPositiveQty: quantity > 0 境界値
    // ============================================================

    @Nested
    @DisplayName("existsByWarehouseIdWithPositiveQty (BR-001 境界値)")
    class ExistsByWarehouseIdWithPositiveQty {

        @Test
        @DisplayName("quantity=0 の在庫1件のみ → false")
        void onlyZeroQty_returnsFalse() {
            insertInventory(WAREHOUSE_A, LOCATION_A, "PIECE", "L01", 0);

            boolean result = inventoryRepository.existsByWarehouseIdWithPositiveQty(WAREHOUSE_A);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("quantity=1 の在庫1件 → true（>0 の最小境界）")
        void exactlyOneQty_returnsTrue() {
            insertInventory(WAREHOUSE_A, LOCATION_A, "PIECE", "L01", 1);

            boolean result = inventoryRepository.existsByWarehouseIdWithPositiveQty(WAREHOUSE_A);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("quantity=0 と quantity=1 が混在 → true")
        void mixedZeroAndPositive_returnsTrue() {
            insertInventory(WAREHOUSE_A, LOCATION_A, "PIECE", "L01", 0);
            insertInventory(WAREHOUSE_A, LOCATION_B, "PIECE", "L02", 1);

            boolean result = inventoryRepository.existsByWarehouseIdWithPositiveQty(WAREHOUSE_A);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("対象倉庫に在庫が一切ない → false")
        void noInventory_returnsFalse() {
            // 別倉庫に正の在庫があっても対象倉庫には無いケース
            insertInventory(WAREHOUSE_B, LOCATION_A, "PIECE", "L01", 10);

            boolean result = inventoryRepository.existsByWarehouseIdWithPositiveQty(WAREHOUSE_A);

            assertThat(result).isFalse();
        }
    }

    // ============================================================
    // existsByLocationIdWithPositiveQty: quantity > 0 境界値
    // ============================================================

    @Nested
    @DisplayName("existsByLocationIdWithPositiveQty (BR-001 境界値)")
    class ExistsByLocationIdWithPositiveQty {

        @Test
        @DisplayName("quantity=0 の在庫1件のみ → false")
        void onlyZeroQty_returnsFalse() {
            insertInventory(WAREHOUSE_A, LOCATION_A, "PIECE", "L01", 0);

            boolean result = inventoryRepository.existsByLocationIdWithPositiveQty(LOCATION_A);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("quantity=1 の在庫1件 → true（>0 の最小境界）")
        void exactlyOneQty_returnsTrue() {
            insertInventory(WAREHOUSE_A, LOCATION_A, "PIECE", "L01", 1);

            boolean result = inventoryRepository.existsByLocationIdWithPositiveQty(LOCATION_A);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("quantity=0 と quantity=1 が同一ロケーションに混在 → true")
        void mixedZeroAndPositive_returnsTrue() {
            // 同一ロケーションでロット違いの2レコード
            insertInventory(WAREHOUSE_A, LOCATION_A, "PIECE", "L01", 0);
            insertInventory(WAREHOUSE_A, LOCATION_A, "PIECE", "L02", 1);

            boolean result = inventoryRepository.existsByLocationIdWithPositiveQty(LOCATION_A);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("対象ロケーションに在庫が一切ない → false")
        void noInventory_returnsFalse() {
            // 別ロケーションに正の在庫があっても対象ロケには無いケース
            insertInventory(WAREHOUSE_A, LOCATION_B, "PIECE", "L01", 10);

            boolean result = inventoryRepository.existsByLocationIdWithPositiveQty(LOCATION_A);

            assertThat(result).isFalse();
        }
    }

    // --- Helper ---

    private void insertInventory(long warehouseId, long locationId, String unitType,
                                  String lotNumber, int quantity) {
        jdbcTemplate.update("""
                INSERT INTO inventories (warehouse_id, location_id, product_id, unit_type,
                                          lot_number, quantity, allocated_qty, version, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 0, 0, CURRENT_TIMESTAMP)
                """, warehouseId, locationId, PRODUCT_ID, unitType, lotNumber, quantity);
    }
}
