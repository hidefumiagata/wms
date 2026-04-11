-- STRING型・BOOLEAN型システムパラメータ追加 (#413)
INSERT INTO system_parameters (param_key, param_value, default_value, display_name, category, value_type, description, display_order) VALUES
('DEFAULT_WAREHOUSE_CODE',      'WH001', 'WH001', 'デフォルト倉庫コード', 'SYSTEM',   'STRING',  'システム全体のデフォルト倉庫コード',              200),
('AUTO_ALLOCATE_ON_OUTBOUND',   'true',  'true',  '出庫時自動引当',       'OUTBOUND', 'BOOLEAN', '出庫指示登録時に在庫自動引当を行うかどうか', 50);
