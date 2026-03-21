CREATE TABLE buildings (
    id bigserial PRIMARY KEY,
    building_code varchar(10) NOT NULL,
    building_name varchar(200) NOT NULL,
    warehouse_id bigint NOT NULL REFERENCES warehouses(id),
    is_active boolean NOT NULL DEFAULT true,
    version integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by bigint NOT NULL REFERENCES users(id),
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL REFERENCES users(id),
    CONSTRAINT buildings_warehouse_building_code_key UNIQUE (warehouse_id, building_code)
);

CREATE INDEX idx_buildings_warehouse_id ON buildings(warehouse_id);
CREATE INDEX idx_buildings_is_active ON buildings(is_active);
