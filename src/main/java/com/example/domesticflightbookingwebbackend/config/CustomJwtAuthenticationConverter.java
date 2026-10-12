package com.example.domesticflightbookingwebbackend.config;

import com.example.domesticflightbookingwebbackend.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

/**
 * Thay converter mặc định: KHÔNG tin claim {@code permissions} trong token mà
 * đọc lại authorities từ {@link UserDetailsServiceImpl} (cache Redis → DB).
 *
 * <p>Nhờ vậy khóa role / vô hiệu user có hiệu lực ngay ở request kế tiếp mà
 * không cần chờ token hết hạn.
 */
@RequiredArgsConstructor
public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserDetailsServiceImpl userDetailsService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> authorities = List.of();
        String subject = jwt.getSubject();
        if (subject != null && !subject.isBlank()) {
            authorities = userDetailsService.getAuthorities(Long.valueOf(subject));
        }
        return new JwtAuthenticationToken(jwt, authorities, subject);
    }
}
