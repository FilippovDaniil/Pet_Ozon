package com.example.marketplace.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Одна позиция заказа в ответе.
 *
 * priceAtOrder — зафиксированная цена на момент оформления заказа.
 * Отличается от CartItemResponse.price тем, что не меняется при изменении
 * текущей цены товара (Product.price).
 */
@Data
public class OrderItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private int quantity;
    private BigDecimal priceAtOrder;
    private String itemStatus;  // null для FULL-заказов; обобщённый бейдж для BNPL (PENDING_ISSUE/ISSUED/CANCELLED/RETURNED)

    // Поштучный учёт фулфилмента (для BNPL-позиций). Сумма = quantity.
    private Integer pendingCount;    // ожидают выдачи
    private Integer issuedCount;     // выданы
    private Integer cancelledCount;  // отменены
    private Integer returnedCount;   // возвращены
}
