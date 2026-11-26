package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.response.eticket.ETicketResponse;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.entity.Airport;
import com.example.domesticflightbookingwebbackend.entity.Booking;
import com.example.domesticflightbookingwebbackend.entity.BookingSegment;
import com.example.domesticflightbookingwebbackend.entity.City;
import com.example.domesticflightbookingwebbackend.entity.ETicket;
import com.example.domesticflightbookingwebbackend.entity.ETicketStatus;
import com.example.domesticflightbookingwebbackend.entity.FareClass;
import com.example.domesticflightbookingwebbackend.entity.Flight;
import com.example.domesticflightbookingwebbackend.entity.FlightSeat;
import com.example.domesticflightbookingwebbackend.entity.Passenger;
import com.example.domesticflightbookingwebbackend.entity.PassengerGender;
import com.example.domesticflightbookingwebbackend.entity.PassengerType;
import com.example.domesticflightbookingwebbackend.entity.SeatClass;
import com.example.domesticflightbookingwebbackend.entity.SeatHold;
import com.example.domesticflightbookingwebbackend.entity.SeatHoldStatus;
import com.example.domesticflightbookingwebbackend.entity.User;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.repository.BookingRepository;
import com.example.domesticflightbookingwebbackend.repository.BookingSegmentRepository;
import com.example.domesticflightbookingwebbackend.repository.ETicketRepository;
import com.example.domesticflightbookingwebbackend.repository.PassengerRepository;
import com.example.domesticflightbookingwebbackend.repository.SeatHoldRepository;
import com.example.domesticflightbookingwebbackend.service.mail.MailService;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ETicketServiceTest {

    private ETicketRepository eTicketRepository;
    private BookingRepository bookingRepository;
    private BookingSegmentRepository bookingSegmentRepository;
    private PassengerRepository passengerRepository;
    private SeatHoldRepository seatHoldRepository;
    private MailService mailService;
    private ETicketService service;

    @BeforeEach
    void setUp() {
        eTicketRepository = mock(ETicketRepository.class);
        bookingRepository = mock(BookingRepository.class);
        bookingSegmentRepository = mock(BookingSegmentRepository.class);
        passengerRepository = mock(PassengerRepository.class);
        seatHoldRepository = mock(SeatHoldRepository.class);
        mailService = mock(MailService.class);

        service = new ETicketService(
                eTicketRepository,
                bookingRepository,
                bookingSegmentRepository,
                passengerRepository,
                seatHoldRepository,
                mailService);
    }

    @Test
    void issueForBooking_phatHanhVeChoCaNguoiLonVaEmBeKhongGhe_kemGuiEmail() {
        Booking booking = booking(1049L, 1L);
        Flight flight = flight(55L, "VN165");
        BookingSegment seg = segment(500L, booking, flight);

        Passenger adult = passenger(200L, booking, "Nguyễn Văn An", PassengerType.ADULT);
        Passenger infant = passenger(201L, booking, "Nguyễn Bé Bi", PassengerType.INFANT);

        FlightSeat seat12A = new FlightSeat();
        seat12A.setId(900L);
        seat12A.setFlight(flight);
        seat12A.setSeatCode("12A");

        SeatHold hold = new SeatHold();
        hold.setBooking(booking);
        hold.setPassenger(adult);
        hold.setFlightSeat(seat12A);
        hold.setStatus(SeatHoldStatus.CONFIRMED);

        when(bookingSegmentRepository.findByBookingIdOrderBySegmentOrderAsc(1049L)).thenReturn(List.of(seg));
        when(passengerRepository.findByBookingIdOrderByIdAsc(1049L)).thenReturn(List.of(adult, infant));
        when(seatHoldRepository.findByBookingIdAndStatusIn(any(), any())).thenReturn(List.of(hold));
        when(eTicketRepository.findByBookingIdOrderByIdAsc(1049L)).thenReturn(List.of());
        when(eTicketRepository.existsByBookingSegmentIdAndPassengerId(any(), any())).thenReturn(false);
        when(eTicketRepository.existsByTicketCode(any())).thenReturn(false);
        when(eTicketRepository.save(any(ETicket.class))).thenAnswer(inv -> {
            ETicket t = inv.getArgument(0);
            t.setId(t.getPassenger().getId() + 1000L);
            return t;
        });

        List<ETicket> issued = service.issueForBooking(booking);

        assertThat(issued).hasSize(2);
        // Người lớn có ghế 12A và mã vé bắt đầu bằng 738- (Vietnam Airlines)
        ETicket adultTicket = issued.get(0);
        assertThat(adultTicket.getTicketCode()).startsWith("738-");
        assertThat(adultTicket.getFlightSeat()).isNotNull();
        assertThat(adultTicket.getFlightSeat().getSeatCode()).isEqualTo("12A");

        // Em bé vẫn có vé điện tử nhưng flightSeat = null
        ETicket infantTicket = issued.get(1);
        assertThat(infantTicket.getTicketCode()).startsWith("738-");
        assertThat(infantTicket.getFlightSeat()).isNull();

        verify(mailService).sendBookingConfirmation(
                eq("an.nguyen@example.com"),
                eq("Nguyễn Văn An"),
                eq("X7K2MP"),
                any(),
                anyList());
    }

    @Test
    void listByBooking_khachHangKhacTruyCap_nem1003ChongIdor() {
        Booking booking = booking(1049L, 2L); // Chủ sở hữu là userId=2
        when(bookingRepository.findWithUserById(1049L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.listByBooking(1L, false, 1049L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void listByBooking_staffHoacChuSoHuu_traDanhSachVe() {
        Booking booking = booking(1049L, 1L);
        Flight flight = flight(55L, "VN165");
        BookingSegment seg = segment(500L, booking, flight);
        Passenger adult = passenger(200L, booking, "Nguyễn Văn An", PassengerType.ADULT);

        ETicket t = new ETicket();
        t.setId(700L);
        t.setBooking(booking);
        t.setBookingSegment(seg);
        t.setPassenger(adult);
        t.setTicketCode("738-4521001");

        when(bookingRepository.findWithUserById(1049L)).thenReturn(Optional.of(booking));
        when(eTicketRepository.findByBookingIdOrderByIdAsc(1049L)).thenReturn(List.of(t));

        List<ETicketResponse> res = service.listByBooking(1L, false, 1049L);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).pnrCode()).isEqualTo("X7K2MP");
        assertThat(res.get(0).ticketCode()).isEqualTo("738-4521001");
    }

    @Test
    void verifyByTicketCode_hopLe_traThongTinXacThucAnToan() {
        Booking booking = booking(1049L, 1L);
        Flight flight = flight(55L, "VN165");
        BookingSegment seg = segment(500L, booking, flight);
        Passenger adult = passenger(200L, booking, "Nguyễn Văn An", PassengerType.ADULT);

        ETicket t = new ETicket();
        t.setId(700L);
        t.setBooking(booking);
        t.setBookingSegment(seg);
        t.setPassenger(adult);
        t.setTicketCode("738-4521001");
        t.setStatus(ETicketStatus.ISSUED);

        when(eTicketRepository.findByTicketCodeIgnoreCase("738-4521001")).thenReturn(Optional.of(t));

        var res = service.verifyByTicketCode(" 738-4521001 ");

        assertThat(res.ticketCode()).isEqualTo("738-4521001");
        assertThat(res.pnrCode()).isEqualTo("X7K2MP");
        assertThat(res.status()).isEqualTo("ISSUED");
        assertThat(res.flightNumber()).isEqualTo("VN165");
        assertThat(res.passengerName()).isEqualTo("Nguyễn Văn An");
    }

    @Test
    void verifyByTicketCode_khongTonTai_nem404() {
        when(eTicketRepository.findByTicketCodeIgnoreCase("999-0000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyByTicketCode("999-0000000"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Booking booking(Long id, Long userId) {
        User u = new User();
        u.setId(userId);
        u.setFullName("Nguyễn Văn An");
        u.setEmail("an.nguyen@example.com");

        Booking b = new Booking();
        b.setId(id);
        b.setBookingCode("X7K2MP");
        b.setUser(u);
        b.setTotalAmount(new BigDecimal("2959000.00"));
        return b;
    }

    private Flight flight(Long id, String number) {
        Airline a = new Airline();
        a.setId(1L);
        a.setAirlineCode("VN");
        a.setAirlineName("Vietnam Airlines");

        City hanCity = new City();
        hanCity.setCityName("Hà Nội");
        Airport han = new Airport();
        han.setAirportCode("HAN");
        han.setAirportName("Nội Bài");
        han.setCity(hanCity);

        City dadCity = new City();
        dadCity.setCityName("Đà Nẵng");
        Airport dad = new Airport();
        dad.setAirportCode("DAD");
        dad.setAirportName("Đà Nẵng");
        dad.setCity(dadCity);

        Flight f = new Flight();
        f.setId(id);
        f.setFlightNumber(number);
        f.setAirline(a);
        f.setDepartureAirport(han);
        f.setArrivalAirport(dad);
        f.setScheduledDeparture(LocalDateTime.now().plusDays(3));
        f.setScheduledArrival(LocalDateTime.now().plusDays(3).plusMinutes(80));
        return f;
    }

    private BookingSegment segment(Long id, Booking booking, Flight flight) {
        FareClass fc = new FareClass();
        fc.setId(2L);
        fc.setFareClassCode("VN_ECON_FLEX");
        fc.setFareClassName("Phổ thông linh hoạt");
        fc.setSeatClass(SeatClass.ECONOMY);

        BookingSegment s = new BookingSegment();
        s.setId(id);
        s.setBooking(booking);
        s.setFlight(flight);
        s.setFareClass(fc);
        s.setSegmentOrder(1);
        return s;
    }

    private Passenger passenger(Long id, Booking booking, String name, PassengerType type) {
        Passenger p = new Passenger();
        p.setId(id);
        p.setBooking(booking);
        p.setFullName(name);
        p.setPassengerType(type);
        p.setGender(PassengerGender.MALE);
        p.setBirthDate(LocalDate.of(1990, 3, 12));
        return p;
    }
}
