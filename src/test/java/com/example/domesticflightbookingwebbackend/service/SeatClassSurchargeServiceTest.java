package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.request.masterdata.SeatClassSurchargeRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.SeatClassSurchargeResponse;
import com.example.domesticflightbookingwebbackend.entity.SeatClass;
import com.example.domesticflightbookingwebbackend.entity.SeatClassSurcharge;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.mapper.SeatClassSurchargeMapper;
import com.example.domesticflightbookingwebbackend.repository.SeatClassSurchargeRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Phụ thu nâng hạng ghế — mặc định dùng chung mọi chuyến (DB v0.5, API v0.4). */
class SeatClassSurchargeServiceTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private SeatClassSurchargeRepository repository;
    private SeatClassSurchargeService service;

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        repository = mock(SeatClassSurchargeRepository.class);
        service = new SeatClassSurchargeService(repository, new SeatClassSurchargeMapper());
    }

    private SeatClassSurchargeRequest request(String from, String to, String amount) {
        return new SeatClassSurchargeRequest(from, to, new BigDecimal(amount), "ACTIVE");
    }

    @Test
    void create_capMoi_thanhCong() {
        when(repository.existsByFromClassAndToClassAndIsDeletedFalse(
                SeatClass.ECONOMY, SeatClass.BUSINESS)).thenReturn(false);
        when(repository.save(any(SeatClassSurcharge.class))).thenAnswer(inv -> {
            SeatClassSurcharge e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        SeatClassSurchargeResponse res = service.create(request("economy", "business", "500000"));

        assertThat(res.fromClass()).isEqualTo("ECONOMY");
        assertThat(res.toClass()).isEqualTo("BUSINESS");
        assertThat(res.surcharge()).isEqualByComparingTo("500000");
        assertThat(res.status()).isEqualTo("ACTIVE");
    }

    @Test
    void create_trungCap_nem1005() {
        when(repository.existsByFromClassAndToClassAndIsDeletedFalse(
                SeatClass.ECONOMY, SeatClass.BUSINESS)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("ECONOMY", "BUSINESS", "500000")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("ECONOMY → BUSINESS");
    }

    @Test
    void update_doiSangCapCuaBanGhiKhac_nem1005() {
        SeatClassSurcharge existing = new SeatClassSurcharge();
        existing.setId(7L);
        existing.setFromClass(SeatClass.ECONOMY);
        existing.setToClass(SeatClass.BUSINESS);
        when(repository.findById(7L)).thenReturn(Optional.of(existing));
        when(repository.existsByFromClassAndToClassAndIsDeletedFalseAndIdNot(
                SeatClass.BUSINESS, SeatClass.FIRST, 7L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(7L, request("BUSINESS", "FIRST", "700000")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void update_giuNguyenCapCuaChinhNo_thanhCong() {
        SeatClassSurcharge existing = new SeatClassSurcharge();
        existing.setId(7L);
        existing.setFromClass(SeatClass.ECONOMY);
        existing.setToClass(SeatClass.BUSINESS);
        when(repository.findById(7L)).thenReturn(Optional.of(existing));
        // Chỉ trùng chính nó → exists với IdNot phải trả false.
        when(repository.existsByFromClassAndToClassAndIsDeletedFalseAndIdNot(
                SeatClass.ECONOMY, SeatClass.BUSINESS, 7L)).thenReturn(false);
        when(repository.save(any(SeatClassSurcharge.class))).thenAnswer(inv -> inv.getArgument(0));

        SeatClassSurchargeResponse res = service.update(7L, request("ECONOMY", "BUSINESS", "650000"));

        assertThat(res.surcharge()).isEqualByComparingTo("650000");
    }

    @Test
    void delete_xoaMem_khongXoaCung() {
        SeatClassSurcharge existing = new SeatClassSurcharge();
        existing.setId(3L);
        when(repository.findById(3L)).thenReturn(Optional.of(existing));
        when(repository.save(any(SeatClassSurcharge.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(3L);

        ArgumentCaptor<SeatClassSurcharge> captor = ArgumentCaptor.forClass(SeatClassSurcharge.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getIsDeleted()).isTrue();
        assertThat(captor.getValue().getIsActive()).isFalse();
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
        verify(repository, never()).delete(any(SeatClassSurcharge.class));
    }

    @Test
    void surchargeAm_viPhamValidate() {
        assertThat(violations("ECONOMY", "BUSINESS", "-1")).hasSize(1);
    }

    @Test
    void hangKhongHopLe_viPhamValidate() {
        assertThat(violations("PREMIUM_ECONOMY", "BUSINESS", "0")).hasSize(1);
    }

    private Set<ConstraintViolation<SeatClassSurchargeRequest>> violations(
            String from, String to, String amount) {
        return validator.validate(request(from, to, amount));
    }
}
