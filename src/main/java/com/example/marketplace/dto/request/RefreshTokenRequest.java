package com.example.marketplace.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Тело запроса POST /api/auth/refresh.
 * Клиент передаёт refresh-токен, сервер отвечает новой парой (access + refresh).
 */
@Getter
@NoArgsConstructor
public class RefreshTokenRequest {

    @NotBlank(message = "refreshToken обязателен")
    private String refreshToken;
}
