package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.request.flight.FlightRequest;
import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightDetailResponse;
import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightResponse;
import com.example.domesticflightbookingwebbackend.service.FlightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;

/**
 * Quản lý chuyến bay — vùng quản trị ADMIN/STAFF (Phase 2 — B2.1, API v0.4 mục 3).
 */
@RestController
@RequestMapping("/api/admin/flights")
@RequiredArgsConstructor
public class FlightController {

    private final FlightService flightService;

    @GetMapping
    @PreAuthorize("hasAuthority('FLIGHT_READ')")
    public ApiResponse<PageData<FlightResponse>> list(
            ListQuery query,
            @RequestParam(name = "airline_id", required = false) Long airlineId,
            @RequestParam(name = "departure_airport_id", required = false) Long departureAirportId,
            @RequestParam(name = "arrival_airport_id", required = false) Long arrivalAirportId,
            @RequestParam(name = "departure_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate,
            @RequestParam(name = "operation_status", required = false) String operationStatus,
            @RequestParam(name = "sales_status", required = false) String salesStatus,
            @RequestParam(name = "status", required = false) String status) {
        String effectiveOpStatus = (operationStatus != null && !operationStatus.isBlank())
                ? operationStatus
                : status;
        return ApiResponse.success(flightService.list(
                query, airlineId, departureAirportId, arrivalAirportId,
                departureDate, effectiveOpStatus, salesStatus));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FLIGHT_DETAIL_READ')")
    public ApiResponse<FlightDetailResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(flightService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FLIGHT_CREATE')")
    public ResponseEntity<ApiResponse<FlightResponse>> create(@Valid @RequestBody FlightRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo chuyến bay thành công", flightService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FLIGHT_UPDATE')")
    public ApiResponse<FlightResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody FlightRequest request) {
        return ApiResponse.success("Cập nhật chuyến bay thành công", flightService.update(id, request));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('FLIGHT_ACTIVATE')")
    public ApiResponse<FlightResponse> activate(@PathVariable Long id) {
        return ApiResponse.success("Mở bán chuyến bay thành công", flightService.activate(id));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('FLIGHT_DEACTIVATE')")
    public ApiResponse<FlightResponse> deactivate(@PathVariable Long id) {
        return ApiResponse.success("Đóng bán chuyến bay thành công", flightService.deactivate(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FLIGHT_DEACTIVATE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        flightService.delete(id);
        return ApiResponse.success(null);
    }
}
