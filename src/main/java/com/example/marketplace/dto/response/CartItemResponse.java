package com.example.marketplace.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Одна позиция корзины в ответе.
 *
 * price — текущая цена товара (берётся из Product.price, не фиксируется).
 * Это нормально для корзины: цена «живая», может меняться пока товар в корзине.
 * Зафиксированная цена (priceAtOrder) появляется только после оформления заказа в OrderItem.
 */
@Data
public class CartItemResponse {
    private Long id;           // id позиции корзины (CartItem.id)
    private Long productId;
    private String productName;
    private int quantity;
    private BigDecimal price;  // актуальная цена за единицу
}
