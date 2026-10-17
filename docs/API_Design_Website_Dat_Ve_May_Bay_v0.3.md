# API Design — Website Đặt Vé Máy Bay Nội Địa

**Phiên bản:** v0.3 (Phase 1 — đã triển khai)
**Ngày:** 13/10/2026
**Trạng thái:** **Đã triển khai** — backend Phase 1 hoàn thành (cụm 0–7), 45 unit test pass, verify E2E bằng curl
**Căn cứ:** Database Design **v0.4** · Permission Matrix v2.0 (3 Phase) · Phase 1 Use Case Specification v2.3 · Jira Plan Sprint 2–6 (Revised)
**Thay thế:** v0.2 (11/10/2026). Lý do bump: ghi nhận các quyết định đã chốt khi triển khai — refresh token + Redis, `permissions.module`, `content` News = HTML sanitize jsoup, reset-password token ở Redis, reset password bởi ADMIN trả mật khẩu tạm, và 5 cột ảnh/mô tả/nhãn bổ sung (xem mục 5).

---

## 0. Phạm vi tài liệu

Tài liệu chốt **hợp đồng API (contract)** giữa Backend và Frontend cho **Phase 1**, làm chuẩn để hai repo (`domestic-flight-booking-web-backend`, `domestic-flight-booking-web-frontend`) code song song mà không lệch.

- **Phase 1 (đặc tả đầy đủ):** Authentication & Account, Profile, User Management, RBAC, Master Data (Airline / City / Airport / Aircraft / Seat Map), Promotion, Voucher (+ Claim voucher cho CUSTOMER), News.
- **Phase 2–3:** liệt kê sơ bộ để khóa naming, không đi sâu (bao gồm Wallet/Ví khách hàng).
- Tài liệu **không** mô tả code triển khai (service/controller) — chỉ mô tả bề mặt API.
- **Chưa đưa vào tài liệu (chờ triển khai sau):** Notifications (bảng `notifications` chưa tạo — xem DB v0.4).

---

## 1. Quy ước chung

### 1.1 Base URL & Versioning

| Mục | Giá trị |
|---|---|
| Base path | `/api` |
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

`BUSINESS_RULE_VIOLATION` dùng cho các ràng buộc đã ghi trong Database Design **v0.4** (ví dụ `end_date >= start_date`, `claim_limit > 0`, `departure_airport_id <> arrival_airport_id`, tổng số ghế sơ đồ = `seat_capacity`, không chồng khoảng hiệu lực giá vé). Message phải nêu rõ quy tắc bị vi phạm.

### 1.5 Xác thực & phân quyền

- Cơ chế: **JWT Bearer** (`Authorization: Bearer <access_token>`), Spring Security 6 OAuth2 Resource Server.
- Access token chứa `sub` (user id), `email`, và danh sách quyền dưới dạng claim `permissions` (danh sách permission_key) — cho phép kiểm tra quyền không cần truy vấn DB mỗi request.
- **GUEST = chưa đăng nhập.** GUEST không phải một role trong database; quyền của GUEST thể hiện bằng `permitAll` ở tầng Spring Security cho các endpoint công khai. Không seed role GUEST (xem mục 4).
- **Role cố định MVP:** `CUSTOMER`, `STAFF`, `ADMIN`. Admin gán role cho user; không tạo role mới qua API (BR-RBAC-03).
- Endpoint công khai (không cần token): đăng ký, đăng nhập, quên/đặt lại mật khẩu, và toàn bộ **GET** dữ liệu công khai (airline, city, airport, promotion, news, news category, flight search).
- Endpoint `SELF` (ví dụ `PROFILE_READ_SELF`, `BOOKING_READ_SELF`, `WALLET_READ_SELF`): hệ thống **luôn** lấy user từ token, không nhận `user_id` từ client — tránh truy cập chéo tài khoản.
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
| 1 | POST | `/api/auth/register` | P (GUEST-only) | AUTH-01 |
| 2 | POST | `/api/auth/login` | P | AUTH-02 |
| 3 | POST | `/api/auth/logout` | A — `LOGOUT` | AUTH-03 |
| 4 | POST | `/api/auth/password-reset/request` | P | AUTH-04 |
| 5 | POST | `/api/auth/password-reset/confirm` | P | AUTH-04 |
| 6 | POST | `/api/auth/change-password` | A — `PASSWORD_CHANGE_SELF` | AUTH-05 |

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
| 7 | GET | `/api/profile` | A — `PROFILE_READ_SELF` |
| 8 | PUT | `/api/profile` | A — `PROFILE_UPDATE_SELF` |

`GET /profile` trả về chính user đang đăng nhập (cấu trúc như `data` của register).

`PUT /profile` — chỉ cho phép sửa `full_name`, `phone`, `avatar_url`:

```json
{ "full_name": "Nguyễn C. T. Bình", "phone": "0907654321", "avatar_url": "https://res.cloudinary.com/..." }
```

