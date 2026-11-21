package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.request.flight.FlightFareRequest;
import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightFareResponse;
import com.example.domesticflightbookingwebbackend.service.FlightFareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản lý giá bán theo chuyến bay — vùng quản trị ADMIN/STAFF (Phase 2 — B2.2, API v0.4 mục 3).
 */
@RestController
@RequestMapping("/api/admin/flight-fares")
@RequiredArgsConstructor
public class FlightFareController {

    private final FlightFareService flightFareService;

    @GetMapping
    @PreAuthorize("hasAuthority('FLIGHT_FARE_READ')")
    public ApiResponse<PageData<FlightFareResponse>> list(
            ListQuery query,
            @RequestParam(name = "flight_id", required = false) Long flightId,
            @RequestParam(name = "fare_class_id", required = false) Long fareClassId) {
        return ApiResponse.success(flightFareService.list(query, flightId, fareClassId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FLIGHT_FARE_READ_DETAIL')")
    public ApiResponse<FlightFareResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(flightFareService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FLIGHT_FARE_CREATE')")
    public ResponseEntity<ApiResponse<FlightFareResponse>> create(
            @Valid @RequestBody FlightFareRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo giá vé theo chuyến thành công", flightFareService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FLIGHT_FARE_UPDATE')")
    public ApiResponse<FlightFareResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody FlightFareRequest request) {
        return ApiResponse.success("Cập nhật giá vé theo chuyến thành công", flightFareService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FLIGHT_FARE_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        flightFareService.delete(id);
        return ApiResponse.success(null);
    }
}
