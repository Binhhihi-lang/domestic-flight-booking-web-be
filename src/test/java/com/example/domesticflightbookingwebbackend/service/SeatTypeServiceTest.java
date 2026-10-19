package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.request.masterdata.SeatTypeRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.SeatTypeResponse;
import com.example.domesticflightbookingwebbackend.entity.SeatType;
import com.example.domesticflightbookingwebbackend.mapper.SeatTypeMapper;
import com.example.domesticflightbookingwebbackend.repository.AircraftSeatRepository;
import com.example.domesticflightbookingwebbackend.repository.SeatTypeRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Test color_hex của loại ghế (DB v0.5 mục 1, API v0.4 mục 2.6). */
class SeatTypeServiceTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private SeatTypeRepository repository;
    private SeatTypeService service;

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
        repository = mock(SeatTypeRepository.class);
        service = new SeatTypeService(repository, mock(AircraftSeatRepository.class), new SeatTypeMapper());
    }

    private SeatTypeRequest request(String colorHex) {
        return new SeatTypeRequest("eco_win", "Phổ thông - cửa sổ", "ECONOMY", colorHex, "ACTIVE");
    }

    private SeatTypeResponse create(String colorHex) {
        when(repository.existsBySeatTypeCodeAndIsDeletedFalse("ECO_WIN")).thenReturn(false);
        when(repository.save(any(SeatType.class))).thenAnswer(inv -> {
            SeatType st = inv.getArgument(0);
            st.setId(1L);
            return st;
        });
        return service.create(request(colorHex));
    }

    @Test
    void create_coMauHopLe_chuanHoaChuHoa() {
        SeatTypeResponse res = create("#0194f3");

        assertThat(res.colorHex()).isEqualTo("#0194F3");
    }

    @Test
    void create_khongCoMau_traNull() {
        assertThat(create(null).colorHex()).isNull();
    }

    @Test
    void create_mauRong_coiNhuKhongDat() {
        assertThat(create("   ").colorHex()).isNull();
    }

    @Test
    void mauSaiDinhDang_nem1001() {
        assertThat(violations("red")).hasSize(1);
        assertThat(violations("#0194F")).hasSize(1);    // thiếu ký tự
        assertThat(violations("0194F3")).hasSize(1);    // thiếu dấu #
        assertThat(violations("#GGGGGG")).hasSize(1);   // ký tự không phải hex
    }

    @Test
    void mauDungDinhDang_khongViPham() {
        assertThat(violations("#0194F3")).isEmpty();
        assertThat(violations("#abcdef")).isEmpty();
        assertThat(violations(null)).isEmpty();
    }

    private Set<ConstraintViolation<SeatTypeRequest>> violations(String colorHex) {
        return validator.validate(request(colorHex));
    }
}
