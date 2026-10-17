# PERMISSION MATRIX v2.1 — Website Đặt Vé Máy Bay Nội Địa

**Phiên bản:** 2.1 (cập nhật 15/10/2026)
**Thay thế:** Permission Matrix v2.0 (3 Phase)
**Căn cứ:** Database Design v0.5 · API Design v0.4 · Phase 1/2/3 Use Case Specification

> **Bản này chuyển từ `.docx` sang `.md`** để cùng định dạng với các tài liệu khác trong `docs/`, và để cập nhật được bằng công cụ soạn thảo văn bản thường.

---

## 1. Phạm vi và mục tiêu

Permission Matrix xác định quyền truy cập của các Actor chính trong phạm vi **3 Phase** của dự án. Tài liệu là baseline để phát triển Functional Requirements, Use Case Catalogue, Use Case Specification, UML, RBAC, API Authorization và Database.

**Phase 4 không thuộc phạm vi báo cáo.**

### 1.1 Thay đổi của v2.1 so với v2.0

| # | Thay đổi | Lý do |
|---|---|---|
| 1 | **Bỏ `WALLET_READ_SELF`** | Hệ thống bỏ ví khách hàng và không tự động hoàn tiền (15/10/2026). Khách liên hệ đại lý để hoàn tiền. |
| 2 | **Đưa `VOUCHER_CLAIM` vào bảng chính** | v2.0 chỉ ghi ở ghi chú cuối, chưa đưa vào bảng phân quyền. Nay đã seed và triển khai ở Phase 1. |
| 3 | **Sửa `FLIGHT_READ_DETAIL` → `FLIGHT_DETAIL_READ`** | v2.0 ghi `FLIGHT_READ_DETAIL`, nhưng DB seed và code dùng `FLIGHT_DETAIL_READ`. Lấy theo DB để khớp. |
| 4 | **Bỏ mục Wallet khỏi phạm vi** | Không còn module ví. |
| 5 | **Ghi rõ `PASSWORD_RESET` là public** | Endpoint quên/đặt lại mật khẩu không cần token (GUEST gọi được). |
| 6 | **Đồng bộ bảng với DB seed thực tế** | Rà soát phát hiện nhiều dòng lệch giữa v2.0 và `V2__seed_rbac.sql` + `V10`. Bảng nay khớp DB. Xem mục 5.10 để biết các điểm lệch và cảnh báo. |

### 1.2 Cách xác định quyền của một role

Trong DB, quyền của mỗi role được seed như sau (nguồn: `V2__seed_rbac.sql` + `V10`):

| Role | Cách cấp | Số quyền |
|---|---|---|
| ADMIN | **Mọi permission** trừ `REGISTER`, `PASSWORD_RESET` (endpoint public) | tất cả |
| STAFF | Danh sách tường minh trong V2 | 59 |
| CUSTOMER | Danh sách tường minh trong V2 + `VOUCHER_CLAIM` ở V10 | 42 |

---

## 2. Phạm vi 3 Phase

| Phase | Scope |
|---|---|
| **PHASE 1 — FOUNDATION & MASTER DATA** | Authentication / User / RBAC · Airline / City / Airport · Aircraft / Seat Configuration · Promotion / Voucher · News / News Category |
| **PHASE 2 — FLIGHT & FARE** | Flight Management · Flight Search / Filter / Sort · FareClass / FareRule · BaggageOption · FlightFare · FlightSeat Inventory |
| **PHASE 3 — BOOKING CORE** | Passenger · One-way / Round-trip · Create Booking · Price Snapshot · Seat Hold · Payment / Retry · Booking Lifecycle · Booking Code / PNR · E-Ticket · Cancel Booking · Dashboard / Statistics |

---

## 3. Actor

| Actor | Loại | Vai trò |
|---|---|---|
| GUEST | End User | Người dùng chưa đăng nhập; sử dụng chức năng Public. |
| CUSTOMER | End User | Khách hàng đã đăng ký/đăng nhập; thực hiện Booking và nghiệp vụ cá nhân. |
| STAFF | Internal User | Nhân viên vận hành; xử lý Flight và Booking, không quản lý User/RBAC. |
| ADMIN | Internal User | Quản trị hệ thống; quản lý User, RBAC, Master Data, Content và nghiệp vụ quản trị. |
| PAYMENT GATEWAY | External Actor | Cổng thanh toán bên ngoài; xử lý và trả kết quả Payment. |

