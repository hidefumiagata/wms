-- ピッキング指示ヘッダ
CREATE TABLE picking_instructions (
    id              BIGSERIAL       NOT NULL,
    instruction_number VARCHAR(50)  NOT NULL,
    warehouse_id    BIGINT          NOT NULL,
    area_id         BIGINT          NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'CREATED',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by      BIGINT          NOT NULL,
    completed_at    TIMESTAMPTZ     NULL,
    completed_by    BIGINT          NULL,
    CONSTRAINT pk_picking_instructions PRIMARY KEY (id),
    CONSTRAINT uq_picking_instructions_number UNIQUE (instruction_number),
    CONSTRAINT fk_picking_instructions_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_picking_instructions_area FOREIGN KEY (area_id) REFERENCES areas(id),
    CONSTRAINT fk_picking_instructions_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_picking_instructions_completed_by FOREIGN KEY (completed_by) REFERENCES users(id)
);

CREATE INDEX idx_picking_instructions_warehouse_status ON picking_instructions (warehouse_id, status);

-- ピッキング指示明細
CREATE TABLE picking_instruction_lines (
    id                      BIGSERIAL       NOT NULL,
    picking_instruction_id  BIGINT          NOT NULL,
    line_no                 INT             NOT NULL,
    outbound_slip_line_id   BIGINT          NOT NULL,
    location_id             BIGINT          NOT NULL,
    location_code           VARCHAR(50)     NOT NULL,
    product_id              BIGINT          NOT NULL,
    product_code            VARCHAR(50)     NOT NULL,
    product_name            VARCHAR(200)    NOT NULL,
    unit_type               VARCHAR(10)     NOT NULL,
    lot_number              VARCHAR(100)    NULL,
    expiry_date             DATE            NULL,
    qty_to_pick             INT             NOT NULL,
    qty_picked              INT             NOT NULL DEFAULT 0,
    line_status             VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    completed_at            TIMESTAMPTZ     NULL,
    completed_by            BIGINT          NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT pk_picking_instruction_lines PRIMARY KEY (id),
    CONSTRAINT uq_picking_instruction_lines_no UNIQUE (picking_instruction_id, line_no),
    CONSTRAINT fk_pil_picking_instruction FOREIGN KEY (picking_instruction_id) REFERENCES picking_instructions(id),
    CONSTRAINT fk_pil_outbound_slip_line FOREIGN KEY (outbound_slip_line_id) REFERENCES outbound_slip_lines(id),
    CONSTRAINT fk_pil_location FOREIGN KEY (location_id) REFERENCES locations(id),
    CONSTRAINT fk_pil_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_pil_completed_by FOREIGN KEY (completed_by) REFERENCES users(id)
);

CREATE INDEX idx_pil_outbound_slip_line ON picking_instruction_lines (outbound_slip_line_id);
CREATE INDEX idx_pil_instruction_location ON picking_instruction_lines (picking_instruction_id, location_code);
