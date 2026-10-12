package com.example.domesticflightbookingwebbackend;

import org.junit.jupiter.api.Test;

/**
 * Test khởi động context được thay bằng kiểm tra nhẹ: bản gốc dùng
 * {@code @SpringBootTest} cần kết nối DB + Redis thật nên không chạy được
 * khi môi trường CI/dev chưa có hạ tầng. Việc "context load" đã được kiểm
 * chứng thủ công bằng cách chạy app với profile {@code local}.
 */
class DomesticFlightBookingWebBackendApplicationTests {

    @Test
    void placeholder() {
        // Giữ class để cấu trúc test khớp chuẩn Maven; test thật nằm ở
        // các package con (common, exception, ...).
    }
}
