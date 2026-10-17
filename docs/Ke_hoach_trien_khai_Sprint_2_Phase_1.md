# Kế hoạch triển khai Sprint 2 — Phase 1 (Authentication, Account & Master Data)

**Ngày lập:** 19/09/2026
**Deadline Sprint 2:** 27/09/2026
**Team:** Bình (backend) + Đức Anh (frontend), integration/shared chung
**Căn cứ:** API Design v0.1 · Permission Matrix v2.0 · Jira Plan Sprint 2 (EPIC-7/8/9) · Database v0 đã migrate xong

---

## 1. Hiện trạng (đã xong 19/09)

| Hạng mục | Trạng thái |
|---|---|
| Database | ✅ 30 bảng v0, Flyway V1/V2/V3 migrate xong lên Aiven, `validate` pass |
| Backend entity/repo | ✅ 27 entity + 27 repository + 14 enum, `mvn compile` pass |
| Frontend nền tảng | ✅ Material + Tailwind, `core/` (auth/token service, interceptor, 3 guard) + `shared/` (page-header, empty-state, confirm-dialog, coming-soon) + layout client/admin |
| Frontend trang | ⚠️ login/register/home mới là UI form (chưa nối API vì BE chưa có controller) |
| Tài liệu | ✅ Tech Stack + API Design v0.1 (md + docx) |

**Khoảng trống lớn nhất:** Backend chưa có bất kỳ lớp nào trên repository — chưa có Security config, JWT, controller, service, DTO, exception handler. Vì vậy mọi story đều phải đi qua bước làm nền tảng BE trước.

---

## 2. Quyết định cần chốt NGAY (trước khi code — chặn 4 story)

Ba open item này trong API Design v0.1 phải chốt ở ngày đầu, nếu không các story Auth sẽ phải sửa lại:

| # | Vấn đề | Đề xuất (nên chọn) |
|---|---|---|
| 1 | **Nơi lưu reset-password token** (v0 không có bảng) | Thêm migration `V4__password_reset_tokens.sql` (user_id, token_hash, expires_at, used_at). An toàn hơn JWT trần vì thu hồi được |
| 2 | **Định dạng `content` của News** | **HTML** (sanitize bằng `jsoup` chống XSS khi lưu/trả). Frontend render bằng `[innerHTML]` sau sanitize |
| 3 | **Revoke token khi logout** (JWT stateless) | MVP: client xóa token, backend trả `data:null`. Không làm blacklist (đỡ 1 bảng, đúng phạm vi Sprint 2) |
| — | Cơ chế đặt lại mật khẩu bởi ADMIN | Chốt kèm khi làm User Management: trả mật khẩu tạm trong response (đơn giản, không cần mail server) |

> Payment gateway, refresh token là vấn đề Phase 3 — **không làm trong Sprint 2**.

---

## 3. Chiến lược: nền tảng trước, pattern hoá phần CRUD

**Hai trục song song từ ngày 1:**

1. **Bình (BE)** làm lớp nền chung rồi mới mở endpoint — vì mọi module đều cần Security/JWT/ApiResponse/error handling.
2. **Đức Anh (FE)** làm tiếp các trang bằng **mock API** (environment cờ) song song, đợi BE có endpoint thật thì nối.

**Điểm mấu chốt giúp kịp deadline:** 6 module "master data" (Airline, City, Airport, Aircraft, Promotion, Voucher, News) đều là **CRUD + list + soft-delete giống hệt nhau**. Làm **một generic base** rồi nhân bản, không viết mỗi module từ đầu:

| Lớp | Generic base | Module con chỉ cần |
|---|---|---|
| BE | `BaseCrudController<T, Req, Resp>`, `BaseCrudService<T>` (dùng `JpaRepository` + `@Getter` thủ công) | DTO + mapper + vài quy tắc nghiệp vụ riêng |
| BE | `ApiResponse<T>` + `GlobalExceptionHandler` | — |
| FE | `BaseListComponent<T>` (search/filter/sort/pagination), `BaseFormDialog` (create/edit/toggle) | model + cột + vài field config |

Ước tính: sau khi generic base chạy ngọt ở **Airline**, 6 module còn lại mỗi bên chỉ mất ~1–1.5 ngày.

---

## 4. Lịch triển khai (20/09 → 27/09)

> Mỗi "Ngày" là một nhịp làm việc; BE/FE chạy song song. Dấu ⛓ = task FE phụ thuộc endpoint BE.

### Ngày 1 — 20/09: Nền tảng BE + chốt quyết định

**Chung:** chốt 4 quyết định ở mục 2; Bình thêm `V4__password_reset_tokens.sql` (nếu chọn cách thêm bảng).

