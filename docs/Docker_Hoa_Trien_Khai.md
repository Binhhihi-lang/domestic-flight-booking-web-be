# Docker hoá dự án — hiểu bản chất & hướng dẫn triển khai

Mục tiêu: máy khác **chỉ cần Docker Desktop**, không phải cài Java 17, Maven, Node,
PostgreSQL hay Redis. Một lệnh `docker compose up -d --build` là có hệ thống chạy được.

---

## 1. Hiểu bản chất trước khi gõ lệnh

### 1.1 Ba khái niệm, một dây chuyền

```
Dockerfile   →   IMAGE   →   CONTAINER
(công thức)     (bản đóng gói)   (tiến trình đang chạy)
```

Với người làm Java, analogy chính xác nhất là:

| Java | Docker |
|---|---|
| `pom.xml` + `src/` | `Dockerfile` |
| `mvn package` → `app.jar` | `docker build` → **image** |
| `java -jar app.jar` → tiến trình đang chạy | `docker run` → **container** |

`.jar` là file đóng gói tĩnh, bản thân nó không chạy gì. Chạy nó mới sinh ra tiến trình —
và chạy 3 lần thì có 3 tiến trình độc lập từ **cùng một** file jar. Image y hệt vậy:
đóng gói tĩnh, tạo bao nhiêu container từ nó cũng được, sửa container này không ảnh
hưởng container kia.

### 1.2 Image là gì — và vì sao "không cần cài gì"

Image = **một hệ thống file đóng băng** (đã cài sẵn mọi thứ cần để chạy) + vài thông tin
khởi chạy (chạy lệnh gì, mở cổng nào).

Mấu chốt: **runtime nằm bên trong image, không lấy từ máy host.**

| | Trên máy bạn | Bên trong image backend |
|---|---|---|
| Java | Có thể không có, hoặc 21, 17… | **Luôn là JRE 17** đã đóng gói sẵn |
| Maven | Không cần | Không có (chỉ có ở stage build, bị vứt đi) |
| PostgreSQL | Không cần | Kết nối qua mạng tới container khác |

Máy đích chỉ cung cấp **kernel** (Windows → Docker Desktop chạy một Linux VM nhỏ qua WSL2).
Mọi thứ còn lại do image lo. Đó chính là "không cần cài gì" — ngoại lệ duy nhất:
phải cài **Docker Desktop**.

Hai tính chất quan trọng:

- **Image là bất biến.** Không sửa được image đang có; muốn đổi thì build image mới.
- **Container có thêm một lớp ghi tạm** (lớp trên cùng). App ghi file vào đó thì mất khi
  xoá container → vì vậy PostgreSQL cần **volume** (`pgdata`) để dữ liệu sống ngoài
  vòng đời container.

### 1.3 Các khái niệm còn lại

| Khái niệm | Hiểu ngắn | Trong dự án này |
|---|---|---|
| **Volume** | Ổ đĩa tách khỏi vòng đời container — xoá container không mất data | `pgdata` giữ dữ liệu PostgreSQL |
| **Network** | Mạng riêng giữa các container; gọi nhau bằng **tên service** | backend gọi `postgres:5432`, nginx gọi `backend:8080` |
| **Compose** | File YAML mô tả nhiều container + quan hệ phụ thuộc | `docker-compose.yml` |
| **Registry** | Kho chứa image (Docker Hub, GHCR) để `pull`/`push` | `postgres`, `redis`, `nginx`, `node`, `maven` |

Điểm cốt lõi: code được build **bên trong** image. Máy đích không cần toolchain,
chỉ cần runtime của Docker.

---

## 2. Bên trong 2 image của dự án

### 2.1 Frontend — build ra file tĩnh, nginx phát file

Bạn hiểu đúng phần biên dịch, chỉ khác **đích đến**:

| | Java | Angular |
|---|---|---|
| Ngôn ngữ nguồn | `.java` | `.ts` + `.html` + `.scss` |
| Công cụ build | `javac` (trong JDK) | `ng build` (chạy trên Node) |
| Kết quả | `.class` trong `.jar` | `index.html`, `main-xxx.js`, `styles-xxx.css` |
| **Ai thực thi code** | **JVM trên server** | **Trình duyệt của người dùng** |

