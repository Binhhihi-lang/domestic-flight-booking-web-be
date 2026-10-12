# Tổng kết công nghệ — Website Đặt Vé Máy Bay Nội Địa

**Ngày:** 19/09/2026
**Phạm vi:** cả 2 repo `domestic-flight-booking-web-backend` + `domestic-flight-booking-web-frontend`
**Căn cứ:** Database Design v0 · Permission Matrix v2.0 · API Design v0.1

---

## 1. Tổng quan

| Thành phần | Công nghệ | Ghi chú |
|---|---|---|
| Backend | Java 17 + Spring Boot 4.1.1 | module starters (Boot 4 tách autoconfigure) |
| Frontend | Angular 22 (SFC + Signals) | standalone-first, lazy loading |
| Database | PostgreSQL 18.6 trên **Aiven** (cloud) | timestamptz, snake_case |
| Migration | Flyway 12.4.0 | 3 version đã migrate thành công |
| Auth | JWT Bearer (Spring Security 6 Resource Server) | permission trong claim, ký **HS256** |
| Token store | **Redis 7.2** (Docker, `docker-compose.dev.yml`) | blacklist access token + whitelist refresh token + cache permission |
| UI kit | Angular Material 22 (theme M3 `azure-blue`) | component |
| Styling | Tailwind CSS 3.4 | layout/utility, config từ laptopshop |
| Media | Cloudinary (cloudinary-http5 2.0.0) | avatar ảnh |
| Test BE | (chưa có mock DB — chạy thẳng Aiven) | `mvn compile` + run profile dev |
| Test FE | Vitest 4.1.11 | 4 test pass |

---

## 2. Backend — `domestic-flight-booking-web-backend`

### 2.1 Ngôn ngữ & framework

| Công nghệ | Version | Vai trò |
|---|---|---|
| Java | 17 | runtime |
| Spring Boot | 4.1.1 | framework nền |
| Spring Data JPA | (Boot 4.1.1) | repository + entity |
| Spring Validation | (Boot 4.1.1) | bean validation (`@Valid`) |
| Spring Web MVC | (Boot 4.1.1) | REST controller |
| Spring Security + OAuth2 Resource Server | (Boot 4.1.1) | JWT xác thực/phân quyền |
| Hibernate ORM | 7.4.5 | JPA provider |

### 2.2 Persistence & migration

| Công nghệ | Version | Vai trò |
|---|---|---|
| PostgreSQL (Aiven) | 18.6 | database chính |
| HikariCP | (Boot 4.1.1) | connection pool |
| Flyway | 12.4.0 | schema migration (`V1/V2/V3`) |
| **Redis (Docker)** | **7.2-alpine** | **blacklist access token + whitelist refresh token + cache permission** |

**Quan trọng (Boot 4 modular):** Flyway auto-config nằm trong module `spring-boot-flyway` — chỉ thêm `flyway-core` + `flyway-database-postgresql` thì Flyway **không chạy**. Bắt buộc có đủ 3 dependency.

### 2.3 Tiện ích (libs)

| Công nghệ | Version | Vai trò |
|---|---|---|
| Lombok | (Boot 4.1.1) | `@Getter/@Setter`, bỏ boilerplate |
| MapStruct | 1.6.3 | map Entity ↔ DTO |
| lombok-mapstruct-binding | 0.2.0 | cho MapStruct hoạt động cùng Lombok |
| Cloudinary | 2.0.0 (http5) | lưu/trả ảnh (avatar) |

### 2.4 Cấu trúc package

```
src/main/java/com/example/domesticflightbookingwebbackend/
├── entity/        # 27 entity + 14 enum (đúng 30 bảng v0)
├── repository/    # 27 interface JpaRepository<T, Long>
└── (sắp tới) dto/ controller/ service/ mapper/ config/ security/
src/main/resources/
├── db/migration/  # V1__init_schema.sql, V2__seed_rbac.sql, V3__seed_master_data.sql
├── application.properties        # spring.profiles.active=dev
└── application-dev.properties    # datasource (Aiven) + cloudinary + JPA validate
```

### 2.5 Quy ước code

- Entity: `@Getter @Setter`, `@Id @GeneratedValue(IDENTITY)`, enum → `@Enumerated(STRING)` + `@Column(length)`, FK → `@ManyToOne(fetch=LAZY) @JoinColumn`, TEXT → `@Column(columnDefinition="TEXT")`, `NUMERIC(p,s)` → `@Column(precision, scale)` BigDecimal. **Không** `@OneToMany`, **không** soft-delete tự bịa (khớp v0).
- Schema do Flyway quản lý, Hibernate chỉ `ddl-auto=validate`.

---

## 3. Frontend — `domestic-flight-booking-web-frontend`

### 3.1 Framework & thư viện

| Công nghệ | Version | Vai trò |
|---|---|---|
| Angular | 22.1.5 (CLI 22.1.7) | SPA framework |
| TypeScript | 6.0.2 | ngôn ngữ |
| RxJS | 7.8 | reactive programming |
| Angular Material | 22.1.7 | component UI (form, table, dialog…) |
| Angular CDK | 22.1.7 | infrastructure Material |
| Tailwind CSS | 3.4.19 | layout/utility |
| PostCSS | 8.5.28 | build Tailwind |
| Autoprefixer | 10.6.1 | prefix CSS |
| Vitest | 4.1.11 | unit test |
| jsdom | 28 | môi trường DOM cho test |
| Prettier | 3.8 | format code |

### 3.2 Cấu trúc `src/app/`

