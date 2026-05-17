package com.example.marketplace.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// DTO одной строки в отчёте по покупателям — ответ на GET /api/accountant/customers.
// Агрегирует данные пользователя и его заказов для анализа клиентской базы бухгалтером.
// Позволяет видеть активность каждого клиента: сколько заказал и сколько потратил.
@Getter
// Lombok: конструктор со всеми полями — нужен для компактного создания в сервисе
@AllArgsConstructor
public class CustomerReportDto {

    // Идентификатор пользователя (User.id) — первичный ключ в таблице users
    private Long id;

    // Отображаемое имя: User.fullName, если заполнено; иначе User.email (fallback)
    private String fullName;

    // Email покупателя — логин и контактные данные
    private String email;

    // Общее количество заказов клиента (любой статус: PAID, CREATED, CANCELLED).
    // Показывает общую активность клиента, а не только оплаченные заказы.
    private int ordersCount;

    // Сумма фактически потраченных средств: только оплаченные заказы (PAID).
    // BigDecimal — для точного суммирования денежных значений
    private BigDecimal totalSpent;

    // Дата регистрации аккаунта (User.createdAt) — для анализа «возраста» клиента
    private LocalDateTime joinedAt;
}
