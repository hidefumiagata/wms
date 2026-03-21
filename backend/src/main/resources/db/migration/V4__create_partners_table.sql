-- ============================================================
-- V4: 取引先マスタテーブル
-- ============================================================

CREATE TABLE partners (
    id                  bigserial       PRIMARY KEY,
    partner_code        varchar(50)     NOT NULL,
    partner_name        varchar(200)    NOT NULL,
    partner_name_kana   varchar(200)    NULL,
    partner_type        varchar(20)     NOT NULL,
    address             varchar(500)    NULL,
    phone               varchar(50)     NULL,
    contact_person      varchar(100)    NULL,
    email               varchar(200)    NULL,
    is_active           boolean         NOT NULL DEFAULT true,
    version             integer         NOT NULL DEFAULT 0,
    created_at          timestamptz     NOT NULL DEFAULT now(),
    created_by          bigint          NULL,
    updated_at          timestamptz     NOT NULL DEFAULT now(),
    updated_by          bigint          NULL,

    CONSTRAINT uk_partners_partner_code UNIQUE (partner_code),
    CONSTRAINT chk_partners_partner_type CHECK (partner_type IN ('SUPPLIER', 'CUSTOMER', 'BOTH')),
    CONSTRAINT fk_partners_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_partners_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);

CREATE INDEX idx_partners_partner_type ON partners(partner_type);
CREATE INDEX idx_partners_is_active ON partners(is_active);
