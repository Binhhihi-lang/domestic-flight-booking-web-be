-- =====================================================================
-- V11__reset_identity_sequences.sql
-- Sửa lỗi: các bảng được seed bằng ID cố định (V2, V3) làm sequence IDENTITY
-- KHÔNG tiến theo. Ví dụ V3 chèn airlines id 1..5 nhưng `airlines_id_seq`
-- vẫn ở mức thấp → INSERT tiếp theo của ứng dụng đụng `airlines_pkey`
-- (duplicate key) dù bảng chưa có bản ghi đó.
--
-- Cách sửa: đặt lại mỗi sequence = max(id) hiện có, để ID kế tiếp là max+1.
-- An toàn chạy lại nhiều lần (idempotent).
-- =====================================================================

-- Các bảng seed ID cố định ở V2 (roles, permissions) và V3 (airlines, cities,
-- airports, fare_classes, seat_types). Dùng setval với GREATEST để không hạ
-- sequence nếu nó đã cao hơn.
SELECT setval(pg_get_serial_sequence('roles', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 0) FROM roles), 1));
SELECT setval(pg_get_serial_sequence('permissions', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 0) FROM permissions), 1));
SELECT setval(pg_get_serial_sequence('airlines', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 0) FROM airlines), 1));
SELECT setval(pg_get_serial_sequence('cities', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 0) FROM cities), 1));
SELECT setval(pg_get_serial_sequence('airports', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 0) FROM airports), 1));
SELECT setval(pg_get_serial_sequence('fare_classes', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 0) FROM fare_classes), 1));
SELECT setval(pg_get_serial_sequence('seat_types', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 0) FROM seat_types), 1));
