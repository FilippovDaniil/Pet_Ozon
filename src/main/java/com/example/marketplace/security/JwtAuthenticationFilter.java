package com.example.marketplace.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP-фильтр: проверяет JWT-токен в каждом входящем запросе.
 *
 * OncePerRequestFilter — базовый класс Spring, гарантирующий, что
 * фильтр выполнится РОВНО ОДИН РАЗ на запрос (не дважды при forward/redirect).
 *
 * Как работает цепочка фильтров Spring Security:
 *   HTTP Request → [JwtAuthenticationFilter] → [другие фильтры] → Controller
 *
 * Этот фильтр зарегистрирован в SecurityConfig:
 *   .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
 * Значит, он выполняется ДО стандартного фильтра логина по форме.
 *
 * После успешной проверки токена фильтр помещает пользователя в SecurityContext —
 * это позволяет @AuthenticationPrincipal User user работать в контроллерах.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Токен должен приходить в заголовке Authorization: Bearer <token>
        final String authHeader = request.getHeader("Authorization");

        // Если заголовка нет или он не начинается с "Bearer " — пропускаем запрос дальше
        // (публичные эндпоинты как /api/products доступны без токена).
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        // Вырезаем сам токен (7 символов = длина "Bearer ")
        final String token = authHeader.substring(7);
        final String email;

        try {
            email = jwtUtil.extractUsername(token);
        } catch (Exception e) {
            // Токен невалиден (истёк, подделан) — пропускаем без аутентификации.
            chain.doFilter(request, response);
            return;
        }

        // Аутентифицируем только если email извлечён и пользователь ещё не аутентифицирован
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            if (jwtUtil.isTokenValid(token, userDetails)) {
                // Создаём объект аутентификации и помещаем в SecurityContext.
                // null в качестве credentials — пароль не нужен (уже проверен через токен).
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        // Передаём запрос следующему фильтру в цепочке.
        chain.doFilter(request, response);
    }
}
