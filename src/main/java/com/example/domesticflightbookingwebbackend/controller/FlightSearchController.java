package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightDetailResponse;
import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightResponse;
import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightSearchResult;
import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightSeatMapResponse;
import com.example.domesticflightbookingwebbackend.entity.Airport;
import com.example.domesticflightbookingwebbackend.entity.TripType;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.repository.AirportRepository;
import com.example.domesticflightbookingwebbackend.service.FlightService;
import com.example.domesticflightbookingwebbackend.service.SeatInventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Tìm kiếm và tra cứu chuyến bay công khai cho khách — vùng {@code /api/public/flights}
 * (Phase 2 — B2.1, API v0.4 mục 3).
 */
@RestController
@RequestMapping("/api/public/flights")
@RequiredArgsConstructor
public class FlightSearchController {

    private final FlightService flightService;
    private final SeatInventoryService seatInventoryService;
    private final AirportRepository airportRepository;

    /**
     * Tìm chuyến bay theo tuyến/ngày ({@code GET /api/public/flights/search}).
     * Nhận ID hoặc mã sân bay (IATA code), và tách {@code adults} + {@code children} + {@code infants}.
     */
    @GetMapping("/search")
    public ApiResponse<List<FlightSearchResult>> search(
            @RequestParam(name = "departure_airport_id", required = false) Long departureAirportId,
            @RequestParam(name = "arrival_airport_id", required = false) Long arrivalAirportId,
            @RequestParam(name = "departure_airport_code", required = false) String departureAirportCode,
            @RequestParam(name = "arrival_airport_code", required = false) String arrivalAirportCode,
            @RequestParam(name = "departure_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate,
            @RequestParam(name = "passengers", required = false) Integer passengers,
            @RequestParam(name = "adults", required = false) Integer adults,
            @RequestParam(name = "children", required = false) Integer children,
            @RequestParam(name = "infants", required = false) Integer infants,
            @RequestParam(name = "trip_type", defaultValue = "ONE_WAY") TripType tripType) {

        Long resolvedDepId = resolveAirportId(departureAirportId, departureAirportCode, "Sân bay đi");
        Long resolvedArrId = resolveAirportId(arrivalAirportId, arrivalAirportCode, "Sân bay đến");
        LocalDate effectiveDate = departureDate != null ? departureDate : LocalDate.now().plusDays(1);

        int seatPassengers;
        if (passengers != null) {
            seatPassengers = passengers;
        } else {
            int adt = adults != null ? adults : 1;
            int chd = children != null ? children : 0;
            seatPassengers = adt + chd;
        }
        return ApiResponse.success(flightService.search(
                resolvedDepId, resolvedArrId, effectiveDate, seatPassengers, tripType));
    }

    @GetMapping
    public ApiResponse<PageData<FlightResponse>> list(
            ListQuery query,
            @RequestParam(name = "airline_id", required = false) Long airlineId,
            @RequestParam(name = "departure_airport_id", required = false) Long departureAirportId,
            @RequestParam(name = "arrival_airport_id", required = false) Long arrivalAirportId,
            @RequestParam(name = "departure_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate) {
        return ApiResponse.success(flightService.listPublic(
                query, airlineId, departureAirportId, arrivalAirportId, departureDate));
    }

    @GetMapping("/{id}")
    public ApiResponse<FlightDetailResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(flightService.getPublicById(id));
    }

    /**
     * Sơ đồ ghế công khai của chuyến bay đang mở bán — dùng cho khách hàng ở bước chọn ghế
     * mà không cần quyền quản trị {@code /api/admin/**}.
     */
    @GetMapping("/{id}/seat-map")
    public ApiResponse<FlightSeatMapResponse> getPublicSeatMap(@PathVariable Long id) {
        flightService.getPublicById(id);
        return ApiResponse.success(seatInventoryService.getSeatMap(id));
    }

    private Long resolveAirportId(Long id, String code, String label) {
        if (id != null) {
            return id;
        }
        if (code != null && !code.isBlank()) {
            Airport airport = airportRepository.findByAirportCodeAndIsDeletedFalse(code.trim().toUpperCase())
                    .orElseThrow(() -> new ResourceNotFoundException(label + " không tồn tại: " + code));
            return airport.getId();
        }
        throw new AppException(ErrorCode.VALIDATION_ERROR, label + " không được để trống");
    }
}
