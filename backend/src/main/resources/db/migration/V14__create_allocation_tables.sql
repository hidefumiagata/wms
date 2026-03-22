-- 引当明細（受注明細と在庫の紐付け）
CREATE TABLE allocation_details (
    id                      BIGSERIAL       PRIMARY KEY,
    outbound_slip_id        BIGINT          NOT NULL REFERENCES outbound_slips(id),
    outbound_slip_line_id   BIGINT          NOT NULL REFERENCES outbound_slip_lines(id),
    inventory_id            BIGINT          NOT NULL REFERENCES inventories(id),
    location_id             BIGINT          NOT NULL REFERENCES locations(id),
    product_id              BIGINT          NOT NULL REFERENCES products(id),
    unit_type               VARCHAR(10)     NOT NULL,
    lot_number              VARCHAR(100),
    expiry_date             DATE,
    allocated_qty           INT             NOT NULL,
    warehouse_id            BIGINT          NOT NULL REFERENCES warehouses(id),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by              BIGINT          NOT NULL REFERENCES users(id),

    CONSTRAINT chk_allocation_details_unit_type CHECK (unit_type IN ('CASE', 'BALL', 'PIECE')),
    CONSTRAINT chk_allocation_details_qty CHECK (allocated_qty > 0)
);

CREATE INDEX idx_allocation_details_slip_line ON allocation_details (outbound_slip_line_id);
CREATE INDEX idx_allocation_details_inventory ON allocation_details (inventory_id);
CREATE INDEX idx_allocation_details_slip ON allocation_details (outbound_slip_id);

-- ばらし指示（引当時に自動生成）
CREATE TABLE unpack_instructions (
    id                  BIGSERIAL       PRIMARY KEY,
    outbound_slip_id    BIGINT          NOT NULL REFERENCES outbound_slips(id),
    location_id         BIGINT          NOT NULL REFERENCES locations(id),
    product_id          BIGINT          NOT NULL REFERENCES products(id),
    from_unit_type      VARCHAR(10)     NOT NULL,
    from_qty            INT             NOT NULL,
    to_unit_type        VARCHAR(10)     NOT NULL,
    to_qty              INT             NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'INSTRUCTED',
    warehouse_id        BIGINT          NOT NULL REFERENCES warehouses(id),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by          BIGINT          NOT NULL REFERENCES users(id),
    completed_at        TIMESTAMPTZ,
    completed_by        BIGINT          REFERENCES users(id),

    CONSTRAINT chk_unpack_from_unit CHECK (from_unit_type IN ('CASE', 'BALL')),
    CONSTRAINT chk_unpack_to_unit CHECK (to_unit_type IN ('BALL', 'PIECE')),
    CONSTRAINT chk_unpack_status CHECK (status IN ('INSTRUCTED', 'COMPLETED')),
    CONSTRAINT chk_unpack_from_qty CHECK (from_qty > 0),
    CONSTRAINT chk_unpack_to_qty CHECK (to_qty > 0)
);

CREATE INDEX idx_unpack_instructions_slip ON unpack_instructions (outbound_slip_id);
CREATE INDEX idx_unpack_instructions_status ON unpack_instructions (status);
