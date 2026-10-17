# Kiến trúc tổng thể — Website Đặt Vé Máy Bay Nội Địa

**Phiên bản:** v1.0
**Ngày:** 14/10/2026
**Phạm vi:** cả 2 repo (`domestic-flight-booking-web-backend` + `domestic-flight-booking-web-frontend`)
**Căn cứ:** Database Design v0.5 · API Design v0.4 · Tech Stack 13/10/2026

> Tài liệu này trả lời câu hỏi **"hệ thống gồm những khối nào, chúng nói chuyện với nhau ra sao, và tại sao lại chia như vậy"**. Đây là tài liệu **kiến trúc**, khác với:
> - **Database Design v0.5** — mô tả *dữ liệu* (bảng, cột, quan hệ).
> - **API Design v0.4** — mô tả *hợp đồng* giữa FE và BE (endpoint, request/response).
> - **Tech Stack** — mô tả *công nghệ* dùng cho từng thành phần.

---

## 1. Bức tranh tổng thể (một hình duy nhất)

```
                         NGƯỜI DÙNG (trình duyệt)
                                    │
                                    ▼
        ┌───────────────────────────────────────────────────┐
        │              FRONTEND — Angular 22                 │
        │              (chạy ở localhost:4200)               │
        │                                                     │
        │   features/          core/              shared/    │
        │   ├ admin/pages/     ├ services/        ├ crud/    │
        │   ├ client/pages/    ├ interceptors/    ├ ui/      │
        │   └ ...              ├ guards/          └ components│
        │                      ├ models/                     │
        │                      └ mock/  ← tắt khi nối API    │
        └───────────────────────────┬───────────────────────┘
                                    │  HTTP + JSON (snake_case)
                                    │  Authorization: Bearer <JWT>
                                    ▼
        ┌───────────────────────────────────────────────────┐
        │              BACKEND — Spring Boot 4.1.1           │
        │              (chạy ở localhost:8080)               │
        │                                                     │
        │   ┌─────────── TẦNG WEB ───────────┐                │
        │   │ SecurityConfig → JWT filter    │                │
        │   │ controller/  (+ BaseCrud)      │                │
        │   └────────────────────────────────┘                │
        │   ┌─────────── TẦNG NGHIỆP VỤ ─────┐                │
        │   │ service/  (+ BaseCrudService)  │                │
        │   │ mapper/  (Entity ↔ DTO)        │                │
        │   └────────────────────────────────┘                │
        │   ┌─────────── TẦNG DỮ LIỆU ───────┐                │
        │   │ repository/ (Spring Data JPA)  │                │
        │   │ entity/     (Hibernate ORM)    │                │
        │   └────────────────────────────────┘                │
        └────────┬──────────────────┬──────────────┬─────────┘
                 │                  │              │
                 ▼                  ▼              ▼
        ┌────────────────┐  ┌─────────────┐  ┌────────────────┐
        │  PostgreSQL 18 │  │  Redis 7.2  │  │   Cloudinary   │
        │    (Aiven)     │  │  (Docker)   │  │    (cloud)     │
        │                │  │             │  │                │
        │ Dữ liệu chính  │  │ Token +     │  │ Ảnh (avatar,   │
        │ + Flyway V1–V12│  │ cache quyền │  │ logo, thumbnail)│
        └────────────────┘  └─────────────┘  └────────────────┘
```

---

## 2. Kiến trúc Backend — phân tầng (layered)

Backend là **ứng dụng 1 khối (monolith)** chia **3 tầng**, mỗi tầng chỉ gọi tầng ngay dưới nó:

