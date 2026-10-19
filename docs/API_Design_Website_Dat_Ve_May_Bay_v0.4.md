# API Design — Website Đặt Vé Máy Bay Nội Địa

**Phiên bản:** v0.4 (Phase 1 — đã triển khai + bổ sung v0.5)
**Ngày:** 15/10/2026 (bản gốc 13/10/2026)
**Trạng thái:** **Đã triển khai** — backend Phase 1 hoàn thành (cụm 0–7), 45 unit test pass, verify E2E bằng curl. Cập nhật 15/10: **(1) bỏ `/v1` — base path nay là `/api`**; (2) **bỏ ví + hoàn tiền tự động** (xóa endpoint `/wallet`, hủy vé không tính tiền hoàn). Cập nhật 14/10: tách vùng path `/admin` + `/public`; ràng buộc ADMIN không xóa ADMIN; `GET /public/news-categories`; chặn bài `DRAFT`; 2 lỗ hổng phân quyền (mục 4.9). v0.4 gốc: thêm cột “Tên API (Chức năng)”, `color_hex`, nhóm endpoint bulk.
**Căn cứ:** Database Design **v0.5** · Permission Matrix v2.0 (3 Phase) · Phase 1 Use Case Specification v2.3 · Jira Plan Sprint 2–6 (Revised)
**Thay thế:** v0.3 (13/10/2026). Lý do bump: (1) thêm cột **Tên API (Chức năng)** vào mọi bảng endpoint; (2) đồng bộ DB v0.5 — thêm `seat_types.color_hex`; (3) thêm nhóm endpoint **thao tác hàng loạt** cho 10 module CRUD; (4) kiểm soát truy cập News; (5) **tách vùng path** `/api/admin` và `/api/public`; (6) **bỏ `/v1`**; (7) **bỏ ví và hoàn tiền tự động**.

---

## 0. Phạm vi tài liệu

Tài liệu chốt **hợp đồng API (contract)** giữa Backend và Frontend cho **Phase 1**, làm chuẩn để hai repo (`domestic-flight-booking-web-backend`, `domestic-flight-booking-web-frontend`) code song song mà không lệch.

- **Phase 1 (đặc tả đầy đủ):** Authentication & Account, Profile, User Management, RBAC, Master Data (Airline / City / Airport / Aircraft / Seat Map), Promotion, Voucher (+ Claim voucher cho CUSTOMER), News (+ danh mục tin cho khách), **Thao tác hàng loạt**.
- **Phase 2–3:** liệt kê sơ bộ để khóa naming, không đi sâu (bao gồm Payment, E-Ticket).
- Tài liệu **không** mô tả code triển khai (service/controller) — chỉ mô tả bề mặt API. *Ngoại lệ: mục 4.9 ghi 2 lỗ hổng phân quyền phát hiện từ code, vì đó là điểm chặn phải sửa trước khi FE nối API thật.*
- **Chưa đưa vào tài liệu (chờ triển khai sau):** Notifications (bảng `notifications` chưa tạo — xem DB v0.5).

> **Cột “Tên API (Chức năng)” (mới v0.4):** mọi bảng endpoint từ mục 2 trở đi có thêm một cột mô tả **chức năng nghiệp vụ** của endpoint bằng tiếng Việt, để FE đặt tên service/method và gắn nhãn UI đúng theo nghiệp vụ, không phải suy diễn từ path.

---

## 1. Quy ước chung

### 1.1 Base URL & Versioning

| Mục | Giá trị |
|---|---|
| Base path | `/api` |
| Content-Type | `application/json; charset=UTF-8` |
| Versioning | **Không dùng version trong path** (bỏ `/v1` — chốt 15/10/2026). Khi cần breaking change thì thêm header `X-API-Version` hoặc bump base path thành `/api/v2` **chỉ khi thật cần**, không giữ song song nhiều version |

> **Vì sao bỏ `/v1` (chốt 15/10/2026):** dự án chưa có client bên ngoài nào phụ thuộc version, và `/api/v1/...` làm path dài, khó đọc. Ranh giới quản trị/khách đã thể hiện rõ bằng `/admin` và `/public` (mục 1.1b), nên segment version là thừa. Đây là **breaking change** — toàn bộ path đổi từ `/api/v1/...` sang `/api/...`.

### 1.1b Ba vùng path — phân biệt quản trị và khách (mới v0.4)

API chia thành **3 vùng** theo đối tượng gọi, để ranh giới quản trị/khách hiện rõ ngay trên URL:

| Vùng | Prefix | Đối tượng | Xác thực | Ví dụ |
|---|---|---|---|---|
| **Public** | `/api/public/**` | Khách (GUEST/CUSTOMER) đọc dữ liệu công khai | Không cần token (`permitAll`) | `GET /api/public/airlines` |
| **Admin** | `/api/admin/**` | Quản trị viên (ADMIN/STAFF) — toàn bộ màn quản trị | Cần token + `@PreAuthorize` | `GET /api/admin/airlines`, `POST /api/admin/users` |
| **Common** | `/api/**` (còn lại) | Người dùng đã đăng nhập — self-service & xác thực | Cần token | `/api/auth/**`, `/api/profile`, `/api/my-vouchers` |

**Quy tắc phân vùng:**

1. **Mọi thứ thuộc màn quản trị → `/api/admin/`.** Kể cả thao tác **đọc** trên màn quản trị (`GET /admin/airlines` trả cả bản ghi `INACTIVE`, filter tự do). Lý do: màn quản trị cần thấy dữ liệu đã ngừng dùng, khác hẳn dữ liệu khách được xem.
2. **Dữ liệu khách được xem → `/api/public/`.** Chỉ trả bản ghi `ACTIVE`, không lộ `DRAFT`/đã xóa mềm. Đây là endpoint **riêng**, không phải cùng endpoint với admin rồi phân nhánh theo quyền — nhờ đó không thể vô tình lộ dữ liệu nội bộ qua đường public.
3. **Self-service** (`profile`, `my-vouchers`, `voucher-store`, booking của chính mình) **không** nằm dưới `/admin` vì không phải màn quản trị — giữ ở vùng common.
4. Module **thuần quản trị** (`users`, `roles`, `permissions`, `news-categories`) chỉ có ở `/admin`, không có bản `/public`.

> **Vì sao tách (chốt 14/10/2026):** trước đây `GET /api/airlines` vừa là public cho khách vừa là danh sách quản trị, phân biệt bằng cách dựa vào quyền người gọi để lọc `INACTIVE` — dễ lộ dữ liệu nếu service quên nhánh. Tách hai path làm ranh giới rõ ràng và **kiểm tra được ở tầng Spring Security** (path prefix), không phụ thuộc logic service.

> ⚠️ **Đây là breaking change so với v0.3.** Toàn bộ path cũ (`/api/airlines`, `/api/users`, …) đổi tiền tố. FE cần cập nhật base path từng service. Bảng đối chiếu đầy đủ ở **mục 2.11**.

### 1.2 Định dạng dữ liệu

- **JSON dùng `snake_case`** cho mọi field (khớp tên cột database).
- Ngày: `YYYY-MM-DD` (`date`). Ngày giờ: ISO-8601 UTC, ví dụ `2026-09-19T08:43:22Z` (`timestamptz`).
- Tiền: kiểu số (`number`) theo đơn vị **VND**, không làm tròn ở tầng API. Mọi response tiền tệ kèm field `currency` (mặc định `"VND"`).
- Trường `null`: **luôn có mặt** trong response.
- ID: `number` (BIGINT). Không dùng string.

### 1.3 Response wrapper

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

**HTTP status luôn phản ánh đúng bản chất** (200/201/204/400/401/403/404/409/500) — `code` trong body bổ sung chi tiết nghiệp vụ.

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
| `1006` | 409/422 | `BUSINESS_RULE_VIOLATION` | Vi phạm quy tắc nghiệp vụ |
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

`BUSINESS_RULE_VIOLATION` dùng cho các ràng buộc đã ghi trong Database Design **v0.5** (ví dụ `end_date >= start_date`, `claim_limit > 0`, `departure_airport_id <> arrival_airport_id`, tổng số ghế sơ đồ = `seat_capacity`, không chồng khoảng hiệu lực giá vé, `color_hex` sai định dạng hex). Message phải nêu rõ quy tắc bị vi phạm.

### 1.5 Xác thực & phân quyền

- Cơ chế: **JWT Bearer** (`Authorization: Bearer <access_token>`), Spring Security 6 OAuth2 Resource Server.
- Access token chứa `sub` (user id), `email`, và danh sách quyền dưới dạng claim `permissions`.
- **GUEST = chưa đăng nhập.** GUEST không phải một role trong database; quyền của GUEST thể hiện bằng `permitAll`. Không seed role GUEST (xem mục 4).
- **Role cố định MVP:** `CUSTOMER`, `STAFF`, `ADMIN`.
- Endpoint công khai (không cần token): đăng ký, đăng nhập, quên/đặt lại mật khẩu, và toàn bộ **GET** dữ liệu công khai (airline, city, airport, promotion, news, news category, flight search).
- Endpoint `SELF` (ví dụ `PROFILE_READ_SELF`, `BOOKING_READ_SELF`): hệ thống **luôn** lấy user từ token, không nhận `user_id` từ client.
- Lỗi xác thực: thiếu/hết hạn token → `1002`; đủ token nhưng thiếu quyền → `1003`.

### 1.6 Phân trang, lọc, sắp xếp (theo BR-LIST-01 → BR-LIST-05)

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

