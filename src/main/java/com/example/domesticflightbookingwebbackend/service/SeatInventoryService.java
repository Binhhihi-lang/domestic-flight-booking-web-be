package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.request.flight.SeatInventoryRequest;
import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightSeatMapResponse;
import com.example.domesticflightbookingwebbackend.entity.Aircraft;
import com.example.domesticflightbookingwebbackend.entity.AircraftSeat;
import com.example.domesticflightbookingwebbackend.entity.Flight;
import com.example.domesticflightbookingwebbackend.entity.FlightSeat;
import com.example.domesticflightbookingwebbackend.entity.FlightSeatStatus;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.mapper.SeatTypeMapper;
import com.example.domesticflightbookingwebbackend.repository.AircraftSeatRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quản lý tồn ghế theo chuyến bay (Phase 2 — B2.3).
 *
 * <p>Quy tắc nghiệp vụ (DB v0.5 mục 3.3 C.5 & mục 5.2):
 * <ul>
 *   <li>Sinh tồn ghế chuyến ({@code flight_seats}) từ sơ đồ ghế của máy bay ({@code aircraft_seats}),
 *       mặc định {@code status = AVAILABLE}, {@code seat_surcharge = seat_type.surcharge}.</li>
 *   <li>Chỉ cho khóa ({@code BLOCKED}) ghế đang {@code AVAILABLE}, và mở khóa ghế đang {@code BLOCKED}
 *       về {@code AVAILABLE}; không cho khóa ghế đang {@code HOLD} hoặc {@code BOOKED}.</li>
 *   <li>Hàm {@link #hold(Long)} thực hiện UPDATE nguyên tử có điều kiện {@code status = 'AVAILABLE'}
 *       kèm tăng {@code version} để chống đặt ghế trùng.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatInventoryService {

    private final FlightSeatRepository flightSeatRepository;
    private final FlightRepository flightRepository;
    private final AircraftSeatRepository aircraftSeatRepository;
    private final SeatTypeMapper seatTypeMapper;

    /**
     * Sinh tồn ghế cho chuyến bay từ {@code aircraft_seats} nếu chưa có ghế nào.
     * Nếu chuyến đã có tồn ghế thì giữ nguyên và trả về số lượng hiện tại (idempotent).
     *
     * @return tổng số ghế đã sinh cho chuyến bay
     */
    @Transactional
    public int generateInventory(Long flightId) {
        Flight flight = findFlightOrThrow(flightId);
        List<FlightSeat> existing = flightSeatRepository.findByFlightId(flightId);
        if (!existing.isEmpty()) {
            return existing.size();
        }

        Aircraft aircraft = flight.getAircraft();
        List<AircraftSeat> aircraftSeats = aircraftSeatRepository.findByAircraftId(aircraft.getId());
        if (aircraftSeats.isEmpty() || aircraftSeats.size() != aircraft.getSeatCapacity()) {
            throw new BusinessException("Máy bay " + aircraft.getAircraftCode()
                    + " chưa được cấu hình đủ sơ đồ ghế (" + aircraftSeats.size()
                    + "/" + aircraft.getSeatCapacity() + ")");
        }

        List<FlightSeat> newSeats = aircraftSeats.stream()
                .map(as -> {
                    FlightSeat fs = new FlightSeat();
                    fs.setFlight(flight);
                    fs.setAircraftSeat(as);
                    fs.setSeatCode(as.getSeatRow() + as.getSeatColumn());
                    fs.setStatus(FlightSeatStatus.AVAILABLE);
                    BigDecimal defaultSurcharge = as.getSeatType().getSurcharge() != null
                            ? as.getSeatType().getSurcharge()
                            : BigDecimal.ZERO;
                    fs.setSeatSurcharge(defaultSurcharge.setScale(2, RoundingMode.HALF_UP));
                    return fs;
                })
                .toList();

        flightSeatRepository.saveAll(newSeats);
        log.info("Đã sinh {} ghế tồn kho cho chuyến bay id={} ({})",
                newSeats.size(), flightId, flight.getFlightNumber());
        return newSeats.size();
    }

    /** Xem sơ đồ tồn ghế của một chuyến bay (tự sinh tồn ghế từ sơ đồ máy bay nếu chuyến chưa có). */
    @Transactional
    public FlightSeatMapResponse getSeatMap(Long flightId) {
        Flight flight = findFlightOrThrow(flightId);
        List<FlightSeat> seats = flightSeatRepository.findByFlightId(flightId);
        if (seats.isEmpty()) {
            generateInventory(flightId);
            seats = flightSeatRepository.findByFlightId(flightId);
        }
        return buildSeatMapResponse(flight, seats);
    }

    /**
     * Khởi tạo (nếu chưa có) và/hoặc cập nhật trạng thái (AVAILABLE/BLOCKED) + phụ thu ghế
     * cho sơ đồ tồn ghế của chuyến bay.
     */
    @Transactional
    public FlightSeatMapResponse saveInventory(Long flightId, SeatInventoryRequest request) {
        Flight flight = findFlightOrThrow(flightId);
        List<FlightSeat> seats = flightSeatRepository.findByFlightId(flightId);
        if (seats.isEmpty()) {
            generateInventory(flightId);
            seats = flightSeatRepository.findByFlightId(flightId);
        }

        if (request != null && request.seats() != null && !request.seats().isEmpty()) {
            Map<Long, FlightSeat> byId = new HashMap<>();
            Map<Long, FlightSeat> byAircraftSeatId = new HashMap<>();
            Map<String, FlightSeat> bySeatCode = new HashMap<>();
            for (FlightSeat fs : seats) {
                if (fs.getId() != null) {
                    byId.put(fs.getId(), fs);
                }
                byAircraftSeatId.put(fs.getAircraftSeat().getId(), fs);
                bySeatCode.put(fs.getSeatCode().toUpperCase(), fs);
            }

            for (SeatInventoryRequest.SeatOverrideItem item : request.seats()) {
                FlightSeat target = resolveTargetSeat(item, byId, byAircraftSeatId, bySeatCode);
                if (item.status() != null && !item.status().isBlank()) {
                    FlightSeatStatus desired = FlightSeatStatus.valueOf(item.status().trim().toUpperCase());
                    applyAdminStatusChange(target, desired);
                }
                if (item.seatSurcharge() != null) {
                    target.setSeatSurcharge(item.seatSurcharge().setScale(2, RoundingMode.HALF_UP));
                }
            }
            flightSeatRepository.saveAll(seats);
        }

        log.info("Cập nhật tồn ghế chuyến bay id={} thành công", flightId);
        return buildSeatMapResponse(flight, seats);
    }

    /** Khóa một ghế vật lý trên chuyến bay (AVAILABLE → BLOCKED). */
    @Transactional
    public void block(Long flightSeatId) {
        FlightSeat seat = findSeatOrThrow(flightSeatId);
        if (seat.getStatus() == FlightSeatStatus.BLOCKED) {
            return;
        }
        if (seat.getStatus() != FlightSeatStatus.AVAILABLE) {
            throw new BusinessException("Không thể khóa ghế " + seat.getSeatCode()
                    + " đang ở trạng thái " + seat.getStatus());
        }
        seat.setStatus(FlightSeatStatus.BLOCKED);
        flightSeatRepository.save(seat);
        log.info("Đã khóa ghế id={} ({})", flightSeatId, seat.getSeatCode());
    }

    /** Mở khóa một ghế đang bị khóa (BLOCKED → AVAILABLE). */
    @Transactional
    public void unblock(Long flightSeatId) {
        FlightSeat seat = findSeatOrThrow(flightSeatId);
        if (seat.getStatus() == FlightSeatStatus.AVAILABLE) {
            return;
        }
        if (seat.getStatus() != FlightSeatStatus.BLOCKED) {
            throw new BusinessException("Chỉ có thể mở khóa ghế đang ở trạng thái BLOCKED (hiện tại: "
                    + seat.getStatus() + ")");
        }
        seat.setStatus(FlightSeatStatus.AVAILABLE);
        flightSeatRepository.save(seat);
        log.info("Đã mở khóa ghế id={} ({})", flightSeatId, seat.getSeatCode());
    }

    /**
     * Giữ ghế nguyên tử bằng câu lệnh UPDATE có điều kiện {@code status = 'AVAILABLE'} (DB v0.5 mục 5.2).
     *
     * @return {@code true} nếu giữ ghế thành công, {@code false} nếu ghế đã bị người khác giữ/đặt/khóa
     */
    @Transactional
    public boolean hold(Long flightSeatId) {
        int updated = flightSeatRepository.holdSeatAtomically(flightSeatId);
        return updated == 1;
    }

    /** Giải phóng ghế đang HOLD về lại AVAILABLE. */
    @Transactional
    public void release(Long flightSeatId) {
        flightSeatRepository.releaseHeldSeat(flightSeatId);
    }

    // ================= Helpers =================

    private Flight findFlightOrThrow(Long flightId) {
        return flightRepository.findWithDetailsById(flightId)
                .filter(f -> !Boolean.TRUE.equals(f.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Chuyến bay không tồn tại"));
    }

    private FlightSeat findSeatOrThrow(Long flightSeatId) {
        return flightSeatRepository.findById(flightSeatId)
                .orElseThrow(() -> new ResourceNotFoundException("Ghế chuyến bay không tồn tại"));
    }

    private FlightSeat resolveTargetSeat(
            SeatInventoryRequest.SeatOverrideItem item,
            Map<Long, FlightSeat> byId,
            Map<Long, FlightSeat> byAircraftSeatId,
            Map<String, FlightSeat> bySeatCode) {
        FlightSeat target = null;
        if (item.flightSeatId() != null) {
            target = byId.get(item.flightSeatId());
        } else if (item.aircraftSeatId() != null) {
            target = byAircraftSeatId.get(item.aircraftSeatId());
        } else if (item.seatCode() != null && !item.seatCode().isBlank()) {
            target = bySeatCode.get(item.seatCode().trim().toUpperCase());
        }
        if (target == null) {
            throw new ResourceNotFoundException("Không tìm thấy ghế trong sơ đồ tồn ghế của chuyến bay");
        }
        return target;
    }

    private void applyAdminStatusChange(FlightSeat seat, FlightSeatStatus desired) {
        if (seat.getStatus() == desired) {
            return;
        }
        if (seat.getStatus() == FlightSeatStatus.HOLD || seat.getStatus() == FlightSeatStatus.BOOKED) {
            throw new BusinessException("Ghế " + seat.getSeatCode()
                    + " đang được khách giữ hoặc đã đặt — không thể đổi trạng thái");
        }
        if (desired != FlightSeatStatus.AVAILABLE && desired != FlightSeatStatus.BLOCKED) {
            throw new BusinessException("Quản trị chỉ được đổi trạng thái ghế sang AVAILABLE hoặc BLOCKED");
        }
        seat.setStatus(desired);
    }

    private FlightSeatMapResponse buildSeatMapResponse(Flight flight, List<FlightSeat> seats) {
        Aircraft aircraft = flight.getAircraft();
        List<FlightSeatMapResponse.FlightSeatEntry> entries = seats.stream()
                .sorted(Comparator.comparing((FlightSeat s) -> s.getAircraftSeat().getSeatRow())
                        .thenComparing(s -> s.getAircraftSeat().getSeatColumn()))
                .map(s -> new FlightSeatMapResponse.FlightSeatEntry(
                        s.getId(),
                        s.getAircraftSeat().getId(),
                        s.getSeatCode(),
                        s.getAircraftSeat().getSeatRow(),
                        s.getAircraftSeat().getSeatColumn(),
                        s.getStatus().name(),
                        s.getSeatSurcharge(),
                        seatTypeMapper.toResponse(s.getAircraftSeat().getSeatType())
                ))
                .toList();

        int available = 0;
        int held = 0;
        int booked = 0;
        int blocked = 0;
        for (FlightSeat s : seats) {
            switch (s.getStatus()) {
                case AVAILABLE -> available++;
                case HOLD -> held++;
                case BOOKED -> booked++;
                case BLOCKED -> blocked++;
            }
        }

        return new FlightSeatMapResponse(
                flight.getId(),
                flight.getFlightNumber(),
                aircraft.getId(),
                aircraft.getAircraftCode(),
                aircraft.getModel(),
                aircraft.getSeatCapacity(),
                aircraft.getAisleAfter(),
                entries.size(),
                available,
                held,
                booked,
                blocked,
                entries
        );
    }
}
