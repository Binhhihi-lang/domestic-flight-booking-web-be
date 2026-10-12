# Đề xuất bổ sung DB v0.4 — trường ảnh cho News, City và Promotion

**Ngày:** 12/10/2026
**Cập nhật:** 12/10/2026 — bổ sung mục 3 (Promotion)
**Người đề xuất:**(FE)
**Lý do:** Giao diện cần hiển thị ảnh/mô tả ở những chỗ mà schema hiện chưa có cột tương ứng.

---

## 1. Bối cảnh

Khi dựng giao diện, phát hiện các màn sau cần dữ liệu mà DB v0.4 chưa có cột:

| Màn | Cần gì | DB hiện có? |
|---|---|---|
| Trang chủ — khối "Tin tức & cẩm nang" | Mỗi thẻ tin cần ảnh đại diện | ❌ `news_items` không có cột ảnh |
| Trang chủ — khối "Tuyến bay phổ biến" | Mỗi tuyến cần ảnh điểm đến | ❌ `cities` không có cột ảnh |
| Trang chủ — khối "Ưu đãi hấp dẫn" | Mỗi thẻ khuyến mãi cần ảnh, dòng mô tả và nhãn | ❌ `promotions` không có cột ảnh/mô tả/nhãn |

**Đã có sẵn, không cần thêm:**
- `airlines.logo_url VARCHAR(500)` — "Link logo" ✅
- `users.avatar_url TEXT` — "Link ảnh đại diện (Cloudinary)" ✅

**Lưu ý về "tuyến bay":** DB v0.4 **không có bảng `routes`**. Tuyến bay là ghép
`cities` → `cities` (qua sân bay). Nên ảnh cho "tuyến phổ biến" phải gắn vào
**thành phố** (mỗi thành phố một ảnh đại diện), không phải vào một bảng tuyến.

---

## 2. Đề xuất migration

```sql
-- V5__add_image_columns.sql
-- Ảnh đại diện cho bài viết tin tức (hiển thị ở thẻ tin trang chủ và trang danh sách).
ALTER TABLE news_items
    ADD COLUMN thumbnail_url VARCHAR(500) NULL;

-- Ảnh đại diện cho thành phố (dùng cho khối "Tuyến bay phổ biến" ở trang chủ).
ALTER TABLE cities
    ADD COLUMN image_url VARCHAR(500) NULL;

-- Ảnh + mô tả + nhãn cho chương trình khuyến mãi (khối "Ưu đãi hấp dẫn" ở trang chủ,
-- trang danh sách khuyến mãi cho khách).
ALTER TABLE promotions
    ADD COLUMN image_url   VARCHAR(500) NULL,
    ADD COLUMN description VARCHAR(500) NULL,
    ADD COLUMN badge_label VARCHAR(30)  NULL;
```

**Kiểu dữ liệu:** `VARCHAR(500)` cho các cột ảnh — khớp đúng kiểu của
`airlines.logo_url` đã có, và đủ chỗ cho URL Cloudinary (thường < 300 ký tự).
`description` cũng `VARCHAR(500)` vì đây là dòng mô tả ngắn trên thẻ, không phải
nội dung dài. `badge_label` để `VARCHAR(30)` — chỉ chứa nhãn ngắn như "Giá sốc".

**Nullable:** tất cả đều `NULL` được. Bài viết / thành phố / khuyến mãi chưa có
ảnh vẫn hợp lệ; FE đã xử lý trường hợp này bằng nền màu thay thế (không hiện icon
ảnh vỡ của trình duyệt).

**Không cần index:** các cột này chỉ để hiển thị, không dùng để lọc/sắp xếp.

**Lưu ý về số hiệu migration:** repo BE hiện chỉ có `V1`–`V3`, và `V1__init_schema.sql`
vẫn là schema v0.1 (`promo_code`/`promo_name`/`status`), chưa phải v0.4
(`promotion_code`/`promotion_name`/`is_active`). Nên `V4` sẽ là migration nâng
schema lên v0.4, và đề xuất này là `V5`. Nếu Bình gộp chung vào `V4` thì càng tốt.

---

## 3. Vì sao Promotion cần tới 3 cột

Thẻ khuyến mãi trên trang chủ (mockup P1) có 4 thành phần thị giác:

```
┌─────────────────────────┐
│       ẢNH               │  ← image_url
│  [Giảm 20%]             │  ← badge_label
├─────────────────────────┤
│ Đà Lạt Mùa Sương Mù     │  ← promotion_name (đã có)
│ Nhập mã DALAT20 giảm... │  ← description
│ Xem chi tiết →          │
└─────────────────────────┘
```

Nếu chỉ có `promotion_name` thì FE buộc phải bịa dòng mô tả, hoặc để thẻ trống
trông thiếu sức thuyết phục — đây là trang chủ bán vé, thẻ ưu đãi mà không có
dòng mô tả thì gần như không có tác dụng marketing.

