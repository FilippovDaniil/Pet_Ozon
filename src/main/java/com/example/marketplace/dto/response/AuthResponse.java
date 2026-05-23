package com.example.marketplace.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Ответ на POST /api/auth/login и /api/auth/register.
 *
 * token        — короткоживущий access-токен (15 минут), передаётся в каждом запросе.
 * refreshToken — долгоживущий токен (7 дней), используется только для обновления access-токена.
 *
 * Клиент хранит оба токена в localStorage и автоматически запрашивает новый access-токен
 * через POST /api/auth/refresh при получении 401.
 */
@Getter
@AllArgsConstructor
public class AuthResponse {
    private String token;         // access JWT-токен
    private String refreshToken;  // refresh UUID-токен
    private Long userId;
    private String email;
    private String role;          // "CLIENT", "SELLER", "ADMIN", "ACCOUNTANT"
    private String fullName;
    private String shopName;      // только для SELLER, иначе null
}
