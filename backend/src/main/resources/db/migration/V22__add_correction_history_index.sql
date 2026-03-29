-- 訂正履歴取得クエリ用の複合インデックス
-- GET /api/v1/inventory/correction-history で使用
-- 条件: (warehouse_id, location_id, product_id, unit_type, movement_type) + ORDER BY executed_at DESC LIMIT 5
CREATE INDEX idx_inventory_movements_correction_history
    ON inventory_movements (warehouse_id, location_id, product_id, unit_type, movement_type, executed_at DESC);
