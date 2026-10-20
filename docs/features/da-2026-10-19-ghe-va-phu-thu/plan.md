# Phụ thu nâng hạng ghế & sửa lỗi giao diện ghế — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thêm bảng cấu hình phụ thu nâng hạng ghế (dùng chung mọi chuyến) với CRUD đầy đủ ở BE và FE, đồng thời sửa 6 lỗi giao diện lưới ghế và hoàn thiện CRUD loại ghế có color picker.

**Architecture:** BE dùng lại `BaseCrudService`/`BaseCrudController` — mỗi module chỉ khai entity, DTO, mapper, repository, service và controller. FE dùng lại `CrudPage` driven by `CrudConfig` — màn mới chỉ khai config, không viết component riêng. Lưới ghế chuyển từ tô màu theo class sang `[style]` binding từ `color_hex`, kèm fallback theo `seat_class`.

**Tech Stack:** Java 17 · Spring Boot 4.1.1 · PostgreSQL · Flyway · Angular 22 · Vitest · JUnit 5 + Mockito + AssertJ

**Spec:** `docs/features/da-2026-10-19-ghe-va-phu-thu/design.md`

## Global Constraints

- Mọi field JSON là **snake_case** (cấu hình Jackson `SNAKE_CASE` toàn cục đã có).
- Mã lỗi cố định: `1001` validate, `1004` không tìm thấy, `1005` trùng, `1006` vi phạm nghiệp vụ.
- Xóa là **xóa mềm** (`is_deleted = true` + `deleted_at`), không xóa cứng.
- Danh sách luôn lọc `is_deleted = false` (BR-LIST-04).
- Path quản trị: `/api/admin/**`; không có `/v1`.
- FE dùng path alias `@core/@shared/@features/@env`; `templateUrl`/`styleUrl` giữ tương đối.
- **Không commit** — user tự commit theo quy tắc dự án. Plan ghi lệnh commit để tham khảo, người thực thi bỏ qua bước đó nếu user chưa cho phép.
- Migration mới phải là **V14** — kiểm `ls src/main/resources/db/migration/` trước khi tạo file.

## Review Focus

- **Màu nền sáng + chữ trắng** → không đọc được. Test: `#90CAF9` phải chọn chữ **đen**.
- **Cặp (from_class, to_class) chưa cấu hình** → Phase 2 phải từ chối bán, không mặc định 0. Test: service trả `null`/rỗng, không trả 0.
- **Sửa bản ghi thành cặp trùng với bản ghi khác** → phải `1006`; sửa giữ nguyên cặp của chính nó → phải OK.
- **Xóa mềm rồi tạo lại cùng cặp** → phải được, không bị unique index chặn.
- **`color_hex = null` trong form** → phải gửi `null`, không gửi `''` (BE `@Pattern` sẽ trượt).
- **Bulk endpoint thiếu `@PreAuthorize`** → hở quyền. Test: controller có annotation trên cả 2 method bulk.

---

## File Structure

### BE — tạo mới

| File | Trách nhiệm |
|---|---|
| `src/main/resources/db/migration/V14__seat_class_surcharges.sql` | Bảng + partial unique index + seed 6 dòng |
| `entity/SeatClassSurcharge.java` | Entity, dùng `SeatClass` enum có sẵn |
| `dto/request/masterdata/SeatClassSurchargeRequest.java` | Request DTO + validate |
| `dto/response/masterdata/SeatClassSurchargeResponse.java` | Response DTO |
| `mapper/SeatClassSurchargeMapper.java` | Entity → Response |
| `repository/SeatClassSurchargeRepository.java` | JpaRepository + JpaSpecificationExecutor + exists methods |
| `service/SeatClassSurchargeService.java` | extends `BaseCrudService` |
| `controller/SeatClassSurchargeController.java` | extends `BaseCrudController` + `@PreAuthorize` |
| `src/test/java/.../service/SeatClassSurchargeServiceTest.java` | Unit test |

### FE — tạo mới

| File | Trách nhiệm |
|---|---|
| `src/app/core/models/seat-surcharge.model.ts` | `SeatClassSurcharge` + payload |
| `src/app/features/admin/pages/seat-class-surcharges/seat-class-surcharges.ts` | `CrudConfig` cho màn mới |
| `src/app/core/mock/data/seat-surcharge.mock.ts` | Seed 6 dòng |

### FE — sửa

| File | Thay đổi |
|---|---|
| `src/app/features/admin/pages/seat-configurations/seat-grid.ts` | Màu theo `color_hex`, tương phản, Map cache, legend |
| `src/app/features/admin/pages/seat-configurations/seat-grid.html` | `[style]` binding, legend, aria-label dùng tên |
| `src/app/features/admin/pages/seat-configurations/seat-grid.css` | Giữ class fallback, thêm `:focus-visible`, style legend |
| `src/app/shared/crud/crud-config.ts` | Thêm `CrudFieldType = 'color'`, `CrudColumn.kind = 'color'` |
| `src/app/shared/crud/crud-page.html` | Render ô color picker + cột màu |
| `src/app/shared/crud/crud-page.ts` | `defaultFor('color')` trả `null` |
| `src/app/features/admin/pages/seat-types/seat-types.ts` | Dùng `type: 'color'` + cột `kind: 'color'` |
| `src/app/features/admin/layout/admin-layout.ts` | Thêm 2 mục sidebar |
| `src/app/app.routes.ts` | Thêm `crudRoutes` cho màn phụ thu |
| `src/app/core/services/catalog.service.ts` | Thêm `SeatClassSurchargeService` |
| `src/app/core/mock/mock-registry.ts` | Đăng ký `MockResource` mới |

> **Kiểm chứng bộ lọc mock:** `MockResource.list()` xử lý filter chung bằng cách
> so **chuỗi tuyệt đối** với trường vô hướng:
> ```typescript
> rows = rows.filter((r) => v === undefined || String(v) === value);
> ```
> `from_class` và `to_class` là chuỗi `'ECONOMY'` → khớp đúng. Không cần
> `arrayFilters`. Filter `status` được xử lý riêng ở đầu hàm (đã có sẵn).

---

## Task 1: BE — Migration V14

**Files:**
- Create: `domestic-flight-booking-web-be/src/main/resources/db/migration/V14__seat_class_surcharges.sql`

**Interfaces:**
- Produces: bảng `seat_class_surcharges` với cột `id, from_class, to_class, surcharge, is_active, is_deleted, deleted_at, created_at, updated_at`; partial unique index `uq_seat_class_surcharges_pair`.

- [ ] **Step 1: Kiểm tra số migration chưa bị dùng**

Run: `ls domestic-flight-booking-web-be/src/main/resources/db/migration/ | tail -3`
Expected: thấy `V13__drop_wallet_and_refund.sql` là file cuối.

- [ ] **Step 2: Tạo file migration**

```sql
-- =====================================================================
-- V14__seat_class_surcharges.sql
-- Phụ thu khi khách chọn ghế khác hạng với hạng vé đã mua.
--
-- Mô hình 2 tầng:
--   1. Bảng này = mặc định dùng chung cho mọi chuyến.
--   2. flight_seats.seat_surcharge = ghi đè theo từng chuyến (Phase 2 điền).
--
-- Quy ước: KHÔNG có dòng (from_class, to_class) = CHƯA CÓ CHÍNH SÁCH
-- → Phase 2 từ chối bán ghế đó. Muốn miễn phí thì tạo dòng surcharge = 0.
-- =====================================================================

CREATE TABLE seat_class_surcharges (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    from_class  VARCHAR(20)   NOT NULL,
    to_class    VARCHAR(20)   NOT NULL,
    surcharge   NUMERIC(12,2) NOT NULL DEFAULT 0,
    is_active   BOOLEAN       NOT NULL DEFAULT true,
    is_deleted  BOOLEAN       NOT NULL DEFAULT false,
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chk_surcharge_from CHECK (from_class IN ('ECONOMY','BUSINESS','FIRST')),
    CONSTRAINT chk_surcharge_to   CHECK (to_class   IN ('ECONOMY','BUSINESS','FIRST')),
    CONSTRAINT chk_surcharge_non_negative CHECK (surcharge >= 0)
);

-- Partial index: bản ghi xóa mềm vẫn nằm lại (BR-LIST-04), nên UNIQUE thường
-- sẽ chặn ADMIN tạo lại cặp đã từng xóa.
CREATE UNIQUE INDEX uq_seat_class_surcharges_pair
    ON seat_class_surcharges (from_class, to_class)
    WHERE is_deleted = false;

INSERT INTO seat_class_surcharges (from_class, to_class, surcharge) VALUES
    ('ECONOMY',  'ECONOMY',        0),
    ('ECONOMY',  'BUSINESS',  500000),
    ('ECONOMY',  'FIRST',    1200000),
    ('BUSINESS', 'ECONOMY',        0),
    ('BUSINESS', 'FIRST',     700000),
    ('FIRST',    'FIRST',          0);
```

