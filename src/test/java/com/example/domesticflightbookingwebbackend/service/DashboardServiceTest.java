package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.response.dashboard.OperationalDashboardResponse;
import com.example.domesticflightbookingwebbackend.dto.response.dashboard.OverviewDashboardResponse;
import com.example.domesticflightbookingwebbackend.entity.Aircraft;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.entity.Airport;
import com.example.domesticflightbookingwebbackend.entity.Booking;
import com.example.domesticflightbookingwebbackend.entity.BookingSegment;
import com.example.domesticflightbookingwebbackend.entity.BookingStatus;
import com.example.domesticflightbookingwebbackend.entity.City;
import com.example.domesticflightbookingwebbackend.entity.Flight;
import com.example.domesticflightbookingwebbackend.entity.FlightOperationStatus;
import com.example.domesticflightbookingwebbackend.entity.FlightSeatStatus;
import com.example.domesticflightbookingwebbackend.entity.Payment;
import com.example.domesticflightbookingwebbackend.entity.PaymentStatus;
import com.example.domesticflightbookingwebbackend.entity.SalesStatus;
import com.example.domesticflightbookingwebbackend.repository.BookingRepository;
import com.example.domesticflightbookingwebbackend.repository.BookingSegmentRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightSeatRepository;
import com.example.domesticflightbookingwebbackend.repository.PassengerRepository;
import com.example.domesticflightbookingwebbackend.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private BookingRepository bookingRepository;
    private BookingSegmentRepository bookingSegmentRepository;
    private PassengerRepository passengerRepository;
    private FlightRepository flightRepository;
    private FlightSeatRepository flightSeatRepository;
    private PaymentRepository paymentRepository;
    private BookingService bookingService;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        bookingSegmentRepository = mock(BookingSegmentRepository.class);
        passengerRepository = mock(PassengerRepository.class);
        flightRepository = mock(FlightRepository.class);
        flightSeatRepository = mock(FlightSeatRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        bookingService = mock(BookingService.class);

        service = new DashboardService(
                bookingRepository,
                bookingSegmentRepository,
                passengerRepository,
                flightRepository,
                flightSeatRepository,
                paymentRepository,
                bookingService);
    }

    @Test
    void operational_thongKeChuyenBayVaViecCanXuLy_khongCoTruongDoanhThu() {
        Flight f1 = flight(1L, "VN165", FlightOperationStatus.SCHEDULED);
        Flight f2 = flight(2L, "VJ621", FlightOperationStatus.DELAYED);

        when(flightRepository.findFlightsInWindow(any(), any())).thenReturn(List.of(f1, f2));
        when(flightSeatRepository.countSeatsByFlightIdsAndStatus(any())).thenReturn(List.of());
        when(bookingRepository.countByCreatedAtBetween(any(), any())).thenReturn(15L);
        when(bookingRepository.countByStatusAndCreatedAtBetween(any(), any(), any())).thenReturn(5L);
        when(passengerRepository.count()).thenReturn(32L);

        OperationalDashboardResponse res = service.operational();

        assertThat(res.flightsToday()).isEqualTo(2L);
        assertThat(res.flightsScheduled()).isEqualTo(1L);
        assertThat(res.flightsDelayed()).isEqualTo(1L);
        assertThat(res.bookingsToday()).isEqualTo(15L);
        assertThat(res.pendingActions()).hasSize(1);
        assertThat(res.pendingActions().get(0).referenceCode()).isEqualTo("VJ621");
    }

    @Test
    void overview_tinhDungDoanhThuVaBieuDo7NgayChoAdmin() {
        Payment p1 = new Payment();
        p1.setStatus(PaymentStatus.SUCCESS);
        p1.setAmount(new BigDecimal("2959000.00"));
        p1.setPaidAt(LocalDateTime.now());

        Payment p2Failed = new Payment();
        p2Failed.setStatus(PaymentStatus.FAILED);
        p2Failed.setAmount(new BigDecimal("5000000.00"));
        p2Failed.setCreatedAt(LocalDateTime.now());

        Booking b1 = new Booking();
        b1.setId(1L);
        b1.setStatus(BookingStatus.CONFIRMED);
        b1.setCreatedAt(LocalDateTime.now());

        Booking b2 = new Booking();
        b2.setId(2L);
        b2.setStatus(BookingStatus.CANCELLED);
        b2.setCreatedAt(LocalDateTime.now());

        when(paymentRepository.findAll()).thenReturn(List.of(p1, p2Failed));
        when(bookingRepository.findAll()).thenReturn(List.of(b1, b2));
        when(flightRepository.findFlightsInWindow(any(), any())).thenReturn(List.of());
        when(bookingRepository.findTop10ByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(bookingService.listAll(any(), any(), any()))
                .thenReturn(new PageData<>(List.of(), new PageData.Pagination(1, 10, 0, 1)));
        when(passengerRepository.count()).thenReturn(4L);

        OverviewDashboardResponse res = service.overview(null, null);

        assertThat(res.revenueToday()).isEqualByComparingTo("2959000.00");
        assertThat(res.revenueMonth()).isEqualByComparingTo("2959000.00");
        assertThat(res.bookingsToday()).isEqualTo(2L);
        assertThat(res.cancelRatePercent()).isEqualTo(50.0);
        assertThat(res.revenueByDay()).hasSize(7);
        assertThat(res.bookingsByStatus())
                .extracting(OverviewDashboardResponse.BookingStatusCountItem::status)
                .containsExactly("CONFIRMED", "PENDING_PAYMENT", "CANCELLED", "EXPIRED")
                .doesNotContain("REFUNDED");
    }

    @Test
    void overview_coBoLocDaysHangBayVaChangBay_tinhDungPhanTichBaoCao() {
        Flight vnHanSgn = flightDetail(10L, "VN201", 1L, "VN", "Vietnam Airlines", "HAN", "Hà Nội", "SGN", "TP.HCM");
        Flight vjHanDad = flightDetail(20L, "VJ501", 2L, "VJ", "Vietjet Air", "HAN", "Hà Nội", "DAD", "Đà Nẵng");

        Booking b1 = new Booking();
        b1.setId(101L);
        b1.setStatus(BookingStatus.CONFIRMED);
        b1.setCreatedAt(LocalDateTime.now());

        Booking b2 = new Booking();
        b2.setId(102L);
        b2.setStatus(BookingStatus.CONFIRMED);
        b2.setCreatedAt(LocalDateTime.now());

        Booking b3 = new Booking();
        b3.setId(103L);
        b3.setStatus(BookingStatus.EXPIRED);
        b3.setCreatedAt(LocalDateTime.now());

        BookingSegment s1 = segment(b1, vnHanSgn, new BigDecimal("3000000.00"));
        BookingSegment s2 = segment(b2, vjHanDad, new BigDecimal("1000000.00"));
        BookingSegment s3 = segment(b3, vnHanSgn, new BigDecimal("1500000.00"));

        Payment p1 = new Payment();
        p1.setBooking(b1);
        p1.setStatus(PaymentStatus.SUCCESS);
        p1.setAmount(new BigDecimal("3000000.00"));
        p1.setPaidAt(LocalDateTime.now());

        Payment p2 = new Payment();
        p2.setBooking(b2);
        p2.setStatus(PaymentStatus.SUCCESS);
        p2.setAmount(new BigDecimal("1000000.00"));
        p2.setPaidAt(LocalDateTime.now());

        PassengerRepository.BookingPassengerCount pax1 = mock(PassengerRepository.BookingPassengerCount.class);
        when(pax1.getBookingId()).thenReturn(101L);
        when(pax1.getPassengerCount()).thenReturn(3L);

        PassengerRepository.BookingPassengerCount pax2 = mock(PassengerRepository.BookingPassengerCount.class);
        when(pax2.getBookingId()).thenReturn(102L);
        when(pax2.getPassengerCount()).thenReturn(2L);

        FlightSeatRepository.FlightStatusCount bookedCount = mock(FlightSeatRepository.FlightStatusCount.class);
        when(bookedCount.getFlightId()).thenReturn(10L);
        when(bookedCount.getStatus()).thenReturn(FlightSeatStatus.BOOKED);
        when(bookedCount.getSeatCount()).thenReturn(90L);

        FlightSeatRepository.FlightStatusCount availCount = mock(FlightSeatRepository.FlightStatusCount.class);
        when(availCount.getFlightId()).thenReturn(10L);
        when(availCount.getStatus()).thenReturn(FlightSeatStatus.AVAILABLE);
        when(availCount.getSeatCount()).thenReturn(90L);

        when(bookingRepository.findAll()).thenReturn(List.of(b1, b2, b3));
        when(bookingSegmentRepository.findByBookingIdInOrderBySegmentOrderAsc(any())).thenReturn(List.of(s1, s2, s3));
        when(passengerRepository.countByBookingIdIn(any())).thenReturn(List.of(pax1, pax2));
        when(paymentRepository.findAll()).thenReturn(List.of(p1, p2));
        when(flightRepository.findFlightsInWindow(any(), any())).thenReturn(List.of(vnHanSgn, vjHanDad));
        when(flightSeatRepository.countSeatsByFlightIdsAndStatus(any())).thenReturn(List.of(bookedCount, availCount));
        when(bookingService.listAll(any(), any(), any()))
                .thenReturn(new PageData<>(List.of(), new PageData.Pagination(1, 50, 0, 1)));
        when(passengerRepository.count()).thenReturn(5L);

        // Không lọc hãng/chặng, days=30 -> gom gọn thành 8 cụm ngày để biểu đồ không tràn ngang
        OverviewDashboardResponse full = service.overview(null, null, null, null, 30);
        assertThat(full.revenueByDay()).hasSize(8);
        assertThat(full.revenueByDay().get(full.revenueByDay().size() - 1).revenue())
                .isEqualByComparingTo("4000000.00");
        assertThat(full.revenueByAirline()).hasSize(2);
        assertThat(full.revenueByAirline().get(0).airlineCode()).isEqualTo("VN");
        assertThat(full.revenueByAirline().get(0).percentage()).isEqualTo(75.0);
        assertThat(full.revenueByAirline().get(1).airlineCode()).isEqualTo("VJ");
        assertThat(full.revenueByAirline().get(1).percentage()).isEqualTo(25.0);
        assertThat(full.bookingsByStatus()).hasSize(4);
        assertThat(full.topRoutes()).hasSize(2);
        assertThat(full.topRoutes().get(0).flightsCount()).isEqualTo(1L);
        assertThat(full.topRoutes().get(0).paxCount()).isEqualTo(3L);
        assertThat(full.topRoutes().get(0).loadFactorPercent()).isEqualTo(50.0);

        // Lọc theo airline_id=1 và route dạng "HAN→SGN"
        OverviewDashboardResponse filteredArrow = service.overview(null, null, 1L, "HAN→SGN", 14);
        assertThat(filteredArrow.revenueByDay()).hasSize(14);
        assertThat(filteredArrow.revenueTotal()).isEqualByComparingTo("3000000.00");
        assertThat(filteredArrow.bookingsTotal()).isEqualTo(2L); // b1 (CONFIRMED) + b3 (EXPIRED)
        assertThat(filteredArrow.revenueByAirline()).hasSize(1);
        assertThat(filteredArrow.revenueByAirline().get(0).airlineCode()).isEqualTo("VN");
        assertThat(filteredArrow.topRoutes()).hasSize(1);
        assertThat(filteredArrow.topRoutes().get(0).departureAirportCode()).isEqualTo("HAN");
        assertThat(filteredArrow.topRoutes().get(0).arrivalAirportCode()).isEqualTo("SGN");

        // Lọc theo route dạng "HAN-DAD"
        OverviewDashboardResponse filteredDash = service.overview(null, null, null, "HAN-DAD", 7);
        assertThat(filteredDash.revenueTotal()).isEqualByComparingTo("1000000.00");
        assertThat(filteredDash.bookingsTotal()).isEqualTo(1L);
        assertThat(filteredDash.revenueByAirline()).hasSize(1);
        assertThat(filteredDash.revenueByAirline().get(0).airlineCode()).isEqualTo("VJ");
    }

    private BookingSegment segment(Booking booking, Flight flight, BigDecimal fareAmount) {
        BookingSegment seg = new BookingSegment();
        seg.setBooking(booking);
        seg.setFlight(flight);
        seg.setFareAmountAtBooking(fareAmount);
        seg.setPromotionDiscountAmount(BigDecimal.ZERO);
        return seg;
    }

    private Flight flightDetail(
            Long id, String number, Long airlineId, String airlineCode, String airlineName,
            String depCode, String depCityName, String arrCode, String arrCityName) {
        Airline airline = new Airline();
        airline.setId(airlineId);
        airline.setAirlineCode(airlineCode);
        airline.setAirlineName(airlineName);

        Aircraft aircraft = new Aircraft();
        aircraft.setAircraftCode(airlineCode + "-A321");
        aircraft.setSeatCapacity(180);

        City depCity = new City();
        depCity.setCityName(depCityName);
        Airport dep = new Airport();
        dep.setAirportCode(depCode);
        dep.setCity(depCity);

        City arrCity = new City();
        arrCity.setCityName(arrCityName);
        Airport arr = new Airport();
        arr.setAirportCode(arrCode);
        arr.setCity(arrCity);

        Flight f = new Flight();
        f.setId(id);
        f.setFlightNumber(number);
        f.setAirline(airline);
        f.setAircraft(aircraft);
        f.setDepartureAirport(dep);
        f.setArrivalAirport(arr);
        f.setScheduledDeparture(LocalDateTime.now().plusHours(2));
        f.setScheduledArrival(LocalDateTime.now().plusHours(4));
        f.setOperationStatus(FlightOperationStatus.SCHEDULED);
        f.setSalesStatus(SalesStatus.OPEN);
        return f;
    }

    private Flight flight(Long id, String number, FlightOperationStatus opStatus) {
        Airline airline = new Airline();
        airline.setAirlineCode("VN");
        airline.setAirlineName("Vietnam Airlines");

        Aircraft aircraft = new Aircraft();
        aircraft.setAircraftCode("VN-A321-05");
        aircraft.setSeatCapacity(180);

        Airport dep = new Airport();
        dep.setAirportCode("HAN");
        Airport arr = new Airport();
        arr.setAirportCode("DAD");

        Flight f = new Flight();
        f.setId(id);
        f.setFlightNumber(number);
        f.setAirline(airline);
        f.setAircraft(aircraft);
        f.setDepartureAirport(dep);
        f.setArrivalAirport(arr);
        f.setScheduledDeparture(LocalDateTime.now().plusHours(2));
        f.setScheduledArrival(LocalDateTime.now().plusHours(3));
        f.setOperationStatus(opStatus);
        f.setSalesStatus(SalesStatus.OPEN);
        return f;
    }
}