---

## 4. Quy ước Permission

- **CREATE:** tạo bản ghi/nghiệp vụ mới.
- **READ:** xem dữ liệu được phép truy cập.
- **UPDATE:** chỉnh sửa dữ liệu; **không** đồng nghĩa Activate/Deactivate.
- **ACTIVATE / DEACTIVATE:** thay đổi trạng thái sử dụng/hiển thị khi Entity có cơ chế này.
- **READ_SELF / UPDATE_SELF:** chỉ áp dụng trên dữ liệu thuộc chính Customer/Staff/Admin.
- **ASSIGN_ROLE** không còn là Permission/Use Case độc lập trong MVP; việc chọn/thay đổi Role được thực hiện trong Create User / Update User.
- Role trong MVP là **Fixed Role:** CUSTOMER, STAFF và ADMIN. ADMIN không tạo, sửa, Activate/Deactivate hoặc Soft Delete Role.
- **APPLY:** Customer áp dụng Voucher vào Booking. **CLAIM:** Customer nhận voucher GLOBAL từ Kho voucher vào ví của mình.
- Không áp dụng CRUD máy móc cho mọi Entity; Entity có Lifecycle phức tạp dùng Status.

---

## 5. Permission Matrix

Ký hiệu: ✅ = được phép · — = không được phép.

### 5.1 Authentication & Profile

| Module | Permission | GUEST | CUSTOMER | STAFF | ADMIN |
|---|---|---|---|---|---|
| Authentication | REGISTER | ✅ | — | — | — |
| Authentication | LOGIN | ✅ | ✅ | ✅ | ✅ |
| Authentication | LOGOUT | — | ✅ | ✅ | ✅ |
| Authentication | PASSWORD_RESET | ✅ | ✅ | ✅ | ✅ |
| Authentication | PASSWORD_CHANGE_SELF | — | ✅ | ✅ | ✅ |
| Profile | PROFILE_READ_SELF | — | ✅ | ✅ | ✅ |
| Profile | PROFILE_UPDATE_SELF | — | ✅ | ✅ | ✅ |

### 5.2 User & RBAC

| Module | Permission | GUEST | CUSTOMER | STAFF | ADMIN |
|---|---|---|---|---|---|
| User | USER_READ | — | — | — | ✅ |
| User | USER_READ_DETAIL | — | — | — | ✅ |
| User | USER_CREATE | — | — | — | ✅ |
| User | USER_UPDATE | — | — | — | ✅ |
| User | USER_DELETE | — | — | — | ✅ |
| RBAC | ROLE_READ | — | — | — | ✅ |

> **Ghi chú:** quyền xem Permission catalog dùng chung `ROLE_READ` (không có key `PERMISSION_READ` riêng).

### 5.3 Master Data — Airline / City / Airport

| Module | Permission | GUEST | CUSTOMER | STAFF | ADMIN |
|---|---|---|---|---|---|
| Airline | AIRLINE_READ | ✅ | ✅ | ✅ | ✅ |
| Airline | AIRLINE_READ_DETAIL | ✅ | ✅ | ✅ | ✅ |
| Airline | AIRLINE_CREATE | — | — | — | ✅ |
| Airline | AIRLINE_UPDATE | — | — | — | ✅ |
| Airline | AIRLINE_DELETE | — | — | — | ✅ |
| City | CITY_READ | ✅ | ✅ | ✅ | ✅ |
| City | CITY_READ_DETAIL | ✅ | ✅ | ✅ | ✅ |
| City | CITY_CREATE | — | — | — | ✅ |
| City | CITY_UPDATE | — | — | — | ✅ |
| City | CITY_DELETE | — | — | — | ✅ |
| Airport | AIRPORT_READ | ✅ | ✅ | ✅ | ✅ |
| Airport | AIRPORT_READ_DETAIL | ✅ | ✅ | ✅ | ✅ |
| Airport | AIRPORT_CREATE | — | — | — | ✅ |
| Airport | AIRPORT_UPDATE | — | — | — | ✅ |
| Airport | AIRPORT_DELETE | — | — | — | ✅ |

> **Ghi chú v2.1 (khớp DB seed):** STAFF **có** quyền đọc Airline/City/Airport (`*_READ`, `*_READ_DETAIL`) — vì STAFF cần tra cứu hãng/sân bay khi xử lý chuyến bay (Phase 2), và DB seed đã cấp. Quyền **ghi** vẫn chỉ ADMIN (BR-FLIGHT-01: STAFF không quản lý Airline/Airport/Aircraft).

