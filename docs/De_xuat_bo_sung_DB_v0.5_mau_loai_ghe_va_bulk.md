# Đề xuất bổ sung DB v0.5 (tiếp) — màu loại ghế & thao tác hàng loạt

**Ngày:** 13/10/2026
**Người đề xuất:** (FE)
**Lý do:** Hai yêu cầu nghiệp vụ mới cần BE bổ sung schema/endpoint trước khi FE nối API thật.
**Liên quan:** file `De_xuat_bo_sung_DB_v0.5_truong_anh.md` (đề xuất trường ảnh cho News/City/Promotion).

---

## 1. Màu cho loại ghế (`seat_types`)

### 1.1 Vấn đề

Màn **Cấu hình sơ đồ ghế** vẽ lưới ghế và tô màu ô theo hạng ghế. Hiện FE **hard-code** màu theo `seat_class`:

| `seat_class` | Màu FE đang dùng |
|---|---|
| `ECONOMY` | `--color-primary-bright` (xanh) |
| `BUSINESS` | `--color-accent` (cam) |
| `FIRST` | `--color-danger` (đỏ) |

Vấn đề: DB v0.4 cho phép **nhiều `seat_type` cùng một `seat_class`** — dữ liệu seed hiện có 5 loại ECONOMY (`ECO`, `ECO_WIN`, `ECO_AIS`, `PREM_ECO`, `EXIT`). Tô cùng một màu thì lưới ghế không phân biệt được "Phổ thông" với "Phổ thông ưu tiên" hay "Ghế thoát hiểm" — trong khi đó lại đúng là thứ ADMIN cần nhìn thấy khi kiểm tra sơ đồ.

ADMIN cũng không có cách nào tự đặt màu cho loại ghế mới.

### 1.2 Đề xuất migration

```sql
-- V5__add_image_columns.sql (nối tiếp file đề xuất trước)
ALTER TABLE seat_types
    ADD COLUMN color_hex VARCHAR(7) NULL;
```

**Kiểu dữ liệu `VARCHAR(7)`:** đủ cho mã màu hex đầy đủ dạng `#RRGGBB` (7 ký tự). Không dùng tên màu (`red`, `blue`) vì phụ thuộc CSS và không kiểm soát được sắc độ.

**Nullable:** `NULL` = chưa đặt màu → FE lùi về màu mặc định theo `seat_class` như hiện nay. Nhờ vậy migration không phá dữ liệu seed đang có.

**Ràng buộc đề xuất (nếu Bình thấy hợp lý):**

```sql
CHECK (color_hex IS NULL OR color_hex ~ '^#[0-9A-Fa-f]{6}$')
```

Chặn ADMIN dán giá trị rác vào cột màu. FE đã giới hạn ở form nhưng ràng buộc ở DB mới là chốt cuối.

**Không cần index:** cột chỉ để hiển thị.

### 1.3 Ảnh hưởng tới API Design v0.2

Thêm `color_hex` vào `seat_types` (mục 2.6):

| Endpoint | Thay đổi |
|---|---|
| `GET /api/v1/seat-types`, `GET /api/v1/seat-types/{id}` | trả thêm `color_hex` (string \| null) |
| `POST`/`PUT /api/v1/seat-types` | nhận `color_hex` trong payload |

```json
{
  "seat_type_code": "ECO_WIN",
  "seat_type_name": "Phổ thông - cửa sổ",
  "seat_class": "ECONOMY",
  "color_hex": "#0194F3",
  "is_active": true
}
```

### 1.4 Trạng thái phía FE

- Form CRUD loại ghế sẽ thêm ô nhập màu (bộ chọn màu + ô nhập hex).
- Bảng loại ghế thêm cột hiển thị màu.
- Lưới sơ đồ ghế đổi từ đọc `seat_class` sang đọc `color_hex`, lùi về màu theo hạng khi `null`.

---

## 2. Thao tác hàng loạt trên bảng danh sách (bulk action)

### 2.1 Vấn đề

Các màn danh sách hiện chỉ thao tác được **một bản ghi một lần** (nút Sửa / Xóa trên từng hàng). Với danh mục có hàng chục bản ghi, người vận hành phải bấm lặp — ví dụ ngừng dùng 15 hãng hàng không thì phải bấm 15 lần và xác nhận 15 lần.

API v0.2 **chưa có endpoint nào cho thao tác hàng loạt**.

### 2.2 Đề xuất endpoint

Ba hành động cần hỗ trợ: **xóa mềm**, **ngừng kích hoạt** (`is_active = false`), **kích hoạt** (`is_active = true`).

