package com.example.member;

import java.util.Base64;

import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /*
     * 비밀번호 암호화
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /*
     * JWT 검증
     *
     * JWT_SECRET은 Base64로 인코딩된 값을 사용하며,
     * HS512 사용을 위해 디코딩 결과가 최소 64바이트인지 확인한다.
     */
    @Bean
    JwtDecoder jwtDecoder(
            @Value("${app.jwt.secret}") String secret) {

        byte[] bytes = Base64.getDecoder().decode(secret);

        if (bytes.length < 64) {
            throw new IllegalArgumentException(
                "JWT_SECRET must contain at least 64 decoded bytes"
            );
        }

        var key = new SecretKeySpec(bytes, "HmacSHA512");

        var decoder = NimbusJwtDecoder
            .withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS512)
            .build();

        decoder.setJwtValidator(
            JwtValidators.createDefault()
        );

        return decoder;
    }

    /*
     * Spring Security 설정
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http)
            throws Exception {

        return http

            /*
             * REST API이므로 CSRF 비활성화
             */
            .csrf(csrf -> csrf.disable())

            /*
             * Basic Authentication 비활성화
             */
            .httpBasic(basic -> basic.disable())

            /*
             * Form Login 비활성화
             */
            .formLogin(form -> form.disable())

            /*
             * JWT 기반 Stateless 인증
             */
            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            /*
             * URL 접근 권한
             */
            .authorizeHttpRequests(auth -> auth

                /*
                 * Spring 내부 ERROR Dispatcher 허용
                 */
                .dispatcherTypeMatchers(
                    DispatcherType.ERROR
                ).permitAll()

                /*
                 * 회원가입 / 로그인
                 *
                 * JWT 없이 접근 가능
                 */
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/signup",
                    "/api/v1/auth/login"
                ).permitAll()

                /*
                 * Kubernetes Probe
                 *
                 * 인증 없이 접근 가능
                 */
                .requestMatchers(
                    HttpMethod.GET,
                    "/actuator/health",
                    "/actuator/health/liveness",
                    "/actuator/health/readiness"
                ).permitAll()

                /*
                 * Prometheus Metrics
                 *
                 * Prometheus가 JWT 없이 수집할 수 있도록 허용
                 */
                .requestMatchers(
                    HttpMethod.GET,
                    "/actuator/prometheus"
                ).permitAll()

                /*
                 * 위에서 명시적으로 허용하지 않은
                 * 나머지 Actuator Endpoint 차단
                 *
                 * 예:
                 * /actuator/env
                 * /actuator/configprops
                 * /actuator/beans
                 */
                .requestMatchers(
                    "/actuator/**"
                ).denyAll()

                /*
                 * 회원 관련 API
                 *
                 * JWT 인증 필요
                 */
                .requestMatchers(
                    "/api/v1/members/**"
                ).authenticated()

                /*
                 * 정의되지 않은 모든 요청 차단
                 */
                .anyRequest().denyAll()
            )

            /*
             * Authorization: Bearer <JWT>
             */
            .oauth2ResourceServer(oauth ->
                oauth.jwt(Customizer.withDefaults())
            )

            .build();
    }
}