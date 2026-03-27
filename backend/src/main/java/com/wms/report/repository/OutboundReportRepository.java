package com.wms.report.repository;

import com.wms.outbound.entity.PickingInstructionLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 出荷系帳票のデータ取得リポジトリ。
 * RPT-12（ピッキング指示書）、RPT-13（出荷検品レポート）、
 * RPT-14（配送リスト）、RPT-15（未出荷リスト・リアルタイム）用。
 */
public interface OutboundReportRepository extends JpaRepository<PickingInstructionLine, Long> {

    /**
     * RPT-12: ピッキング指示書データ取得。
     * ロケーションコード昇順 → 商品コード昇順でソート。
     */
    @Query(value = """
            SELECT pil.location_code,
                   pil.product_code,
                   pil.product_name,
                   pil.unit_type,
                   pil.qty_to_pick,
                   os.slip_number,
                   os.partner_name,
                   os.planned_date,
                   pil.lot_number
            FROM picking_instruction_lines pil
              JOIN picking_instructions pi ON pil.picking_instruction_id = pi.id
              JOIN outbound_slip_lines osl ON pil.outbound_slip_line_id = osl.id
              JOIN outbound_slips os ON osl.outbound_slip_id = os.id
            WHERE pi.id = :pickingInstructionId
            ORDER BY pil.location_code ASC, pil.product_code ASC
            """, nativeQuery = true)
    List<Object[]> findPickingInstructionReportData(@Param("pickingInstructionId") Long pickingInstructionId);

    /**
     * RPT-13: 出荷検品レポートデータ取得。
     * 商品コード昇順でソート。
     */
    @Query(value = """
            SELECT os.slip_number,
                   os.partner_name,
                   os.planned_date,
                   osl.product_code,
                   osl.product_name,
                   osl.unit_type,
                   osl.ordered_qty,
                   osl.inspected_qty
            FROM outbound_slip_lines osl
              JOIN outbound_slips os ON osl.outbound_slip_id = os.id
            WHERE os.id = :slipId
            ORDER BY osl.product_code ASC
            """, nativeQuery = true)
    List<Object[]> findShippingInspectionReportData(@Param("slipId") Long slipId);

    /**
     * RPT-14: 配送リストデータ取得。
     * 出荷伝票ヘッダーを出荷予定日昇順で取得。
     */
    @Query(value = """
            SELECT os.id,
                   os.slip_number,
                   os.partner_name,
                   os.planned_date,
                   os.status,
                   os.carrier,
                   os.tracking_number,
                   p.address
            FROM outbound_slips os
              LEFT JOIN partners p ON os.partner_id = p.id
            WHERE os.warehouse_id = :warehouseId
              AND (:plannedDateFrom IS NULL OR os.planned_date >= :plannedDateFrom)
              AND (:plannedDateTo IS NULL OR os.planned_date <= :plannedDateTo)
              AND (:status IS NULL OR os.status = :status)
              AND (:carrier IS NULL OR os.carrier LIKE :carrier ESCAPE '\')
            ORDER BY os.planned_date ASC, os.slip_number ASC
            """, nativeQuery = true)
    List<Object[]> findDeliveryListHeaderData(
            @Param("warehouseId") Long warehouseId,
            @Param("plannedDateFrom") LocalDate plannedDateFrom,
            @Param("plannedDateTo") LocalDate plannedDateTo,
            @Param("status") String status,
            @Param("carrier") String carrier);

    /**
     * RPT-14: 配送リストの明細行データ取得。
     */
    @Query(value = """
            SELECT osl.outbound_slip_id,
                   osl.product_code,
                   osl.product_name,
                   osl.unit_type,
                   osl.ordered_qty
            FROM outbound_slip_lines osl
            WHERE osl.outbound_slip_id IN :slipIds
            ORDER BY osl.outbound_slip_id ASC, osl.line_no ASC
            """, nativeQuery = true)
    List<Object[]> findDeliveryListLineData(@Param("slipIds") List<Long> slipIds);

    /**
     * RPT-15: 未出荷リスト（リアルタイム）データ取得。
     * 出荷予定日が基準日以前で、SHIPPED/CANCELLED 以外のステータスの伝票を取得。
     */
    @Query(value = """
            SELECT os.slip_number,
                   os.partner_name,
                   os.planned_date,
                   osl.product_code,
                   osl.product_name,
                   osl.ordered_qty,
                   os.status
            FROM outbound_slip_lines osl
              JOIN outbound_slips os ON osl.outbound_slip_id = os.id
            WHERE os.warehouse_id = :warehouseId
              AND os.planned_date <= :asOfDate
              AND os.status NOT IN ('SHIPPED', 'CANCELLED')
            ORDER BY os.partner_name ASC, os.planned_date ASC, osl.product_code ASC
            """, nativeQuery = true)
    List<Object[]> findUnshippedRealtimeData(
            @Param("warehouseId") Long warehouseId,
            @Param("asOfDate") LocalDate asOfDate);
}
