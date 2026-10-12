-- =====================================================================
-- V8__booking_core.sql
-- Nâng v0 → v0.4 — phần D (đặt vé, thanh toán, vé điện tử):
--   * booking_flights → booking_segments (chặng bay của booking).
--   * bookings: bảng giá chi tiết + cột hủy/hoàn + payment_expires_at.
--   * seat_holds: gắn hành khách, phụ thu lúc giữ, hạn giữ ghế.
--   * payments / e_tickets: đổi tên cột, thêm ràng buộc.
-- =====================================================================

-- ========== 1. booking_flights → booking_segments ==========

ALTER TABLE booking_flights RENAME TO booking_segments;
ALTER TABLE booking_segments RENAME COLUMN price_snapshot TO fare_amount_at_booking;
ALTER TABLE booking_segments
    RENAME CONSTRAINT booking_flights_booking_id_segment_order_key TO uq_booking_segments_order;
ALTER INDEX booking_flights_pkey RENAME TO booking_segments_pkey;
ALTER INDEX idx_booking_flights_flight_id RENAME TO idx_booking_segments_flight_id;

-- version quy định vé áp dụng lúc đặt (dùng tính hoàn tiền)
ALTER TABLE booking_segments ADD COLUMN fare_rule_id BIGINT REFERENCES fare_rules (id);
UPDATE booking_segments bs
SET fare_rule_id = (
    SELECT fr.id FROM fare_rules fr
    WHERE fr.fare_class_id = bs.fare_class_id
    ORDER BY fr.version DESC LIMIT 1
);
ALTER TABLE booking_segments ALTER COLUMN fare_rule_id SET NOT NULL;

-- khuyến mãi đã áp cho chặng
ALTER TABLE booking_segments ADD COLUMN promotion_id BIGINT REFERENCES promotions (id);
ALTER TABLE booking_segments ADD COLUMN promotion_discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE booking_segments ADD CONSTRAINT booking_segments_promo_discount_check
    CHECK (promotion_discount_amount >= 0);

-- một chuyến chỉ xuất hiện một lần trong một booking
ALTER TABLE booking_segments ADD CONSTRAINT uq_booking_segments_flight UNIQUE (booking_id, flight_id);

-- ========== 2. bookings ==========

ALTER TABLE bookings RENAME COLUMN snapshot_currency TO currency;
-- v0 chưa có cột hạn thanh toán; v0.4 thêm payment_expires_at (= lúc tạo + 15 phút)
ALTER TABLE bookings ADD COLUMN payment_expires_at TIMESTAMPTZ;
UPDATE bookings SET payment_expires_at = created_at + INTERVAL '15 minutes' WHERE payment_expires_at IS NULL;
ALTER TABLE bookings ALTER COLUMN payment_expires_at SET NOT NULL;

ALTER TABLE bookings ADD COLUMN fare_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD CONSTRAINT bookings_fare_amount_check CHECK (fare_amount >= 0);
ALTER TABLE bookings ADD COLUMN promotion_discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD CONSTRAINT bookings_promo_discount_check CHECK (promotion_discount_amount >= 0);
ALTER TABLE bookings ADD COLUMN voucher_discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD CONSTRAINT bookings_voucher_discount_check CHECK (voucher_discount_amount >= 0);
ALTER TABLE bookings ADD COLUMN seat_surcharge_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD CONSTRAINT bookings_seat_surcharge_check CHECK (seat_surcharge_amount >= 0);
ALTER TABLE bookings ADD COLUMN baggage_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD CONSTRAINT bookings_baggage_amount_check CHECK (baggage_amount >= 0);

-- hủy / hoàn tiền
ALTER TABLE bookings ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE bookings ADD COLUMN cancel_reason VARCHAR(255);
ALTER TABLE bookings ADD COLUMN applied_refund_percent NUMERIC(5, 2);
ALTER TABLE bookings ADD CONSTRAINT bookings_applied_refund_percent_check
    CHECK (applied_refund_percent IS NULL OR applied_refund_percent BETWEEN 0 AND 100);
