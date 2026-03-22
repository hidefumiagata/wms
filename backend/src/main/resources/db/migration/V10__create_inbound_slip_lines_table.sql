-- 入荷伝票明細
CREATE TABLE inbound_slip_lines (
    id                      BIGSERIAL       PRIMARY KEY,
    inbound_slip_id         BIGINT          NOT NULL REFERENCES inbound_slips(id),
    line_no                 INT             NOT NULL,
    product_id              BIGINT          NOT NULL REFERENCES products(id),
    product_code            VARCHAR(50)     NOT NULL,
    product_name            VARCHAR(200)    NOT NULL,
    unit_type               VARCHAR(10)     NOT NULL,
    planned_qty             INT             NOT NULL,
    inspected_qty           INT,
    lot_number              VARCHAR(100),
    expiry_date             DATE,
    putaway_location_id     BIGINT          REFERENCES locations(id),
    putaway_location_code   VARCHAR(50),
    line_status             VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    inspected_at            TIMESTAMPTZ,
    inspected_by            BIGINT          REFERENCES users(id),
    stored_at               TIMESTAMPTZ,
    stored_by               BIGINT          REFERENCES users(id),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                 INT             NOT NULL DEFAULT 0,

    CONSTRAINT uq_inbound_slip_lines_slip_line UNIQUE (inbound_slip_id, line_no),
    CONSTRAINT chk_inbound_slip_lines_unit_type CHECK (unit_type IN ('CASE', 'BALL', 'PIECE')),
    CONSTRAINT chk_inbound_slip_lines_line_status CHECK (line_status IN ('PENDING', 'INSPECTED', 'STORED')),
    CONSTRAINT chk_inbound_slip_lines_planned_qty CHECK (planned_qty > 0)
);
