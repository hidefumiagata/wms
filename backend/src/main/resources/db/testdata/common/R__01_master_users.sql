-- ============================================================
-- Repeatable migration: テストユーザー（共通マスタ）
-- admin (id=1) は V2 で作成済み。追加ユーザーのみ投入。
-- パスワードは全員 "Test@1234" (BCrypt strength=12)
-- ============================================================

INSERT INTO users (user_code, full_name, email, password_hash, role, is_active, password_change_required, created_by, updated_by, created_at, updated_at)
SELECT v.user_code, v.full_name, v.email, v.password_hash, v.role, true, false, 1, 1, now(), now()
FROM (VALUES
    ('manager01', '倉庫管理者 太郎', 'manager01@example.com', '$2a$12$LJ3m4ys3Gzl5sGBDVT0z6.YiGGqHBfNbMqOT3LzYMzRFGCpOVeKey', 'WAREHOUSE_MANAGER'),
    ('staff01',   '倉庫作業員 花子', 'staff01@example.com',   '$2a$12$LJ3m4ys3Gzl5sGBDVT0z6.YiGGqHBfNbMqOT3LzYMzRFGCpOVeKey', 'WAREHOUSE_STAFF'),
    ('staff02',   '倉庫作業員 次郎', 'staff02@example.com',   '$2a$12$LJ3m4ys3Gzl5sGBDVT0z6.YiGGqHBfNbMqOT3LzYMzRFGCpOVeKey', 'WAREHOUSE_STAFF'),
    ('viewer01',  '閲覧者 三郎',     'viewer01@example.com',  '$2a$12$LJ3m4ys3Gzl5sGBDVT0z6.YiGGqHBfNbMqOT3LzYMzRFGCpOVeKey', 'VIEWER')
) AS v(user_code, full_name, email, password_hash, role)
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.user_code = v.user_code);
