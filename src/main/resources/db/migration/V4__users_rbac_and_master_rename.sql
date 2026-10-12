-- =====================================================================
-- V4__users_rbac_and_master_rename.sql
-- Nâng schema v0 (V1–V3) lên Database Design v0.4 — phần A (users/RBAC)
-- và phần B (master data). Nguyên tắc: ALTER/RENAME giữ dữ liệu.
-- KHÔNG sửa V1–V3 (Flyway checksum).
--
-- Quy ước v0.4 áp dụng từ đây:
--   * is_active  = bật/tắt sử dụng (thay cho cột status ACTIVE/INACTIVE)
--   * is_deleted + deleted_at = xóa mềm
--   * mã nghiệp vụ (email, *_code, name) dùng PARTIAL UNIQUE INDEX
--     WHERE is_deleted = false → tài khoản/bản ghi đã xóa mềm không chặn tạo lại
-- =====================================================================

-- ========== A. users & RBAC ==========

-- Mỗi user đúng 1 role (v0.4 thay bảng nối user_roles bằng cột users.role_id)
ALTER TABLE users ADD COLUMN role_id BIGINT REFERENCES roles (id);
UPDATE users u
SET role_id = (SELECT ur.role_id FROM user_roles ur WHERE ur.user_id = u.id ORDER BY ur.role_id LIMIT 1);
-- user không có dòng user_roles nào → mặc định CUSTOMER
UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'CUSTOMER') WHERE role_id IS NULL;
ALTER TABLE users ALTER COLUMN role_id SET NOT NULL;
CREATE INDEX idx_users_role_id ON users (role_id);

-- status (ACTIVE/INACTIVE) → is_active boolean
ALTER TABLE users ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
UPDATE users SET is_active = (status = 'ACTIVE');
ALTER TABLE users DROP COLUMN status;

-- xóa mềm
ALTER TABLE users ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;

DROP TABLE user_roles;

-- email UNIQUE → partial unique (email của tài khoản đã xóa mềm không chặn đăng ký lại)
ALTER TABLE users DROP CONSTRAINT users_email_key;
CREATE UNIQUE INDEX uq_users_email_active ON users (email) WHERE is_deleted = false;

-- ========== B. Master data ==========

-- ---- airlines ----
ALTER TABLE airlines ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
UPDATE airlines SET is_active = (status = 'ACTIVE');
ALTER TABLE airlines DROP COLUMN status;
ALTER TABLE airlines ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE airlines ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE airlines ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE airlines ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE airlines DROP CONSTRAINT airlines_airline_code_key;
CREATE UNIQUE INDEX uq_airlines_code_active ON airlines (airline_code) WHERE is_deleted = false;

-- ---- cities ----
ALTER TABLE cities ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE cities ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE cities ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE cities ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE cities ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE cities DROP CONSTRAINT cities_city_code_key;
CREATE UNIQUE INDEX uq_cities_code_active ON cities (city_code) WHERE is_deleted = false;

-- ---- airports ----
ALTER TABLE airports ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE airports ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE airports ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE airports ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE airports ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE airports DROP CONSTRAINT airports_airport_code_key;
CREATE UNIQUE INDEX uq_airports_code_active ON airports (airport_code) WHERE is_deleted = false;

-- ---- aircrafts ----
ALTER TABLE aircrafts ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE aircrafts ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE aircrafts ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE aircrafts ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE aircrafts ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE aircrafts DROP CONSTRAINT aircrafts_aircraft_code_key;
CREATE UNIQUE INDEX uq_aircrafts_code_active ON aircrafts (aircraft_code) WHERE is_deleted = false;

-- ---- seat_types (v0.4: CRUD đầy đủ, thêm active/soft-delete; đổi tên cột) ----
ALTER TABLE seat_types RENAME COLUMN type_code TO seat_type_code;
ALTER TABLE seat_types RENAME COLUMN type_name TO seat_type_name;
ALTER TABLE seat_types ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE seat_types ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE seat_types ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE seat_types ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE seat_types ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE seat_types DROP CONSTRAINT seat_types_type_code_key;
CREATE UNIQUE INDEX uq_seat_types_code_active ON seat_types (seat_type_code) WHERE is_deleted = false;

