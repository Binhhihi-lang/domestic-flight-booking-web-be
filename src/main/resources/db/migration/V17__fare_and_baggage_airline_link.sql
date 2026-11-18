-- =====================================================================
-- V17__fare_and_baggage_airline_link.sql
--
-- Hạng vé và gói hành lý thuộc ĐÚNG MỘT hãng hàng không.
--
-- Lý do: hạng vé là sản phẩm giá + điều kiện hoàn của một hãng cụ thể —
-- hạng vé của Vietnam Airlines không dùng được cho chuyến VietJet Air.
-- Trước V17 hai bảng này không có cột hãng nên màn quản trị không thể
-- hiện cột "Hãng áp dụng" và bộ lọc theo hãng.
--
-- Quan hệ một-nhiều: fare_classes.airline_id → airlines.id, KHÔNG cần
-- bảng nối, vì một hạng vé chỉ thuộc một hãng.
--
-- Kèm theo: mã hạng vé chỉ cần duy nhất TRONG một hãng, nên unique index
-- chuyển từ (fare_class_code) sang (airline_id, fare_class_code).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. fare_classes: thêm hãng áp dụng
-- ---------------------------------------------------------------------
ALTER TABLE fare_classes ADD COLUMN airline_id BIGINT;

-- Dữ liệu cũ (6 hạng vé chuẩn) gán về Vietnam Airlines.
UPDATE fare_classes
SET airline_id = (SELECT id FROM airlines WHERE airline_code = 'VN' AND is_deleted = false LIMIT 1)
WHERE airline_id IS NULL;

-- Phòng trường hợp môi trường không có hãng VN: lấy hãng đầu tiên còn sống.
UPDATE fare_classes
SET airline_id = (SELECT MIN(id) FROM airlines WHERE is_deleted = false)
WHERE airline_id IS NULL;

ALTER TABLE fare_classes ALTER COLUMN airline_id SET NOT NULL;
ALTER TABLE fare_classes
    ADD CONSTRAINT fk_fare_classes_airline
    FOREIGN KEY (airline_id) REFERENCES airlines (id);

CREATE INDEX idx_fare_classes_airline_id ON fare_classes (airline_id);

-- Mã hạng vé duy nhất theo từng hãng, không duy nhất toàn hệ thống.
DROP INDEX IF EXISTS uq_fare_classes_code_active;
CREATE UNIQUE INDEX uq_fare_classes_code_per_airline
    ON fare_classes (airline_id, fare_class_code)
    WHERE is_deleted = false;

-- ---------------------------------------------------------------------
-- 2. baggage_options: thêm hãng áp dụng
-- ---------------------------------------------------------------------
ALTER TABLE baggage_options ADD COLUMN airline_id BIGINT;

UPDATE baggage_options
SET airline_id = (SELECT id FROM airlines WHERE airline_code = 'VN' AND is_deleted = false LIMIT 1)
WHERE airline_id IS NULL;

UPDATE baggage_options
SET airline_id = (SELECT MIN(id) FROM airlines WHERE is_deleted = false)
WHERE airline_id IS NULL;

ALTER TABLE baggage_options ALTER COLUMN airline_id SET NOT NULL;
ALTER TABLE baggage_options
    ADD CONSTRAINT fk_baggage_options_airline
    FOREIGN KEY (airline_id) REFERENCES airlines (id);

CREATE INDEX idx_baggage_options_airline_id ON baggage_options (airline_id);

-- ---------------------------------------------------------------------
-- 3. Mỗi hãng đều có đủ bộ hạng vé chuẩn
--    Hạng vé cũ đã gán cho VN ở bước 1; bước này bù cho các hãng còn lại.
-- ---------------------------------------------------------------------
INSERT INTO fare_classes (airline_id, fare_class_code, fare_class_name, seat_class)
SELECT a.id, v.code, v.name, v.cabin
FROM airlines a
CROSS JOIN (VALUES
    ('ECON_SAVER',      'Phổ thông tiết kiệm',          'ECONOMY'),
    ('ECON_STANDARD',   'Phổ thông tiêu chuẩn',         'ECONOMY'),
    ('ECON_FLEX',       'Phổ thông linh hoạt',          'ECONOMY'),
    ('PREM_ECO_STANDARD','Phổ thông đặc biệt tiêu chuẩn','PREMIUM_ECONOMY'),
    ('PREM_ECO_FLEX',   'Phổ thông đặc biệt linh hoạt', 'PREMIUM_ECONOMY'),
    ('BIZ_STANDARD',    'Thương gia tiêu chuẩn',        'BUSINESS'),
    ('BIZ_FLEX',        'Thương gia linh hoạt',         'BUSINESS')
) AS v(code, name, cabin)
WHERE a.is_deleted = false
  AND NOT EXISTS (
        SELECT 1 FROM fare_classes fc
        WHERE fc.airline_id = a.id
          AND fc.fare_class_code = v.code
          AND fc.is_deleted = false
    );

-- Quy định hoàn vé cho hạng vé vừa thêm (hạng vé chưa có rule nào).
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
WHERE fc.is_deleted = false
  AND fc.fare_class_code IN (
        'ECON_SAVER', 'ECON_STANDARD', 'ECON_FLEX',
        'PREM_ECO_STANDARD', 'PREM_ECO_FLEX',
        'BIZ_STANDARD', 'BIZ_FLEX'
    )
  AND NOT EXISTS (
        SELECT 1 FROM fare_rules fr WHERE fr.fare_class_id = fc.id
    );

-- ---------------------------------------------------------------------
-- 4. Mỗi hãng có bộ gói hành lý ký gửi riêng
-- ---------------------------------------------------------------------
INSERT INTO baggage_options (airline_id, name, weight_kg, price)
SELECT a.id, v.name, v.weight, v.price
FROM airlines a
CROSS JOIN (VALUES
    ('Hành lý ký gửi 20 kg', 20::numeric, 250000::numeric),
    ('Hành lý ký gửi 30 kg', 30::numeric, 350000::numeric),
    ('Hành lý ký gửi 40 kg', 40::numeric, 450000::numeric)
) AS v(name, weight, price)
WHERE a.is_deleted = false
  AND NOT EXISTS (
        SELECT 1 FROM baggage_options bo
        WHERE bo.airline_id = a.id
          AND bo.name = v.name
          AND bo.is_deleted = false
    );
