package com.example.marketplace.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// DTO для запроса отправки произвольного письма через POST /api/admin/email/send.
// Аннотация @Valid в EmailController активирует Bean Validation для всех полей этого класса.
// Если хотя бы одно поле не проходит проверку — Spring возвращает 400 Bad Request
// с описанием нарушений, не вызывая метод контроллера.
// Lombok генерирует геттеры (нужны для чтения полей в контроллере) и сеттеры
// (нужны для десериализации JSON → объект Jackson-ом)
@Getter
@Setter
public class AdminEmailRequest {

    // @NotBlank — запрещает null, пустую строку "" и строку из одних пробелов "   "
    // @Email — проверяет, что строка содержит корректный email-адрес (есть @, домен и т.д.)
    // Обе аннотации из jakarta.validation — стандарт Bean Validation 3.x (Spring Boot 3.x)
    @NotBlank
    @Email
    private String to;

    // @NotBlank — тема не может быть пустой или состоять только из пробелов
    @NotBlank
    private String subject;

    // @NotBlank — текст письма обязателен; "   " (пробелы) не пройдёт валидацию
    @NotBlank
    private String text;
}