### 5.4 Aircraft & Seat Configuration

| Module | Permission | GUEST | CUSTOMER | STAFF | ADMIN |
|---|---|---|---|---|---|
| Aircraft | AIRCRAFT_READ | — | — | ✅ | ✅ |
| Aircraft | AIRCRAFT_READ_DETAIL | — | — | ✅ | ✅ |
| Aircraft | AIRCRAFT_CREATE | — | — | — | ✅ |
| Aircraft | AIRCRAFT_UPDATE | — | — | — | ✅ |
| Aircraft | AIRCRAFT_DELETE | — | — | — | ✅ |
| Seat Configuration | SEAT_CONFIG_READ | — | — | ✅ | ✅ |
| Seat Configuration | SEAT_CONFIG_READ_DETAIL | — | — | ✅ | ✅ |
| Seat Configuration | SEAT_CONFIG_CREATE | — | — | — | ✅ |
| Seat Configuration | SEAT_CONFIG_UPDATE | — | — | — | ✅ |
| Seat Configuration | SEAT_CONFIG_DELETE | — | — | — | ✅ |

> **Ghi chú v2.1:** STAFF **đọc** được Aircraft/Seat Config (cần khi tạo Flight — chọn máy bay đã có sơ đồ ghế). Quyền ghi chỉ ADMIN. DB seed đã cấp đúng như vậy.

### 5.5 Promotion & Voucher

| Module | Permission | GUEST | CUSTOMER | STAFF | ADMIN |
|---|---|---|---|---|---|
| Promotion | PROMOTION_READ | ✅ | ✅ | ✅ | ✅ |
| Promotion | PROMOTION_READ_DETAIL | ✅ | ✅ | ✅ | ✅ |
| Promotion | PROMOTION_CREATE | — | — | — | ✅ |
| Promotion | PROMOTION_UPDATE | — | — | — | ✅ |
| Promotion | PROMOTION_DELETE | — | — | — | ✅ |
| Voucher | VOUCHER_READ | — | ✅ | ✅ | ✅ |
| Voucher | VOUCHER_READ_DETAIL | — | ✅ | ✅ | ✅ |
| Voucher | VOUCHER_CREATE | — | — | — | ✅ |
| Voucher | VOUCHER_UPDATE | — | — | — | ✅ |
| Voucher | VOUCHER_DELETE | — | — | — | ✅ |
| Voucher | VOUCHER_APPLY | — | ✅ | — | — |
| Voucher | **VOUCHER_CLAIM** *(mới v2.1)* | — | ✅ | — | — |

> **Ghi chú v2.1 (khớp DB seed):**
> - `PROMOTION_READ`/`READ_DETAIL` là **public** — khách xem khuyến mãi đang hiệu lực trên trang chủ. STAFF cũng có.
> - `VOUCHER_READ`/`READ_DETAIL`: **CUSTOMER và STAFF đều có** (DB seed cấp cho cả hai) — CUSTOMER dùng cho ví voucher của mình; STAFF tra cứu khi hỗ trợ booking. **Không** public cho GUEST.
> - `VOUCHER_CLAIM` đưa vào bảng chính từ v2.1 (v2.0 chỉ ghi ở ghi chú).

### 5.6 News & News Category

| Module | Permission | GUEST | CUSTOMER | STAFF | ADMIN |
|---|---|---|---|---|---|
| News Category | NEWS_CATEGORY_READ | ✅ | ✅ | ✅ | ✅ |
| News Category | NEWS_CATEGORY_READ_DETAIL | ✅ | ✅ | ✅ | ✅ |
| News Category | NEWS_CATEGORY_CREATE | — | — | — | ✅ |
| News Category | NEWS_CATEGORY_UPDATE | — | — | — | ✅ |
| News Category | NEWS_CATEGORY_DELETE | — | — | — | ✅ |
| News | NEWS_READ | ✅ | ✅ | ✅ | ✅ |
| News | NEWS_READ_DETAIL | ✅ | ✅ | ✅ | ✅ |
| News | NEWS_CREATE | — | — | — | ✅ |
| News | NEWS_UPDATE | — | — | — | ✅ |
| News | NEWS_DELETE | — | — | — | ✅ |
| News | NEWS_PUBLISH | — | — | — | ✅ |
| News | NEWS_UNPUBLISH | — | — | — | ✅ |

