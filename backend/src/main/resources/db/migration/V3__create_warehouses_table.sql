-- ============================================================
-- V3: 倉庫マスタテーブル
-- ============================================================

CREATE TABLE warehouses (
    id                  bigserial       PRIMARY KEY,
    warehouse_code      varchar(50)     NOT NULL,
    warehouse_name      varchar(200)    NOT NULL,
    warehouse_name_kana varchar(200)    NULL,
    address             varchar(500)    NULL,
    is_active           boolean         NOT NULL DEFAULT true,
    version             integer         NOT NULL DEFAULT 0,
    created_at          timestamptz     NOT NULL DEFAULT now(),
    created_by          bigint          NULL,
    updated_at          timestamptz     NOT NULL DEFAULT now(),
    updated_by          bigint          NULL,

    CONSTRAINT uk_warehouses_warehouse_code UNIQUE (warehouse_code),
    CONSTRAINT fk_warehouses_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_warehouses_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);