`email`, `status`, `roles` **không** sửa được qua endpoint này (email đổi qua luồng riêng; status/role chỉ ADMIN qua module User). Gửi field ngoài whitelist → bỏ qua, không lỗi.

**Upload avatar:** `POST /api/profile/avatar` (multipart/form-data, field `file`) → trả `{ "avatar_url": "..." }`. Ảnh lưu qua Cloudinary (đã cấu hình trong `application-dev.properties`).

### 2.3 User Management (ADMIN)

Bảng `users`, `user_roles`. Nguồn: USR-01 → USR-05.

| # | Method | Path | Quyền |
|---|---|---|---|
| 9 | GET | `/api/users` | A — `USER_READ` |
| 10 | GET | `/api/users/{id}` | A — `USER_READ_DETAIL` |
| 11 | POST | `/api/users` | A — `USER_CREATE` |
| 12 | PUT | `/api/users/{id}` | A — `USER_UPDATE` |
| 13 | DELETE | `/api/users/{id}` | A — `USER_DELETE` |

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
| 14 | GET | `/api/roles` | A — `ROLE_READ` |
| 15 | GET | `/api/permissions` | A — `ROLE_READ` |

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
| 16 | GET | `/api/airlines` | P — `AIRLINE_READ` |
| 17 | GET | `/api/airlines/{id}` | P — `AIRLINE_READ_DETAIL` |
| 18 | POST | `/api/airlines` | A — `AIRLINE_CREATE` |
| 19 | PUT | `/api/airlines/{id}` | A — `AIRLINE_UPDATE` |
| 20 | DELETE | `/api/airlines/{id}` | A — `AIRLINE_DELETE` |

| Module | Base path | Permission prefix | Bảng |
|---|---|---|---|
| Airline | `/api/airlines` | `AIRLINE_` | `airlines` |
| City | `/api/cities` | `CITY_` | `cities` |
| Airport | `/api/airports` | `AIRPORT_` | `airports` |

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

### 2.6 Aircraft & Seat Map (sơ đồ ghế)

Bảng `aircrafts`, `seat_types`, `aircraft_seats`. Nguồn: AC-01→05, SEAT-01, SEAT-02.

| # | Method | Path | Quyền |
|---|---|---|---|
| 21 | GET | `/api/aircrafts` | A — `AIRCRAFT_READ` |
| 22 | GET | `/api/aircrafts/{id}` | A — `AIRCRAFT_READ_DETAIL` |
| 23 | POST | `/api/aircrafts` | A — `AIRCRAFT_CREATE` |
| 24 | PUT | `/api/aircrafts/{id}` | A — `AIRCRAFT_UPDATE` |
| 25 | DELETE | `/api/aircrafts/{id}` | A — `AIRCRAFT_DELETE` |
| 26 | GET | `/api/seat-types` | A — `SEAT_CONFIG_READ` |
| 27 | GET | `/api/seat-types/{id}` | A — `SEAT_CONFIG_READ_DETAIL` |
| 28 | POST | `/api/seat-types` | A — `SEAT_CONFIG_CREATE` |
| 29 | PUT | `/api/seat-types/{id}` | A — `SEAT_CONFIG_UPDATE` |
| 30 | DELETE | `/api/seat-types/{id}` | A — `SEAT_CONFIG_DELETE` |
| 31 | GET | `/api/aircrafts/{id}/seat-map` | A — `SEAT_CONFIG_READ_DETAIL` |
| 32 | PUT | `/api/aircrafts/{id}/seat-map` | A — `SEAT_CONFIG_UPDATE` |

**Khác biệt so với Master Data ở 2.5: Aircraft KHÔNG public** — chỉ `STAFF`/`ADMIN` (AC-01, AC-05). GUEST/CUSTOMER không truy cập được.

**POST `/aircrafts`**:

```json
{ "aircraft_code": "VN-A350-01", "model": "Airbus A350-900", "manufacturer": "Airbus", "seat_capacity": 305 }
```

`seat_capacity > 0` → vi phạm `1006`. `aircraft_code` UNIQUE → `1005`.

**Seat Type (v0.4 — CRUD đầy đủ):** danh mục loại ghế, nay CRUD bình thường như master data khác (thêm `is_active`, soft delete `is_deleted`/`deleted_at`).

```json
{
  "seat_type_code": "ECO_WIN",
  "seat_type_name": "Phổ thông - cửa sổ",
  "seat_class": "ECONOMY",
  "is_active": true
}
```

`seat_class` ∈ `ECONOMY` | `BUSINESS` | `FIRST`. `seat_type_code` UNIQUE (chỉ tính bản ghi chưa xóa mềm) → `1005`. **DELETE** là soft delete; loại ghế đang được `aircraft_seats` dùng → từ chối `1006` (chỉ cho tắt `is_active`). `PUT` cập nhật **và** toggle `is_active`.

