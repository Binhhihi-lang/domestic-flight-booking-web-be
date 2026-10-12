package com.example.domesticflightbookingwebbackend.exception;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessException_traMa1006VaHttp409() {
        BusinessException ex = new BusinessException("Ngày kết thúc phải sau ngày bắt đầu");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAppException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(1006);
        assertThat(response.getBody().getMessage()).isEqualTo("Ngày kết thúc phải sau ngày bắt đầu");
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    void resourceNotFound_traMa1004VaHttp404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Hãng bay không tồn tại");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAppException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getCode()).isEqualTo(1004);
    }

    @Test
    void duplicateResource_traMa1005VaHttp409() {
        DuplicateResourceException ex = new DuplicateResourceException("Mã hãng đã tồn tại");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAppException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo(1005);
    }

    @Test
    void validation_traMa1001VoiMangFieldError() throws Exception {
        MethodArgumentNotValidException ex = buildValidationException();

        ResponseEntity<ApiResponse<List<GlobalExceptionHandler.FieldErrorDetail>>> response =
                handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(1001);

        List<GlobalExceptionHandler.FieldErrorDetail> errors = response.getBody().getData();
        assertThat(errors).isNotNull();
        assertThat(errors).extracting(GlobalExceptionHandler.FieldErrorDetail::field)
                .containsExactlyInAnyOrder("email", "full_name");
        assertThat(errors).extracting(GlobalExceptionHandler.FieldErrorDetail::message)
                .containsExactlyInAnyOrder("Email không đúng định dạng", "Không được để trống");
    }

    @Test
    void uncaught_traMa9999VaKhongLoChiTiet() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUncaught(new IllegalStateException("chi tiết nội bộ nhạy cảm"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getCode()).isEqualTo(9999);
        assertThat(response.getBody().getMessage()).doesNotContain("chi tiết nội bộ nhạy cảm");
    }

    private MethodArgumentNotValidException buildValidationException() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "Email không đúng định dạng"));
        bindingResult.addError(new FieldError("request", "full_name", "Không được để trống"));

        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        return new MethodArgumentNotValidException(parameter, bindingResult);
    }

    @SuppressWarnings("unused")
    private void dummy(String arg) {
    }
}
