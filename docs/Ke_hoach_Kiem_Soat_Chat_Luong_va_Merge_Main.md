# Kế hoạch Kiểm soát Chất lượng & Merge vào `main`

**Repo:** `domestic-flight-booking-web-backend` (nhánh `binh`)
**Ngày lập:** 13/10/2026
**Bối cảnh:** Phase 1 backend đã xong toàn bộ 8 cụm (45/45 unit test pass, verify E2E bằng curl thủ công). Trước khi merge `binh` → `main`, cần lấp khoảng trống về **kiểm soát chất lượng tự động** và **bằng chứng kiểm thử lưu lại được**.

---

## 1. Mục tiêu & phạm vi

**Mục tiêu:** có lưới kiểm thử tự động đủ để tự tin merge `main`, và có bộ kiểm thử API lưu lại để người khác (Đức Anh, giảng viên) chạy lại được.

**Trong phạm vi:**
- Test tích hợp (integration) khởi động context thật với hạ tầng container.
- Bộ sưu tập Postman + chạy tự động bằng Newman.
- CI GitHub Actions chặn merge khi test fail.
- Tài liệu kiến trúc tổng thể.
- Checklist trước merge.

**Ngoài phạm vi:** viết thêm tính năng, sửa nghiệp vụ, Phase 2/3, viết lại code hiện có.

---

## 2. Hiện trạng — đang có gì, thiếu gì

| Hạng mục | Trạng thái | Ghi chú |
|---|---|---|
| Unit test | ✅ Có — 45 test / 7 file | `common/`, `exception/`, `security/`, `service/` |
| Integration test (context load) | ❌ **Thiếu** | `DomesticFlightBookingWebBackendApplicationTests` chỉ là `placeholder()` rỗng |
| Controller/slice test (`@WebMvcTest`) | ❌ Thiếu | Không có test nào kiểm mapping + phân quyền thật |
| Postman / `.http` collection | ❌ **Thiếu** | Không có file nào trong repo; E2E trước đây làm bằng curl rồi bỏ |
| CI (GitHub Actions) | ❌ Thiếu | Không có `.github/workflows/` |
| Coverage report (JaCoCo) | ❌ Thiếu | Không đo được % phủ |
| Tài liệu kiến trúc tổng thể | ⚠️ Rải rác | Có trong `Tech_Stack_*.md` nhưng chưa có sơ đồ luồng request end-to-end |
| Swagger/OpenAPI | ✅ Có | `OpenApiConfig.java` — có Bearer JWT, dùng được để sinh Postman |

**Rủi ro lớn nhất khi merge:** không có test nào chứng minh **toàn bộ context Spring khởi động được** (Flyway + JPA validate + Security + Redis) trong môi trường sạch. Hiện chỉ có bằng chứng thủ công.

---

## 3. Quyết định về GitHub Spec Kit

**Kết luận: KHÔNG cài.** (User đã chốt 13/10/2026.)

**Lý do:**
1. Spec Kit quản lý *"xây cái gì"* (spec/plan/tasks), **không** quản lý *"xây có đúng không"* — không giải quyết vấn đề kiểm soát chất lượng.
2. Dự án **đã có hệ spec tương đương**: Use Case Spec Phase 1/2/3, Permission Matrix, API Design v0.3, DB Design v0.4, Jira Plan, `Ke_hoach_Trien_Khai_Backend.md`.
3. Phase 1 đã xong → retrofit constitution + spec cho tính năng hoàn thành là overhead gần như thuần.
4. Tránh rủi ro **hai hệ spec song song** (`docs/` vs `specs/`) làm loãng nguồn sự thật.

**Đường trung dung đã chọn:** dùng skill `spec-driven-development` đã cài cho **Phase 2**, và **mượn quy ước** Spec Kit (mỗi feature một thư mục `spec.md` / `plan.md` / `tasks.md`) mà không cần cài tool. Xem lại quyết định này **trước khi bắt đầu Phase 2**.

---

## 4. Kiến trúc tổng thể

### 4.1 Sơ đồ luồng request

