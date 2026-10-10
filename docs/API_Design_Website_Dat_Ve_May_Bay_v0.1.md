# API Design — Website Đặt Vé Máy Bay Nội Địa

**Phiên bản:** v0.1 (Phase 1)
**Ngày:** 19/09/2026
**Trạng thái:** Bản thiết kế — chưa triển khai code
**Căn cứ:** Database Design v0 · Permission Matrix v2.0 (3 Phase) · Phase 1 Use Case Specification v2.3 · Jira Plan Sprint 2–6 (Revised)

---

## 0. Phạm vi tài liệu

Tài liệu chốt **hợp đồng API (contract)** giữa Backend và Frontend cho **Phase 1**, làm chuẩn để hai repo (`domestic-flight-booking-web-backend`, `domestic-flight-booking-web-frontend`) code song song mà không lệch.

- **Phase 1 (đặc tả đầy đủ):** Authentication & Account, Profile, User Management, RBAC, Master Data (Airline / City / Airport / Aircraft / Seat Configuration), Promotion, Voucher, News.
- **Phase 2–3:** liệt kê sơ bộ để khóa naming, không đi sâu.
- Tài liệu **không** mô tả code triển khai (service/controller) — chỉ mô tả bề mặt API.

---

## 1. Quy ước chung

### 1.1 Base URL & Versioning

| Mục | Giá trị |
|---|---|
| Base path | `/api/v1` |
| Content-Type | `application/json; charset=UTF-8` |
| Versioning | Theo path segment (`/v1`). Khi breaking change → `/v2`, giữ `/v1` chạy song song |

### 1.2 Định dạng dữ liệu

- **JSON dùng `snake_case`** cho mọi field (khớp tên cột database, giảm ma sát khi map DTO).
- Ngày: `YYYY-MM-DD` (`date`). Ngày giờ: ISO-8601 UTC, ví dụ `2026-09-19T08:43:22Z` (`timestamptz`).
- Tiền: kiểu số (`number`) theo đơn vị **VND**, không làm tròn ở tầng API. Mọi response tiền tệ kèm field `currency` (mặc định `"VND"`).
- Trường `null`: **luôn có mặt** trong response (không lược bỏ key) để client không phải phân biệt "không có field" và "field rỗng".
- ID: `number` (BIGINT). Không dùng string.

### 1.3 Response wrapper

Mọi response (thành công và lỗi) đều bọc trong một envelope thống nhất:

```json
{
  "code": 1000,
  "message": "Success",
  "data": { }
}
```

| Field | Kiểu | Mô tả |
|---|---|---|
| `code` | number | Mã kết quả nghiệp vụ (xem 1.4). `1000` = thành công |
| `message` | string | Thông điệp hiển thị được cho người dùng (tiếng Việt) |
| `data` | object \| array \| null | Payload. `null` khi lỗi hoặc khi thao tác không trả dữ liệu |

**HTTP status luôn phản ánh đúng bản chất** (200/201/204/400/401/403/404/409/500) — `code` trong body bổ sung chi tiết nghiệp vụ. Client **không** chỉ dựa vào HTTP status để hiển thị lỗi.

Với danh sách, `data` là object phân trang (xem 1.6), không phải mảng trần.

### 1.4 Error codes

| Code | HTTP | Tên | Khi nào |
|---|---|---|---|
| `1000` | 200/201 | `SUCCESS` | Thành công |
| `1001` | 400 | `VALIDATION_ERROR` | Dữ liệu đầu vào sai định dạng/thiếu trường bắt buộc. `data` chứa mảng lỗi theo field |
| `1002` | 401 | `UNAUTHENTICATED` | Thiếu/sai/hết hạn JWT |
| `1003` | 403 | `ACCESS_DENIED` | Đã đăng nhập nhưng thiếu permission |
| `1004` | 404 | `RESOURCE_NOT_FOUND` | Không tìm thấy tài nguyên |
| `1005` | 409 | `DUPLICATE_RESOURCE` | Vi phạm ràng buộc UNIQUE (email, airline_code, city_code, …) |
| `1006` | 409/422 | `BUSINESS_RULE_VIOLATION` | Vi phạm quy tắc nghiệp vụ (xem ghi chú dưới) |
| `1007` | 400 | `INVALID_CREDENTIALS` | Sai email/mật khẩu khi login |
| `1008` | 403 | `ACCOUNT_INACTIVE` | Tài khoản `status = INACTIVE` |
| `9999` | 500 | `INTERNAL_ERROR` | Lỗi hệ thống. Không lộ stack trace ra client |

Lỗi validate trả chi tiết theo field:

```json
{
  "code": 1001,
  "message": "Dữ liệu không hợp lệ",
  "data": [
    { "field": "email", "message": "Email không đúng định dạng" },
    { "field": "full_name", "message": "Không được để trống" }
  ]
}
```

`BUSINESS_RULE_VIOLATION` dùng cho các ràng buộc đã ghi trong Database Design v0 (ví dụ `end_date >= start_date`, `usage_limit > 0`, `departure_airport_id <> arrival_airport_id`). Message phải nêu rõ quy tắc bị vi phạm.

### 1.5 Xác thực & phân quyền

