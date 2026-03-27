-- ============================================================
-- Repeatable migration: デモ用入荷データ（20件、各3〜5明細）
-- 直近5営業日分（CURRENT_DATE - 4 〜 CURRENT_DATE）
-- ステータス: STORED(入庫完了) 15件 + CONFIRMED(確認済) 3件 + PLANNED(予定) 2件
-- 伝票番号は CURRENT_DATE から動的生成
-- ============================================================

-- 入荷伝票の投入（冪等: slip_number で重複チェック）
INSERT INTO inbound_slips (slip_number, slip_type, warehouse_id, warehouse_code, warehouse_name, partner_id, partner_code, partner_name, planned_date, status, created_by, updated_by, created_at, updated_at)
SELECT v.slip_number, 'NORMAL', w.id, w.warehouse_code, w.warehouse_name,
       p.id, p.partner_code, p.partner_name,
       v.planned_date, v.status, 2, 2, v.planned_date::timestamp + interval '9 hours', v.planned_date::timestamp + interval '9 hours'
FROM (VALUES
    ('INB-' || to_char(CURRENT_DATE - 4, 'YYYYMMDD') || '-0001', 'SUP001', CURRENT_DATE - 4, 'STORED'),
    ('INB-' || to_char(CURRENT_DATE - 4, 'YYYYMMDD') || '-0002', 'SUP002', CURRENT_DATE - 4, 'STORED'),
    ('INB-' || to_char(CURRENT_DATE - 4, 'YYYYMMDD') || '-0003', 'SUP003', CURRENT_DATE - 4, 'STORED'),
    ('INB-' || to_char(CURRENT_DATE - 3, 'YYYYMMDD') || '-0001', 'SUP001', CURRENT_DATE - 3, 'STORED'),
    ('INB-' || to_char(CURRENT_DATE - 3, 'YYYYMMDD') || '-0002', 'SUP004', CURRENT_DATE - 3, 'STORED'),
    ('INB-' || to_char(CURRENT_DATE - 3, 'YYYYMMDD') || '-0003', 'SUP005', CURRENT_DATE - 3, 'STORED'),
    ('INB-' || to_char(CURRENT_DATE - 3, 'YYYYMMDD') || '-0004', 'SUP002', CURRENT_DATE - 3, 'STORED'),
    ('INB-' || to_char(CURRENT_DATE - 2, 'YYYYMMDD') || '-0001', 'SUP006', CURRENT_DATE - 2, 'STORED'),
    ('INB-' || to_char(CURRENT_DATE - 2, 'YYYYMMDD') || '-0002', 'SUP003', CURRENT_DATE - 2, 'STORED'),
    ('INB-' || to_char(CURRENT_DATE - 2, 'YYYYMMDD') || '-0003', 'SUP007', CURRENT_DATE - 2, 'STORED'),
    ('INB-' || to_char(CURRENT_DATE - 2, 'YYYYMMDD') || '-0004', 'SUP001', CURRENT_DATE - 2, 'STORED'),
    ('INB-' || to_char(CURRENT_DATE - 1, 'YYYYMMDD') || '-0001', 'SUP008', CURRENT_DATE - 1, 'STORED'),
    ('INB-' || to_char(CURRENT_DATE - 1, 'YYYYMMDD') || '-0002', 'SUP004', CURRENT_DATE - 1, 'STORED'),
    ('INB-' || to_char(CURRENT_DATE - 1, 'YYYYMMDD') || '-0003', 'SUP009', CURRENT_DATE - 1, 'STORED'),
    ('INB-' || to_char(CURRENT_DATE - 1, 'YYYYMMDD') || '-0004', 'SUP002', CURRENT_DATE - 1, 'STORED'),
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0001', 'SUP001', CURRENT_DATE,     'CONFIRMED'),
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0002', 'SUP005', CURRENT_DATE,     'CONFIRMED'),
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0003', 'SUP003', CURRENT_DATE,     'CONFIRMED'),
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0004', 'SUP010', CURRENT_DATE,     'PLANNED'),
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0005', 'SUP006', CURRENT_DATE,     'PLANNED')
) AS v(slip_number, partner_code, planned_date, status)
CROSS JOIN warehouses w
JOIN partners p ON p.partner_code = v.partner_code
WHERE w.warehouse_code = 'WH001'
  AND NOT EXISTS (SELECT 1 FROM inbound_slips s WHERE s.slip_number = v.slip_number);

