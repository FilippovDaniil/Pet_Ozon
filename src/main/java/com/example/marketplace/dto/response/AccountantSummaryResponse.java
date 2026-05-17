package com.example.marketplace.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

// DTO сводного отчёта для бухгалтера — ответ на GET /api/accountant/summary.
// Содержит ключевые бизнес-показатели (KPI) платформы: заказы, выручка, корзины, письма.
// Используется для отображения 8 карточек-метрик на дашборде бухгалтера.
@Getter
// Lombok: генерирует конструктор со всеми полями в порядке объявления.
// Это позволяет создавать объект одной строкой в AccountantService: new AccountantSummaryResponse(...)
@AllArgsConstructor
public class AccountantSummaryResponse {

    // Всего заказов в системе вне зависимости от статуса (PAID + CREATED + CANCELLED и т.д.)
    private long totalOrders;

    // Только оплаченные заказы (статус OrderStatus.PAID) — реально исполненные продажи
    private long paidOrders;

    // Фактическая выручка: сумма totalAmount только по PAID-заказам.
    // BigDecimal — для точных финансовых вычислений без погрешностей float/double
    private BigDecimal totalRevenue;

    // Количество пользователей с ролью CLIENT — размер клиентской базы
    private long totalClients;

    // Суммарное количество позиций во всех корзинах — показатель потенциального спроса
    private long cartItemsCount;

    // Потенциальная выручка: Σ (цена товара × количество) по всем позициям корзин.
    // Показывает, сколько денег «лежит» в незавершённых корзинах
    private BigDecimal potentialRevenue;

    // Общее число записей в таблице email_logs (успешные + неуспешные отправки)
    private long emailsSent;

    // Из всех попыток — сколько писем успешно доставлено (EmailLog.success = true)
    private long emailsSuccess;
}