- **BR-LIST-01:** search/filter/sort/pagination là **hành vi đọc**.
- **BR-LIST-02:** khi đổi keyword/filter/sort, client **quay về trang 1**. Kết quả rỗng là **trạng thái hợp lệ** — trả `200` + `items: []`.
- **BR-LIST-03:** nhiều filter kết hợp theo **AND**. `sort` chỉ nhận field trong **whitelist**; field ngoài whitelist → bỏ qua, dùng sort mặc định.
- **BR-LIST-04:** GUEST/CUSTOMER chỉ truy vấn dữ liệu công khai hoặc **thuộc sở hữu của mình**. Dữ liệu `DRAFT`, chưa publish, đã soft-delete **không được lộ**.
- **BR-LIST-05:** Role/Permission là catalog cố định, read-only → không cần phân trang.

### 1.7 Ngày giờ & tiền tệ

- Server lưu và trả `timestamptz` theo **UTC**; client hiển thị theo múi giờ người dùng (UTC+7).
- `date` không kèm múi giờ.
- Tiền: `number` + `currency`. Không format dấu phân cách ở tầng API.

---

## 2. Phase 1 — Đặc tả endpoint theo module

Ký hiệu: **P** = public (permitAll, không cần token) · **A** = cần access token.

### 2.1 Authentication & Account

Bảng `users`. Nguồn: AUTH-01 → AUTH-06.

| # | Method | Path | Tên API (Chức năng) | Quyền | UC |
|---|---|---|---|---|---|
| 1 | POST | `/api/auth/register` | Đăng ký tài khoản khách hàng | P (GUEST-only) | AUTH-01 |
| 2 | POST | `/api/auth/login` | Đăng nhập, cấp cặp access/refresh token | P | AUTH-02 |
| 3 | POST | `/api/auth/logout` | Đăng xuất, thu hồi token | A — `LOGOUT` | AUTH-03 |
| 4 | POST | `/api/auth/refresh` | Làm mới access token từ refresh token | P | — |
| 5 | POST | `/api/auth/password-reset/request` | Yêu cầu gửi hướng dẫn đặt lại mật khẩu | P | AUTH-04 |
| 6 | POST | `/api/auth/password-reset/confirm` | Xác nhận đặt lại mật khẩu bằng token | P | AUTH-04 |
| 7 | POST | `/api/auth/change-password` | Đổi mật khẩu của chính mình | A — `PASSWORD_CHANGE_SELF` | AUTH-05 |

**POST `/auth/register`** — request:

```json
{
  "email": "binh@example.com",
  "password": "P@ssw0rd123",
  "full_name": "Nguyễn Tiến Bình",
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
    "full_name": "Nguyễn Tiến Bình",
    "phone": "0901234567",
    "avatar_url": null,
    "status": "ACTIVE",
    "roles": ["CUSTOMER"],
    "created_at": "2026-09-19T08:43:22Z"
  }
}
```

Ràng buộc: `email` UNIQUE → trùng trả `1005`. Mật khẩu lưu hash (BCrypt), **không bao giờ** trả `password_hash`. Role gán cứng `CUSTOMER`.

