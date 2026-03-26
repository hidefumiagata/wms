-- ばらし指示にばらし元在庫IDを追加（確実な元在庫特定のため）
ALTER TABLE unpack_instructions
    ADD COLUMN source_inventory_id BIGINT REFERENCES inventories(id);

-- 既存データの source_inventory_id は NULL のまま（後方互換）
CREATE INDEX idx_unpack_instructions_source_inventory ON unpack_instructions (source_inventory_id);

-- FIFO引当用: inventory_movementsのINBOUND入庫日時集約クエリ高速化
CREATE INDEX idx_inventory_movements_inbound_fifo
    ON inventory_movements (product_id, location_id, unit_type, lot_number, expiry_date, executed_at)
    WHERE movement_type = 'INBOUND';
