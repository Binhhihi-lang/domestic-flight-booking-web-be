package com.example.domesticflightbookingwebbackend.config;

import com.example.domesticflightbookingwebbackend.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình bảo mật: stateless + JWT Bearer.
 *
 * <p>Phân quyền chi tiết theo permission nằm ở {@code @PreAuthorize} từng
 * endpoint (method-level); ở đây chỉ chặn theo nhóm path.
 * Endpoint công khai (mục 1.5 API Design v0.2): auth (register/login/reset) +
 * GET dữ liệu công khai (airline, city, airport, promotion, news, news category).
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomJwtDecoder customJwtDecoder;
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    @Bean
    public CustomJwtAuthenticationConverter jwtAuthenticationConverter() {
        return new CustomJwtAuthenticationConverter(userDetailsService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Tài liệu API + health
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // Auth công khai: đăng ký, đăng nhập, quên/đặt lại mật khẩu.
                        // Logout + change-password yêu cầu token nên khai báo riêng TRƯỚC.
                        .requestMatchers("/api/v1/auth/logout", "/api/v1/auth/change-password").authenticated()
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // GET dữ liệu công khai (guest xem được)
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/airlines/**",
                                "/api/v1/cities/**",
                                "/api/v1/airports/**",
                                "/api/v1/promotions/**",
                                "/api/v1/news/**",
                                "/api/v1/news-categories/**"
                        ).permitAll()

                        // Còn lại phải đăng nhập; quyền chi tiết do @PreAuthorize lo
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(customJwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .exceptionHandling(exception -> exception.accessDeniedHandler(customAccessDeniedHandler));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
