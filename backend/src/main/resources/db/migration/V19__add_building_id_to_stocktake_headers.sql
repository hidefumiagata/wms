-- stocktake_headers に building_id カラムを追加
ALTER TABLE stocktake_headers
    ADD COLUMN building_id BIGINT REFERENCES buildings(id);

-- 既存データのバックフィル: stocktake_lines → locations → areas → buildings
UPDATE stocktake_headers h
SET building_id = sub.building_id
FROM (
    SELECT DISTINCT ON (sl.stocktake_header_id)
           sl.stocktake_header_id,
           a.building_id
    FROM stocktake_lines sl
    JOIN locations l ON sl.location_id = l.id
    JOIN areas a ON l.area_id = a.id
    ORDER BY sl.stocktake_header_id, sl.id
) sub
WHERE h.id = sub.stocktake_header_id;

-- NOT NULL制約は追加しない（既存データにnullが残る可能性があるため）
-- 検索用インデックス
CREATE INDEX idx_stocktake_headers_building ON stocktake_headers(building_id);
CREATE INDEX idx_stocktake_headers_number ON stocktake_headers(stocktake_number);
