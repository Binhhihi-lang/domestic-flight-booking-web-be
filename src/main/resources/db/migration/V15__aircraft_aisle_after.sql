-- =====================================================================
-- V15__aircraft_aisle_after.sql
-- Vị trí lối đi giữa các cột ghế của máy bay.
--
-- Vì sao cần: lối đi là đặc điểm của CÁCH BỐ TRÍ ghế (3-3, 2-3-2, 3-3-3),
-- không suy được từ số cột. Máy bay thân rộng có 2 lối đi; thân hẹp có 1.
-- Lưu danh sách cột mà SAU ĐÓ là lối đi, phân tách bằng dấu phẩy.
--
-- Ví dụ:
--   'C'     → A B C | D E F          (3-3, 1 lối đi)
--   'C,E'   → A B C | D E | F G      (3-2-2, 2 lối đi)
--   NULL    → không có lối đi (cabin nhỏ)
-- =====================================================================

ALTER TABLE aircrafts ADD COLUMN aisle_after VARCHAR(20) NULL;

-- Chỉ chấp nhận chữ cái cột viết hoa, cách nhau bằng dấu phẩy.
ALTER TABLE aircrafts
    ADD CONSTRAINT chk_aircrafts_aisle_after
    CHECK (aisle_after IS NULL OR aisle_after ~ '^[A-Z](,[A-Z])*$');