**POST `/auth/login`** — request `{ "email", "password" }`. Response `data`:

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "refresh_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "user": {
    "id": 12,
    "email": "binh@example.com",
    "full_name": "Nguyễn Tiến Bình",
    "avatar_url": null,
    "roles": ["CUSTOMER"],
    "permissions": ["BOOKING_CREATE", "BOOKING_READ_SELF", "..."]
  }
}
```

Sai thông tin → `1007` (401). Tài khoản `INACTIVE` → `1008` (403). Thông điệp lỗi của `1007` **không** phân biệt "sai email" và "sai mật khẩu".

**POST `/auth/refresh`** — request `{ "refresh_token" }`, trả cặp token mới. Refresh token whitelist ở Redis, xoay vòng khi refresh.

**POST `/auth/logout`** — response `200` với `data: null`. Blacklist `jti` ở Redis + thu hồi refresh token.

**POST `/auth/password-reset/request`** — request `{ "email" }`. **Luôn** trả `200` với message trung tính, kể cả khi email không tồn tại. Trả thêm `data.reset_token` để test luồng.

**POST `/auth/password-reset/confirm`** — request `{ "token", "new_password" }`. Token sai/hết hạn → `1006`.

**POST `/auth/change-password`** — request `{ "current_password", "new_password" }`. Sai mật khẩu hiện tại → `1007`.

### 2.2 Profile

Nguồn: PRO-01, PRO-02. Luôn thao tác trên user trong token.

| # | Method | Path | Tên API (Chức năng) | Quyền |
|---|---|---|---|---|
| 8 | GET | `/api/profile` | Xem hồ sơ cá nhân của mình | A — `PROFILE_READ_SELF` |
| 9 | PUT | `/api/profile` | Cập nhật hồ sơ cá nhân | A — `PROFILE_UPDATE_SELF` |
| 10 | POST | `/api/profile/avatar` | Tải ảnh đại diện lên Cloudinary | A — `PROFILE_UPDATE_SELF` |

`GET /profile` trả về chính user đang đăng nhập (cấu trúc như `data` của register).

`PUT /profile` — chỉ cho phép sửa `full_name`, `phone`, `avatar_url`:

```json
{ "full_name": "Nguyễn C. T. Bình", "phone": "0907654321", "avatar_url": "https://res.cloudinary.com/..." }
```

`email`, `status`, `roles` **không** sửa được qua endpoint này. Gửi field ngoài whitelist → bỏ qua, không lỗi.

**Upload avatar:** `POST /profile/avatar` (multipart/form-data, field `file`) → trả `{ "avatar_url": "..." }`.

### 2.3 User Management (ADMIN)

Bảng `users`. Nguồn: USR-01 → USR-05. **Toàn bộ dưới `/api/admin/users`** — không có bản public.

| # | Method | Path | Tên API (Chức năng) | Quyền |
|---|---|---|---|---|
| 11 | GET | `/api/admin/users` | Danh sách người dùng (phân trang, lọc) | A — `USER_READ` |
| 12 | GET | `/api/admin/users/{id}` | Chi tiết một người dùng | A — `USER_READ_DETAIL` |
| 13 | POST | `/api/admin/users` | Tạo người dùng mới và gán role | A — `USER_CREATE` |
| 14 | PUT | `/api/admin/users/{id}` | Cập nhật thông tin, đổi role, bật/tắt trạng thái | A — `USER_UPDATE` |
| 15 | DELETE | `/api/admin/users/{id}` | Xóa mềm người dùng | A — `USER_DELETE` |
| 16 | POST | `/api/admin/users/{id}/reset-password` | Đặt lại mật khẩu, trả mật khẩu tạm | A — `USER_UPDATE` |
| 17 | PATCH | `/api/admin/users/bulk` | Bật/tắt trạng thái nhiều người dùng | A — `USER_UPDATE` |
| 18 | DELETE | `/api/admin/users/bulk` | Xóa mềm nhiều người dùng | A — `USER_DELETE` |

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

`password` không nằm trong payload update — ADMIN đặt lại mật khẩu qua `POST /admin/users/{id}/reset-password` (trả mật khẩu tạm).

**DELETE `/admin/users/{id}`** — **soft delete**: đặt `status = INACTIVE`, giữ nguyên dữ liệu lịch sử (USR-05). Trả `200` + `data: null`.

#### 🔒 Ràng buộc xóa tài khoản (bắt buộc, mới v0.4)

**ADMIN KHÔNG được xóa tài khoản ADMIN khác — kể cả chính mình.** Chỉ được xóa tài khoản `STAFF` và `CUSTOMER`. Đây là quy tắc **cùng cấp không xóa nhau**, chống việc một ADMIN đơn lẻ hạ bệ toàn bộ ban quản trị.

| Người gọi | Xóa CUSTOMER | Xóa STAFF | Xóa ADMIN |
|---|---|---|---|
| ADMIN | ✅ | ✅ | ❌ `1006` |
| STAFF | ❌ `1003` | ❌ `1003` | ❌ `1003` |

Quy tắc kiểm tra **theo role của bản ghi bị xóa**, không theo quan hệ "ai tạo ai":

- Bản ghi bị xóa có `role = ADMIN` → **luôn từ chối** `1006` với message rõ: *"Không thể xóa tài khoản ADMIN"*. Không phân biệt là ADMIN khác hay chính mình.
- Ràng buộc "ADMIN ACTIVE cuối cùng" (đã có ở v0.3) **vẫn giữ** nhưng nay là lớp thứ hai: vì đã cấm xóa mọi ADMIN, lớp này không còn đường kích hoạt qua xóa đơn lẻ — giữ lại để phòng khi quy tắc trên được nới sau này.

> **Vì sao cấm hẳn thay vì chỉ chặn "ADMIN cuối cùng":** quy tắc cũ chỉ chặn xóa khi còn 1 ADMIN, nên 2 ADMIN vẫn xóa được nhau — một ADMIN có thể xóa ADMIN kia rồi tự xóa mình. Cấm xóa mọi tài khoản ADMIN là ranh giới rõ và dễ kiểm chứng hơn. Muốn gỡ một ADMIN thì đổi role họ xuống STAFF/CUSTOMER trước (qua `PUT /admin/users/{id}`), rồi mới xóa — thao tác đổi role đã có kiểm tra riêng.

**Áp cho cả bulk:** `DELETE /admin/users/bulk` kiểm tra từng bản ghi; id nào là ADMIN → vào `failed` với `1006`, các id STAFF/CUSTOMER vẫn xóa bình thường.

**Áp cho cả `PUT /admin/users/{id}`?** Không. Ràng buộc này **chỉ cho xóa**. ADMIN vẫn được **đổi role** một ADMIN khác (ví dụ hạ xuống STAFF) — nhưng vẫn giữ ràng buộc cũ: không tự hạ quyền nếu mình là ADMIN ACTIVE cuối cùng → `1006`.

Chặn leo thang đặc quyền (đã có từ v0.3): ADMIN **không** được tự hạ quyền chính mình nếu đó là ADMIN cuối cùng đang `ACTIVE` → `1006`.

Filter danh sách: `status`, `role`. Whitelist sort: `email`, `full_name`, `created_at`. Whitelist keyword: `email`, `full_name`, `phone`.

> **Bulk (17, 18):** xem mục 2.10. Lưu ý `users` giữ nguyên ràng buộc "ADMIN cuối cùng" **cho từng bản ghi** — bản ghi vi phạm vào `failed`, không chặn cả lô.

### 2.4 RBAC — Role & Permission (ADMIN, read-only)

Bảng `roles`, `permissions`, `role_permissions`. Nguồn: RBAC-01, RBAC-02.

| # | Method | Path | Tên API (Chức năng) | Quyền |
|---|---|---|---|---|
| 19 | GET | `/api/admin/roles` | Danh sách vai trò (catalog cố định) | A — `ROLE_READ` |
| 20 | GET | `/api/admin/permissions` | Danh sách quyền (catalog, lọc theo module) | A — `ROLE_READ` |

**Không** có POST/PUT/DELETE cho role và permission ở MVP (BR-RBAC-03).

`GET /admin/roles` trả **mảng trần** trong `data` (không phân trang — BR-LIST-05):

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

`GET /admin/permissions` hỗ trợ search/filter/sort:

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

**Lưu ý:** quyền xem Permission catalog dùng chung key `ROLE_READ`.

### 2.5 Master Data — Airline / City / Airport

Bảng `airlines`, `cities`, `airports`. Nguồn: AIR-01→05, CITY-01→05, APT-01→05.

Cả ba module dùng **cùng một khuôn** endpoint, và **tách đôi thành 2 vùng**: `/public` cho khách đọc, `/admin` cho quản trị. Ví dụ với Airline:

| # | Method | Path | Tên API (Chức năng) | Quyền |
|---|---|---|---|---|
| 21 | GET | `/api/public/airlines` | Danh sách hãng (khách xem, chỉ ACTIVE) | P — `AIRLINE_READ` |
| 22 | GET | `/api/public/airlines/{id}` | Chi tiết một hãng (khách xem) | P — `AIRLINE_READ_DETAIL` |
| 23 | GET | `/api/admin/airlines` | Danh sách hãng (quản trị, xem cả INACTIVE) | A — `AIRLINE_READ` |
| 24 | GET | `/api/admin/airlines/{id}` | Chi tiết một hãng (quản trị) | A — `AIRLINE_READ_DETAIL` |
| 25 | POST | `/api/admin/airlines` | Tạo hãng hàng không mới | A — `AIRLINE_CREATE` |
| 26 | PUT | `/api/admin/airlines/{id}` | Cập nhật hãng hàng không | A — `AIRLINE_UPDATE` |
| 27 | DELETE | `/api/admin/airlines/{id}` | Xóa mềm hãng hàng không | A — `AIRLINE_DELETE` |
| 28 | PUT | `/api/admin/airlines/{id}/active` | Bật/tắt trạng thái một hãng | A — `AIRLINE_UPDATE` |
| 29 | PATCH | `/api/admin/airlines/bulk` | Bật/tắt trạng thái nhiều hãng | A — `AIRLINE_UPDATE` |
| 30 | DELETE | `/api/admin/airlines/bulk` | Xóa mềm nhiều hãng | A — `AIRLINE_DELETE` |

| Module | Public path | Admin path | Permission prefix | Bảng |
|---|---|---|---|---|
| Airline | `/api/public/airlines` | `/api/admin/airlines` | `AIRLINE_` | `airlines` |
| City | `/api/public/cities` | `/api/admin/cities` | `CITY_` | `cities` |
| Airport | `/api/public/airports` | `/api/admin/airports` | `AIRPORT_` | `airports` |

**`GET /public/airlines` — khách xem.** Chỉ trả bản ghi `status = ACTIVE`, không phân nhánh theo quyền (vì đây là endpoint riêng cho khách). Sắp xếp mặc định theo tên.

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

**`GET /admin/airlines` — quản trị.** ADMIN/STAFF thấy **tất cả** bản ghi chưa xóa mềm (kể cả `INACTIVE`), truyền `status` để lọc.

Whitelist cho từng module:

| Module | keyword | filter | sort |
|---|---|---|---|
| Airline | `airline_code`, `airline_name` | `status`, `country` | `airline_code`, `airline_name` |
| City | `city_code`, `city_name` | `country` | `city_code`, `city_name` |
| Airport | `airport_code`, `airport_name` | `city_id` | `airport_code`, `airport_name` |

**POST `/admin/airlines`** (ADMIN):

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

**PUT** — cập nhật thông tin **và** toggle `status`. `code` là định danh nghiệp vụ: **không** cho đổi qua PUT.

**DELETE** — **soft delete**. Trước khi cho ngừng dùng, service kiểm tra ràng buộc tham chiếu (ví dụ Airline còn Flight đang hoạt động → `1006`).

### 2.6 Aircraft & Seat Map (sơ đồ ghế)

Bảng `aircrafts`, `seat_types`, `aircraft_seats`. Nguồn: AC-01→05, SEAT-01, SEAT-02.

**Aircraft KHÔNG có bản public** — chỉ `STAFF`/`ADMIN`, toàn bộ dưới `/api/admin`.

| # | Method | Path | Tên API (Chức năng) | Quyền |
|---|---|---|---|---|
| 31 | GET | `/api/admin/aircrafts` | Danh sách máy bay | A — `AIRCRAFT_READ` |
| 32 | GET | `/api/admin/aircrafts/{id}` | Chi tiết một máy bay | A — `AIRCRAFT_READ_DETAIL` |
| 33 | POST | `/api/admin/aircrafts` | Tạo máy bay mới | A — `AIRCRAFT_CREATE` |
| 34 | PUT | `/api/admin/aircrafts/{id}` | Cập nhật máy bay | A — `AIRCRAFT_UPDATE` |
| 35 | DELETE | `/api/admin/aircrafts/{id}` | Xóa mềm máy bay | A — `AIRCRAFT_DELETE` |
| 36 | GET | `/api/admin/seat-types` | Danh sách loại ghế | A — `SEAT_CONFIG_READ` |
| 37 | GET | `/api/admin/seat-types/{id}` | Chi tiết một loại ghế | A — `SEAT_CONFIG_READ_DETAIL` |
| 38 | POST | `/api/admin/seat-types` | Tạo loại ghế mới | A — `SEAT_CONFIG_CREATE` |
| 39 | PUT | `/api/admin/seat-types/{id}` | Cập nhật loại ghế (kèm màu) | A — `SEAT_CONFIG_UPDATE` |
| 40 | DELETE | `/api/admin/seat-types/{id}` | Xóa mềm loại ghế | A — `SEAT_CONFIG_DELETE` |
| 41 | GET | `/api/admin/aircrafts/{id}/seat-map` | Xem sơ đồ ghế của máy bay | A — `SEAT_CONFIG_READ_DETAIL` |
| 42 | PUT | `/api/admin/aircrafts/{id}/seat-map` | Lưu (thay thế) toàn bộ sơ đồ ghế | A — `SEAT_CONFIG_UPDATE` |
| 43 | PATCH | `/api/admin/seat-types/bulk` | Bật/tắt trạng thái nhiều loại ghế | A — `SEAT_CONFIG_UPDATE` |
| 44 | DELETE | `/api/admin/seat-types/bulk` | Xóa mềm nhiều loại ghế | A — `SEAT_CONFIG_DELETE` |

**POST `/admin/aircrafts`**:

```json
{ "aircraft_code": "VN-A350-01", "model": "Airbus A350-900", "manufacturer": "Airbus", "seat_capacity": 305 }
```

`seat_capacity > 0` → vi phạm `1006`. `aircraft_code` UNIQUE → `1005`.

**Seat Type (v0.5 — thêm `color_hex`):** danh mục loại ghế, CRUD đầy đủ.

```json
{
  "seat_type_code": "ECO_WIN",
  "seat_type_name": "Phổ thông - cửa sổ",
  "seat_class": "ECONOMY",
  "color_hex": "#0194F3",
  "status": "ACTIVE"
}
```

| Field | Ghi chú |
|---|---|
| `seat_class` | `ECONOMY` \| `BUSINESS` \| `FIRST` |
| **`color_hex` (mới v0.5)** | **Mã màu hiển thị trên sơ đồ ghế, dạng `#RRGGBB` (7 ký tự). `null` = chưa đặt màu → FE lùi về màu mặc định theo `seat_class`. Sai định dạng → `1001` (validate ở DTO) hoặc `1006` (CHECK ở DB)** |

`seat_type_code` UNIQUE (chỉ tính bản ghi chưa xóa mềm) → `1005`. **DELETE** là soft delete; loại ghế đang được `aircraft_seats` dùng → từ chối `1006` (chỉ cho tắt `is_active`). `PUT` cập nhật **và** toggle `is_active`.

> **Vì sao cần `color_hex`:** DB cho phép nhiều `seat_type` cùng một `seat_class` (5 loại ECONOMY: `ECO`, `ECO_WIN`, `ECO_AIS`, `PREM_ECO`, `EXIT`). Tô màu theo `seat_class` thì lưới ghế không phân biệt được "Phổ thông" với "Phổ thông ưu tiên" hay "Ghế thoát hiểm".