- [ ] **Step 3: Kiểm tra migration chạy được**

Run: `cd domestic-flight-booking-web-be && ./mvnw -q flyway:info -Dflyway.url=$DB_URL 2>&1 | tail -20`
Expected: nếu không có DB local thì **bỏ qua bước này** — test ở Task 5 sẽ xác nhận qua unit test. Ghi chú lại là chưa chạy thật.

---

## Task 2: BE — Entity + DTO + Mapper + Repository

**Files:**
- Create: `.../entity/SeatClassSurcharge.java`
- Create: `.../dto/request/masterdata/SeatClassSurchargeRequest.java`
- Create: `.../dto/response/masterdata/SeatClassSurchargeResponse.java`
- Create: `.../mapper/SeatClassSurchargeMapper.java`
- Create: `.../repository/SeatClassSurchargeRepository.java`

**Interfaces:**
- Consumes: `SeatClass` enum (đã có: `ECONOMY, BUSINESS, FIRST`).
- Produces:
  - `SeatClassSurcharge` entity với getter/setter Lombok cho mọi cột ở Task 1.
  - `SeatClassSurchargeRequest(String fromClass, String toClass, BigDecimal surcharge, String status)`.
  - `SeatClassSurchargeResponse(Long id, String fromClass, String toClass, BigDecimal surcharge, String status)`.
  - `SeatClassSurchargeMapper.toResponse(SeatClassSurcharge) -> SeatClassSurchargeResponse`.
  - `SeatClassSurchargeRepository extends JpaRepository<SeatClassSurcharge, Long>, JpaSpecificationExecutor<SeatClassSurcharge>` với 2 method exists.

- [ ] **Step 1: Tạo entity**

```java
package com.example.domesticflightbookingwebbackend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Phụ thu khi khách chọn ghế khác hạng với hạng vé đã mua.
 *
 * Mặc định dùng chung cho mọi chuyến; flight_seats.seat_surcharge ghi đè
 * theo từng chuyến. Không có dòng (fromClass, toClass) nghĩa là CHƯA CÓ
 * CHÍNH SÁCH — không được mặc định 0.
 */
@Getter
@Setter
@Entity
@Table(name = "seat_class_surcharges")
public class SeatClassSurcharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Hạng VÉ khách đã mua. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_class", nullable = false, length = 20)
    private SeatClass fromClass;

    /** Hạng GHẾ vật lý khách chọn. */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_class", nullable = false, length = 20)
    private SeatClass toClass;

    @Column(name = "surcharge", nullable = false, precision = 12, scale = 2)
    private BigDecimal surcharge = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
```

- [ ] **Step 2: Tạo request DTO**

```java
package com.example.domesticflightbookingwebbackend.dto.request.masterdata;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/** Payload POST/PUT /api/admin/seat-class-surcharges. */
public record SeatClassSurchargeRequest(

        @NotBlank(message = "Hạng vé không được để trống")
        @Pattern(regexp = "ECONOMY|BUSINESS|FIRST", message = "Hạng vé chỉ nhận ECONOMY, BUSINESS hoặc FIRST")
        String fromClass,

        @NotBlank(message = "Hạng ghế không được để trống")
        @Pattern(regexp = "ECONOMY|BUSINESS|FIRST", message = "Hạng ghế chỉ nhận ECONOMY, BUSINESS hoặc FIRST")
        String toClass,

        @NotNull(message = "Phụ thu không được để trống")
        @DecimalMin(value = "0", message = "Phụ thu không được âm")
        BigDecimal surcharge,

        /** ACTIVE | INACTIVE — không gửi thì mặc định ACTIVE. */
        @Pattern(regexp = "(?i)ACTIVE|INACTIVE", message = "Trạng thái chỉ nhận ACTIVE hoặc INACTIVE")
        String status
) {
}
```

- [ ] **Step 3: Tạo response DTO**

```java
package com.example.domesticflightbookingwebbackend.dto.response.masterdata;

import java.math.BigDecimal;

/** Một dòng phụ thu nâng hạng trả về client. */
public record SeatClassSurchargeResponse(
        Long id,
        String fromClass,
        String toClass,
        BigDecimal surcharge,
        String status
) {
}
```

- [ ] **Step 4: Tạo mapper**

```java
package com.example.domesticflightbookingwebbackend.mapper;

import com.example.domesticflightbookingwebbackend.dto.response.masterdata.SeatClassSurchargeResponse;
import com.example.domesticflightbookingwebbackend.entity.SeatClassSurcharge;
import org.springframework.stereotype.Component;

@Component
public class SeatClassSurchargeMapper {

    public SeatClassSurchargeResponse toResponse(SeatClassSurcharge e) {
        return new SeatClassSurchargeResponse(
                e.getId(),
                e.getFromClass().name(),
                e.getToClass().name(),
                e.getSurcharge(),
                Boolean.TRUE.equals(e.getIsActive()) ? "ACTIVE" : "INACTIVE"
        );
    }
}
```

- [ ] **Step 5: Tạo repository**

```java
package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.SeatClass;
import com.example.domesticflightbookingwebbackend.entity.SeatClassSurcharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SeatClassSurchargeRepository
        extends JpaRepository<SeatClassSurcharge, Long>, JpaSpecificationExecutor<SeatClassSurcharge> {

    boolean existsByFromClassAndToClassAndIsDeletedFalse(SeatClass fromClass, SeatClass toClass);

    /** Khi SỬA: kiểm trùng cặp nhưng bỏ qua chính bản ghi đang sửa. */
    boolean existsByFromClassAndToClassAndIsDeletedFalseAndIdNot(
            SeatClass fromClass, SeatClass toClass, Long id);
}
```

- [ ] **Step 6: Biên dịch**

Run: `cd domestic-flight-booking-web-be && ./mvnw -q compile`
Expected: BUILD SUCCESS, không lỗi.

---

## Task 3: BE — Service

**Files:**
- Create: `.../service/SeatClassSurchargeService.java`

**Interfaces:**
- Consumes: Task 2 (entity, DTO, mapper, repository), `BaseCrudService` (abstract: `resourceName()`, `toResponse()`, `createEntity()`, `updateEntity()`, `softDelete()`, `setActive()`, `search()`).
- Produces: `SeatClassSurchargeService extends BaseCrudService<SeatClassSurcharge, Long, SeatClassSurchargeRequest, SeatClassSurchargeResponse>`.

- [ ] **Step 1: Tạo service**

