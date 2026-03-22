-- 入荷伝票ヘッダー
CREATE TABLE inbound_slips (
    id              BIGSERIAL       PRIMARY KEY,
    slip_number     VARCHAR(50)     NOT NULL,
    slip_type       VARCHAR(30)     NOT NULL,
    transfer_slip_number VARCHAR(50),
    warehouse_id    BIGINT          NOT NULL REFERENCES warehouses(id),
    warehouse_code  VARCHAR(50)     NOT NULL,
    warehouse_name  VARCHAR(200)    NOT NULL,
    partner_id      BIGINT          REFERENCES partners(id),
    partner_code    VARCHAR(50),
    partner_name    VARCHAR(200),
    planned_date    DATE            NOT NULL,
    status          VARCHAR(30)     NOT NULL DEFAULT 'PLANNED',
    note            TEXT,
    cancelled_at    TIMESTAMPTZ,
    cancelled_by    BIGINT          REFERENCES users(id),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by      BIGINT          NOT NULL REFERENCES users(id),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_by      BIGINT          NOT NULL REFERENCES users(id),
    version         INT             NOT NULL DEFAULT 0,

    CONSTRAINT uq_inbound_slips_slip_number UNIQUE (slip_number),
    CONSTRAINT chk_inbound_slips_slip_type CHECK (slip_type IN ('NORMAL', 'WAREHOUSE_TRANSFER')),
    CONSTRAINT chk_inbound_slips_status CHECK (status IN ('PLANNED', 'CONFIRMED', 'INSPECTING', 'PARTIAL_STORED', 'STORED', 'CANCELLED'))
);

CREATE INDEX idx_inbound_slips_wh_planned ON inbound_slips (warehouse_id, planned_date);
CREATE INDEX idx_inbound_slips_wh_status ON inbound_slips (warehouse_id, status);
CREATE INDEX idx_inbound_slips_transfer ON inbound_slips (transfer_slip_number);
