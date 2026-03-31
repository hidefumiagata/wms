-- 返品伝票テーブル
CREATE TABLE return_slips (
    id              BIGSERIAL       PRIMARY KEY,
    return_number   VARCHAR(30)     NOT NULL,
    return_type     VARCHAR(20)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'COMPLETED',
    warehouse_id    BIGINT          NOT NULL REFERENCES warehouses(id),
    warehouse_code  VARCHAR(50)     NOT NULL,
    warehouse_name  VARCHAR(200)    NOT NULL,
    partner_id      BIGINT          REFERENCES partners(id),
    partner_code    VARCHAR(50),
    partner_name    VARCHAR(200),
    product_id      BIGINT          NOT NULL REFERENCES products(id),
    product_code    VARCHAR(50)     NOT NULL,
    product_name    VARCHAR(200)    NOT NULL,
    quantity        INT             NOT NULL,
    unit_type       VARCHAR(10)     NOT NULL,
    location_id     BIGINT          REFERENCES locations(id),
    location_code   VARCHAR(50),
    return_reason   VARCHAR(30)     NOT NULL,
    return_reason_note TEXT,
    related_slip_number VARCHAR(50),
    lot_number      VARCHAR(100),
    expiry_date     DATE,
    return_date     DATE            NOT NULL,
    created_by      BIGINT          NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_by      BIGINT          NOT NULL REFERENCES users(id),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version         BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT uq_return_slips_return_number UNIQUE (return_number)
);

CREATE INDEX idx_return_slips_warehouse_date ON return_slips (warehouse_id, return_date);
CREATE INDEX idx_return_slips_product ON return_slips (product_id);
CREATE INDEX idx_return_slips_partner ON return_slips (partner_id);
CREATE INDEX idx_return_slips_type ON return_slips (return_type);
