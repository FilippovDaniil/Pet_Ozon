package com.example.marketplace.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

// @TestConfiguration — аналог @Configuration, но загружается ТОЛЬКО в тестах.
// Spring не включает этот класс в основной контекст приложения.
// Используется вместо SecurityConfig + JwtAuthenticationFilter, которые мы исключаем в @WebMvcTest.
@TestConfiguration
public class TestSecurityConfig {

    // Создаём упрощённую цепочку фильтров безопасности специально для тестов.
    // Логика идентична SecurityConfig, но без JWT-фильтра — аутентификация подставляется
    // через .with(user(...)) прямо в тестовых запросах.
    @Bean
    public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        return http
                // Отключаем CSRF: в тестах используем MockMvc без браузера, CSRF не нужен
                .csrf(AbstractHttpConfigurer::disable)
                // STATELESS: Spring не создаёт HTTP-сессию, состояние хранится только в JWT
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Правила доступа — копия из основного SecurityConfig
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/search/**").permitAll()
                        // Публичные endpoint-ы платёжного шлюза (bank redirect без JWT)
                        .requestMatchers("/api/payment/callback", "/api/payment/fail").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/seller/**").hasRole("SELLER")
                        .requestMatchers("/api/accountant/**").hasRole("ACCOUNTANT")
                        // CategoryController: POST/DELETE требуют ADMIN (аналог @PreAuthorize на сервисе)
                        .requestMatchers(HttpMethod.POST, "/api/categories").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/categories/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                // Если запрос без аутентификации к защищённому ресурсу — вернуть 401 (не редирект)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .build();
    }
}
