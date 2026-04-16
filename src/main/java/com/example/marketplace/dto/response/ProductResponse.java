package com.example.marketplace.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private int stockQuantity;
    private String imageUrl;
    private Long sellerId;
    private String sellerName;
    private String shopName;
}
