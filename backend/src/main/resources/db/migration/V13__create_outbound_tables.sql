-- 出荷伝票ヘッダー（受注）
CREATE TABLE outbound_slips (
    id                   BIGSERIAL       PRIMARY KEY,
    slip_number          VARCHAR(50)     NOT NULL,
    slip_type            VARCHAR(30)     NOT NULL,
    transfer_slip_number VARCHAR(50),
    warehouse_id         BIGINT          NOT NULL REFERENCES warehouses(id),
    warehouse_code       VARCHAR(50)     NOT NULL,
    warehouse_name       VARCHAR(200)    NOT NULL,
    partner_id           BIGINT          REFERENCES partners(id),
    partner_code         VARCHAR(50),
    partner_name         VARCHAR(200),
    planned_date         DATE            NOT NULL,
    carrier              VARCHAR(100),
    tracking_number      VARCHAR(100),
    status               VARCHAR(30)     NOT NULL DEFAULT 'ORDERED',
    note                 TEXT,
    shipped_at           TIMESTAMPTZ,
    shipped_by           BIGINT          REFERENCES users(id),
    cancelled_at         TIMESTAMPTZ,
    cancelled_by         BIGINT          REFERENCES users(id),
    cancel_reason        TEXT,
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by           BIGINT          NOT NULL REFERENCES users(id),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_by           BIGINT          NOT NULL REFERENCES users(id),
    version              INT             NOT NULL DEFAULT 0,

    CONSTRAINT uq_outbound_slips_slip_number UNIQUE (slip_number),
    CONSTRAINT chk_outbound_slips_slip_type CHECK (slip_type IN ('NORMAL', 'WAREHOUSE_TRANSFER')),
    CONSTRAINT chk_outbound_slips_status CHECK (status IN ('ORDERED', 'PARTIAL_ALLOCATED', 'ALLOCATED', 'PICKING_COMPLETED', 'INSPECTING', 'SHIPPED', 'CANCELLED'))
);

CREATE INDEX idx_outbound_slips_wh_planned ON outbound_slips (warehouse_id, planned_date);
CREATE INDEX idx_outbound_slips_wh_status ON outbound_slips (warehouse_id, status);
CREATE INDEX idx_outbound_slips_transfer ON outbound_slips (transfer_slip_number);

-- 出荷伝票明細
CREATE TABLE outbound_slip_lines (
    id                BIGSERIAL       PRIMARY KEY,
    outbound_slip_id  BIGINT          NOT NULL REFERENCES outbound_slips(id) ON DELETE CASCADE,
    line_no           INT             NOT NULL,
    product_id        BIGINT          NOT NULL REFERENCES products(id),
    product_code      VARCHAR(50)     NOT NULL,
    product_name      VARCHAR(200)    NOT NULL,
    unit_type         VARCHAR(10)     NOT NULL,
    ordered_qty       INT             NOT NULL,
    inspected_qty     INT,
    shipped_qty       INT             NOT NULL DEFAULT 0,
    line_status       VARCHAR(30)     NOT NULL DEFAULT 'ORDERED',
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_outbound_slip_lines_slip_line UNIQUE (outbound_slip_id, line_no),
    CONSTRAINT chk_outbound_slip_lines_unit_type CHECK (unit_type IN ('CASE', 'BALL', 'PIECE')),
    CONSTRAINT chk_outbound_slip_lines_status CHECK (line_status IN ('ORDERED', 'PARTIAL_ALLOCATED', 'ALLOCATED', 'PICKING_COMPLETED', 'SHIPPED', 'CANCELLED'))
);