> **Ghi chú v2.1:** khách **đọc** được News Category (dropdown lọc ở trang Tin tức). Endpoint public chỉ trả `id` + `name` của danh mục đang hoạt động — xem API v0.4 mục 2.9.

### 5.7 Flight & Fare (Phase 2)

| Module | Permission | GUEST | CUSTOMER | STAFF | ADMIN |
|---|---|---|---|---|---|
| Flight Search | FLIGHT_SEARCH | ✅ | ✅ | ✅ | ✅ |
| Flight Search | FLIGHT_READ | ✅ | ✅ | ✅ | ✅ |
| Flight Search | **FLIGHT_DETAIL_READ** *(sửa tên v2.1)* | ✅ | ✅ | ✅ | ✅ |
| Flight | FLIGHT_READ | — | — | ✅ | ✅ |
| Flight | FLIGHT_DETAIL_READ | — | — | ✅ | ✅ |
| Flight | FLIGHT_CREATE | — | — | ✅ | ✅ |
| Flight | FLIGHT_UPDATE | — | — | ✅ | ✅ |
| Flight | FLIGHT_ACTIVATE | — | — | ✅ | ✅ |
| Flight | FLIGHT_DEACTIVATE | — | — | ✅ | ✅ |
| FareClass | FARE_CLASS_READ | — | — | ✅ | ✅ |
| FareClass | FARE_CLASS_READ_DETAIL | — | — | ✅ | ✅ |
| FareClass | FARE_CLASS_CREATE | — | — | ✅ | ✅ |
| FareClass | FARE_CLASS_UPDATE | — | — | ✅ | ✅ |
| FareClass | FARE_CLASS_DELETE | — | — | ✅ | ✅ |
| FareRule | FARE_RULE_READ | — | — | ✅ | ✅ |
| FareRule | FARE_RULE_READ_DETAIL | — | — | ✅ | ✅ |
| FareRule | FARE_RULE_CREATE | — | — | ✅ | ✅ |
| FareRule | FARE_RULE_UPDATE | — | — | ✅ | ✅ |
| FareRule | FARE_RULE_DELETE | — | — | ✅ | ✅ |
| BaggageOption | BAGGAGE_READ | — | — | ✅ | ✅ |
| BaggageOption | BAGGAGE_READ_DETAIL | — | — | ✅ | ✅ |
| BaggageOption | BAGGAGE_CREATE | — | — | ✅ | ✅ |
| BaggageOption | BAGGAGE_UPDATE | — | — | ✅ | ✅ |
| BaggageOption | BAGGAGE_DELETE | — | — | ✅ | ✅ |
| FlightFare | FLIGHT_FARE_READ | — | ✅ | ✅ | ✅ |
| FlightFare | FLIGHT_FARE_READ_DETAIL | — | ✅ | ✅ | ✅ |
| FlightFare | FLIGHT_FARE_CREATE | — | — | ✅ | ✅ |
| FlightFare | FLIGHT_FARE_UPDATE | — | — | ✅ | ✅ |
| FlightFare | FLIGHT_FARE_DELETE | — | — | ✅ | ✅ |
| FlightSeat Inventory | SEAT_INVENTORY_READ | — | ✅ | ✅ | ✅ |
| FlightSeat Inventory | SEAT_INVENTORY_READ_DETAIL | — | ✅ | ✅ | ✅ |
| FlightSeat Inventory | SEAT_INVENTORY_MANAGE | — | — | ✅ | ✅ |

> **Ghi chú v2.1 (khớp DB seed):** STAFF có **toàn quyền** trên Flight, FareClass, FareRule, BaggageOption, FlightFare, Seat Inventory (cả đọc lẫn ghi) — đúng BR-FLIGHT-01 (STAFF vận hành chuyến bay và giá). CUSTOMER chỉ **đọc** `FLIGHT_FARE_READ`/`SEAT_INVENTORY_READ` (cần để hiển thị giá và tình trạng ghế khi tìm chuyến), **không** đọc được `FARE_CLASS_READ` hay `BAGGAGE_READ`.
>
> **`FLIGHT_READ`/`FLIGHT_DETAIL_READ` là một key duy nhất** cho cả tìm kiếm (public) và quản lý (STAFF/ADMIN) — khác biệt nằm ở các key **ghi**. Xem API v0.4 mục 4.2.

