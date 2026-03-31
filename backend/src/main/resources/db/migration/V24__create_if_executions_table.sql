-- V24: I/F取り込み履歴テーブル
CREATE TABLE if_executions (
    id              BIGSERIAL       PRIMARY KEY,
    if_type         VARCHAR(30)     NOT NULL,
    file_name       VARCHAR(500)    NOT NULL,
    blob_path       VARCHAR(1000),
    total_count     INTEGER         NOT NULL DEFAULT 0,
    success_count   INTEGER         NOT NULL DEFAULT 0,
    error_count     INTEGER         NOT NULL DEFAULT 0,
    mode            VARCHAR(20)     NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    error_message   TEXT,
    blob_move_failed BOOLEAN        NOT NULL DEFAULT FALSE,
    warehouse_id    BIGINT          NOT NULL REFERENCES warehouses(id),
    executed_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    executed_by     BIGINT          NOT NULL REFERENCES users(id)
);

-- 履歴一覧取得用インデックス
CREATE INDEX idx_if_executions_type_executed_at ON if_executions (if_type, executed_at DESC);
CREATE INDEX idx_if_executions_warehouse_executed_at ON if_executions (warehouse_id, executed_at DESC);
