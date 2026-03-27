-- ============================================================
-- Repeatable migration: デモ用在庫データ
-- 入庫完了分の在庫レコード（常温エリアA01/A02, 冷蔵B01, 冷凍B02）
-- ============================================================

-- 常温在庫（A01エリアのロケーションに配置）
INSERT INTO inventories (warehouse_id, location_id, product_id, unit_type, quantity, allocated_qty, updated_at)
SELECT w.id, l.id, pr.id, v.unit_type, v.qty, 0, now()
FROM (VALUES
    ('A-01-A01-01-01-01', 'AMB-001', 'CASE', 22),
    ('A-01-A01-01-01-02', 'AMB-002', 'CASE', 18),
    ('A-01-A01-01-01-03', 'AMB-003', 'CASE', 12),
    ('A-01-A01-01-01-04', 'AMB-004', 'CASE',  8),
    ('A-01-A01-01-01-05', 'AMB-005', 'CASE',  6),
    ('A-01-A01-01-02-01', 'AMB-006', 'CASE', 10),
    ('A-01-A01-01-02-02', 'AMB-007', 'CASE',  8),
    ('A-01-A01-01-02-03', 'AMB-008', 'CASE',  6),
    ('A-01-A01-01-02-04', 'AMB-009', 'CASE',  8),
    ('A-01-A01-01-02-05', 'AMB-010', 'CASE',  8),
    ('A-01-A01-01-03-01', 'AMB-011', 'CASE',  5),
    ('A-01-A01-01-03-02', 'AMB-012', 'CASE', 10),
    ('A-01-A01-01-03-03', 'AMB-013', 'CASE', 15),
    ('A-01-A01-01-03-04', 'AMB-014', 'CASE', 10),
    ('A-01-A01-01-03-05', 'AMB-015', 'CASE',  6),
    ('A-01-A01-02-01-01', 'AMB-016', 'CASE',  8),
    ('A-01-A01-02-01-02', 'AMB-017', 'CASE', 10),
    ('A-01-A01-02-01-03', 'AMB-018', 'CASE', 10),
    ('A-01-A01-02-01-04', 'AMB-019', 'CASE',  6),
    ('A-01-A01-02-01-05', 'AMB-020', 'CASE',  8),
    ('A-01-A01-02-02-01', 'AMB-023', 'CASE', 12),
    ('A-01-A01-02-02-02', 'AMB-025', 'CASE', 10),
    ('A-01-A01-02-02-03', 'AMB-026', 'CASE', 12),
    ('A-01-A01-02-02-04', 'AMB-027', 'CASE', 10),
    ('A-01-A01-02-02-05', 'AMB-028', 'CASE', 10),
    -- A02エリア
    ('A-01-A02-01-01-01', 'AMB-029', 'CASE', 12),
    ('A-01-A02-01-01-02', 'AMB-030', 'CASE', 15),
    ('A-01-A02-01-01-03', 'AMB-036', 'CASE',  6),
    ('A-01-A02-01-01-04', 'AMB-037', 'CASE',  4),
    ('A-01-A02-01-01-05', 'AMB-039', 'CASE',  8),
    ('A-01-A02-01-02-01', 'AMB-041', 'CASE',  8),
    ('A-01-A02-01-02-02', 'AMB-042', 'CASE',  6),
    ('A-01-A02-01-02-03', 'AMB-044', 'CASE', 10),
    ('A-01-A02-01-02-04', 'AMB-046', 'CASE', 10),
    ('A-01-A02-01-02-05', 'AMB-047', 'CASE', 12),
    ('A-01-A02-01-03-01', 'AMB-048', 'CASE',  8),
    ('A-01-A02-01-03-02', 'AMB-051', 'CASE', 10),
    ('A-01-A02-01-03-03', 'AMB-052', 'CASE', 10),
    ('A-01-A02-01-03-04', 'AMB-057', 'CASE', 20),
    ('A-01-A02-01-03-05', 'AMB-058', 'CASE', 15)
) AS v(loc_code, product_code, unit_type, qty)
CROSS JOIN warehouses w
JOIN locations l ON l.warehouse_id = w.id AND l.location_code = v.loc_code
JOIN products pr ON pr.product_code = v.product_code
WHERE w.warehouse_code = 'WH001'
  AND NOT EXISTS (
    SELECT 1 FROM inventories i
    WHERE i.location_id = l.id AND i.product_id = pr.id AND i.unit_type = v.unit_type
  );

