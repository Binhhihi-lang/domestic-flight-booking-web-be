package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.response.booking.HoldSeatResponse;
import com.example.domesticflightbookingwebbackend.entity.AircraftSeat;
import com.example.domesticflightbookingwebbackend.entity.Booking;
import com.example.domesticflightbookingwebbackend.entity.BookingSegment;
import com.example.domesticflightbookingwebbackend.entity.BookingStatus;
import com.example.domesticflightbookingwebbackend.entity.FareClass;
import com.example.domesticflightbookingwebbackend.entity.Flight;
import com.example.domesticflightbookingwebbackend.entity.FlightSeat;
import com.example.domesticflightbookingwebbackend.entity.FlightSeatStatus;
import com.example.domesticflightbookingwebbackend.entity.Passenger;
import com.example.domesticflightbookingwebbackend.entity.PassengerType;
import com.example.domesticflightbookingwebbackend.entity.SeatClass;
import com.example.domesticflightbookingwebbackend.entity.SeatHold;
import com.example.domesticflightbookingwebbackend.entity.SeatHoldStatus;
import com.example.domesticflightbookingwebbackend.entity.SeatType;
import com.example.domesticflightbookingwebbackend.entity.User;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.repository.BookingRepository;
import com.example.domesticflightbookingwebbackend.repository.BookingSegmentRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightSeatRepository;
import com.example.domesticflightbookingwebbackend.repository.PassengerRepository;
import com.example.domesticflightbookingwebbackend.repository.SeatHoldRepository;
import com.example.domesticflightbookingwebbackend.repository.UserVoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeatHoldServiceTest {

    private SeatHoldRepository seatHoldRepository;
    private SeatInventoryService seatInventoryService;
    private FlightSeatRepository flightSeatRepository;
    private BookingRepository bookingRepository;
    private BookingSegmentRepository bookingSegmentRepository;
    private PassengerRepository passengerRepository;
    private UserVoucherRepository userVoucherRepository;
    private PricingService pricingService;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private SeatHoldService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        seatHoldRepository = mock(SeatHoldRepository.class);
        seatInventoryService = mock(SeatInventoryService.class);
        flightSeatRepository = mock(FlightSeatRepository.class);
        bookingRepository = mock(BookingRepository.class);
        bookingSegmentRepository = mock(BookingSegmentRepository.class);
        passengerRepository = mock(PassengerRepository.class);
        userVoucherRepository = mock(UserVoucherRepository.class);
        pricingService = mock(PricingService.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        service = new SeatHoldService(
                seatHoldRepository,
                seatInventoryService,
                flightSeatRepository,
                bookingRepository,
                bookingSegmentRepository,
                passengerRepository,
                userVoucherRepository,
                pricingService,
                stringRedisTemplate);
    }

    @Test
    void hold_hopLe_giuGheThanhCong3LopVaCongPhuThu() {
        Booking booking = booking(100L, 1L);
        Passenger passenger = passenger(200L, booking, PassengerType.ADULT);
        Flight flight = flight(55L, "VN165");
        BookingSegment segment = segment(booking, flight, SeatClass.ECONOMY);
        FlightSeat seat = flightSeat(900L, flight, "7A", SeatClass.ECONOMY, new BigDecimal("70000"));

        when(bookingRepository.findWithUserById(100L)).thenReturn(Optional.of(booking));
        when(passengerRepository.findById(200L)).thenReturn(Optional.of(passenger));
        when(flightSeatRepository.findWithDetailsById(900L)).thenReturn(Optional.of(seat));
        when(bookingSegmentRepository.findByBookingIdOrderBySegmentOrderAsc(100L)).thenReturn(List.of(segment));
        when(seatHoldRepository.findByBookingIdAndStatusIn(any(), any())).thenReturn(List.of());
        when(seatInventoryService.hold(900L)).thenReturn(true);
        when(seatHoldRepository.saveAndFlush(any(SeatHold.class))).thenAnswer(inv -> {
            SeatHold sh = inv.getArgument(0);
            sh.setId(300L);
            return sh;
        });
        when(pricingService.computeTotal(any())).thenReturn(new BigDecimal("1270000.00"));

        HoldSeatResponse res = service.hold(1L, 100L, 200L, 900L);

        assertThat(res.id()).isEqualTo(300L);
        assertThat(res.seatCode()).isEqualTo("7A");
        assertThat(res.surchargeAtBooking()).isEqualByComparingTo("70000.00");
        assertThat(res.status()).isEqualTo("HOLD");
        verify(seatInventoryService).hold(900L);
    }

    @Test
    void hold_khongPhaiChuSoHuuBooking_nem1003ChongIdor() {
        Booking booking = booking(100L, 1L); // Chủ sở hữu là userId=1
        when(bookingRepository.findWithUserById(100L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.hold(999L, 100L, 200L, 900L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void hold_emBeInfant_nem1006() {
        Booking booking = booking(100L, 1L);
        Passenger infant = passenger(201L, booking, PassengerType.INFANT);

        when(bookingRepository.findWithUserById(100L)).thenReturn(Optional.of(booking));
        when(passengerRepository.findById(201L)).thenReturn(Optional.of(infant));

        assertThatThrownBy(() -> service.hold(1L, 100L, 201L, 900L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("INFANT");
    }

    @Test
    void hold_saiKhoangMuaVePhoThongChonGheThuongGia_nem1006() {
        Booking booking = booking(100L, 1L);
        Passenger passenger = passenger(200L, booking, PassengerType.ADULT);
        Flight flight = flight(55L, "VN165");
        BookingSegment ecoSegment = segment(booking, flight, SeatClass.ECONOMY);
        FlightSeat bizSeat = flightSeat(901L, flight, "1A", SeatClass.BUSINESS, BigDecimal.ZERO);

        when(bookingRepository.findWithUserById(100L)).thenReturn(Optional.of(booking));
        when(passengerRepository.findById(200L)).thenReturn(Optional.of(passenger));
        when(flightSeatRepository.findWithDetailsById(901L)).thenReturn(Optional.of(bizSeat));
        when(bookingSegmentRepository.findByBookingIdOrderBySegmentOrderAsc(100L)).thenReturn(List.of(ecoSegment));

        assertThatThrownBy(() -> service.hold(1L, 100L, 200L, 901L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("khoang");
    }

    @Test
    void hold_redisLockDangBiGiuBoiNguoiKhac_nem1006() {
        Booking booking = booking(100L, 1L);
        Passenger passenger = passenger(200L, booking, PassengerType.ADULT);
        Flight flight = flight(55L, "VN165");
        BookingSegment segment = segment(booking, flight, SeatClass.ECONOMY);
        FlightSeat seat = flightSeat(900L, flight, "7A", SeatClass.ECONOMY, BigDecimal.ZERO);

        when(bookingRepository.findWithUserById(100L)).thenReturn(Optional.of(booking));
        when(passengerRepository.findById(200L)).thenReturn(Optional.of(passenger));
        when(flightSeatRepository.findWithDetailsById(900L)).thenReturn(Optional.of(seat));
        when(bookingSegmentRepository.findByBookingIdOrderBySegmentOrderAsc(100L)).thenReturn(List.of(segment));
        when(seatHoldRepository.findByBookingIdAndStatusIn(any(), any())).thenReturn(List.of());
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> service.hold(1L, 100L, 200L, 900L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đang có khách hàng khác thao tác");
    }

    @Test
    void releaseExpired_quanhQuetVaGiaiPhongBookingQuaHan15Phut() {
        Booking expiredBooking = booking(100L, 1L);
        expiredBooking.setPaymentExpiresAt(LocalDateTime.now().minusMinutes(1));
        Flight flight = flight(55L, "VN165");
        FlightSeat seat = flightSeat(900L, flight, "7A", SeatClass.ECONOMY, BigDecimal.ZERO);
        seat.setStatus(FlightSeatStatus.HOLD);

        SeatHold hold = new SeatHold();
        hold.setId(300L);
        hold.setBooking(expiredBooking);
        hold.setFlightSeat(seat);
        hold.setStatus(SeatHoldStatus.HOLD);

        when(bookingRepository.findByStatusAndPaymentExpiresAtBefore(any(), any()))
                .thenReturn(List.of(expiredBooking));
        when(seatHoldRepository.findByBookingIdAndStatusIn(any(), any()))
                .thenReturn(List.of(hold));
        when(userVoucherRepository.findByBookingId(100L)).thenReturn(Optional.empty());
        when(seatHoldRepository.findByStatusAndHoldExpiresAtBefore(any(), any())).thenReturn(List.of());

        int released = service.releaseExpired();

        assertThat(released).isEqualTo(1);
        assertThat(expiredBooking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(hold.getStatus()).isEqualTo(SeatHoldStatus.RELEASED);
        verify(seatInventoryService).release(900L);
    }

    private Booking booking(Long id, Long userId) {
        User u = new User();
        u.setId(userId);

        Booking b = new Booking();
        b.setId(id);
        b.setBookingCode("X7K2MP");
        b.setUser(u);
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        b.setPaymentExpiresAt(LocalDateTime.now().plusMinutes(15));
        return b;
    }

    private Passenger passenger(Long id, Booking booking, PassengerType type) {
        Passenger p = new Passenger();
        p.setId(id);
        p.setBooking(booking);
        p.setFullName("Nguyễn Văn An");
        p.setPassengerType(type);
        return p;
    }

    private Flight flight(Long id, String number) {
        Flight f = new Flight();
        f.setId(id);
        f.setFlightNumber(number);
        return f;
    }

    private BookingSegment segment(Booking booking, Flight flight, SeatClass cabin) {
        FareClass fc = new FareClass();
        fc.setId(2L);
        fc.setFareClassCode("VN_ECON_FLEX");
        fc.setSeatClass(cabin);

        BookingSegment s = new BookingSegment();
        s.setBooking(booking);
        s.setFlight(flight);
        s.setFareClass(fc);
        return s;
    }

    private FlightSeat flightSeat(Long id, Flight flight, String code, SeatClass cabin, BigDecimal surcharge) {
        SeatType st = new SeatType();
        st.setId(1L);
        st.setSeatTypeCode("ECO_FRONT");
        st.setSeatTypeName("Ghế hàng đầu");
        st.setSeatClass(cabin);

        AircraftSeat as = new AircraftSeat();
        as.setId(10L);
        as.setSeatType(st);

        FlightSeat fs = new FlightSeat();
        fs.setId(id);
        fs.setFlight(flight);
        fs.setAircraftSeat(as);
        fs.setSeatCode(code);
        fs.setStatus(FlightSeatStatus.AVAILABLE);
        fs.setSeatSurcharge(surcharge);
        return fs;
    }
}
