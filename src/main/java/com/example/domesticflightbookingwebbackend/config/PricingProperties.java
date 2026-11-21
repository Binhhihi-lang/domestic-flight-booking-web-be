package com.example.domesticflightbookingwebbackend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình tỉ lệ giá vé theo loại hành khách (DB v0.5 mục 5.3 / API v0.4 mục 3).
 * Giá trong {@code flight_fares} là giá người lớn ({@code base_fare + tax_fee});
 * giá trẻ em và em bé tính theo phần trăm cố định cấu hình tại đây.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.pricing")
public class PricingProperties {

    /** Phần trăm giá trẻ em (2–11 tuổi) so với giá người lớn. Mặc định 75%. */
    private int childFarePercent = 75;

    /** Phần trăm giá em bé (<2 tuổi) so với giá người lớn. Mặc định 10%. */
    private int infantFarePercent = 10;
}
