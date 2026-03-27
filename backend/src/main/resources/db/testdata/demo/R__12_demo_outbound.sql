-- ============================================================
-- Repeatable migration: デモ用出荷データ（15件）
-- SHIPPED(出荷完了) 8件 + ALLOCATED(引当完了) 4件 + ORDERED(受注) 3件
-- ============================================================

INSERT INTO outbound_slips (slip_number, slip_type, warehouse_id, warehouse_code, warehouse_name, partner_id, partner_code, partner_name, planned_date, status, created_by, updated_by, created_at, updated_at)
SELECT v.slip_number, 'NORMAL', w.id, w.warehouse_code, w.warehouse_name,
       p.id, p.partner_code, p.partner_name,
       v.planned_date, v.status, 2, 2, v.planned_date::timestamp + interval '8 hours', v.planned_date::timestamp + interval '8 hours'
FROM (VALUES
    ('OUT-20260323-0001', 'CUS001', CURRENT_DATE - 4, 'SHIPPED'),
    ('OUT-20260323-0002', 'CUS002', CURRENT_DATE - 4, 'SHIPPED'),
    ('OUT-20260324-0001', 'CUS003', CURRENT_DATE - 3, 'SHIPPED'),
    ('OUT-20260324-0002', 'CUS004', CURRENT_DATE - 3, 'SHIPPED'),
    ('OUT-20260325-0001', 'CUS005', CURRENT_DATE - 2, 'SHIPPED'),
    ('OUT-20260325-0002', 'CUS001', CURRENT_DATE - 2, 'SHIPPED'),
    ('OUT-20260326-0001', 'CUS006', CURRENT_DATE - 1, 'SHIPPED'),
    ('OUT-20260326-0002', 'CUS007', CURRENT_DATE - 1, 'SHIPPED'),
    ('OUT-20260327-0001', 'CUS008', CURRENT_DATE,     'ALLOCATED'),
    ('OUT-20260327-0002', 'CUS009', CURRENT_DATE,     'ALLOCATED'),
    ('OUT-20260327-0003', 'CUS002', CURRENT_DATE,     'ALLOCATED'),
    ('OUT-20260327-0004', 'CUS010', CURRENT_DATE,     'ALLOCATED'),
    ('OUT-20260328-0001', 'CUS001', CURRENT_DATE + 1, 'ORDERED'),
    ('OUT-20260328-0002', 'CUS003', CURRENT_DATE + 1, 'ORDERED'),
    ('OUT-20260328-0003', 'CUS005', CURRENT_DATE + 1, 'ORDERED')
) AS v(slip_number, partner_code, planned_date, status)
CROSS JOIN warehouses w
JOIN partners p ON p.partner_code = v.partner_code
WHERE w.warehouse_code = 'WH001'
  AND NOT EXISTS (SELECT 1 FROM outbound_slips s WHERE s.slip_number = v.slip_number);

-- 出荷明細
INSERT INTO outbound_slip_lines (outbound_slip_id, line_no, product_id, product_code, product_name, unit_type, ordered_qty, shipped_qty, line_status, created_at, updated_at)
SELECT s.id, v.line_no, pr.id, pr.product_code, pr.product_name, v.unit_type, v.ordered_qty,
       CASE WHEN s.status = 'SHIPPED' THEN v.ordered_qty ELSE 0 END,
       CASE
         WHEN s.status = 'SHIPPED' THEN 'SHIPPED'
         WHEN s.status = 'ALLOCATED' THEN 'ALLOCATED'
         ELSE 'ORDERED'
       END,
       s.created_at, s.created_at
FROM (VALUES
    ('OUT-20260323-0001', 1, 'AMB-001', 'CASE', 3),
    ('OUT-20260323-0001', 2, 'AMB-011', 'CASE', 2),
    ('OUT-20260323-0002', 1, 'AMB-002', 'CASE', 4),
    ('OUT-20260323-0002', 2, 'AMB-026', 'CASE', 3),
    ('OUT-20260323-0002', 3, 'AMB-027', 'CASE', 2),
    ('OUT-20260324-0001', 1, 'AMB-003', 'CASE', 5),
    ('OUT-20260324-0001', 2, 'AMB-012', 'CASE', 3),
    ('OUT-20260324-0002', 1, 'AMB-013', 'CASE', 4),
    ('OUT-20260324-0002', 2, 'AMB-014', 'CASE', 3),
    ('OUT-20260324-0002', 3, 'AMB-015', 'CASE', 2),
    ('OUT-20260325-0001', 1, 'AMB-004', 'CASE', 2),
    ('OUT-20260325-0001', 2, 'AMB-020', 'CASE', 3),
    ('OUT-20260325-0002', 1, 'AMB-036', 'CASE', 2),
    ('OUT-20260325-0002', 2, 'AMB-037', 'CASE', 1),
    ('OUT-20260326-0001', 1, 'AMB-051', 'CASE', 3),
    ('OUT-20260326-0001', 2, 'AMB-052', 'CASE', 3),
    ('OUT-20260326-0001', 3, 'AMB-057', 'CASE', 5),
    ('OUT-20260326-0002', 1, 'AMB-039', 'CASE', 2),
    ('OUT-20260326-0002', 2, 'AMB-041', 'CASE', 3),
    ('OUT-20260327-0001', 1, 'AMB-005', 'CASE', 2),
    ('OUT-20260327-0001', 2, 'AMB-006', 'CASE', 3),
    ('OUT-20260327-0002', 1, 'AMB-028', 'CASE', 3),
    ('OUT-20260327-0002', 2, 'AMB-029', 'CASE', 4),
    ('OUT-20260327-0003', 1, 'AMB-046', 'CASE', 3),
    ('OUT-20260327-0003', 2, 'AMB-047', 'CASE', 4),
    ('OUT-20260327-0004', 1, 'AMB-042', 'CASE', 2),
    ('OUT-20260327-0004', 2, 'AMB-058', 'CASE', 5),
    ('OUT-20260328-0001', 1, 'AMB-001', 'CASE', 5),
    ('OUT-20260328-0001', 2, 'AMB-002', 'CASE', 4),
    ('OUT-20260328-0002', 1, 'AMB-023', 'CASE', 3),
    ('OUT-20260328-0002', 2, 'AMB-030', 'CASE', 5),
    ('OUT-20260328-0003', 1, 'AMB-007', 'CASE', 2),
    ('OUT-20260328-0003', 2, 'AMB-008', 'CASE', 3),
    ('OUT-20260328-0003', 3, 'AMB-009', 'CASE', 2)
) AS v(slip_number, line_no, product_code, unit_type, ordered_qty)
JOIN outbound_slips s ON s.slip_number = v.slip_number
JOIN products pr ON pr.product_code = v.product_code
WHERE NOT EXISTS (
    SELECT 1 FROM outbound_slip_lines l WHERE l.outbound_slip_id = s.id AND l.line_no = v.line_no
);
