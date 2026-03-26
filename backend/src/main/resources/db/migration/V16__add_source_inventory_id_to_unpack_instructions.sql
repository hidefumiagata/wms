-- ばらし指示にばらし元在庫IDを追加（確実な元在庫特定のため）
ALTER TABLE unpack_instructions
    ADD COLUMN source_inventory_id BIGINT REFERENCES inventories(id);

-- 既存データの source_inventory_id は NULL のまま（後方互換）
CREATE INDEX idx_unpack_instructions_source_inventory ON unpack_instructions (source_inventory_id);
