package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.dto.request.masterdata.SeatMapRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.SeatMapResponse;
import com.example.domesticflightbookingwebbackend.service.SeatMapService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sơ đồ ghế của máy bay (mục 2.6 API v0.2) — thao tác theo cả sơ đồ. */
@RestController
@RequestMapping("/api/admin/aircrafts/{aircraftId}/seat-map")
@RequiredArgsConstructor
public class SeatMapController {

    private final SeatMapService seatMapService;

    @GetMapping
    @PreAuthorize("hasAuthority('SEAT_CONFIG_READ_DETAIL')")
    public ApiResponse<SeatMapResponse> getSeatMap(@PathVariable Long aircraftId) {
        return ApiResponse.success(seatMapService.getSeatMap(aircraftId));
    }

    /** PUT thay thế toàn bộ sơ đồ trong một transaction. */
    @PutMapping
    @PreAuthorize("hasAuthority('SEAT_CONFIG_UPDATE')")
    public ApiResponse<SeatMapResponse> saveSeatMap(@PathVariable Long aircraftId,
                                                    @Valid @RequestBody SeatMapRequest request) {
        return ApiResponse.success("Lưu sơ đồ ghế thành công",
                seatMapService.saveSeatMap(aircraftId, request));
    }
}