Vì code chạy ở trình duyệt, server **không cần** hiểu Angular. Nó chỉ cần làm một việc:
khi trình duyệt xin `/main-abc.js`, đọc file trên đĩa và gửi bytes về. Đó là
**phục vụ tĩnh** — không biên dịch, không render, không chạy logic gì.

Vậy tại sao vẫn cần "server"? Vì trình duyệt chỉ tải file qua HTTP, phải có ai đó trả lời.
Nginx làm 3 việc trong dự án này:

1. **SPA fallback** — gõ `localhost:4200/admin/airlines` rồi F5. Trên đĩa không có file
   nào tên `admin/airlines` cả. `try_files $uri $uri/ /index.html` bảo nginx: không có
   file thì trả `index.html`, rồi Angular Router đọc URL và render đúng trang.
   Thiếu dòng này là F5 ra 404.
2. **Proxy `/api`** — trình duyệt gọi `/api/...` cùng origin, nginx chuyển tiếp sang
   `backend:8080`. Nhờ vậy không cần CORS.
3. **Cache + gzip** — file build có hash trong tên (`main-W7QKIAYE.js`) nên cache 30 ngày;
   nén trước khi gửi.

Lúc dev không dùng nginx: `ng serve` là server Node có hot reload. Nginx chỉ xuất hiện
khi đóng gói production.

### 2.2 Backend — fat jar, không cần cài Tomcat

`mvn package` với Spring Boot không chỉ nén class — nó tạo **fat jar**: `BOOT-INF/lib`
chứa toàn bộ thư viện, kèm **Tomcat nhúng**. Nên `java -jar app.jar` là có HTTP server
ở cổng 8080 ngay, không cài Tomcat, không deploy WAR.

Vì vậy image runtime rất mỏng: chỉ JRE 17 + 1 file `app.jar`. Chỉ cần tinh chỉnh một chỗ
so với cách hiểu thông thường: **JDK** (có `javac`) để build, **JRE** để chạy — image
runtime cố tình chỉ có JRE cho nhẹ và ít lỗ hổng hơn.

---

## 3. Các file đã thêm

**Repo backend** (`domestic-flight-booking-web-backend`)

| File | Vai trò |
|---|---|
| `Dockerfile` | 2 stage: `maven:3.9-eclipse-temurin-17` build jar → `eclipse-temurin:17-jre-alpine` chỉ giữ jar, chạy bằng user thường |
| `.dockerignore` | Không gửi `target/`, `.env`, `.git/`, `docs/` vào build context |
| `src/main/resources/application-docker.properties` | Profile `docker`: DB = `postgres:5432`, Redis = `redis:6379`, mọi giá trị có mặc định |
| `docker-compose.yml` | Duy nhất: postgres + redis + backend + frontend; bật hạ tầng một mình để code bằng IDE, hoặc bật cả 4 để demo |

**Repo frontend** (`domestic-flight-booking-web-frontend`)

| File | Vai trò |
|---|---|
| `Dockerfile` | 2 stage: `node:24-alpine` chạy `npm ci && npm run build` → `nginx:1.27-alpine` phục vụ file tĩnh |
| `nginx.conf` | SPA fallback (`try_files ... /index.html`), proxy `^~ /api/` → `backend:8080`, cache file có hash |
| `.dockerignore` | Không gửi `node_modules/`, `dist/`, `.angular/` |
| `src/environments/environment.prod.ts` | `apiBaseUrl: '/api'` — đường dẫn tương đối |
| `angular.json` | `fileReplacements` cho configuration `production` |

### Vì sao backend có thêm profile `docker`

Spring Boot quản lý cấu hình theo profile, và Docker là môi trường **thứ ba** có địa chỉ
host khác hẳn hai môi trường đã có:

| Profile | DB | Redis | Dùng khi |
|---|---|---|---|
| `dev` | Aiven (qua `.env`) | localhost hoặc cloud | code hằng ngày, dữ liệu thật |
| `local` | `localhost:5433` (dev compose) | `localhost:6379` | IDE + hạ tầng dev |
| `docker` | **`postgres:5432`** | **`redis:6379`** | stack compose đầy đủ |

