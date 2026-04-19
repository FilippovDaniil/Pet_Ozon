package com.example.marketplace.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;

/**
 * Утилита для тестов: мокирует JwtAuthenticationFilter так, чтобы он пропускал запросы.
 * Находится в пакете security для доступа к protected-методу doFilterInternal.
 */
public class JwtFilterTestHelper {

    public static void stubPassThrough(JwtAuthenticationFilter jwtFilter) throws Exception {
        lenient().doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtFilter).doFilterInternal(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class),
                any(FilterChain.class));
    }
}