- Cơ chế: **JWT Bearer** (`Authorization: Bearer <access_token>`), Spring Security 6 OAuth2 Resource Server.
- Access token chứa `sub` (user id), `email`, và danh sách quyền dưới dạng claim `permissions` (danh sách permission_key) — cho phép kiểm tra quyền không cần truy vấn DB mỗi request.
- **GUEST = chưa đăng nhập.** GUEST không phải một role trong database; quyền của GUEST thể hiện bằng `permitAll` ở tầng Spring Security cho các endpoint công khai. Không seed role GUEST (xem mục 4).
- **Role cố định MVP:** `CUSTOMER`, `STAFF`, `ADMIN`. Admin gán role cho user; không tạo role mới qua API (BR-RBAC-03).
- Endpoint công khai (không cần token): đăng ký, đăng nhập, quên/đặt lại mật khẩu, và toàn bộ **GET** dữ liệu công khai (airline, city, airport, promotion, news, news category, flight search).
- Endpoint `SELF` (ví dụ `PROFILE_READ_SELF`, `BOOKING_READ_SELF`): hệ thống **luôn** lấy user từ token, không nhận `user_id` từ client — tránh truy cập chéo tài khoản.
- Lỗi xác thực: thiếu/hết hạn token → `1002`; đủ token nhưng thiếu quyền → `1003`.

### 1.6 Phân trang, lọc, sắp xếp (theo BR-LIST-01 → BR-LIST-05)

Query params chuẩn cho mọi endpoint danh sách:

| Param | Kiểu | Mặc định | Ghi chú |
|---|---|---|---|
| `page` | number | `1` | Trang hiện tại, bắt đầu từ **1** |
| `size` | number | `20` | Số bản ghi/trang, tối đa `100` |
| `keyword` | string | — | Tìm kiếm theo trường text được whitelist của từng module |
| `sort` | string | tuỳ module | `field,direction` — ví dụ `airline_name,asc`. `direction` ∈ `asc` \| `desc` |
| *(filter)* | string | — | Filter riêng từng module, ví dụ `status=ACTIVE`, `city_id=3` |

Response danh sách:

```json
{
  "code": 1000,
  "message": "Success",
  "data": {
    "items": [ ],
    "pagination": {
      "page": 1,
      "size": 20,
      "total_items": 137,
      "total_pages": 7
    }
  }
}
```

Quy tắc bắt buộc:

- **BR-LIST-01:** các thao tác search/filter/sort/pagination là **hành vi đọc**, không tạo/cập nhật/xóa dữ liệu.
- **BR-LIST-02:** khi người dùng đổi keyword/filter/sort, client **quay về trang 1**. Kết quả rỗng là **trạng thái hợp lệ** — trả `200` + `items: []`, **không** trả lỗi.
- **BR-LIST-03:** nhiều filter kết hợp theo **AND**. `sort` chỉ nhận field nằm trong **whitelist** của từng module; field ngoài whitelist → bỏ qua và dùng sort mặc định (không trả lỗi 500). Pagination thực hiện ở **backend**.
- **BR-LIST-04:** GUEST/CUSTOMER chỉ truy vấn được dữ liệu công khai hoặc dữ liệu **thuộc sở hữu của mình**. Dữ liệu `DRAFT`, chưa publish, đã soft-delete, hoặc trạng thái nội bộ **không được lộ** qua bất kỳ đường search/filter/sort/pagination nào. Đây là ràng buộc **bắt buộc ở tầng service**, không chỉ ở tầng UI.
- **BR-LIST-05:** Role/Permission là catalog cố định, read-only → không cần phân trang (xem 2.4).

### 1.7 Ngày giờ & tiền tệ

- Server lưu và trả `timestamptz` theo **UTC**; client chịu trách nhiệm hiển thị theo múi giờ người dùng (UTC+7).
- `date` (ngày thuần, ví dụ `birth_date`, `start_date`) không kèm múi giờ.
- Tiền: `number` + `currency`. Không format dấu phân cách ở tầng API — việc hiển thị thuộc client.

---

## 2. Phase 1 — Đặc tả endpoint theo module

Ký hiệu: **P** = public (permitAll, không cần token) · **A** = cần access token.

### 2.1 Authentication & Account

Bảng `users`. Nguồn: AUTH-01 → AUTH-06.

| # | Method | Path | Quyền | UC |
|---|---|---|---|---|
| 1 | POST | `/api/v1/auth/register` | P (GUEST-only) | AUTH-01 |
| 2 | POST | `/api/v1/auth/login` | P | AUTH-02 |
| 3 | POST | `/api/v1/auth/logout` | A — `LOGOUT` | AUTH-03 |
| 4 | POST | `/api/v1/auth/password-reset/request` | P | AUTH-04 |
| 5 | POST | `/api/v1/auth/password-reset/confirm` | P | AUTH-04 |
| 6 | POST | `/api/v1/auth/change-password` | A — `PASSWORD_CHANGE_SELF` | AUTH-05 |

**POST `/auth/register`** — request:

```json
{
  "email": "binh@example.com",
  "password": "P@ssw0rd123",
  "full_name": "Nguyễn Chút Tiến Bình",
  "phone": "0901234567"
}
```

Response `201`:

```json
{
  "code": 1000,
  "message": "Đăng ký thành công",
  "data": {
    "id": 12,
    "email": "binh@example.com",
    "full_name": "Nguyễn Chút Tiến Bình",
    "phone": "0901234567",
    "avatar_url": null,
    "status": "ACTIVE",
    "roles": ["CUSTOMER"],
    "created_at": "2026-09-19T08:43:22Z"
  }
}
```