**Seat Map — sơ đồ ghế của một máy bay:** thao tác theo **cả sơ đồ**, không CRUD từng ghế.

`PUT /admin/aircrafts/{id}/seat-map` nhận **toàn bộ** danh sách ghế và **thay thế** sơ đồ hiện có trong một transaction:

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

- Không có hai ghế trùng `(seat_row, seat_column)` trong payload.
- `seat_type_id` phải tồn tại **và** `is_active = true` → không tồn tại trả `1004`.
- **Tổng số ghế = `aircrafts.seat_capacity`**.
- Máy bay **chưa có** dòng `flight_seats` nào trỏ tới `aircraft_seats` của nó.

`GET /admin/aircrafts/{id}/seat-map` trả sơ đồ hiện tại kèm loại ghế (**và màu của loại ghế**):

```json
{
  "aircraft_id": 1,
  "seat_capacity": 305,
  "configured_seats": 3,
  "seats": [
    { "id": 10, "seat_row": 1, "seat_column": "A", "seat_type": { "id": 3, "seat_type_code": "BUS", "seat_type_name": "Ghế thương gia", "seat_class": "BUSINESS", "color_hex": "#FF8C00" } }
  ]
}
```

**Lưu ý:** `flight_seats` (tồn ghế của một chuyến cụ thể) là bảng **khác** — sinh từ `aircraft_seats` khi tạo Seat Inventory cho chuyến.

### 2.7 Promotion

Bảng `promotions`, `promotion_scopes`, `promotion_excluded_flights`. Nguồn: PROMO-01 → PROMO-05.

| # | Method | Path | Tên API (Chức năng) | Quyền |
|---|---|---|---|---|
| 45 | GET | `/api/public/promotions` | Danh sách khuyến mãi đang hiệu lực (khách xem) | P — `PROMOTION_READ` |
| 46 | GET | `/api/public/promotions/{id}` | Chi tiết một khuyến mãi (khách xem) | P — `PROMOTION_READ_DETAIL` |
| 47 | GET | `/api/admin/promotions` | Danh sách khuyến mãi (quản trị, xem cả đã tắt) | A — `PROMOTION_READ` |
| 48 | GET | `/api/admin/promotions/{id}` | Chi tiết một khuyến mãi (quản trị) | A — `PROMOTION_READ_DETAIL` |
| 49 | POST | `/api/admin/promotions` | Tạo chương trình khuyến mãi | A — `PROMOTION_CREATE` |
| 50 | PUT | `/api/admin/promotions/{id}` | Cập nhật khuyến mãi (kèm phạm vi, loại trừ) | A — `PROMOTION_UPDATE` |
| 51 | DELETE | `/api/admin/promotions/{id}` | Xóa mềm khuyến mãi | A — `PROMOTION_DELETE` |
| 52 | PATCH | `/api/admin/promotions/bulk` | Bật/tắt trạng thái nhiều khuyến mãi | A — `PROMOTION_UPDATE` |
| 53 | DELETE | `/api/admin/promotions/bulk` | Xóa mềm nhiều khuyến mãi | A — `PROMOTION_DELETE` |

**POST `/admin/promotions`**:

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
| `min_fare_amount` | Giá dòng vé tối thiểu mới được giảm; `null` = không yêu cầu |
| `applicable_trip_type` | `ANY` \| `ONE_WAY` \| `ROUND_TRIP`, mặc định `ANY` |
| `scopes[]` | Phạm vi áp dụng (`target_type` ∈ `AIRLINE`/`FARE_CLASS`/`ROUTE`/`FLIGHT`). **Không có dòng nào = áp cho tất cả chuyến bay.** Nhiều dòng là **HOẶC**. ROUTE **có hướng** |
| `excluded_flight_ids[]` | Chuyến bay bị **loại trừ**. Loại trừ **luôn thắng** phạm vi |

Ràng buộc từ DB v0.5 → vi phạm `1006`: `end_date >= start_date`, `discount_value > 0` (PERCENT thì ≤ 100), `max_discount_amount > 0`, `min_fare_amount >= 0`. `promotion_code` UNIQUE → `1005`.

**`GET /public/promotions` — khách xem:** chỉ trả promotion đang hiệu lực (`is_active = true` và `start_date <= hôm nay <= end_date`). **`GET /admin/promotions` — quản trị:** thấy tất cả, filter được `is_active`.

Whitelist: keyword `promotion_code`, `promotion_name`; filter `is_active`, `discount_type`, `applicable_trip_type`; sort `start_date`, `end_date`, `promotion_code`.

**PUT** — cập nhật **và** toggle `is_active`, thay toàn bộ `scopes`/`excluded_flight_ids`. **DELETE** — soft delete.

### 2.8 Voucher (mô hình ví — khách không nhập mã)

Bảng `vouchers`, `voucher_scopes`, `voucher_excluded_flights`, `user_vouchers`. Nguồn: VOU-01 → VOU-05 (+ UC Claim Voucher).

| # | Method | Path | Tên API (Chức năng) | Quyền |
|---|---|---|---|---|
| 54 | GET | `/api/admin/vouchers` | Danh sách voucher (quản trị) | A — `VOUCHER_READ` |
| 55 | GET | `/api/admin/vouchers/{id}` | Chi tiết một voucher | A — `VOUCHER_READ_DETAIL` |
| 56 | POST | `/api/admin/vouchers` | Tạo voucher mới | A — `VOUCHER_CREATE` |
| 57 | PUT | `/api/admin/vouchers/{id}` | Cập nhật voucher (kèm phạm vi, loại trừ) | A — `VOUCHER_UPDATE` |
| 58 | DELETE | `/api/admin/vouchers/{id}` | Xóa mềm voucher | A — `VOUCHER_DELETE` |
| 59 | POST | `/api/admin/vouchers/{id}/assign` | Tặng voucher cho khách (ASSIGNED) | A — `VOUCHER_UPDATE` |
| 60 | PATCH | `/api/admin/vouchers/bulk` | Bật/tắt trạng thái nhiều voucher | A — `VOUCHER_UPDATE` |
| 61 | DELETE | `/api/admin/vouchers/bulk` | Xóa mềm nhiều voucher | A — `VOUCHER_DELETE` |
| 62 | GET | `/api/voucher-store` | Kho voucher GLOBAL đang phát (self-service) | A — `VOUCHER_CLAIM` |
| 63 | POST | `/api/vouchers/{id}/claim` | Khách bấm Nhận voucher vào ví | A — `VOUCHER_CLAIM` |
| 64 | GET | `/api/my-vouchers` | Ví voucher của tôi | A — `VOUCHER_READ` (SELF) |
| 65 | GET | `/api/my-vouchers/{id}` | Chi tiết một voucher trong ví | A — `VOUCHER_READ_DETAIL` (SELF) |

> **Vì sao `voucher-store` / `claim` / `my-vouchers` KHÔNG nằm dưới `/admin`:** đây là thao tác **của khách trên dữ liệu của chính khách** (xem kho, nhận, xem ví mình) — không phải màn quản trị. Giữ ở vùng common. Chỉ phần **định nghĩa voucher** (CRUD, assign) mới là quản trị.

**Đổi mô hình so với v0.1:** voucher **không dùng mã gõ tay**. Khách **nhận** voucher vào ví rồi **chọn từ ví** khi thanh toán.

**POST `/admin/vouchers`** (ADMIN):

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
| `distribution_type` | `GLOBAL` \| `ASSIGNED` |
| `max_discount_amount` | Trần giảm cho **cả booking** (voucher tính trên cả booking) |
| `min_order_amount` | Tổng giá vé tối thiểu (sau khuyến mãi, chỉ tính dòng ADULT/CHILD khớp phạm vi) |
| `claim_limit` | Tổng số lượt được **nhận**, chỉ áp cho `GLOBAL`; `null` = không giới hạn, nếu có phải `> 0` → `1006` |

Voucher **không giảm cho hành khách INFANT**.

**`GET /voucher-store`** (CUSTOMER) — các voucher `distribution_type = GLOBAL`, đang hiệu lực, còn lượt nhận và khách **chưa** nhận. Trả về kèm cờ `claimed`.

**`POST /vouchers/{id}/claim`** (CUSTOMER):

```json
{ "code": 1000, "message": "Success", "data": { "user_voucher_id": 88, "status": "AVAILABLE" } }
```

Backend chạy `UPDATE vouchers SET claimed_count = claimed_count + 1 WHERE id = :id AND (claim_limit IS NULL OR claimed_count < claim_limit)`; **1 dòng bị ảnh hưởng** mới tạo `user_vouchers` (`source = CLAIMED`). Hết lượt → `1006`. Khách đã nhận → `1005`.

**`GET /my-vouchers`** (CUSTOMER) — voucher **thuộc tài khoản** trong token:

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

`status` ∈ `AVAILABLE` | `RESERVED` | `USED` | `EXPIRED`.

**`POST /admin/vouchers/{id}/assign`** (ADMIN) — tặng voucher `ASSIGNED`:

```json
{ "user_ids": [12, 15, 20] }
```

Ràng buộc UNIQUE `(user_id, voucher_id)` → cấp trùng `1005`. Voucher `GLOBAL` không assign → `1006`.

### 2.9 News & News Category

Bảng `news_items`, `news_categories`, `news_category_mapping`. Nguồn: NEWS-01→07, NEWS-CAT-01→05.

