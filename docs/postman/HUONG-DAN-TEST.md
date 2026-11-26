# Hướng dẫn test API bằng Postman — Website Đặt Vé Máy Bay Nội Địa

Tài liệu này hướng dẫn bạn tự kiểm thử toàn bộ backend `domestic-flight-booking-web-backend` **độc lập bằng Postman, không cần mở Frontend**, bao gồm đầy đủ **3 vai trò (`ADMIN`, `STAFF`, `CUSTOMER`)**, **luồng đặt vé End-to-End**, **xử lý thanh toán/hủy đơn/thông báo** và **cơ chế tự động lưu token**.

---

## 1. Chuẩn bị (làm một lần)

### 1.1. Khởi động hạ tầng Docker (PostgreSQL + Redis)

Mở PowerShell tại thư mục gốc `d:\ChuyenDeTT`:

```powershell
docker compose -f domestic-flight-booking-web-backend\docker-compose.yml up -d postgres redis
```

### 1.2. Chạy Backend Spring Boot

```powershell
.\domestic-flight-booking-web-backend\mvnw.cmd -s D:\Maven\.m2\settings.xml spring-boot:run -f domestic-flight-booking-web-backend\pom.xml
```

- Backend chạy mặc định ở `http://localhost:8080` (profile `local`), tự chạy Flyway migration `V1`–`V19` và nạp sẵn dữ liệu demo (34 bảng).
- Nếu bạn đổi cổng server, sửa biến `base_url` trong tab **Variables** của Collection.

### 1.3. Import Collection vào Postman

1. Mở Postman → **File → Import**
2. Chọn file `domestic-flight-booking-web-backend/docs/postman/Domestic_Flight_Booking.postman_collection.json`
3. Collection **"Domestic Flight Booking API — Hệ thống Đặt Vé Máy Bay Nội Địa"** sẽ xuất hiện ở sidebar trái.

---

## 2. Cơ chế Tự động Lưu Token & Biến Collection

### 2.1. Tài khoản Demo 3 Vai trò (Seeded sẵn trong DB)

| Vai trò | Email | Mật khẩu | Biến Token tự lưu |
|---|---|---|---|
| **ADMIN** | `admin@flightbooking.local` | `Admin@12345` | `token_admin` / `refresh_token_admin` |
| **STAFF** | `staff@flightbooking.local` | `Staff@12345` | `token_staff` / `refresh_token_staff` |
| **CUSTOMER** | `customer@flightbooking.local` | `Customer@12345` | `token_customer` / `refresh_token_customer` |

### 2.2. Cách tự động lưu token

Collection đã cài sẵn **Collection-level Test Script**:
- Mỗi khi bạn gọi `POST /api/auth/login` hoặc `POST /api/auth/refresh` thành công (`200 OK`), script sẽ đọc `data.user.roles` và tự động lưu `data.access_token` + `data.refresh_token` vào **đúng biến của vai trò đó** (`token_admin`, `token_staff`, hoặc `token_customer`).
- Toàn bộ request trong Folder 02, 03, 04, 05 đều đã gắn sẵn `Authorization: Bearer {{token_customer}}`, `{{token_staff}}`, `{{token_admin}}` tương ứng — **không cần copy/dán token thủ công**.
- Mọi ID phát sinh trong luồng (`flight_id`, `fare_class_id`, `flight_seat_id`, `booking_id`, `order_code`, `booking_code`, `passenger_id`, `ticket_code`, `admin_flight_id`, `admin_seat_id`) đều được script **tự động lưu và truyền sang bước tiếp theo**.

### 2.3. Kiểm tra tự động (Test Results)

Sau mỗi lần bấm **Send**, mở tab **Test Results** ở khung response:
- Xanh (`PASS`): đúng mã HTTP, đúng `code: 1000`, đúng dữ liệu & phân quyền.
- Đỏ (`FAIL`): xem chi tiết dòng báo lỗi ở tab **Test Results** và **Body**.

---

## 3. Thứ tự Chạy Kiểm thử

Bạn có thể chạy nhanh toàn bộ collection bằng cách click chuột phải vào Collection → **Run collection**, hoặc chạy thủ công từng folder theo trình tự sau:

### Folder `00 — Công khai (Public — Không cần Token)`
Kiểm tra server hoạt động và dữ liệu danh mục công khai (`cities`, `airports`, `airlines`, `promotions`, `news`, `fare-rules`, `baggage-options`).

