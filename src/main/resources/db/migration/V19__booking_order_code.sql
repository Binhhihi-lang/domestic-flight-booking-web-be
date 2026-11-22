-- V19: Thêm mã đơn hàng chống đoán ID tuần tự (DH + timestamp mili-giây)
ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS order_code VARCHAR(30);

UPDATE bookings
SET order_code = 'DH' || (FLOOR(EXTRACT(EPOCH FROM COALESCE(created_at, NOW())) * 1000)::BIGINT + id)
WHERE order_code IS NULL;

ALTER TABLE bookings
    ALTER COLUMN order_code SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_bookings_order_code
    ON bookings (order_code);