-- ---- seat_configurations → aircraft_seats (mỗi dòng là MỘT GHẾ của máy bay) ----
ALTER TABLE seat_configurations RENAME TO aircraft_seats;
ALTER TABLE aircraft_seats RENAME COLUMN row_number TO seat_row;
ALTER TABLE aircraft_seats RENAME COLUMN column_code TO seat_column;
ALTER TABLE aircraft_seats ALTER COLUMN seat_column TYPE VARCHAR(2);
ALTER TABLE aircraft_seats
    RENAME CONSTRAINT seat_configurations_aircraft_id_row_number_column_code_key
    TO uq_aircraft_seats_position;
ALTER INDEX seat_configurations_pkey RENAME TO aircraft_seats_pkey;
ALTER TABLE aircraft_seats
    RENAME CONSTRAINT seat_configurations_aircraft_id_fkey TO aircraft_seats_aircraft_id_fkey;
ALTER TABLE aircraft_seats
    RENAME CONSTRAINT seat_configurations_seat_type_id_fkey TO aircraft_seats_seat_type_id_fkey;

-- ---- promotions ----
ALTER TABLE promotions RENAME COLUMN promo_code TO promotion_code;
ALTER TABLE promotions RENAME COLUMN promo_name TO promotion_name;
ALTER TABLE promotions ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
UPDATE promotions SET is_active = (status = 'ACTIVE');
ALTER TABLE promotions DROP COLUMN status;
ALTER TABLE promotions ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE promotions ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE promotions ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE promotions ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE promotions DROP CONSTRAINT promotions_promo_code_key;
CREATE UNIQUE INDEX uq_promotions_code_active ON promotions (promotion_code) WHERE is_deleted = false;

-- ---- vouchers ----
ALTER TABLE vouchers ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
UPDATE vouchers SET is_active = (status = 'ACTIVE');
ALTER TABLE vouchers DROP COLUMN status;
ALTER TABLE vouchers ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE vouchers ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE vouchers ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE vouchers ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE vouchers DROP CONSTRAINT vouchers_voucher_code_key;
CREATE UNIQUE INDEX uq_vouchers_code_active ON vouchers (voucher_code) WHERE is_deleted = false;

-- ---- news_categories ----
ALTER TABLE news_categories ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
UPDATE news_categories SET is_active = (status = 'ACTIVE');
ALTER TABLE news_categories DROP COLUMN status;
ALTER TABLE news_categories ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE news_categories ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE news_categories DROP CONSTRAINT news_categories_name_key;
CREATE UNIQUE INDEX uq_news_categories_name_active ON news_categories (name) WHERE is_deleted = false;

-- ---- news_items (thêm soft-delete; status nhận thêm UNPUBLISHED) ----
ALTER TABLE news_items ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE news_items ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE news_items ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE news_items ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE news_items DROP CONSTRAINT news_items_status_check;
ALTER TABLE news_items ADD CONSTRAINT news_items_status_check
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'UNPUBLISHED'));

-- ---- fare_classes ----
ALTER TABLE fare_classes RENAME COLUMN class_code TO fare_class_code;
ALTER TABLE fare_classes RENAME COLUMN class_name TO fare_class_name;
ALTER TABLE fare_classes ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE fare_classes ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE fare_classes ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE fare_classes ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE fare_classes ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE fare_classes DROP CONSTRAINT fare_classes_class_code_key;
CREATE UNIQUE INDEX uq_fare_classes_code_active ON fare_classes (fare_class_code) WHERE is_deleted = false;

-- ---- baggage_options ----
ALTER TABLE baggage_options ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
UPDATE baggage_options SET is_active = (status = 'ACTIVE');
ALTER TABLE baggage_options DROP COLUMN status;
ALTER TABLE baggage_options ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE baggage_options ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE baggage_options ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE baggage_options ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
