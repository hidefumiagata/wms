-- 未入荷リスト確定テーブル
-- 日替処理で自動生成。入荷予定日が対象営業日以前で入庫完了していない入荷予定を記録する。
CREATE TABLE unreceived_list_records (
    id              BIGSERIAL       PRIMARY KEY,
    batch_business_date DATE        NOT NULL,
    inbound_slip_id BIGINT          NOT NULL REFERENCES inbound_slips(id),
    slip_number     VARCHAR(50)     NOT NULL,
    planned_date    DATE            NOT NULL,
    warehouse_code  VARCHAR(50)     NOT NULL,
    partner_code    VARCHAR(50),
    partner_name    VARCHAR(200),
    product_code    VARCHAR(50)     NOT NULL,
    product_name    VARCHAR(200)    NOT NULL,
    unit_type       VARCHAR(10)     NOT NULL,
    planned_qty     INT             NOT NULL,
    current_status  VARCHAR(30)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_unreceived_list_records_batch_wh
    ON unreceived_list_records (batch_business_date, warehouse_code);