**Seat Map — sơ đồ ghế của một máy bay (SEAT-01, mục 5.7 DB v0.4):** thao tác theo **cả sơ đồ**, không CRUD từng ghế. Mỗi dòng là **một ghế vật lý** (`seat_row`, `seat_column`, `seat_type_id`); tất cả dòng của một máy bay ghép thành **một** sơ đồ duy nhất.

`PUT /aircrafts/{id}/seat-map` nhận **toàn bộ** danh sách ghế và **thay thế** sơ đồ hiện có trong một transaction:

```json
{
  "seats": [
    { "seat_row": 1, "seat_column": "A", "seat_type_id": 3 },
    { "seat_row": 1, "seat_column": "B", "seat_type_id": 3 },
    { "seat_row": 12, "seat_column": "A", "seat_type_id": 1 }
  ]
}
```

Ràng buộc khi lưu → `1006` nếu vi phạm:

- Không có hai ghế trùng `(seat_row, seat_column)` trong payload (UNIQUE `(aircraft_id, seat_row, seat_column)`).
- `seat_type_id` phải tồn tại **và** `is_active = true` → không tồn tại trả `1004`.
- **Tổng số ghế = `aircrafts.seat_capacity`** (v0.4 yêu cầu khớp chính xác, không chỉ "không vượt").
- Máy bay **chưa có** dòng `flight_seats` nào trỏ tới `aircraft_seats` của nó. Nếu đã có → từ chối `1006`: sửa sơ đồ sẽ làm đổi ghế của chuyến đang bán. Muốn đổi sơ đồ sau khi đã khai thác thì tạo phi cơ mới.

Gợi ý UI (mục 5.7): ADMIN khai báo theo **khối** (hàng từ–đến, danh sách cột, loại ghế) → hệ thống sinh lưới ghế để **xem trước**, chỉnh lẻ từng ô, rồi bấm Lưu để gửi cả sơ đồ.

`GET /aircrafts/{id}/seat-map` trả sơ đồ hiện tại kèm loại ghế:

```json
{
  "aircraft_id": 1,
  "seat_capacity": 305,
  "configured_seats": 3,
  "seats": [
    { "id": 10, "seat_row": 1, "seat_column": "A", "seat_type": { "id": 3, "seat_type_code": "BUS", "seat_type_name": "Ghế thương gia", "seat_class": "BUSINESS" } }
  ]
}
```

**Lưu ý:** `flight_seats` (tồn ghế của một chuyến cụ thể, mục 3 Phase 2) là bảng **khác** — sinh từ `aircraft_seats` khi tạo Seat Inventory cho chuyến. Đừng lẫn `GET /aircrafts/{id}/seat-map` (sơ đồ máy bay) với `GET /flights/{id}/seat-map` (tồn ghế chuyến).

### 2.7 Promotion

Bảng `promotions`, `promotion_scopes`, `promotion_excluded_flights`. Nguồn: PROMO-01 → PROMO-05.

| # | Method | Path | Quyền |
|---|---|---|---|
| 33 | GET | `/api/promotions` | P — `PROMOTION_READ` |
| 34 | GET | `/api/promotions/{id}` | P — `PROMOTION_READ_DETAIL` |
| 35 | POST | `/api/promotions` | A — `PROMOTION_CREATE` |
| 36 | PUT | `/api/promotions/{id}` | A — `PROMOTION_UPDATE` |
| 37 | DELETE | `/api/promotions/{id}` | A — `PROMOTION_DELETE` |

**POST `/promotions`** (v0.4 — thêm trần giảm, giá tối thiểu, loại hành trình, phạm vi, loại trừ):

```json
{
  "promotion_code": "HE2026",
  "promotion_name": "Khuyến mãi hè 2026",
  "discount_type": "PERCENT",
  "discount_value": 15.00,
  "max_discount_amount": 150000.00,
  "min_fare_amount": 500000.00,
  "applicable_trip_type": "ANY",
  "start_date": "2026-06-01",
  "end_date": "2026-08-31",
  "is_active": true,
  "scopes": [
    { "target_type": "AIRLINE", "airline_id": 1 },
    { "target_type": "ROUTE", "departure_airport_id": 1, "arrival_airport_id": 3 }
  ],
  "excluded_flight_ids": [101, 102]
}
```

| Field | Ghi chú |
|---|---|
| `discount_type` | `PERCENT` \| `AMOUNT` |
| `max_discount_amount` | Trần giảm cho **mỗi dòng vé**; chỉ có ý nghĩa với `PERCENT`, `null` = không trần |
| `min_fare_amount` | Giá dòng vé tối thiểu (đã gồm thuế phí) mới được giảm; `null` = không yêu cầu |
| `applicable_trip_type` | `ANY` \| `ONE_WAY` \| `ROUND_TRIP`, mặc định `ANY`. Là điều kiện của **cả booking**, so với `bookings.trip_type` |
| `scopes[]` | Phạm vi áp dụng (`target_type` ∈ `AIRLINE`/`FARE_CLASS`/`ROUTE`/`FLIGHT`). **Không có dòng nào = áp cho tất cả chuyến bay.** Nhiều dòng là điều kiện **HOẶC**. ROUTE **có hướng** — muốn áp cả hai chiều phải thêm dòng ROUTE đảo chiều |
| `excluded_flight_ids[]` | Chuyến bay bị **loại trừ**. Loại trừ **luôn thắng** phạm vi |