-- 冷蔵在庫（B01エリア、ロット・期限付き）
INSERT INTO inventories (warehouse_id, location_id, product_id, unit_type, lot_number, expiry_date, quantity, allocated_qty, updated_at)
SELECT w.id, l.id, pr.id, v.unit_type, v.lot_number, v.expiry_date::date, v.qty, 0, now()
FROM (VALUES
    ('B-01-B01-01-01-01', 'REF-001', 'CASE', 'LOT-2603A', '2026-04-15', 10),
    ('B-01-B01-01-01-02', 'REF-004', 'CASE', 'LOT-2603B', '2026-04-10', 15),
    ('B-01-B01-01-01-03', 'REF-005', 'CASE', 'LOT-2603A', '2026-04-20',  8),
    ('B-01-B01-01-01-04', 'REF-009', 'CASE', 'LOT-2603C', '2026-04-12', 12),
    ('B-01-B01-01-01-05', 'REF-013', 'CASE', 'LOT-2603A', '2026-04-05',  8),
    ('B-01-B01-01-02-01', 'REF-014', 'CASE', 'LOT-2603B', '2026-04-08', 10)
) AS v(loc_code, product_code, unit_type, lot_number, expiry_date, qty)
CROSS JOIN warehouses w
JOIN locations l ON l.warehouse_id = w.id AND l.location_code = v.loc_code
JOIN products pr ON pr.product_code = v.product_code
WHERE w.warehouse_code = 'WH001'
  AND NOT EXISTS (
    SELECT 1 FROM inventories i
    WHERE i.location_id = l.id AND i.product_id = pr.id AND i.unit_type = v.unit_type
      AND i.lot_number = v.lot_number
  );

-- 冷凍在庫（B02エリア、ロット・期限付き）
INSERT INTO inventories (warehouse_id, location_id, product_id, unit_type, lot_number, expiry_date, quantity, allocated_qty, updated_at)
SELECT w.id, l.id, pr.id, v.unit_type, v.lot_number, v.expiry_date::date, v.qty, 0, now()
FROM (VALUES
    ('B-01-B02-01-01-01', 'FRZ-001', 'CASE', 'LOT-F2603A', '2026-09-30', 10),
    ('B-01-B02-01-01-02', 'FRZ-002', 'CASE', 'LOT-F2603A', '2026-09-30', 12),
    ('B-01-B02-01-01-03', 'FRZ-003', 'CASE', 'LOT-F2603B', '2026-10-15',  8),
    ('B-01-B02-01-01-04', 'FRZ-004', 'CASE', 'LOT-F2603A', '2026-10-31',  8),
    ('B-01-B02-01-01-05', 'FRZ-005', 'CASE', 'LOT-F2603B', '2026-11-15', 10),
    ('B-01-B02-01-02-01', 'FRZ-009', 'CASE', 'LOT-F2603A', '2026-12-31',  6),
    ('B-01-B02-01-02-02', 'FRZ-011', 'CASE', 'LOT-F2603B', '2026-10-31', 10)
) AS v(loc_code, product_code, unit_type, lot_number, expiry_date, qty)
CROSS JOIN warehouses w
JOIN locations l ON l.warehouse_id = w.id AND l.location_code = v.loc_code
JOIN products pr ON pr.product_code = v.product_code
WHERE w.warehouse_code = 'WH001'
  AND NOT EXISTS (
    SELECT 1 FROM inventories i
    WHERE i.location_id = l.id AND i.product_id = pr.id AND i.unit_type = v.unit_type
      AND i.lot_number = v.lot_number
  );
