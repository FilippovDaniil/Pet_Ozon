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
    private BigDecimal priceAtOrder;  // историческая цена, зафиксированная при заказе
}