ALTER TABLE bookings ADD COLUMN refund_amount NUMERIC(12, 2);
ALTER TABLE bookings ADD CONSTRAINT bookings_refund_amount_check
    CHECK (refund_amount IS NULL OR (refund_amount >= 0 AND refund_amount <= total_amount));
ALTER TABLE bookings ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- tổng tiền phải khớp bảng giá chi tiết
ALTER TABLE bookings ADD CONSTRAINT bookings_total_amount_check_formula
    CHECK (total_amount = fare_amount - promotion_discount_amount - voucher_discount_amount
                         + seat_surcharge_amount + baggage_amount);

-- ========== 3. seat_holds ==========

ALTER TABLE seat_holds RENAME COLUMN held_until TO hold_expires_at;
ALTER TABLE seat_holds ADD COLUMN passenger_id BIGINT REFERENCES passengers (id);
-- (V1–V3 không có booking/seat_hold nào nên không cần backfill)
ALTER TABLE seat_holds ALTER COLUMN passenger_id SET NOT NULL;
ALTER TABLE seat_holds ADD COLUMN surcharge_at_booking NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE seat_holds ADD CONSTRAINT seat_holds_surcharge_check CHECK (surcharge_at_booking >= 0);
ALTER TABLE seat_holds ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE INDEX idx_seat_holds_passenger_id ON seat_holds (passenger_id);

-- v0.4: chặn cả HOLD lẫn CONFIRMED cùng chiếm một ghế (thay index cũ chỉ HOLD)
DROP INDEX uq_seat_holds_active_per_seat;
CREATE UNIQUE INDEX uq_seat_holds_active_per_seat
    ON seat_holds (flight_seat_id) WHERE status IN ('HOLD', 'CONFIRMED');

-- ========== 4. booking_baggages ==========

ALTER TABLE booking_baggages RENAME COLUMN price_snapshot TO unit_price_at_booking;

-- ========== 5. payments ==========

ALTER TABLE payments RENAME COLUMN provider TO gateway_name;
ALTER TABLE payments RENAME COLUMN gateway_ref TO gateway_transaction_id;

-- v0.4: số tiền thanh toán phải > 0
ALTER TABLE payments DROP CONSTRAINT payments_amount_check;
ALTER TABLE payments ADD CONSTRAINT payments_amount_check CHECK (amount > 0);

-- cổng gọi callback hai lần không tạo giao dịch trùng
CREATE UNIQUE INDEX uq_payments_gateway_transaction
    ON payments (gateway_name, gateway_transaction_id)
    WHERE gateway_transaction_id IS NOT NULL;

-- ========== 6. e_tickets ==========

ALTER TABLE e_tickets RENAME COLUMN booking_flight_id TO booking_segment_id;
ALTER TABLE e_tickets
    RENAME CONSTRAINT e_tickets_booking_flight_id_fkey TO e_tickets_booking_segment_id_fkey;
ALTER INDEX idx_e_tickets_booking_flight_id RENAME TO idx_e_tickets_booking_segment_id;

-- PNR là của cả booking (lấy từ bookings.booking_code) → bỏ cột riêng
ALTER TABLE e_tickets DROP CONSTRAINT e_tickets_pnr_code_key;
ALTER TABLE e_tickets DROP COLUMN pnr_code;

-- trạng thái vé (hủy booking → CANCELLED)
ALTER TABLE e_tickets ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'ISSUED';
ALTER TABLE e_tickets ADD CONSTRAINT e_tickets_status_check CHECK (status IN ('ISSUED', 'CANCELLED'));

-- mỗi hành khách chỉ một vé trên một chặng
ALTER TABLE e_tickets ADD CONSTRAINT uq_e_tickets_segment_passenger
    UNIQUE (booking_segment_id, passenger_id);
