CREATE TABLE inventory_movements (
    id              BIGSERIAL       PRIMARY KEY,
    warehouse_id    BIGINT          NOT NULL REFERENCES warehouses(id),
    location_id     BIGINT          NOT NULL REFERENCES locations(id),
    location_code   VARCHAR(50)     NOT NULL,
    product_id      BIGINT          NOT NULL REFERENCES products(id),
    product_code    VARCHAR(50)     NOT NULL,
    product_name    VARCHAR(200)    NOT NULL,
    unit_type       VARCHAR(10)     NOT NULL,
    lot_number      VARCHAR(100),
    expiry_date     DATE,
    movement_type   VARCHAR(30)     NOT NULL,
    quantity        INT             NOT NULL,
    quantity_after  INT             NOT NULL,
    reference_id    BIGINT,
    reference_type  VARCHAR(50),
    correction_reason VARCHAR(500),
    executed_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    executed_by     BIGINT          NOT NULL REFERENCES users(id)
);
CREATE INDEX idx_inv_movements_wh_product ON inventory_movements (warehouse_id, product_id, executed_at);
CREATE INDEX idx_inv_movements_wh_location ON inventory_movements (warehouse_id, location_id, executed_at);
