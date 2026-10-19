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
 * <p>Phân vùng path (mục 1.1b API v0.4):
 * <ul>
 *   <li>{@code /api/public/**} — khách đọc, không token, CHỈ GET.</li>
 *   <li>{@code /api/admin/**} — quản trị, phải đăng nhập; quyền chi tiết do
 *       {@code @PreAuthorize} từng endpoint quyết.</li>
 *   <li>{@code /api/auth/**} — đăng ký/đăng nhập/quên mật khẩu công khai.</li>
 *   <li>Self-service ({@code /api/profile}, {@code /api/my-vouchers}…) — đăng nhập.</li>
 * </ul>
 *
 * <p>Mọi path cũ {@code /api/v1/**} đã bị bỏ — request vào đó rơi về 401.
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

                        // Vùng khách: CHỈ GET, không cần token. Đặt TRƯỚC /admin để
                        // không bao giờ lọt xuống nhánh authenticated.
                        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()

                        // Auth công khai: đăng ký, đăng nhập, quên/đặt lại mật khẩu.
                        // Logout + change-password yêu cầu token nên khai báo riêng TRƯỚC.
                        .requestMatchers("/api/auth/logout", "/api/auth/change-password").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()

                        // Vùng quản trị + self-service: phải đăng nhập
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
