-- ============================================================
-- V2: 認証関連テーブル
-- ============================================================

-- users（ユーザーマスタ）
CREATE TABLE users (
    id              bigserial       PRIMARY KEY,
    user_code       varchar(50)     NOT NULL,
    full_name       varchar(200)    NOT NULL,
    email           varchar(200)    NOT NULL,
    password_hash   varchar(255)    NOT NULL,
    role            varchar(30)     NOT NULL,
    is_active       boolean         NOT NULL DEFAULT true,
    version         integer         NOT NULL DEFAULT 0,
    password_change_required boolean NOT NULL DEFAULT true,
    failed_login_count int          NOT NULL DEFAULT 0,
    locked          boolean         NOT NULL DEFAULT false,
    locked_at       timestamptz     NULL,
    created_at      timestamptz     NOT NULL DEFAULT now(),
    created_by      bigint          NULL,
    updated_at      timestamptz     NOT NULL DEFAULT now(),
    updated_by      bigint          NULL,

    CONSTRAINT uk_users_user_code UNIQUE (user_code),
    CONSTRAINT ck_users_role CHECK (role IN ('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')),
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);

-- refresh_tokens（リフレッシュトークン）
CREATE TABLE refresh_tokens (
    id              bigserial       PRIMARY KEY,
    user_id         bigint          NOT NULL,
    token_hash      varchar(255)    NOT NULL,
    expires_at      timestamptz     NOT NULL,
    created_at      timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT uk_refresh_tokens_user_id UNIQUE (user_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);

-- password_reset_tokens（パスワードリセットトークン）
CREATE TABLE password_reset_tokens (
    id              bigserial       PRIMARY KEY,
    user_id         bigint          NOT NULL,
    token_hash      varchar(256)    NOT NULL,
    expires_at      timestamptz     NOT NULL,
    created_at      timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT uk_password_reset_tokens_user_id UNIQUE (user_id),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_password_reset_tokens_token_hash ON password_reset_tokens(token_hash);

-- system_parameters（システムパラメータ）
CREATE TABLE system_parameters (
    id              bigserial       PRIMARY KEY,
    param_key       varchar(100)    NOT NULL,
    param_value     varchar(500)    NOT NULL,
    default_value   varchar(500)    NOT NULL,
    display_name    varchar(200)    NOT NULL,
    category        varchar(50)     NOT NULL,
    value_type      varchar(20)     NOT NULL,
    description     varchar(500)    NULL,
    display_order   int             NOT NULL DEFAULT 0,
    version         integer         NOT NULL DEFAULT 0,
    updated_at      timestamptz     NOT NULL DEFAULT now(),
    updated_by      bigint          NULL,

    CONSTRAINT uk_system_parameters_param_key UNIQUE (param_key),
    CONSTRAINT fk_system_parameters_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);

-- ============================================================
-- 初期データ
-- ============================================================

-- 初期管理者ユーザー（パスワード: Admin@1234）
-- BCrypt hash generated with strength=12
INSERT INTO users (user_code, full_name, email, password_hash, role, is_active, password_change_required, created_at, updated_at)
VALUES ('admin', '初期管理者', 'admin@example.com',
        '$2a$12$LJ3m4ys3Gzl5sGBDVT0z6.YiGGqHBfNbMqOT3LzYMzRFGCpOVeKey',
        'SYSTEM_ADMIN', true, true, now(), now());

-- システムパラメータ初期データ
INSERT INTO system_parameters (param_key, param_value, default_value, display_name, category, value_type, description, display_order) VALUES
('LOCATION_CAPACITY_CASE',        '1',   '1',   'ロケーション収容上限（ケース）', 'INVENTORY', 'INTEGER', '1ロケーションあたりのケース最大数', 10),
('LOCATION_CAPACITY_BALL',        '6',   '6',   'ロケーション収容上限（ボール）', 'INVENTORY', 'INTEGER', '1ロケーションあたりのボール最大数', 20),
('LOCATION_CAPACITY_PIECE',       '100', '100', 'ロケーション収容上限（バラ）',   'INVENTORY', 'INTEGER', '1ロケーションあたりのバラ最大数',   30),
('LOGIN_FAILURE_LOCK_COUNT',      '5',   '5',   'ログイン失敗ロック回数',         'SECURITY',  'INTEGER', '連続ログイン失敗でアカウントをロックする回数', 100),
('SESSION_TIMEOUT_MINUTES',       '60',  '60',  'セッションタイムアウト（分）',    'SECURITY',  'INTEGER', '最終操作からセッションが失効するまでの時間（分）', 110),
('PASSWORD_RESET_EXPIRY_MINUTES', '30',  '30',  'パスワードリセットリンク有効期限（分）', 'SECURITY', 'INTEGER', 'パスワードリセットリンクの有効期限（分）', 120);
