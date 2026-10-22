package com.example.order;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Bean
    public JwtDecoder jwtDecoder() {

        // Member Service / Product Service와 동일하게
        // Secrets Manager의 Base64 JWT_SECRET을 디코딩해서 사용
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);

        // HS512는 최소 64바이트(512bit) 키 사용
        if (keyBytes.length < 64) {
            throw new IllegalStateException(
                "JWT secret must be at least 64 bytes after Base64 decoding"
            );
        }

        SecretKey key = new SecretKeySpec(
            keyBytes,
            "HmacSHA512"
        );

        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS512)
            .build();

        // exp, nbf 등 기본 JWT 검증
        decoder.setJwtValidator(
            JwtValidators.createDefault()
        );

        return decoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
        throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .cors(Customizer.withDefaults())

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .authorizeHttpRequests(auth -> auth

                // Kubernetes Health Check 허용
                .requestMatchers(
                    HttpMethod.GET,
                    "/actuator/health",
                    "/actuator/health/liveness",
                    "/actuator/health/readiness"
                ).permitAll()

                // Prometheus Metrics 허용
                .requestMatchers(
                    HttpMethod.GET,
                    "/actuator/prometheus"
                ).permitAll()

                // 나머지 Actuator 접근 차단
                .requestMatchers(
                    "/actuator/**"
                ).denyAll()

                // 주문 API는 로그인 필수
                .requestMatchers(
                    "/api/v1/orders/**"
                ).authenticated()

                // 그 외 모든 요청 차단
                .anyRequest().denyAll()
            )

            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(Customizer.withDefaults())
            );

        return http.build();
    }
}