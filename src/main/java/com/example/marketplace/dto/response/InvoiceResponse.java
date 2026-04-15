package com.example.marketplace.dto.response;

import com.example.marketplace.entity.enums.InvoiceStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InvoiceResponse {
    private Long id;
    private Long orderId;
    private BigDecimal amount;
    private InvoiceStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
