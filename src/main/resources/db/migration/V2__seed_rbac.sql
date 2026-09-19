-- =====================================================================
-- V2__seed_rbac.sql
-- Seed RBAC theo Permission Matrix v2.0 (đã duyệt, phạm vi 3 Phase)
--
-- Nguyên tắc:
--   * Role cố định: CUSTOMER, STAFF, ADMIN (BR-RBAC-03 — ADMIN không tạo
--     role mới). GUEST KHÔNG có role: là ràng buộc permitAll ở tầng Spring
--     Security, không seed vào bảng.
--   * ASSIGN_ROLE không phải permission độc lập: đổi role nằm trong
--     Update User (BR-RBAC-02).
--   * Quyết định xử lý mơ hồ của matrix (đã chốt):
--     1) FLIGHT_READ / FLIGHT_DETAIL_READ của "Flight Search" (GUEST public)
--        và "Flight Management" là CÙNG MỘT KEY duy nhất → seed 1 key, cấp
--        cho CUSTOMER/STAFF/ADMIN; quyền quản lý chỉ là CREATE/UPDATE/
--        ACTIVATE/DEACTIVATE (STAFF/ADMIN).
--     2) FARE_RULE_* và SEAT_INVENTORY_* không có bảng riêng trong v0
--        (nằm trong fare_classes / flight_seats / flight_fares) nhưng vẫn
--        seed key theo matrix đã duyệt — enforcement ở tầng service.
--     3) REGISTER + PASSWORD_RESET chỉ có ở cột GUEST → là endpoint công
--        khai (permitAll), không cấp cho role nào.
-- =====================================================================

INSERT INTO roles (name, description) VALUES
    ('CUSTOMER', 'Khách hàng đã đăng ký/đăng nhập — nghiệp vụ cá nhân và booking'),
    ('STAFF',    'Nhân viên vận hành — quản lý flight, fare, seat, booking'),
    ('ADMIN',    'Quản trị hệ thống — user, RBAC, master data, content');

