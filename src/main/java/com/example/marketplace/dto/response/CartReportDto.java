package com.example.marketplace.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

// DTO одной строки в отчёте по содержимому корзин — ответ на GET /api/accountant/carts.
// Отображает, какой товар, в каком количестве и на какую сумму находится в корзине
// каждого покупателя. Используется бухгалтером для анализа незавершённых покупок.
@Getter
// Lombok: конструктор со всеми полями для компактного создания в AccountantService.getCartsReport()
@AllArgsConstructor
public class CartReportDto {

    // Имя покупателя: User.fullName или User.email (fallback если fullName не заполнен)
    private String customerName;

    // Email покупателя — для однозначной идентификации клиента
    private String customerEmail;

    // Название товара из каталога (Product.name)
    private String productName;

    // Категория товара (Product.category): "Ноутбуки", "Аудио", "Периферия" и т.д.
    private String category;

    // Количество единиц данного товара в корзине покупателя
    private int quantity;

    // Текущая цена товара за единицу (Product.price) — на момент формирования отчёта
    private BigDecimal price;

    // Стоимость позиции итого: price × quantity. Вычисляется в сервисе, не хранится в БД.
    private BigDecimal subtotal;
}
