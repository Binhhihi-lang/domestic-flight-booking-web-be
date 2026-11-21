package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.config.PricingProperties;
import com.example.domesticflightbookingwebbackend.dto.request.flight.FlightFareRequest;
import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightFareResponse;
import com.example.domesticflightbookingwebbackend.dto.response.flight.PricedFareResponse;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.entity.FareClass;
import com.example.domesticflightbookingwebbackend.entity.FareRule;
import com.example.domesticflightbookingwebbackend.entity.Flight;
import com.example.domesticflightbookingwebbackend.entity.FlightFare;
import com.example.domesticflightbookingwebbackend.entity.SeatClass;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.mapper.FlightFareMapper;
import com.example.domesticflightbookingwebbackend.repository.FareClassRepository;
import com.example.domesticflightbookingwebbackend.repository.FareRuleRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightFareRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlightFareServiceTest {

    private FlightFareRepository flightFareRepository;
    private FlightRepository flightRepository;
    private FareClassRepository fareClassRepository;
    private FareRuleRepository fareRuleRepository;
    private FlightSeatRepository flightSeatRepository;
    private FlightFareService service;

    @BeforeEach
    void setUp() {
        flightFareRepository = mock(FlightFareRepository.class);
        flightRepository = mock(FlightRepository.class);
        fareClassRepository = mock(FareClassRepository.class);
        fareRuleRepository = mock(FareRuleRepository.class);
        flightSeatRepository = mock(FlightSeatRepository.class);

        PricingProperties pricingProperties = new PricingProperties();
        pricingProperties.setChildFarePercent(75);
        pricingProperties.setInfantFarePercent(10);

        service = new FlightFareService(
                flightFareRepository,
                flightRepository,
                fareClassRepository,
                fareRuleRepository,
                flightSeatRepository,
                pricingProperties,
                new FlightFareMapper());
    }

    @Test
    void create_tinhDungGiaAdultChild75Infant10() {
        Flight flight = flight(55L, "VN165", 1L, "VN");
        FareClass fc = fareClass(2L, "VN_ECON_SAVER", SeatClass.ECONOMY, 1L, "VN");

        when(flightRepository.findById(55L)).thenReturn(Optional.of(flight));
        when(fareClassRepository.findById(2L)).thenReturn(Optional.of(fc));
        when(flightFareRepository.existsOverlappingFare(any(), any(), any(), any(), any())).thenReturn(false);
        when(flightFareRepository.save(any(FlightFare.class))).thenAnswer(inv -> {
            FlightFare ff = inv.getArgument(0);
            ff.setId(100L);
            return ff;
        });

        FlightFareResponse res = service.create(new FlightFareRequest(
                55L, 2L,
                new BigDecimal("760000"),
                new BigDecimal("130000"),
                "VND",
                LocalDateTime.of(2026, 11, 1, 0, 0),
                null));

        assertThat(res.id()).isEqualTo(100L);
        assertThat(res.adultPrice()).isEqualByComparingTo("890000.00");
        assertThat(res.childPrice()).isEqualByComparingTo("667500.00");
        assertThat(res.infantPrice()).isEqualByComparingTo("89000.00");
    }

    @Test
    void create_trungKhoangHieuLuc_nem1006() {
        Flight flight = flight(55L, "VN165", 1L, "VN");
        FareClass fc = fareClass(2L, "VN_ECON_SAVER", SeatClass.ECONOMY, 1L, "VN");

        when(flightRepository.findById(55L)).thenReturn(Optional.of(flight));
        when(fareClassRepository.findById(2L)).thenReturn(Optional.of(fc));
        when(flightFareRepository.existsOverlappingFare(any(), any(), any(), any(), any())).thenReturn(true);

        FlightFareRequest req = new FlightFareRequest(
                55L, 2L,
                new BigDecimal("1000000"),
                new BigDecimal("200000"),
                "VND",
                LocalDateTime.of(2026, 11, 1, 0, 0),
                LocalDateTime.of(2026, 12, 1, 0, 0));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chồng lấn");
    }

    @Test
    void create_effectiveToTruocEffectiveFrom_nem1006() {
        Flight flight = flight(55L, "VN165", 1L, "VN");
        FareClass fc = fareClass(2L, "VN_ECON_SAVER", SeatClass.ECONOMY, 1L, "VN");

        when(flightRepository.findById(55L)).thenReturn(Optional.of(flight));
        when(fareClassRepository.findById(2L)).thenReturn(Optional.of(fc));

        FlightFareRequest req = new FlightFareRequest(
                55L, 2L,
                new BigDecimal("1000000"),
                new BigDecimal("200000"),
                "VND",
                LocalDateTime.of(2026, 12, 1, 0, 0),
                LocalDateTime.of(2026, 11, 1, 0, 0));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("effective_to");
    }

    @Test
    void create_hangVeKhacHangChuyenBay_nem1006() {
        Flight flight = flight(55L, "VN165", 1L, "VN");
        FareClass vjFare = fareClass(9L, "VJ_ECON_SAVER", SeatClass.ECONOMY, 2L, "VJ");

        when(flightRepository.findById(55L)).thenReturn(Optional.of(flight));
        when(fareClassRepository.findById(9L)).thenReturn(Optional.of(vjFare));

        FlightFareRequest req = new FlightFareRequest(
                55L, 9L,
                new BigDecimal("800000"),
                new BigDecimal("150000"),
                "VND",
                LocalDateTime.of(2026, 11, 1, 0, 0),
                null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không thuộc hãng");
    }

    @Test
    void getPricedFares_traVeGiaKemSoGheTrongVaChinhSachHoan() {
        Flight flight = flight(55L, "VN165", 1L, "VN");
        FareClass fc = fareClass(2L, "VN_ECON_FLEX", SeatClass.ECONOMY, 1L, "VN");
        FlightFare ff = new FlightFare();
        ff.setId(101L);
        ff.setFlight(flight);
        ff.setFareClass(fc);
        ff.setBaseFare(new BigDecimal("1000000"));
        ff.setTaxFee(new BigDecimal("200000"));
        ff.setCurrency("VND");

        FareRule rule = new FareRule();
        rule.setId(5L);
        rule.setFareClass(fc);
        rule.setVersion(1);
        rule.setIsRefundable(true);
        rule.setRefundMinHoursBeforeDeparture(24);
        rule.setRefundPercent(new BigDecimal("90"));
        rule.setDescription("Hoàn 90% trước 24h");

        FlightSeatRepository.FlightCabinAvailability avail = new FlightSeatRepository.FlightCabinAvailability() {
            @Override
            public Long getFlightId() {
                return 55L;
            }

            @Override
            public SeatClass getSeatClass() {
                return SeatClass.ECONOMY;
            }

            @Override
            public Long getAvailableCount() {
                return 18L;
            }
        };

        when(flightFareRepository.findActiveFaresByFlightIds(any(), any())).thenReturn(List.of(ff));
        when(flightSeatRepository.countAvailableSeatsByFlightIdsAndCabin(any())).thenReturn(List.of(avail));
        when(fareRuleRepository.findActiveRulesByFareClassIds(any(), any())).thenReturn(List.of(rule));

        List<PricedFareResponse> res = service.getPricedFares(55L);

        assertThat(res).hasSize(1);
        PricedFareResponse item = res.get(0);
        assertThat(item.adultPrice()).isEqualByComparingTo("1200000.00");
        assertThat(item.childPrice()).isEqualByComparingTo("900000.00");
        assertThat(item.infantPrice()).isEqualByComparingTo("120000.00");
        assertThat(item.seatsAvailable()).isEqualTo(18L);
        assertThat(item.isRefundable()).isTrue();
        assertThat(item.refundPercent()).isEqualByComparingTo("90");
    }

    private Flight flight(Long id, String number, Long airlineId, String airlineCode) {
        Airline airline = new Airline();
        airline.setId(airlineId);
        airline.setAirlineCode(airlineCode);
        airline.setAirlineName(airlineCode);
        airline.setIsDeleted(false);

        Flight f = new Flight();
        f.setId(id);
        f.setFlightNumber(number);
        f.setAirline(airline);
        f.setIsDeleted(false);
        return f;
    }

    private FareClass fareClass(Long id, String code, SeatClass seatClass, Long airlineId, String airlineCode) {
        Airline airline = new Airline();
        airline.setId(airlineId);
        airline.setAirlineCode(airlineCode);
        airline.setAirlineName(airlineCode);
        airline.setIsDeleted(false);

        FareClass fc = new FareClass();
        fc.setId(id);
        fc.setAirline(airline);
        fc.setFareClassCode(code);
        fc.setFareClassName(code);
        fc.setSeatClass(seatClass);
        fc.setIsActive(true);
        fc.setIsDeleted(false);
        return fc;
    }
}