-- -----------------------------------------------------------------
-- Permissions — nhóm theo module (id do IDENTITY tự sinh)
-- -----------------------------------------------------------------
INSERT INTO permissions (permission_key, description) VALUES
    -- Authentication & Account
    ('LOGIN',                  'Đăng nhập hệ thống'),
    ('LOGOUT',                 'Đăng xuất'),
    ('PASSWORD_CHANGE_SELF',   'Đổi mật khẩu của chính mình'),
    ('PROFILE_READ_SELF',      'Xem hồ sơ của chính mình'),
    ('PROFILE_UPDATE_SELF',    'Cập nhật hồ sơ của chính mình'),
    -- User Management (chỉ ADMIN)
    ('USER_READ',              'Xem danh sách người dùng'),
    ('USER_READ_DETAIL',       'Xem chi tiết người dùng'),
    ('USER_CREATE',            'Tạo người dùng (gồm chọn role)'),
    ('USER_UPDATE',            'Cập nhật người dùng (gồm đổi role)'),
    ('USER_DELETE',            'Vô hiệu người dùng'),
    ('ROLE_READ',              'Xem danh sách role'),
    -- Master Data: Airline
    ('AIRLINE_READ',           'Xem danh sách hãng hàng không'),
    ('AIRLINE_READ_DETAIL',    'Xem chi tiết hãng hàng không'),
    ('AIRLINE_CREATE',         'Tạo hãng hàng không'),
    ('AIRLINE_UPDATE',         'Cập nhật hãng hàng không'),
    ('AIRLINE_DELETE',         'Xóa hãng hàng không'),
    -- Master Data: City
    ('CITY_READ',              'Xem danh sách thành phố'),
    ('CITY_READ_DETAIL',       'Xem chi tiết thành phố'),
    ('CITY_CREATE',            'Tạo thành phố'),
    ('CITY_UPDATE',            'Cập nhật thành phố'),
    ('CITY_DELETE',            'Xóa thành phố'),
    -- Master Data: Airport
    ('AIRPORT_READ',           'Xem danh sách sân bay'),
    ('AIRPORT_READ_DETAIL',    'Xem chi tiết sân bay'),
    ('AIRPORT_CREATE',         'Tạo sân bay'),
    ('AIRPORT_UPDATE',         'Cập nhật sân bay'),
    ('AIRPORT_DELETE',         'Xóa sân bay'),
    -- Master Data: Aircraft
    ('AIRCRAFT_READ',          'Xem danh sách máy bay'),
    ('AIRCRAFT_READ_DETAIL',   'Xem chi tiết máy bay'),
    ('AIRCRAFT_CREATE',        'Tạo máy bay'),
    ('AIRCRAFT_UPDATE',        'Cập nhật máy bay'),
    ('AIRCRAFT_DELETE',        'Xóa máy bay'),
    -- Master Data: Seat Configuration
    ('SEAT_CONFIG_READ',       'Xem danh sách cấu hình ghế'),
    ('SEAT_CONFIG_READ_DETAIL','Xem chi tiết cấu hình ghế'),
    ('SEAT_CONFIG_CREATE',     'Tạo cấu hình ghế'),
    ('SEAT_CONFIG_UPDATE',     'Cập nhật cấu hình ghế'),
    ('SEAT_CONFIG_DELETE',     'Xóa cấu hình ghế'),
    -- Promotion
    ('PROMOTION_READ',         'Xem danh sách khuyến mãi'),
    ('PROMOTION_READ_DETAIL',  'Xem chi tiết khuyến mãi'),
    ('PROMOTION_CREATE',       'Tạo khuyến mãi'),
    ('PROMOTION_UPDATE',       'Cập nhật khuyến mãi'),
    ('PROMOTION_DELETE',       'Xóa khuyến mãi'),
    -- Voucher
    ('VOUCHER_READ',           'Xem danh sách voucher'),
    ('VOUCHER_READ_DETAIL',    'Xem chi tiết voucher'),
    ('VOUCHER_CREATE',         'Tạo voucher'),
    ('VOUCHER_UPDATE',         'Cập nhật voucher'),
    ('VOUCHER_DELETE',         'Xóa voucher'),
    ('VOUCHER_APPLY',          'Áp voucher vào booking'),
    -- News / News Category
    ('NEWS_CATEGORY_READ',     'Xem danh sách chuyên mục tin tức'),
    ('NEWS_CATEGORY_READ_DETAIL','Xem chi tiết chuyên mục tin tức'),
    ('NEWS_CATEGORY_CREATE',   'Tạo chuyên mục tin tức'),
    ('NEWS_CATEGORY_UPDATE',   'Cập nhật chuyên mục tin tức'),
    ('NEWS_CATEGORY_DELETE',   'Xóa chuyên mục tin tức'),
    ('NEWS_READ',              'Xem danh sách tin tức'),
    ('NEWS_READ_DETAIL',       'Xem chi tiết tin tức'),
    ('NEWS_CREATE',            'Tạo tin tức'),
    ('NEWS_UPDATE',            'Cập nhật tin tức'),
    ('NEWS_DELETE',            'Xóa tin tức'),
    ('NEWS_PUBLISH',           'Xuất bản tin tức'),
    ('NEWS_UNPUBLISH',         'Hủy xuất bản tin tức'),
    -- Flight: public search & management
    ('FLIGHT_SEARCH',          'Tìm kiếm chuyến bay (public)'),
    ('FLIGHT_READ',            'Xem danh sách chuyến bay'),
    ('FLIGHT_DETAIL_READ',     'Xem chi tiết chuyến bay'),
    ('FLIGHT_CREATE',          'Tạo chuyến bay'),
    ('FLIGHT_UPDATE',          'Cập nhật chuyến bay'),
    ('FLIGHT_ACTIVATE',        'Kích hoạt chuyến bay / mở bán'),
    ('FLIGHT_DEACTIVATE',      'Hủy / đóng chuyến bay'),
    -- Fare
    ('FARE_CLASS_READ',        'Xem danh sách hạng vé'),
    ('FARE_CLASS_READ_DETAIL', 'Xem chi tiết hạng vé'),
    ('FARE_CLASS_CREATE',      'Tạo hạng vé'),
    ('FARE_CLASS_UPDATE',      'Cập nhật hạng vé'),
    ('FARE_CLASS_DELETE',      'Xóa hạng vé'),
    ('FARE_RULE_READ',         'Xem danh sách quy định giá'),
    ('FARE_RULE_READ_DETAIL',  'Xem chi tiết quy định giá'),
    ('FARE_RULE_CREATE',       'Tạo quy định giá'),
    ('FARE_RULE_UPDATE',       'Cập nhật quy định giá'),
    ('FARE_RULE_DELETE',       'Xóa quy định giá'),
    -- Baggage
    ('BAGGAGE_READ',           'Xem danh sách hành lý'),
    ('BAGGAGE_READ_DETAIL',    'Xem chi tiết hành lý'),
    ('BAGGAGE_CREATE',         'Tạo gói hành lý'),
    ('BAGGAGE_UPDATE',         'Cập nhật gói hành lý'),
    ('BAGGAGE_DELETE',         'Xóa gói hành lý'),
    -- Flight Fare
    ('FLIGHT_FARE_READ',       'Xem danh sách giá vé theo chuyến'),
    ('FLIGHT_FARE_READ_DETAIL','Xem chi tiết giá vé theo chuyến'),
    ('FLIGHT_FARE_CREATE',     'Tạo giá vé theo chuyến'),
    ('FLIGHT_FARE_UPDATE',     'Cập nhật giá vé theo chuyến'),
    ('FLIGHT_FARE_DELETE',     'Xóa giá vé theo chuyến'),
    -- Seat Inventory
    ('SEAT_INVENTORY_READ',        'Xem tổng quan kho ghế'),
    ('SEAT_INVENTORY_READ_DETAIL', 'Xem chi tiết kho ghế chuyến bay'),
    ('SEAT_INVENTORY_MANAGE',      'Điều chỉnh kho ghế / trạng thái ghế'),
    -- Passenger
    ('PASSENGER_CREATE',       'Khai báo hành khách cho booking'),
    ('PASSENGER_READ_SELF',    'Xem hành khách của booking mình'),
    ('PASSENGER_UPDATE_SELF',  'Cập nhật hành khách của booking mình'),
    -- Booking
    ('BOOKING_CREATE',         'Tạo booking'),
    ('BOOKING_READ_SELF',      'Xem booking của mình'),
    ('BOOKING_READ',           'Xem mọi booking (vận hành)'),
    ('BOOKING_CANCEL_SELF',    'Hủy booking của mình'),
    -- Payment
    ('PAYMENT_CREATE',         'Tạo giao dịch thanh toán'),
    ('PAYMENT_READ_SELF',      'Xem giao dịch thanh toán của mình'),
    -- E-Ticket
    ('ETICKET_READ_SELF',      'Xem vé điện tử của mình'),
    -- Dashboard / Notification
    ('DASHBOARD_READ_OPERATIONAL', 'Xem dashboard vận hành (doanh thu, chuyến bay)'),
    ('DASHBOARD_READ',             'Xem dashboard toàn hệ thống'),
    ('NOTIFICATION_READ_SELF',     'Xem thông báo của mình');