**Bình (BE):**
- `config/SecurityConfig` (stateless, `/api/auth/**` + GET công khai `permitAll`, còn lại authenticated; method-level `@PreAuthorize("hasAuthority('XXX')")`). Prefix `ROLE_`/permission vào claim JWT.
- `security/JwtService` (tạo/validate token, claim `sub`, `email`, `permissions`), `UserDetailsServiceImpl`, `PasswordEncoder` (BCrypt).
- `common/ApiResponse<T>`, `common/GlobalExceptionHandler` (map 9 mã lỗi ở API Design mục 1.4), `common/ListQuery` + `PageData`.
- Exception: `BusinessException`, `ResourceNotFoundException`, `DuplicateResourceException`.

**Đức Anh (FE):**
- `core/models` cho 9 module master data + RBAC + user (đối chiếu API Design mục 2). Thêm `core/services/base-crud.service.ts` (generic list/get/create/update/delete).
- Environment thêm cờ `useMock` để UI chạy trước khi BE xong.

### Ngày 2 — 21/09: Auth endpoints + nối Login/Register

**Bình:** `AuthController` (register/login/logout/change-password/reset request+confirm), `AuthService`, DTO. Login trả `access_token` + user + `permissions` theo API Design 2.1. ⛓ xong `POST /auth/login`, `POST /auth/register`.

**Đức Anh:** nối login/register form với API thật (bỏ mock). Hoàn thiện guard redirect (`?redirect=`) + interceptor xử lý 401 → xóa phiên.

**Chốt cuối ngày (đích tích hợp):** đăng ký → tài khoản vào bảng `users` (role CUSTOMER), đăng nhập → nhận JWT, vào được `/profile` (page trống đã có guard).

### Ngày 3 — 22/09: Profile + User Management (ADMIN)

**Bình:** `ProfileController` (GET/PUT self); `UserController` CRUD + soft-delete (chặn ADMIN tự hạ quyền cuối cùng). ⛓ `GET/PUT /profile`, `GET/POST/PUT/DELETE /users`, `GET /users/{id}`.

**Đức Anh:** `features/client/pages/profile` (xem + sửa, upload avatar qua Cloudinary). Khởi làm `features/admin/pages/users` (list + filter role/status + form create/edit + toggle, dùng `BaseListComponent` + `BaseFormDialog`).

**Đích:** ADMIN xem và tạo được user với role; CUSTOMER sửa được hồ sơ.

### Ngày 4 — 23/09: RBAC + Airline (pattern mẫu ★)

**Bình:** `RoleController`/`PermissionController` (GET, read-only). Bắt đầu `AirlineController` **đúng chuẩn generic** — viết cùng `BaseCrudController`/`BaseCrudService` ở đây để Airline là "con thỏ thử". ⛓ `GET /roles`, `GET /permissions`, `GET/POST/PUT/DELETE /airlines`.

**Đức Anh:** `features/admin/pages/roles-permissions` (2 tab xem, không CRUD — BR-RBAC-03). Bắt đầu `features/admin/pages/airlines` làm mẫu cho `BaseListComponent` + form dialog.

**Đích:** Airline đi được **toàn vòng** list → create → edit → toggle → soft-delete → (guest public list). Đây là bản mẫu để 6 module sau copy.

### Ngày 5 — 24/09: City + Airport (nhân bản pattern)

**Bình:** City/Airport theo đúng pattern Airline (Airport thêm FK `city_id` + tọa độ, response lồng `city`). ⛓ đủ CRUD 2 module.

**Đức Anh:** `features/admin/pages/cities`, `features/admin/pages/airports` (copy pattern airline; airport có dropdown city). Các trang **guest public** hiển thị list (để Pha sau FĐ tìm chuyến) — hiện tại chỉ render ở client khi cần.

**Đích:** 3 module master data đầu hoàn chỉnh, chứng minh tốc độ pattern ~1 ngày 2 module.

### Ngày 6 — 25/09: Aircraft + Seat Configuration

**Bình:** Aircraft CRUD (không public — chỉ STAFF/ADMIN) + Seat Configuration (`GET/PUT` cả sơ đồ, kiểm tra `seat_capacity`, UNIQUE vị trí). ⛓ `GET/POST/PUT/DELETE /aircrafts`, `GET /seat-types`, `GET/PUT /aircrafts/{id}/seat-configurations`.

**Đức Anh:** `features/admin/pages/aircrafts` + màn hình sơ đồ ghế (đơn giản: nhập danh sách row/col/loại, hoặc grid). Đây là task UI phức tạp nhất Sprint 2 — ưu tiên làm gọn, đừng sa vào editor sơ đồ đẹp.