**Về `badge_label`:** phần lớn nhãn FE **suy ra được** từ dữ liệu đã có — ví dụ
`discount_type = PERCENT, discount_value = 20` → "Giảm 20%". Nhưng một số nhãn là
copy marketing thuần tuý, không suy ra được từ con số nào: "Giá sốc", "Đồng giá",
"Flash sale". Nếu không có cột này thì FE phải hardcode, và ADMIN không đổi được.

**Phương án thay thế nếu không muốn thêm `badge_label`:** FE tự sinh nhãn từ
`discount_type` + `discount_value` và bỏ hẳn nhãn marketing. Được, nhưng mất khả
năng truyền thông. Tôi đề xuất thêm cột vì chi phí chỉ là một `VARCHAR(30)`.

**Ràng buộc đề xuất (nếu Bình thấy hợp lý):** `badge_label` nên có CHECK độ dài
để tránh ADMIN dán cả câu vào nhãn — nhưng đây là chi tiết nhỏ, FE đã giới hạn
`maxlength` ở form.

---

## 4. Ảnh hưởng tới API Design v0.2

Thêm các trường vào response (không phá vỡ hợp đồng — client cũ bỏ qua trường lạ):

| Endpoint | Trường thêm vào |
|---|---|
| `GET /api/v1/news`, `GET /api/v1/news/{id}` | `thumbnail_url` (string \| null) |
| `POST`/`PUT /api/v1/news` | nhận `thumbnail_url` trong payload |
| `GET /api/v1/cities`, `GET /api/v1/cities/{id}` | `image_url` (string \| null) |
| `POST`/`PUT /api/v1/cities` | nhận `image_url` trong payload |
| `GET /api/v1/promotions`, `GET /api/v1/promotions/{id}` | `image_url`, `description`, `badge_label` (string \| null) |
| `POST`/`PUT /api/v1/promotions` | nhận 3 trường trên trong payload |

Đúng quy ước mục 1.2 API v0.2: JSON `snake_case`, trường `null` **luôn có mặt**
trong response.

**Payload `POST /promotions` sau khi bổ sung** (phần in đậm là mới):

```json
{
  "promotion_code": "HE2026",
  "promotion_name": "Khuyến mãi hè 2026",
  "image_url": "https://res.cloudinary.com/...",
  "description": "Nhập mã HE2026 giảm ngay 15% khi đặt vé khứ hồi.",
  "badge_label": "Giảm 15%",
  "discount_type": "PERCENT",
  "discount_value": 15.00,
  "max_discount_amount": 150000.00,
  "min_fare_amount": 500000.00,
  "applicable_trip_type": "ANY",
  "start_date": "2026-06-01",
  "end_date": "2026-08-31",
  "is_active": true,
  "scopes": [],
  "excluded_flight_ids": []
}
```

**Whitelist tìm kiếm:** đề xuất thêm `description` vào danh sách keyword của
`GET /promotions`? — **Không cần.** Whitelist hiện tại (`promotion_code`,
`promotion_name`) là đủ; thêm `description` chỉ làm kết quả tìm kiếm loãng.

---

## 5. Trạng thái phía FE (đã làm sẵn)

- Model `NewsItem` đã có `thumbnail_url: string | null` (kèm ghi chú chờ BE).
- Model `City` cần thêm `image_url` khi BE xác nhận.
- Model `Promotion` đã thêm `image_url`, `description`, `badge_label` (chờ BE).
- Form quản trị News đã có ô **"Ảnh đại diện"** kiểu `image`: dán URL **hoặc**
  chọn file từ máy (đọc thành data URL, xem trước ngay).
- Form quản trị Promotion đã có ô ảnh cùng kiểu, thêm ô mô tả và ô nhãn.
- Bảng và trang chi tiết đã hiển thị ảnh thu nhỏ; ảnh lỗi/chưa có thì hiện khung
  nền có icon, **không bao giờ hiện icon ảnh vỡ của trình duyệt**.
- Trang chủ đã có khối "Tin tức & cẩm nang" dùng `thumbnail_url`, và khối
  "Ưu đãi hấp dẫn" nay đọc từ `PromotionService` (lọc chương trình đang hiệu lực).

**Về upload file:** FE hiện đọc ảnh từ máy thành data URL và gửi kèm payload.
Khi BE có endpoint upload (Cloudinary đã cấu hình sẵn cho avatar), chỉ cần đổi
chỗ đọc file thành gọi upload rồi lưu URL trả về — phần còn lại giữ nguyên.
Giới hạn 2 MB ở FE vì data URL phình ~33% và đi kèm payload JSON.

---

## 6. Việc cần Bình xác nhận

1. Đồng ý thêm 5 cột trên qua migration `V5` (`news_items.thumbnail_url`,
   `cities.image_url`, `promotions.image_url`, `promotions.description`,
   `promotions.badge_label`)?
