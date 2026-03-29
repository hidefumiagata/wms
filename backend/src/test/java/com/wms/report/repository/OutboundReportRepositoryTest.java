package com.wms.report.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OutboundReportRepository JPAスライステスト。
 * H2（PostgreSQLモード）+ Hibernate ddl-auto=create-drop で実行。
 * 特に1つの出荷明細が複数のピッキング指示明細に分割されるケースを検証する。
 */
@DataJpaTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@DisplayName("OutboundReportRepository")
class OutboundReportRepositoryTest {

    @Autowired
    private OutboundReportRepository outboundReportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long outboundSlipId;

    @BeforeEach
    void setUp() {
        // クリーンアップ（依存順）
        jdbcTemplate.execute("DELETE FROM picking_instruction_lines");
        jdbcTemplate.execute("DELETE FROM picking_instructions");
        jdbcTemplate.execute("DELETE FROM outbound_slip_lines");
        jdbcTemplate.execute("DELETE FROM outbound_slips");

        // テスト用ユーザー（FK制約用）
        jdbcTemplate.execute("""
                MERGE INTO users (id, user_code, full_name, email, password_hash, role,
                                   is_active, password_change_required, failed_login_count, locked,
                                   version, created_at, updated_at)
                KEY (id)
                VALUES (1, 'testuser', 'テストユーザー', 'test@example.com',
                        '$2a$12$dummy', 'SYSTEM_ADMIN', true, false, 0, false,
                        0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);

        // 倉庫
        jdbcTemplate.execute("""
                MERGE INTO warehouses (id, warehouse_code, warehouse_name,
                                        is_active, version, created_at, updated_at)
                KEY (id)
                VALUES (1, 'WH-T01', 'テスト倉庫', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);

        // 商品
        jdbcTemplate.execute("""
                MERGE INTO products (id, product_code, product_name, case_quantity, ball_quantity,
                                      storage_condition, is_hazardous, lot_manage_flag,
                                      expiry_manage_flag, shipment_stop_flag,
                                      is_active, version, created_by, updated_by,
                                      created_at, updated_at)
                KEY (id)
                VALUES (1, 'P-001', '商品A', 10, 5, 'AMBIENT', false, false, false, false,
                        true, 0, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.execute("""
                MERGE INTO products (id, product_code, product_name, case_quantity, ball_quantity,
                                      storage_condition, is_hazardous, lot_manage_flag,
                                      expiry_manage_flag, shipment_stop_flag,
                                      is_active, version, created_by, updated_by,
                                      created_at, updated_at)
                KEY (id)
                VALUES (2, 'P-002', '商品B', 10, 5, 'AMBIENT', false, false, false, false,
                        true, 0, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);

        // 棟
        jdbcTemplate.execute("""
                MERGE INTO buildings (id, building_code, building_name, warehouse_id,
                                       is_active, version, created_by, updated_by,
                                       created_at, updated_at)
                KEY (id)
                VALUES (1, 'B1', '第1棟', 1, true, 0, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);

        // エリア
        jdbcTemplate.execute("""
                MERGE INTO areas (id, area_code, area_name, warehouse_id, building_id,
                                   storage_condition, area_type,
                                   is_active, version, created_by, updated_by,
                                   created_at, updated_at)
                KEY (id)
                VALUES (1, 'A1', 'エリア1', 1, 1, 'AMBIENT', 'STOCK',
                        true, 0, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);

        // ロケーション
        jdbcTemplate.execute("""
                MERGE INTO locations (id, location_code, warehouse_id, area_id,
                                       is_active, is_stocktaking_locked, version,
                                       created_by, updated_by, created_at, updated_at)
                KEY (id)
                VALUES (1, 'LOC-001', 1, 1, true, false, 0, 1, 1,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.execute("""
                MERGE INTO locations (id, location_code, warehouse_id, area_id,
                                       is_active, is_stocktaking_locked, version,
                                       created_by, updated_by, created_at, updated_at)
                KEY (id)
                VALUES (2, 'LOC-002', 1, 1, true, false, 0, 1, 1,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);

        // 出荷伝票
        jdbcTemplate.update("""
                INSERT INTO outbound_slips (slip_number, slip_type, warehouse_id, warehouse_code,
                                            warehouse_name, partner_name, planned_date, status,
                                            version, created_by, updated_by,
                                            created_at, updated_at)
                VALUES ('OUT-TEST-001', 'NORMAL', 1, 'WH-T01', 'テスト倉庫', 'テスト出荷先',
                        '2026-03-15', 'INSPECTING', 0, 1, 1,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        outboundSlipId = jdbcTemplate.queryForObject(
                "SELECT id FROM outbound_slips WHERE slip_number = 'OUT-TEST-001'", Long.class);

        // 出荷伝票明細 Line 1: 商品A, 受注数=30, 検品数=28
        jdbcTemplate.update("""
                INSERT INTO outbound_slip_lines (outbound_slip_id, line_no, product_id, product_code,
                                                  product_name, unit_type, ordered_qty, inspected_qty,
                                                  shipped_qty, line_status,
                                                  created_at, updated_at)
                VALUES (?, 1, 1, 'P-001', '商品A', 'CASE', 30, 28,
                        0, 'INSPECTING',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, outboundSlipId);

        Long oslId1 = jdbcTemplate.queryForObject(
                "SELECT id FROM outbound_slip_lines WHERE outbound_slip_id = ? AND line_no = 1",
                Long.class, outboundSlipId);

        // 出荷伝票明細 Line 2: 商品B, 受注数=10, ピッキング指示なし
        jdbcTemplate.update("""
                INSERT INTO outbound_slip_lines (outbound_slip_id, line_no, product_id, product_code,
                                                  product_name, unit_type, ordered_qty,
                                                  shipped_qty, line_status,
                                                  created_at, updated_at)
                VALUES (?, 2, 2, 'P-002', '商品B', 'PIECE', 10,
                        0, 'ORDERED',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, outboundSlipId);

        // ピッキング指示ヘッダ
        jdbcTemplate.update("""
                INSERT INTO picking_instructions (id, instruction_number, warehouse_id, area_id,
                                                   status, created_by, created_at)
                VALUES (1, 'PI-TEST-001', 1, 1, 'COMPLETED', 1, CURRENT_TIMESTAMP)
                """);

        // ピッキング指示明細: 1つの出荷明細（oslId1）を2つのロケーションに分割
        // LOC-001 から 18個ピック
        jdbcTemplate.update("""
                INSERT INTO picking_instruction_lines (picking_instruction_id, line_no,
                    outbound_slip_line_id, location_id, location_code,
                    product_id, product_code, product_name, unit_type,
                    qty_to_pick, qty_picked, line_status,
                    created_at, updated_at)
                VALUES (1, 1, ?, 1, 'LOC-001', 1, 'P-001', '商品A', 'CASE', 20, 18, 'COMPLETED',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, oslId1);

        // LOC-002 から 10個ピック
        jdbcTemplate.update("""
                INSERT INTO picking_instruction_lines (picking_instruction_id, line_no,
                    outbound_slip_line_id, location_id, location_code,
                    product_id, product_code, product_name, unit_type,
                    qty_to_pick, qty_picked, line_status,
                    created_at, updated_at)
                VALUES (1, 2, ?, 2, 'LOC-002', 1, 'P-001', '商品A', 'CASE', 10, 10, 'COMPLETED',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, oslId1);
    }

    @Nested
    @DisplayName("findShippingInspectionReportData")
    class FindShippingInspectionReportData {

        @Test
        @DisplayName("複数ピッキング指示明細のqty_pickedがSUM集計される（18+10=28）")
        void multiplePickingLines_summedCorrectly() {
            List<Object[]> results = outboundReportRepository
                    .findShippingInspectionReportData(outboundSlipId);

            assertThat(results).hasSize(2);

            // 商品コード昇順: P-001が先
            Object[] row1 = results.get(0);
            assertThat(row1[0]).isEqualTo("OUT-TEST-001");                 // slip_number
            assertThat(row1[3]).isEqualTo("P-001");                        // product_code
            assertThat(((Number) row1[6]).intValue()).isEqualTo(28);       // picked_qty = 18 + 10
            assertThat(((Number) row1[7]).intValue()).isEqualTo(28);       // inspected_qty
        }

        @Test
        @DisplayName("ピッキング指示が存在しない出荷明細はpicked_qty=0で返される（COALESCE）")
        void noPickingLines_returnsZeroPickedQty() {
            List<Object[]> results = outboundReportRepository
                    .findShippingInspectionReportData(outboundSlipId);

            Object[] rowP002 = results.stream()
                    .filter(r -> "P-002".equals(r[3]))
                    .findFirst()
                    .orElseThrow();

            assertThat(((Number) rowP002[6]).intValue()).isEqualTo(0);     // picked_qty = 0
            assertThat(rowP002[7]).isNull();                               // inspected_qty = null
        }

        @Test
        @DisplayName("存在しない伝票IDの場合は空リストが返される")
        void nonExistentSlipId_returnsEmpty() {
            List<Object[]> results = outboundReportRepository
                    .findShippingInspectionReportData(99999L);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("部分ピッキング：qty_pickedが指示数より少ないケース")
        void partialPicking_returnsActualPickedQty() {
            jdbcTemplate.update("""
                    INSERT INTO outbound_slips (slip_number, slip_type, warehouse_id, warehouse_code,
                                                warehouse_name, partner_name, planned_date, status,
                                                version, created_by, updated_by,
                                                created_at, updated_at)
                    VALUES ('OUT-TEST-002', 'NORMAL', 1, 'WH-T01', 'テスト倉庫', 'テスト出荷先B',
                            '2026-03-16', 'INSPECTING', 0, 1, 1,
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            Long slipId2 = jdbcTemplate.queryForObject(
                    "SELECT id FROM outbound_slips WHERE slip_number = 'OUT-TEST-002'", Long.class);

            jdbcTemplate.update("""
                    INSERT INTO outbound_slip_lines (outbound_slip_id, line_no, product_id, product_code,
                                                      product_name, unit_type, ordered_qty, inspected_qty,
                                                      shipped_qty, line_status,
                                                      created_at, updated_at)
                    VALUES (?, 1, 1, 'P-001', '商品A', 'CASE', 50, 15,
                            0, 'INSPECTING',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, slipId2);

            Long oslId = jdbcTemplate.queryForObject(
                    "SELECT id FROM outbound_slip_lines WHERE outbound_slip_id = ? AND line_no = 1",
                    Long.class, slipId2);

            // ピッキング指示: 指示数=50 だが実際には15個しかピックしていない
            jdbcTemplate.update("""
                    INSERT INTO picking_instructions (id, instruction_number, warehouse_id, area_id,
                                                       status, created_by, created_at)
                    VALUES (2, 'PI-TEST-002', 1, 1, 'COMPLETED', 1, CURRENT_TIMESTAMP)
                    """);
            jdbcTemplate.update("""
                    INSERT INTO picking_instruction_lines (picking_instruction_id, line_no,
                        outbound_slip_line_id, location_id, location_code,
                        product_id, product_code, product_name, unit_type,
                        qty_to_pick, qty_picked, line_status,
                        created_at, updated_at)
                    VALUES (2, 1, ?, 1, 'LOC-001', 1, 'P-001', '商品A', 'CASE', 50, 15, 'COMPLETED',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, oslId);

            List<Object[]> results = outboundReportRepository
                    .findShippingInspectionReportData(slipId2);

            assertThat(results).hasSize(1);
            Object[] row = results.get(0);
            assertThat(((Number) row[6]).intValue()).isEqualTo(15);        // picked_qty = 15 (受注数50ではない)
            assertThat(((Number) row[7]).intValue()).isEqualTo(15);        // inspected_qty = 15
        }

        @Test
        @DisplayName("同一商品コードの複数出荷明細行が別行として返される（GROUP BY osl.id）")
        void sameProductCode_multipleLines_notMerged() {
            // 新しい出荷伝票を作成
            jdbcTemplate.update("""
                    INSERT INTO outbound_slips (slip_number, slip_type, warehouse_id, warehouse_code,
                                                warehouse_name, partner_name, planned_date, status,
                                                version, created_by, updated_by,
                                                created_at, updated_at)
                    VALUES ('OUT-TEST-003', 'NORMAL', 1, 'WH-T01', 'テスト倉庫', 'テスト出荷先C',
                            '2026-03-17', 'INSPECTING', 0, 1, 1,
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            Long slipId3 = jdbcTemplate.queryForObject(
                    "SELECT id FROM outbound_slips WHERE slip_number = 'OUT-TEST-003'", Long.class);

            // 同一商品P-001で2つの明細行（ロット違い等を想定）
            jdbcTemplate.update("""
                    INSERT INTO outbound_slip_lines (outbound_slip_id, line_no, product_id, product_code,
                                                      product_name, unit_type, ordered_qty, inspected_qty,
                                                      shipped_qty, line_status,
                                                      created_at, updated_at)
                    VALUES (?, 1, 1, 'P-001', '商品A', 'CASE', 20, 20,
                            0, 'INSPECTING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, slipId3);
            jdbcTemplate.update("""
                    INSERT INTO outbound_slip_lines (outbound_slip_id, line_no, product_id, product_code,
                                                      product_name, unit_type, ordered_qty, inspected_qty,
                                                      shipped_qty, line_status,
                                                      created_at, updated_at)
                    VALUES (?, 2, 1, 'P-001', '商品A', 'CASE', 10, 10,
                            0, 'INSPECTING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, slipId3);

            Long oslId3a = jdbcTemplate.queryForObject(
                    "SELECT id FROM outbound_slip_lines WHERE outbound_slip_id = ? AND line_no = 1",
                    Long.class, slipId3);
            Long oslId3b = jdbcTemplate.queryForObject(
                    "SELECT id FROM outbound_slip_lines WHERE outbound_slip_id = ? AND line_no = 2",
                    Long.class, slipId3);

            // ピッキング指示
            jdbcTemplate.update("""
                    INSERT INTO picking_instructions (id, instruction_number, warehouse_id, area_id,
                                                       status, created_by, created_at)
                    VALUES (3, 'PI-TEST-003', 1, 1, 'COMPLETED', 1, CURRENT_TIMESTAMP)
                    """);
            jdbcTemplate.update("""
                    INSERT INTO picking_instruction_lines (picking_instruction_id, line_no,
                        outbound_slip_line_id, location_id, location_code,
                        product_id, product_code, product_name, unit_type,
                        qty_to_pick, qty_picked, line_status,
                        created_at, updated_at)
                    VALUES (3, 1, ?, 1, 'LOC-001', 1, 'P-001', '商品A', 'CASE', 20, 20, 'COMPLETED',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, oslId3a);
            jdbcTemplate.update("""
                    INSERT INTO picking_instruction_lines (picking_instruction_id, line_no,
                        outbound_slip_line_id, location_id, location_code,
                        product_id, product_code, product_name, unit_type,
                        qty_to_pick, qty_picked, line_status,
                        created_at, updated_at)
                    VALUES (3, 2, ?, 2, 'LOC-002', 1, 'P-001', '商品A', 'CASE', 10, 10, 'COMPLETED',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, oslId3b);

            List<Object[]> results = outboundReportRepository
                    .findShippingInspectionReportData(slipId3);

            // 同一商品でも2行が別々に返される（集約されない）
            assertThat(results).hasSize(2);
            assertThat(((Number) results.get(0)[6]).intValue()).isEqualTo(20);  // Line 1: picked_qty = 20
            assertThat(((Number) results.get(1)[6]).intValue()).isEqualTo(10);  // Line 2: picked_qty = 10
        }
    }
}