Theo khuôn `SeatTypeService`. Điểm khác biệt: kiểm trùng **cặp** chứ không phải một trường, và `updateEntity` cũng phải kiểm (quyết định #9 cho đổi cặp).

```java
package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.dto.request.masterdata.SeatClassSurchargeRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.SeatClassSurchargeResponse;
import com.example.domesticflightbookingwebbackend.entity.SeatClass;
import com.example.domesticflightbookingwebbackend.entity.SeatClassSurcharge;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.mapper.SeatClassSurchargeMapper;
import com.example.domesticflightbookingwebbackend.repository.SeatClassSurchargeRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Phụ thu nâng hạng ghế — mặc định dùng chung mọi chuyến.
 *
 * LƯU Ý CHO PHASE 2: không tìm thấy cặp (from, to) nghĩa là CHƯA CÓ CHÍNH SÁCH
 * → phải từ chối bán ghế đó. KHÔNG được lùi về 0 — làm vậy thì ADMIN xóa nhầm
 * một dòng sẽ khiến khách bay hạng cao miễn phí mà không có cảnh báo.
 */
@Slf4j
@Service
public class SeatClassSurchargeService
        extends BaseCrudService<SeatClassSurcharge, Long, SeatClassSurchargeRequest, SeatClassSurchargeResponse> {

    private static final String[] SORT_WHITELIST = {"fromClass", "toClass", "surcharge"};

    private final SeatClassSurchargeRepository repository;
    private final SeatClassSurchargeMapper mapper;

    public SeatClassSurchargeService(SeatClassSurchargeRepository repository,
                                     SeatClassSurchargeMapper mapper) {
        super(repository);
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    protected String resourceName() {
        return "Phụ thu nâng hạng";
    }

    @Override
    protected SeatClassSurchargeResponse toResponse(SeatClassSurcharge entity) {
        return mapper.toResponse(entity);
    }

    @Override
    protected SeatClassSurcharge createEntity(SeatClassSurchargeRequest request) {
        SeatClass from = parseClass(request.fromClass());
        SeatClass to = parseClass(request.toClass());
        if (repository.existsByFromClassAndToClassAndIsDeletedFalse(from, to)) {
            throw new DuplicateResourceException(
                    "Đã có phụ thu cho cặp " + from + " → " + to);
        }
        SeatClassSurcharge entity = new SeatClassSurcharge();
        entity.setFromClass(from);
        entity.setToClass(to);
        applyRequest(entity, request);
        return entity;
    }

    @Override
    protected void updateEntity(SeatClassSurcharge entity, SeatClassSurchargeRequest request) {
        SeatClass from = parseClass(request.fromClass());
        SeatClass to = parseClass(request.toClass());
        // Cho phép đổi cặp, nhưng không được trùng bản ghi KHÁC.
        if (repository.existsByFromClassAndToClassAndIsDeletedFalseAndIdNot(from, to, entity.getId())) {
            throw new DuplicateResourceException(
                    "Đã có phụ thu cho cặp " + from + " → " + to);
        }
        entity.setFromClass(from);
        entity.setToClass(to);
        applyRequest(entity, request);
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private void applyRequest(SeatClassSurcharge entity, SeatClassSurchargeRequest request) {
        entity.setSurcharge(request.surcharge());
        entity.setIsActive(!"INACTIVE".equalsIgnoreCase(request.status()));
    }

    private static SeatClass parseClass(String value) {
        return SeatClass.valueOf(value.trim().toUpperCase());
    }

    @Override
    protected void softDelete(SeatClassSurcharge entity) {
        entity.setIsDeleted(true);
        entity.setIsActive(false);
        entity.setDeletedAt(LocalDateTime.now());
    }

    @Override
    protected void setActive(SeatClassSurcharge entity, boolean active) {
        entity.setIsActive(active);
    }

    @Override
    protected Page<SeatClassSurcharge> search(ListQuery query, Map<String, String> filters) {
        Pageable pageable = PageMapper.toPageable(query, "fromClass", SORT_WHITELIST);
        return repository.findAll(buildSpec(query, filters), pageable);
    }

    private Specification<SeatClassSurcharge> buildSpec(ListQuery query, Map<String, String> filters) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));   // BR-LIST-04

            String from = filters.get("from_class");
            if (from != null) {
                predicates.add(cb.equal(root.get("fromClass"), parseClass(from)));
            }
            String to = filters.get("to_class");
            if (to != null) {
                predicates.add(cb.equal(root.get("toClass"), parseClass(to)));
            }
            String status = filters.get("status");
            if (status != null) {
                predicates.add(cb.equal(root.get("isActive"), "ACTIVE".equalsIgnoreCase(status)));
            }

            String keyword = query.getKeywordOrNull();
            if (keyword != null) {
                // Chỉ có 3 giá trị hạng → tìm theo chuỗi con của tên hạng.
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fromClass").as(String.class)), like),
                        cb.like(cb.lower(root.get("toClass").as(String.class)), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

- [ ] **Step 2: Biên dịch**

Run: `cd domestic-flight-booking-web-be && ./mvnw -q compile`
Expected: BUILD SUCCESS.

---

## Task 4: BE — Controller

**Files:**
- Create: `.../controller/SeatClassSurchargeController.java`

**Interfaces:**
- Consumes: Task 3 (`SeatClassSurchargeService`), `BaseCrudController` (methods: `list`, `getById`, `create`, `update`, `toggleActive`, `delete`, `bulkToggle`, `bulkDelete`, helper `filters(Object...)`).
- Produces: 8 endpoint dưới `/api/admin/seat-class-surcharges`.

- [ ] **Step 1: Tạo controller**

Theo khuôn `SeatTypeController`. **Bắt buộc** gắn `@PreAuthorize` cho **cả 2 method bulk** — `BaseCrudController` không gắn sẵn.

```java
package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.request.common.BulkRequest;
import com.example.domesticflightbookingwebbackend.dto.request.masterdata.SeatClassSurchargeRequest;
import com.example.domesticflightbookingwebbackend.dto.response.common.BulkResult;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.SeatClassSurchargeResponse;
import com.example.domesticflightbookingwebbackend.entity.SeatClassSurcharge;
import com.example.domesticflightbookingwebbackend.service.SeatClassSurchargeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Phụ thu nâng hạng ghế — CRUD đầy đủ, không có bản public.
 * Quyền dùng chung nhóm SEAT_CONFIG vì cùng thuộc cấu hình ghế.
 */
@RestController
@RequestMapping("/api/admin/seat-class-surcharges")
public class SeatClassSurchargeController
        extends BaseCrudController<SeatClassSurcharge, Long,
                                   SeatClassSurchargeRequest, SeatClassSurchargeResponse> {

    public SeatClassSurchargeController(SeatClassSurchargeService service) {
        super(service);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SEAT_CONFIG_READ')")
    public ApiResponse<PageData<SeatClassSurchargeResponse>> list(
            ListQuery query,
            @RequestParam(name = "from_class", required = false) String fromClass,
            @RequestParam(name = "to_class", required = false) String toClass,
            @RequestParam(name = "status", required = false) String status) {
        return list(query, filters("from_class", fromClass, "to_class", toClass, "status", status));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SEAT_CONFIG_READ_DETAIL')")
    public ApiResponse<SeatClassSurchargeResponse> getById(@PathVariable Long id) {
        return super.getById(id);
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('SEAT_CONFIG_CREATE')")
    public ResponseEntity<ApiResponse<SeatClassSurchargeResponse>> create(
            @Valid @RequestBody SeatClassSurchargeRequest request) {
        return super.create(request);
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SEAT_CONFIG_UPDATE')")
    public ApiResponse<SeatClassSurchargeResponse> update(
            @PathVariable Long id, @Valid @RequestBody SeatClassSurchargeRequest request) {
        return super.update(id, request);
    }

    @Override
    @PutMapping("/{id}/active")
    @PreAuthorize("hasAuthority('SEAT_CONFIG_UPDATE')")
    public ApiResponse<SeatClassSurchargeResponse> toggleActive(
            @PathVariable Long id, @RequestParam boolean value) {
        return super.toggleActive(id, value);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SEAT_CONFIG_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return super.delete(id);
    }

    @Override
    @PatchMapping("/bulk")
    @PreAuthorize("hasAuthority('SEAT_CONFIG_UPDATE')")
    public ApiResponse<BulkResult> bulkToggle(@Valid @RequestBody BulkRequest request) {
        return super.bulkToggle(request);
    }

    @Override
    @DeleteMapping("/bulk")
    @PreAuthorize("hasAuthority('SEAT_CONFIG_DELETE')")
    public ApiResponse<BulkResult> bulkDelete(@Valid @RequestBody BulkRequest request) {
        return super.bulkDelete(request);
    }
}
```

- [ ] **Step 2: Biên dịch**

Run: `cd domestic-flight-booking-web-be && ./mvnw -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Kiểm tra quyền đã gắn đủ**

Run: `grep -c "@PreAuthorize" domestic-flight-booking-web-be/src/main/java/com/example/domesticflightbookingwebbackend/controller/SeatClassSurchargeController.java`
Expected: `8`

---

## Task 5: BE — Unit test

**Files:**
- Create: `domestic-flight-booking-web-be/src/test/java/com/example/domesticflightbookingwebbackend/service/SeatClassSurchargeServiceTest.java`

**Interfaces:**
- Consumes: Task 3 (`SeatClassSurchargeService`), Task 2 (repository, mapper).
- Produces: test cho 5 hành vi trong Review Focus.

- [ ] **Step 1: Viết test thất bại trước**

Theo khuôn `SeatTypeServiceTest`: mock repository, dựng service trực tiếp, dùng `Validator` thật cho DTO.

```java
package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.request.masterdata.SeatClassSurchargeRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.SeatClassSurchargeResponse;
import com.example.domesticflightbookingwebbackend.entity.SeatClass;
import com.example.domesticflightbookingwebbackend.entity.SeatClassSurcharge;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.mapper.SeatClassSurchargeMapper;
import com.example.domesticflightbookingwebbackend.repository.SeatClassSurchargeRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SeatClassSurchargeServiceTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private SeatClassSurchargeRepository repository;
    private SeatClassSurchargeService service;

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        repository = mock(SeatClassSurchargeRepository.class);
        service = new SeatClassSurchargeService(repository, new SeatClassSurchargeMapper());
    }

    private SeatClassSurchargeRequest request(String from, String to, String amount) {
        return new SeatClassSurchargeRequest(from, to, new BigDecimal(amount), "ACTIVE");
    }

    @Test
    void create_capMoi_thanhCong() {
        when(repository.existsByFromClassAndToClassAndIsDeletedFalse(
                SeatClass.ECONOMY, SeatClass.BUSINESS)).thenReturn(false);
        when(repository.save(any(SeatClassSurcharge.class))).thenAnswer(inv -> {
            SeatClassSurcharge e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        SeatClassSurchargeResponse res = service.create(request("economy", "business", "500000"));

        assertThat(res.fromClass()).isEqualTo("ECONOMY");
        assertThat(res.toClass()).isEqualTo("BUSINESS");
        assertThat(res.surcharge()).isEqualByComparingTo("500000");
        assertThat(res.status()).isEqualTo("ACTIVE");
    }

    @Test
    void create_trungCap_nem1005() {
        when(repository.existsByFromClassAndToClassAndIsDeletedFalse(
                SeatClass.ECONOMY, SeatClass.BUSINESS)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("ECONOMY", "BUSINESS", "500000")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("ECONOMY → BUSINESS");
    }

    @Test
    void update_doiSangCapCuaBanGhiKhac_nem1005() {
        SeatClassSurcharge existing = new SeatClassSurcharge();
        existing.setId(7L);
        existing.setFromClass(SeatClass.ECONOMY);
        existing.setToClass(SeatClass.BUSINESS);
        when(repository.findById(7L)).thenReturn(Optional.of(existing));
        when(repository.existsByFromClassAndToClassAndIsDeletedFalseAndIdNot(
                SeatClass.BUSINESS, SeatClass.FIRST, 7L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(7L, request("BUSINESS", "FIRST", "700000")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void update_giuNguyenCapCuaChinhNo_thanhCong() {
        SeatClassSurcharge existing = new SeatClassSurcharge();
        existing.setId(7L);
        existing.setFromClass(SeatClass.ECONOMY);
        existing.setToClass(SeatClass.BUSINESS);
        when(repository.findById(7L)).thenReturn(Optional.of(existing));
        // Chỉ trùng chính nó → exists với IdNot phải trả false.
        when(repository.existsByFromClassAndToClassAndIsDeletedFalseAndIdNot(
                SeatClass.ECONOMY, SeatClass.BUSINESS, 7L)).thenReturn(false);
        when(repository.save(any(SeatClassSurcharge.class))).thenAnswer(inv -> inv.getArgument(0));

        SeatClassSurchargeResponse res = service.update(7L, request("ECONOMY", "BUSINESS", "650000"));

        assertThat(res.surcharge()).isEqualByComparingTo("650000");
    }

    @Test
    void delete_xoaMem_khongXoaCung() {
        SeatClassSurcharge existing = new SeatClassSurcharge();
        existing.setId(3L);
        when(repository.findById(3L)).thenReturn(Optional.of(existing));
        when(repository.save(any(SeatClassSurcharge.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(3L);

        ArgumentCaptor<SeatClassSurcharge> captor = ArgumentCaptor.forClass(SeatClassSurcharge.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getIsDeleted()).isTrue();
        assertThat(captor.getValue().getIsActive()).isFalse();
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
        verify(repository, never()).delete(any());
    }

    @Test
    void surchargeAm_viPhamValidate() {
        assertThat(violations("ECONOMY", "BUSINESS", "-1")).hasSize(1);
    }

    @Test
    void hangKhongHopLe_viPhamValidate() {
        assertThat(violations("PREMIUM_ECONOMY", "BUSINESS", "0")).hasSize(1);
    }

    private Set<ConstraintViolation<SeatClassSurchargeRequest>> violations(
            String from, String to, String amount) {
        return validator.validate(request(from, to, amount));
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận đỏ trước**

Run: `cd domestic-flight-booking-web-be && ./mvnw -q test -Dtest=SeatClassSurchargeServiceTest`
Expected: FAIL — chưa có class service (nếu Task 3 chưa xong) hoặc fail ở assertion.

- [ ] **Step 3: Chạy lại sau khi Task 3 hoàn tất**

Run: `cd domestic-flight-booking-web-be && ./mvnw -q test -Dtest=SeatClassSurchargeServiceTest`
Expected: 7 test PASS.

- [ ] **Step 4: Chạy toàn bộ test BE**

Run: `cd domestic-flight-booking-web-be && ./mvnw -q test`
Expected: toàn bộ xanh, số test tăng từ 57 lên 64.

---

## Task 6: FE — Model + Service + Mock

**Files:**
- Create: `domestic-flight-booking-web-fe/src/app/core/models/seat-surcharge.model.ts`
- Create: `domestic-flight-booking-web-fe/src/app/core/mock/data/seat-surcharge.mock.ts`
- Modify: `domestic-flight-booking-web-fe/src/app/core/services/catalog.service.ts`
- Modify: `domestic-flight-booking-web-fe/src/app/core/mock/mock-registry.ts`

**Interfaces:**
- Consumes: `BaseCrudService` (FE), `MockResource`, `SeatClass` type từ `fleet-news.model.ts`.
- Produces:
  - `SeatClassSurcharge` interface + `SeatClassSurchargePayload`.
  - `SeatClassSurchargeService extends BaseCrudService<SeatClassSurcharge>` với `super('admin/seat-class-surcharges')`.
  - `SEAT_CLASS_SURCHARGES_MOCK: SeatClassSurcharge[]` — 6 dòng.

- [ ] **Step 1: Tạo model**

```typescript
import { SeatClass } from '@core/models/fleet-news.model';
import { ActiveStatus } from '@core/models/airline.model';

/**
 * Phụ thu khi khách chọn ghế khác hạng với hạng vé đã mua.
 *
 * Không có dòng (from_class, to_class) = CHƯA CÓ CHÍNH SÁCH. Khác với dòng có
 * `surcharge = 0` (cố ý miễn phí). Đừng gộp hai trạng thái này.
 */
export interface SeatClassSurcharge {
  id: number;
  from_class: SeatClass;
  to_class: SeatClass;
  surcharge: number;
  status: ActiveStatus;
}

export type SeatClassSurchargePayload = Omit<SeatClassSurcharge, 'id'>;
```

- [ ] **Step 2: Tạo mock seed**

```typescript
import { SeatClassSurcharge } from '@core/models/seat-surcharge.model';

/** Seed khớp migration V14 — 6 cặp có chính sách. */
export const SEAT_CLASS_SURCHARGES_MOCK: SeatClassSurcharge[] = [
  { id: 1, from_class: 'ECONOMY',  to_class: 'ECONOMY',  surcharge: 0,       status: 'ACTIVE' },
  { id: 2, from_class: 'ECONOMY',  to_class: 'BUSINESS', surcharge: 500000,  status: 'ACTIVE' },
  { id: 3, from_class: 'ECONOMY',  to_class: 'FIRST',    surcharge: 1200000, status: 'ACTIVE' },
  { id: 4, from_class: 'BUSINESS', to_class: 'ECONOMY',  surcharge: 0,       status: 'ACTIVE' },
  { id: 5, from_class: 'BUSINESS', to_class: 'FIRST',    surcharge: 700000,  status: 'ACTIVE' },
  { id: 6, from_class: 'FIRST',    to_class: 'FIRST',    surcharge: 0,       status: 'ACTIVE' },
];
```

- [ ] **Step 3: Thêm service vào `catalog.service.ts`**

Thêm import model và class:

```typescript
@Injectable({ providedIn: 'root' })
export class SeatClassSurchargeService extends BaseCrudService<SeatClassSurcharge> {
  constructor() {
    super('admin/seat-class-surcharges');
  }
}
```

- [ ] **Step 4: Đăng ký mock resource**

Trong `mock-registry.ts`, thêm vào mảng `resources` (sau `admin/seat-types`):

```typescript
new MockResource<SeatClassSurcharge>({
  path: 'admin/seat-class-surcharges',
  seed: SEAT_CLASS_SURCHARGES_MOCK,
  searchFields: ['from_class', 'to_class'],
  noun: 'phụ thu nâng hạng',
  // UNIQUE là CẶP (from_class, to_class) nên không dùng được `uniqueField`.
  // `validate` chạy trước khi ghi, cho cả create và update.
  validate: (payload, existing) => {
    const from = payload.from_class;
    const to = payload.to_class;
    if (!from || !to) {
      return 'Hạng vé và hạng ghế không được để trống';
    }
    const id = (payload as { id?: number }).id;
    const clash = existing.some(
      (r) => r.id !== id && r.from_class === from && r.to_class === to,
    );
    return clash ? `Đã có phụ thu cho cặp ${from} → ${to}` : null;
  },
}),
```

> **Lưu ý:** `MockResource.update()` gọi `validate(merged, alive())` với
> `merged = { ...target, ...payload }` — nên `payload.id` **luôn có giá trị**
> (lấy từ bản ghi gốc). Vì vậy điều kiện `r.id !== payload.id` phân biệt đúng
> "trùng bản ghi khác" với "giữ nguyên cặp của chính nó". Không cần sửa
> `mock-resource.ts`.

- [ ] **Step 5: Kiểm lại hành vi update trong mock**

Đọc `mock-resource.ts` và xác nhận `update()` dùng `merged` (đã có `id`):

```typescript
const merged = { ...target, ...payload } as T;
const invalid = this.options.validate?.(merged, this.alive());
```

Nếu đúng như trên → `validate` ở Step 4 chạy đúng cho cả create và update. Không sửa gì thêm.

- [ ] **Step 5: Biên dịch**

Run: `cd domestic-flight-booking-web-fe && npx ng build`
Expected: BUILD SUCCESS.

---

## Task 7: FE — Sửa lưới ghế (Cụm A)

**Files:**
- Modify: `domestic-flight-booking-web-fe/src/app/features/admin/pages/seat-configurations/seat-grid.ts`
- Modify: `domestic-flight-booking-web-fe/src/app/features/admin/pages/seat-configurations/seat-grid.html`
- Modify: `domestic-flight-booking-web-fe/src/app/features/admin/pages/seat-configurations/seat-grid.css`
- Test: `domestic-flight-booking-web-fe/src/app/features/admin/pages/seat-configurations/seat-grid.spec.ts` (tạo mới)

**Interfaces:**
- Consumes: `AircraftSeat`, `SeatType` từ `@core/models/fleet-news.model`.
- Produces: `SeatGrid` với các method `cellBackground(seat)`, `cellTextColor(seat)`, `legendItems()`; input `seats`, `seatTypes` giữ nguyên.

- [ ] **Step 1: Viết test thất bại**

`seat-grid.spec.ts` — test qua DOM và qua hàm thuần đã export, không gọi method `protected`:

```typescript
import { TestBed } from '@angular/core/testing';
import { AircraftSeat, SeatType } from '@core/models/fleet-news.model';
import { SeatGrid, contrastRatio, relativeLuminance } from '@features/admin/pages/seat-configurations/seat-grid';

function seatType(id: number, cls: SeatType['seat_class'], color: string | null): SeatType {
  return {
    id,
    seat_type_code: `T${id}`,
    seat_type_name: `Loại ${id}`,
    seat_class: cls,
    color_hex: color,
    status: 'ACTIVE',
  };
}

function mount(seats: AircraftSeat[], seatTypes: SeatType[]) {
  TestBed.configureTestingModule({ imports: [SeatGrid] });
  const fixture = TestBed.createComponent(SeatGrid);
  fixture.componentRef.setInput('seats', seats);
  fixture.componentRef.setInput('seatTypes', seatTypes);
  fixture.detectChanges();
  return fixture;
}

function firstCell(fixture: ReturnType<typeof mount>): HTMLButtonElement {
  return (fixture.nativeElement as HTMLElement).querySelector('button.cell') as HTMLButtonElement;
}

describe('SeatGrid — màu theo color_hex', () => {
  it('dùng color_hex khi có', () => {
    const f = mount(
      [{ seat_row: 1, seat_column: 'A', seat_type_id: 1 }],
      [seatType(1, 'ECONOMY', '#90CAF9')],
    );
    expect(firstCell(f).style.background).toBe('rgb(144, 202, 249)');
  });

  it('fallback theo seat_class khi color_hex null', () => {
    const f = mount(
      [{ seat_row: 1, seat_column: 'A', seat_type_id: 2 }],
      [seatType(2, 'BUSINESS', null)],
    );
    // CSS var chưa resolve trong test → style chứa đúng chuỗi khai báo.
    expect(firstCell(f).getAttribute('style')).toContain('--color-accent');
  });

  it('#90CAF9 là nền SÁNG → chữ tối', () => {
    expect(contrastRatio('#90CAF9', '#ffffff')).toBeLessThan(4.5);
    const f = mount(
      [{ seat_row: 1, seat_column: 'A', seat_type_id: 1 }],
      [seatType(1, 'ECONOMY', '#90CAF9')],
    );
    expect(firstCell(f).style.color).toBe('rgb(17, 17, 17)');
  });

  it('#1E88E5 là nền TỐI → chữ trắng', () => {
    expect(contrastRatio('#1E88E5', '#ffffff')).toBeGreaterThanOrEqual(4.5);
    const f = mount(
      [{ seat_row: 1, seat_column: 'A', seat_type_id: 1 }],
      [seatType(1, 'ECONOMY', '#1E88E5')],
    );
    expect(firstCell(f).style.color).toBe('rgb(255, 255, 255)');
  });

  it('legend chỉ gồm loại ghế CÓ MẶT trong sơ đồ', () => {
    const f = mount(
      [
        { seat_row: 1, seat_column: 'A', seat_type_id: 1 },
        { seat_row: 1, seat_column: 'B', seat_type_id: 2 },
      ],
      [seatType(1, 'ECONOMY', '#90CAF9'), seatType(2, 'BUSINESS', '#F9A825'), seatType(3, 'FIRST', '#6A1B9A')],
    );
    const names = [...(f.nativeElement as HTMLElement).querySelectorAll('.legend__name')]
      .map((el) => el.textContent?.trim());
    expect(names).toEqual(['Loại 1', 'Loại 2']);
  });

  it('aria-label dùng TÊN loại ghế, không phải mã', () => {
    const f = mount(
      [{ seat_row: 1, seat_column: 'A', seat_type_id: 1 }],
      [seatType(1, 'ECONOMY', '#90CAF9')],
    );
    expect(firstCell(f).getAttribute('aria-label')).toContain('Loại 1');
  });

  it('hàm tương phản có gamma linearization — #90CAF9 sáng hơn #1E88E5', () => {
    expect(relativeLuminance('#90CAF9')).toBeGreaterThan(relativeLuminance('#1E88E5'));
  });
});
```

- [ ] **Step 2: Chạy test để xác nhận đỏ**

Run: `cd domestic-flight-booking-web-fe && npx ng test --watch=false --include='**/seat-grid.spec.ts'`
Expected: FAIL — `contrastRatio`/`relativeLuminance` chưa export, lưới chưa dùng `[style]`.

- [ ] **Step 3: Sửa `seat-grid.ts`**

Thay 3 method `seatAt`, `typeCode`, `classOf` bằng phiên bản có cache + màu. Thêm helper tính tương phản.

```typescript
  /** Tra cứu nhanh — tránh `find()` trong vòng lặp lồng (O(n²) → O(1)). */
  private readonly seatTypeById = computed(() => {
    const map = new Map<number, SeatType>();
    for (const t of this.seatTypes()) {
      map.set(t.id, t);
    }
    return map;
  });

  private readonly seatByKey = computed(() => {
    const map = new Map<string, AircraftSeat>();
    for (const s of this.seats()) {
      map.set(`${s.seat_row}-${s.seat_column}`, s);
    }
    return map;
  });

  protected seatAt(row: number, column: string): AircraftSeat | null {
    return this.seatByKey().get(`${row}-${column}`) ?? null;
  }

  /** Mã loại ghế để hiện trong ô, ví dụ "ECO". */
  protected typeCode(seatTypeId: number): string {
    return this.seatTypeById().get(seatTypeId)?.seat_type_code ?? '?';
  }

  /** Tên loại ghế cho aria-label — đọc lên mới hiểu, khác mã thô. */
  protected typeName(seatTypeId: number): string {
    return this.seatTypeById().get(seatTypeId)?.seat_type_name ?? 'Không rõ loại ghế';
  }

  /** Màu nền: ưu tiên color_hex, fallback theo seat_class. */
  protected cellBackground(seat: AircraftSeat): string {
    const type = this.seatTypeById().get(seat.seat_type_id);
    if (type?.color_hex) {
      return type.color_hex;
    }
    return this.fallbackColor(type?.seat_class ?? 'ECONOMY');
  }

  /** Màu chữ theo độ tương phản WCAG 2.1 (có gamma linearization). */
  protected cellTextColor(seat: AircraftSeat): string {
    const bg = this.cellBackground(seat);
    // `var(--...)` không parse được thành hex → coi như nền tối, chữ trắng.
    if (!bg.startsWith('#')) {
      return '#fff';
    }
    return contrastRatio(bg, '#ffffff') >= 4.5 ? '#fff' : '#111';
  }

  /** Màu mặc định theo hạng — dùng cho lưới và legend khi color_hex null. */
  protected fallbackColor(seatClass: SeatType['seat_class']): string {
    switch (seatClass) {
      case 'BUSINESS':
        return 'var(--color-accent)';
      case 'FIRST':
        return 'var(--color-danger)';
      default:
        return 'var(--color-primary-bright)';
    }
  }

  /** Chú thích màu — chỉ loại ghế CÓ MẶT trong sơ đồ đang xem. */
  protected readonly legendItems = computed(() => {
    const usedIds = new Set(this.seats().map((s) => s.seat_type_id));
    return [...usedIds]
      .map((id) => this.seatTypeById().get(id))
      .filter((t): t is SeatType => t !== undefined)
      .sort((a, b) => a.seat_type_code.localeCompare(b.seat_type_code));
  });
```

Thêm hàm thuần ở cuối file (export để test được):

```typescript
/** Kênh màu [0..255] → độ sáng tuyến tính sRGB (WCAG 2.1). */
function channelToLinear(value: number): number {
  const c = value / 255;
  return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
}

/** Độ sáng tương đối của màu hex `#RRGGBB` hoặc `#RGB`. */
export function relativeLuminance(hex: string): number {
  const m = hex.trim().match(/^#?([0-9a-f]{3}|[0-9a-f]{6})$/i);
  if (!m) {
    return 0;   // màu CSS khác (var(...)) → coi như tối
  }
  const raw = m[1];
  const full = raw.length === 3 ? raw.split('').map((c) => c + c).join('') : raw;
  const r = parseInt(full.slice(0, 2), 16);
  const g = parseInt(full.slice(2, 4), 16);
  const b = parseInt(full.slice(4, 6), 16);
  return 0.2126 * channelToLinear(r) + 0.7152 * channelToLinear(g) + 0.0722 * channelToLinear(b);
}

/** Tỉ lệ tương phản giữa hai màu hex (WCAG 2.1). */
export function contrastRatio(a: string, b: string): number {
  const la = relativeLuminance(a);
  const lb = relativeLuminance(b);
  const light = Math.max(la, lb);
  const dark = Math.min(la, lb);
  return (light + 0.05) / (dark + 0.05);
}
```

- [ ] **Step 4: Sửa `seat-grid.html`**

```html
<div class="grid-wrap">
  <table class="grid">
    <caption class="sr-only">Lưới ghế xem trước</caption>
    <thead>
      <tr>
        <th class="grid__corner" scope="col"></th>
        @for (col of columns(); track col) {
          <th class="grid__colhead" scope="col">{{ col }}</th>
        }
      </tr>
    </thead>
    <tbody>
      @for (row of rows(); track row) {
        <tr>
          <th class="grid__rowhead" scope="row">{{ row }}</th>
          @for (col of columns(); track col) {
            @if (seatAt(row, col); as seat) {
              <td>
                <button
                  type="button"
                  class="cell"
                  [style.background]="cellBackground(seat)"
                  [style.color]="cellTextColor(seat)"
                  [attr.aria-label]="'Ghế ' + row + col + ', ' + typeName(seat.seat_type_id)"
                  (click)="onCell(seat)"
                >
                  {{ typeCode(seat.seat_type_id) }}
                </button>
              </td>
            } @else {
              <td class="grid__gap" aria-hidden="true"></td>
            }
          }
        </tr>
      }
    </tbody>
  </table>
</div>

@if (legendItems().length > 0) {
  <ul class="legend" aria-label="Chú thích màu ghế">
    @for (t of legendItems(); track t.id) {
      <li class="legend__item">
        <span class="legend__swatch" [style.background]="t.color_hex ?? fallbackColor(t.seat_class)"></span>
        <span class="legend__name">{{ t.seat_type_name }}</span>
        @if (!t.color_hex) {
          <span class="legend__note">mặc định</span>
        }
      </li>
    }
  </ul>
}
```

- [ ] **Step 5: Thêm `fallbackColor` vào `seat-grid.ts`**

```typescript
  /** Màu mặc định theo hạng — dùng cho legend khi color_hex null. */
  protected fallbackColor(seatClass: SeatType['seat_class']): string {
    switch (seatClass) {
      case 'BUSINESS':
        return 'var(--color-accent)';
      case 'FIRST':
        return 'var(--color-danger)';
      default:
        return 'var(--color-primary-bright)';
    }
  }
```

- [ ] **Step 5: Sửa `seat-grid.css`**

Giữ `.cell` cơ bản, bỏ `color: #fff` cố định (đã chuyển sang `[style]`), thêm focus và style legend:

```css
.cell {
  width: 38px;
  height: 38px;
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  font-family: var(--font-body);
  font-size: 10px;
  font-weight: 700;
  cursor: pointer;
  transition: transform var(--dur-fast) var(--ease);
}

.cell:hover {
  transform: scale(1.08);
}

.cell:focus-visible {
  outline: 2px solid var(--color-primary-bright);
  outline-offset: 2px;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-4);
  padding: 0;
  list-style: none;
}

.legend__item {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 12px;
  color: var(--color-ink-subtle);
}

.legend__swatch {
  width: 14px;
  height: 14px;
  border-radius: 3px;
  border: 1px solid var(--color-border);
}

.legend__note {
  font-style: italic;
}
```

> `.cell--economy/.cell--business/.cell--first` **xóa hẳn** — màu nay đi qua `[style]` binding từ `cellBackground()`, giữ lại class cũ sẽ thành mã chết.

- [ ] **Step 6: Chạy test**

Run: `cd domestic-flight-booking-web-fe && npx ng test --watch=false --include='**/seat-grid.spec.ts'`
Expected: 7 test PASS.

---

## Task 8: FE — CRUD color picker (Cụm B)

**Files:**
- Modify: `src/app/shared/crud/crud-config.ts`
- Modify: `src/app/shared/crud/crud-page.ts`
- Modify: `src/app/shared/crud/crud-page.html`
- Modify: `src/app/features/admin/pages/seat-types/seat-types.ts`

**Interfaces:**
- Produces: `CrudFieldType` mở rộng thêm `'color'`; `CrudColumn.kind` mở rộng thêm `'color'`.

- [ ] **Step 1: Mở rộng type union**

Trong `crud-config.ts`:

```typescript
export type CrudFieldType = 'text' | 'number' | 'date' | 'select' | 'textarea' | 'toggle' | 'image' | 'color';
```

Và trong `CrudColumn`:

```typescript
  /** `code`: chữ đậm, số thẳng cột. `num`: canh phải. `color`: ô vuông màu. */
  kind?: 'code' | 'num' | 'color';
```

- [ ] **Step 2: Sửa `defaultFor` trong `crud-page.ts`**

```typescript
  private defaultFor(field: CrudField): unknown {
    switch (field.type) {
      case 'toggle':
        return true;
      case 'number':
        return null;
      case 'color':
        // `null` chứ không phải '': BE validate bằng @Pattern hex, chuỗi rỗng
        // sẽ trượt. null = chưa đặt màu, hợp lệ.
        return null;
      case 'select':
        return field.options?.length ? field.options[0].value : null;
      default:
        return '';
    }
  }
```

- [ ] **Step 3: Render ô color trong `crud-page.html`**

Thêm `@case ('color')` ngay trước `@default`:

```html
                @case ('color') {
                  <div class="colorfield">
                    <input
                      [id]="'cf-' + f.key"
                      type="color"
                      [value]="asString(fieldValue(f.key)) || '#000000'"
                      (input)="setField(f.key, asColor($any($event.target).value))"
                      [name]="f.key"
                      aria-label="Chọn màu"
                    />
                    <input
                      type="text"
                      class="colorfield__hex"
                      [value]="asString(fieldValue(f.key))"
                      (input)="setField(f.key, asColor($any($event.target).value))"
                      maxlength="7"
                      placeholder="#RRGGBB"
                      [attr.aria-label]="f.label + ' — mã màu'"
                    />
                    @if (fieldValue(f.key)) {
                      <button class="colorfield__clear" type="button" (click)="setField(f.key, null)">
                        Bỏ màu
                      </button>
                    }
                  </div>
                }
```

Thêm method vào `crud-page.ts`:

```typescript
  /** Chuẩn hoá giá trị ô màu: rỗng → null, ngược lại chữ HOA. */
  protected asColor(value: string): string | null {
    const v = (value ?? '').trim();
    return v.length === 0 ? null : v.toUpperCase();
  }
```

- [ ] **Step 4: Render cột màu trong `crud-page.html`**

Sửa nhánh `@if (c.image) ... @else` — thêm nhánh `c.kind === 'color'` **trước** nhánh `c.tone`:

```html
                      } @else if (c.kind === 'color') {
                        @if (c.value(row)) {
                          <span class="swatch" [style.background]="c.value(row)"></span>
                          <span class="swatch__hex">{{ c.value(row) }}</span>
                        } @else {
                          <span class="swatch swatch--none" aria-hidden="true"></span>
                          <span class="swatch__hex">Mặc định</span>
                        }
                      } @else if (c.tone) {
```

Thêm CSS vào `crud-page.css`:

```css
.swatch {
  display: inline-block;
  width: 16px;
  height: 16px;
  border-radius: 4px;
  border: 1px solid var(--color-border);
  vertical-align: middle;
  margin-right: var(--space-2);
}

.swatch--none {
  background: repeating-linear-gradient(
    45deg,
    var(--color-surface-alt),
    var(--color-surface-alt) 3px,
    var(--color-border) 3px,
    var(--color-border) 6px
  );
}

.swatch__hex {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
}

.colorfield {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.colorfield input[type='color'] {
  width: 44px;
  height: 36px;
  padding: 2px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  cursor: pointer;
}

.colorfield__hex {
  width: 110px;
  font-family: var(--font-mono, monospace);
  text-transform: uppercase;
}

.colorfield__clear {
  border: none;
  background: none;
  color: var(--color-danger);
  font-size: 12px;
  cursor: pointer;
  text-decoration: underline;
}
```

- [ ] **Step 5: Dùng trong `seat-types.ts`**

Đổi field `color_hex`:

```typescript
      {
        key: 'color_hex', label: 'Màu hiển thị', type: 'color',
        hint: 'Dùng để tô ô trên sơ đồ ghế. Để trống thì dùng màu mặc định theo hạng.',
      },
```

Thêm cột vào `columns` (sau cột "Hạng ghế"):

```typescript
      {
        label: 'Màu',
        value: (r) => r.color_hex ?? '',
        kind: 'color',
        width: '140px',
      },
```

- [ ] **Step 6: Build + test**

Run: `cd domestic-flight-booking-web-fe && npx ng build`
Expected: BUILD SUCCESS.

Run: `cd domestic-flight-booking-web-fe && npx ng test --watch=false`
Expected: toàn bộ xanh (115 test cũ + 6 test mới từ Task 7).

---

## Task 9: FE — Màn CRUD phụ thu + sidebar (Cụm C)

**Files:**
- Create: `src/app/features/admin/pages/seat-class-surcharges/seat-class-surcharges.ts`
- Modify: `src/app/app.routes.ts`
- Modify: `src/app/features/admin/layout/admin-layout.ts`

**Interfaces:**
- Consumes: Task 6 (`SeatClassSurchargeService`, `SeatClassSurcharge` model), Task 8 (`type: 'color'` không dùng ở đây nhưng phải có sẵn).
- Produces: route `/admin/seat-class-surcharges` (4 route con), 2 mục sidebar.

- [ ] **Step 1: Tạo component config**

```typescript
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { SeatClassSurcharge } from '@core/models/seat-surcharge.model';
import { SeatClassSurchargeService } from '@core/services/catalog.service';
import { CrudConfig } from '@shared/crud/crud-config';
import { CrudPage } from '@shared/crud/crud-page';

const CLASS_LABEL: Record<string, string> = {
  ECONOMY: 'Phổ thông',
  BUSINESS: 'Thương gia',
  FIRST: 'Hạng nhất',
};

const CLASS_OPTIONS = [
  { value: 'ECONOMY', label: 'Phổ thông' },
  { value: 'BUSINESS', label: 'Thương gia' },
  { value: 'FIRST', label: 'Hạng nhất' },
];

/**
 * Phụ thu nâng hạng ghế — mặc định dùng chung mọi chuyến.
 *
 * Không có dòng cho một cặp nghĩa là CHƯA CÓ CHÍNH SÁCH; muốn miễn phí thì
 * tạo dòng phụ thu 0 tường minh.
 */
@Component({
  selector: 'app-admin-seat-class-surcharges',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CrudPage],
  template: `<app-crud-page [config]="config" [service]="service" />`,
})
export class AdminSeatClassSurcharges {
  protected readonly service = inject(SeatClassSurchargeService);

  protected readonly config: CrudConfig<SeatClassSurcharge, Partial<SeatClassSurcharge>> = {
    title: 'Phụ thu nâng hạng',
    subtitle: 'Phụ thu khi khách chọn ghế khác hạng với hạng vé đã mua',
    createLabel: 'Thêm mức phụ thu',
    searchPlaceholder: 'Tìm theo hạng vé hoặc hạng ghế',
    emptyIcon: 'payments',
    emptyMessage: 'Chưa có mức phụ thu nào',
    emptyHint: 'Thêm mức phụ thu đầu tiên để bắt đầu',
    editTitle: 'Sửa mức phụ thu',
    deleteTitle: () => 'Xóa mức phụ thu?',
    deleteMessage: (r) =>
      `Xóa phụ thu ${CLASS_LABEL[r.from_class]} → ${CLASS_LABEL[r.to_class]}? `
      + 'Sau khi xóa, cặp này được coi là CHƯA CÓ CHÍNH SÁCH.',
    columns: [
      {
        label: 'Hạng vé',
        value: (r) => CLASS_LABEL[r.from_class] ?? r.from_class,
        kind: 'code',
        width: '140px',
      },
      {
        label: 'Hạng ghế',
        value: (r) => CLASS_LABEL[r.to_class] ?? r.to_class,
        kind: 'code',
        width: '140px',
      },
      {
        label: 'Phụ thu',
        value: (r) => (r.surcharge === 0 ? 'Miễn phí' : `${r.surcharge.toLocaleString('vi-VN')}₫`),
        kind: 'num',
      },
      {
        label: 'Trạng thái',
        value: (r) => (r.status === 'ACTIVE' ? 'Đang áp dụng' : 'Tạm dừng'),
        tone: (r) => (r.status === 'ACTIVE' ? 'success' : 'neutral'),
      },
    ],
    fields: [
      {
        key: 'from_class', shownInTable: true,
        label: 'Hạng vé', type: 'select', required: true, options: CLASS_OPTIONS,
        hint: 'Hạng vé khách đã mua',
      },
      {
        key: 'to_class', shownInTable: true,
        label: 'Hạng ghế', type: 'select', required: true, options: CLASS_OPTIONS,
        hint: 'Hạng của ghế khách chọn',
      },
      {
        key: 'surcharge', shownInTable: true,
        label: 'Phụ thu (₫)', type: 'number', required: true, min: 0,
        hint: 'Nhập 0 nếu muốn miễn phí cho cặp này',
      },
      {
        key: 'status', shownInTable: true,
        label: 'Trạng thái', type: 'select',
        hint: 'Đang áp dụng',
        options: [
          { value: 'ACTIVE', label: 'Đang áp dụng' },
          { value: 'INACTIVE', label: 'Tạm dừng' },
        ],
      },
    ],
    filters: [
      {
        key: 'from_class', label: 'Lọc theo hạng vé', defaultValue: '',
        options: [{ value: '', label: 'Tất cả hạng vé' }, ...CLASS_OPTIONS],
      },
      {
        key: 'to_class', label: 'Lọc theo hạng ghế', defaultValue: '',
        options: [{ value: '', label: 'Tất cả hạng ghế' }, ...CLASS_OPTIONS],
      },
      {
        key: 'status', label: 'Lọc theo trạng thái', defaultValue: '',
        options: [
          { value: '', label: 'Tất cả trạng thái' },
          { value: 'ACTIVE', label: 'Đang áp dụng' },
          { value: 'INACTIVE', label: 'Tạm dừng' },
        ],
      },
    ],
  };
}
```

- [ ] **Step 2: Thêm route**

Trong `app.routes.ts`, sau `crudRoutes('seat-types', ...)`:

```typescript
      ...crudRoutes('seat-class-surcharges', 'Phụ thu nâng hạng', () =>
        import('@features/admin/pages/seat-class-surcharges/seat-class-surcharges').then(
          (m) => m.AdminSeatClassSurcharges,
        ),
        { adminOnly: true },
      ),
```

- [ ] **Step 3: Thêm 2 mục sidebar**

Trong `admin-layout.ts`, nhóm "Danh mục hàng không", sau mục `aircrafts`:

```typescript
        { path: '/admin/seat-types', label: 'Loại ghế', icon: 'event_seat', adminOnly: true },
        { path: '/admin/seat-class-surcharges', label: 'Phụ thu nâng hạng', icon: 'payments', adminOnly: true },
```

- [ ] **Step 4: Build + test**

Run: `cd domestic-flight-booking-web-fe && npx ng build`
Expected: BUILD SUCCESS.

Run: `cd domestic-flight-booking-web-fe && npx ng test --watch=false`
Expected: toàn bộ xanh.

---

## Task 10: Kiểm chứng toàn cục

**Files:** không sửa.

- [ ] **Step 1: Build + test BE**

Run: `cd domestic-flight-booking-web-be && ./mvnw -q test`
Expected: toàn bộ PASS, số test ≥ 64.

- [ ] **Step 2: Build + test FE**

Run: `cd domestic-flight-booking-web-fe && npx ng build && npx ng test --watch=false`
Expected: BUILD SUCCESS; test PASS.

- [ ] **Step 3: Kiểm không còn `/api/v1`**

Run: `grep -rn "/api/v1" domestic-flight-booking-web-fe/src domestic-flight-booking-web-be/src/main/java`
Expected: không có kết quả.

- [ ] **Step 4: Kiểm quyền đủ trên controller mới**

Run: `grep -c "@PreAuthorize" domestic-flight-booking-web-be/src/main/java/com/example/domesticflightbookingwebbackend/controller/SeatClassSurchargeController.java`
Expected: `8`

- [ ] **Step 5: Kiểm thủ công với mock**

1. Đặt `useMock: true` trong `src/environments/environment.ts`.
2. Mở `/admin/seat-types` → thấy cột "Màu", form có color picker + nút "Bỏ màu".
3. Mở `/admin/seat-class-surcharges` → thấy 6 dòng seed, sửa/xóa/thêm được.
4. Mở `/admin/aircrafts` → "Cấu hình" một máy bay → khai khối → "Sinh lưới" → lưới hiện 8 màu khác nhau, chữ đọc được, legend hiện đúng.
5. Đặt lại `useMock: false`.

- [ ] **Step 6: Re-index codebase memory**

Chạy `index_repository` cho cả 2 repo theo quy tắc [[feedback-index-codebase-memory]].

- [ ] **Step 7: Cập nhật tài liệu**

- `docs/features/da-2026-10-19-ghe-va-phu-thu/README.md`: đổi trạng thái 3 cụm thành "Xong".
- `docs/API_Design_Website_Dat_Ve_May_Bay_v0.4.md`: thêm mục 2.6b cho 8 endpoint mới.
- `docs/Database_Design_Domestic_Flight_Booking_v0.5.md`: thêm bảng `seat_class_surcharges` vào nhóm B.

---

## Thứ tự thực thi

Task 1→5 là BE, Task 6→9 là FE. Có thể chạy song song **Task 1–5** với **Task 7–8** (không phụ thuộc), nhưng Task 6 và Task 9 phụ thuộc Task 3 (hợp đồng API) để biết hình dạng response.

Đề xuất: làm tuần tự 1→10 để mỗi bước đều có test xác nhận.