-- -----------------------------------------------------------------
-- role_permissions — map theo cột ✅ của Permission Matrix v2.0
-- CUSTOMER: auth cá nhân + đọc dữ liệu public + tự phục vụ (booking/payment)
-- -----------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.permission_key IN (
    -- Auth & account cá nhân
    'LOGIN', 'LOGOUT', 'PASSWORD_CHANGE_SELF', 'PROFILE_READ_SELF', 'PROFILE_UPDATE_SELF',
    -- Đọc dữ liệu public
    'AIRLINE_READ', 'AIRLINE_READ_DETAIL',
    'CITY_READ', 'CITY_READ_DETAIL',
    'AIRPORT_READ', 'AIRPORT_READ_DETAIL',
    'PROMOTION_READ', 'PROMOTION_READ_DETAIL',
    'VOUCHER_READ', 'VOUCHER_READ_DETAIL',
    'NEWS_CATEGORY_READ', 'NEWS_CATEGORY_READ_DETAIL',
    'NEWS_READ', 'NEWS_READ_DETAIL',
    'FLIGHT_SEARCH', 'FLIGHT_READ', 'FLIGHT_DETAIL_READ',
    'FARE_CLASS_READ', 'FARE_CLASS_READ_DETAIL',
    'BAGGAGE_READ', 'BAGGAGE_READ_DETAIL',
    'FLIGHT_FARE_READ', 'FLIGHT_FARE_READ_DETAIL',
    'SEAT_INVENTORY_READ', 'SEAT_INVENTORY_READ_DETAIL',
    -- Tự phục vụ (SELF)
    'VOUCHER_APPLY',
    'PASSENGER_CREATE', 'PASSENGER_READ_SELF', 'PASSENGER_UPDATE_SELF',
    'BOOKING_CREATE', 'BOOKING_READ_SELF', 'BOOKING_CANCEL_SELF',
    'PAYMENT_CREATE', 'PAYMENT_READ_SELF',
    'ETICKET_READ_SELF',
    'NOTIFICATION_READ_SELF'
)
WHERE r.name = 'CUSTOMER';