```
Angular FE ──HTTP/JSON──►
   │  Envelope mọi response: { "code": int, "message": string, "data": T }
   ▼
┌───────────────────────────────────────────────────────────────────┐
│ Security Filter Chain  (config/SecurityConfig.java)               │
│  • STATELESS — không session                                       │
│  • CustomJwtDecoder → giải mã JWT HS256 → kiểm blacklist ở Redis   │
│  • permitAll: /auth/** (trừ logout/change-password),               │
│    GET public: Airline/City/Airport/News/Promotion, swagger-ui     │
│  • anyRequest().authenticated()                                    │
│  • CustomJwtAuthenticationConverter → map claim → GrantedAuthority │
│  • 401: JwtAuthenticationEntryPoint · 403: CustomAccessDeniedHandler│
└──────────────────────────────┬────────────────────────────────────┘
                               ▼
Controller   BaseCrudController<T, ID, Req, Resp>  +  @PreAuthorize (mỗi module)
             (16 controller, 87 endpoint)
                               ▼
Service      BaseCrudService<T, ID, Req, Resp>  →  16 service cụ thể
                               ▼
Repository   Spring Data JPA (27 repository)  ──►  PostgreSQL (Aiven / Docker local)
                               │
Mapper       MapStruct (6 mapper) · DTO request/response  ── tách entity khỏi API
                               │
Hạ tầng phụ  Redis: blacklist access token · whitelist refresh token ·
             cache permission · reset-password token (TTL 15')
             Cloudinary: upload ảnh (avatar, ảnh News/City/Promotion)
Cross-cutting GlobalExceptionHandler + ErrorCode (1000/1001/1004/1005/1006/9999…)
              ListQuery + PageMapper (phân trang, snake_case, sort whitelist)
              HtmlSanitizer (jsoup) — chống XSS cho content News
```

### 4.2 Quy ước xuyên suốt

| Quy ước | Chi tiết |
|---|---|
| Envelope | `{code, message, data}` — **không** dùng `@JsonInclude(NON_NULL)`, field null vẫn xuất hiện |
| JSON naming | Java camelCase → JSON **snake_case** (`spring.jackson.property-naming-strategy=SNAKE_CASE`) |
| Thời gian | ISO-8601 (`write-dates-as-timestamps=false`), lưu UTC |
| Schema | Do **Flyway** quản lý (V1→V11); Hibernate chỉ `validate` — lệch là app không start |
| Xóa | **Mềm** — mọi `search()` lọc `isDeleted = false` (BR-LIST-04) |
| Phân quyền | `@PreAuthorize` ở tầng **method** controller, không ở tầng class |
| Khóa chính | `BIGINT IDENTITY` (không UUID); mã nghiệp vụ riêng (`booking_code`, `ticket_code`) |
| Profile | `local` (Docker: PG 5433 + Redis 6379) · `dev` (Aiven, đọc `.env`) |

### 4.3 Đánh giá

**Mạnh:** phân tầng sạch đúng chuẩn Spring Boot; generic base CRUD giảm lặp; tách DTO/entity qua MapStruct; xử lý lỗi tập trung; cấu hình theo profile rõ ràng.

**Yếu:** vành ngoài mỏng — **không có lưới test tự động ở tầng tích hợp/API**, và **không có CI chặn merge**. Đây chính là phần kế hoạch này lấp.

---

## 5. Lưới kiểm thử 3 tầng

| Tầng | Công cụ | Chạy bằng | Kiểm cái gì | Cần hạ tầng? |
|---|---|---|---|---|
| **1. Unit** | JUnit 5 + Mockito | `mvn test` | Logic service, mapper, sanitize, error code | Không |
| **2. Integration** | `@SpringBootTest` + Testcontainers | `mvn verify` | Context load, Flyway V1→V11, `validate` pass, bảo mật thật | Có (tự dựng container) |
| **3. API** | Postman + Newman | `newman run …` | Hợp đồng API, phân quyền, envelope, case biên | Có (app đang chạy) |

### 5.1 Tầng 1 — Unit (đã có, giữ nguyên)
45 test hiện tại. Bổ sung khi sửa service.

### 5.2 Tầng 2 — Integration (CẦN LÀM)

**Vấn đề:** test hiện tại bỏ `@SpringBootTest` vì cần DB + Redis thật. Giải pháp: **Testcontainers** tự dựng Postgres + Redis trong lúc test, không phụ thuộc máy dev.