Ràng buộc từ DB v0.4 → vi phạm `1006`: `end_date >= start_date`, `discount_value > 0` (PERCENT thì ≤ 100), `max_discount_amount > 0`, `min_fare_amount >= 0`. `promotion_code` UNIQUE → `1005`. Scope sai kiểu (vd `target_type=AIRLINE` mà thiếu `airline_id`) → `1001`/`1006`.

**GET danh sách — public:** GUEST/CUSTOMER chỉ thấy promotion đang hiệu lực (`is_active = true` **và** `start_date <= hôm nay <= end_date`). ADMIN/STAFF thấy tất cả, filter được `is_active`.

Whitelist: keyword `promotion_code`, `promotion_name`; filter `is_active`, `discount_type`, `applicable_trip_type`; sort `start_date`, `end_date`, `promotion_code`.

**PUT** — cập nhật **và** toggle `is_active`, thay toàn bộ `scopes`/`excluded_flight_ids`. **DELETE** — soft delete (`is_deleted = true`, `deleted_at = now()`).

**Lưu ý Phase 1:** Promotion ở Phase 1 chỉ **quản lý định nghĩa**. Việc tính giá giảm hiển thị lúc tìm chuyến thuộc Phase 2–3 (mục 5.3 DB v0.4).

### 2.8 Voucher (mô hình ví — khách không nhập mã)

Bảng `vouchers`, `voucher_scopes`, `voucher_excluded_flights`, `user_vouchers`. Nguồn: VOU-01 → VOU-05 (+ UC Claim Voucher mới).

| # | Method | Path | Quyền |
|---|---|---|---|
| 38 | GET | `/api/vouchers` | A — `VOUCHER_READ` |
| 39 | GET | `/api/vouchers/{id}` | A — `VOUCHER_READ_DETAIL` |
| 40 | POST | `/api/vouchers` | A — `VOUCHER_CREATE` |
| 41 | PUT | `/api/vouchers/{id}` | A — `VOUCHER_UPDATE` |
| 42 | DELETE | `/api/vouchers/{id}` | A — `VOUCHER_DELETE` |
| 43 | POST | `/api/vouchers/{id}/assign` | A — `VOUCHER_UPDATE` |
| 44 | GET | `/api/voucher-store` | A — `VOUCHER_CLAIM` (Kho voucher — GLOBAL đang phát) |
| 45 | POST | `/api/vouchers/{id}/claim` | A — `VOUCHER_CLAIM` |
| 46 | GET | `/api/my-vouchers` | A — `VOUCHER_READ` (SELF) |
| 47 | GET | `/api/my-vouchers/{id}` | A — `VOUCHER_READ_DETAIL` (SELF) |

**Đổi mô hình so với v0.1 (quan trọng cho FE):** voucher **không dùng mã gõ tay**. Khách **nhận** voucher vào ví (`user_vouchers`) rồi **chọn từ ví** khi thanh toán. Vì vậy:
- `voucher_code` chỉ là mã **nội bộ** để ADMIN tra cứu, khách không nhập.
- Bỏ `voucher_type`/`value_amount` → dùng `discount_type`/`discount_value` (giống promotion).
- Bỏ `per_user_limit` (mỗi khách giữ mỗi voucher một lần — UNIQUE `(user_id, voucher_id)`).
- `usage_limit` → `claim_limit` (đếm lượt **nhận**); `scope` → `distribution_type`.
- Thêm `voucher_name`, `description`, `min_order_amount`, `max_discount_amount`, `applicable_trip_type`, phạm vi + loại trừ.

**POST `/vouchers`** (ADMIN):

```json
{
  "voucher_code": "SUMMER50K",
  "voucher_name": "Giảm 50K mùa hè",
  "description": "Áp dụng cho mọi chuyến nội địa, đơn từ 500K",
  "distribution_type": "GLOBAL",
  "discount_type": "AMOUNT",
  "discount_value": 50000.00,
  "max_discount_amount": null,
  "min_order_amount": 500000.00,
  "applicable_trip_type": "ANY",
  "start_date": "2026-06-01",
  "end_date": "2026-12-31",
  "claim_limit": 1000,
  "is_active": true,
  "scopes": [
    { "target_type": "AIRLINE", "airline_id": 1 }
  ],
  "excluded_flight_ids": []
}
```