### Folder `01 — Xác thực & Phân quyền 3 Vai trò`
1. Chạy **01, 02, 03**: Đăng nhập lần lượt `ADMIN`, `STAFF`, `CUSTOMER` → 3 biến `token_admin`, `token_staff`, `token_customer` tự động được điền.
2. Chạy **04**: Test làm mới token (`POST /api/auth/refresh`) với `refresh_token_customer`.
3. Chạy **05, 06, 07**: Gọi `GET /api/profile` với từng token để kiểm chứng đúng người dùng.
4. Chạy **08, 09, 10, 11**: Kiểm tra các nhánh lỗi & RBAC:
   - Sai mật khẩu → `400` (`code: 1007`)
   - Không có token → `401`
   - `CUSTOMER` gọi `/api/admin/dashboard` → `403`
   - `STAFF` gọi `/api/admin/users` → `403` (chỉ `ADMIN` có quyền `USER_READ`)

### Folder `02 — Luồng Đặt Vé End-to-End (CUSTOMER)`
Chạy tuần tự từ **01 → 13** (dùng `token_customer`):

| Bước | Request | Kết quả kỳ vọng & Biến tự lưu |
|---|---|---|
| 01 | `GET /api/public/flights/search` | Tìm chuyến bay mở bán có ghế trống → lưu `flight_id`, `fare_class_id`, `flight_airline_id` |
| 02 | `GET /api/public/flights/{{flight_id}}/seat-map` | Tìm 1 ghế `AVAILABLE` → lưu `flight_seat_id`, `flight_seat_code` |
| 03 | `GET /api/public/baggage-options` | Lấy gói hành lý theo hãng → lưu `baggage_option_id` |
| 04 | `GET /api/my-vouchers` | Xem ví voucher của khách hàng |
| 05 | `POST /api/bookings` | Tạo đơn `PENDING_PAYMENT` → lưu `booking_id`, `order_code`, `passenger_id` |
| 06 | `POST /api/bookings/{{booking_id}}/seats/hold` | Giữ ghế tạm 15 phút (Redis Lock + Atomic SQL) → lưu `seat_hold_id` |
| 07 | `POST /api/bookings/{{booking_id}}/baggages` | Chọn gói hành lý ký gửi, tự tính lại `total_amount` |
| 08 | `GET /api/bookings/{{booking_id}}` | Kiểm tra đầy đủ chặng, hành khách, ghế giữ, hành lý trước khi thanh toán |
| 09 | `POST /api/bookings/{{booking_id}}/payments` | Thanh toán giả lập `VNPAY` (`simulate_result: "SUCCESS"`) → đơn tự chuyển `CONFIRMED`, xuất E-Ticket |
| 10 | `GET /api/bookings/{{booking_id}}` | Xác nhận `status: CONFIRMED` & có mã PNR `booking_code` |
| 11 | `GET /api/bookings/{{booking_id}}/e-tickets` | Lấy vé điện tử → lưu `ticket_code` |
| 12 | `GET /api/public/e-tickets/verify` | Xác thực QR vé điện tử công khai (ẩn PII nhạy cảm) |
| 13 | `GET /api/public/bookings/lookup` | Tra cứu đặt chỗ công khai bằng PNR + email |

### Folder `03 — Xử lý Payment Callback, Hủy đơn & Thông báo (CUSTOMER)`
- Tạo 1 đơn phụ (`cancel_booking_id`) để không ảnh hưởng đến đơn đã `CONFIRMED` ở Folder 02.
- Tạo giao dịch `VIETQR` ở trạng thái `PENDING` → gọi `POST /api/payments/callback` giả lập kết quả `FAILED`.
- Khách hàng tự hủy đơn (`POST /api/bookings/{{cancel_booking_id}}/cancel`) → trạng thái chuyển `CANCELLED`.
- Kiểm tra lịch sử đặt vé (`GET /api/bookings`) và thông báo in-app (`unread-count`, danh sách, `read-all`).