```
src/app/
├── core/                          # dùng chung, nạp 1 lần
│   ├── models/                    # api-response, auth
│   ├── services/                  # auth.service, token.service
│   ├── interceptors/              # auth.interceptor (gắn JWT + xử lý lỗi)
│   └── guards/                    # auth.guard, staffGuard, adminGuard
├── shared/
│   ├── material.ts                # MATERIAL_IMPORTS (mảng module Material)
│   └── components/                # page-header, empty-state, confirm-dialog, coming-soon
├── features/
│   ├── client/                    # layout + pages (home, login, register)
│   └── admin/                     # layout + pages (dashboard)
├── app.routes.ts                  # lazy load theo khu vực + guard theo role
├── app.config.ts                  # provideHttpClient(+interceptor), provideRouter
└── app.ts                         # root component
src/
├── environments/environment.ts    # apiBaseUrl = http://localhost:8080/api/v1
├── material-theme.scss            # theme M3 azure-blue
└── styles.css                     # @tailwind + global reset
```

### 3.3 Quy ước code

- **Standalone components**, `ChangeDetectionStrategy.OnPush`, `signal`/`input()`.
- Material lo việc của component (button, form field, table, dialog); Tailwind lo bố cục/khoảng cách/responsive/màu trạng thái.
- Giao tiếp API: mọi response bọc `ApiResponse<T>` (`{code, message, data}`); danh sách trả `{items, pagination}`.
- JWT lưu `fb_access_token` + user `fb_current_user` trong localStorage (bọc try/catch), bearer gắn qua interceptor.

---

## 4. Database — 30 bảng (v0)

| Nhóm | Bảng |
|---|---|
| RBAC (5) | `users`, `roles`, `permissions`, `user_roles`, `role_permissions` |
| Master data (11) | `airlines`, `cities`, `airports`, `aircrafts`, `seat_types`, `seat_configurations`, `promotions`, `vouchers`, `user_vouchers`, `news_categories`, `news_items`, `news_category_mapping` |
| Flight & fare (5) | `flights`, `fare_classes`, `flight_fares`, `flight_seats`, `baggage_options` |
| Booking core (9) | `bookings`, `booking_flights`, `passengers`, `seat_holds`, `booking_baggages`, `voucher_usages`, `payments`, `e_tickets` |

Quy ước: PK `BIGINT GENERATED BY DEFAULT AS IDENTITY`, enum = `VARCHAR + CHECK`, thời gian `TIMESTAMPTZ`, tiền `NUMERIC(12,2)` + `VND`.

---

## 5. Công cụ phát triển (môi trường máy user)

| Công cụ | Chi tiết |
|---|---|
| Java build | Maven — local repo `D:\Maven\.m2` (xem `D:\Maven\.m2\settings.xml`) |
| Node | nvm root `C:\Users\Admin\AppData\Local\nvm`, version `v24.18.1` (runtime) |
| npm | 10.9.4 — lấy từ node `v22.21.0` (bản `v24.18.1` **thiếu npm**) |
| DB client | DBeaver → Aiven (user tự kết nối) |
| Hạ tầng dev | `docker-compose.dev.yml` (Redis 7.2 + Postgres 18 local) — chỉ chạy hạ tầng, BE/FE chạy bằng IDE |
| Môi trường | file `.env` ở gốc repo (copy từ `.env.example`), gồm: `FLIGHTBOOKING_DB_URL`, `DB_FB_USERNAME`, `DB_FB_PASSWORD`, `CLOUDINARY_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`. Spring nạp qua `spring.config.import=optional:file:./.env[.properties]`. Lưu ý: biến môi trường OS/IDE **đè** giá trị trong `.env` (xem mục 7). |

---

## 6. Tài liệu tham chiếu (docs/)

| Tài liệu | Vai trò |
|---|---|
| `Database_Design_Domestic_Flight_Booking_v0.docx` | chuẩn schema (30 bảng) |
| `Permission_Matrix_v2.0_3_Phase.docx` | chuẩn phân quyền |
| `Phase_1_Use_Case_Specification_v2.3_...docx` | chuẩn nghiệp vụ Phase 1 |
| `API_Design_Website_Dat_Ve_May_Bay_v0.1.md` / `.docx` | hợp đồng API Backend ↔ Frontend |
| `Jira_Plan_Website_Dat_Ve_May_Bay_Sprint_2-6_2026_Revised.docx` | kế hoạch sprint |

---

## 7. Cấu hình môi trường (.env)

Repo BE dùng file `.env` ở **gốc repo** để chứa biến môi trường, thay vì phải set thủ công trên từng máy.

| File | Commit? | Vai trò |
|---|---|---|
| `.env.example` | ✅ có | Mẫu, ai clone về copy thành `.env` rồi điền giá trị thật |
| `.env` | ❌ không (gitignore) | Chứa thông tin thật, chỉ nằm trên máy mỗi người |

**Cách dùng:**
```bash
cp .env.example .env     # rồi mở .env điền giá trị thật
./mvnw spring-boot:run   # chạy từ gốc repo
```

**Định dạng `.env`** (Spring đọc theo cú pháp `.properties`, xem `spring.config.import` trong `application.properties`):
- Viết `KEY=value`, **không** `export`, **không** bọc nháy `"..."`.
- `FLIGHTBOOKING_DB_URL` **bắt buộc** có tiền tố `jdbc:`.

**Thứ tự ưu tiên (quan trọng):** biến môi trường OS/IDE **đè** giá trị trong `.env`. Nên nếu trong IntelliJ Run Configuration còn env var cũ, nó sẽ thắng `.env`. Muốn chắc chắn dùng `.env`, hãy xóa env var trong run config.

**Trạng thái hiện tại (2026-10-11):** database Aiven tên là **`defaultdb`** (không phải `flightbooking`), dùng `btree_gist` 1.8 (đã kiểm tra `CREATE EXTENSION` thành công).

---

*File này là tổng kết kỹ thuật chính thức; cập nhật khi thay đổi stack/version.*