### Ngày 7 — 26/09: Promotion + Voucher

**Bình:** Promotion CRUD (public list chỉ thấy đang hiệu lực; ADMIN filter status); Voucher definition CRUD + `GET /my-vouchers` (SELF) + `POST /vouchers/{id}/assign`. ⛓ đủ API.

**Đức Anh:** `features/admin/pages/promotions`, `features/admin/pages/vouchers`; `features/client/pages/my-vouchers` (danh sách voucher của CUSTOMER). Tích hợp confirm-dialog cho soft-delete/toggle.

### Ngày 8 — 27/09: News + Unit test + dọn dẹp

**Bình:** News + NewsCategory CRUD + publish/unpublish (sanitize HTML content). Bổ sung **unit test** cho service Auth/User/MasterData (mock repository; không cần DB test). Kiểm tra toàn bộ `mvn test`/`mvn compile`.

**Đức Anh:** `features/client/pages/news` + `news-detail`; `features/admin/pages/news` + `news-categories` (publish/unpublish). Chạy `ng build` + `ng test` (thêm spec dựng `BaseListComponent` mẫu).

**Chung:** integration test từng cặp API↔UI (register→login→profile→admin CRUD 1 module), code review chéo, update Jira từng task về Done.

### Buffer — 28/09 (nếu trượt deadline)
- Ôm các Story chưa xong News/Aircraft-SeatConfig; fix lỗi phát sinh; chuẩn bị demo Sprint 2.

---

## 5. Ma trận phụ thuộc (BE → FE)

| Endpoint BE | Story FE chờ nó |
|---|---|
| `POST /auth/register`, `login`, `logout` | Login/Register/Logout pages |
| `POST /auth/change-password`, `reset-password/*` | Change/Reset password pages |
| `GET/PUT /profile` | Profile page |
| `GET/POST/PUT/DELETE /users` | Admin User Management |
| `GET /roles`, `GET /permissions` | Admin RBAC |
| CRUD `/airlines` `/cities` `/airports` | Admin master-data + guest list |
| `/aircrafts`, `/seat-configurations` | Admin Aircraft + Seat map |
| CRUD `/promotions` `/vouchers` + `/my-vouchers` | Admin promo/voucher + client my-vouchers |
| `/news`, `/news-categories` | Client news + admin news/cat |

Vì FE có mock flag, Đức Anh **không cần ngồi chờ** — làm UI trước, nối API khi Bình báo endpoint sẵn sàng mỗi tối.

---

## 6. Định nghĩa "Done" cho mỗi Story

- BE: endpoint đúng API Design (method/path/permission/response), **unit test service pass**, `mvn compile` sạch, đã chạy tay qua (curl/Postman) với role đúng.
- FE: build pass, vitest pass, UI khớp danh sách UI đã duyệt, gọi đúng endpoint, xử lý được ít nhất lỗi `1002` (401) và `1005` (trùng).
- Shared: integration chạy end-to-end nhịp chính, ghi nhận trên Jira, không còn TODO phát sinh không được theo dõi.

---

## 7. Rủi ro & khắc phục

| Rủi ro | Mức | Xử lý |
|---|---|---|
| Bình một mình làm hết lớp nền BE (Security/JWT/error) | Cao | Ưu tiên tuyệt đối ngày 1; chấp nhận 4 story Auth đứng sau |
| UI sơ đồ ghế (Seat Config) ngốn thời gian | Trung | Giảm scope: nhập row/col/loại dạng bảng, không làm editor đồ hoạ |
| Mock flag FE trễ/loạn khi nối API thật | Trung | Chỉ dùng mock cho phase chờ; nối xong tắt ngay, không giữ 2 nhánh |
| Trôi deadline vì quá nhiều module | Trung | Generic base (BE+FE) là chiếc phao chính — Airline làm mẫu thật kỹ ngày 4 |
| Node v24 thiếu npm / env `DB_URL` thiếu `jdbc:` | Thấp | Đã ghi nhớ — xem memory `dev-environment-paths` |

---

## 8. Việc user cần tự làm

1. Sửa env `FLIGHTBOOKING_DB_URL` thêm tiền tố `jdbc:` (để `mvn spring-boot:run` chạy thẳng, hết phải override).
2. Xóa 2 tài liệu cũ (`Bao_cao_do_an_mobile_nhom4_sua.docx`, `QLDA_Jira_CDTT.docx`).
3. Chốt 3 quyết định mục 2 (đã có đề xuất sẵn — xác nhận để Bình không block).

---

*Kế hoạch dựa trên hiện trạng thật 19/09; điều chỉnh linh hoạt theo tiến độ từng ngày, cập nhật Jira theo Epic/Story/Task như Quy ước Jira.*