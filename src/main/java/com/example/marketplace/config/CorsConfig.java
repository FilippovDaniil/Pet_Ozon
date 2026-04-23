package com.example.marketplace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Конфигурация CORS (Cross-Origin Resource Sharing).
 *
 * Что такое CORS и зачем он нужен?
 * Браузер блокирует AJAX-запросы с одного домена (origin) к другому.
 * Например, фронтенд на http://localhost:3000 не может запросить API
 * на http://localhost:8080 — это разные origins.
 * CORS — механизм, которым сервер сообщает браузеру: «разрешаю запросы с этих доменов».
 *
 * В этом конфиге мы разрешаем запросы со ВСЕХ origins ("*").
 * На production нужно заменить на конкретный домен фронтенда.
 *
 * Бин подключается в SecurityConfig через .cors(Customizer.withDefaults()).
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Разрешаем запросы с любого origin (домена).
        // На production: List.of("https://your-frontend.com")
        config.setAllowedOriginPatterns(List.of("*"));

        // Разрешённые HTTP-методы.
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // Разрешаем все заголовки в запросе (Authorization, Content-Type и т.д.).
        config.setAllowedHeaders(List.of("*"));

        // Разрешаем клиенту читать заголовок Authorization из ответа.
        // Нужно, чтобы фронтенд мог извлечь JWT-токен при логине.
        config.setExposedHeaders(List.of("Authorization"));

        // Браузер кэширует preflight-ответ (OPTIONS) на 1 час.
        config.setMaxAge(3600L);

        // Применяем правила ко всем URL-путям.
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
