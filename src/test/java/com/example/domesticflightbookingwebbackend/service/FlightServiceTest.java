package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.request.flight.FlightRequest;
import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightResponse;
import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightSearchResult;
import com.example.domesticflightbookingwebbackend.dto.response.flight.PricedFareResponse;
import com.example.domesticflightbookingwebbackend.entity.Aircraft;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.entity.Airport;
import com.example.domesticflightbookingwebbackend.entity.City;
import com.example.domesticflightbookingwebbackend.entity.Flight;
import com.example.domesticflightbookingwebbackend.entity.FlightOperationStatus;
import com.example.domesticflightbookingwebbackend.entity.FlightSeatStatus;
import com.example.domesticflightbookingwebbackend.entity.SalesStatus;
import com.example.domesticflightbookingwebbackend.entity.TripType;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.mapper.AircraftMapper;
import com.example.domesticflightbookingwebbackend.mapper.AirlineMapper;
import com.example.domesticflightbookingwebbackend.mapper.AirportMapper;
import com.example.domesticflightbookingwebbackend.mapper.CityMapper;
import com.example.domesticflightbookingwebbackend.mapper.FlightMapper;
import com.example.domesticflightbookingwebbackend.repository.AircraftRepository;
import com.example.domesticflightbookingwebbackend.repository.AircraftSeatRepository;
import com.example.domesticflightbookingwebbackend.repository.AirlineRepository;
import com.example.domesticflightbookingwebbackend.repository.AirportRepository;
import com.example.domesticflightbookingwebbackend.repository.BookingSegmentRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightFareRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlightServiceTest {

    private FlightRepository flightRepository;
    private AirlineRepository airlineRepository;
    private AircraftRepository aircraftRepository;
    private AirportRepository airportRepository;
    private AircraftSeatRepository aircraftSeatRepository;
    private FlightFareRepository flightFareRepository;
    private FlightSeatRepository flightSeatRepository;
    private BookingSegmentRepository bookingSegmentRepository;
    private FlightFareService flightFareService;
    private SeatInventoryService seatInventoryService;
    private FlightService service;

    @BeforeEach
    void setUp() {
        flightRepository = mock(FlightRepository.class);
        airlineRepository = mock(AirlineRepository.class);
        aircraftRepository = mock(AircraftRepository.class);
        airportRepository = mock(AirportRepository.class);
        aircraftSeatRepository = mock(AircraftSeatRepository.class);
        flightFareRepository = mock(FlightFareRepository.class);
        flightSeatRepository = mock(FlightSeatRepository.class);
        bookingSegmentRepository = mock(BookingSegmentRepository.class);
        flightFareService = mock(FlightFareService.class);
        seatInventoryService = mock(SeatInventoryService.class);

        FlightMapper flightMapper = new FlightMapper(
                new AirlineMapper(),
                new AircraftMapper(),
                new AirportMapper(new CityMapper()));

        service = new FlightService(
                flightRepository,
                airlineRepository,
                aircraftRepository,
                airportRepository,
                aircraftSeatRepository,
                flightFareRepository,
                flightSeatRepository,
                bookingSegmentRepository,
                flightFareService,
                seatInventoryService,
                flightMapper);
    }

    @Test
    void create_hopLe_khoiTaoDraftVaClosed() {
        Airline vn = airline(1L, "VN");
        Aircraft aircraft = aircraft(3L, "VN-A321-05", 180);
        Airport han = airport(1L, "HAN");
        Airport dad = airport(3L, "DAD");

        when(airlineRepository.findById(1L)).thenReturn(Optional.of(vn));
        when(aircraftRepository.findById(3L)).thenReturn(Optional.of(aircraft));
        when(aircraftSeatRepository.countByAircraftId(3L)).thenReturn(180L);
        when(airportRepository.findById(1L)).thenReturn(Optional.of(han));
        when(airportRepository.findById(3L)).thenReturn(Optional.of(dad));
        when(flightRepository.existsByFlightNumberIgnoreCaseAndScheduledDepartureAndIsDeletedFalse(any(), any()))
                .thenReturn(false);
        when(flightRepository.save(any(Flight.class))).thenAnswer(inv -> {
            Flight f = inv.getArgument(0);
            f.setId(55L);
            return f;
        });

        FlightResponse res = service.create(new FlightRequest(
                "vn165", 1L, 3L, 1L, 3L,
                LocalDateTime.of(2026, 11, 25, 6, 0),
                LocalDateTime.of(2026, 11, 25, 7, 25),
                null, null, null));

        assertThat(res.id()).isEqualTo(55L);
        assertThat(res.flightNumber()).isEqualTo("VN165");
        assertThat(res.durationMinutes()).isEqualTo(85L);
        assertThat(res.operationStatus()).isEqualTo("DRAFT");
        assertThat(res.salesStatus()).isEqualTo("CLOSED");
    }

    @Test
    void create_sanBayDiTrungSanBayDen_nem1006() {
        FlightRequest req = new FlightRequest(
                "VN165", 1L, 3L, 1L, 1L,
                LocalDateTime.of(2026, 11, 25, 6, 0),
                LocalDateTime.of(2026, 11, 25, 7, 25),
                null, null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("trùng nhau");
    }

    @Test
    void create_mayBayChuaDuSoDoGhe_nem1006() {
        Airline vn = airline(1L, "VN");
        Aircraft aircraft = aircraft(3L, "VN-A321-05", 180);

        when(airlineRepository.findById(1L)).thenReturn(Optional.of(vn));
        when(aircraftRepository.findById(3L)).thenReturn(Optional.of(aircraft));
        when(aircraftSeatRepository.countByAircraftId(3L)).thenReturn(120L);

        FlightRequest req = new FlightRequest(
                "VN165", 1L, 3L, 1L, 3L,
                LocalDateTime.of(2026, 11, 25, 6, 0),
                LocalDateTime.of(2026, 11, 25, 7, 25),
                null, null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa có sơ đồ ghế đủ sức chứa");
    }

    @Test
    void create_trungSoHieuVaGioKhoiHanh_nem1005() {
        when(flightRepository.existsByFlightNumberIgnoreCaseAndScheduledDepartureAndIsDeletedFalse(any(), any()))
                .thenReturn(true);

        FlightRequest req = new FlightRequest(
                "VN165", 1L, 3L, 1L, 3L,
                LocalDateTime.of(2026, 11, 25, 6, 0),
                LocalDateTime.of(2026, 11, 25, 7, 25),
                null, null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void activate_coGiaVe_tuDongSinhTonGheVaChuyenSangOpenScheduled() {
        Flight flight = existingFlight(55L, "VN165");
        when(flightRepository.findWithDetailsById(55L)).thenReturn(Optional.of(flight));
        when(flightFareRepository.existsByFlightId(55L)).thenReturn(true);
        when(flightSeatRepository.existsByFlightId(55L)).thenReturn(false);
        when(flightRepository.save(any(Flight.class))).thenAnswer(inv -> inv.getArgument(0));

        FlightResponse res = service.activate(55L);

        verify(seatInventoryService).generateInventory(55L);
        assertThat(res.salesStatus()).isEqualTo("OPEN");
        assertThat(res.operationStatus()).isEqualTo("SCHEDULED");
    }

    @Test
    void activate_chuaCoGiaVe_nem1006() {
        Flight flight = existingFlight(55L, "VN165");
        when(flightRepository.findWithDetailsById(55L)).thenReturn(Optional.of(flight));
        when(flightFareRepository.existsByFlightId(55L)).thenReturn(false);

        assertThatThrownBy(() -> service.activate(55L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa được cấu hình giá vé");
    }

    @Test
    void update_doiMayBayKhiDaCoBooking_nem1006() {
        Flight flight = existingFlight(55L, "VN165");
        when(flightRepository.findWithDetailsById(55L)).thenReturn(Optional.of(flight));
        when(bookingSegmentRepository.existsByFlightId(55L)).thenReturn(true);

        FlightRequest req = new FlightRequest(
                "VN165", 1L, 99L, 1L, 3L,
                LocalDateTime.of(2026, 11, 25, 6, 0),
                LocalDateTime.of(2026, 11, 25, 7, 25),
                "SCHEDULED", "OPEN", null);

        assertThatThrownBy(() -> service.update(55L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đổi máy bay");
    }

    @Test
    void search_locChuyenDuGheVaTraGiaThapNhat() {
        Flight flight = existingFlight(55L, "VN165");
        flight.setSalesStatus(SalesStatus.OPEN);
        flight.setOperationStatus(FlightOperationStatus.SCHEDULED);

        PricedFareResponse saver = new PricedFareResponse(
                101L, 2L, "VN_ECON_SAVER", "Phổ thông tiết kiệm", "ECONOMY",
                new BigDecimal("760000"), new BigDecimal("130000"),
                new BigDecimal("890000"), new BigDecimal("667500"), new BigDecimal("89000"),
                "VND", 4L, false, 0, BigDecimal.ZERO, null);
        PricedFareResponse biz = new PricedFareResponse(
                102L, 7L, "VN_BIZ_FLEX", "Thương gia linh hoạt", "BUSINESS",
                new BigDecimal("2800000"), new BigDecimal("350000"),
                new BigDecimal("3150000"), new BigDecimal("2362500"), new BigDecimal("315000"),
                "VND", 1L, true, 24, new BigDecimal("100"), null);

        when(flightRepository.findOpenFlightsForSearch(any(), any(), any(), any()))
                .thenReturn(List.of(flight));
        when(flightFareService.getPricedFaresByFlightIds(List.of(55L)))
                .thenReturn(Map.of(55L, List.of(saver, biz)));

        List<FlightSearchResult> results = service.search(
                1L, 3L, LocalDate.now().plusDays(2), 2, TripType.ONE_WAY);

        assertThat(results).hasSize(1);
        FlightSearchResult item = results.get(0);
        // Hạng Thương gia chỉ còn 1 ghế (< 2 khách) nên bị lọc bỏ, chỉ còn Phổ thông tiết kiệm (4 ghế)
        assertThat(item.fares()).hasSize(1);
        assertThat(item.lowestAdultPrice()).isEqualByComparingTo("890000");
        assertThat(item.lowestFareClassCode()).isEqualTo("VN_ECON_SAVER");
    }

    @Test
    void search_ngayQuaKhu_nem1001() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        assertThatThrownBy(() -> service.search(1L, 3L, yesterday, 1, TripType.ONE_WAY))
                .isInstanceOf(com.example.domesticflightbookingwebbackend.exception.AppException.class)
                .hasMessageContaining("quá khứ");
    }

    @Test
    void search_ngayHomNay_chiTimTuThoiDiemHienTai() {
        LocalDate today = LocalDate.now();
        when(flightRepository.findOpenFlightsForSearch(any(), any(), any(), any()))
                .thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        service.search(1L, 3L, today, 1, TripType.ONE_WAY);

        org.mockito.ArgumentCaptor<LocalDateTime> startCaptor = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(flightRepository).findOpenFlightsForSearch(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(3L),
                startCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(today.plusDays(1).atStartOfDay()));
        assertThat(startCaptor.getValue()).isAfterOrEqualTo(before);
    }

    @Test
    void getPublicById_khongLoDanhSachHanhKhach() {
        Flight flight = existingFlight(55L, "VN165");
        flight.setSalesStatus(SalesStatus.OPEN);
        flight.setOperationStatus(FlightOperationStatus.SCHEDULED);
        when(flightRepository.findWithDetailsById(55L)).thenReturn(Optional.of(flight));
        when(flightSeatRepository.countByFlightId(55L)).thenReturn(180L);
        when(flightSeatRepository.countByFlightIdAndStatus(55L, FlightSeatStatus.AVAILABLE)).thenReturn(150L);
        when(flightFareService.getPricedFares(55L)).thenReturn(List.of());

        var detail = service.getPublicById(55L);
        assertThat(detail.passengers()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_napSeatCapacityVaAvailableSeatsBang1CauQueryKhongN1() {
        Flight f1 = existingFlight(55L, "VN165");
        Flight f2 = existingFlight(56L, "VN166"); // chưa sinh flight_seats → fallback aircraft.seatCapacity (180)

        FlightSeatRepository.FlightSeatSummary sum1 = mock(FlightSeatRepository.FlightSeatSummary.class);
        when(sum1.getFlightId()).thenReturn(55L);
        when(sum1.getTotalSeats()).thenReturn(180L);
        when(sum1.getAvailableSeats()).thenReturn(124L);

        when(flightRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(f1, f2)));
        when(flightSeatRepository.summarizeSeatsByFlightIds(List.of(55L, 56L)))
                .thenReturn(List.of(sum1));

        PageData<FlightResponse> page = service.list(new ListQuery(), null, null, null, null, null, null);

        verify(flightSeatRepository, times(1)).summarizeSeatsByFlightIds(List.of(55L, 56L));
        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).seatCapacity()).isEqualTo(180);
        assertThat(page.items().get(0).availableSeats()).isEqualTo(124);
        assertThat(page.items().get(1).seatCapacity()).isEqualTo(180);
        assertThat(page.items().get(1).availableSeats()).isEqualTo(180);
    }

    private Flight existingFlight(Long id, String number) {
        Flight f = new Flight();
        f.setId(id);
        f.setFlightNumber(number);
        f.setAirline(airline(1L, "VN"));
        f.setAircraft(aircraft(3L, "VN-A321-05", 180));
        f.setDepartureAirport(airport(1L, "HAN"));
        f.setArrivalAirport(airport(3L, "DAD"));
        f.setScheduledDeparture(LocalDateTime.of(2026, 11, 25, 6, 0));
        f.setScheduledArrival(LocalDateTime.of(2026, 11, 25, 7, 25));
        f.setOperationStatus(FlightOperationStatus.DRAFT);
        f.setSalesStatus(SalesStatus.CLOSED);
        f.setIsDeleted(false);
        return f;
    }

    private Airline airline(Long id, String code) {
        Airline a = new Airline();
        a.setId(id);
        a.setAirlineCode(code);
        a.setAirlineName("Vietnam Airlines");
        a.setIsActive(true);
        a.setIsDeleted(false);
        return a;
    }

    private Aircraft aircraft(Long id, String code, int capacity) {
        Aircraft a = new Aircraft();
        a.setId(id);
        a.setAircraftCode(code);
        a.setModel("Airbus A321neo");
        a.setSeatCapacity(capacity);
        a.setAisleAfter("C");
        a.setIsActive(true);
        a.setIsDeleted(false);
        return a;
    }

    private Airport airport(Long id, String code) {
        City c = new City();
        c.setId(id);
        c.setCityCode(code);
        c.setCityName(code);
        c.setCountry("Việt Nam");
        c.setIsActive(true);
        c.setIsDeleted(false);

        Airport ap = new Airport();
        ap.setId(id);
        ap.setAirportCode(code);
        ap.setAirportName("Sân bay " + code);
        ap.setCity(c);
        ap.setIsActive(true);
        ap.setIsDeleted(false);
        return ap;
    }
}