**Việc cần làm:**
1. Thêm dependency `spring-boot-testcontainers`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter` (scope `test`).
2. Tạo `src/test/resources/application-test.properties`:
   - `spring.jpa.hibernate.ddl-auto=validate` (giữ nguyên — đây là chốt chặn).
   - `spring.flyway.enabled=true`, location mặc định `classpath:db/migration`.
   - `jwt.signer-key` giả, `cloudinary.*` để trống.
3. Tạo `AbstractIntegrationTest` (base class):
   - `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("test")`, `@Testcontainers`.
   - Container Postgres 18 + Redis 7.2 dùng `@ServiceConnection` (Spring Boot 4 hỗ trợ) hoặc `@DynamicPropertySource`.
4. Viết **smoke test** `ApplicationSmokeIT`:
   - Context load thành công (đây là test quan trọng nhất — thay `placeholder()`).
   - Flyway đã chạy đủ V1→V11 (query `flyway_schema_history`).
   - `GET /api/airlines` → 200, envelope đúng, `data.content` là mảng.
   - `POST /api/auth/login` sai mật khẩu → code 1002/1007 (INVALID_CREDENTIALS), không phải 500.
5. Viết **auth flow IT** `AuthFlowIT`:
   - register → login (lấy token) → `GET /api/profile` bằng token → 200.
   - refresh token → token mới dùng được.
   - logout → gọi lại API cũ → **401** (chứng minh blacklist Redis hoạt động).
   - Không token gọi endpoint bảo vệ → **401**.
   - Token user thường gọi endpoint ADMIN → **403**.
6. Đổi tên test tích hợp thành `*IT.java` để tách khỏi `*Test.java` (chạy ở pha `verify` nếu dùng failsafe; hoặc giữ surefire cho đơn giản).

**Lưu ý kỹ thuật (từ bug đã gặp):**
- Boot 4 dùng **Jackson 3** — không dùng property Jackson 2.
- Postgres 18 image mount `/var/lib/postgresql` (không phải `.../data`).
- V3 seed ID cố định → đã có `V11__reset_identity_sequences.sql`; test tạo mới bản ghi phải qua V11.

### 5.3 Tầng 3 — API (CẦN LÀM)
Xem mục 6.

---

## 6. Postman collection + Newman (CẦN LÀM)

### 6.1 Sinh collection
1. Chạy app: `docker compose -f docker-compose.dev.yml up -d` → `mvn spring-boot:run -Dspring-boot.run.profiles=local`.
2. Vào `http://localhost:8080/swagger-ui.html` → OpenAPI JSON ở `/v3/api-docs`.
3. Import `/v3/api-docs` vào Postman → có sẵn 87 endpoint.

### 6.2 Cấu trúc đề xuất
```
postman/
├── FlightBooking.postman_collection.json
├── FlightBooking.local.postman_environment.json
└── README.md          # cách chạy newman
```

Biến môi trường: `baseUrl` (`http://localhost:8080/api`), `accessToken`, `refreshToken`, `adminToken`, `userToken`, các `*Id` sinh ra trong lúc chạy.

### 6.3 Nhóm request + assertion (tab Tests)

| Nhóm | Luồng | Assertion chính |
|---|---|---|
| **Auth** | register → login → profile → refresh → logout → gọi lại | 200/201; lưu token vào biến; sau logout API cũ phải **401** |
| **RBAC** | login user thường → gọi endpoint ADMIN | **403** |
| **Master Data** | CRUD Airline → trùng code → xóa mềm → GET list | 201; trùng → **1005**; sau xóa list không còn |
| **Promotion** | tạo → public GET → percent > 100 → scope thiếu `airline_id` | 1000; có `scopes`; **1006**; **1001** |
| **Voucher** | claim → claim lại → hết lượt → store → assign | 1000; **1005**; **1006**; store ẩn khi hết lượt |
| **News** | tạo → publish không có chuyên mục → publish → public → unpublish | **1006**; 1000; XSS `<script>` bị strip; DRAFT không lộ public |