| Field | Ghi chú |
|---|---|
| `distribution_type` | `GLOBAL` (toàn hệ thống, khách bấm Nhận) \| `ASSIGNED` (ADMIN tặng riêng) |
| `discount_type` | `PERCENT` \| `AMOUNT` |
| `max_discount_amount` | Trần giảm cho **cả booking** (voucher tính trên cả booking, khác promotion tính theo dòng vé) |
| `min_order_amount` | Tổng giá vé tối thiểu (sau khuyến mãi, chỉ tính dòng ADULT/CHILD khớp phạm vi) mới dùng được |
| `applicable_trip_type` | `ANY` \| `ONE_WAY` \| `ROUND_TRIP`, mặc định `ANY` |
| `claim_limit` | Tổng số lượt được **nhận**, chỉ áp cho `GLOBAL`; `null` = không giới hạn, nếu có phải `> 0` → `1006` |
| `scopes[]` / `excluded_flight_ids[]` | Giống promotion (2.7); `voucher_excluded_flights` là bảng **mới** ở v0.4 |

Voucher **không giảm cho hành khách INFANT** (cùng quy tắc promotion).

**`GET /voucher-store`** (CUSTOMER, mới) — Kho voucher: các voucher `distribution_type = GLOBAL`, đang hiệu lực, còn lượt nhận (`claim_limit IS NULL OR claimed_count < claim_limit`) và khách **chưa** nhận. Trả về kèm cờ `claimed` để FE hiển thị nút Nhận / Đã nhận.

**`POST /vouchers/{id}/claim`** (CUSTOMER, `VOUCHER_CLAIM`) — khách bấm Nhận một voucher GLOBAL:

```json
{ "code": 1000, "message": "Success", "data": { "user_voucher_id": 88, "status": "AVAILABLE" } }
```

Backend chạy `UPDATE vouchers SET claimed_count = claimed_count + 1 WHERE id = :id AND (claim_limit IS NULL OR claimed_count < claim_limit)`; **1 dòng bị ảnh hưởng** mới tạo `user_vouchers` (`source = CLAIMED`). Hết lượt → `1006`. Khách đã nhận voucher này → `1005` (UNIQUE `(user_id, voucher_id)`).

**`GET /my-vouchers`** (CUSTOMER, VOU-01/VOU-05) — voucher **thuộc tài khoản** trong token, đọc từ `user_vouchers`:

```json
{
  "code": 1000,
  "message": "Success",
  "data": {
    "items": [
      {
        "id": 7, "status": "AVAILABLE", "source": "CLAIMED",
        "received_at": "2026-09-01T02:00:00Z", "expires_at": "2026-12-31T17:00:00Z", "used_at": null,
        "voucher": { "id": 3, "voucher_code": "SUMMER50K", "voucher_name": "Giảm 50K mùa hè", "discount_type": "AMOUNT", "discount_value": 50000.00, "min_order_amount": 500000.00, "applicable_trip_type": "ANY", "start_date": "2026-06-01", "end_date": "2026-12-31" }
      }
    ],
    "pagination": { "page": 1, "size": 20, "total_items": 1, "total_pages": 1 }
  }
}
```

`status` ∈ `AVAILABLE` | `RESERVED` (đang gắn với booking chờ thanh toán) | `USED` | `EXPIRED`. Endpoint SELF **không** nhận `user_id` từ client (xem 1.5).

**`POST /vouchers/{id}/assign`** (ADMIN) — tặng voucher `ASSIGNED` cho user:

```json
{ "user_ids": [12, 15, 20] }
```

Ràng buộc UNIQUE `(user_id, voucher_id)` → cấp trùng `1005`. Voucher `distribution_type = GLOBAL` không assign → `1006`.

**Lưu ý Phase 1:** quản lý định nghĩa voucher + Claim + xem ví thuộc Phase 1. **Áp voucher vào booking** (`VOUCHER_APPLY`) thuộc Phase 3 (mục 5.4 DB v0.4).

### 2.9 News & News Category

Bảng `news_items`, `news_categories`, `news_category_mapping`. Nguồn: NEWS-01→07, NEWS-CAT-01→05.

