package com.example.gateway;

import java.util.Base64;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    /*
     * JWT 검증
     *
     * Member Service에서 발급한 JWT를
     * 동일한 JWT_SECRET + HS512 방식으로 검증
     */
    @Bean
    ReactiveJwtDecoder jwtDecoder(
            @Value("${app.jwt.secret}") String secret) {

        byte[] bytes = Base64.getDecoder().decode(secret);

        if (bytes.length < 64) {
            throw new IllegalArgumentException(
                "JWT_SECRET must contain at least 64 decoded bytes"
            );
        }

        var key = new SecretKeySpec(
            bytes,
            "HmacSHA512"
        );

        var decoder = NimbusReactiveJwtDecoder
            .withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS512)
            .build();

        decoder.setJwtValidator(
            JwtValidators.createDefault()
        );

        return decoder;
    }

    /*
     * Frontend CORS
     *
     * https://www.renuvo.store
     *          ↓
     * https://api.renuvo.store
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of(
            "https://www.renuvo.store"
        ));

        config.setAllowedMethods(List.of(
            "GET",
            "POST",
            "PUT",
            "DELETE",
            "OPTIONS"
        ));

        config.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "Accept"
        ));

        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
            "/**",
            config
        );

        return source;
    }

    /*
     * Spring Security
     */
    @Bean
    SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http) {

        return http

            /*
             * REST API이므로 CSRF 비활성화
             */
            .csrf(
                ServerHttpSecurity.CsrfSpec::disable
            )

            /*
             * CORS 활성화
             */
            .cors(cors ->
                cors.configurationSource(
                    corsConfigurationSource()
                )
            )

            /*
             * Basic / Form Login 비활성화
             */
            .httpBasic(
                ServerHttpSecurity.HttpBasicSpec::disable
            )

            .formLogin(
                ServerHttpSecurity.FormLoginSpec::disable
            )

            /*
             * 서버에 SecurityContext를 저장하지 않음
             * JWT 기반 Stateless 인증
             */
            .securityContextRepository(
                NoOpServerSecurityContextRepository
                    .getInstance()
            )

            /*
             * URL 접근 권한
             */
            .authorizeExchange(auth -> auth

                /*
                 * 브라우저 CORS Preflight
                 */
                .pathMatchers(
                    HttpMethod.OPTIONS,
                    "/**"
                ).permitAll()

                /*
                 * 회원가입 / 로그인
                 *
                 * JWT 없이 접근 가능
                 */
                .pathMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/signup",
                    "/api/v1/auth/login"
                ).permitAll()

                /*
                 * 상품 목록 / 상세 조회
                 *
                 * 로그인하지 않은 사용자도 조회 가능
                 */
                .pathMatchers(
                    HttpMethod.GET,
                    "/api/v1/products",
                    "/api/v1/products/**"
                ).permitAll()

                /*
                 * Kubernetes
                 * Liveness / Readiness Probe
                 */
                .pathMatchers(
                    HttpMethod.GET,
                    "/actuator/health",
                    "/actuator/health/liveness",
                    "/actuator/health/readiness"
                ).permitAll()

                /*
                 * Prometheus Metrics
                 *
                 * Prometheus가 JWT 없이
                 * 메트릭을 수집할 수 있도록 허용
                 */
                .pathMatchers(
                    HttpMethod.GET,
                    "/actuator/prometheus"
                ).permitAll()

                /*
                 * 위에서 허용한 Endpoint를 제외한
                 * 나머지 Actuator Endpoint 차단
                 *
                 * /actuator/env
                 * /actuator/beans
                 * /actuator/configprops
                 * 등의 노출 방지
                 */
                .pathMatchers(
                    "/actuator/**"
                ).denyAll()

                /*
                 * 나머지 API는 JWT 인증 필요
                 *
                 * 상품 등록/수정/삭제
                 * 주문 생성/조회
                 * 회원 관련 API 등
                 */
                .pathMatchers(
                    "/api/v1/**"
                ).authenticated()

                /*
                 * 정의되지 않은 요청 차단
                 */
                .anyExchange().denyAll()
            )

            /*
             * Authorization: Bearer <JWT>
             */
            .oauth2ResourceServer(oauth ->
                oauth.jwt(
                    Customizer.withDefaults()
                )
            )

            .build();
    }
}