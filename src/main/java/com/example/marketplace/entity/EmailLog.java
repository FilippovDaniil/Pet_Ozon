package com.example.marketplace.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Сущность для хранения истории отправленных писем.
// Каждый вызов sendOrderReceipt / sendCustomEmail создаёт одну запись —
// независимо от того, удалась ли отправка или нет. Это позволяет бухгалтеру
// видеть как успешные доставки, так и все сбои SMTP.
@Entity
// Маппинг на таблицу email_logs; ddl-auto=update создаёт её автоматически при первом старте
@Table(name = "email_logs")
// Lombok: генерирует геттеры для всех полей (используются сервисом при чтении)
@Getter
// Lombok: генерирует сеттеры для всех полей (используются в EmailService.saveLog)
@Setter
// Lombok: создаёт конструктор без аргументов — обязателен для JPA (Hibernate создаёт объекты через него)
@NoArgsConstructor
public class EmailLog {

    // Автоинкрементный первичный ключ — PostgreSQL генерирует значение сам (SERIAL / IDENTITY)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Email получателя письма; nullable=false → NOT NULL в БД, поле обязательно
    @Column(nullable = false)
    private String recipient;

    // Тема письма; nullable=false → NOT NULL в БД, поле обязательно
    @Column(nullable = false)
    private String subject;

    // Дата и время попытки отправки; заполняется вызовом LocalDateTime.now() в saveLog()
    private LocalDateTime sentAt;

    // Флаг результата: true — письмо принял SMTP-сервер, false — произошла ошибка отправки
    private boolean success;

    // Текст исключения при ошибке отправки; null если success=true.
    // length=500 — усекаем длинные сообщения (MailException может содержать stack-trace),
    // чтобы не превышать разумный лимит столбца в БД
    @Column(length = 500)
    private String errorMessage;
}