Ràng buộc: `email` UNIQUE → trùng trả `1005`. Mật khẩu lưu dạng hash (BCrypt), **không bao giờ** trả `password_hash` trong bất kỳ response nào. Role gán cứng `CUSTOMER` — client không được gửi `roles` khi register.

**POST `/auth/login`** — request `{ "email", "password" }`. Response `data`:

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "user": {
    "id": 12,
    "email": "binh@example.com",
    "full_name": "Nguyễn Chút Tiến Bình",
    "avatar_url": null,
    "roles": ["CUSTOMER"],
    "permissions": ["BOOKING_CREATE", "BOOKING_READ_SELF", "..."]
  }
}
```

Sai thông tin → `1007` (401). Tài khoản `INACTIVE` → `1008` (403). Thông điệp lỗi của `1007` **không** phân biệt "sai email" và "sai mật khẩu" (chống dò tài khoản).

**POST `/auth/logout`** — response `200` với `data: null`. MVP dùng JWT stateless: logout chủ yếu để client xóa token; backend ghi nhận sự kiện và (nếu bật) đưa token vào danh sách thu hồi.

**POST `/auth/password-reset/request`** — request `{ "email" }`. **Luôn** trả `200` với message trung tính ("Nếu email tồn tại, hướng dẫn đã được gửi"), kể cả khi email không tồn tại — chống dò tài khoản. Xem mục 5 về nơi lưu token reset.

**POST `/auth/password-reset/confirm`** — request `{ "token", "new_password" }`. Token sai/hết hạn → `1006`.

**POST `/auth/change-password`** — request `{ "current_password", "new_password" }`. Sai mật khẩu hiện tại → `1007`.

### 2.2 Profile

Nguồn: PRO-01, PRO-02. Luôn thao tác trên user trong token.

| # | Method | Path | Quyền |
|---|---|---|---|
| 7 | GET | `/api/v1/profile` | A — `PROFILE_READ_SELF` |
| 8 | PUT | `/api/v1/profile` | A — `PROFILE_UPDATE_SELF` |

`GET /profile` trả về chính user đang đăng nhập (cấu trúc như `data` của register).

`PUT /profile` — chỉ cho phép sửa `full_name`, `phone`, `avatar_url`:

```json
{ "full_name": "Nguyễn C. T. Bình", "phone": "0907654321", "avatar_url": "https://res.cloudinary.com/..." }
```

`email`, `status`, `roles` **không** sửa được qua endpoint này (email đổi qua luồng riêng; status/role chỉ ADMIN qua module User). Gửi field ngoài whitelist → bỏ qua, không lỗi.

**Upload avatar:** `POST /api/v1/profile/avatar` (multipart/form-data, field `file`) → trả `{ "avatar_url": "..." }`. Ảnh lưu qua Cloudinary (đã cấu hình trong `application-dev.properties`).

### 2.3 User Management (ADMIN)

Bảng `users`, `user_roles`. Nguồn: USR-01 → USR-05.

| # | Method | Path | Quyền |
|---|---|---|---|
| 9 | GET | `/api/v1/users` | A — `USER_READ` |
| 10 | GET | `/api/v1/users/{id}` | A — `USER_READ_DETAIL` |
| 11 | POST | `/api/v1/users` | A — `USER_CREATE` |
| 12 | PUT | `/api/v1/users/{id}` | A — `USER_UPDATE` |
| 13 | DELETE | `/api/v1/users/{id}` | A — `USER_DELETE` |

**POST `/users`** — ADMIN tạo user và **chọn role ngay khi tạo** (BR-RBAC-02):

```json
{
  "email": "staff01@example.com",
  "password": "Temp@12345",
  "full_name": "Trần Đức Anh",
  "phone": "0912345678",
  "status": "ACTIVE",
  "roles": ["STAFF"]
}
```

`roles` chỉ nhận giá trị trong `CUSTOMER` / `STAFF` / `ADMIN`. Giá trị khác → `1001`.

**PUT `/users/{id}`** — cập nhật thông tin **và** đổi role **và** toggle trạng thái (USR-04 gộp cả ba):

```json
{
  "full_name": "Trần Đức Anh",
  "phone": "0912345678",
  "status": "INACTIVE",
  "roles": ["STAFF"]
}
```

`password` không nằm trong payload update — ADMIN đặt lại mật khẩu qua `POST /users/{id}/reset-password` (trả mật khẩu tạm), tách khỏi luồng sửa hồ sơ.

**DELETE `/users/{id}`** — **soft delete**: đặt `status = INACTIVE`, giữ nguyên dữ liệu lịch sử (USR-05). Trả `200` + `data: null`. **Không** xóa bản ghi khỏi database.

Chặn leo thang đặc quyền: ADMIN **không** được tự hạ quyền chính mình nếu đó là ADMIN cuối cùng đang `ACTIVE` → `1006`.

Filter danh sách: `status`, `role`. Whitelist sort: `email`, `full_name`, `created_at`. Whitelist keyword: `email`, `full_name`, `phone`.

### 2.4 RBAC — Role & Permission (ADMIN, read-only)

Bảng `roles`, `permissions`, `role_permissions`. Nguồn: RBAC-01, RBAC-02.

| # | Method | Path | Quyền |
|---|---|---|---|
| 14 | GET | `/api/v1/roles` | A — `ROLE_READ` |
| 15 | GET | `/api/v1/permissions` | A — `ROLE_READ` |

**Không** có POST/PUT/DELETE cho role và permission ở MVP: role cố định, permission catalog seed và chỉ đọc (BR-RBAC-03).

`GET /roles` trả **mảng trần** trong `data` (không phân trang — BR-LIST-05):

```json
{
  "code": 1000,
  "message": "Success",
  "data": [
    { "id": 1, "name": "CUSTOMER", "description": "Khách hàng đã đăng ký/đăng nhập — nghiệp vụ cá nhân và booking" },
    { "id": 2, "name": "STAFF", "description": "Nhân viên vận hành — quản lý flight, fare, seat, booking" },
    { "id": 3, "name": "ADMIN", "description": "Quản trị hệ thống — user, RBAC, master data, content" }
  ]
}
```

`GET /permissions` hỗ trợ search/filter/sort theo BR-LIST-05:

| Param | Giá trị |
|---|---|
| `keyword` | tìm theo `permission_key` hoặc `description` |
| `module` | filter theo nhóm module (`AUTH`, `USER`, `AIRLINE`, `CITY`, …) |
| `sort` | `permission_key,asc` (mặc định) hoặc `permission_key,desc` |

```json
{
  "code": 1000,
  "message": "Success",
  "data": [
    { "id": 1, "permission_key": "LOGIN", "description": "Đăng nhập hệ thống", "module": "AUTH" }
  ]
}
```

**Lưu ý:** quyền xem Permission catalog dùng chung key `ROLE_READ` — Permission Matrix v2.0 không định nghĩa key `PERMISSION_READ` riêng (xem mục 4).

### 2.5 Master Data — Airline / City / Airport

Bảng `airlines`, `cities`, `airports`. Nguồn: AIR-01→05, CITY-01→05, APT-01→05.

Cả ba module dùng **cùng một khuôn** 5 endpoint. Ví dụ với Airline:

| # | Method | Path | Quyền |
|---|---|---|---|
| 16 | GET | `/api/v1/airlines` | P — `AIRLINE_READ` |
| 17 | GET | `/api/v1/airlines/{id}` | P — `AIRLINE_READ_DETAIL` |
| 18 | POST | `/api/v1/airlines` | A — `AIRLINE_CREATE` |
| 19 | PUT | `/api/v1/airlines/{id}` | A — `AIRLINE_UPDATE` |
| 20 | DELETE | `/api/v1/airlines/{id}` | A — `AIRLINE_DELETE` |

| Module | Base path | Permission prefix | Bảng |
|---|---|---|---|
| Airline | `/api/v1/airlines` | `AIRLINE_` | `airlines` |
| City | `/api/v1/cities` | `CITY_` | `cities` |
| Airport | `/api/v1/airports` | `AIRPORT_` | `airports` |

**GET danh sách — public.** Trả về **chỉ** bản ghi `status = ACTIVE` cho GUEST/CUSTOMER. ADMIN/STAFF gọi cùng endpoint có thể truyền `status=INACTIVE` để xem cả bản ghi đã ngừng dùng (BR-LIST-04).

```json
{
  "code": 1000,
  "message": "Success",
  "data": {
    "items": [
      { "id": 1, "airline_code": "VN", "airline_name": "Vietnam Airlines", "country": "Việt Nam", "logo_url": null, "status": "ACTIVE" }
    ],
    "pagination": { "page": 1, "size": 20, "total_items": 5, "total_pages": 1 }
  }
}
```

Whitelist cho từng module:

| Module | keyword | filter | sort |
|---|---|---|---|
| Airline | `airline_code`, `airline_name` | `status`, `country` | `airline_code`, `airline_name` |
| City | `city_code`, `city_name` | `country` | `city_code`, `city_name` |
| Airport | `airport_code`, `airport_name` | `city_id` | `airport_code`, `airport_name` |

**POST `/airlines`** (ADMIN):

```json
{ "airline_code": "VJ", "airline_name": "VietJet Air", "country": "Việt Nam", "logo_url": "https://...", "status": "ACTIVE" }
```

`airline_code` UNIQUE → trùng `1005`. Response `201`.

**Airport** có thêm FK `city_id` và toạ độ:

```json
{
  "airport_code": "HAN",
  "airport_name": "Sân bay Quốc tế Nội Bài",
  "city_id": 1,
  "latitude": 21.221200,
  "longitude": 105.807200
}
```

`city_id` không tồn tại → `1004`. Response chi tiết Airport trả kèm `city` lồng:

```json
{
  "id": 1, "airport_code": "HAN", "airport_name": "Sân bay Quốc tế Nội Bài",
  "latitude": 21.221200, "longitude": 105.807200,
  "city": { "id": 1, "city_code": "HAN", "city_name": "Hà Nội", "country": "Việt Nam" }
}
```

**PUT** — cập nhật thông tin **và** toggle `status` (AIR-03/CITY-03/APT-03 gộp cả hai). `code` là định danh nghiệp vụ: **không** cho đổi qua PUT (tránh phá tham chiếu ở tầng dữ liệu); muốn đổi phải tạo mới + soft delete bản cũ.

**DELETE** — **soft delete**: đặt `status = INACTIVE`, không xóa bản ghi. Trước khi cho ngừng dùng, service kiểm tra ràng buộc tham chiếu (ví dụ Airline còn Flight đang hoạt động → `1006`).

### 2.6 Aircraft & Seat Configuration

Bảng `aircrafts`, `seat_types`, `seat_configurations`. Nguồn: AC-01→05, SEAT-01.

| # | Method | Path | Quyền |
|---|---|---|---|
| 21 | GET | `/api/v1/aircrafts` | A — `AIRCRAFT_READ` |
| 22 | GET | `/api/v1/aircrafts/{id}` | A — `AIRCRAFT_READ_DETAIL` |
| 23 | POST | `/api/v1/aircrafts` | A — `AIRCRAFT_CREATE` |
| 24 | PUT | `/api/v1/aircrafts/{id}` | A — `AIRCRAFT_UPDATE` |
| 25 | DELETE | `/api/v1/aircrafts/{id}` | A — `AIRCRAFT_DELETE` |
| 26 | GET | `/api/v1/seat-types` | A — `SEAT_CONFIG_READ` |
| 27 | GET | `/api/v1/aircrafts/{id}/seat-configurations` | A — `SEAT_CONFIG_READ_DETAIL` |
| 28 | PUT | `/api/v1/aircrafts/{id}/seat-configurations` | A — `SEAT_CONFIG_UPDATE` |

**Khác biệt so với Master Data ở 2.5: Aircraft KHÔNG public** — chỉ `STAFF`/`ADMIN` (AC-01, AC-05). GUEST/CUSTOMER không truy cập được.

**POST `/aircrafts`**:

```json
{ "aircraft_code": "VN-A350-01", "model": "Airbus A350-900", "manufacturer": "Airbus", "seat_capacity": 305 }
```

`seat_capacity > 0` → vi phạm `1006`. `aircraft_code` UNIQUE → `1005`.

**Seat Configuration** — sơ đồ ghế của một máy bay (SEAT-01), thao tác theo **cả sơ đồ**, không CRUD từng ghế:

```json
{
  "seats": [
    { "row_number": 1, "column_code": "A", "seat_type_id": 3 },
    { "row_number": 1, "column_code": "B", "seat_type_id": 3 },
    { "row_number": 10, "column_code": "A", "seat_type_id": 1 }
  ]
}
```

`PUT` thay **toàn bộ** sơ đồ của máy bay (replace-all trong một transaction). Ràng buộc: UNIQUE `(aircraft_id, row_number, column_code)` → trùng vị trí trong payload trả `1006`. `seat_type_id` không tồn tại → `1004`. Số ghế cấu hình **không được vượt** `aircrafts.seat_capacity` → `1006`.

`GET /aircrafts/{id}/seat-configurations` trả sơ đồ kèm loại ghế:

```json
{
  "aircraft_id": 1,
  "seat_capacity": 305,
  "configured_seats": 3,
  "seats": [
    { "id": 10, "row_number": 1, "column_code": "A", "seat_type": { "id": 3, "type_code": "BUS", "type_name": "Ghế thương gia", "seat_class": "BUSINESS" } }
  ]
}
```

`GET /seat-types` — danh mục loại ghế (seed sẵn: `ECO`, `PREM_ECO`, `BUS`), read-only ở Phase 1, trả mảng trần.

### 2.7 Promotion

Bảng `promotions`. Nguồn: PROMO-01 → PROMO-05.

| # | Method | Path | Quyền |
|---|---|---|---|
| 29 | GET | `/api/v1/promotions` | P — `PROMOTION_READ` |
| 30 | GET | `/api/v1/promotions/{id}` | P — `PROMOTION_READ_DETAIL` |
| 31 | POST | `/api/v1/promotions` | A — `PROMOTION_CREATE` |
| 32 | PUT | `/api/v1/promotions/{id}` | A — `PROMOTION_UPDATE` |
| 33 | DELETE | `/api/v1/promotions/{id}` | A — `PROMOTION_DELETE` |

**POST `/promotions`**:

```json
{
  "promo_code": "HE2026",
  "promo_name": "Khuyến mãi hè 2026",
  "discount_type": "PERCENT",
  "discount_value": 15.00,
  "start_date": "2026-06-01",
  "end_date": "2026-08-31",
  "status": "ACTIVE"
}
```

`discount_type` ∈ `PERCENT` | `AMOUNT`. Ràng buộc từ Database Design v0: `end_date >= start_date` và `discount_value >= 0` → vi phạm `1006`. `promo_code` UNIQUE → `1005`.

**GET danh sách — public:** GUEST/CUSTOMER chỉ thấy promotion đang hiệu lực (`status = ACTIVE` **và** `start_date <= hôm nay <= end_date`). ADMIN/STAFF thấy tất cả, filter được `status`.

Whitelist: keyword `promo_code`, `promo_name`; filter `status`, `discount_type`; sort `start_date`, `end_date`, `promo_code`.

**PUT** — cập nhật **và** toggle `status`. **DELETE** — soft delete (`status = INACTIVE`).

**Lưu ý Phase 1:** Promotion ở Phase 1 chỉ **quản lý định nghĩa**. Việc áp dụng vào giá vé thuộc Phase 3.

### 2.8 Voucher

Bảng `vouchers`, `user_vouchers`. Nguồn: VOU-01 → VOU-05.

| # | Method | Path | Quyền |
|---|---|---|---|
| 34 | GET | `/api/v1/vouchers` | A — `VOUCHER_READ` |
| 35 | GET | `/api/v1/vouchers/{id}` | A — `VOUCHER_READ_DETAIL` |
| 36 | POST | `/api/v1/vouchers` | A — `VOUCHER_CREATE` |
| 37 | PUT | `/api/v1/vouchers/{id}` | A — `VOUCHER_UPDATE` |
| 38 | DELETE | `/api/v1/vouchers/{id}` | A — `VOUCHER_DELETE` |
| 39 | GET | `/api/v1/my-vouchers` | A — `VOUCHER_READ` (SELF) |
| 40 | GET | `/api/v1/my-vouchers/{id}` | A — `VOUCHER_READ_DETAIL` (SELF) |
| 41 | POST | `/api/v1/vouchers/{id}/assign` | A — `VOUCHER_UPDATE` |

**POST `/vouchers`** (ADMIN):

```json
{
  "voucher_code": "SUMMER50K",
  "voucher_type": "AMOUNT",
  "value_amount": 50000.00,
  "start_date": "2026-06-01",
  "end_date": "2026-12-31",
  "usage_limit": 1000,
  "per_user_limit": 1,
  "scope": "GLOBAL",
  "status": "ACTIVE"
}
```

`scope` ∈ `GLOBAL` (dùng mã, ai cũng áp được) | `ASSIGNED` (cấp cho user cụ thể). `usage_limit`/`per_user_limit` cho phép `null` = không giới hạn, nhưng nếu có giá trị phải `> 0` → `1006`.

**`GET /my-vouchers`** (CUSTOMER, VOU-01/VOU-05) — danh sách voucher **thuộc tài khoản** trong token, đọc từ `user_vouchers`:

```json
{
  "code": 1000,
  "message": "Success",
  "data": {
    "items": [
      {
        "id": 7, "status": "AVAILABLE", "assigned_at": "2026-09-01T02:00:00Z", "used_at": null,
        "voucher": { "id": 3, "voucher_code": "SUMMER50K", "voucher_type": "AMOUNT", "value_amount": 50000.00, "start_date": "2026-06-01", "end_date": "2026-12-31" }
      }
    ],
    "pagination": { "page": 1, "size": 20, "total_items": 1, "total_pages": 1 }
  }
}
```

`status` ∈ `AVAILABLE` | `USED` | `EXPIRED`. Endpoint SELF **không** nhận `user_id` từ client (xem 1.5).

**`POST /vouchers/{id}/assign`** (ADMIN) — cấp voucher `ASSIGNED` cho user:

```json
{ "user_ids": [12, 15, 20] }
```

Ràng buộc UNIQUE `(user_id, voucher_id)` → cấp trùng `1005`. Voucher `scope = GLOBAL` không cần assign → `1006`.

**Lưu ý Phase 1:** validate/apply/redeem voucher thuộc **Phase 3**; Phase 1 chỉ quản lý definition và assignment (Approved Baseline Decision).

### 2.9 News & News Category

Bảng `news_items`, `news_categories`, `news_category_mapping`. Nguồn: NEWS-01→07, NEWS-CAT-01→05.

| # | Method | Path | Quyền |
|---|---|---|---|
| 42 | GET | `/api/v1/news` | P — `NEWS_READ` |
| 43 | GET | `/api/v1/news/{id}` | P — `NEWS_READ_DETAIL` |
| 44 | POST | `/api/v1/news` | A — `NEWS_CREATE` |
| 45 | PUT | `/api/v1/news/{id}` | A — `NEWS_UPDATE` |
| 46 | DELETE | `/api/v1/news/{id}` | A — `NEWS_DELETE` |
| 47 | POST | `/api/v1/news/{id}/publish` | A — `NEWS_PUBLISH` |
| 48 | POST | `/api/v1/news/{id}/unpublish` | A — `NEWS_UNPUBLISH` |
| 49 | GET | `/api/v1/news-categories` | A — `NEWS_CATEGORY_READ` |
| 50 | GET | `/api/v1/news-categories/{id}` | A — `NEWS_CATEGORY_READ_DETAIL` |
| 51 | POST | `/api/v1/news-categories` | A — `NEWS_CATEGORY_CREATE` |
| 52 | PUT | `/api/v1/news-categories/{id}` | A — `NEWS_CATEGORY_UPDATE` |
| 53 | DELETE | `/api/v1/news-categories/{id}` | A — `NEWS_CATEGORY_DELETE` |

**POST `/news`** (ADMIN):

```json
{
  "title": "Vietnam Airlines mở bán vé Tết 2027",
  "content": "<p>Nội dung...</p>",
  "category_ids": [2, 5]
}
```

News tạo ra mặc định `status = DRAFT`. `category_ids` ghi vào `news_category_mapping`; id không tồn tại → `1004`.

**Publish / Unpublish** — tách khỏi `PUT` vì là hành động nghiệp vụ riêng, có permission riêng:

- `POST /news/{id}/publish` → `status = PUBLISHED`, gán `published_at = now()`. Response trả `published_at`.
- `POST /news/{id}/unpublish` → `status = DRAFT`, giữ nguyên `published_at` cũ (NEWS-05: ngừng hiển thị nhưng **không xóa dữ liệu**).

**GET danh sách — public (NEWS-01):** GUEST/CUSTOMER chỉ thấy `status = PUBLISHED`, sắp xếp mặc định `published_at,desc` (BR-LIST-04). ADMIN/STAFF filter được `status=DRAFT`.

Whitelist: keyword `title`; filter `status`, `category_id`; sort `published_at`, `title`.

**News Category:** `GET` danh sách theo UC NEWS-CAT-01 là **ADMIN** (khác News — category không public). `DELETE` là soft delete (`status = INACTIVE`); category còn News tham chiếu → `1006`. `GET /news-categories/{id}` trả kèm danh sách News thuộc category (NEWS-CAT-02).

---

## 3. Phase 2–3 — Sơ bộ (khóa naming)

Chỉ liệt kê để hai repo dùng thống nhất tên; đặc tả chi tiết viết khi tới sprint tương ứng.

### Phase 2 — Flight & Fare

| Method | Path | Quyền dự kiến |
|---|---|---|
| GET | `/api/v1/flights/search` | P — `FLIGHT_SEARCH` |
| GET | `/api/v1/flights` | P — `FLIGHT_READ` |
| GET | `/api/v1/flights/{id}` | P — `FLIGHT_DETAIL_READ` |
| POST/PUT | `/api/v1/flights`, `/api/v1/flights/{id}` | A — `FLIGHT_CREATE` / `FLIGHT_UPDATE` |
| POST | `/api/v1/flights/{id}/activate`, `/deactivate` | A — `FLIGHT_ACTIVATE` / `FLIGHT_DEACTIVATE` |
| GET/POST/PUT/DELETE | `/api/v1/fare-classes` | A — `FARE_CLASS_*` |
| GET/POST/PUT/DELETE | `/api/v1/flight-fares` | A — `FLIGHT_FARE_*` |
| GET/POST/PUT/DELETE | `/api/v1/baggage-options` | A — `BAGGAGE_*` |
| GET | `/api/v1/flights/{id}/seat-map` | A — `SEAT_INVENTORY_READ_DETAIL` |
| PUT | `/api/v1/flights/{id}/seat-inventory` | A — `SEAT_INVENTORY_MANAGE` |
| GET/POST/PUT/DELETE | `/api/v1/fare-rules` | A — `FARE_RULE_*` |

Flight Search nhận `departure_airport_id`, `arrival_airport_id`, `departure_date`, `passengers`, `trip_type` — khớp index `idx_flights_route_departure`.

### Phase 3 — Booking, Payment, E-Ticket

| Method | Path | Quyền dự kiến |
|---|---|---|
| POST | `/api/v1/bookings` | A — `BOOKING_CREATE` |
| GET | `/api/v1/bookings` | A — `BOOKING_READ_SELF` |
| GET | `/api/v1/bookings/{id}` | A — `BOOKING_READ_SELF` |
| POST | `/api/v1/bookings/{id}/cancel` | A — `BOOKING_CANCEL_SELF` |
| POST | `/api/v1/bookings/{id}/passengers` | A — `PASSENGER_CREATE` |
| POST | `/api/v1/bookings/{id}/seats/hold` | A — `BOOKING_CREATE` |
| POST | `/api/v1/bookings/{id}/baggages` | A — `BOOKING_CREATE` |
| POST | `/api/v1/bookings/{id}/voucher` | A — `VOUCHER_APPLY` |
| POST | `/api/v1/bookings/{id}/payments` | A — `PAYMENT_CREATE` |
| GET | `/api/v1/payments/{id}` | A — `PAYMENT_READ_SELF` |
| POST | `/api/v1/payments/callback` | P (gateway callback, xác thực bằng chữ ký) |
| GET | `/api/v1/bookings/{id}/e-tickets` | A — `ETICKET_READ_SELF` |
| GET | `/api/v1/admin/bookings` | A — `BOOKING_READ` (STAFF) |
| GET | `/api/v1/dashboard/operational` | A — `DASHBOARD_READ_OPERATIONAL` |
| GET | `/api/v1/dashboard` | A — `DASHBOARD_READ` |
| GET | `/api/v1/notifications` | A — `NOTIFICATION_READ_SELF` |

**Booking của GUEST: không tồn tại.** GUEST muốn đặt vé phải đăng nhập/đăng ký CUSTOMER (Approved Baseline Decision).

---

## 4. Quyết định thiết kế & điểm mơ hồ đã xử lý

Bốn điểm dưới đây được chốt khi seed RBAC và thiết kế API; ghi lại để hai repo không hiểu lệch.

### 4.1 GUEST không phải một role

GUEST = "người dùng chưa đăng nhập". Trong database **không có** bản ghi role `GUEST`; quyền của GUEST thể hiện bằng `permitAll` ở cấu hình Spring Security cho các endpoint công khai. Vì vậy bảng `roles` chỉ có 3 dòng: `CUSTOMER`, `STAFF`, `ADMIN`.

### 4.2 `FLIGHT_READ` / `FLIGHT_DETAIL_READ` là một key duy nhất

Permission Matrix v2.0 liệt kê hai nhóm "Flight Search" (GUEST public) và "Flight Management" (STAFF/ADMIN). Hai nhóm này dùng **cùng một key** `FLIGHT_READ` / `FLIGHT_DETAIL_READ`, không tách đôi. Khác biệt giữa public và quản lý nằm ở các key **ghi**: `FLIGHT_CREATE`, `FLIGHT_UPDATE`, `FLIGHT_ACTIVATE`, `FLIGHT_DEACTIVATE` (chỉ STAFF/ADMIN). Endpoint `GET /flights` vì thế là `permitAll`, còn các endpoint ghi yêu cầu permission tương ứng.

### 4.3 `FARE_RULE_*` và `SEAT_INVENTORY_*` chưa có bảng riêng

Permission Matrix có hai nhóm key này, nhưng Database Design v0 **không** có bảng `fare_rules` hay `seat_inventory` — nghiệp vụ nằm trong `fare_classes`, `flight_fares`, `flight_seats`, `seat_configurations`. Quyết định: **vẫn seed đủ key** theo matrix đã duyệt (giữ nguyên chuẩn), và **ràng buộc ở tầng service**, không bịa thêm bảng. Khi triển khai Phase 2 sẽ ánh xạ key → thao tác trên các bảng sẵn có.

### 4.4 `ROLE_READ` dùng chung cho cả Role và Permission

Permission Matrix không định nghĩa key `PERMISSION_READ`. RBAC-02 (View Permission, read-only) dùng chung `ROLE_READ`. Nếu sau này cần tách quyền xem permission khỏi quyền xem role, thêm key mới qua migration.

### 4.5 Soft Delete thực hiện bằng `status`

Use Case Phase 1 yêu cầu "Soft Delete" ở 9 use case (USR-05, AIR-04, CITY-04, APT-04, AC-04, PROMO-04, VOU-04, NEWS-06, NEWS-CAT-05). Database Design v0 **không** có cột `is_deleted` / `deleted_at`. Quyết định: soft delete = đặt `status = INACTIVE` (hoặc `DRAFT` với News) — **giữ nguyên bản ghi**, không xóa vật lý, không thêm cột ngoài v0. Hệ quả: mọi truy vấn phải lọc `status` đúng theo BR-LIST-04.

### 4.6 Frontend: Angular Material + Tailwind CSS (đã chốt)

Approved Baseline Decision ghi Frontend = **Angular + Tailwind CSS**, kế hoạch triển khai dùng **Angular Material**. **Quyết định 19/09/2026: dùng cả hai**, đúng pattern kế thừa từ dự án laptopshop — **Angular Material 22** (theme M3 `azure-blue`) cho component, **Tailwind CSS 3.4** cho layout/utility. Cấu hình Tailwind (`tailwind.config.js` với design token primary/success/warning/danger/surface, `postcss.config.js`) lấy nguyên từ laptopshop.

Phân chia trách nhiệm: Material giữ component (form field, button, table, dialog, snackbar) để có sẵn hành vi và accessibility; Tailwind lo bố cục, khoảng cách, responsive, màu trạng thái. Không viết lại component Material bằng Tailwind.

---

## 5. Open items

Các điểm chưa đủ dữ liệu để chốt, cần quyết định trước khi code tới phần liên quan.

| # | Vấn đề | Ảnh hưởng | Đề xuất |
|---|---|---|---|
| 1 | **Nơi lưu token đặt lại mật khẩu** — v0 không có bảng/cột nào cho reset token | Endpoint `/auth/password-reset/*` không triển khai được nếu không chốt | Thêm bảng `password_reset_tokens` (user_id, token_hash, expires_at, used_at) qua migration mới, hoặc dùng JWT ngắn hạn ký riêng cho mục đích reset (không cần bảng) |
| 2 | **Hợp đồng cổng thanh toán** — chưa chốt dùng ZaloPay / VNPay / MoMo | Toàn bộ module Payment Phase 3 | Chờ nghiệp vụ Phase 3; `payments.provider` + `gateway_ref` trong v0 đã đủ chỗ để chứa |
| 3 | **Cơ chế logout/revoke token** — JWT stateless không thu hồi được token đã phát | AUTH-03 | MVP: client xóa token. Nếu cần thu hồi thật → thêm bảng blacklist hoặc dùng refresh token có lưu DB |
| 4 | **Refresh token** — chưa có trong Use Case Phase 1 | Trải nghiệm đăng nhập dài | Hiện dùng access token đơn; bổ sung sau nếu cần |
| 5 | **Quy tắc đặt lại mật khẩu bởi ADMIN** (`POST /users/{id}/reset-password`) | Module User Management | Cần chốt: trả mật khẩu tạm hay gửi email? |
| 6 | **Định dạng `content` của News** — HTML hay Markdown | Endpoint News + hiển thị FE | Cần chốt để sanitize đúng cách (chống XSS) |

---

## 6. Truy vết permission ↔ endpoint

Mọi permission trong Permission Matrix v2.0 đều có chỗ xuất hiện trong tài liệu này, trừ các trường hợp đã giải thích ở mục 4.

| Nhóm permission | Endpoint tương ứng |
|---|---|
| `REGISTER`, `PASSWORD_RESET` | 2.1 — **public**, không cấp cho role nào (GUEST-only) |
| `LOGIN`, `LOGOUT`, `PASSWORD_CHANGE_SELF` | 2.1 |
| `PROFILE_READ_SELF`, `PROFILE_UPDATE_SELF` | 2.2 |
| `USER_*` (5), `ROLE_READ` | 2.3, 2.4 |
| `AIRLINE_*`, `CITY_*`, `AIRPORT_*` | 2.5 |
| `AIRCRAFT_*`, `SEAT_CONFIG_*` | 2.6 |
| `PROMOTION_*` | 2.7 |
| `VOUCHER_*`, `VOUCHER_APPLY` | 2.8 (`VOUCHER_APPLY` dùng ở Phase 3) |
| `NEWS_*`, `NEWS_CATEGORY_*` | 2.9 |
| `FLIGHT_*`, `FARE_CLASS_*`, `FLIGHT_FARE_*`, `BAGGAGE_*`, `FARE_RULE_*`, `SEAT_INVENTORY_*` | 3 — Phase 2 |
| `PASSENGER_*`, `BOOKING_*`, `PAYMENT_*`, `ETICKET_READ_SELF`, `DASHBOARD_*`, `NOTIFICATION_READ_SELF` | 3 — Phase 3 |

---

*Hết tài liệu v0.1 — Phase 1. Cập nhật khi chốt các Open items ở mục 5.*
