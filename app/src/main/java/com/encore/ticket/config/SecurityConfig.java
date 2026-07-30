package com.encore.ticket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(new ProblemDetailAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new ProblemDetailAccessDeniedHandler(objectMapper)))
                .addFilterBefore(new StubBearerTokenFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(HttpMethod.GET, "/auth/oauth/{provider}/authorize").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/oauth/{provider}/callback").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/logout").permitAll()

                        .requestMatchers(HttpMethod.GET, "/concerts/ranking").permitAll()
                        .requestMatchers(HttpMethod.GET, "/concerts").permitAll()
                        .requestMatchers(HttpMethod.GET, "/concerts/{concertId}").permitAll()
                        .requestMatchers(HttpMethod.POST, "/concerts/{concertId}/views").permitAll()
                        .requestMatchers(HttpMethod.POST, "/concerts/{concertId}/likes").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/concerts/{concertId}/likes").authenticated()

                        .requestMatchers(HttpMethod.POST, "/queue/{scheduleId}/tokens").authenticated()
                        .requestMatchers(HttpMethod.GET, "/queue/{scheduleId}/status").authenticated()
                        .requestMatchers(HttpMethod.GET, "/schedules/{scheduleId}/seats").authenticated()

                        .requestMatchers(HttpMethod.POST, "/reservations/holds").authenticated()
                        .requestMatchers(HttpMethod.POST, "/reservations").authenticated()
                        .requestMatchers(HttpMethod.GET, "/reservations").authenticated()
                        .requestMatchers(HttpMethod.GET, "/reservations/{reservationId}").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/reservations/{reservationId}").authenticated()

                        .requestMatchers(HttpMethod.POST, "/payments/confirm").authenticated()
                        .requestMatchers(HttpMethod.GET, "/payments/{orderId}").authenticated()

                        .anyRequest().authenticated()
                );
        return http.build();
    }
}