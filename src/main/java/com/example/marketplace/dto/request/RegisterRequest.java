package com.example.marketplace.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Тело запроса POST /api/auth/register.
 *
 * @Size(min = 6) — минимальная длина строки. Дополняет @NotBlank:
 * пустая строка не пройдёт @NotBlank, а короткая — @Size.
 *
 * fullName необязателен — можно зарегистрироваться без имени.
 */
@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный формат email")
    private String email;

    @NotBlank(message = "Пароль обязателен")
    @Size(min = 6, message = "Пароль должен содержать минимум 6 символов")
    private String password;

    private String fullName;
}
