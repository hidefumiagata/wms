-- ============================================================
-- Repeatable migration: デモ用入荷データ（20件、各3〜5明細）
-- 直近5営業日分（CURRENT_DATE - 4 〜 CURRENT_DATE）
-- ステータス: STORED(入庫完了) 15件 + CONFIRMED(確認済) 3件 + PLANNED(予定) 2件
-- ============================================================

-- 入荷伝票の投入（冪等: slip_number で重複チェック）
INSERT INTO inbound_slips (slip_number, slip_type, warehouse_id, warehouse_code, warehouse_name, partner_id, partner_code, partner_name, planned_date, status, created_by, updated_by, created_at, updated_at)
SELECT v.slip_number, 'NORMAL', w.id, w.warehouse_code, w.warehouse_name,
       p.id, p.partner_code, p.partner_name,
       v.planned_date, v.status, 2, 2, v.planned_date::timestamp + interval '9 hours', v.planned_date::timestamp + interval '9 hours'
FROM (VALUES
    ('INB-20260323-0001', 'SUP001', CURRENT_DATE - 4, 'STORED'),
    ('INB-20260323-0002', 'SUP002', CURRENT_DATE - 4, 'STORED'),
    ('INB-20260323-0003', 'SUP003', CURRENT_DATE - 4, 'STORED'),
    ('INB-20260324-0001', 'SUP001', CURRENT_DATE - 3, 'STORED'),
    ('INB-20260324-0002', 'SUP004', CURRENT_DATE - 3, 'STORED'),
    ('INB-20260324-0003', 'SUP005', CURRENT_DATE - 3, 'STORED'),
    ('INB-20260324-0004', 'SUP002', CURRENT_DATE - 3, 'STORED'),
    ('INB-20260325-0001', 'SUP006', CURRENT_DATE - 2, 'STORED'),
    ('INB-20260325-0002', 'SUP003', CURRENT_DATE - 2, 'STORED'),
    ('INB-20260325-0003', 'SUP007', CURRENT_DATE - 2, 'STORED'),
    ('INB-20260325-0004', 'SUP001', CURRENT_DATE - 2, 'STORED'),
    ('INB-20260326-0001', 'SUP008', CURRENT_DATE - 1, 'STORED'),
    ('INB-20260326-0002', 'SUP004', CURRENT_DATE - 1, 'STORED'),
    ('INB-20260326-0003', 'SUP009', CURRENT_DATE - 1, 'STORED'),
    ('INB-20260326-0004', 'SUP002', CURRENT_DATE - 1, 'STORED'),
    ('INB-20260327-0001', 'SUP001', CURRENT_DATE,     'CONFIRMED'),
    ('INB-20260327-0002', 'SUP005', CURRENT_DATE,     'CONFIRMED'),
    ('INB-20260327-0003', 'SUP003', CURRENT_DATE,     'CONFIRMED'),
    ('INB-20260327-0004', 'SUP010', CURRENT_DATE,     'PLANNED'),
    ('INB-20260327-0005', 'SUP006', CURRENT_DATE,     'PLANNED')
) AS v(slip_number, partner_code, planned_date, status)
CROSS JOIN warehouses w
JOIN partners p ON p.partner_code = v.partner_code
WHERE w.warehouse_code = 'WH001'
  AND NOT EXISTS (SELECT 1 FROM inbound_slips s WHERE s.slip_number = v.slip_number);

-- 入荷明細の投入（各伝票に3〜5明細）
INSERT INTO inbound_slip_lines (inbound_slip_id, line_no, product_id, product_code, product_name, unit_type, planned_qty, inspected_qty, line_status, created_at, updated_at)
SELECT s.id, v.line_no, pr.id, pr.product_code, pr.product_name, v.unit_type, v.planned_qty,
       CASE WHEN s.status IN ('STORED', 'CONFIRMED') THEN v.planned_qty ELSE NULL END,
       CASE WHEN s.status = 'STORED' THEN 'STORED' WHEN s.status = 'CONFIRMED' THEN 'INSPECTED' ELSE 'PENDING' END,
       s.created_at, s.created_at
