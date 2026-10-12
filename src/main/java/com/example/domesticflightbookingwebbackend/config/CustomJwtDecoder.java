package com.example.domesticflightbookingwebbackend.config;

import com.example.domesticflightbookingwebbackend.repository.redis.InvalidatedTokenRepository;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.text.ParseException;

/**
 * Giải mã access token: kiểm tra blacklist Redis TRƯỚC khi verify chữ ký,
 * sau đó giao cho {@link NimbusJwtDecoder} verify HS256 + hạn dùng.
 */
@Component
public class CustomJwtDecoder implements JwtDecoder {

    private final InvalidatedTokenRepository invalidatedTokenRepository;
    private final String signerKey;
    private NimbusJwtDecoder nimbusJwtDecoder;

    public CustomJwtDecoder(InvalidatedTokenRepository invalidatedTokenRepository,
                            @Value("${jwt.signer-key}") String signerKey) {
        this.invalidatedTokenRepository = invalidatedTokenRepository;
        this.signerKey = signerKey;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        String jwtId;
        try {
            JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
            jwtId = claims.getJWTID();
        } catch (ParseException e) {
            throw new JwtException("Token không hợp lệ");
        }

        if (jwtId != null && invalidatedTokenRepository.existsById(jwtId)) {
            throw new JwtException("Token đã bị thu hồi");
        }

        if (nimbusJwtDecoder == null) {
            SecretKeySpec key = new SecretKeySpec(signerKey.getBytes(), "HS256");
            nimbusJwtDecoder = NimbusJwtDecoder.withSecretKey(key)
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build();
        }
        return nimbusJwtDecoder.decode(token);
    }
}