```
HTTP request
     │
     ▼
┌─────────────────────────────────────────────────────────────┐
│ TẦNG WEB (controller/)                                      │
│ • Nhận request, validate đầu vào (@Valid)                   │
│ • Kiểm tra quyền (@PreAuthorize)                            │
│ • Gọi service, bọc kết quả vào ApiResponse                  │
│ • KHÔNG chứa nghiệp vụ                                      │
│                                                             │
│ BaseCrudController<T,ID,Req,Resp> — 6 endpoint chung        │
│   GET / · GET /{id} · POST · PUT /{id} · PUT /{id}/active   │
│   · DELETE /{id}                                            │
│ → 9 controller master data kế thừa, chỉ khai path + quyền   │
└──────────────────────────┬──────────────────────────────────┘
                           │ gọi (KHÔNG nhảy tầng)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│ TẦNG NGHIỆP VỤ (service/ + mapper/)                         │
│ • Quy tắc nghiệp vụ: kiểm tra ràng buộc, tính giá,          │
│   chuyển trạng thái, phân quyền sở hữu                      │
│ • @Transactional — ranh giới giao dịch                      │
│ • Ném BusinessException (1006) / ResourceNotFound (1004)    │
│                                                             │
│ BaseCrudService<T,ID,Req,Resp> — list/get/create/update/    │
│   toggleActive/delete; lớp con override phần khác nhau      │
│ mapper/ (MapStruct) — Entity ⇄ DTO, KHÔNG lộ Entity ra API  │
└──────────────────────────┬──────────────────────────────────┘
                           │ gọi
                           ▼
┌─────────────────────────────────────────────────────────────┐
│ TẦNG DỮ LIỆU (repository/ + entity/)                        │
│ • repository/ — interface Spring Data JPA (findAll, ...)    │
│ • entity/ — lớp ánh xạ bảng; quan hệ @ManyToOne LAZY        │
│ • Không chứa nghiệp vụ                                      │
└──────────────────────────┬──────────────────────────────────┘
                           │ Hibernate ORM
                           ▼
                    PostgreSQL (Aiven)
```

### 2.1 Vì sao phân tầng như vậy

| Nguyên tắc | Lý do |
|---|---|
| Controller **không** gọi Repository trực tiếp | Nghiệp vụ tập trung một chỗ; đổi cách lưu trữ không phải sửa controller |
| Service **không** trả Entity ra ngoài | Entity có `passwordHash`…; DTO là hợp đồng API, đổi DB không phá FE |
| Mapper tách riêng | Chuyển đổi Entity ⇄ DTO không lẫn vào logic nghiệp vụ |
| `BaseCrud*` dùng chung | 9 module master data giống nhau về hình dạng → viết 1 lần, lớp con chỉ khai phần khác |

### 2.2 Mẫu thiết kế chủ đạo: Template Method

`BaseCrudController` / `BaseCrudService` là **lớp trừu tượng** định nghĩa *khung* thuật toán; lớp con **override** các bước khác nhau:

```
BaseCrudService (abstract)
├── list()          ← dùng chung
├── getById()       ← dùng chung
├── create()        ← dùng chung, gọi createEntity() [abstract]
├── update()        ← dùng chung, gọi updateEntity() [abstract]
├── toggleActive()  ← dùng chung, gọi setActive()   [abstract]
└── delete()        ← dùng chung, gọi softDelete()  [abstract]

Lớp con override: createEntity · updateEntity · softDelete · setActive
                  · search · toResponse · resourceName
```

Ví dụ `SeatTypeService` override `delete()` để **chặn xóa loại ghế đang được sơ đồ máy bay dùng** — phần còn lại thừa hưởng nguyên.

### 2.3 Cấu trúc package backend

```
src/main/java/com/example/domesticflightbookingwebbackend/
├── common/       ApiResponse, PageData, ListQuery, PageMapper, HtmlSanitizer
├── config/       SecurityConfig, CORS, OpenApi, Cloudinary, DataInitializer
├── controller/   BaseCrudController + controller từng module
├── dto/
│   ├── request/  auth · profile · user · masterdata · promotion · voucher · news
│   └── response/ auth · user · rbac · masterdata · promotion · voucher · news
├── entity/       entity + enum (+ entity/redis/: RefreshToken, InvalidatedToken…)
├── exception/    ErrorCode · AppException (+3 lớp con) · GlobalExceptionHandler
├── mapper/       MapStruct: Entity ⇄ DTO
├── repository/   JpaRepository (+ repository/redis/: CrudRepository)
├── security/     JwtService · UserDetailsServiceImpl · SecurityUtils
└── service/      BaseCrudService + service từng module
```

---

## 3. Kiến trúc Frontend — Angular 22

FE chia **3 vùng** theo mức dùng lại:

```
src/app/
├── core/        Dùng MỘT lần cho cả app (singleton)
│   ├── services/     auth · token · base-crud · airline · catalog · sanitize
│   ├── interceptors/ auth.interceptor  ← gắn Bearer token + bắt lỗi 401
│   ├── guards/       auth.guard        ← chặn route khi chưa đăng nhập
│   ├── models/       interface khớp DTO backend (snake_case)
│   └── mock/         dữ liệu giả — bật/tắt bằng environment.useMock
│
├── shared/      Dùng NHIỀU nơi
│   ├── crud/         lớp CRUD dùng chung (bảng + form + dialog)
│   ├── ui/           app-button, app-card, status-badge, empty-state…
│   └── components/   confirm-dialog, page-header, brand-logo…
│
└── features/    Chia theo MÀN HÌNH
    ├── admin/        layout + 14 màn quản trị
    │   └── pages/    airlines · cities · airports · aircrafts · seat-types
    │                 · seat-configurations · promotions · vouchers · news
    │                 · news-categories · users · roles · permissions · dashboard
    ├── client/       layout + màn khách
    │   └── pages/    home · login · register · flight-results · account
    └── style-guide/  trang tham chiếu design system
```