2. `cities.image_url` có đúng là chỗ nên gắn ảnh tuyến bay không (thay vì tạo
   bảng `routes` riêng)? — theo tôi là đúng, vì DB không có bảng tuyến và
   "tuyến" chỉ là cặp thành phố.
3. `news_items` có cần thêm cả `summary`/`excerpt` không? Hiện FE tự cắt 110 ký
   tự đầu của `content` để làm dòng mô tả — nếu muốn ADMIN kiểm soát mô tả thì
   cần thêm cột.
4. `promotions.badge_label` — có đồng ý thêm không, hay để FE tự sinh nhãn từ
   `discount_type` + `discount_value` và bỏ nhãn marketing? (Xem mục 3.)
5. Xác nhận `V4` sẽ là migration nâng schema v0.1 → v0.4, để `V5` này không
   đánh số sai.

---

## 7. Phản hồi của BE (12/10/2026) — ĐÃ CHỐT

### 7.1 Số hiệu migration: dùng **V10**, không phải V5

Repo BE **đã dùng hết V4–V9** cho việc nâng schema v0.1 → v0.4 (Cụm 1):
`V4` users/RBAC + master rename · `V5` flights/fares · `V6` fare_rules ·
`V7` promotion/voucher scopes · `V8` booking core · `V9` wallets.

→ Phần bổ sung này là **`V10__content_images_and_permission_module.sql`**.
(Câu hỏi #5 ở mục 6 đã được trả lời: V4 chính là migration nâng lên v0.4, và
đã chạy xong trên Postgres local.)

### 7.2 Năm cột ảnh/mô tả/nhãn — **ĐỒNG Ý CẢ 5**

Đúng như đề xuất ở mục 2: `news_items.thumbnail_url`, `cities.image_url`,
`promotions.image_url`, `promotions.description`, `promotions.badge_label`
(đều `VARCHAR(500)` / `VARCHAR(30)`, nullable, không index).

Trả lời các câu hỏi:
- **#2 `cities.image_url` cho "tuyến bay phổ biến"** — đúng, không tạo bảng
  `routes`. DB v0.4 không có bảng tuyến; tuyến chỉ là cặp thành phố.
- **#3 `news_items.summary`** — **không thêm**. FE tự cắt `content` là đủ cho
  thẻ tin trang chủ; thêm cột nữa là thừa ở Phase 1. Nếu sau này ADMIN cần
  kiểm soát mô tả riêng thì mở migration mới.
- **#4 `promotions.badge_label`** — **đồng ý thêm**. Chi phí một `VARCHAR(30)`
  nhỏ, đổi lại ADMIN đổi được nhãn marketing ("Giá sốc", "Đồng giá") mà FE
  không phải hardcode.

### 7.3 Bổ sung ngoài đề xuất: `permissions.module` + 2 quyền còn thiếu

Khi rà soát lại API Design v0.2 mục 2.4, phát hiện **doc đã hứa** hai thứ mà
schema chưa có:

1. **`permissions.module`** — API v0.2 mục 2.4 cho phép `GET /permissions?module=AUTH`
   và trả `module` trong response (`{"permission_key": "LOGIN", "module": "AUTH"}`).
   Nhưng bảng `permissions` (cả DB v0.4 A.3) chỉ có `id`, `permission_key`,
   `description`. → **Thêm cột `module VARCHAR(30)`, backfill theo nhóm có sẵn
   trong V2 seed** (AUTH, USER, AIRLINE, CITY, AIRPORT, AIRCRAFT, SEAT_CONFIG,
   PROMOTION, VOUCHER, NEWS, FLIGHT, FARE, BAGGAGE, FLIGHT_FARE, SEAT_INVENTORY,
   PASSENGER, BOOKING, PAYMENT, ETICKET, DASHBOARD).
2. **2 quyền `VOUCHER_CLAIM` + `WALLET_READ_SELF`** — DB v0.4 A.3 ghi rõ "Cần
   seed thêm 2 quyền mới... cấp cho CUSTOMER", nhưng V2 seed không có và V4–V9
   cũng chưa thêm. → **Seed ngay trong V10, gán cho CUSTOMER.**

### 7.4 Việc FE cần làm sau khi BE xong V10

- `City` model thêm `image_url` (mục 5 đang chờ BE xác nhận — nay đã xác nhận).
- Khi BE có endpoint upload Cloudinary, đổi chỗ đọc file thành gọi upload.
- Màn RBAC (nếu làm) đọc thêm trường `module` từ `GET /permissions`.

### 7.5 Phạm vi tài liệu

**Không sửa `Database_Design_..._v0.4.docx`** — giữ nguyên làm chuẩn gốc.
Phần bổ sung này sống trong file đề xuất này + migration `V10`, truy vết được
qua `flyway_schema_history`. Nguồn sự thật cho code là **migration + entity**,
không phải docx.
