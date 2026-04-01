-- ============================================================
-- Repeatable migration: 結合テスト用認証テストユーザー
-- ============================================================

INSERT INTO users (user_code, full_name, email, password_hash, role, is_active, password_change_required, failed_login_count, locked, locked_at, created_by, updated_by, created_at, updated_at)
SELECT v.user_code, v.full_name, v.email, v.password_hash, v.role, v.is_active, v.password_change_required, v.failed_login_count, v.locked, v.locked_at, 1, 1, now(), now()
FROM (VALUES
    ('admin001',      '管理者 001',                   'admin001@example.com',      '$2a$12$skg3ZnV5KsoXyeJhBCGTPuPjC148MJusitNnHzLgUr34qkxe/PuHG', 'SYSTEM_ADMIN',       true,  false, 0, false, NULL::timestamptz),
    ('wh_manager01',  '倉庫管理者 001',               'wh_manager01@example.com',  '$2a$12$TntL0dGRVd7PsnUU3saqAeYibge4ojQEyylSmUade3Bmfb9LbTECy', 'WAREHOUSE_MANAGER',  true,  false, 0, false, NULL::timestamptz),
    ('wh_staff01',    '倉庫作業員 001',               'wh_staff01@example.com',    '$2a$12$BolNxtVPd6UXrjU6UhK5BOWkSt2pxpnI4iy7pZojUgoN69TwvJ7gi', 'WAREHOUSE_STAFF',    true,  false, 0, false, NULL::timestamptz),
    ('locked_user',   'ロック済みユーザー',           'locked_user@example.com',   '$2a$12$X6tdu2gavkwseJrwZ1ZjiOaJLNiMWkAb194U5RZNOcVNFwbILsxne', 'WAREHOUSE_STAFF',    true,  false, 5, true,  now()),
    ('inactive_user', '無効ユーザー',                 'inactive_user@example.com', '$2a$12$X6tdu2gavkwseJrwZ1ZjiOaJLNiMWkAb194U5RZNOcVNFwbILsxne', 'WAREHOUSE_STAFF',    false, false, 0, false, NULL::timestamptz),
    ('lock_target',   'ロック対象ユーザー',           'lock_target@example.com',   '$2a$12$H9eUUbl9W6GOBbchegSSnuQ8oi.MBJPBD1ttmujMnkuY6cCbdn1sy', 'WAREHOUSE_STAFF',    true,  false, 0, false, NULL::timestamptz),
    ('new_user',      '初回パスワード変更ユーザー',   'new_user@example.com',      '$2a$12$0Y7fIyoybKcvnJg0rHJmoefnGSvVUQs0GmFBsv0Jt0bcOESrTEziK', 'WAREHOUSE_STAFF',    true,  true,  0, false, NULL::timestamptz),
    ('pw_change_user','パスワード変更ユーザー',       'pw_change_user@example.com','$2a$12$0Jyy9aA3U1NJuTk0UM9NIuQTqfl4NfBdOu5wBMDK71CTGSYtRyPzm', 'WAREHOUSE_MANAGER',  true,  false, 0, false, NULL::timestamptz),
    ('reset_user',    'リセットユーザー',             'reset_user@example.com',    '$2a$12$5JtcSjImM9V1tzkXK0luoOPx/eivHz7/YtPNxwYsrhzC7o890ineG', 'WAREHOUSE_STAFF',    true,  false, 0, false, NULL::timestamptz),
    ('pw_lock_user',  'パスワードロックユーザー',     'pw_lock_user@example.com',  '$2a$12$TntL0dGRVd7PsnUU3saqAeYibge4ojQEyylSmUade3Bmfb9LbTECy', 'WAREHOUSE_MANAGER',  true,  false, 0, false, NULL::timestamptz)
) AS v(user_code, full_name, email, password_hash, role, is_active, password_change_required, failed_login_count, locked, locked_at)
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.user_code = v.user_code);
