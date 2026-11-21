package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.request.common.BulkRequest;
import com.example.domesticflightbookingwebbackend.dto.request.masterdata.FareRuleRequest;
import com.example.domesticflightbookingwebbackend.dto.response.common.BulkResult;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.FareRuleResponse;
import com.example.domesticflightbookingwebbackend.entity.FareRule;
import com.example.domesticflightbookingwebbackend.service.FareRuleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Quy định hoàn vé — quản trị CRUD (Phase 2). */
@RestController
@RequestMapping("/api/admin/fare-rules")
public class FareRuleController extends BaseCrudController<FareRule, Long, FareRuleRequest, FareRuleResponse> {

    public FareRuleController(FareRuleService service) {
        super(service);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FARE_RULE_READ')")
    public ApiResponse<PageData<FareRuleResponse>> list(
            ListQuery query,
            @RequestParam(name = "fare_class_id", required = false) String fareClassId,
            @RequestParam(name = "is_refundable", required = false) String isRefundable) {
        return list(query, filters("fare_class_id", fareClassId, "is_refundable", isRefundable));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FARE_RULE_READ_DETAIL')")
    public ApiResponse<FareRuleResponse> getById(@PathVariable Long id) {
        return super.getById(id);
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('FARE_RULE_CREATE')")
    public ResponseEntity<ApiResponse<FareRuleResponse>> create(@Valid @RequestBody FareRuleRequest request) {
        return super.create(request);
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FARE_RULE_UPDATE')")
    public ApiResponse<FareRuleResponse> update(@PathVariable Long id, @Valid @RequestBody FareRuleRequest request) {
        return super.update(id, request);
    }

    @Override
    @PutMapping("/{id}/active")
    @PreAuthorize("hasAuthority('FARE_RULE_UPDATE')")
    public ApiResponse<FareRuleResponse> toggleActive(@PathVariable Long id, @RequestParam boolean value) {
        return super.toggleActive(id, value);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FARE_RULE_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return super.delete(id);
    }

    @Override
    @PatchMapping("/bulk")
    @PreAuthorize("hasAuthority('FARE_RULE_UPDATE')")
    public ApiResponse<BulkResult> bulkToggle(@Valid @RequestBody BulkRequest request) {
        return super.bulkToggle(request);
    }

    @Override
    @DeleteMapping("/bulk")
    @PreAuthorize("hasAuthority('FARE_RULE_DELETE')")
    public ApiResponse<BulkResult> bulkDelete(@Valid @RequestBody BulkRequest request) {
        return super.bulkDelete(request);
    }
}
