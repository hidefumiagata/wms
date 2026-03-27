-- ============================================================
-- Repeatable migration: 倉庫・棟・エリア・ロケーション（共通マスタ）
-- 1倉庫 / 2棟 / 6エリア / 約240ロケーション
-- ============================================================

-- === 倉庫 ===
INSERT INTO warehouses (warehouse_code, warehouse_name, warehouse_name_kana, address, is_active, created_by, updated_by, created_at, updated_at)
SELECT 'WH001', 'メイン倉庫', 'メインソウコ', '東京都江東区有明1-1-1', true, 1, 1, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM warehouses WHERE warehouse_code = 'WH001');

-- === 棟 ===
INSERT INTO buildings (building_code, building_name, warehouse_id, is_active, created_by, updated_by, created_at, updated_at)
SELECT v.building_code, v.building_name, w.id, true, 1, 1, now(), now()
FROM (VALUES
    ('A', 'A棟（常温）'),
    ('B', 'B棟（冷蔵・冷凍）')
) AS v(building_code, building_name)
CROSS JOIN warehouses w
WHERE w.warehouse_code = 'WH001'
  AND NOT EXISTS (
    SELECT 1 FROM buildings b WHERE b.warehouse_id = w.id AND b.building_code = v.building_code
  );

-- === エリア ===
-- A棟: 常温在庫エリア(2面) + 入荷エリア + 出荷エリア
-- B棟: 冷蔵在庫エリア + 冷凍在庫エリア
INSERT INTO areas (area_code, area_name, warehouse_id, building_id, storage_condition, area_type, is_active, created_by, updated_by, created_at, updated_at)
SELECT v.area_code, v.area_name, w.id, b.id, v.storage_condition, v.area_type, true, 1, 1, now(), now()
FROM (VALUES
    ('A', 'A01', 'A棟 常温在庫1',  'AMBIENT',      'STOCK'),
    ('A', 'A02', 'A棟 常温在庫2',  'AMBIENT',      'STOCK'),
    ('A', 'INB', 'A棟 入荷エリア', 'AMBIENT',      'INBOUND'),
    ('A', 'OUT', 'A棟 出荷エリア', 'AMBIENT',      'OUTBOUND'),
    ('B', 'B01', 'B棟 冷蔵在庫',   'REFRIGERATED', 'STOCK'),
    ('B', 'B02', 'B棟 冷凍在庫',   'FROZEN',       'STOCK')
) AS v(building_code, area_code, area_name, storage_condition, area_type)
CROSS JOIN warehouses w
JOIN buildings b ON b.warehouse_id = w.id AND b.building_code = v.building_code
WHERE w.warehouse_code = 'WH001'
  AND NOT EXISTS (
    SELECT 1 FROM areas a WHERE a.building_id = b.id AND a.area_code = v.area_code
  );

-- === ロケーション ===
-- 在庫エリア(STOCK): 棚-段-並び のパターンで生成
-- A01: 棚01-04, 段01-03, 並び01-05 = 60ロケーション
-- A02: 棚01-04, 段01-03, 並び01-05 = 60ロケーション
-- B01: 棚01-03, 段01-02, 並び01-05 = 30ロケーション
-- B02: 棚01-02, 段01-02, 並び01-05 = 20ロケーション
-- 入荷/出荷エリア: 各1ロケーション

-- A01 在庫ロケーション (60件)
INSERT INTO locations (location_code, warehouse_id, area_id, is_active, created_by, updated_by, created_at, updated_at)
SELECT
    'A-01-A01-' || LPAD(shelf::text, 2, '0') || '-' || LPAD(tier::text, 2, '0') || '-' || LPAD(pos::text, 2, '0'),
    w.id, a.id, true, 1, 1, now(), now()
FROM warehouses w
JOIN buildings b ON b.warehouse_id = w.id AND b.building_code = 'A'
JOIN areas a ON a.building_id = b.id AND a.area_code = 'A01'
CROSS JOIN generate_series(1, 4) AS shelf
CROSS JOIN generate_series(1, 3) AS tier
CROSS JOIN generate_series(1, 5) AS pos
WHERE w.warehouse_code = 'WH001'
  AND NOT EXISTS (
    SELECT 1 FROM locations l
    WHERE l.warehouse_id = w.id
      AND l.location_code = 'A-01-A01-' || LPAD(shelf::text, 2, '0') || '-' || LPAD(tier::text, 2, '0') || '-' || LPAD(pos::text, 2, '0')
  );

