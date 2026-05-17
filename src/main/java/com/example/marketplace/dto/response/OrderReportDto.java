package com.example.marketplace.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class OrderReportDto {
    private Long orderId;
    private String customerName;
    private String customerEmail;
    private LocalDateTime orderDate;
    private String status;
    private BigDecimal totalAmount;
    private int itemsCount;
    private String shippingAddress;
}
