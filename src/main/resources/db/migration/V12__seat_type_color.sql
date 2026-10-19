-- =====================================================================
-- V12__seat_type_color.sql
-- DB v0.5 mục 1 — màu hiển thị cho loại ghế.
--
-- Bối cảnh: một seat_class có thể có nhiều seat_type (5 loại ECONOMY trong
-- seed). Màn cấu hình sơ đồ ghế tô màu theo seat_class nên không phân biệt
-- được "Phổ thông" với "Phổ thông ưu tiên" hay "Ghế thoát hiểm".
--
-- NULL = chưa đặt màu -> FE lùi về màu mặc định theo seat_class, nên
-- migration không phá dữ liệu seed hiện có.
-- =====================================================================

ALTER TABLE seat_types ADD COLUMN color_hex VARCHAR(7) NULL;

-- Chốt cuối chặn giá trị rác (FE đã giới hạn ở form nhưng DB mới là ràng buộc thật)
ALTER TABLE seat_types
    ADD CONSTRAINT chk_seat_types_color_hex
    CHECK (color_hex IS NULL OR color_hex ~ '^#[0-9A-Fa-f]{6}$');
