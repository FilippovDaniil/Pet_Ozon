package com.example.marketplace.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CustomerReportDto {
    private Long id;
    private String fullName;
    private String email;
    private int ordersCount;
    private BigDecimal totalSpent;
    private LocalDateTime joinedAt;
}