| # | Method | Path | Tên API (Chức năng) | Quyền |
|---|---|---|---|---|
| 66 | GET | `/api/public/news` | Danh sách bài viết đã đăng (khách xem) | P — `NEWS_READ` |
| 67 | GET | `/api/public/news/{id}` | Chi tiết bài viết đã đăng (khách xem) | P — `NEWS_READ_DETAIL` |
| 68 | GET | `/api/public/news-categories` | Danh sách chuyên mục cho khách (chỉ đang hoạt động) | P — `NEWS_CATEGORY_READ` |
| 69 | GET | `/api/admin/news` | Danh sách bài viết (quản trị, xem cả DRAFT) | A — `NEWS_READ` |
| 70 | GET | `/api/admin/news/{id}` | Chi tiết bài viết (quản trị) | A — `NEWS_READ_DETAIL` |
| 71 | POST | `/api/admin/news` | Tạo bài viết mới (nháp) | A — `NEWS_CREATE` |
| 72 | PUT | `/api/admin/news/{id}` | Cập nhật bài viết | A — `NEWS_UPDATE` |
| 73 | DELETE | `/api/admin/news/{id}` | Xóa mềm bài viết | A — `NEWS_DELETE` |
| 74 | POST | `/api/admin/news/{id}/publish` | Phát hành bài viết | A — `NEWS_PUBLISH` |
| 75 | POST | `/api/admin/news/{id}/unpublish` | Ngừng hiển thị bài viết | A — `NEWS_UNPUBLISH` |
| 76 | GET | `/api/admin/news-categories` | Danh sách chuyên mục tin (quản trị) | A — `NEWS_CATEGORY_READ` |
| 77 | GET | `/api/admin/news-categories/{id}` | Chi tiết chuyên mục (kèm bài viết) | A — `NEWS_CATEGORY_READ_DETAIL` |
| 78 | POST | `/api/admin/news-categories` | Tạo chuyên mục mới | A — `NEWS_CATEGORY_CREATE` |
| 79 | PUT | `/api/admin/news-categories/{id}` | Cập nhật chuyên mục | A — `NEWS_CATEGORY_UPDATE` |
| 80 | DELETE | `/api/admin/news-categories/{id}` | Xóa mềm chuyên mục | A — `NEWS_CATEGORY_DELETE` |
| 81 | PATCH | `/api/admin/news/bulk` | Đổi trạng thái nhiều bài viết (publish/unpublish hàng loạt) | A — `NEWS_UPDATE` |
| 82 | DELETE | `/api/admin/news/bulk` | Xóa mềm nhiều bài viết | A — `NEWS_DELETE` |
| 83 | PATCH | `/api/admin/news-categories/bulk` | Bật/tắt trạng thái nhiều chuyên mục | A — `NEWS_CATEGORY_UPDATE` |
| 84 | DELETE | `/api/admin/news-categories/bulk` | Xóa mềm nhiều chuyên mục | A — `NEWS_CATEGORY_DELETE` |

> **Vì sao News tách 2 vùng rõ hơn các module khác:** `GET /public/news` chỉ trả `PUBLISHED`, còn `GET /admin/news` trả cả `DRAFT`. Đây chính là ranh giới từng gây lỗ hổng (mục 4.9-A) khi hai việc dùng chung một endpoint.

**POST `/admin/news`** (ADMIN):

```json
{
  "title": "Vietnam Airlines mở bán vé Tết 2027",
  "content": "<p>Nội dung...</p>",
  "thumbnail_url": "https://res.cloudinary.com/...",
  "category_ids": [2, 5]
}
```

News tạo ra mặc định `status = DRAFT`. `content` là HTML, **sanitize bằng jsoup** (Safelist.relaxed, chặn script/iframe/event handler) trước khi lưu. `category_ids` ghi vào `news_category_mapping`; id không tồn tại → `1004`.

**Publish / Unpublish** — tách khỏi `PUT` vì là hành động nghiệp vụ riêng, có permission riêng:

- `POST /admin/news/{id}/publish` → `status = PUBLISHED`, gán `published_at = now()`.
- `POST /admin/news/{id}/unpublish` → `status = DRAFT`, giữ nguyên `published_at` cũ.

**`GET /public/news` — khách xem (NEWS-01):** chỉ trả `status = PUBLISHED`, sắp xếp mặc định `published_at,desc`. **`GET /admin/news` — quản trị:** thấy tất cả, filter được `status=DRAFT`.

Whitelist: keyword `title`; filter `status`, `category_id`; sort `published_at`, `title`.

#### 🔒 `GET /public/news/{id}` — chỉ trả bài `PUBLISHED` (bắt buộc, mới v0.4)

> **Nguồn:** `De_xuat_bo_sung_DB_v0.5_mau_loai_ghe_va_bulk.md` mục 4 (FE báo lỗi bảo mật).

NEWS-01 quy định GUEST/CUSTOMER chỉ thấy `status = PUBLISHED`. Việc tách vùng path (`/public/news/{id}` vs `/admin/news/{id}`) **giải quyết gốc vấn đề**: endpoint khách chỉ tồn tại ở `/public`, và **`/public/news/{id}` chỉ trả bài `PUBLISHED`**. Bài `DRAFT`/`UNPUBLISHED` không tồn tại với khách.

**Quy tắc:** khách gọi `GET /public/news/{id}` với id của bài không phải `PUBLISHED` → trả **`1004` (RESOURCE_NOT_FOUND)**, **KHÔNG** trả `1003` (ACCESS_DENIED).

**Vì sao `1004` chứ không `1003`:** `1003` xác nhận "bài này CÓ tồn tại nhưng bạn không được xem" — kẻ tấn công học được là `id` đó hợp lệ, dùng để dò `id`. `1004` không tiết lộ gì. Cùng nguyên tắc với `/auth/login` không phân biệt "sai email" và "sai mật khẩu" (mục 2.1).

**ADMIN/STAFF** xem bài `DRAFT` qua `/admin/news/{id}` (không phải qua `/public`) — nên **không cần phân nhánh theo quyền trong cùng một endpoint nữa**. Đây là lợi ích trực tiếp của việc tách path: `/public/news/{id}` có thể **luôn** lọc `PUBLISHED` mà không sợ chặn nhầm admin.

> **Trạng thái code BE (cập nhật 17/10/2026):** quy tắc nay được áp ở **path**, không phải theo quyền — `NewsService.getPublicById` (dùng ở `GET /api/public/news/{id}`) chặn `status != PUBLISHED` → `1004`; vùng `GET /api/admin/news/{id}` đọc mọi trạng thái. FE vẫn giữ lớp chặn client (`news-detail.ts` kiểm tra `status !== 'PUBLISHED'`) cho UX.

#### `GET /public/news-categories` — danh mục cho khách (mới v0.4)

> **Nguồn:** `De_xuat_bo_sung_DB_v0.5_mau_loai_ghe_va_bulk.md` mục 3 (FE). **Đã chốt phương án (b).**

Trang Tin tức cho khách cần dropdown lọc theo danh mục. `GET /admin/news-categories` là quản trị nên khách không gọi được. FE đang tạm suy danh mục từ chính danh sách news — chỉ liệt kê được danh mục **có bài đã đăng**.

**Chốt: thêm endpoint public riêng `GET /api/public/news-categories`** (phương án b) thay vì mở public endpoint quản trị (phương án a).

| | Phương án (a) mở public endpoint quản trị | **Phương án (b) thêm `/public/news-categories` — ĐÃ CHỌN** |
|---|---|---|
| Phân quyền | Endpoint quản trị thành public — mất ranh giới | Endpoint quản trị giữ nguyên; khách có endpoint riêng |
| Dữ liệu lộ | Trả cả `description` + `status` (chỉ có nghĩa với ADMIN) | Chỉ trả `id` + `name`, chỉ danh mục `is_active = true` |

`GET /api/public/news-categories` — **P**, không cần token:

```json
{
  "code": 1000,
  "message": "Success",
  "data": [
    { "id": 2, "name": "Khuyến mãi" },
    { "id": 5, "name": "Cẩm nang du lịch" }
  ]
}
```

Trả **mảng trần** (không phân trang — danh mục ít), chỉ `id` + `name`, chỉ danh mục chưa xóa mềm và `is_active = true`. Không lộ `description`/`status`.

**News Category (quản trị):** `GET /admin/news-categories` — **ADMIN**. `DELETE` là soft delete; category còn News tham chiếu → `1006`.

### 2.10 Thao tác hàng loạt (bulk action) — mới v0.4

> **Đề xuất gốc:** `De_xuat_bo_sung_DB_v0.5_mau_loai_ghe_va_bulk.md` mục 2 (FE). **Không sinh thay đổi schema** — chỉ bổ sung endpoint.

**Vấn đề:** các màn danh sách chỉ thao tác được một bản ghi một lần. Với danh mục hàng chục bản ghi, người vận hành phải bấm lặp (ví dụ ngừng dùng 15 hãng → bấm 15 lần).

**Ba hành động cần hỗ trợ:** xóa mềm, ngừng kích hoạt (`is_active = false`), kích hoạt (`is_active = true`).

**Thiết kế endpoint:** một endpoint cho mỗi hành động, nhận danh sách id.

| Method | Path | Tên API (Chức năng) | Quyền |
|---|---|---|---|
| `PATCH` | `/api/admin/{resource}/bulk` | Bật/tắt trạng thái nhiều bản ghi | như `*_UPDATE` của module |
| `DELETE` | `/api/admin/{resource}/bulk` | Xóa mềm nhiều bản ghi | như `*_DELETE` của module |

Ví dụ với airlines:

```
PATCH  /api/admin/airlines/bulk   { "ids": [1, 2, 3], "status": "INACTIVE" }
DELETE /api/admin/airlines/bulk   { "ids": [1, 2, 3] }
```

