package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.request.masterdata.SeatMapRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.SeatMapResponse;
import com.example.domesticflightbookingwebbackend.entity.Aircraft;
import com.example.domesticflightbookingwebbackend.entity.SeatClass;
import com.example.domesticflightbookingwebbackend.entity.SeatType;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.mapper.SeatTypeMapper;
import com.example.domesticflightbookingwebbackend.repository.AircraftRepository;
import com.example.domesticflightbookingwebbackend.repository.AircraftSeatRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightSeatRepository;
import com.example.domesticflightbookingwebbackend.repository.SeatTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Test 4 ràng buộc của sơ đồ ghế (mục 2.6 API v0.2, DB v0.4 mục 5.7). */
class SeatMapServiceTest {

    private AircraftRepository aircraftRepository;
    private AircraftSeatRepository aircraftSeatRepository;
    private SeatTypeRepository seatTypeRepository;
    private FlightSeatRepository flightSeatRepository;
    private SeatMapService service;

    @BeforeEach
    void setUp() {
        aircraftRepository = mock(AircraftRepository.class);
        aircraftSeatRepository = mock(AircraftSeatRepository.class);
        seatTypeRepository = mock(SeatTypeRepository.class);
        flightSeatRepository = mock(FlightSeatRepository.class);
        service = new SeatMapService(aircraftRepository, aircraftSeatRepository,
                seatTypeRepository, flightSeatRepository, new SeatTypeMapper());
    }

    private Aircraft aircraft(int capacity) {
        Aircraft a = new Aircraft();
        a.setId(1L);
        a.setSeatCapacity(capacity);
        a.setIsDeleted(false);
        return a;
    }

    private SeatType seatType(boolean active) {
        SeatType st = new SeatType();
        st.setId(3L);
        st.setSeatTypeCode("ECO");
        st.setSeatTypeName("Phổ thông");
        st.setSeatClass(SeatClass.ECONOMY);
        st.setIsActive(active);
        st.setIsDeleted(false);
        return st;
    }

    private SeatMapRequest.SeatItem item(Integer row, String col, Long typeId) {
        return new SeatMapRequest.SeatItem(row, col, typeId);
    }

    @Test
    void tongGheKhacCapacity_nem1006() {
        when(aircraftRepository.findById(1L)).thenReturn(Optional.of(aircraft(4)));

        SeatMapRequest req = new SeatMapRequest(List.of(item(1, "A", 3L)));   // 1 ghế ≠ 4

        assertThatThrownBy(() -> service.saveSeatMap(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sức chứa");
        verify(aircraftSeatRepository, never()).saveAll(any());
    }

    @Test
    void trungViTriGhe_nem1006() {
        when(aircraftRepository.findById(1L)).thenReturn(Optional.of(aircraft(2)));

        SeatMapRequest req = new SeatMapRequest(List.of(
                item(1, "A", 3L), item(1, "A", 3L)));   // trùng (1,A)

        assertThatThrownBy(() -> service.saveSeatMap(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Trùng vị trí");
    }

    @Test
    void mayBayDaKhaiThac_nem1006() {
        when(aircraftRepository.findById(1L)).thenReturn(Optional.of(aircraft(1)));
        when(flightSeatRepository.existsByFlightAircraftId(1L)).thenReturn(true);

        SeatMapRequest req = new SeatMapRequest(List.of(item(1, "A", 3L)));

        assertThatThrownBy(() -> service.saveSeatMap(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được dùng");
    }

    @Test
    void seatTypeKhongTonTai_nem1004() {
        when(aircraftRepository.findById(1L)).thenReturn(Optional.of(aircraft(1)));
        when(flightSeatRepository.existsByFlightAircraftId(1L)).thenReturn(false);
        when(seatTypeRepository.findById(99L)).thenReturn(Optional.empty());

        SeatMapRequest req = new SeatMapRequest(List.of(item(1, "A", 99L)));

        assertThatThrownBy(() -> service.saveSeatMap(1L, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void seatTypeDaTat_nem1006() {
        when(aircraftRepository.findById(1L)).thenReturn(Optional.of(aircraft(1)));
        when(flightSeatRepository.existsByFlightAircraftId(1L)).thenReturn(false);
        when(seatTypeRepository.findById(3L)).thenReturn(Optional.of(seatType(false)));

        SeatMapRequest req = new SeatMapRequest(List.of(item(1, "A", 3L)));

        assertThatThrownBy(() -> service.saveSeatMap(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã tắt");
    }

    @Test
    void hopLe_traConfiguredSeatsDung() {
        when(aircraftRepository.findById(1L)).thenReturn(Optional.of(aircraft(2)));
        when(flightSeatRepository.existsByFlightAircraftId(1L)).thenReturn(false);
        when(seatTypeRepository.findById(3L)).thenReturn(Optional.of(seatType(true)));
        when(aircraftSeatRepository.findByAircraftId(1L)).thenReturn(List.of());
        when(aircraftSeatRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        SeatMapResponse res = service.saveSeatMap(1L, new SeatMapRequest(List.of(
                item(1, "A", 3L), item(1, "b", 3L))));

        assertThat(res.configuredSeats()).isEqualTo(2);
        assertThat(res.seatCapacity()).isEqualTo(2);
        assertThat(res.seats()).extracting(SeatMapResponse.SeatEntry::seatColumn)
                .containsExactly("A", "B");   // đã upper-case
    }

    @Test
    void thieuTruongBatBuoc_nem1001() {
        when(aircraftRepository.findById(1L)).thenReturn(Optional.of(aircraft(1)));

        SeatMapRequest req = new SeatMapRequest(List.of(item(null, "A", 3L)));

        assertThatThrownBy(() -> service.saveSeatMap(1L, req))
                .isInstanceOf(com.example.domesticflightbookingwebbackend.exception.AppException.class);
    }
}
