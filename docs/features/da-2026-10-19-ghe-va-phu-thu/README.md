# Nhánh `da` — 2026-10-19 · Ghế & phụ thu nâng hạng

Thư mục tài liệu cho cụm thay đổi về ghế ngồi: phụ thu nâng hạng, màu loại ghế, lỗi giao diện lưới ghế.

## Tài liệu

| File | Nội dung |
|---|---|
| [design.md](design.md) | Thiết kế: vì sao, mục tiêu, schema, API, quyết định đã chốt |
| [plan.md](plan.md) | Kế hoạch thi hành 10 task, chia 3 cụm A/B/C |
| `api-seat-class-surcharges.md` | Hợp đồng API chi tiết — *gộp trong plan.md Task 4, không tách file* |

## Trạng thái

**Nhánh:** `feature/ghe-va-phu-thu` (cả BE và FE)

| Cụm | Nội dung | Task | Trạng thái |
|---|---|---|---|
| — | Migration V14 + entity/DTO/mapper/repo | 1–2 | ✅ Xong |
| C | Service + controller + test BE | 3–5 | ✅ Xong — 7 test mới |
| C | Model + service + mock FE | 6 | ✅ Xong |
| A | Sửa lỗi giao diện lưới ghế | 7 | ✅ Xong |
| B | Color picker + cột màu trong CRUD | 8 | ✅ Xong |
| C | Màn CRUD phụ thu + sidebar | 9 | ✅ Xong |
| — | Kiểm chứng toàn cục + tài liệu | 10 | ✅ Xong |

### Bổ sung sau khi xem giao diện thật (20/10)

| Việc | Chi tiết |
|---|---|
| **Sơ đồ cabin** | Thêm mũi + đuôi máy bay (SVG), nền khoang, ghế bo góc |
| **Bỏ chữ trong ô ghế** | 40px không đủ cho "ECO_WIN"/"FIRST" — loại ghế đọc qua legend + tooltip + aria-label |
| **Lối đi cấu hình được** | Migration **V15** thêm `aircrafts.aisle_after VARCHAR(20)` — "C" (3-3), "C,E" (3-2-2) |
| **Căn thẳng cột** | Sửa `padding` của `<td>` + bề rộng `.grid__corner` — trước đó lệch 24px |
| **Legend** | Thêm mã loại ghế, sửa khoảng cách bị dính chữ |
| **Nút "Bỏ màu"** | Tăng gap 8→12/16px, thêm padding |

**Kiểm chứng:** `ng build` ✅ · `ng test` **126/126** ✅ · `mvnw test` **64/64** ✅ · không còn `/api/v1` · controller mới có 8 `@PreAuthorize` · đo thực tế: header và ô ghế lệch **0px**, 30 hàng có đúng 1 lối đi mỗi hàng.

## Quyết định đã chốt

- **Phương án 3 + Cách A**: bảng cấu hình làm mặc định, `flight_seats.seat_surcharge` ghi đè; tra bảng lúc tạo inventory.
- **Phạm vi đợt này**: chỉ bảng cấu hình + CRUD + FE admin. Không làm Flight CRUD / Seat Inventory / API chọn ghế (thuộc Phase 2–3).
- Khách BUSINESS chọn ghế ECONOMY: **cho phép, phụ thu 0**.
- **Không thêm** `fare_class` FIRST.
- Phụ thu cho CHILD = **100%**.

## Tài liệu căn cứ (repo BE)

- `docs/Database_Design_Domestic_Flight_Booking_v0.5.md` — mục 5.3 (tính giá), 5.4, giả định #7 và #18
- `docs/API_Design_Website_Dat_Ve_May_Bay_v0.4.md` — mục 2.6 (Aircraft & Seat Map)
- `docs/Permission_Matrix_v2.1.md` — mục 5.4 (Aircraft & Seat Configuration)

## Ghi chú cho Phase 2

`flight_seats.seat_surcharge` là một con số **vật lý** trên ghế, nhưng một chuyến có nhiều `fare_class`. Phase 2 cần quyết định: lấy phụ thu theo `fare_class` thấp nhất, hay tính phụ thu lúc khách chọn ghế bằng cách tra bảng cấu hình. Xem [design.md §5.3](design.md).
