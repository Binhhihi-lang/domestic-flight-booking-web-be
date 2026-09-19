-- =====================================================================
-- V3__seed_master_data.sql
-- Dữ liệu gốc nội địa Việt Nam (id cột định — ổn định giữa các môi trường).
--
-- Phạm vi: chỉ dữ liệu nền cho Phase 1 (master data). KHÔNG seed
-- aircrafts / seat_configurations / flights / bookings — thuộc nghiệp vụ
-- Sprint sau (Flight & Fare, Booking Core).
--
-- Ghi chú: v0 seat_class chỉ nhận ECONOMY/BUSINESS/FIRST, nên hạng
-- PREMIUM_ECONOMY là một class_code (seat_class = ECONOMY).
-- =====================================================================

-- Hãng hàng không nội địa -------------------------------------------------
INSERT INTO airlines (id, airline_code, airline_name, country, status) VALUES
    (1, 'VN', 'Vietnam Airlines',      'Việt Nam', 'ACTIVE'),
    (2, 'VJ', 'VietJet Air',           'Việt Nam', 'ACTIVE'),
    (3, 'QH', 'Bamboo Airways',        'Việt Nam', 'ACTIVE'),
    (4, 'BL', 'Pacific Airlines',      'Việt Nam', 'ACTIVE'),
    (5, 'VU', 'Vietravel Airlines',    'Việt Nam', 'ACTIVE');

-- Thành phố (city_code = mã IATA thành phố) -------------------------------
INSERT INTO cities (id, city_code, city_name, country) VALUES
    (1,  'HAN', 'Hà Nội',       'Việt Nam'),
    (2,  'SGN', 'TP. Hồ Chí Minh', 'Việt Nam'),
    (3,  'DAD', 'Đà Nẵng',      'Việt Nam'),
    (4,  'HPH', 'Hải Phòng',    'Việt Nam'),
    (5,  'HUI', 'Huế',          'Việt Nam'),
    (6,  'CXR', 'Nha Trang',    'Việt Nam'),
    (7,  'DLI', 'Đà Lạt',       'Việt Nam'),
    (8,  'VCA', 'Cần Thơ',      'Việt Nam'),
    (9,  'PQC', 'Phú Quốc',     'Việt Nam'),
    (10, 'BMV', 'Buôn Ma Thuột', 'Việt Nam'),
    (11, 'VII', 'Vinh',         'Việt Nam'),
    (12, 'THD', 'Thanh Hóa',    'Việt Nam');

-- Sân bay (airport_code = mã IATA sân bay) --------------------------------
INSERT INTO airports (id, airport_code, airport_name, city_id) VALUES
    (1,  'HAN', 'Sân bay Quốc tế Nội Bài',   1),
    (2,  'SGN', 'Sân bay Quốc tế Tân Sơn Nhất', 2),
    (3,  'DAD', 'Sân bay Quốc tế Đà Nẵng',   3),
    (4,  'HPH', 'Sân bay Quốc tế Cát Bi',     4),
    (5,  'HUI', 'Sân bay Quốc tế Phú Bài',    5),
    (6,  'CXR', 'Sân bay Quốc tế Cam Ranh',   6),
    (7,  'DLI', 'Sân bay Liên Khương',        7),
    (8,  'VCA', 'Sân bay Quốc tế Trà Nóc',    8),
    (9,  'PQC', 'Sân bay Quốc tế Phú Quốc',   9),
    (10, 'BMV', 'Sân bay Buôn Ma Thuột',     10),
    (11, 'VII', 'Sân bay Vinh',              11),
    (12, 'THD', 'Sân bay Thọ Xuân',          12);

-- Hạng vé ban đầu (dùng cho Flight Fare in Sprint sau) ---------------------
INSERT INTO fare_classes (id, class_code, class_name, seat_class) VALUES
    (1, 'ECONOMY',          'Hạng phổ thông',          'ECONOMY'),
    (2, 'PREMIUM_ECONOMY',  'Hạng phổ thông đặc biệt', 'ECONOMY'),
    (3, 'BUSINESS',         'Hạng thương gia',         'BUSINESS');

-- Loại ghế tương ứng (dùng cho Seat Configuration in Sprint sau) -----------
INSERT INTO seat_types (id, type_code, type_name, seat_class) VALUES
    (1, 'ECO',     'Ghế phổ thông',          'ECONOMY'),
    (2, 'PREM_ECO','Ghế phổ thông đặc biệt', 'ECONOMY'),
    (3, 'BUS',     'Ghế thương gia',         'BUSINESS');