Điểm mấu chốt: **bên trong container, `localhost` là chính container đó.** Backend ở
container `backend` không thấy Postgres ở `localhost:5433` — nó phải gọi `postgres:5432`
(tên service, cổng nội bộ của container DB). Nên không tái dùng được
`application-local.properties`.

Cũng không dùng được `application-dev.properties` vì file đó **bắt buộc** có
`FLIGHTBOOKING_DB_URL`, `DB_FB_USERNAME`, `DB_FB_PASSWORD`, `JWT_SIGNER_KEY`,
`CLOUDINARY_*` — tức muốn chạy demo phải có tài khoản Aiven, trái mục tiêu "máy nào bật
lên cũng chạy". Profile `docker` thì **tự chứa, mọi giá trị có mặc định**.

Spring tự nạp file nhờ đúng một dòng trong compose:

```yaml
SPRING_PROFILES_ACTIVE: docker
```

**Vì sao không nhét hết cấu hình vào `docker-compose.yml`:** làm được, nhưng phải dùng
tên biến phẳng (`SPRING_DATASOURCE_URL`, `SPRING_DATA_REDIS_HOST`…), rải rác trong YAML,
khó đọc và dễ sai. Tách profile thì: cấu hình nằm một chỗ cạnh các profile khác, review
diff dễ, và compose chỉ khai báo thứ **khác mặc định** (JWT key, Cloudinary, mail).

Ngoài ra profile này cũng là chỗ sửa lỗi `JavaMailSender`: thiếu `spring.mail.host` thì
Spring Boot không tạo bean mail và app chết lúc khởi động.

### Vì sao đổi `apiBaseUrl` sang `/api`

Bản dev vẫn là `http://localhost:8080/api`. Nhưng nếu giữ nguyên khi chạy container,
trình duyệt mở từ máy khác sẽ gọi `localhost:8080` **của chính máy đó** → hỏng.
Dùng đường dẫn tương đối thì:

- nginx phục vụ FE và proxy `/api` về backend → **cùng origin, không cần CORS**;
- chạy ở `localhost`, IP LAN hay domain nào cũng đúng, không phải sửa code.

Thay thế file environment chỉ áp cho **build production**, nên `ng serve` và unit test
giữ nguyên hành vi cũ.

### Service nào cần Dockerfile, service nào không

| Service | Image lấy từ đâu | Có Dockerfile? |
|---|---|---|
| `postgres` | Docker Hub (`postgres:18-alpine`) | Không, compose chỉ `pull` |
| `redis` | Docker Hub (`redis:7.2-alpine`) | Không |
| `backend` | Tự build từ repo BE | Có |
| `frontend` | Tự build từ repo FE | Có |

**4 container không phải quy luật của Docker** — đó là lựa chọn để hệ thống tự chạy được
trọn vẹn trên một máy. Production thật thường chỉ có 2 container (BE + FE), còn DB/Redis
là dịch vụ managed bên ngoài (Aiven).

---

## 4. Chạy

```bash
cd domestic-flight-booking-web-backend          # compose nằm ở repo backend

docker compose up -d --build                    # lần đầu: build + chạy nền
docker compose logs -f backend                  # xem log backend
docker compose ps                               # trạng thái 4 container
docker compose down                             # tắt, GIỮ dữ liệu
docker compose down -v                          # tắt, XOÁ sạch dữ liệu
```

Compose build frontend từ thư mục anh em `../domestic-flight-booking-web-frontend`,
nên **hai repo phải nằm cạnh nhau, giữ đúng tên thư mục**.

### Một compose, hai chế độ chạy

Chỉ còn **một** `docker-compose.yml` (file `docker-compose.dev.yml` cũ đã được gộp vào đây).
Compose **không biết Spring profile là gì** — nó chỉ bật/tắt container. Profile là quyết định
của **app lúc khởi động** (đọc `application-*.properties` nào), tức quyết định app nối DB nào.

| Mục đích | Lệnh | Container chạy | App chạy ở đâu | Profile | DB |
|---|---|---|---|---|---|
| Code + dữ liệu thật | `docker compose up -d redis` | chỉ Redis | IDE / `ng serve` | `dev` | **Aiven** |
| Code offline | `docker compose up -d postgres redis` | Postgres `:5433` + Redis | IDE / `ng serve` | `local` | postgres local |
| Demo / bàn giao | `docker compose up -d --build` | cả 4 | trong container | `docker` | postgres local |

