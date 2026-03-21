CREATE TABLE areas (
    id bigserial PRIMARY KEY,
    area_code varchar(20) NOT NULL,
    area_name varchar(200) NOT NULL,
    warehouse_id bigint NOT NULL REFERENCES warehouses(id),
    building_id bigint NOT NULL REFERENCES buildings(id),
    storage_condition varchar(20) NOT NULL,
    area_type varchar(20) NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    version integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by bigint NOT NULL REFERENCES users(id),
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL REFERENCES users(id),
    CONSTRAINT areas_building_area_code_key UNIQUE (building_id, area_code),
    CONSTRAINT areas_storage_condition_check CHECK (storage_condition IN ('AMBIENT', 'REFRIGERATED', 'FROZEN')),
    CONSTRAINT areas_area_type_check CHECK (area_type IN ('STOCK', 'INBOUND', 'OUTBOUND', 'RETURN'))
);

CREATE INDEX idx_areas_warehouse_id ON areas(warehouse_id);
CREATE INDEX idx_areas_building_id ON areas(building_id);
CREATE INDEX idx_areas_area_type ON areas(area_type);
CREATE INDEX idx_areas_is_active ON areas(is_active);
