-- V18: バッチ実行履歴・集計系テーブル作成
-- RPT-17（日次集計レポート）で使用するテーブル群

-- バッチ実行履歴
CREATE TABLE IF NOT EXISTS batch_execution_logs (
    id              BIGSERIAL       NOT NULL PRIMARY KEY,
    target_business_date DATE       NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'RUNNING',
    step1_status    VARCHAR(20),
    step2_status    VARCHAR(20),
    step3_status    VARCHAR(20),
    step4_status    VARCHAR(20),
    step5_status    VARCHAR(20),
    step6_status    VARCHAR(20),
    error_message   TEXT,
    started_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    executed_by     BIGINT          NOT NULL,
    CONSTRAINT uq_batch_execution_logs_date UNIQUE (target_business_date)
);

-- 日次集計レコード（日替処理で自動生成。RPT-17のデータソース）
CREATE TABLE IF NOT EXISTS daily_summary_records (
    id                          BIGSERIAL   NOT NULL PRIMARY KEY,
    business_date               DATE        NOT NULL,
    warehouse_id                BIGINT      NOT NULL,
    warehouse_code              VARCHAR(50) NOT NULL,
    inbound_count               INT         NOT NULL DEFAULT 0,
    inbound_line_count          INT         NOT NULL DEFAULT 0,
    inbound_quantity_total      BIGINT      NOT NULL DEFAULT 0,
    outbound_count              INT         NOT NULL DEFAULT 0,
    outbound_line_count         INT         NOT NULL DEFAULT 0,
    outbound_quantity_total     BIGINT      NOT NULL DEFAULT 0,
    return_count                INT         NOT NULL DEFAULT 0,
    return_quantity_total       INT         NOT NULL DEFAULT 0,
    inventory_quantity_total    BIGINT      NOT NULL DEFAULT 0,
    unreceived_count            INT         NOT NULL DEFAULT 0,
    unshipped_count             INT         NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_daily_summary_date_wh UNIQUE (business_date, warehouse_id)
);

-- 入荷実績サマリー
CREATE TABLE IF NOT EXISTS inbound_summaries (
    id                      BIGSERIAL   NOT NULL PRIMARY KEY,
    business_date           DATE        NOT NULL,
    warehouse_id            BIGINT      NOT NULL,
    warehouse_code          VARCHAR(50) NOT NULL,
    inbound_count           INT         NOT NULL,
    inbound_line_count      INT         NOT NULL,
    inbound_quantity_total  BIGINT      NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_inbound_summaries_date_wh UNIQUE (business_date, warehouse_id)
);

-- 出荷実績サマリー
CREATE TABLE IF NOT EXISTS outbound_summaries (
    id                      BIGSERIAL   NOT NULL PRIMARY KEY,
    business_date           DATE        NOT NULL,
    warehouse_id            BIGINT      NOT NULL,
    warehouse_code          VARCHAR(50) NOT NULL,
    outbound_count          INT         NOT NULL,
    outbound_line_count     INT         NOT NULL,
    outbound_quantity_total BIGINT      NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_outbound_summaries_date_wh UNIQUE (business_date, warehouse_id)
);

-- 在庫スナップショット
CREATE TABLE IF NOT EXISTS inventory_snapshots (
    id              BIGSERIAL       NOT NULL PRIMARY KEY,
    business_date   DATE            NOT NULL,
    warehouse_id    BIGINT          NOT NULL,
    warehouse_code  VARCHAR(50)     NOT NULL,
    product_id      BIGINT          NOT NULL,
    product_code    VARCHAR(50)     NOT NULL,
    product_name    VARCHAR(200)    NOT NULL,
    unit_type       VARCHAR(10)     NOT NULL,
    total_quantity  BIGINT          NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_inventory_snapshots UNIQUE (business_date, warehouse_id, product_id, unit_type)
);
