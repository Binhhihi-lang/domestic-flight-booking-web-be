-- =====================================================================
-- V5__flights_and_fares.sql
-- Nâng v0 → v0.4 — phần C (chuyến bay & giá vé).
--   * flights: tách 2 trạng thái operation_status / sales_status,
--     thêm parent_flight_id (chuyến bổ sung), soft-delete.
--   * flight_seats: đổi tên cột, UNAVAILABLE → BLOCKED, thêm version.
--   * flight_fares: chỉ lưu giá NGƯỜI LỚN (base_fare + tax_fee) theo
--     khoảng hiệu lực; bỏ cột status.
-- =====================================================================

-- ========== flights ==========

-- status → operation_status với bộ giá trị vận hành mới
ALTER TABLE flights ADD COLUMN operation_status VARCHAR(15) NOT NULL DEFAULT 'DRAFT';
UPDATE flights SET operation_status = CASE status
    WHEN 'SCHEDULED' THEN 'SCHEDULED'
    WHEN 'DELAYED'   THEN 'DELAYED'
    WHEN 'CANCELLED' THEN 'CANCELLED'
    WHEN 'COMPLETED' THEN 'ARRIVED'
    ELSE 'DRAFT'
END;
ALTER TABLE flights DROP CONSTRAINT flights_status_check;
ALTER TABLE flights DROP COLUMN status;
ALTER TABLE flights ADD CONSTRAINT flights_operation_status_check
    CHECK (operation_status IN ('DRAFT', 'SCHEDULED', 'BOARDING', 'DELAYED', 'DEPARTED', 'ARRIVED', 'CANCELLED'));

-- sales_status mới (mở/đóng bán vé); thay cho cột sale_open
ALTER TABLE flights ADD COLUMN sales_status VARCHAR(10) NOT NULL DEFAULT 'CLOSED';
UPDATE flights SET sales_status = CASE WHEN sale_open THEN 'OPEN' ELSE 'CLOSED' END;
ALTER TABLE flights DROP COLUMN sale_open;
ALTER TABLE flights ADD CONSTRAINT flights_sales_status_check
    CHECK (sales_status IN ('OPEN', 'CLOSED'));

-- chuyến bổ sung trỏ về chuyến gốc
ALTER TABLE flights ADD COLUMN parent_flight_id BIGINT REFERENCES flights (id);
CREATE INDEX idx_flights_parent_flight_id ON flights (parent_flight_id);

-- xóa mềm + dấu thời gian
ALTER TABLE flights ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE flights ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE flights ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE flights ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- không trùng số hiệu + giờ khởi hành cho bản ghi chưa xóa mềm
CREATE UNIQUE INDEX uq_flights_number_departure_active
    ON flights (flight_number, scheduled_departure) WHERE is_deleted = false;

-- ========== flight_seats ==========

ALTER TABLE flight_seats RENAME COLUMN seat_config_id TO aircraft_seat_id;
ALTER TABLE flight_seats RENAME COLUMN price_at_time TO seat_surcharge;
ALTER TABLE flight_seats ALTER COLUMN seat_surcharge SET DEFAULT 0;
UPDATE flight_seats SET seat_surcharge = 0 WHERE seat_surcharge IS NULL;
ALTER TABLE flight_seats ALTER COLUMN seat_surcharge SET NOT NULL;
ALTER TABLE flight_seats DROP CONSTRAINT flight_seats_price_at_time_check;
ALTER TABLE flight_seats ADD CONSTRAINT flight_seats_seat_surcharge_check CHECK (seat_surcharge >= 0);

-- UNAVAILABLE → BLOCKED
ALTER TABLE flight_seats DROP CONSTRAINT flight_seats_status_check;
UPDATE flight_seats SET status = 'BLOCKED' WHERE status = 'UNAVAILABLE';
ALTER TABLE flight_seats ADD CONSTRAINT flight_seats_status_check
    CHECK (status IN ('AVAILABLE', 'HOLD', 'BOOKED', 'BLOCKED'));

-- khóa lạc quan chống 2 người cùng chiếm 1 ghế
ALTER TABLE flight_seats ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- mỗi ghế gốc của máy bay chỉ sinh 1 lần cho một chuyến
ALTER TABLE flight_seats ADD CONSTRAINT uq_flight_seats_aircraft_seat
    UNIQUE (flight_id, aircraft_seat_id);

-- ========== flight_fares ==========
-- v0: price + effective_date + status. v0.4: base_fare + tax_fee + khoảng hiệu lực.

ALTER TABLE flight_fares RENAME COLUMN price TO base_fare;
ALTER TABLE flight_fares RENAME COLUMN effective_date TO effective_from;
ALTER TABLE flight_fares ADD COLUMN tax_fee NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE flight_fares ADD CONSTRAINT flight_fares_tax_fee_check CHECK (tax_fee >= 0);
ALTER TABLE flight_fares ADD COLUMN effective_to TIMESTAMPTZ;
ALTER TABLE flight_fares ADD CONSTRAINT flight_fares_effective_range_check
    CHECK (effective_to IS NULL OR effective_to > effective_from);

-- bỏ status: v0.4 xác định giá hiệu lực bằng khoảng effective_from/effective_to
ALTER TABLE flight_fares DROP CONSTRAINT flight_fares_status_check;
ALTER TABLE flight_fares DROP COLUMN status;
ALTER TABLE flight_fares DROP CONSTRAINT flight_fares_flight_id_fare_class_id_effective_date_key;
ALTER TABLE flight_fares ALTER COLUMN effective_from DROP DEFAULT;
ALTER TABLE flight_fares ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- không cho 2 dòng giá chồng thời gian cùng (flight_id, fare_class_id).
-- Cần extension btree_gist (đã bật trên Aiven và Postgres local).
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE flight_fares ADD CONSTRAINT ex_flight_fares_no_overlap
    EXCLUDE USING gist (
        flight_id WITH =,
        fare_class_id WITH =,
        tstzrange(effective_from, COALESCE(effective_to, 'infinity'::timestamptz)) WITH &&
    );