-- A02 在庫ロケーション (60件)
INSERT INTO locations (location_code, warehouse_id, area_id, is_active, created_by, updated_by, created_at, updated_at)
SELECT
    'A-01-A02-' || LPAD(shelf::text, 2, '0') || '-' || LPAD(tier::text, 2, '0') || '-' || LPAD(pos::text, 2, '0'),
    w.id, a.id, true, 1, 1, now(), now()
FROM warehouses w
JOIN buildings b ON b.warehouse_id = w.id AND b.building_code = 'A'
JOIN areas a ON a.building_id = b.id AND a.area_code = 'A02'
CROSS JOIN generate_series(1, 4) AS shelf
CROSS JOIN generate_series(1, 3) AS tier
CROSS JOIN generate_series(1, 5) AS pos
WHERE w.warehouse_code = 'WH001'
  AND NOT EXISTS (
    SELECT 1 FROM locations l
    WHERE l.warehouse_id = w.id
      AND l.location_code = 'A-01-A02-' || LPAD(shelf::text, 2, '0') || '-' || LPAD(tier::text, 2, '0') || '-' || LPAD(pos::text, 2, '0')
  );

-- B01 冷蔵ロケーション (30件)
INSERT INTO locations (location_code, warehouse_id, area_id, is_active, created_by, updated_by, created_at, updated_at)
SELECT
    'B-01-B01-' || LPAD(shelf::text, 2, '0') || '-' || LPAD(tier::text, 2, '0') || '-' || LPAD(pos::text, 2, '0'),
    w.id, a.id, true, 1, 1, now(), now()
FROM warehouses w
JOIN buildings b ON b.warehouse_id = w.id AND b.building_code = 'B'
JOIN areas a ON a.building_id = b.id AND a.area_code = 'B01'
CROSS JOIN generate_series(1, 3) AS shelf
CROSS JOIN generate_series(1, 2) AS tier
CROSS JOIN generate_series(1, 5) AS pos
WHERE w.warehouse_code = 'WH001'
  AND NOT EXISTS (
    SELECT 1 FROM locations l
    WHERE l.warehouse_id = w.id
      AND l.location_code = 'B-01-B01-' || LPAD(shelf::text, 2, '0') || '-' || LPAD(tier::text, 2, '0') || '-' || LPAD(pos::text, 2, '0')
  );

-- B02 冷凍ロケーション (20件)
INSERT INTO locations (location_code, warehouse_id, area_id, is_active, created_by, updated_by, created_at, updated_at)
SELECT
    'B-01-B02-' || LPAD(shelf::text, 2, '0') || '-' || LPAD(tier::text, 2, '0') || '-' || LPAD(pos::text, 2, '0'),
    w.id, a.id, true, 1, 1, now(), now()
FROM warehouses w
JOIN buildings b ON b.warehouse_id = w.id AND b.building_code = 'B'
JOIN areas a ON a.building_id = b.id AND a.area_code = 'B02'
CROSS JOIN generate_series(1, 2) AS shelf
CROSS JOIN generate_series(1, 2) AS tier
CROSS JOIN generate_series(1, 5) AS pos
WHERE w.warehouse_code = 'WH001'
  AND NOT EXISTS (
    SELECT 1 FROM locations l
    WHERE l.warehouse_id = w.id
      AND l.location_code = 'B-01-B02-' || LPAD(shelf::text, 2, '0') || '-' || LPAD(tier::text, 2, '0') || '-' || LPAD(pos::text, 2, '0')
  );

-- 入荷エリアロケーション (1件)
INSERT INTO locations (location_code, warehouse_id, area_id, is_active, created_by, updated_by, created_at, updated_at)
SELECT 'A-01-INB-01', w.id, a.id, true, 1, 1, now(), now()
FROM warehouses w
JOIN buildings b ON b.warehouse_id = w.id AND b.building_code = 'A'
JOIN areas a ON a.building_id = b.id AND a.area_code = 'INB'
WHERE w.warehouse_code = 'WH001'
  AND NOT EXISTS (SELECT 1 FROM locations l WHERE l.warehouse_id = w.id AND l.location_code = 'A-01-INB-01');

-- 出荷エリアロケーション (1件)
INSERT INTO locations (location_code, warehouse_id, area_id, is_active, created_by, updated_by, created_at, updated_at)
SELECT 'A-01-OUT-01', w.id, a.id, true, 1, 1, now(), now()
FROM warehouses w
JOIN buildings b ON b.warehouse_id = w.id AND b.building_code = 'A'
JOIN areas a ON a.building_id = b.id AND a.area_code = 'OUT'
WHERE w.warehouse_code = 'WH001'
  AND NOT EXISTS (SELECT 1 FROM locations l WHERE l.warehouse_id = w.id AND l.location_code = 'A-01-OUT-01');