### Folder `04 — Vai trò STAFF (Vận hành chuyến bay, Ghế & Hỗ trợ Booking)`
Dùng `token_staff`:
- `GET /api/admin/dashboard`: kiểm chứng trả về **Dashboard vận hành** (`flights_today`, `today_schedule`, `pending_actions`) và **không lộ** `revenue_total`.
- Xem danh sách chuyến bay, chi tiết chuyến bay kèm hành khách, sơ đồ tồn ghế.
- Test khóa ghế (`POST /api/admin/flight-seats/{{admin_seat_id}}/block` → `BLOCKED`) và mở khóa lại ngay (`unblock` → `AVAILABLE`) để trả nguyên trạng DB.
- Xem danh sách booking toàn hệ thống, chi tiết booking và E-Ticket hỗ trợ khách hàng.

### Folder `05 — Vai trò ADMIN (Quản trị, Danh mục, RBAC & Báo cáo Doanh thu)`
Dùng `token_admin`:
- `GET /api/admin/dashboard/overview` & `/reports?days=30`: kiểm chứng trả đầy đủ chỉ số doanh thu (`revenue_total`, `revenue_by_airline`, `bookings_by_status`).
- Quản lý người dùng (`GET /api/admin/users`), vai trò & ma trận quyền (`roles`, `permissions`).
- Rà soát toàn bộ danh mục Master Data (`airlines`, `aircrafts`, `seat-types`, `fare-classes`, `baggage-options`, `fare-rules`, `flight-fares`), Marketing (`promotions`, `vouchers`) và Tin tức (`news`).

### Folder `06 — Trường hợp Biên & Ngoại lệ (Edge Cases & Negative Tests)`
Kiểm thử đầy đủ 19 tình huống biên, ngoại lệ và bảo mật theo bảng mã lỗi chuẩn (`1001`–`1007`):
- **Validation & Biên đầu vào (`400`, `code: 1001`):** Login rỗng, đăng ký email/mật khẩu sai, tạo booking **10 hành khách** (vượt biên tối đa 9), `segments` rỗng, hành lý `quantity = 0`, phương thức thanh toán không hỗ trợ (`CRYPTO`).
- **Quy tắc Nghiệp vụ & Trùng lặp (`409`, `code: 1005`/`1006`):** Số `INFANT` > `ADULT`, sai tuổi theo quy tắc `D2=A` (`ADULT < 12 tuổi`), giữ ghế trên đơn đã `CONFIRMED`, thanh toán lại đơn đã `CONFIRMED`, hủy lại đơn đã `CANCELLED`, đăng ký trùng email (`1005`).
- **Không tồn tại (`404`, `code: 1004`):** Booking ID không tồn tại (`99999999`), tra cứu đặt chỗ sai PNR/email, xác thực QR vé điện tử giả.
- **Bảo mật & Chống IDOR (`401`/`403`/`404`):** Đăng nhập khách hàng thứ 2 (`minhhoang@vinafly.vn` → tự lưu `token_customer_2`) thử xem trộm `booking_id` của khách hàng 1, JWT giả mạo sai chữ ký, Refresh Token rác.

---

## 4. Xử lý sự cố thường gặp

| Triệu chứng | Nguyên nhân | Cách khắc phục |
|---|---|---|
| `ECONNREFUSED localhost:8080` | Backend chưa khởi động | Chạy `mvnw spring-boot:run` như Mục 1.2 |
| `401 UNAUTHORIZED` | Chưa đăng nhập hoặc Access Token hết hạn (15 phút) | Chạy lại bước 01–03 ở **Folder 01** để tự làm mới token |
| `403 FORBIDDEN` | Dùng nhầm token vai trò (vd `CUSTOMER` gọi API `/admin/**`) | Kiểm tra header `Authorization` đã đúng biến `token_admin` / `token_staff` / `token_customer` |
| Bước 05/06 ở Folder 02 báo lỗi `{{flight_id}}` | Chưa chạy bước 01–02 của Folder 02 | Chạy tuần tự từ bước **01 → 02 → 03 → 05 → 06** để biến tự lưu |
| Redis connection refused khi login | Chưa bật container Docker Redis | Chạy `docker compose -f domestic-flight-booking-web-backend\docker-compose.yml up -d postgres redis` |

---

## 5. Tài liệu liên quan

- Đặc tả API đầy đủ: [API_Design_Website_Dat_Ve_May_Bay_v0.4.md](../API_Design_Website_Dat_Ve_May_Bay_v0.4.md)
- Thiết kế CSDL & ERD: [Database_Design_Domestic_Flight_Booking_v0.5.md](../Database_Design_Domestic_Flight_Booking_v0.5.md)
- Ma trận phân quyền: [Permission_Matrix_v2.1.md](../Permission_Matrix_v2.1.md)