FROM (VALUES
    -- INB-20260323-0001 (3明細: 常温飲料)
    ('INB-20260323-0001', 1, 'AMB-001', 'CASE', 10),
    ('INB-20260323-0001', 2, 'AMB-002', 'CASE',  8),
    ('INB-20260323-0001', 3, 'AMB-003', 'CASE', 12),
    -- INB-20260323-0002 (4明細: 乾物・調味料)
    ('INB-20260323-0002', 1, 'AMB-011', 'CASE',  5),
    ('INB-20260323-0002', 2, 'AMB-012', 'CASE', 10),
    ('INB-20260323-0002', 3, 'AMB-015', 'CASE',  6),
    ('INB-20260323-0002', 4, 'AMB-020', 'CASE',  8),
    -- INB-20260323-0003 (3明細: 冷蔵)
    ('INB-20260323-0003', 1, 'REF-001', 'CASE', 10),
    ('INB-20260323-0003', 2, 'REF-004', 'CASE', 15),
    ('INB-20260323-0003', 3, 'REF-005', 'CASE',  8),
    -- INB-20260324-0001 (4明細)
    ('INB-20260324-0001', 1, 'AMB-004', 'CASE',  8),
    ('INB-20260324-0001', 2, 'AMB-005', 'CASE',  6),
    ('INB-20260324-0001', 3, 'AMB-026', 'CASE', 12),
    ('INB-20260324-0001', 4, 'AMB-027', 'CASE', 10),
    -- INB-20260324-0002 (3明細: 冷凍)
    ('INB-20260324-0002', 1, 'FRZ-001', 'CASE', 10),
    ('INB-20260324-0002', 2, 'FRZ-002', 'CASE', 12),
    ('INB-20260324-0002', 3, 'FRZ-003', 'CASE',  8),
    -- INB-20260324-0003 (3明細)
    ('INB-20260324-0003', 1, 'AMB-036', 'CASE',  6),
    ('INB-20260324-0003', 2, 'AMB-037', 'CASE',  4),
    ('INB-20260324-0003', 3, 'AMB-039', 'CASE',  8),
    -- INB-20260324-0004 (3明細)
    ('INB-20260324-0004', 1, 'AMB-013', 'CASE', 15),
    ('INB-20260324-0004', 2, 'AMB-014', 'CASE', 10),
    ('INB-20260324-0004', 3, 'AMB-023', 'CASE', 12),
    -- INB-20260325-0001 (4明細)
    ('INB-20260325-0001', 1, 'AMB-051', 'CASE', 10),
    ('INB-20260325-0001', 2, 'AMB-052', 'CASE', 10),
    ('INB-20260325-0001', 3, 'AMB-057', 'CASE', 20),
    ('INB-20260325-0001', 4, 'AMB-058', 'CASE', 15),
    -- INB-20260325-0002 (3明細: 冷蔵)
    ('INB-20260325-0002', 1, 'REF-013', 'CASE',  8),
    ('INB-20260325-0002', 2, 'REF-014', 'CASE', 10),
    ('INB-20260325-0002', 3, 'REF-009', 'CASE', 12),
    -- INB-20260325-0003 (3明細)
    ('INB-20260325-0003', 1, 'AMB-041', 'CASE',  8),
    ('INB-20260325-0003', 2, 'AMB-042', 'CASE',  6),
    ('INB-20260325-0003', 3, 'AMB-044', 'CASE', 10),
    -- INB-20260325-0004 (4明細)
    ('INB-20260325-0004', 1, 'AMB-006', 'CASE', 10),
    ('INB-20260325-0004', 2, 'AMB-007', 'CASE',  8),
    ('INB-20260325-0004', 3, 'AMB-008', 'CASE',  6),
    ('INB-20260325-0004', 4, 'AMB-009', 'CASE',  8),
    -- INB-20260326-0001 (3明細)
    ('INB-20260326-0001', 1, 'AMB-028', 'CASE', 10),
    ('INB-20260326-0001', 2, 'AMB-029', 'CASE', 12),
    ('INB-20260326-0001', 3, 'AMB-030', 'CASE', 15),
    -- INB-20260326-0002 (4明細: 冷凍)
    ('INB-20260326-0002', 1, 'FRZ-004', 'CASE',  8),
    ('INB-20260326-0002', 2, 'FRZ-005', 'CASE', 10),
    ('INB-20260326-0002', 3, 'FRZ-009', 'CASE',  6),
    ('INB-20260326-0002', 4, 'FRZ-011', 'CASE', 10),
    -- INB-20260326-0003 (3明細)
    ('INB-20260326-0003', 1, 'AMB-046', 'CASE', 10),
    ('INB-20260326-0003', 2, 'AMB-047', 'CASE', 12),
    ('INB-20260326-0003', 3, 'AMB-048', 'CASE',  8),
    -- INB-20260326-0004 (3明細)
    ('INB-20260326-0004', 1, 'AMB-016', 'CASE',  8),
    ('INB-20260326-0004', 2, 'AMB-017', 'CASE', 10),
    ('INB-20260326-0004', 3, 'AMB-018', 'CASE', 10),
    -- INB-20260327-0001 (5明細: 今日・確認済)
    ('INB-20260327-0001', 1, 'AMB-001', 'CASE', 12),
    ('INB-20260327-0001', 2, 'AMB-002', 'CASE', 10),
    ('INB-20260327-0001', 3, 'AMB-010', 'CASE',  8),
    ('INB-20260327-0001', 4, 'AMB-019', 'CASE',  6),
    ('INB-20260327-0001', 5, 'AMB-025', 'CASE', 10),
    -- INB-20260327-0002 (3明細: 今日・確認済)
    ('INB-20260327-0002', 1, 'AMB-053', 'CASE',  8),
    ('INB-20260327-0002', 2, 'AMB-054', 'CASE', 10),
    ('INB-20260327-0002', 3, 'AMB-055', 'CASE',  6),
    -- INB-20260327-0003 (3明細: 今日・確認済)
    ('INB-20260327-0003', 1, 'REF-006', 'CASE', 10),
    ('INB-20260327-0003', 2, 'REF-007', 'CASE',  8),
    ('INB-20260327-0003', 3, 'REF-008', 'CASE', 12),
    -- INB-20260327-0004 (3明細: 今日・予定)
    ('INB-20260327-0004', 1, 'AMB-031', 'CASE', 15),
    ('INB-20260327-0004', 2, 'AMB-032', 'CASE', 12),
    ('INB-20260327-0004', 3, 'AMB-033', 'CASE', 10),
    -- INB-20260327-0005 (3明細: 今日・予定)
    ('INB-20260327-0005', 1, 'AMB-040', 'CASE', 10),
    ('INB-20260327-0005', 2, 'AMB-043', 'CASE',  8),
    ('INB-20260327-0005', 3, 'AMB-045', 'CASE', 12)
) AS v(slip_number, line_no, product_code, unit_type, planned_qty)
JOIN inbound_slips s ON s.slip_number = v.slip_number
JOIN products pr ON pr.product_code = v.product_code
WHERE NOT EXISTS (
    SELECT 1 FROM inbound_slip_lines l WHERE l.inbound_slip_id = s.id AND l.line_no = v.line_no
);
