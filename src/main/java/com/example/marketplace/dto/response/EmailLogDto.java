package com.example.marketplace.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

// DTO одной записи из истории отправки писем — ответ на GET /api/accountant/emails.
// Переносит данные сущности EmailLog в HTTP-ответ, не раскрывая лишних деталей реализации.
// Бухгалтер видит кому, когда и с каким результатом было отправлено каждое письмо.
@Getter
// Lombok: конструктор со всеми полями для компактного создания в AccountantService.getEmailsReport()
@AllArgsConstructor
public class EmailLogDto {

    // Идентификатор записи в таблице email_logs
    private Long id;

    // Email получателя письма
    private String recipient;

    // Тема письма
    private String subject;

    // Дата и время попытки отправки (фиксируется в EmailService.saveLog())
    private LocalDateTime sentAt;

    // true — SMTP-сервер принял письмо, false — произошла ошибка при отправке
    private boolean success;

    // Текст ошибки (из MailException.getMessage()) при success=false; null при успехе.
    // Усечён до 500 символов на уровне сущности EmailLog
    private String errorMessage;
}