### 5.8 Booking, Payment & E-Ticket (Phase 3)

| Module | Permission | GUEST | CUSTOMER | STAFF | ADMIN |
|---|---|---|---|---|---|
| Passenger | PASSENGER_CREATE | — | ✅ | — | — |
| Passenger | PASSENGER_READ_SELF | — | ✅ | — | — |
| Passenger | PASSENGER_UPDATE_SELF | — | ✅ | — | — |
| Booking | BOOKING_CREATE | — | ✅ | — | — |
| Booking | BOOKING_READ_SELF | — | ✅ | — | — |
| Booking | BOOKING_READ | — | — | ✅ | ✅ |
| Booking | BOOKING_CANCEL_SELF | — | ✅ | — | — |
| Payment | PAYMENT_CREATE | — | ✅ | — | — |
| Payment | PAYMENT_READ_SELF | — | ✅ | — | — |
| E-Ticket | ETICKET_READ_SELF | — | ✅ | — | — |
| Dashboard | DASHBOARD_READ_OPERATIONAL | — | — | ✅ | ✅ |
| Dashboard | DASHBOARD_READ | — | — | — | ✅ |

> **ĐÃ BỎ khỏi bảng (v2.1):** ~~Wallet | WALLET_READ_SELF~~. Hệ thống không còn ví khách hàng và không tự động hoàn tiền — khách liên hệ đại lý (email + số điện thoại trên website) để yêu cầu hoàn tiền. Xem DB v0.5 mục 3.5 và 5.5.

### 5.9 Notification (chưa triển khai)

| Module | Permission | GUEST | CUSTOMER | STAFF | ADMIN |
|---|---|---|---|---|---|
| Notification | NOTIFICATION_READ_SELF | — | — | — | — |

> Bảng `notifications` chưa triển khai giai đoạn này — key đã seed nhưng chưa có endpoint.

### 5.10 ⚠️ Các điểm lệch giữa Permission Matrix v2.0 và DB seed

Khi rà soát để viết v2.1, phát hiện **nhiều dòng lệch** giữa Permission Matrix v2.0 (file `.docx`) và quyền thực tế đã seed trong `V2__seed_rbac.sql` + `V10`. Bảng ở các mục 5.1–5.9 nay đã **khớp DB** (nguồn sự thật là code/migration). Dưới đây là danh sách để đối chiếu:

| # | Điểm | v2.0 ghi | DB seed thực tế | v2.1 lấy theo |
|---|---|---|---|---|
| 1 | `WALLET_READ_SELF` | có (ghi chú) | có ở CUSTOMER + ADMIN (V10) | **BỎ** (bỏ ví 15/10/2026) |
| 2 | `VOUCHER_CLAIM` | có (ghi chú) | CUSTOMER + ADMIN (V10) | có — đưa vào bảng chính |
| 3 | `FLIGHT_READ_DETAIL` vs `FLIGHT_DETAIL_READ` | `FLIGHT_READ_DETAIL` | `FLIGHT_DETAIL_READ` | **DB** |
| 4 | AIRLINE/CITY/AIRPORT `_READ` cho STAFF | `—` | **có** | **DB** (STAFF cần tra cứu) |
| 5 | AIRCRAFT / SEAT_CONFIG `_READ` cho STAFF | `—` | **có** | **DB** (STAFF chọn máy bay khi tạo Flight) |
| 6 | PROMOTION `_READ` cho STAFF | `—` | **có** | **DB** |
| 7 | VOUCHER `_READ` cho CUSTOMER + STAFF | `—` | **có** cả hai | **DB** (CUSTOMER xem ví voucher) |
| 8 | FARE_CLASS / FARE_RULE / BAGGAGE / FLIGHT_FARE / SEAT_INVENTORY cho STAFF | chỉ ADMIN | **STAFF có toàn quyền** | **DB** (đúng BR-FLIGHT-01) |
| 9 | FLIGHT_FARE / SEAT_INVENTORY `_READ` cho CUSTOMER | `—` | **có** | **DB** (hiển thị giá + tình trạng ghế khi tìm chuyến) |
| 10 | NEWS_CATEGORY / NEWS `_READ` cho STAFF | `—` | **có** | **DB** |
| 11 | `PASSENGER_*`, `NOTIFICATION_READ_SELF` cho STAFF | `—` | **có** | **DB** |

