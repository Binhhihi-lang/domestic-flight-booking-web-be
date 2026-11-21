package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.request.masterdata.BaggageOptionRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.BaggageOptionResponse;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.entity.BaggageOption;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.mapper.BaggageOptionMapper;
import com.example.domesticflightbookingwebbackend.repository.AirlineRepository;
import com.example.domesticflightbookingwebbackend.repository.BaggageOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BaggageOptionServiceTest {

    private BaggageOptionRepository baggageOptionRepository;
    private AirlineRepository airlineRepository;
    private BaggageOptionService service;

    @BeforeEach
    void setUp() {
        baggageOptionRepository = mock(BaggageOptionRepository.class);
        airlineRepository = mock(AirlineRepository.class);
        service = new BaggageOptionService(
                baggageOptionRepository, airlineRepository, new BaggageOptionMapper());
    }

    @Test
    void create_thanhCong() {
        Airline vn = airline(1L, "VN", "Vietnam Airlines");
        when(airlineRepository.findById(1L)).thenReturn(Optional.of(vn));
        when(baggageOptionRepository.existsByAirlineIdAndNameIgnoreCaseAndIsDeletedFalse(1L, "Hành lý 20 kg"))
                .thenReturn(false);
        when(baggageOptionRepository.save(any(BaggageOption.class))).thenAnswer(inv -> {
            BaggageOption opt = inv.getArgument(0);
            opt.setId(15L);
            return opt;
        });

        BaggageOptionResponse res = service.create(new BaggageOptionRequest(
                1L, "  Hành lý 20 kg  ", new BigDecimal("20"), new BigDecimal("250000"), "ACTIVE"));

        assertThat(res.name()).isEqualTo("Hành lý 20 kg");
        assertThat(res.airlineCode()).isEqualTo("VN");
        assertThat(res.price()).isEqualByComparingTo("250000");
    }

    @Test
    void create_trungTenTrongCungHang_nem1005() {
        when(airlineRepository.findById(1L)).thenReturn(Optional.of(airline(1L, "VN", "Vietnam Airlines")));
        when(baggageOptionRepository.existsByAirlineIdAndNameIgnoreCaseAndIsDeletedFalse(1L, "Hành lý 20 kg"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(new BaggageOptionRequest(
                1L, "Hành lý 20 kg", new BigDecimal("20"), new BigDecimal("250000"), "ACTIVE")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void update_trungTenBanGhiKhac_nem1005() {
        BaggageOption existing = new BaggageOption();
        existing.setId(5L);
        existing.setAirline(airline(1L, "VN", "Vietnam Airlines"));
        when(baggageOptionRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(baggageOptionRepository.existsByAirlineIdAndNameIgnoreCaseAndIsDeletedFalseAndIdNot(
                1L, "Hành lý 30 kg", 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(5L, new BaggageOptionRequest(
                1L, "Hành lý 30 kg", new BigDecimal("30"), new BigDecimal("350000"), "ACTIVE")))
                .isInstanceOf(DuplicateResourceException.class);
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
