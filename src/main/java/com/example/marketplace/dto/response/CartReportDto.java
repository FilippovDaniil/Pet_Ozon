package com.example.marketplace.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class CartReportDto {
    private String customerName;
    private String customerEmail;
    private String productName;
    private String category;
    private int quantity;
    private BigDecimal price;
    private BigDecimal subtotal;
}
