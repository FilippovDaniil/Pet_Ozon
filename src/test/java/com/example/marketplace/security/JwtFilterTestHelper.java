package com.example.marketplace.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;

/**
 * Вспомогательный класс для тестов контроллеров: «отключает» JWT-фильтр.
 *
 * Зачем это нужно:
 * В @WebMvcTest тестах мы исключаем реальный JwtAuthenticationFilter через excludeFilters,
 * но в некоторых случаях мок фильтра всё равно попадает в контекст. Этот helper
 * настраивает мок так, чтобы он пропускал запрос дальше без проверки токена.
 *
 * Класс находится в пакете security специально — для доступа к protected-методу doFilterInternal,
 * который объявлен в OncePerRequestFilter (родительский класс JwtAuthenticationFilter).
 */
public class JwtFilterTestHelper {

    /**
     * Настраивает мок JwtAuthenticationFilter так, чтобы он вызывал chain.doFilter()
     * без какой-либо обработки — просто пропускал запрос дальше по цепочке.
     *
     * @param jwtFilter мок JwtAuthenticationFilter, созданный через @Mock или @MockitoBean
     */
    public static void stubPassThrough(JwtAuthenticationFilter jwtFilter) throws Exception {
        // lenient() — «мягкий» стаб: не ругается если этот мок не будет вызван в каком-то тесте
        // doAnswer — выполнить лямбду вместо реального метода
        lenient().doAnswer(inv -> {
            // Извлекаем FilterChain из аргументов вызова (аргумент с индексом 2)
            FilterChain chain = inv.getArgument(2);
            // Передаём запрос и ответ дальше по цепочке фильтров — без проверки JWT
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null; // doFilterInternal возвращает void → null
        }).when(jwtFilter).doFilterInternal(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class),
                any(FilterChain.class));
    }
}