Vì sao chỉ cần **một** DB local: profile `local` và `docker` cùng trỏ vào **cùng một**
container Postgres, chỉ khác đường đi — từ IDE là `localhost:5433` (cổng publish ra host),
từ container khác là `postgres:5432` (cổng trong compose network). Dữ liệu vì thế dùng
chung giữa hai chế độ, không sinh ra DB thứ hai gây nhầm.

### Hai database

| DB | Nằm ở đâu | Cổng từ máy host | Do đâu | Profile dùng | Dữ liệu |
|---|---|---|---|---|---|
| **Aiven** | cloud | theo `.env` (SSL) | ngoài Docker | `dev` | thật, lâu dài |
| **Local** | container `vinafly-postgres-1` | `5433` | `docker-compose.yml` | `local`, `docker` | seed + dữ liệu thử |

Hai DB **độc lập hoàn toàn**: dữ liệu tạo ở DB này không thấy ở DB kia, mỗi DB có bảng
`flyway_schema_history` riêng, và `docker compose down -v` chỉ xoá DB local.

Muốn biết app đang nối DB nào: xem dòng đầu log Flyway lúc khởi động —

```
Database: jdbc:postgresql://postgres:5432/flightbooking      ← chạy trong container (docker)
Database: jdbc:postgresql://localhost:5433/flightbooking     ← chạy từ IDE (local)
Database: jdbc:postgresql://<aiven-host>/defaultdb?sslmode=require   ← Aiven (dev)
```

Muốn cả 4 service nhưng DB vẫn là Aiven: xem mục 7 bên dưới.

### Truy cập

| URL | Nội dung |
|---|---|
| http://localhost:4200 | Giao diện Angular |
| http://localhost:8080 | API backend (gọi trực tiếp) |
| http://localhost:4200/api/... | API qua proxy của nginx |

Tài khoản ADMIN tạo tự động khi database trống (DataInitializer):
`admin@flightbooking.local` / `Admin@12345`. Dữ liệu master (hãng bay, sân bay, ghế…)
do Flyway V1–V15 seed sẵn.

---

## 5. Sửa code thì Docker làm gì

Image được xếp từ nhiều **layer**, mỗi lệnh `COPY`/`RUN` là một layer. Layer không đổi
thì Docker dùng lại kết quả cũ:

```
COPY pom.xml         → layer "dependency"     ← chỉ đổi khi sửa pom
RUN mvn go-offline
COPY src             → layer "source code"    ← đổi mỗi lần sửa Java
RUN mvn package
```

Vì `pom.xml` nằm **trước** `src` trong Dockerfile, sửa Java chỉ phải chạy lại 2 layer cuối
→ ~15–30s thay vì tải lại toàn bộ Maven. Đây chính là lý do `pom.xml`/`package.json` được
`COPY` riêng thay vì copy cả thư mục một lần.

| Bạn sửa | Lệnh | Thời gian |
|---|---|---|
| Code Java | `docker compose up -d --build backend` | ~20s |
| Code Angular | `docker compose up -d --build frontend` | ~15s |
| `pom.xml` / `package.json` | như trên, nhưng tải lại dependency | 1–3 phút |

Chỉ rebuild service đã đổi; service không đổi vẫn chạy image cũ.

---

## 6. Database đổi cấu trúc

Flyway lo, không đụng gì tới Docker:

1. Tạo `src/main/resources/db/migration/V16__ten_mo_ta.sql`.
2. `docker compose up -d --build backend` → lúc khởi động Flyway chạy migration còn thiếu,
   rồi Hibernate `validate` xác nhận entity khớp schema.

Ba nguyên tắc:

- **Không sửa file migration đã chạy** — checksum đổi là `validate` fail ngay. Luôn thêm
  file mới.
- Test 2 lần: `down -v` rồi `up` (mô phỏng máy mới) **và** chạy trên DB đang có dữ liệu
  (giống Aiven thật) — migration thêm cột `NOT NULL` không default sẽ chết ở đây.
