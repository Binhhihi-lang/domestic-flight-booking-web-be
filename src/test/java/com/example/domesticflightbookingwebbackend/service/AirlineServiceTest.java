package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.request.masterdata.AirlineRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.AirlineResponse;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.mapper.AirlineMapper;
import com.example.domesticflightbookingwebbackend.repository.AirlineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Test service master data (mẫu Airline) với repository mock. */
class AirlineServiceTest {

    private AirlineRepository repository;
    private AirlineService service;

    @BeforeEach
    void setUp() {
        repository = mock(AirlineRepository.class);
        service = new AirlineService(repository, new AirlineMapper());
    }

    @Test
    void create_chuanHoaCodeVaTraResponse() {
        when(repository.existsByAirlineCodeAndIsDeletedFalse("VJ")).thenReturn(false);
        when(repository.save(any(Airline.class))).thenAnswer(inv -> {
            Airline a = inv.getArgument(0);
            a.setId(7L);
            return a;
        });

        AirlineResponse res = service.create(new AirlineRequest(
                "vj", "VietJet Air", "Việt Nam", null, "ACTIVE"));

        assertThat(res.id()).isEqualTo(7L);
        assertThat(res.airlineCode()).isEqualTo("VJ");       // đã upper-case
        assertThat(res.status()).isEqualTo("ACTIVE");
    }

    @Test
    void create_trungCodeNem1005() {
        when(repository.existsByAirlineCodeAndIsDeletedFalse("VN")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new AirlineRequest(
                "VN", "Vietnam Airlines", "Việt Nam", null, "ACTIVE")))
                .isInstanceOf(DuplicateResourceException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void create_statusNullMacDinhActive() {
        when(repository.existsByAirlineCodeAndIsDeletedFalse("QH")).thenReturn(false);
        when(repository.save(any(Airline.class))).thenAnswer(inv -> inv.getArgument(0));

        AirlineResponse res = service.create(new AirlineRequest(
                "QH", "Bamboo", "Việt Nam", null, null));

        assertThat(res.status()).isEqualTo("ACTIVE");
    }

    @Test
    void getById_khongTonTaiNem1004() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_laSoftDeleteKhongXoaCung() {
        Airline airline = new Airline();
        airline.setId(3L);
        airline.setIsActive(true);
        airline.setIsDeleted(false);
        when(repository.findById(3L)).thenReturn(Optional.of(airline));
        when(repository.save(any(Airline.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(3L);

        assertThat(airline.getIsDeleted()).isTrue();
        assertThat(airline.getIsActive()).isFalse();
        assertThat(airline.getDeletedAt()).isNotNull();
        verify(repository, never()).deleteById(any());
    }

    @Test
    void toggleActive_doiCoIsActive() {
        Airline airline = new Airline();
        airline.setId(4L);
        airline.setIsActive(true);
        when(repository.findById(4L)).thenReturn(Optional.of(airline));
        when(repository.save(any(Airline.class))).thenAnswer(inv -> inv.getArgument(0));

        AirlineResponse res = service.toggleActive(4L, false);

        assertThat(res.status()).isEqualTo("INACTIVE");
    }

    @Test
    void update_khongDoiCode() {
        Airline airline = new Airline();
        airline.setId(5L);
        airline.setAirlineCode("VN");
        airline.setIsActive(true);
        when(repository.findById(5L)).thenReturn(Optional.of(airline));
        when(repository.save(any(Airline.class))).thenAnswer(inv -> inv.getArgument(0));

        AirlineResponse res = service.update(5L, new AirlineRequest(
                "ZZ", "Tên mới", "Việt Nam", null, "ACTIVE"));

        assertThat(res.airlineCode()).isEqualTo("VN");       // code giữ nguyên
        assertThat(res.airlineName()).isEqualTo("Tên mới");
    }
}
