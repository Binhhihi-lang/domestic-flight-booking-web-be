package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.request.flight.SeatInventoryRequest;
import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightSeatMapResponse;
import com.example.domesticflightbookingwebbackend.entity.Aircraft;
import com.example.domesticflightbookingwebbackend.entity.AircraftSeat;
import com.example.domesticflightbookingwebbackend.entity.Flight;
import com.example.domesticflightbookingwebbackend.entity.FlightSeat;
import com.example.domesticflightbookingwebbackend.entity.FlightSeatStatus;
import com.example.domesticflightbookingwebbackend.entity.SeatClass;
import com.example.domesticflightbookingwebbackend.entity.SeatType;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.mapper.SeatTypeMapper;
import com.example.domesticflightbookingwebbackend.repository.AircraftSeatRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeatInventoryServiceTest {

    private FlightSeatRepository flightSeatRepository;
    private FlightRepository flightRepository;
    private AircraftSeatRepository aircraftSeatRepository;
    private SeatInventoryService service;

    @BeforeEach
    void setUp() {
        flightSeatRepository = mock(FlightSeatRepository.class);
        flightRepository = mock(FlightRepository.class);
        aircraftSeatRepository = mock(AircraftSeatRepository.class);
        service = new SeatInventoryService(
                flightSeatRepository,
                flightRepository,
                aircraftSeatRepository,
                new SeatTypeMapper());
    }

    @Test
    void generateInventory_sinhDuGheTuAircraftSeats() {
        Aircraft aircraft = aircraft(3L, "VN-A321-05", 2);
        Flight flight = flight(55L, "VN165", aircraft);

        SeatType ecoFront = seatType(1L, "ECO_FRONT", SeatClass.ECONOMY, new BigDecimal("70000"));
        SeatType ecoStd = seatType(2L, "ECO", SeatClass.ECONOMY, BigDecimal.ZERO);

        AircraftSeat s1 = aircraftSeat(11L, aircraft, ecoFront, 1, "A");
        AircraftSeat s2 = aircraftSeat(12L, aircraft, ecoStd, 1, "B");

        when(flightRepository.findWithDetailsById(55L)).thenReturn(Optional.of(flight));
        when(flightSeatRepository.findByFlightId(55L)).thenReturn(List.of());
        when(aircraftSeatRepository.findByAircraftId(3L)).thenReturn(List.of(s1, s2));

        int count = service.generateInventory(55L);

        assertThat(count).isEqualTo(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FlightSeat>> captor = ArgumentCaptor.forClass(List.class);
        verify(flightSeatRepository).saveAll(captor.capture());

        List<FlightSeat> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getSeatCode()).isEqualTo("1A");
        assertThat(saved.get(0).getStatus()).isEqualTo(FlightSeatStatus.AVAILABLE);
        assertThat(saved.get(0).getSeatSurcharge()).isEqualByComparingTo("70000.00");
        assertThat(saved.get(1).getSeatCode()).isEqualTo("1B");
        assertThat(saved.get(1).getSeatSurcharge()).isEqualByComparingTo("0.00");
    }

    @Test
    void generateInventory_mayBayChuaDuSoDoGhe_nem1006() {
        Aircraft aircraft = aircraft(3L, "VN-A321-05", 180);
        Flight flight = flight(55L, "VN165", aircraft);

        when(flightRepository.findWithDetailsById(55L)).thenReturn(Optional.of(flight));
        when(flightSeatRepository.findByFlightId(55L)).thenReturn(List.of());
        when(aircraftSeatRepository.findByAircraftId(3L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.generateInventory(55L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa được cấu hình đủ sơ đồ ghế");
    }

    @Test
    void blockAndUnblock_doiTrangThaiThanhCong() {
        FlightSeat seat = new FlightSeat();
        seat.setId(500L);
        seat.setSeatCode("12A");
        seat.setStatus(FlightSeatStatus.AVAILABLE);

        when(flightSeatRepository.findById(500L)).thenReturn(Optional.of(seat));

        service.block(500L);
        assertThat(seat.getStatus()).isEqualTo(FlightSeatStatus.BLOCKED);

        service.unblock(500L);
        assertThat(seat.getStatus()).isEqualTo(FlightSeatStatus.AVAILABLE);
    }

    @Test
    void block_gheDangBooked_nem1006() {
        FlightSeat seat = new FlightSeat();
        seat.setId(501L);
        seat.setSeatCode("12B");
        seat.setStatus(FlightSeatStatus.BOOKED);

        when(flightSeatRepository.findById(501L)).thenReturn(Optional.of(seat));

        assertThatThrownBy(() -> service.block(501L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("BOOKED");
    }

    @Test
    void saveInventory_capNhatSurchargeVaStatus() {
        Aircraft aircraft = aircraft(3L, "VN-A321-05", 1);
        Flight flight = flight(55L, "VN165", aircraft);
        SeatType eco = seatType(1L, "ECO", SeatClass.ECONOMY, BigDecimal.ZERO);
        AircraftSeat as = aircraftSeat(11L, aircraft, eco, 1, "A");

        FlightSeat fs = new FlightSeat();
        fs.setId(900L);
        fs.setFlight(flight);
        fs.setAircraftSeat(as);
        fs.setSeatCode("1A");
        fs.setStatus(FlightSeatStatus.AVAILABLE);
        fs.setSeatSurcharge(BigDecimal.ZERO);

        when(flightRepository.findWithDetailsById(55L)).thenReturn(Optional.of(flight));
        when(flightSeatRepository.findByFlightId(55L)).thenReturn(List.of(fs));

        SeatInventoryRequest req = new SeatInventoryRequest(List.of(
                new SeatInventoryRequest.SeatOverrideItem(
                        900L, null, "1A", "BLOCKED", new BigDecimal("150000"))
        ));

        FlightSeatMapResponse res = service.saveInventory(55L, req);

        assertThat(res.blockedSeats()).isEqualTo(1);
        assertThat(res.seats().get(0).status()).isEqualTo("BLOCKED");
        assertThat(res.seats().get(0).seatSurcharge()).isEqualByComparingTo("150000.00");
        verify(flightSeatRepository).saveAll(anyList());
    }

    @Test
    void hold_goiUpdateNguyenTu() {
        when(flightSeatRepository.holdSeatAtomically(900L)).thenReturn(1);
        when(flightSeatRepository.holdSeatAtomically(901L)).thenReturn(0);

        assertThat(service.hold(900L)).isTrue();
        assertThat(service.hold(901L)).isFalse();
    }

    private Aircraft aircraft(Long id, String code, int capacity) {
        Aircraft a = new Aircraft();
        a.setId(id);
        a.setAircraftCode(code);
        a.setModel("Airbus A321");
        a.setSeatCapacity(capacity);
        a.setAisleAfter("C");
        a.setIsActive(true);
        a.setIsDeleted(false);
        return a;
    }

    private Flight flight(Long id, String number, Aircraft aircraft) {
        Flight f = new Flight();
        f.setId(id);
        f.setFlightNumber(number);
        f.setAircraft(aircraft);
        f.setIsDeleted(false);
        return f;
    }

    private SeatType seatType(Long id, String code, SeatClass seatClass, BigDecimal surcharge) {
        SeatType st = new SeatType();
        st.setId(id);
        st.setSeatTypeCode(code);
        st.setSeatTypeName(code);
        st.setSeatClass(seatClass);
        st.setSurcharge(surcharge);
        st.setIsActive(true);
        st.setIsDeleted(false);
        return st;
    }

    private AircraftSeat aircraftSeat(Long id, Aircraft aircraft, SeatType seatType, int row, String col) {
        AircraftSeat as = new AircraftSeat();
        as.setId(id);
        as.setAircraft(aircraft);
        as.setSeatType(seatType);
        as.setSeatRow(row);
        as.setSeatColumn(col);
        return as;
    }
}
