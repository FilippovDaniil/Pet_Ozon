package com.example.marketplace.filter;

import com.example.marketplace.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Фильтр обогащения логов контекстом текущего пользователя через MDC.
 *
 * ── Почему MDC? ──────────────────────────────────────────────────────────────
 * MDC (Mapped Diagnostic Context) — ThreadLocal-словарь, значения из которого
 * Logback автоматически вставляет в каждую строку лога через %mdc{key}.
 * Мы кладём userId, role, requestId один раз в начале запроса —
 * и они появляются во ВСЕХ логах этого запроса: в сервисах, репозиториях, аспектах.
 *
 * ── Порядок выполнения ───────────────────────────────────────────────────────
 * Spring Security (order = -100) выполняется раньше этого фильтра (order = MAX_VALUE).
 * Поэтому к моменту нашего выполнения JwtAuthenticationFilter уже записал User
 * в SecurityContext — мы можем его безопасно читать.
 *
 * ── MDC.clear() в finally ────────────────────────────────────────────────────
 * Нити из ThreadPool переиспользуются между запросами. Без clear() данные
 * предыдущего пользователя «утекут» в следующий запрос той же нити.
 */
@Slf4j
@Component
public class AuditMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Короткий ID запроса: позволяет связать все логи одного HTTP-вызова
        MDC.put("requestId", UUID.randomUUID().toString().substring(0, 8));

        // SecurityContext уже заполнен JwtAuthenticationFilter к этому моменту
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User user) {
            MDC.put("userId", String.valueOf(user.getId()));
            MDC.put("userEmail", user.getEmail());
            MDC.put("role", user.getRole().name());
        }

        String method = request.getMethod();
        String uri = request.getRequestURI();
        long start = System.currentTimeMillis();

        log.debug("→ HTTP {} {}", method, uri);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            log.debug("← HTTP {} {} {} ({}ms)", method, uri, response.getStatus(), duration);
            MDC.clear();
        }
    }
}
