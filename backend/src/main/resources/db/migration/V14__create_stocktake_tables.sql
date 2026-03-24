-- 棚卸ヘッダ
CREATE TABLE stocktake_headers (
    id BIGSERIAL PRIMARY KEY,
    stocktake_number VARCHAR(50) NOT NULL,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
    target_description VARCHAR(500),
    stocktake_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'STARTED',
    note TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_by BIGINT NOT NULL REFERENCES users(id),
    confirmed_at TIMESTAMPTZ,
    confirmed_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_stocktake_number UNIQUE (stocktake_number)
);

CREATE INDEX idx_stocktake_headers_warehouse ON stocktake_headers(warehouse_id);
CREATE INDEX idx_stocktake_headers_status ON stocktake_headers(status);

-- 棚卸明細
CREATE TABLE stocktake_lines (
    id BIGSERIAL PRIMARY KEY,
    stocktake_header_id BIGINT NOT NULL REFERENCES stocktake_headers(id),
    location_id BIGINT NOT NULL REFERENCES locations(id),
    location_code VARCHAR(50) NOT NULL,
    product_id BIGINT NOT NULL REFERENCES products(id),
    product_code VARCHAR(50) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    unit_type VARCHAR(10) NOT NULL,
    lot_number VARCHAR(100),
    expiry_date DATE,
    quantity_before INT NOT NULL,
    quantity_counted INT,
    quantity_diff INT,
    is_counted BOOLEAN NOT NULL DEFAULT false,
    counted_at TIMESTAMPTZ,
    counted_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_stocktake_lines_header ON stocktake_lines(stocktake_header_id, location_code);
