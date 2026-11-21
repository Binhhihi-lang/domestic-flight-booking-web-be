package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.response.dashboard.OperationalDashboardResponse;
import com.example.domesticflightbookingwebbackend.dto.response.dashboard.OverviewDashboardResponse;
import com.example.domesticflightbookingwebbackend.entity.Aircraft;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.entity.Airport;
import com.example.domesticflightbookingwebbackend.entity.Booking;
import com.example.domesticflightbookingwebbackend.entity.BookingStatus;
import com.example.domesticflightbookingwebbackend.entity.Flight;
import com.example.domesticflightbookingwebbackend.entity.FlightOperationStatus;
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
