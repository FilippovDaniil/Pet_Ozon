package com.example.marketplace.controller;

import com.example.marketplace.dto.request.AdminEmailRequest;
import com.example.marketplace.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Контроллер для отправки произвольных писем администратором.
// Базовый путь /api/admin/email вложен в /api/admin/**, который SecurityConfig
// уже ограничил ролью ADMIN — первый уровень защиты на уровне URL.
@RestController
// Все методы класса обрабатывают запросы по базовому пути /api/admin/email
@RequestMapping("/api/admin/email")
// Lombok: вместо ручного написания конструктора — генерируется автоматически для final-полей.
// Spring видит единственный конструктор и подставляет бин EmailService через DI.
@RequiredArgsConstructor
public class EmailController {

    // EmailService выполняет фактическую отправку через JavaMailSender (SMTP Yandex)
    private final EmailService emailService;

    // POST /api/admin/email/send — отправить произвольное письмо любому адресату
    @PostMapping("/send")
    // Второй уровень защиты: даже если запрос каким-то образом обошёл SecurityConfig,
    // метод вернёт 403, если у пользователя нет роли ADMIN.
    // @PreAuthorize работает через AOP — перехватывает вызов до выполнения метода.
    @PreAuthorize("hasRole('ADMIN')")
    // @Valid — запускает Bean Validation для AdminEmailRequest перед входом в метод.
    // При нарушении ограничений (@NotBlank, @Email) Spring автоматически возвращает 400.
    // @RequestBody — Jackson десериализует JSON из тела запроса в объект AdminEmailRequest.
    public ResponseEntity<Void> send(@Valid @RequestBody AdminEmailRequest req) {
        // Делегируем отправку сервису, передавая три поля из тела запроса.
        // Если SMTP недоступен — EmailService бросает RuntimeException → GlobalExceptionHandler → 500
        emailService.sendCustomEmail(req.getTo(), req.getSubject(), req.getText());
        // 200 OK без тела ответа — письмо принято к отправке
        return ResponseEntity.ok().build();
    }
}
