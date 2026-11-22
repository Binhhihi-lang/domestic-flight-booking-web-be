package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.request.booking.BookingRequest;
import com.example.domesticflightbookingwebbackend.dto.request.booking.CancelBookingRequest;
import com.example.domesticflightbookingwebbackend.dto.response.booking.BookingDetailResponse;
import com.example.domesticflightbookingwebbackend.entity.Aircraft;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.entity.Airport;
import com.example.domesticflightbookingwebbackend.entity.Booking;
import com.example.domesticflightbookingwebbackend.entity.BookingSegment;
import com.example.domesticflightbookingwebbackend.entity.BookingStatus;
import com.example.domesticflightbookingwebbackend.entity.City;
import com.example.domesticflightbookingwebbackend.entity.ETicket;
import com.example.domesticflightbookingwebbackend.entity.ETicketStatus;
import com.example.domesticflightbookingwebbackend.entity.FareClass;
import com.example.domesticflightbookingwebbackend.entity.FareRule;
import com.example.domesticflightbookingwebbackend.entity.Flight;
import com.example.domesticflightbookingwebbackend.entity.FlightOperationStatus;
import com.example.domesticflightbookingwebbackend.entity.SalesStatus;
import com.example.domesticflightbookingwebbackend.entity.SeatClass;
import com.example.domesticflightbookingwebbackend.entity.TripType;
import com.example.domesticflightbookingwebbackend.entity.User;
import com.example.domesticflightbookingwebbackend.entity.UserVoucher;
import com.example.domesticflightbookingwebbackend.entity.UserVoucherStatus;
import com.example.domesticflightbookingwebbackend.entity.Voucher;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.repository.BaggageOptionRepository;
import com.example.domesticflightbookingwebbackend.repository.BookingBaggageRepository;
import com.example.domesticflightbookingwebbackend.repository.BookingRepository;
import com.example.domesticflightbookingwebbackend.repository.BookingSegmentRepository;
import com.example.domesticflightbookingwebbackend.repository.ETicketRepository;
import com.example.domesticflightbookingwebbackend.repository.FareClassRepository;
import com.example.domesticflightbookingwebbackend.repository.FareRuleRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightRepository;
import com.example.domesticflightbookingwebbackend.repository.PassengerRepository;
import com.example.domesticflightbookingwebbackend.repository.SeatHoldRepository;
import com.example.domesticflightbookingwebbackend.repository.UserRepository;
import com.example.domesticflightbookingwebbackend.repository.UserVoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingServiceTest {

    private BookingRepository bookingRepository;
    private BookingSegmentRepository bookingSegmentRepository;
    private PassengerRepository passengerRepository;
    private SeatHoldRepository seatHoldRepository;
    private BookingBaggageRepository bookingBaggageRepository;
    private BaggageOptionRepository baggageOptionRepository;
    private ETicketRepository eTicketRepository;
    private FlightRepository flightRepository;
    private FareClassRepository fareClassRepository;
    private FareRuleRepository fareRuleRepository;
    private UserRepository userRepository;
    private UserVoucherRepository userVoucherRepository;
    private PricingService pricingService;
    private SeatHoldService seatHoldService;
    private BookingService service;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        bookingSegmentRepository = mock(BookingSegmentRepository.class);
        passengerRepository = mock(PassengerRepository.class);
        seatHoldRepository = mock(SeatHoldRepository.class);
        bookingBaggageRepository = mock(BookingBaggageRepository.class);
        baggageOptionRepository = mock(BaggageOptionRepository.class);
        eTicketRepository = mock(ETicketRepository.class);
        flightRepository = mock(FlightRepository.class);
        fareClassRepository = mock(FareClassRepository.class);
        fareRuleRepository = mock(FareRuleRepository.class);
        userRepository = mock(UserRepository.class);
        userVoucherRepository = mock(UserVoucherRepository.class);
        pricingService = mock(PricingService.class);
        seatHoldService = mock(SeatHoldService.class);

        service = new BookingService(
                bookingRepository,
                bookingSegmentRepository,
                passengerRepository,
                seatHoldRepository,
                bookingBaggageRepository,
                baggageOptionRepository,
                eTicketRepository,
                flightRepository,
                fareClassRepository,
                fareRuleRepository,
                userRepository,
                userVoucherRepository,
                pricingService,
                seatHoldService);
    }

    @Test
    void create_motChieuHopLe_taoPendingPaymentKemHan15Phut() {
        User user = user(1L);
        Flight flight = openFlight(55L, "VN165");
        FareClass fc = fareClass(2L, flight.getAirline());
        FareRule rule = fareRule(10L, fc);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(bookingRepository.existsByBookingCode(any())).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(1049L);
            }
            return b;
        });
        when(passengerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(flightRepository.findWithDetailsById(55L)).thenReturn(Optional.of(flight));
        when(fareClassRepository.findById(2L)).thenReturn(Optional.of(fc));
        when(fareRuleRepository.findActiveRulesByFareClassId(any(), any())).thenReturn(List.of(rule));
        when(pricingService.priceSegmentWithBestPromotion(any(), any(), any(), any()))
                .thenReturn(new PricingService.SegmentPricingResult(
                        new BigDecimal("1290000.00"),
                        new BigDecimal("1290000.00"),
                        null,
                        BigDecimal.ZERO));
        when(pricingService.computeTotal(any())).thenReturn(new BigDecimal("1290000.00"));
        when(bookingSegmentRepository.save(any(BookingSegment.class))).thenAnswer(inv -> {
            BookingSegment s = inv.getArgument(0);
            s.setId(500L);
            return s;
        });
        when(bookingSegmentRepository.findByBookingIdOrderBySegmentOrderAsc(1049L))
                .thenAnswer(inv -> {
                    BookingSegment s = new BookingSegment();
                    s.setId(500L);
                    s.setSegmentOrder(1);
                    s.setFlight(flight);
                    s.setFareClass(fc);
                    s.setFareRule(rule);
                    s.setFareAmountAtBooking(new BigDecimal("1290000.00"));
                    s.setPromotionDiscountAmount(BigDecimal.ZERO);
                    s.setCurrency("VND");
                    return List.of(s);
                });
        when(passengerRepository.findByBookingIdOrderByIdAsc(1049L)).thenReturn(List.of());
        when(seatHoldRepository.findByBookingIdOrderByIdAsc(1049L)).thenReturn(List.of());
        when(bookingBaggageRepository.findByBookingIdOrderByIdAsc(1049L)).thenReturn(List.of());
        when(userVoucherRepository.findByBookingId(1049L)).thenReturn(Optional.empty());

        BookingRequest req = new BookingRequest(
                "ONE_WAY",
                List.of(new BookingRequest.SegmentItem(55L, 2L, 1)),
                List.of(new BookingRequest.PassengerItem(
                        "Nguyễn Văn An", "MALE", LocalDate.of(1990, 3, 12),
                        "ADULT", "079190001234", "0901234567", "an.nguyen@example.com")),
                null);

        BookingDetailResponse res = service.create(1L, req);

        assertThat(res.id()).isEqualTo(1049L);
        assertThat(res.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(res.bookingCode()).hasSize(6);
        assertThat(res.totalAmount()).isEqualByComparingTo("1290000.00");
        assertThat(res.paymentExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(14));
        assertThat(res.agencyContact().hotline()).isEqualTo("1900 1234");
    }

    @Test
    void create_soEmBeVuotQuaSoNguoiLon_nem1006() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));

        BookingRequest req = new BookingRequest(
                "ONE_WAY",
                List.of(new BookingRequest.SegmentItem(55L, 2L, 1)),
                List.of(
                        new BookingRequest.PassengerItem("Người lớn 1", "MALE", LocalDate.of(1990, 1, 1), "ADULT", null, null, null),
                        new BookingRequest.PassengerItem("Em bé 1", "MALE", LocalDate.of(2025, 6, 1), "INFANT", null, null, null),
                        new BookingRequest.PassengerItem("Em bé 2", "FEMALE", LocalDate.of(2025, 8, 1), "INFANT", null, null, null)
                ),
                null);

        assertThatThrownBy(() -> service.create(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("INFANT");
    }

    @Test
    void getSelf_truyCapBookingNguoiKhac_nem1003ChongIdor() {
        Booking otherUserBooking = new Booking();
        otherUserBooking.setId(1049L);
        otherUserBooking.setUser(user(2L)); // Chủ sở hữu là userId=2

        when(bookingRepository.findWithUserById(1049L)).thenReturn(Optional.of(otherUserBooking));

        assertThatThrownBy(() -> service.getSelf(1L, 1049L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void cancel_donPendingPayment_nhaGheHuyVeTraVoucherVaKhongTuHoanTien() {
        User u = user(1L);
        Booking booking = new Booking();
        booking.setId(1049L);
        booking.setBookingCode("X7K2MP");
        booking.setUser(u);
        booking.setTripType(TripType.ONE_WAY);
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalAmount(new BigDecimal("2959000.00"));

        Flight flight = openFlight(55L, "VN165");
        flight.setScheduledDeparture(LocalDateTime.now().plusDays(3));
        FareClass fc = fareClass(2L, flight.getAirline());
        FareRule rule = fareRule(10L, fc);

        BookingSegment seg = new BookingSegment();
        seg.setId(500L);
        seg.setBooking(booking);
        seg.setFlight(flight);
        seg.setFareClass(fc);
        seg.setFareRule(rule);
        seg.setSegmentOrder(1);
        seg.setFareAmountAtBooking(new BigDecimal("2959000.00"));
        seg.setPromotionDiscountAmount(BigDecimal.ZERO);

        ETicket ticket = new ETicket();
        ticket.setId(700L);
        ticket.setStatus(ETicketStatus.ISSUED);

        Voucher v = new Voucher();
        v.setId(5L);
        v.setVoucherCode("BAYHE2026");
        v.setVoucherName("Giảm 200k");

        UserVoucher uv = new UserVoucher();
        uv.setId(88L);
        uv.setVoucher(v);
        uv.setBooking(booking);
        uv.setStatus(UserVoucherStatus.RESERVED);
        uv.setExpiresAt(LocalDateTime.now().plusDays(10));

        when(bookingRepository.findWithUserById(1049L)).thenReturn(Optional.of(booking));
        when(bookingSegmentRepository.findByBookingIdOrderBySegmentOrderAsc(1049L)).thenReturn(List.of(seg));
        when(eTicketRepository.findByBookingIdOrderByIdAsc(1049L)).thenReturn(List.of(ticket));
        when(userVoucherRepository.findByBookingId(1049L)).thenReturn(Optional.of(uv));
        when(passengerRepository.findByBookingIdOrderByIdAsc(1049L)).thenReturn(List.of());
        when(seatHoldRepository.findByBookingIdOrderByIdAsc(1049L)).thenReturn(List.of());
        when(bookingBaggageRepository.findByBookingIdOrderByIdAsc(1049L)).thenReturn(List.of());

        BookingDetailResponse res = service.cancel(1L, 1049L, new CancelBookingRequest("Đổi kế hoạch công tác"));

        assertThat(res.status()).isEqualTo("CANCELLED");
        assertThat(res.cancelReason()).isEqualTo("Đổi kế hoạch công tác");
        assertThat(res.cancelledAt()).isNotNull();
        verify(seatHoldService).releaseAllForBooking(1049L);
        assertThat(ticket.getStatus()).isEqualTo(ETicketStatus.CANCELLED);
        assertThat(uv.getStatus()).isEqualTo(UserVoucherStatus.AVAILABLE);
        assertThat(uv.getBooking()).isNull();
        // Vẫn trả kèm chính sách hoàn vé tham khảo và thông tin liên hệ đại lý
        assertThat(res.segments().get(0).fareRule().refundPercent()).isEqualByComparingTo("90");
        assertThat(res.agencyContact().supportEmail()).isEqualTo("hotro@vinafly.vn");
    }

    @Test
    void lookupPublic_saiEmail_nemResourceNotFound() {
        User u = user(1L);
        Booking booking = new Booking();
        booking.setId(1049L);
        booking.setBookingCode("X7K2MP");
        booking.setUser(u);

        when(bookingRepository.findWithUserByBookingCodeIgnoreCase("X7K2MP")).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.lookupPublic("X7K2MP", "other@example.com"))
                .isInstanceOf(com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException.class);
    }

    private User user(Long id) {
        User u = new User();
        u.setId(id);
        u.setFullName("Nguyễn Văn An");
        u.setEmail("an.nguyen@example.com");
        u.setPhone("0901234567");
        u.setIsDeleted(false);
        return u;
    }

    private Flight openFlight(Long id, String number) {
        Airline airline = new Airline();
        airline.setId(1L);
        airline.setAirlineCode("VN");
        airline.setAirlineName("Vietnam Airlines");

        Aircraft aircraft = new Aircraft();
        aircraft.setId(3L);
        aircraft.setAircraftCode("VN-A321-05");
        aircraft.setModel("Airbus A321neo");

        City hanCity = new City();
        hanCity.setCityName("Hà Nội");
        Airport han = new Airport();
        han.setId(1L);
        han.setAirportCode("HAN");
        han.setAirportName("Nội Bài");
        han.setCity(hanCity);

        City dadCity = new City();
        dadCity.setCityName("Đà Nẵng");
        Airport dad = new Airport();
        dad.setId(3L);
        dad.setAirportCode("DAD");
        dad.setAirportName("Đà Nẵng");
        dad.setCity(dadCity);

        Flight f = new Flight();
        f.setId(id);
        f.setFlightNumber(number);
        f.setAirline(airline);
        f.setAircraft(aircraft);
        f.setDepartureAirport(han);
        f.setArrivalAirport(dad);
        f.setScheduledDeparture(LocalDateTime.now().plusDays(5));
        f.setScheduledArrival(LocalDateTime.now().plusDays(5).plusMinutes(85));
        f.setOperationStatus(FlightOperationStatus.SCHEDULED);
        f.setSalesStatus(SalesStatus.OPEN);
        f.setIsDeleted(false);
        return f;
    }

    private FareClass fareClass(Long id, Airline airline) {
        FareClass fc = new FareClass();
        fc.setId(id);
        fc.setAirline(airline);
        fc.setFareClassCode("VN_ECON_FLEX");
        fc.setFareClassName("Phổ thông linh hoạt");
        fc.setSeatClass(SeatClass.ECONOMY);
        fc.setIsActive(true);
        fc.setIsDeleted(false);
        return fc;
    }

    private FareRule fareRule(Long id, FareClass fc) {
        FareRule r = new FareRule();
        r.setId(id);
        r.setFareClass(fc);
        r.setVersion(1);
        r.setIsRefundable(true);
        r.setRefundMinHoursBeforeDeparture(24);
        r.setRefundPercent(new BigDecimal("90"));
        r.setDescription("Hoàn 90% trước 24h");
        return r;
    }
}