> **Việc cần bạn quyết:** các dòng 4–11 là **DB seed cấp nhiều hơn** so với matrix v2.0. Tài liệu v2.1 nay lấy theo DB (vì code đang chạy như vậy). Nếu bạn cho rằng DB seed **cấp thừa** (ví dụ STAFF không nên đọc News, hay CUSTOMER không nên đọc FLIGHT_FARE), thì cần sửa **migration mới** (thu hồi quyền) — không sửa V2/V10 vì đã chạy. Ngược lại nếu DB đúng thì matrix v2.0 mới là bản ghi sai và nay đã được sửa.

> **Nguyên tắc từ nay:** khi matrix và DB lệch nhau, **DB (migration) là nguồn sự thật** — vì đó là thứ hệ thống thực sự kiểm tra. Matrix chỉ là tài liệu mô tả.

---

## 6. Business Rules quan trọng

| ID | Business Rule |
|---|---|
| BR-RBAC-01 | MVP sử dụng các Role cố định CUSTOMER, STAFF và ADMIN. |
| BR-RBAC-02 | ADMIN có thể lựa chọn/thay đổi Role của User trong Create User / Update User theo Role được hệ thống cho phép. |
| BR-RBAC-03 | MVP không cho phép ADMIN tạo mới, chỉnh sửa, Activate/Deactivate hoặc Soft Delete Role. |
| **BR-RBAC-04** *(mới v2.1)* | **ADMIN không xóa được tài khoản có role ADMIN (kể cả chính mình)** — chỉ xóa STAFF/CUSTOMER. Muốn gỡ một ADMIN thì đổi role xuống STAFF/CUSTOMER trước, rồi mới xóa. |
| BR-NEWS-01 | Một News có thể được gán một hoặc nhiều News Category. |
| BR-NEWS-02 | News có thể được tạo ở trạng thái DRAFT mà chưa có News Category. |
| BR-NEWS-03 | News phải được gán ít nhất một News Category trước khi Publish. |
| BR-FLIGHT-01 | STAFF được Create/Update/Activate/Deactivate Flight nhưng không quản lý Airline/Airport/Aircraft. |
| BR-BOOKING-01 | GUEST không được tạo Booking; muốn đặt vé phải đăng nhập hoặc đăng ký CUSTOMER. |
| BR-BOOKING-02 | CUSTOMER có thể đặt vé cho chính mình hoặc Passenger khác. |
| BR-DATA-01 | Activate/Deactivate không thay thế Update; Update dùng để sửa dữ liệu. |
| BR-DATA-02 | Hard Delete không được ưu tiên với dữ liệu nghiệp vụ có liên kết/lịch sử. |
| BR-DATA-03 | isActive biểu thị trạng thái sử dụng; isDeleted biểu thị Soft Delete; deletedAt lưu thời điểm Soft Delete. |
| BR-DATA-04 | Không dùng isActive thay cho Status với Entity có Lifecycle phức tạp như Booking, Payment, News và Flight. |

---

## 7. Ngoài phạm vi báo cáo

Các chức năng sau không thuộc Permission Matrix này và không nằm trong scope 3 Phase:

- Review / Rating
- Refund qua cổng thanh toán (hoàn tiền thật)
- **Hoàn tiền vào ví** *(bỏ khỏi phạm vi từ v2.1 — hệ thống không có ví; khách liên hệ đại lý)*
- Online Check-in
- Boarding Pass
- Audit Log
- Các tính năng mở rộng sau Booking Core

> PAYMENT GATEWAY và NOTIFICATION SERVICE vẫn có thể được mô hình hóa như External Actor trong Use Case/Sequence/Architecture khi cần, nhưng không phải là cột Permission của End User/Internal User.

---

## 8. Traceability

Permission Matrix v2.1 → Functional Requirements → Use Case Catalogue → Use Case Specification → Activity Diagram → Sequence Diagram → Domain Model/ERD → API Authorization → Test Case

---

*Hết tài liệu v2.1.*

*Lịch sử phiên bản: v2.0 (baseline 3 Phase) → **v2.1 (15/10/2026: bỏ WALLET_READ_SELF cùng nhóm ví; đưa VOUCHER_CLAIM vào bảng chính; sửa FLIGHT_READ_DETAIL → FLIGHT_DETAIL_READ; bổ sung BR-RBAC-04; chuyển sang định dạng `.md`)**.*