### 6.4 Chạy tự động
```bash
npm install -g newman
newman run postman/FlightBooking.postman_collection.json \
       -e postman/FlightBooking.local.postman_environment.json \
       --reporters cli,htmlextra
```

---

## 7. CI — GitHub Actions (CẦN LÀM)

Repo backend là GitHub (`Binhhihi-lang/domestic-flight-booking-web`), nên dùng **GitHub Actions**.

**File:** `.github/workflows/ci.yml`
```yaml
name: CI
on:
  push: { branches: [main, binh, da, dev] }
  pull_request: { branches: [main] }
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin', cache: 'maven' }
      - run: mvn -B verify      # unit + integration (Testcontainers chạy sẵn trên ubuntu-latest)
```
Testcontainers chạy được trên runner Ubuntu có Docker sẵn — không cần service container thủ công.

**Tùy chọn:** thêm bước chạy Newman nếu muốn test API trong CI (cần dựng app + DB trong workflow — phức tạp hơn, để sau).

---

## 8. Checklist trước khi merge `binh` → `main`

- [ ] `mvn -B verify` pass **toàn bộ** (unit + integration), không skip test nào.
- [ ] Migration V1→V11 chạy sạch trên Postgres trống (Testcontainers chứng minh điều này).
- [ ] `ddl-auto=validate` không báo lệch entity/schema.
- [ ] Postman collection chạy hết, **0 assertion fail**.
- [ ] CI xanh trên PR vào `main`.
- [ ] Rà `.env` **không** bị commit (chỉ commit `.env.example`).
- [ ] Rà không có secret hard-code trong `application-*.properties`.
- [ ] Tài liệu kiến trúc + README hướng dẫn chạy đã cập nhật.
- [ ] Migration lên Aiven đã chạy (hoặc có kế hoạch rõ) — **user tự làm, backup trước**.
- [ ] `git status` sạch, không còn file tạm.

---

## 9. Kế hoạch thi hành theo cụm

| Cụm | Nội dung | Trạng thái |
|---|---|---|
| **Q0** | Viết kế hoạch này + ghi memory | ✅ Xong 13/10/2026 |
| **Q1** | Testcontainers + `AbstractIntegrationTest` + `ApplicationSmokeIT` + `AuthFlowIT` | ⬜ Chưa làm |
| **Q2** | Postman collection + environment + README + chạy Newman | ⬜ Chưa làm |
| **Q3** | GitHub Actions CI + JaCoCo (tùy chọn) | ⬜ Chưa làm |
| **Q4** | Tài liệu kiến trúc tổng thể + README chạy dự án + rà checklist mục 8 | ⬜ Chưa làm |

**Thứ tự ưu tiên:** Q1 → Q2 → Q3 → Q4. Q1 là chốt chặn quan trọng nhất.

---

## 10. Rủi ro

| Rủi ro | Mức | Xử lý |
|---|---|---|
| Testcontainers không chạy được do Docker Desktop tắt | Trung | Bật Docker trước; CI chạy trên Ubuntu có Docker sẵn |
| Testcontainers kéo image chậm lần đầu | Thấp | Chấp nhận; cache image sau lần đầu |
| Boot 4 + Testcontainers `@ServiceConnection` khác Boot 3 | Trung | Nếu lỗi → dùng `@DynamicPropertySource` thủ công |
| Integration test đụng dữ liệu seed V2/V3 | Trung | Dùng `@Transactional` rollback hoặc `@Sql` dọn dẹp |
| Postman collection lệch khi API đổi | Trung | Sinh lại từ `/v3/api-docs`; giữ collection trong repo để review diff |
| Newman cần app + DB đang chạy | Thấp | Ghi rõ trong `postman/README.md` |

---

## 11. Việc user cần làm

1. **Duyệt kế hoạch này**, chốt thứ tự cụm Q1→Q4.
2. **Bật Docker Desktop** trước khi chạy Q1.
3. **Commit** sau mỗi cụm (tôi sẽ báo khi cụm xong) — theo quy tắc user tự commit.
4. **Backup Aiven** trước khi chạy migration V4–V11 lên đó (việc còn lại từ Phase 1).

---

*File này là kế hoạch thi hành kiểm soát chất lượng; cập nhật trạng thái ở mục 9 sau mỗi cụm.*