Đề xuất dùng **một endpoint cho mỗi hành động**, nhận danh sách id:

| Method | Path | Quyền | Việc |
|---|---|---|---|
| `PATCH` | `/api/v1/{resource}/bulk` | như `*_UPDATE` / `*_DELETE` của module | đổi `is_active` hàng loạt |
| `DELETE` | `/api/v1/{resource}/bulk` | như `*_DELETE` của module | xóa mềm hàng loạt |

Ví dụ với airlines:

```
PATCH  /api/v1/airlines/bulk   { "ids": [1, 2, 3], "is_active": false }
DELETE /api/v1/airlines/bulk   { "ids": [1, 2, 3] }
```

**Vì sao `PATCH` cho việc bật/tắt thay vì `PUT`:** chỉ đổi MỘT trường (`is_active`) trên nhiều bản ghi, không thay thế toàn bộ tài nguyên — đúng ngữ nghĩa PATCH.

**Vì sao `DELETE` có body:** HTTP cho phép body trong DELETE (RFC 9110), và danh sách id có thể dài. Phương án thay thế là `POST /{resource}/bulk-delete` nếu Bình muốn tránh body trong DELETE — cả hai đều chấp nhận được, FE chỉ cần biết chốt cái nào.

**Áp cho module nào:** mọi module CRUD đang có ở Phase 1 — `airlines`, `cities`, `airports`, `aircrafts`, `seat-types`, `promotions`, `vouchers`, `news`, `news-categories`, `users`.

### 2.3 Response đề xuất

Trả về **kết quả từng phần** thay vì all-or-nothing, vì ràng buộc nghiệp vụ có thể chặn một vài bản ghi mà không chặn phần còn lại:

```json
{
  "code": 1000,
  "message": "Đã cập nhật 13/15 bản ghi",
  "data": {
    "succeeded": [1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14],
    "failed": [
      { "id": 4,  "code": 1006, "message": "Hãng còn chuyến bay đang hoạt động" },
      { "id": 15, "code": 1006, "message": "Không thể ngừng hoạt động ADMIN cuối cùng" }
    ]
  }
}
```

**Vì sao không all-or-nothing:** ràng buộc tham chiếu của từng module (airline còn flight → `1006`; seat_type đang được `aircraft_seats` dùng → `1006`; ADMIN cuối cùng → `1006`) khiến việc chọn 15 bản ghi rồi bị chặn cả 15 vì 1 bản ghi là trải nghiệm tệ. Trả kết quả từng phần để FE hiện được "13 thành công, 2 bị chặn" và nói rõ lý do.

Nếu Bình thấy all-or-nothing dễ làm hơn (một transaction, rollback cả lô) thì cũng được — FE chỉ cần biết để hiện thông báo cho đúng.

### 2.4 Ràng buộc cần giữ

- Vẫn **xóa mềm** (`is_deleted = true`), không xóa cứng — như DELETE đơn lẻ.
- Vẫn kiểm tra ràng buộc tham chiếu **từng bản ghi** trước khi đổi.
- Giới hạn số id mỗi lần gọi (đề xuất **100**) để tránh một request khổng lồ.
- Id không tồn tại → đưa vào `failed` với `1004`, không làm hỏng cả lô.

### 2.5 Trạng thái phía FE

- Bảng danh sách thêm **cột checkbox** ở đầu: chọn từng hàng + chọn tất cả (chỉ trong trang hiện tại, không chọn xuyên trang).
- Khi có ≥1 hàng được chọn, hiện **thanh hành động** ở đầu bảng: "Đã chọn N" + nút **Kích hoạt** / **Ngừng** / **Xóa**.
- Xóa hàng loạt **phải qua hộp thoại xác nhận** nêu rõ số lượng (hành động phá hủy).
- Sau khi xong, hiện kết quả từng phần nếu có bản ghi bị chặn.

---

## 3. Việc cần Be xác nhận

1. **`seat_types.color_hex VARCHAR(7) NULL`** — đồng ý thêm qua migration `V5`? Có thêm CHECK regex hex không?
2. **Endpoint bulk** — chốt dùng `PATCH /{resource}/bulk` + `DELETE /{resource}/bulk` (body chứa `ids`), hay `POST /{resource}/bulk-delete` cho xóa?
3. **Kết quả bulk** — trả **từng phần** (`succeeded` + `failed`) hay **all-or-nothing** (rollback cả lô nếu một bản ghi vi phạm)?
4. **Giới hạn số id** mỗi lần gọi — 100 có hợp lý không?
5. **Bulk áp cho module nào** — danh sách 10 module ở mục 2.2 có đúng không, hay chỉ một số?
