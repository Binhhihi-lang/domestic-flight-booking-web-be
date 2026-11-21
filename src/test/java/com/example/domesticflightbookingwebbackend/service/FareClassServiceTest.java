package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.request.masterdata.FareClassRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.FareClassResponse;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.entity.FareClass;
import com.example.domesticflightbookingwebbackend.entity.SeatClass;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.mapper.FareClassMapper;
import com.example.domesticflightbookingwebbackend.repository.AirlineRepository;
import com.example.domesticflightbookingwebbackend.repository.FareClassRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightFareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FareClassServiceTest {

    private FareClassRepository fareClassRepository;
    private AirlineRepository airlineRepository;
    private FlightFareRepository flightFareRepository;
    private FareClassService service;

    @BeforeEach
    void setUp() {
        fareClassRepository = mock(FareClassRepository.class);
        airlineRepository = mock(AirlineRepository.class);
        flightFareRepository = mock(FlightFareRepository.class);
        service = new FareClassService(
                fareClassRepository, airlineRepository, flightFareRepository, new FareClassMapper());
    }

    @Test
    void create_tuDongGanTienToHang() {
        Airline vn = airline(1L, "VN", "Vietnam Airlines");
        when(airlineRepository.findById(1L)).thenReturn(Optional.of(vn));
        when(fareClassRepository.existsByFareClassCodeAndIsDeletedFalse("VN_ECON_SAVER")).thenReturn(false);
        when(fareClassRepository.save(any(FareClass.class))).thenAnswer(inv -> {
            FareClass fc = inv.getArgument(0);
            fc.setId(10L);
            return fc;
        });

        FareClassResponse res = service.create(new FareClassRequest(
                1L, "econ_saver", "Phổ thông tiết kiệm", "ECONOMY", "ACTIVE"));

        assertThat(res.fareClassCode()).isEqualTo("VN_ECON_SAVER");
        assertThat(res.airlineCode()).isEqualTo("VN");
        assertThat(res.status()).isEqualTo("ACTIVE");
    }

    @Test
    void create_daCoTienTo_khongNhanDoi() {
        Airline vj = airline(2L, "VJ", "VietJet Air");
        when(airlineRepository.findById(2L)).thenReturn(Optional.of(vj));
        when(fareClassRepository.existsByFareClassCodeAndIsDeletedFalse("VJ_BIZ_FLEX")).thenReturn(false);
        when(fareClassRepository.save(any(FareClass.class))).thenAnswer(inv -> inv.getArgument(0));

        FareClassResponse res = service.create(new FareClassRequest(
                2L, "VJ_BIZ_FLEX", "Thương gia linh hoạt", "BUSINESS", "ACTIVE"));

        assertThat(res.fareClassCode()).isEqualTo("VJ_BIZ_FLEX");
    }

    @Test
    void create_trungMa_nem1005() {
        when(airlineRepository.findById(1L)).thenReturn(Optional.of(airline(1L, "VN", "Vietnam Airlines")));
        when(fareClassRepository.existsByFareClassCodeAndIsDeletedFalse("VN_ECON_SAVER")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new FareClassRequest(
                1L, "ECON_SAVER", "Phổ thông", "ECONOMY", "ACTIVE")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void create_hangKhongTonTai_nem1004() {
        when(airlineRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new FareClassRequest(
                99L, "ECON_SAVER", "Phổ thông", "ECONOMY", "ACTIVE")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_khongDoiMaVaHang() {
        Airline vn = airline(1L, "VN", "Vietnam Airlines");
        FareClass existing = new FareClass();
        existing.setId(5L);
        existing.setAirline(vn);
        existing.setFareClassCode("VN_ECON_SAVER");
        existing.setFareClassName("Cũ");
        existing.setSeatClass(SeatClass.ECONOMY);

        when(fareClassRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(fareClassRepository.save(any(FareClass.class))).thenAnswer(inv -> inv.getArgument(0));

        FareClassResponse res = service.update(5L, new FareClassRequest(
                2L, "OTHER_CODE", "Tên mới", "PREMIUM_ECONOMY", "INACTIVE"));

        assertThat(res.airlineId()).isEqualTo(1L);
        assertThat(res.fareClassCode()).isEqualTo("VN_ECON_SAVER");
        assertThat(res.fareClassName()).isEqualTo("Tên mới");
        assertThat(res.seatClass()).isEqualTo("PREMIUM_ECONOMY");
        assertThat(res.status()).isEqualTo("INACTIVE");
    }

    @Test
    void delete_dangCoGiaChuyenBay_nem1006() {
        FareClass existing = new FareClass();
        existing.setId(5L);
        when(fareClassRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(flightFareRepository.existsByFareClassId(5L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(5L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void delete_chuaDung_xoaMemThanhCong() {
        FareClass existing = new FareClass();
        existing.setId(5L);
        when(fareClassRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(flightFareRepository.existsByFareClassId(5L)).thenReturn(false);

        service.delete(5L);

        assertThat(existing.getIsDeleted()).isTrue();
        assertThat(existing.getIsActive()).isFalse();
        verify(fareClassRepository).save(existing);
    }

    private Airline airline(Long id, String code, String name) {
        Airline a = new Airline();
        a.setId(id);
        a.setAirlineCode(code);
        a.setAirlineName(name);
        a.setIsDeleted(false);
        return a;
    }
}