- Production nên chạy migration có kiểm soát (review file, backup, dùng Flyway CLI/CI)
  thay vì để app tự migrate lúc khởi động.

---

## 7. Cấu hình

Mọi thứ có mặc định chạy demo được. Muốn đổi thì đặt biến môi trường trong
`docker-compose.yml` (hoặc file `.env` cạnh compose — Compose tự đọc):

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `JWT_SIGNER_KEY` | key demo | Đổi khi triển khai thật |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Origin FE được phép gọi |
| `CLOUDINARY_*` | rỗng | Rỗng thì tính năng upload ảnh không dùng được |
| `MAIL_ENABLED` / `MAIL_*` | `false` | `false` → link đặt lại mật khẩu chỉ ghi log |

### Dùng PostgreSQL thật (Aiven) thay vì postgres trong compose

Thêm vào service `backend` trong `docker-compose.yml`:

```yaml
      SPRING_DATASOURCE_URL: jdbc:postgresql://HOST:PORT/defaultdb?sslmode=require
      SPRING_DATASOURCE_USERNAME: avnadmin
      SPRING_DATASOURCE_PASSWORD: <mật khẩu>
```

(hoặc thêm cả `SPRING_PROFILES_ACTIVE: dev` + các biến `FLIGHTBOOKING_DB_URL`,
`DB_FB_USERNAME`, `DB_FB_PASSWORD`, `REDIS_HOST`… như profile `dev` vốn dùng).
Khi đó có thể xoá service `postgres` và volume `pgdata`.

---

## 8. Dev hằng ngày — chỉ bật hạ tầng

```bash
docker compose up -d postgres redis                  # postgres (5433) + redis
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
npm start                                            # ng serve, gọi http://localhost:8080
```

Dùng Aiven cho DB thì bật mỗi Redis và chạy profile `dev`:

```bash
docker compose up -d redis
```

> PowerShell: **phải bọc nháy** tham số `-D...`, nếu không nó bị cắt thành
> `.run.profiles=local` và Maven báo `Unknown lifecycle phase`. Chạy từ IntelliJ
> thì đặt profile trong run configuration, không cần tham số dòng lệnh.

Vì sao dev chỉ đóng gói DB + Redis (không đóng gói FE/BE):

- sửa code không phải rebuild image — vòng lặp dev tính bằng giây thay vì phút;
- IDE cần tiến trình thật để debug/breakpoint, trong container phải cấu hình remote debug;
- `ng serve` có hot reload, nginx phục vụ file tĩnh thì không.

Đánh đổi: dev và demo chạy hai đường khác nhau, nên **trước khi bàn giao phải test lại
bằng `docker compose up -d --build`**.

---

## 9. Bàn giao không kèm source code

Hiện tại máy khác cần **source** rồi `docker compose up --build`. Nếu muốn bàn giao chỉ
bằng image (như gửi một file `.jar`), đẩy image lên registry:

```bash
docker push <registry>/vinafly/backend:1.0.0
docker push <registry>/vinafly/frontend:1.0.0
```

Máy kia chỉ cần file compose trỏ tới image đó và chạy `docker compose pull && docker compose up -d`.

---

## 10. Lỗi thường gặp

| Hiện tượng | Nguyên nhân / cách xử lý |
|---|---|
| `port is already allocated` | Cổng 4200/8080 đang bị chiếm — tắt tiến trình đang chạy, hoặc đổi `"4200:80"` thành `"4300:80"` |
| Sửa code mà không thấy đổi | Image chưa build lại: `docker compose up -d --build backend` |
| `failed to solve: ../domestic-flight-booking-web-frontend` | Thiếu repo FE hoặc sai tên thư mục |
| Backend lỗi `ddl-auto=validate` | Entity lệch schema: kiểm tra migration mới đã tạo đúng thứ tự V*.sql |
| FE trắng trang / 404 khi F5 | Kiểm tra `nginx.conf` còn dòng `try_files $uri $uri/ /index.html` |
| Log `AuthenticationServiceException: Token không hợp lệ` | Trình duyệt còn token cũ/rác (phiên trước, hoặc key JWT khác) → API vẫn trả 401 đúng; xoá Local Storage rồi đăng nhập lại |
| Muốn xem container đang có gì | `docker compose exec backend sh`, `docker compose exec postgres psql -U flight -d flightbooking` |

