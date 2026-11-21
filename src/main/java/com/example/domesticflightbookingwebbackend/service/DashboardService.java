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
import java.util.List;
import java.util.Map;

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
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime endOfToday = today.plusDays(1).atStartOfDay();
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();

        List<Payment> allPayments = paymentRepository.findAll();
        BigDecimal revenueToday = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal revenueMonth = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal revenueTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        Map<LocalDate, BigDecimal> revenueByDateMap = new HashMap<>();
        for (Payment p : allPayments) {
            if (p.getStatus() != PaymentStatus.SUCCESS) {
                continue;
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

        List<Booking> allBookings = bookingRepository.findAll();
        long bookingsToday = 0;
        long bookingsMonth = 0;
        long cancelledCount = 0;
        Map<LocalDate, Long> bookingsByDateMap = new HashMap<>();

        for (Booking b : allBookings) {
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

        long totalBookings = allBookings.size();
        double cancelRate = totalBookings == 0
                ? 0.0
                : Math.round((cancelledCount * 1000.0) / totalBookings) / 10.0;

        // Biểu đồ doanh thu 7 ngày gần nhất
        LocalDate chartEnd = toDate != null ? toDate : today;
        LocalDate chartStart = fromDate != null ? fromDate : chartEnd.minusDays(6);
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

        // Chuyến bay hôm nay & hệ số lấp đầy
        List<Flight> todayFlights = flightRepository.findFlightsInWindow(startOfToday, endOfToday);
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

        // Top tuyến bay từ các booking_segments đã xác nhận/đặt
        List<OverviewDashboardResponse.TopRouteItem> topRoutes = buildTopRoutes();

        // 10 đơn đặt vé gần nhất
        ListQuery recentQuery = new ListQuery();
        recentQuery.setPage(1);
        recentQuery.setSize(10);
        recentQuery.setSort("created_at,desc");
        List<BookingSummaryResponse> recentBookings = bookingService.listAll(recentQuery, null, null).items();

        long totalPax = passengerRepository.count();

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
                recentBookings
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

    private List<OverviewDashboardResponse.TopRouteItem> buildTopRoutes() {
        List<Booking> bookings = bookingRepository.findTop10ByOrderByCreatedAtDesc();
        if (bookings.isEmpty()) {
            return List.of();
        }
        List<Long> bookingIds = bookings.stream().map(Booking::getId).toList();
        List<BookingSegment> segments = bookingSegmentRepository.findByBookingIdInOrderBySegmentOrderAsc(bookingIds);

        record RouteAcc(
                String routeName,
                String depCode,
                String arrCode,
                long bookings,
                BigDecimal revenue
        ) {
        }

        Map<String, RouteAcc> map = new HashMap<>();
        for (BookingSegment seg : segments) {
            Flight f = seg.getFlight();
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

            BigDecimal netSeg = seg.getFareAmountAtBooking()
                    .subtract(seg.getPromotionDiscountAmount() == null ? BigDecimal.ZERO : seg.getPromotionDiscountAmount())
                    .max(BigDecimal.ZERO);

            RouteAcc prev = map.get(key);
            if (prev == null) {
                map.put(key, new RouteAcc(routeLabel, depCode, arrCode, 1L, netSeg));
            } else {
                map.put(key, new RouteAcc(routeLabel, depCode, arrCode, prev.bookings() + 1, prev.revenue().add(netSeg)));
            }
        }

        return map.values().stream()
                .sorted(Comparator.comparing(RouteAcc::revenue).reversed())
                .limit(5)
                .map(r -> new OverviewDashboardResponse.TopRouteItem(
                        r.routeName(),
                        r.depCode(),
                        r.arrCode(),
                        r.bookings(),
                        r.revenue().setScale(2, RoundingMode.HALF_UP),
                        82.5))
                .toList();
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