| # | Method | Path | Quyền |
|---|---|---|---|
| 48 | GET | `/api/news` | P — `NEWS_READ` |
| 49 | GET | `/api/news/{id}` | P — `NEWS_READ_DETAIL` |
| 50 | POST | `/api/news` | A — `NEWS_CREATE` |
| 51 | PUT | `/api/news/{id}` | A — `NEWS_UPDATE` |
| 52 | DELETE | `/api/news/{id}` | A — `NEWS_DELETE` |
| 53 | POST | `/api/news/{id}/publish` | A — `NEWS_PUBLISH` |
| 54 | POST | `/api/news/{id}/unpublish` | A — `NEWS_UNPUBLISH` |
| 55 | GET | `/api/news-categories` | A — `NEWS_CATEGORY_READ` |
| 56 | GET | `/api/news-categories/{id}` | A — `NEWS_CATEGORY_READ_DETAIL` |
| 57 | POST | `/api/news-categories` | A — `NEWS_CATEGORY_CREATE` |
| 58 | PUT | `/api/news-categories/{id}` | A — `NEWS_CATEGORY_UPDATE` |
| 59 | DELETE | `/api/news-categories/{id}` | A — `NEWS_CATEGORY_DELETE` |

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
| GET | `/api/flights/search` | P — `FLIGHT_SEARCH` |
| GET | `/api/flights` | P — `FLIGHT_READ` |
| GET | `/api/flights/{id}` | P — `FLIGHT_DETAIL_READ` |
| POST/PUT | `/api/flights`, `/api/flights/{id}` | A — `FLIGHT_CREATE` / `FLIGHT_UPDATE` |
| POST | `/api/flights/{id}/activate`, `/deactivate` | A — `FLIGHT_ACTIVATE` / `FLIGHT_DEACTIVATE` |
| GET/POST/PUT/DELETE | `/api/fare-classes` | A — `FARE_CLASS_*` |
| GET/POST/PUT/DELETE | `/api/flight-fares` | A — `FLIGHT_FARE_*` |
| GET/POST/PUT/DELETE | `/api/baggage-options` | A — `BAGGAGE_*` |
| GET | `/api/flights/{id}/seat-map` | A — `SEAT_INVENTORY_READ_DETAIL` |
| PUT | `/api/flights/{id}/seat-inventory` | A — `SEAT_INVENTORY_MANAGE` |
| GET/POST/PUT/DELETE | `/api/fare-rules` | A — `FARE_RULE_*` |

Flight Search nhận `departure_airport_id`, `arrival_airport_id`, `departure_date`, `passengers`, `trip_type` — khớp index `idx_flights_route_departure`. **Bắt buộc gửi `trip_type`** để backend áp đúng `applicable_trip_type` của promotion/voucher và hiển thị giá giảm chính xác.

**Giá vé trả sẵn 3 loại hành khách (chốt 2026-10-11):** `flight_fares` chỉ lưu giá **người lớn** (`base_fare`, `tax_fee`, khoảng hiệu lực `effective_from`/`effective_to`). Backend tính sẵn và trả **cả ba** giá để FE chỉ hiển thị, không tự nhân tỉ lệ:

```json
{
  "flight_id": 55, "fare_class_id": 2,
  "base_fare": 1000000.00, "tax_fee": 200000.00,
  "adult_price": 1200000.00,
  "child_price": 900000.00,
  "infant_price": 120000.00,
  "currency": "VND",
  "effective_from": "2026-06-01T00:00:00Z", "effective_to": null
}
```

Tỉ lệ cấu hình trong `application.yml` (`app.pricing.child-fare-percent: 75`, `infant-fare-percent: 10`), **không có bảng riêng**, chung cho mọi hãng. Không cho hai dòng chồng khoảng hiệu lực cùng `(flight_id, fare_class_id)` → `1006` (ràng buộc `EXCLUDE USING gist` với `btree_gist` ở tầng DB).

**Flight bổ sung khi cầu cao (mục 5.1 DB v0.4):** `POST /flights` nhận `parent_flight_id` (chuyến bổ sung cùng tuyến/giờ, khác `flight_number`). Chỉ chọn được Aircraft **đã có sơ đồ ghế đủ `seat_capacity`** → nếu chưa đủ, `1006`. Không cho đổi `aircraft_id` của chuyến đã có Booking.

**FareRule:** mỗi loại vé gắn quy định hoàn riêng — `is_refundable`, `refund_min_hours_before_departure`, `refund_percent` (mục 5.5 DB v0.4).

### Phase 3 — Booking, Payment, E-Ticket, Wallet

| Method | Path | Quyền dự kiến |
|---|---|---|
| POST | `/api/bookings` | A — `BOOKING_CREATE` |
| GET | `/api/bookings` | A — `BOOKING_READ_SELF` |
| GET | `/api/bookings/{id}` | A — `BOOKING_READ_SELF` |
| POST | `/api/bookings/{id}/cancel` | A — `BOOKING_CANCEL_SELF` |
| POST | `/api/bookings/{id}/passengers` | A — `PASSENGER_CREATE` |
| POST | `/api/bookings/{id}/seats/hold` | A — `BOOKING_CREATE` |
| POST | `/api/bookings/{id}/baggages` | A — `BOOKING_CREATE` |
| POST | `/api/bookings/{id}/voucher` | A — `VOUCHER_APPLY` |
| POST | `/api/bookings/{id}/payments` | A — `PAYMENT_CREATE` |
| GET | `/api/payments/{id}` | A — `PAYMENT_READ_SELF` |
| POST | `/api/payments/callback` | P (gateway callback, xác thực bằng chữ ký) |
| GET | `/api/bookings/{id}/e-tickets` | A — `ETICKET_READ_SELF` |
| GET | `/api/wallet` | A — `WALLET_READ_SELF` |
| GET | `/api/wallet/transactions` | A — `WALLET_READ_SELF` |
| GET | `/api/admin/bookings` | A — `BOOKING_READ` (STAFF) |
| GET | `/api/dashboard/operational` | A — `DASHBOARD_READ_OPERATIONAL` |
| GET | `/api/dashboard` | A — `DASHBOARD_READ` |

