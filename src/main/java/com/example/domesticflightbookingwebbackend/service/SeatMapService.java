package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.request.masterdata.SeatMapRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.SeatMapResponse;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.SeatTypeResponse;
import com.example.domesticflightbookingwebbackend.entity.Aircraft;
import com.example.domesticflightbookingwebbackend.entity.AircraftSeat;
import com.example.domesticflightbookingwebbackend.entity.SeatType;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.mapper.SeatTypeMapper;
import com.example.domesticflightbookingwebbackend.repository.AircraftRepository;
import com.example.domesticflightbookingwebbackend.repository.AircraftSeatRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightSeatRepository;
import com.example.domesticflightbookingwebbackend.repository.SeatTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sơ đồ ghế máy bay (mục 2.6 API v0.2, DB v0.4 mục 5.7).
 * Thao tác theo CẢ sơ đồ: PUT thay thế toàn bộ trong một transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatMapService {

    private final AircraftRepository aircraftRepository;
    private final AircraftSeatRepository aircraftSeatRepository;
    private final SeatTypeRepository seatTypeRepository;
    private final FlightSeatRepository flightSeatRepository;
    private final SeatTypeMapper seatTypeMapper;

    @Transactional(readOnly = true)
    public SeatMapResponse getSeatMap(Long aircraftId) {
        Aircraft aircraft = findAircraft(aircraftId);
        List<AircraftSeat> seats = aircraftSeatRepository.findByAircraftId(aircraftId);
        return buildResponse(aircraft, seats);
    }

    /**
     * Thay thế toàn bộ sơ đồ ghế. Ràng buộc (vi phạm → 1006/1004):
     * 1. Không trùng (seat_row, seat_column).
     * 2. seat_type tồn tại + is_active.
     * 3. Tổng số ghế = aircraft.seat_capacity.
     * 4. Máy bay chưa có flight_seats nào (đã khai thác thì không cho sửa).
     */
    @Transactional
    public SeatMapResponse saveSeatMap(Long aircraftId, SeatMapRequest request) {
        Aircraft aircraft = findAircraft(aircraftId);
        List<SeatMapRequest.SeatItem> items = request.seats();

        validateNoDuplicates(items);
        if (items.size() != aircraft.getSeatCapacity()) {
            throw new BusinessException("Tổng số ghế (" + items.size()
                    + ") phải bằng sức chứa máy bay (" + aircraft.getSeatCapacity() + ")");
        }
        if (flightSeatRepository.existsByFlightAircraftId(aircraftId)) {
            log.warn("Sửa sơ đồ máy bay id={} bị từ chối: đã có chuyến khai thác", aircraftId);
            throw new BusinessException(
                    "Máy bay đã được dùng cho chuyến bay — không thể sửa sơ đồ ghế");
        }

        // Thay thế toàn bộ: xóa ghế cũ rồi ghi ghế mới
        aircraftSeatRepository.deleteAll(aircraftSeatRepository.findByAircraftId(aircraftId));

        List<AircraftSeat> newSeats = items.stream()
                .map(item -> toEntity(aircraft, item))
                .toList();
        aircraftSeatRepository.saveAll(newSeats);

        log.info("Lưu sơ đồ ghế máy bay id={}: {} ghế", aircraftId, newSeats.size());
        return buildResponse(aircraft, newSeats);
    }

    // ================= Helpers =================

    private Aircraft findAircraft(Long aircraftId) {
        return aircraftRepository.findById(aircraftId)
                .filter(a -> !Boolean.TRUE.equals(a.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Máy bay không tồn tại"));
    }

    private void validateNoDuplicates(List<SeatMapRequest.SeatItem> items) {
        Set<String> seen = new HashSet<>();
        for (SeatMapRequest.SeatItem item : items) {
            if (item.seatRow() == null || item.seatColumn() == null || item.seatTypeId() == null) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Mỗi ghế phải có seat_row, seat_column và seat_type_id");
            }
            String key = item.seatRow() + ":" + item.seatColumn().toUpperCase();
            if (!seen.add(key)) {
                throw new BusinessException(
                        "Trùng vị trí ghế: hàng " + item.seatRow() + " cột " + item.seatColumn());
            }
        }
    }

    private AircraftSeat toEntity(Aircraft aircraft, SeatMapRequest.SeatItem item) {
        SeatType seatType = seatTypeRepository.findById(item.seatTypeId())
                .filter(st -> !Boolean.TRUE.equals(st.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Loại ghế không tồn tại: " + item.seatTypeId()));
        if (!Boolean.TRUE.equals(seatType.getIsActive())) {
            throw new BusinessException("Loại ghế đã tắt: " + seatType.getSeatTypeCode());
        }

        AircraftSeat seat = new AircraftSeat();
        seat.setAircraft(aircraft);
        seat.setSeatType(seatType);
        seat.setSeatRow(item.seatRow());
        seat.setSeatColumn(item.seatColumn().trim().toUpperCase());
        return seat;
    }

    private SeatMapResponse buildResponse(Aircraft aircraft, List<AircraftSeat> seats) {
        List<SeatMapResponse.SeatEntry> entries = seats.stream()
                .sorted(Comparator.comparing(AircraftSeat::getSeatRow)
                        .thenComparing(AircraftSeat::getSeatColumn))
                .map(s -> new SeatMapResponse.SeatEntry(
                        s.getId(), s.getSeatRow(), s.getSeatColumn(),
                        seatTypeMapper.toResponse(s.getSeatType())))
                .toList();
        return new SeatMapResponse(aircraft.getId(), aircraft.getSeatCapacity(), entries.size(), entries);
    }
}
