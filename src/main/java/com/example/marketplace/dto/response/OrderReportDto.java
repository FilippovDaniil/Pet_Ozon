package com.example.marketplace.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// DTO одной строки в отчёте по заказам — ответ на GET /api/accountant/orders.
// Создаётся в AccountantService.getOrdersReport() маппингом из сущности Order:
// каждый Order превращается в один OrderReportDto и отдаётся бухгалтеру.
@Getter
// Lombok: конструктор со всеми полями — нужен для компактного new OrderReportDto(...) в сервисе
@AllArgsConstructor
public class OrderReportDto {

    // Идентификатор заказа (первичный ключ Order.id)
    private Long orderId;

    // Отображаемое имя покупателя: User.fullName, если заполнено; иначе User.email (fallback)
    private String customerName;

    // Email покупателя (User.email) — однозначный идентификатор для связи с клиентом
    private String customerEmail;

    // Дата и время оформления заказа (Order.orderDate)
    private LocalDateTime orderDate;

    // Статус в виде строки: "PAID", "CREATED", "CANCELLED" — получается через OrderStatus.name()
    private String status;

    // Итоговая сумма заказа в рублях (Order.totalAmount)
    private BigDecimal totalAmount;

    // Количество позиций в заказе: order.getItems().size()
    private int itemsCount;

    // Адрес доставки, указанный покупателем при оформлении (Order.shippingAddress)
    private String shippingAddress;
}