**Vì sao `PATCH` cho việc bật/tắt thay vì `PUT`:** chỉ đổi MỘT trường (`is_active`) trên nhiều bản ghi, không thay thế toàn bộ tài nguyên — đúng ngữ nghĩa PATCH.

**Vì sao `DELETE` có body:** HTTP cho phép body trong DELETE (RFC 9110), và danh sách id có thể dài. Phương án thay thế là `POST /{resource}/bulk-delete` — chốt dùng **`DELETE` có body** để đồng nhất ngữ nghĩa với DELETE đơn lẻ.

**Áp cho module nào:** mọi module CRUD đang có ở Phase 1 — `airlines`, `cities`, `airports`, `aircrafts`, `seat-types`, `promotions`, `vouchers`, `news`, `news-categories`, `users`. Bulk **chỉ tồn tại ở vùng `/admin`** (không có bulk cho khách).

**Request body:**

```json
// PATCH /admin/{resource}/bulk  — module có is_active
{ "ids": [1, 2, 3], "status": "INACTIVE" }

// PATCH /admin/news/bulk  — news dùng status DRAFT/PUBLISHED/UNPUBLISHED
{ "ids": [1, 2, 3], "status": "PUBLISHED" }

// DELETE /admin/{resource}/bulk
{ "ids": [1, 2, 3] }
```

| Field | Kiểu | Ghi chú |
|---|---|---|
| `ids` | array\<number\> | Danh sách id. **Bắt buộc**, không rỗng. Tối đa **100** id/lần gọi → vượt `1001` |
| `status` | string | Chỉ có ở `PATCH`. Module master data nhận `ACTIVE` \| `INACTIVE`; `news` nhận `PUBLISHED` \| `UNPUBLISHED` (xem ghi chú dưới) |

> **Đã chốt — bulk cho `news` (phương án b):** `news_items` **không có cột `is_active`**; trạng thái của nó là `status` ∈ `DRAFT` / `PUBLISHED` / `UNPUBLISHED`. Vì vậy `PATCH /admin/news/bulk` là **đổi trạng thái hàng loạt**, không phải "bật/tắt kích hoạt": body nhận `{ "ids": [...], "status": "PUBLISHED" | "UNPUBLISHED" }`. Khi bulk publish, **giữ nguyên ràng buộc** "bài phải có ≥1 chuyên mục" cho từng bản ghi — bài vi phạm vào `failed`, không chặn cả lô. `DRAFT` không nhận qua bulk (đưa bài về nháp là `unpublish`).

> ⚠️ **Field `status` hay `is_active`:** đề xuất gốc của FE viết `{ "is_active": false }`. Tài liệu chốt dùng **`status`** để đồng nhất với toàn bộ API master data (DB lưu `is_active BOOLEAN` nhưng API luôn trả/nhận `status` chuỗi — xem mục 4.7). Nếu FE muốn giữ `is_active` boolean thì báo lại để thống nhất một quy ước duy nhất.

**Response đề xuất — trả kết quả TỪNG PHẦN** thay vì all-or-nothing:

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

**Vì sao không all-or-nothing:** ràng buộc tham chiếu của từng module (airline còn flight → `1006`; seat_type đang được `aircraft_seats` dùng → `1006`; ADMIN cuối cùng → `1006`) khiến việc chọn 15 bản ghi rồi bị chặn cả 15 vì 1 bản ghi là trải nghiệm tệ. Trả kết quả từng phần để FE hiện "13 thành công, 2 bị chặn" kèm lý do.

**Ràng buộc cần giữ:**

- Vẫn **xóa mềm** (`is_deleted = true`), không xóa cứng — như DELETE đơn lẻ.
- Vẫn kiểm tra ràng buộc tham chiếu **từng bản ghi** trước khi đổi.
- Giới hạn **100 id** mỗi lần gọi.
- Id không tồn tại → đưa vào `failed` với `1004`, không làm hỏng cả lô.

**Trạng thái phía FE:**

- Bảng danh sách thêm **cột checkbox** ở đầu: chọn từng hàng + chọn tất cả (chỉ trong trang hiện tại).
- Khi có ≥1 hàng được chọn, hiện **thanh hành động**: "Đã chọn N" + nút **Kích hoạt** / **Ngừng** / **Xóa**.
- Xóa hàng loạt **phải qua hộp thoại xác nhận** nêu rõ số lượng.
- Sau khi xong, hiện kết quả từng phần nếu có bản ghi bị chặn.

### 2.11 Bảng đối chiếu path v0.3 → v0.4 (breaking change)

> **Dành cho FE:** toàn bộ path đổi tiền tố — **bỏ `/v1`** (base path nay là `/api`), **và** tách vùng `/admin` · `/public`. Dùng bảng này để sửa base path từng service.

| Module | Path v0.3 (cũ) | Path v0.4 (mới) | Loại |
|---|---|---|---|
| Auth | `/api/v1/auth/**` | `/api/auth/**` | *bỏ /v1* |
| Profile | `/api/v1/profile` | `/api/profile` | *bỏ /v1* |
| User (list/detail) | `/api/v1/users` | `/api/admin/users` | ADMIN |
| Role | `/api/v1/roles` | `/api/admin/roles` | ADMIN |
| Permission | `/api/v1/permissions` | `/api/admin/permissions` | ADMIN |
| Airline | `/api/v1/airlines` | `/api/public/airlines` (đọc) · `/api/admin/airlines` (quản trị) | tách đôi |
| City | `/api/v1/cities` | `/api/public/cities` · `/api/admin/cities` | tách đôi |
| Airport | `/api/v1/airports` | `/api/public/airports` · `/api/admin/airports` | tách đôi |
| Aircraft | `/api/v1/aircrafts` | `/api/admin/aircrafts` | ADMIN |
| Seat Type | `/api/v1/seat-types` | `/api/admin/seat-types` | ADMIN |
| Seat Map | `/api/v1/aircrafts/{id}/seat-map` | `/api/admin/aircrafts/{id}/seat-map` | ADMIN |
| Promotion | `/api/v1/promotions` | `/api/public/promotions` · `/api/admin/promotions` | tách đôi |
| Voucher (định nghĩa) | `/api/v1/vouchers` | `/api/admin/vouchers` | ADMIN |
| Voucher (khách) | `/api/v1/voucher-store`, `/my-vouchers`, `/vouchers/{id}/claim` | `/api/voucher-store`, `/api/my-vouchers`, `/api/vouchers/{id}/claim` | common |
| News | `/api/v1/news` | `/api/public/news` · `/api/admin/news` | tách đôi |
| News Category | `/api/v1/news-categories` | `/api/public/news-categories` · `/api/admin/news-categories` | tách đôi |
| Bulk | `/api/v1/{resource}/bulk` | `/api/admin/{resource}/bulk` | ADMIN |
| ~~Wallet~~ | ~~`/api/v1/wallet`~~ | **BỎ** (15/10/2026) | — |

**Quy tắc chuyển đổi nhanh cho FE:**

1. **Bỏ `/v1`** ở mọi path (base path: `/api/v1` → `/api`).
2. Endpoint **quản trị** (màn ADMIN/STAFF) → thêm `/admin` ngay sau `/api`.
3. Endpoint **khách đọc** (trang chủ, trang danh sách công khai) → thêm `/public`.
4. Endpoint **self-service** (`profile`, `my-vouchers`, `voucher-store`, `claim`, booking của mình) → chỉ bỏ `/v1`, giữ phần còn lại.
5. `/auth/**` → chỉ bỏ `/v1`.
6. **Xóa service ví** khỏi FE (endpoint `/wallet` không còn).

---

## 3. Phase 2–3 — Sơ bộ (khóa naming)

### Phase 2 — Flight & Fare

> Áp cùng quy tắc phân vùng: tìm kiếm/xem chuyến cho khách → `/public`; quản lý chuyến/giá → `/admin`.

| Method | Path | Tên API (Chức năng) | Quyền dự kiến |
|---|---|---|---|
| GET | `/api/public/flights/search` | Tìm chuyến bay theo tuyến/ngày | P — `FLIGHT_SEARCH` |
| GET | `/api/public/flights` | Danh sách chuyến bay (khách xem) | P — `FLIGHT_READ` |
| GET | `/api/public/flights/{id}` | Chi tiết một chuyến bay | P — `FLIGHT_DETAIL_READ` |
| GET | `/api/admin/flights` | Danh sách chuyến bay (quản trị) | A — `FLIGHT_READ` |
| POST/PUT | `/api/admin/flights`, `/api/admin/flights/{id}` | Tạo / cập nhật chuyến bay | A — `FLIGHT_CREATE` / `FLIGHT_UPDATE` |
| POST | `/api/admin/flights/{id}/activate`, `/deactivate` | Mở / đóng bán chuyến bay | A — `FLIGHT_ACTIVATE` / `FLIGHT_DEACTIVATE` |
| GET/POST/PUT/DELETE | `/api/admin/fare-classes` | Quản lý loại vé / hạng giá | A — `FARE_CLASS_*` |
| GET/POST/PUT/DELETE | `/api/admin/flight-fares` | Quản lý giá bán theo chuyến | A — `FLIGHT_FARE_*` |
| GET/POST/PUT/DELETE | `/api/admin/baggage-options` | Quản lý gói hành lý | A — `BAGGAGE_*` |
| GET | `/api/admin/flights/{id}/seat-map` | Xem tồn ghế của chuyến | A — `SEAT_INVENTORY_READ_DETAIL` |
| PUT | `/api/admin/flights/{id}/seat-inventory` | Khởi tạo / cập nhật tồn ghế chuyến | A — `SEAT_INVENTORY_MANAGE` |
| GET/POST/PUT/DELETE | `/api/admin/fare-rules` | Quản lý quy định hoàn vé | A — `FARE_RULE_*` |

