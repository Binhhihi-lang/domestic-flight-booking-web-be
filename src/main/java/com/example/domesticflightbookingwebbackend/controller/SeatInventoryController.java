package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.dto.request.flight.SeatInventoryRequest;
import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightSeatMapResponse;
import com.example.domesticflightbookingwebbackend.service.SeatInventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản lý tồn ghế theo chuyến bay — vùng quản trị ADMIN/STAFF (Phase 2 — B2.3, API v0.4 mục 3).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class SeatInventoryController {

    private final SeatInventoryService seatInventoryService;

    @GetMapping({"/flights/{flightId}/seat-map", "/flights/{flightId}/seat-inventory"})
    @PreAuthorize("hasAuthority('SEAT_INVENTORY_READ_DETAIL')")
    public ApiResponse<FlightSeatMapResponse> getSeatMap(@PathVariable Long flightId) {
        return ApiResponse.success(seatInventoryService.getSeatMap(flightId));
    }

    @PutMapping("/flights/{flightId}/seat-inventory")
    @PreAuthorize("hasAuthority('SEAT_INVENTORY_MANAGE')")
    public ApiResponse<FlightSeatMapResponse> saveInventory(
            @PathVariable Long flightId,
            @Valid @RequestBody(required = false) SeatInventoryRequest request) {
        return ApiResponse.success("Cập nhật tồn ghế chuyến bay thành công",
                seatInventoryService.saveInventory(flightId, request));
    }

    @PostMapping("/flight-seats/{flightSeatId}/block")
    @PreAuthorize("hasAuthority('SEAT_INVENTORY_MANAGE')")
    public ApiResponse<Void> block(@PathVariable Long flightSeatId) {
        seatInventoryService.block(flightSeatId);
        return ApiResponse.success("Đã khóa ghế", null);
    }

    @PostMapping("/flight-seats/{flightSeatId}/unblock")
    @PreAuthorize("hasAuthority('SEAT_INVENTORY_MANAGE')")
    public ApiResponse<Void> unblock(@PathVariable Long flightSeatId) {
        seatInventoryService.unblock(flightSeatId);
        return ApiResponse.success("Đã mở khóa ghế", null);
    }
}
