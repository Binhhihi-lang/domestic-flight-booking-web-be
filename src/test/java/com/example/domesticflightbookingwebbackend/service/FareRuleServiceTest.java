package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.request.masterdata.FareRuleRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.FareRuleResponse;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.entity.FareClass;
import com.example.domesticflightbookingwebbackend.entity.FareRule;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.mapper.FareRuleMapper;
import com.example.domesticflightbookingwebbackend.repository.BookingSegmentRepository;
import com.example.domesticflightbookingwebbackend.repository.FareClassRepository;
import com.example.domesticflightbookingwebbackend.repository.FareRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FareRuleServiceTest {

    private FareRuleRepository fareRuleRepository;
    private FareClassRepository fareClassRepository;
    private BookingSegmentRepository bookingSegmentRepository;
    private FareRuleService service;

    @BeforeEach
    void setUp() {
        fareRuleRepository = mock(FareRuleRepository.class);
        fareClassRepository = mock(FareClassRepository.class);
        bookingSegmentRepository = mock(BookingSegmentRepository.class);
        service = new FareRuleService(
                fareRuleRepository, fareClassRepository, bookingSegmentRepository, new FareRuleMapper());
    }

    @Test
    void create_tuDongTangVersion() {
        FareClass fc = fareClass(3L, "VN_ECON_FLEX");
        when(fareClassRepository.findById(3L)).thenReturn(Optional.of(fc));
        when(fareRuleRepository.findMaxVersion(3L)).thenReturn(2);
        when(fareRuleRepository.save(any(FareRule.class))).thenAnswer(inv -> {
            FareRule r = inv.getArgument(0);
            r.setId(20L);
            return r;
        });

        FareRuleResponse res = service.create(new FareRuleRequest(
                3L, true, 24, new BigDecimal("90"), "Hoàn 90% trước 24h",
                LocalDateTime.of(2026, 12, 1, 0, 0), "ACTIVE"));

        assertThat(res.version()).isEqualTo(3);
        assertThat(res.fareClassCode()).isEqualTo("VN_ECON_FLEX");
        assertThat(res.refundPercent()).isEqualByComparingTo("90");
    }

    @Test
    void create_khongHoanMaPhanTramLonHon0_nem1006() {
        FareRuleRequest req = new FareRuleRequest(
                3L, false, 0, new BigDecimal("50"), "Sai logic", null, "ACTIVE");

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void update_daCoDatChoThamChieu_nem1006() {
        FareRule existing = new FareRule();
        existing.setId(7L);
        when(fareRuleRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(bookingSegmentRepository.existsByFareRuleId(7L)).thenReturn(true);

        FareRuleRequest req = new FareRuleRequest(
                3L, true, 24, new BigDecimal("80"), "Cập nhật", null, "ACTIVE");

        assertThatThrownBy(() -> service.update(7L, req))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void delete_daCoDatChoThamChieu_nem1006() {
        FareRule existing = new FareRule();
        existing.setId(7L);
        when(fareRuleRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(bookingSegmentRepository.existsByFareRuleId(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(7L))
                .isInstanceOf(BusinessException.class);
    }

    private FareClass fareClass(Long id, String code) {
        Airline airline = new Airline();
        airline.setId(1L);
        airline.setAirlineCode("VN");

        FareClass fc = new FareClass();
        fc.setId(id);
        fc.setAirline(airline);
        fc.setFareClassCode(code);
        fc.setFareClassName("Phổ thông linh hoạt");
        fc.setIsDeleted(false);
        return fc;
    }
}