Flight Search nhận `departure_airport_id`, `arrival_airport_id`, `departure_date`, `passengers`, `trip_type`. **Bắt buộc gửi `trip_type`** để backend áp đúng `applicable_trip_type` của promotion/voucher.

**Giá vé trả sẵn 3 loại hành khách:** `flight_fares` chỉ lưu giá **người lớn**. Backend tính sẵn và trả **cả ba** giá:

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

Tỉ lệ cấu hình trong `application.yml` (`app.pricing.child-fare-percent: 75`, `infant-fare-percent: 10`). Không cho hai dòng chồng khoảng hiệu lực cùng `(flight_id, fare_class_id)` → `1006`.

**Flight bổ sung khi cầu cao:** `POST /admin/flights` nhận `parent_flight_id`. Chỉ chọn được Aircraft **đã có sơ đồ ghế đủ `seat_capacity`** → nếu chưa đủ, `1006`.

### Phase 3 — Booking, Payment, E-Ticket

| Method | Path | Tên API (Chức năng) | Quyền dự kiến |
|---|---|---|---|
| POST | `/api/bookings` | Tạo booking mới | A — `BOOKING_CREATE` |
| GET | `/api/bookings` | Danh sách booking của tôi | A — `BOOKING_READ_SELF` |
| GET | `/api/bookings/{id}` | Chi tiết một booking | A — `BOOKING_READ_SELF` |
| POST | `/api/bookings/{id}/cancel` | Hủy booking (không tự hoàn tiền — xem ghi chú) | A — `BOOKING_CANCEL_SELF` |
| POST | `/api/bookings/{id}/passengers` | Thêm hành khách vào booking | A — `PASSENGER_CREATE` |
| POST | `/api/bookings/{id}/seats/hold` | Giữ ghế tạm 15 phút | A — `BOOKING_CREATE` |
| POST | `/api/bookings/{id}/baggages` | Chọn gói hành lý | A — `BOOKING_CREATE` |
| POST | `/api/bookings/{id}/voucher` | Áp voucher từ ví vào booking | A — `VOUCHER_APPLY` |
| POST | `/api/bookings/{id}/payments` | Tạo giao dịch thanh toán | A — `PAYMENT_CREATE` |
| GET | `/api/payments/{id}` | Chi tiết một giao dịch thanh toán | A — `PAYMENT_READ_SELF` |
| POST | `/api/payments/callback` | Nhận callback từ cổng thanh toán | P (xác thực bằng chữ ký) |
| GET | `/api/bookings/{id}/e-tickets` | Danh sách vé điện tử của booking | A — `ETICKET_READ_SELF` |
| GET | `/api/admin/bookings` | Danh sách booking toàn hệ thống | A — `BOOKING_READ` (STAFF) |
| GET | `/api/admin/dashboard/operational` | Dashboard vận hành | A — `DASHBOARD_READ_OPERATIONAL` |
| GET | `/api/admin/dashboard` | Dashboard tổng quan | A — `DASHBOARD_READ` |

> ❌ **ĐÃ BỎ 15/10/2026 — endpoint ví:** ~~`GET /api/wallet`~~ và ~~`GET /api/wallet/transactions`~~. Hệ thống không còn ví khách hàng (xem DB v0.5 mục 3.5). Quyền `WALLET_READ_SELF` cũng bị bỏ.

> **Phân vùng Phase 3:** booking/payment **của khách** giữ ở vùng common (`/bookings`, `/payments`) vì là self-service. Chỉ **màn quản trị** (`/admin/bookings`, `/admin/dashboard`) nằm dưới `/admin`. `POST /payments/callback` là endpoint của cổng thanh toán (public, xác thực chữ ký) — không thuộc `/admin` lẫn `/public` vì không phải khách gọi.

**`POST /bookings/{id}/voucher`** — **chọn voucher từ ví**, không nhập mã. Body nhận `user_voucher_id`, **không** nhận `voucher_code`.

**`POST /bookings/{id}/cancel` — KHÔNG tự động hoàn tiền (chốt 15/10/2026).** Hủy booking chỉ đổi `status = CANCELLED`, ghi `cancelled_at` + `cancel_reason`, giải phóng ghế và hủy vé điện tử. Hệ thống **không** tính tiền hoàn, **không** cộng ví. Chính sách hoàn của loại vé (`fare_rules`) vẫn trả trong response chi tiết booking để **hiển thị tham khảo** — kèm thông tin liên hệ đại lý (email + số điện thoại) để khách yêu cầu hoàn tiền. Xem DB v0.5 mục 5.5.

**Booking của GUEST: không tồn tại.** GUEST muốn đặt vé phải đăng nhập/đăng ký CUSTOMER.

---

## 4. Quyết định thiết kế & điểm mơ hồ đã xử lý

### 4.1 GUEST không phải một role

GUEST = "người dùng chưa đăng nhập". Không có bản ghi role `GUEST`; quyền của GUEST thể hiện bằng `permitAll`. Bảng `roles` chỉ có 3 dòng: `CUSTOMER`, `STAFF`, `ADMIN`.

### 4.2 `FLIGHT_READ` / `FLIGHT_DETAIL_READ` là một key duy nhất

Hai nhóm "Flight Search" (public) và "Flight Management" dùng **cùng một key** `FLIGHT_READ` / `FLIGHT_DETAIL_READ`. Khác biệt public/quản lý nằm ở các key **ghi**.

### 4.3 `FARE_RULE_*` và `SEAT_INVENTORY_*`

`fare_rules` đã là bảng riêng → `FARE_RULE_*` ánh xạ trực tiếp lên bảng này. `SEAT_INVENTORY_*` vẫn không có bảng riêng — nghiệp vụ nằm ở `flight_seats` và `aircraft_seats`; **vẫn seed đủ key** và **ràng buộc ở tầng service**.

### 4.4 `ROLE_READ` dùng chung cho cả Role và Permission

Permission Matrix không định nghĩa key `PERMISSION_READ`. RBAC-02 dùng chung `ROLE_READ`.

### 4.5 Soft Delete — v0.4 tách `is_active` và `is_deleted`

- `is_active` — bật/tắt sử dụng.
- `is_deleted` + `deleted_at` — xóa mềm.

Các endpoint `DELETE` ở Phase 1 nay là **soft delete thật**. Unique của cột mã dùng **partial unique index** `WHERE is_deleted = false`. Mọi truy vấn phải lọc `is_deleted = false` (BR-LIST-04).

### 4.6 Frontend: Angular Material + Tailwind CSS (đã chốt)

**Angular Material 22** (theme M3 `azure-blue`) cho component, **Tailwind CSS 3.4** cho layout/utility. Material giữ component; Tailwind lo bố cục, khoảng cách, responsive, màu trạng thái.

### 4.7 Quy ước `is_active` ở tầng API — chuỗi `status`, không phải boolean (chốt v0.4)

**Quan trọng cho FE:** DB v0.4/v0.5 lưu `is_active BOOLEAN`, nhưng **API trả về field `status` dạng chuỗi** `"ACTIVE"` / `"INACTIVE"` cho mọi module master data (airline, city, airport, aircraft, seat-type, promotion, voucher, news-category, user). Request `POST`/`PUT` cũng nhận `status` chuỗi.

- Đề xuất bulk của FE viết ví dụ `{ "ids": [1,2,3], "is_active": false }` — **nhưng API dùng `status`**. Chốt v0.4: bulk **PATCH nhận `status`** để đồng nhất với phần còn lại: `{ "ids": [1,2,3], "status": "INACTIVE" }`.
- Riêng `users` và `news` dùng `status` theo nghĩa trạng thái nghiệp vụ khác (`ACTIVE`/`INACTIVE` cho user; `DRAFT`/`PUBLISHED`/`UNPUBLISHED` cho news) — bulk PATCH nhận `status` với tập giá trị tương ứng của module.

### 4.8 Bảng `is_active` ↔ `status` theo module (đối chiếu nhanh)

| Module | Cột DB | Field API | Giá trị |
|---|---|---|---|
| airlines, cities, airports, aircrafts, seat-types, promotions, vouchers, news-categories, users | `is_active` | `status` | `ACTIVE` \| `INACTIVE` |
| news_items | `status` | `status` | `DRAFT` \| `PUBLISHED` \| `UNPUBLISHED` |

### 4.9 ⚠️ Lỗ hổng phân quyền phát hiện khi rà code (13/10/2026) — ✅ ĐÃ VÁ 17/10/2026

Hai vấn đề dưới đây **phát hiện từ code backend thực tế** (không chỉ từ tài liệu), cả hai đều nằm ở tầng Spring Security và cần sửa trước khi FE nối API thật. **Việc tách vùng `/admin` và `/public` (mục 1.1b) chính là cách xử lý gốc cho cả hai** — ranh giới nay nằm ở **path prefix**, kiểm tra được ở tầng Spring Security thay vì phụ thuộc logic service.

**Nguyên nhân gốc chung (v0.3):** `SecurityConfig` (dòng 55–63) khai `permitAll` cho `GET /api/news/**` và `/api/news-categories/**`. Khi request khớp `permitAll`, Spring Security **không chạy `@PreAuthorize`** ở method tương ứng — nên `@PreAuthorize('NEWS_READ_DETAIL')` đặt trên `GET /news/{id}` **không có tác dụng** cho khách chưa đăng nhập. Quyền thực tế của mọi `GET` dưới hai path này do **service** quyết định, không phải annotation.

