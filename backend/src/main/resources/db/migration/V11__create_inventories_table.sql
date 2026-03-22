CREATE TABLE inventories (
    id              BIGSERIAL       PRIMARY KEY,
    warehouse_id    BIGINT          NOT NULL REFERENCES warehouses(id),
    location_id     BIGINT          NOT NULL REFERENCES locations(id),
    product_id      BIGINT          NOT NULL REFERENCES products(id),
    unit_type       VARCHAR(10)     NOT NULL,
    lot_number      VARCHAR(100),
    expiry_date     DATE,
    quantity        INT             NOT NULL DEFAULT 0,
    allocated_qty   INT             NOT NULL DEFAULT 0,
    version         BIGINT          NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_inventories UNIQUE (location_id, product_id, unit_type, lot_number, expiry_date) NULLS NOT DISTINCT,
    CONSTRAINT chk_inventories_quantity CHECK (quantity >= 0),
    CONSTRAINT chk_inventories_allocated CHECK (allocated_qty >= 0),
    CONSTRAINT chk_inventories_allocated_lte CHECK (allocated_qty <= quantity),
    CONSTRAINT chk_inventories_unit_type CHECK (unit_type IN ('CASE', 'BALL', 'PIECE'))
);
CREATE INDEX idx_inventories_wh_product ON inventories (warehouse_id, product_id);
CREATE INDEX idx_inventories_wh_location ON inventories (warehouse_id, location_id);
