-- V26: バッチ処理基盤テーブル追加
-- business_date テーブル、unshipped_list_records テーブル、バックアップテーブル群

-- 営業日管理テーブル（1行のみ）
CREATE TABLE IF NOT EXISTS business_date (
    id                      INTEGER         NOT NULL PRIMARY KEY DEFAULT 1,
    current_business_date   DATE            NOT NULL,
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_by              BIGINT,
    CONSTRAINT ck_business_date_single_row CHECK (id = 1)
);

-- 初期データ投入（現在日付）
INSERT INTO business_date (id, current_business_date, updated_at)
VALUES (1, CURRENT_DATE, now())
ON CONFLICT (id) DO NOTHING;

-- 未出荷リストレコード
CREATE TABLE IF NOT EXISTS unshipped_list_records (
    id                  BIGSERIAL       NOT NULL PRIMARY KEY,
    batch_business_date DATE            NOT NULL,
    outbound_slip_id    BIGINT          NOT NULL,
    slip_number         VARCHAR(50)     NOT NULL,
    planned_date        DATE            NOT NULL,
    warehouse_code      VARCHAR(50)     NOT NULL,
    partner_code        VARCHAR(50),
    partner_name        VARCHAR(200),
    product_code        VARCHAR(50)     NOT NULL,
    product_name        VARCHAR(200)    NOT NULL,
    unit_type           VARCHAR(10)     NOT NULL,
    ordered_qty         INT             NOT NULL,
    current_status      VARCHAR(30)     NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_unshipped_list_records_bdate_wh
    ON unshipped_list_records (batch_business_date, warehouse_code);

-- バックアップテーブル群（入荷伝票）
CREATE TABLE IF NOT EXISTS inbound_slips_backup (
    LIKE inbound_slips INCLUDING ALL,
    backed_up_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    batch_execution_log_id  BIGINT NOT NULL
);

-- バックアップテーブル群（入荷伝票明細）
CREATE TABLE IF NOT EXISTS inbound_slip_lines_backup (
    LIKE inbound_slip_lines INCLUDING ALL,
    backed_up_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    batch_execution_log_id  BIGINT NOT NULL
);

-- バックアップテーブル群（出荷伝票）
CREATE TABLE IF NOT EXISTS outbound_slips_backup (
    LIKE outbound_slips INCLUDING ALL,
    backed_up_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    batch_execution_log_id  BIGINT NOT NULL
);

-- バックアップテーブル群（出荷伝票明細）
CREATE TABLE IF NOT EXISTS outbound_slip_lines_backup (
    LIKE outbound_slip_lines INCLUDING ALL,
    backed_up_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    batch_execution_log_id  BIGINT NOT NULL
);

-- バックアップテーブル群（在庫移���）
CREATE TABLE IF NOT EXISTS inventory_movements_backup (
    LIKE inventory_movements INCLUDING ALL,
    backed_up_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    batch_execution_log_id  BIGINT NOT NULL
);

-- batch_execution_logs の UNIQUE 制約を削除（再実行で FAILEDレコード削除→新規INSERT の流れに対応）
-- 代わりに部分ユニーク制約を追加（RUNNINGまたはSUCCESS状態のレコードのみ一意制約）
ALTER TABLE batch_execution_logs DROP CONSTRAINT IF EXISTS uq_batch_execution_logs_date;
CREATE UNIQUE INDEX IF NOT EXISTS uq_batch_execution_logs_date_active
    ON batch_execution_logs (target_business_date)
    WHERE status IN ('RUNNING', 'SUCCESS');