Xem log của tất cả container cùng lúc: `docker compose logs -f`.

---

## 11. Tóm gọn mô hình trong đầu

1. Dockerfile = công thức; **image** = kết quả đóng gói bất biến (giống `.jar`);
   **container** = tiến trình chạy từ image.
2. Image **mang theo runtime** → máy đích không cần Java/Node/Postgres, chỉ cần Docker.
3. FE build ra file tĩnh, **nginx phát file** cho trình duyệt; BE build ra fat jar,
   **JVM chạy** nó.
4. Node và Maven chỉ sống trong **stage build** rồi bị vứt — image cuối chỉ còn thứ cần
   để chạy.
5. **Volume** giữ dữ liệu DB ngoài container; **network** cho container gọi nhau bằng
   tên service.
6. Compose chỉ là bản khai báo "chạy những cái này, nối chúng lại, mở cổng này".

---

## 12. Phụ lục: build, biên dịch, thông dịch

### 12.1 "Build" là gì

**Build = biến source (người đọc được) thành thứ runtime đích chạy được**, kèm gom
dependency và tối ưu. Không có ngôn ngữ nào chạy thẳng văn bản source.

| | Java | PHP |
|---|---|---|
| Đơn vị đem đi deploy | `.jar` (bytecode đã dịch **hết**) | chính file `.php` (source) |
| Ai dịch | `javac` lúc **build** | Zend Engine, **lúc chạy** |
| Dịch khi nào | một lần, toàn bộ | lười, từng file, lần đầu được `include` |
| Cache opcode ở đâu | nằm trong `.jar` — bền, đi theo artifact | OPcache trong RAM — mất khi restart |
| Chi phí trả ở đâu | lúc build | request đầu tiên / sau mỗi lần restart |
| Lỗi cú pháp phát hiện | ngay khi build | khi dòng code đó được chạy tới |

### 12.2 Java chạy thế nào

```
.java  --javac-->  .class (bytecode)  --jar-->  app.jar
app.jar --java -jar--> JVM nạp bytecode, thực thi (JIT dịch tiếp sang mã máy)
```

Dịch **trước**, một lần, toàn bộ. Vì vậy jar là artifact bất biến: máy nào có JVM cũng
chạy ra cùng kết quả, không cần đọc lại source.

### 12.3 PHP chạy thế nào

```
index.php --php-fpm/Zend--> dịch .php sang opcode --> chạy opcode --> lưu opcode vào OPcache (RAM)
request sau --> dùng lại opcode, không đọc file
```

Vì thế: file không được `include` thì không bao giờ được dịch; restart PHP-FPM là mất
cache → request đầu tiên chậm ("cold start"); ở dev OPcache kiểm tra `mtime` nên sửa file
là dịch lại ngay, production thường tắt kiểm tra đó và phải restart khi deploy.
Có hai thứ kéo PHP về gần "build một lần": **OPcache preload** (dịch sẵn lúc khởi động)
và **JIT** (PHP 8, dịch opcode nóng sang mã máy).

Lưu ý: build của PHP (`composer install`, `npm run build`, `artisan config:cache`) là
chuẩn bị **môi trường + asset**, không liên quan việc dịch code PHP.

### 12.4 Gọi là "thông dịch" hay "runtime"

Hai từ trả lời hai câu hỏi khác nhau:

- **Thông dịch (interpreted)** = *mô hình thực thi*: source được dịch và chạy từng bước
  bởi bộ thông dịch, không sinh artifact riêng.
- **Runtime** = *thành phần/môi trường thực thi*: JVM (Java), Zend Engine trong php-fpm
  (PHP), V8 (Node).

Nên nói: **PHP là ngôn ngữ thông dịch, chạy trên runtime Zend**; **Java là ngôn ngữ biên
dịch trước (ra bytecode), chạy trên runtime JVM**. Java cũng có runtime, chỉ khác là nó
không đọc `.java` — nó cần bytecode đã dịch sẵn.

Khác biệt cốt lõi không phải "có hay không có compiler", mà là **dịch lúc nào** và
**cache kết quả ở đâu**.