| # | Vấn đề (trạng thái v0.3) | Mức độ | Cách xử lý ở v0.4 |
|---|---|---|---|
| A | `GET /news/{id}` không chặn bài `DRAFT`. `NewsController.getById` kế thừa `BaseCrudController.getById` → `BaseCrudService.getById` chỉ `findById` rồi map, **không kiểm tra `status`**. Cộng với `permitAll`, **bất kỳ ai** (kể cả không token) đoán `id` là đọc được toàn bộ nội dung bài nháp. | **CAO (lộ dữ liệu)** | **Tách endpoint**: `GET /public/news/{id}` (luôn chỉ trả `PUBLISHED`, id khác → `1004`) và `GET /admin/news/{id}` (ADMIN/STAFF, thấy cả `DRAFT`). Ranh giới ở path, không cần phân nhánh quyền trong service. |
| B | `PUT /news/{id}/active` không yêu cầu quyền. `NewsController` **thiếu override `toggleActive`** (các controller khác đều có). Method thừa hưởng từ `BaseCrudController` **không mang `@PreAuthorize`** → bất kỳ user đã đăng nhập (kể cả CUSTOMER) gọi `PUT /api/news/{id}/active?value=true` là **publish/unpublish được bài viết**. | **TRUNG BÌNH (ghi trái phép)** | Endpoint quản trị dời sang `PUT /admin/news/{id}/active`, bắt buộc `@PreAuthorize('NEWS_UPDATE')`. Hoặc **bỏ hẳn route base** cho news vì đã có `publish`/`unpublish` chuyên biệt. |

**Ghi chú thêm:** `setActive` của `NewsService` hiện map `active=true → PUBLISHED`, `active=false → UNPUBLISHED` — tức route base đang bị dùng như publish/unpublish thứ hai, trùng chức năng với 2 endpoint chuyên biệt. Nên **bỏ route base** cho news thay vì chỉ thêm quyền.

**Kiểm chứng:** `SeatTypeController`, `AirlineController`, `CityController`, `AirportController`, `AircraftController`, `PromotionController`, `VoucherController`, `NewsCategoryController` đều override `toggleActive` kèm `@PreAuthorize`. **Chỉ `NewsController` thiếu.**

> **Trạng thái triển khai (17/10/2026 — cụm 9 + 11):** đã hoàn thành cả 4 việc — (1) 6 controller public mới (`AirlinePublicController`, `CityPublicController`, `AirportPublicController`, `PromotionPublicController`, `NewsPublicController`, `NewsCategoryPublicController`); (2) `SecurityConfig` viết lại: `/api/public/**` = `permitAll` **chỉ GET**, `/api/admin/**` = authenticated; (3) mọi controller quản trị đã đổi `@RequestMapping` sang `/api/admin/**`, bỏ `/v1`; (4) `UserService.delete` chặn xóa **mọi** tài khoản ADMIN → 1006. Ngoài ra `NewsController` đã override `toggleActive` với `@PreAuthorize('NEWS_UPDATE')` và `NewsService.setActive` gán `published_at` khi bật lần đầu.

---

## 5. Open items — ĐÃ CHỐT khi triển khai (13/10/2026)

| # | Vấn đề | Quyết định đã chốt | Nơi triển khai |
|---|---|---|---|
| 1 | Nơi lưu token đặt lại mật khẩu | **Redis, TTL 15 phút** (`PASSWORD_RESET_TOKEN`) | `entity/redis/PasswordResetToken` + `AuthService.requestPasswordReset` |
| 2 | Hợp đồng cổng thanh toán | **Chưa chốt** — thuộc Phase 3 | — |
| 3 | Cơ chế logout/revoke token | **Blacklist `jti` ở Redis** + thu hồi refresh token | `entity/redis/InvalidatedToken` + `CustomJwtDecoder` |
| 4 | Refresh token | **Làm thật**: whitelist ở Redis, xoay vòng khi refresh | `entity/redis/RefreshToken` + `AuthService.refresh` |
| 5 | Reset password bởi ADMIN | **Trả mật khẩu tạm** trong response | `POST /admin/users/{id}/reset-password` → `{temporary_password}` |
| 6 | Định dạng `content` của News | **HTML + sanitize jsoup** | `common/HtmlSanitizer` + `NewsService` |
| 7 | Vùng path quản trị vs khách | **Tách `/api/admin/**` và `/api/public/**`** (chốt 14/10/2026) | `SecurityConfig` + `@RequestMapping` từng controller |
| 8 | ADMIN xóa tài khoản ADMIN | **Cấm** — ADMIN chỉ xóa STAFF/CUSTOMER (chốt 14/10/2026) | `UserService.delete` |

**Khác biệt đã chốt khi triển khai (ghi để FE biết):**

- **`GET` dữ liệu công khai (Airline/City/Airport/News/Promotion) nay ở `/api/public/...`** — xem mục 1.1b và bảng đối chiếu 2.11.
- **`POST /auth/refresh`** là endpoint bổ sung ngoài bảng 2.1.
- `POST /auth/password-reset/request` trả thêm `data.reset_token` để test luồng.
- Danh sách dùng sort whitelist theo **tên field snake_case** (`created_at,desc`).

**Bổ sung của v0.5 (đã ghi ở `docs/De_xuat_bo_sung_DB_v0.5_truong_anh.md` mục 7, migration V10):**

- `permissions.module` — `VARCHAR(30)` + backfill.
- Seed `VOUCHER_CLAIM` + `WALLET_READ_SELF` cho CUSTOMER. *(Từ 15/10/2026, `WALLET_READ_SELF` bị bỏ cùng nhóm ví.)*
- 5 cột ảnh/mô tả/nhãn: `news_items.thumbnail_url`, `cities.image_url`, `promotions.image_url`/`description`/`badge_label`.

**Bổ sung của v0.4 (tài liệu này):**

- `seat_types.color_hex` — migration **V12** (xem DB v0.5 mục 8).
- Nhóm endpoint **bulk** cho 10 module (mục 2.10).
- **Bỏ `/v1`** khỏi mọi path (base path nay là `/api`).
- **Bỏ ví và hoàn tiền tự động** (15/10/2026) — xóa 2 endpoint `/wallet`, bỏ quyền `WALLET_READ_SELF`, hủy vé không còn tính tiền hoàn. Migration **V13** (xem DB v0.5 mục 8).
- **Tách vùng path `/admin` và `/public`** (mục 1.1b) — breaking change, bảng đối chiếu ở mục 2.11.
- **Ràng buộc ADMIN không xóa ADMIN** (mục 2.3).
- `GET /public/news-categories` + quy tắc chặn `DRAFT` ở `GET /public/news/{id}` (mục 2.9).

---

## 6. Truy vết permission ↔ endpoint

| Nhóm permission | Endpoint tương ứng |
|---|---|
| `REGISTER`, `PASSWORD_RESET` | 2.1 — **public** |
| `LOGIN`, `LOGOUT`, `PASSWORD_CHANGE_SELF` | 2.1 |
| `PROFILE_READ_SELF`, `PROFILE_UPDATE_SELF` | 2.2 |
| `USER_*` (5), `ROLE_READ` | 2.3, 2.4 — đều dưới `/admin` |
| `AIRLINE_*`, `CITY_*`, `AIRPORT_*` | 2.5 — đọc ở `/public`, ghi ở `/admin` |
| `AIRCRAFT_*`, `SEAT_CONFIG_*` | 2.6 — toàn bộ `/admin` |
| `PROMOTION_*` | 2.7 — đọc ở `/public`, ghi ở `/admin` |
| `VOUCHER_*`, `VOUCHER_APPLY`, `VOUCHER_CLAIM` | 2.8 — định nghĩa ở `/admin`, claim/ví ở common |
| `NEWS_*`, `NEWS_CATEGORY_*` | 2.9 — đọc ở `/public`, ghi ở `/admin`; 2 lỗ hổng ở mục 4.9 |
| **Bulk (mọi module)** | **2.10 — `/admin/{resource}/bulk`, dùng lại `*_UPDATE` / `*_DELETE`, KHÔNG sinh key mới** |
| `FLIGHT_*`, `FARE_CLASS_*`, `FLIGHT_FARE_*`, `BAGGAGE_*`, `FARE_RULE_*`, `SEAT_INVENTORY_*` | 3 — Phase 2 |
| `PASSENGER_*`, `BOOKING_*`, `PAYMENT_*`, `ETICKET_READ_SELF`, `DASHBOARD_*` | 3 — Phase 3 |
| `NOTIFICATION_READ_SELF` | Chưa có endpoint — bảng `notifications` chưa triển khai |

---

*Hết tài liệu v0.4 — Phase 1 (đã triển khai).*

*Lịch sử phiên bản: v0.1 (19/09/2026) → v0.2 (11/10/2026, đồng bộ Database Design v0.4) → v0.3 (13/10/2026, chốt open items + ghi nhận triển khai backend Phase 1) → v0.4 (13/10/2026, thêm cột Tên API (Chức năng), đồng bộ DB v0.5 — `color_hex`, thêm nhóm endpoint bulk) → v0.4 cập nhật 14/10/2026 (tách vùng path `/admin` + `/public`, ràng buộc ADMIN không xóa ADMIN, `GET /public/news-categories`, chặn bài `DRAFT`, 2 lỗ hổng phân quyền mục 4.9) → **v0.4 cập nhật 15/10/2026: bỏ `/v1` (base path `/api`), bỏ ví + hoàn tiền tự động (xóa endpoint `/wallet`, hủy vé không tính tiền hoàn — migration V13)**.*
