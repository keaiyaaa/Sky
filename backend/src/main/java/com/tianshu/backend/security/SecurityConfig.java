package com.tianshu.backend.security;

import com.tianshu.backend.common.ApiErrorCodes;
import com.tianshu.backend.common.ApiErrorResponseWriter;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiErrorResponseWriter errorResponseWriter
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/system/ping",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setHeader("WWW-Authenticate", "Bearer");
                            errorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED,
                                    ApiErrorCodes.UNAUTHENTICATED, "需要登录");
                        })
                        .accessDeniedHandler((request, response, exception) ->
                                errorResponseWriter.write(request, response, HttpStatus.FORBIDDEN,
                                        ApiErrorCodes.FORBIDDEN, "无权执行此操作")))
                .build();
    }
}