**`POST /bookings/{id}/voucher`** — **chọn voucher từ ví**, không nhập mã. Body nhận `user_voucher_id` (id dòng `user_vouchers` do hệ thống trả về), **không** nhận `voucher_code` — nên không thể đoán mã để thử. Voucher chuyển `AVAILABLE → RESERVED` và gắn `booking_id`.

**Wallet (Ví khách hàng, mục 5.5 DB v0.4):** khi hủy vé CONFIRMED, tiền hoàn (`refund_amount`) cộng vào `wallets.balance` và ghi một dòng `wallet_transactions` (`REFUND_CREDIT`). `GET /wallet` trả số dư; `GET /wallet/transactions` trả lịch sử. **Chưa** cho dùng số dư ví để thanh toán booking mới (ngoài phạm vi giai đoạn này).

**Booking của GUEST: không tồn tại.** GUEST muốn đặt vé phải đăng nhập/đăng ký CUSTOMER (Approved Baseline Decision).

**Chưa đưa vào tài liệu:** `GET /notifications` — bảng `notifications` chưa triển khai giai đoạn này (xem DB v0.4), sẽ bổ sung khi làm chức năng thông báo.

---

## 4. Quyết định thiết kế & điểm mơ hồ đã xử lý

Bốn điểm dưới đây được chốt khi seed RBAC và thiết kế API; ghi lại để hai repo không hiểu lệch.

### 4.1 GUEST không phải một role

GUEST = "người dùng chưa đăng nhập". Trong database **không có** bản ghi role `GUEST`; quyền của GUEST thể hiện bằng `permitAll` ở cấu hình Spring Security cho các endpoint công khai. Vì vậy bảng `roles` chỉ có 3 dòng: `CUSTOMER`, `STAFF`, `ADMIN`.

### 4.2 `FLIGHT_READ` / `FLIGHT_DETAIL_READ` là một key duy nhất

Permission Matrix v2.0 liệt kê hai nhóm "Flight Search" (GUEST public) và "Flight Management" (STAFF/ADMIN). Hai nhóm này dùng **cùng một key** `FLIGHT_READ` / `FLIGHT_DETAIL_READ`, không tách đôi. Khác biệt giữa public và quản lý nằm ở các key **ghi**: `FLIGHT_CREATE`, `FLIGHT_UPDATE`, `FLIGHT_ACTIVATE`, `FLIGHT_DEACTIVATE` (chỉ STAFF/ADMIN). Endpoint `GET /flights` vì thế là `permitAll`, còn các endpoint ghi yêu cầu permission tương ứng.

### 4.3 `FARE_RULE_*` và `SEAT_INVENTORY_*`

Permission Matrix có hai nhóm key này. **Từ DB v0.4, `fare_rules` đã là bảng riêng** (quy định hoàn vé theo loại vé: `is_refundable`, `refund_min_hours_before_departure`, `refund_percent`) → `FARE_RULE_*` ánh xạ trực tiếp lên bảng này. `SEAT_INVENTORY_*` vẫn không có bảng riêng — nghiệp vụ nằm ở `flight_seats` (tồn ghế chuyến) và `aircraft_seats` (sơ đồ máy bay); **vẫn seed đủ key** theo matrix đã duyệt và **ràng buộc ở tầng service**.

### 4.4 `ROLE_READ` dùng chung cho cả Role và Permission

Permission Matrix không định nghĩa key `PERMISSION_READ`. RBAC-02 (View Permission, read-only) dùng chung `ROLE_READ`. Nếu sau này cần tách quyền xem permission khỏi quyền xem role, thêm key mới qua migration.

### 4.5 Soft Delete — v0.4 tách `is_active` và `is_deleted`

Use Case Phase 1 yêu cầu "Soft Delete". **v0.1 quy ước soft delete = `status = INACTIVE`**; **DB v0.4 tách hai khái niệm**:
- `is_active` — bật/tắt sử dụng (thay cho `status = INACTIVE`).
- `is_deleted` + `deleted_at` — xóa mềm, vẫn giữ lịch sử.

Vì vậy các endpoint `DELETE` ở Phase 1 nay là **soft delete thật** (`is_deleted = true`), `PUT` toggle `is_active`. Unique của cột mã (email, airline_code, `seat_type_code`, `promotion_code`, `voucher_code`) dùng **partial unique index** `WHERE is_deleted = false` → bản ghi đã xóa mềm không chặn việc tạo mã trùng. Mọi truy vấn phải lọc `is_deleted = false` (BR-LIST-04).

### 4.6 Frontend: Angular Material + Tailwind CSS (đã chốt)