-- 入荷明細の投入（各伝票に3〜5明細）
-- 冷蔵・冷凍品にはlot_number/expiry_dateを付与（業務ルール準拠）
INSERT INTO inbound_slip_lines (inbound_slip_id, line_no, product_id, product_code, product_name, unit_type, planned_qty, inspected_qty, lot_number, expiry_date, line_status, created_at, updated_at)
SELECT s.id, v.line_no, pr.id, pr.product_code, pr.product_name, v.unit_type, v.planned_qty,
       CASE WHEN s.status IN ('STORED', 'CONFIRMED') THEN v.planned_qty ELSE NULL END,
       v.lot_number, v.expiry_date::date,
       CASE WHEN s.status = 'STORED' THEN 'STORED' WHEN s.status = 'CONFIRMED' THEN 'INSPECTED' ELSE 'PENDING' END,
       s.created_at, s.created_at
FROM (VALUES
    -- Day-4 slip 1 (3明細: 常温飲料)
    ('INB-' || to_char(CURRENT_DATE - 4, 'YYYYMMDD') || '-0001', 1, 'AMB-001', 'CASE', 10, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 4, 'YYYYMMDD') || '-0001', 2, 'AMB-002', 'CASE',  8, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 4, 'YYYYMMDD') || '-0001', 3, 'AMB-003', 'CASE', 12, NULL, NULL),
    -- Day-4 slip 2 (4明細: 乾物・調味料)
    ('INB-' || to_char(CURRENT_DATE - 4, 'YYYYMMDD') || '-0002', 1, 'AMB-011', 'CASE',  5, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 4, 'YYYYMMDD') || '-0002', 2, 'AMB-012', 'CASE', 10, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 4, 'YYYYMMDD') || '-0002', 3, 'AMB-015', 'CASE',  6, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 4, 'YYYYMMDD') || '-0002', 4, 'AMB-020', 'CASE',  8, NULL, NULL),
    -- Day-4 slip 3 (3明細: 冷蔵 lot/expiry付き)
    ('INB-' || to_char(CURRENT_DATE - 4, 'YYYYMMDD') || '-0003', 1, 'REF-001', 'CASE', 10, 'LOT-2603A', to_char(CURRENT_DATE + 20, 'YYYY-MM-DD')),
    ('INB-' || to_char(CURRENT_DATE - 4, 'YYYYMMDD') || '-0003', 2, 'REF-004', 'CASE', 15, 'LOT-2603B', to_char(CURRENT_DATE + 15, 'YYYY-MM-DD')),
    ('INB-' || to_char(CURRENT_DATE - 4, 'YYYYMMDD') || '-0003', 3, 'REF-005', 'CASE',  8, 'LOT-2603A', to_char(CURRENT_DATE + 25, 'YYYY-MM-DD')),
    -- Day-3 slip 1 (4明細)
    ('INB-' || to_char(CURRENT_DATE - 3, 'YYYYMMDD') || '-0001', 1, 'AMB-004', 'CASE',  8, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 3, 'YYYYMMDD') || '-0001', 2, 'AMB-005', 'CASE',  6, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 3, 'YYYYMMDD') || '-0001', 3, 'AMB-026', 'CASE', 12, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 3, 'YYYYMMDD') || '-0001', 4, 'AMB-027', 'CASE', 10, NULL, NULL),
    -- Day-3 slip 2 (3明細: 冷凍 lot/expiry付き)
    ('INB-' || to_char(CURRENT_DATE - 3, 'YYYYMMDD') || '-0002', 1, 'FRZ-001', 'CASE', 10, 'LOT-F2603A', to_char(CURRENT_DATE + 180, 'YYYY-MM-DD')),
    ('INB-' || to_char(CURRENT_DATE - 3, 'YYYYMMDD') || '-0002', 2, 'FRZ-002', 'CASE', 12, 'LOT-F2603A', to_char(CURRENT_DATE + 180, 'YYYY-MM-DD')),
    ('INB-' || to_char(CURRENT_DATE - 3, 'YYYYMMDD') || '-0002', 3, 'FRZ-003', 'CASE',  8, 'LOT-F2603B', to_char(CURRENT_DATE + 200, 'YYYY-MM-DD')),
    -- Day-3 slip 3 (3明細)
    ('INB-' || to_char(CURRENT_DATE - 3, 'YYYYMMDD') || '-0003', 1, 'AMB-036', 'CASE',  6, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 3, 'YYYYMMDD') || '-0003', 2, 'AMB-037', 'CASE',  4, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 3, 'YYYYMMDD') || '-0003', 3, 'AMB-039', 'CASE',  8, NULL, NULL),
    -- Day-3 slip 4 (3明細)
    ('INB-' || to_char(CURRENT_DATE - 3, 'YYYYMMDD') || '-0004', 1, 'AMB-013', 'CASE', 15, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 3, 'YYYYMMDD') || '-0004', 2, 'AMB-014', 'CASE', 10, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 3, 'YYYYMMDD') || '-0004', 3, 'AMB-023', 'CASE', 12, NULL, NULL),
    -- Day-2 slip 1 (4明細)
    ('INB-' || to_char(CURRENT_DATE - 2, 'YYYYMMDD') || '-0001', 1, 'AMB-051', 'CASE', 10, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 2, 'YYYYMMDD') || '-0001', 2, 'AMB-052', 'CASE', 10, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 2, 'YYYYMMDD') || '-0001', 3, 'AMB-057', 'CASE', 20, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 2, 'YYYYMMDD') || '-0001', 4, 'AMB-058', 'CASE', 15, NULL, NULL),
    -- Day-2 slip 2 (3明細: 冷蔵 lot/expiry付き)
    ('INB-' || to_char(CURRENT_DATE - 2, 'YYYYMMDD') || '-0002', 1, 'REF-013', 'CASE',  8, 'LOT-2603A', to_char(CURRENT_DATE + 10, 'YYYY-MM-DD')),
    ('INB-' || to_char(CURRENT_DATE - 2, 'YYYYMMDD') || '-0002', 2, 'REF-014', 'CASE', 10, 'LOT-2603B', to_char(CURRENT_DATE + 12, 'YYYY-MM-DD')),
    ('INB-' || to_char(CURRENT_DATE - 2, 'YYYYMMDD') || '-0002', 3, 'REF-009', 'CASE', 12, 'LOT-2603C', to_char(CURRENT_DATE + 17, 'YYYY-MM-DD')),
    -- Day-2 slip 3 (3明細)
    ('INB-' || to_char(CURRENT_DATE - 2, 'YYYYMMDD') || '-0003', 1, 'AMB-041', 'CASE',  8, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 2, 'YYYYMMDD') || '-0003', 2, 'AMB-042', 'CASE',  6, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 2, 'YYYYMMDD') || '-0003', 3, 'AMB-044', 'CASE', 10, NULL, NULL),
    -- Day-2 slip 4 (4明細)
    ('INB-' || to_char(CURRENT_DATE - 2, 'YYYYMMDD') || '-0004', 1, 'AMB-006', 'CASE', 10, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 2, 'YYYYMMDD') || '-0004', 2, 'AMB-007', 'CASE',  8, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 2, 'YYYYMMDD') || '-0004', 3, 'AMB-008', 'CASE',  6, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 2, 'YYYYMMDD') || '-0004', 4, 'AMB-009', 'CASE',  8, NULL, NULL),
    -- Day-1 slip 1 (3明細)
    ('INB-' || to_char(CURRENT_DATE - 1, 'YYYYMMDD') || '-0001', 1, 'AMB-028', 'CASE', 10, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 1, 'YYYYMMDD') || '-0001', 2, 'AMB-029', 'CASE', 12, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 1, 'YYYYMMDD') || '-0001', 3, 'AMB-030', 'CASE', 15, NULL, NULL),
    -- Day-1 slip 2 (4明細: 冷凍 lot/expiry付き)
    ('INB-' || to_char(CURRENT_DATE - 1, 'YYYYMMDD') || '-0002', 1, 'FRZ-004', 'CASE',  8, 'LOT-F2603A', to_char(CURRENT_DATE + 210, 'YYYY-MM-DD')),
    ('INB-' || to_char(CURRENT_DATE - 1, 'YYYYMMDD') || '-0002', 2, 'FRZ-005', 'CASE', 10, 'LOT-F2603B', to_char(CURRENT_DATE + 230, 'YYYY-MM-DD')),
    ('INB-' || to_char(CURRENT_DATE - 1, 'YYYYMMDD') || '-0002', 3, 'FRZ-009', 'CASE',  6, 'LOT-F2603A', to_char(CURRENT_DATE + 270, 'YYYY-MM-DD')),
    ('INB-' || to_char(CURRENT_DATE - 1, 'YYYYMMDD') || '-0002', 4, 'FRZ-011', 'CASE', 10, 'LOT-F2603B', to_char(CURRENT_DATE + 210, 'YYYY-MM-DD')),
    -- Day-1 slip 3 (3明細)
    ('INB-' || to_char(CURRENT_DATE - 1, 'YYYYMMDD') || '-0003', 1, 'AMB-046', 'CASE', 10, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 1, 'YYYYMMDD') || '-0003', 2, 'AMB-047', 'CASE', 12, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 1, 'YYYYMMDD') || '-0003', 3, 'AMB-048', 'CASE',  8, NULL, NULL),
    -- Day-1 slip 4 (3明細)
    ('INB-' || to_char(CURRENT_DATE - 1, 'YYYYMMDD') || '-0004', 1, 'AMB-016', 'CASE',  8, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 1, 'YYYYMMDD') || '-0004', 2, 'AMB-017', 'CASE', 10, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE - 1, 'YYYYMMDD') || '-0004', 3, 'AMB-018', 'CASE', 10, NULL, NULL),
    -- Today slip 1 (5明細: 確認済)
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0001', 1, 'AMB-001', 'CASE', 12, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0001', 2, 'AMB-002', 'CASE', 10, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0001', 3, 'AMB-010', 'CASE',  8, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0001', 4, 'AMB-019', 'CASE',  6, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0001', 5, 'AMB-025', 'CASE', 10, NULL, NULL),
    -- Today slip 2 (3明細: 確認済)
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0002', 1, 'AMB-053', 'CASE',  8, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0002', 2, 'AMB-054', 'CASE', 10, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0002', 3, 'AMB-055', 'CASE',  6, NULL, NULL),
    -- Today slip 3 (3明細: 確認済・冷蔵 lot/expiry付き)
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0003', 1, 'REF-006', 'CASE', 10, 'LOT-2603D', to_char(CURRENT_DATE + 18, 'YYYY-MM-DD')),
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0003', 2, 'REF-007', 'CASE',  8, 'LOT-2603D', to_char(CURRENT_DATE + 20, 'YYYY-MM-DD')),
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0003', 3, 'REF-008', 'CASE', 12, 'LOT-2603D', to_char(CURRENT_DATE + 22, 'YYYY-MM-DD')),
    -- Today slip 4 (3明細: 予定)
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0004', 1, 'AMB-031', 'CASE', 15, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0004', 2, 'AMB-032', 'CASE', 12, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0004', 3, 'AMB-033', 'CASE', 10, NULL, NULL),
    -- Today slip 5 (3明細: 予定)
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0005', 1, 'AMB-040', 'CASE', 10, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0005', 2, 'AMB-043', 'CASE',  8, NULL, NULL),
    ('INB-' || to_char(CURRENT_DATE,     'YYYYMMDD') || '-0005', 3, 'AMB-045', 'CASE', 12, NULL, NULL)
) AS v(slip_number, line_no, product_code, unit_type, planned_qty, lot_number, expiry_date)
JOIN inbound_slips s ON s.slip_number = v.slip_number
JOIN products pr ON pr.product_code = v.product_code
WHERE NOT EXISTS (
    SELECT 1 FROM inbound_slip_lines l WHERE l.inbound_slip_id = s.id AND l.line_no = v.line_no
);
