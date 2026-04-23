package com.example.marketplace.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ответ на GET /api/cart и все операции с корзиной.
 *
 * totalPrice вычисляется в CartService.toCartResponse() на основе items,
 * а не хранится в БД — всегда актуально при изменении цены товара.
 */
@Data
public class CartResponse {
    private Long id;
    private List<CartItemResponse> items;
    private BigDecimal totalPrice;  // сумма всех позиций: Σ(price × quantity)
}
