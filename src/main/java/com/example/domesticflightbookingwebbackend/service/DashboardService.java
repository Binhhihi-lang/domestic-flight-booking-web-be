package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.dto.response.booking.BookingSummaryResponse;
import com.example.domesticflightbookingwebbackend.dto.response.dashboard.OperationalDashboardResponse;
import com.example.domesticflightbookingwebbackend.dto.response.dashboard.OverviewDashboardResponse;
import com.example.domesticflightbookingwebbackend.entity.Booking;
import com.example.domesticflightbookingwebbackend.entity.BookingSegment;
import com.example.domesticflightbookingwebbackend.entity.BookingStatus;
import com.example.domesticflightbookingwebbackend.entity.Flight;
import com.example.domesticflightbookingwebbackend.entity.FlightOperationStatus;
import com.example.domesticflightbookingwebbackend.entity.FlightSeatStatus;
import com.example.domesticflightbookingwebbackend.entity.Payment;
import com.example.domesticflightbookingwebbackend.entity.PaymentStatus;
import com.example.domesticflightbookingwebbackend.repository.BookingRepository;
import com.example.domesticflightbookingwebbackend.repository.BookingSegmentRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightSeatRepository;
import com.example.domesticflightbookingwebbackend.repository.PassengerRepository;
import com.example.domesticflightbookingwebbackend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dịch vụ Dashboard Vận hành (STAFF) & Dashboard Tổng quan / Báo cáo (ADMIN) (Phase 3B — B4.3).
 *
 * <p>Quy tắc phân quyền & nghiệp vụ (API v0.4 mục 3 & Nghiệp vụ 09 mục 5.8):
 * <ul>
 *   <li>{@link #operational()} ({@code GET /api/admin/dashboard/operational} — quyền {@code DASHBOARD_READ_OPERATIONAL}):
 *       dành cho Nhân viên vận hành (STAFF), chỉ thống kê chuyến bay hôm nay, tình trạng ghế,
 *       đơn đặt chỗ trong ngày và các việc cần xử lý. <b>TUYỆT ĐỐI KHÔNG trả doanh thu</b>.</li>
 *   <li>{@link #overview(LocalDate, LocalDate)} ({@code GET /api/admin/dashboard} — quyền {@code DASHBOARD_READ}):
 *       dành cho Quản trị viên (ADMIN), thống kê doanh thu hôm nay / tháng này / tổng cộng,
 *       biểu đồ doanh thu 7 ngày gần nhất, top tuyến bay theo doanh thu & hệ số lấp đầy,
 *       tỷ lệ hủy vé và danh sách đơn đặt chỗ mới nhất.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final BookingRepository bookingRepository;
    private final BookingSegmentRepository bookingSegmentRepository;
    private final PassengerRepository passengerRepository;
    private final FlightRepository flightRepository;
    private final FlightSeatRepository flightSeatRepository;
    private final PaymentRepository paymentRepository;
    private final BookingService bookingService;

    /**
     * Dashboard vận hành ca trực cho Nhân viên (STAFF) — KHÔNG chứa bất kỳ trường doanh thu nào.
     */
    @Transactional(readOnly = true)
    public OperationalDashboardResponse operational() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime endOfToday = today.plusDays(1).atStartOfDay();

        List<Flight> todayFlights = flightRepository.findFlightsInWindow(startOfToday, endOfToday);
        Map<Long, Map<FlightSeatStatus, Long>> seatCountsByFlight = loadSeatCounts(todayFlights);

        long scheduled = 0;
        long boarding = 0;
        long delayed = 0;
        long cancelled = 0;
        List<OperationalDashboardResponse.FlightOperationItem> scheduleItems = new ArrayList<>();
        List<OperationalDashboardResponse.ActionRequiredItem> pendingActions = new ArrayList<>();

        for (Flight f : todayFlights) {
            switch (f.getOperationStatus()) {
                case SCHEDULED -> scheduled++;
                case BOARDING -> boarding++;
                case DELAYED -> delayed++;
                case CANCELLED -> cancelled++;
                default -> {
                }
            }

            int capacity = f.getAircraft().getSeatCapacity() == null ? 180 : f.getAircraft().getSeatCapacity();
            Map<FlightSeatStatus, Long> counts = seatCountsByFlight.getOrDefault(f.getId(), Map.of());
            long booked = counts.getOrDefault(FlightSeatStatus.BOOKED, 0L);
            long held = counts.getOrDefault(FlightSeatStatus.HOLD, 0L);
            long blocked = counts.getOrDefault(FlightSeatStatus.BLOCKED, 0L);
            long available = counts.isEmpty()
                    ? capacity
                    : counts.getOrDefault(FlightSeatStatus.AVAILABLE, Math.max(0L, capacity - booked - held - blocked));

            String route = f.getDepartureAirport().getAirportCode() + " → " + f.getArrivalAirport().getAirportCode();
            if (scheduleItems.size() < 25) {
                scheduleItems.add(new OperationalDashboardResponse.FlightOperationItem(
                        f.getId(),
                        f.getFlightNumber(),
                        f.getAirline().getAirlineCode(),
                        f.getAirline().getAirlineName(),
                        f.getAircraft().getAircraftCode(),
                        route,
                        f.getScheduledDeparture(),
                        f.getScheduledArrival(),
                        f.getOperationStatus().name(),
                        f.getSalesStatus().name(),
                        capacity,
                        booked,
                        held,
                        available
                ));
            }

            if (f.getOperationStatus() == FlightOperationStatus.DELAYED && pendingActions.size() < 15) {
                pendingActions.add(new OperationalDashboardResponse.ActionRequiredItem(
                        "FLIGHT_DELAYED",
                        f.getFlightNumber(),
                        "Chuyến bay " + f.getFlightNumber() + " (" + route + ") đang bị hoãn",
                        "Kiểm tra giờ khởi hành mới và hỗ trợ hành khách trên chuyến",
                        f.getScheduledDeparture()));
            } else if (f.getOperationStatus() == FlightOperationStatus.CANCELLED && pendingActions.size() < 15) {
                pendingActions.add(new OperationalDashboardResponse.ActionRequiredItem(
                        "FLIGHT_CANCELLED",
                        f.getFlightNumber(),
                        "Chuyến bay " + f.getFlightNumber() + " (" + route + ") đã hủy",
                        "Rà soát danh sách đặt chỗ bị ảnh hưởng để hỗ trợ đổi/hoàn vé",
                        f.getScheduledDeparture()));
            }
        }

        long bookingsToday = bookingRepository.countByCreatedAtBetween(startOfToday, endOfToday);
        long pendingPaymentToday = bookingRepository.countByStatusAndCreatedAtBetween(
                BookingStatus.PENDING_PAYMENT, startOfToday, endOfToday);
        long confirmedToday = bookingRepository.countByStatusAndCreatedAtBetween(
                BookingStatus.CONFIRMED, startOfToday, endOfToday);

        long passengersToday = passengerRepository.count();

        return new OperationalDashboardResponse(
                today,
                todayFlights.size(),
                scheduled,
                boarding,
                delayed,
                cancelled,
                bookingsToday,
                pendingPaymentToday,
                confirmedToday,
                passengersToday,
                scheduleItems,
                pendingActions
        );
    }

    /**
     * Dashboard tổng quan & Báo cáo doanh thu cho Quản trị viên (ADMIN).
     */
    @Transactional(readOnly = true)
    public OverviewDashboardResponse overview(LocalDate fromDate, LocalDate toDate) {
        return overview(fromDate, toDate, null, null, null);
    }

    /**
     * Dashboard tổng quan & Báo cáo thống kê cho Quản trị viên (ADMIN) có hỗ trợ bộ lọc
     * khoảng ngày ({@code fromDate}/{@code toDate}/{@code days}), hãng bay ({@code airlineId})
     * và chặng bay ({@code route} dạng {@code HAN-SGN} hoặc {@code HAN→SGN}).
     */
    @Transactional(readOnly = true)
    public OverviewDashboardResponse overview(
            LocalDate fromDate,
            LocalDate toDate,
            Long airlineId,
            String route,
            Integer days) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime endOfToday = today.plusDays(1).atStartOfDay();
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();

        LocalDate chartEnd = toDate != null ? toDate : today;
        LocalDate computedStart;
        if (fromDate != null) {
            computedStart = fromDate;
        } else if (days != null && days > 0) {
            computedStart = chartEnd.minusDays(days - 1L);
        } else {
            computedStart = chartEnd.minusDays(6);
        }
        LocalDate chartStart = computedStart.isAfter(chartEnd) ? chartEnd : computedStart;

        String[] routeFilter = parseRoute(route);
        boolean hasDateFilter = fromDate != null || toDate != null || (days != null && days > 0);
        boolean hasSegmentFilter = airlineId != null || routeFilter != null;

        List<Booking> allBookings = bookingRepository.findAll();
        List<Long> allBookingIds = allBookings.stream()
                .map(Booking::getId)
                .filter(id -> id != null)
                .toList();

        Map<Long, List<BookingSegment>> segmentsByBooking = new HashMap<>();
        Map<Long, Long> paxByBooking = new HashMap<>();
        if (!allBookingIds.isEmpty()) {
            List<BookingSegment> allSegments =
                    bookingSegmentRepository.findByBookingIdInOrderBySegmentOrderAsc(allBookingIds);
            if (allSegments != null) {
                for (BookingSegment seg : allSegments) {
                    if (seg.getBooking() != null && seg.getBooking().getId() != null) {
                        segmentsByBooking.computeIfAbsent(seg.getBooking().getId(), k -> new ArrayList<>()).add(seg);
                    }
                }
            }
            List<PassengerRepository.BookingPassengerCount> paxRows =
                    passengerRepository.countByBookingIdIn(allBookingIds);
            if (paxRows != null) {
                for (PassengerRepository.BookingPassengerCount row : paxRows) {
                    if (row.getBookingId() != null && row.getPassengerCount() != null) {
                        paxByBooking.put(row.getBookingId(), row.getPassengerCount());
                    }
                }
            }
        }

        List<Booking> filteredBookings = new ArrayList<>();
        Set<Long> matchedBookingIds = new HashSet<>();
        for (Booking b : allBookings) {
            if (!hasSegmentFilter) {
                filteredBookings.add(b);
                if (b.getId() != null) {
                    matchedBookingIds.add(b.getId());
                }
            } else {
                List<BookingSegment> segs = segmentsByBooking.getOrDefault(b.getId(), List.of());
                boolean matches = segs.stream().anyMatch(seg -> matchesSegment(seg, airlineId, routeFilter));
                if (matches) {
                    filteredBookings.add(b);
                    if (b.getId() != null) {
                        matchedBookingIds.add(b.getId());
                    }
                }
            }
        }

        List<Payment> allPayments = paymentRepository.findAll();
        BigDecimal revenueToday = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal revenueMonth = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal revenueTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Set<Long> paidBookingIds = new HashSet<>();

        Map<LocalDate, BigDecimal> revenueByDateMap = new HashMap<>();
        for (Payment p : allPayments) {
            if (p.getStatus() != PaymentStatus.SUCCESS) {
                continue;
            }
            if (hasSegmentFilter) {
                if (p.getBooking() == null || !matchedBookingIds.contains(p.getBooking().getId())) {
                    continue;
                }
            }
            if (p.getBooking() != null && p.getBooking().getId() != null) {
                paidBookingIds.add(p.getBooking().getId());
            }

            BigDecimal amt = p.getAmount() == null ? BigDecimal.ZERO : p.getAmount();
            LocalDateTime paidTime = p.getPaidAt() != null ? p.getPaidAt() : p.getCreatedAt();
            revenueTotal = revenueTotal.add(amt);

            if (paidTime != null) {
                if (!paidTime.isBefore(startOfToday) && paidTime.isBefore(endOfToday)) {
                    revenueToday = revenueToday.add(amt);
                }
                if (!paidTime.isBefore(startOfMonth) && paidTime.isBefore(endOfToday)) {
                    revenueMonth = revenueMonth.add(amt);
                }
                revenueByDateMap.merge(paidTime.toLocalDate(), amt, BigDecimal::add);
            }
        }

        long bookingsToday = 0;
        long bookingsMonth = 0;
        long cancelledCount = 0;
        Map<LocalDate, Long> bookingsByDateMap = new HashMap<>();

        for (Booking b : filteredBookings) {
            LocalDateTime created = b.getCreatedAt();
            if (created != null) {
                if (!created.isBefore(startOfToday) && created.isBefore(endOfToday)) {
                    bookingsToday++;
                }
                if (!created.isBefore(startOfMonth) && created.isBefore(endOfToday)) {
                    bookingsMonth++;
                }
                bookingsByDateMap.merge(created.toLocalDate(), 1L, Long::sum);
            }
            if (b.getStatus() == BookingStatus.CANCELLED) {
                cancelledCount++;
            }
        }

        long totalBookings = filteredBookings.size();
        double cancelRate = totalBookings == 0
                ? 0.0
                : Math.round((cancelledCount * 1000.0) / totalBookings) / 10.0;

        // Biểu đồ doanh thu theo khoảng ngày
        List<OverviewDashboardResponse.DailyRevenueItem> revenueByDay = new ArrayList<>();
        for (LocalDate d = chartStart; !d.isAfter(chartEnd); d = d.plusDays(1)) {
            BigDecimal rev = revenueByDateMap.getOrDefault(d, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            long bCount = bookingsByDateMap.getOrDefault(d, 0L);
            revenueByDay.add(new OverviewDashboardResponse.DailyRevenueItem(
                    d,
                    vietnameseDayLabel(d.getDayOfWeek()),
                    rev,
                    bCount));
        }

        // Chuyến bay hôm nay & hệ số lấp đầy (có áp dụng bộ lọc hãng / chặng nếu truyền)
        List<Flight> todayFlights = flightRepository.findFlightsInWindow(startOfToday, endOfToday).stream()
                .filter(f -> matchesFlight(f, airlineId, routeFilter))
                .toList();
        long flightsDelayedToday = todayFlights.stream()
                .filter(f -> f.getOperationStatus() == FlightOperationStatus.DELAYED)
                .count();

        Map<Long, Map<FlightSeatStatus, Long>> seatCounts = loadSeatCounts(todayFlights);
        long totalCap = 0;
        long totalOccupied = 0;
        for (Flight f : todayFlights) {
            int cap = f.getAircraft().getSeatCapacity() == null ? 180 : f.getAircraft().getSeatCapacity();
            Map<FlightSeatStatus, Long> c = seatCounts.getOrDefault(f.getId(), Map.of());
            if (!c.isEmpty()) {
                totalCap += cap;
                totalOccupied += c.getOrDefault(FlightSeatStatus.BOOKED, 0L)
                        + c.getOrDefault(FlightSeatStatus.HOLD, 0L);
            }
        }
        double occupancyRate = totalCap == 0
                ? 0.0
                : Math.round((totalOccupied * 1000.0) / totalCap) / 10.0;

        // Tập đơn dùng cho các bảng phân tích báo cáo (áp dụng thêm lọc ngày nếu user truyền from_date/to_date/days)
        List<Booking> reportBookings = hasDateFilter
                ? filteredBookings.stream()
                        .filter(b -> b.getCreatedAt() != null
                                && !b.getCreatedAt().toLocalDate().isBefore(chartStart)
                                && !b.getCreatedAt().toLocalDate().isAfter(chartEnd))
                        .toList()
                : filteredBookings;

        List<OverviewDashboardResponse.BookingStatusCountItem> bookingsByStatus =
                buildBookingsByStatus(reportBookings);
        List<OverviewDashboardResponse.AirlineRevenueItem> revenueByAirline =
                buildRevenueByAirline(reportBookings, paidBookingIds, segmentsByBooking, airlineId, routeFilter);
        List<OverviewDashboardResponse.TopRouteItem> topRoutes =
                buildTopRoutes(reportBookings, paidBookingIds, segmentsByBooking, paxByBooking, airlineId, routeFilter);

        // 10 đơn đặt vé gần nhất
        ListQuery recentQuery = new ListQuery();
        recentQuery.setPage(1);
        recentQuery.setSize(hasSegmentFilter ? 50 : 10);
        recentQuery.setSort("created_at,desc");
        List<BookingSummaryResponse> recentRaw = bookingService.listAll(recentQuery, null, null).items();
        List<BookingSummaryResponse> recentBookings = hasSegmentFilter
                ? recentRaw.stream()
                        .filter(item -> matchedBookingIds.contains(item.id()))
                        .limit(10)
                        .toList()
                : recentRaw;

        long totalPax = hasSegmentFilter
                ? filteredBookings.stream()
                        .mapToLong(b -> paxByBooking.getOrDefault(b.getId(), 0L))
                        .sum()
                : passengerRepository.count();

        return new OverviewDashboardResponse(
                revenueToday.setScale(2, RoundingMode.HALF_UP),
                revenueMonth.setScale(2, RoundingMode.HALF_UP),
                revenueTotal.setScale(2, RoundingMode.HALF_UP),
                bookingsToday,
                bookingsMonth,
                totalBookings,
                totalPax,
                totalPax,
                todayFlights.size(),
                flightsDelayedToday,
                occupancyRate,
                cancelRate,
                revenueByDay,
                topRoutes,
                recentBookings,
                revenueByAirline,
                bookingsByStatus
        );
    }

    // ================= Helpers =================

    private Map<Long, Map<FlightSeatStatus, Long>> loadSeatCounts(List<Flight> flights) {
        if (flights.isEmpty()) {
            return Map.of();
        }
        List<Long> flightIds = flights.stream().map(Flight::getId).toList();
        Map<Long, Map<FlightSeatStatus, Long>> result = new HashMap<>();
        for (FlightSeatRepository.FlightStatusCount row
                : flightSeatRepository.countSeatsByFlightIdsAndStatus(flightIds)) {
            result.computeIfAbsent(row.getFlightId(), k -> new EnumMap<>(FlightSeatStatus.class))
                    .put(row.getStatus(), row.getSeatCount());
        }
        return result;
    }

    /** Thống kê đơn đặt chỗ theo ĐÚNG 4 trạng thái thật trong enum BookingStatus. */
    private List<OverviewDashboardResponse.BookingStatusCountItem> buildBookingsByStatus(List<Booking> bookings) {
        Map<BookingStatus, Long> counts = new EnumMap<>(BookingStatus.class);
        for (Booking b : bookings) {
            if (b.getStatus() != null) {
                counts.merge(b.getStatus(), 1L, Long::sum);
            }
        }
        long total = bookings.size();
        List<BookingStatus> orderedStatuses = List.of(
                BookingStatus.CONFIRMED,
                BookingStatus.PENDING_PAYMENT,
                BookingStatus.CANCELLED,
                BookingStatus.EXPIRED);

        List<OverviewDashboardResponse.BookingStatusCountItem> items = new ArrayList<>(orderedStatuses.size());
        for (BookingStatus status : orderedStatuses) {
            long count = counts.getOrDefault(status, 0L);
            double pct = total == 0 ? 0.0 : Math.round((count * 1000.0) / total) / 10.0;
            items.add(new OverviewDashboardResponse.BookingStatusCountItem(status.name(), count, pct));
        }
        return items;
    }

    /** Doanh thu & số đơn đặt theo từng hãng hàng không từ các đơn đã xác nhận/thanh toán. */
    private List<OverviewDashboardResponse.AirlineRevenueItem> buildRevenueByAirline(
            List<Booking> bookings,
            Set<Long> paidBookingIds,
            Map<Long, List<BookingSegment>> segmentsByBooking,
            Long airlineId,
            String[] routeFilter) {
        record AirlineAcc(String code, String name, BigDecimal revenue, Set<Long> bookingIds) {
        }

        Map<String, AirlineAcc> map = new HashMap<>();
        for (Booking b : bookings) {
            if (b.getStatus() != BookingStatus.CONFIRMED && !paidBookingIds.contains(b.getId())) {
                continue;
            }
            for (BookingSegment seg : segmentsByBooking.getOrDefault(b.getId(), List.of())) {
                if (!matchesSegment(seg, airlineId, routeFilter)) {
                    continue;
                }
                Flight f = seg.getFlight();
                if (f == null || f.getAirline() == null) {
                    continue;
                }
                String code = f.getAirline().getAirlineCode();
                String name = f.getAirline().getAirlineName();
                BigDecimal netSeg = netSegmentAmount(seg);

                AirlineAcc prev = map.get(code);
                if (prev == null) {
                    Set<Long> ids = new HashSet<>();
                    if (b.getId() != null) {
                        ids.add(b.getId());
                    }
                    map.put(code, new AirlineAcc(code, name, netSeg, ids));
                } else {
                    if (b.getId() != null) {
                        prev.bookingIds().add(b.getId());
                    }
                    map.put(code, new AirlineAcc(code, name, prev.revenue().add(netSeg), prev.bookingIds()));
                }
            }
        }

        BigDecimal totalRevenue = map.values().stream()
                .map(AirlineAcc::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return map.values().stream()
                .sorted(Comparator.comparing(AirlineAcc::revenue).reversed())
                .map(acc -> {
                    double pct = totalRevenue.compareTo(BigDecimal.ZERO) == 0
                            ? 0.0
                            : acc.revenue()
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(totalRevenue, 1, RoundingMode.HALF_UP)
                                    .doubleValue();
                    return new OverviewDashboardResponse.AirlineRevenueItem(
                            acc.code(),
                            acc.name(),
                            acc.revenue().setScale(2, RoundingMode.HALF_UP),
                            acc.bookingIds().size(),
                            pct);
                })
                .toList();
    }

    /** Top tuyến bay theo doanh thu kèm số chuyến bay, số hành khách và hệ số lấp đầy thực. */
    private List<OverviewDashboardResponse.TopRouteItem> buildTopRoutes(
            List<Booking> bookings,
            Set<Long> paidBookingIds,
            Map<Long, List<BookingSegment>> segmentsByBooking,
            Map<Long, Long> paxByBooking,
            Long airlineId,
            String[] routeFilter) {
        record RouteAcc(
                String routeName,
                String depCode,
                String arrCode,
                Set<Long> bookingIds,
                Map<Long, Flight> flights,
                long paxCount,
                BigDecimal revenue
        ) {
        }

        Map<String, RouteAcc> map = new HashMap<>();
        for (Booking b : bookings) {
            if (b.getStatus() != BookingStatus.CONFIRMED && !paidBookingIds.contains(b.getId())) {
                continue;
            }
            long paxOfBooking = paxByBooking.getOrDefault(b.getId(), 1L);
            for (BookingSegment seg : segmentsByBooking.getOrDefault(b.getId(), List.of())) {
                if (!matchesSegment(seg, airlineId, routeFilter)) {
                    continue;
                }
                Flight f = seg.getFlight();
                if (f == null || f.getDepartureAirport() == null || f.getArrivalAirport() == null) {
                    continue;
                }
                String depCode = f.getDepartureAirport().getAirportCode();
                String arrCode = f.getArrivalAirport().getAirportCode();
                String depCity = f.getDepartureAirport().getCity() != null
                        ? f.getDepartureAirport().getCity().getCityName()
                        : depCode;
                String arrCity = f.getArrivalAirport().getCity() != null
                        ? f.getArrivalAirport().getCity().getCityName()
                        : arrCode;
                String routeLabel = depCity + " → " + arrCity;
                String key = depCode + "-" + arrCode;
                BigDecimal netSeg = netSegmentAmount(seg);

                RouteAcc prev = map.get(key);
                if (prev == null) {
                    Set<Long> bIds = new HashSet<>();
                    if (b.getId() != null) {
                        bIds.add(b.getId());
                    }
                    Map<Long, Flight> fMap = new HashMap<>();
                    if (f.getId() != null) {
                        fMap.put(f.getId(), f);
                    }
                    map.put(key, new RouteAcc(routeLabel, depCode, arrCode, bIds, fMap, paxOfBooking, netSeg));
                } else {
                    if (b.getId() != null) {
                        prev.bookingIds().add(b.getId());
                    }
                    if (f.getId() != null) {
                        prev.flights().putIfAbsent(f.getId(), f);
                    }
                    map.put(key, new RouteAcc(
                            routeLabel,
                            depCode,
                            arrCode,
                            prev.bookingIds(),
                            prev.flights(),
                            prev.paxCount() + paxOfBooking,
                            prev.revenue().add(netSeg)));
                }
            }
        }

        List<RouteAcc> top = map.values().stream()
                .sorted(Comparator.comparing(RouteAcc::revenue).reversed())
                .limit(10)
                .toList();

        List<Flight> distinctFlights = top.stream()
                .flatMap(r -> r.flights().values().stream())
                .distinct()
                .toList();
        Map<Long, Map<FlightSeatStatus, Long>> seatCountsByFlight = loadSeatCounts(distinctFlights);

        return top.stream()
                .map(r -> {
                    long capSum = 0;
                    long occupiedSum = 0;
                    for (Flight f : r.flights().values()) {
                        Map<FlightSeatStatus, Long> counts = seatCountsByFlight.getOrDefault(f.getId(), Map.of());
                        if (!counts.isEmpty()) {
                            long totalSeats = counts.values().stream().mapToLong(Long::longValue).sum();
                            capSum += totalSeats > 0
                                    ? totalSeats
                                    : (f.getAircraft() != null && f.getAircraft().getSeatCapacity() != null
                                            ? f.getAircraft().getSeatCapacity()
                                            : 180);
                            occupiedSum += counts.getOrDefault(FlightSeatStatus.BOOKED, 0L)
                                    + counts.getOrDefault(FlightSeatStatus.HOLD, 0L);
                        }
                    }
                    double loadFactor = capSum == 0
                            ? 0.0
                            : Math.round((occupiedSum * 1000.0) / capSum) / 10.0;
                    return new OverviewDashboardResponse.TopRouteItem(
                            r.routeName(),
                            r.depCode(),
                            r.arrCode(),
                            r.bookingIds().size(),
                            r.revenue().setScale(2, RoundingMode.HALF_UP),
                            loadFactor,
                            r.flights().size(),
                            r.paxCount());
                })
                .toList();
    }

    private BigDecimal netSegmentAmount(BookingSegment seg) {
        BigDecimal fare = seg.getFareAmountAtBooking() == null ? BigDecimal.ZERO : seg.getFareAmountAtBooking();
        BigDecimal promo = seg.getPromotionDiscountAmount() == null ? BigDecimal.ZERO : seg.getPromotionDiscountAmount();
        return fare.subtract(promo).max(BigDecimal.ZERO);
    }

    private boolean matchesSegment(BookingSegment seg, Long airlineId, String[] routeFilter) {
        if (seg == null || seg.getFlight() == null) {
            return false;
        }
        return matchesFlight(seg.getFlight(), airlineId, routeFilter);
    }

    private boolean matchesFlight(Flight flight, Long airlineId, String[] routeFilter) {
        if (flight == null) {
            return false;
        }
        if (airlineId != null) {
            if (flight.getAirline() == null || !airlineId.equals(flight.getAirline().getId())) {
                return false;
            }
        }
        if (routeFilter != null) {
            if (flight.getDepartureAirport() == null || flight.getArrivalAirport() == null) {
                return false;
            }
            String dep = flight.getDepartureAirport().getAirportCode();
            String arr = flight.getArrivalAirport().getAirportCode();
            if (!routeFilter[0].equalsIgnoreCase(dep) || !routeFilter[1].equalsIgnoreCase(arr)) {
                return false;
            }
        }
        return true;
    }

    private String[] parseRoute(String route) {
        if (route == null || route.isBlank()) {
            return null;
        }
        String normalized = route.trim()
                .replace("→", "-")
                .replace("->", "-")
                .replace("–", "-")
                .replace("—", "-");
        String[] parts = normalized.split("-");
        if (parts.length != 2) {
            return null;
        }
        String dep = parts[0].trim().toUpperCase();
        String arr = parts[1].trim().toUpperCase();
        if (dep.isEmpty() || arr.isEmpty()) {
            return null;
        }
        return new String[]{dep, arr};
    }

    private String vietnameseDayLabel(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "T2";
            case TUESDAY -> "T3";
            case WEDNESDAY -> "T4";
            case THURSDAY -> "T5";
            case FRIDAY -> "T6";
            case SATURDAY -> "T7";
            case SUNDAY -> "CN";
        };
    }
}
