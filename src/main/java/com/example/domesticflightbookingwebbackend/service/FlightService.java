package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.dto.request.flight.FlightRequest;
import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightDetailResponse;
import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightResponse;
import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightSearchResult;
import com.example.domesticflightbookingwebbackend.dto.response.flight.PricedFareResponse;
import com.example.domesticflightbookingwebbackend.entity.Aircraft;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.entity.Airport;
import com.example.domesticflightbookingwebbackend.entity.Flight;
import com.example.domesticflightbookingwebbackend.entity.FlightOperationStatus;
import com.example.domesticflightbookingwebbackend.entity.FlightSeatStatus;
import com.example.domesticflightbookingwebbackend.entity.SalesStatus;
import com.example.domesticflightbookingwebbackend.entity.TripType;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.mapper.FlightMapper;
import com.example.domesticflightbookingwebbackend.repository.AircraftRepository;
import com.example.domesticflightbookingwebbackend.repository.AircraftSeatRepository;
import com.example.domesticflightbookingwebbackend.repository.AirlineRepository;
import com.example.domesticflightbookingwebbackend.repository.AirportRepository;
import com.example.domesticflightbookingwebbackend.repository.BookingSegmentRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightFareRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightSeatRepository;
import com.example.domesticflightbookingwebbackend.repository.PassengerRepository;
import com.example.domesticflightbookingwebbackend.repository.SeatHoldRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Nghiệp vụ Chuyến bay — Quản trị & Tìm kiếm công khai (Phase 2 — B2.1).
 *
 * <p>Quy tắc nghiệp vụ (DB v0.5 mục 3.3 C.1, mục 5.1, API v0.4 mục 3):
 * <ul>
 *   <li>Chuyến bay mới mặc định {@code operation_status = DRAFT}, {@code sales_status = CLOSED}.</li>
 *   <li>{@code departure_airport_id <> arrival_airport_id} và {@code scheduled_arrival > scheduled_departure}.</li>
 *   <li>UNIQUE {@code (flight_number, scheduled_departure)} trên các bản ghi chưa xóa mềm.</li>
 *   <li>Chỉ chọn được máy bay đang ACTIVE và đã có sơ đồ ghế đủ {@code seat_capacity}.</li>
 *   <li>Không cho đổi máy bay ({@code aircraft_id}) hoặc xóa chuyến bay khi đã phát sinh Booking.</li>
 *   <li>Mở bán ({@code activate}): chuyến bay chưa hủy/cất cánh, bắt buộc đã có ít nhất 1 dòng giá
 *       ({@code flight_fares}); tự động sinh tồn ghế ({@code flight_seats}) nếu chưa sinh, chuyển
 *       {@code sales_status = OPEN} và nếu đang {@code DRAFT} thì chuyển sang {@code SCHEDULED}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightService {

    private static final String[] SORT_WHITELIST = {
            "flight_number", "scheduled_departure", "scheduled_arrival", "created_at"
    };

    private final FlightRepository flightRepository;
    private final AirlineRepository airlineRepository;
    private final AircraftRepository aircraftRepository;
    private final AirportRepository airportRepository;
    private final AircraftSeatRepository aircraftSeatRepository;
    private final FlightFareRepository flightFareRepository;
    private final FlightSeatRepository flightSeatRepository;
    private final BookingSegmentRepository bookingSegmentRepository;
    private final FlightFareService flightFareService;
    private final SeatInventoryService seatInventoryService;
    private final FlightMapper flightMapper;
    private NotificationService notificationService;
    private PassengerRepository passengerRepository;
    private SeatHoldRepository seatHoldRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setPassengerRepository(PassengerRepository passengerRepository) {
        this.passengerRepository = passengerRepository;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSeatHoldRepository(SeatHoldRepository seatHoldRepository) {
        this.seatHoldRepository = seatHoldRepository;
    }

    /**
     * Tìm kiếm chuyến bay công khai cho khách ({@code GET /api/public/flights/search}).
     * Chỉ trả các chuyến đang {@code OPEN}, chưa hủy, chưa xóa mềm, có ít nhất 1 hạng vé đang hiệu lực
     * và còn đủ số ghế trống cho số hành khách cần ghế ({@code passengers}).
     */
    @Transactional(readOnly = true)
    public List<FlightSearchResult> search(
            Long departureAirportId,
            Long arrivalAirportId,
            LocalDate departureDate,
            int passengers,
            TripType tripType) {
        if (departureAirportId == null || arrivalAirportId == null || departureDate == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Phải cung cấp sân bay đi, sân bay đến và ngày khởi hành");
        }
        if (departureAirportId.equals(arrivalAirportId)) {
            throw new BusinessException("Sân bay đi và sân bay đến không được trùng nhau");
        }
        if (passengers < 1 || passengers > 9) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Số hành khách phải từ 1 đến 9");
        }
        if (tripType == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Loại hành trình (trip_type) là bắt buộc");
        }

        LocalDateTime startOfDay = departureDate.atStartOfDay();
        LocalDateTime endOfDay = departureDate.plusDays(1).atStartOfDay();

        List<Flight> flights = flightRepository.findOpenFlightsForSearch(
                departureAirportId, arrivalAirportId, startOfDay, endOfDay);
        if (flights.isEmpty()) {
            return List.of();
        }

        List<Long> flightIds = flights.stream().map(Flight::getId).toList();
        Map<Long, List<PricedFareResponse>> faresByFlight = flightFareService.getPricedFaresByFlightIds(flightIds);

        List<FlightSearchResult> results = new ArrayList<>();
        for (Flight flight : flights) {
            List<PricedFareResponse> allFares = faresByFlight.getOrDefault(flight.getId(), List.of());
            List<PricedFareResponse> availableFares = allFares.stream()
                    .filter(f -> f.seatsAvailable() >= passengers)
                    .toList();
            if (!availableFares.isEmpty()) {
                results.add(flightMapper.toSearchResult(flight, availableFares));
            }
        }
        return results;
    }

    /** Danh sách chuyến bay công khai cho khách (chỉ chuyến OPEN và chưa hủy). */
    @Transactional(readOnly = true)
    public PageData<FlightResponse> listPublic(
            ListQuery query,
            Long airlineId,
            Long departureAirportId,
            Long arrivalAirportId,
            LocalDate departureDate) {
        Pageable pageable = PageMapper.toPageable(query, "scheduledDeparture", SORT_WHITELIST);
        Specification<Flight> spec = buildSpec(
                query, airlineId, departureAirportId, arrivalAirportId, departureDate,
                null, SalesStatus.OPEN.name(), true);
        Page<Flight> page = flightRepository.findAll(spec, pageable);
        return PageMapper.toPageData(page, flightMapper::toResponse);
    }

    /** Chi tiết chuyến bay công khai cho khách (chỉ chuyến OPEN và chưa hủy — không lộ danh sách hành khách). */
    @Transactional(readOnly = true)
    public FlightDetailResponse getPublicById(Long id) {
        Flight flight = findOrThrow(id);
        if (flight.getSalesStatus() != SalesStatus.OPEN
                || flight.getOperationStatus() == FlightOperationStatus.CANCELLED) {
            throw new ResourceNotFoundException("Chuyến bay không tồn tại");
        }
        return buildDetailResponse(flight, false);
    }

    /** Danh sách chuyến bay cho màn quản trị (ADMIN/STAFF). */
    @Transactional(readOnly = true)
    public PageData<FlightResponse> list(
            ListQuery query,
            Long airlineId,
            Long departureAirportId,
            Long arrivalAirportId,
            LocalDate departureDate,
            String operationStatus,
            String salesStatus) {
        Pageable pageable = PageMapper.toPageable(query, "scheduledDeparture", SORT_WHITELIST);
        Specification<Flight> spec = buildSpec(
                query, airlineId, departureAirportId, arrivalAirportId, departureDate,
                operationStatus, salesStatus, false);
        Page<Flight> page = flightRepository.findAll(spec, pageable);
        return PageMapper.toPageData(page, flightMapper::toResponse);
    }

    /** Chi tiết chuyến bay cho màn quản trị (ADMIN/STAFF — kèm danh sách hành khách). */
    @Transactional(readOnly = true)
    public FlightDetailResponse getById(Long id) {
        Flight flight = findOrThrow(id);
        return buildDetailResponse(flight, true);
    }

    @Transactional
    public FlightResponse create(FlightRequest request) {
        validateRouteAndSchedule(request);
        String flightNumber = request.flightNumber().trim().toUpperCase();
        if (flightRepository.existsByFlightNumberIgnoreCaseAndScheduledDepartureAndIsDeletedFalse(
                flightNumber, request.scheduledDeparture())) {
            throw new DuplicateResourceException("Chuyến bay " + flightNumber
                    + " khởi hành lúc " + request.scheduledDeparture() + " đã tồn tại");
        }

        Airline airline = resolveActiveAirline(request.airlineId());
        Aircraft aircraft = resolveReadyAircraft(request.aircraftId());
        Airport depAirport = resolveActiveAirport(request.departureAirportId(), "Sân bay đi");
        Airport arrAirport = resolveActiveAirport(request.arrivalAirportId(), "Sân bay đến");
        Flight parentFlight = resolveParentFlight(request.parentFlightId(), null);

        Flight flight = new Flight();
        flight.setFlightNumber(flightNumber);
        flight.setAirline(airline);
        flight.setAircraft(aircraft);
        flight.setDepartureAirport(depAirport);
        flight.setArrivalAirport(arrAirport);
        flight.setScheduledDeparture(request.scheduledDeparture());
        flight.setScheduledArrival(request.scheduledArrival());
        flight.setParentFlight(parentFlight);
        flight.setOperationStatus(request.operationStatus() != null && !request.operationStatus().isBlank()
                ? FlightOperationStatus.valueOf(request.operationStatus().trim().toUpperCase())
                : FlightOperationStatus.DRAFT);
        flight.setSalesStatus(SalesStatus.CLOSED);

        Flight saved = flightRepository.save(flight);
        log.info("Tạo chuyến bay id={} ({}) thành công", saved.getId(), saved.getFlightNumber());
        return flightMapper.toResponse(saved);
    }

    @Transactional
    public FlightResponse update(Long id, FlightRequest request) {
        Flight flight = findOrThrow(id);
        validateRouteAndSchedule(request);
        String flightNumber = request.flightNumber().trim().toUpperCase();
        if (flightRepository.existsByFlightNumberIgnoreCaseAndScheduledDepartureAndIsDeletedFalseAndIdNot(
                flightNumber, request.scheduledDeparture(), id)) {
            throw new DuplicateResourceException("Chuyến bay " + flightNumber
                    + " khởi hành lúc " + request.scheduledDeparture() + " đã tồn tại");
        }

        boolean aircraftChanged = !flight.getAircraft().getId().equals(request.aircraftId());
        if (aircraftChanged) {
            validateNoBooking(flight, "Không thể đổi máy bay khi chuyến bay đã có đặt chỗ");
            if (flightSeatRepository.existsByFlightId(id)) {
                throw new BusinessException("Không thể đổi máy bay khi chuyến bay đã sinh tồn ghế");
            }
            Aircraft newAircraft = resolveReadyAircraft(request.aircraftId());
            flight.setAircraft(newAircraft);
        }

        Airline airline = resolveActiveAirline(request.airlineId());
        Airport depAirport = resolveActiveAirport(request.departureAirportId(), "Sân bay đi");
        Airport arrAirport = resolveActiveAirport(request.arrivalAirportId(), "Sân bay đến");
        Flight parentFlight = resolveParentFlight(request.parentFlightId(), id);

        boolean scheduleChanged = !flight.getScheduledDeparture().equals(request.scheduledDeparture())
                || !flight.getScheduledArrival().equals(request.scheduledArrival());
        FlightOperationStatus oldOpStatus = flight.getOperationStatus();

        flight.setFlightNumber(flightNumber);
        flight.setAirline(airline);
        flight.setDepartureAirport(depAirport);
        flight.setArrivalAirport(arrAirport);
        flight.setScheduledDeparture(request.scheduledDeparture());
        flight.setScheduledArrival(request.scheduledArrival());
        flight.setParentFlight(parentFlight);

        if (request.operationStatus() != null && !request.operationStatus().isBlank()) {
            FlightOperationStatus newOpStatus =
                    FlightOperationStatus.valueOf(request.operationStatus().trim().toUpperCase());
            flight.setOperationStatus(newOpStatus);
            if (newOpStatus == FlightOperationStatus.CANCELLED) {
                flight.setSalesStatus(SalesStatus.CLOSED);
            }
        }
        flight.setUpdatedAt(LocalDateTime.now());

        Flight saved = flightRepository.save(flight);
        emitFlightChangeNotifications(saved, scheduleChanged, oldOpStatus, saved.getOperationStatus());
        log.info("Cập nhật chuyến bay id={} ({}) thành công", id, saved.getFlightNumber());
        return flightMapper.toResponse(saved);
    }

    /**
     * Mở bán chuyến bay ({@code POST /api/admin/flights/{id}/activate}).
     * Kiểm tra chuyến bay đã có bảng giá ({@code flight_fares}) và tự động sinh tồn ghế
     * ({@code flight_seats}) nếu chưa có.
     */
    @Transactional
    public FlightResponse activate(Long id) {
        Flight flight = findOrThrow(id);
        if (flight.getOperationStatus() == FlightOperationStatus.CANCELLED
                || flight.getOperationStatus() == FlightOperationStatus.DEPARTED
                || flight.getOperationStatus() == FlightOperationStatus.ARRIVED) {
            throw new BusinessException("Không thể mở bán chuyến bay ở trạng thái " + flight.getOperationStatus());
        }
        if (!flightFareRepository.existsByFlightId(id)) {
            throw new BusinessException("Chuyến bay chưa được cấu hình giá vé — không thể mở bán");
        }
        if (!flightSeatRepository.existsByFlightId(id)) {
            seatInventoryService.generateInventory(id);
        }
        if (flight.getOperationStatus() == FlightOperationStatus.DRAFT) {
            flight.setOperationStatus(FlightOperationStatus.SCHEDULED);
        }
        flight.setSalesStatus(SalesStatus.OPEN);
        flight.setUpdatedAt(LocalDateTime.now());

        Flight saved = flightRepository.save(flight);
        log.info("Đã mở bán chuyến bay id={} ({})", id, saved.getFlightNumber());
        return flightMapper.toResponse(saved);
    }

    /** Đóng bán chuyến bay ({@code POST /api/admin/flights/{id}/deactivate}). */
    @Transactional
    public FlightResponse deactivate(Long id) {
        Flight flight = findOrThrow(id);
        flight.setSalesStatus(SalesStatus.CLOSED);
        flight.setUpdatedAt(LocalDateTime.now());

        Flight saved = flightRepository.save(flight);
        log.info("Đã đóng bán chuyến bay id={} ({})", id, saved.getFlightNumber());
        return flightMapper.toResponse(saved);
    }

    /** Xóa mềm chuyến bay. Từ chối nếu chuyến đã phát sinh đặt chỗ. */
    @Transactional
    public void delete(Long id) {
        Flight flight = findOrThrow(id);
        validateNoBooking(flight, "Chuyến bay đã phát sinh đặt chỗ — không thể xóa");
        flight.setIsDeleted(true);
        flight.setDeletedAt(LocalDateTime.now());
        flight.setSalesStatus(SalesStatus.CLOSED);
        flight.setUpdatedAt(LocalDateTime.now());
        flightRepository.save(flight);
        log.info("Xóa mềm chuyến bay id={} ({})", id, flight.getFlightNumber());
    }

    // ================= Helpers =================

    private Flight findOrThrow(Long id) {
        return flightRepository.findWithDetailsById(id)
                .filter(f -> !Boolean.TRUE.equals(f.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Chuyến bay không tồn tại"));
    }

    private FlightDetailResponse buildDetailResponse(Flight flight, boolean includePassengers) {
        long totalSeats = flightSeatRepository.countByFlightId(flight.getId());
        long availableSeats = flightSeatRepository.countByFlightIdAndStatus(
                flight.getId(), FlightSeatStatus.AVAILABLE);
        List<PricedFareResponse> fares = flightFareService.getPricedFares(flight.getId());
        List<FlightDetailResponse.FlightPassengerItem> passengers =
                includePassengers ? loadFlightPassengers(flight.getId()) : List.of();
        return flightMapper.toDetailResponse(flight, totalSeats, availableSeats, fares, passengers);
    }

    private List<FlightDetailResponse.FlightPassengerItem> loadFlightPassengers(Long flightId) {
        if (passengerRepository == null) {
            return List.of();
        }
        List<com.example.domesticflightbookingwebbackend.entity.BookingSegment> segments =
                bookingSegmentRepository.findByFlightId(flightId);
        if (segments.isEmpty()) {
            return List.of();
        }

        List<Long> bookingIds = segments.stream()
                .map(s -> s.getBooking().getId())
                .distinct()
                .toList();

        Map<Long, List<com.example.domesticflightbookingwebbackend.entity.Passenger>> passengersByBooking =
                new java.util.HashMap<>();
        for (var p : passengerRepository.findByBookingIdInOrderByIdAsc(bookingIds)) {
            passengersByBooking.computeIfAbsent(p.getBooking().getId(), k -> new ArrayList<>()).add(p);
        }

        Map<Long, String> seatByPassenger = new java.util.HashMap<>();
        if (seatHoldRepository != null) {
            List<com.example.domesticflightbookingwebbackend.entity.SeatHold> holds =
                    seatHoldRepository.findByBookingIdInAndStatusIn(
                            bookingIds,
                            List.of(com.example.domesticflightbookingwebbackend.entity.SeatHoldStatus.HOLD,
                                    com.example.domesticflightbookingwebbackend.entity.SeatHoldStatus.CONFIRMED));
            for (var h : holds) {
                if (h.getFlightSeat() != null && h.getFlightSeat().getFlight().getId().equals(flightId)) {
                    seatByPassenger.put(h.getPassenger().getId(), h.getFlightSeat().getSeatCode());
                }
            }
        }

        List<FlightDetailResponse.FlightPassengerItem> result = new ArrayList<>();
        for (var seg : segments) {
            var b = seg.getBooking();
            String orderCode = "DH-" + b.getId();
            String pnrCode = b.getStatus() == com.example.domesticflightbookingwebbackend.entity.BookingStatus.CONFIRMED
                    ? b.getBookingCode()
                    : null;
            String fareName = seg.getFareClass() != null ? seg.getFareClass().getFareClassName() : "";
            for (var p : passengersByBooking.getOrDefault(b.getId(), List.of())) {
                result.add(new FlightDetailResponse.FlightPassengerItem(
                        orderCode,
                        pnrCode,
                        p.getFullName(),
                        p.getPassengerType().name(),
                        seatByPassenger.get(p.getId()),
                        fareName,
                        b.getStatus().name(),
                        b.getStatus() == com.example.domesticflightbookingwebbackend.entity.BookingStatus.CONFIRMED
                ));
            }
        }
        return result;
    }

    private void validateRouteAndSchedule(FlightRequest request) {
        if (request.departureAirportId().equals(request.arrivalAirportId())) {
            throw new BusinessException("Sân bay đi và sân bay đến không được trùng nhau");
        }
        if (!request.scheduledArrival().isAfter(request.scheduledDeparture())) {
            throw new BusinessException("Giờ đến dự kiến phải sau giờ khởi hành dự kiến");
        }
    }

    private Airline resolveActiveAirline(Long airlineId) {
        Airline airline = airlineRepository.findById(airlineId)
                .filter(a -> !Boolean.TRUE.equals(a.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Hãng hàng không không tồn tại"));
        if (!Boolean.TRUE.equals(airline.getIsActive())) {
            throw new BusinessException("Hãng hàng không đang ngừng hoạt động: " + airline.getAirlineCode());
        }
        return airline;
    }

    private Aircraft resolveReadyAircraft(Long aircraftId) {
        Aircraft aircraft = aircraftRepository.findById(aircraftId)
                .filter(a -> !Boolean.TRUE.equals(a.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Máy bay không tồn tại"));
        if (!Boolean.TRUE.equals(aircraft.getIsActive())) {
            throw new BusinessException("Máy bay đang ngừng hoạt động: " + aircraft.getAircraftCode());
        }
        validateAircraftReady(aircraft);
        return aircraft;
    }

    /** Máy bay phải đã cấu hình sơ đồ ghế khớp với {@code seat_capacity} (API v0.4 mục 3 / DB v0.5 mục 5.7). */
    private void validateAircraftReady(Aircraft aircraft) {
        long configuredSeats = aircraftSeatRepository.countByAircraftId(aircraft.getId());
        if (configuredSeats != aircraft.getSeatCapacity()) {
            throw new BusinessException("Máy bay " + aircraft.getAircraftCode()
                    + " chưa có sơ đồ ghế đủ sức chứa (" + configuredSeats
                    + "/" + aircraft.getSeatCapacity() + " ghế)");
        }
    }

    private Airport resolveActiveAirport(Long airportId, String label) {
        Airport airport = airportRepository.findById(airportId)
                .filter(a -> !Boolean.TRUE.equals(a.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException(label + " không tồn tại"));
        if (!Boolean.TRUE.equals(airport.getIsActive())) {
            throw new BusinessException(label + " đang ngừng hoạt động: " + airport.getAirportCode());
        }
        return airport;
    }

    private Flight resolveParentFlight(Long parentFlightId, Long currentFlightId) {
        if (parentFlightId == null) {
            return null;
        }
        if (currentFlightId != null && currentFlightId.equals(parentFlightId)) {
            throw new BusinessException("Chuyến bay bổ sung không thể trỏ về chính nó");
        }
        return flightRepository.findById(parentFlightId)
                .filter(f -> !Boolean.TRUE.equals(f.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Chuyến bay gốc không tồn tại"));
    }

    private void validateNoBooking(Flight flight, String message) {
        if (bookingSegmentRepository.existsByFlightId(flight.getId())) {
            throw new BusinessException(message);
        }
    }

    private void emitFlightChangeNotifications(
            Flight flight,
            boolean scheduleChanged,
            FlightOperationStatus oldStatus,
            FlightOperationStatus newStatus) {
        if (notificationService == null) {
            return;
        }
        boolean becameDelayed = oldStatus != FlightOperationStatus.DELAYED
                && newStatus == FlightOperationStatus.DELAYED;
        boolean becameCancelled = oldStatus != FlightOperationStatus.CANCELLED
                && newStatus == FlightOperationStatus.CANCELLED;

        if (!scheduleChanged && !becameDelayed && !becameCancelled) {
            return;
        }

        List<com.example.domesticflightbookingwebbackend.entity.BookingSegment> segments =
                bookingSegmentRepository.findByFlightId(flight.getId());

        if (becameCancelled) {
            String title = "Chuyến bay " + flight.getFlightNumber() + " đã huỷ";
            String body = "Chuyến bay " + flight.getFlightNumber() + " khởi hành lúc "
                    + flight.getScheduledDeparture() + " đã bị huỷ. Vui lòng kiểm tra đặt chỗ.";
            String dedupe = "flight:" + flight.getId() + ":cancelled";
            notificationService.notifyStaffAndAdmins(
                    com.example.domesticflightbookingwebbackend.entity.NotificationType.FLIGHT_CANCELLED,
                    title, body, "/admin/flights", dedupe);
            for (var seg : segments) {
                if (seg.getBooking() != null && seg.getBooking().getUser() != null) {
                    notificationService.notify(
                            seg.getBooking().getUser().getId(),
                            com.example.domesticflightbookingwebbackend.entity.NotificationType.FLIGHT_CANCELLED,
                            title, body, "/account/bookings/" + seg.getBooking().getId(), dedupe);
                }
            }
        } else if (becameDelayed) {
            String title = "Chuyến bay " + flight.getFlightNumber() + " bị tạm hoãn (DELAYED)";
            String body = "Chuyến bay " + flight.getFlightNumber() + " dự kiến khởi hành lúc "
                    + flight.getScheduledDeparture() + ".";
            String dedupe = "flight:" + flight.getId() + ":delayed:" + flight.getScheduledDeparture();
            notificationService.notifyStaffAndAdmins(
                    com.example.domesticflightbookingwebbackend.entity.NotificationType.FLIGHT_DELAYED,
                    title, body, "/admin/flights", dedupe);
            for (var seg : segments) {
                if (seg.getBooking() != null && seg.getBooking().getUser() != null) {
                    notificationService.notify(
                            seg.getBooking().getUser().getId(),
                            com.example.domesticflightbookingwebbackend.entity.NotificationType.FLIGHT_DELAYED,
                            title, body, "/account/bookings/" + seg.getBooking().getId(), dedupe);
                }
            }
        } else if (scheduleChanged && !segments.isEmpty()) {
            String title = "Chuyến bay " + flight.getFlightNumber() + " đổi giờ khởi hành";
            String body = "Giờ khởi hành mới của chuyến " + flight.getFlightNumber() + " là "
                    + flight.getScheduledDeparture() + ".";
            String dedupe = "flight:" + flight.getId() + ":schedule:" + flight.getScheduledDeparture();
            notificationService.notifyStaffAndAdmins(
                    com.example.domesticflightbookingwebbackend.entity.NotificationType.FLIGHT_SCHEDULE_CHANGED,
                    title, body, "/admin/flights", dedupe);
            for (var seg : segments) {
                if (seg.getBooking() != null && seg.getBooking().getUser() != null) {
                    notificationService.notify(
                            seg.getBooking().getUser().getId(),
                            com.example.domesticflightbookingwebbackend.entity.NotificationType.FLIGHT_SCHEDULE_CHANGED,
                            title, body, "/account/bookings/" + seg.getBooking().getId(), dedupe);
                }
            }
        }
    }

    private Specification<Flight> buildSpec(
            ListQuery query,
            Long airlineId,
            Long departureAirportId,
            Long arrivalAirportId,
            LocalDate departureDate,
            String operationStatus,
            String salesStatus,
            boolean publicOnly) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (airlineId != null) {
                predicates.add(cb.equal(root.get("airline").get("id"), airlineId));
            }
            if (departureAirportId != null) {
                predicates.add(cb.equal(root.get("departureAirport").get("id"), departureAirportId));
            }
            if (arrivalAirportId != null) {
                predicates.add(cb.equal(root.get("arrivalAirport").get("id"), arrivalAirportId));
            }
            if (departureDate != null) {
                LocalDateTime start = departureDate.atStartOfDay();
                LocalDateTime end = departureDate.plusDays(1).atStartOfDay();
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledDeparture"), start));
                predicates.add(cb.lessThan(root.get("scheduledDeparture"), end));
            }
            if (operationStatus != null && !operationStatus.isBlank()) {
                predicates.add(cb.equal(
                        root.get("operationStatus"),
                        FlightOperationStatus.valueOf(operationStatus.trim().toUpperCase())));
            }
            if (salesStatus != null && !salesStatus.isBlank()) {
                predicates.add(cb.equal(
                        root.get("salesStatus"),
                        SalesStatus.valueOf(salesStatus.trim().toUpperCase())));
            }
            if (publicOnly) {
                predicates.add(cb.notEqual(root.get("operationStatus"), FlightOperationStatus.CANCELLED));
            }

            String keyword = query.getKeywordOrNull();
            if (keyword != null) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("flightNumber")), like),
                        cb.like(cb.lower(root.get("airline").get("airlineCode")), like),
                        cb.like(cb.lower(root.get("airline").get("airlineName")), like),
                        cb.like(cb.lower(root.get("departureAirport").get("airportCode")), like),
                        cb.like(cb.lower(root.get("arrivalAirport").get("airportCode")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