### 3.1 Luồng một request đi qua FE

```
Component (màn hình)
     │ gọi
     ▼
Service (core/services/*.service.ts)   ← BaseCrudService cho module master data
     │ HttpClient.get/post...
     ▼
auth.interceptor                        ← gắn Authorization: Bearer <token>
     │                                    bắt 401 → xóa phiên → guard về login
     ▼
environment.apiBaseUrl                  ← http://localhost:8080/api
     │
     ▼ (khi useMock = true)
mock layer  → trả dữ liệu giả, KHÔNG ra mạng
```

**Điểm quan trọng — lớp mock:** `environment.useMock = true` khiến request không ra backend. Khi BE sẵn sàng, chỉ cần đổi `useMock = false`, **không sửa code**. Đây là cơ chế để 2 repo phát triển song song.

### 3.2 Vì sao FE chia `core / shared / features`

| Vùng | Tiêu chí | Ví dụ |
|---|---|---|
| `core` | Dùng **một lần**, là hạ tầng của cả app | `auth.interceptor`, `auth.guard`, `environment` |
| `shared` | Dùng **nhiều màn**, không thuộc màn nào | `app-button`, `confirm-dialog`, lớp CRUD |
| `features` | Thuộc **một nhóm màn**, có thể lazy-load | màn quản lý hãng bay, trang chủ |

---

## 4. Ba khối hạ tầng (nằm ngoài ứng dụng)

| Khối | Vai trò | Vì sao tách riêng |
|---|---|---|
| **PostgreSQL 18 (Aiven)** | Dữ liệu chính; schema do Flyway V1–V12 quản lý | DB trên cloud, không tự dựng; migration là mã nguồn |
| **Redis 7.2 (Docker)** | Blacklist access token + whitelist refresh token + cache quyền + token reset mật khẩu | Dữ liệu **tạm thời, hết hạn tự động** — không hợp với Postgres. Redis restart thì mất, khách đăng nhập lại |
| **Cloudinary (cloud)** | Lưu ảnh: avatar, logo hãng, ảnh thành phố, thumbnail tin | Không lưu file nhị phân trong DB; CDN + tối ưu ảnh sẵn |

> **Vì sao token để ở Redis mà không để trong Postgres:** token có TTL (hết hạn tự xóa), đọc/ghi cực nhiều mỗi request → Redis nhanh hơn và có TTL sẵn. Xem DB v0.5 mục 2.2.

---

## 5. Luồng xác thực (auth flow) — ví dụ xuyên hệ thống

Đây là luồng cắt qua **cả 3 khối**, cho thấy chúng phối hợp ra sao:

```
1. Khách POST /api/auth/login  (email, password)
        │
        ▼
2. BE: AuthService
   • tìm user trong PostgreSQL
   • so khớp BCrypt password
   • sinh access token (JWT, HS256) + refresh token
   • LƯU refresh token vào Redis (whitelist)
   • trả về { access_token, refresh_token, user }
        │
        ▼
3. FE: TokenService lưu token (localStorage)
        │
        ▼
4. Mọi request sau: auth.interceptor gắn "Authorization: Bearer <access_token>"
        │
        ▼
5. BE: CustomJwtDecoder
   • xác minh chữ ký + hạn
   • kiểm tra token có trong blacklist Redis không
   • đọc claim permissions → dựng SecurityContext
        │
        ▼
6. @PreAuthorize("hasAuthority('AIRLINE_CREATE')") cho qua / chặn (1003)
```

**Refresh token xoay vòng (BE đã làm, FE chưa nối):** endpoint `POST /auth/refresh` phát cặp token mới và **vô hiệu token cũ** trong Redis — chống đánh cắp refresh token. Hiện FE chưa gọi endpoint này (token hết hạn thì `auth.interceptor` xóa phiên và đưa về trang đăng nhập); khi nối API cần bổ sung gọi refresh.

---

## 6. Quyết định kiến trúc & lý do

