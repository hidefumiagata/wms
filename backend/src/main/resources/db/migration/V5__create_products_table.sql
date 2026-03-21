-- 商品マスタ
CREATE TABLE products (
    id              bigserial       PRIMARY KEY,
    product_code    varchar(50)     NOT NULL,
    product_name    varchar(200)    NOT NULL,
    product_name_kana varchar(200),
    case_quantity   int             NOT NULL,
    ball_quantity   int             NOT NULL,
    barcode         varchar(100),
    storage_condition varchar(20)   NOT NULL,
    is_hazardous    boolean         NOT NULL DEFAULT false,
    lot_manage_flag boolean         NOT NULL DEFAULT false,
    expiry_manage_flag boolean      NOT NULL DEFAULT false,
    shipment_stop_flag boolean      NOT NULL DEFAULT false,
    is_active       boolean         NOT NULL DEFAULT true,
    version         integer         NOT NULL DEFAULT 0,
    created_at      timestamptz     NOT NULL DEFAULT now(),
    created_by      bigint          NOT NULL REFERENCES users(id),
    updated_at      timestamptz     NOT NULL DEFAULT now(),
    updated_by      bigint          NOT NULL REFERENCES users(id),

    CONSTRAINT products_product_code_key    UNIQUE (product_code),
    CONSTRAINT products_storage_condition_check
        CHECK (storage_condition IN ('AMBIENT', 'REFRIGERATED', 'FROZEN')),
    CONSTRAINT products_case_quantity_positive  CHECK (case_quantity >= 1),
    CONSTRAINT products_ball_quantity_positive  CHECK (ball_quantity >= 1)
);

CREATE INDEX idx_products_storage_condition ON products(storage_condition);
CREATE INDEX idx_products_is_active         ON products(is_active);
CREATE INDEX idx_products_shipment_stop     ON products(shipment_stop_flag);
