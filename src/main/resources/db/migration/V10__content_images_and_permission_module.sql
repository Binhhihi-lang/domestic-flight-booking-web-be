-- =====================================================================
-- V10__content_images_and_permission_module.sql
-- Bổ sung ngoài DB v0.4 (ghi ở docs/De_xuat_bo_sung_DB_v0.5_truong_anh.md
-- mục 7 — phản hồi của BE, đã chốt 12/10/2026):
--
--   1. Cột ảnh/mô tả/nhãn cho News, City, Promotion (đề xuất của FE).
--   2. permissions.module — API Design v0.2 mục 2.4 cho phép lọc `?module=`
--      và trả `module` trong response, nhưng schema chưa có cột.
--   3. Seed 2 quyền VOUCHER_CLAIM + WALLET_READ_SELF (DB v0.4 A.3 yêu cầu
--      cấp cho CUSTOMER, nhưng V2 chưa có).
-- =====================================================================

-- ========== 1. Cột ảnh/mô tả/nhãn ==========

-- Ảnh đại diện bài viết tin tức (thẻ tin trang chủ + trang danh sách)
ALTER TABLE news_items ADD COLUMN thumbnail_url VARCHAR(500);

-- Ảnh đại diện thành phố (khối "Tuyến bay phổ biến"; DB không có bảng routes,
-- tuyến chỉ là cặp thành phố nên ảnh gắn vào thành phố)
ALTER TABLE cities ADD COLUMN image_url VARCHAR(500);

-- Ảnh + mô tả + nhãn marketing cho chương trình khuyến mãi
ALTER TABLE promotions ADD COLUMN image_url VARCHAR(500);
ALTER TABLE promotions ADD COLUMN description VARCHAR(500);
ALTER TABLE promotions ADD COLUMN badge_label VARCHAR(30);

-- ========== 2. permissions.module ==========

ALTER TABLE permissions ADD COLUMN module VARCHAR(30);

-- Backfill theo tiền tố permission_key, chọn tiền tố DÀI NHẤT khớp trước
-- (để FLIGHT_FARE_* vào FLIGHT_FARE chứ không phải FLIGHT, NEWS_CATEGORY_*
-- vào NEWS_CATEGORY chứ không phải NEWS).
WITH module_map(prefix, module) AS (
    VALUES
        ('NEWS_CATEGORY_',  'NEWS_CATEGORY'),
        ('NEWS_',           'NEWS'),
        ('SEAT_INVENTORY_', 'SEAT_INVENTORY'),
        ('SEAT_CONFIG_',    'SEAT_CONFIG'),
        ('FLIGHT_FARE_',    'FLIGHT_FARE'),
        ('FLIGHT_',         'FLIGHT'),
        ('FARE_CLASS_',     'FARE'),
        ('FARE_RULE_',      'FARE'),
        ('AIRLINE_',        'AIRLINE'),
        ('AIRCRAFT_',       'AIRCRAFT'),
        ('AIRPORT_',        'AIRPORT'),
        ('BAGGAGE_',        'BAGGAGE'),
        ('BOOKING_',        'BOOKING'),
        ('PAYMENT_',        'PAYMENT'),
        ('PASSENGER_',      'PASSENGER'),
        ('PROMOTION_',      'PROMOTION'),
        ('VOUCHER_',        'VOUCHER'),
        ('CITY_',           'CITY'),
        ('USER_',           'USER'),
        ('ROLE_',           'USER'),
        ('ETICKET_',        'ETICKET'),
        ('DASHBOARD_',      'DASHBOARD'),
        ('NOTIFICATION_',   'NOTIFICATION'),
        ('PASSWORD_',       'AUTH'),
        ('PROFILE_',        'AUTH'),
        ('LOGIN',           'AUTH'),
        ('LOGOUT',          'AUTH')
),
best_match AS (
    SELECT DISTINCT ON (p.id) p.id, mm.module
    FROM permissions p
    JOIN module_map mm ON p.permission_key LIKE mm.prefix || '%'
    ORDER BY p.id, length(mm.prefix) DESC
)
UPDATE permissions p
SET module = bm.module
FROM best_match bm
WHERE p.id = bm.id;

-- Mọi quyền phải khớp được một module; nếu còn NULL thì map ở trên thiếu tiền tố
ALTER TABLE permissions ALTER COLUMN module SET NOT NULL;

-- ========== 3. Seed 2 quyền còn thiếu (DB v0.4 A.3) ==========

INSERT INTO permissions (permission_key, description, module) VALUES
    ('VOUCHER_CLAIM',    'Nhận voucher vào ví của mình',              'VOUCHER'),
    ('WALLET_READ_SELF', 'Xem ví và lịch sử giao dịch của mình',      'WALLET');

-- Cấp cho CUSTOMER (đúng DB v0.4 A.3)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.permission_key IN ('VOUCHER_CLAIM', 'WALLET_READ_SELF')
WHERE r.name = 'CUSTOMER';

-- ADMIN giữ toàn quyền: V2 đã cấp mọi permission hiện có cho ADMIN, nên 2 quyền
-- thêm sau phải cấp bù (cùng loại trừ REGISTER/PASSWORD_RESET là endpoint public)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.permission_key IN ('VOUCHER_CLAIM', 'WALLET_READ_SELF')
WHERE r.name = 'ADMIN';