-- -----------------------------------------------------------------
-- STAFF: quyền customer + quản lý flight/fare/baggage/seat + xem booking + dashboard vận hành.
-- KHÔNG quản lý User/RBAC (Permission Matrix: User Management chỉ ADMIN).
-- -----------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.permission_key IN (
    -- Auth & account cá nhân
    'LOGIN', 'LOGOUT', 'PASSWORD_CHANGE_SELF', 'PROFILE_READ_SELF', 'PROFILE_UPDATE_SELF',
    -- Đọc dữ liệu public
    'AIRLINE_READ', 'AIRLINE_READ_DETAIL',
    'CITY_READ', 'CITY_READ_DETAIL',
    'AIRPORT_READ', 'AIRPORT_READ_DETAIL',
    'AIRCRAFT_READ', 'AIRCRAFT_READ_DETAIL',
    'SEAT_CONFIG_READ', 'SEAT_CONFIG_READ_DETAIL',
    'PROMOTION_READ', 'PROMOTION_READ_DETAIL',
    'VOUCHER_READ', 'VOUCHER_READ_DETAIL',
    'NEWS_CATEGORY_READ', 'NEWS_CATEGORY_READ_DETAIL',
    'NEWS_READ', 'NEWS_READ_DETAIL',
    'FLIGHT_SEARCH', 'FLIGHT_READ', 'FLIGHT_DETAIL_READ',
    'FARE_CLASS_READ', 'FARE_CLASS_READ_DETAIL',
    'BAGGAGE_READ', 'BAGGAGE_READ_DETAIL',
    'FLIGHT_FARE_READ', 'FLIGHT_FARE_READ_DETAIL',
    -- Nghiệp vụ vận hành (quản lý flight/fare/baggage/seat)
    'FLIGHT_CREATE', 'FLIGHT_UPDATE', 'FLIGHT_ACTIVATE', 'FLIGHT_DEACTIVATE',
    'FARE_CLASS_CREATE', 'FARE_CLASS_UPDATE', 'FARE_CLASS_DELETE',
    'FARE_RULE_READ', 'FARE_RULE_READ_DETAIL', 'FARE_RULE_CREATE', 'FARE_RULE_UPDATE', 'FARE_RULE_DELETE',
    'BAGGAGE_CREATE', 'BAGGAGE_UPDATE', 'BAGGAGE_DELETE',
    'FLIGHT_FARE_CREATE', 'FLIGHT_FARE_UPDATE', 'FLIGHT_FARE_DELETE',
    'SEAT_INVENTORY_READ', 'SEAT_INVENTORY_READ_DETAIL', 'SEAT_INVENTORY_MANAGE',
    'BOOKING_READ',
    'PASSENGER_CREATE', 'PASSENGER_READ_SELF', 'PASSENGER_UPDATE_SELF',
    'DASHBOARD_READ_OPERATIONAL',
    'NOTIFICATION_READ_SELF'
)
WHERE r.name = 'STAFF';

-- -----------------------------------------------------------------
-- ADMIN: toàn quyền. Loại REGISTER + PASSWORD_RESET (public GUEST-only,
-- permitAll ở Security — không phải quyền của role).
-- -----------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.permission_key NOT IN ('REGISTER', 'PASSWORD_RESET')
WHERE r.name = 'ADMIN';