| # | Quyết định | Lý do | Đánh đổi |
|---|---|---|---|
| 1 | **Monolith phân tầng**, không microservice | Quy mô đồ án, 1 nhóm 2 người | Không scale độc lập từng module — chưa cần |
| 2 | **Lớp CRUD dùng chung** (`BaseCrud*`) ở cả 2 repo | 9 module master data cùng hình dạng | Module có logic lạ phải override — chấp nhận được |
| 3 | **DTO tách khỏi Entity** (MapStruct) | Không lộ `passwordHash`; đổi DB không phá FE | Thêm lớp chuyển đổi |
| 4 | **JWT stateless + Redis** cho token | Không giữ session ở BE; thu hồi token được | Phụ thuộc Redis — nếu Redis chết phải đăng nhập lại |
| 5 | **Flyway** quản lý schema | Migration là mã nguồn, chạy lại được, có version | Không sửa V1–V3 đã chạy (checksum) |
| 6 | **Mock layer ở FE** | 2 repo code song song, FE không chờ BE | Phải giữ mock khớp hợp đồng API |
| 7 | **`/public` vs `/admin`** tách path | Ranh giới quyền ở tầng Spring Security, không phụ thuộc service | Breaking change — FE phải đổi base path |
| 8 | **Soft delete** (`is_deleted`) toàn hệ thống | Giữ lịch sử; dữ liệu giao dịch không xóa cứng | Mọi truy vấn phải lọc `is_deleted = false` |

---

## 7. Xử lý lỗi xuyên tầng

Một cơ chế lỗi thống nhất chạy từ DB tới màn hình:

```
DB CHECK / UNIQUE vi phạm
        │
        ▼
Service ném:  BusinessException(1006) · ResourceNotFound(1004)
              DuplicateResource(1005) · AppException(1001)
        │
        ▼
GlobalExceptionHandler  (@RestControllerAdvice)
   bắt mọi exception → bọc thành ApiResponse { code, message, data }
        │
        ▼
FE: auth.interceptor bắt lỗi HTTP
   • 401/1002 → clearSession() → guard đưa về login
   • lỗi khác → ném Error(message) để component hiện thông báo
```

**Bảng mã lỗi** (đầy đủ ở API v0.4 mục 1.4): `1000` thành công · `1001` validate · `1002` chưa đăng nhập · `1003` thiếu quyền · `1004` không tìm thấy · `1005` trùng · `1006` vi phạm nghiệp vụ · `9999` lỗi hệ thống.

---

## 8. Sơ đồ triển khai (deployment)

### 8.1 Hiện tại (dev)

```
   Máy dev
   ├── Frontend   ng serve        → localhost:4200
   ├── Backend    IDE / mvn       → localhost:8080   (profile: local)
   └── Docker     docker-compose.dev.yml
                  ├── Redis    → localhost:6379
                  └── Postgres → localhost:5433  (chỉ để test migration)

   Cloud
   ├── Aiven PostgreSQL 18.6      ← DB thật
   └── Cloudinary                 ← ảnh
```

### 8.2 Khi chạy thật (dự kiến)

```
   Trình duyệt ──HTTPS──▶ Static hosting (Angular build)
                                │  /api/*
                                ▼
                          Backend (JAR) ──┬──▶ PostgreSQL (Aiven)
                                          ├──▶ Redis (managed)
                                          └──▶ Cloudinary
```

---

## 9. Bản đồ tài liệu — đọc gì khi nào

| Câu hỏi | Đọc tài liệu |
|---|---|
| Hệ thống gồm khối nào, nói chuyện ra sao? | **Tài liệu này** |
| Bảng nào, cột nào, quan hệ nào? | Database Design **v0.5** (mục 3, 4) |
| Vẽ sơ đồ lớp để nộp? | `docs/class-diagram/` (4 file) + DB v0.5 mục 9 |
| Endpoint nào, request/response ra sao? | API Design **v0.4** |
| Dùng công nghệ gì, version nào? | Tech Stack |
| Màn hình nào, UC nào? | Phase 1/2/3 Use Case Specification |
| Ai được làm gì? | Permission Matrix v2.0 |

---

*Hết tài liệu v1.0.*

*Tài liệu này mô tả kiến trúc **đang có** (as-is) của Phase 1, kèm các quyết định đã chốt tới 15/10/2026. Khi thêm module mới (Phase 2–3: Flight, Booking, Payment, E-Ticket), cập nhật mục 1, 5, 6.*

> **Ghi chú 15/10/2026:** hệ thống **không còn ví khách hàng** và **không tự động hoàn tiền**. Khách muốn hoàn tiền liên hệ đại lý (email + số điện thoại trên website). Vì vậy tài liệu này không mô tả module Wallet; Redis **chỉ** còn giữ token và cache quyền (không liên quan ví).