Approved Baseline Decision ghi Frontend = **Angular + Tailwind CSS**, kế hoạch triển khai dùng **Angular Material**. **Quyết định 19/09/2026: dùng cả hai**, đúng pattern kế thừa từ dự án laptopshop — **Angular Material 22** (theme M3 `azure-blue`) cho component, **Tailwind CSS 3.4** cho layout/utility. Cấu hình Tailwind (`tailwind.config.js` với design token primary/success/warning/danger/surface, `postcss.config.js`) lấy nguyên từ laptopshop.

Phân chia trách nhiệm: Material giữ component (form field, button, table, dialog, snackbar) để có sẵn hành vi và accessibility; Tailwind lo bố cục, khoảng cách, responsive, màu trạng thái. Không viết lại component Material bằng Tailwind.

---

## 5. Open items — ĐÃ CHỐT khi triển khai (13/10/2026)

Sáu điểm để ngỏ ở v0.2 nay đã chốt và **triển khai xong** ở backend Phase 1:

| # | Vấn đề | Quyết định đã chốt | Nơi triển khai |
|---|---|---|---|
| 1 | Nơi lưu token đặt lại mật khẩu | **Redis, TTL 15 phút** (`PASSWORD_RESET_TOKEN`). Không cần migration; Redis restart thì token mất, khách yêu cầu lại | `entity/redis/PasswordResetToken` + `AuthService.requestPasswordReset` |
| 2 | Hợp đồng cổng thanh toán | **Chưa chốt** — thuộc Phase 3. Cột `payments.gateway_name`/`gateway_transaction_id` (v0.4) đã đủ chỗ | — |
| 3 | Cơ chế logout/revoke token | **Blacklist `jti` ở Redis** (TTL = thời gian còn lại) + thu hồi refresh token. Token cũ bị từ chối ngay ở request kế tiếp | `entity/redis/InvalidatedToken` + `CustomJwtDecoder` |
| 4 | Refresh token | **Làm thật**: whitelist ở Redis, xoay vòng khi refresh, giữ hạn tuyệt đối của phiên | `entity/redis/RefreshToken` + `AuthService.refresh` |
| 5 | Reset password bởi ADMIN | **Trả mật khẩu tạm** trong response (không có mail server) | `POST /users/{id}/reset-password` → `{temporary_password}` |
| 6 | Định dạng `content` của News | **HTML + sanitize jsoup** trước khi lưu (Safelist.relaxed, chặn script/iframe/event handler) | `common/HtmlSanitizer` + `NewsService` |

**Ngoài 6 open item trên, khi triển khai phát sinh 3 bổ sung (đã ghi ở `docs/De_xuat_bo_sung_DB_v0.5_truong_anh.md` mục 7, migration V10):**

- `permissions.module` — v0.2 mục 2.4 đã hứa filter `?module=` và trả `module` trong response nhưng schema chưa có cột. Đã thêm `VARCHAR(30)` + backfill.
- Seed `VOUCHER_CLAIM` + `WALLET_READ_SELF` cho CUSTOMER (DB v0.4 A.3 yêu cầu, V2 seed thiếu).
- 5 cột ảnh/mô tả/nhãn: `news_items.thumbnail_url`, `cities.image_url`, `promotions.image_url`/`description`/`badge_label`.

**Khác biệt đã chốt khi triển khai (ghi để FE biết):**

- **`GET` Airline/City/Airport/News/Promotion (cả list lẫn `{id}`) là PUBLIC** — không cần token, đúng ký hiệu **P** ở mục 2.5/2.7/2.9.
- **`POST /auth/refresh`** là endpoint bổ sung ngoài bảng 2.1 (nhận `{refresh_token}`, trả cặp token mới).
- `POST /auth/password-reset/request` trả thêm `data.reset_token` để test luồng (khi có mail server sẽ bỏ).
- Danh sách dùng sort whitelist theo **tên field snake_case** (`created_at,desc`); field ngoài whitelist bị bỏ qua, dùng sort mặc định (BR-LIST-03).

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
| `VOUCHER_*`, `VOUCHER_APPLY`, `VOUCHER_CLAIM` | 2.8 (`VOUCHER_APPLY` dùng ở Phase 3) |
| `NEWS_*`, `NEWS_CATEGORY_*` | 2.9 |
| `FLIGHT_*`, `FARE_CLASS_*`, `FLIGHT_FARE_*`, `BAGGAGE_*`, `FARE_RULE_*`, `SEAT_INVENTORY_*` | 3 — Phase 2 |
| `PASSENGER_*`, `BOOKING_*`, `PAYMENT_*`, `ETICKET_READ_SELF`, `WALLET_READ_SELF`, `DASHBOARD_*` | 3 — Phase 3 |
| `NOTIFICATION_READ_SELF` | Chưa có endpoint — bảng `notifications` chưa triển khai |

---

*Hết tài liệu v0.3 — Phase 1 (đã triển khai).*

*Lịch sử phiên bản: v0.1 (19/09/2026) → v0.2 (11/10/2026, đồng bộ Database Design v0.4) → v0.3 (13/10/2026, chốt open items + ghi nhận triển khai backend Phase 1).*
