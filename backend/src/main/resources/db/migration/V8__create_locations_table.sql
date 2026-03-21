CREATE TABLE locations (
    id bigserial PRIMARY KEY,
    location_code varchar(50) NOT NULL,
    location_name varchar(200),
    warehouse_id bigint NOT NULL REFERENCES warehouses(id),
    area_id bigint NOT NULL REFERENCES areas(id),
    is_active boolean NOT NULL DEFAULT true,
    is_stocktaking_locked boolean NOT NULL DEFAULT false,
    version integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by bigint NOT NULL REFERENCES users(id),
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL REFERENCES users(id),
    CONSTRAINT locations_warehouse_location_code_key UNIQUE (warehouse_id, location_code)
);

CREATE INDEX idx_locations_warehouse_id ON locations(warehouse_id);
CREATE INDEX idx_locations_area_id ON locations(area_id);
CREATE INDEX idx_locations_is_active ON locations(is_active);
