-- =====================================================================
-- V16__cabin_fare_seat_model.sql
--
-- Chuẩn hoá mô hình 3 tầng:
--   1. Khoang (seat_class): ECONOMY | PREMIUM_ECONOMY | BUSINESS.
--      Bỏ FIRST — dự án nội địa không khai thác hạng nhất.
--   2. Hạng vé (fare_class): nhiều hạng vé trong MỘT khoang.
--   3. Loại ghế (seat_type): phụ thu gắn với đặc tính ghế,
--      KHÔNG còn phụ thu đổi khoang.
--
-- Bỏ bảng seat_class_surcharges: đổi khoang là đổi hạng vé,
-- không phải phụ thu chọn ghế.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Loại ghế: thêm phụ thu mặc định
-- ---------------------------------------------------------------------
ALTER TABLE seat_types ADD COLUMN surcharge NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE seat_types ADD CONSTRAINT chk_seat_types_surcharge CHECK (surcharge >= 0);

-- ---------------------------------------------------------------------
-- 2. Nới CHECK seat_class: thêm PREMIUM_ECONOMY, bỏ FIRST
-- ---------------------------------------------------------------------
ALTER TABLE seat_types DROP CONSTRAINT IF EXISTS seat_types_seat_class_check;
ALTER TABLE seat_types ADD CONSTRAINT seat_types_seat_class_check
    CHECK (seat_class IN ('ECONOMY', 'PREMIUM_ECONOMY', 'BUSINESS'));

ALTER TABLE fare_classes DROP CONSTRAINT IF EXISTS fare_classes_seat_class_check;
ALTER TABLE fare_classes ADD CONSTRAINT fare_classes_seat_class_check
    CHECK (seat_class IN ('ECONOMY', 'PREMIUM_ECONOMY', 'BUSINESS'));

-- ---------------------------------------------------------------------
-- 3. Chuẩn hoá loại ghế seed cũ + thêm loại ghế Phổ thông
--    Tên gọi theo đúng tên ghế trên máy bay, không lặp tên khoang.
--    PREMIUM_ECONOMY và BUSINESS chỉ có một loại ghế chuẩn, phụ thu 0.
-- ---------------------------------------------------------------------
UPDATE seat_types
SET seat_type_name = 'Ghế tiêu chuẩn',
    seat_class     = 'ECONOMY',
    surcharge      = 0
WHERE seat_type_code = 'ECO';

UPDATE seat_types
SET seat_type_name = 'Ghế ngả rộng',
    seat_class     = 'PREMIUM_ECONOMY',
    surcharge      = 0
WHERE seat_type_code = 'PREM_ECO';

UPDATE seat_types
SET seat_type_name = 'Ghế thương gia',
    seat_class     = 'BUSINESS',
    surcharge      = 0
WHERE seat_type_code = 'BUS';

-- Phổ thông có nhiều loại ghế; đây là nơi phát sinh phụ thu chọn ghế.
-- Dùng NOT EXISTS thay ON CONFLICT vì unique index là partial (WHERE is_deleted = false).
INSERT INTO seat_types (seat_type_code, seat_type_name, seat_class, color_hex, surcharge)
SELECT v.code, v.name, v.cabin, v.color, v.surcharge
FROM (VALUES
    ('ECO_WIN',  'Ghế cửa sổ',     'ECONOMY', '#64B5F6',      0::numeric),
    ('ECO_AIS',  'Ghế lối đi',     'ECONOMY', '#90CAF9',      0::numeric),
    ('ECO_FRONT','Ghế hàng đầu',   'ECONOMY', '#42A5F5',  70000::numeric),
    ('ECO_EXIT', 'Ghế thoát hiểm', 'ECONOMY', '#1E88E5', 120000::numeric)
) AS v(code, name, cabin, color, surcharge)
WHERE NOT EXISTS (
    SELECT 1 FROM seat_types st
    WHERE st.seat_type_code = v.code AND st.is_deleted = false
);

-- ---------------------------------------------------------------------
-- 4. Hạng vé: đổi 3 dòng cũ thành hạng chuẩn, thêm hạng vé con
-- ---------------------------------------------------------------------
UPDATE fare_classes
SET fare_class_code = 'ECON_STANDARD',
    fare_class_name = 'Phổ thông tiêu chuẩn',
    seat_class      = 'ECONOMY'
WHERE fare_class_code = 'ECONOMY';

UPDATE fare_classes
SET fare_class_code = 'PREM_ECO_STANDARD',
    fare_class_name = 'Phổ thông đặc biệt tiêu chuẩn',
    seat_class      = 'PREMIUM_ECONOMY'
WHERE fare_class_code = 'PREMIUM_ECONOMY';

UPDATE fare_classes
SET fare_class_code = 'BIZ_STANDARD',
    fare_class_name = 'Thương gia tiêu chuẩn',
    seat_class      = 'BUSINESS'
WHERE fare_class_code = 'BUSINESS';

INSERT INTO fare_classes (fare_class_code, fare_class_name, seat_class)
SELECT v.code, v.name, v.cabin
FROM (VALUES
    ('ECON_SAVER',    'Phổ thông tiết kiệm',          'ECONOMY'),
    ('ECON_FLEX',     'Phổ thông linh hoạt',          'ECONOMY'),
    ('PREM_ECO_FLEX', 'Phổ thông đặc biệt linh hoạt', 'PREMIUM_ECONOMY'),
    ('BIZ_FLEX',      'Thương gia linh hoạt',         'BUSINESS')
) AS v(code, name, cabin)
WHERE NOT EXISTS (
    SELECT 1 FROM fare_classes fc
    WHERE fc.fare_class_code = v.code AND fc.is_deleted = false
);

-- Quy định hoàn vé cho các hạng vé mới (chỉ thêm khi chưa có rule).
INSERT INTO fare_rules
    (fare_class_id, version, is_refundable, refund_min_hours_before_departure,
     refund_percent, description, effective_from)
SELECT
    fc.id,
    1,
    CASE fc.fare_class_code WHEN 'ECON_SAVER' THEN false ELSE true END,
    CASE fc.fare_class_code WHEN 'ECON_SAVER' THEN 0 ELSE 24 END,
    CASE fc.fare_class_code
        WHEN 'ECON_SAVER'        THEN 0
        WHEN 'ECON_FLEX'         THEN 90
        WHEN 'PREM_ECO_STANDARD' THEN 85
        WHEN 'PREM_ECO_FLEX'     THEN 95
        WHEN 'BIZ_FLEX'          THEN 100
        ELSE 80
    END,
    'Chính sách hoàn vé mặc định',
    now()
FROM fare_classes fc
WHERE fc.fare_class_code IN (
        'ECON_SAVER', 'ECON_FLEX',
        'PREM_ECO_STANDARD', 'PREM_ECO_FLEX',
        'BIZ_STANDARD', 'BIZ_FLEX'
    )
  AND NOT EXISTS (
        SELECT 1 FROM fare_rules fr WHERE fr.fare_class_id = fc.id
    );

-- ---------------------------------------------------------------------
-- 5. Bỏ mô hình phụ thu đổi khoang
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS seat_class_surcharges;
