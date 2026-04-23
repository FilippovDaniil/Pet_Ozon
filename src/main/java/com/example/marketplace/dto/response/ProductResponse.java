package com.example.marketplace.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Ответ на запросы о товаре (GET /api/products, GET /api/products/{id}).
 *
 * «Плоский» DTO: данные продавца встроены напрямую (sellerId, sellerName, shopName),
 * а не вложенным объектом. Это упрощает десериализацию на клиенте.
 *
 * Поле imageUrl может быть null — клиент должен это обрабатывать.
 */
@Data
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private int stockQuantity;
    private String imageUrl;
    private String category;
    // Данные продавца — заполняются только если у товара есть seller.
    private Long sellerId;
    private String sellerName;
    private String shopName;
}
