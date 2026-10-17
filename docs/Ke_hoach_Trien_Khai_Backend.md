# Kế hoạch triển khai Backend — Website Đặt Vé Máy Bay Nội Địa

**Ngày lập:** 12/10/2026
**Phạm vi:** repo `domestic-flight-booking-web-backend` (Spring Boot 4.1.1 / Java 17)
**Căn cứ:** Database Design **v0.4** · API Design **v0.2** · Permission Matrix **v2.0** · Phase 1 Use Case Spec v2.3 · Tech Stack

---

## 0. Mục đích tài liệu

Chốt **thứ tự và nội dung công việc Backend** thành các **cụm bàn giao** (mỗi cụm = 1 lần commit/push). Session sau đọc file này là biết đang ở đâu, làm gì tiếp, không phải dò lại từ đầu.

Trạng thái mỗi cụm ghi ngay trong bảng ở mục 6: `⬜ chưa làm` · `🔄 đang làm` · `✅ xong (đã push)`.

---

## 1. Quyết định đã chốt

| # | Quyết định | Lý do |
|---|---|---|
| 1 | **Khóa chính giữ `BIGINT IDENTITY`**, không đổi sang UUID | Kiểu PK không phải biện pháp bảo mật; rủi ro thật là IDOR do thiếu kiểm tra quyền. BIGINT index gọn, FK nhẹ, `@GeneratedValue(IDENTITY)` là mặc định JPA. V1–V3 đã chạy với BIGINT. Xem mục 1.1 |
| 2 | **Redis (Docker) lưu blacklist access token + whitelist refresh token** | Kế thừa pattern `laptopshop` — xem mục 4. Doc API v0.2 mục 5 để ngỏ 2 điểm này (open item #3, #4); nay chốt làm thật |
| 3 | **Không chạy migration lên Aiven trong session này** | V4–V9 đụng dữ liệu seed đang có. Verify bằng Postgres local trong Docker trước; user tự chạy lên Aiven khi đã ưng |
| 4 | **Kế thừa cấu trúc package của laptopshop** | Đã quen; pattern ApiResponse / mapper / JWT tự chế / split controller đã kiểm chứng |
| 5 | **REST Docs gỡ khỏi pom** (đã làm) | Không dùng ở đâu; dự án đã có springdoc-openapi. Kèm sửa lỗi 2 Maven repo lệch nhau |

### 1.1 Vì sao BIGINT chứ không UUID (ghi lại để không hỏi lại)

- **ID đoán được không phải lỗ hổng.** Lỗ hổng là **IDOR/BOLA** (OWASP API Security Top 10 #1): đổi `/bookings/5` → `/bookings/6`. Chặn bằng cách kiểm tra quyền sở hữu **ngay ở tầng truy vấn** (`findByIdAndUserId(id, currentUserId)`), không phải bằng UUID.
- **Bảo mật nằm ở 3 chỗ khác:** (a) endpoint SELF luôn lấy user từ token, không nhận `user_id` từ client; (b) mọi query theo `{id}` kèm điều kiện sở hữu; (c) thứ lộ ra ngoài và nhạy cảm dùng **mã nghiệp vụ riêng** (`booking_code` = PNR, `ticket_code`) — đúng pattern Stripe (`cus_`, `pi_`).
- **Điểm rủi ro cụ thể Phase 1:** `POST /bookings/{id}/voucher` nhận `user_voucher_id` (tuần tự, đoán được) → service **bắt buộc** kiểm tra dòng `user_vouchers` thuộc user trong token và đang `AVAILABLE`. UUID cũng không cứu được nếu thiếu check này.
- **Khi nào mới đổi:** nếu sau này cần che số lượng user/ngày tạo, hoặc tách microservice phát sinh ID phân tán → dùng **UUIDv7** (RFC 9562, có thứ tự thời gian; **không** dùng v4 vì làm phân mảnh B-tree). Hoặc thêm cột `public_id UUID` riêng cho `users`/`bookings`, PK nội bộ vẫn BIGINT.

---

## 2. Hiện trạng (đo tại 12/10/2026)

| Hạng mục | Trạng thái |
|---|---|
| Database Aiven | V1/V2/V3 đã migrate (**schema v0**, không sửa được — Flyway checksum) |
| BE `entity/` | 42 file (entity + enum) — **theo v0**, lệch v0.4 |
| BE `repository/` | 27 interface `JpaRepository<T, Long>` |
| BE tầng trên repository | ❌ **Không có gì**: chưa có `dto/`, `controller/`, `service/`, `mapper/`, `config/`, `security/`, `common/` |
| BE pom | ✅ đủ dep (data-jpa, security, oauth2-resource-server, validation, flyway, cloudinary, mapstruct, springdoc); ❌ **chưa có** `spring-boot-starter-data-redis` |
| Docker | Có Docker 29.4.0; **daemon chưa chạy** (cần bật Docker Desktop) |
| FE | Nhánh `binh` đã merge từ `da`; model theo **v0.4** (`is_active`/`is_deleted`), `BaseCrudService` dùng envelope + `PageData`, `useMock: true` |

**Kết luận:** khoảng trống lớn nhất vẫn là **toàn bộ tầng trên repository**. Và schema v0 phải nâng lên v0.4 **trước**, vì entity/service/controller đều viết trên schema đó, FE cũng đã theo v0.4.

---

## 3. Cấu trúc package đích

Kế thừa laptopshop, gốc `com.example.domesticflightbookingwebbackend`:

```
├── common/              # ApiResponse, PageData, ListQuery, PageMapper, constants
├── config/              # SecurityConfig, RedisConfig, CloudinaryConfig, OpenApiConfig,
│                        # PricingProperties, CustomJwtDecoder, JwtAuthenticationEntryPoint,
│                        # CustomAccessDeniedHandler, CustomJwtAuthenticationConverter, DataInitializer
├── controller/          # chia theo module (AuthController, AirlineController, ...)
├── dto/
│   ├── request/         # chia subpackage theo module
│   └── response/
├── entity/              # (đã có) + enum
├── exception/           # AppException, ErrorCode, GlobalExceptionHandler
├── mapper/              # MapStruct: Entity ↔ DTO
├── repository/          # (đã có)
├── security/            # JwtService, UserDetailsServiceImpl, SecurityUtils
└── service/             # nghiệp vụ; BaseCrudService ở đây
```

**Quy ước code** (khớp Tech Stack mục 2.5):
- Entity: `@Getter @Setter`, `@Id @GeneratedValue(IDENTITY)`, enum → `@Enumerated(STRING)`, FK → `@ManyToOne(fetch=LAZY) @JoinColumn`, TEXT → `columnDefinition="TEXT"`, `NUMERIC(p,s)` → `BigDecimal`. **Không** `@OneToMany` (tránh N+1 ngoài ý muốn).
- Schema do Flyway quản lý; Hibernate chỉ `ddl-auto=validate` → **entity phải khớp cột chính xác**, sai là app không start.
- DTO dùng `record` khi bất biến; response luôn bọc `ApiResponse<T>`.

---

## 4. Hạ tầng dev — Redis & Postgres qua Docker

Tạo `docker-compose.dev.yml` ở gốc repo BE. **Chỉ chạy hạ tầng**, không chạy backend/frontend (dev chạy bằng IDE).

```yaml
services:
  redis:
    image: redis:7.2-alpine
    container_name: flight_redis
    ports: ["6379:6379"]        # dev cần map ra ngoài để IDE kết nối
    restart: unless-stopped

  postgres:                      # CHỈ để test migration local, không phải DB thật
    image: postgres:18-alpine
    container_name: flight_pg_local
    environment:
      POSTGRES_DB: flightbooking
      POSTGRES_USER: flight
      POSTGRES_PASSWORD: flight
    ports: ["5433:5432"]         # 5433 tránh đụng Postgres khác đang chạy
    volumes: ["flight_pg_data:/var/lib/postgresql/data"]
    restart: unless-stopped

volumes:
  flight_pg_data:
```

Lệnh:
```bash
docker compose -f docker-compose.dev.yml up -d      # bật
docker compose -f docker-compose.dev.yml down       # tắt (giữ volume)
docker compose -f docker-compose.dev.yml down -v    # tắt + xóa data (reset sạch)
```

**Cấu hình Spring** (thêm vào `application-dev.properties`, giữ Aiven làm mặc định):
```properties
# Redis — blacklist access token + whitelist refresh token
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.password=${REDIS_PASSWORD:}
spring.data.redis.ssl.enabled=${REDIS_SSL:false}
```

Thêm vào `.env.example`: `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL`, `JWT_SIGNER_KEY`, `JWT_ACCESS_DURATION`, `JWT_REFRESHABLE_DURATION`.

> ⚠️ **Docker Desktop phải đang chạy** trước khi dùng compose. Hiện tại daemon chưa bật.

---

## 5. Các cụm bàn giao

Mỗi cụm là **một đơn vị commit/push độc lập**, tự chứa, không để lại trạng thái nửa vời.

---

### Cụm 0 — Hạ tầng dev & dependency

**Mục tiêu:** có Redis + Postgres local chạy được, pom đủ dep.

**Việc làm:**
1. `docker-compose.dev.yml` (như mục 4).
2. pom: thêm `spring-boot-starter-data-redis`.
3. `application-dev.properties`: thêm block `spring.data.redis`.
4. `.env.example`: thêm biến Redis + JWT.
5. Bổ sung vào `docs/Tech_Stack_Website_Dat_Ve_May_Bay.md`: Redis 7.2, docker compose dev.

**Done khi:** `docker compose -f docker-compose.dev.yml up -d` → `redis-cli ping` trả `PONG`; `mvn -o clean compile` pass.

---

### Cụm 1 — Migration V4→V9 + refactor entity/repository (schema v0 → v0.4) ⚠️ CHẶN MỌI THỨ

**Mục tiêu:** DB khớp Database Design v0.4. Đây là cụm **bắt buộc làm trước** — mọi service/controller đều viết trên schema này.

**Nguyên tắc viết migration:** `ALTER TABLE ... RENAME` + backfill, **giữ dữ liệu**. V1–V3 không sửa (Flyway checksum). V4 trở đi chưa chạy nên gộp thẳng thay đổi v0.4 vào.

| File | Nội dung |
|---|---|
| `V4__users_rbac_and_master_rename.sql` | `users`: thêm `role_id` (backfill từ `user_roles`) rồi DROP `user_roles`; `status` → `is_active`; thêm `is_deleted`/`deleted_at`/`created_at`/`updated_at`. `seat_configurations` → `aircraft_seats`, `row_number`→`seat_row`, `column_code`→`seat_column`, thêm `UNIQUE(aircraft_id, seat_row, seat_column)`. `seat_types`: thêm `is_active`/`is_deleted`/`deleted_at`/`created_at`/`updated_at`, rename `type_code`→`seat_type_code`, `type_name`→`seat_type_name`, partial unique. Rename `fare_classes` (`class_code`→`fare_class_code`, `class_name`→`fare_class_name`), `promotions` (`promo_code`→`promotion_code`, `promo_name`→`promotion_name`). Soft-delete cols cho mọi bảng master data |
| `V5__flights_and_fares.sql` | `flights`: `status`→`operation_status` (enum mới DRAFT/SCHEDULED/BOARDING/DELAYED/DEPARTED/ARRIVED/CANCELLED), thêm `sales_status` (OPEN/CLOSED), `base_flight_id`→`parent_flight_id`, `sale_open` bỏ. `flight_seats`: `seat_config_id`→`aircraft_seat_id`, `price_at_time`→`seat_surcharge`, `UNAVAILABLE`→`BLOCKED`, thêm `version`, `UNIQUE(flight_id, aircraft_seat_id)`. `flight_fares`: tái cấu trúc thành `base_fare`+`tax_fee`+`effective_from`/`effective_to`, **bỏ `passenger_type`** (backfill chỉ giữ dòng ADULT) |
| `V6__fare_rules.sql` | Tạo bảng `fare_rules` + seed 1 rule mặc định mỗi `fare_class` |
| `V7__promotion_voucher_scopes.sql` | Tạo `promotion_scopes`, `promotion_excluded_flights`, `voucher_scopes`, `voucher_excluded_flights`. `promotions` thêm `max_discount_amount`, `min_fare_amount`, `applicable_trip_type`. `vouchers`: rename `voucher_type`→`discount_type`, `value_amount`→`discount_value`, `scope`→`distribution_type`, `usage_limit`→`claim_limit`, thêm `voucher_name`/`description`/`min_order_amount`/`max_discount_amount`/`applicable_trip_type`/`claimed_count`; bỏ `per_user_limit`. `user_vouchers`: `assigned_at`→`received_at`, thêm `expires_at`/`booking_id`/`discount_amount`/`source`, status thêm `RESERVED`. Chuyển dữ liệu `voucher_usages` → `user_vouchers` rồi DROP |
| `V8__booking_core.sql` | `booking_flights` → `booking_segments`, `price_snapshot`→`fare_amount_at_booking`, thêm `fare_rule_id`/`promotion_id`/`promotion_discount_amount`. `bookings`: thêm bảng giá chi tiết (`fare_amount`, `promotion_discount_amount`, `voucher_discount_amount`, `seat_surcharge_amount`, `baggage_amount`), `total_amount` giữ; `snapshot_currency`→`currency`; `expires_at`→`payment_expires_at`; thêm `cancelled_at`/`cancel_reason`/`applied_refund_percent`/`refund_amount`/`updated_at`. `seat_holds`: thêm `passenger_id`/`surcharge_at_booking`, `held_until`→`hold_expires_at`, partial unique index `(flight_seat_id) WHERE status IN (HOLD, CONFIRMED)`. `booking_baggages`: `price_snapshot`→`unit_price_at_booking`. `payments`: `provider`→`gateway_name`, `gateway_ref`→`gateway_transaction_id`, `UNIQUE(gateway_name, gateway_transaction_id)`. `e_tickets`: `booking_flight_id`→`booking_segment_id`, bỏ `pnr_code` (lấy từ `bookings.booking_code`), thêm `status`/`issued_at`, `UNIQUE(booking_segment_id, passenger_id)` |
| `V9__wallets.sql` | Tạo `wallets` (+ `version`), `wallet_transactions`. Tạo ví số dư 0 cho CUSTOMER hiện có |

**Refactor code kèm cụm này:**
- **Xóa:** `UserRole`, `VoucherUsage` (entity + repository).
- **Đổi tên:** `SeatConfiguration`→`AircraftSeat`, `BookingFlight`→`BookingSegment` (+ repository tương ứng).
- **Thêm entity + repository:** `FareRule`, `PromotionScope`, `PromotionExcludedFlight`, `VoucherScope`, `VoucherExcludedFlight`, `Wallet`, `WalletTransaction`.
- **Sửa entity:** `User` (role_id, is_active/is_deleted/deleted_at), `Flight`, `FlightFare` (bỏ passenger_type), `SeatType`, `Promotion`, `Voucher`, `UserVoucher`, `SeatHold`, `ETicket`, `Booking`, `FlightSeat`, `BookingBaggage`, `Payment`, `Airline`, `City`, `Airport`, `Aircraft`, `BaggageOption`, `FareClass`, `NewsCategory`, `NewsItem`.
- **Enum:** thêm `FlightOperationStatus`, `SalesStatus`, `TripTypeCondition` (ANY/ONE_WAY/ROUND_TRIP); sửa `FlightSeatStatus` (`UNAVAILABLE`→`BLOCKED`), `UserVoucherStatus` (+`RESERVED`), `NewsStatus` (+`UNPUBLISHED`); rà lại `FlightStatus`/`Status`/`LifecycleStatus` xem còn dùng không.
- **Enum trong entity** giữ ở package `entity/` (đúng quy ước hiện tại).

**Done khi:**
1. `docker compose -f docker-compose.dev.yml up -d postgres`
2. Chạy Flyway V1→V9 trên Postgres local **từ đầu** (V1–V3 rồi V4–V9) → không lỗi.
3. App start với profile trỏ Postgres local + `ddl-auto=validate` → **validate pass** (đây là bằng chứng entity khớp schema).
4. `mvn -o clean compile` pass.

**Không làm trong cụm này:** không chạy lên Aiven (quyết định #3).

---

### Cụm 2 — Lớp nền common

**Mục tiêu:** có khuôn response/lỗi/phân trang dùng chung cho mọi module.

**Việc làm:**
- `common/ApiResponse<T>` — envelope `{code, message, data}`; static factory `success()`, `error(ErrorCode)`.
- `common/PageData<T>` — `{items, pagination{page,size,total_items,total_pages}}`.
- `common/ListQuery` — parse `page`/`size`/`keyword`/`sort` (+ validate: `page ≥ 1`, `1 ≤ size ≤ 100`).
- `common/PageMapper` — chuyển `Page<T>` → `PageData<D>`.
- `exception/ErrorCode` — enum 9 mã ở API Design 1.4 (`1000`…`1008`, `9999`) kèm HTTP status + message tiếng Việt.
- `exception/AppException` (+ `BusinessException`, `ResourceNotFoundException`, `DuplicateResourceException`).
- `exception/GlobalExceptionHandler` — `@RestControllerAdvice`, map exception → `ApiResponse`; validate lỗi → `data` là mảng `{field, message}`; không lộ stack trace.
- `service/BaseCrudService<T, ID, Req, Resp>` + `controller/BaseCrudController<T, ID, Req, Resp>` — mẫu để nhân bản 7 module master data.

**Done khi:** có unit test cho `GlobalExceptionHandler` + `PageMapper`; `mvn -o test` pass.

---

### Cụm 3 — Security, JWT & Redis token

**Mục tiêu:** xác thực/phân quyền chạy được, có blacklist/refresh token.

**Việc làm:**
- `entity/redis/InvalidatedToken` (`@RedisHash("INVALIDATED_TOKEN")`, TTL = thời gian còn lại của token).
- `entity/redis/RefreshToken` (`@RedisHash("REFRESH_TOKEN")`, `@Indexed userId`, `@Indexed deviceId`).
- `entity/redis/CachedAuthorities` (`@RedisHash("USER_AUTHORITIES")`, TTL 300s) — cache permission.
- 3 repository tương ứng (`CrudRepository`).
- `security/JwtService` — tạo/parse token, claim `sub` (userId), `email`, `jti`, `permissions`.
- `config/CustomJwtDecoder` — **check blacklist trước** khi verify chữ ký.
- `config/CustomJwtAuthenticationConverter` — đọc `CachedAuthorities`, miss thì query DB rồi cache.
- `config/SecurityConfig` — stateless; `permitAll` cho `/api/auth/**` + public GET; còn lại authenticated; `@EnableMethodSecurity` cho `@PreAuthorize("hasAuthority('XXX')")`.
- `config/JwtAuthenticationEntryPoint` (→ `1002`), `config/CustomAccessDeniedHandler` (→ `1003`).
- `security/UserDetailsServiceImpl`, `PasswordEncoder` (BCrypt).
- `config/RedisConfig` (nếu cần serializer tùy chỉnh).
- `config/DataInitializer` — **không seed role/permission** (đã seed ở V2; role cố định theo BR-RBAC-03). Chỉ dùng để tạo tài khoản ADMIN đầu tiên nếu DB trống.

**Cần chốt trước khi code** (mục 7): thuật toán ký — **đề xuất HS256** (kế thừa laptopshop, đơn giản, 1 service). Ví dụ trong API Design dùng `RS256` chỉ là minh hoạ chuỗi.

**Done khi:** unit test `JwtService` (tạo/parse/hết hạn) + test login→logout→token bị từ chối; `mvn -o test` pass.

---

### Cụm 4 — Auth & Account (API Design 2.1)

**Endpoint:** `POST /auth/register` · `/auth/login` · `/auth/logout` · `/auth/refresh` · `/auth/password-reset/request` · `/auth/password-reset/confirm` · `/auth/change-password`

**Việc làm:** `AuthController`, `AuthService`, DTO request/response, mapper.
- Register: gán cứng role `CUSTOMER`, **tạo ví số dư 0 cùng transaction** (DB v0.4 A.1), hash BCrypt.
- Login: trả `access_token` + `refresh_token` + user + `permissions`; sai → `1007` (message không phân biệt sai email/mật khẩu); `INACTIVE` → `1008`.
- Logout: đưa `jti` access token vào `InvalidatedToken` (TTL = thời gian còn lại) + xóa refresh token khỏi Redis.
- Refresh: xoay vòng — xóa refresh cũ, cấp cặp mới; giữ hạn tuyệt đối của phiên (không kéo dài vô hạn).
- Password reset: token lưu **Redis** (TTL 15 phút) — nhất quán với hạ tầng đã có, không cần migration. Request **luôn** trả `200` message trung tính (chống dò tài khoản).
- Change password: sai mật khẩu hiện tại → `1007`; đổi xong thu hồi mọi refresh token của user.

**Done khi:** curl/Postman chạy được register → login → refresh → logout → token cũ bị từ chối (`1002`).

---

### Cụm 5 — Profile, User Management & RBAC (API Design 2.2–2.4)

**Endpoint:** `GET/PUT /profile` · `POST /profile/avatar` · `GET/POST/PUT/DELETE /users` · `GET /users/{id}` · `POST /users/{id}/reset-password` · `GET /roles` · `GET /permissions`

**Việc làm:**
- Profile: luôn lấy user từ token; PUT chỉ whitelist `full_name`/`phone`/`avatar_url`; upload avatar qua Cloudinary.
- User Management (ADMIN): tạo user chọn role ngay; PUT sửa info + đổi role + toggle `is_active`; DELETE = soft delete. Chặn ADMIN tự hạ quyền nếu là ADMIN `ACTIVE` cuối cùng → `1006`. Reset password trả mật khẩu tạm.
- RBAC: `GET /roles` trả **mảng trần** (BR-LIST-05); `GET /permissions` có search/filter/sort; **không** POST/PUT/DELETE (BR-RBAC-03).
- Sau khi đổi role/user → **evict `CachedAuthorities`** của user đó.

**Done khi:** ADMIN tạo được user role STAFF; CUSTOMER sửa được hồ sơ; đổi role → permission có hiệu lực ngay (cache đã evict).

---

### Cụm 6 — Master Data (API Design 2.5–2.6)

**Endpoint:** CRUD `/airlines` · `/cities` · `/airports` · `/aircrafts` · `/seat-types` · `GET/PUT /aircrafts/{id}/seat-map`

**Thứ tự:** **Airline làm mẫu thật kỹ** (dùng `BaseCrudController`/`BaseCrudService` ở cụm 2) → City → Airport → Aircraft → SeatType → SeatMap.

**Quy tắc chung:**
- GET public cho Airline/City/Airport (chỉ `is_active` + `is_deleted=false`); **Aircraft KHÔNG public** (chỉ STAFF/ADMIN).
- `code` là định danh nghiệp vụ — **không cho đổi qua PUT**.
- DELETE = soft delete (`is_deleted = true`); kiểm tra ràng buộc tham chiếu trước (Airline còn Flight → `1006`).
- Airport: FK `city_id`, response lồng `city`; `city_id` không tồn tại → `1004`.
- SeatMap: `PUT` nhận **toàn bộ** danh sách ghế, **thay thế** trong 1 transaction. Ràng buộc: không trùng `(seat_row, seat_column)`; `seat_type_id` tồn tại + `is_active`; **tổng ghế = `aircrafts.seat_capacity`**; máy bay chưa có `flight_seats` nào → nếu có, từ chối `1006`.
- Whitelist keyword/filter/sort theo bảng ở API Design 2.5.

**Done khi:** Airline đi trọn vòng list→create→edit→toggle→soft-delete→guest list; 5 module còn lại theo cùng khuôn.

---

### Cụm 7 — Promotion, Voucher & News (API Design 2.7–2.9)

**Endpoint:** CRUD `/promotions` · `/vouchers` · `/voucher-store` · `/vouchers/{id}/claim` · `/vouchers/{id}/assign` · `/my-vouchers` · `/news` (+publish/unpublish) · `/news-categories`

**Việc làm:**
- Promotion: quản lý định nghĩa + `scopes`/`excluded_flight_ids` (thay toàn bộ khi PUT); public list chỉ thấy đang hiệu lực.
- Voucher: `distribution_type` GLOBAL/ASSIGNED; Claim dùng **UPDATE có điều kiện** (`claimed_count < claim_limit`) → 1 dòng bị ảnh hưởng mới tạo `user_vouchers`; hết lượt → `1006`; nhận trùng → `1005`.
- `GET /voucher-store` (kho voucher GLOBAL còn lượt, kèm cờ `claimed`); `GET /my-vouchers` (đọc `user_vouchers` của user trong token).
- News: tạo mặc định `DRAFT`; publish/unpublish tách riêng; **sanitize HTML bằng jsoup** chống XSS (cần thêm dep `jsoup`); publish phải có ≥1 category (BR-NEWS-03).
- News Category: **không public** (khác News); DELETE soft delete; còn News tham chiếu → `1006`.

**Done khi:** CUSTOMER claim được voucher + thấy trong ví; ADMIN tạo news → publish → guest đọc được.

---

### Cụm 8 — Test, hardening & tài liệu

- Unit test service cho Auth/User/MasterData (mock repository).
- Integration test nhịp chính: register → login → admin CRUD Airline → claim voucher.
- Kiểm tra `@PreAuthorize` phủ đủ 59 endpoint (đối chiếu mục 6 API Design).
- Rà BR-LIST-04: mọi truy vấn lọc `is_deleted = false`; dữ liệu DRAFT/chưa publish không lộ.
- Cập nhật `docs/Tech_Stack_Website_Dat_Ve_May_Bay.md` (Redis, docker, JWT algorithm).
- Cập nhật API Design v0.2 → v0.3 cho phần mở rộng scope (refresh token, Redis).

---

## 6. Bảng theo dõi cụm

| Cụm | Nội dung | Phụ thuộc | Trạng thái |
|---|---|---|---|
| 0 | Hạ tầng dev (Docker Redis+Postgres, pom dep) | — | ✅ 12/10/2026 |
| 1 | Migration V4→V9 + refactor entity/repo | 0 | ✅ 12/10/2026 |
| 2 | Lớp nền common (ApiResponse, exception, BaseCrud) | 1 | ✅ 12/10/2026 |
| 3 | Security + JWT + Redis token | 2 | ✅ 12/10/2026 |
| 4 | Auth & Account | 3 | ✅ 13/10/2026 |
| 5 | Profile + User Management + RBAC | 4 | ✅ 13/10/2026 |
| 6 | Master Data (Airline mẫu → 5 module) | 2 | ✅ 13/10/2026 |
| 7 | Promotion + Voucher + News | 6 | ✅ 13/10/2026 |
| 8 | Test, hardening, tài liệu | 4–7 | ✅ 13/10/2026 |
| + | **V10** — ảnh News/City/Promotion + `permissions.module` + 2 quyền mới | 1 | ✅ 12/10/2026 |
| **10** | **`color_hex` loại ghế (V12)** | 8 | ⬜ **1 · làm trước** |
| **9** | **Vá 4 lỗ hổng bảo mật + bỏ ví (V13)** | 8, 10 | ⬜ **2 · ưu tiên cao** |
| **11** | **Tách `/admin` `/public` + bỏ `/v1` + bulk** | 9, 10 | ⬜ **3 · rủi ro cao** |

**Chi tiết 3 cụm mới ở mục 5b.** Thứ tự cố định **10 → 9 → 11**: Flyway chạy theo số tăng dần nên V12 phải có trước V13.

**Cụm 6 chỉ phụ thuộc cụm 2** (không cần chờ Security) → có thể chạy song song với cụm 3–5 nếu cần tăng tốc.

**Đã xong trước kế hoạch này:** sửa lỗi pom (gỡ REST Docs + đồng bộ 2 Maven repo về `D:\Maven\.m2\repository`).

**Ghi chú Cụm 0 (12/10/2026):** Docker daemon đã bật (29.4.0). Redis `PONG` + Postgres 18.6 local up. Postgres image 18+ đổi layout volume → mount phải là `/var/lib/postgresql` (không phải `/var/lib/postgresql/data`), nếu không container restart loop.

**Ghi chú Cụm 1 (12/10/2026):**
- Viết xong V4–V9; chạy V1→V9 trên Postgres local sạch: cả 9 version `success = t`.
- Refactor entity: xóa `Status`/`LifecycleStatus`/`FlightStatus`/`VoucherScope` (enum cũ), thêm `FlightOperationStatus`/`SalesStatus`/`TripTypeCondition`/`ScopeTargetType`/`UserVoucherSource`/`ETicketStatus`/`WalletTransactionType`/`VoucherDistributionType`; `FlightSeatStatus` (+BLOCKED), `UserVoucherStatus` (+RESERVED), `NewsStatus` (+UNPUBLISHED).
- Xóa `SeatConfiguration`→`AircraftSeat`, `BookingFlight`→`BookingSegment`, `VoucherUsage` (gộp vào `UserVoucher`). Thêm entity+repo: `FareRule`, `PromotionScope`, `PromotionExcludedFlight`, `VoucherScope`, `VoucherExcludedFlight`, `Wallet`, `WalletTransaction`.
- `User` bỏ `@ManyToMany roles` → `@ManyToOne role`; `aircraft_seats.seat_column` = VARCHAR(2).
- **Đã verify:** `mvn clean compile` pass + app start profile `local` với `ddl-auto=validate` **pass** (bằng chứng entity khớp schema v0.4).
- **Lưu ý kỹ thuật:** (1) Postgres rename FK phải dùng `ALTER TABLE ... RENAME CONSTRAINT`, không phải `ALTER INDEX`. (2) `flight_fares` dùng `EXCLUDE USING gist` → cần extension `btree_gist` (migration V5 tự `CREATE EXTENSION IF NOT EXISTS`). (3) `bookings` v0 **không có** cột `expires_at` → V8 phải `ADD COLUMN payment_expires_at` (không phải rename).
- **Chưa chạy lên Aiven** (đúng quyết định #3). Thêm `application-local.properties` (profile `local`) trỏ Docker Postgres/Redis để test không cần biến môi trường.

**Ghi chú Cụm 2 (12/10/2026):**
- `common/`: `ApiResponse<T>` (envelope `{code,message,data}`, static factory `success/error`; **không** `@JsonInclude(NON_NULL)` — hợp đồng 1.2 yêu cầu field null luôn có mặt), `PageData<T>` (record `items` + `pagination{page,size,total_items,total_pages}`), `ListQuery` (page 1-based, size kẹp ≤100, parse `sort=field,dir`, whitelist sort), `PageMapper` (Spring `Page` 0-based → API 1-based; `toPageable` bỏ qua sort ngoài whitelist theo BR-LIST-03).
- `exception/`: `ErrorCode` (9 mã 1000–1008 + 9999, kèm HTTP status), `AppException` + `BusinessException`(1006)/`ResourceNotFoundException`(1004)/`DuplicateResourceException`(1005), `GlobalExceptionHandler` (`@RestControllerAdvice`; validate → 1001 với `data` là mảng `{field,message}`; `DataIntegrityViolation` → 1005; lỗi lạ → 9999 không lộ stack trace).
- `service/BaseCrudService<T,ID,Req,Resp>` + `controller/BaseCrudController<T,ID,Req,Resp>` — 6 hook cho module con (resourceName/toResponse/createEntity/updateEntity/softDelete/setActive/search). Thêm endpoint `PUT /{id}/active?value=` để toggle `is_active`.
- **snake_case JSON**: Boot 4 dùng Jackson 3 (`tools.jackson`) — `Jackson2ObjectMapperBuilderCustomizer` **không còn**; dùng property `spring.jackson.property-naming-strategy=SNAKE_CASE` trong `application.properties` (đã thêm), không cần class Java.
- **Đã verify:** `mvn test` → **19/19 pass** (ListQuery 8, PageMapper 5, GlobalExceptionHandler 5, placeholder 1). Test `contextLoads` cũ đổi thành placeholder vì `@SpringBootTest` cần DB+Redis thật.

**Ghi chú Cụm 3 (12/10/2026):**
- `entity/redis/`: `InvalidatedToken` (blacklist access token, key = jti), `RefreshToken` (whitelist, `@Indexed userId`), `CachedAuthorities` (cache quyền, TTL 300s) + 3 repo `CrudRepository` trong `repository/redis/`.
- `security/`: `JwtService` (**HS256**, claim `sub`=userId, `email`, `roles`, `permissions`, `type`=access/refresh, `jti`), `UserDetailsServiceImpl` (đọc cache Redis → DB, user khóa/xóa mềm → authorities rỗng, KHÔNG cache kết quả rỗng), `SecurityUtils` (`getCurrentUserId` từ token — chặn IDOR).
- `config/`: `CustomJwtDecoder` (check blacklist TRƯỚC khi verify chữ ký), `CustomJwtAuthenticationConverter` (đọc lại quyền từ DB/cache, không tin claim), `SecurityConfig` (stateless, permitAll auth + GET public, `@EnableMethodSecurity`, BCrypt), `JwtAuthenticationEntryPoint`→1002, `CustomAccessDeniedHandler`→1003, `CorsConfig`, `OpenApiConfig`, `DataInitializer` (tạo ADMIN đầu tiên + ví khi DB trống).
- **Đã verify:** `mvn test` → **24/24 pass** (thêm 5 test `JwtServiceTest`); app start profile `local` OK, `DataInitializer` tạo `admin@flightbooking.local` (role ADMIN, is_active) + ví số dư 0; Redis kết nối được.
- **Lưu ý Jackson 3 (Boot 4):** `spring.jackson.serialization.write-dates-as-timestamps` **không còn** → dùng `spring.jackson.datatype.datetime.write-dates-as-timestamps`. Sai key làm app **fail ngay khi start** (`No enum constant ...SerializationFeature.write-dates-as-timestamps`).
- Mặc định tài khoản ADMIN đầu tiên: `admin@flightbooking.local` / `Admin@12345` (override bằng `app.admin.email`/`app.admin.password`).

**Ghi chú V10 (12/10/2026) — bổ sung ngoài DB v0.4:**
- Yêu cầu mới từ FE (merge nhánh `da`): `docs/De_xuat_bo_sung_DB_v0.5_truong_anh.md`. Phản hồi của BE ghi ở **mục 7** của chính file đó.
- `V10__content_images_and_permission_module.sql`: (1) 5 cột ảnh/mô tả/nhãn — `news_items.thumbnail_url`, `cities.image_url`, `promotions.image_url`/`description`/`badge_label`; (2) `permissions.module VARCHAR(30)` + backfill 102 quyền theo tiền tố dài-nhất-khớp (→ 23 module); (3) seed `VOUCHER_CLAIM` + `WALLET_READ_SELF`, cấp CUSTOMER + ADMIN.
- Entity cập nhật: `Permission` (+module), `NewsItem` (+thumbnailUrl), `City` (+imageUrl), `Promotion` (+imageUrl/description/badgeLabel).
- **Đã verify:** V10 migrate OK trên Postgres local (104 quyền, 0 NULL module); `mvn compile` + `validate` pass.
- **Phát hiện:** API v0.2 mục 2.4 **đã hứa** `module` từ trước nhưng schema chưa có — đây là lỗ hổng doc↔DB, nay đã khớp. Tương tự 2 quyền `VOUCHER_CLAIM`/`WALLET_READ_SELF` DB v0.4 A.3 yêu cầu nhưng V2 chưa seed.
- **Không sửa docx v0.4** (quyết định của user) — delta sống ở file đề xuất + migration.

**Ghi chú Cụm 4 (13/10/2026):**
- `dto/request/auth/`: `RegisterRequest`, `LoginRequest`, `PasswordResetRequest`, `PasswordResetConfirmRequest`, `ChangePasswordRequest` (đều `record` + `@Valid`).
- `dto/response/auth/`: `UserResponse`, `AuthenticatedUserResponse`, `LoginResponse`, `PasswordResetResponse`.
- `mapper/UserMapper` — DB v0.4 (`is_active` boolean + `role_id` đơn) → hợp đồng API (`status` chuỗi ACTIVE/INACTIVE + `roles` mảng).
- `service/AuthService` — register (tạo ví số dư 0 cùng transaction), login (1007 không phân biệt sai email/mật khẩu; INACTIVE → 1008), logout (blacklist jti + thu hồi refresh), refresh (xoay vòng, **giữ hạn tuyệt đối**), reset password (Redis TTL 15 phút, thu hồi token cũ, dùng 1 lần), change-password (thu hồi mọi refresh + evict cache quyền).
- `controller/AuthController` — 7 endpoint: register/login/logout/refresh/password-reset request+confirm/change-password.
- `entity/redis/PasswordResetToken` + repo (open item #2 chốt: **Redis TTL 15 phút**, không cần migration).
- **Đã verify E2E thật (curl):** register→login→refresh→logout→token cũ bị từ chối. **11/11 case pass**: register 1000, trùng email 1005, login 1000, sai mật khẩu 1007, email lạ 1007 (không lộ), no-token 1002, path lạ 404, refresh 1000 (token mới khác cũ), logout 1000, token đã logout 1002, refresh cũ đã xoay 1002. Reset password 8/8 pass (chống dò tài khoản, token dùng 1 lần). Change-password 4/4 pass. `mvn test` → **25/25 pass**.
- **BUG ĐÃ SỬA (phát hiện khi test):** `GlobalExceptionHandler` để `NoResourceFoundException` rơi vào handler `Exception` chung → path không tồn tại trả **500 thay vì 404**. Đã thêm handler map cả `NoHandlerFoundException` + `NoResourceFoundException` → 1004. Thêm unit test hồi quy.
- **Lưu ý thiết kế:** đổi mật khẩu chỉ thu hồi **refresh token**, KHÔNG blacklist access token đang lưu hành (theo kế hoạch). Hệ quả: access token cũ còn hiệu lực tới khi hết hạn (mặc định 1 giờ). Muốn thu hồi ngay thì phải blacklist — chưa làm ở Phase 1.

**Ghi chú Cụm 5 (13/10/2026):**
- `config/CloudinaryConfig` + `service/CloudinaryService` (upload avatar, thư mục `flight-booking/avatars`, chỉ nhận `image/*`).
- `service/ProfileService` + `controller/ProfileController` — GET/PUT `/profile` (whitelist `full_name`/`phone`/`avatar_url`), POST `/profile/avatar` (multipart).
- `service/UserService` + `controller/UserController` — CRUD + soft delete + reset-password. Lọc `status`/`role`, keyword `email/full_name/phone`, sort whitelist `email`/`full_name`/`created_at` (dùng `Specification` + `JpaSpecificationExecutor`).
- `service/RbacService` + `controller/RbacController` — `GET /roles` (mảng trần), `GET /permissions` (keyword/module/sort), read-only.
- DTO: `dto/request/profile/*`, `dto/request/user/*`, `dto/response/rbac/*`, `dto/response/user/ResetPasswordResponse`.
- BR đã cài: chặn ADMIN tự hạ quyền/xóa nếu là ADMIN ACTIVE **cuối cùng** → 1006; đổi role/khóa user → **evict cache quyền** (hiệu lực ngay) + thu hồi refresh token; DELETE = soft delete (`is_active=false` + `is_deleted=true`).
- **BUG ĐÃ SỬA (phát hiện khi test):** `PageMapper.toPageable` truyền thẳng tên field API (snake_case `created_at`) vào `Sort.by` của Spring Data → `PropertyReferenceException: No property 'created' found` → **500**. Đã thêm chuyển đổi snake_case → camelCase; `defaultSortField` giờ truyền tên property entity (`createdAt`). Thêm test hồi quy.
- **Đã verify E2E thật (curl):** 21 case pass — profile GET/PUT + no-token 1002; roles trả 3 role; permissions `?module=AUTH` trả 5; user CRUD (create STAFF 1000, role sai 1001, trùng email 1005, list có pagination, sort `created_at,desc` 1000, filter `status`+`role` 1000, update 1000, reset-password 1000, delete soft 1000, GET sau xóa 1004); chặn tự hạ quyền ADMIN cuối 1006; **cache quyền evict hiệu lực ngay** (STAFF bị hạ xuống CUSTOMER → token cũ gọi `/users` trả 1003). `mvn test` → **26/26 pass**.

**Ghi chú Cụm 6 (13/10/2026):**
- Nâng `BaseCrudService`/`BaseCrudController` hỗ trợ filter riêng từng module (`Map<String,String> filters`); base KHÔNG map `@GetMapping` để module tự khai (tránh ambiguous mapping).
- 6 module: Airline (mẫu) · City (có `image_url`) · Airport (FK `city_id`, response lồng `city`) · Aircraft (KHÔNG public) · SeatType (CRUD đầy đủ, DELETE chặn khi đang dùng) · SeatMap (`GET/PUT /aircrafts/{id}/seat-map`).
- SeatMap 4 ràng buộc: không trùng `(seat_row,seat_column)` → 1006; `seat_type` tồn tại + active → 1004/1006; tổng ghế = `seat_capacity` → 1006; máy bay đã có `flight_seats` → 1006.
- Thêm log (`@Slf4j`) ở BaseCrudService + AirlineService; rút gọn javadoc theo yêu cầu user.
- **BUG #1 ĐÃ SỬA (nghiêm trọng):** V3 seed ID cố định nhưng sequence IDENTITY không tiến → `INSERT` mới đụng `airlines_pkey`. Thêm `V11__reset_identity_sequences.sql` `setval` cho 7 bảng seed (roles, permissions, airlines, cities, airports, fare_classes, seat_types).
- **BUG #2 ĐÃ SỬA:** `@PreAuthorize('*_READ_DETAIL')` trên GET detail Airline/City/Airport chặn cả GUEST — sai spec (mục 2.5 ghi **P** = public cho cả GET list lẫn detail). Đã bỏ `@PreAuthorize` ở GET detail 3 module này.
- **Đã verify E2E thật (curl):** 11/11 case — GET public không token 1000 (airlines/airlines{id}/cities{id}/airports{id}, city lồng đúng); POST/DELETE không token 1002; Aircraft không token 1002 (không public); CUSTOMER GET public 1000 nhưng POST 1003. SeatMap: PUT 4 ghế = capacity 1000, GET configured_seats=4, PUT ≠capacity 1006, trùng vị trí 1006, DELETE seat-type đang dùng 1006. `mvn test` → **26/26 pass**.

**Ghi chú Cụm 7 (13/10/2026):**
- Thêm dep **jsoup 1.18.3** (open item #3) + `common/HtmlSanitizer` (Safelist.relaxed, chặn script/iframe/event handler) — sanitize `news_items.content`.
- **Promotion**: CRUD + scopes/excluded (PUT thay toàn bộ); GET công khai chỉ thấy đang hiệu lực (`is_active` + trong khoảng ngày), ADMIN filter `is_active`. Validate: `end_date>=start_date`, `discount_value>0`, PERCENT≤100, trần>0, min≥0 → 1006.
- **Voucher**: CRUD + `voucher_scopes`/`voucher_excluded_flights`; **Claim** dùng `UPDATE ... WHERE claim_limit IS NULL OR claimed_count < claim_limit` (1 dòng mới tạo `user_vouchers`) → hết lượt 1006, nhận trùng 1005; `GET /voucher-store` (GLOBAL đang hiệu lực + còn lượt + cờ `claimed`); `GET /my-vouchers` (SELF, không nhận user_id từ client); `POST /vouchers/{id}/assign` (ASSIGNED, bỏ qua user đã có). Chỉ claim được GLOBAL; chỉ assign được ASSIGNED → 1006.
- **News**: `entity/NewsCategoryMapping` + repo (bảng nối N—N); tạo mặc định DRAFT; **publish** cần ≥1 chuyên mục → 1006; **unpublish** về DRAFT giữ `published_at`; public list chỉ PUBLISHED. **NewsCategory KHÔNG public**, DELETE chặn khi còn bài viết → 1006.
- **Đã verify E2E thật (curl):** Promotion 5/5 (tạo 1000, public GET có scopes, percent>100 1006, scope thiếu airline_id 1001); Voucher 11/11 (claim 1000, claim lại 1005, **hết lượt 1006**, store ẩn khi hết lượt, assign ASSIGNED 1000, assign GLOBAL 1006, my-vouchers có voucher tặng); News 8/8 (**XSS `<script>` bị strip**, DRAFT không lộ ra public, publish 1000, public thấy bài, unpublish về DRAFT + ẩn khỏi public, xóa category còn bài 1006). `mvn test` → **26/26 pass**.
- **Lưu ý:** lỗi "publish 1006" khi test lần đầu là do **script test parse id sai** (sed tham lam), không phải bug code — đã xác nhận lại bằng parse JSON đúng.

**Ghi chú Cụm 8 (13/10/2026) — Phase 1 backend HOÀN THÀNH:**
- **Unit test mới:** `AirlineServiceTest` (7 test — chuẩn hóa code, trùng 1005, status mặc định, 1004, soft delete không xóa cứng, toggle, update không đổi code), `SeatMapServiceTest` (7 test — 4 ràng buộc + upper-case cột + thiếu trường), `HtmlSanitizerTest` (5 test — strip script/onerror/iframe, giữ thẻ an toàn, null). Tổng **45/45 test pass**.
- **Kiểm tra `@PreAuthorize`:** 80 endpoint (không tính base). 15 endpoint không có `@PreAuthorize` — **đã rà từng cái, tất cả là public hợp lệ**: `register`/`login`/`refresh`/`password-reset/request`/`password-reset/confirm` + GET list/detail của Airline/City/Airport/News/Promotion (đúng ký hiệu **P** ở spec). Phủ quyền đầy đủ.
- **Rà BR-LIST-04:** 9/9 service có `search()` đều lọc `isFalse(root.get("isDeleted"))`; `UserService` lọc trong Specification. Không có `findAll()` trần lộ bản ghi xóa mềm. `RbacService` dùng role/permission (catalog cố định, không có cột `is_deleted`) — đúng.
- **Tài liệu cập nhật:** `Tech_Stack_Website_Dat_Ve_May_Bay.md` (V1–V11, jsoup, JUnit/Mockito 45 test, cấu trúc package thật, quy ước snake_case + sort whitelist); tạo **`API_Design_..._v0.3.md`** — chốt 6 open item (Redis reset token, blacklist+refresh token, ADMIN reset trả mật khẩu tạm, News HTML+jsoup) + ghi 3 bổ sung V10 + khác biệt khi triển khai (GET public, `/auth/refresh`, `reset_token` trong response).

**→ Phase 1 backend xong toàn bộ cụm 0–8.** Chưa chạy migration lên Aiven (quyết định #3) — user tự backup rồi chạy V4–V11.

---

## 5b. Các cụm bổ sung (17/10/2026) — CHƯA TRIỂN KHAI

> **Bối cảnh:** sau Phase 1 phát sinh 3 nhóm việc: (a) đồng bộ tài liệu v0.5/v0.4/v2.1, (b) vá lỗ hổng bảo mật phát hiện khi rà code, (c) thay đổi nghiệp vụ (bỏ ví/hoàn tiền).
>
> **Thứ tự đã chốt: Cụm 10 → Cụm 9 → Cụm 11** (số cụm = thứ tự làm). Lý do: Flyway chạy theo số tăng dần nên **V12 (color_hex) phải có trước V13 (drop ví)**. Mỗi cụm commit riêng để dễ rollback.

### Cụm 10 — `color_hex` cho loại ghế (rủi ro thấp) — LÀM TRƯỚC

| # | Việc | File liên quan | Cách làm |
|---|---|---|---|
| 10.1 | Migration **V12** | `V12__seat_type_color.sql` | `ALTER TABLE seat_types ADD COLUMN color_hex VARCHAR(7) NULL;` + `CHECK (color_hex IS NULL OR color_hex ~ '^#[0-9A-Fa-f]{6}$')` |
| 10.2 | Entity `SeatType` | `entity/SeatType.java` | Thêm `@Column(name = "color_hex", length = 7) private String colorHex;` |
| 10.3 | DTO + Mapper | `SeatTypeRequest`, `SeatTypeResponse`, `SeatTypeMapper` | Thêm `color_hex`; validate regex ở request (`@Pattern`) |
| 10.4 | Seat map response | `SeatMapResponse` | Trả `color_hex` trong `seat_type` lồng |
| 10.5 | Test | `SeatTypeServiceTest` (nếu có) | Hex sai → 1001; `null` hợp lệ |

**Nghiệm thu:** tạo/sửa loại ghế có `color_hex`; sai định dạng → 1001; `null` OK; `GET /aircrafts/{id}/seat-map` trả `color_hex`.

---

### Cụm 9 — Vá 4 lỗ hổng bảo mật + bỏ ví (⚠️ ưu tiên cao) — LÀM SAU CỤM 10

| # | Việc | File liên quan | Cách làm |
|---|---|---|---|
| 9.1 | **Chặn `DRAFT` ở chi tiết News** | `NewsController`, `NewsService` | Override `getById`: khách (không có `NEWS_READ_DETAIL`) + `status != PUBLISHED` → ném `ResourceNotFoundException` (**1004**, không phải 1003) |
| 9.2 | **`PUT /news/{id}/active` thiếu quyền** | `NewsController` | Override `toggleActive` với `hasAuthority('NEWS_UPDATE')`. **Khuyến nghị: bỏ hẳn route base** — đã có `publish`/`unpublish` chuyên biệt |
| 9.3 | **ADMIN xóa được ADMIN** | `UserService.delete` | Nếu role bản ghi = `ADMIN` → `BusinessException` 1006 "Không thể xóa tài khoản ADMIN". Giữ ràng buộc "ADMIN cuối cùng" làm lớp 2. **Chỉ áp DELETE**, không áp đổi role |
| 9.4 | Migration **V13** | `V13__drop_wallet_and_refund.sql` | `DROP TABLE wallet_transactions;` → `DROP TABLE wallets;` → `ALTER TABLE bookings DROP COLUMN applied_refund_percent, DROP COLUMN refund_amount;` → xóa `role_permissions` + `permissions` của `WALLET_READ_SELF` |
| 9.5 | Xóa code ví | `entity/Wallet`, `WalletTransaction`, `WalletTransactionType`; `repository/Wallet*Repository`; `service/UserService.create`; `service/AuthService.register`; `config/DataInitializer` | Xóa 3 entity + 2 repository; bỏ đoạn tạo ví ở register / create user / seed admin |
| 9.6 | Cập nhật test | `src/test/...` | Sửa test assert có ví; thêm test cho 9.1/9.2/9.3 |

**Nghiệm thu (curl):**
- Khách GET bài DRAFT → **1004**
- CUSTOMER gọi `PUT /news/{id}/active` → **1003**
- ADMIN xóa ADMIN khác → **1006**; ADMIN xóa STAFF → **1000**
- Register CUSTOMER mới → **không** còn tạo dòng `wallets`
- `mvn test` pass; app start profile `local` (Flyway chạy V12 + V13) OK

---

### Cụm 11 — Tách `/admin` `/public` + bỏ `/v1` + bulk (rủi ro CAO) — LÀM CUỐI

> Chạm **14 controller + SecurityConfig**. Breaking change toàn bộ API — FE phải đổi đồng thời.

| # | Việc | Chi tiết |
|---|---|---|
| 11.1 | Bỏ `/v1` | `/api/v1/<x>` → `/api/<x>` ở mọi controller + `SecurityConfig` (8 matcher) |
| 11.2 | Tách vùng path | Quản trị → `/api/admin/<x>`; khách đọc → `/api/public/<x>` (bảng đầy đủ: API v0.4 mục 2.11) |
| 11.3 | Tách controller cho module 2 vùng | News, NewsCategory, Airline, City, Airport, Promotion — mỗi module **2 controller** (public chỉ GET) |
| 11.4 | `SecurityConfig` | `permitAll` cho `/api/public/**` (chỉ GET); `/api/admin/**` = authenticated; `/api/auth/**` public |
| 11.5 | Endpoint bulk | `PATCH /api/admin/{resource}/bulk` + `DELETE /api/admin/{resource}/bulk` ở `BaseCrudController`/`BaseCrudService` |
| 11.6 | Cập nhật test + curl | Toàn bộ path trong test phải đổi |

**Nghiệm thu:** `/api/public/airlines` không token → 1000; `/api/admin/airlines` không token → 1002; CUSTOMER gọi `/api/admin/users` → 1003; path cũ `/api/v1/*` → 404.

**Phối hợp FE:** `frontend/docs/plans/2026-10-17-yeu-cau-chinh-sua-tu-be.md` (nhánh `binh`).

---

## 6. Bảng theo dõi cụm

| # | Vấn đề | Đề xuất | Ảnh hưởng |
|---|---|---|---|
| 1 | **Thuật toán ký JWT** | **HS256** (kế thừa laptopshop, 1 secret, đơn giản). Ví dụ `RS256` trong doc chỉ là minh hoạ | Cụm 3 |
| 2 | **Nơi lưu reset-password token** | **Redis** (TTL 15 phút) — nhất quán hạ tầng, không cần migration. Tradeoff: Redis restart thì token mất, user request lại | Cụm 4 |
| 3 | **Định dạng `content` News** | **HTML + sanitize jsoup** (thêm dep) | Cụm 7 |
| 4 | **Reset password bởi ADMIN** | Trả mật khẩu tạm trong response (không cần mail server) | Cụm 5 |
| 5 | **Refresh token nằm ngoài Use Case Phase 1** | Doc API v0.2 mục 5 để ngỏ (open item #4). Nay làm thật → **cần cập nhật API Design lên v0.3** | Cụm 3–4 |
| 6 | **Docker Desktop chưa bật** | User bật trước khi chạy cụm 0 | Cụm 0 |

---

## 8. Rủi ro

| Rủi ro | Mức | Xử lý |
|---|---|---|
| Migration V4–V9 sai → mất dữ liệu Aiven | **Cao** | Verify trên Postgres local từ V1 (cụm 1); **backup Aiven** trước khi user tự chạy |
| Entity không khớp schema sau refactor | Cao | `ddl-auto=validate` là chốt chặn: app không start nếu lệch |
| `validate` fail vì cột `NUMERIC`/`TIMESTAMPTZ` khai báo lệch | Trung | Đối chiếu từng entity với V4–V9 khi viết; chạy validate ngay sau cụm 1 |
| Redis chưa chạy → app không start (decoder cần) | Trung | Cụm 0 dựng Redis trước; ghi rõ trong README/HUONG-DAN |
| Scope phình (refresh token, Redis) vượt Use Case Phase 1 | Trung | Đã ghi nhận ở open item #5; cập nhật doc ở cụm 8 |

---

## 9. Việc user cần làm

1. **Bật Docker Desktop** (daemon đang tắt).
2. **Duyệt kế hoạch này** + chốt 5 open item ở mục 7 (đều đã có đề xuất).
3. **Backup DB Aiven** trước khi chạy V4–V9 lên đó (kế hoạch này **không** tự chạy).
4. Push git sau mỗi cụm (tôi sẽ báo khi cụm xong).

---

*File này là kế hoạch thi hành Backend; cập nhật trạng thái ở mục 6 sau mỗi cụm.*
