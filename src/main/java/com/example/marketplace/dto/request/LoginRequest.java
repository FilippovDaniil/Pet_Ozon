package com.example.marketplace.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Тело запроса POST /api/auth/login.
 *
 * DTO (Data Transfer Object) — простой объект-контейнер для передачи данных
 * между клиентом и сервером. Не содержит бизнес-логики.
 *
 * Аннотации из Jakarta Validation (ранее javax.validation):
 *   @NotBlank — поле не должно быть null, пустым или состоять только из пробелов.
 *   @Email    — значение должно соответствовать формату email (проверяет регулярным выражением).
 *
 * Валидация срабатывает, когда контроллер помечает параметр @Valid.
 * При ошибке GlobalExceptionHandler.handleValidation() вернёт 400 Bad Request.
 */
@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный формат email")
    private String email;

    @NotBlank(message = "Пароль обязателен")
    private String password;
}
