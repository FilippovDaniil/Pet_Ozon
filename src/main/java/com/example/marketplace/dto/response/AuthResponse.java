package com.example.marketplace.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Ответ на POST /api/auth/login и /api/auth/register.
 *
 * @AllArgsConstructor (Lombok) — генерирует конструктор со ВСЕМИ полями.
 * Используется в AuthController: new AuthResponse(token, userId, ...).
 *
 * Содержит JWT-токен и базовые данные пользователя, чтобы клиент
 * не делал дополнительный запрос к /api/profile/me сразу после входа.
 */
@Getter
@AllArgsConstructor
public class AuthResponse {
    private String token;    // JWT-токен для отправки в Authorization: Bearer <token>
    private Long userId;
    private String email;
    private String role;     // "CLIENT", "SELLER" или "ADMIN"
    private String fullName;
    private String shopName; // только для SELLER, иначе null
}
